package com.example.methodmesh.modules.nfc

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotCaptureGateTest {
    @Test
    fun `only the first signal in a capture session is accepted`() {
        val gate = OneShotCaptureGate()
        assertTrue(gate.claim())
        assertFalse(gate.claim())
        assertFalse(gate.claim())
    }
}
