package org.nixos.gradle2nix

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.management.PluginRequest
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GradleVersion
import java.net.URL
import java.util.*
import org.nixos.gradle2nix.Gradle as NixGradle
import org.nixos.gradle2nix.Project as NixProject

@Suppress("unused")
open class Gradle2NixPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle) {
        val configurationNames: List<String> =
            System.getProperty("org.nixos.gradle2nix.configurations")?.split(",") ?: emptyList()

        val pluginRequests = collectPlugins(gradle)

        gradle.projectsLoaded {
            rootProject.serviceOf<ToolingModelBuilderRegistry>()
                .register(NixToolingModelBuilder(configurationNames, pluginRequests))
        }
    }

    private fun collectPlugins(gradle: Gradle): List<PluginRequest> {
        val pluginRequests = mutableListOf<PluginRequest>()
        gradle.settingsEvaluated {
            pluginManagement.resolutionStrategy.eachPlugin {
                if (requested.id.namespace != null && requested.id.namespace != "org.gradle") {
                    pluginRequests.add(target)
                }
            }
        }
        return pluginRequests
    }
}

private class NixToolingModelBuilder(
    private val explicitConfigurations: List<String>,
    private val pluginRequests: List<PluginRequest>
) : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return modelName == "org.nixos.gradle2nix.Build"
    }

    override fun buildAll(modelName: String, project: Project): Build = project.run {
        val plugins = buildPlugins(pluginRequests)
        DefaultBuild(
            gradle = buildGradle(),
            pluginDependencies = plugins,
            rootProject = buildProject(explicitConfigurations, plugins),
            includedBuilds = includedBuilds()
        )
    }
}

private fun Project.buildGradle(): DefaultGradle =
    with(tasks.getByName<Wrapper>("wrapper")) {
        DefaultGradle(
            version = gradleVersion,
            type = distributionType.name.toLowerCase(Locale.US),
            url = distributionUrl,
            sha256 = sha256,
            nativeVersion = gradle.gradleHomeDir?.resolve("lib")?.listFiles()
                ?.firstOrNull { f -> nativePlatformJarRegex matches f.name }?.let { nf ->
                    nativePlatformJarRegex.find(nf.name)?.groupValues?.get(1)
                }
                ?: throw IllegalStateException(
                    """
                            Failed to find native-platform jar in ${gradle.gradleHomeDir}.

                            Ask Tad to fix this.
                        """.trimIndent()
                )
        )
    }

private fun Project.buildPlugins(pluginRequests: List<PluginRequest>): DefaultDependencies =
    with(PluginResolver(gradle as GradleInternal, pluginRequests)) {
        DefaultDependencies(repositories.repositories(), artifacts())
    }

private fun Project.includedBuilds(): List<DefaultIncludedBuild> =
    gradle.includedBuilds.map {
        DefaultIncludedBuild(it.name, it.projectDir.toRelativeString(project.projectDir))
    }

private fun Project.buildProject(
    explicitConfigurations: List<String>,
    plugins: DefaultDependencies
): DefaultProject =
    DefaultProject(
        name = name,
        version = version.toString(),
        path = path,
        projectDir = projectDir.toRelativeString(rootProject.projectDir),
        buildscriptDependencies = buildscriptDependencies(plugins),
        projectDependencies = projectDependencies(explicitConfigurations),
        children = childProjects.values.map { it.buildProject(explicitConfigurations, plugins) }
    )

private fun Project.buildscriptDependencies(plugins: DefaultDependencies): DefaultDependencies =
    with(DependencyResolver(buildscript.configurations, buildscript.dependencies)) {
        DefaultDependencies(
            repositories = buildscript.repositories.repositories(),
            artifacts = buildscript.configurations
                .filter { it.isCanBeResolved }
                .flatMap { resolveDependencies(it) + resolvePoms(it) }
                .minus(plugins.artifacts)
                .distinct()
        )
    }

private fun Project.projectDependencies(explicitConfigurations: List<String>): DefaultDependencies =
    with(DependencyResolver(configurations, dependencies)) {
        val toResolve = if (explicitConfigurations.isEmpty()) {
            configurations.filter { it.isCanBeResolved }
        } else {
            configurations.filter { it.name in explicitConfigurations }
        }

        DefaultDependencies(
            repositories = repositories.repositories(),
            artifacts = toResolve.flatMap { resolveDependencies(it) + resolvePoms(it) }
                .sorted()
                .distinct()
        )
    }

private fun fetchDistSha256(url: String): String {
    return URL("$url.sha256").openConnection().run {
        connect()
        getInputStream().reader().use { it.readText() }
    }
}

private val nativePlatformJarRegex = Regex("""native-platform-([\d.]+)\.jar""")

internal fun RepositoryHandler.repositories() = DefaultRepositories(
    maven = filterIsInstance<MavenArtifactRepository>()
        .filterNot { it.name == "Embedded Kotlin Repository" }
        .map { repo ->
            DefaultMaven(listOf(repo.url.toString()) + repo.artifactUrls.map { it.toString() })
        }
)

private val Wrapper.sha256: String
    get() {
        return if (GradleVersion.current() < GradleVersion.version("4.5")) {
            fetchDistSha256(distributionUrl)
        } else {
            distributionSha256Sum ?: fetchDistSha256(distributionUrl)
        }
    }
