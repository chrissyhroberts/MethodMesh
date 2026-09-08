package com.example.methodmesh.modules.psychomotorvigilance

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object PsychomotorVigilanceModule : MethodMeshModule {
    override val moduleId = "psychomotorvigilance"
    override val displayName = "Psychomotor vigilance test (PVT)"
    override val summary = "Run a standard 10-minute PVT or 3-minute PVT-B reaction-time/vigilance assessment locally on the device."

    override fun as100Methods() = listOf(As100PsychomotorVigilanceMethod)

    override fun rilBindings() = listOf(
        RilBinding("measure psychomotor vigilance", As100PsychomotorVigilanceMethod.ID, "Run a visual psychomotor vigilance test"),
        RilBinding("run reaction time test", As100PsychomotorVigilanceMethod.ID, "Run the PVT reaction-time task")
    )

    override fun capabilityScreens() = listOf(PsychomotorVigilanceCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100PsychomotorVigilanceMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "protocol",
                "Protocol",
                defaultValue = PvtProtocol.STANDARD_10.key,
                choices = PvtProtocol.all.map { it.key }
            ),
            MethodSetting.IntSetting(
                "countdown_seconds",
                "Pre-test countdown (seconds)",
                defaultValue = 3,
                minimum = 0,
                maximum = 10
            )
        )
    )
}
