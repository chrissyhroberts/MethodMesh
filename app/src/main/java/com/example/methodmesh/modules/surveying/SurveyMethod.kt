package com.example.methodmesh.modules.surveying

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import java.time.Instant
import java.util.Locale
import kotlin.math.abs

object SurveyFields {
    const val STATUS = "survey_status"
    const val VALID = "survey_valid"
    const val METHOD = "survey_method"
    const val MAIN_RESULT = "survey_main_result"
    const val METADATA_JSON = "survey_metadata_json"
    const val ERROR = "survey_error"

    const val START_EASTING = "start_easting"
    const val START_NORTHING = "start_northing"
    const val START_ELEVATION = "start_elevation"
    const val END_EASTING = "end_easting"
    const val END_NORTHING = "end_northing"
    const val END_ELEVATION = "end_elevation"
    const val DELTA_EASTING = "delta_easting"
    const val DELTA_NORTHING = "delta_northing"
    const val HORIZONTAL_DISTANCE_M = "horizontal_distance_m"
    const val SLOPE_DISTANCE_M = "slope_distance_m"
    const val ELEVATION_DIFFERENCE_M = "elevation_difference_m"
    const val AZIMUTH_DEG = "azimuth_deg"
    const val QUADRANT_BEARING = "quadrant_bearing"
    const val SLOPE_PERCENT = "slope_percent"

    const val CHAINAGE_M = "chainage_m"
    const val OFFSET_RIGHT_M = "offset_right_m"

    const val TOTAL_DISTANCE_M = "total_distance_m"
    const val MISCLOSURE_EASTING_M = "misclosure_easting_m"
    const val MISCLOSURE_NORTHING_M = "misclosure_northing_m"
    const val LINEAR_MISCLOSURE_M = "linear_misclosure_m"
    const val RELATIVE_PRECISION = "relative_precision"
    const val CLOSURE_PASS = "closure_pass"
    const val ADJUSTMENT_MODE = "adjustment_mode"
    const val POINT_COUNT = "point_count"
    const val POINTS_CSV = "survey_points_csv"

    const val AREA_SQ_M = "area_sq_m"
    const val SIGNED_AREA_SQ_M = "signed_area_sq_m"
    const val PERIMETER_M = "perimeter_m"
    const val CENTROID_EASTING = "centroid_easting"
    const val CENTROID_NORTHING = "centroid_northing"

    const val START_RL_M = "start_reduced_level_m"
    const val FINAL_RL_M = "final_reduced_level_m"
    const val ADJUSTED_FINAL_RL_M = "adjusted_final_reduced_level_m"
    const val KNOWN_CLOSE_RL_M = "known_close_reduced_level_m"
    const val LEVEL_CLOSURE_ERROR_M = "level_closure_error_m"
    const val SUM_BS_M = "sum_backsight_m"
    const val SUM_FS_M = "sum_foresight_m"
    const val LEVEL_ARITHMETIC_CHECK_ERROR_M = "level_arithmetic_check_error_m"
    const val LEVEL_BOOK_CSV = "level_book_csv"

    const val GRADE_PERCENT = "grade_percent"
    const val SLOPE_ANGLE_DEG = "slope_angle_deg"
    const val ONE_IN_N = "one_in_n"

    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val GPS_SAMPLE_COUNT = "gps_sample_count"
    const val GPS_RMS_SCATTER_M = "gps_rms_scatter_m"
    const val GPS_MAX_SCATTER_M = "gps_max_scatter_m"
    const val GPS_MEAN_REPORTED_ACCURACY_M = "gps_mean_reported_accuracy_m"
    const val GPS_WEIGHTED = "gps_accuracy_weighted"

    const val INTERSECTION_EASTING = "intersection_easting"
    const val INTERSECTION_NORTHING = "intersection_northing"
    const val CROSSING_ANGLE_DEG = "crossing_angle_deg"

    const val JOB_ID = "survey_job_id"
    const val JOB_NAME = "survey_job_name"
    const val OBSERVATION_COUNT = "observation_count"

