plugins {
    `embedded-kotlin`
    kotlin("kapt")
}

dependencies {
    api("com.squareup.moshi:moshi:+")
    api("com.squareup.okio:okio:+")
    kapt("com.squareup.moshi:moshi-kotlin-codegen:+")
}
