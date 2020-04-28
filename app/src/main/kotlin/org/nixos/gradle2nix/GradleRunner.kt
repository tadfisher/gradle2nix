package org.nixos.gradle2nix

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

fun connect(config: Config): ProjectConnection =
    GradleConnector.newConnector()
        .apply {
            if (config.gradleVersion != null) {
                useGradleVersion(config.gradleVersion)
            }
        }
        .forProjectDirectory(config.projectDir)
        .connect()

@Suppress("UnstableApiUsage")
fun ProjectConnection.getBuildModel(config: Config, path: String): DefaultBuild {
    return model(Build::class.java).apply {
        addArguments(
            "--init-script=$shareDir/init.gradle",
            "-Porg.nixos.gradle2nix.configurations=${config.configurations.joinToString(",")}",
            "-Porg.nixos.gradle2nix.subprojects=${config.subprojects.joinToString(",")}"
        )
        if (config.gradleArgs != null) addArguments(config.gradleArgs)
        if (path.isNotEmpty()) addArguments("--project-dir=$path")
        if (!config.quiet) {
            setStandardOutput(System.err)
            setStandardError(System.err)
        }
    }.get().let { DefaultBuild(it) }
}
