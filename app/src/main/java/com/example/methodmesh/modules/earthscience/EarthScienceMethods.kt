package com.example.methodmesh.modules.earthscience

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
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.abs

internal const val EARTH_SCIENCE_VERSION = "0.1.1"

internal fun Map<String, String>.earthValue(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

internal fun formatEarth(value: Double, decimals: Int = 6): String {
    if (!value.isFinite()) return ""
    val formatted = "% .${decimals}f".replace(" ", "").format(java.util.Locale.US, value)
    return formatted.trimEnd('0').trimEnd('.').ifBlank { "0" }
}

internal fun earthAudit(
    methodId: String,
    algorithm: String,
    inputs: Map<String, Any?> = emptyMap(),
    outputs: Map<String, Any?> = emptyMap(),
    extra: Map<String, Any?> = emptyMap()
): String = JSONObject().apply {
    put("method_id", methodId)
    put("version", EARTH_SCIENCE_VERSION)
    put("algorithm", algorithm)
    put("captured_time_iso", Instant.now().toString())
    put("network_used", false)
    put("inputs", JSONObject(inputs))
    put("outputs", JSONObject(outputs))
    extra.forEach { (key, value) -> put(key, value) }
}.toString()

abstract class EarthScienceMethod(
    final override val id: String,
    name: String,
    description: String,
    outputs: List<String>,
    methodType: MethodObjectType = MethodObjectType.Calculation
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = methodType,
        name = name,
        version = EARTH_SCIENCE_VERSION,
        description = description,
        outputs = outputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Earth science", "status" to "Development")
    )
    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    final override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    abstract fun calculate(settings: Map<String, String>): Map<String, String>

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val statusField = descriptor.outputs.firstOrNull { it.endsWith("_status") }
        val errorField = descriptor.outputs.firstOrNull { it.endsWith("_error") }
        val ok = statusField?.let { values[it] } == "succeeded"
        val entity = Entity(
            ArchitectureId("earth-science:${id}:${System.currentTimeMillis()}"),
            "EarthScienceResult",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.earthscience", id, EARTH_SCIENCE_VERSION)
        val observation = Observation(
            phenomenon = id,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok || errorField == null) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }

    fun executeValues(values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val request = request(
            action = id,
            context = (invocation?.asMap(id) ?: emptyMap()) + values,
            signals = emptyList(),
            inputs = emptyList()
        )
        return result(request, calculate(values), invocation)
    }

    fun resultFromValues(values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val request = request(
            action = id,
            context = (invocation?.asMap(id) ?: emptyMap()) + values,
            signals = emptyList(),
            inputs = emptyList()
        )
        return result(request, values, invocation)
    }
}

object GeodesyDistanceFields {
    const val STATUS = "geodesy_distance_status"
    const val RESULT = "geodesy_distance_result"
    const val DISTANCE_M = "geodesy_distance_m"
    const val INITIAL_BEARING_DEG = "geodesy_initial_bearing_deg"
    const val FINAL_BEARING_DEG = "geodesy_final_bearing_deg"
    const val ALGORITHM = "geodesy_distance_algorithm"
    const val AUDIT_JSON = "geodesy_distance_audit_json"
    const val ERROR = "geodesy_distance_error"
    val outputs = listOf(STATUS, RESULT, DISTANCE_M, INITIAL_BEARING_DEG, FINAL_BEARING_DEG, ALGORITHM, AUDIT_JSON, ERROR)
}

object As100GeodesyDistanceBearingMethod : EarthScienceMethod(
    id = "geodesy.distance_bearing",
    name = "Geodesic distance and bearing",
    description = "Calculate WGS84 ellipsoidal distance and initial/final bearings between two coordinates.",
    outputs = GeodesyDistanceFields.outputs
) {
    const val ID = "geodesy.distance_bearing"

    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val lat1 = settings.earthValue("latitude_1")?.toDoubleOrNull() ?: error("Latitude 1 is required.")
        val lon1 = settings.earthValue("longitude_1")?.toDoubleOrNull() ?: error("Longitude 1 is required.")
        val lat2 = settings.earthValue("latitude_2")?.toDoubleOrNull() ?: error("Latitude 2 is required.")
        val lon2 = settings.earthValue("longitude_2")?.toDoubleOrNull() ?: error("Longitude 2 is required.")
        val inverse = EarthScienceMath.inverseVincenty(lat1, lon1, lat2, lon2)
        val algorithm = if (inverse.converged) "Vincenty inverse on WGS84" else "Spherical fallback after Vincenty non-convergence"
        val resultText = "${formatEarth(inverse.distanceM, 2)} m · ${formatEarth(inverse.initialBearingDeg, 2)}° → ${formatEarth(inverse.finalBearingDeg, 2)}°"
        linkedMapOf(
            GeodesyDistanceFields.STATUS to "succeeded",
            GeodesyDistanceFields.RESULT to resultText,
            GeodesyDistanceFields.DISTANCE_M to formatEarth(inverse.distanceM, 3),
            GeodesyDistanceFields.INITIAL_BEARING_DEG to formatEarth(inverse.initialBearingDeg, 6),
            GeodesyDistanceFields.FINAL_BEARING_DEG to formatEarth(inverse.finalBearingDeg, 6),
            GeodesyDistanceFields.ALGORITHM to algorithm,
            GeodesyDistanceFields.AUDIT_JSON to earthAudit(ID, algorithm, mapOf("latitude_1" to lat1, "longitude_1" to lon1, "latitude_2" to lat2, "longitude_2" to lon2)),
            GeodesyDistanceFields.ERROR to ""
        )
    }.getOrElse { error -> failure(error.message ?: "Geodesic calculation failed.") }

    private fun failure(message: String) = linkedMapOf(
        GeodesyDistanceFields.STATUS to "failed", GeodesyDistanceFields.RESULT to "", GeodesyDistanceFields.DISTANCE_M to "",
        GeodesyDistanceFields.INITIAL_BEARING_DEG to "", GeodesyDistanceFields.FINAL_BEARING_DEG to "", GeodesyDistanceFields.ALGORITHM to "",
        GeodesyDistanceFields.AUDIT_JSON to earthAudit(ID, "Vincenty inverse on WGS84", extra = mapOf("error" to message)), GeodesyDistanceFields.ERROR to message
    )
}

