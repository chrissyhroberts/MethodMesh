package com.example.methodmesh.modules.aviation

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object AviationDashboardFields {
    const val STATUS = "aviation_dashboard_status"
    const val VALUE = "aviation_dashboard_value"
    const val SNAPSHOT_TIME_ISO = "aviation_dashboard_snapshot_time_iso"
    const val QUERY_LATITUDE = "aviation_dashboard_latitude"
    const val QUERY_LONGITUDE = "aviation_dashboard_longitude"
    const val LOCATION_SOURCE = "aviation_dashboard_location_source"
    const val LOCATION_ACCURACY_M = "aviation_dashboard_location_accuracy_m"
    const val GPS_ALTITUDE_FT = "aviation_dashboard_gps_altitude_ft"
    const val GROUND_SPEED_KT = "aviation_dashboard_ground_speed_kt"
    const val TRACK_DEG = "aviation_dashboard_track_deg"
    const val AIRFIELD_COUNT = "aviation_dashboard_airfield_count"
    const val SELECTED_AIRFIELD_IDENT = "aviation_dashboard_airfield_ident"
    const val SELECTED_AIRFIELD_NAME = "aviation_dashboard_airfield_name"
    const val SELECTED_AIRFIELD_DISTANCE_NM = "aviation_dashboard_airfield_distance_nm"
    const val SELECTED_AIRFIELD_BEARING_DEG = "aviation_dashboard_airfield_bearing_deg"
    const val SELECTED_AIRFIELD_ELEVATION_FT = "aviation_dashboard_airfield_elevation_ft"
    const val SELECTED_AIRFIELD_MUNICIPALITY = "aviation_dashboard_airfield_municipality"
    const val AIRFIELDS_JSON = "aviation_dashboard_airfields_json"
    const val SELECTED_RUNWAY_IDENT = "aviation_dashboard_runway_ident"
    const val SELECTED_RUNWAY_HEADING_DEG = "aviation_dashboard_runway_heading_deg"
    const val SELECTED_RUNWAY_LENGTH_FT = "aviation_dashboard_runway_length_ft"
    const val SELECTED_RUNWAY_SURFACE = "aviation_dashboard_runway_surface"
    const val RUNWAYS_JSON = "aviation_dashboard_runways_json"
    const val WIND_VALUE = "aviation_dashboard_wind_value"
    const val HEADWIND_KT = "aviation_dashboard_headwind_kt"
    const val TAILWIND_KT = "aviation_dashboard_tailwind_kt"
    const val CROSSWIND_KT = "aviation_dashboard_crosswind_kt"
    const val CROSSWIND_SIDE = "aviation_dashboard_crosswind_side"
    const val ALTITUDE_VALUE = "aviation_dashboard_altitude_value"
    const val PRESSURE_ALTITUDE_FT = "aviation_dashboard_pressure_altitude_ft"
    const val DENSITY_ALTITUDE_FT = "aviation_dashboard_density_altitude_ft"
    const val AIRFIELD_CATALOG_UPDATED_ISO = "aviation_dashboard_airfield_catalog_updated_iso"
    const val RUNWAY_CATALOG_UPDATED_ISO = "aviation_dashboard_runway_catalog_updated_iso"
    const val DATA_SOURCE = "aviation_dashboard_data_source"
    const val WARNING = "aviation_dashboard_warning"
    const val AUDIT_JSON = "aviation_dashboard_audit_json"
    const val ERROR = "aviation_dashboard_error"

    val outputs = listOf(
        VALUE, SNAPSHOT_TIME_ISO,
        QUERY_LATITUDE, QUERY_LONGITUDE, LOCATION_SOURCE, LOCATION_ACCURACY_M, GPS_ALTITUDE_FT, GROUND_SPEED_KT, TRACK_DEG,
        AIRFIELD_COUNT, SELECTED_AIRFIELD_IDENT, SELECTED_AIRFIELD_NAME, SELECTED_AIRFIELD_DISTANCE_NM,
        SELECTED_AIRFIELD_BEARING_DEG, SELECTED_AIRFIELD_ELEVATION_FT, SELECTED_AIRFIELD_MUNICIPALITY, AIRFIELDS_JSON,
        SELECTED_RUNWAY_IDENT, SELECTED_RUNWAY_HEADING_DEG, SELECTED_RUNWAY_LENGTH_FT, SELECTED_RUNWAY_SURFACE, RUNWAYS_JSON,
        WIND_VALUE, HEADWIND_KT, TAILWIND_KT, CROSSWIND_KT, CROSSWIND_SIDE,
        ALTITUDE_VALUE, PRESSURE_ALTITUDE_FT, DENSITY_ALTITUDE_FT,
        AIRFIELD_CATALOG_UPDATED_ISO, RUNWAY_CATALOG_UPDATED_ISO, DATA_SOURCE, WARNING, STATUS, AUDIT_JSON, ERROR
    )
}

