package com.example.methodmesh.modules.trustedtimestamp

import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.time.Instant

data class TrustedTimestampAuthority(
    val id: String,
    val name: String,
    val endpoint: String,
    val signingCertificateUrl: String? = null,
    val rootCertificateUrl: String? = null,
    val expectedSigningCertificateFileSha256: String? = null,
    val expectedRootCertificateFileSha256: String? = null
)

object TrustedTimestampAuthorities {
    /*
     * Provider-specific trust information belongs here.
     *
     * The hashes below are SHA-256 hashes of the certificate FILES published by
     * FreeTSA in 2026. Certificate rotation is expected and must be maintained.
     *
     * A root certificate bundled in a proof archive is never trusted merely
     * because it is bundled there.
     */
    val FREETSA = TrustedTimestampAuthority(
        id = "freetsa",
        name = "FreeTSA",
        endpoint = "https://freetsa.org/tsr",
        signingCertificateUrl = "https://freetsa.org/files/tsa.crt",
        rootCertificateUrl = "https://freetsa.org/files/cacert.pem",
        expectedSigningCertificateFileSha256 = "8bfb0305bb64e2571ca507552ef3245cb1c2fee8728e0ff8689225081ea13467",
        expectedRootCertificateFileSha256 = "2151b61137ffa86bf664691ba67e7da0b19f98c758e3d228d5d8ebf27e044438"
    )

    fun forEndpoint(url: String): TrustedTimestampAuthority? =
        listOf(FREETSA).firstOrNull { it.endpoint.equals(url.trim(), ignoreCase = true) }
}

data class TimestampSource(
    val displayName: String,
    val sizeBytes: Long,
    val sha256: String
)

data class TimestampCertificateInfo(
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val notBeforeIso: String,
    val notAfterIso: String,
    val sha256Fingerprint: String,
    val extendedKeyUsage: List<String>
)

data class TrustedTimestampEvidence(
    val authorityName: String,
    val authorityUrl: String,
    val generationTimeIso: String,
    val serialNumber: String,
    val policyOid: String,
    val messageImprintAlgorithmOid: String,
    val messageImprintSha256: String,
    val nonce: String?,
    val requestDer: ByteArray,
    val responseDer: ByteArray,
    val tokenSha256: String,
    val signerCertificatePem: String?,
    val signerCertificate: TimestampCertificateInfo?,
    val rootCertificatePem: String?,
    val rootCertificate: TimestampCertificateInfo?,
    val trustStatus: String
)

fun ByteArray.sha256Hex(): String =
    MessageDigest.getInstance("SHA-256").digest(this).toHex()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun X509Certificate.toTimestampCertificateInfo(): TimestampCertificateInfo =
    TimestampCertificateInfo(
        subject = subjectX500Principal.name,
        issuer = issuerX500Principal.name,
        serialNumber = serialNumber.toString(16),
        notBeforeIso = Instant.ofEpochMilli(notBefore.time).toString(),
        notAfterIso = Instant.ofEpochMilli(notAfter.time).toString(),
        sha256Fingerprint = encoded.sha256Hex(),
        extendedKeyUsage = extendedKeyUsage ?: emptyList()
    )

fun proofZipName(originalName: String): String {
    val safe = originalName
        .replace(Regex("""[\\/:*?"<>|]"""), "_")
        .ifBlank { "content" }
    return "${safe}_proof_of_existence.zip"
}
