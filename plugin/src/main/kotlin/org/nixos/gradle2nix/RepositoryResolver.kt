package org.nixos.gradle2nix

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.auth.BasicSessionCredentials
import org.apache.ivy.core.LogOptions
import org.apache.ivy.core.cache.ArtifactOrigin
import org.apache.ivy.core.cache.CacheResourceOptions
import org.apache.ivy.core.cache.DefaultRepositoryCacheManager
import org.apache.ivy.core.cache.RepositoryCacheManager
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.DownloadOptions
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.core.settings.TimeoutConstraint
import org.apache.ivy.plugins.repository.Repository
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.repository.url.URLRepository
import org.apache.ivy.plugins.repository.url.URLResource
import org.apache.ivy.plugins.resolver.AbstractResolver
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.apache.ivy.plugins.resolver.URLResolver
import org.apache.maven.artifact.repository.metadata.io.xpp3.MetadataXpp3Reader
import org.codehaus.plexus.util.ReaderFactory
import org.codehaus.plexus.util.xml.pull.XmlPullParserException
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.AuthenticationContainer
import org.gradle.api.artifacts.repositories.AuthenticationSupported
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.UrlArtifactRepository
import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.authentication.aws.AwsImAuthentication
import org.gradle.internal.authentication.AllSchemesAuthentication
import org.gradle.kotlin.dsl.getCredentials
import java.io.IOException
import java.net.URI
import org.apache.ivy.core.module.descriptor.Artifact as IvyArtifact
import org.apache.ivy.core.module.descriptor.DefaultArtifact as IvyDefaultArtifact
import org.apache.ivy.plugins.resolver.RepositoryResolver as IvyRepositoryResolver

internal fun ResolutionAwareRepository.repositoryResolver(
    project: Project,
    scope: ConfigurationScope,
    ivySettings: IvySettings
): RepositoryResolver? =
    when(this) {
        is MavenArtifactRepository -> MavenResolver(project, scope, ivySettings, this)
        is IvyArtifactRepository -> IvyResolver(project, scope, ivySettings, this)
        else -> null
    }

internal sealed class RepositoryResolver {
    companion object {
        @JvmStatic
        protected val log: Logger = Logging.getLogger("gradle2nix")
    }

    abstract val ivyResolver: IvyRepositoryResolver

    abstract fun resolve(
        artifactId: DefaultArtifactIdentifier,
        sha256: String? = null
    ): DefaultArtifact?
}

internal class MavenResolver(
    project: Project,
    scope: ConfigurationScope,
    ivySettings: IvySettings,
    repository: MavenArtifactRepository
) : RepositoryResolver() {

    override val ivyResolver: IBiblioResolver = IBiblioResolver().apply {
        name = repository.name
        root = repository.url.toString()
        isM2compatible = true
        settings = ivySettings
        setCache(cacheManager(project, scope, ivySettings, repository).name)
        setRepository(resolverRepository(repository))
    }

    override fun resolve(artifactId: DefaultArtifactIdentifier, sha256: String?): DefaultArtifact? {
        val ivyArtifact: IvyArtifact = artifactId.toArtifact()
        val origin = ivyResolver.locate(ivyArtifact)
        if (origin == null || !origin.isExists) return null
        val hash = if (sha256 != null) sha256 else {
            val report = ivyResolver.download(origin, downloadOptions)
            report.localFile?.sha256().also {
                if (it == null) log.error(report.toString())
            }
        }
        if (hash == null) {
            log.error("Failed to download '$artifactId' from repository '${ivyResolver.repository.name}'")
            return null
        }
        val snapshotVersion: SnapshotVersion? = artifactId.version.snapshotVersion()?.let {
            findSnapshotVersion(artifactId, it)
        }
        return DefaultArtifact(
            id = artifactId,
            name = artifactId.filename(snapshotVersion),
            path = artifactId.repoPath(),
            timestamp = snapshotVersion?.timestamp,
            build = snapshotVersion?.build,
            urls = listOf(origin.location),
            sha256 = hash
        )
    }

    private fun findSnapshotVersion(
        artifactId: ArtifactIdentifier,
        snapshotVersion: SnapshotVersion
    ): SnapshotVersion {
        if (snapshotVersion.timestamp != null) return snapshotVersion
        val metadataLocation = "${ivyResolver.root}${artifactId.repoPath()}/maven-metadata.xml"
        val metadataFile = ivyResolver.repositoryCacheManager.downloadRepositoryResource(
            ivyResolver.repository.getResource(metadataLocation),
            "maven-metadata",
            "maven-metadata",
            "xml",
            CacheResourceOptions(),
            ivyResolver.repository
        ).localFile

        if (metadataFile == null) {
            log.warn("maven-metadata.xml not found for snapshot dependency: $artifactId")
            return snapshotVersion
        }

        fun parseError(e: Throwable): Pair<String?, Int?> {
            log.error("Failed to parse maven-metadata.xml for artifact: $artifactId")
            log.error("Error was: ${e.message}", e)
            return null to null
        }

        val (timestamp: String?, build: Int?) = try {
            MetadataXpp3Reader()
                .read(ReaderFactory.newXmlReader(metadataFile))
                .versioning?.snapshot?.run { timestamp to buildNumber }
                ?: null to null
        } catch (e: IOException) {
            parseError(e)
        } catch (e: XmlPullParserException) {
            parseError(e)
        }

        return snapshotVersion.copy(timestamp = timestamp, build = build)
    }
}

