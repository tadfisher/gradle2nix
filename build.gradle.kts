plugins {
    base
    idea
    id("com.github.ben-manes.versions") version "0.21.0"
    kotlin("jvm") version embeddedKotlinVersion apply false
    kotlin("kapt") version embeddedKotlinVersion apply false
    id("com.github.johnrengelman.shadow") version "5.0.0" apply false
    id("org.ysb33r.gradletest") version "2.0-rc.4" apply false
}

tasks {
    wrapper {
        gradleVersion = "5.4.1"
        distributionType = Wrapper.DistributionType.ALL
    }
}
