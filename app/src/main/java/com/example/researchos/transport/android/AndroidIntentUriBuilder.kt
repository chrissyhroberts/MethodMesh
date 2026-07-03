package com.example.researchos.transport.android

import com.example.researchos.core.Method
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.androidExtraPrefix
import com.example.researchos.transport.encodeTransportValue

object AndroidIntentUriBuilder {

    private const val action = "com.example.researchos.RUN_METHOD"
    private const val packageName = "com.example.researchos"

    fun build(
        method: Method,
        settingsState: SettingsState,
        returnMode: ReturnMode
    ): String {
        val parts = mutableListOf<String>()

        parts += "intent:#Intent"
        parts += "action=$action"
        parts += "package=$packageName"
        parts += "S.method_id=${encodeTransportValue(method.manifest.id)}"
        parts += "S.return_mode=${encodeTransportValue(returnMode.id)}"

        settingsState.asMap()
            .toSortedMap()
            .forEach { (key, value) ->
                parts += "${androidExtraPrefix(value)}.$key=${encodeTransportValue(value)}"
            }

        parts += "end"

        return parts.joinToString(";")
    }
}