object GeodesyDestinationFields {
    const val STATUS = "geodesy_destination_status"
    const val RESULT = "geodesy_destination_result"
    const val LATITUDE = "geodesy_destination_latitude"
    const val LONGITUDE = "geodesy_destination_longitude"
    const val FINAL_BEARING_DEG = "geodesy_destination_final_bearing_deg"
    const val AUDIT_JSON = "geodesy_destination_audit_json"
    const val ERROR = "geodesy_destination_error"
    val outputs = listOf(STATUS, RESULT, LATITUDE, LONGITUDE, FINAL_BEARING_DEG, AUDIT_JSON, ERROR)
}

object As100GeodesyDestinationMethod : EarthScienceMethod(
    id = "geodesy.destination",
    name = "Geodesic destination",
    description = "Project a WGS84 coordinate by initial bearing and distance.",
    outputs = GeodesyDestinationFields.outputs
) {
    const val ID = "geodesy.destination"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val lat = settings.earthValue("latitude")?.toDoubleOrNull() ?: error("Latitude is required.")
        val lon = settings.earthValue("longitude")?.toDoubleOrNull() ?: error("Longitude is required.")
        val bearing = settings.earthValue("bearing_deg")?.toDoubleOrNull() ?: error("Bearing is required.")
        val distance = settings.earthValue("distance_m")?.toDoubleOrNull() ?: error("Distance is required.")
        val destination = EarthScienceMath.directVincenty(lat, lon, bearing, distance)
        val resultText = "${formatEarth(destination.latitude, 6)}, ${formatEarth(destination.longitude, 6)} · final ${formatEarth(destination.finalBearingDeg, 2)}°"
        linkedMapOf(
            GeodesyDestinationFields.STATUS to "succeeded",
            GeodesyDestinationFields.RESULT to resultText,
            GeodesyDestinationFields.LATITUDE to formatEarth(destination.latitude, 8),
            GeodesyDestinationFields.LONGITUDE to formatEarth(destination.longitude, 8),
            GeodesyDestinationFields.FINAL_BEARING_DEG to formatEarth(destination.finalBearingDeg, 6),
            GeodesyDestinationFields.AUDIT_JSON to earthAudit(ID, "Vincenty direct on WGS84", mapOf("latitude" to lat, "longitude" to lon, "bearing_deg" to bearing, "distance_m" to distance), extra = mapOf("converged" to destination.converged)),
            GeodesyDestinationFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        GeodesyDestinationFields.STATUS to "failed", GeodesyDestinationFields.RESULT to "", GeodesyDestinationFields.LATITUDE to "", GeodesyDestinationFields.LONGITUDE to "",
        GeodesyDestinationFields.FINAL_BEARING_DEG to "", GeodesyDestinationFields.AUDIT_JSON to earthAudit(ID, "Vincenty direct on WGS84", extra = mapOf("error" to e.message)),
        GeodesyDestinationFields.ERROR to (e.message ?: "Destination calculation failed.")
    ) }
}

object GeodesyUtmFields {
    const val STATUS = "geodesy_utm_status"
    const val RESULT = "geodesy_utm_result"
    const val ZONE = "geodesy_utm_zone"
    const val HEMISPHERE = "geodesy_utm_hemisphere"
    const val EASTING_M = "geodesy_utm_easting_m"
    const val NORTHING_M = "geodesy_utm_northing_m"
    const val DATUM = "geodesy_utm_datum"
    const val AUDIT_JSON = "geodesy_utm_audit_json"
    const val ERROR = "geodesy_utm_error"
    val outputs = listOf(STATUS, RESULT, ZONE, HEMISPHERE, EASTING_M, NORTHING_M, DATUM, AUDIT_JSON, ERROR)
}

object As100GeodesyWgs84ToUtmMethod : EarthScienceMethod(
    id = "geodesy.wgs84_to_utm",
    name = "WGS84 to UTM",
    description = "Convert WGS84 decimal latitude/longitude to a UTM zone, easting and northing.",
    outputs = GeodesyUtmFields.outputs
) {
    const val ID = "geodesy.wgs84_to_utm"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val lat = settings.earthValue("latitude")?.toDoubleOrNull() ?: error("Latitude is required.")
        val lon = settings.earthValue("longitude")?.toDoubleOrNull() ?: error("Longitude is required.")
        val utm = EarthScienceMath.wgs84ToUtm(lat, lon)
        val resultText = "${utm.zone}${utm.hemisphere} ${formatEarth(utm.eastingM, 2)} E ${formatEarth(utm.northingM, 2)} N"
        linkedMapOf(
            GeodesyUtmFields.STATUS to "succeeded", GeodesyUtmFields.RESULT to resultText, GeodesyUtmFields.ZONE to utm.zone.toString(),
            GeodesyUtmFields.HEMISPHERE to utm.hemisphere, GeodesyUtmFields.EASTING_M to formatEarth(utm.eastingM, 3), GeodesyUtmFields.NORTHING_M to formatEarth(utm.northingM, 3),
            GeodesyUtmFields.DATUM to "WGS84", GeodesyUtmFields.AUDIT_JSON to earthAudit(ID, "Transverse Mercator / UTM on WGS84", mapOf("latitude" to lat, "longitude" to lon)), GeodesyUtmFields.ERROR to ""
        )
    }.getOrElse { e -> geodesyUtmFailure(ID, e.message ?: "UTM conversion failed.") }
}

