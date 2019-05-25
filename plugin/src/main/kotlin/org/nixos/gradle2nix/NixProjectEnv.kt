package org.nixos.gradle2nix

import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.listProperty
import java.net.URI

open class NixProjectEnv : NixEnv() {
    @Input @Optional
    val configurations = project.objects.listProperty<String>().conventionCompat(emptyList())

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
