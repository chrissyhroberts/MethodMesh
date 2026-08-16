package com.example.researchos.modules.imageredaction

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object ImageRedactionModule : ResearchOSModule {
    override val moduleId = "imageredaction"
    override val displayName = "Image annotation and redaction"
    override val summary = "Mask selected image regions and return only a redacted image attachment."

    override fun as100Methods() = listOf(As100ImageRedactionMethod)
    override fun capabilityScreens() = listOf(ImageRedactionCapabilityScreen)
    override fun rilBindings() = listOf(
        RilBinding("redact image", As100ImageRedactionMethod.ID, "Mask selected image regions and return a redacted image")
    )

    override fun capabilitySettings() = mapOf(
        As100ImageRedactionMethod.ID to listOf(
            MethodSetting.ChoiceSetting("input_source", "Input source", defaultValue = "camera", choices = listOf("camera", "file_picker")),
            MethodSetting.IntSetting("grid_rows", "Grid rows", defaultValue = 10, minimum = 1, maximum = 50),
            MethodSetting.IntSetting("grid_columns", "Grid columns", defaultValue = 10, minimum = 1, maximum = 50),
            MethodSetting.ChoiceSetting("redaction_style", "Redaction style", defaultValue = "black", choices = listOf("black", "white"))
        )
    )
}
