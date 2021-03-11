package org.nixos.gradle2nix

import com.amazonaws.AmazonServiceException
import com.amazonaws.ClientConfiguration
import com.amazonaws.auth.AWSCredentials
import com.amazonaws.auth.AWSStaticCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder
import com.amazonaws.regions.Region
import com.amazonaws.regions.RegionUtils
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.AmazonS3ClientBuilder
import com.amazonaws.services.s3.model.GetObjectRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectMetadata
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectInputStream
import com.amazonaws.util.AwsHostNameUtils
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.SSLConnectionSocketFactory
import org.apache.ivy.core.settings.TimeoutConstraint
import org.apache.ivy.plugins.repository.AbstractRepository
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.TransferEvent
import org.apache.ivy.util.FileUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.net.URI
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager


class S3Repository(
    private val client: AmazonS3,
    timeoutConstraint: TimeoutConstraint? = null
) : AbstractRepository(timeoutConstraint) {

    constructor(
        credentials: AWSCredentials?,
        endpoint: URI?,
        timeoutConstraint: TimeoutConstraint?
    ) : this(
        AmazonS3ClientBuilder.standard().apply {
            credentials?.let { setCredentials(AWSStaticCredentialsProvider(it)) }

            if (endpoint != null) {
                setEndpointConfiguration(
                    AwsClientBuilder.EndpointConfiguration(
                        endpoint.toString(),
                        AwsHostNameUtils.parseRegion(endpoint.host, null) ?: Regions.US_EAST_1.name
                    )
                )
                isChunkedEncodingDisabled = true
                isPathStyleAccessEnabled = true
            } else {
                region = Regions.US_EAST_1.name
            }

            if (System.getProperty("org.nixos.gradle2nix.s3test") != null) {
                clientConfiguration = ClientConfiguration().apply {
                    apacheHttpClientConfig.sslSocketFactory = SSLConnectionSocketFactory(
                        createBlindlyTrustingSslContext(),
                        NoopHostnameVerifier.INSTANCE
                    )
                }
            }
        }.build(),
        timeoutConstraint
    )

    private val cache = mutableMapOf<String, S3Resource>()

    private val progress = RepositoryCopyProgressListener(this)

    override fun getResource(source: String): Resource =
        cache.getOrPut(source) { S3Resource(this, URI(source)) }

    override fun get(source: String, destination: File) {
        fireTransferInitiated(getResource(source), TransferEvent.REQUEST_GET)
        try {
            val res = getResource(source)
            val totalLength = res.contentLength
            if (totalLength > 0) {
                progress.totalLength = totalLength
            }
            destination.parentFile?.mkdirs()
            FileUtil.copy(
                res.openStream(),
                FileOutputStream(destination),
                progress,
                true
            )
            fireTransferCompleted(res.contentLength)
        } catch (e: Exception) {
            fireTransferError(e)
            throw e
        } finally {
            progress.totalLength = null
        }
    }

    override fun list(parent: String): List<String> =
        S3Resource(this, URI(parent))
            .let { resource ->
                generateSequence({
                    try {
                        withClient(resource) {
                            listObjects(
                                ListObjectsRequest()
                                    .withBucketName(resource.bucket)
                                    .withPrefix(resource.key)
                                    .withDelimiter("/")
                            )
                        }
                    } catch (e: AmazonServiceException) {
                        throw S3RepositoryException(e)
                    }
                }) { prev ->
                    if (!prev.isTruncated) {
                        null
                    } else {
                        try {
                            withClient(resource) {
                                listNextBatchOfObjects(prev)
                            }
                        } catch (e: AmazonServiceException) {
                            throw S3RepositoryException(e)
                        }
                    }
                }
            }
            .flatMap { listing ->
                listing.commonPrefixes.asSequence() +
                        listing.objectSummaries.asSequence().map { it.key }
            }
            .toList()

    internal fun <T> withClient(
        resource: S3Resource,
        block: AmazonS3.() -> T
    ): T = client.apply {
        resource.region?.let { setRegion(it) }
    }.block()
}

