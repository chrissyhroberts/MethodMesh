package com.example.researchos.transport.ril

import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode

/**
 * Transport adapter that makes RIL the canonical internal request path.
 *
 * Android extras, ODK appearance strings, query strings and intent URIs may still
 * provide legacy parameters such as `actions`, `returns` and `entity_id`, but
 * those parameters are first compiled into a small RIL request and then parsed
 * through [RilRequestParser]. This prevents the app from maintaining two
 * independent execution request models.
 */
object RilTransportAdapter {
    private val requestKeys = listOf("ril", "request", "researchos_request")

    fun parse(values: Map<String, String>, source: String): ParsedLaunchConfig {
        val requestText = requestKeys.firstNotNullOfOrNull { key -> values[key]?.takeIf { it.isNotBlank() } }
        if (RilRequestParser.looksLikeRil(requestText)) {
            return RilRequestParser.parse(requestText.orEmpty(), source = source)
        }

        val rilText = compileLegacyValuesToRil(values)
        val parsed = RilRequestParser.parse(rilText, source = source)
        val context = parsed.context + legacyContext(values)
        val settings = parsed.settings + legacySettings(values)
        val warnings = parsed.warnings + listOf("Legacy transport parameters were normalised through the RIL adapter.")

        return parsed.copy(
            context = context,
            settings = settings,
            warnings = warnings
        )
    }

    fun compileLegacyValuesToRil(values: Map<String, String>): String {
        val actions = actionIds(values)
        val subject = subject(values)
        val selectors = selectorLines(values)
        val mode = returnMode(values)

        return buildString {
            appendLine("WHAT")
            actions.forEach { action -> appendLine("execute $action") }
            appendLine("WHERE")
            if (subject != null) appendLine(subject)
            appendLine("RESULT")
            selectors.forEach { selector -> appendLine("return $selector") }
            if (mode != null) appendLine("format $mode")
        }
    }

    private fun actionIds(values: Map<String, String>): List<String> {
        val raw = values["actions"]
            ?: values["chain"]
            ?: values["workflow"]
            ?: values["methods"]
            ?: values["method_chain"]

        val fromChain = raw
            ?.split(',', '>', '|', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()

        if (fromChain.isNotEmpty()) return fromChain

        return listOfNotNull(
            values["method"]
                ?: values["method_id"]
                ?: values["module"]
                ?: values["module_id"]
                ?: values["capability"]
                ?: values["capability_id"]
        )
    }

    private fun subject(values: Map<String, String>): String? {
        values["subject"]?.takeIf { it.isNotBlank() }?.let { return it }
        values["participant_id"]?.takeIf { it.isNotBlank() }?.let { return "participant/$it" }
        values["specimen_id"]?.takeIf { it.isNotBlank() }?.let { return "specimen/$it" }

        val type = values["entity_type"] ?: values["context_entity_type"]
        val id = values["entity_id"] ?: values["context_entity_id"] ?: values["subject_id"]
        return if (!type.isNullOrBlank() && !id.isNullOrBlank()) "$type/$id" else null
    }

    private fun selectorLines(values: Map<String, String>): List<String> {
        val returnValue = values["return"]
        val selectorText = values["returns"]
            ?: values["graph_return"]
            ?: values["graph_returns"]
            ?: values["select"]
            ?: values["selector"]
            ?: values["selectors"]
            ?: returnValue?.takeIf { GraphSelectorParser.looksLikeSelector(it) }

        return selectorText
            ?.split(',', ';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun returnMode(values: Map<String, String>): String? {
        val returnValue = values["return"]
        return values["return_mode"]
            ?: returnValue?.takeUnless { GraphSelectorParser.looksLikeSelector(it) }
            ?: values["mode"]
    }

    private fun legacyContext(values: Map<String, String>): Map<String, String> = values
        .filterKeys { key -> key.startsWith("context_") || key in contextKeys }
        .mapKeys { (key, _) -> key.removePrefix("context_") }

    private fun legacySettings(values: Map<String, String>): Map<String, String> = values
        .filterKeys { key -> key !in reservedKeys && key !in contextKeys && !key.startsWith("context_") }

    private val contextKeys = setOf(
        "caller", "entity_type", "entity_id", "subject", "subject_id", "participant_id",
        "specimen_id", "visit_id", "form_id", "operator_id", "context_entity_type", "context_entity_id"
    )

    private val reservedKeys = setOf(
        "method", "method_id", "module", "module_id", "capability", "capability_id",
        "actions", "chain", "workflow", "methods", "method_chain",
        "return_mode", "return", "mode", "action", "ril", "request", "researchos_request",
        "returns", "graph_return", "graph_returns", "select", "selector", "selectors"
    )
}
