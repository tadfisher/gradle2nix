package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.BINTRAY_JCENTER_URL
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.map
import strikt.assertions.single
import strikt.assertions.startsWith

class SubprojectsTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext<Fixture>("subproject tests") {
        withFixture("subprojects/multi-module") {
            test("builds multi-module project") {
                expectThat(build().rootProject) {
                    get("root project dependencies") { projectDependencies }.and {
                        ids.containsExactly(
                            "junit:junit:4.12@jar",
                            "junit:junit:4.12@pom",
                            "org.hamcrest:hamcrest-core:1.3@jar",
                            "org.hamcrest:hamcrest-core:1.3@pom",
                            "org.hamcrest:hamcrest-parent:1.3@pom"
                        )
                        all {
                            get("urls") { urls }.single().startsWith(BINTRAY_JCENTER_URL)
                        }
                    }

                    get("children") { children }.and {
                        hasSize(2)

                        get(0).and {
                            get("name") { name }.isEqualTo("child-a")
                            get("projectDir") { projectDir }.isEqualTo("child-a")

                            get("child-a project dependencies") { projectDependencies }.and {
                                ids.containsExactly(
                                    "com.squareup.okio:okio:2.2.2@jar",
                                    "com.squareup.okio:okio:2.2.2@pom",
                                    "org.jetbrains:annotations:13.0@jar",
                                    "org.jetbrains:annotations:13.0@pom",
                                    "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@jar",
                                    "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@pom",
                                    "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@jar",
                                    "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@pom"
                                )

                                all {
                                    get("urls") { urls }.single().startsWith(BINTRAY_JCENTER_URL)
                                }
                            }
                        }

                        get(1).and {
                            get("name") { name }.isEqualTo("child-b")
                            get("projectDir") { projectDir }.isEqualTo("child-b")

                            get("child-b project dependencies") { projectDependencies }.and {
                                ids.containsExactly(
                                    "com.squareup.moshi:moshi:1.8.0@jar",
                                    "com.squareup.moshi:moshi:1.8.0@pom",
                                    "com.squareup.moshi:moshi-parent:1.8.0@pom",
                                    "com.squareup.okio:okio:1.16.0@jar",        // compileClasspath
                                    "com.squareup.okio:okio:1.16.0@pom",        // compileClasspath
                                    "com.squareup.okio:okio:2.2.2@jar",         // runtimeClasspath
                                    "com.squareup.okio:okio:2.2.2@pom",         // runtimeClasspath
                                    "com.squareup.okio:okio-parent:1.16.0@pom", // compileClasspath
                                    "org.jetbrains:annotations:13.0@jar",
                                    "org.jetbrains:annotations:13.0@pom",
                                    "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@jar",
                                    "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@pom",
                                    "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@jar",
                                    "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@pom",
                                    "org.sonatype.oss:oss-parent:7@pom"
                                )

                                all {
                                    get("urls") { urls }.single().startsWith(BINTRAY_JCENTER_URL)
                                }
                            }
                        }
                    }
                }
            }

            test("builds single subproject") {
                expectThat(build(subprojects = listOf(":child-a")).rootProject) {
                    get("root project dependencies") { projectDependencies }.and {
                        ids.containsExactly(
                            "junit:junit:4.12@jar",
                            "junit:junit:4.12@pom",
                            "org.hamcrest:hamcrest-core:1.3@jar",
                            "org.hamcrest:hamcrest-core:1.3@pom",
                            "org.hamcrest:hamcrest-parent:1.3@pom"
                        )

                        all {
                            get("urls") { urls }.single().startsWith(BINTRAY_JCENTER_URL)
                        }
                    }

                    get("children") { children }.single().and {
                        get("name") { name }.isEqualTo("child-a")
                        get("projectDir") { projectDir }.isEqualTo("child-a")

                        get("child-a project dependencies") { projectDependencies }.and {
                            ids.containsExactly(
                                "com.squareup.okio:okio:2.2.2@jar",
                                "com.squareup.okio:okio:2.2.2@pom",
                                "org.jetbrains:annotations:13.0@jar",
                                "org.jetbrains:annotations:13.0@pom",
                                "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@jar",
                                "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@pom",
                                "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@jar",
                                "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@pom"
                            )

                            all {
                                get("urls") { urls }.single().startsWith(BINTRAY_JCENTER_URL)
                            }
                        }
                    }
                }
            }
        }

        withFixture("subprojects/dependent-subprojects") {
            test("includes dependent subprojects") {
                expectThat(build(subprojects = listOf(":child-a"))) {
                    get("children") { rootProject.children }
                        .map { it.path }
                        .containsExactlyInAnyOrder(":child-a", ":child-b", ":child-c")
                }

                expectThat(build(subprojects = listOf(":child-b"))) {
                    get("children") { rootProject.children }
                        .map { it.path }
                        .containsExactlyInAnyOrder(":child-b", ":child-c")
                }
            }
        }
    }
}
