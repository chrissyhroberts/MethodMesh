package com.example.methodmesh.modules.cryptography

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Dependency-free JWE Compact Serialization for password protection.
 *
 * Standards:
 * - RFC 7516 JSON Web Encryption (JWE)
 * - RFC 7518 PBES2-HS256+A128KW and A256GCM
 * - RFC 3394 AES Key Wrap
 *
 * This implementation intentionally supports one conservative profile only:
 *   alg = PBES2-HS256+A128KW
 *   enc = A256GCM
 *
 * Keeping the profile narrow makes the implementation testable and portable while
 * avoiding a private MethodMesh ciphertext format.
 */
object JweEngine {
    const val ALG = "PBES2-HS256+A128KW"
    const val ENC = "A256GCM"
    const val DEFAULT_P2C = 210_000
    const val MAX_INPUT_BYTES = 24 * 1024 * 1024

    private val rng = SecureRandom()
    private val b64u = Base64.getUrlEncoder().withoutPadding()
    private val b64ud = Base64.getUrlDecoder()

    data class Metadata(
        val algorithm: String,
        val encryption: String,
        val p2c: Int,
        val p2sBytes: Int
    )

    fun encrypt(plaintext: ByteArray, password: CharArray, p2c: Int = DEFAULT_P2C): String {
        require(plaintext.size <= MAX_INPUT_BYTES) {
            "JWE input exceeds ${MAX_INPUT_BYTES / (1024 * 1024)} MiB standalone limit."
        }
        require(password.isNotEmpty()) { "Password is required." }
        require(p2c in 10_000..1_000_000) { "PBES2 iteration count outside accepted range." }

        val p2s = randomBytes(16)
        val protectedJson = buildHeader(p2s, p2c)
        val protected = b64u.encodeToString(protectedJson.toByteArray(StandardCharsets.UTF_8))

        val kek = deriveKek(password, p2s, p2c)
        val cek = randomBytes(32)
        val wrappedCek = AesKeyWrap.wrap(kek, cek)

        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(protected.toByteArray(StandardCharsets.US_ASCII))
        val sealed = cipher.doFinal(plaintext)
        val tag = sealed.copyOfRange(sealed.size - 16, sealed.size)
        val ciphertext = sealed.copyOfRange(0, sealed.size - 16)

        cek.fill(0)
        kek.fill(0)

        return listOf(
            protected,
            b64u.encodeToString(wrappedCek),
            b64u.encodeToString(iv),
            b64u.encodeToString(ciphertext),
            b64u.encodeToString(tag)
        ).joinToString(".")
    }

