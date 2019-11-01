package org.nixos.gradle2nix

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class SubprojectsTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `builds multi-module project with kotlin dsl`() {
        root.resolve("child-a").also { it.mkdirs() }
            .resolve("build.gradle.kts").writeText("""
                plugins {
                    java
                }

                dependencies {
                    implementation("com.squareup.okio:okio:2.2.2")
                }
            """.trimIndent())

        root.resolve("child-b").also { it.mkdirs() }
            .resolve("build.gradle.kts").writeText("""
                plugins {
                    java
                }

                dependencies {
                    implementation(project(":child-a"))
                    implementation("com.squareup.moshi:moshi:1.8.0")
                }
            """.trimIndent())

        root.resolve("settings.gradle.kts").writeText("""
            include(":child-a", ":child-b")
        """.trimIndent())

        val model = root.buildKotlin("""
            plugins {
                java
            }

            allprojects {
                repositories {
                    jcenter()
                }
            }

            dependencies {
                testImplementation("junit:junit:4.12")
            }
        """.trimIndent())

        with(model.rootProject) {
            with(projectDependencies) {
                assertEquals(listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                    repositories.maven)

                assertArtifacts(
                    jar("junit:junit:4.12"),
                    pom("junit:junit:4.12"),
                    jar("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-parent:1.3"),
                    actual = artifacts)
            }

            assertEquals(2, children.size)

            with(children[0]) {
                assertEquals("child-a", name)
                assertEquals(root.resolve("child-a").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(
                        listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven
                    )

                    assertArtifacts(
                        jar("com.squareup.okio:okio:2.2.2"),
                        pom("com.squareup.okio:okio:2.2.2"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        jar("org.jetbrains:annotations:13.0"),
                        pom("org.jetbrains:annotations:13.0"),
                        actual = artifacts
                    )
                }
            }

            with(children[1]) {
                assertEquals("child-b", name)
                assertEquals(root.resolve("child-b").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(
                        listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven
                    )

                    assertArtifacts(
                        pom("com.squareup.moshi:moshi-parent:1.8.0"),
                        jar("com.squareup.moshi:moshi:1.8.0"),
                        pom("com.squareup.moshi:moshi:1.8.0"),
                        pom("com.squareup.okio:okio-parent:1.16.0"),
                        jar("com.squareup.okio:okio:1.16.0"),
                        pom("com.squareup.okio:okio:1.16.0"),
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
    }

    @Test
    fun `builds multi-module project with groovy dsl`() {
        root.resolve("child-a").also { it.mkdirs() }
            .resolve("build.gradle").writeText("""
                plugins {
                    id 'java'
                }

                dependencies {
                    implementation 'com.squareup.okio:okio:2.2.2'
                }
            """.trimIndent())

        root.resolve("child-b").also { it.mkdirs() }
            .resolve("build.gradle").writeText("""
                plugins {
                    id 'java'
                }

                dependencies {
                    implementation project(':child-a')
                    implementation 'com.squareup.moshi:moshi:1.8.0'
                }
            """.trimIndent())

        root.resolve("settings.gradle").writeText("""
            include ':child-a', ':child-b'
        """.trimIndent())

        val model = root.buildGroovy("""
            plugins {
                id 'java'
            }

            allprojects {
                repositories {
                    jcenter()
                }
            }

            dependencies {
                testImplementation 'junit:junit:4.12'
            }
        """.trimIndent())

        with(model.rootProject) {
            with(projectDependencies) {
                assertEquals(listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                    repositories.maven)

                assertArtifacts(
                    jar("junit:junit:4.12"),
                    pom("junit:junit:4.12"),
                    jar("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-parent:1.3"),
                    actual = artifacts)
            }

            assertEquals(2, children.size)

            with(children[0]) {
                assertEquals("child-a", name)
                assertEquals(root.resolve("child-a").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(
                        listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven
                    )

                    assertArtifacts(
                        jar("com.squareup.okio:okio:2.2.2"),
                        pom("com.squareup.okio:okio:2.2.2"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        jar("org.jetbrains:annotations:13.0"),
                        pom("org.jetbrains:annotations:13.0"),
                        actual = artifacts
                    )
                }
            }

            with(children[1]) {
                assertEquals("child-b", name)
                assertEquals(root.resolve("child-b").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven)

                    assertArtifacts(
                        pom("com.squareup.moshi:moshi-parent:1.8.0"),
                        jar("com.squareup.moshi:moshi:1.8.0"),
                        pom("com.squareup.moshi:moshi:1.8.0"),
                        pom("com.squareup.okio:okio-parent:1.16.0"),
                        jar("com.squareup.okio:okio:1.16.0"),
                        pom("com.squareup.okio:okio:1.16.0"),
                        jar("com.squareup.okio:okio:2.2.2"),
                        pom("com.squareup.okio:okio:2.2.2"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        jar("org.jetbrains:annotations:13.0"),
                        pom("org.jetbrains:annotations:13.0"),
                        pom("org.sonatype.oss:oss-parent:7"),
                        actual = artifacts)
                }
            }
        }
    }

    @Test
    fun `builds single subproject in multi-module project with kotlin dsl`() {
        root.resolve("child-a").also { it.mkdirs() }
            .resolve("build.gradle.kts").writeText("""
                plugins {
                    java
                }

                dependencies {
                    implementation("com.squareup.okio:okio:2.2.2")
                }
            """.trimIndent())

        root.resolve("child-b").also { it.mkdirs() }
            .resolve("build.gradle.kts").writeText("""
                plugins {
                    java
                }

                dependencies {
                    implementation("com.squareup.moshi:moshi:1.8.0")
                }
            """.trimIndent())

        root.resolve("settings.gradle.kts").writeText("""
            include(":child-a", ":child-b")
        """.trimIndent())

        val model = root.buildKotlin("""
            plugins {
                java
            }

            allprojects {
                repositories {
                    jcenter()
                }
            }

            dependencies {
                testImplementation("junit:junit:4.12")
            }
        """.trimIndent(),
            subprojects = listOf(":child-a"))

        with(model.rootProject) {
            with(projectDependencies) {
                assertEquals(listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                    repositories.maven)

                assertArtifacts(
                    jar("junit:junit:4.12"),
                    pom("junit:junit:4.12"),
                    jar("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-parent:1.3"),
                    actual = artifacts)
            }

            assertEquals(1, children.size)

            with(children[0]) {
                assertEquals("child-a", name)
                assertEquals(root.resolve("child-a").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(
                        listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven
                    )

                    assertArtifacts(
                        jar("com.squareup.okio:okio:2.2.2"),
                        pom("com.squareup.okio:okio:2.2.2"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        jar("org.jetbrains:annotations:13.0"),
                        pom("org.jetbrains:annotations:13.0"),
                        actual = artifacts
                    )
                }
            }
        }
    }

    @Test
    fun `builds single subproject in multi-module project with groovy dsl`() {
        root.resolve("child-a").also { it.mkdirs() }
            .resolve("build.gradle").writeText("""
                plugins {
                    id 'java'
                }

                dependencies {
                    implementation 'com.squareup.okio:okio:2.2.2'
                }
            """.trimIndent())

        root.resolve("child-b").also { it.mkdirs() }
            .resolve("build.gradle").writeText("""
                plugins {
                    id 'java'
                }

                dependencies {
                    implementation 'com.squareup.moshi:moshi:1.8.0'
                }
            """.trimIndent())

        root.resolve("settings.gradle").writeText("""
            include ':child-a', ':child-b'
        """.trimIndent())

        val model = root.buildGroovy("""
            plugins {
                id 'java'
            }

            allprojects {
                repositories {
                    jcenter()
                }
            }

            dependencies {
                testImplementation 'junit:junit:4.12'
            }
        """.trimIndent(),
            subprojects = listOf(":child-a"))

        with(model.rootProject) {
            with(projectDependencies) {
                assertEquals(listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                    repositories.maven)

                assertArtifacts(
                    jar("junit:junit:4.12"),
                    pom("junit:junit:4.12"),
                    jar("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-core:1.3"),
                    pom("org.hamcrest:hamcrest-parent:1.3"),
                    actual = artifacts)
            }

            assertEquals(1, children.size)

            with(children[0]) {
                assertEquals("child-a", name)
                assertEquals(root.resolve("child-a").toRelativeString(root), projectDir)

                with(projectDependencies) {
                    assertEquals(
                        listOf(DefaultMaven(urls = listOf("https://jcenter.bintray.com/"))),
                        repositories.maven
                    )

                    assertArtifacts(
                        jar("com.squareup.okio:okio:2.2.2"),
                        pom("com.squareup.okio:okio:2.2.2"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60"),
                        jar("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        pom("org.jetbrains.kotlin:kotlin-stdlib:1.2.60"),
                        jar("org.jetbrains:annotations:13.0"),
                        pom("org.jetbrains:annotations:13.0"),
                        actual = artifacts
                    )
                }
            }
        }
    }
}

