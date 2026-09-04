package com.example.methodmesh.modules.imageredaction

import java.security.MessageDigest

object ImageRedactionHash {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
