buildscript {
    configurations.classpath {
        resolutionStrategy.activateDependencyLocking()
    }
}

plugins {
    `kotlin-dsl`
    id("com.github.johnrengelman.shadow")
    id("org.ajoberstar.stutter")
}
apply {
    plugin("kotlin")
}

group = "org.nixos"
version = "1.0.0-SNAPSHOT"

dependencyLocking {
    lockAllConfigurations()
}

configurations {
    compile {
        dependencies.remove(project.dependencies.gradleApi())
    }
}

dependencies {
    implementation(project(":model"))
    shadow(gradleApi())
    compileOnly("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("org.apache.maven:maven-model:latest.release")
    implementation("org.apache.maven:maven-model-builder:latest.release")

    compatTestImplementation(embeddedKotlin("stdlib-jdk8"))
    compatTestImplementation(embeddedKotlin("test-junit5"))
    compatTestImplementation(embeddedKotlin("reflect"))
    compatTestImplementation("org.junit.jupiter:junit-jupiter-api:5.4+")
    compatTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.4+")
    compatTestImplementation(gradleTestKit())
    compatTestImplementation(project(":model"))
    compatTestImplementation("io.strikt:strikt-core:latest.release")
}

gradlePlugin {
    plugins {
        register("gradle2nix") {
            id = "org.nixos.gradle2nix"
            displayName = "gradle2nix"
            description = "Expose Gradle tooling model for the gradle2nix tool"
            implementationClass = "org.nixos.gradle2nix.Gradle2NixPlugin"
        }
    }
}

kotlinDslPluginOptions {
    experimentalWarning.set(false)
}

stutter {
    java(8) {
        compatibleRange("4.4")
    }
    java(11) {
        compatibleRange("5.0")
    }
}

tasks {
    pluginUnderTestMetadata {
        pluginClasspath.setFrom(files(shadowJar))
    }

    withType<Test> {
        useJUnitPlatform()
    }
}