    val outputs = listOf(
        STATUS, VALID, METHOD, MAIN_RESULT,
        START_EASTING, START_NORTHING, START_ELEVATION, END_EASTING, END_NORTHING, END_ELEVATION,
        DELTA_EASTING, DELTA_NORTHING, HORIZONTAL_DISTANCE_M, SLOPE_DISTANCE_M, ELEVATION_DIFFERENCE_M,
        AZIMUTH_DEG, QUADRANT_BEARING, SLOPE_PERCENT,
        CHAINAGE_M, OFFSET_RIGHT_M,
        TOTAL_DISTANCE_M, MISCLOSURE_EASTING_M, MISCLOSURE_NORTHING_M, LINEAR_MISCLOSURE_M,
        RELATIVE_PRECISION, CLOSURE_PASS, ADJUSTMENT_MODE, POINT_COUNT, POINTS_CSV,
        AREA_SQ_M, SIGNED_AREA_SQ_M, PERIMETER_M, CENTROID_EASTING, CENTROID_NORTHING,
        START_RL_M, FINAL_RL_M, ADJUSTED_FINAL_RL_M, KNOWN_CLOSE_RL_M, LEVEL_CLOSURE_ERROR_M,
        SUM_BS_M, SUM_FS_M, LEVEL_ARITHMETIC_CHECK_ERROR_M, LEVEL_BOOK_CSV,
        GRADE_PERCENT, SLOPE_ANGLE_DEG, ONE_IN_N,
        LATITUDE, LONGITUDE, GPS_SAMPLE_COUNT, GPS_RMS_SCATTER_M, GPS_MAX_SCATTER_M,
        GPS_MEAN_REPORTED_ACCURACY_M, GPS_WEIGHTED,
        INTERSECTION_EASTING, INTERSECTION_NORTHING, CROSSING_ANGLE_DEG,
        JOB_ID, JOB_NAME, OBSERVATION_COUNT,
        METADATA_JSON, ERROR
    )
}

data class SurveyCalculation(
    val valid: Boolean,
    val mainResult: String,
    val fields: Map<String, String> = emptyMap(),
    val error: String = ""
)