private fun geodesyUtmFailure(methodId: String, message: String) = linkedMapOf(
    GeodesyUtmFields.STATUS to "failed", GeodesyUtmFields.RESULT to "", GeodesyUtmFields.ZONE to "", GeodesyUtmFields.HEMISPHERE to "",
    GeodesyUtmFields.EASTING_M to "", GeodesyUtmFields.NORTHING_M to "", GeodesyUtmFields.DATUM to "WGS84",
    GeodesyUtmFields.AUDIT_JSON to earthAudit(methodId, "Transverse Mercator / UTM on WGS84", extra = mapOf("error" to message)), GeodesyUtmFields.ERROR to message
)

object GeodesyWgs84Fields {
    const val STATUS = "geodesy_wgs84_status"
    const val RESULT = "geodesy_wgs84_result"
    const val LATITUDE = "geodesy_wgs84_latitude"
    const val LONGITUDE = "geodesy_wgs84_longitude"
    const val DATUM = "geodesy_wgs84_datum"
    const val AUDIT_JSON = "geodesy_wgs84_audit_json"
    const val ERROR = "geodesy_wgs84_error"
    val outputs = listOf(STATUS, RESULT, LATITUDE, LONGITUDE, DATUM, AUDIT_JSON, ERROR)
}

object As100GeodesyUtmToWgs84Method : EarthScienceMethod(
    id = "geodesy.utm_to_wgs84",
    name = "UTM to WGS84",
    description = "Convert UTM zone/easting/northing to WGS84 decimal latitude/longitude.",
    outputs = GeodesyWgs84Fields.outputs
) {
    const val ID = "geodesy.utm_to_wgs84"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val pasted = settings.earthValue("utm_text")?.trim().orEmpty()
        val parsed = if (pasted.isNotBlank()) parseUtmText(pasted) else null
        val zone = parsed?.zone ?: settings.earthValue("zone")?.toIntOrNull() ?: error("UTM zone is required (for example 30 from 30N).")
        val hemisphere = parsed?.hemisphere ?: settings.earthValue("hemisphere")?.uppercase() ?: "N"
        val easting = parsed?.eastingM ?: settings.earthValue("easting_m")?.toDoubleOrNull() ?: error("UTM easting is required.")
        val northing = parsed?.northingM ?: settings.earthValue("northing_m")?.toDoubleOrNull() ?: error("UTM northing is required.")
        val point = EarthScienceMath.utmToWgs84(zone, hemisphere, easting, northing)
        linkedMapOf(
            GeodesyWgs84Fields.STATUS to "succeeded", GeodesyWgs84Fields.RESULT to "${formatEarth(point.latitude, 6)}, ${formatEarth(point.longitude, 6)}",
            GeodesyWgs84Fields.LATITUDE to formatEarth(point.latitude, 8), GeodesyWgs84Fields.LONGITUDE to formatEarth(point.longitude, 8), GeodesyWgs84Fields.DATUM to "WGS84",
            GeodesyWgs84Fields.AUDIT_JSON to earthAudit(ID, "Inverse Transverse Mercator / UTM on WGS84", mapOf("zone" to zone, "hemisphere" to hemisphere, "easting_m" to easting, "northing_m" to northing)), GeodesyWgs84Fields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        GeodesyWgs84Fields.STATUS to "failed", GeodesyWgs84Fields.RESULT to "", GeodesyWgs84Fields.LATITUDE to "", GeodesyWgs84Fields.LONGITUDE to "", GeodesyWgs84Fields.DATUM to "WGS84",
        GeodesyWgs84Fields.AUDIT_JSON to earthAudit(ID, "Inverse Transverse Mercator / UTM on WGS84", extra = mapOf("error" to e.message)), GeodesyWgs84Fields.ERROR to (e.message ?: "UTM conversion failed.")
    ) }
}


private data class ParsedUtm(val zone: Int, val hemisphere: String, val eastingM: Double, val northingM: Double)

private fun parseUtmText(raw: String): ParsedUtm {
    val cleaned = raw.trim().uppercase().replace(",", " ").replace(Regex("\\s+"), " ")
    val match = Regex("^(\\d{1,2})\\s*([NS])\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(?:E|EASTING)?\\s+([0-9]+(?:\\.[0-9]+)?)\\s*(?:N|NORTHING)?$").matchEntire(cleaned)
        ?: error("Could not parse UTM text. Paste a line like: 30N 699316.2 E 5710164.4 N")
    return ParsedUtm(
        zone = match.groupValues[1].toInt(),
        hemisphere = match.groupValues[2],
        eastingM = match.groupValues[3].toDouble(),
        northingM = match.groupValues[4].toDouble()
    )
}

