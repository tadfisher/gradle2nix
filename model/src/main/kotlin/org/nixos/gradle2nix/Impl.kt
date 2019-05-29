package org.nixos.gradle2nix

import com.squareup.moshi.JsonClass
import java.io.Serializable

@JsonClass(generateAdapter = true)
data class DefaultBuild(
    override val gradle: DefaultGradle,
    override val pluginDependencies: DefaultDependencies,
    override val rootProject: DefaultProject,
    override val includedBuilds: List<DefaultIncludedBuild>
) : Build, Serializable {
    constructor(model: Build) : this(
        DefaultGradle(model.gradle),
        DefaultDependencies(model.pluginDependencies),
        DefaultProject(model.rootProject),
        model.includedBuilds.map { DefaultIncludedBuild(it) }
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
    override val path: String,
    override val projectDir: String,
    override val buildscriptDependencies: DefaultDependencies,
    override val projectDependencies: DefaultDependencies,
    override val children: List<DefaultProject>
) : Project, Serializable {
    constructor(model: Project) : this(
        model.name,
        model.path,
        model.projectDir,
        DefaultDependencies(model.buildscriptDependencies),
        DefaultDependencies(model.projectDependencies),
        model.children.map { DefaultProject(it) }
    )
}

@JsonClass(generateAdapter = true)
data class DefaultDependencies(
    override val repositories: DefaultRepositories,
    override val artifacts: List<DefaultArtifact>
) : Dependencies, Serializable {
    constructor(model: Dependencies) : this(
        DefaultRepositories(model.repositories),
        model.artifacts.map { DefaultArtifact(it) }
    )
}

@JsonClass(generateAdapter = true)
data class DefaultRepositories(
    override val maven: List<DefaultMaven>
) : Repositories, Serializable {
    constructor(model: Repositories) : this(
        model.maven.map { DefaultMaven(it) }
    )
}

@JsonClass(generateAdapter = true)
data class DefaultMaven(
    override val urls: List<String>
) : Maven, Serializable {
    constructor(model: Maven) : this(
        model.urls.toList()
    )
}

@JsonClass(generateAdapter = true)
data class DefaultArtifact(
    override val groupId: String,
    override val artifactId: String,
    override val version: String,
    override val classifier: String,
    override val extension: String,
    override val sha256: String
) : Artifact, Comparable<DefaultArtifact>, Serializable {
    constructor(model: Artifact) : this(
        model.groupId,
        model.artifactId,
        model.version,
        model.classifier,
        model.extension,
        model.sha256
    )

    override fun toString() = "$groupId:$artifactId:$version:$classifier:$extension"
    override fun compareTo(other: DefaultArtifact): Int = toString().compareTo(other.toString())
}
