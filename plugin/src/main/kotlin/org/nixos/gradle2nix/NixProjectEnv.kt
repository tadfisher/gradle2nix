package org.nixos.gradle2nix

import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.setProperty
import java.net.URI
import javax.inject.Inject

open class NixProjectEnv @Inject constructor(
    layout: ProjectLayout,
    objects: ObjectFactory
) : NixEnv(layout, objects) {
    @Input @Optional
    val configurations = objects.setProperty<String>().empty()


    private val resolveConfigurations by lazy {
        val configs = configurations.get()
        if (configs.isEmpty()) {
            project.configurations.filter { it.isCanBeResolved }
        } else {
            project.configurations.filter { it.name in configs }
        }
    }

    private val resolver by lazy {
        Resolver(project.configurations,
            project.dependencies,
            logger
        )
    }

    override fun environment(): String = "project"

    override fun repositories(): List<String> =
        project.repositories.flatMap { it.repositoryUrls() }.map(URI::toString)

    override fun artifacts(): List<Artifact> {
        return resolveConfigurations
            .flatMap { resolver.resolveDependencies(it) + resolver.resolvePoms(it) }
            .sorted()
            .distinct()
    }

    override fun filename(): String = "project.json"
}
