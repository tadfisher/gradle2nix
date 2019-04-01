tasks.register("runGradleTest") {
    dependsOn("nixGradleEnv")
}
