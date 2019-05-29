package org.nixos.gradle2nix

import java.io.File

fun resolveProjects(config: Config) = config.allProjects.run {
    if (config.buildSrc) {
        flatMap { listOfNotNull(it, it.findBuildSrc()) }
    } else {
        this
    }
}

fun File.findBuildSrc(): File? =
    resolve("buildSrc").takeIf { it.isDirectory }

fun File.isProjectRoot(): Boolean =
    isDirectory && (resolve("settings.gradle").isFile || resolve("settings.gradle.kts").isFile)
