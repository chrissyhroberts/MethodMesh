package com.example.methodmesh.modules.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcOverwritePolicyTest {
    private val existingHash = "a".repeat(64)

    @Test
    fun `empty-only rejects a non-empty tag`() {
        val decision = evaluateOverwritePolicy(
            NfcOverwritePolicy.EmptyOnly,
            hasExistingContent = true,
            existingMessageHash = existingHash,
            expectedCurrentHash = null
        )
        assertFalse(decision.allowed)
    }

    @Test
    fun `replace explicitly permits a non-empty tag`() {
        val decision = evaluateOverwritePolicy(
            NfcOverwritePolicy.Replace,
            hasExistingContent = true,
            existingMessageHash = existingHash,
            expectedCurrentHash = null
        )
        assertTrue(decision.allowed)
    }

    @Test
    fun `compare-and-replace requires the current hash`() {
        assertTrue(evaluateOverwritePolicy(
            NfcOverwritePolicy.CompareAndReplace,
            hasExistingContent = true,
            existingMessageHash = existingHash,
            expectedCurrentHash = existingHash.uppercase()
        ).allowed)
        assertFalse(evaluateOverwritePolicy(
            NfcOverwritePolicy.CompareAndReplace,
            hasExistingContent = true,
            existingMessageHash = existingHash,
            expectedCurrentHash = "b".repeat(64)
        ).allowed)
    }

    @Test
    fun `unknown wire policy is rejected`() {
        assertNull(NfcOverwritePolicy.parse("overwrite"))
    }

    @Test
    fun `factory formatted empty text record counts as blank`() {
        assertFalse(
            isMeaningfulNdefRecord(
                isEmptyTnf = false,
                isTextRecord = true,
                decodedText = "",
                isUriRecord = false,
                decodedUri = null,
                payloadSize = 3,
                idSize = 0
            )
        )
    }

    @Test
    fun `text and binary payloads count as existing content`() {
        assertTrue(
            isMeaningfulNdefRecord(
                isEmptyTnf = false,
                isTextRecord = true,
                decodedText = "participant_P001",
                isUriRecord = false,
                decodedUri = null,
                payloadSize = 19,
                idSize = 0
            )
        )
        assertTrue(
            isMeaningfulNdefRecord(
                isEmptyTnf = false,
                isTextRecord = false,
                decodedText = null,
                isUriRecord = false,
                decodedUri = null,
                payloadSize = 4,
                idSize = 0
            )
        )
    }

    @Test
    fun `custom media type writes its exact value as MIME payload`() {
        assertEquals(
            NfcRecordEncoding.Mime,
            classifyNfcRecordType("application/x-participantid")
        )
        assertEquals(
            NfcRecordEncoding.Text,
            classifyNfcRecordType("text/plain")
        )
    }

    @Test
    fun `ambiguous post-write communication errors request a fresh read-back`() {
        assertTrue(requiresFreshNdefReadBack(
            "NDEF write completed, but the immediate read-back could not verify it."
        ))
        assertTrue(requiresFreshNdefReadBack(
            "NDEF write may have completed, but communication was lost before verification."
        ))
        assertFalse(requiresFreshNdefReadBack("NDEF write failed: tag is read-only."))
    }
}
