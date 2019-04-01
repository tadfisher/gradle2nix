package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass
import okio.buffer
import okio.sink
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.repositories.ArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.net.URI
import javax.inject.Inject

abstract class NixEnv(layout: ProjectLayout, objects: ObjectFactory): DefaultTask() {
    abstract fun environment(): String
    abstract fun repositories(): List<String>
    abstract fun artifacts(): List<Artifact>
    abstract fun filename(): String

    @Internal
    val outputDir = objects.directoryProperty()
        .conventionCompat(layout.buildDirectory.dir("nix"))

    @OutputFile
    val outputFile = objects.fileProperty()
        .conventionCompat(outputDir.map { it.file(filename()) })

    @TaskAction
    open fun run() {
        val outFile = outputFile.get().asFile
        outFile.parentFile.mkdirs()

        val buildEnv = BuildEnv(project.path, environment(), repositories(), artifacts())
        outFile.sink().buffer().use { out ->
            moshi.adapter(BuildEnv::class.java)
                .indent("  ")
                .toJson(out, buildEnv)
        }
    }
}

@JsonClass(generateAdapter = true)
data class BuildEnv(
    val path: String,
    val env: String,
    val repositories: List<String>,
    val artifacts: List<Artifact>
)

@JsonClass(generateAdapter = true)
data class Artifact(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val classifier: String,
    val extension: String,
    val sha256: String
) : Comparable<Artifact> {
    override fun toString() = "$groupId:$artifactId:$version:$classifier:$extension"

    override fun compareTo(other: Artifact): Int {
        return toString().compareTo(other.toString())
    }
}

internal fun ArtifactRepository.repositoryUrls(): Set<URI> {
    return when (this) {
        is MavenArtifactRepository -> setOf(url) + artifactUrls
        else -> emptySet()
    }.filterNotTo(mutableSetOf()) { it.scheme == "file" }
}
