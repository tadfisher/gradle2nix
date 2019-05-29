package org.nixos.gradle2nix

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.moshi.Moshi
import okio.buffer
import okio.sink
import java.io.File

data class Config(
    val wrapper: Boolean,
    val gradleVersion: String?,
    val configurations: List<String>,
    val projectDir: File,
    val includes: List<File>,
    val buildSrc: Boolean,
    val verbose: Boolean
) {
    val allProjects = listOf(projectDir) + includes
}

class Main : CliktCommand() {
    val wrapper: Boolean by option("--gradle-wrapper", "-w",
        help = "Use the project's gradle wrapper for building")
        .flag()

    val gradleVersion: String? by option("--gradle-version", "-g",
        help = "Use a specific Gradle version")

    val configurations: List<String> by option(help = "Project configuration(s)").multiple()

    val projectDir: File by argument(help = "Path to the project root")
        .projectDir()
        .default(File("."))

    val outputDir: File by option("--out", "-o",
        help = "Path to write Nix environment files")
        .file(fileOkay = false, folderOkay = true)
        .default(File("."))

    val includes: List<File> by option("--include", "-i",
        help = "Path to included build(s)",
        metavar = "DIR")
        .file(exists = true, fileOkay = false, folderOkay = true, readable = true)
        .multiple()
        .validate { files ->
            val failures = files.filterNot { it.isProjectRoot() }
            if (failures.isNotEmpty()) {
                val message = failures.joinToString("\n    ")
                fail("Included builds are not Gradle projects:\n$message\n" +
                        "Gradle projects must contain a settings.gradle or settings.gradle.kts script.")
            }
        }

    val buildSrc: Boolean by option("--enableBuildSrc", help = "Include buildSrc project")
        .flag("--disableBuildSrc", default = true)

    val verbose: Boolean by option("--verbose", "-v", help = "Enable verbose logging")
        .flag(default = false)

    override fun run() {
        val config = Config(wrapper, gradleVersion, configurations, projectDir, includes, buildSrc, verbose)
        val (log, warn, error) = Logger(verbose = config.verbose)

        val json by lazy { Moshi.Builder().build().adapter(DefaultBuild::class.java).indent("  ") }
        val out by lazy { outputDir.also { it.mkdirs() }}

        val paths = resolveProjects(config).map { p ->
            p.toRelativeString(config.projectDir)
        }

        connect(config).use { connection ->
            for (project in paths) {
                log("Resolving project model: ${project.takeIf { it.isNotEmpty() } ?: "root project"}")
                val build = connection.getBuildModel(config, project)
                val filename = build.rootProject.name + ".json"
                val file = out.resolve(filename)
                file.sink().buffer().use { sink -> json.toJson(sink, build) }
                log("  --> $file")
            }
        }
    }
}

fun ProcessedArgument<String, String>.projectDir(): ProcessedArgument<File, File> {
    return convert(completionCandidates = CompletionCandidates.Path) {
        File(it).also { file ->
            if (!file.exists()) fail("Directory \"$file\" does not exist.")
            if (file.isFile) fail("Directory \"$file\" is a file.")
            if (!file.canRead()) fail("Directory \"$file\" is not readable.")
            if (!file.isProjectRoot()) fail("Directory \"$file\" is not a Gradle project.")
        }
    }
}

fun main(args: Array<String>) = Main().main(args)

