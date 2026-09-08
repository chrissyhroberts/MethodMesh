package com.example.methodmesh.modules.trustedtimestamp

import android.content.ContentResolver
import android.net.Uri
import android.util.Base64
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.SignerInformationVerifier
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import org.bouncycastle.tsp.TimeStampRequest
import org.bouncycastle.tsp.TimeStampRequestGenerator
import org.bouncycastle.tsp.TimeStampResponse
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object TrustedTimestampEngine {

    fun hashUri(contentResolver: ContentResolver, uri: Uri): Pair<Long, ByteArray> {
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L
        contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open selected file." }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                size += count
            }
        }
        return size to digest.digest()
    }

    fun hashTextUtf8(text: String): Pair<Long, ByteArray> {
        val bytes = text.toByteArray(Charsets.UTF_8)
        return bytes.size.toLong() to MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun requestTimestamp(
        digest: ByteArray,
        authorityUrl: String,
        timeoutMs: Int
    ): Pair<TimeStampRequest, ByteArray> {
        require(digest.size == 32) { "Digest must be SHA-256." }

        val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = BigInteger(1, nonceBytes)
        val request = TimeStampRequestGenerator()
            .apply { setCertReq(true) }
            .generate(NISTObjectIdentifiers.id_sha256, digest, nonce)
        val requestDer = request.encoded

        val connection = (URL(authorityUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs.coerceIn(1000, 30000)
            readTimeout = timeoutMs.coerceIn(1000, 30000)
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "application/timestamp-query")
            setRequestProperty("Accept", "application/timestamp-reply")
            setFixedLengthStreamingMode(requestDer.size)
        }

        try {
            connection.outputStream.use { it.write(requestDer) }
            if (connection.responseCode !in 200..299) {
                error("Timestamp authority returned HTTP ${connection.responseCode}.")
            }
            return request to connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun parseAndValidate(
        request: TimeStampRequest,
        responseDer: ByteArray,
        digest: ByteArray,
        authorityUrl: String,
        timeoutMs: Int
    ): TrustedTimestampEvidence {
        val response = TimeStampResponse(responseDer)
        response.validate(request)

        val token = response.timeStampToken
            ?: error("Timestamp response contains no RFC 3161 timestamp token.")
        val info = token.timeStampInfo

        require(info.messageImprintDigest.contentEquals(digest)) {
            "Timestamp token message imprint does not match the source digest."
        }

        val signerHolder = token.certificates
            .getMatches(null)
            .filterIsInstance<X509CertificateHolder>()
            .firstOrNull { holder ->
                token.sid.match(holder)
            }

        val signerCertificate = signerHolder?.let {
            JcaX509CertificateConverter().getCertificate(it)
        }

        val signatureValid = signerHolder?.let {
            val verifier: SignerInformationVerifier =
                JcaSimpleSignerInfoVerifierBuilder().build(it)
            token.validate(verifier)
            true
        } ?: false

        val authority = TrustedTimestampAuthorities.forEndpoint(authorityUrl)
        val publishedSigner = authority?.signingCertificateUrl?.let { url ->
            runCatching { downloadCertificate(url, timeoutMs) }.getOrNull()
        }
        val publishedRoot = authority?.rootCertificateUrl?.let { url ->
            runCatching { downloadCertificate(url, timeoutMs) }.getOrNull()
        }

        val packagedSignerPem = publishedSigner?.pem ?: signerCertificate?.toPem()
        val packagedSignerCertificate =
            packagedSignerPem?.let(::parseCertificate) ?: signerCertificate
        val packagedRootCertificate =
            publishedRoot?.pem?.let(::parseCertificate)

        val signerPinOk = authority?.expectedSigningCertificateFileSha256?.let { expected ->
            publishedSigner?.downloadedFileSha256?.equals(expected, ignoreCase = true) == true
        }
        val rootPinOk = authority?.expectedRootCertificateFileSha256?.let { expected ->
            publishedRoot?.downloadedFileSha256?.equals(expected, ignoreCase = true) == true
        }

        val trustStatus = when {
            !signatureValid -> "signature_invalid"
            authority == null -> "cryptographically_valid_unconfigured_trust"
            signerPinOk == true && rootPinOk == true -> "trusted_registry_match"
            else -> "trusted_registry_mismatch"
        }

        return TrustedTimestampEvidence(
            authorityName = authority?.name ?: authorityUrl,
            authorityUrl = authorityUrl,
            generationTimeIso = info.genTime.toInstant().toString(),
            serialNumber = info.serialNumber.toString(16),
            policyOid = info.policy.toString(),
            messageImprintAlgorithmOid = info.messageImprintAlgOID.id,
            messageImprintSha256 = info.messageImprintDigest.toHex(),
            nonce = request.nonce?.toString(),
            requestDer = request.encoded,
            responseDer = responseDer,
            tokenSha256 = token.encoded.sha256Hex(),
            signerCertificatePem = packagedSignerPem,
            signerCertificate = packagedSignerCertificate?.toTimestampCertificateInfo(),
            rootCertificatePem = publishedRoot?.pem,
            rootCertificate = packagedRootCertificate?.toTimestampCertificateInfo(),
            trustStatus = trustStatus
        )
    }

    fun createProofZip(
        source: TimestampSource,
        evidence: TrustedTimestampEvidence,
        originalTextUtf8: ByteArray? = null
    ): ByteArray {
        val entries = linkedMapOf<String, ByteArray>()
        entries["timestamp.tsq"] = evidence.requestDer
        entries["timestamp.tsr"] = evidence.responseDer
        evidence.signerCertificatePem?.let {
            entries["tsa-signing-cert.pem"] = it.toByteArray(Charsets.UTF_8)
        }
        evidence.rootCertificatePem?.let {
            entries["tsa-root.pem"] = it.toByteArray(Charsets.UTF_8)
        }
        originalTextUtf8?.let {
            entries["timestamped-text.txt"] = it
        }

        val componentHashes = entries.mapValues { (_, bytes) -> bytes.sha256Hex() }
        entries["proof.json"] = ProofText.proofJson(source, evidence, componentHashes)
            .toByteArray(Charsets.UTF_8)
        entries["README.txt"] = ProofText.readme(source, evidence)
            .toByteArray(Charsets.UTF_8)
        entries["verify.sh"] = ProofText.verifySh(source, evidence)
            .toByteArray(Charsets.UTF_8)
        entries["verify.ps1"] = ProofText.verifyPowerShell(source)
            .toByteArray(Charsets.UTF_8)

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            output.toByteArray()
        }
    }

    private data class DownloadedCertificate(
        val pem: String,
        val downloadedFileSha256: String
    )

    private fun downloadCertificate(url: String, timeoutMs: Int): DownloadedCertificate {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = timeoutMs.coerceIn(1000, 30000)
        connection.readTimeout = timeoutMs.coerceIn(1000, 30000)
        connection.useCaches = false

        return try {
            val bytes = connection.inputStream.use { it.readBytes() }
            val text = bytes.toString(Charsets.US_ASCII)
            val pem = if (text.contains("BEGIN CERTIFICATE")) {
                text
            } else {
                parseCertificate(bytes).toPem()
            }
            DownloadedCertificate(
                pem = pem,
                downloadedFileSha256 = bytes.sha256Hex()
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCertificate(pem: String): X509Certificate =
        parseCertificate(pem.toByteArray(Charsets.US_ASCII))

    private fun parseCertificate(bytes: ByteArray): X509Certificate =
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate

    private fun X509Certificate.toPem(): String {
        val encodedText = Base64.encodeToString(encoded, Base64.NO_WRAP)
        return buildString {
            appendLine("-----BEGIN CERTIFICATE-----")
            encodedText.chunked(64).forEach { appendLine(it) }
            appendLine("-----END CERTIFICATE-----")
        }
    }
}
