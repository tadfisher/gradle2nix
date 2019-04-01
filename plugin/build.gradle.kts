plugins {
    id("com.github.johnrengelman.shadow") version "4.0.0"
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    kotlin("kapt") version embeddedKotlinVersion
}

group = "org.nixos"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.apache.maven:maven-model:3.5.4")
    implementation("org.apache.maven:maven-model-builder:3.5.4")
    implementation("com.squareup.okio:okio:2.2.2")
    implementation("com.squareup.moshi:moshi:1.8.0")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")
}

gradlePlugin {
    plugins {
        register("gradle2nix") {
            id = "org.nixos.gradle2nix"
            displayName = "gradle2nix"
            description = "Create Nix configurations suitable for reproducible packaging"
            implementationClass = "org.nixos.gradle2nix.Gradle2NixPlugin"
        }
    }
}