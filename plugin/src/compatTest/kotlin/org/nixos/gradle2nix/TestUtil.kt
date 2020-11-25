package org.nixos.gradle2nix

import com.squareup.moshi.Moshi
import dev.minutest.ContextBuilder
import dev.minutest.MinutestFixture
import dev.minutest.Node
import dev.minutest.TestContextBuilder
import dev.minutest.closeableFixture
import dev.minutest.experimental.SKIP
import dev.minutest.experimental.TransformingAnnotation
import io.javalin.Javalin
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
import java.nio.file.Paths

private val moshi = Moshi.Builder().build()

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
    subprojects: List<String>
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
            "-Porg.nixos.gradle2nix.subprojects=${subprojects.joinToString(",")}"
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

@MinutestFixture
class RepositoryFixture(private val server: Javalin) : Closeable {
    override fun close() {
        server.stop()
    }
}

@MinutestFixture
class TestFixture(val name: String, val source: File) : Closeable {
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
class ProjectFixture(private val parent: TestFixture, private val source: File) : Closeable {
    private val dest: File

    init {
        require(source.exists() && source.isDirectory && parent.source in source.parents()) {
            "${parent.name}: Test project not found: $source"
        }
        val rel = source.toRelativeString(parent.source)
        dest = parent.dest.resolve(rel)
    }

    fun copy() {
        source.copyRecursively(dest, true)
    }

    fun build(
        configurations: List<String> = emptyList(),
        subprojects: List<String> = emptyList()
    ) = dest.build(configurations, subprojects)

    override fun close() {
        dest.deleteRecursively()
    }
}

fun ContextBuilder<*>.withRepository(
    name: String,
    block: TestContextBuilder<*, RepositoryFixture>.() -> Unit
) = derivedContext<RepositoryFixture>("with repository: ${name}") {
    closeableFixture {
        RepositoryFixture(Javalin.create { config ->
            config.addStaticFiles("/repositories/$name")
        }.start(9999))
    }

    block()
}

fun ContextBuilder<*>.withFixture(
    name: String,
    block: TestContextBuilder<*, ProjectFixture>.() -> Unit
) = derivedContext<TestFixture>(name) {
    val url = checkNotNull(Thread.currentThread().contextClassLoader.getResource(name)?.toURI()) {
        "$name: No test fixture found"
    }
    val fixtureRoot = Paths.get(url).toFile().absoluteFile

    deriveFixture {
        TestFixture(name, fixtureRoot)
    }

    val testRoots = fixtureRoot.listFiles()!!
        .filter { it.isDirectory }
        .map { it.absoluteFile }
        .toList()

    testRoots.forEach { testRoot ->
        derivedContext<ProjectFixture>(testRoot.name) {
            deriveFixture { ProjectFixture(this, testRoot) }
            before { copy() }
            after { close() }
            block()
        }
    }
}
