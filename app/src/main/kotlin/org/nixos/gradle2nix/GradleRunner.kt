package org.nixos.gradle2nix

import org.gradle.tooling.GradleConnector
import java.io.File

class GradleRunner(
    private val projectDir: File,
    private val useWrapper: Boolean,
    private val gradleVersion: String?,
    private val configurations: List<String>
) {
    companion object {
        val initScript: String = System.getProperty("org.nixos.gradle2nix.initScript")
    }

    fun runGradle() {
        GradleConnector.newConnector()
            .apply {
                if (useWrapper) {
                    useBuildDistribution()
                } else if (gradleVersion != null) {
                    useGradleVersion(gradleVersion)
                }
            }
            .forProjectDirectory(projectDir)
            .connect()
            .use { connection ->
                connection.newBuild()
                    .withArguments("--init-script", initScript)
                    .apply {
                        if (configurations.isNotEmpty()) {
                            withArguments(
                                "-Dorg.nixos.gradle2nix.configurations=${configurations.joinToString(
                                    ","
                                )}"
                            )
                        }
                    }
                    .forTasks("nixGradleEnv")
                    .setStandardOutput(System.out)
                    .setStandardError(System.err)
                    .run()
            }
    }
}