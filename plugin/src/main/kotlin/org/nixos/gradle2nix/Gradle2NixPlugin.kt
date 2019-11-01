package org.nixos.gradle2nix

import com.squareup.moshi.Moshi
import okio.buffer
import okio.sink
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.internal.GradleInternal
import org.gradle.api.invocation.Gradle
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.kotlin.dsl.withType
import org.gradle.plugin.management.PluginRequest
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.gradle.util.GradleVersion
import java.net.URL
import java.util.Locale

@Suppress("unused")
open class Gradle2NixPlugin : Plugin<Gradle> {
    override fun apply(gradle: Gradle): Unit = gradle.run {
        val pluginRequests = collectPlugins()

        projectsLoaded {
            val modelProperties = rootProject.loadModelProperties()
            rootProject.serviceOf<ToolingModelBuilderRegistry>()
                .register(NixToolingModelBuilder(modelProperties, pluginRequests))

            rootProject.tasks.register("nixModel") {
                doLast {
                    val outFile = project.mkdir(project.buildDir.resolve("nix")).resolve("model.json")
                    val model = project.buildModel(modelProperties, pluginRequests)
                    outFile.sink().buffer().use { out ->
                        Moshi.Builder().build()
                            .adapter(DefaultBuild::class.java)
                            .indent("  ")
                            .toJson(out, model)
                        out.flush()
                    }
                }
            }
        }
    }
}

private const val NIX_MODEL_NAME = "org.nixos.gradle2nix.Build"

private class NixToolingModelBuilder(
    private val modelProperties: ModelProperties,
    private val pluginRequests: List<PluginRequest>
) : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean {
        return modelName == NIX_MODEL_NAME
    }

    override fun buildAll(modelName: String, project: Project): Build =
        project.buildModel(modelProperties, pluginRequests)
}

private fun Gradle.collectPlugins(): List<PluginRequest> {
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

private fun Project.buildModel(
    modelProperties: ModelProperties,
    pluginRequests: List<PluginRequest>
): DefaultBuild {
    val plugins = buildPlugins(pluginRequests)

    val subprojects = if (modelProperties.subprojects.isEmpty()) {
        project.subprojects
    } else {
        project.subprojects
            .filter { it.path in modelProperties.subprojects }
            // Include dependent subprojects as well
            .flatMap { setOf(it) + it.dependentSubprojects(modelProperties.configurations) }
            .toSet()
    }

    return DefaultBuild(
        gradle = buildGradle(),
        pluginDependencies = plugins,
        rootProject = buildProject(modelProperties.configurations, subprojects, plugins),
        includedBuilds = includedBuilds()
    )
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
    explicitSubprojects: Collection<Project>,
    plugins: DefaultDependencies
): DefaultProject {
    logger.lifecycle("    Subproject: $path")
    return DefaultProject(
        name = name,
        version = version.toString(),
        path = path,
        projectDir = projectDir.toRelativeString(rootProject.projectDir),
        buildscriptDependencies = buildscriptDependencies(plugins),
        projectDependencies = projectDependencies(explicitConfigurations),
        children = explicitSubprojects.map { it.buildProject(explicitConfigurations, emptyList(), plugins) }
    )
}

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
        val toResolve = collectConfigurations(explicitConfigurations)
        DefaultDependencies(
            repositories = repositories.repositories(),
            artifacts = toResolve.flatMap { resolveDependencies(it) + resolvePoms(it) }
                .sorted()
                .distinct()
        )
    }

private fun Project.dependentSubprojects(explicitConfigurations: List<String>): Set<Project> {
    return collectConfigurations(explicitConfigurations)
        .flatMap { it.allDependencies.withType<ProjectDependency>() }
        .map { it.dependencyProject }
        .toSet()
        .flatMap { setOf(it) + it.dependentSubprojects(explicitConfigurations) }
        .toSet()
}

private fun Project.collectConfigurations(
    explicitConfigurations: List<String>
): Set<Configuration> {
    return if (explicitConfigurations.isEmpty()) {
        configurations.filter { it.isCanBeResolved }.toSet()
    } else {
        configurations.filter { it.name in explicitConfigurations }.toSet()
    }
}

private val excludedRepoNames = setOf(
    "Embedded Kotlin Repository",
    ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME
)

internal fun RepositoryHandler.repositories() = DefaultRepositories(
    maven = filterIsInstance<MavenArtifactRepository>()
        .filter { it.name !in excludedRepoNames }
        .map { repo ->
            DefaultMaven(listOf(repo.url.toString()) + repo.artifactUrls.map { it.toString() })
        }
)

private fun fetchDistSha256(url: String): String {
    return URL("$url.sha256").openConnection().run {
        connect()
        getInputStream().reader().use { it.readText() }
    }
}

private val nativePlatformJarRegex = Regex("""native-platform-([\d.]+)\.jar""")

private val Wrapper.sha256: String
    get() {
        return if (GradleVersion.current() < GradleVersion.version("4.5")) {
            fetchDistSha256(distributionUrl)
        } else {
            distributionSha256Sum ?: fetchDistSha256(distributionUrl)
        }
    }
