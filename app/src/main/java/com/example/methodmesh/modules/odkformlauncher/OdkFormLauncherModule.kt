package com.example.methodmesh.modules.odkformlauncher

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object OdkFormLauncherModule : MethodMeshModule {
    override val moduleId = "odkformlauncher"
    override val displayName = "ODK form launcher"
    override val summary = "Open a named form already available in ODK Collect."

    override fun as100Methods() = listOf(As100OdkFormLauncherMethod)

    override fun rilBindings() = listOf(
        RilBinding("open odk form", As100OdkFormLauncherMethod.ID, "Open a named ODK Collect form"),
        RilBinding("launch odk form", As100OdkFormLauncherMethod.ID, "Launch a named ODK Collect form")
    )

    override fun capabilityScreens() = listOf(OdkFormLauncherCapabilityScreen)

    override fun capabilitySettings() = mapOf(As100OdkFormLauncherMethod.ID to listOf(
        MethodSetting.TextSetting("project_id", "Project ID", defaultValue = ""),
        MethodSetting.TextSetting("form_selector", "Form ID or display name", defaultValue = "")
    ))
}
