package com.example.methodmesh.modules.trustedtimestamp

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TrustedTimestampVerificationTest {
    @Test
    fun `source hash mismatch is reported before network verification`() {
        val proof = proofZip(expectedHash = "00".repeat(32))

        val result = TrustedTimestampVerifier.verify(
            proofBytes = proof,
            sourceBytes = "actual source".toByteArray(),
            fallbackSourceName = "source.txt",
            timeoutMs = 1_000,
            proofName = "example-proof.zip"
        )

        assertEquals("failed_source_hash_mismatch", result.values[TrustedTimestampVerificationFields.STATUS])
        assertEquals("example-proof.zip", result.values[TrustedTimestampVerificationFields.PROOF_FILENAME])
        assertEquals("false", result.values[TrustedTimestampVerificationFields.HASH_MATCH])
    }

    private fun proofZip(expectedHash: String): ByteArray {
        val manifest = JSONObject()
            .put("subject", JSONObject().put("filename", "source.txt").put("sha256", expectedHash))
            .put("timestamp", JSONObject())
        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("proof.json"))
                zip.write(manifest.toString().toByteArray())
                zip.closeEntry()
            }
            output.toByteArray()
        }
    }
}
