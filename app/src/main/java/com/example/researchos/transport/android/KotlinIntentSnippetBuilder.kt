package com.example.researchos.transport.android

import com.example.researchos.core.Method
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.ReturnMode

object KotlinIntentSnippetBuilder {

    private const val action = "com.example.researchos.RUN_METHOD"
    private const val packageName = "com.example.researchos"

    fun build(
        method: Method,
        settingsState: SettingsState,
        returnMode: ReturnMode
    ): String {
        val lines = mutableListOf<String>()

        lines += "Intent(\"$action\")"
        lines += "    .setPackage(\"$packageName\")"
        lines += "    .putExtra(\"method_id\", \"${method.manifest.id}\")"
        lines += "    .putExtra(\"return_mode\", \"${returnMode.id}\")"

        settingsState.asMap()
            .toSortedMap()
            .forEach { (key, value) ->
                lines += "    .putExtra(\"$key\", ${formatKotlinValue(value)})"
            }

        return lines.joinToString("\n")
    }

    private fun formatKotlinValue(value: Any?): String {
        return when (value) {
            null -> "null"
            is Boolean -> value.toString()
            is Number -> value.toString()
            else -> "\"${value.toString().replace("\"", "\\\"")}\""
        }
    }
}
