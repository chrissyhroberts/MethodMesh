package com.example.researchos.modules.attestation

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.settings.SettingsState

object As100CreateAttestationMethod : As100Method {
    const val ID = "attestation.create"
    private const val VERSION = "1.0.0"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Create signed attestation")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Create signed attestation",
        version = VERSION,
        description = "Create a tamper-evident event attestation signed by the phone's non-exportable private key.",
        outputs = listOf(
            "attestation_schema_version", "attestation_id", "study_id", "event_type",
            "event_payload_hash", "event_payload_mode", "verification_method", "verification_evidence_format", "verification_evidence_hash",
            "device_event_time_iso", "device_monotonic_counter", "previous_attestation_hash",
            "attestation_hash", "hash_algorithm", "public_key_id", "public_key_algorithm",
            "public_key_format", "public_key_base64", "signature", "signature_algorithm",
            "trusted_timestamp_policy", "trusted_timestamp_status", "trusted_timestamp_authority", "trusted_timestamp_time_iso",
            "trusted_timestamp_serial", "trusted_timestamp_attested_hash", "trusted_timestamp_token_sha256",
            "trusted_timestamp_token_base64"
        ),
        graphOutputs = listOf("attestation.signed_event"),
        parameters = mapOf("category" to "Attestation", "status" to "Experimental")
    )
    override val contract: MethodContract = MethodContract(
        method = ref,
        requiredContext = listOf(
            "event_payload_hash",
            "verification_method",
            "verification_evidence_format",
            "verification_evidence_hash"
        ),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val c = request.context
        val method = c["verification_method"]?.takeIf { it.isNotBlank() }?.let { raw ->
            AttestationVerificationMethod.values().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        } ?: return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Failed,
            diagnostics = mapOf("reason" to "attestation.create requires a valid verification_method")
        )
        val timestampPolicy = try {
            TrustedTimestampPolicy.fromContext(c)
        } catch (error: IllegalArgumentException) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Failed,
                diagnostics = mapOf("reason" to (error.message ?: "Invalid trusted timestamp policy"))
            )
        }
        val verificationEvidence = try {
            AttestationEvidence(
                format = c["verification_evidence_format"].orEmpty(),
                hash = c["verification_evidence_hash"].orEmpty().lowercase()
            )
        } catch (error: IllegalArgumentException) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Failed,
                diagnostics = mapOf("reason" to (error.message ?: "Invalid verification evidence"))
            )
        }
        val trustedTimestampAuthority = c["trusted_timestamp_authority"]
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: c["timestamp_authority"]?.trim()?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL
        val trustedTimestampTimeoutMs = c["trusted_timestamp_timeout_ms"]
            ?.trim()
            ?.toIntOrNull()
            ?: 3500
        val record = try {
            AttestationRepository.createRecord(
                studyId = c["study_id"].orEmpty(),
                operatorId = c["operator_id"].orEmpty(),
                subjectRef = c["subject_ref"].orEmpty().ifBlank { InvocationContext.from(c)?.subjectRef()?.id?.value.orEmpty() },
                eventType = c["event_type"].orEmpty(),
                eventPayloadHash = c["event_payload_hash"],
                verificationMethod = method,
                verificationEvidence = verificationEvidence,
                trustedTimestampPolicy = timestampPolicy,
                trustedTimestampAuthorityUrl = trustedTimestampAuthority,
                trustedTimestampTimeoutMs = trustedTimestampTimeoutMs
            )
        } catch (error: TrustedTimestampRequiredException) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Failed,
                diagnostics = mapOf(
                    "reason" to (error.message ?: "Trusted timestamp required but unavailable"),
                    "trusted_timestamp_policy" to timestampPolicy.wireValue,
                    "trusted_timestamp_status" to "required_failed",
                    "trusted_timestamp_authority" to trustedTimestampAuthority
                )
            )
        } catch (error: IllegalArgumentException) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Failed,
                diagnostics = mapOf(
                    "reason" to (error.message ?: "Invalid attestation payload input"),
                    "trusted_timestamp_policy" to timestampPolicy.wireValue,
                    "trusted_timestamp_authority" to trustedTimestampAuthority
                )
            )
        }
        val provenance = ProvenanceContext(
            provider = "researchos.attestation",
            methodId = ID,
            methodVersion = VERSION,
            deviceId = record.publicKeyId,
            operatorId = record.operatorId
        )
        val observation = Observation(
            phenomenon = "attestation.signed_event",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = record.asOutputMap(),
            temporalContext = request.temporalContext.copy(eventTimeEpochMs = record.deviceEventTimeEpochMs),
            provenance = provenance
        )
        val transformation = Transformation(
            action = "attestation.sign_event",
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf("attestation_hash" to record.attestationHash, "public_key_id" to record.publicKeyId)
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf("attestation_hash" to record.attestationHash, "public_key_id" to record.publicKeyId)
        )
    }
}

object As100CreateAttestationAnchorMethod : As100Method {
    const val ID = "attestation.anchor_bundle"
    private const val VERSION = "1.0.0"

    override val id: String = ID
    override val ref: ArchitectureRef = ArchitectureRef(ArchitectureId(ID), "Method", "Create nightly attestation anchor")
    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Create nightly attestation anchor",
        version = VERSION,
        description = "Export the current signed attestation-chain head and public key for submission through an ODK nightly anchor form.",
        outputs = listOf(
            "anchor_id", "study_id", "operator_id", "public_key_id", "public_key_base64",
            "last_anchor_hash", "current_chain_head_hash", "number_of_attestations",
            "first_unanchored_attestation_time", "last_unanchored_attestation_time",
            "attestation_record_bundle_hash", "bundle_signature", "created_device_time_iso"
        ),
        graphOutputs = listOf("attestation.odk_anchor_bundle"),
        parameters = mapOf("category" to "Attestation", "status" to "Experimental")
    )
    override val contract: MethodContract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val c = request.context
        val bundle = AttestationRepository.createAnchorBundle(
            studyId = c["study_id"].orEmpty().ifBlank { "study_demo" },
            operatorId = c["operator_id"].orEmpty().ifBlank { "operator_unknown" }
        )
        val provenance = ProvenanceContext(
            provider = "researchos.attestation",
            methodId = ID,
            methodVersion = VERSION,
            deviceId = bundle.publicKeyId,
            operatorId = bundle.operatorId
        )
        val observation = Observation(
            phenomenon = "attestation.odk_anchor_bundle",
            subject = InvocationContext.from(c)?.subjectRef(),
            values = bundle.asOdkFields(),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = "attestation.create_odk_anchor_bundle",
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf("chain_head" to bundle.currentChainHeadHash, "bundle_hash" to bundle.bundlePayloadHash)
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf("chain_head" to bundle.currentChainHeadHash, "bundle_hash" to bundle.bundlePayloadHash)
        )
    }
}
