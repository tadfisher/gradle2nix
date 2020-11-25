package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NixGradleEnv(
    val name: String,
    val version: String,
    val path: String,
    val gradle: DefaultGradle,
    val dependencies: Map<String, List<DefaultArtifact>>
)

fun buildEnv(builds: Map<String, DefaultBuild>): Map<String, NixGradleEnv> =
    builds.mapValues { (path, build) ->
        NixGradleEnv(
            name = build.rootProject.name,
            version = build.rootProject.version,
            path = path,
            gradle = build.gradle,
            dependencies = mapOf(
                "settings" to build.settingsDependencies,
                "plugin" to build.pluginDependencies,
                "buildscript" to build.rootProject.collectDependencies(DefaultProject::buildscriptDependencies),
                "project" to build.rootProject.collectDependencies(DefaultProject::projectDependencies)
            )
        )
    }

private fun DefaultProject.collectDependencies(
    chooser: DefaultProject.() -> List<DefaultArtifact>
): List<DefaultArtifact> {
    val result = mutableMapOf<ArtifactIdentifier, DefaultArtifact>()
    mergeRepo(result, chooser())
    for (child in children) {
        mergeRepo(result, child.collectDependencies(chooser))
    }
    return result.values.toList()
}

private fun mergeRepo(
    base: MutableMap<ArtifactIdentifier, DefaultArtifact>,
    extra: List<DefaultArtifact>
) {
    extra.forEach { artifact ->
        base.merge(artifact.id, artifact) { old, new ->
            old.copy(urls = old.urls.union(new.urls).toList())
        }
    }
}
