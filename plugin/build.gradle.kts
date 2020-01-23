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

sourceSets {
    compatTest {
        resources {
            srcDir("$rootDir/fixtures")
        }
    }
}

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
    implementation("org.apache.ivy:ivy:latest.release")
    implementation("org.apache.maven:maven-repository-metadata:latest.release")

    compatTestImplementation(embeddedKotlin("stdlib-jdk8"))
    compatTestImplementation(embeddedKotlin("test-junit5"))
    compatTestImplementation(embeddedKotlin("reflect"))
    compatTestImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    compatTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    compatTestImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    compatTestRuntimeOnly("org.junit.platform:junit-platform-launcher:latest.release")
    compatTestImplementation("dev.minutest:minutest:latest.release")
    compatTestImplementation(gradleTestKit())
    compatTestImplementation(project(":model"))
    compatTestImplementation("io.strikt:strikt-core:latest.release")
    compatTestImplementation("com.squareup.okio:okio:latest.release")
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
