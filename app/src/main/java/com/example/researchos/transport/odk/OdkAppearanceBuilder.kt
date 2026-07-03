package com.example.researchos.transport.odk

import com.example.researchos.core.Method
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.encodeTransportValue

object OdkAppearanceBuilder {

    fun build(
        method: Method,
        settingsState: SettingsState,
        returnMode: ReturnMode
    ): String {
        val parts = mutableListOf<String>()

        parts += "method=${encodeTransportValue(method.manifest.id)}"
        parts += "return_mode=${encodeTransportValue(returnMode.id)}"

        settingsState.asMap()
            .toSortedMap()
            .forEach { (key, value) ->
                parts += "${encodeTransportValue(key)}=${encodeTransportValue(value)}"
            }

        return "researchos(${parts.joinToString(";")})"
    }
}
