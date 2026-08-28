package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.crypto.Digests
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.platform.nfc.AndroidNfcDeviceService
import com.example.methodmesh.platform.nfc.NfcTagSignal
import com.example.methodmesh.settings.SettingsState

/** Administrative protocol-card operations. These are deliberately separate from check/complete. */
abstract class ProtocolNfcAdministrationMethod(
    final override val id: String,
    private val operation: String,
    description: String
) : As100Method {
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", "Protocol NFC $operation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Method,
        name = "Protocol NFC $operation",
        version = VERSION,
        description = description,
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = ProtocolNfcTrackingFields.outputFields,
        parameters = mapOf("category" to "NFC", "status" to "Experimental", "operation" to operation)
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = descriptor.inputs,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Protocol NFC administration requires a live NFC tag."))

    abstract fun run(tagSignal: NfcTagSignal, context: Map<String, String>): ExecutionResult

    protected fun result(
        tagSignal: NfcTagSignal,
        context: Map<String, String>,
        values: Map<String, String>,
        success: Boolean,
        message: String
    ): ExecutionResult {
        val uid = values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val request = request(id, context + values, listOf(tagSignal.signal), emptyList())
        val provenance = ProvenanceContext(tagSignal.signal.provenance.provider, id, VERSION)
        val entity = Entity(
            id = ArchitectureId("nfc-tag:$uid"),
            entityType = "NfcTag",
            attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid),
            temporalContext = tagSignal.signal.temporalContext
        )
        val observation = Observation(
            phenomenon = "nfc.protocol.$operation",
            subject = ArchitectureRef(entity.id, entity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val status = if (success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val transformation = Transformation(
            action = id,
            method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            diagnostics = mapOf("message" to message, "protocol_operation" to operation),
            temporalContext = observation.temporalContext,
            provenance = provenance
        )
        return ResearchRuntime.session.record(
            As100ExecutionEngine.complete(
                request,
                status,
                entities = listOf(entity),
                observations = listOf(observation),
                transformations = listOf(transformation),
                diagnostics = mapOf(ProtocolNfcTrackingFields.PROTOCOL_REASON to message)
            ).withInvocationContext(InvocationContext.from(context))
        )
    }

    protected fun baseValues(state: ProtocolNfcState, tagValues: Map<String, String>, context: Map<String, String>): LinkedHashMap<String, String> = linkedMapOf(
        ProtocolNfcTrackingFields.PROTOCOL_ID to state.protocolId,
        ProtocolNfcTrackingFields.PROTOCOL_VERSION to state.protocolVersion,
        ProtocolNfcTrackingFields.FLAG_BIT_COUNT to state.flagBitCount.toString(),
        ProtocolNfcTrackingFields.COMPLETION_BIT_COUNT to state.completionBitCount.toString(),
        ProtocolNfcTrackingFields.FLAG_BITS to state.flagBitsHex,
        ProtocolNfcTrackingFields.COMPLETION_BITS_STATE to state.completionBitsHex,
        ProtocolNfcTrackingFields.PROTOCOL_STATE_BITS to state.bitsHex,
        ProtocolNfcTrackingFields.PROTOCOL_STATE_VERSION to state.stateVersion.toString(),
        ProtocolNfcTrackingFields.PROTOCOL_STATE_HASH to ProtocolNfcStateCodec.stateHash(state),
        ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD to ProtocolNfcStateCodec.encode(state),
        ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD_HASH to ProtocolNfcStateCodec.stateHash(state),
        ProtocolNfcTrackingFields.PROTOCOL_UPDATED_TIME_ISO to state.updatedAtIso,
        ProtocolNfcTrackingFields.PROTOCOL_PROVISIONED to "true",
        ProtocolNfcTrackingFields.PROTOCOL_OPERATION to operation,
        ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE to context[ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE].orEmpty(),
        NfcEvidenceFields.TAG_UID_HEX to tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
        NfcEvidenceFields.NDEF_MESSAGE_SHA256 to tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty()
    )

    protected fun writeState(tagSignal: NfcTagSignal, state: ProtocolNfcState, policy: NfcOverwritePolicy): NfcWriteResult =
        NfcTagRepository.writeOrReplaceRecord(
            tagSignal.androidTag,
            NfcWriteRequest(
                recordType = ProtocolNfcStateCodec.RECORD_TYPE,
                value = ProtocolNfcStateCodec.encode(state),
                mimeType = ProtocolNfcStateCodec.RECORD_TYPE,
                overwritePolicy = policy
            ),
            ProtocolNfcStateCodec::isProtocolRecord
        )

    companion object { const val VERSION = "1.0.0" }
}

object As100ProtocolNfcProvisionMethod : ProtocolNfcAdministrationMethod(
    id = "protocol_nfc_provision",
    operation = "provision",
    description = "Create the initial offline protocol-progress receipt on a participant NFC card."
) {
    override fun run(tagSignal: NfcTagSignal, context: Map<String, String>): ExecutionResult {
        val definitionJson = context[ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_JSON].orEmpty().trim()
        val definition = definitionJson.takeIf(String::isNotBlank)?.let(ProtocolNfcDefinitionCodec::decode)
        if (definitionJson.isNotBlank() && definition == null) {
            val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
            return failure(tagSignal, context, tagValues, "protocol_definition_json is not a valid MethodMesh protocol definition.")
        }
        val protocolId = definition?.protocolId ?: context[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty()
        val version = definition?.protocolVersion ?: context[ProtocolNfcTrackingFields.PROTOCOL_VERSION].orEmpty().ifBlank { "1" }
        val flagCount = definition?.flagBitCount ?: context[ProtocolNfcTrackingFields.FLAG_BIT_COUNT].orEmpty().toIntOrNull() ?: 8
        val completionCount = definition?.completionBitCount ?: context[ProtocolNfcTrackingFields.COMPLETION_BIT_COUNT].orEmpty().toIntOrNull() ?: 8
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val existing = ProtocolNfcStateCodec.stateFrom(tagSignal.androidTag)
        val policy = NfcOverwritePolicy.parse(context["overwrite_policy"]) ?: NfcOverwritePolicy.EmptyOnly
        val hasMeaningful = tagValues[NfcEvidenceFields.NDEF_HAS_MEANINGFUL_CONTENT] == "true"
        if (protocolId.isBlank()) return failure(tagSignal, context, tagValues, "protocol_id is required.")
        if (policy == NfcOverwritePolicy.EmptyOnly && hasMeaningful) return failure(tagSignal, context, tagValues, "The tag is not empty. Use replace only when re-provisioning is intentional.")
        if (existing != null && policy == NfcOverwritePolicy.EmptyOnly) return failure(tagSignal, context, tagValues, "This tag already has a protocol receipt. Use replace to re-provision it.")
        val state = runCatching {
            ProtocolNfcStateCodec.provision(
                protocolId, version, flagCount, completionCount,
                context["initial_flag_bits"].orEmpty(), context["initial_completion_bits"].orEmpty()
            )
        }.getOrElse { return failure(tagSignal, context, tagValues, it.message ?: "Invalid protocol definition.") }
        val write = writeState(tagSignal, state, NfcOverwritePolicy.Replace)
        val finalValues = baseValues(state, write.tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "provision")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "provisioned")
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, write.verified.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, write.message)
            put(ProtocolNfcTrackingFields.PROTOCOL_DEVIATION, "false")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_HASH, ProtocolNfcStateCodec.stateHash(state))
            put(ProtocolNfcTrackingFields.PROTOCOL_DEFINITION_HASH, definition?.let { Digests.sha256Hex(definitionJson) }.orEmpty())
            put(ProtocolNfcTrackingFields.FLAG_DEFINITIONS, definition?.let(ProtocolNfcDefinitionCodec::flagDefinitions).orEmpty())
            put(ProtocolNfcTrackingFields.STEP_DEFINITIONS, definition?.let(ProtocolNfcDefinitionCodec::stepDefinitions).orEmpty())
        }
        return result(tagSignal, context, finalValues, write.success && write.verified, write.message)
    }

    private fun failure(tagSignal: NfcTagSignal, context: Map<String, String>, tagValues: Map<String, String>, message: String): ExecutionResult {
        val state = ProtocolNfcStateCodec.empty(context[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty(), context[ProtocolNfcTrackingFields.PROTOCOL_VERSION].orEmpty().ifBlank { "1" })
        return result(tagSignal, context, baseValues(state, tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_PROVISIONED, "false")
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "provision")
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, message)
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, "false")
        }, false, message)
    }
}