abstract class SurveyMethod(
    final override val id: String,
    private val methodName: String,
    private val methodDescription: String,
    private val graphOutput: String
) : As100Method {
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", methodName)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = methodName,
        version = "0.1.0",
        description = methodDescription,
        outputs = SurveyFields.outputs,
        graphOutputs = listOf(graphOutput),
        parameters = mapOf("category" to "Surveying", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = result(request, calculateSafely(request.context))
        .withInvocationContext(InvocationContext.from(request.context))

    fun calculateSafely(context: Map<String, String>): SurveyCalculation = runCatching {
        calculate(context)
    }.getOrElse { e ->
        SurveyCalculation(false, "Calculation failed", error = e.message ?: e::class.java.simpleName)
    }

    protected abstract fun calculate(context: Map<String, String>): SurveyCalculation

    fun result(request: ExecutionRequest, calculated: SurveyCalculation): ExecutionResult {
        val status = if (calculated.valid) TransformationStatus.Succeeded else TransformationStatus.Failed
        val common = linkedMapOf(
            SurveyFields.STATUS to if (calculated.valid) "ok" else "error",
            SurveyFields.VALID to calculated.valid.toString(),
            SurveyFields.METHOD to id,
            SurveyFields.MAIN_RESULT to calculated.mainResult,
            SurveyFields.ERROR to calculated.error,
            SurveyFields.METADATA_JSON to metadataJson(id, calculated)
        )
        val values = common + calculated.fields
        val entity = Entity(
            id = ArchitectureId("survey:${id}:${System.currentTimeMillis()}"),
            entityType = "SurveyCalculation",
            attributes = mapOf(SurveyFields.METHOD to id),
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.surveying", id, "0.1.0")
        val observation = Observation(
            phenomenon = "surveying.calculation",
            subject = ArchitectureRef(entity.id, entity.objectType, methodName),
            values = values + ("survey_calculated_time_iso" to Instant.now().toString()),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            status,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf("survey_valid" to calculated.valid.toString())
        )
    }
}

object As100BearingDistanceMethod : SurveyMethod(
    "survey.bearing_distance",
    "Bearing and distance",
    "Calculate local-grid or GPS bearing/distance between two points.",
    "survey.bearing_distance"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val mode = context.value("coordinate_mode", "planar").lowercase(Locale.US)
        return if (mode == "gps" || mode == "wgs84") {
            val lat1 = context.double("start_latitude", "gps_latitude", "latitude")
            val lon1 = context.double("start_longitude", "gps_longitude", "longitude")
            val lat2 = context.double("end_latitude", "target_latitude")
            val lon2 = context.double("end_longitude", "target_longitude")
            val inv = SurveyCalculations.geodesicInverse(lat1, lon1, lat2, lon2)
            SurveyCalculation(
                true,
                "${inv.distanceM.f2()} m at ${inv.initialBearingDeg.f4()}°",
                mapOf(
                    SurveyFields.HORIZONTAL_DISTANCE_M to inv.distanceM.f4(),
                    SurveyFields.AZIMUTH_DEG to inv.initialBearingDeg.f6(),
                    SurveyFields.QUADRANT_BEARING to SurveyCalculations.quadrantBearing(inv.initialBearingDeg),
                    SurveyFields.LATITUDE to lat2.f8(),
                    SurveyFields.LONGITUDE to lon2.f8()
                )
            )
        } else {
            val a = SurveyCalculations.Point("A", context.double("start_easting"), context.double("start_northing"), context.optionalDouble("start_elevation"))
            val b = SurveyCalculations.Point("B", context.double("end_easting"), context.double("end_northing"), context.optionalDouble("end_elevation"))
            val inv = SurveyCalculations.inversePlanar(a, b)
            SurveyCalculation(
                true,
                "${inv.horizontalDistance.f3()} m at ${inv.azimuthDeg.f4()}° (${inv.quadrantBearing})",
                mapOf(
                    SurveyFields.START_EASTING to a.easting.f4(), SurveyFields.START_NORTHING to a.northing.f4(),
                    SurveyFields.END_EASTING to b.easting.f4(), SurveyFields.END_NORTHING to b.northing.f4(),
                    SurveyFields.DELTA_EASTING to inv.deltaEasting.f4(), SurveyFields.DELTA_NORTHING to inv.deltaNorthing.f4(),
                    SurveyFields.HORIZONTAL_DISTANCE_M to inv.horizontalDistance.f4(), SurveyFields.AZIMUTH_DEG to inv.azimuthDeg.f6(),
                    SurveyFields.QUADRANT_BEARING to inv.quadrantBearing,
                    SurveyFields.ELEVATION_DIFFERENCE_M to inv.elevationDifference.f4(), SurveyFields.SLOPE_DISTANCE_M to inv.slopeDistance.f4(),
                    SurveyFields.SLOPE_PERCENT to inv.slopePercent.f4()
                )
            )
        }
    }
}

object As100ForwardCoordinateMethod : SurveyMethod(
    "survey.forward_coordinate", "Forward coordinate", "Compute a target coordinate from start, azimuth and distance.", "survey.coordinate"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val start = SurveyCalculations.Point("start", context.double("start_easting"), context.double("start_northing"))
        val az = context.double("azimuth_deg", "bearing_deg")
        val distance = context.double("horizontal_distance_m", "distance_m")
        val target = SurveyCalculations.forwardPlanar(start, az, distance)
        return SurveyCalculation(true, "E ${target.easting.f3()}, N ${target.northing.f3()}", mapOf(
            SurveyFields.START_EASTING to start.easting.f4(), SurveyFields.START_NORTHING to start.northing.f4(),
            SurveyFields.END_EASTING to target.easting.f4(), SurveyFields.END_NORTHING to target.northing.f4(),
            SurveyFields.HORIZONTAL_DISTANCE_M to distance.f4(), SurveyFields.AZIMUTH_DEG to SurveyCalculations.normalizeAzimuth(az).f6(),
            SurveyFields.QUADRANT_BEARING to SurveyCalculations.quadrantBearing(az)
        ))
    }
}

