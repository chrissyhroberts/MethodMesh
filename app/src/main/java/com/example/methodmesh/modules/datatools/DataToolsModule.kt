package com.example.methodmesh.modules.datatools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object DataToolsModule : MethodMeshModule {
    override val moduleId = "datatools"
    override val displayName = "Data tools"
    override val summary = "JSON, Base64, URL, hex, timestamp and CSV/JSON transformations."
    override val iconKey = "data"
    override fun as100Methods() = listOf(As100DataToolsMethod)
    override fun rilBindings() = listOf(RilBinding("transform data", As100DataToolsMethod.ID, "Run an offline data encoding or format operation"))
    override fun capabilityScreens() = listOf(DataToolsCapabilityScreen)
    override fun capabilitySettings() = mapOf(
        As100DataToolsMethod.ID to listOf(
            MethodSetting.ChoiceSetting("operation", "Operation", defaultValue = "json_pretty", choices = listOf("json_pretty", "json_minify", "json_validate", "base64_encode", "base64_decode", "url_encode", "url_decode", "hex_encode", "hex_decode", "unix_to_iso", "iso_to_unix", "csv_to_json", "json_to_csv")),
            MethodSetting.TextSetting("data", "Data", defaultValue = "")
        )
    )
}