object GnssAverageFields {
    const val STATUS = "gnss_average_status"
    const val RESULT = "gnss_average_result"
    const val LATITUDE = "gnss_average_latitude"
    const val LONGITUDE = "gnss_average_longitude"
    const val ALTITUDE_M = "gnss_average_altitude_m"
    const val ACCEPTED_FIX_COUNT = "gnss_average_accepted_fix_count"
    const val REJECTED_FIX_COUNT = "gnss_average_rejected_fix_count"
    const val MEAN_REPORTED_ACCURACY_M = "gnss_average_mean_reported_accuracy_m"
    const val RMS_SPREAD_M = "gnss_average_rms_spread_m"
    const val MAX_SPREAD_M = "gnss_average_max_spread_m"
    const val WEIGHTING = "gnss_average_weighting"
    const val CAPTURED_TIME_ISO = "gnss_average_captured_time_iso"
    const val AUDIT_JSON = "gnss_average_audit_json"
    const val ERROR = "gnss_average_error"
    val outputs = listOf(STATUS, RESULT, LATITUDE, LONGITUDE, ALTITUDE_M, ACCEPTED_FIX_COUNT, REJECTED_FIX_COUNT, MEAN_REPORTED_ACCURACY_M, RMS_SPREAD_M, MAX_SPREAD_M, WEIGHTING, CAPTURED_TIME_ISO, AUDIT_JSON, ERROR)
}

object As100GnssAveragePositionMethod : EarthScienceMethod(
    id = "gnss.average_position",
    name = "Average GNSS position",
    description = "Collect repeated Android location fixes and calculate a weighted or unweighted mean position with spread diagnostics.",
    outputs = GnssAverageFields.outputs,
    methodType = MethodObjectType.DeviceService
) {
    const val ID = "gnss.average_position"

    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val fixesText = settings.earthValue("fixes_json") ?: error("Live GNSS capture is required unless fixes_json is supplied.")
        val weighting = settings.earthValue("weighting") ?: "inverse_variance"
        val fixes = parseFixes(fixesText)
        fromFixes(fixes, rejectedFixes = 0, weighting = weighting)
    }.getOrElse { e -> failure(e.message ?: "GNSS averaging failed.") }

    internal fun fromFixes(fixes: List<GnssFix>, rejectedFixes: Int, weighting: String): Map<String, String> = runCatching {
        val average = EarthScienceMath.averageGnss(fixes, weighting)
        val capturedTime = Instant.now().toString()
        val resultText = "${formatEarth(average.latitude, 6)}, ${formatEarth(average.longitude, 6)} · ${average.acceptedFixes} fixes · RMS ${formatEarth(average.rmsSpreadM, 2)} m"
        linkedMapOf(
            GnssAverageFields.STATUS to "succeeded", GnssAverageFields.RESULT to resultText,
            GnssAverageFields.LATITUDE to formatEarth(average.latitude, 8), GnssAverageFields.LONGITUDE to formatEarth(average.longitude, 8),
            GnssAverageFields.ALTITUDE_M to average.altitudeM?.let { formatEarth(it, 3) }.orEmpty(),
            GnssAverageFields.ACCEPTED_FIX_COUNT to average.acceptedFixes.toString(), GnssAverageFields.REJECTED_FIX_COUNT to rejectedFixes.toString(),
            GnssAverageFields.MEAN_REPORTED_ACCURACY_M to formatEarth(average.meanReportedAccuracyM, 3), GnssAverageFields.RMS_SPREAD_M to formatEarth(average.rmsSpreadM, 3),
            GnssAverageFields.MAX_SPREAD_M to formatEarth(average.maxSpreadM, 3), GnssAverageFields.WEIGHTING to average.weighting,
            GnssAverageFields.CAPTURED_TIME_ISO to capturedTime,
            GnssAverageFields.AUDIT_JSON to earthAudit(ID, "Unit-vector geographic mean; optional inverse squared Android accuracy weighting", outputs = mapOf("accepted_fix_count" to average.acceptedFixes, "rejected_fix_count" to rejectedFixes, "rms_spread_m" to average.rmsSpreadM), extra = mapOf("location_provider" to "Android FusedLocationProviderClient", "accuracy_weighting_is_heuristic" to true)),
            GnssAverageFields.ERROR to ""
        )
    }.getOrElse { e -> failure(e.message ?: "GNSS averaging failed.") }

    private fun parseFixes(text: String): List<GnssFix> {
        val array = JSONArray(text)
        return List(array.length()) { index ->
            val item = array.getJSONObject(index)
            GnssFix(
                latitude = item.getDouble("latitude"),
                longitude = item.getDouble("longitude"),
                accuracyM = item.optDouble("accuracy_m", 1.0),
                altitudeM = if (item.has("altitude_m") && !item.isNull("altitude_m")) item.getDouble("altitude_m") else null
            )
        }
    }

    private fun failure(message: String) = linkedMapOf(
        GnssAverageFields.STATUS to "failed", GnssAverageFields.RESULT to "", GnssAverageFields.LATITUDE to "", GnssAverageFields.LONGITUDE to "", GnssAverageFields.ALTITUDE_M to "",
        GnssAverageFields.ACCEPTED_FIX_COUNT to "0", GnssAverageFields.REJECTED_FIX_COUNT to "0", GnssAverageFields.MEAN_REPORTED_ACCURACY_M to "", GnssAverageFields.RMS_SPREAD_M to "", GnssAverageFields.MAX_SPREAD_M to "",
        GnssAverageFields.WEIGHTING to "", GnssAverageFields.CAPTURED_TIME_ISO to Instant.now().toString(), GnssAverageFields.AUDIT_JSON to earthAudit(ID, "GNSS averaging", extra = mapOf("error" to message)), GnssAverageFields.ERROR to message
    )
}

