package org.nixos.gradle2nix

import java.io.File
import java.net.URL
import java.security.MessageDigest

private const val HEX = "0123456789abcdef"

internal fun File.sha256(): String = readBytes().sha256()

private fun ByteArray.sha256() = buildString {
    MessageDigest.getInstance("SHA-256").digest(this@sha256)
        .asSequence()
        .map(Byte::toInt)
        .forEach {
            append(HEX[it shr 4 and 0x0f])
            append(HEX[it and 0x0f])
        }
}

internal fun String.toUrl(): URL = URL(this)
