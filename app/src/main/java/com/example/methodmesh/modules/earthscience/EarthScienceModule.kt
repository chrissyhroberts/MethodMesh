package com.example.methodmesh.modules.earthscience

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object EarthScienceModule : MethodMeshModule {
    override val moduleId = "earthscience"
    override val displayName = "Earth science utilities"
    override val summary = "Field geodesy, GNSS averaging, structural geology, geological time and earth-material classification."

    override fun as100Methods() = listOf(
        As100GeodesyDistanceBearingMethod,
        As100GeodesyDestinationMethod,
        As100GeodesyWgs84ToUtmMethod,
        As100GeodesyUtmToWgs84Method,
        As100GnssAveragePositionMethod,
        As100StructuralPlaneMethod,
        As100StructuralPlaneCaptureMethod,
        As100StructuralLineMethod,
        As100StructuralPlaneIntersectionMethod,
        As100GeoTimeLookupMethod,
        As100SoilTextureMethod,
        As100SedimentGrainSizeMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("calculate geodesic distance", As100GeodesyDistanceBearingMethod.ID, "Calculate WGS84 ellipsoidal distance and bearings between two positions"),
        RilBinding("calculate destination coordinate", As100GeodesyDestinationMethod.ID, "Project a WGS84 coordinate by bearing and distance"),
        RilBinding("convert WGS84 to UTM", As100GeodesyWgs84ToUtmMethod.ID, "Convert latitude/longitude to UTM"),
        RilBinding("convert UTM to WGS84", As100GeodesyUtmToWgs84Method.ID, "Convert UTM coordinates to latitude/longitude"),
        RilBinding("average GNSS position", As100GnssAveragePositionMethod.ID, "Collect and average repeated GNSS fixes"),
        RilBinding("normalise geological plane", As100StructuralPlaneMethod.ID, "Normalise strike and dip and derive dip direction and pole"),
        RilBinding("capture geological plane", As100StructuralPlaneCaptureMethod.ID, "Capture a Development-stage strike and dip estimate from phone orientation"),
        RilBinding("normalise geological line", As100StructuralLineMethod.ID, "Normalise trend and plunge"),
        RilBinding("intersect geological planes", As100StructuralPlaneIntersectionMethod.ID, "Calculate the trend and plunge of the intersection of two planes"),
        RilBinding("lookup geological time", As100GeoTimeLookupMethod.ID, "Classify a numerical geological age using the bundled ICS hierarchy"),
        RilBinding("classify soil texture", As100SoilTextureMethod.ID, "Classify sand, silt and clay percentages using USDA textural classes"),
        RilBinding("classify sediment grain size", As100SedimentGrainSizeMethod.ID, "Convert millimetres and phi and classify Wentworth grain size")
    )

    override fun capabilityScreens() = listOf(
        GeodesyDistanceBearingScreen,
        GeodesyDestinationScreen,
        GeodesyWgs84ToUtmScreen,
        GeodesyUtmToWgs84Screen,
        GnssAverageCapabilityScreen,
        StructuralPlaneScreen,
        StructuralPlaneCaptureCapabilityScreen,
        StructuralLineScreen,
        StructuralPlaneIntersectionScreen,
        GeoTimeLookupScreen,
        SoilTextureScreen,
        SedimentGrainSizeScreen
    )

    override fun capabilitySettings() = mapOf(
        As100GeodesyDistanceBearingMethod.ID to listOf(
            MethodSetting.FloatSetting("latitude_1", "Latitude 1", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("longitude_1", "Longitude 1", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.FloatSetting("latitude_2", "Latitude 2", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("longitude_2", "Longitude 2", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6)
        ),
        As100GeodesyDestinationMethod.ID to listOf(
            MethodSetting.FloatSetting("latitude", "Start latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("longitude", "Start longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.FloatSetting("bearing_deg", "Initial bearing", defaultValue = 0f, minimum = 0f, maximum = 360f, decimals = 3),
            MethodSetting.FloatSetting("distance_m", "Distance", defaultValue = 100f, minimum = 0f, unit = "m", decimals = 3)
        ),
        As100GeodesyWgs84ToUtmMethod.ID to listOf(
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -80f, maximum = 84f, decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6)
        ),
        As100GeodesyUtmToWgs84Method.ID to listOf(
            MethodSetting.TextSetting("utm_text", "Complete UTM result", defaultValue = ""),
            MethodSetting.IntSetting("zone", "Zone number", defaultValue = 31, minimum = 1, maximum = 60),
            MethodSetting.ChoiceSetting("hemisphere", "Hemisphere", defaultValue = "N", choices = listOf("N", "S")),
            MethodSetting.FloatSetting("easting_m", "Easting", defaultValue = 500000f, minimum = 0f, maximum = 1000000f, unit = "m", decimals = 3),
            MethodSetting.FloatSetting("northing_m", "Northing", defaultValue = 0f, minimum = 0f, maximum = 10000000f, unit = "m", decimals = 3)
        ),
        As100GnssAveragePositionMethod.ID to listOf(
            MethodSetting.IntSetting("target_fix_count", "Accepted fixes", defaultValue = 20, minimum = 2, maximum = 500),
            MethodSetting.FloatSetting("max_accuracy_m", "Maximum horizontal uncertainty", defaultValue = 10f, minimum = 0.5f, maximum = 500f, unit = "m", decimals = 1),
            MethodSetting.ChoiceSetting("weighting", "Averaging", defaultValue = "inverse_variance", choices = listOf("inverse_variance", "equal"))
        ),
        As100StructuralPlaneMethod.ID to listOf(
            MethodSetting.FloatSetting("strike_deg", "Strike", defaultValue = 0f, minimum = 0f, maximum = 360f, decimals = 1),
            MethodSetting.FloatSetting("dip_deg", "Dip", defaultValue = 0f, minimum = 0f, maximum = 90f, decimals = 1),
            MethodSetting.ChoiceSetting("convention", "Convention", defaultValue = "right_hand_rule", choices = listOf("right_hand_rule", "explicit_dip_direction")),
            MethodSetting.FloatSetting("dip_direction_deg", "Dip direction", defaultValue = 90f, minimum = 0f, maximum = 360f, decimals = 1)
        ),
        As100StructuralPlaneCaptureMethod.ID to listOf(
            MethodSetting.ChoiceSetting("strike_reference", "Strike reference", defaultValue = "magnetic", choices = listOf("magnetic")),
            MethodSetting.FloatSetting("max_level_error_deg", "Maximum along-strike tilt", defaultValue = 5f, minimum = 0.5f, maximum = 20f, decimals = 1)
        ),
        As100StructuralLineMethod.ID to listOf(
            MethodSetting.FloatSetting("trend_deg", "Trend", defaultValue = 0f, minimum = 0f, maximum = 360f, decimals = 1),
            MethodSetting.FloatSetting("plunge_deg", "Plunge", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 1)
        ),
        As100StructuralPlaneIntersectionMethod.ID to listOf(
            MethodSetting.FloatSetting("strike_1_deg", "Plane 1 strike", defaultValue = 0f, minimum = 0f, maximum = 360f, decimals = 1),
            MethodSetting.FloatSetting("dip_1_deg", "Plane 1 dip", defaultValue = 30f, minimum = 0f, maximum = 90f, decimals = 1),
            MethodSetting.FloatSetting("strike_2_deg", "Plane 2 strike", defaultValue = 90f, minimum = 0f, maximum = 360f, decimals = 1),
            MethodSetting.FloatSetting("dip_2_deg", "Plane 2 dip", defaultValue = 30f, minimum = 0f, maximum = 90f, decimals = 1)
        ),
        As100GeoTimeLookupMethod.ID to listOf(
            MethodSetting.FloatSetting("age_ma", "Age", defaultValue = 66f, minimum = 0f, maximum = 4600f, unit = "Ma", decimals = 4)
        ),
        As100SoilTextureMethod.ID to listOf(
            MethodSetting.FloatSetting("sand_pct", "Sand", defaultValue = 40f, minimum = 0f, maximum = 100f, unit = "%", decimals = 1),
            MethodSetting.FloatSetting("silt_pct", "Silt", defaultValue = 40f, minimum = 0f, maximum = 100f, unit = "%", decimals = 1),
            MethodSetting.FloatSetting("clay_pct", "Clay", defaultValue = 20f, minimum = 0f, maximum = 100f, unit = "%", decimals = 1),
            MethodSetting.FloatSetting("sum_tolerance_pct", "Allowed total deviation", defaultValue = 1f, minimum = 0f, maximum = 5f, unit = "%", decimals = 1)
        ),
        As100SedimentGrainSizeMethod.ID to listOf(
            MethodSetting.ChoiceSetting("input_mode", "Input unit", defaultValue = "diameter_mm", choices = listOf("diameter_mm", "phi")),
            MethodSetting.FloatSetting("diameter_mm", "Diameter", defaultValue = 1f, minimum = 0.000001f, unit = "mm", decimals = 6),
            MethodSetting.FloatSetting("phi", "Phi", defaultValue = 0f, minimum = -20f, maximum = 30f, decimals = 3)
        )
    )
}
