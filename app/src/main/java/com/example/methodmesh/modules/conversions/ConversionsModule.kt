package com.example.methodmesh.modules.conversions

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ConversionsModule : MethodMeshModule {
    override val moduleId = "conversions"
    override val displayName = "Conversions / General Calculator"
    override val summary = "Offline units, percentages, ratios, date arithmetic and simple geometry."
    override val iconKey = "calculation"
    override fun as100Methods() = listOf(As100ConversionsMethod)
    override fun rilBindings() = listOf(RilBinding("convert units", As100ConversionsMethod.ID, "Convert units or run a general calculation"))
    override fun capabilityScreens() = listOf(ConversionsCapabilityScreen)

    private val unitChoices = listOf(
        "m", "km", "cm", "mm", "in", "ft", "yd", "mi",
        "m2", "km2", "cm2", "ft2", "acre", "hectare",
        "L", "mL", "m3", "cm3", "US_gal", "UK_gal", "US_fl_oz",
        "kg", "g", "mg", "lb", "oz", "C", "F", "K",
        "m/s", "km/h", "mph", "knot", "Pa", "kPa", "bar", "psi", "mmHg",
        "J", "kJ", "Wh", "kWh", "cal", "kcal", "W", "kW", "MW", "hp",
        "rad", "deg", "grad", "B", "KB", "MB", "GB", "KiB", "MiB", "GiB"
    ).distinct()

    override fun capabilitySettings() = mapOf(
        As100ConversionsMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "category",
                "Calculation",
                defaultValue = "length",
                choices = listOf("length", "area", "volume", "mass", "temperature", "speed", "pressure", "energy", "power", "angle", "data_size", "percentage", "ratio", "date_difference", "date_arithmetic", "age", "geometry")
            ),
            MethodSetting.TextSetting("value", "Value / A", defaultValue = ""),
            MethodSetting.TextSetting("value2", "Second value / B", defaultValue = ""),
            MethodSetting.TextSetting("value3", "Third value / C", defaultValue = ""),
            MethodSetting.ChoiceSetting("from_unit", "From unit", defaultValue = "m", choices = unitChoices),
            MethodSetting.ChoiceSetting("to_unit", "To unit", defaultValue = "km", choices = unitChoices),
            MethodSetting.ChoiceSetting(
                "operation",
                "Operation",
                defaultValue = "convert",
                choices = listOf("convert", "percent_of", "what_percent", "percent_change", "increase_by_percent", "decrease_by_percent", "a_to_b", "solve_proportion", "add_days", "add_weeks", "add_months", "add_years", "subtract_days", "area", "perimeter", "circumference")
            ),
            MethodSetting.TextSetting("date1", "Date 1 / birth date (YYYY-MM-DD)", defaultValue = ""),
            MethodSetting.TextSetting("date2", "Date 2 / at date (YYYY-MM-DD)", defaultValue = ""),
            MethodSetting.ChoiceSetting("shape", "Geometry shape", defaultValue = "rectangle", choices = listOf("rectangle", "triangle", "circle"))
        )
    )
}
