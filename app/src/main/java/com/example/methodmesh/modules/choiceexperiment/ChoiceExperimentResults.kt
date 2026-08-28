package com.example.methodmesh.modules.choiceexperiment

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
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState

internal object DceResultFactory {
    const val VERSION = "0.1.0"

    fun method(method: DceMethod): As100Method = object : As100Method {
        override val id: String = method.id
        override val ref: ArchitectureRef = ArchitectureRef(
            id = ArchitectureId(method.id),
            type = "Method",
            label = method.title
        )
        override val descriptor: MethodDescriptor = MethodDescriptor(
            id = ArchitectureId(method.id),
            methodType = MethodObjectType.Method,
            name = method.title,
            version = VERSION,
            description = "Interactive discrete choice experiment capability: ${method.title}.",
            outputs = outputFields(),
            graphOutputs = listOf(method.phenomenon),
            parameters = mapOf(
                "category" to "Discrete choice experiment",
                "configuration" to configurationParameters(method)
            )
        )
        override val contract: MethodContract = MethodContract(
            method = ref,
            producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
            producedFields = outputFields(),
            producedGraphOutputs = listOf(method.phenomenon)
        )
        override fun request(
            action: String,
            context: Map<String, String>,
            signals: List<com.example.methodmesh.core.methodmesh.Signal>,
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
        ): ExecutionResult = complete(
            method = method,
            request = request,
            config = DceConfigParser.from(request.context, method),
            resultJson = "{}",
            responseCount = 0,
            extraValues = mapOf("screen_required" to "true")
        )
    }

    fun complete(
        method: DceMethod,
        request: ExecutionRequest,
        config: DceConfig,
        resultJson: String,
        responseCount: Int,
        extraValues: Map<String, String> = emptyMap()
    ): ExecutionResult {
        val provenance = ProvenanceContext(
            provider = "methodmesh.modules.choiceexperiment",
            methodId = method.id,
            methodVersion = VERSION
        )
        val values = linkedMapOf(
            "method" to method.id.removePrefix("dce."),
            "module" to "choice",
            "result_json" to resultJson,
            "session_id" to config.sessionId,
            "seed" to config.seed,
            "round_count" to config.rounds.toString(),
            "response_count" to responseCount.toString()
        ) + extraValues
        val observation = Observation(
            phenomenon = method.phenomenon,
            subject = InvocationContext.from(request.context)?.subjectRef(),
            values = values,
            provenance = provenance,
            temporalContext = request.temporalContext
        )
        val transformation = Transformation(
            action = "run.${method.id}",
            method = ArchitectureRef(ArchitectureId(method.id), "Method", method.title),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf("response_count" to responseCount.toString())
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf(
                "method" to method.id,
                "module" to "choice",
                "response_count" to responseCount.toString()
            )
        )
    }

    fun requestFor(method: DceMethod, action: String, context: Map<String, String>): ExecutionRequest =
        As100ExecutionEngine.request(
            action = action,
            method = ArchitectureRef(ArchitectureId(method.id), "Method", method.title),
            context = context
        )

    private fun outputFields(): List<String> = listOf(
        "method",
        "module",
        "result_json",
        "session_id",
        "seed",
        "round_count",
        "response_count"
    )

    private fun configurationParameters(method: DceMethod): String = when (method) {
        DceMethod.Pairwise -> "rounds,items,seed"
        DceMethod.MaxDiff -> "rounds,items,items_per_round,seed"
        DceMethod.Ranking -> "rounds,items,seed"
        DceMethod.Points -> "points,items,seed"
        DceMethod.Conjoint -> "rounds,classes,profiles_per_round,seed"
    }
}
