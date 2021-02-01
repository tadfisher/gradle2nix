@file:Suppress("UnstableApiUsage")

enableFeaturePreview("ONE_LOCKFILE_PER_PROJECT")

pluginManagement {
    val shadowVersion: String by settings
    val stutterVersion: String by settings

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.github.johnrengelman.shadow" -> useVersion(shadowVersion)
                "org.ajoberstar.stutter" -> useVersion(stutterVersion)
            }
            when (requested.id.namespace) {
                "org.jetbrains.kotlin",
                "org.jetbrains.kotlin.plugin" -> useVersion(embeddedKotlinVersion)
            }
        }
    }
}

plugins {
    kotlin("jvm") apply false
    kotlin("kapt") apply false
    id("com.github.johnrengelman.shadow") apply false
    id("org.ajoberstar.stutter") apply false
}

dependencyResolutionManagement {
    repositories {
        jcenter()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
        maven {
            url = uri("https://dl.bintray.com/ohnosequences/maven")
            content {
                includeGroup("ohnosequences")
            }
        }
    }
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
}

buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

include(":app", ":model", ":plugin")
