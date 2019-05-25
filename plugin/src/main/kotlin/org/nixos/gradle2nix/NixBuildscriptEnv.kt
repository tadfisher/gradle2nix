package org.nixos.gradle2nix

import okio.buffer
import okio.source
import org.gradle.api.tasks.InputFile
import java.net.URI

open class NixBuildscriptEnv : NixEnv() {
    @InputFile
    val pluginEnvFile = project.filePropertyCompat()

    private val pluginEnv: BuildEnv by lazy {
        pluginEnvFile.get().asFile.source().buffer().use { src ->
            moshi.adapter(BuildEnv::class.java).fromJson(src)
                ?: throw IllegalStateException(
                    "Cannot load plugin env from ${pluginEnvFile.get().asFile.path}")
        }
    }

    private val resolver by lazy {
        Resolver(project.buildscript.configurations,
            project.buildscript.dependencies,
            logger
        )
    }

    override fun environment(): String = "buildscript"

    override fun repositories(): List<String> =
        project.buildscript.repositories.flatMap { it.repositoryUrls() }.map(URI::toString)

    override fun artifacts(): List<Artifact> {
        return project.buildscript.configurations
            .filter { it.isCanBeResolved }
            .flatMap { resolver.resolveDependencies(it) + resolver.resolvePoms(it) }
            .minus(pluginEnv.artifacts)
            .sorted()
            .distinct()
    }

    override fun filename(): String = "buildscript.json"
}
