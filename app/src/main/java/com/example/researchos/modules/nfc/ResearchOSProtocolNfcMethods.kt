package com.example.researchos.modules.nfc

import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
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
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import com.example.researchos.settings.SettingsState
import java.time.Instant

abstract class ProtocolNfcMethod(
    final override val id: String,
    private val operation: String
) : As100Method {
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", "Protocol NFC $operation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id), methodType = MethodObjectType.Method,
        name = "Protocol NFC $operation", version = VERSION,
        description = "Check or update an offline protocol progress receipt on an NFC participant card.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = ProtocolNfcTrackingFields.outputFields,
        parameters = mapOf("category" to "NFC", "status" to "Experimental", "operation" to operation)
    )
    override val contract = MethodContract(
        method = ref, acceptedSignals = descriptor.inputs, requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Protocol NFC tracking requires a live NFC tag."))

    fun run(tagSignal: NfcTagSignal, context: Map<String, String>): ExecutionResult {
        val protocolId = context[ProtocolNfcTrackingFields.PROTOCOL_ID].orEmpty()
        val protocolVersion = context[ProtocolNfcTrackingFields.PROTOCOL_VERSION].orEmpty().ifBlank { "1" }
        val stepId = context[ProtocolNfcTrackingFields.STEP_ID].orEmpty()
        val flagBitCount = context[ProtocolNfcTrackingFields.FLAG_BIT_COUNT].orEmpty().toIntOrNull()?.coerceIn(1, 65535) ?: 8
        val completionBitCount = context[ProtocolNfcTrackingFields.COMPLETION_BIT_COUNT].orEmpty().toIntOrNull()?.coerceIn(1, 65535) ?: 8
        val requiredBits = context[ProtocolNfcTrackingFields.REQUIRED_BITS].orEmpty().ifBlank { "00" }
        val requiredValue = context[ProtocolNfcTrackingFields.REQUIRED_VALUE].orEmpty().ifBlank { "00" }
        val requiredExpression = context[ProtocolNfcTrackingFields.REQUIRED_EXPRESSION].orEmpty()
        val completionBits = context[ProtocolNfcTrackingFields.COMPLETION_BITS].orEmpty().ifBlank { "00" }
        val setFlags = context[ProtocolNfcTrackingFields.SET_FLAGS].orEmpty()
        val clearFlags = context[ProtocolNfcTrackingFields.CLEAR_FLAGS].orEmpty()
        val flagDefinitions = context[ProtocolNfcTrackingFields.FLAG_DEFINITIONS].orEmpty()
        val stepDefinitions = context[ProtocolNfcTrackingFields.STEP_DEFINITIONS].orEmpty()
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val current = ProtocolNfcStateCodec.stateFrom(tagSignal.androidTag)
        val state = current ?: ProtocolNfcStateCodec.empty(protocolId, protocolVersion, flagBitCount, completionBitCount)
        val configValid = current != null && protocolId.isNotBlank() && state.flagBitCount == flagBitCount && state.completionBitCount == completionBitCount &&
            ProtocolNfcStateCodec.normaliseHex(requiredBits) != null && ProtocolNfcStateCodec.normaliseHex(requiredValue) != null &&
            ProtocolNfcStateCodec.normaliseHex(completionBits) != null && (setFlags.isBlank() || ProtocolNfcStateCodec.normaliseHex(setFlags) != null) &&
            (clearFlags.isBlank() || ProtocolNfcStateCodec.normaliseHex(clearFlags) != null)
        val sameProtocol = state.protocolId.isBlank() || (state.protocolId == protocolId && state.protocolVersion == protocolVersion)
        val allowed = configValid && sameProtocol && ProtocolNfcStateCodec.expressionMatches(state, requiredExpression, requiredBits, requiredValue)
        val reason = when {
            current == null -> "This card has not been provisioned for a protocol. Run protocol_nfc_provision first."
            !configValid -> "protocol_id and valid hexadecimal bit masks are required."
            !sameProtocol -> "The card belongs to protocol ${state.protocolId} version ${state.protocolVersion}."
            allowed -> "Required protocol progress is present."
            else -> "Required protocol progress is not present on this card."
        }
        var finalState = state
        var writeVerified = false
        var status = TransformationStatus.Succeeded
        if (operation == "complete") {
            if (!allowed) {
                status = TransformationStatus.Failed
            } else {
                val eventHash = context["event_payload_hash"].orEmpty().ifBlank { tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty() }
                finalState = ProtocolNfcStateCodec.complete(state, completionBits, eventHash, setFlags, clearFlags)
                val write = NfcTagRepository.writeOrReplaceRecord(
                    tagSignal.androidTag,
                    NfcWriteRequest(
                        recordType = ProtocolNfcStateCodec.RECORD_TYPE,
                        value = ProtocolNfcStateCodec.encode(finalState),
                        mimeType = ProtocolNfcStateCodec.RECORD_TYPE,
                        overwritePolicy = NfcOverwritePolicy.Replace
                    ),
                    ProtocolNfcStateCodec::isProtocolRecord
                )
                writeVerified = write.verified
                if (!write.success) status = TransformationStatus.Failed
            }
        }
        val uid = tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val values = linkedMapOf<String, String>().apply {
            put(ProtocolNfcTrackingFields.PROTOCOL_ID, protocolId)
            put(ProtocolNfcTrackingFields.PROTOCOL_VERSION, protocolVersion)
            put(ProtocolNfcTrackingFields.STEP_ID, stepId)
            put(ProtocolNfcTrackingFields.FLAG_BIT_COUNT, flagBitCount.toString())
            put(ProtocolNfcTrackingFields.COMPLETION_BIT_COUNT, completionBitCount.toString())
            put(ProtocolNfcTrackingFields.FLAG_BITS, finalState.flagBitsHex)
            put(ProtocolNfcTrackingFields.COMPLETION_BITS_STATE, finalState.completionBitsHex)
            put(ProtocolNfcTrackingFields.FLAG_DEFINITIONS, flagDefinitions)
            put(ProtocolNfcTrackingFields.STEP_DEFINITIONS, stepDefinitions)
            put(ProtocolNfcTrackingFields.ACTIVE_FLAG_LABELS, ProtocolNfcStateCodec.labelsForBits(finalState.flagBitsHex, flagDefinitions))
            put(ProtocolNfcTrackingFields.COMPLETED_STEP_LABELS, ProtocolNfcStateCodec.labelsForBits(finalState.completionBitsHex, stepDefinitions))
            put(ProtocolNfcTrackingFields.REQUIRED_BITS, requiredBits)
            put(ProtocolNfcTrackingFields.REQUIRED_VALUE, requiredValue)
            put(ProtocolNfcTrackingFields.REQUIRED_EXPRESSION, requiredExpression)
            put(ProtocolNfcTrackingFields.COMPLETION_BITS, completionBits)
            put(ProtocolNfcTrackingFields.PROTOCOL_ALLOWED, allowed.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_REASON, if (status == TransformationStatus.Failed && operation == "complete") "Step could not be completed: $reason" else reason)
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_BITS, finalState.bitsHex)
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_VERSION, finalState.stateVersion.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_HASH, com.example.researchos.core.crypto.Digests.sha256Hex(ProtocolNfcStateCodec.encode(finalState)))
            put(ProtocolNfcTrackingFields.PROTOCOL_UPDATED_TIME_ISO, finalState.updatedAtIso)
            put(ProtocolNfcTrackingFields.PROTOCOL_WRITE_VERIFIED, writeVerified.toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_OPERATION, operation)
            put(ProtocolNfcTrackingFields.PROTOCOL_PROVISIONED, (current != null).toString())
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD, ProtocolNfcStateCodec.encode(finalState))
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_PAYLOAD_HASH, ProtocolNfcStateCodec.stateHash(finalState))
            put(ProtocolNfcTrackingFields.PROTOCOL_STATE_SOURCE, "card")
            put(ProtocolNfcTrackingFields.PROTOCOL_DEVIATION, (!allowed).toString())
            put(NfcEvidenceFields.TAG_UID_HEX, uid)
            put(NfcEvidenceFields.NDEF_MESSAGE_SHA256, tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty())
        }
        val request = request(id, context, listOf(tagSignal.signal), emptyList())
        val provenance = ProvenanceContext(tagSignal.signal.provenance.provider, id, VERSION)
        val entity = Entity(id = ArchitectureId("nfc-tag:$uid"), entityType = "NfcTag", attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid), temporalContext = tagSignal.signal.temporalContext)
        val observation = Observation(
            phenomenon = "nfc.protocol.progress",
            subject = ArchitectureRef(entity.id, entity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            diagnostics = mapOf("protocol_allowed" to allowed.toString(), "protocol_operation" to operation),
            temporalContext = observation.temporalContext,
            provenance = provenance
        )
        return ResearchRuntime.session.record(
            As100ExecutionEngine.complete(request, status, entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation), diagnostics = mapOf(ProtocolNfcTrackingFields.PROTOCOL_REASON to values[ProtocolNfcTrackingFields.PROTOCOL_REASON].orEmpty())).withInvocationContext(InvocationContext.from(context))
        )
    }

    companion object { const val VERSION = "1.0.0" }
}

object As100ProtocolNfcCheckMethod : ProtocolNfcMethod("protocol_nfc_check", "check")
object As100ProtocolNfcCompleteMethod : ProtocolNfcMethod("protocol_nfc_complete", "complete")
