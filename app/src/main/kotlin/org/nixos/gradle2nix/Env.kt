package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NixGradleEnv(
    val project: String,
    val pluginRepo: List<Dependency>,
    val projectRepos: Map<String, List<Dependency>>
)

@JsonClass(generateAdapter = true)
data class Dependency(
    val name: String,
    val filename: String,
    val path: String,
    val urls: List<String>,
    val sha256: String
)

fun buildEnv(builds: Map<String, DefaultBuild>): Map<String, NixGradleEnv> =
    builds.mapValues { (path, build) ->
        NixGradleEnv(
            project = path,
            pluginRepo = buildRepo(build.pluginDependencies).values.toList(),
            projectRepos = mapOf(
                "buildscript" to build.rootProject.collectDependencies(DefaultProject::buildscriptDependencies).values.toList(),
                "project" to build.rootProject.collectDependencies(DefaultProject::projectDependencies).values.toList()
            ))
    }

private fun DefaultProject.collectDependencies(chooser: DefaultProject.() -> DefaultDependencies): Map<DefaultArtifact, Dependency> {
    val result = mutableMapOf<DefaultArtifact, Dependency>()
    mergeRepo(result, buildRepo(chooser()))
    for (child in children) {
        mergeRepo(result, child.collectDependencies(chooser))
    }
    return result
}

private fun buildRepo(deps: DefaultDependencies): Map<DefaultArtifact, Dependency> =
    deps.artifacts.associate { artifact ->
        val name = with(artifact) {
            buildString {
                append("$groupId-$artifactId-$version")
                if (classifier.isNotEmpty()) append("-$classifier")
                append("-$extension")
                replace(Regex("[^A-Za-z0-9+\\-._?=]"), "_")
            }
        }
        val filename = with(artifact) {
            buildString {
                append("$artifactId-$version")
                if (classifier.isNotEmpty()) append("-$classifier")
                append(".$extension")
            }
        }
        val path = with(artifact) { "${groupId.replace(".", "/")}/$artifactId/$version" }
        val dep = Dependency(
            name = name,
            filename = filename,
            path = path,
            urls = deps.repositories.maven.flatMap { repo ->
                repo.urls.map { "${it.removeSuffix("/")}/$path/$filename"  }
            },
            sha256 = artifact.sha256
        )
        artifact to dep
    }

private fun mergeRepo(base: MutableMap<DefaultArtifact, Dependency>, extra: Map<DefaultArtifact, Dependency>) {
    extra.forEach { (artifact, dep) ->
        base.merge(artifact, dep) { old, new ->
            old.copy(urls = old.urls + (new.urls - old.urls))
        }
    }
}
