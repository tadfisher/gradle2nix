package org.nixos.gradle2nix

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.defaultLazy
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import java.io.File

class Main : CliktCommand() {
    val wrapper: Boolean by option(help = "Use the project's gradle wrapper for building").flag()
    val gradleVersion: String? by option(help = "Use a specific Gradle version")
    val configurations: List<String> by option(help = "Project configuration(s)").multiple()
    val projectDir: File by argument(help = "Path to the project root")
        .file(exists = true, fileOkay = false, folderOkay = true, readable = true)
        .defaultLazy { File(".") }

    override fun run() {
        GradleRunner(projectDir, wrapper, gradleVersion, configurations).runGradle()
    }
}

fun main(args: Array<String>) = Main().main(args)