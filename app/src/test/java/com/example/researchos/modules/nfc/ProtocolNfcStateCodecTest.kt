package com.example.researchos.modules.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolNfcStateCodecTest {
    @Test
    fun stateRoundTripsAndCompletionAdvancesBits() {
        val initial = ProtocolNfcStateCodec.empty("study-a", "1", flagBitCount = 8, completionBitCount = 16)
        val encoded = ProtocolNfcStateCodec.encode(initial)
        val decoded = ProtocolNfcStateCodec.decode(encoded)!!
        assertEquals(initial, decoded)
        assertTrue(ProtocolNfcStateCodec.requiredBitsMatch(decoded, "00", "00"))

        val completed = ProtocolNfcStateCodec.complete(decoded, "0002", "event-hash", setFlagsHex = "04")
        assertEquals("04", completed.flagBitsHex)
        assertEquals("0002", completed.completionBitsHex)
        assertEquals("040002", completed.bitsHex)
        assertEquals(1L, completed.stateVersion)
        assertTrue(ProtocolNfcStateCodec.requiredBitsMatch(completed, "0002", "0002"))
        assertTrue(ProtocolNfcStateCodec.expressionMatches(completed, "ANY(0001,0002)", "00", "00"))
        assertEquals("flag_two", ProtocolNfcStateCodec.labelsForBits(completed.flagBitsHex, "2=flag_two"))
        assertEquals("form_two", ProtocolNfcStateCodec.labelsForBits(completed.completionBitsHex, "1=form_two"))
    }
}
