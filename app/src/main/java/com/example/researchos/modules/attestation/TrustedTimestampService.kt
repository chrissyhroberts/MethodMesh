package com.example.researchos.modules.attestation

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

/**
 * Opportunistic RFC 3161 timestamping.
 *
 * Only the SHA-256 attestation hash is sent to the authority. A successful
 * response is a transferable, independently verifiable proof that the signed
 * chain head existed no later than the TSA's signed generation time.
 * Failure is deliberately non-fatal: offline attestations remain locally
 * signed and hash chained, and a later timestamped chain head anchors them.
 */
object TrustedTimestampService {
    const val DEFAULT_TSA_URL = "https://freetsa.org/tsr"

    data class Evidence(
        val authorityUrl: String,
        val generationTimeIso: String,
        val serialNumber: String,
        val tokenBase64: String,
        val tokenSha256: String,
        val attestedHash: String
    )

    fun requestIfAvailable(
        attestationHashHex: String,
        authorityUrl: String = DEFAULT_TSA_URL,
        timeoutMs: Int = 3500
    ): Evidence? = runBlocking(Dispatchers.IO) {
        runCatching { request(attestationHashHex, authorityUrl, timeoutMs) }.getOrNull()
    }

    private fun request(attestationHashHex: String, authorityUrl: String, timeoutMs: Int): Evidence {
        val digest = attestationHashHex.hexToBytes()
        require(digest.size == 32) { "Attestation hash must be SHA-256" }

        val nonce = SecureRandom().nextLong().let { if (it == Long.MIN_VALUE) 0L else kotlin.math.abs(it) }
        val request = TimeStampRequestGenerator().apply { setCertReq(true) }
            .generate(NISTObjectIdentifiers.id_sha256, digest, java.math.BigInteger.valueOf(nonce))
            .encoded

        val connection = (URL(authorityUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/timestamp-query")
            setRequestProperty("Accept", "application/timestamp-reply")
            setFixedLengthStreamingMode(request.size)
        }
        try {
            connection.outputStream.use { it.write(request) }
            if (connection.responseCode !in 200..299) {
                error("Timestamp authority returned HTTP ${connection.responseCode}")
            }
            val responseBytes = connection.inputStream.use { it.readBytes() }
            val response = TimeStampResponse(responseBytes)
            response.validate(
                TimeStampRequestGenerator().apply { setCertReq(true) }
                    .generate(NISTObjectIdentifiers.id_sha256, digest, java.math.BigInteger.valueOf(nonce))
            )
            val token = response.timeStampToken ?: error("Timestamp response contained no token")
            val info = token.timeStampInfo
            require(info.messageImprintDigest.contentEquals(digest)) { "Timestamp token does not bind the attestation hash" }
            val encoded = token.encoded
            return Evidence(
                authorityUrl = authorityUrl,
                generationTimeIso = Instant.ofEpochMilli(info.genTime.time).toString(),
                serialNumber = info.serialNumber.toString(16),
                tokenBase64 = Base64.encodeToString(encoded, Base64.NO_WRAP),
                tokenSha256 = encoded.sha256Hex(),
                attestedHash = attestationHashHex
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0)
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    private fun ByteArray.sha256Hex(): String = MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
}
