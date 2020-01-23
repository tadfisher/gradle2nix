package org.nixos.gradle2nix

import com.squareup.moshi.Moshi
import dev.minutest.ContextBuilder
import dev.minutest.MinutestFixture
import dev.minutest.TestContextBuilder
import okio.buffer
import okio.source
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.util.GradleVersion
import org.junit.jupiter.api.Assumptions.assumeTrue
import strikt.api.Assertion
import strikt.assertions.map
import java.io.File
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

const val SONATYPE_OSS_URL = "https://oss.sonatype.org/content/repositories/snapshots/"

private val moshi = Moshi.Builder().build()

private val gradleVersion = GradleVersion.version(System.getProperty("compat.gradle.version"))

val GRADLE_4_5 = GradleVersion.version("4.5")

private fun File.initscript() = resolve("init.gradle").also {
    it.writer().use { out ->
        val classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath())
            .asFiles.joinToString { n -> "'$n'" }
        out.appendln("""
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
    return resolve("build/nix/model.json").run {
        println(readText())
        source().buffer().use { src ->
            checkNotNull(moshi.adapter(DefaultBuild::class.java).fromJson(src))
        }
    }
}

val <T : Iterable<Artifact>> Assertion.Builder<T>.ids: Assertion.Builder<Iterable<String>>
    get() = map { it.id.toString() }

@MinutestFixture
class Fixture(val testRoots: List<Path>)

@MinutestFixture
class ProjectFixture(val testRoot: Path) {
    fun build(
        configurations: List<String> = emptyList(),
        subprojects: List<String> = emptyList()
    ) = testRoot.toFile().build(configurations, subprojects)
}

fun ContextBuilder<Fixture>.withFixture(
    name: String,
    block: TestContextBuilder<Fixture, ProjectFixture>.() -> Unit
) = context(name) {
    val url = checkNotNull(Thread.currentThread().contextClassLoader.getResource(name)?.toURI()) {
        "$name: No test fixture found"
    }
    val fixtureRoot = Paths.get(url)
    val dest = createTempDir("gradle2nix").toPath()
    val src = checkNotNull(fixtureRoot.takeIf(Files::exists)) {
        "$name: Test fixture not found: $fixtureRoot}"
    }
    src.toFile().copyRecursively(dest.toFile())
    val testRoots = Files.list(dest).filter { Files.isDirectory(it) }.toList()

    fixture {
        Fixture(testRoots)
    }

    afterAll {
        dest.toFile().deleteRecursively()
    }

    testRoots.forEach { testRoot ->
        derivedContext<ProjectFixture>(testRoot.fileName.toString()) {
            deriveFixture { ProjectFixture(testRoot) }
            block()
        }
    }
}
