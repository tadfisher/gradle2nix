plugins {
    java
}

repositories {
    mavenCentral()
    maven { url = uri("https://oss.sonatype.org/content/repositories/snapshots/") }
}

dependencies {
    "implementation"("com.squareup.okio:okio:2.5.0-SNAPSHOT")
}