    fun decrypt(compactJwe: String, password: CharArray): ByteArray {
        require(password.isNotEmpty()) { "Password is required." }
        val parts = compactJwe.trim().split('.')
        require(parts.size == 5) { "Expected JWE Compact Serialization with five parts." }
        val headerJson = String(b64ud.decode(parts[0]), StandardCharsets.UTF_8)
        val header = parseHeader(headerJson)
        require(header.alg == ALG) { "Unsupported JWE alg: ${header.alg}" }
        require(header.enc == ENC) { "Unsupported JWE enc: ${header.enc}" }
        require(header.p2c in 10_000..1_000_000) { "Unsafe or unsupported PBES2 iteration count." }

        val kek = deriveKek(password, b64ud.decode(header.p2s), header.p2c)
        val cek = AesKeyWrap.unwrap(kek, b64ud.decode(parts[1]))
        require(cek.size == 32) { "Unexpected CEK length." }

        val iv = b64ud.decode(parts[2])
        val ciphertext = b64ud.decode(parts[3])
        val tag = b64ud.decode(parts[4])
        require(iv.size == 12) { "A256GCM JWE IV must be 96 bits." }
        require(tag.size == 16) { "A256GCM JWE tag must be 128 bits." }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(cek, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(parts[0].toByteArray(StandardCharsets.US_ASCII))
        val plaintext = cipher.doFinal(ciphertext + tag)

        cek.fill(0)
        kek.fill(0)
        return plaintext
    }

    fun inspect(compactJwe: String): Metadata {
        val parts = compactJwe.trim().split('.')
        require(parts.size == 5) { "Expected JWE Compact Serialization with five parts." }
        val header = parseHeader(String(b64ud.decode(parts[0]), StandardCharsets.UTF_8))
        return Metadata(header.alg, header.enc, header.p2c, b64ud.decode(header.p2s).size)
    }

    private data class Header(val alg: String, val enc: String, val p2s: String, val p2c: Int)

    private fun buildHeader(p2s: ByteArray, p2c: Int): String =
        "{\"alg\":\"$ALG\",\"enc\":\"$ENC\",\"p2s\":\"${b64u.encodeToString(p2s)}\",\"p2c\":$p2c}"

    /** Fixed-schema parser: avoids introducing a JSON dependency into the cryptographic primitive. */
    private fun parseHeader(json: String): Header {
        fun stringField(name: String): String {
            val match = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").find(json)
            return match?.groupValues?.get(1) ?: error("Missing JWE header field: $name")
        }
        fun intField(name: String): Int {
            val match = Regex("\\\"${Regex.escape(name)}\\\"\\s*:\\s*(\\d+)").find(json)
            return match?.groupValues?.get(1)?.toInt() ?: error("Missing JWE header field: $name")
        }
        return Header(stringField("alg"), stringField("enc"), stringField("p2s"), intField("p2c"))
    }

    private fun deriveKek(password: CharArray, p2s: ByteArray, p2c: Int): ByteArray {
        val algBytes = ALG.toByteArray(StandardCharsets.US_ASCII)
        val salt = algBytes + byteArrayOf(0) + p2s
        val spec = PBEKeySpec(password, salt, p2c, 128)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(size: Int): ByteArray = ByteArray(size).also(rng::nextBytes)
}

/** Minimal RFC 3394 AES Key Wrap used by the supported JWE PBES2 profile. */
object AesKeyWrap {
    private val IV = byteArrayOf(
        0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(),
        0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte(), 0xA6.toByte()
    )

    fun wrap(kek: ByteArray, keyData: ByteArray): ByteArray {
        require(keyData.size >= 16 && keyData.size % 8 == 0)
        val n = keyData.size / 8
        var a = IV.copyOf()
        val r = Array(n) { i -> keyData.copyOfRange(i * 8, (i + 1) * 8) }
        val aes = Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"))
        for (j in 0..5) {
            for (i in 0 until n) {
                val b = aes.doFinal(a + r[i])
                a = xorT(b.copyOfRange(0, 8), (n * j + i + 1).toLong())
                r[i] = b.copyOfRange(8, 16)
            }
        }
        return a + r.flatMap { it.asIterable() }.toByteArray()
    }

    fun unwrap(kek: ByteArray, wrapped: ByteArray): ByteArray {
        require(wrapped.size >= 24 && wrapped.size % 8 == 0)
        val n = wrapped.size / 8 - 1
        var a = wrapped.copyOfRange(0, 8)
        val r = Array(n) { i -> wrapped.copyOfRange((i + 1) * 8, (i + 2) * 8) }
        val aes = Cipher.getInstance("AES/ECB/NoPadding")
        aes.init(Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"))
        for (j in 5 downTo 0) {
            for (i in n - 1 downTo 0) {
                val block = xorT(a, (n * j + i + 1).toLong()) + r[i]
                val b = aes.doFinal(block)
                a = b.copyOfRange(0, 8)
                r[i] = b.copyOfRange(8, 16)
            }
        }
        require(a.contentEquals(IV)) { "AES Key Wrap integrity check failed." }
        return r.flatMap { it.asIterable() }.toByteArray()
    }

    private fun xorT(a: ByteArray, t: Long): ByteArray {
        val out = a.copyOf()
        val tBytes = ByteBuffer.allocate(8).putLong(t).array()
        for (i in 0 until 8) out[i] = (out[i].toInt() xor tBytes[i].toInt()).toByte()
        return out
    }
}
