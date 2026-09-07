package com.example.methodmesh.modules.digitalsigning

import android.util.Base64
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.TSPAlgorithms
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.security.SecureRandom

object DigitalSigningTsaClient {
    const val DEFAULT_TSA_URL = "https://freetsa.org/tsr"
    private const val CONNECT_TIMEOUT_MS = 12_000
    private const val READ_TIMEOUT_MS = 20_000

    fun timestamp(tsaUrl: String = DEFAULT_TSA_URL, signedSha256Hex: String): TsaAttestation {
        val resolvedTsaUrl = tsaUrl.trim().ifBlank { DEFAULT_TSA_URL }

        return runCatching {
            val imprint = DigitalSigningHash.hexToBytes(signedSha256Hex)
            require(imprint.size == 32) { "Signed PDF SHA-256 is invalid." }

            val generator = TimeStampRequestGenerator().apply { setCertReq(true) }
            val nonce = BigInteger(128, SecureRandom()).abs()
            val request = generator.generate(TSPAlgorithms.SHA256, imprint, nonce)
            val responseBytes = postTimestampRequest(resolvedTsaUrl, request.encoded)
            val response = TimeStampResponse(responseBytes)
            response.validate(request)

            val token = response.timeStampToken
                ?: error("The TSA response did not contain a timestamp token (status ${response.status}: ${response.statusString.orEmpty()}).")
            val info = token.timeStampInfo
            val imprintMatches = info.messageImprintDigest.contentEquals(imprint)
            require(imprintMatches) { "The TSA token message imprint does not match the signed PDF SHA-256." }

            // Avoid Store.getMatches(token.sid): on the current Bouncy Castle Kotlin
            // surface SignerId and Selector generics do not line up cleanly. Retrieve
            // the included certificates, then ask SignerId to match each holder.
            val includedCertificates = token.certificates.getMatches(null)
            val signerHolder: X509CertificateHolder = includedCertificates
                .firstOrNull { holder: X509CertificateHolder -> token.sid.match(holder) }
                ?: error("The TSA token did not include its signer certificate.")

            val provider = BouncyCastleProvider()
            val verifier = JcaSimpleSignerInfoVerifierBuilder()
                .setProvider(provider)
                .build(signerHolder)
            token.validate(verifier)

            val certificate = JcaX509CertificateConverter()
                .setProvider(provider)
                .getCertificate(signerHolder)
            val certificateValidAtTimestamp = runCatching {
                certificate.checkValidity(info.genTime)
                true
            }.getOrDefault(false)

            TsaAttestation(
                requested = true,
                status = "verified",
                authority = authorityLabel(resolvedTsaUrl, info.tsa?.toString()),
                timestampUtc = info.genTime.toInstant().toString(),
                messageImprintAlgorithm = info.messageImprintAlgOID.id,
                messageImprintHex = signedSha256Hex,
                policyOid = info.policy.id,
                serialNumber = info.serialNumber.toString(),
                tokenBase64 = Base64.encodeToString(token.encoded, Base64.NO_WRAP),
                signatureValid = true,
                imprintMatchesSignedPdf = true,
                certificateValidAtTimestamp = certificateValidAtTimestamp,
                error = null
            )
        }.getOrElse { error ->
            TsaAttestation(
                requested = true,
                status = "failed",
                authority = runCatching { URI(resolvedTsaUrl).host }.getOrNull(),
                messageImprintHex = signedSha256Hex,
                signatureValid = false,
                imprintMatchesSignedPdf = null,
                certificateValidAtTimestamp = null,
                error = error.message ?: error.javaClass.simpleName
            )
        }
    }

    private fun postTimestampRequest(tsaUrl: String, body: ByteArray): ByteArray {
        val connection = (URL(tsaUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/timestamp-query")
            setRequestProperty("Accept", "application/timestamp-reply")
            setFixedLengthStreamingMode(body.size)
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            require(status in 200..299) { "TSA HTTP $status${bytes.decodeToString().take(160).let { if (it.isBlank()) "" else ": $it" }}" }
            require(bytes.isNotEmpty()) { "TSA returned an empty response." }
            bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun authorityLabel(url: String, tokenTsaName: String?): String {
        if (!tokenTsaName.isNullOrBlank()) return tokenTsaName
        return runCatching { URI(url).host }.getOrNull().orEmpty().ifBlank { url }
    }
}
