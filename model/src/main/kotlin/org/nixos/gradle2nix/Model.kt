package org.nixos.gradle2nix

interface Build {
    val gradle: Gradle
    val pluginDependencies: Dependencies
    val rootProject: Project
    val includedBuilds: List<IncludedBuild>
}

interface IncludedBuild {
    val name: String
    val projectDir: String
}

interface Gradle {
    val version: String
    val type: String
    val url: String
    val sha256: String
    val nativeVersion: String
}

interface Project {
    val name: String
    val path: String
    val projectDir: String
    val buildscriptDependencies: Dependencies
    val projectDependencies: Dependencies
    val children: List<Project>
}

interface Dependencies {
    val repositories: Repositories
    val artifacts: List<Artifact>
}

interface Repositories {
    val maven: List<Maven>
}

interface Maven {
    val urls: List<String>
}

interface Artifact {
    val groupId: String
    val artifactId: String
    val version: String
    val classifier: String
    val extension: String
    val sha256: String
}
