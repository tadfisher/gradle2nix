package org.nixos.gradle2nix

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.repositories.ResolutionAwareRepository
import org.gradle.plugin.management.PluginRequest
import org.gradle.plugin.use.internal.PluginDependencyResolutionServices
import javax.inject.Inject

internal open class PluginResolver @Inject constructor(
    project: Project,
    pluginDependencyResolutionServices: PluginDependencyResolutionServices
) {
    private val configurations = pluginDependencyResolutionServices.configurationContainer

    private val resolver = ConfigurationResolverFactory(
        project,
        ConfigurationScope.PLUGIN,
        pluginDependencyResolutionServices.resolveRepositoryHandler.filterIsInstance<ResolutionAwareRepository>()
    ).create(pluginDependencyResolutionServices.dependencyHandler)

    fun resolve(pluginRequests: List<PluginRequest>): List<DefaultArtifact> {
        val markerDependencies = pluginRequests.map { request ->
            request.module?.let { module ->
                ApiHack.defaultExternalModuleDependency(module.group, module.name, module.version)
            } ?: request.id.id.let { id ->
                ApiHack.defaultExternalModuleDependency(id, "$id.gradle.plugin", request.version)
            }
        }
        return resolver.resolve(configurations.detachedConfiguration(*markerDependencies.toTypedArray()))
    }
}