object As100ProtocolNfcReconstructMethod : ProtocolNfcAdministrationMethod(
    id = "protocol_nfc_reconstruct",
    operation = "reconstruct",
    description = "Restore a protocol receipt to a replacement NFC card from a verified exported state payload."
) {
    override fun run(tagSignal: NfcTagSignal, context: Map<String, String>): ExecutionResult {
        val payload = context[ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD].orEmpty().trim()
        val reason = context[ProtocolNfcTrackingFields.RECONSTRUCTION_REASON].orEmpty().trim()
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val state = ProtocolNfcStateCodec.decode(payload)
        if (state == null) return failure(tagSignal, context, tagValues, "protocol_state_payload is not a valid MethodMesh protocol receipt.")
        if (reason.isBlank()) return failure(tagSignal, context, tagValues, "reconstruction_reason is required for a replacement card.")
        val expectedHash = context[ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD_HASH].orEmpty().trim()
        if (expectedHash.isNotBlank() && !expectedHash.equals(ProtocolNfcStateCodec.stateHash(state), ignoreCase = true)) {
            return failure(tagSignal, context, tagValues, "protocol_state_payload_hash does not match the supplied state.")
        }
        val write = writeState(tagSignal, state, NfcOverwritePolicy.Replace)
        val finalValues = baseValues(state, write.tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "reconstruct")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "reconstructed")
            put(ProtocolNfcTrackingFields.RECONSTRUCTION_REASON, reason)
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, write.verified.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, write.message)
            put(ProtocolNfcTrackingFields.PROTOCOL_DEVIATION, "false")
        }
        return result(tagSignal, context, finalValues, write.success && write.verified, write.message)
    }

    private fun failure(tagSignal: NfcTagSignal, context: Map<String, String>, tagValues: Map<String, String>, message: String): ExecutionResult {
        val state = ProtocolNfcStateCodec.decode(context[ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD].orEmpty())
            ?: ProtocolNfcStateCodec.empty("", "1")
        return result(tagSignal, context, baseValues(state, tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "reconstruct")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "reconstruction_rejected")
            put(ProtocolNfcTrackingFields.RECONSTRUCTION_REASON, context[ProtocolNfcTrackingFields.RECONSTRUCTION_REASON].orEmpty())
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, "false")
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, message)
        }, false, message)
    }
}

