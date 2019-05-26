tasks.register("runGradleTest") {
    dependsOn("nixGradleEnv")

    doLast {
        assert(file("gradle-env.json").readText() == file("gradle/nix/gradle-env.json").readText()) {
            "Mismatch: gradle-env.json"
        }
        assert(file("buildSrc/gradle-env.json").readText() == file("buildSrc/gradle/nix/gradle-env.json").readText()) {
            "Mismatch (buildSrc): gradle-env.json"
        }
    }
}
