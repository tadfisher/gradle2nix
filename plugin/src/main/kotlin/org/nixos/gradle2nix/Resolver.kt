package org.nixos.gradle2nix

import okio.ByteString
import okio.HashingSource
import okio.blackholeSink
import okio.buffer
import okio.source
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
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import java.io.File
import java.io.InputStream
import java.net.URI

internal class Resolver(
    private val configurations: ConfigurationContainer,
    private val dependencies: DependencyHandler,
    private val logger: Logger
) {
    private val mavenPomResolver = MavenPomResolver(configurations, dependencies)

    fun resolveDependencies(configuration: Configuration): Set<Artifact> {
        if (!configuration.isCanBeResolved) {
            logger.warn("Cannot resolve configuration ${configuration.name}; ignoring.")
            return emptySet()
        }
        return configuration.resolvedConfiguration.resolvedArtifacts.mapTo(sortedSetOf()) {
            with (it) {
                Artifact(
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
    ): Set<Artifact> {
        val configuration = configurations.detachedConfiguration(*(dependencies.toTypedArray()))
        configuration.isTransitive = includeTransitive
        return resolveDependencies(configuration)
    }

    fun resolvePoms(configuration: Configuration): Set<Artifact> {
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
                sequenceOf(Artifact(
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
    ): Set<Artifact> {
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
    private val resolvedDependencies = mutableSetOf<Artifact>()

    @Synchronized
    fun resolve(pom: File): Set<Artifact> {
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
        resolvedDependencies.add(Artifact(
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

private fun sha256(file: File): String {
    val hashSource = HashingSource.sha256(file.source())
    val hash: ByteString = hashSource.buffer().use { source ->
        source.readAll(blackholeSink())
        hashSource.hash
    }
    return hash.base64()
}
