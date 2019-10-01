package org.nixos.gradle2nix

import com.github.ajalt.clikt.completion.CompletionCandidates
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.output.CliktHelpFormatter
import com.github.ajalt.clikt.parameters.arguments.ProcessedArgument
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.arguments.default
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okio.buffer
import okio.sink
import java.io.File

val shareDir: String = System.getProperty("org.nixos.gradle2nix.share")

data class Config(
    val wrapper: Boolean,
    val gradleVersion: String?,
    val configurations: List<String>,
    val projectDir: File,
    val includes: List<File>,
    val buildSrc: Boolean,
    val quiet: Boolean,
    val args: List<String>,
    val ignoreMavenLocal: Boolean
) {
    val allProjects = listOf(projectDir) + includes
}

class Main : CliktCommand(
    name = "gradle2nix"
) {
    private val wrapper: Boolean by option("--gradle-wrapper", "-w",
        help = "Use the project's gradle wrapper for building")
        .flag()

    private val gradleVersion: String? by option("--gradle-version", "-g",
        metavar = "VERSION",
        help = "Use a specific Gradle version")

    private val configurations: List<String> by option("--configuration", "-c",
        metavar = "NAME",
        help = "Add a configuration to resolve (default: all configurations)")
        .multiple()

    private val includes: List<File> by option("--include", "-i",
        metavar = "DIR",
        help = "Add an additional project to include")
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

    private val outDir: File? by option("--out-dir", "-o",
        metavar = "DIR",
        help = "Path to write generated files (default: PROJECT-DIR)")
        .file(fileOkay = false, folderOkay = true)

    private val envFile: String by option("--env", "-e",
        metavar = "FILENAME",
        help = "Prefix for environment files (.json and .nix)")
        .default("gradle-env")

    private val buildSrc: Boolean by option("--build-src", "-b", help = "Include buildSrc project (default: true)")
        .flag("--no-build-src", "-nb", default = true)

    private val quiet: Boolean by option("--quiet", "-q", help = "Disable logging")
        .flag(default = false)

    private val projectDir: File by argument("PROJECT-DIR", help = "Path to the project root (default: .)")
        .projectDir()
        .default(File("."))

    private val args: List<String> by option("--arg", "-a",
            metavar = "ARGUMENT",
            help = "Add an extra argument to the build.gradle")
            .multiple()

    private val ignoreMavenLocal: Boolean by option("--ignore-maven-local",
            help = "Don't include mavenLocal() repositories")
            .flag()

    init {
        context {
            helpFormatter = CliktHelpFormatter(showDefaultValues = true)
        }
    }

    override fun run() {
        val config = Config(wrapper, gradleVersion, configurations, projectDir, includes, buildSrc, quiet, args, ignoreMavenLocal)
        val (log, _, _) = Logger(verbose = !config.quiet)

        val paths = resolveProjects(config).map { p ->
            p.toRelativeString(config.projectDir)
        }

        val models = connect(config).use { connection ->
            paths.associate { project ->
                log("Resolving project model: ${project.takeIf { it.isNotEmpty() } ?: "root project"}...")
                project to connection.getBuildModel(config, project)
            }
        }

        log("Building environment...")
        val nixGradleEnv = buildEnv(models)

        val outDir = outDir ?: projectDir

        val json = outDir.resolve("$envFile.json")
        log("Writing environment to $json")

        json.sink().buffer().use { out ->
            Moshi.Builder().build()
                .adapter<Map<String, NixGradleEnv>>(
                    Types.newParameterizedType(Map::class.java, String::class.java, NixGradleEnv::class.java)
                )
                .indent("  ")
                .toJson(out, nixGradleEnv)
            out.flush()
        }

        val nix = outDir.resolve("$envFile.nix")
        log("Writing Nix script to $nix")

        File(shareDir).resolve("gradle-env.nix").copyTo(nix, overwrite = true)
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

