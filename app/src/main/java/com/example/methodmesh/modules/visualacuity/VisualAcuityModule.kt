package com.example.methodmesh.modules.visualacuity

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object VisualAcuityModule : MethodMeshModule {
    override val moduleId = "visualacuity"
    override val displayName = "Visual acuity"
    override val summary = "Rapid calibrated tumbling-E visual acuity measurement with logMAR and Snellen results."

    override fun as100Methods() = listOf(As100VisualAcuityMethod)

    override fun rilBindings() = listOf(
        RilBinding("measure visual acuity", As100VisualAcuityMethod.ID, "Measure calibrated visual acuity"),
        RilBinding("test visual acuity", As100VisualAcuityMethod.ID, "Run a rapid tumbling-E visual acuity test"),
        RilBinding("measure eyesight", As100VisualAcuityMethod.ID, "Measure visual acuity")
    )

    override fun capabilityScreens() = listOf(VisualAcuityCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100VisualAcuityMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "test_mode",
                "Test mode",
                defaultValue = "distance",
                choices = listOf("distance", "near")
            ),
            MethodSetting.ChoiceSetting(
                "eye",
                "Eye",
                defaultValue = "right",
                choices = listOf("right", "left", "binocular")
            ),
            MethodSetting.ChoiceSetting(
                "correction",
                "Correction worn",
                defaultValue = "habitual",
                choices = listOf("habitual", "none", "not_recorded")
            ),
            MethodSetting.BooleanSetting(
                "ambient_light_warning",
                "Warn above 1000 lux",
                defaultValue = true
            )
        )
    )
}
