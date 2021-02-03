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
    test {
        java.srcDir("src/test/kotlin")
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
    compileOnly("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("org.apache.maven:maven-repository-metadata:latest.release")
    implementation(project(":ivy"))
    implementation(project(":model"))
    shadow(gradleApi())

    compatTestImplementation("com.adobe.testing:s3mock-junit5:latest.release")
    compatTestImplementation("com.squareup.okio:okio:latest.release")
    compatTestImplementation("dev.minutest:minutest:latest.release")
    compatTestImplementation("io.javalin:javalin:latest.release")
    compatTestImplementation("io.strikt:strikt-core:latest.release")
    compatTestImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    compatTestImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    compatTestImplementation(embeddedKotlin("reflect"))
    compatTestImplementation(embeddedKotlin("stdlib-jdk8"))
    compatTestImplementation(embeddedKotlin("test-junit5"))
    compatTestImplementation(gradleTestKit())
    compatTestImplementation(project(":model"))
    compatTestRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    compatTestRuntimeOnly("org.junit.platform:junit-platform-launcher:latest.release")
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
    isSparse = true
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
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }

        // Default logging config exposes a classpath conflict between
        // the Gradle API and SFL4J.
        // (Sprint Boot is used in S3Mock)
        systemProperty("org.springframework.boot.logging.LoggingSystem", "org.springframework.boot.logging.java.JavaLoggingSystem")

        systemProperty("fixtures", "$rootDir/fixtures")
    }
}
