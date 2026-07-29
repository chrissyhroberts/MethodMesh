package com.example.researchos.modules.gpstargetnavigator

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object GpsTargetNavigatorModule : ResearchOSModule {
    override val moduleId: String = "gpstargetnavigator"
    override val displayName: String = "GPS target navigator"


    override fun as100Methods() = listOf(As100LocateTargetMethod)

    override fun rilBindings() = listOf(
        RilBinding("navigate gps", As100LocateTargetMethod.ID, "Navigate to a configured GPS target"),
        RilBinding("navigate target", As100LocateTargetMethod.ID, "Navigate to a configured GPS target"),
        RilBinding("navigate location", As100LocateTargetMethod.ID, "Navigate to a configured GPS target"),
        RilBinding("gps navigate", As100LocateTargetMethod.ID, "Navigate to a configured GPS target")
    )

    override fun capabilityScreens() = listOf(GpsTargetNavigatorCapabilityScreen)

    override fun capabilitySettings() = mapOf(As100LocateTargetMethod.ID to GpsTargetNavigatorInteraction().settings)
}
