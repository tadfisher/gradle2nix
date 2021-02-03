package org.nixos.gradle2nix

import com.adobe.testing.s3mock.S3MockApplication
import com.adobe.testing.s3mock.junit5.S3MockExtension
import com.adobe.testing.s3mock.testsupport.common.S3MockStarter
import com.squareup.moshi.Moshi
import dev.minutest.ContextBuilder
import dev.minutest.MinutestFixture
import dev.minutest.Node
import dev.minutest.TestContextBuilder
import dev.minutest.afterEach
import dev.minutest.beforeEach
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.TransformingAnnotation
import dev.minutest.given
import dev.minutest.givenClosable
import dev.minutest.given_
import io.javalin.Javalin
import io.javalin.http.staticfiles.Location
import okio.buffer
import okio.source
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assumptions.assumeTrue
import strikt.api.Assertion
import strikt.assertions.map
import java.io.Closeable
import java.io.File
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicBoolean

private val moshi = Moshi.Builder().build()

val fixtureRoot = File(System.getProperty("fixtures"))

val gradleVersion = System.getProperty("compat.gradle.version")
    ?.let(GradleVersion::version)
    ?: GradleVersion.current()

val GRADLE_4_5 = GradleVersion.version("4.5")

fun GRADLE_MIN(version: String) = object : TransformingAnnotation() {
    override fun <F> transform(node: Node<F>): Node<F> =
        if (gradleVersion < GradleVersion.version(version)) SKIP.transform(node) else node
}

private fun File.initscript() = resolve("init.gradle").also {
    it.writer().use { out ->
        val classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath())
            .asFiles.joinToString { n -> "'$n'" }
        out.append("""
            initscript {
                dependencies {
                    classpath files($classpath)
                }
            }

            apply plugin: org.nixos.gradle2nix.Gradle2NixPlugin
            
        """.trimIndent())
     }
}

fun File.buildKotlin(
    script: String,
    configurations: List<String> = emptyList(),
    subprojects: List<String> = emptyList()
): DefaultBuild {
    assumeTrue(gradleVersion >= GRADLE_4_5)
    resolve("build.gradle.kts").writeText(script)
    return build(configurations, subprojects)
}

private fun File.build(
    configurations: List<String>,
    subprojects: List<String>,
    extraArguments: List<String> = emptyList()
): DefaultBuild {
    val log = StringWriter()

    val result = GradleRunner.create()
        .withGradleVersion(gradleVersion.version)
        .withProjectDir(this)
        .forwardStdOutput(log)
        .forwardStdError(log)
        .withArguments(
            "nixModel",
            "--init-script=${initscript()}",
            "--stacktrace",
            "-Porg.nixos.gradle2nix.configurations=${configurations.joinToString(",")}",
            "-Porg.nixos.gradle2nix.subprojects=${subprojects.joinToString(",")}",
            *(extraArguments.toTypedArray())
        )
        .runCatching { build() }

    result.onFailure { error ->
        System.err.print(log)
        throw error
    }

    print(log)

    return resolve("build/nix/model.json").run {
        println(readText())
        source().buffer().use { src ->
            checkNotNull(moshi.adapter(DefaultBuild::class.java).fromJson(src))
        }
    }
}

val <T : Iterable<Artifact>> Assertion.Builder<T>.ids: Assertion.Builder<Iterable<String>>
    get() = map { it.id.toString() }

private fun File.parents() = generateSequence(parentFile) { it.parentFile }

abstract class ArgumentsSupplier(private val parent: ArgumentsSupplier? = null) {
    open val arguments: List<String> = emptyList()

    val extraArguments: List<String> get() = (parent?.extraArguments ?: emptyList()) + arguments
}

@MinutestFixture
class RepositoryFixture(
    private val server: Javalin,
    parent: ArgumentsSupplier? = null
) : ArgumentsSupplier(parent), Closeable {
    override fun close() {
        server.stop()
    }
}

