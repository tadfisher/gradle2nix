package org.nixos.gradle2nix

import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import javax.inject.Inject

internal open class PluginResolver @Inject constructor(
    pluginDependencyResolutionServices: PluginDependencyResolutionServices
) {
    private val configurations = pluginDependencyResolutionServices.configurationContainer

    private val resolver = ConfigurationResolverFactory(
        pluginDependencyResolutionServices.resolveRepositoryHandler
    ).create(pluginDependencyResolutionServices.dependencyHandler)

    fun resolve(pluginRequests: List<PluginRequest>): List<DefaultArtifact> {
        val markerDependencies = pluginRequests.map {
            it.module?.let { selector ->
                DefaultExternalModuleDependency(selector.group, selector.name, selector.version)
            } ?: it.id.run {
                DefaultExternalModuleDependency(id, "$id.gradle.plugin", it.version)
            }
        }
        return resolver.resolve(configurations.detachedConfiguration(*markerDependencies.toTypedArray()))
    }
}

