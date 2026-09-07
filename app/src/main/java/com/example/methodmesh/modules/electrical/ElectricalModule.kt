package com.example.methodmesh.modules.electrical

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ElectricalModule : MethodMeshModule {
    override val moduleId = "electrical"
    override val displayName = "Electrical"
    override val summary = "Solve common electrical calculations for field and bench work, with compact copy/share-first results."
    override val iconKey = "electrical"

    override fun as100Methods() = listOf(As100ElectricalWorkbenchMethod)
    override fun capabilityScreens() = listOf(ElectricalWorkbenchCapabilityScreen)
    override fun rilBindings() = listOf(
        RilBinding("electrical workbench", As100ElectricalWorkbenchMethod.ID, "Open the electrical calculation workbench"),
        RilBinding("calculate ohms law", As100ElectricalWorkbenchMethod.ID, "Solve voltage, current, resistance and power"),
        RilBinding("calculate voltage drop", As100ElectricalWorkbenchMethod.ID, "Estimate conductor voltage drop"),
        RilBinding("calculate ac power", As100ElectricalWorkbenchMethod.ID, "Calculate single- or three-phase power")
    )

    override fun capabilitySettings() = mapOf(
        As100ElectricalWorkbenchMethod.ID to listOf(
            MethodSetting.ChoiceSetting("tool", "Tool", defaultValue = "ohms_law", choices = listOf("ohms_law", "ac_power", "voltage_drop", "network", "energy", "rc", "rl", "resistor_code")),
            MethodSetting.TextSetting("voltage_v", "Voltage (V)", "Leave blank when voltage is unknown.", defaultValue = ""),
            MethodSetting.TextSetting("current_a", "Current (A)", "Leave blank when current is unknown.", defaultValue = ""),
            MethodSetting.TextSetting("resistance_ohm", "Resistance (Ω)", "Leave blank when resistance is unknown.", defaultValue = ""),
            MethodSetting.TextSetting("power_w", "Power (W)", "Leave blank when power is unknown.", defaultValue = ""),
            MethodSetting.ChoiceSetting("phase", "System", defaultValue = "single_phase", choices = listOf("single_phase", "three_phase")),
            MethodSetting.TextSetting("power_factor", "Power factor", defaultValue = "1"),
            MethodSetting.TextSetting("length_m", "One-way route length (m)", defaultValue = ""),
            MethodSetting.TextSetting("conductor_area_mm2", "Conductor area (mm²)", defaultValue = ""),
            MethodSetting.ChoiceSetting("material", "Conductor", defaultValue = "copper", choices = listOf("copper", "aluminium")),
            MethodSetting.TextSetting("temperature_c", "Conductor temperature (°C)", defaultValue = "20"),
            MethodSetting.ChoiceSetting("arrangement", "Arrangement", defaultValue = "series", choices = listOf("series", "parallel")),
            MethodSetting.ChoiceSetting("network_type", "Component", defaultValue = "resistance", choices = listOf("resistance", "capacitance")),
            MethodSetting.TextSetting("values", "Network values", "Comma-separated base SI values.", defaultValue = ""),
            MethodSetting.TextSetting("duration_hours", "Duration (hours)", defaultValue = ""),
            MethodSetting.TextSetting("capacity_ah", "Battery capacity (Ah)", defaultValue = ""),
            MethodSetting.TextSetting("capacitance_f", "Capacitance (F)", defaultValue = ""),
            MethodSetting.TextSetting("inductance_h", "Inductance (H)", defaultValue = ""),
            MethodSetting.TextSetting("bands", "Resistor bands", "Comma-separated colours in reading order.", defaultValue = "brown,black,red,gold")
        )
    )
}