@MinutestFixture
class S3Fixture(
    private val name: String,
    parent: ArgumentsSupplier? = null
) : ArgumentsSupplier(parent), Closeable {
    private val s3mock = S3Mock(
        initialBuckets = listOf(name),
        secureConnection = false
    )

    override val arguments: List<String> get() = listOf(
        "-Dorg.gradle.s3.endpoint=${s3mock.serviceEndpoint}",
        "-Dorg.nixos.gradle2nix.s3test=true"
    )

    init {
        s3mock.startServer()

        val s3root = fixtureRoot.resolve(name)
        val s3client = s3mock.createS3Client()
        require(s3root.exists() && s3root.isDirectory) {
            "$name: S3 fixture not found: $s3root"
        }
        s3root.walkTopDown()
            .filter { it.isFile }
            .forEach { file ->
                val key = file.toRelativeString(s3root)
                s3client.putObject(name, key, file)
            }
    }

    override fun close() {
        s3mock.stopServer()
    }
}

@MinutestFixture
class TestFixture(
    val name: String,
    val source: File,
    parent: ArgumentsSupplier? = null
) : ArgumentsSupplier(parent), Closeable {
    val dest: File

    init {
        require(source.exists() && source.isDirectory) {
            "$name: Test fixture not found: $source}"
        }
        dest = createTempDir(prefix = name, suffix = "")
    }

    override fun close() {
        dest.deleteRecursively()
    }
}

@MinutestFixture
data class ProjectFixture(
    private val parent: TestFixture,
    private val source: File
) : Closeable {
    private val dest: File

    init {
        require(source.exists() && source.isDirectory && parent.source in source.parents()) {
            "${parent.name}: Test project not found: $source"
        }
        val rel = source.toRelativeString(parent.source)
        dest = parent.dest.resolve(rel)
    }

    fun copySource() {
        source.copyRecursively(dest, true)
    }

    fun build(
        configurations: List<String> = emptyList(),
        subprojects: List<String> = emptyList()
    ) = dest.build(configurations, subprojects, parent.extraArguments)

    override fun close() {
        dest.deleteRecursively()
    }
}

fun ContextBuilder<*>.withBucket(
    name: String,
    block: TestContextBuilder<*, S3Fixture>.() -> Unit
) = derivedContext<S3Fixture>("with s3 bucket: $name") {
    given_ { parent ->
        S3Fixture(name, parent as? ArgumentsSupplier)
    }

    afterEach { it.close() }

    block()
}

fun ContextBuilder<*>.withRepository(
    name: String,
    block: TestContextBuilder<*, RepositoryFixture>.() -> Unit
) = derivedContext<RepositoryFixture>("with repository: $name") {
    given_ { parent ->
        RepositoryFixture(
            server = Javalin.create { config ->
                config.addStaticFiles("${fixtureRoot}/repositories/$name", Location.EXTERNAL)
            }.start(9999),
            parent = parent as? ArgumentsSupplier
        )
    }

    afterEach { it.close() }

    block()
}

fun ContextBuilder<*>.withFixture(
    name: String,
    block: TestContextBuilder<*, ProjectFixture>.() -> Unit
) = derivedContext<TestFixture>(name) {

    val projectRoot = fixtureRoot.resolve(name).also {
        check(it.exists()) { "$name: project fixture not found: $it" }
    }

    given_ { parent ->
        TestFixture(name, projectRoot, parent as? ArgumentsSupplier)
    }

    val testRoots = projectRoot.listFiles()!!
        .filter { it.isDirectory }
        .map { it.absoluteFile }
        .toList()

    testRoots.forEach { testRoot ->
        derivedContext<ProjectFixture>(testRoot.name) {
            given_ { parent -> ProjectFixture(parent, testRoot) }
            beforeEach { copySource() }
            afterEach { close() }
            block()
        }
    }
}

class S3Mock(
    initialBuckets: List<String> = emptyList(),
    secureConnection: Boolean = true
) : S3MockStarter(
    mapOf(
        S3MockApplication.PROP_INITIAL_BUCKETS to initialBuckets.joinToString(","),
        S3MockApplication.PROP_SECURE_CONNECTION to secureConnection
    )
) {
    private val running = AtomicBoolean()

    fun startServer() {
        if (running.compareAndSet(false, true)) {
            start()
        }
    }

    fun stopServer() {
        if (running.compareAndSet(true, false)) {
            stop()
        }
    }
}