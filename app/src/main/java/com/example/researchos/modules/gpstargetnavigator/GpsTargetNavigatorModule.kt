package com.example.researchos.modules.gpstargetnavigator

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object GpsTargetNavigatorModule : ResearchOSModule {
    override val moduleId: String = "gpstargetnavigator"
    override val displayName: String = "GPS target navigator"

    override fun legacyMethods() = listOf(GpsTargetNavigatorMethod())

    override fun as100Methods() = listOf(As100LocateTargetMethod)

    override fun rilBindings() = listOf(
        RilBinding("navigate gps", "gps.navigate_to_target", "Navigate to a configured GPS target"),
        RilBinding("navigate target", "gps.navigate_to_target", "Navigate to a configured GPS target"),
        RilBinding("navigate location", "gps.navigate_to_target", "Navigate to a configured GPS target"),
        RilBinding("gps navigate", "gps.navigate_to_target", "Navigate to a configured GPS target")
    )

    override fun capabilityScreens() = listOf(GpsTargetNavigatorCapabilityScreen)
}