object As100OffsetPointMethod : SurveyMethod(
    "survey.offset_point", "Offset point", "Compute a point at chainage and signed right offset from a baseline.", "survey.offset"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val a = SurveyCalculations.Point("A", context.double("start_easting"), context.double("start_northing"))
        val b = SurveyCalculations.Point("B", context.double("end_easting"), context.double("end_northing"))
        val chainage = context.double("chainage_m")
        val offset = context.double("offset_right_m", "offset_m")
        val p = SurveyCalculations.offsetPoint(a, b, chainage, offset)
        return SurveyCalculation(true, "E ${p.easting.f3()}, N ${p.northing.f3()}", mapOf(
            SurveyFields.END_EASTING to p.easting.f4(), SurveyFields.END_NORTHING to p.northing.f4(),
            SurveyFields.CHAINAGE_M to chainage.f4(), SurveyFields.OFFSET_RIGHT_M to offset.f4()
        ))
    }
}

object As100ChainageOffsetMethod : SurveyMethod(
    "survey.chainage_offset", "Chainage and offset", "Project a point onto a baseline and return chainage plus signed right offset.", "survey.offset"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val a = SurveyCalculations.Point("A", context.double("start_easting"), context.double("start_northing"))
        val b = SurveyCalculations.Point("B", context.double("end_easting"), context.double("end_northing"))
        val p = SurveyCalculations.Point("P", context.double("point_easting"), context.double("point_northing"))
        val out = SurveyCalculations.chainageOffset(a, b, p)
        return SurveyCalculation(true, "Ch ${out.chainage.f3()} m; offset ${out.offsetRight.f3()} m right", mapOf(
            SurveyFields.CHAINAGE_M to out.chainage.f4(), SurveyFields.OFFSET_RIGHT_M to out.offsetRight.f4(),
            SurveyFields.END_EASTING to p.easting.f4(), SurveyFields.END_NORTHING to p.northing.f4()
        ))
    }
}

object As100TraverseMethod : SurveyMethod(
    "survey.traverse", "Traverse calculation", "Compute traverse coordinates, closure and optional Bowditch/transit adjustment.", "survey.traverse"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val start = SurveyCalculations.Point(context.value("start_point_id", "START"), context.double("start_easting"), context.double("start_northing"))
        val legs = SurveyCalculations.parseTraverseLegs(context.value("traverse_legs", "legs"))
        val closeE = context.optionalDouble("close_easting")
        val closeN = context.optionalDouble("close_northing")
        val knownClose = if (closeE != null && closeN != null) SurveyCalculations.Point("CLOSE", closeE, closeN) else null
        val minPrecision = context.optionalDouble("minimum_relative_precision")
        val result = SurveyCalculations.traverse(start, legs, knownClose, context.value("adjustment_mode", "none"), minPrecision)
        val closureText = result.linearMisclosure?.let {
            val rp = result.relativePrecision?.let { x -> if (x.isInfinite()) "∞" else "1:${x.f0()}" }.orEmpty()
            "closure ${it.f3()} m${if (rp.isNotBlank()) " ($rp)" else ""}"
        } ?: "open traverse"
        return SurveyCalculation(true, "${legs.size} legs; $closureText", mapOf(
            SurveyFields.TOTAL_DISTANCE_M to result.totalDistance.f4(),
            SurveyFields.END_EASTING to result.adjustedEndEasting.f4(), SurveyFields.END_NORTHING to result.adjustedEndNorthing.f4(),
            SurveyFields.MISCLOSURE_EASTING_M to result.misclosureEasting.f4(), SurveyFields.MISCLOSURE_NORTHING_M to result.misclosureNorthing.f4(),
            SurveyFields.LINEAR_MISCLOSURE_M to result.linearMisclosure.f4(), SurveyFields.RELATIVE_PRECISION to result.relativePrecision.f4(),
            SurveyFields.CLOSURE_PASS to result.closurePass?.toString().orEmpty(), SurveyFields.ADJUSTMENT_MODE to result.adjustmentMode,
            SurveyFields.POINT_COUNT to result.points.size.toString(), SurveyFields.POINTS_CSV to SurveyCalculations.traverseCsv(result)
        ))
    }
}

