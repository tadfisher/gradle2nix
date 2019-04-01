package org.nixos.gradle2nix

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

private fun versionAtLeast(version: String): Boolean =
    GradleVersion.current() >= GradleVersion.version(version)

internal fun <T> Property<T>.conventionCompat(value: T): Property<T> {
    return if (versionAtLeast("5.1")) {
        convention(value)
    } else {
        apply { set(value) }
    }
}

internal fun <T> Property<T>.conventionCompat(valueProvider: Provider<out T>): Property<T> {
    return if (versionAtLeast("5.1")) {
        convention(valueProvider)
    } else {
        apply { set(valueProvider) }
    }
}

internal fun DirectoryProperty.conventionCompat(
    value: Directory
): DirectoryProperty {
    return if (versionAtLeast("5.1")) {
        convention(value)
    } else {
        apply { set(value) }
    }
}

internal fun DirectoryProperty.conventionCompat(
    valueProvider: Provider<out Directory>
): DirectoryProperty {
    return if (versionAtLeast("5.1")) {
        convention(valueProvider)
    } else {
        apply { set(valueProvider) }
    }
}


internal fun RegularFileProperty.conventionCompat(
    value: RegularFile
): RegularFileProperty {
    return if (versionAtLeast("5.1")) {
        convention(value)
    } else {
        apply { set(value) }
    }
}

internal fun RegularFileProperty.conventionCompat(
    valueProvider: Provider<out RegularFile>
): RegularFileProperty {
    return if (versionAtLeast("5.1")) {
        convention(valueProvider)
    } else {
        apply { set(valueProvider) }
    }
}
