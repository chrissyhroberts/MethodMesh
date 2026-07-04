package com.example.researchos.transport.ril

import com.example.researchos.transport.GraphSelector
import com.example.researchos.transport.GraphSelectorParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode

/**
 * Minimal implementation binding for the ResearchOS Intent Language (RIL) v0.03.
 *
 * The RIL specification defines the semantic sections WHAT, WHEN, WHERE, HOW and
 * RESULT. It deliberately does not define Android extras or JSON binding syntax.
 * This parser implements a small human-readable text binding for Android/ODK
 * transport while preserving the RIL separation of concerns:
 *
 *   WHAT   -> ordered action identifiers
 *   WHERE  -> invocation context / subject entity
 *   HOW    -> execution/settings policies
 *   RESULT -> requested graph selectors and return mode
 *
 * Supported examples:
 *
 *   WHAT
 *   scan nfc
 *   verify identity fingerprint
 *   WHERE
 *   participant/P001
 *   RESULT
 *   return observation.nfc.uid as tag_uid
 *   return execution.id as execution_id
 *   format json
 *
 * Shorthand is also accepted for transport convenience:
 *
 *   execute nfc.read for participant/P001
 *   return observation.nfc.uid as tag_uid
 */
object RilRequestParser {

    fun looksLikeRil(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        val text = raw.trim()
        val lower = text.lowercase()
        return Regex("""(?is)^\s*(what|when|where|how|result|results)\b""").containsMatchIn(text) ||
            lower.startsWith("execute ") ||
            Regex("""(?is)[;\n]\s*(what|when|where|how|result|results)\b""").containsMatchIn(text) ||
            Regex("""(?im)^\s*(scan|capture|measure|verify|identify|retrieve|submit|observe|navigate|execute)\b""").containsMatchIn(text)
    }

    fun parse(raw: String, source: String = "ril_text"): ParsedLaunchConfig {
        val lines = normaliseLines(raw)

        val actions = mutableListOf<String>()
        val selectors = mutableListOf<GraphSelector>()
        val context = linkedMapOf<String, String>()
        val settings = linkedMapOf<String, String>()
        val warnings = mutableListOf<String>()
        var returnMode: ReturnMode? = null
        var section: Section = Section.WHAT

        lines.forEach { line ->
            val maybeSection = Section.fromLine(line)
            if (maybeSection != null) {
                section = maybeSection
                return@forEach
            }

            extractInlineSubject(line)?.let { applySubject(it, context) }

            if (isResultDirective(line)) {
                parseResultLine(line, selectors, settings).also { mode -> if (mode != null) returnMode = mode }
                return@forEach
            }

            when (section) {
                Section.WHAT -> parseWhatLine(line, actions, settings, warnings)
                Section.WHEN -> parsePolicyLine(line, "when", settings)
                Section.WHERE -> parseWhereLine(line, context, settings)
                Section.HOW -> parseHowLine(line, settings).also { mode -> if (mode != null) returnMode = mode }
                Section.RESULT -> parseResultLine(line, selectors, settings).also { mode -> if (mode != null) returnMode = mode }
            }
        }

        val methodId = actions.firstOrNull()
        return ParsedLaunchConfig(
            methodId = methodId,
            actionIds = actions,
            returnMode = returnMode,
            settings = settings,
            context = context,
            returnSelectors = selectors,
            warnings = warnings,
            source = source
        )
    }