data class AviationDashboardTelemetry(
    val accuracyM: Double? = null,
    val gpsAltitudeFt: Double? = null,
    val groundSpeedKt: Double? = null,
    val trackDeg: Double? = null
)

object As100AviationDashboardMethod : As100Method {
    const val ID = "aviation.dashboard"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Aviation dashboard snapshot")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Aviation dashboard",
        version = AVIATION_VERSION,
        description = "Create a refreshable local aviation snapshot from GPS, cached airfield/runway data and optional current conditions.",
        inputs = listOf(
            "latitude", "longitude", "location_accuracy_m", "gps_altitude_ft", "ground_speed_kt", "track_deg",
            "radius_nm", "airfield_types", "scheduled_only", "max_results", "refresh_policy",
            "airfield_ident", "runway_ident", "runway_heading_deg", "conditions_enabled", "wind_direction_deg", "wind_speed_kt",
            "include_gust", "gust_speed_kt", "qnh_unit", "qnh_value", "oat_c"
        ),
        outputs = AviationDashboardFields.outputs,
        graphOutputs = listOf("aviation.dashboard"),
        parameters = mapOf("category" to "Aviation", "status" to "Development")
    )

    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(
            request,
            failure("Aviation dashboard execution requires the Android capability boundary so GPS and local aviation catalogues can be accessed."),
            InvocationContext.from(request.context)
        )

    fun values(
        search: AviationAirfieldRepository.SearchResult,
        runwayResult: AviationAirfieldRepository.RunwayResult?,
        selectedAirfield: AviationAirfieldRepository.Airfield?,
        selectedRunway: AviationAirfieldRepository.RunwayEnd?,
        latitude: Double,
        longitude: Double,
        locationSource: String,
        telemetry: AviationDashboardTelemetry,
        settings: Map<String, String>
    ): Map<String, String> {
        val conditionsEnabled = settings.aviationValue("conditions_enabled")?.toBooleanStrictOrNull() ?: false
        val runwayHeading = settings.aviationValue("runway_heading_deg")?.toDoubleOrNull() ?: selectedRunway?.headingDeg
        val windDirection = settings.aviationValue("wind_direction_deg")?.toDoubleOrNull()
        val windSpeed = settings.aviationValue("wind_speed_kt")?.toDoubleOrNull()
        val gust = settings.aviationValue("gust_speed_kt")?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val includeGust = settings.aviationValue("include_gust")?.toBooleanStrictOrNull() ?: (gust != null)

        val windValues = if (conditionsEnabled && runwayHeading != null && windDirection != null && windSpeed != null) {
            As100RunwayWindMethod.generate(
                mapOf(
                    "runway_heading_deg" to runwayHeading.toString(),
                    "wind_direction_deg" to windDirection.toString(),
                    "wind_speed_kt" to windSpeed.toString(),
                    "include_gust" to includeGust.toString(),
                    "gust_speed_kt" to if (includeGust) gust?.toString().orEmpty() else ""
                )
            ).takeIf { it[RunwayWindFields.STATUS] == "succeeded" }
        } else null

        val qnhUnit = settings.aviationValue("qnh_unit") ?: "hpa"
        val qnhValue = settings.aviationValue("qnh_value")?.toDoubleOrNull()
        val oatC = settings.aviationValue("oat_c")?.toDoubleOrNull()
        val altitudeValues = if (conditionsEnabled && selectedAirfield?.elevationFt != null && qnhValue != null && oatC != null) {
            As100AviationAltitudeMethod.generate(
                mapOf(
                    "field_elevation_ft" to selectedAirfield.elevationFt.toString(),
                    "qnh_unit" to qnhUnit,
                    "qnh_value" to qnhValue.toString(),
                    "oat_c" to oatC.toString()
                )
            ).takeIf { it[AltitudeFields.STATUS] == "succeeded" }
        } else null

        val airfieldsJson = JSONArray().apply {
            search.airfields.forEach { airfield ->
                put(JSONObject().apply {
                    put("ident", airfield.ident)
                    put("name", airfield.name)
                    put("type", airfield.type)
                    put("distance_nm", airfield.distanceNm)
                    put("bearing_deg", airfield.bearingDeg)
                    put("elevation_ft", airfield.elevationFt ?: JSONObject.NULL)
                    put("municipality", airfield.municipality)
                    put("latitude", airfield.latitude)
                    put("longitude", airfield.longitude)
                })
            }
        }

        val runwaysJson = JSONArray().apply {
            runwayResult?.runwayEnds.orEmpty().forEach { runway ->
                put(JSONObject().apply {
                    put("ident", runway.ident)
                    put("reciprocal_ident", runway.reciprocalIdent)
                    put("heading_deg", runway.headingDeg)
                    put("heading_source", runway.headingSource)
                    put("length_ft", runway.lengthFt ?: JSONObject.NULL)
                    put("width_ft", runway.widthFt ?: JSONObject.NULL)
                    put("surface", runway.surface)
                    put("lighted", runway.lighted)
                })
            }
        }

        val warnings = listOf(search.warning, runwayResult?.warning.orEmpty())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" ")

        val headline = selectedAirfield?.let { airfield ->
            val ident = airfield.ident.ifBlank { airfield.gpsCode.ifBlank { airfield.iataCode } }
            buildString {
                append(ident.ifBlank { "Nearest airfield" })
                append(" · ")
                append(AviationCalculations.format(airfield.distanceNm, 1))
                append(" NM · ")
                append(AviationCalculations.format(airfield.bearingDeg, 0))
                append("°")
                if (selectedRunway != null) append(" · RWY ${selectedRunway.ident}")
            }
        } ?: "No matching airfield found"

        val snapshotTime = Instant.now().toString()
        val audit = JSONObject().apply {
            put("snapshot_time_iso", snapshotTime)
            put("query_latitude", latitude)
            put("query_longitude", longitude)
            put("location_source", locationSource)
            put("location_accuracy_m", telemetry.accuracyM ?: JSONObject.NULL)
            put("gps_altitude_ft", telemetry.gpsAltitudeFt ?: JSONObject.NULL)
            put("ground_speed_kt", telemetry.groundSpeedKt ?: JSONObject.NULL)
            put("track_deg", telemetry.trackDeg ?: JSONObject.NULL)
            put("selected_airfield", selectedAirfield?.let { JSONObject().apply {
                put("ident", it.ident); put("name", it.name); put("distance_nm", it.distanceNm); put("bearing_deg", it.bearingDeg)
                put("elevation_ft", it.elevationFt ?: JSONObject.NULL); put("municipality", it.municipality)
            } } ?: JSONObject.NULL)
            put("selected_runway", selectedRunway?.let { JSONObject().apply {
                put("ident", it.ident); put("heading_deg", it.headingDeg); put("heading_source", it.headingSource)
                put("length_ft", it.lengthFt ?: JSONObject.NULL); put("surface", it.surface)
            } } ?: JSONObject.NULL)
            put("conditions_enabled", conditionsEnabled)
            put("wind", windValues?.let { JSONObject(it[RunwayWindFields.AUDIT_JSON].orEmpty().ifBlank { "{}" }) } ?: JSONObject.NULL)
            put("altitude", altitudeValues?.let { JSONObject(it[AltitudeFields.AUDIT_JSON].orEmpty().ifBlank { "{}" }) } ?: JSONObject.NULL)
            put("airfield_catalog_updated_iso", search.catalogUpdatedIso)
            put("runway_catalog_updated_iso", runwayResult?.catalogUpdatedIso ?: JSONObject.NULL)
            put("airfield_source", search.sourceUrl)
            put("runway_source", runwayResult?.sourceUrl ?: JSONObject.NULL)
            put("warning", warnings)
        }

        return linkedMapOf(
            AviationDashboardFields.VALUE to headline,
            AviationDashboardFields.SNAPSHOT_TIME_ISO to snapshotTime,
            AviationDashboardFields.QUERY_LATITUDE to latitude.toString(),
            AviationDashboardFields.QUERY_LONGITUDE to longitude.toString(),
            AviationDashboardFields.LOCATION_SOURCE to locationSource,
            AviationDashboardFields.LOCATION_ACCURACY_M to telemetry.accuracyM?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationDashboardFields.GPS_ALTITUDE_FT to telemetry.gpsAltitudeFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationDashboardFields.GROUND_SPEED_KT to telemetry.groundSpeedKt?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationDashboardFields.TRACK_DEG to telemetry.trackDeg?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationDashboardFields.AIRFIELD_COUNT to search.airfields.size.toString(),
            AviationDashboardFields.SELECTED_AIRFIELD_IDENT to selectedAirfield?.ident.orEmpty(),
            AviationDashboardFields.SELECTED_AIRFIELD_NAME to selectedAirfield?.name.orEmpty(),
            AviationDashboardFields.SELECTED_AIRFIELD_DISTANCE_NM to selectedAirfield?.distanceNm?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            AviationDashboardFields.SELECTED_AIRFIELD_BEARING_DEG to selectedAirfield?.bearingDeg?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            AviationDashboardFields.SELECTED_AIRFIELD_ELEVATION_FT to selectedAirfield?.elevationFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationDashboardFields.SELECTED_AIRFIELD_MUNICIPALITY to selectedAirfield?.municipality.orEmpty(),
            AviationDashboardFields.AIRFIELDS_JSON to airfieldsJson.toString(),
            AviationDashboardFields.SELECTED_RUNWAY_IDENT to selectedRunway?.ident.orEmpty(),
            AviationDashboardFields.SELECTED_RUNWAY_HEADING_DEG to selectedRunway?.headingDeg?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationDashboardFields.SELECTED_RUNWAY_LENGTH_FT to selectedRunway?.lengthFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            AviationDashboardFields.SELECTED_RUNWAY_SURFACE to selectedRunway?.surface.orEmpty(),
            AviationDashboardFields.RUNWAYS_JSON to runwaysJson.toString(),
            AviationDashboardFields.WIND_VALUE to windValues?.get(RunwayWindFields.VALUE).orEmpty(),
            AviationDashboardFields.HEADWIND_KT to windValues?.get(RunwayWindFields.HEADWIND_KT).orEmpty(),
            AviationDashboardFields.TAILWIND_KT to windValues?.get(RunwayWindFields.TAILWIND_KT).orEmpty(),
            AviationDashboardFields.CROSSWIND_KT to windValues?.get(RunwayWindFields.CROSSWIND_KT).orEmpty(),
            AviationDashboardFields.CROSSWIND_SIDE to windValues?.get(RunwayWindFields.CROSSWIND_SIDE).orEmpty(),
            AviationDashboardFields.ALTITUDE_VALUE to altitudeValues?.get(AltitudeFields.VALUE).orEmpty(),
            AviationDashboardFields.PRESSURE_ALTITUDE_FT to altitudeValues?.get(AltitudeFields.PRESSURE_ALTITUDE_FT).orEmpty(),
            AviationDashboardFields.DENSITY_ALTITUDE_FT to altitudeValues?.get(AltitudeFields.DENSITY_ALTITUDE_FT).orEmpty(),
            AviationDashboardFields.AIRFIELD_CATALOG_UPDATED_ISO to search.catalogUpdatedIso,
            AviationDashboardFields.RUNWAY_CATALOG_UPDATED_ISO to runwayResult?.catalogUpdatedIso.orEmpty(),
            AviationDashboardFields.DATA_SOURCE to search.sourceUrl,
            AviationDashboardFields.WARNING to warnings,
            AviationDashboardFields.STATUS to "succeeded",
            AviationDashboardFields.AUDIT_JSON to audit.toString(),
            AviationDashboardFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, AviationDashboardFields.STATUS, AviationDashboardFields.ERROR)

    fun failure(error: String): Map<String, String> = linkedMapOf(
        AviationDashboardFields.VALUE to "",
        AviationDashboardFields.STATUS to "failed",
        AviationDashboardFields.WARNING to "",
        AviationDashboardFields.AIRFIELDS_JSON to "[]",
        AviationDashboardFields.RUNWAYS_JSON to "[]",
        AviationDashboardFields.AUDIT_JSON to "{}",
        AviationDashboardFields.ERROR to error
    )
}
