package com.example.researchos.transport.odk

import com.example.researchos.core.Method
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.android.AndroidIntentUriBuilder

object OdkIntentColumnBuilder {

    fun build(
        method: Method,
        settingsState: SettingsState,
        returnMode: ReturnMode
    ): String {
        return AndroidIntentUriBuilder.build(
            method = method,
            settingsState = settingsState,
            returnMode = returnMode
        )
    }
}
