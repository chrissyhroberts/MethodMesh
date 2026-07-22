package com.example.researchos.modules.attestation

import java.time.Instant
import java.util.UUID

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
    val verificationEvidencePayload: String,
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
    val trustedTimestamp: TrustedTimestampService.Evidence? = null
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
        put("attestation_schema_version", "3")
        put("attestation_id", attestationId)
        put("study_id", studyId)
        put("event_type", eventType)
        put("event_payload_hash", eventPayloadHash)
        put("event_payload_mode", eventPayloadMode)
        put("verification_method", verificationMethod.name)
        put("verification_evidence_payload", verificationEvidencePayload)
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
            verificationEvidenceHash: String,
            deviceEventTimeIso: String,
            deviceMonotonicCounter: Long,
            previousAttestationHash: String,
            publicKeyId: String
        ): String = listOf(
            "attestation_schema_version=3",
            "attestation_id=$attestationId",
            "study_id=$studyId",
            "operator_id=$operatorId",
            "subject_id=$subjectRef",
            "event_type=$eventType",
            "event_payload_hash=$eventPayloadHash",
            "event_payload_mode=$eventPayloadMode",
            "verification_method=$verificationMethod",
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
        verificationEvidence: String,
        trustedTimestampPolicy: TrustedTimestampPolicy = TrustedTimestampPolicy.Disabled
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
        val evidenceHash = AttestationCrypto.sha256Hex(verificationEvidence)
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
            verificationEvidenceHash = evidenceHash,
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
            verificationEvidencePayload = verificationEvidence,
            verificationEvidenceHash = evidenceHash,
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
                val evidence = TrustedTimestampService.requestIfAvailable(
                    attestationHashHex = record.attestationHash,
                    authorityUrl = TrustedTimestampService.DEFAULT_TSA_URL
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
