package org.nixos.gradle2nix

import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection

private val initScript: String = System.getProperty("org.nixos.gradle2nix.initScript")

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
        "--init-script=$initScript",
        "-Dorg.nixos.gradle2nix.configurations='${config.configurations.joinToString(",")}'"
    )

    if (path.isNotEmpty()) {
        arguments += "--project-dir=$path"
    }

    return model(Build::class.java)
        .withArguments(arguments)
        .apply {
            if (config.verbose) {
                setStandardOutput(System.out)
                setStandardError(System.err)
            }
        }
        .get()
        .let { DefaultBuild(it) }
}
