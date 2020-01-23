package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.containsExactly
import strikt.assertions.filter
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.single
import strikt.assertions.startsWith

class DependencyTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext<Fixture>("dependency tests") {

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

        withFixture("dependency/dynamic-snapshot") {
            test("resolves snapshot dependency with dynamic version") {
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

        withFixture("dependency/snapshot") {
            test("resolves snapshot dependency") {
                expectThat(build()) {
                    get("root project dependencies") { rootProject.projectDependencies }
                        .filter { it.id.name == "okio" }
                        .and {
                            ids.containsExactly(
                                "com.squareup.okio:okio:2.5.0-SNAPSHOT@jar",
                                "com.squareup.okio:okio:2.5.0-SNAPSHOT@module",
                                "com.squareup.okio:okio:2.5.0-SNAPSHOT@pom"
                            )
                            all {
                                get("timestamp") { timestamp }.isNotNull()
                                get("build") { build }.isNotNull()
                                get("urls") { urls }.single().startsWith(SONATYPE_OSS_URL)
                            }
                        }
                }
            }
        }
    }
}