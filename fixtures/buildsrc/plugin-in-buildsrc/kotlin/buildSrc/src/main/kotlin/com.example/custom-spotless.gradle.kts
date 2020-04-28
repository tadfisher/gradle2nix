package com.example

plugins {
    com.diffplug.gradle.spotless
}

spotless {
    kotlin {
        ktlint()
    }
}