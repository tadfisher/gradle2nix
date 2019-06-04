package org.nixos.gradle2nix

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

fun connect(config: Config): ProjectConnection =
    GradleConnector.newConnector()
        .apply {
            if (config.wrapper) {
                useBuildDistribution()
            } else if (config.gradleVersion != null) {
                useGradleVersion(config.gradleVersion)
            }
        }
        .forProjectDirectory(config.projectDir)
        .connect()

fun ProjectConnection.getBuildModel(config: Config, path: String): DefaultBuild {
    val arguments = mutableListOf(
        "--init-script=$shareDir/init.gradle",
        "-Dorg.nixos.gradle2nix.configurations='${config.configurations.joinToString(",")}'"
    )

    if (path.isNotEmpty()) {
        arguments += "--project-dir=$path"
    }

    return model(Build::class.java)
        .withArguments(arguments)
        .apply {
            if (!config.quiet) {
                setStandardOutput(System.err)
                setStandardError(System.err)
            }
        }
        .get()
        .let { DefaultBuild(it) }
}
