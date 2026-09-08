package com.example.methodmesh.modules.imageredaction

import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.MessageDigest

class ImageRedactionHashTest {
    @Test
    fun sha256MatchesActualFinalJpegBytes() {
        val finalJpegBytes = byteArrayOf(
            0xFF.toByte(),
            0xD8.toByte(),
            0xFF.toByte(),
            0xE0.toByte(),
            0x00,
            0x10,
            'J'.code.toByte(),
            'F'.code.toByte(),
            'I'.code.toByte(),
            'F'.code.toByte(),
            0x00,
            0xFF.toByte(),
            0xD9.toByte()
        )
        val expected = MessageDigest.getInstance("SHA-256")
            .digest(finalJpegBytes)
            .joinToString(separator = "") { "%02x".format(it) }

        assertEquals(expected, ImageRedactionHash.sha256Hex(finalJpegBytes))
    }

    @Test
    fun redactedImageSha256IsACanonicalOutputField() {
        assertEquals(true, ImageRedactionFields.REDACTED_IMAGE_SHA256 in ImageRedactionFields.outputs)
    }
}