object StructuralPlaneFields {
    const val STATUS = "structural_plane_status"
    const val RESULT = "structural_plane_result"
    const val STRIKE_DEG = "structural_strike_deg"
    const val DIP_DEG = "structural_dip_deg"
    const val DIP_DIRECTION_DEG = "structural_dip_direction_deg"
    const val POLE_TREND_DEG = "structural_pole_trend_deg"
    const val POLE_PLUNGE_DEG = "structural_pole_plunge_deg"
    const val NORTH_REFERENCE = "structural_north_reference"
    const val SENSOR_SOURCE = "structural_sensor_source"
    const val MAGNETOMETER_ACCURACY = "structural_magnetometer_accuracy"
    const val ALONG_STRIKE_LEVEL_ERROR_DEG = "structural_along_strike_level_error_deg"
    const val CAPTURED_TIME_ISO = "structural_captured_time_iso"
    const val AUDIT_JSON = "structural_plane_audit_json"
    const val ERROR = "structural_plane_error"
    val outputs = listOf(STATUS, RESULT, STRIKE_DEG, DIP_DEG, DIP_DIRECTION_DEG, POLE_TREND_DEG, POLE_PLUNGE_DEG, NORTH_REFERENCE, SENSOR_SOURCE, MAGNETOMETER_ACCURACY, ALONG_STRIKE_LEVEL_ERROR_DEG, CAPTURED_TIME_ISO, AUDIT_JSON, ERROR)
}

object As100StructuralPlaneMethod : EarthScienceMethod(
    id = "structural.plane",
    name = "Structural plane",
    description = "Normalise strike/dip orientation, derive dip direction, and calculate the plane pole.",
    outputs = StructuralPlaneFields.outputs
) {
    const val ID = "structural.plane"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val strike = settings.earthValue("strike_deg")?.toDoubleOrNull() ?: error("Strike is required.")
        val dip = settings.earthValue("dip_deg")?.toDoubleOrNull() ?: error("Dip is required.")
        val convention = settings.earthValue("convention") ?: "right_hand_rule"
        val explicitDirection = settings.earthValue("dip_direction_deg")?.toDoubleOrNull()
        val plane = EarthScienceMath.normalisePlane(strike, dip, convention, explicitDirection)
        planeValues(ID, plane, northReference = "supplied", sensorSource = "none", magnetometerAccuracy = null, levelErrorDeg = null)
    }.getOrElse { e -> structuralPlaneFailure(ID, e.message ?: "Plane calculation failed.") }
}

object As100StructuralPlaneCaptureMethod : EarthScienceMethod(
    id = "structural.plane_capture",
    name = "Capture structural plane",
    description = "Development-stage strike/dip capture from phone magnetic heading, pitch and roll.",
    outputs = StructuralPlaneFields.outputs,
    methodType = MethodObjectType.DeviceService
) {
    const val ID = "structural.plane_capture"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val reference = settings.earthValue("strike_reference") ?: "magnetic"
        require(reference == "magnetic") { "v0.1 structural capture supports magnetic strike only." }
        val heading = settings.earthValue("heading_deg")?.toDoubleOrNull() ?: error("Live phone heading is required.")
        val roll = settings.earthValue("roll_deg")?.toDoubleOrNull() ?: error("Live phone roll is required.")
        val pitch = settings.earthValue("pitch_deg")?.toDoubleOrNull() ?: 0.0
        val accuracy = settings.earthValue("magnetometer_accuracy")?.toIntOrNull()
        val maxLevelError = settings.earthValue("max_level_error_deg")?.toDoubleOrNull()
        fromSensor(heading, pitch, roll, accuracy, maxLevelError)
    }.getOrElse { e -> structuralPlaneFailure(ID, e.message ?: "Structural plane capture failed.") }

    fun fromSensor(headingDeg: Double, pitchDeg: Double, rollDeg: Double, magnetometerAccuracy: Int?, maxLevelErrorDeg: Double? = null): Map<String, String> {
        if (maxLevelErrorDeg != null) require(abs(pitchDeg) <= maxLevelErrorDeg) { "Along-strike level error exceeds the configured limit." }
        val strike = EarthScienceMath.normaliseDegrees(headingDeg)
        val dip = abs(rollDeg).coerceIn(0.0, 90.0)
        val dipDirection = EarthScienceMath.normaliseDegrees(strike + if (rollDeg >= 0.0) 90.0 else -90.0)
        val plane = EarthScienceMath.normalisePlane(strike, dip, "explicit_dip_direction", dipDirection)
        return planeValues(ID, plane, northReference = "magnetic", sensorSource = "PhoneSensorRepository", magnetometerAccuracy = magnetometerAccuracy, levelErrorDeg = abs(pitchDeg), extra = mapOf("raw_heading_deg" to headingDeg, "raw_pitch_deg" to pitchDeg, "raw_roll_deg" to rollDeg, "max_level_error_deg" to maxLevelErrorDeg, "capture_geometry" to "phone top edge aligned with strike; roll magnitude interpreted as dip"))
    }
}

