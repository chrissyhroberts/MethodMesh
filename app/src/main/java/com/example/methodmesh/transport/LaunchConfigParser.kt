package com.example.methodmesh.transport

import com.example.methodmesh.transport.ril.RilRequestParser
import com.example.methodmesh.transport.ril.RilTransportAdapter
import java.net.URLDecoder

/**
 * Parses canonical MethodMesh deep links and Android intent URIs into a
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
)

object LaunchConfigParser {

    fun parse(text: String): ParsedLaunchConfig {
        val trimmed = text.trim()

        return when {
            RilRequestParser.looksLikeRil(trimmed) ->
                RilRequestParser.parse(trimmed, source = "ril_text")

            trimmed.startsWith("intent:#Intent") ->
                parseAndroidIntentUri(trimmed)

            trimmed.startsWith("methodmesh://") ->
                parseQueryLike(trimmed.substringAfter("?", trimmed.substringAfter("://")))

            trimmed.contains("=") ->
                parseQueryLike(trimmed.substringAfter("?", trimmed))

            else ->
                ParsedLaunchConfig(
                    methodId = null,
                    returnMode = null,
                    settings = emptyMap(),
                    warnings = listOf("Input was not recognised as a MethodMesh appearance, query string, or Android intent URI.")
                )
        }
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
