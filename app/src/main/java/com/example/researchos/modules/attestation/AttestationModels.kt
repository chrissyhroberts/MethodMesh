package com.example.researchos.modules.attestation

import java.time.Instant
import java.util.UUID

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
    val verificationMethod: AttestationVerificationMethod,
    val verificationEvidencePayload: String,
    val verificationEvidenceHash: String,
    val deviceEventTimeIso: String,
    val deviceEventTimeEpochMs: Long,
    val deviceMonotonicCounter: Long,
    val previousAttestationHash: String,
    val publicKeyId: String,
    val keyAlias: String,
    val signature: String
) {
    val canonicalPayload: String
        get() = canonicalPayload(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = eventPayloadHash,
            verificationMethod = verificationMethod.name,
            verificationEvidenceHash = verificationEvidenceHash,
            deviceEventTimeIso = deviceEventTimeIso,
            deviceEventTimeEpochMs = deviceEventTimeEpochMs,
            deviceMonotonicCounter = deviceMonotonicCounter,
            previousAttestationHash = previousAttestationHash,
            publicKeyId = publicKeyId,
            keyAlias = keyAlias
        )

    val attestationHash: String
        get() = AttestationCrypto.sha256Hex(canonicalPayload + "\nsignature=" + signature)

    fun asOutputMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("attestation_id", attestationId)
        put("study_id", studyId)
        put("operator_id", operatorId)
        put("subject_ref", subjectRef)
        put("event_type", eventType)
        put("event_payload_hash", eventPayloadHash)
        put("verification_method", verificationMethod.name)
        put("verification_evidence_payload", verificationEvidencePayload)
        put("verification_evidence_hash", verificationEvidenceHash)
        if (verificationMethod == AttestationVerificationMethod.Qr) {
            put("qr_payload", verificationEvidencePayload)
            put("qr_payload_hash", verificationEvidenceHash)
        }
        put("device_event_time_iso", deviceEventTimeIso)
        put("device_event_time_epoch_ms", deviceEventTimeEpochMs.toString())
        put("device_monotonic_counter", deviceMonotonicCounter.toString())
        put("previous_attestation_hash", previousAttestationHash)
        put("attestation_hash", attestationHash)
        put("public_key_id", publicKeyId)
        put("key_alias", keyAlias)
        put("signature", signature)
        put("signature_algorithm", "SHA256withECDSA")
        put("evidence_integrity_rule", "sha256(verification_evidence_payload)=verification_evidence_hash")
    }


    companion object {
        fun canonicalPayload(
            attestationId: String,
            studyId: String,
            operatorId: String,
            subjectRef: String,
            eventType: String,
            eventPayloadHash: String,
            verificationMethod: String,
            verificationEvidenceHash: String,
            deviceEventTimeIso: String,
            deviceEventTimeEpochMs: Long,
            deviceMonotonicCounter: Long,
            previousAttestationHash: String,
            publicKeyId: String,
            keyAlias: String
        ): String = listOf(
            "attestation_id=$attestationId",
            "study_id=$studyId",
            "operator_id=$operatorId",
            "subject_ref=$subjectRef",
            "event_type=$eventType",
            "event_payload_hash=$eventPayloadHash",
            "verification_method=$verificationMethod",
            "verification_evidence_hash=$verificationEvidenceHash",
            "device_event_time_iso=$deviceEventTimeIso",
            "device_event_time_epoch_ms=$deviceEventTimeEpochMs",
            "device_monotonic_counter=$deviceMonotonicCounter",
            "previous_attestation_hash=$previousAttestationHash",
            "public_key_id=$publicKeyId",
            "key_alias=$keyAlias"
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
        eventPayload: String,
        verificationMethod: AttestationVerificationMethod,
        verificationEvidence: String
    ): AttestationRecord {
        AttestationCrypto.ensureKeyPair()
        val now = System.currentTimeMillis()
        val counter = records.size + 1L
        val previous = headHash()
        val publicKeyId = AttestationCrypto.publicKeyId()
        val payloadHash = AttestationCrypto.sha256Hex(eventPayload)
        val evidenceHash = AttestationCrypto.sha256Hex(verificationEvidence)
        val attestationId = "att_${UUID.randomUUID()}"
        val canonical = AttestationRecord.canonicalPayload(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = payloadHash,
            verificationMethod = verificationMethod.name,
            verificationEvidenceHash = evidenceHash,
            deviceEventTimeIso = Instant.ofEpochMilli(now).toString(),
            deviceEventTimeEpochMs = now,
            deviceMonotonicCounter = counter,
            previousAttestationHash = previous,
            publicKeyId = publicKeyId,
            keyAlias = AttestationCrypto.keyAlias()
        )
        val record = AttestationRecord(
            attestationId = attestationId,
            studyId = studyId,
            operatorId = operatorId,
            subjectRef = subjectRef,
            eventType = eventType,
            eventPayloadHash = payloadHash,
            verificationMethod = verificationMethod,
            verificationEvidencePayload = verificationEvidence,
            verificationEvidenceHash = evidenceHash,
            deviceEventTimeIso = Instant.ofEpochMilli(now).toString(),
            deviceEventTimeEpochMs = now,
            deviceMonotonicCounter = counter,
            previousAttestationHash = previous,
            publicKeyId = publicKeyId,
            keyAlias = AttestationCrypto.keyAlias(),
            signature = AttestationCrypto.signCanonical(canonical)
        )
        records += record
        return record
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
