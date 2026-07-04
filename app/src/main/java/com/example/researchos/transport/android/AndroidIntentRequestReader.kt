package com.example.researchos.transport.android

import android.content.Intent
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.workflow.ExternalWorkflowRequest

object AndroidIntentRequestReader {
    fun parse(intent: Intent): ParsedLaunchConfig = intent.dataString
        ?.let { LaunchConfigParser.parse(it) }
        ?: parseExtras(intent)

    fun workflowRequest(intent: Intent): ExternalWorkflowRequest {
        val parsed = parse(intent)
        return ExternalWorkflowRequest.from(
            parsed = parsed,
            invocationContext = invocationContextFrom(parsed)
        )
    }

    fun invocationContextFrom(parsed: ParsedLaunchConfig): InvocationContext {
        val merged = parsed.settings + parsed.context
        val entityType = merged["entity_type"]
            ?: merged["context_entity_type"]
            ?: when {
                merged["specimen_id"] != null -> "specimen"
                else -> "participant"
            }
        val entityId = merged["entity_id"]
            ?: merged["context_entity_id"]
            ?: merged["subject_id"]
            ?: merged["participant_id"]
            ?: merged["specimen_id"]
            ?: "P001"

        return InvocationContext(
            caller = merged["caller"] ?: parsed.source ?: "external_app",
            entityType = entityType,
            entityId = entityId,
            visitId = merged["visit_id"].orEmpty(),
            formId = merged["form_id"].orEmpty(),
            operatorId = merged["operator_id"].orEmpty()
        )
    }

    private fun parseExtras(intent: Intent): ParsedLaunchConfig {
        val values = mutableMapOf<String, String>()
        val extras = intent.extras
        extras?.keySet()?.forEach { key -> values[key] = extras.get(key)?.toString().orEmpty() }
        intent.action?.let { values.putIfAbsent("action", it) }

        val actionText = values["actions"]
            ?: values["chain"]
            ?: values["workflow"]
            ?: values["methods"]
            ?: values["method_chain"]
        val actionIds = parseActionIds(actionText)
        val methodId = actionIds.firstOrNull()
            ?: values["method"]
            ?: values["method_id"]
            ?: values["module"]
            ?: values["module_id"]
            ?: values["capability"]
            ?: values["capability_id"]

        val returnValue = values["return"]
        val selectorText = values["returns"]
            ?: values["graph_return"]
            ?: values["graph_returns"]
            ?: values["select"]
            ?: values["selector"]
            ?: values["selectors"]
            ?: returnValue?.takeIf { GraphSelectorParser.looksLikeSelector(it) }

        val returnMode = values["return_mode"]
            ?: returnValue?.takeUnless { GraphSelectorParser.looksLikeSelector(it) }
            ?: values["mode"]

        val reserved = setOf(
            "method", "method_id", "module", "module_id", "capability", "capability_id",
            "actions", "chain", "workflow", "methods", "method_chain",
            "return_mode", "return", "mode", "action",
            "returns", "graph_return", "graph_returns", "select", "selector", "selectors"
        )

        val contextKeys = setOf(
            "caller", "entity_type", "entity_id", "subject_id", "participant_id",
            "specimen_id", "visit_id", "form_id", "operator_id",
            "context_entity_type", "context_entity_id"
        )

        val context = values
            .filterKeys { key -> key.startsWith("context_") || key in contextKeys }
            .mapKeys { (key, _) -> key.removePrefix("context_") }

        val settings = values
            .filterKeys { key -> key !in reserved && key !in contextKeys && !key.startsWith("context_") }

        return ParsedLaunchConfig(
            methodId = methodId,
            actionIds = actionIds.ifEmpty { methodId?.let { listOf(it) } ?: emptyList() },
            returnMode = returnMode?.let { ReturnMode.fromId(it) },
            settings = settings,
            context = context,
            returnSelectors = GraphSelectorParser.parse(selectorText),
            source = "android_extras"
        )
    }

    private fun parseActionIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '>', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
    }
}
