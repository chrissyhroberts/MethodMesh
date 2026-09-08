package com.example.methodmesh.modules.cryptography

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.ln

object CryptoEncoding {
    fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }
    fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
    fun base64UrlDecode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}

object HashEngine {
    private val supported = setOf("SHA-256", "SHA-512")

    fun digest(bytes: ByteArray, algorithm: String = "SHA-256"): ByteArray {
        require(algorithm in supported) { "Unsupported digest algorithm: $algorithm" }
        return MessageDigest.getInstance(algorithm).digest(bytes)
    }

    fun hex(bytes: ByteArray, algorithm: String = "SHA-256"): String =
        with(CryptoEncoding) { digest(bytes, algorithm).hex() }

    fun verify(bytes: ByteArray, expectedHex: String, algorithm: String = "SHA-256"): Boolean =
        MessageDigest.isEqual(
            digest(bytes, algorithm),
            expectedHex.trim().lowercase().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        )

    fun hex(input: java.io.InputStream, algorithm: String = "SHA-256"): String {
        require(algorithm in supported) { "Unsupported digest algorithm: $algorithm" }
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
        return with(CryptoEncoding) { digest.digest().hex() }
    }

    fun hex(file: java.io.File, algorithm: String = "SHA-256"): String =
        file.inputStream().buffered().use { hex(it, algorithm) }
}

data class GeneratedSecret(
    val value: String,
    val entropyBits: Double,
    val mode: String,
    val alphabetSize: Int? = null
)

object PasswordEngine {
    private val rng = SecureRandom()
    private const val LOWER = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#%+=_-:,.?"
    private const val ALNUM = LOWER + UPPER + DIGITS
    private const val ALL = ALNUM + SYMBOLS
    private const val HEX = "0123456789abcdef"
    private val consonants = "bcdfghjklmnprstvwxyz".toCharArray()
    private val vowels = "aeiouy".toCharArray()

    fun random(length: Int = 20, includeSymbols: Boolean = true): GeneratedSecret {
        require(length in 4..512)
        val alphabet = if (includeSymbols) ALL else ALNUM
        return fromAlphabet(length, alphabet, if (includeSymbols) "random" else "alphanumeric")
    }

    fun pin(length: Int = 8): GeneratedSecret {
        require(length in 4..64)
        return fromAlphabet(length, "0123456789", "pin")
    }

    fun hex(bytes: Int = 32): GeneratedSecret {
        require(bytes in 4..256)
        val raw = ByteArray(bytes).also(rng::nextBytes)
        return GeneratedSecret(
            value = with(CryptoEncoding) { raw.hex() },
            entropyBits = bytes * 8.0,
            mode = "hex",
            alphabetSize = HEX.length
        )
    }

    fun token(bytes: Int = 32): GeneratedSecret {
        require(bytes in 4..256)
        val raw = ByteArray(bytes).also(rng::nextBytes)
        return GeneratedSecret(
            value = with(CryptoEncoding) { raw.base64Url() },
            entropyBits = bytes * 8.0,
            mode = "base64url_token"
        )
    }

    /**
     * Human-enterable random phrase without a bundled dictionary dependency.
     * Each pseudo-word is four uniformly selected CV syllables, then words are
     * independently selected. Entropy is derived from the actual choice space.
     */
    fun syllablePhrase(words: Int = 6, separator: String = "-"): GeneratedSecret {
        require(words in 3..20)
        val syllableChoices = consonants.size * vowels.size
        val sb = StringBuilder()
        repeat(words) { wordIndex ->
            if (wordIndex > 0) sb.append(separator)
            repeat(4) {
                sb.append(consonants[rng.nextInt(consonants.size)])
                sb.append(vowels[rng.nextInt(vowels.size)])
            }
        }
        val entropy = words * 4 * log2(syllableChoices.toDouble())
        return GeneratedSecret(sb.toString(), entropy, "syllable_phrase", syllableChoices)
    }

    private fun fromAlphabet(length: Int, alphabet: String, mode: String): GeneratedSecret {
        val value = CharArray(length) { alphabet[rng.nextInt(alphabet.length)] }.concatToString()
        return GeneratedSecret(value, length * log2(alphabet.length.toDouble()), mode, alphabet.length)
    }

    private fun log2(value: Double): Double = ln(value) / ln(2.0)
}

