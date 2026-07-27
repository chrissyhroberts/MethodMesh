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
import com.example.researchos.core.researchos.QualityAssessment
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.ValidationFinding
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import com.example.researchos.settings.SettingsState

/** Canonical NFC write method. No parallel evidence or intervention model is produced. */
object As100NfcWriteMethod : As100Method {
    const val ID = "nfc_tag_write"
    const val VERSION = "0.4.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC Tag Write")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC Tag Write",
        version = VERSION,
        description = "Write an NDEF record and emit one canonical post-write observation.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = listOf(
            NfcWriteFields.WRITE_SUCCESS, NfcWriteFields.WRITE_MESSAGE,
            NfcWriteFields.WRITE_RECORD_TYPE, NfcWriteFields.WRITE_SIZE_BYTES,
            NfcWriteFields.OVERWRITE_POLICY, NfcWriteFields.PREVIOUS_MESSAGE_HASH,
            NfcWriteFields.WRITTEN_MESSAGE_HASH, NfcWriteFields.WRITE_VERIFIED
        ) + NfcEvidenceFields.tagOutputFields,
        parameters = mapOf("category" to "NFC", "status" to "Experimental", "device_service" to AndroidNfcDeviceService.SERVICE_ID)
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        requiredContext = listOf("record_type", "value"),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>): ExecutionRequest =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val signal = request.signals.firstOrNull() ?: return As100ExecutionEngine.complete(
            request, TransformationStatus.Unsupported,
            diagnostics = mapOf("reason" to "NFC write requires a live NfcTagSignal from the Android NFC Device Service.")
        )
        return As100ExecutionEngine.complete(
            request, TransformationStatus.Unsupported,
            transformations = listOf(Transformation(
                action = "intervene.nfc.write", method = ref,
                inputs = listOf(ArchitectureRef(signal.id, signal.objectType, signal.signalType)),
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf("reason" to "A generic Signal has no Android Tag handle. Use the NFC device-service path."),
                temporalContext = signal.temporalContext,
                provenance = ProvenanceContext(provider = signal.provenance.provider, methodId = ID, methodVersion = VERSION)
            ))
        )
    }

    fun write(tagSignal: NfcTagSignal, writeRequest: NfcWriteRequest, invocationContext: InvocationContext? = null): ExecutionResult {
        val write = NfcTagRepository.writeTag(tagSignal.androidTag, writeRequest)
        val request = request(
            ID,
            invocationContext?.asMap(ID).orEmpty() + mapOf(
                "record_type" to writeRequest.recordType, "value" to writeRequest.value,
                "mime_type" to writeRequest.mimeType, "language_code" to writeRequest.languageCode,
                "overwrite_policy" to writeRequest.overwritePolicy.wireValue,
                "expected_current_hash" to writeRequest.expectedCurrentHash.orEmpty()
            ),
            listOf(tagSignal.signal)
        )
        val provenance = ProvenanceContext(
            provider = tagSignal.signal.provenance.provider, methodId = ID, methodVersion = VERSION,
            operatorId = invocationContext?.operatorId
        )
        val uid = write.tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val entity = Entity(
            id = ArchitectureId("nfc-tag:$uid"), entityType = "NfcTag",
            attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid), temporalContext = tagSignal.signal.temporalContext
        )
        val values = linkedMapOf(
            NfcWriteFields.WRITE_SUCCESS to write.success.toString(),
            NfcWriteFields.WRITE_MESSAGE to write.message,
            NfcWriteFields.WRITE_RECORD_TYPE to writeRequest.recordType,
            NfcWriteFields.WRITE_SIZE_BYTES to write.sizeBytes.toString(),
            NfcWriteFields.OVERWRITE_POLICY to write.overwritePolicy,
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to write.previousMessageHash,
            NfcWriteFields.WRITTEN_MESSAGE_HASH to write.writtenMessageHash,
            NfcWriteFields.WRITE_VERIFIED to write.verified.toString()
        ) + write.tagValues + runCatching {
            NfcCredentialEvidence.fields(write.tagValues)
        }.getOrDefault(emptyMap())
        val observation = Observation(
            phenomenon = "nfc.tag.write", subject = ArchitectureRef(entity.id, entity.objectType, uid),
            values = values, sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
            temporalContext = tagSignal.signal.temporalContext, provenance = provenance
        )
        val status = if (write.success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val diagnostics = mapOf(NfcWriteFields.WRITE_SUCCESS to write.success.toString(), NfcWriteFields.WRITE_MESSAGE to write.message)
        val transformation = Transformation(
            action = "intervene.nfc.write", method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status, diagnostics = diagnostics, temporalContext = observation.temporalContext, provenance = provenance
        )
        val result = As100ExecutionEngine.complete(
            request, status, entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation),
            validation = listOf(ValidationFinding(write.success, write.message, NfcWriteFields.WRITE_SUCCESS, if (write.success) "nfc_write_succeeded" else "nfc_write_failed")),
            quality = QualityAssessment(usable = write.success, metrics = diagnostics), diagnostics = diagnostics
        ).withInvocationContext(invocationContext)
        return ResearchRuntime.session.record(result)
    }
}