private fun planeValues(
    methodId: String,
    plane: StructuralPlane,
    northReference: String,
    sensorSource: String,
    magnetometerAccuracy: Int?,
    levelErrorDeg: Double?,
    extra: Map<String, Any?> = emptyMap()
): Map<String, String> {
    val captured = Instant.now().toString()
    val resultText = "${formatEarth(plane.strikeDeg, 1)}/${formatEarth(plane.dipDeg, 1)} · dip ${formatEarth(plane.dipDirectionDeg, 1)}° · pole ${formatEarth(plane.poleTrendDeg, 1)}/${formatEarth(plane.polePlungeDeg, 1)}"
    return linkedMapOf(
        StructuralPlaneFields.STATUS to "succeeded", StructuralPlaneFields.RESULT to resultText,
        StructuralPlaneFields.STRIKE_DEG to formatEarth(plane.strikeDeg, 4), StructuralPlaneFields.DIP_DEG to formatEarth(plane.dipDeg, 4),
        StructuralPlaneFields.DIP_DIRECTION_DEG to formatEarth(plane.dipDirectionDeg, 4), StructuralPlaneFields.POLE_TREND_DEG to formatEarth(plane.poleTrendDeg, 4), StructuralPlaneFields.POLE_PLUNGE_DEG to formatEarth(plane.polePlungeDeg, 4),
        StructuralPlaneFields.NORTH_REFERENCE to northReference, StructuralPlaneFields.SENSOR_SOURCE to sensorSource, StructuralPlaneFields.MAGNETOMETER_ACCURACY to magnetometerAccuracy?.toString().orEmpty(),
        StructuralPlaneFields.ALONG_STRIKE_LEVEL_ERROR_DEG to levelErrorDeg?.let { formatEarth(it, 3) }.orEmpty(), StructuralPlaneFields.CAPTURED_TIME_ISO to captured,
        StructuralPlaneFields.AUDIT_JSON to earthAudit(methodId, "Right-hand-rule structural orientation and plane pole", outputs = mapOf("strike_deg" to plane.strikeDeg, "dip_deg" to plane.dipDeg, "dip_direction_deg" to plane.dipDirectionDeg), extra = mapOf("north_reference" to northReference, "sensor_source" to sensorSource) + extra),
        StructuralPlaneFields.ERROR to ""
    )
}

private fun structuralPlaneFailure(methodId: String, message: String) = linkedMapOf(
    StructuralPlaneFields.STATUS to "failed", StructuralPlaneFields.RESULT to "", StructuralPlaneFields.STRIKE_DEG to "", StructuralPlaneFields.DIP_DEG to "", StructuralPlaneFields.DIP_DIRECTION_DEG to "",
    StructuralPlaneFields.POLE_TREND_DEG to "", StructuralPlaneFields.POLE_PLUNGE_DEG to "", StructuralPlaneFields.NORTH_REFERENCE to "", StructuralPlaneFields.SENSOR_SOURCE to "", StructuralPlaneFields.MAGNETOMETER_ACCURACY to "",
    StructuralPlaneFields.ALONG_STRIKE_LEVEL_ERROR_DEG to "", StructuralPlaneFields.CAPTURED_TIME_ISO to Instant.now().toString(), StructuralPlaneFields.AUDIT_JSON to earthAudit(methodId, "Structural plane", extra = mapOf("error" to message)), StructuralPlaneFields.ERROR to message
)

object StructuralLineFields {
    const val STATUS = "structural_line_status"
    const val RESULT = "structural_line_result"
    const val TREND_DEG = "structural_trend_deg"
    const val PLUNGE_DEG = "structural_plunge_deg"
    const val AUDIT_JSON = "structural_line_audit_json"
    const val ERROR = "structural_line_error"
    val outputs = listOf(STATUS, RESULT, TREND_DEG, PLUNGE_DEG, AUDIT_JSON, ERROR)
}

object As100StructuralLineMethod : EarthScienceMethod(
    id = "structural.line",
    name = "Structural line",
    description = "Normalise trend/plunge orientation to a downward-plunging geological line.",
    outputs = StructuralLineFields.outputs
) {
    const val ID = "structural.line"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val trend = settings.earthValue("trend_deg")?.toDoubleOrNull() ?: error("Trend is required.")
        val plunge = settings.earthValue("plunge_deg")?.toDoubleOrNull() ?: error("Plunge is required.")
        val line = EarthScienceMath.normaliseLine(trend, plunge)
        linkedMapOf(
            StructuralLineFields.STATUS to "succeeded", StructuralLineFields.RESULT to "${formatEarth(line.trendDeg, 1)}/${formatEarth(line.plungeDeg, 1)}",
            StructuralLineFields.TREND_DEG to formatEarth(line.trendDeg, 4), StructuralLineFields.PLUNGE_DEG to formatEarth(line.plungeDeg, 4),
            StructuralLineFields.AUDIT_JSON to earthAudit(ID, "Trend/plunge normalisation", mapOf("trend_deg" to trend, "plunge_deg" to plunge)), StructuralLineFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        StructuralLineFields.STATUS to "failed", StructuralLineFields.RESULT to "", StructuralLineFields.TREND_DEG to "", StructuralLineFields.PLUNGE_DEG to "",
        StructuralLineFields.AUDIT_JSON to earthAudit(ID, "Trend/plunge normalisation", extra = mapOf("error" to e.message)), StructuralLineFields.ERROR to (e.message ?: "Line calculation failed.")
    ) }
}

object StructuralIntersectionFields {
    const val STATUS = "structural_intersection_status"
    const val RESULT = "structural_intersection_result"
    const val TREND_DEG = "structural_intersection_trend_deg"
    const val PLUNGE_DEG = "structural_intersection_plunge_deg"
    const val AUDIT_JSON = "structural_intersection_audit_json"
    const val ERROR = "structural_intersection_error"
    val outputs = listOf(STATUS, RESULT, TREND_DEG, PLUNGE_DEG, AUDIT_JSON, ERROR)
}

