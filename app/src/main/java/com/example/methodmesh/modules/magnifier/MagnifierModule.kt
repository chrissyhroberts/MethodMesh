package com.example.methodmesh.modules.magnifier

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object MagnifierModule : MethodMeshModule {
    override val moduleId = "magnifier"
    override val displayName = "Magnifier"
    override val summary = "Use the rear camera as a handheld magnifier, freeze the inspected frame, apply a viewing filter, and return the captured image."
    override val iconKey = "tool"

    override fun as100Methods() = listOf(As100MagnifierCaptureMethod)
    override fun capabilityScreens() = listOf(MagnifierCapabilityScreen)
    override fun rilBindings() = listOf(
        RilBinding(
            phrase = "magnify and capture image",
            actionId = As100MagnifierCaptureMethod.ID,
            description = "Inspect a subject with camera zoom and return the chosen frozen frame"
        )
    )

    override fun capabilitySettings() = mapOf(
        As100MagnifierCaptureMethod.ID to listOf(
            MethodSetting.FloatSetting(
                "initial_zoom",
                "Initial zoom",
                "Starting digital zoom ratio; the device camera limit still applies.",
                "Camera",
                2f,
                1f,
                10f,
                0.25f,
                "×",
                2
            ),
            MethodSetting.ChoiceSetting(
                "default_filter",
                "Default inspection filter",
                "Filter selected when a frame is frozen.",
                "Inspection",
                "normal",
                listOf("normal", "high_contrast", "monochrome", "negative")
            ),
            MethodSetting.BooleanSetting(
                "allow_torch",
                "Allow torch",
                "Show the torch control when the rear camera supports it.",
                "Camera",
                true
            )
        )
    )
}
