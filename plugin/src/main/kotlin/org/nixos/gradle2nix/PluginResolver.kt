package org.nixos.gradle2nix

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.plugins.PluginImplementation
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import org.gradle.plugin.use.resolve.internal.ArtifactRepositoriesPluginResolver
import org.gradle.plugin.use.resolve.internal.PluginResolution
import org.gradle.plugin.use.resolve.internal.PluginResolutionResult
import org.gradle.plugin.use.resolve.internal.PluginResolveContext

internal class PluginResolver(
    gradle: GradleInternal,
    private val pluginRequests: Collection<PluginRequest>
) {
    private val pluginDependencyResolutionServices = gradle.serviceOf<PluginDependencyResolutionServices>()
    private val versionSelectorScheme = gradle.serviceOf<VersionSelectorScheme>()

    private val artifactRepositoriesPluginResolver = ArtifactRepositoriesPluginResolver(
        pluginDependencyResolutionServices,
        versionSelectorScheme
    )

    val repositories = pluginDependencyResolutionServices.resolveRepositoryHandler

    private val resolver by lazy {
        DependencyResolver(
            pluginDependencyResolutionServices.configurationContainer,
            pluginDependencyResolutionServices.dependencyHandler
        )
    }

    private val pluginResult by lazy {
        PluginResult().apply {
            for (request in pluginRequests.filterIsInstance<PluginRequestInternal>()) {
                artifactRepositoriesPluginResolver.resolve(request, this)
            }
        }
    }

    private val pluginContext by lazy {
        PluginContext().apply {
            for (result in pluginResult.found) result.execute(this)
        }
    }

    fun artifacts(): List<DefaultArtifact> {
        return (resolver.resolveDependencies(pluginContext.dependencies, true) +
            resolver.resolvePoms(pluginContext.dependencies, true))
            .sorted()
            .distinct()
    }

    private class PluginResult : PluginResolutionResult {
        val found = mutableSetOf<PluginResolution>()

        override fun notFound(sourceDescription: String?, notFoundMessage: String?) {}

        override fun notFound(
            sourceDescription: String?,
            notFoundMessage: String?,
            notFoundDetail: String?
        ) {
        }

        override fun isFound(): Boolean = true

        override fun found(sourceDescription: String, pluginResolution: PluginResolution) {
            found.add(pluginResolution)
        }
    }

    private class PluginContext : PluginResolveContext {
        val dependencies = mutableSetOf<ExternalModuleDependency>()
        val repositories = mutableSetOf<String>()

        override fun add(plugin: PluginImplementation<*>) {
            println("add: $plugin")
        }

        override fun addFromDifferentLoader(plugin: PluginImplementation<*>) {
            println("addFromDifferentLoader: $plugin")
        }

        override fun addLegacy(pluginId: PluginId, m2RepoUrl: String, dependencyNotation: Any) {
            repositories.add(m2RepoUrl)
        }

        override fun addLegacy(pluginId: PluginId, dependencyNotation: Any) {
            if (dependencyNotation is ExternalModuleDependency) {
                dependencies.add(dependencyNotation)
            }
        }
    }
}

