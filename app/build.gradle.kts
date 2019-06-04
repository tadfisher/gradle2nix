import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
}

group = "org.nixos"
version = "1.0.0-SNAPSHOT"

repositories {
    jcenter()
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

dependencies {
    implementation(project(":model"))
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("com.github.ajalt:clikt:latest.release")
    implementation("org.slf4j:slf4j-api:latest.release")
    runtimeOnly("org.slf4j:slf4j-simple:latest.release")
    implementation("com.squareup.moshi:moshi:latest.release")
    implementation("com.squareup.moshi:moshi-adapters:latest.release")
    implementation("com.squareup.moshi:moshi-kotlin:latest.release")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:latest.release")
    implementation("com.squareup.okio:okio:latest.release")
}

application {
    mainClassName = "org.nixos.gradle2nix.MainKt"
    applicationName = "gradle2nix"
    applicationDefaultJvmArgs += "-Dorg.nixos.gradle2nix.share=@APP_HOME@/share"
    applicationDistribution
        .from(tasks.getByPath(":plugin:shadowJar"))
        .into("share")
        .rename("plugin.*\\.jar", "plugin.jar")
}

tasks {
    startScripts {
        doLast {
            unixScript.writeText(unixScript.readText().replace("@APP_HOME@", "\$APP_HOME"))
            windowsScript.writeText(windowsScript.readText().replace("@APP_HOME@", "%APP_HOME%"))
        }
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
        }
    }
}
