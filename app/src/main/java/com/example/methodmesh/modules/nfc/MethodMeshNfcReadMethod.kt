package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.Observation
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
import com.example.methodmesh.core.ResearchRuntime

/**
 * Native AS1.00 method for NFC tag reads.
 *
 * NFC tag reads are interpreted directly into canonical AS1.00 results.
 */
object As100NfcReadMethod : As100Method {
    const val ID = "nfc_tag_read"
    const val VERSION = "0.3.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "NFC Tag Read"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "NFC Tag Read",
        version = VERSION,
        description = "Interpret an NFC tag-discovery signal as structured observation evidence and an immutable NFC tag artifact.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = NfcEvidenceFields.tagOutputFields,
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "device_service" to AndroidNfcDeviceService.SERVICE_ID
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
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
    ): ExecutionResult {
        val signal = request.signals.firstOrNull()
        if (signal == null) {
            return As100ExecutionEngine.complete(
                request = request,
                status = TransformationStatus.Unsupported,
                diagnostics = mapOf(
                    "reason" to "NFC read requires a live NfcTagSignal from the Android NFC Device Service."
                )
            )
        }

        val transformation = Transformation(
            action = "interpret.nfc.signal",
            method = ref,
            inputs = listOf(ArchitectureRef(signal.id, signal.objectType, signal.signalType)),
            status = TransformationStatus.Unsupported,
            diagnostics = mapOf(
                "reason" to "A generic Signal does not contain the Android Tag handle needed for NDEF decoding. Use read(tagSignal) from the NFC Device Service path."
            ),
            temporalContext = signal.temporalContext,
            provenance = ProvenanceContext(
                provider = signal.provenance.provider,
                methodId = ID,
                methodVersion = VERSION
            )
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Unsupported,
            transformations = listOf(transformation),
            diagnostics = transformation.diagnostics
        )
    }

    fun read(tagSignal: NfcTagSignal, invocationContext: InvocationContext? = null): ExecutionResult {
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val values = tagValues + runCatching {
            NfcCredentialEvidence.fields(tagValues)
        }.getOrDefault(emptyMap())
        val uid = values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val valid = uid.isNotBlank()
        val context = invocationContext?.asMap(ID).orEmpty()
        val request = request(
            action = ID,
            context = context,
            signals = listOf(tagSignal.signal)
        )
        val provenance = ProvenanceContext(
            provider = tagSignal.signal.provenance.provider,
            methodId = ID,
            methodVersion = VERSION,
            operatorId = invocationContext?.operatorId
        )
        val tagEntity = Entity(
            id = ArchitectureId("nfc-tag:$uid"),
            entityType = "NfcTag",
            attributes = mapOf(
                NfcEvidenceFields.TAG_UID_HEX to uid,
                NfcEvidenceFields.TAG_UID_DEC to values[NfcEvidenceFields.TAG_UID_DEC].orEmpty(),
                NfcEvidenceFields.TECH_LIST to values[NfcEvidenceFields.TECH_LIST].orEmpty()
            ),
            temporalContext = tagSignal.signal.temporalContext
        )
        val observation = Observation(
            phenomenon = "nfc.tag.state",
            subject = ArchitectureRef(tagEntity.id, tagEntity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(
                tagSignal.signal.id,
                tagSignal.signal.objectType,
                tagSignal.signal.signalType
            ),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val status = if (valid) TransformationStatus.Succeeded else TransformationStatus.Failed
        val transformation = Transformation(
            action = "interpret.nfc.signal",
            method = ref,
            inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            diagnostics = mapOf(
                "ndef_supported" to values[NfcEvidenceFields.NDEF_SUPPORTED].orEmpty(),
                "record_count" to values[NfcEvidenceFields.NDEF_RECORD_COUNT].orEmpty()
            ),
            temporalContext = observation.temporalContext,
            provenance = provenance
        )
        val result = As100ExecutionEngine.complete(
            request = request,
            status = status,
            entities = listOf(tagEntity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            validation = listOf(
                ValidationFinding(
                    passed = valid,
                    message = if (valid) "Android exposed the NFC tag UID." else "Android did not expose the NFC tag UID.",
                    field = NfcEvidenceFields.TAG_UID_HEX,
                    code = if (valid) "nfc_uid_present" else "nfc_uid_missing"
                )
            ),
            quality = QualityAssessment(
                usable = valid,
                metrics = transformation.diagnostics
            ),
            diagnostics = transformation.diagnostics
        ).withInvocationContext(invocationContext)
        return ResearchRuntime.session.record(result)
    }

    fun observationValues(result: ExecutionResult): Map<String, String> =
        result.observations.lastOrNull { it.phenomenon == "nfc.tag.state" }?.values.orEmpty()
}
