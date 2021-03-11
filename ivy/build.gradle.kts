plugins {
    kotlin("jvm")
}

dependencies {
    api("org.apache.ivy:ivy:latest.release")
    api("com.amazonaws:aws-java-sdk-s3:latest.release")

    testImplementation("com.adobe.testing:s3mock-junit5:latest.release")
    testImplementation("io.strikt:strikt-core:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-api:latest.release")
    testImplementation("org.junit.jupiter:junit-jupiter-params:latest.release")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:latest.release")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:latest.release")
}

tasks {
    test {
        useJUnitPlatform {
            includeEngines("junit-jupiter")
        }
        systemProperty("fixtures", "$rootDir/fixtures")
    }
}