object As100AreaMethod : SurveyMethod(
    "survey.area", "Area from coordinates", "Calculate polygon area, perimeter and centroid using coordinate geometry.", "survey.area"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val points = SurveyCalculations.parsePoints(context.value("coordinates", "points"))
        val result = SurveyCalculations.polygon(points)
        return SurveyCalculation(true, "${result.area.f3()} m²; perimeter ${result.perimeter.f3()} m", mapOf(
            SurveyFields.AREA_SQ_M to result.area.f4(), SurveyFields.SIGNED_AREA_SQ_M to result.signedArea.f4(),
            SurveyFields.PERIMETER_M to result.perimeter.f4(), SurveyFields.CENTROID_EASTING to result.centroidEasting.f4(),
            SurveyFields.CENTROID_NORTHING to result.centroidNorthing.f4(), SurveyFields.POINT_COUNT to points.size.toString(),
            SurveyFields.POINTS_CSV to SurveyCalculations.pointsCsv(points)
        ))
    }
}

object As100LevelReduceMethod : SurveyMethod(
    "survey.level_reduce", "Reduce levelling book", "Reduce BS/IS/FS observations, calculate rise/fall, RL, arithmetic check and closure correction.", "survey.levelling"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val startRl = context.double("start_reduced_level_m", "start_rl_m")
        val observations = SurveyCalculations.parseLevelObservations(context.value("level_observations", "observations"))
        val close = context.optionalDouble("known_close_reduced_level_m", "close_rl_m")
        val distribute = context.boolean("distribute_closure", true)
        val result = SurveyCalculations.reduceLevelBook(startRl, observations, close, distribute)
        val main = if (result.finalReducedLevel != null) {
            "Final RL ${result.finalReducedLevel.f4()} m" + (result.closureError?.let { "; closure ${it.signF4()} m" } ?: "")
        } else result.message
        return SurveyCalculation(result.valid, main, mapOf(
            SurveyFields.START_RL_M to startRl.f4(), SurveyFields.FINAL_RL_M to result.finalReducedLevel.f4(),
            SurveyFields.ADJUSTED_FINAL_RL_M to result.adjustedFinalReducedLevel.f4(), SurveyFields.KNOWN_CLOSE_RL_M to close.f4(),
            SurveyFields.LEVEL_CLOSURE_ERROR_M to result.closureError.f4(), SurveyFields.TOTAL_DISTANCE_M to result.totalDistance.f3(),
            SurveyFields.SUM_BS_M to result.sumBacksight.f4(), SurveyFields.SUM_FS_M to result.sumForesight.f4(),
            SurveyFields.LEVEL_ARITHMETIC_CHECK_ERROR_M to result.arithmeticCheckError.f6(),
            SurveyFields.OBSERVATION_COUNT to observations.size.toString(), SurveyFields.LEVEL_BOOK_CSV to SurveyCalculations.levelCsv(result)
        ), if (result.valid) "" else result.message)
    }
}

object As100GradeMethod : SurveyMethod(
    "survey.grade", "Grade / rise-fall", "Convert rise/fall and horizontal distance into grade percent, angle and 1:n slope.", "survey.grade"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val rise = context.double("rise_m", "rise")
        val distance = context.double("horizontal_distance_m", "distance_m")
        val result = SurveyCalculations.grade(rise, distance)
        val grade = result.getValue("grade_percent")
        val angle = result.getValue("angle_deg")
        val oneInN = result.getValue("one_in_n")
        return SurveyCalculation(true, "${grade.signF3()}% (${angle.signF3()}°)", mapOf(
            SurveyFields.ELEVATION_DIFFERENCE_M to rise.f4(), SurveyFields.HORIZONTAL_DISTANCE_M to distance.f4(),
            SurveyFields.GRADE_PERCENT to grade.f4(), SurveyFields.SLOPE_ANGLE_DEG to angle.f4(), SurveyFields.ONE_IN_N to oneInN.f4()
        ))
    }
}

