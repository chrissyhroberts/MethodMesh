package com.example.methodmesh.modules.screentools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ScreenToolsModule : MethodMeshModule {
    override val moduleId = "screentools"
    override val displayName = "Screen Tools"
    override val version = "0.1.0"
    override val summary = "Capture a user-authorised screen into a volatile frame for later visual tools."
    override val iconKey = "document"

    override fun as100Methods() = listOf(ScreenCaptureMethod)

    override fun rilBindings() = listOf(
        RilBinding("capture screen", ScreenCaptureMethod.ID, "Capture the permitted visible screen into volatile memory")
    )

    override fun capabilityScreens() = listOf(ScreenCaptureCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        ScreenCaptureMethod.ID to listOf(
            MethodSetting.BooleanSetting(
                id = "clear_previous_frames",
                label = "Clear previous frames",
                description = "Start a new volatile capture session.",
                defaultValue = true
            )
        )
    )
}
