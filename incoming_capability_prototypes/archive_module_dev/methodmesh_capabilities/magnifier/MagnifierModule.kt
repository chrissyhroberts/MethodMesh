package com.example.methodmesh.modules.magnifier

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object MagnifierModule : MethodMeshModule {
    override val moduleId = "magnifier"
    override val displayName = "Visual inspection / Magnifier"
    override val summary = "Camera magnifier with zoom, torch, autofocus/focus hold, freeze-frame and inspection filters."
    override val iconKey = "camera"
    override fun as100Methods() = listOf(As100MagnifierMethod)
    override fun rilBindings() = listOf(RilBinding("inspect with magnifier", As100MagnifierMethod.ID, "Open visual inspection magnifier and capture an image"))
    override fun capabilityScreens() = listOf(MagnifierCapabilityScreen)
    override fun capabilitySettings() = mapOf(
        As100MagnifierMethod.ID to listOf(
            MethodSetting.FloatSetting("initial_zoom", "Initial zoom", defaultValue = 1f, minimum = 1f, maximum = 10f),
            MethodSetting.ChoiceSetting("default_filter", "Default filter", defaultValue = "normal", choices = listOf("normal", "high_contrast", "monochrome", "negative")),
            MethodSetting.BooleanSetting("allow_torch", "Allow torch", defaultValue = true)
        )
    )
}