object As100GpsAverageMethod : SurveyMethod(
    "survey.gps_average", "Average GPS fixes", "Average repeated GPS positions and report scatter for field QC.", "survey.gps.average"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val fixesText = context["gps_fixes"].orEmpty().ifBlank {
            val lat = context.optionalDouble("gps_latitude", "latitude")
            val lon = context.optionalDouble("gps_longitude", "longitude")
            val acc = context.optionalDouble("gps_accuracy_m", "accuracy_m")
            if (lat != null && lon != null) "$lat,$lon,${acc ?: ""}" else ""
        }
        val fixes = SurveyCalculations.parseGpsFixes(fixesText)
        val result = SurveyCalculations.averageGps(fixes, context.boolean("accuracy_weighted", true))
        return SurveyCalculation(true, "${result.latitude.f8()}, ${result.longitude.f8()} (RMS ${result.rmsScatterM.f2()} m)", mapOf(
            SurveyFields.LATITUDE to result.latitude.f8(), SurveyFields.LONGITUDE to result.longitude.f8(),
            SurveyFields.GPS_SAMPLE_COUNT to result.sampleCount.toString(), SurveyFields.GPS_RMS_SCATTER_M to result.rmsScatterM.f3(),
            SurveyFields.GPS_MAX_SCATTER_M to result.maxScatterM.f3(), SurveyFields.GPS_MEAN_REPORTED_ACCURACY_M to result.meanReportedAccuracyM.f3(),
            SurveyFields.GPS_WEIGHTED to result.weighted.toString()
        ))
    }
}

object As100IntersectionMethod : SurveyMethod(
    "survey.intersection", "Bearing-bearing intersection", "Intersect two grid bearings from known stations and report geometry quality.", "survey.intersection"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val a = SurveyCalculations.Point("A", context.double("station_a_easting"), context.double("station_a_northing"))
        val b = SurveyCalculations.Point("B", context.double("station_b_easting"), context.double("station_b_northing"))
        val result = SurveyCalculations.bearingBearingIntersection(a, context.double("bearing_a_deg"), b, context.double("bearing_b_deg"))
        val main = if (result.valid) "E ${result.easting.f3()}, N ${result.northing.f3()}" else result.message
        return SurveyCalculation(result.valid, main, mapOf(
            SurveyFields.INTERSECTION_EASTING to result.easting.f4(), SurveyFields.INTERSECTION_NORTHING to result.northing.f4(),
            SurveyFields.CROSSING_ANGLE_DEG to result.crossingAngleDeg.f4()
        ), if (result.valid) "" else result.message)
    }
}

object As100SetoutMethod : SurveyMethod(
    "survey.setout", "Local-grid set-out", "Calculate bearing and distance from a current/grid station to a target coordinate.", "survey.setout"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val current = SurveyCalculations.Point("CURRENT", context.double("current_easting", "start_easting"), context.double("current_northing", "start_northing"))
        val target = SurveyCalculations.Point("TARGET", context.double("target_easting", "end_easting"), context.double("target_northing", "end_northing"))
        val inv = SurveyCalculations.inversePlanar(current, target)
        return SurveyCalculation(true, "Go ${inv.horizontalDistance.f3()} m at ${inv.azimuthDeg.f4()}°", mapOf(
            SurveyFields.HORIZONTAL_DISTANCE_M to inv.horizontalDistance.f4(), SurveyFields.AZIMUTH_DEG to inv.azimuthDeg.f6(),
            SurveyFields.QUADRANT_BEARING to inv.quadrantBearing, SurveyFields.DELTA_EASTING to inv.deltaEasting.f4(),
            SurveyFields.DELTA_NORTHING to inv.deltaNorthing.f4(), SurveyFields.END_EASTING to target.easting.f4(), SurveyFields.END_NORTHING to target.northing.f4()
        ))
    }
}

