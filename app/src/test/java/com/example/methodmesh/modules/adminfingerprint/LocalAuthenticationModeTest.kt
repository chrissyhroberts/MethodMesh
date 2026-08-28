package com.example.methodmesh.modules.adminfingerprint

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalAuthenticationModeTest {
    @Test
    fun `all explicit authentication modes parse`() {
        assertEquals(
            LocalAuthenticationMode.Biometric,
            LocalAuthenticationMode.parse("biometric")
        )
        assertEquals(
            LocalAuthenticationMode.DeviceCredential,
            LocalAuthenticationMode.parse("device_credential")
        )
        assertEquals(
            LocalAuthenticationMode.BiometricOrDeviceCredential,
            LocalAuthenticationMode.parse("biometric_or_device_credential")
        )
    }

    @Test
    fun `unknown authentication modes are rejected`() {
        assertNull(LocalAuthenticationMode.parse("fingerprint_or_pin"))
    }
}
