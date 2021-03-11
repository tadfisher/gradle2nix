package org.nixos.gradle2nix

import com.adobe.testing.s3mock.junit5.S3MockExtension
import com.amazonaws.services.s3.AmazonS3
import org.apache.ivy.ant.IvyDependencyArtifact
import org.apache.ivy.core.module.descriptor.Artifact
import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.id.ArtifactRevisionId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.repository.Resource
import org.apache.ivy.plugins.resolver.IBiblioResolver
import org.apache.ivy.plugins.resolver.URLResolver
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.RegisterExtension
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isTrue
import java.io.File
import java.net.URI
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class S3Test {
    companion object {
        const val bucket = "repositories"

        val fixtureRoot = File(System.getProperty("fixtures")).resolve(bucket)

        @JvmField
        @RegisterExtension
        val s3mock: S3MockExtension = S3MockExtension.builder().withInitialBuckets(bucket).build()
    }

    @BeforeAll
    fun populateBucket(client: AmazonS3) {
        fixtureRoot.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val key = file.toRelativeString(fixtureRoot)
                client.putObject(bucket, key, file)
            }
    }

    @Test
    fun listsContents(client: AmazonS3) {
        val repository = S3Repository(client)
        expectThat(repository.list("s3://repositories/m2/org/apache/test/1.0.0/")).containsExactly(
            "m2/org/apache/test/1.0.0/test-1.0.0.jar",
            "m2/org/apache/test/1.0.0/test-1.0.0.pom",
        )
    }

    @Test
    fun findsResourceMetadata(client: AmazonS3) {
        val repository = S3Repository(client)
        val resource: Resource = S3Resource(repository, URI("s3://repositories/m2/org/apache/test/1.0.0/test-1.0.0.pom"))
        expectThat(resource).and {
            get { exists() }.isTrue()
            get { contentLength }.isNotEqualTo(-1L)
            get { lastModified }.isNotEqualTo(-1L)
        }
    }

    @Test
    fun downloadsResource(client: AmazonS3) {
        val repository = S3Repository(client)
        val resource: Resource = S3Resource(repository, URI("s3://repositories/m2/org/apache/test/1.0.0/test-1.0.0.pom"))
        val source = fixtureRoot.resolve("m2/org/apache/test/1.0.0/test-1.0.0.pom").readText()

        expectThat(resource.openStream().bufferedReader().readText()).isEqualTo(source)
    }

    @ExperimentalPathApi
    @Test
    fun locatesArtifact(client: AmazonS3) {
        val resolver = IBiblioResolver().apply {
            name = "s3"
            root = "s3://repositories/m2/"
            isM2compatible = true
            settings = IvySettings().apply {
                defaultInit()
                setDefaultRepositoryCacheBasedir(createTempDirectory().toString())
            }
            repository = S3Repository(client)
        }
        val origin = resolver.locate(DefaultArtifact(
            ArtifactRevisionId.newInstance(
                ModuleRevisionId.newInstance("org.apache", "test", "1.0.0"),
                "test",
                "jar",
                "jar"
            ),
            null,
            null,
            false
        ))

        expectThat(origin).isNotNull().and {
            get { isExists }.isTrue()
            get { isLocal }.isFalse()
            get { location }.isEqualTo("s3://repositories/m2/org/apache/test/1.0.0/test-1.0.0.jar")
        }
    }
}