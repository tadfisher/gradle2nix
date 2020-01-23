package org.nixos.gradle2nix

import org.gradle.api.Project

data class ModelProperties(
    val configurations: List<String>,
    val subprojects: List<String>
)

internal fun Project.loadModelProperties(): ModelProperties {
    return ModelProperties(
        configurations = this["org.nixos.gradle2nix.configurations"]?.split(",") ?: emptyList(),
        subprojects = this["org.nixos.gradle2nix.subprojects"]?.split(",") ?: emptyList()
    )
}

private operator fun Project.get(key: String): String? {
    return System.getProperty(key)?.takeIf { it.isNotEmpty() }
        ?: (properties[key] as? String)?.takeIf { it.isNotEmpty() }
}