package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.transport.GraphSelector
import com.example.methodmesh.transport.ParsedLaunchConfig
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.modules.MethodMeshModuleRegistry

data class ExternalActionRequest(
    val requestedId: String,
    val canonicalId: String = MethodMeshModuleRegistry.canonicalAction(requestedId)
        ?: requestedId.trim(),
    val settings: Map<String, String> = emptyMap()
)

data class ExternalWorkflowRequest(
    val actions: List<ExternalActionRequest>,
    val invocationContext: InvocationContext,
    val returns: List<GraphSelector>,
    val returnMode: ReturnMode,
    val settings: Map<String, String> = emptyMap(),
    val source: String = "android_intent",
    val warnings: List<String> = emptyList()
) {
    companion object {
        fun from(parsed: ParsedLaunchConfig, invocationContext: InvocationContext): ExternalWorkflowRequest {
            val actionIds = parsed.actionIds.ifEmpty { parsed.methodId?.let { listOf(it) } ?: emptyList() }
            return ExternalWorkflowRequest(
                actions = actionIds.map { ExternalActionRequest(requestedId = it, settings = parsed.settings) },
                invocationContext = invocationContext,
                returns = parsed.returnSelectors,
                returnMode = parsed.returnMode ?: ReturnMode.Json,
                settings = parsed.settings,
                source = parsed.source ?: "android_intent",
                warnings = parsed.warnings
            )
        }
    }
}

data class ConfirmedWorkflowStep(
    val action: ExternalActionRequest,
    val result: ExecutionResult
)
