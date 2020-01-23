package org.nixos.gradle2nix

interface Build {
    val gradle: Gradle
    val pluginDependencies: List<Artifact>
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
    val version: String
    val path: String
    val projectDir: String
    val buildscriptDependencies: List<Artifact>
    val projectDependencies: List<Artifact>
    val children: List<Project>
}

interface Artifact {
    val id: ArtifactIdentifier
    val name: String
    val path: String
    val timestamp: String?
    val build: Int?
    val urls: List<String>
    val sha256: String
}

interface ArtifactIdentifier {
    val group: String
    val name: String
    val version: String
    val type: String
    val extension: String
    val classifier: String?
}