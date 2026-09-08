package com.example.methodmesh.modules.aviation

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AviationModule : MethodMeshModule {
    override val moduleId = "aviation"
    override val displayName = "Aviation tools"
    override val summary = "Pilot-oriented flight deck, emergency phone-reference instruments, airfield/runway context and offline planning calculations with GPS-aware inputs and audit outputs."

    override fun as100Methods() = listOf(
        As100AviationDashboardMethod,
        As100AviationEmergencyInstrumentsMethod,
        As100NearbyAirfieldsMethod,
        As100RunwayWindMethod,
        As100AviationAltitudeMethod,
        As100AviationE6bMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("open emergency instrument panel", As100AviationEmergencyInstrumentsMethod.ID, "Open a persistent supplementary phone-sensor emergency reference panel"),
        RilBinding("show emergency instruments", As100AviationEmergencyInstrumentsMethod.ID, "Capture a supplementary phone-sensor aviation reference snapshot"),
        RilBinding("open aviation dashboard", As100AviationDashboardMethod.ID, "Open a persistent GPS-aware aviation dashboard"),
        RilBinding("show aviation dashboard", As100AviationDashboardMethod.ID, "Refresh local aviation context and return a snapshot"),
        RilBinding("find nearby airfields", As100NearbyAirfieldsMethod.ID, "Find aerodromes around current or supplied coordinates"),
        RilBinding("calculate runway wind", As100RunwayWindMethod.ID, "Calculate runway headwind, tailwind and crosswind components"),
        RilBinding("calculate density altitude", As100AviationAltitudeMethod.ID, "Calculate pressure and density altitude"),
        RilBinding("calculate e6b", As100AviationE6bMethod.ID, "Run common electronic flight-computer calculations")
    )

    override fun capabilityScreens() = listOf(
        AviationDashboardCapabilityScreen,
        AviationEmergencyInstrumentsCapabilityScreen,
        NearbyAirfieldsCapabilityScreen,
        RunwayWindCapabilityScreen,
        AviationAltitudeCapabilityScreen,
        AviationE6bCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100AviationEmergencyInstrumentsMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "mount_orientation", "Phone mount", "Physical phone orientation in a rigid aircraft mount.", "Attitude",
                defaultValue = "portrait_up", choices = listOf("portrait_up", "landscape_left", "landscape_right", "portrait_down")
            ),
            MethodSetting.FloatSetting("pitch_zero_deg", "Pitch zero", "Saved device pitch offset for a known level reference.", "Attitude", 0f, -180f, 180f, 0.1f, "°", 1),
            MethodSetting.FloatSetting("roll_zero_deg", "Roll zero", "Saved device roll offset for a known level reference.", "Attitude", 0f, -180f, 180f, 0.1f, "°", 1),
            MethodSetting.BooleanSetting("attitude_calibrated", "Attitude calibrated", "Whether the saved zero is a deliberate level reference.", "Attitude", false),
            MethodSetting.ChoiceSetting(
                "airfield_radius_nm", "Airfield radius", "Radius for nearby airfield reference.", "Airfields",
                defaultValue = "25", choices = listOf("10", "25", "50", "100")
            ),
            MethodSetting.ChoiceSetting(
                "airfield_refresh_policy", "Airfield reference data", "Use cached airfield reference data without blocking the live instruments.", "Airfields",
                defaultValue = "refresh_if_stale", choices = listOf("refresh_if_stale", "cache_only", "force_refresh")
            )
        ),
        As100AviationDashboardMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "radius_nm", "Search radius", "Radius around current or supplied position.", "Airfields",
                defaultValue = "25", choices = listOf("5", "10", "25", "50", "100", "200")
            ),
            MethodSetting.MultiChoiceSetting(
                "airfield_types", "Airfield types", "Aerodrome types included on the dashboard.", "Airfields",
                defaultValue = "large_airport|medium_airport|small_airport",
                choices = listOf("large_airport", "medium_airport", "small_airport", "heliport", "seaplane_base", "balloonport")
            ),
            MethodSetting.BooleanSetting(
                "scheduled_only", "Scheduled-service only", "Hide aerodromes without scheduled service.", "Airfields", defaultValue = false
            ),
            MethodSetting.ChoiceSetting(
                "max_results", "Nearby airfields", "Number of nearby airfields retained in the dashboard strip.", "Airfields",
                defaultValue = "10", choices = listOf("5", "10", "20")
            ),
            MethodSetting.ChoiceSetting(
                "refresh_policy", "Aviation catalogues", "Airfield and runway catalogues are cached locally.", "Data",
                defaultValue = "refresh_if_stale", choices = listOf("refresh_if_stale", "cache_only", "force_refresh")
            ),
            MethodSetting.BooleanSetting(
                "conditions_enabled", "Use current conditions", "Enable wind-component and density-altitude cards.", "Conditions", defaultValue = false
            ),
            MethodSetting.IntSetting("wind_direction_deg", "Wind from", "Direction wind is from.", "Conditions", 0, 0, 360, 1, "°"),
            MethodSetting.FloatSetting("wind_speed_kt", "Wind speed", "Steady wind speed.", "Conditions", 0f, 0f, 250f, 1f, "kt", 1),
            MethodSetting.BooleanSetting("include_gust", "Include gust", "Include gust components.", "Conditions", false),
            MethodSetting.FloatSetting("gust_speed_kt", "Gust speed", "Optional gust speed.", "Conditions", 0f, 0f, 300f, 1f, "kt", 1),
            MethodSetting.ChoiceSetting("qnh_unit", "QNH unit", "Pressure unit.", "Conditions", "hpa", listOf("hpa", "inhg")),
            MethodSetting.FloatSetting("qnh_value", "QNH", "Current QNH/altimeter setting.", "Conditions", 1013.25f, null, null, 0.01f, null, 2),
            MethodSetting.FloatSetting("oat_c", "Outside-air temperature", "Current outside-air temperature.", "Conditions", 15f, -100f, 80f, 0.1f, "°C", 1)
        ),
        As100NearbyAirfieldsMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "radius_nm", "Search radius", "Radius around current or supplied position.", "Search",
                defaultValue = "25", choices = listOf("5", "10", "25", "50", "100", "200")
            ),
            MethodSetting.MultiChoiceSetting(
                "airfield_types", "Airfield types", "Select aerodrome types to include.", "Search",
                defaultValue = "large_airport|medium_airport|small_airport",
                choices = listOf("large_airport", "medium_airport", "small_airport", "heliport", "seaplane_base", "balloonport")
            ),
            MethodSetting.BooleanSetting(
                "scheduled_only", "Scheduled-service only", "Hide aerodromes without scheduled service.", "Search", defaultValue = false
            ),
            MethodSetting.ChoiceSetting(
                "max_results", "Maximum results", "Number of nearest matching airfields returned.", "Search",
                defaultValue = "10", choices = listOf("5", "10", "20", "50")
            ),
            MethodSetting.ChoiceSetting(
                "refresh_policy", "Airfield data", "The catalogue is cached locally after download.", "Data",
                defaultValue = "refresh_if_stale", choices = listOf("refresh_if_stale", "cache_only", "force_refresh")
            )
        ),
        As100RunwayWindMethod.ID to listOf(
            MethodSetting.IntSetting("runway_heading_deg", "Runway heading", "Magnetic runway heading in degrees.", "Runway", 0, 0, 360, 1, "°"),
            MethodSetting.IntSetting("wind_direction_deg", "Wind from", "Wind direction in degrees.", "Wind", 0, 0, 360, 1, "°"),
            MethodSetting.FloatSetting("wind_speed_kt", "Wind speed", "Steady wind speed.", "Wind", 0f, 0f, 250f, 1f, "kt", 1),
            MethodSetting.BooleanSetting("include_gust", "Include gust", "Calculate gust components as well.", "Wind", false),
            MethodSetting.FloatSetting("gust_speed_kt", "Gust speed", "Optional gust speed.", "Wind", 0f, 0f, 300f, 1f, "kt", 1)
        ),
        As100AviationAltitudeMethod.ID to listOf(
            MethodSetting.FloatSetting("field_elevation_ft", "Field elevation", "Aerodrome elevation; can be piped from nearby-airfield output.", "Inputs", 0f, -2000f, 30000f, 1f, "ft", 0),
            MethodSetting.ChoiceSetting("qnh_unit", "QNH unit", "Pressure unit.", "Inputs", "hpa", listOf("hpa", "inhg")),
            MethodSetting.FloatSetting("qnh_value", "QNH", "Current QNH/altimeter setting; valid range depends on selected unit.", "Inputs", 1013.25f, null, null, 0.01f, null, 2),
            MethodSetting.FloatSetting("oat_c", "Outside-air temperature", "Current outside-air temperature.", "Inputs", 15f, -100f, 80f, 0.1f, "°C", 1)
        ),
        As100AviationE6bMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "calculation", "Calculation", "Choose what to solve for.", "Calculation", "time_from_distance_speed",
                listOf("time_from_distance_speed", "distance_from_speed_time", "speed_from_distance_time", "fuel_required", "endurance", "range")
            ),
            MethodSetting.FloatSetting("distance_nm", "Distance", null, "Inputs", 0f, 0f, 100000f, 0.1f, "NM", 1),
            MethodSetting.FloatSetting("groundspeed_kt", "Groundspeed", null, "Inputs", 0f, 0f, 2000f, 0.1f, "kt", 1),
            MethodSetting.FloatSetting("time_minutes", "Time", null, "Inputs", 0f, 0f, 100000f, 0.1f, "min", 1),
            MethodSetting.ChoiceSetting("fuel_unit", "Fuel unit", "Fuel available and burn must use the same unit.", "Fuel", "L", listOf("L", "US gal", "Imp gal", "kg")),
            MethodSetting.FloatSetting("fuel_available", "Fuel available", null, "Fuel", 0f, 0f, 100000f, 0.1f, null, 1),
            MethodSetting.FloatSetting("fuel_burn_per_hour", "Fuel burn per hour", null, "Fuel", 0f, 0f, 100000f, 0.1f, null, 1)
        )
    )
}
