package com.example.researchos.core.crypto

import java.security.MessageDigest

object Digests {
    fun sha256Hex(value: String): String = sha256Hex(value.toByteArray(Charsets.UTF_8))

    fun sha256Hex(value: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(value)
        .joinToString("") { "%02x".format(it) }
}
