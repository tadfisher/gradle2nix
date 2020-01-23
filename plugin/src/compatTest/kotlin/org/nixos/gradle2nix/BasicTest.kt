package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.BINTRAY_JCENTER_URL
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler.MAVEN_CENTRAL_URL
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.map
import strikt.assertions.startsWith

class BasicTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext<Fixture>("basic tests") {
        withFixture("basic/basic-java-project") {
            test("builds basic java project") {
                expectThat(build()) {
                    get("gradle version") { gradle.version }.isEqualTo(System.getProperty("compat.gradle.version"))

                    get("root project dependencies") { rootProject.projectDependencies }.and {
                        ids.containsExactly(
                            "com.squareup.moshi:moshi:1.8.0@jar",
                            "com.squareup.moshi:moshi:1.8.0@pom",
                            "com.squareup.moshi:moshi-parent:1.8.0@pom",
                            "com.squareup.okio:okio:2.2.2@jar",
                            "com.squareup.okio:okio:2.2.2@pom",
                            "org.jetbrains:annotations:13.0@jar",
                            "org.jetbrains:annotations:13.0@pom",
                            "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@jar",
                            "org.jetbrains.kotlin:kotlin-stdlib:1.2.60@pom",
                            "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@jar",
                            "org.jetbrains.kotlin:kotlin-stdlib-common:1.2.60@pom",
                            "org.sonatype.oss:oss-parent:7@pom"
                        )

                        map { it.urls }.all {
                            hasSize(2)
                            get(0).startsWith(BINTRAY_JCENTER_URL)
                            get(1).startsWith(MAVEN_CENTRAL_URL)
                        }
                    }
                }
            }
        }
    }
}
