package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.given
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import dev.minutest.test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.io.File

class WrapperTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext<File>("wrapper tests") {
        given { createTempDir("gradle2nix") }

        test("resolves gradle wrapper version") {
            expectThat(buildKotlin("""
                tasks.withType<org.gradle.api.tasks.wrapper.Wrapper> {
                    gradleVersion = "5.5.1"
                }
            """.trimIndent())) {
                get("gradle version") { gradle.version }.isEqualTo("5.5.1")
            }
        }
    }
}