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
import java.util.UUID

const val DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL = "https://freetsa.org/tsr"

data class TrustedTimestampEvidence(
    val authorityUrl: String,
    val generationTimeIso: String,
    val serialNumber: String,
    val tokenBase64: String,
    val tokenSha256: String,
    val attestedHash: String
)

private fun requestTrustedTimestampIfAvailable(
    attestationHashHex: String,
    authorityUrl: String = DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL,
    timeoutMs: Int = 3500
): TrustedTimestampEvidence? = runBlocking(Dispatchers.IO) {
    runCatching { requestTrustedTimestamp(attestationHashHex, authorityUrl, timeoutMs) }.getOrNull()
}

private fun requestTrustedTimestamp(
    attestationHashHex: String,
    authorityUrl: String,
    timeoutMs: Int
): TrustedTimestampEvidence {
    val digest = attestationHashHex.hexToBytes()
    require(digest.size == 32) { "Attestation hash must be SHA-256" }

    val nonce = SecureRandom().nextLong().let { if (it == Long.MIN_VALUE) 0L else kotlin.math.abs(it) }
    val timestampRequest = TimeStampRequestGenerator().apply { setCertReq(true) }
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
        setFixedLengthStreamingMode(timestampRequest.size)
    }
    try {
        connection.outputStream.use { it.write(timestampRequest) }
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
        return TrustedTimestampEvidence(
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

enum class TrustedTimestampPolicy(val wireValue: String) {
    Disabled("disabled"),
    Preferred("preferred"),
    Required("required");

    companion object {
        fun fromContext(context: Map<String, String>): TrustedTimestampPolicy {
            val raw = context["trusted_timestamp"]?.trim()?.lowercase()

            return when (raw) {
                "preferred" -> Preferred
                "required" -> Required
                null, "", "disabled" -> Disabled
                else -> throw IllegalArgumentException(
                    "trusted_timestamp must be disabled, preferred, or required"
                )
            }
        }
    }
}

enum class AttestationVerificationMethod(val label: String) {
    Fingerprint("fingerprint / biometric"),
    Pin("phone PIN / pattern / password"),
    Qr("QR token"),
    Nfc("NFC tag"),
    Password("study password token")
}

data class AttestationRecord(
    val attestationId: String,
    val studyId: String,
    val operatorId: String,
    val subjectRef: String,
    val eventType: String,
    val eventPayloadHash: String,
    val eventPayloadMode: String,
    val verificationMethod: AttestationVerificationMethod,
    val verificationEvidenceFormat: String,
    val verificationEvidenceHash: String,
    val deviceEventTimeIso: String,
    val deviceEventTimeEpochMs: Long,
    val deviceMonotonicCounter: Long,
    val previousAttestationHash: String,
    val publicKeyId: String,
    val publicKeyAlgorithm: String,
    val publicKeyFormat: String,
    val publicKeyBase64: String,
    val signature: String,
    val trustedTimestampPolicy: TrustedTimestampPolicy = TrustedTimestampPolicy.Disabled,
    val trustedTimestamp: TrustedTimestampEvidence? = null
) {
    val canonicalPayload: String
        get() = canonicalPayload(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = eventPayloadHash,
            eventPayloadMode = eventPayloadMode,
            verificationMethod = verificationMethod.name,
            verificationEvidenceFormat = verificationEvidenceFormat,
            verificationEvidenceHash = verificationEvidenceHash,
            deviceEventTimeIso = deviceEventTimeIso,
            deviceMonotonicCounter = deviceMonotonicCounter,
            previousAttestationHash = previousAttestationHash,
            publicKeyId = publicKeyId
        )

    val attestationHash: String
        get() = AttestationCrypto.sha256Hex(canonicalPayload + "\nsignature=" + signature)

    /**
     * Minimal caller-facing attestation record.
     *
     * Every field here is either required to interpret/verify the attestation or
     * is the requested trusted-timestamp evidence. Internal identifiers and
     * derivable internal metadata remains in the ResearchOS graph and detailed selectors.
     */
    fun asOutputMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("attestation_schema_version", "4")
        put("attestation_id", attestationId)
        put("study_id", studyId)
        put("event_type", eventType)
        put("event_payload_hash", eventPayloadHash)
        put("event_payload_mode", eventPayloadMode)
        put("verification_method", verificationMethod.name)
        put("verification_evidence_format", verificationEvidenceFormat)
        put("verification_evidence_hash", verificationEvidenceHash)
        put("device_event_time_iso", deviceEventTimeIso)
        put("device_monotonic_counter", deviceMonotonicCounter.toString())
        put("previous_attestation_hash", previousAttestationHash)
        put("attestation_hash", attestationHash)
        put("hash_algorithm", "SHA-256")
        put("public_key_id", publicKeyId)
        put("public_key_algorithm", publicKeyAlgorithm)
        put("public_key_format", publicKeyFormat)
        put("public_key_base64", publicKeyBase64)
        put("signature", signature)
        put("signature_algorithm", "SHA256withECDSA")
        put("trusted_timestamp_policy", trustedTimestampPolicy.wireValue)
        put("trusted_timestamp_status", when {
            trustedTimestamp != null -> "rfc3161_verified"
            trustedTimestampPolicy == TrustedTimestampPolicy.Disabled -> "disabled"
            else -> "unavailable"
        })
        trustedTimestamp?.let { timestamp ->
            put("trusted_timestamp_authority", timestamp.authorityUrl)
            put("trusted_timestamp_time_iso", timestamp.generationTimeIso)
            put("trusted_timestamp_serial", timestamp.serialNumber)
            put("trusted_timestamp_attested_hash", timestamp.attestedHash)
            put("trusted_timestamp_token_sha256", timestamp.tokenSha256)
            put("trusted_timestamp_token_base64", timestamp.tokenBase64)
        }
    }


    companion object {
        fun canonicalPayload(
            attestationId: String,
            studyId: String,
            operatorId: String,
            subjectRef: String,
            eventType: String,
            eventPayloadHash: String,
            eventPayloadMode: String,
            verificationMethod: String,
            verificationEvidenceFormat: String,
            verificationEvidenceHash: String,
            deviceEventTimeIso: String,
            deviceMonotonicCounter: Long,
            previousAttestationHash: String,
            publicKeyId: String
        ): String = listOf(
            "attestation_schema_version=4",
            "attestation_id=$attestationId",
            "study_id=$studyId",
            "operator_id=$operatorId",
            "subject_id=$subjectRef",
            "event_type=$eventType",
            "event_payload_hash=$eventPayloadHash",
            "event_payload_mode=$eventPayloadMode",
            "verification_method=$verificationMethod",
            "verification_evidence_format=$verificationEvidenceFormat",
            "verification_evidence_hash=$verificationEvidenceHash",
            "device_event_time_iso=$deviceEventTimeIso",
            "device_monotonic_counter=$deviceMonotonicCounter",
            "previous_attestation_hash=$previousAttestationHash",
            "public_key_id=$publicKeyId",
            "hash_algorithm=SHA-256",
            "signature_algorithm=SHA256withECDSA"
        ).joinToString("\n")
    }
}

data class AttestationAnchorBundle(
    val anchorId: String,
    val studyId: String,
    val operatorId: String,
    val publicKeyId: String,
    val publicKeyBase64: String,
    val lastAnchoredHash: String,
    val currentChainHeadHash: String,
    val firstUnanchoredEventTimeIso: String,
    val lastUnanchoredEventTimeIso: String,
    val attestationCount: Int,
    val bundlePayloadHash: String,
    val bundleSignature: String,
    val createdDeviceTimeIso: String
) {
    fun asOdkFields(): Map<String, String> = linkedMapOf(
        "anchor_id" to anchorId,
        "study_id" to studyId,
        "operator_id" to operatorId,
        "public_key_id" to publicKeyId,
        "public_key_base64" to publicKeyBase64,
        "last_anchor_hash" to lastAnchoredHash,
        "current_chain_head_hash" to currentChainHeadHash,
        "number_of_attestations" to attestationCount.toString(),
        "first_unanchored_attestation_time" to firstUnanchoredEventTimeIso,
        "last_unanchored_attestation_time" to lastUnanchoredEventTimeIso,
        "attestation_record_bundle_hash" to bundlePayloadHash,
        "bundle_signature" to bundleSignature,
        "created_device_time_iso" to createdDeviceTimeIso,
        "signature_algorithm" to "SHA256withECDSA"
    )
}

object AttestationRepository {
    private const val GENESIS_HASH = "GENESIS"
    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")
    private val records = mutableListOf<AttestationRecord>()
    private var lastAnchoredHash: String = GENESIS_HASH

    fun allRecords(): List<AttestationRecord> = records.toList()
    fun headHash(): String = records.lastOrNull()?.attestationHash ?: lastAnchoredHash
    fun lastAnchorHash(): String = lastAnchoredHash
    fun unanchoredRecords(): List<AttestationRecord> = records.dropWhile { it.attestationHash != lastAnchoredHash }.let { tail ->
        if (lastAnchoredHash == GENESIS_HASH) records.toList() else tail.drop(1)
    }

    fun createRecord(
        studyId: String,
        operatorId: String,
        subjectRef: String,
        eventType: String,
        eventPayloadHash: String?,
        verificationMethod: AttestationVerificationMethod,
        verificationEvidence: AttestationEvidence,
        trustedTimestampPolicy: TrustedTimestampPolicy = TrustedTimestampPolicy.Disabled,
        trustedTimestampAuthorityUrl: String = DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL,
        trustedTimestampTimeoutMs: Int = 3500
    ): AttestationRecord {
        AttestationCrypto.ensureKeyPair()
        val now = System.currentTimeMillis()
        val counter = records.size + 1L
        val previous = headHash()
        val publicKeyId = AttestationCrypto.publicKeyId()
        val publicKeyBase64 = AttestationCrypto.publicKeyBase64()
        val suppliedHash = eventPayloadHash?.trim().orEmpty()
        require(SHA256_HEX.matches(suppliedHash)) {
            "attestation.create requires event_payload_hash as a 64-character hexadecimal SHA-256 digest"
        }
        val payloadHash = suppliedHash.lowercase()
        val payloadMode = "supplied_hash"
        val attestationId = "att_${UUID.randomUUID()}"
        val canonical = AttestationRecord.canonicalPayload(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = payloadHash,
            eventPayloadMode = payloadMode,
            verificationMethod = verificationMethod.name,
            verificationEvidenceFormat = verificationEvidence.format,
            verificationEvidenceHash = verificationEvidence.hash,
            deviceEventTimeIso = Instant.ofEpochMilli(now).toString(),
            deviceMonotonicCounter = counter,
            previousAttestationHash = previous,
            publicKeyId = publicKeyId
        )
        val record = AttestationRecord(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = payloadHash,
            eventPayloadMode = payloadMode,
            verificationMethod = verificationMethod,
            verificationEvidenceFormat = verificationEvidence.format,
            verificationEvidenceHash = verificationEvidence.hash,
            deviceEventTimeIso = Instant.ofEpochMilli(now).toString(),
            deviceEventTimeEpochMs = now,
            deviceMonotonicCounter = counter,
            previousAttestationHash = previous,
            publicKeyId = publicKeyId,
            publicKeyAlgorithm = "EC",
            publicKeyFormat = "X.509",
            publicKeyBase64 = publicKeyBase64,
            signature = AttestationCrypto.signCanonical(canonical),
            trustedTimestampPolicy = trustedTimestampPolicy
        )
        val timestamped = when (trustedTimestampPolicy) {
            TrustedTimestampPolicy.Disabled -> record
            TrustedTimestampPolicy.Preferred, TrustedTimestampPolicy.Required -> {
                val evidence = requestTrustedTimestampIfAvailable(
                    attestationHashHex = record.attestationHash,
                    authorityUrl = trustedTimestampAuthorityUrl.ifBlank { DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL },
                    timeoutMs = trustedTimestampTimeoutMs.coerceIn(1000, 30000)
                )
                if (trustedTimestampPolicy == TrustedTimestampPolicy.Required && evidence == null) {
                    throw TrustedTimestampRequiredException(
                        "A trusted RFC 3161 timestamp was required but could not be obtained."
                    )
                }
                record.copy(trustedTimestamp = evidence)
            }
        }
        records += timestamped
        return timestamped
    }

    fun createAnchorBundle(studyId: String, operatorId: String): AttestationAnchorBundle {
        AttestationCrypto.ensureKeyPair()
        val unanchored = unanchoredRecords()
        val head = headHash()
        val payload = unanchored.joinToString("\n---\n") { it.asOutputMap().entries.joinToString("\n") { e -> "${e.key}=${e.value}" } }
        val payloadHash = AttestationCrypto.sha256Hex(payload)
        val created = Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        val canonical = listOf(
            "study_id=$studyId",
            "operator_id=$operatorId",
            "public_key_id=${AttestationCrypto.publicKeyId()}",
            "last_anchor_hash=$lastAnchoredHash",
            "current_chain_head_hash=$head",
            "attestation_count=${unanchored.size}",
            "attestation_record_bundle_hash=$payloadHash",
            "created_device_time_iso=$created"
        ).joinToString("\n")
        val bundle = AttestationAnchorBundle(
            anchorId = "anchor_${UUID.randomUUID()}",
            studyId = studyId,
            operatorId = operatorId,
            publicKeyId = AttestationCrypto.publicKeyId(),
            publicKeyBase64 = AttestationCrypto.publicKeyBase64(),
            lastAnchoredHash = lastAnchoredHash,
            currentChainHeadHash = head,
            firstUnanchoredEventTimeIso = unanchored.firstOrNull()?.deviceEventTimeIso.orEmpty(),
            lastUnanchoredEventTimeIso = unanchored.lastOrNull()?.deviceEventTimeIso.orEmpty(),
            attestationCount = unanchored.size,
            bundlePayloadHash = payloadHash,
            bundleSignature = AttestationCrypto.signCanonical(canonical),
            createdDeviceTimeIso = created
        )
        lastAnchoredHash = head
        return bundle
    }
}

class TrustedTimestampRequiredException(message: String) : IllegalStateException(message)
