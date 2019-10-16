plugins {
    `embedded-kotlin`
    kotlin("kapt")
}

dependencies {
    compileOnly("com.squareup.moshi:moshi:+")
    compileOnly("com.squareup.okio:okio:+")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:+")

    // https://github.com/gradle/gradle/issues/10697
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-common:1.3.41")
    testImplementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.3.41")
}
