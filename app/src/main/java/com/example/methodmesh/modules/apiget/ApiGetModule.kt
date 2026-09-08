package com.example.methodmesh.modules.apiget

import com.example.methodmesh.core.onlinedata.BundledApiDefinitions
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ApiGetModule : MethodMeshModule {
    override val moduleId = "apiget"
    override val displayName = "Online API GET"
    override val summary = "Run declared online API/RSS-style data links with compact returns and audit JSON."

    override fun as100Methods() = listOf(As100ApiGetMethod)

    override fun rilBindings() = listOf(
        RilBinding("get online api data", As100ApiGetMethod.ID, "Run a declared online API definition"),
        RilBinding("fetch api", As100ApiGetMethod.ID, "Fetch data from a declared API link")
    )

    override fun capabilityScreens() = listOf(ApiGetCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100ApiGetMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "definition_id",
                label = "API link",
                defaultValue = BundledApiDefinitions.openMeteoCurrentWeather.id,
                choices = BundledApiDefinitions.all.map { it.id },
                group = "Request"
            ),
            MethodSetting.TextSetting(
                id = "latitude",
                label = "Latitude",
                defaultValue = "52.0779",
                group = "Request"
            ),
            MethodSetting.TextSetting(
                id = "longitude",
                label = "Longitude",
                defaultValue = "-0.0580",
                group = "Request"
            ),
            MethodSetting.MultiChoiceSetting(
                id = "result_paths",
                label = "Returned fields",
                description = "Tree paths to return as useful values.",
                defaultValue = "current.temperature_2m|current.relative_humidity_2m",
                choices = BundledApiDefinitions.all.flatMap { definition ->
                    As100ApiGetMethod.resultPathOptions(definition).map { it.path }
                }.distinct(),
                delimiter = "|",
                group = "Return"
            ),
            MethodSetting.TextSetting(
                id = "fallback_value",
                label = "Fallback value",
                description = "Optional value to return if the selected path is blank or missing.",
                defaultValue = "",
                group = "Return"
            )
        )
    )
}
