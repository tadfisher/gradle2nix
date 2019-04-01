package org.nixos.gradle2nix

import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.configure
import java.io.File

open class NixBuildSrcEnv : GradleBuild() {
    init {
        configure {
            tasks = listOf("nixGradleEnv")
            startParameter.addInitScript(writeInitScriptTo(dir.resolve("build/nix/init.gradle")))
        }
    }
}

private fun writeInitScriptTo(dest: File): File {
    dest.parentFile.mkdirs()
    dest.writeText("""
        initscript {
            dependencies {
                classpath files("$pluginJar")
            }
        }

        apply plugin: org.nixos.gradle2nix.Gradle2NixPlugin
    """.trimIndent())
    return dest
}
