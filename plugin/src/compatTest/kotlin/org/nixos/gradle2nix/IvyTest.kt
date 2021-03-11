package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import dev.minutest.test
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.containsExactly
import strikt.assertions.map
import strikt.assertions.single
import strikt.assertions.startsWith

class IvyTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext("ivy tests") {
        withFixture("ivy/basic") {
            test("resolves ivy dependencies") {
                expectThat(build()) {
                    get("root project dependencies") { rootProject.projectDependencies }.and {
                        ids.containsExactly(
                            "org.opendof.core-java:dof-cipher-sms4:1.0@jar",
                            "org.opendof.core-java:dof-oal:7.0.2@jar"
                        )

                        map { it.urls }.all {
                            single().startsWith("https://asset.opendof.org/artifact")
                        }
                    }
                }
            }
        }
    }
}