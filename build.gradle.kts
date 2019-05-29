plugins {
    base
    idea
    id("com.github.ben-manes.versions") version "0.21.0"
    kotlin("jvm") version embeddedKotlinVersion apply false
    kotlin("kapt") version embeddedKotlinVersion apply false
    id("com.github.johnrengelman.shadow") version "5.0.0" apply false
    id("org.ajoberstar.stutter") version "0.5.0" apply false
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
        gradleVersion = "5.4.1"
        distributionType = Wrapper.DistributionType.ALL
    }
}
