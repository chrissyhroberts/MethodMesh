package com.example.researchos.transport

import com.example.researchos.core.MethodExecutionRequest
import com.example.researchos.core.ResearchContext
import com.example.researchos.transport.ril.RilRequestParser
import com.example.researchos.transport.ril.RilTransportAdapter
import java.net.URLDecoder

/**
 * Parses ODK appearance strings, Android intent URIs, and simple query strings into a
 * transport-neutral launch request.
 */
data class ParsedLaunchConfig(
    val methodId: String?,
    val actionIds: List<String> = methodId?.let { listOf(it) } ?: emptyList(),
    val returnMode: ReturnMode?,
    val settings: Map<String, String>,
    val context: Map<String, String> = emptyMap(),
    val returnSelectors: List<GraphSelector> = emptyList(),
    val warnings: List<String> = emptyList(),
    val source: String? = null
) {
    fun toExecutionRequest(): MethodExecutionRequest? =
        methodId?.let { id ->
            MethodExecutionRequest(
                methodId = id,
                context = ResearchContext(context),
                parameters = settings,
                transport = source
            )
        }
}

object LaunchConfigParser {

    fun parse(text: String): ParsedLaunchConfig {
        val trimmed = text.trim()

        return when {
            RilRequestParser.looksLikeRil(trimmed) ->
                RilRequestParser.parse(trimmed, source = "ril_text")

            (trimmed.startsWith("researchos(") || trimmed.startsWith("xlsformlab(")) && trimmed.endsWith(")") ->
                parseAppearance(trimmed)

            trimmed.startsWith("intent:#Intent") ->
                parseAndroidIntentUri(trimmed)

            trimmed.startsWith("researchos://") || trimmed.startsWith("xlsformlab://") ->
                parseQueryLike(trimmed.substringAfter("?", trimmed.substringAfter("://")))

            trimmed.contains("=") ->
                parseQueryLike(trimmed.substringAfter("?", trimmed))

            else ->
                ParsedLaunchConfig(
                    methodId = null,
                    returnMode = null,
                    settings = emptyMap(),
                    warnings = listOf("Input was not recognised as a ResearchOS appearance, query string, or Android intent URI.")
                )
        }
    }

    private fun parseAppearance(text: String): ParsedLaunchConfig {
        val inside = text
            .removePrefix("researchos(")
            .removePrefix("xlsformlab(")
            .removeSuffix(")")

        return buildConfig(
            values = parseKeyValueParts(inside.split(";"), androidPrefixes = false),
            source = "odk_appearance"
        )
    }

    private fun parseAndroidIntentUri(text: String): ParsedLaunchConfig {
        return buildConfig(
            values = parseKeyValueParts(text.split(";"), androidPrefixes = true),
            source = "android_intent_uri"
        )
    }

    private fun parseQueryLike(text: String): ParsedLaunchConfig {
        val normalised = text.removePrefix("?").replace("&", ";")
        return buildConfig(
            values = parseKeyValueParts(normalised.split(";"), androidPrefixes = false),
            source = "query"
        )
    }

    private fun buildConfig(values: Map<String, String>, source: String): ParsedLaunchConfig =
        RilTransportAdapter.parse(values, source)

    private fun parseActionIds(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw
            .split(',', '>', '|', '\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseKeyValueParts(parts: List<String>, androidPrefixes: Boolean): Map<String, String> {
        val values = mutableMapOf<String, String>()

        parts.forEach { rawPart ->
            val part = rawPart.trim()

            if (!part.contains("=")) {
                return@forEach
            }

            val key = part.substringBefore("=").removePrefix("S.").removePrefix("B.").removePrefix("i.").removePrefix("f.")
            val value = part.substringAfter("=")

            val normalisedKey = if (androidPrefixes && key.length > 2 && key[1] == '.') {
                key.substring(2)
            } else {
                key
            }

            values[decode(normalisedKey)] = decode(value)
        }

        return values
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value, "UTF-8")
}
