package org.nixos.gradle2nix

import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains

class PluginTest : JUnit5Minutests {
    @Tests
    fun tests() = rootContext<Fixture>("plugin tests") {
        withFixture("plugin/resolves-from-default-repo") {
            test("resolves plugin from default repo") {
                expectThat(build()) {
                    get("plugin dependencies") { pluginDependencies }.ids
                        .contains("org.jetbrains.kotlin.jvm:org.jetbrains.kotlin.jvm.gradle.plugin:1.3.50@pom")
                }
            }
        }
    }
}