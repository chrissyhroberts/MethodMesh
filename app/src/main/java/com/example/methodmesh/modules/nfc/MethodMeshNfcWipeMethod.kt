package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.ResearchRuntime
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
import com.example.methodmesh.core.methodmesh.QualityAssessment
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.ValidationFinding
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.platform.nfc.AndroidNfcDeviceService
import com.example.methodmesh.platform.nfc.NfcTagSignal
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

object As100NfcWipeMethod : As100Method {
    const val ID = "nfc_tag_wipe"
    const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC tag wipe")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC tag wipe",
        version = VERSION,
        description = "Replace the current NDEF message with a verified empty NDEF record.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = NfcWipeFields.outputFields,
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "device_service" to AndroidNfcDeviceService.SERVICE_ID
        )
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = descriptor.inputs,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "NFC tag wipe requires live NFC interaction.")
    )

    fun wipe(
        tagSignal: NfcTagSignal,
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        val wipe = NfcTagRepository.wipeTag(tagSignal.androidTag)
        val uid = wipe.tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val values = linkedMapOf(
            NfcWipeFields.WIPE_SUCCESS to wipe.success.toString(),
            NfcWipeFields.WIPE_MESSAGE to wipe.message,
            NfcWipeFields.WIPED_TIME_ISO to Instant.now().toString(),
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to wipe.previousMessageHash,
            NfcWriteFields.WRITTEN_MESSAGE_HASH to wipe.writtenMessageHash,
            NfcWriteFields.WRITE_VERIFIED to wipe.verified.toString(),
            NfcEvidenceFields.TAG_UID_HEX to uid,
            NfcEvidenceFields.NDEF_RECORD_COUNT to wipe.tagValues[NfcEvidenceFields.NDEF_RECORD_COUNT].orEmpty(),
            NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES to wipe.tagValues[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES].orEmpty()
        )
        val request = request(
            action = ID,
            context = invocationContext?.asMap(ID).orEmpty(),
            signals = listOf(tagSignal.signal),
            inputs = emptyList()
        )
        val provenance = ProvenanceContext(
            provider = tagSignal.signal.provenance.provider,
            methodId = ID,
            methodVersion = VERSION,
            operatorId = invocationContext?.operatorId
        )
        val entity = Entity(
            id = ArchitectureId("nfc-tag:$uid"),
            entityType = "NfcTag",
            attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid),
            temporalContext = tagSignal.signal.temporalContext
        )
        val observation = Observation(
            phenomenon = "nfc.tag.wiped",
            subject = ArchitectureRef(entity.id, entity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val status = if (wipe.success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val diagnostics = mapOf(
            NfcWipeFields.WIPE_SUCCESS to wipe.success.toString(),
            NfcWipeFields.WIPE_MESSAGE to wipe.message
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            diagnostics = diagnostics,
            temporalContext = observation.temporalContext,
            provenance = provenance
        )
        return ResearchRuntime.session.record(
            As100ExecutionEngine.complete(
                request = request,
                status = status,
                entities = listOf(entity),
                observations = listOf(observation),
                transformations = listOf(transformation),
                validation = listOf(
                    ValidationFinding(
                        passed = wipe.success,
                        message = wipe.message,
                        field = NfcWipeFields.WIPE_SUCCESS,
                        code = if (wipe.success) "nfc_wipe_succeeded" else "nfc_wipe_failed"
                    )
                ),
                quality = QualityAssessment(usable = wipe.success, metrics = diagnostics),
                diagnostics = diagnostics
            ).withInvocationContext(invocationContext)
        )
    }

    fun confirmEmpty(
        tagSignal: NfcTagSignal,
        previousValues: Map<String, String> = emptyMap(),
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val empty = tagValues[NfcEvidenceFields.NDEF_RECORD_COUNT] == "1" &&
            tagValues[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX].orEmpty().isBlank() &&
            tagValues[NfcEvidenceFields.NDEF_RECORDS_JSON].orEmpty().contains("\"tnf\":0")
        val message = if (empty) {
            "NDEF user content removed and empty message verified on the confirmation tap."
        } else {
            "The tag did not verify as an empty NDEF message."
        }
        val wipe = NfcWriteResult(
            success = empty,
            message = message,
            sizeBytes = tagValues[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES]?.toIntOrNull() ?: 0,
            tagValues = tagValues,
            overwritePolicy = NfcOverwritePolicy.Replace.wireValue,
            previousMessageHash = previousValues[NfcWriteFields.PREVIOUS_MESSAGE_HASH].orEmpty(),
            writtenMessageHash = tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty(),
            verified = empty
        )
        return wipeResult(tagSignal, wipe, invocationContext)
    }

    private fun wipeResult(
        tagSignal: NfcTagSignal,
        wipe: NfcWriteResult,
        invocationContext: InvocationContext?
    ): ExecutionResult {
        val uid = wipe.tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val values = linkedMapOf(
            NfcWipeFields.WIPE_SUCCESS to wipe.success.toString(),
            NfcWipeFields.WIPE_MESSAGE to wipe.message,
            NfcWipeFields.WIPED_TIME_ISO to Instant.now().toString(),
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to wipe.previousMessageHash,
            NfcWriteFields.WRITTEN_MESSAGE_HASH to wipe.writtenMessageHash,
            NfcWriteFields.WRITE_VERIFIED to wipe.verified.toString(),
            NfcEvidenceFields.TAG_UID_HEX to uid,
            NfcEvidenceFields.NDEF_RECORD_COUNT to wipe.tagValues[NfcEvidenceFields.NDEF_RECORD_COUNT].orEmpty(),
            NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES to wipe.tagValues[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES].orEmpty(),
            NfcEvidenceFields.NDEF_MESSAGE_SHA256 to wipe.tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty()
        )
        val request = request(
            action = ID,
            context = invocationContext?.asMap(ID).orEmpty(),
            signals = listOf(tagSignal.signal),
            inputs = emptyList()
        )
        val provenance = ProvenanceContext(
            provider = tagSignal.signal.provenance.provider,
            methodId = ID,
            methodVersion = VERSION,
            operatorId = invocationContext?.operatorId
        )
        val entity = Entity(
            id = ArchitectureId("nfc-tag:$uid"),
            entityType = "NfcTag",
            attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid),
            temporalContext = tagSignal.signal.temporalContext
        )
        val observation = Observation(
            phenomenon = "nfc.tag.wiped",
            subject = ArchitectureRef(entity.id, entity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val status = if (wipe.success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val diagnostics = mapOf(
            NfcWipeFields.WIPE_SUCCESS to wipe.success.toString(),
            NfcWipeFields.WIPE_MESSAGE to wipe.message
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            diagnostics = diagnostics,
            temporalContext = observation.temporalContext,
            provenance = provenance
        )
        return ResearchRuntime.session.record(
            As100ExecutionEngine.complete(
                request = request,
                status = status,
                entities = listOf(entity),
                observations = listOf(observation),
                transformations = listOf(transformation),
                validation = listOf(
                    ValidationFinding(
                        passed = wipe.success,
                        message = wipe.message,
                        field = NfcWipeFields.WIPE_SUCCESS,
                        code = if (wipe.success) "nfc_wipe_succeeded" else "nfc_wipe_failed"
                    )
                ),
                quality = QualityAssessment(usable = wipe.success, metrics = diagnostics),
                diagnostics = diagnostics
            ).withInvocationContext(invocationContext)
        )
    }
}
