package com.example.researchos.transport.ril

import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.ParsedLaunchConfig
import java.nio.charset.StandardCharsets
import java.util.Base64

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

    /**
     * Flat Android/ODK transport has a deliberately explicit input namespace.
     *
     * `input_<name>` is exposed to the capability as `<name>`. Unprefixed
     * unknown keys are caller return placeholders and are ignored. This keeps
     * output fields from accidentally overriding capability configuration.
     *
     * `input64_<name>` is the same contract with a URL-safe Base64 encoded
     * UTF-8 value. It is useful for static structured inputs whose punctuation
     * is awkward in XLSForm intent syntax.
     */
    private fun transportSettings(values: Map<String, String>): Map<String, String> = buildMap {
        values.filterKeys { it.startsWith(INPUT_PREFIX) }.forEach { (key, value) ->
            val setting = key.removePrefix(INPUT_PREFIX)
            if (setting.isNotBlank() && value.isNotBlank()) put(setting, value)
        }
        values.filterKeys { it.startsWith(INPUT64_PREFIX) }.forEach { (key, value) ->
            val setting = key.removePrefix(INPUT64_PREFIX)
            decodeUrlSafeBase64(value)?.let { decoded ->
                if (setting.isNotBlank()) put(setting, decoded)
            }
        }
    }

    private fun decodeUrlSafeBase64(value: String): String? {
        if (value.isBlank()) return null
        val padding = "=".repeat((4 - value.length % 4) % 4)
        return runCatching {
            String(
                Base64.getUrlDecoder().decode(value + padding),
                StandardCharsets.UTF_8
            )
        }.getOrNull()
    }

    private val contextKeys = setOf(
        "caller", "entity_type", "entity_id", "participant_id", "specimen_id",
        "visit_id", "form_id", "operator_id"
    )

    private const val INPUT_PREFIX = "input_"
    private const val INPUT64_PREFIX = "input64_"
}
