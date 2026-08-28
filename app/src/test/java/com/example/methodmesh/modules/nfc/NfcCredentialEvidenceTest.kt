package com.example.methodmesh.modules.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcCredentialEvidenceTest {
    @Test
    fun `canonical evidence binds normalized UID and raw first NDEF payload`() {
        val fields = NfcCredentialEvidence.fields(
            mapOf(
                NfcEvidenceFields.TAG_UID_HEX to "04 a1",
                NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX to "736563726574"
            )
        )

        assertEquals(
            NfcCredentialEvidence.FORMAT,
            fields[NfcCredentialEvidence.FORMAT_FIELD]
        )
        assertEquals(
            "27de2cfec57811ad53f31fe06fd4f0376c7019846fb427dd164a6b5ac3933994",
            fields[NfcCredentialEvidence.HASH_FIELD]
        )
    }

    @Test
    fun `empty NDEF payload is represented explicitly`() {
        val fields = NfcCredentialEvidence.fields(
            mapOf(NfcEvidenceFields.TAG_UID_HEX to "04A1")
        )

        assertEquals(
            "932cffbee439ace464c8a41ea03533732b424598343c2cd8ed62983b2aa22021",
            fields[NfcCredentialEvidence.HASH_FIELD]
        )
    }
}
