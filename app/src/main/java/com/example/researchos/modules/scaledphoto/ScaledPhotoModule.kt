package com.example.researchos.modules.scaledphoto

import com.example.researchos.modules.*
import com.example.researchos.settings.MethodSetting

object ScaledPhotoModule : ResearchOSModule {
    override val moduleId = "scaledphoto"
    override val displayName = "Image annotation and redaction"
    override val summary = "Capture a ruler-calibrated photo and an optional grid-based region selection."
    override fun as100Methods() = listOf(As100ScaledPhotoMethod)
    override fun capabilityScreens() = listOf(ScaledPhotoCapabilityScreen)
    override fun rilBindings() = listOf(RilBinding("capture scaled photo", As100ScaledPhotoMethod.ID, "Capture a calibrated photograph and optional grid selection"))
    override fun capabilitySettings() = mapOf(As100ScaledPhotoMethod.ID to listOf(
        MethodSetting.FloatSetting("ruler_length_mm", "Calibration ruler length", "Physical length represented by the HUD ruler", "Calibration", 50f, 5f, 500f, 1f, "mm", 1),
        MethodSetting.ChoiceSetting("capture_orientation", "Photo orientation", "Choose the orientation written into the captured image", "Camera", "portrait", listOf("portrait", "landscape")),
        MethodSetting.ChoiceSetting("hud_scale_ratio", "HUD scale ratio", "Display multiplier for the on-screen graticule", "Calibration", "1", listOf("1", "2", "3", "4")),
        MethodSetting.IntSetting("grid_rows", "Grid rows", "Overlay rows", "Overlay", 10, 1, 20),
        MethodSetting.IntSetting("grid_columns", "Grid columns", "Overlay columns", "Overlay", 10, 1, 20),
        MethodSetting.BooleanSetting("show_grid", "Show grid overlay", group = "Overlay", defaultValue = true),
        MethodSetting.BooleanSetting("macro_mode", "Macro focus mode", "Use a closer focus/zoom target for close-up subjects", "Camera", false)
    ))
}