data class TotpAccount(
    val issuer: String,
    val accountName: String,
    val secretBase32: String,
    val digits: Int = 6,
    val periodSeconds: Int = 30,
    val algorithm: String = "SHA1"
) {
    val label: String get() = if (issuer.isBlank()) accountName else "$issuer: $accountName"
}

data class TotpCode(val code: String, val validForSeconds: Int, val counter: Long)

object TotpEngine {
    private const val BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun generate(account: TotpAccount, epochSeconds: Long = System.currentTimeMillis() / 1000): TotpCode {
        require(account.digits in 6..8)
        require(account.periodSeconds in 5..300)
        val secret = decodeBase32(account.secretBase32)
        val counter = epochSeconds / account.periodSeconds
        val algo = when (account.algorithm.uppercase().replace("-", "")) {
            "SHA1" -> "HmacSHA1"
            "SHA256" -> "HmacSHA256"
            "SHA512" -> "HmacSHA512"
            else -> error("Unsupported TOTP algorithm: ${account.algorithm}")
        }
        val msg = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            msg[i] = (value and 0xff).toByte()
            value = value ushr 8
        }
        val mac = Mac.getInstance(algo)
        mac.init(SecretKeySpec(secret, algo))
        val h = mac.doFinal(msg)
        val offset = h.last().toInt() and 0x0f
        val binary = ((h[offset].toInt() and 0x7f) shl 24) or
            ((h[offset + 1].toInt() and 0xff) shl 16) or
            ((h[offset + 2].toInt() and 0xff) shl 8) or
            (h[offset + 3].toInt() and 0xff)
        val modulus = pow10(account.digits)
        val code = (binary.toLong() % modulus).toString().padStart(account.digits, '0')
        val remaining = account.periodSeconds - (epochSeconds % account.periodSeconds).toInt()
        return TotpCode(code, remaining, counter)
    }

    fun parseOtpAuth(uri: String): TotpAccount {
        val parsed = java.net.URI(uri.trim())
        require(parsed.scheme.equals("otpauth", true)) { "Not an otpauth URI." }
        require(parsed.host.equals("totp", true)) { "Only TOTP is supported by this capability." }
        val rawLabel = java.net.URLDecoder.decode(parsed.path.removePrefix("/"), "UTF-8")
        val params = parsed.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate { pair ->
            val parts = pair.split('=', limit = 2)
            java.net.URLDecoder.decode(parts[0], "UTF-8") to
                java.net.URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
        }
        val issuerFromLabel = rawLabel.substringBefore(':', "").trim()
        val accountName = rawLabel.substringAfter(':', rawLabel).trim()
        val issuer = params["issuer"].orEmpty().ifBlank { issuerFromLabel }
        val secret = params["secret"].orEmpty().replace(" ", "").uppercase()
        require(secret.isNotBlank()) { "otpauth URI has no secret." }
        return TotpAccount(
            issuer = issuer,
            accountName = accountName,
            secretBase32 = secret,
            digits = params["digits"]?.toIntOrNull() ?: 6,
            periodSeconds = params["period"]?.toIntOrNull() ?: 30,
            algorithm = params["algorithm"] ?: "SHA1"
        )
    }

    fun toOtpAuth(account: TotpAccount): String {
        val label = if (account.issuer.isBlank()) account.accountName else "${account.issuer}:${account.accountName}"
        fun enc(v: String) = java.net.URLEncoder.encode(v, "UTF-8").replace("+", "%20")
        return buildString {
            append("otpauth://totp/")
            append(enc(label))
            append("?secret=").append(enc(account.secretBase32.replace(" ", "").uppercase()))
            if (account.issuer.isNotBlank()) append("&issuer=").append(enc(account.issuer))
            append("&algorithm=").append(enc(account.algorithm.uppercase().replace("-", "")))
            append("&digits=").append(account.digits)
            append("&period=").append(account.periodSeconds)
        }
    }

    private fun decodeBase32(value: String): ByteArray {
        val cleaned = value.uppercase().filter { !it.isWhitespace() && it != '=' }
        var buffer = 0
        var bitsLeft = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in cleaned) {
            val index = BASE32.indexOf(c)
            require(index >= 0) { "Invalid Base32 character: $c" }
            buffer = (buffer shl 5) or index
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.write((buffer shr bitsLeft) and 0xff)
            }
        }
        return out.toByteArray()
    }

    private fun pow10(n: Int): Long {
        var out = 1L
        repeat(n) { out *= 10L }
        return out
    }
}
