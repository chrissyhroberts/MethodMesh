package com.example.methodmesh.modules.externaldisplay

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ExternalDisplayModule : MethodMeshModule {
    override val moduleId = "externaldisplay"
    override val displayName = "External display"
    override val summary = "Present MethodMesh content on a projector, TV or other Android display, with explicit system-casting handoff."
    override val iconKey = "presentation"
    override fun as100Methods() = listOf(As100ExternalDisplayMethod)
    override fun capabilityScreens() = listOf(ExternalDisplayCapabilityScreen)
    override fun rilBindings() = listOf(RilBinding("present on external display", As100ExternalDisplayMethod.ID, "Present MethodMesh content on a connected display"))
    override fun capabilitySettings() = mapOf(As100ExternalDisplayMethod.ID to listOf(
        MethodSetting.ChoiceSetting("mode", "Mode", defaultValue = "present", choices = listOf("present", "system_handoff")),
        MethodSetting.BooleanSetting("blank_on_start", "Start blanked", "Keep the external surface black until content is ready.", defaultValue = false)
    ))
}
