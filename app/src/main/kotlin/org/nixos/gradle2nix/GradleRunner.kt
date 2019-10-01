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
        "--init-script=$shareDir/init.gradle"
    )
    arguments += config.args
    if (path.isNotEmpty()) {
        arguments += "--project-dir=$path"
    }
    val jvmArguments = mutableListOf(
        "-Dorg.nixos.gradle2nix.ignoreMavenLocal=${config.ignoreMavenLocal}"
    )
    if (config.configurations.isNotEmpty()) {
        jvmArguments += "-Dorg.nixos.gradle2nix.configurations='${config.configurations.joinToString(",")}'"
    }

    return model(Build::class.java)
        .withArguments(arguments)
        .addJvmArguments(jvmArguments)
        .apply {
            if (!config.quiet) {
                setStandardOutput(System.err)
                setStandardError(System.err)
            }
        }
        .get()
        .let { DefaultBuild(it) }
}
