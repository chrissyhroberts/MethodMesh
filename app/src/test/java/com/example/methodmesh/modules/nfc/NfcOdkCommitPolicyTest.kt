package com.example.methodmesh.modules.nfc

import org.junit.Assert.assertEquals
import org.junit.Test

class NfcOdkCommitPolicyTest {
    @Test
    fun `auto policy matches standalone defaults`() {
        assertEquals(NfcOdkCommitPolicy.Mode.VALUE, NfcOdkCommitPolicy.decide("date", "", "visit_date").mode)
        assertEquals("yyyy-mm-dd", NfcOdkCommitPolicy.decide("date", "", "visit_date").transform)
        assertEquals(NfcOdkCommitPolicy.Mode.SHA256, NfcOdkCommitPolicy.decide("text", "", "notes").mode)
        assertEquals(NfcOdkCommitPolicy.Mode.EXCLUDE, NfcOdkCommitPolicy.decide("calculate", "", "derived").mode)
        assertEquals(NfcOdkCommitPolicy.Mode.VALUE, NfcOdkCommitPolicy.decide("select_one sex", "", "sex").mode)
        assertEquals(NfcOdkCommitPolicy.Mode.SHA256, NfcOdkCommitPolicy.decide("select_multiple symptoms", "", "symptoms").mode)
    }

    @org.junit.Test(expected = IllegalArgumentException::class)
    fun `media must be explicitly excluded`() {
        NfcOdkCommitPolicy.decide("image", "auto", "photo")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `select multiple cannot use value`() {
        NfcOdkCommitPolicy.decide("select_multiple symptoms", "value", "symptoms")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unknown override fails`() {
        NfcOdkCommitPolicy.decide("text", "bogus", "notes")
    }

    @Test
    fun `explicit exclusions and hashes are enforced`() {
        assertEquals(NfcOdkCommitPolicy.Mode.EXCLUDE, NfcOdkCommitPolicy.decide("text", "exclude", "private_note").mode)
        assertEquals(NfcOdkCommitPolicy.Mode.SHA256, NfcOdkCommitPolicy.decide("calculate", "sha256", "derived").mode)
        assertEquals(NfcOdkCommitPolicy.Mode.EXCLUDE, NfcOdkCommitPolicy.decide("begin repeat", "exclude", "visits").mode)
    }
}