class S3Resource(
    private val repository: S3Repository,
    private val url: URI
 ) : Resource {

    private val source: Source by lazy {
        REGIONAL_ENDPOINT_PATTERN.find(url.normalize().toString())
            ?.let {
                val (bucket, region, _, key) = it.destructured
                Source(
                    bucket = bucket,
                    key = key,
                    region = when (region) {
                        "external-1" -> Region.getRegion(Regions.US_EAST_1)
                        else -> RegionUtils.getRegion(region)
                    }
                )
            }
            ?: Source(
                bucket = url.bucket(),
                key = url.key(),
                region = null
            )
    }

    private val metadata: Metadata by lazy {
        try {
            getMetadata()
        } catch (e: AmazonServiceException) {
            null
        }?.let { meta ->
            Metadata(
                exists = true,
                contentLength = meta.contentLength,
                lastModified = meta.lastModified.time
            )
        } ?: Metadata(
            exists = false,
            contentLength = 0,
            lastModified = 0
        )
    }

    val bucket: String get() = source.bucket

    val key: String get() = source.key

    val region: Region? get() = source.region

    override fun getName(): String = url.toString()

    override fun getLastModified(): Long = metadata.lastModified

    override fun getContentLength(): Long = metadata.contentLength

    override fun exists(): Boolean = metadata.exists

    override fun isLocal(): Boolean = false

    override fun clone(cloneName: String): Resource = S3Resource(repository, URI(cloneName))

    override fun openStream(): InputStream =
        try { getContent() }
        catch (e: AmazonServiceException) { throw S3RepositoryException(e) }
            ?: throw S3RepositoryException()

    private fun getMetadata(): ObjectMetadata? =
        getObject(withContent = false)?.objectMetadata

    private fun getContent(): S3ObjectInputStream? =
        getObject(withContent = true)?.objectContent

    private fun getObject(withContent: Boolean = true): S3Object? {
        val request = GetObjectRequest(bucket, key)
        if (!withContent) {
            request.setRange(0, 0)
        }

        return try {
            repository.withClient(this) { getObject(request) }
        } catch (e: AmazonServiceException) {
            val errorCode = e.errorCode
            if (errorCode != null && "NoSuchKey".compareTo(errorCode, ignoreCase = true) == 0) {
                null
            } else {
                e.printStackTrace()
                throw e
            }
        }
    }

    private data class Source(
        val bucket: String,
        val key: String,
        val region: Region?
    )

    private data class Metadata(
        val exists: Boolean,
        val contentLength: Long,
        val lastModified: Long
    )

    companion object {
        private val REGIONAL_ENDPOINT_PATTERN =
            Regex("""^s3://(.+)?\.s3[.-]([a-z0-9-]+)\.amazonaws\.com(\.[a-z]+)?/(.+)""")
    }
}

class S3RepositoryException : RuntimeException {
    constructor() : super()

    constructor(throwable: Throwable) : super(throwable)
}

private fun URI.bucket(): String = normalize().host

private fun URI.key(): String = normalize().path.removePrefix("/")

// Used for testing.
private fun createBlindlyTrustingSslContext(): SSLContext? {
    return try {
        SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(object : X509ExtendedTrustManager() {
                override fun getAcceptedIssuers(): Array<X509Certificate>? = null
                override fun checkClientTrusted(arg0: Array<X509Certificate?>?, arg1: String?, arg2: Socket?) {}
                override fun checkClientTrusted(arg0: Array<X509Certificate?>?, arg1: String?, arg2: SSLEngine?) {}
                override fun checkClientTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                override fun checkServerTrusted(certs: Array<X509Certificate?>?, authType: String?) {}
                override fun checkServerTrusted(arg0: Array<X509Certificate?>?, arg1: String?, arg2: Socket?) {}
                override fun checkServerTrusted(arg0: Array<X509Certificate?>?, arg1: String?, arg2: SSLEngine?) {}
            }), SecureRandom())
        }
    } catch (e: NoSuchAlgorithmException) {
        throw RuntimeException("Unexpected exception", e)
    } catch (e: KeyManagementException) {
        throw RuntimeException("Unexpected exception", e)
    }
}
