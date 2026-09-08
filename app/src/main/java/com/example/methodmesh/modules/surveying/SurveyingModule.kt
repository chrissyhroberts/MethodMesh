package com.example.methodmesh.modules.surveying

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object SurveyingModule : MethodMeshModule {
    override val moduleId = "surveying"
    override val displayName = "Surveying"
    override val summary = "Traverse, coordinates, offsets, levelling, area, GPS QC and field-book tools."
    override val iconKey = "location"

    override fun as100Methods() = listOf(
        As100BearingDistanceMethod,
        As100ForwardCoordinateMethod,
        As100OffsetPointMethod,
        As100ChainageOffsetMethod,
        As100TraverseMethod,
        As100AreaMethod,
        As100LevelReduceMethod,
        As100GradeMethod,
        As100GpsAverageMethod,
        As100IntersectionMethod,
        As100SetoutMethod,
        As100TraverseBookMethod,
        As100LevellingBookMethod
    )

    override fun capabilityScreens() = listOf(
        BearingDistanceCapabilityScreen,
        ForwardCoordinateCapabilityScreen,
        OffsetPointCapabilityScreen,
        ChainageOffsetCapabilityScreen,
        TraverseCapabilityScreen,
        AreaCapabilityScreen,
        LevelReduceCapabilityScreen,
        GradeCapabilityScreen,
        GpsAverageCapabilityScreen,
        IntersectionCapabilityScreen,
        SetoutCapabilityScreen,
        TraverseBookCapabilityScreen,
        LevellingBookCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding("calculate bearing and distance", As100BearingDistanceMethod.id, "Bearing/distance between two coordinates"),
        RilBinding("project survey coordinate", As100ForwardCoordinateMethod.id, "Forward coordinate from azimuth and distance"),
        RilBinding("calculate survey offset", As100OffsetPointMethod.id, "Coordinate from baseline chainage and signed offset"),
        RilBinding("calculate chainage offset", As100ChainageOffsetMethod.id, "Project a coordinate onto a baseline"),
        RilBinding("calculate traverse", As100TraverseMethod.id, "Traverse coordinates, closure and adjustment"),
        RilBinding("calculate area from coordinates", As100AreaMethod.id, "Shoelace area, perimeter and centroid"),
        RilBinding("reduce levelling book", As100LevelReduceMethod.id, "Reduce BS/IS/FS observations and closure"),
        RilBinding("calculate grade", As100GradeMethod.id, "Rise/fall, percent grade, angle and 1:n"),
        RilBinding("average gps fixes", As100GpsAverageMethod.id, "Average repeated GPS fixes and report scatter"),
        RilBinding("intersect survey bearings", As100IntersectionMethod.id, "Bearing-bearing intersection from two control points"),
        RilBinding("set out survey point", As100SetoutMethod.id, "Grid bearing and distance to a target"),
        RilBinding("open traverse field book", As100TraverseBookMethod.id, "Persistent traverse dashboard"),
        RilBinding("open levelling field book", As100LevellingBookMethod.id, "Persistent levelling dashboard")
    )

    override fun capabilitySettings() = mapOf(
        As100BearingDistanceMethod.id to listOf(
            MethodSetting.ChoiceSetting("coordinate_mode", "Coordinate mode", defaultValue = "planar", choices = listOf("planar", "gps")),
            MethodSetting.FloatSetting("start_easting", "Start easting", defaultValue = 0f),
            MethodSetting.FloatSetting("start_northing", "Start northing", defaultValue = 0f),
            MethodSetting.TextSetting("start_elevation", "Start elevation (optional)", defaultValue = ""),
            MethodSetting.FloatSetting("end_easting", "End easting", defaultValue = 0f),
            MethodSetting.FloatSetting("end_northing", "End northing", defaultValue = 0f),
            MethodSetting.TextSetting("end_elevation", "End elevation (optional)", defaultValue = ""),
            MethodSetting.TextSetting("start_latitude", "Start latitude", defaultValue = ""),
            MethodSetting.TextSetting("start_longitude", "Start longitude", defaultValue = ""),
            MethodSetting.TextSetting("end_latitude", "End latitude", defaultValue = ""),
            MethodSetting.TextSetting("end_longitude", "End longitude", defaultValue = "")
        ),
        As100ForwardCoordinateMethod.id to listOf(
            MethodSetting.FloatSetting("start_easting", "Start easting", defaultValue = 0f),
            MethodSetting.FloatSetting("start_northing", "Start northing", defaultValue = 0f),
            MethodSetting.FloatSetting("azimuth_deg", "Azimuth", defaultValue = 0f, minimum = -3600f, maximum = 3600f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("horizontal_distance_m", "Horizontal distance", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 4)
        ),
        As100OffsetPointMethod.id to listOf(
            MethodSetting.FloatSetting("start_easting", "Baseline start easting", defaultValue = 0f),
            MethodSetting.FloatSetting("start_northing", "Baseline start northing", defaultValue = 0f),
            MethodSetting.FloatSetting("end_easting", "Baseline end easting", defaultValue = 0f),
            MethodSetting.FloatSetting("end_northing", "Baseline end northing", defaultValue = 100f),
            MethodSetting.FloatSetting("chainage_m", "Chainage", defaultValue = 0f, unit = "m", decimals = 4),
            MethodSetting.FloatSetting("offset_right_m", "Offset (right positive)", defaultValue = 0f, unit = "m", decimals = 4)
        ),
        As100ChainageOffsetMethod.id to listOf(
            MethodSetting.FloatSetting("start_easting", "Baseline start easting", defaultValue = 0f),
            MethodSetting.FloatSetting("start_northing", "Baseline start northing", defaultValue = 0f),
            MethodSetting.FloatSetting("end_easting", "Baseline end easting", defaultValue = 0f),
            MethodSetting.FloatSetting("end_northing", "Baseline end northing", defaultValue = 100f),
            MethodSetting.FloatSetting("point_easting", "Point easting", defaultValue = 0f),
            MethodSetting.FloatSetting("point_northing", "Point northing", defaultValue = 0f)
        ),
        As100TraverseMethod.id to traverseSettings(),
        As100AreaMethod.id to listOf(
            MethodSetting.TextSetting("coordinates", "Coordinates", defaultValue = "P1,0,0\nP2,10,0\nP3,10,10\nP4,0,10")
        ),
        As100LevelReduceMethod.id to levelSettings(),
        As100GradeMethod.id to listOf(
            MethodSetting.FloatSetting("rise_m", "Rise (+) / fall (-)", defaultValue = 0f, unit = "m", decimals = 4),
            MethodSetting.FloatSetting("horizontal_distance_m", "Horizontal distance", defaultValue = 1f, minimum = 0.000001f, unit = "m", decimals = 4)
        ),
        As100GpsAverageMethod.id to listOf(
            MethodSetting.TextSetting("gps_fixes", "GPS fixes: lat,lon,accuracy per line", defaultValue = ""),
            MethodSetting.BooleanSetting("accuracy_weighted", "Weight by reported accuracy", defaultValue = true)
        ),
        As100IntersectionMethod.id to listOf(
            MethodSetting.FloatSetting("station_a_easting", "Station A easting", defaultValue = 0f),
            MethodSetting.FloatSetting("station_a_northing", "Station A northing", defaultValue = 0f),
            MethodSetting.FloatSetting("bearing_a_deg", "Bearing from A", defaultValue = 90f, unit = "°", decimals = 6),
            MethodSetting.FloatSetting("station_b_easting", "Station B easting", defaultValue = 100f),
            MethodSetting.FloatSetting("station_b_northing", "Station B northing", defaultValue = -100f),
            MethodSetting.FloatSetting("bearing_b_deg", "Bearing from B", defaultValue = 0f, unit = "°", decimals = 6)
        ),
        As100SetoutMethod.id to listOf(
            MethodSetting.FloatSetting("current_easting", "Current easting", defaultValue = 0f),
            MethodSetting.FloatSetting("current_northing", "Current northing", defaultValue = 0f),
            MethodSetting.FloatSetting("target_easting", "Target easting", defaultValue = 0f),
            MethodSetting.FloatSetting("target_northing", "Target northing", defaultValue = 0f)
        ),
        As100TraverseBookMethod.id to traverseBookSettings(),
        As100LevellingBookMethod.id to levellingBookSettings()
    )

    private fun traverseSettings() = listOf(
        MethodSetting.TextSetting("start_point_id", "Start point ID", defaultValue = "START"),
        MethodSetting.FloatSetting("start_easting", "Start easting", defaultValue = 0f),
        MethodSetting.FloatSetting("start_northing", "Start northing", defaultValue = 0f),
        MethodSetting.TextSetting("traverse_legs", "Legs: point,bearing,distance per line", defaultValue = "P1,90,100\nP2,0,100"),
        MethodSetting.TextSetting("close_easting", "Known close easting (optional)", defaultValue = ""),
        MethodSetting.TextSetting("close_northing", "Known close northing (optional)", defaultValue = ""),
        MethodSetting.ChoiceSetting("adjustment_mode", "Adjustment", defaultValue = "none", choices = listOf("none", "bowditch", "transit")),
        MethodSetting.TextSetting("minimum_relative_precision", "Minimum relative precision 1:n (optional)", defaultValue = "")
    )

    private fun levelSettings() = listOf(
        MethodSetting.FloatSetting("start_reduced_level_m", "Starting reduced level", defaultValue = 100f, unit = "m", decimals = 4),
        MethodSetting.TextSetting("level_observations", "Observations: station,BS|IS|FS,reading,distance", defaultValue = "BM,BS,1.500,0\nP1,IS,2.000,25\nCP1,FS,2.500,25\nCP1,BS,1.000,0\nEND,FS,1.500,50"),
        MethodSetting.TextSetting("known_close_reduced_level_m", "Known closing RL (optional)", defaultValue = ""),
        MethodSetting.BooleanSetting("distribute_closure", "Distribute closure correction", defaultValue = true)
    )

    private fun traverseBookSettings() = listOf(
        MethodSetting.TextSetting("survey_job_id", "Job ID (blank = create/select locally)", defaultValue = ""),
        MethodSetting.TextSetting("survey_job_name", "Job name", defaultValue = "Traverse"),
        MethodSetting.TextSetting("start_point_id", "Start point ID", defaultValue = "START"),
        MethodSetting.FloatSetting("start_easting", "Start easting", defaultValue = 0f),
        MethodSetting.FloatSetting("start_northing", "Start northing", defaultValue = 0f),
        MethodSetting.TextSetting("close_easting", "Known close easting (optional)", defaultValue = ""),
        MethodSetting.TextSetting("close_northing", "Known close northing (optional)", defaultValue = ""),
        MethodSetting.ChoiceSetting("adjustment_mode", "Adjustment", defaultValue = "none", choices = listOf("none", "bowditch", "transit")),
        MethodSetting.TextSetting("minimum_relative_precision", "Minimum relative precision 1:n (optional)", defaultValue = "")
    )

    private fun levellingBookSettings() = listOf(
        MethodSetting.TextSetting("survey_job_id", "Job ID (blank = create/select locally)", defaultValue = ""),
        MethodSetting.TextSetting("survey_job_name", "Job name", defaultValue = "Levelling"),
        MethodSetting.FloatSetting("start_reduced_level_m", "Starting reduced level", defaultValue = 100f, unit = "m", decimals = 4),
        MethodSetting.TextSetting("known_close_reduced_level_m", "Known closing RL (optional)", defaultValue = ""),
        MethodSetting.BooleanSetting("distribute_closure", "Distribute closure correction", defaultValue = true)
    )
}
