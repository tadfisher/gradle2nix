package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import dev.minutest.test
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.flatMap
import strikt.assertions.map

class S3Test : JUnit5Minutests {
    @Tests
    fun tests() = rootContext("s3 tests") {
        withBucket("repositories") {
            withFixture("s3/maven") {

                test("dependency from s3 maven repo") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }.and {
                            ids.containsExactly(
                                "org.apache:test:1.0.0@jar",
                                "org.apache:test:1.0.0@pom"
                            )
                            flatMap { it.urls }.containsExactly(
                                "s3://repositories/m2/org/apache/test/1.0.0/test-1.0.0.jar",
                                "s3://repositories/m2/org/apache/test/1.0.0/test-1.0.0.pom"
                            )
                        }
                    }
                }
            }

            withFixture("s3/maven-snapshot") {
                test("snapshot dependency from s3 maven repo") {
                    expectThat(build()) {
                        get("root project dependencies") { rootProject.projectDependencies }.and {
                            ids.containsExactly(
                                "org.apache:test-SNAPSHOT1:2.0.0-SNAPSHOT@jar",
                                "org.apache:test-SNAPSHOT1:2.0.0-SNAPSHOT@pom"
                            )
                            flatMap { it.urls }.containsExactly(
                                "s3://repositories/m2/org/apache/test-SNAPSHOT1/2.0.0-SNAPSHOT/test-SNAPSHOT1-2.0.0-20070310.181613-3.jar",
                                "s3://repositories/m2/org/apache/test-SNAPSHOT1/2.0.0-SNAPSHOT/test-SNAPSHOT1-2.0.0-20070310.181613-3.pom"
                            )
                        }
                    }
                }
            }
        }
    }
}