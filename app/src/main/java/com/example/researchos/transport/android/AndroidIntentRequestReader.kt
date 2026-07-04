package com.example.researchos.transport.android

import android.content.Intent
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.ril.RilTransportAdapter
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

        return RilTransportAdapter.parse(values, source = "android_extras")
    }

    private fun parseActionIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(',', '>', '|', '\n').map { it.trim() }.filter { it.isNotBlank() }
    }
}
