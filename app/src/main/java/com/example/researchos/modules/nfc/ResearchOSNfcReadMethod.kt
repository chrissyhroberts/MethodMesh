package com.example.researchos.modules.nfc

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
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
import com.example.researchos.core.ResearchRuntime

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
        outputs = NfcEvidenceFields.tagOutputFields + listOf(
            ResearchOutputFields.PROVENANCE_JSON,
            ResearchOutputFields.CAPTURE_OUTCOME_JSON,
            ResearchOutputFields.QUALITY_JSON,
            ResearchOutputFields.VALIDATION_JSON,
            ResearchOutputFields.ARTIFACT_JSON,
            ResearchOutputFields.EVIDENCE_JSON,
            ResearchOutputFields.EXECUTION_JSON
        ),
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

    private fun readBundleInternal(tagSignal: NfcTagSignal): NfcReadEvidenceBundle =
        NfcTagRepository.readTagSignal(
            tagSignal = tagSignal,
            methodId = ID,
            methodVersion = VERSION,
            methodObjectType = "Method",
            methodLabel = "NFC Tag Read"
        )

    fun readBundle(tagSignal: NfcTagSignal, invocationContext: InvocationContext? = null): NfcReadEvidenceBundle {
        val bundle = readBundleInternal(tagSignal).withInvocationContext(invocationContext)
        ResearchRuntime.session.record(bundle.executionResult)
        return bundle
    }

    private fun NfcReadEvidenceBundle.withInvocationContext(invocationContext: InvocationContext?): NfcReadEvidenceBundle {
        if (invocationContext == null) return this
        val contextMap = invocationContext.asMap() + mapOf("requested_capability" to ID)
        return copy(
            executionResult = executionResult.copy(
                request = executionResult.request.copy(context = contextMap)
            )
        )
    }

}