internal class IvyResolver(
    project: Project,
    scope: ConfigurationScope,
    ivySettings: IvySettings,
    repository: IvyArtifactRepository
) : RepositoryResolver() {

    override val ivyResolver: URLResolver = URLResolver().apply {
        name = repository.name
        val ivyResolver = (repository as ResolutionAwareRepository).createResolver() as IvyResolver
        isM2compatible = ivyResolver.isM2compatible
        for (p in ivyResolver.ivyPatterns) addIvyPattern(p)
        for (p in ivyResolver.artifactPatterns) addArtifactPattern(p)
        settings = ivySettings
        setCache(cacheManager(project, scope, ivySettings, repository).name)
        setRepository(resolverRepository(repository))
    }

    override fun resolve(artifactId: DefaultArtifactIdentifier, sha256: String?): DefaultArtifact? {
        val ivyArtifact: IvyArtifact = artifactId.toArtifact()
        val origin = ivyResolver.locate(ivyArtifact)?.takeIf(ArtifactOrigin::isExists) ?: return null
        val hash = if (sha256 != null) sha256 else {
            val report = ivyResolver.download(origin, downloadOptions)
            report.localFile?.sha256().also {
                if (it == null) log.error(report.toString())
            }
        }
        if (hash == null) {
            log.error("Failed to download '$artifactId' from repository '${ivyResolver.repository.name}'")
            return null
        }
        return DefaultArtifact(
            id = DefaultArtifactIdentifier(artifactId),
            name = artifactId.filename(null),
            path = artifactId.repoPath(),
            urls = listOf(origin.location),
            sha256 = hash
        )
    }
}

private fun cacheManager(
    project: Project,
    scope: ConfigurationScope,
    ivySettings: IvySettings,
    repository: ArtifactRepository
): RepositoryCacheManager {
    return DefaultRepositoryCacheManager(
        "${scope.name.toLowerCase()}-${repository.name}-cache",
        ivySettings,
        project.buildDir.resolve("tmp/gradle2nix/${repository.name}").also {
            it.mkdirs()
        }
    ).also {
        ivySettings.addRepositoryCacheManager(it)
    }
}

private val metadataTypes = setOf("pom", "ivy")

private fun ArtifactIdentifier.toArtifact(): IvyArtifact {
    val moduleRevisionId = ModuleRevisionId.newInstance(group, name, version)
    val artifactRevisionId = ArtifactRevisionId.newInstance(
        moduleRevisionId,
        name,
        type,
        extension,
        classifier?.let { mapOf("classifier" to it) }
    )
    return IvyDefaultArtifact(artifactRevisionId, null, null, type in metadataTypes)
}

private data class SnapshotVersion(
    val base: String,
    val timestamp: String?,
    val build: Int?
) {
    override fun toString(): String {
        return if (timestamp != null && build != null) {
            "$base-$timestamp-$build"
        } else {
            "$base-SNAPSHOT"
        }
    }
}

private val SNAPSHOT_REGEX = Regex("^(.*)-SNAPSHOT$")
private val SNAPSHOT_TIMESTAMPED_REGEX = Regex("^(.*)-([0-9]{8}.[0-9]{6})-([0-9]+)$")

private fun String.snapshotVersion(): SnapshotVersion? {
    return SNAPSHOT_REGEX.find(this)?.destructured?.let { (base) ->
        SnapshotVersion(base, null, null)
    } ?: SNAPSHOT_TIMESTAMPED_REGEX.find(this)?.destructured?.let { (base, timestamp, build) ->
        SnapshotVersion(base, timestamp, build.toInt())
    }
}

private fun ArtifactIdentifier.repoPath(): String =
    "${group.replace('.', '/')}/$name/$version"

private fun ArtifactIdentifier.filename(
    snapshotVersion: SnapshotVersion?
): String = buildString {
    append(name, "-", snapshotVersion ?: version)
    if (classifier != null) append("-", classifier)
    append(".", extension)
}

private val downloadOptions = DownloadOptions().apply {
    log = LogOptions.LOG_DEFAULT
}

private fun <T> AbstractResolver.resolverRepository(
    repository: T
) : Repository
where T : ArtifactRepository,
      T : AuthenticationSupported =
    when (val scheme = repository.url.scheme) {
        "s3" -> s3Repository(
            repository.getCredentials(AwsCredentials::class),
            LazyTimeoutConstraint(this)
        )
        "http", "https" -> URLRepository(LazyTimeoutConstraint(this))
        else -> throw IllegalStateException("Unknown repository URL scheme: $scheme")
    }

private fun s3Repository(
    credentials: AwsCredentials?,
    timeoutConstraint: TimeoutConstraint
): Repository {
    val awsCredentials = credentials?.let {
        if (it.sessionToken == null) {
            BasicAWSCredentials(it.accessKey, it.secretKey)
        } else {
            BasicSessionCredentials(it.accessKey, it.secretKey, it.sessionToken)
        }
    }
    return S3Repository(
        credentials = awsCredentials,
        endpoint = System.getProperty("org.gradle.s3.endpoint")?.let { URI(it) },
        timeoutConstraint = timeoutConstraint
    ).apply {
        name = "AWS S3"
    }
}

private class LazyTimeoutConstraint(
    private val resolver: AbstractResolver
) : TimeoutConstraint {
    override fun getConnectionTimeout(): Int =
        resolver.timeoutConstraint?.connectionTimeout ?: -1

    override fun getReadTimeout(): Int =
        resolver.timeoutConstraint?.readTimeout ?: -1
}

// Compatibility shim as UrlArtifactRepository was added in Gradle 6.0
private val ArtifactRepository.url: URI get() = when (this) {
    is MavenArtifactRepository -> url
    is IvyArtifactRepository -> url
    else -> throw IllegalStateException("Unhandled repository type: ${this::class.simpleName}")
}