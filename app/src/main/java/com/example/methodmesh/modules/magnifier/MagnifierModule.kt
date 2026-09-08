package com.example.methodmesh.modules.magnifier

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object MagnifierModule : MethodMeshModule {
    override val moduleId = "magnifier"
    override val displayName = "Magnifier"
    override val summary = "Use the rear or front camera as a handheld magnifier with live inspection filters, freeze the inspected frame, and return the captured image."
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
            MethodSetting.ChoiceSetting(
                "camera_facing",
                "Camera",
                "Choose the rear or front camera for inspection and capture.",
                "Camera",
                "rear",
                listOf("rear", "front")
            ),
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
                "Starting live filter; the same filter is shown on the live preview, frozen inspection frame, and returned image.",
                "Inspection",
                "normal",
                listOf("normal", "high_contrast", "monochrome", "negative")
            ),
            MethodSetting.BooleanSetting(
                "allow_torch",
                "Allow back light",
                "Allow the rear-camera flash LED to be used as the magnifier back light when supported by the selected camera.",
                "Lighting",
                true
            ),
            MethodSetting.BooleanSetting(
                "allow_front_light",
                "Allow front light",
                "Allow the display to provide a bright white front light around the camera preview.",
                "Lighting",
                true
            ),
            MethodSetting.ChoiceSetting(
                "default_light",
                "Default light",
                "Lighting state when the magnifier opens. Front light uses the display; back light uses the camera flash LED.",
                "Lighting",
                "off",
                listOf("off", "front", "back")
            ),
            MethodSetting.FloatSetting(
                "front_light_brightness",
                "Front light brightness",
                "Display brightness used while the front light is active.",
                "Lighting",
                1f,
                0.25f,
                1f,
                0.05f,
                null,
                2
            )
        )
    )
}
