package com.example.researchos.modules.calibratedscale

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object CalibratedScaleModule : ResearchOSModule {
    override val moduleId: String = "calibratedscale"
    override val displayName: String = "Calibrated scale"


    override fun as100Methods() = listOf(As100CalibratedScaleMethod)

    override fun rilBindings() = listOf(
        RilBinding("measure scale", As100CalibratedScaleMethod.ID, "Capture a calibrated scale value"),
        RilBinding("observe scale", As100CalibratedScaleMethod.ID, "Capture a calibrated scale value"),
        RilBinding("capture scale", As100CalibratedScaleMethod.ID, "Capture a calibrated scale value"),
        RilBinding("measure calibrated scale", As100CalibratedScaleMethod.ID, "Capture a calibrated scale value")
    )

    override fun capabilityScreens() = listOf(CalibratedScaleCapabilityScreen)

    override fun capabilitySettings() = mapOf(As100CalibratedScaleMethod.ID to CalibratedScaleInteraction().settings)
}
