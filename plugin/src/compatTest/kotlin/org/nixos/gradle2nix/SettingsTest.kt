package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.containsExactly

class SettingsTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext("settings tests") {
        withRepository("m2") {
            withFixture("settings/buildscript") {
                test("resolves settings plugin in buildscript classpath") {
                    expectThat(build()) {
                        get("settings dependencies") { settingsDependencies }.ids
                            .containsExactly(
                                "org.apache:test:1.0.0@jar",
                                "org.apache:test:1.0.0@pom"
                            )
                    }
                }
            }
        }
    }
}