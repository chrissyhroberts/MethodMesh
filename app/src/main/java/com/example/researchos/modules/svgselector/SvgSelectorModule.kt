package com.example.researchos.modules.svgselector

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

object SvgSelectorModule : ResearchOSModule {
    override val moduleId = "svgselector"
    override val displayName = "SVG polygon selector"
    override val summary = "Select one, multiple, or ordered SVG polygons with timestamped audit events."

    override fun as100Methods() = listOf(As100SvgSelectorMethod)
    override fun capabilityScreens(): List<CapabilityScreenSpec> = listOf(SvgSelectorCapabilityScreen)
    override fun rilBindings() = listOf(
        RilBinding("select svg polygons", As100SvgSelectorMethod.ID, "Select polygons on a stored SVG"),
        RilBinding("run svg selector", As100SvgSelectorMethod.ID, "Select one, multiple, or ordered SVG polygons")
    )
    override fun capabilitySettings() = mapOf(As100SvgSelectorMethod.ID to listOf(
        MethodSetting.TextSetting("svg_name", "SVG file name", "Name in ResearchOS app storage/svg", "Input", "bodymap_black.svg"),
        MethodSetting.ChoiceSetting("selection_mode", "Selection mode", "single, multiple, or strict sequence", "Input", "single", listOf("single", "multiple", "sequence"))
    ))
}