object As100ProtocolNfcOverrideMethod : ProtocolNfcAdministrationMethod(
    id = "protocol_nfc_override",
    operation = "override",
    description = "Apply an authorised, justified manual change to protocol flags or completion bits."
) {
    override fun run(tagSignal: NfcTagSignal, context: Map<String, String>): ExecutionResult {
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val state = ProtocolNfcStateCodec.stateFrom(tagSignal.androidTag)
            ?: return failure(tagSignal, context, tagValues, "This card has not been provisioned for a protocol.")
        val protocolId = context[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty()
        val reason = context[ProtocolNfcTrackingFields.OVERRIDE_JUSTIFICATION].orEmpty().trim()
        if (protocolId.isNotBlank() && protocolId != state.protocolId) return failure(tagSignal, context, tagValues, "The card belongs to protocol ${state.protocolId}.")
        if (reason.isBlank()) return failure(tagSignal, context, tagValues, "override_justification is required.")
        val updated = runCatching {
            ProtocolNfcStateCodec.override(
                state = state,
                setCompletionBitsHex = context["set_completion_bits"].orEmpty(),
                clearCompletionBitsHex = context["clear_completion_bits"].orEmpty(),
                setFlagsHex = context[ProtocolNfcTrackingFields.SET_FLAGS].orEmpty(),
                clearFlagsHex = context[ProtocolNfcTrackingFields.CLEAR_FLAGS].orEmpty(),
                eventHash = context["event_payload_hash"].orEmpty()
            )
        }.getOrElse { return failure(tagSignal, context, tagValues, it.message ?: "Invalid override bit masks.") }
        val write = writeState(tagSignal, updated, NfcOverwritePolicy.Replace)
        val finalValues = baseValues(updated, write.tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "override")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "override")
            put(ProtocolNfcTrackingFields.OVERRIDE_JUSTIFICATION, reason)
            put(ProtocolNfcTrackingFields.OVERRIDE_ACTOR, context["operator_id"].orEmpty())
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, write.verified.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, write.message)
            put(ProtocolNfcTrackingFields.PROTOCOL_DEVIATION, "true")
        }
        return result(tagSignal, context, finalValues, write.success && write.verified, write.message)
    }

    private fun failure(tagSignal: NfcTagSignal, context: Map<String, String>, tagValues: Map<String, String>, message: String): ExecutionResult {
        val state = ProtocolNfcStateCodec.stateFrom(tagSignal.androidTag) ?: ProtocolNfcStateCodec.empty(context[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty(), "1")
        return result(tagSignal, context, baseValues(state, tagValues, context).apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, "override")
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "override_rejected")
            put(ProtocolNfcTrackingFields.OVERRIDE_JUSTIFICATION, context[ProtocolNfcTrackingFields.OVERRIDE_JUSTIFICATION].orEmpty())
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, "false")
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, message)
            put(ProtocolNfcTrackingFields.PROTOCOL_DEVIATION, "true")
        }, false, message)
    }
}
