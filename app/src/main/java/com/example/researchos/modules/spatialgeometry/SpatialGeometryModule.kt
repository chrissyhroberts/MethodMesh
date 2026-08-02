package com.example.researchos.modules.spatialgeometry

import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.settings.MethodSetting

object SpatialGeometryModule : ResearchOSModule {
    override val moduleId = "spatialgeometry"
    override val displayName = "Spatial geometry"
    override val summary = "Measure heights, slopes, and distances with phone orientation sensors."

    override fun as100Methods() = listOf(
        As100TreeHeightMethod,
        As100SlopeInclinationMethod,
        As100GeometryDistanceMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("estimate height", As100TreeHeightMethod.id, "Triangulate object height from distance and top/base angles"),
        RilBinding("measure slope", As100SlopeInclinationMethod.id, "Measure slope and inclination from phone orientation"),
        RilBinding("estimate distance", As100GeometryDistanceMethod.id, "Estimate distance from a known reference height and angular size")
    )

    override fun capabilityScreens() = listOf(
        TreeHeightCapabilityScreen,
        SlopeInclinationCapabilityScreen,
        GeometryDistanceCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100TreeHeightMethod.id to listOf(
            MethodSetting.FloatSetting("horizontal_distance_m", "Horizontal distance", defaultValue = 10f, minimum = 0.01f, maximum = 100000f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("observer_height_m", "Observer eye height", defaultValue = 1.6f, minimum = 0f, maximum = 10f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("base_angle_deg", "Base angle", defaultValue = 0f, minimum = -89f, maximum = 89f, unit = "°", decimals = 2),
            MethodSetting.FloatSetting("top_angle_deg", "Top angle", defaultValue = 0f, minimum = -89f, maximum = 89f, unit = "°", decimals = 2)
        ),
        As100SlopeInclinationMethod.id to listOf(
            MethodSetting.FloatSetting("slope_angle_deg", "Top/bottom inclination", defaultValue = 0f, minimum = -89f, maximum = 89f, unit = "°", decimals = 2),
            MethodSetting.FloatSetting("tilt_angle_deg", "Left/right tilt", defaultValue = 0f, minimum = -89f, maximum = 89f, unit = "°", decimals = 2)
        ),
        As100GeometryDistanceMethod.id to listOf(
            MethodSetting.FloatSetting("reference_height_m", "Known reference height", defaultValue = 1f, minimum = 0.001f, maximum = 100000f, unit = "m", decimals = 3),
            MethodSetting.FloatSetting("angular_size_deg", "Angular size", defaultValue = 10f, minimum = 0.01f, maximum = 179f, unit = "°", decimals = 2)
        )
    )
}
