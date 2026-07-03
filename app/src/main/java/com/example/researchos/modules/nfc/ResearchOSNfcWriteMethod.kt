package com.example.researchos.modules.nfc

import com.example.researchos.core.Observation
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import com.example.researchos.settings.SettingsState

/**
 * Native AS1.00 method for NFC tag writes.
 *
 * NFC Write is an intervention method: it accepts a transient NFC tag-discovery
 * signal from the Android NFC Device Service, performs an NDEF write, and
 * returns intervention plus post-write observation records. The legacy
 * NfcWriteMethod remains only as the current Compose launcher shell.
 */
object As100NfcWriteMethod : As100Method {
    const val ID = "nfc_tag_write"
    const val VERSION = "0.3.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "NFC Tag Write"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC Tag Write",
        version = VERSION,
        description = "Write an NDEF record to an NFC tag and emit intervention outcome plus post-write NFC observation evidence.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = listOf(
            NfcWriteFields.WRITE_SUCCESS,
            NfcWriteFields.WRITE_MESSAGE,
            NfcWriteFields.WRITE_RECORD_TYPE,
            NfcWriteFields.WRITE_SIZE_BYTES,
            NfcWriteFields.INTERVENTION_JSON,
            NfcWriteFields.POST_WRITE_EVIDENCE_JSON
        ) + NfcEvidenceFields.tagOutputFields + researchEnvelopeSchema().map { it.id },
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "device_service" to AndroidNfcDeviceService.SERVICE_ID,
            "interaction" to "interactive_nfc_write"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        requiredContext = listOf("record_type", "value"),
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
                    "reason" to "NFC write requires a live NfcTagSignal from the Android NFC Device Service."
                )
            )
        }

        val transformation = Transformation(
            action = "intervene.nfc.write",
            method = ref,
            inputs = listOf(ArchitectureRef(signal.id, signal.objectType, signal.signalType)),
            status = TransformationStatus.Unsupported,
            diagnostics = mapOf(
                "reason" to "A generic Signal does not contain the Android Tag handle needed for NDEF writing. Use write(tagSignal, request) from the NFC Device Service path."
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

    fun writeBundle(tagSignal: NfcTagSignal, request: NfcWriteRequest): NfcWriteEvidenceBundle =
        NfcTagRepository.writeTagSignal(
            tagSignal = tagSignal,
            request = request,
            methodId = ID,
            methodVersion = VERSION,
            methodObjectType = "Method",
            methodLabel = "NFC Tag Write"
        )

    fun write(tagSignal: NfcTagSignal, request: NfcWriteRequest): Observation =
        NfcObservationMapper.fromWriteBundle(writeBundle(tagSignal, request))
}
