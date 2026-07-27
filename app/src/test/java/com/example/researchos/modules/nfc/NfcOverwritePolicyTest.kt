package com.example.researchos.modules.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
}
