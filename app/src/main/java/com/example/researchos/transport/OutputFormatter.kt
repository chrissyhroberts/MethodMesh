package com.example.researchos.transport

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.Observation

object OutputFormatter {

    fun format(output: MethodOutput, returnMode: ReturnMode): String =
        formatFields(output.fields, returnMode)

    fun format(artifact: Observation, returnMode: ReturnMode, includeProvenance: Boolean = true): String =
        formatFields(artifact.toRecord(includeProvenance), returnMode)

    private fun formatFields(fields: Map<String, Any?>, returnMode: ReturnMode): String {
        return when (returnMode) {
            ReturnMode.Single -> fields.values.firstOrNull()?.toString() ?: ""

            ReturnMode.Fields -> fields.entries.joinToString("\n") { (key, value) ->
                "$key=$value"
            }

            ReturnMode.Json -> fields.entries.joinToString(
                prefix = "{\n",
                separator = ",\n",
                postfix = "\n}"
            ) { (key, value) ->
                "  ${quote(key)}: ${formatJsonValue(value)}"
            }

            ReturnMode.Datapoints -> fields.entries.mapIndexed { index, entry ->
                "${index + 1},${escapeCsv(entry.key)},${escapeCsv(entry.value?.toString() ?: "") }"
            }.joinToString("\n")
        }
    }

    private fun formatJsonValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Number -> value.toString()
            is Boolean -> value.toString()
            is Map<*, *> -> value.entries.joinToString(
                prefix = "{",
                separator = ",",
                postfix = "}"
            ) { (key, nestedValue) ->
                "${quote(key.toString())}:${formatJsonValue(nestedValue)}"
            }
            is Iterable<*> -> value.joinToString(
                prefix = "[",
                separator = ",",
                postfix = "]"
            ) { item -> formatJsonValue(item) }
            else -> quote(value.toString())
        }
    }

    private fun quote(value: String): String =
        "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")}\""

    private fun escapeCsv(value: String): String {
        val mustQuote = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        val escaped = value.replace("\"", "\"\"")
        return if (mustQuote) "\"$escaped\"" else escaped
    }
}
