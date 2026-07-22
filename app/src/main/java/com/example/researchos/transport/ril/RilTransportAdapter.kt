package com.example.researchos.transport.ril

import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.ParsedLaunchConfig

/**
 * Transport adapter that makes RIL the canonical internal request path.
 *
 * Android extras and function-style ODK calls are compiled into RIL before
 * execution. The canonical transport keys are intentionally small and strict.
 */
object RilTransportAdapter {
    private const val REQUEST_KEY = "ril"

    fun parse(values: Map<String, String>, source: String): ParsedLaunchConfig {
        val requestText = values[REQUEST_KEY]?.takeIf { it.isNotBlank() }
        if (RilRequestParser.looksLikeRil(requestText)) {
            return RilRequestParser.parse(requestText.orEmpty(), source = source)
        }

        val rilText = compileTransportValuesToRil(values)
        val parsed = RilRequestParser.parse(rilText, source = source)
        val context = parsed.context + transportContext(values)
        val settings = parsed.settings + transportSettings(values)
        return parsed.copy(
            context = context,
            settings = settings
        )
    }

    fun compileTransportValuesToRil(values: Map<String, String>): String {
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
        return listOfNotNull(values["method_id"]?.takeIf { it.isNotBlank() })
    }

    private fun subject(values: Map<String, String>): String? {
        values["participant_id"]?.takeIf { it.isNotBlank() }?.let { return "participant/$it" }
        values["specimen_id"]?.takeIf { it.isNotBlank() }?.let { return "specimen/$it" }

        val type = values["entity_type"]
        val id = values["entity_id"]
        return if (!type.isNullOrBlank() && !id.isNullOrBlank()) "$type/$id" else null
    }

    private fun selectorLines(values: Map<String, String>): List<String> {
        val selectorText = values["returns"]

        return selectorText
            ?.split(',', ';', '\n')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }

    private fun returnMode(values: Map<String, String>): String? {
        return values["return_mode"]
    }

    private fun transportContext(values: Map<String, String>): Map<String, String> = values
        .filterKeys { key -> key in contextKeys }

    private fun transportSettings(values: Map<String, String>): Map<String, String> = values
        .filterKeys { key -> key !in reservedKeys && key !in contextKeys }

    private val contextKeys = setOf(
        "caller", "entity_type", "entity_id", "participant_id", "specimen_id",
        "visit_id", "form_id", "operator_id"
    )

    private val reservedKeys = setOf(
        "method_id", "return_mode", "action", "ril", "returns"
    )
}
