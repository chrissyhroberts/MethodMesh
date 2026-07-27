package com.example.researchos.modules.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

class NfcPortableCredentialTest {
    private val signer = TestCredentialSigner()

    @Test
    fun `portable credential round trip verifies without exposing its subject or pin`() {
        val provisioned = NfcPortableCredentialFormat.provision(
            credentialSubjectId = "operator_geoff",
            credentialId = "credential_001",
            pin = "1234".toCharArray(),
            signer = signer
        )

        assertFalse(provisioned.envelope.contains("operator_geoff"))
        assertFalse(provisioned.envelope.contains("1234"))
        assertTrue(
            "Typical credential envelope must fit a 492-byte NDEF tag with record overhead",
            provisioned.envelope.toByteArray(Charsets.UTF_8).size < 450
        )

        val verified = NfcPortableCredentialFormat.verify(
            envelope = provisioned.envelope,
            pin = "1234".toCharArray(),
            trustedIssuerKeyIds = setOf(signer.keyId)
        )

        assertTrue(verified.verified)
        assertEquals("operator_geoff", verified.credentialSubjectId)
        assertEquals("credential_001", verified.credentialId)
        assertEquals(true, verified.issuerTrusted)
        assertEquals(provisioned.credentialSecretHash, verified.credentialSecretHash)
    }

    @Test
    fun `wrong pin cannot decrypt credential`() {
        val provisioned = NfcPortableCredentialFormat.provision(
            credentialSubjectId = "operator_geoff",
            credentialId = "credential_002",
            pin = "123456".toCharArray(),
            signer = signer
        )

        val verified = NfcPortableCredentialFormat.verify(
            envelope = provisioned.envelope,
            pin = "654321".toCharArray()
        )

        assertFalse(verified.verified)
        assertEquals("PIN is incorrect.", verified.message)
        assertTrue(verified.issuerSignatureValid)
    }

    @Test
    fun `supplied issuer allow-list is enforced`() {
        val provisioned = NfcPortableCredentialFormat.provision(
            credentialSubjectId = "operator_geoff",
            credentialId = "credential_003",
            pin = "123456".toCharArray(),
            signer = signer
        )

        val verified = NfcPortableCredentialFormat.verify(
            envelope = provisioned.envelope,
            pin = "123456".toCharArray(),
            trustedIssuerKeyIds = setOf("different_issuer")
        )

        assertFalse(verified.verified)
        assertEquals(false, verified.issuerTrusted)
        assertTrue(verified.issuerSignatureValid)
    }

    @Test
    fun `credential envelope is found after text metadata or in a later record`() {
        val provisioned = NfcPortableCredentialFormat.provision(
            credentialSubjectId = "operator_geoff",
            credentialId = "credential_004",
            pin = "123456".toCharArray(),
            signer = signer
        )

        assertEquals(
            provisioned.envelope,
            NfcPortableCredentialFormat.extractEnvelope(
                listOf(
                    "ordinary first record",
                    "\u0002en${provisioned.envelope} | unrelated trailing record"
                )
            )
        )
    }

    private class TestCredentialSigner : NfcCredentialSigner {
        private val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }

        override val publicKey: PublicKey = keyPair.public

        override fun sign(content: ByteArray): ByteArray =
            Signature.getInstance(NfcCredentialSigner.SIGNATURE_ALGORITHM).run {
                initSign(keyPair.private)
                update(content)
                sign()
            }
    }
}
