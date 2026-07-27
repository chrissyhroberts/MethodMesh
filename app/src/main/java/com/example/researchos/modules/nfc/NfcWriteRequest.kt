package com.example.researchos.modules.nfc

enum class NfcOverwritePolicy(val wireValue: String, val label: String) {
    EmptyOnly("empty_only", "Empty tags only"),
    Replace("replace", "Replace existing content"),
    CompareAndReplace("compare_and_replace", "Replace only if hash matches");

    companion object {
        fun parse(value: String?): NfcOverwritePolicy? = entries.firstOrNull {
            it.wireValue.equals(value?.trim(), ignoreCase = true)
        }
    }
}

internal data class NfcOverwriteDecision(val allowed: Boolean, val reason: String)

internal fun evaluateOverwritePolicy(
    policy: NfcOverwritePolicy,
    hasExistingContent: Boolean,
    existingMessageHash: String?,
    expectedCurrentHash: String?
): NfcOverwriteDecision = when (policy) {
    NfcOverwritePolicy.EmptyOnly -> if (hasExistingContent) {
        NfcOverwriteDecision(false, "Tag already contains NDEF data. Choose replace explicitly to overwrite it.")
    } else {
        NfcOverwriteDecision(true, "Tag is empty.")
    }
    NfcOverwritePolicy.Replace -> NfcOverwriteDecision(true, "Existing content replacement explicitly allowed.")
    NfcOverwritePolicy.CompareAndReplace -> {
        val expected = expectedCurrentHash?.trim()?.lowercase().orEmpty()
        when {
            !expected.matches(Regex("[0-9a-f]{64}")) -> NfcOverwriteDecision(
                false,
                "Compare-and-replace requires a 64-character SHA-256 hash of the current NDEF message."
            )
            existingMessageHash == null -> NfcOverwriteDecision(false, "Tag has no current NDEF message to compare.")
            !existingMessageHash.equals(expected, ignoreCase = true) -> NfcOverwriteDecision(
                false,
                "Current NDEF message hash does not match expected_current_hash."
            )
            else -> NfcOverwriteDecision(true, "Current NDEF message hash matches.")
        }
    }
}

data class NfcWriteRequest(
    val recordType: String,
    val value: String,
    val mimeType: String = "text/plain",
    val languageCode: String = "en",
    val overwritePolicy: NfcOverwritePolicy = NfcOverwritePolicy.EmptyOnly,
    val expectedCurrentHash: String? = null
)
