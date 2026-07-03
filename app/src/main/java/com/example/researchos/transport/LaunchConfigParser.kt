package com.example.researchos.transport

import com.example.researchos.core.MethodExecutionRequest
import com.example.researchos.core.ResearchContext
import java.net.URLDecoder

/**
 * Parses ODK appearance strings, Android intent URIs, and simple query strings into a
 * transport-neutral launch request.
 */
data class ParsedLaunchConfig(
    val methodId: String?,
    val returnMode: ReturnMode?,
    val settings: Map<String, String>,
    val context: Map<String, String> = emptyMap(),
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
            (trimmed.startsWith("researchos(") || trimmed.startsWith("xlsformlab(")) && trimmed.endsWith(")") ->
                parseAppearance(trimmed)

            trimmed.startsWith("intent:#Intent") ->
                parseAndroidIntentUri(trimmed)

            trimmed.contains("=") ->
                parseQueryLike(trimmed)

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

    private fun buildConfig(values: Map<String, String>, source: String): ParsedLaunchConfig {
        val methodId = values["method"]
            ?: values["method_id"]
            ?: values["module"]
            ?: values["module_id"]

        val returnMode = values["return_mode"]
            ?: values["return"]
            ?: values["mode"]

        val reserved = setOf(
            "method", "method_id", "module", "module_id",
            "return_mode", "return", "mode"
        )

        val context = values
            .filterKeys { key -> key.startsWith("context_") }
            .mapKeys { (key, _) -> key.removePrefix("context_") }

        val settings = values
            .filterKeys { key -> key !in reserved && !key.startsWith("context_") }

        return ParsedLaunchConfig(
            methodId = methodId,
            returnMode = returnMode?.let { ReturnMode.fromId(it) },
            settings = settings,
            context = context,
            source = source
        )
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