object As100StructuralPlaneIntersectionMethod : EarthScienceMethod(
    id = "structural.plane_intersection",
    name = "Plane intersection",
    description = "Calculate the line of intersection of two right-hand-rule geological planes.",
    outputs = StructuralIntersectionFields.outputs
) {
    const val ID = "structural.plane_intersection"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val s1 = settings.earthValue("strike_1_deg")?.toDoubleOrNull() ?: error("Plane 1 strike is required.")
        val d1 = settings.earthValue("dip_1_deg")?.toDoubleOrNull() ?: error("Plane 1 dip is required.")
        val s2 = settings.earthValue("strike_2_deg")?.toDoubleOrNull() ?: error("Plane 2 strike is required.")
        val d2 = settings.earthValue("dip_2_deg")?.toDoubleOrNull() ?: error("Plane 2 dip is required.")
        val line = EarthScienceMath.planeIntersection(s1, d1, s2, d2)
        linkedMapOf(
            StructuralIntersectionFields.STATUS to "succeeded", StructuralIntersectionFields.RESULT to "${formatEarth(line.trendDeg, 1)}/${formatEarth(line.plungeDeg, 1)}",
            StructuralIntersectionFields.TREND_DEG to formatEarth(line.trendDeg, 4), StructuralIntersectionFields.PLUNGE_DEG to formatEarth(line.plungeDeg, 4),
            StructuralIntersectionFields.AUDIT_JSON to earthAudit(ID, "Cross product of plane pole vectors", mapOf("strike_1_deg" to s1, "dip_1_deg" to d1, "strike_2_deg" to s2, "dip_2_deg" to d2)), StructuralIntersectionFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        StructuralIntersectionFields.STATUS to "failed", StructuralIntersectionFields.RESULT to "", StructuralIntersectionFields.TREND_DEG to "", StructuralIntersectionFields.PLUNGE_DEG to "",
        StructuralIntersectionFields.AUDIT_JSON to earthAudit(ID, "Cross product of plane pole vectors", extra = mapOf("error" to e.message)), StructuralIntersectionFields.ERROR to (e.message ?: "Plane intersection failed.")
    ) }
}

object GeoTimeFields {
    const val STATUS = "geotime_status"
    const val RESULT = "geotime_result"
    const val AGE_MA = "geotime_age_ma"
    const val EON = "geotime_eon"
    const val ERA = "geotime_era"
    const val PERIOD = "geotime_period"
    const val SUBDIVISION = "geotime_subdivision"
    const val SOURCE = "geotime_source"
    const val SOURCE_VERSION = "geotime_source_version"
    const val AUDIT_JSON = "geotime_audit_json"
    const val ERROR = "geotime_error"
    val outputs = listOf(STATUS, RESULT, AGE_MA, EON, ERA, PERIOD, SUBDIVISION, SOURCE, SOURCE_VERSION, AUDIT_JSON, ERROR)
}

object As100GeoTimeLookupMethod : EarthScienceMethod(
    id = "geotime.lookup",
    name = "Geological time lookup",
    description = "Classify a numerical age in Ma using a bundled, versioned subset of the International Chronostratigraphic Chart hierarchy.",
    outputs = GeoTimeFields.outputs
) {
    const val ID = "geotime.lookup"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val age = settings.earthValue("age_ma")?.toDoubleOrNull() ?: error("Age in Ma is required.")
        val time = EarthScienceMath.geologicalTime(age)
        val hierarchy = listOf(time.eon, time.era, time.period, time.subdivision).filter { it.isNotBlank() }.joinToString(" · ")
        linkedMapOf(
            GeoTimeFields.STATUS to "succeeded", GeoTimeFields.RESULT to "$hierarchy · ${formatEarth(time.ageMa, 4)} Ma", GeoTimeFields.AGE_MA to formatEarth(time.ageMa, 6),
            GeoTimeFields.EON to time.eon, GeoTimeFields.ERA to time.era, GeoTimeFields.PERIOD to time.period, GeoTimeFields.SUBDIVISION to time.subdivision,
            GeoTimeFields.SOURCE to "International Commission on Stratigraphy", GeoTimeFields.SOURCE_VERSION to time.sourceVersion,
            GeoTimeFields.AUDIT_JSON to earthAudit(ID, "Versioned interval-boundary lookup", mapOf("age_ma" to age), extra = mapOf("source" to "ICS International Chronostratigraphic Chart", "source_version" to time.sourceVersion)), GeoTimeFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        GeoTimeFields.STATUS to "failed", GeoTimeFields.RESULT to "", GeoTimeFields.AGE_MA to "", GeoTimeFields.EON to "", GeoTimeFields.ERA to "", GeoTimeFields.PERIOD to "", GeoTimeFields.SUBDIVISION to "",
        GeoTimeFields.SOURCE to "International Commission on Stratigraphy", GeoTimeFields.SOURCE_VERSION to "2026/06", GeoTimeFields.AUDIT_JSON to earthAudit(ID, "Versioned interval-boundary lookup", extra = mapOf("error" to e.message)), GeoTimeFields.ERROR to (e.message ?: "Geological time lookup failed.")
    ) }
}

object SoilTextureFields {
    const val STATUS = "soil_texture_status"
    const val RESULT = "soil_texture_result"
    const val CLASS = "soil_texture_class"
    const val SAND_PCT = "soil_texture_sand_pct"
    const val SILT_PCT = "soil_texture_silt_pct"
    const val CLAY_PCT = "soil_texture_clay_pct"
    const val TOTAL_PCT = "soil_texture_total_pct"
    const val SOURCE = "soil_texture_source"
    const val AUDIT_JSON = "soil_texture_audit_json"
    const val ERROR = "soil_texture_error"
    val outputs = listOf(STATUS, RESULT, CLASS, SAND_PCT, SILT_PCT, CLAY_PCT, TOTAL_PCT, SOURCE, AUDIT_JSON, ERROR)
}

