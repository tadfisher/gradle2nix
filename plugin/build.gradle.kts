plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    kotlin("kapt")
    id("com.github.johnrengelman.shadow")
    id("org.ysb33r.gradletest")
}

group = "org.nixos"
version = "1.0.0-SNAPSHOT"

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("org.apache.maven:maven-model:3.6.1")
    implementation("org.apache.maven:maven-model-builder:3.6.1")
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

tasks {
    gradleTest {
        versions("5.0", "5.1.1", "5.2.1", "5.3.1", "5.4.1")
        kotlinDsl = true
    }

    gradleTestGenerator {
        dependsOn(shadowJar)
        doLast {
            file(gradleTest.get().initScript).bufferedWriter().use { out ->
                out.appendln("""
                    initscript {
                        dependencies {
                            classpath fileTree('file:${buildDir.absolutePath}/libs'.toURI()) {
                                include '*.jar'
                            }
                """.trimIndent())

                out.appendln("""
                        }
                    }

                    apply plugin: org.nixos.gradle2nix.Gradle2NixPlugin
                """.trimIndent())
            }
        }
    }
}
