package org.nixos.gradle2nix

import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultModelBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals


class BasicTest {
    companion object {
        @JvmStatic @TempDir lateinit var projectDir: File

        val initScript: File by lazy {
            projectDir.resolve("init.gradle").also {
                it.writer().use { out ->
//                    val classpath = DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath())
//                        .asFiles.joinToString(prefix = "'", postfix = "'")
//                    out.appendln("""
//                        initscript {
//                            dependencies {
//                                classpath files($classpath)
//                            }
//                        }
//
//                        apply plugin: org.nixos.gradle2nix.Gradle2NixPlugin
//                    """.trimIndent())
                    out.appendln("apply plugin: org.nixos.gradle2nix.Gradle2NixPlugin")
                }
            }
        }
    }

    @Test
    fun `builds basic project with kotlin dsl`() {
        projectDir.resolve("build.gradle.kts").writeText("""
            plugins {
                java
            }

            repositories {
                jcenter()
            }

            dependencies {
                implementation("com.squareup.okio:okio:2.2.2")
                implementation("com.squareup.moshi:moshi:1.8.0")
            }
        """.trimIndent())

        val connection = GradleConnector.newConnector()
            .useGradleVersion(System.getProperty("compat.gradle.version"))
            .forProjectDirectory(projectDir)
            .connect()

        val model = (connection.model(Build::class.java) as DefaultModelBuilder<Build>)
            .withArguments(
                "--init-script=$initScript",
                "--stacktrace"
            )
            .withInjectedClassPath(DefaultClassPath.of(PluginUnderTestMetadataReading.readImplementationClasspath()))
            .setStandardOutput(System.out)
            .setStandardError(System.out)
            .get()

        assertEquals(model.gradle.version, System.getProperty("compat.gradle.version"))

        with(model.rootProject.projectDependencies) {
            with(repositories) {
                assertEquals(1, maven.size)
                assertEquals(maven[0].urls[0], "https://jcenter.bintray.com/")
            }
        }
    }
}
