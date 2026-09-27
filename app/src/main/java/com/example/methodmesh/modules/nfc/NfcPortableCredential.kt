package com.example.methodmesh.modules.nfc

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.example.methodmesh.core.crypto.Digests
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.nio.charset.StandardCharsets
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.interfaces.ECPublicKey
import java.time.Instant
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object NfcPortableCredentialFormat {
    const val VERSION = "ROSC1"
    const val MIME_TYPE = "methodmesh:portable-credential"
    const val KEY_DERIVATION = "argon2id-m32768-t3-p1"
    private const val MEMORY_KIB = 32 * 1024
    private const val ITERATIONS = 3
    private const val PARALLELISM = 1
    private const val KEY_BYTES = 32
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val SECRET_BYTES = 32
    private val envelopePattern = Regex("""ROSC1(?:\.[A-Za-z0-9_-]+){8}""")

    data class ProvisionedCredential(
        val envelope: String,
        val credentialId: String,
        val credentialSubjectId: String,
        val pinLength: Int,
        val issuedAtIso: String,
        val issuerKeyId: String,
        val issuerPublicKeyBase64: String,
        val envelopeHash: String,
        val credentialSecretHash: String
    )

    data class ParsedEnvelope(
        val pinLength: Int,
        val credentialId: String,
        val issuerKeyId: String,
        val salt: ByteArray,
        val nonce: ByteArray,
        val ciphertext: ByteArray,
        val issuerPublicKey: ByteArray,
        val signature: ByteArray,
        val unsignedEnvelope: String,
        val envelope: String
    )

    data class VerifiedCredential(
        val verified: Boolean,
        val message: String,
        val credentialId: String = "",
        val credentialSubjectId: String = "",
        val pinLength: Int = 0,
        val issuedAtIso: String = "",
        val issuerKeyId: String = "",
        val issuerPublicKeyBase64: String = "",
        val issuerSignatureValid: Boolean = false,
        val issuerTrusted: Boolean? = null,
        val envelopeHash: String = "",
        val credentialSecretHash: String = ""
    )

    fun provision(
        credentialSubjectId: String,
        credentialId: String,
        pin: CharArray,
        signer: NfcCredentialSigner,
        issuedAt: Instant = Instant.now(),
        random: SecureRandom = SecureRandom()
    ): ProvisionedCredential {
        val normalizedSubjectId = credentialSubjectId.trim()
        val normalizedCredentialId = credentialId.trim()
        require(normalizedSubjectId.isNotBlank()) { "credential_subject_id is required." }
        require(normalizedCredentialId.isNotBlank()) { "credential_id is required." }
        validatePin(pin)

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val secret = ByteArray(SECRET_BYTES).also(random::nextBytes)
        val issuedAtIso = issuedAt.toString()
        val plaintext = listOf(
            VERSION,
            encodeString(normalizedSubjectId),
            encodeBytes(secret),
            encodeString(issuedAtIso)
        ).joinToString(".").toByteArray(StandardCharsets.UTF_8)

        val issuerKeyId = signer.keyId
        val aad = associatedData(
            pinLength = pin.size,
            credentialId = normalizedCredentialId,
            issuerKeyId = issuerKeyId
        )
        val key = deriveKey(pin, salt)
        val ciphertext = encrypt(plaintext, key, nonce, aad)
        key.fill(0)

        val unsignedEnvelope = listOf(
            VERSION,
            pin.size.toString(),
            encodeString(normalizedCredentialId),
            issuerKeyId,
            encodeBytes(salt),
            encodeBytes(nonce),
            encodeBytes(ciphertext),
            encodeBytes(compressPublicKey(signer.publicKey))
        ).joinToString(".")
        val signature = signer.sign(unsignedEnvelope.toByteArray(StandardCharsets.UTF_8))
        val envelope = "$unsignedEnvelope.${encodeBytes(signature)}"
        pin.fill('\u0000')

        return ProvisionedCredential(
            envelope = envelope,
            credentialId = normalizedCredentialId,
            credentialSubjectId = normalizedSubjectId,
            pinLength = aad.pinLength,
            issuedAtIso = issuedAtIso,
            issuerKeyId = issuerKeyId,
            issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(signer.publicKey.encoded),
            envelopeHash = Digests.sha256Hex(envelope),
            credentialSecretHash = Digests.sha256Hex(secret)
        )
    }

    fun parse(envelope: String): ParsedEnvelope {
        val parts = envelope.trim().split('.')
        require(parts.size == 9 && parts[0] == VERSION) {
            "Tag does not contain a supported MethodMesh portable credential."
        }
        val pinLength = parts[1].toIntOrNull()
            ?.takeIf { it == 4 || it == 6 }
            ?: error("Credential PIN length is invalid.")
        val unsigned = parts.take(8).joinToString(".")
        return ParsedEnvelope(
            pinLength = pinLength,
            credentialId = decodeString(parts[2]),
            issuerKeyId = parts[3],
            salt = decodeBytes(parts[4]),
            nonce = decodeBytes(parts[5]),
            ciphertext = decodeBytes(parts[6]),
            issuerPublicKey = decodeBytes(parts[7]),
            signature = decodeBytes(parts[8]),
            unsignedEnvelope = unsigned,
            envelope = envelope.trim()
        )
    }

    /**
     * Finds a credential envelope without assuming that it is the first raw
     * NDEF payload. This also tolerates NDEF text-record language metadata and
     * tags containing more than one record.
     */
    fun extractEnvelope(candidateTexts: Iterable<String>): String? =
        candidateTexts
            .asSequence()
            .flatMap { candidate -> envelopePattern.findAll(candidate).map(MatchResult::value) }
            .firstOrNull { candidate -> runCatching { parse(candidate) }.isSuccess }

    fun verify(
        envelope: String,
        pin: CharArray,
        trustedIssuerKeyIds: Set<String> = emptySet()
    ): VerifiedCredential {
        val parsed = runCatching { parse(envelope) }.getOrElse { error ->
            pin.fill('\u0000')
            return VerifiedCredential(false, error.message ?: "Credential format is invalid.")
        }
        if (pin.size != parsed.pinLength || pin.any { !it.isDigit() }) {
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "Enter the ${parsed.pinLength}-digit PIN.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        }

        val publicKey = runCatching { decodePublicKey(parsed.issuerPublicKey) }.getOrNull()
        val actualIssuerKeyId = publicKey?.encoded?.let(Digests::sha256Hex)?.take(16).orEmpty()
        val signatureValid = publicKey != null &&
            actualIssuerKeyId == parsed.issuerKeyId &&
            verifySignature(
                publicKey,
                parsed.unsignedEnvelope.toByteArray(StandardCharsets.UTF_8),
                parsed.signature
            )
        if (!signatureValid) {
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "Credential issuer signature is invalid.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                issuerPublicKeyBase64 = publicKey?.encoded
                    ?.let(Base64.getEncoder()::encodeToString)
                    .orEmpty(),
                issuerSignatureValid = false,
                issuerTrusted = trustedIssuerKeyIds.takeIf(Set<String>::isNotEmpty)
                    ?.contains(parsed.issuerKeyId),
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        }
        val verifiedPublicKey = requireNotNull(publicKey)
        if (trustedIssuerKeyIds.isNotEmpty() && parsed.issuerKeyId !in trustedIssuerKeyIds) {
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "Credential was signed by an issuer that is not trusted for this workflow.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(verifiedPublicKey.encoded),
                issuerSignatureValid = true,
                issuerTrusted = false,
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        }

        val aad = associatedData(parsed.pinLength, parsed.credentialId, parsed.issuerKeyId)
        val key = runCatching { deriveKey(pin, parsed.salt) }.getOrElse {
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "Credential key-derivation data is invalid.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(verifiedPublicKey.encoded),
                issuerSignatureValid = true,
                issuerTrusted = trustedIssuerKeyIds.takeIf(Set<String>::isNotEmpty)
                    ?.contains(parsed.issuerKeyId),
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        }
        val plaintext = try {
            decrypt(parsed.ciphertext, key, parsed.nonce, aad)
        } catch (_: AEADBadTagException) {
            key.fill(0)
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "PIN is incorrect.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(verifiedPublicKey.encoded),
                issuerSignatureValid = true,
                issuerTrusted = trustedIssuerKeyIds.takeIf(Set<String>::isNotEmpty)
                    ?.contains(parsed.issuerKeyId),
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        } catch (_: Exception) {
            key.fill(0)
            pin.fill('\u0000')
            return VerifiedCredential(
                verified = false,
                message = "Credential encryption data is invalid.",
                credentialId = parsed.credentialId,
                pinLength = parsed.pinLength,
                issuerKeyId = parsed.issuerKeyId,
                issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(verifiedPublicKey.encoded),
                issuerSignatureValid = true,
                issuerTrusted = trustedIssuerKeyIds.takeIf(Set<String>::isNotEmpty)
                    ?.contains(parsed.issuerKeyId),
                envelopeHash = Digests.sha256Hex(parsed.envelope)
            )
        } finally {
            key.fill(0)
            pin.fill('\u0000')
        }
        val plaintextParts = String(plaintext, StandardCharsets.UTF_8).split('.')
        if (plaintextParts.size != 4 || plaintextParts[0] != VERSION) {
            return VerifiedCredential(false, "Decrypted credential structure is invalid.")
        }
        val memberId = runCatching { decodeString(plaintextParts[1]) }.getOrDefault("")
        val secret = runCatching { decodeBytes(plaintextParts[2]) }.getOrDefault(ByteArray(0))
        val issuedAtIso = runCatching { decodeString(plaintextParts[3]) }.getOrDefault("")
        if (memberId.isBlank() || secret.size != SECRET_BYTES || issuedAtIso.isBlank()) {
            return VerifiedCredential(false, "Decrypted credential contents are invalid.")
        }

        return VerifiedCredential(
            verified = true,
            message = "Credential and PIN verified.",
            credentialId = parsed.credentialId,
            credentialSubjectId = memberId,
            pinLength = parsed.pinLength,
            issuedAtIso = issuedAtIso,
            issuerKeyId = parsed.issuerKeyId,
            issuerPublicKeyBase64 = Base64.getEncoder().encodeToString(verifiedPublicKey.encoded),
            issuerSignatureValid = true,
            issuerTrusted = trustedIssuerKeyIds.takeIf(Set<String>::isNotEmpty)
                ?.contains(parsed.issuerKeyId),
            envelopeHash = Digests.sha256Hex(parsed.envelope),
            credentialSecretHash = Digests.sha256Hex(secret)
        )
    }

    private data class CredentialAad(
        val bytes: ByteArray,
        val pinLength: Int
    )

    private fun associatedData(
        pinLength: Int,
        credentialId: String,
        issuerKeyId: String
    ): CredentialAad = CredentialAad(
        bytes = "$VERSION|$pinLength|$credentialId|$issuerKeyId|$KEY_DERIVATION"
            .toByteArray(StandardCharsets.UTF_8),
        pinLength = pinLength
    )

    private fun validatePin(pin: CharArray) {
        require(pin.size == 4 || pin.size == 6) { "PIN must contain exactly 4 or 6 digits." }
        require(pin.all(Char::isDigit)) { "PIN must contain digits only." }
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray): ByteArray {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withMemoryAsKB(MEMORY_KIB)
            .withIterations(ITERATIONS)
            .withParallelism(PARALLELISM)
            .build()
        return ByteArray(KEY_BYTES).also { output ->
            Argon2BytesGenerator().apply { init(parameters) }.generateBytes(pin, output)
        }
    }

    private fun encrypt(
        plaintext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: CredentialAad
    ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad.bytes)
        doFinal(plaintext)
    }

    private fun decrypt(
        ciphertext: ByteArray,
        key: ByteArray,
        nonce: ByteArray,
        aad: CredentialAad
    ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        updateAAD(aad.bytes)
        doFinal(ciphertext)
    }

    private fun compressPublicKey(publicKey: PublicKey): ByteArray {
        val ecKey = publicKey as? ECPublicKey
            ?: error("Credential issuer key must be an EC public key.")
        val x = ecKey.w.affineX.toFixedUnsignedBytes(32)
        val prefix = if (ecKey.w.affineY.testBit(0)) 0x03 else 0x02
        return byteArrayOf(prefix.toByte()) + x
    }

    private fun decodePublicKey(encoded: ByteArray): PublicKey {
        require(encoded.size == 33 && encoded[0].toInt() and 0xff in 2..3) {
            "Issuer public key is not a compressed P-256 key."
        }
        val parameters = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec("secp256r1"))
        }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val prime = (parameters.curve.field as java.security.spec.ECFieldFp).p
        val x = BigInteger(1, encoded.copyOfRange(1, encoded.size))
        val ySquared = x.modPow(BigInteger.valueOf(3), prime)
            .add(parameters.curve.a.multiply(x))
            .add(parameters.curve.b)
            .mod(prime)
        var y = ySquared.modPow(prime.add(BigInteger.ONE).shiftRight(2), prime)
        val wantsOdd = encoded[0].toInt() and 1 == 1
        if (y.testBit(0) != wantsOdd) y = prime.subtract(y)
        require(y.multiply(y).mod(prime) == ySquared) { "Compressed issuer public key is invalid." }
        return KeyFactory.getInstance("EC").generatePublic(
            ECPublicKeySpec(ECPoint(x, y), parameters)
        )
    }

    private fun verifySignature(publicKey: PublicKey, content: ByteArray, signature: ByteArray): Boolean =
        runCatching {
            Signature.getInstance(NfcCredentialSigner.SIGNATURE_ALGORITHM).run {
                initVerify(publicKey)
                update(content)
                verify(signature)
            }
        }.getOrDefault(false)

    private fun encodeString(value: String): String =
        encodeBytes(value.toByteArray(StandardCharsets.UTF_8))

    private fun decodeString(value: String): String =
        String(decodeBytes(value), StandardCharsets.UTF_8)

    private fun encodeBytes(value: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value)

    private fun decodeBytes(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)
}

private fun BigInteger.toFixedUnsignedBytes(size: Int): ByteArray {
    val raw = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    require(raw.size <= size) { "Integer does not fit in $size bytes." }
    return ByteArray(size - raw.size) + raw
}

internal interface NfcCredentialSigner {
    val publicKey: PublicKey
    val keyId: String
        get() = Digests.sha256Hex(publicKey.encoded).take(16)

    fun sign(content: ByteArray): ByteArray

    companion object {
        const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }
}

internal object AndroidNfcCredentialSigner : NfcCredentialSigner {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "methodmesh_nfc_credential_issuer_v1"

    private val keyPair: KeyPair
        get() = existingKeyPair() ?: generateKeyPair()

    override val publicKey: PublicKey
        get() = keyPair.public

    override fun sign(content: ByteArray): ByteArray =
        Signature.getInstance(NfcCredentialSigner.SIGNATURE_ALGORITHM).run {
            initSign(keyPair.private)
            update(content)
            sign()
        }

    private fun existingKeyPair(): KeyPair? = runCatching {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey ?: return null
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey ?: return null
        KeyPair(publicKey, privateKey)
    }.getOrNull()

    private fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        generator.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKeyPair()
    }
}
