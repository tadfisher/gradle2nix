plugins {
    java
}

repositories {
    maven { url = uri("http://localhost:9999") }
}

dependencies {
    implementation(platform("io.micrometer:micrometer-bom:1.5.1"))
    implementation("io.micrometer:micrometer-core")
}