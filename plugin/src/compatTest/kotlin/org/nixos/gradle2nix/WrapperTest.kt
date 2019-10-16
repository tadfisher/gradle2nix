package org.nixos.gradle2nix

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

class WrapperTest {
    @TempDir
    lateinit var root: File

    @Test
    fun `resolves gradle version from wrapper configuration`() {
        val model = root.buildKotlin("""
            tasks.wrapper {
                gradleVersion = "5.5.1"
            }
        """.trimIndent())

        assertEquals(model.gradle.version, "5.5.1")
    }
}