plugins {
    `embedded-kotlin`
    kotlin("kapt")
}

dependencies {
    api("com.squareup.moshi:moshi:latest.release")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:latest.release")
    implementation("net.swiftzer.semver:semver:latest.release")
}
