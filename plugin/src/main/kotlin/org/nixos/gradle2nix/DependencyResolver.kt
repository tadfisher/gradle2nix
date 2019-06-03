package org.nixos.gradle2nix

import org.apache.maven.model.Parent
import org.apache.maven.model.Repository
import org.apache.maven.model.building.DefaultModelBuilderFactory
import org.apache.maven.model.building.DefaultModelBuildingRequest
import org.apache.maven.model.building.ModelBuildingRequest
import org.apache.maven.model.building.ModelSource2
import org.apache.maven.model.resolution.ModelResolver
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File
import java.io.InputStream
import java.net.URI
import java.security.MessageDigest

internal class DependencyResolver(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val logger: Logger = Logging.getLogger(DependencyResolver::class.simpleName)
) {
    private val mavenPomResolver = MavenPomResolver(configurations, dependencies)

    fun resolveDependencies(configuration: Configuration): Set<DefaultArtifact> {
        if (!configuration.isCanBeResolved) {
            logger.warn("Cannot resolve configuration ${configuration.name}; ignoring.")
            return emptySet()
        }
        return configuration.resolvedConfiguration.resolvedArtifacts.mapTo(sortedSetOf()) {
            with (it) {
                DefaultArtifact(
                    groupId = moduleVersion.id.group,
                    artifactId = moduleVersion.id.name,
                    version = moduleVersion.id.version,
                    classifier = classifier ?: "",
                    extension = extension,
                    sha256 = sha256(file)
                )
            }
        }
    }

    fun resolveDependencies(
        dependencies: Collection<Dependency>,
        includeTransitive: Boolean = false
    ): Set<DefaultArtifact> {
        val configuration = configurations.detachedConfiguration(*(dependencies.toTypedArray()))
        configuration.isTransitive = includeTransitive
        return resolveDependencies(configuration)
    }

    fun resolvePoms(configuration: Configuration): Set<DefaultArtifact> {
        return dependencies.createArtifactResolutionQuery()
            .forComponents(configuration.incoming.resolutionResult.allComponents.map { it.id })
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute()
            .resolvedComponents.asSequence()
            .flatMap { component ->
                val id = component.id
                if (id !is ModuleComponentIdentifier) {
                    emptySequence()
                } else {
                    component.getArtifacts(MavenPomArtifact::class.java).asSequence()
                        .filterIsInstance<ResolvedArtifactResult>()
                        .map { id to it }
                }
            }
            .flatMapTo(sortedSetOf()) { (id, artifact) ->
                sequenceOf(DefaultArtifact(
                    groupId = id.group,
                    artifactId = id.module,
                    version = id.version,
                    classifier = "",
                    extension = artifact.file.extension,
                    sha256 = sha256(artifact.file)
                )) + mavenPomResolver.resolve(artifact.file).asSequence()
            }
    }

    fun resolvePoms(
        dependencies: Collection<Dependency>,
        includeTransitive: Boolean = false
    ): Set<DefaultArtifact> {
        val configuration = configurations.detachedConfiguration(*(dependencies.toTypedArray()))
        configuration.isTransitive = includeTransitive
        return resolvePoms(configuration)
    }
}

private class MavenPomResolver(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler
) : ModelResolver {
    private val modelBuilder = DefaultModelBuilderFactory().newInstance()
    private val resolvedDependencies = mutableSetOf<DefaultArtifact>()

    @Synchronized
    fun resolve(pom: File): Set<DefaultArtifact> {
        resolvedDependencies.clear()
        modelBuilder.build(
            DefaultModelBuildingRequest()
                .setModelResolver(this)
                .setPomFile(pom)
                .setSystemProperties(System.getProperties())
                .setValidationLevel(ModelBuildingRequest.VALIDATION_LEVEL_MINIMAL)
        ).effectiveModel
        return resolvedDependencies.toSet()
    }

    override fun newCopy() = this

    override fun resolveModel(
        groupId: String,
        artifactId: String,
        version: String
    ): ModelSource2 {
        val file = configurations
            .detachedConfiguration(dependencies.create("$groupId:$artifactId:$version@pom"))
            .singleFile
        resolvedDependencies.add(DefaultArtifact(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            classifier = "",
            extension = file.extension,
            sha256 = sha256(file)
        ))

        return object : ModelSource2 {
            override fun getLocation(): String = file.absolutePath
            override fun getLocationURI(): URI = file.absoluteFile.toURI()
            override fun getRelatedSource(relPath: String?): ModelSource2? = null
            override fun getInputStream(): InputStream = file.inputStream()
        }
    }

    override fun resolveModel(parent: Parent): ModelSource2 =
        resolveModel(parent.groupId, parent.artifactId, parent.version)

    override fun resolveModel(dependency: org.apache.maven.model.Dependency): ModelSource2 =
        resolveModel(dependency.groupId, dependency.artifactId, dependency.version)

    override fun addRepository(repository: Repository) {}

    override fun addRepository(repository: Repository, replace: Boolean) {}
}

private val HEX = "0123456789ABCDEF"

private fun sha256(file: File): String = buildString {
    MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        .asSequence()
        .map { it.toInt() }
        .forEach {
            append(HEX[it shr 4 and 0x0f])
            append(HEX[it and 0x0f])
        }
}
