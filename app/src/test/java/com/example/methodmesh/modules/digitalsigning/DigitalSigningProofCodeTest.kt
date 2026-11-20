package com.example.methodmesh.modules.digitalsigning

import org.junit.Assert.assertEquals
import org.junit.Test

class DigitalSigningProofCodeTest {
    @Test
    fun `proof code is deterministic projection of committed pdf hash`() {
        assertEquals("7A3C-91E2", DigitalSigningProofCode.fromSha256("7a3c91e2" + "00".repeat(28)))
    }
}
