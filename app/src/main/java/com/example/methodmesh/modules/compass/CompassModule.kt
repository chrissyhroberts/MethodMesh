package com.example.methodmesh.modules.compass

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object CompassModule : MethodMeshModule {
    override val moduleId = "compass"
    override val displayName = "Compass"
    override val summary = "Read a magnetic heading or sight a configured bearing with a live alignment reticle."

    override fun as100Methods() = listOf(As100CompassMethod)

    override fun rilBindings() = listOf(
        RilBinding("read compass", As100CompassMethod.ID, "Read and capture a compass bearing"),
        RilBinding("capture bearing", As100CompassMethod.ID, "Sight and capture a configured bearing"),
        RilBinding("sight north", As100CompassMethod.ID, "Sight magnetic north")
    )

    override fun capabilityScreens() = listOf(CompassCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100CompassMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "target_mode",
                label = "Target",
                defaultValue = "north",
                choices = listOf("north", "bearing")
            ),
            MethodSetting.FloatSetting(
                id = "target_bearing_deg",
                label = "Target bearing",
                defaultValue = 0f,
                minimum = 0f,
                maximum = 359.9f,
                step = 0.1f,
                unit = "°",
                decimals = 1
            ),
            MethodSetting.FloatSetting(
                id = "alignment_tolerance_deg",
                label = "Alignment tolerance",
                description = "The sighting ring turns green inside ± this many degrees.",
                defaultValue = 5f,
                minimum = 1f,
                maximum = 30f,
                step = 1f,
                unit = "°",
                decimals = 0
            ),
            MethodSetting.BooleanSetting(
                id = "show_camera_in_sight",
                label = "Show camera in sighting mode",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = "start_in_sight_mode",
                label = "Start in sighting mode",
                defaultValue = false
            )
        )
    )
}
