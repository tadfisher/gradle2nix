package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import dev.minutest.test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.filter
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull

class DependencyTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext("dependency tests") {

        withRepository("m2") {

            withFixture("dependency/classifier") {
                test("resolves dependency with classifier") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }.ids.containsExactly(
                            "com.badlogicgames.gdx:gdx-parent:1.9.9@pom",
                            "com.badlogicgames.gdx:gdx-platform:1.9.9:natives-desktop@jar",
                            "com.badlogicgames.gdx:gdx-platform:1.9.9@pom",
                            "org.sonatype.oss:oss-parent:5@pom"
                        )
                    }
                }
            }

            withFixture("dependency/maven-bom") {
                GRADLE_MIN("5.0") - test("resolves dependencies from maven bom platform") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }
                            .ids
                            .containsExactly(
                                "io.micrometer:micrometer-bom:1.5.1@pom",
                                "io.micrometer:micrometer-core:1.5.1@jar",
                                "io.micrometer:micrometer-core:1.5.1@pom",
                                "org.hdrhistogram:HdrHistogram:2.1.12@jar",
                                "org.hdrhistogram:HdrHistogram:2.1.12@pom"
                            )
                    }
                }
            }

            withFixture("dependency/snapshot") {
                test("resolves snapshot dependency") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }
                            .and {
                                ids.containsExactly(
                                    "org.apache:test-SNAPSHOT2:2.0.2-SNAPSHOT@jar",
                                    "org.apache:test-SNAPSHOT2:2.0.2-SNAPSHOT@pom"
                                )
                                all {
                                    get("timestamp") { timestamp }.isNull()
                                    get("build") { build }.isNotNull()
                                }
                            }
                    }
                }
            }

            withFixture("dependency/snapshot-dynamic") {
                test("resolves snapshot dependency with dynamic version") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }
                            .and {
                                ids.containsExactly(
                                    "org.apache:test-SNAPSHOT1:2.0.2-SNAPSHOT@jar",
                                    "org.apache:test-SNAPSHOT1:2.0.2-SNAPSHOT@pom"
                                )
                                all {
                                    get("timestamp") { timestamp }.isEqualTo("20070310.181613")
                                    get("build") { build }.isEqualTo(3)
                                }
                            }
                    }
                }
            }

            withFixture("dependency/snapshot-redirect") {
                test("resolves snapshot dependency with redirect") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }
                            .filter { it.id.name == "packr" }
                            .all {
                                get("id.version") { id.version }.isEqualTo("-SNAPSHOT")
                                get("timestamp") { timestamp }.isNotNull()
                                get("build") { build }.isNotNull()
                            }
                    }
                }
            }
        }
    }
}