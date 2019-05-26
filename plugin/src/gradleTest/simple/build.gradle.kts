plugins {
    java
}

repositories {
    jcenter()
}

dependencies {
    implementation("com.squareup.okio:okio:2.2.2")
    implementation("com.squareup.moshi:moshi:1.8.0")
}

tasks.register("runGradleTest") {
    dependsOn("nixGradleEnv")

    doLast {
        assert(file("gradle-env.json").readText() == file("gradle/nix/gradle-env.json").readText()) {
            "Mismatch: gradle-env.json"
        }
    }
}
