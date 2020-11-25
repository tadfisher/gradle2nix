package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass
import net.swiftzer.semver.SemVer
import java.io.Serializable
import java.lang.IllegalArgumentException

@JsonClass(generateAdapter = true)
data class DefaultBuild(
    override val gradle: DefaultGradle,
    override val settingsDependencies: List<DefaultArtifact>,
    override val pluginDependencies: List<DefaultArtifact>,
    override val rootProject: DefaultProject,
    override val includedBuilds: List<DefaultIncludedBuild>
) : Build, Serializable {
    constructor(model: Build) : this(
        DefaultGradle(model.gradle),
        model.settingsDependencies.map(::DefaultArtifact),
        model.pluginDependencies.map(::DefaultArtifact),
        DefaultProject(model.rootProject),
        model.includedBuilds.map(::DefaultIncludedBuild)
    )
}

@JsonClass(generateAdapter = true)
data class DefaultIncludedBuild(
    override val name: String,
    override val projectDir: String
) : IncludedBuild, Serializable {
    constructor(model: IncludedBuild) : this(
        model.name,
        model.projectDir
    )
}

@JsonClass(generateAdapter = true)
data class DefaultGradle(
    override val version: String,
    override val type: String,
    override val url: String,
    override val sha256: String,
    override val nativeVersion: String
) : Gradle, Serializable {
    constructor(model: Gradle) : this(
        model.version,
        model.type,
        model.url,
        model.sha256,
        model.nativeVersion
    )
}

@JsonClass(generateAdapter = true)
data class DefaultProject(
    override val name: String,
    override val version: String,
    override val path: String,
    override val projectDir: String,
    override val buildscriptDependencies: List<DefaultArtifact>,
    override val projectDependencies: List<DefaultArtifact>,
    override val children: List<DefaultProject>
) : Project, Serializable {
    constructor(model: Project) : this(
        model.name,
        model.version,
        model.path,
        model.projectDir,
        model.buildscriptDependencies.map(::DefaultArtifact),
        model.projectDependencies.map(::DefaultArtifact),
        model.children.map { DefaultProject(it) }
    )
}

@JsonClass(generateAdapter = true)
data class DefaultArtifact(
    override val id: DefaultArtifactIdentifier,
    override val name: String,
    override val path: String,
    override val timestamp: String? = null,
    override val build: Int? = null,
    override val urls: List<String>,
    override val sha256: String
) : Artifact, Comparable<DefaultArtifact>, Serializable {
    constructor(model: Artifact) : this(
        DefaultArtifactIdentifier(model.id),
        model.name,
        model.path,
        model.timestamp,
        model.build,
        model.urls,
        model.sha256
    )

    override fun toString() = id.toString()
    override fun compareTo(other: DefaultArtifact): Int = id.compareTo(other.id)
}

@JsonClass(generateAdapter = true)
data class DefaultArtifactIdentifier(
    override val group: String,
    override val name: String,
    override val version: String,
    override val type: String,
    override val extension: String = type,
    override val classifier: String? = null
) : ArtifactIdentifier, Comparable<DefaultArtifactIdentifier>, Serializable {
    constructor(model: ArtifactIdentifier) : this(
        model.group,
        model.name,
        model.version,
        model.type,
        model.extension,
        model.classifier
    )

    @delegate:Transient
    private val semver: SemVer? by lazy {
        try {
            SemVer.parse(version)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    override fun compareTo(other: DefaultArtifactIdentifier): Int {
        return group.compareTo(other.group).takeUnless { it == 0 }
            ?: name.compareTo(other.name).takeUnless { it == 0 }
            ?: other.semver?.let { semver?.compareTo(it) }?.takeUnless { it == 0 }
            ?: type.compareTo(other.type).takeUnless { it == 0 }
            ?: other.classifier?.let { classifier?.compareTo(it) }?.takeUnless { it == 0 }
            ?: 0
    }

    override fun toString(): String = buildString {
        append("$group:$name:$version")
        if (classifier != null) append(":$classifier")
        append("@$type")
    }
}