package com.example.methodmesh.modules.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcCredentialFormContractTest {
    @Test
    fun `v1 maps current credential methods and expiry schema`() {
        assertEquals("v1", NfcCredentialFormContract.CURRENT_VERSION)
        assertEquals(As100NfcCredentialProvisioningMethod.VERSION, NfcCredentialFormContract.provisioning.methodVersion)
        assertEquals(As100NfcCredentialVerificationMethod.VERSION, NfcCredentialFormContract.verification.methodVersion)
        assertEquals(NfcPortableCredentialFormat.VERSION, NfcCredentialFormContract.provisioning.credentialFormatVersion)
        assertEquals("valid_until_date_iso", NfcCredentialFormContract.provisioning.requiredInputs["valid_until_date"])
        assertTrue(NfcCredentialFormContract.verification.requiredReturnFields.contains(NfcProvisionFields.VALID_UNTIL_ISO))
    }

    @Test
    fun `contract lookup is explicitly versioned`() {
        assertTrue(NfcCredentialFormContract.forMethod("nfc_credential_verification", "v1") != null)
        assertEquals(null, NfcCredentialFormContract.forMethod("nfc_credential_verification", "v2"))
        assertEquals(listOf("v1"), NfcCredentialFormContract.supportedVersions())
    }
}
