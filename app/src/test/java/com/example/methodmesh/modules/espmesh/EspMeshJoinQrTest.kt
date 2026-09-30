package com.example.methodmesh.modules.espmesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64

class EspMeshJoinQrTest {
    private val e2eKey = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32) { it.toByte() })

    @Test fun joinBundleRoundTripsBothKeys() {
        val original = EspMeshJoinBundle("field-a", "esp-secret", e2eKey)
        assertEquals(original, EspMeshJoinBundle.decode(original.encode()))
    }

    @Test fun unrelatedOrMalformedQrCodesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { EspMeshJoinBundle.decode("{\"protocol\":\"other\",\"version\":1}") }
        assertThrows(IllegalArgumentException::class.java) { EspMeshJoinBundle.decode(EspMeshJoinBundle("field-a", "esp-secret", "bad").encode()) }
    }
}