    private fun normaliseLines(raw: String): List<String> = raw
        .replace("\r\n", "\n")
        .replace("\r", "\n")
        // Android shell testing often uses semicolon-separated RIL. Treat these
        // as transport separators, not as part of the language payload.
        .replace(";", "\n")
        // Allow compact one-line requests such as:
        // WHAT scan nfc WHERE participant/P001 RESULT return observation.nfc.uid as tag_uid
        .replace(Regex("(?i)\\b(WHAT|WHEN|WHERE|HOW|RESULTS?|THEN)\\b")) { match ->
            if (match.value.equals("THEN", ignoreCase = true)) "\n" else "\n${match.value.uppercase()}\n"
        }
        .split('\n')
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }

    private fun isResultDirective(line: String): Boolean {
        val lower = line.lowercase()
        return lower.startsWith("return ") ||
            lower.startsWith("data ") ||
            lower.startsWith("provenance ") ||
            lower.startsWith("metadata ") ||
            lower.startsWith("diagnostics ") ||
            lower.startsWith("format ") ||
            lower.startsWith("shape ") ||
            lower.startsWith("return_mode") ||
            GraphSelectorParser.looksLikeSelector(line)
    }

    private fun parseWhatLine(
        line: String,
        actions: MutableList<String>,
        settings: MutableMap<String, String>,
        warnings: MutableList<String>
    ) {
        val actionPart = line.substringBefore(" for ", line).trim()
        val parameterValues = parseParameterBlock(actionPart)
        settings.putAll(parameterValues)
        val withoutParams = actionPart.replace(Regex("\\(.*\\)"), "")
        val action = canonicalAction(withoutParams)
        if (action != null) {
            actions.add(action)
        } else if (!line.startsWith("return ", ignoreCase = true)) {
            warnings.add("RIL WHAT line was not recognised: $line")
        }
    }

    private fun parseWhereLine(
        line: String,
        context: MutableMap<String, String>,
        settings: MutableMap<String, String>
    ) {
        when {
            line.startsWith("for ", ignoreCase = true) -> applySubject(line.removePrefixIgnoreCase("for").trim(), context)
            line.startsWith("subject ", ignoreCase = true) -> applySubject(line.removePrefixIgnoreCase("subject").trim(), context)
            line.startsWith("entity ", ignoreCase = true) -> applySubject(line.removePrefixIgnoreCase("entity").trim(), context)
            line.contains("=") -> {
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim().trimQuote()
                if (key in contextKeys || key.startsWith("context_")) {
                    context[key.removePrefix("context_")] = value
                } else {
                    settings[key] = value
                }
            }
            line.contains("/") -> applySubject(line, context)
            else -> settings["where"] = line
        }
    }

    private fun parseHowLine(line: String, settings: MutableMap<String, String>): ReturnMode? {
        val mode = parseReturnMode(line)
        if (mode != null) return mode
        parsePolicyLine(line, "how", settings)
        return null
    }

    private fun parseResultLine(
        line: String,
        selectors: MutableList<GraphSelector>,
        settings: MutableMap<String, String>
    ): ReturnMode? {
        val mode = parseReturnMode(line)
        if (mode != null) return mode

        when {
            line.startsWith("return ", ignoreCase = true) -> {
                val selectorText = line.removePrefixIgnoreCase("return").trim()
                selectors.addAll(GraphSelectorParser.parse(selectorText))
            }
            line.startsWith("data ", ignoreCase = true) -> {
                val selectorText = line.removePrefixIgnoreCase("data").trim()
                selectors.addAll(GraphSelectorParser.parse(selectorText))
            }
            line.startsWith("provenance ", ignoreCase = true) -> {
                settings["provenance"] = line.removePrefixIgnoreCase("provenance").trim()
            }
            line.startsWith("metadata ", ignoreCase = true) -> {
                settings["metadata"] = line.removePrefixIgnoreCase("metadata").trim()
            }
            line.startsWith("diagnostics ", ignoreCase = true) -> {
                settings["diagnostics"] = line.removePrefixIgnoreCase("diagnostics").trim()
            }
            GraphSelectorParser.looksLikeSelector(line) -> selectors.addAll(GraphSelectorParser.parse(line))
            line.contains("=") -> {
                val key = line.substringBefore("=").trim()
                val value = line.substringAfter("=").trim().trimQuote()
                settings["result_$key"] = value
            }
        }
        return null
    }

    private fun parsePolicyLine(line: String, prefix: String, settings: MutableMap<String, String>) {
        if (line.contains("=")) {
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim().trimQuote()
            settings[key] = value
        } else {
            val tokens = line.split(Regex("\\s+"), limit = 2)
            if (tokens.size == 2) settings["${prefix}_${tokens[0].lowercase()}"] = tokens[1].trimQuote()
        }
    }

    private fun canonicalAction(raw: String): String? {
        val value = raw.trim().trimQuote()
        if (value.isBlank()) return null
        val lower = value.lowercase()

        if (lower.startsWith("execute ")) {
            return value.removePrefixIgnoreCase("execute").trim().substringBefore(" ").trim()
        }

        val words = lower.split(Regex("\\s+|\\.|/|:")).filter { it.isNotBlank() }
        val verb = words.firstOrNull() ?: return null
        val rest = words.drop(1).toSet()

        return when (verb) {
            "scan", "read", "capture" -> when {
                "nfc" in rest || "tag" in rest -> "nfc.read"
                "fingerprint" in rest || "identity" in rest -> "identity.verify"
                else -> null
            }
            "verify", "identify" -> when {
                "fingerprint" in rest || "identity" in rest || "participant" in rest || "operator" in rest -> "identity.verify"
                else -> null
            }
            "navigate" -> when {
                "gps" in rest || "target" in rest || "location" in rest -> "gps.navigate_to_target"
                else -> "gps.navigate_to_target"
            }
            "measure", "observe" -> when {
                "scale" in rest || "calibrated" in rest -> "scale.capture"
                else -> null
            }
            else -> null
        }
    }

    private fun parseParameterBlock(raw: String): Map<String, String> {
        val params = linkedMapOf<String, String>()
        Regex("\\((.*)\\)").find(raw)?.groupValues?.getOrNull(1)?.let { inside ->
            inside.split(',', ';').forEach { part ->
                if (part.contains("=")) {
                    params[part.substringBefore("=").trim()] = part.substringAfter("=").trim().trimQuote()
                }
            }
        }
        return params
    }

    private fun extractInlineSubject(line: String): String? {
        val match = Regex("\\bfor\\s+([^;]+)$", RegexOption.IGNORE_CASE).find(line)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun applySubject(raw: String, context: MutableMap<String, String>) {
        val subject = raw.trim().trimQuote()
        if (subject.isBlank()) return
        context["subject"] = subject
        if (subject.contains("/")) {
            context["entity_type"] = subject.substringBefore("/").trim()
            context["entity_id"] = subject.substringAfter("/").trim()
        } else {
            val parts = subject.split(Regex("\\s+"), limit = 2)
            if (parts.size == 2) {
                context["entity_type"] = parts[0]
                context["entity_id"] = parts[1]
            }
        }
    }

    private fun parseReturnMode(line: String): ReturnMode? {
        val lower = line.lowercase()
        val value = when {
            lower.startsWith("format ") -> line.removePrefixIgnoreCase("format").trim()
            lower.startsWith("shape ") -> line.removePrefixIgnoreCase("shape").trim()
            lower.startsWith("return_mode ") -> line.removePrefixIgnoreCase("return_mode").trim()
            lower.startsWith("return mode ") -> line.removePrefixIgnoreCase("return mode").trim()
            lower.startsWith("return_mode=") -> line.substringAfter("=").trim()
            else -> null
        }
        return value?.let { ReturnMode.fromId(it.trimQuote()) }
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

    private fun String.trimQuote(): String = trim().trim('"', '\'')

    private val contextKeys = setOf(
        "caller", "entity_type", "entity_id", "subject", "subject_id", "participant_id",
        "specimen_id", "visit_id", "form_id", "operator_id", "context_entity_type", "context_entity_id"
    )

    private enum class Section {
        WHAT, WHEN, WHERE, HOW, RESULT;

        companion object {
            fun fromLine(line: String): Section? = when (line.trim().uppercase()) {
                "WHAT" -> WHAT
                "WHEN" -> WHEN
                "WHERE" -> WHERE
                "HOW" -> HOW
                "RESULT", "RESULTS" -> RESULT
                else -> null
            }
        }
    }
}
