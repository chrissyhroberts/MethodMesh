package com.example.methodmesh.modules.diving

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object DivingSettings {
    private fun environment() = listOf(
        MethodSetting.ChoiceSetting(
            id = "water_type",
            label = "Water type",
            description = "Select seawater/freshwater or use custom density.",
            group = "Environment",
            defaultValue = "seawater",
            choices = listOf("seawater", "freshwater", "custom")
        ),
        MethodSetting.FloatSetting(
            id = "water_density_kg_m3",
            label = "Water density",
            description = "Used for hydrostatic pressure. Seawater default 1025 kg/m³; freshwater default 1000 kg/m³.",
            group = "Environment",
            defaultValue = 1025f,
            minimum = 900f,
            maximum = 1100f,
            step = 1f,
            unit = "kg/m³",
            decimals = 0
        ),
        MethodSetting.FloatSetting(
            id = "surface_pressure_bar",
            label = "Surface pressure",
            description = "Absolute pressure at the dive site. Standard sea-level default is 1.01325 bar.",
            group = "Environment",
            defaultValue = 1.01325f,
            minimum = 0.5f,
            maximum = 1.2f,
            step = 0.001f,
            unit = "bar abs",
            decimals = 3
        )
    )

    private fun mix(defaultFo2: Float = 21f, defaultFhe: Float = 0f) = listOf(
        MethodSetting.FloatSetting(
            id = "fo2_percent",
            label = "Analysed oxygen",
            description = "Enter the measured oxygen percentage for the gas actually being used.",
            group = "Gas",
            defaultValue = defaultFo2,
            minimum = 1f,
            maximum = 100f,
            step = 0.1f,
            unit = "% O₂",
            decimals = 1
        ),
        MethodSetting.FloatSetting(
            id = "fhe_percent",
            label = "Analysed helium",
            description = "Enter 0 for air/nitrox.",
            group = "Gas",
            defaultValue = defaultFhe,
            minimum = 0f,
            maximum = 99f,
            step = 0.1f,
            unit = "% He",
            decimals = 1
        )
    )

    private fun ppo2Limit() = listOf(
        MethodSetting.FloatSetting(
            id = "ppo2_limit_bar",
            label = "Configured ppO₂ limit",
            description = "A planning input, not a MethodMesh declaration of a universally safe limit. Use the value required by your training/organisation/procedure.",
            group = "Gas",
            defaultValue = 1.4f,
            minimum = 0.5f,
            maximum = 2.0f,
            step = 0.05f,
            unit = "bar",
            decimals = 2
        )
    )

    val all: Map<String, List<MethodSetting>> = mapOf(
        DivingIds.SAC_RMV to listOf(
            MethodSetting.FloatSetting("cylinder_volume_l", "Cylinder water volume", group = "Cylinder", defaultValue = 12f, minimum = 0.5f, maximum = 50f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Start pressure", group = "Cylinder", defaultValue = 200f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("end_pressure_bar", "End pressure", group = "Cylinder", defaultValue = 100f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("average_depth_m", "Average depth", group = "Dive", defaultValue = 20f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("duration_min", "Dive duration", group = "Dive", defaultValue = 40f, minimum = 0.1f, maximum = 1440f, step = 1f, unit = "min", decimals = 1)
        ) + environment(),

        DivingIds.CYLINDER_CAPACITY to listOf(
            MethodSetting.FloatSetting("cylinder_volume_l", "Cylinder water volume", group = "Cylinder", defaultValue = 12f, minimum = 0.5f, maximum = 100f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Start pressure", group = "Cylinder", defaultValue = 200f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("reserve_pressure_bar", "Reserve pressure", group = "Cylinder", defaultValue = 50f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0)
        ),

        DivingIds.PPO2 to mix(32f) + listOf(
            MethodSetting.FloatSetting("depth_m", "Depth", group = "Dive", defaultValue = 30f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1)
        ) + ppo2Limit() + environment(),

        DivingIds.MOD to mix(32f) + ppo2Limit() + environment(),

        DivingIds.BEST_MIX to listOf(
            MethodSetting.FloatSetting("depth_m", "Planned depth", group = "Dive", defaultValue = 30f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1)
        ) + ppo2Limit() + environment(),

        DivingIds.EAD to listOf(
            MethodSetting.FloatSetting("fo2_percent", "Analysed oxygen", group = "Gas", defaultValue = 32f, minimum = 21f, maximum = 100f, step = 0.1f, unit = "% O₂", decimals = 1),
            MethodSetting.FloatSetting("fhe_percent", "Helium", description = "Must remain 0 for this EAD implementation.", group = "Gas", defaultValue = 0f, minimum = 0f, maximum = 0f, step = 1f, unit = "% He", decimals = 0),
            MethodSetting.FloatSetting("depth_m", "Depth", group = "Dive", defaultValue = 30f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1)
        ) + environment(),

        DivingIds.END to mix(18f, 45f) + listOf(
            MethodSetting.FloatSetting("depth_m", "Depth", group = "Dive", defaultValue = 50f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.ChoiceSetting(
                "narcotic_model",
                "Narcotic-gas model",
                description = "Choose whether only nitrogen, or oxygen plus nitrogen, are treated as narcotic for the equivalence calculation.",
                group = "Model",
                defaultValue = "oxygen_and_nitrogen",
                choices = listOf("oxygen_and_nitrogen", "nitrogen_only")
            )
        ) + environment(),

        DivingIds.GAS_DENSITY to mix(21f, 0f) + listOf(
            MethodSetting.FloatSetting("depth_m", "Depth", group = "Dive", defaultValue = 40f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("gas_temperature_c", "Gas temperature", description = "Ideal-gas density calculation temperature.", group = "Environment", defaultValue = 20f, minimum = -5f, maximum = 50f, step = 0.5f, unit = "°C", decimals = 1)
        ) + environment(),

        DivingIds.GAS_PLAN to listOf(
            MethodSetting.FloatSetting("rmv_l_min", "RMV", group = "Diver", defaultValue = 18f, minimum = 1f, maximum = 100f, step = 0.5f, unit = "L/min", decimals = 1),
            MethodSetting.FloatSetting("cylinder_volume_l", "Cylinder water volume", group = "Cylinder", defaultValue = 12f, minimum = 0.5f, maximum = 100f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Start pressure", group = "Cylinder", defaultValue = 200f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("reserve_pressure_bar", "Reserve pressure", group = "Cylinder", defaultValue = 50f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.TextSetting(
                "segments",
                "Profile segments",
                description = "Semicolon-separated depth:minutes entries; optional third value is RMV multiplier. Named form: label:depth:minutes:multiplier. Example: descent:10:2:1;bottom:30:20:1;ascent:15:2:1;stop:5:3:1",
                group = "Profile",
                defaultValue = "bottom:20:20:1;stop:5:3:1"
            )
        ) + environment(),

        DivingIds.BAILOUT_PLAN to listOf(
            MethodSetting.FloatSetting("max_depth_m", "Starting depth", group = "Profile", defaultValue = 30f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("problem_time_min", "Problem-solving time", group = "Profile", defaultValue = 1f, minimum = 0f, maximum = 30f, step = 0.5f, unit = "min", decimals = 1),
            MethodSetting.FloatSetting("stressed_rmv_l_min", "Stressed RMV", group = "Diver", defaultValue = 30f, minimum = 1f, maximum = 150f, step = 0.5f, unit = "L/min", decimals = 1),
            MethodSetting.FloatSetting("ascent_rate_m_min", "Ascent rate", group = "Profile", defaultValue = 9f, minimum = 1f, maximum = 30f, step = 0.5f, unit = "m/min", decimals = 1),
            MethodSetting.FloatSetting("stop_depth_m", "Entered stop depth", description = "A manually entered gas-planning stop; MethodMesh does not calculate decompression stops.", group = "Profile", defaultValue = 5f, minimum = 0f, maximum = 100f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("stop_time_min", "Entered stop duration", description = "A manually entered gas-planning stop duration; not generated by a decompression model.", group = "Profile", defaultValue = 3f, minimum = 0f, maximum = 120f, step = 0.5f, unit = "min", decimals = 1),
            MethodSetting.FloatSetting("cylinder_volume_l", "Bailout cylinder water volume", group = "Cylinder", defaultValue = 7f, minimum = 0.5f, maximum = 100f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Bailout start pressure", group = "Cylinder", defaultValue = 200f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("reserve_pressure_bar", "Bailout reserve pressure", group = "Cylinder", defaultValue = 30f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0)
        ) + environment(),

        DivingIds.NAVIGATION to listOf(
            MethodSetting.FloatSetting("heading_deg", "Outbound heading", group = "Navigation", defaultValue = 0f, minimum = 0f, maximum = 359.9f, step = 1f, unit = "°", decimals = 0),
            MethodSetting.FloatSetting("distance_m", "Planned distance", description = "Enter distance to calculate time; set 0 if using entered time instead.", group = "Navigation", defaultValue = 0f, minimum = 0f, maximum = 10000f, step = 1f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("time_min", "Planned time", description = "Enter time to calculate distance; set 0 if using entered distance instead.", group = "Navigation", defaultValue = 5f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "min", decimals = 1),
            MethodSetting.FloatSetting("speed_m_min", "Swim speed", group = "Navigation", defaultValue = 15f, minimum = 0.1f, maximum = 200f, step = 0.5f, unit = "m/min", decimals = 1)
        ),

        DivingIds.LIFT to listOf(
            MethodSetting.FloatSetting("lift_mass_kg", "Mass to lift", group = "Lift", defaultValue = 10f, minimum = 0f, maximum = 10000f, step = 0.5f, unit = "kg", decimals = 1),
            MethodSetting.FloatSetting("depth_m", "Depth", group = "Lift", defaultValue = 20f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1)
        ) + environment(),

        DivingIds.DASHBOARD to listOf(
            MethodSetting.FloatSetting("depth_m", "Planned depth", group = "Dive", defaultValue = 30f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("duration_min", "Planned constant-depth time", group = "Dive", defaultValue = 25f, minimum = 0f, maximum = 600f, step = 1f, unit = "min", decimals = 1),
            MethodSetting.FloatSetting("rmv_l_min", "Planning RMV", group = "Diver", defaultValue = 18f, minimum = 1f, maximum = 100f, step = 0.5f, unit = "L/min", decimals = 1),
            MethodSetting.FloatSetting("cylinder_volume_l", "Cylinder water volume", group = "Cylinder", defaultValue = 12f, minimum = 0.5f, maximum = 100f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Start pressure", group = "Cylinder", defaultValue = 200f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("reserve_pressure_bar", "Reserve pressure", group = "Cylinder", defaultValue = 50f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0)
        ) + mix(32f, 0f) + ppo2Limit() + listOf(
            MethodSetting.ChoiceSetting("narcotic_model", "Narcotic-gas model", group = "Model", defaultValue = "oxygen_and_nitrogen", choices = listOf("oxygen_and_nitrogen", "nitrogen_only")),
            MethodSetting.FloatSetting("gas_temperature_c", "Gas temperature", group = "Environment", defaultValue = 20f, minimum = -5f, maximum = 50f, step = 0.5f, unit = "°C", decimals = 1)
        ) + environment()
    )

    fun forMethod(methodId: String): List<MethodSetting> = all[methodId].orEmpty()
}

object DivingModule : MethodMeshModule {
    override val moduleId = "diving"
    override val displayName = "Diving"
    override val summary = "Recreational and technical diving physics, gas planning, cylinder/log records, and persistent planning dashboards."
    override val iconKey = "diving"

    override fun as100Methods() = DivingMethods.allCore + DivingRecordMethods.all

    override fun rilBindings() = listOf(
        RilBinding("calculate diving SAC RMV", DivingIds.SAC_RMV, "Calculate SAC and RMV from a dive and cylinder pressure drop"),
        RilBinding("calculate cylinder gas", DivingIds.CYLINDER_CAPACITY, "Calculate nominal cylinder gas and reserve"),
        RilBinding("calculate diving ppO2", DivingIds.PPO2, "Calculate oxygen partial pressure at depth"),
        RilBinding("calculate diving MOD", DivingIds.MOD, "Calculate maximum operating depth from analysed FO2 and configured ppO2 limit"),
        RilBinding("calculate nitrox best mix", DivingIds.BEST_MIX, "Calculate maximum FO2 at a planned depth for a configured ppO2 limit"),
        RilBinding("calculate equivalent air depth", DivingIds.EAD, "Calculate EAD for nitrox"),
        RilBinding("calculate equivalent narcotic depth", DivingIds.END, "Calculate END for helium mixes"),
        RilBinding("calculate diving gas density", DivingIds.GAS_DENSITY, "Estimate ideal-gas breathing density at depth"),
        RilBinding("plan diving gas", DivingIds.GAS_PLAN, "Calculate multi-segment gas requirement and reserve margin"),
        RilBinding("plan diving bailout gas", DivingIds.BAILOUT_PLAN, "Calculate simplified bailout ascent gas requirement"),
        RilBinding("calculate dive navigation", DivingIds.NAVIGATION, "Calculate reciprocal heading and simple time/distance navigation"),
        RilBinding("calculate lift bag gas", DivingIds.LIFT, "Estimate minimum lift-bag displacement and inflation gas"),
        RilBinding("open dive planner dashboard", DivingIds.DASHBOARD, "Open the persistent diving planning dashboard"),
        RilBinding("record dive", DivingIds.LOG_RECORD, "Record a dive in the local MethodMesh dive log"),
        RilBinding("open dive log dashboard", DivingIds.LOG_DASHBOARD, "Summarise locally recorded dives"),
        RilBinding("record diving gas analysis", DivingIds.GAS_ANALYSIS_RECORD, "Record an analysed O2/He mix for a cylinder"),
        RilBinding("record diving cylinder", DivingIds.CYLINDER_RECORD, "Create or update a local diving cylinder record"),
        RilBinding("open diving gas dashboard", DivingIds.GAS_DASHBOARD, "Summarise local cylinders and gas analyses")
    )

    override fun capabilityScreens() = DivingCapabilityScreens.all

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = DivingSettings.all + DivingRecordSettings.all
}
