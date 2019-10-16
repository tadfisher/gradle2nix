plugins {
    base
    idea
    kotlin("jvm") version embeddedKotlinVersion apply false
    kotlin("kapt") version embeddedKotlinVersion apply false
    id("com.github.johnrengelman.shadow") version "5.1.0" apply false
    id("org.ajoberstar.stutter") version "0.5.0" apply false
}

subprojects {
    repositories {
        jcenter()
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}

allprojects {
    plugins.withType<JavaBasePlugin> {
        this@allprojects.withConvention(JavaPluginConvention::class) {
            sourceSets.all {
                configurations {
                    named(compileClasspathConfigurationName) {
                        resolutionStrategy.activateDependencyLocking()
                    }
                    named(runtimeClasspathConfigurationName) {
                        resolutionStrategy.activateDependencyLocking()
                    }
                }
            }

            tasks.register("lock") {
                doFirst {
                    assert(gradle.startParameter.isWriteDependencyLocks)
                }
                doLast {
                    sourceSets.all {
                        configurations[compileClasspathConfigurationName].resolve()
                        configurations[runtimeClasspathConfigurationName].resolve()
                    }
                }
            }
        }
    }
}

tasks {
    wrapper {
        gradleVersion = "5.6.2"
        distributionType = Wrapper.DistributionType.ALL
    }
}
