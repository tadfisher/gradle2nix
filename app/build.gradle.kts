import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    kotlin("kapt")
    application
}

dependencies {
    implementation(project(":model"))
    implementation(kotlin("reflect"))
    implementation("org.gradle:gradle-tooling-api:${gradle.gradleVersion}")
    implementation("com.github.ajalt:clikt:latest.release")
    implementation("org.slf4j:slf4j-api:latest.release")
    runtimeOnly("org.slf4j:slf4j-simple:latest.release")
    implementation("com.squareup.moshi:moshi-adapters:latest.release")
    implementation("com.squareup.moshi:moshi-kotlin:latest.release")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:latest.release")
    implementation("com.squareup.okio:okio:latest.release")

    testRuntimeOnly(kotlin("reflect"))
    testImplementation("org.spekframework.spek2:spek-dsl-jvm:latest.release")
    testRuntimeOnly("org.spekframework.spek2:spek-runner-junit5:latest.release")
    testImplementation("io.strikt:strikt-core:latest.release")
}

application {
    mainClass.set("org.nixos.gradle2nix.MainKt")
    applicationName = "gradle2nix"
    applicationDefaultJvmArgs += "-Dorg.nixos.gradle2nix.share=@APP_HOME@/share"
    applicationDistribution
        .from(tasks.getByPath(":plugin:shadowJar"))
        .into("share")
        .rename("plugin.*\\.jar", "plugin.jar")
}

sourceSets {
    test {
        resources {
            srcDir("$rootDir/fixtures")
        }
    }
}

tasks {
    (run) {
        dependsOn(installDist)
        doFirst {
            systemProperties("org.nixos.gradle2nix.share" to installDist.get().destinationDir.resolve("share"))
        }
    }

    startScripts {
        doLast {
            unixScript.writeText(unixScript.readText().replace("@APP_HOME@", "\$APP_HOME"))
            windowsScript.writeText(windowsScript.readText().replace("@APP_HOME@", "%APP_HOME%"))
        }
    }

    test {
        dependsOn(installDist)
        doFirst {
            systemProperties("org.nixos.gradle2nix.share" to installDist.get().destinationDir.resolve("share"))
        }
        useJUnitPlatform {
            includeEngines("spek2")
        }
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "1.8"
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
}