object As100SoilTextureMethod : EarthScienceMethod(
    id = "soil.texture_usda",
    name = "USDA soil texture",
    description = "Classify sand, silt and clay percentages into the USDA textural classes.",
    outputs = SoilTextureFields.outputs
) {
    const val ID = "soil.texture_usda"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val sand = settings.earthValue("sand_pct")?.toDoubleOrNull() ?: error("Sand percentage is required.")
        val silt = settings.earthValue("silt_pct")?.toDoubleOrNull() ?: error("Silt percentage is required.")
        val clay = settings.earthValue("clay_pct")?.toDoubleOrNull() ?: error("Clay percentage is required.")
        val tolerance = settings.earthValue("sum_tolerance_pct")?.toDoubleOrNull() ?: 1.0
        val total = sand + silt + clay
        require(sand >= 0 && silt >= 0 && clay >= 0) { "Percentages cannot be negative." }
        require(abs(total - 100.0) <= tolerance) { "Sand + silt + clay must total 100% within the configured tolerance." }
        val texture = EarthScienceMath.classifySoilTexture(sand, silt, clay, tolerance)
        linkedMapOf(
            SoilTextureFields.STATUS to "succeeded", SoilTextureFields.RESULT to "$texture · ${formatEarth(sand, 1)}/${formatEarth(silt, 1)}/${formatEarth(clay, 1)}% sand/silt/clay", SoilTextureFields.CLASS to texture,
            SoilTextureFields.SAND_PCT to formatEarth(sand, 3), SoilTextureFields.SILT_PCT to formatEarth(silt, 3), SoilTextureFields.CLAY_PCT to formatEarth(clay, 3), SoilTextureFields.TOTAL_PCT to formatEarth(total, 3),
            SoilTextureFields.SOURCE to "USDA-NRCS textural classes", SoilTextureFields.AUDIT_JSON to earthAudit(ID, "USDA textural triangle rule set", mapOf("sand_pct" to sand, "silt_pct" to silt, "clay_pct" to clay, "sum_tolerance_pct" to tolerance)), SoilTextureFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        SoilTextureFields.STATUS to "failed", SoilTextureFields.RESULT to "", SoilTextureFields.CLASS to "", SoilTextureFields.SAND_PCT to "", SoilTextureFields.SILT_PCT to "", SoilTextureFields.CLAY_PCT to "", SoilTextureFields.TOTAL_PCT to "",
        SoilTextureFields.SOURCE to "USDA-NRCS textural classes", SoilTextureFields.AUDIT_JSON to earthAudit(ID, "USDA textural triangle rule set", extra = mapOf("error" to e.message)), SoilTextureFields.ERROR to (e.message ?: "Soil texture classification failed.")
    ) }
}

object SedimentGrainFields {
    const val STATUS = "sediment_grain_status"
    const val RESULT = "sediment_grain_result"
    const val DIAMETER_MM = "sediment_grain_diameter_mm"
    const val PHI = "sediment_grain_phi"
    const val WENTWORTH_CLASS = "sediment_grain_wentworth_class"
    const val BROAD_CLASS = "sediment_grain_broad_class"
    const val SOURCE = "sediment_grain_source"
    const val AUDIT_JSON = "sediment_grain_audit_json"
    const val ERROR = "sediment_grain_error"
    val outputs = listOf(STATUS, RESULT, DIAMETER_MM, PHI, WENTWORTH_CLASS, BROAD_CLASS, SOURCE, AUDIT_JSON, ERROR)
}

object As100SedimentGrainSizeMethod : EarthScienceMethod(
    id = "sediment.grain_size",
    name = "Sediment grain size",
    description = "Convert diameter and phi and classify a grain using Wentworth size classes.",
    outputs = SedimentGrainFields.outputs
) {
    const val ID = "sediment.grain_size"
    override fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val mode = settings.earthValue("input_mode") ?: "diameter_mm"
        val grain = EarthScienceMath.grainSize(
            inputMode = mode,
            diameterMm = settings.earthValue("diameter_mm")?.toDoubleOrNull(),
            phi = settings.earthValue("phi")?.toDoubleOrNull()
        )
        linkedMapOf(
            SedimentGrainFields.STATUS to "succeeded", SedimentGrainFields.RESULT to "${grain.wentworthClass} · ${formatEarth(grain.diameterMm, 6)} mm · φ ${formatEarth(grain.phi, 3)}",
            SedimentGrainFields.DIAMETER_MM to formatEarth(grain.diameterMm, 9), SedimentGrainFields.PHI to formatEarth(grain.phi, 6), SedimentGrainFields.WENTWORTH_CLASS to grain.wentworthClass, SedimentGrainFields.BROAD_CLASS to grain.broadClass,
            SedimentGrainFields.SOURCE to "Wentworth grain-size scale", SedimentGrainFields.AUDIT_JSON to earthAudit(ID, "phi = -log2(diameter_mm)", mapOf("input_mode" to mode)), SedimentGrainFields.ERROR to ""
        )
    }.getOrElse { e -> linkedMapOf(
        SedimentGrainFields.STATUS to "failed", SedimentGrainFields.RESULT to "", SedimentGrainFields.DIAMETER_MM to "", SedimentGrainFields.PHI to "", SedimentGrainFields.WENTWORTH_CLASS to "", SedimentGrainFields.BROAD_CLASS to "",
        SedimentGrainFields.SOURCE to "Wentworth grain-size scale", SedimentGrainFields.AUDIT_JSON to earthAudit(ID, "phi = -log2(diameter_mm)", extra = mapOf("error" to e.message)), SedimentGrainFields.ERROR to (e.message ?: "Grain-size classification failed.")
    ) }
}
