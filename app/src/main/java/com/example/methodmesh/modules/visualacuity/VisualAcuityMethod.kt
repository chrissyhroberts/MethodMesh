package com.example.methodmesh.modules.visualacuity

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
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
import com.example.methodmesh.settings.SettingsState

object VisualAcuityFields {
    const val STATUS = "visual_acuity_status"
    const val RESULT = "visual_acuity_result"
    const val LOGMAR = "visual_acuity_logmar"
    const val SNELLEN_METRIC = "visual_acuity_snellen_metric"
    const val SNELLEN_IMPERIAL = "visual_acuity_snellen_imperial"
    const val DECIMAL = "visual_acuity_decimal"
    const val AUDIT_JSON = "visual_acuity_audit_json"
    const val ERROR = "visual_acuity_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        LOGMAR,
        SNELLEN_METRIC,
        SNELLEN_IMPERIAL,
        DECIMAL,
        AUDIT_JSON,
        ERROR
    )
}

object As100VisualAcuityMethod : As100Method {
    const val ID = "visual_acuity.measure"
    const val VERSION = "0.1.4"

    override val id: String = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Visual acuity measurement")

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Visual acuity",
        version = VERSION,
        description = "Measure visual acuity with a calibrated tumbling-E fast staircase.",
        inputs = listOf("manual.visual_acuity.response"),
        outputs = VisualAcuityFields.outputs,
        graphOutputs = listOf("measurement.visual_acuity"),
        parameters = mapOf(
            "category" to "Measurement",
            "status" to "Development",
            "interaction" to "calibrated_tumbling_e",
            "staircase" to "${VisualAcuityStaircase.ALGORITHM_ID}:${VisualAcuityStaircase.ALGORITHM_VERSION}"
        )
    )

    override val contract = MethodContract(
        method = ref,
        acceptedSignals = listOf("manual.visual_acuity.response"),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    /** The measurement is interactive; the capability screen constructs the completed result. */
    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = As100ExecutionEngine.complete(
        request = request,
        status = TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "Visual acuity measurement requires the interactive calibrated optotype screen.")
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[VisualAcuityFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext(
            provider = "methodmesh.measure.visual_acuity",
            methodId = ID,
            methodVersion = VERSION
        )
        val observation = Observation(
            phenomenon = "measurement.visual_acuity",
            subject = invocation?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = request.action,
            method = ref,
            inputs = request.inputs + request.signals.map { ArchitectureRef(it.id, "Signal", it.signalType) },
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(VisualAcuityFields.ERROR to values[VisualAcuityFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