/** Dashboard method: the screen supplies the current persisted traverse book as normal traverse context. */
object As100TraverseBookMethod : SurveyMethod(
    "survey.traverse_book", "Traverse field book", "Persistent traverse field book with live closure QC and snapshot output.", "survey.traverse.book"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val base = As100TraverseMethod.calculateSafely(context)
        return base.copy(fields = base.fields + mapOf(
            SurveyFields.JOB_ID to context.value("survey_job_id", ""),
            SurveyFields.JOB_NAME to context.value("survey_job_name", "")
        ))
    }
}

/** Dashboard method: the screen supplies the current persisted levelling book as normal levelling context. */
object As100LevellingBookMethod : SurveyMethod(
    "survey.levelling_book", "Levelling field book", "Persistent BS/IS/FS levelling book with arithmetic and closure checks.", "survey.levelling.book"
) {
    override fun calculate(context: Map<String, String>): SurveyCalculation {
        val base = As100LevelReduceMethod.calculateSafely(context)
        return base.copy(fields = base.fields + mapOf(
            SurveyFields.JOB_ID to context.value("survey_job_id", ""),
            SurveyFields.JOB_NAME to context.value("survey_job_name", "")
        ))
    }
}

private fun Map<String, String>.value(vararg keys: String): String {
    keys.forEach { key ->
        val direct = this[key]
        if (!direct.isNullOrBlank()) return direct
        val input = this["input_$key"]
        if (!input.isNullOrBlank()) return input
    }
    return ""
}

private fun Map<String, String>.double(vararg keys: String): Double =
    optionalDouble(*keys) ?: error("Missing or invalid numeric input: ${keys.firstOrNull().orEmpty()}")

private fun Map<String, String>.optionalDouble(vararg keys: String): Double? =
    keys.asSequence().flatMap { sequenceOf(it, "input_$it") }.mapNotNull { this[it]?.trim()?.takeIf(String::isNotBlank)?.toDoubleOrNull() }.firstOrNull()

private fun Map<String, String>.boolean(key: String, default: Boolean): Boolean {
    val raw = value(key)
    if (raw.isBlank()) return default
    return raw.equals("true", true) || raw == "1" || raw.equals("yes", true) || raw.equals("y", true)
}

private fun metadataJson(id: String, calculation: SurveyCalculation): String {
    val fields = linkedMapOf<String, String>()
    fields["method_id"] = id
    fields["valid"] = calculation.valid.toString()
    fields["main_result"] = calculation.mainResult
    if (calculation.error.isNotBlank()) fields["error"] = calculation.error
    calculation.fields.forEach { (k, v) -> fields[k] = v }
    return fields.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "\"${k.jsonEscape()}\":\"${v.jsonEscape()}\"" }
}

private fun String.jsonEscape(): String = replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
private fun Double.f0(): String = String.format(Locale.US, "%.0f", this)
private fun Double.f2(): String = String.format(Locale.US, "%.2f", this)
private fun Double.f3(): String = String.format(Locale.US, "%.3f", this)
private fun Double.f4(): String = String.format(Locale.US, "%.4f", this)
private fun Double.f6(): String = String.format(Locale.US, "%.6f", this)
private fun Double.f8(): String = String.format(Locale.US, "%.8f", this)
private fun Double?.f3(): String = this?.takeIf { it.isFinite() }?.f3().orEmpty()
private fun Double?.f4(): String = this?.takeIf { it.isFinite() }?.f4().orEmpty()
private fun Double?.f6(): String = this?.takeIf { it.isFinite() }?.f6().orEmpty()
private fun Double?.f8(): String = this?.takeIf { it.isFinite() }?.f8().orEmpty()
private fun Double.signF3(): String = (if (this >= 0) "+" else "") + f3()
private fun Double.signF4(): String = (if (this >= 0) "+" else "") + f4()
