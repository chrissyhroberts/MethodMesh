package com.example.researchos.transport.workflow

import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.transport.GraphSelector
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode
import com.example.researchos.modules.ResearchOSModuleRegistry

data class ExternalActionRequest(
    val requestedId: String,
    val canonicalId: String = CapabilityAlias.canonical(requestedId),
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

object CapabilityAlias {
    private val compatibilityAliases = mapOf(
        "nfc.read" to "nfc_tag_read",
        "nfc.write" to "nfc_tag_write",
        "identity.verify" to "admin_fingerprint_confirmation",
        "fingerprint.verify" to "admin_fingerprint_confirmation",
        "gps.navigate" to "gps_target_navigator",
        "gps.navigate_to_target" to "gps_target_navigator",
        "gps.target" to "gps_target_navigator",
        "scale.capture" to "calibrated_scale"
    )

    fun canonical(id: String): String {
        val trimmed = id.trim()
        return ResearchOSModuleRegistry.canonicalAction(trimmed)
            ?.let { compatibilityAliases[it] ?: it }
            ?: compatibilityAliases[trimmed]
            ?: trimmed
    }
}
