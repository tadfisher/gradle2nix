plugins {
    `embedded-kotlin`
    kotlin("kapt")
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

dependencies {
    compileOnly("com.squareup.moshi:moshi:1.8.0")
    compileOnly("com.squareup.okio:okio:2.2.2")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:1.8.0")
}
