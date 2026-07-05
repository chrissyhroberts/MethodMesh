package com.example.researchos.modules.calibratedscale

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object CalibratedScaleModule : ResearchOSModule {
    override val moduleId: String = "calibratedscale"
    override val displayName: String = "Calibrated scale"

    override fun legacyMethods() = listOf(CalibratedScaleMethod())

    override fun as100Methods() = listOf(As100CalibratedScaleMethod)

    override fun rilBindings() = listOf(
        RilBinding("measure scale", "scale.capture", "Capture a calibrated scale value"),
        RilBinding("observe scale", "scale.capture", "Capture a calibrated scale value"),
        RilBinding("capture scale", "scale.capture", "Capture a calibrated scale value"),
        RilBinding("measure calibrated scale", "scale.capture", "Capture a calibrated scale value")
    )
}
