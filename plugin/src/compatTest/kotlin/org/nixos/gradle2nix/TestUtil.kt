package org.nixos.gradle2nix

import org.gradle.api.internal.artifacts.dsl.ParsedModuleStringNotation
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.tooling.GradleConnector
import java.io.File
import kotlin.test.assertTrue

private fun File.initscript() = resolve("init.gradle").also {
    it.writer().use { out ->
        val classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath())
            .asFiles.joinToString(prefix = "'", postfix = "'")
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

fun File.buildGroovy(script: String): DefaultBuild {
    resolve("build.gradle").writeText(script)
    return build()
}

fun File.buildKotlin(script: String): DefaultBuild {
    resolve("build.gradle.kts").writeText(script)
    return build()
}

private fun File.build(): DefaultBuild {
    return GradleConnector.newConnector()
        .useGradleVersion(System.getProperty("compat.gradle.version"))
        .forProjectDirectory(this)
        .connect()
        .model(Build::class.java)
        .withArguments(
            "--init-script=${initscript()}",
            "--stacktrace"
        )
        .setStandardOutput(System.out)
        .setStandardError(System.out)
        .get()
        .let { DefaultBuild(it) }
}

fun jar(notation: String, sha256: String = ""): DefaultArtifact =
        artifact(notation, sha256, "jar")

fun pom(notation: String, sha256: String = ""): DefaultArtifact =
        artifact(notation, sha256, "pom")

private fun artifact(notation: String, sha256: String, type: String): DefaultArtifact {
    val parsed = ParsedModuleStringNotation(notation, type)
    return DefaultArtifact(
        groupId = parsed.group ?: "",
        artifactId = parsed.name ?: "",
        version = parsed.version ?: "",
        classifier = parsed.classifier ?: "",
        extension = type,
        sha256 = sha256
    )
}

private fun artifactEquals(expected: DefaultArtifact, actual: DefaultArtifact): Boolean {
    return with (expected) {
        groupId == actual.groupId &&
                artifactId == actual.artifactId &&
                version == actual.version &&
                classifier == actual.classifier &&
                extension == actual.extension &&
                (sha256.takeIf { it.isNotEmpty() }?.equals(actual.sha256) ?: true)
    }
}

fun assertArtifacts(vararg expected: DefaultArtifact, actual: List<DefaultArtifact>) {
    val mismatches = mutableListOf<Mismatch>()
    val remaining = mutableListOf<DefaultArtifact>().also { it.addAll(actual) }
    expected.forEachIndexed { i: Int, exp: DefaultArtifact ->
        val act = actual[i]
        if (!artifactEquals(exp, act)) {
            mismatches += Mismatch(i, exp, act)
        } else {
            remaining -= act
        }
    }
    assertTrue(mismatches.isEmpty() && remaining.isEmpty(), """
        Artifact mismatches:
        ${mismatches.joinToString("\n            ", prefix = "            ")}

        Missing artifacts:
        ${remaining.joinToString("\n            ", prefix = "            ")}
    """)
}

data class Mismatch(
    val index: Int,
    val expected: DefaultArtifact,
    val actual: DefaultArtifact
)
