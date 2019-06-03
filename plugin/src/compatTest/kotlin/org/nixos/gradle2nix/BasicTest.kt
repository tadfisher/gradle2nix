package org.nixos.gradle2nix

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals


class BasicTest {
    @TempDir lateinit var projectDir: File

    @Test
    fun `builds basic project with kotlin dsl`() {
        val model = projectDir.buildKotlin("""
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

        assertEquals(model.gradle.version, System.getProperty("compat.gradle.version"))

        with(model.rootProject.projectDependencies) {
            with(repositories) {
                assertEquals(1, maven.size)
                assertEquals(maven[0].urls[0], "https://jcenter.bintray.com/")
            }

            assertArtifacts(
                pom("com.squareup.moshi:moshi-parent:1.8.0"),
                jar("com.squareup.moshi:moshi:1.8.0"),
                pom("com.squareup.moshi:moshi:1.8.0"),
                jar("com.squareup.okio:okio:2.2.2"),
                pom("com.squareup.okio:okio:2.2.2"),
                jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                jar("org.jetbrains:annotations:13.0"),
                pom("org.jetbrains:annotations:13.0"),
                pom("org.sonatype.oss:oss-parent:7"),
                actual = artifacts
            )
        }
    }

    @Test
    fun `builds basic project with groovy dsl`() {
        val model = projectDir.buildGroovy("""
            plugins {
                id("java")
            }

            repositories {
                jcenter()
            }

            dependencies {
                implementation 'com.squareup.okio:okio:2.2.2'
                implementation 'com.squareup.moshi:moshi:1.8.0'
            }
        """.trimIndent())

        assertEquals(model.gradle.version, System.getProperty("compat.gradle.version"))

        with(model.rootProject.projectDependencies) {
            with(repositories) {
                assertEquals(1, maven.size)
                assertEquals(maven[0].urls[0], "https://jcenter.bintray.com/")
            }

            assertArtifacts(
                pom("com.squareup.moshi:moshi-parent:1.8.0"),
                jar("com.squareup.moshi:moshi:1.8.0"),
                pom("com.squareup.moshi:moshi:1.8.0"),
                jar("com.squareup.okio:okio:2.2.2"),
                pom("com.squareup.okio:okio:2.2.2"),
                jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                jar("org.jetbrains:annotations:13.0"),
                pom("org.jetbrains:annotations:13.0"),
                pom("org.sonatype.oss:oss-parent:7"),
                actual = artifacts
            )
        }
    }
}
