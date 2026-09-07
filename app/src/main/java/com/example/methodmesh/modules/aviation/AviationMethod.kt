package com.example.methodmesh.modules.aviation

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

internal const val AVIATION_VERSION = "0.3.2"

internal object AviationExecutionSupport {
    fun complete(
        method: As100Method,
        version: String,
        phenomenon: String,
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?,
        statusField: String,
        errorField: String
    ): ExecutionResult {
        val ok = values[statusField] == "succeeded"
        val entity = Entity(
            ArchitectureId("aviation:${method.id}:${System.currentTimeMillis()}"),
            "AviationResult",
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.aviation", method.id, version)
        val observation = Observation(
            phenomenon = phenomenon,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = method.id,
            method = method.ref,
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
            diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }
}

internal fun Map<String, String>.aviationValue(vararg keys: String): String? {
    for (key in keys) {
        val candidates = listOf(key, "input_$key", "previous_$key")
        for (candidate in candidates) {
            this[candidate]?.takeIf { it.isNotBlank() }?.let { return it }
        }
    }
    return null
}

object NearbyAirfieldFields {
    const val STATUS = "airfield_nearby_status"
    const val VALUE = "airfield_nearby_value"
    const val COUNT = "airfield_nearby_count"
    const val NEAREST_IDENT = "airfield_nearest_ident"
    const val NEAREST_NAME = "airfield_nearest_name"
    const val NEAREST_DISTANCE_NM = "airfield_nearest_distance_nm"
    const val NEAREST_BEARING_DEG = "airfield_nearest_bearing_deg"
    const val NEAREST_LATITUDE = "airfield_nearest_latitude"
    const val NEAREST_LONGITUDE = "airfield_nearest_longitude"
    const val NEAREST_ELEVATION_FT = "airfield_nearest_elevation_ft"
    const val AIRFIELDS_JSON = "airfield_nearby_audit_json"
    const val QUERY_LATITUDE = "airfield_query_latitude"
    const val QUERY_LONGITUDE = "airfield_query_longitude"
    const val LOCATION_SOURCE = "airfield_location_source"
    const val CATALOG_UPDATED_ISO = "airfield_catalog_updated_iso"
    const val DATA_SOURCE = "airfield_data_source"
    const val WARNING = "airfield_warning"
    const val ERROR = "airfield_nearby_error"

    val outputs = listOf(
        VALUE,
        COUNT,
        NEAREST_IDENT,
        NEAREST_NAME,
        NEAREST_DISTANCE_NM,
        NEAREST_BEARING_DEG,
        NEAREST_LATITUDE,
        NEAREST_LONGITUDE,
        NEAREST_ELEVATION_FT,
        STATUS,
        QUERY_LATITUDE,
        QUERY_LONGITUDE,
        LOCATION_SOURCE,
        CATALOG_UPDATED_ISO,
        DATA_SOURCE,
        WARNING,
        AIRFIELDS_JSON,
        ERROR
    )
}

object As100NearbyAirfieldsMethod : As100Method {
    const val ID = "aviation.airfield.nearby"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Nearby airfield lookup")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "Nearby airfields",
        version = AVIATION_VERSION,
        description = "Find nearby aerodromes from GPS or supplied coordinates using a cached OurAirports catalogue.",
        inputs = listOf("latitude", "longitude", "radius_nm", "airfield_types", "scheduled_only", "max_results", "refresh_policy"),
        outputs = NearbyAirfieldFields.outputs,
        graphOutputs = listOf("aviation.airfield.nearby"),
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
            failure("Nearby-airfield execution requires the Android capability boundary so GPS and the local catalogue can be accessed."),
            InvocationContext.from(request.context)
        )

    fun values(
        search: AviationAirfieldRepository.SearchResult,
        latitude: Double,
        longitude: Double,
        locationSource: String
    ): Map<String, String> {
        val nearest = search.airfields.firstOrNull()
        val json = JSONArray().apply {
            search.airfields.forEach { airfield ->
                put(JSONObject().apply {
                    put("ident", airfield.ident)
                    put("type", airfield.type)
                    put("name", airfield.name)
                    put("latitude", airfield.latitude)
                    put("longitude", airfield.longitude)
                    put("elevation_ft", airfield.elevationFt ?: JSONObject.NULL)
                    put("iso_country", airfield.isoCountry)
                    put("municipality", airfield.municipality)
                    put("scheduled_service", airfield.scheduledService)
                    put("gps_code", airfield.gpsCode)
                    put("iata_code", airfield.iataCode)
                    put("distance_nm", airfield.distanceNm)
                    put("bearing_deg", airfield.bearingDeg)
                })
            }
        }
        val value = if (search.airfields.isEmpty()) {
            "No matching airfields found."
        } else {
            search.airfields.joinToString("\n") { airfield ->
                val code = airfield.ident.ifBlank { airfield.gpsCode.ifBlank { airfield.iataCode } }
                "$code — ${airfield.name}: ${AviationCalculations.format(airfield.distanceNm, 1)} NM, ${AviationCalculations.format(airfield.bearingDeg, 0)}°"
            }
        }
        return linkedMapOf(
            NearbyAirfieldFields.VALUE to value,
            NearbyAirfieldFields.COUNT to search.airfields.size.toString(),
            NearbyAirfieldFields.NEAREST_IDENT to nearest?.ident.orEmpty(),
            NearbyAirfieldFields.NEAREST_NAME to nearest?.name.orEmpty(),
            NearbyAirfieldFields.NEAREST_DISTANCE_NM to nearest?.distanceNm?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            NearbyAirfieldFields.NEAREST_BEARING_DEG to nearest?.bearingDeg?.let { AviationCalculations.format(it, 1) }.orEmpty(),
            NearbyAirfieldFields.NEAREST_LATITUDE to nearest?.latitude?.toString().orEmpty(),
            NearbyAirfieldFields.NEAREST_LONGITUDE to nearest?.longitude?.toString().orEmpty(),
            NearbyAirfieldFields.NEAREST_ELEVATION_FT to nearest?.elevationFt?.let { AviationCalculations.format(it, 0) }.orEmpty(),
            NearbyAirfieldFields.STATUS to "succeeded",
            NearbyAirfieldFields.QUERY_LATITUDE to latitude.toString(),
            NearbyAirfieldFields.QUERY_LONGITUDE to longitude.toString(),
            NearbyAirfieldFields.LOCATION_SOURCE to locationSource,
            NearbyAirfieldFields.CATALOG_UPDATED_ISO to search.catalogUpdatedIso,
            NearbyAirfieldFields.DATA_SOURCE to search.sourceUrl,
            NearbyAirfieldFields.WARNING to search.warning,
            NearbyAirfieldFields.AIRFIELDS_JSON to json.toString(),
            NearbyAirfieldFields.ERROR to ""
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult =
        AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, NearbyAirfieldFields.STATUS, NearbyAirfieldFields.ERROR)

    fun failure(error: String): Map<String, String> = linkedMapOf(
        NearbyAirfieldFields.VALUE to "",
        NearbyAirfieldFields.COUNT to "0",
        NearbyAirfieldFields.STATUS to "failed",
        NearbyAirfieldFields.WARNING to "",
        NearbyAirfieldFields.AIRFIELDS_JSON to "[]",
        NearbyAirfieldFields.ERROR to error
    )
}

object RunwayWindFields {
    const val STATUS = "runway_wind_status"
    const val VALUE = "runway_wind_value"
    const val HEADWIND_KT = "runway_headwind_kt"
    const val TAILWIND_KT = "runway_tailwind_kt"
    const val CROSSWIND_KT = "runway_crosswind_kt"
    const val CROSSWIND_SIDE = "runway_crosswind_side"
    const val GUST_HEADWIND_KT = "runway_gust_headwind_kt"
    const val GUST_TAILWIND_KT = "runway_gust_tailwind_kt"
    const val GUST_CROSSWIND_KT = "runway_gust_crosswind_kt"
    const val AUDIT_JSON = "runway_wind_audit_json"
    const val ERROR = "runway_wind_error"
    val outputs = listOf(VALUE, HEADWIND_KT, TAILWIND_KT, CROSSWIND_KT, CROSSWIND_SIDE, GUST_HEADWIND_KT, GUST_TAILWIND_KT, GUST_CROSSWIND_KT, STATUS, AUDIT_JSON, ERROR)
}

object As100RunwayWindMethod : As100Method {
    const val ID = "aviation.runway.wind"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Calculation", "Runway wind components")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Runway wind components", version = AVIATION_VERSION,
        description = "Calculate headwind, tailwind and crosswind components for a runway heading.",
        inputs = listOf("runway_heading_deg", "wind_direction_deg", "wind_speed_kt", "include_gust", "gust_speed_kt"),
        outputs = RunwayWindFields.outputs, graphOutputs = listOf("aviation.runway.wind"),
        parameters = mapOf("category" to "Aviation", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> = runCatching {
        val heading = settings.aviationValue("runway_heading_deg", "runway_heading")?.toDoubleOrNull() ?: error("Runway heading is required.")
        val direction = settings.aviationValue("wind_direction_deg", "wind_direction")?.toDoubleOrNull() ?: error("Wind direction is required.")
        val speed = settings.aviationValue("wind_speed_kt", "wind_speed")?.toDoubleOrNull() ?: error("Wind speed is required.")
        val gustRaw = settings.aviationValue("gust_speed_kt", "wind_gust_kt")?.toDoubleOrNull()?.takeIf { it > 0.0 }
        val includeGust = settings.aviationValue("include_gust")?.toBooleanStrictOrNull() ?: (gustRaw != null)
        val gust = gustRaw.takeIf { includeGust }
        val steady = AviationCalculations.runwayWind(heading, direction, speed)
        val gustComponents = gust?.let { AviationCalculations.runwayWind(heading, direction, it) }
        val value = buildString {
            if (steady.headwindKt > 0.05) append("Headwind ${AviationCalculations.format(steady.headwindKt)} kt")
            if (steady.tailwindKt > 0.05) append("Tailwind ${AviationCalculations.format(steady.tailwindKt)} kt")
            if (steady.crosswindKt > 0.05) {
                if (isNotEmpty()) append("; ")
                append("Crosswind ${AviationCalculations.format(steady.crosswindKt)} kt from ${steady.crosswindSide}")
            }
            if (isEmpty()) append("Calm / negligible components")
            gustComponents?.let {
                append(". Gust crosswind ${AviationCalculations.format(it.crosswindKt)} kt")
            }
        }
        val audit = JSONObject().apply {
            put("runway_heading_deg", heading); put("wind_direction_deg", direction); put("wind_speed_kt", speed)
            put("gust_speed_kt", gust ?: JSONObject.NULL); put("calculation", "trigonometric wind components")
        }
        linkedMapOf(
            RunwayWindFields.VALUE to value,
            RunwayWindFields.HEADWIND_KT to AviationCalculations.format(steady.headwindKt, 2),
            RunwayWindFields.TAILWIND_KT to AviationCalculations.format(steady.tailwindKt, 2),
            RunwayWindFields.CROSSWIND_KT to AviationCalculations.format(steady.crosswindKt, 2),
            RunwayWindFields.CROSSWIND_SIDE to steady.crosswindSide,
            RunwayWindFields.GUST_HEADWIND_KT to gustComponents?.headwindKt?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            RunwayWindFields.GUST_TAILWIND_KT to gustComponents?.tailwindKt?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            RunwayWindFields.GUST_CROSSWIND_KT to gustComponents?.crosswindKt?.let { AviationCalculations.format(it, 2) }.orEmpty(),
            RunwayWindFields.STATUS to "succeeded",
            RunwayWindFields.AUDIT_JSON to audit.toString(),
            RunwayWindFields.ERROR to ""
        )
    }.getOrElse { error -> linkedMapOf(RunwayWindFields.VALUE to "", RunwayWindFields.STATUS to "failed", RunwayWindFields.AUDIT_JSON to "{}", RunwayWindFields.ERROR to (error.message ?: "Wind calculation failed.")) }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult = AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, RunwayWindFields.STATUS, RunwayWindFields.ERROR)
}

object AltitudeFields {
    const val STATUS = "aviation_altitude_status"
    const val VALUE = "aviation_altitude_value"
    const val PRESSURE_ALTITUDE_FT = "pressure_altitude_ft"
    const val DENSITY_ALTITUDE_FT = "density_altitude_ft"
    const val ISA_TEMPERATURE_C = "isa_temperature_c"
    const val ISA_DEVIATION_C = "isa_deviation_c"
    const val QNH_HPA = "qnh_hpa"
    const val AUDIT_JSON = "aviation_altitude_audit_json"
    const val ERROR = "aviation_altitude_error"
    val outputs = listOf(VALUE, PRESSURE_ALTITUDE_FT, DENSITY_ALTITUDE_FT, ISA_TEMPERATURE_C, ISA_DEVIATION_C, QNH_HPA, STATUS, AUDIT_JSON, ERROR)
}

object As100AviationAltitudeMethod : As100Method {
    const val ID = "aviation.altitude.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Calculation", "Pressure and density altitude")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Pressure / density altitude", version = AVIATION_VERSION,
        description = "Calculate pressure and density altitude for planning from elevation, QNH and outside-air temperature.",
        inputs = listOf("field_elevation_ft", "qnh_value", "qnh_unit", "oat_c"), outputs = AltitudeFields.outputs,
        graphOutputs = listOf("aviation.altitude.calculate"), parameters = mapOf("category" to "Aviation", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> = runCatching {
        val elevation = settings.aviationValue("field_elevation_ft", "airfield_nearest_elevation_ft", "elevation_ft")?.toDoubleOrNull() ?: error("Field elevation is required.")
        val qnhUnit = settings.aviationValue("qnh_unit") ?: if (settings.aviationValue("qnh_inhg") != null) "inhg" else "hpa"
        val qnh = settings.aviationValue("qnh_value", if (qnhUnit == "inhg") "qnh_inhg" else "qnh_hpa", "pressure_hpa")?.toDoubleOrNull() ?: error("QNH is required.")
        val oat = settings.aviationValue("oat_c", "temperature_c")?.toDoubleOrNull() ?: error("Outside-air temperature is required.")
        val calculated = AviationCalculations.altitude(elevation, qnh, qnhUnit, oat)
        val value = "Pressure altitude ${AviationCalculations.format(calculated.pressureAltitudeFt, 0)} ft; density altitude ${AviationCalculations.format(calculated.densityAltitudeFt, 0)} ft"
        val audit = JSONObject().apply {
            put("field_elevation_ft", elevation); put("qnh_input", qnh); put("qnh_unit", qnhUnit); put("oat_c", oat)
            put("method", "standard-atmosphere pressure correction + 120 ft/°C density-altitude planning approximation")
            put("safety_note", "Planning aid only; use AFM/POH performance data for aircraft operation.")
        }
        linkedMapOf(
            AltitudeFields.VALUE to value,
            AltitudeFields.PRESSURE_ALTITUDE_FT to AviationCalculations.format(calculated.pressureAltitudeFt, 1),
            AltitudeFields.DENSITY_ALTITUDE_FT to AviationCalculations.format(calculated.densityAltitudeFt, 1),
            AltitudeFields.ISA_TEMPERATURE_C to AviationCalculations.format(calculated.isaTemperatureC, 1),
            AltitudeFields.ISA_DEVIATION_C to AviationCalculations.format(calculated.isaDeviationC, 1),
            AltitudeFields.QNH_HPA to AviationCalculations.format(calculated.qnhHpa, 2),
            AltitudeFields.STATUS to "succeeded",
            AltitudeFields.AUDIT_JSON to audit.toString(),
            AltitudeFields.ERROR to ""
        )
    }.getOrElse { error -> linkedMapOf(AltitudeFields.VALUE to "", AltitudeFields.STATUS to "failed", AltitudeFields.AUDIT_JSON to "{}", AltitudeFields.ERROR to (error.message ?: "Altitude calculation failed.")) }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult = AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, AltitudeFields.STATUS, AltitudeFields.ERROR)
}

object E6bFields {
    const val STATUS = "aviation_e6b_status"
    const val VALUE = "aviation_e6b_value"
    const val RESULT = "aviation_e6b_result"
    const val RESULT_UNIT = "aviation_e6b_result_unit"
    const val CALCULATION = "aviation_e6b_calculation"
    const val AUDIT_JSON = "aviation_e6b_audit_json"
    const val ERROR = "aviation_e6b_error"
    val outputs = listOf(VALUE, RESULT, RESULT_UNIT, CALCULATION, STATUS, AUDIT_JSON, ERROR)
}

object As100AviationE6bMethod : As100Method {
    const val ID = "aviation.e6b.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Calculation", "Electronic flight computer calculations")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "E6B flight calculator", version = AVIATION_VERSION,
        description = "Calculate time, distance, groundspeed, fuel required, endurance or range.",
        inputs = listOf("calculation", "distance_nm", "groundspeed_kt", "time_minutes", "fuel_unit", "fuel_available", "fuel_burn_per_hour"),
        outputs = E6bFields.outputs, graphOutputs = listOf("aviation.e6b.calculate"), parameters = mapOf("category" to "Aviation", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, generate(request.context), InvocationContext.from(request.context))

    fun generate(settings: Map<String, String>): Map<String, String> = runCatching {
        val calculation = settings.aviationValue("calculation") ?: "time_from_distance_speed"
        val distance = settings.aviationValue("distance_nm", "route_distance_nm")?.toDoubleOrNull()
        val speed = settings.aviationValue("groundspeed_kt", "ground_speed_kt")?.toDoubleOrNull()
        val time = settings.aviationValue("time_minutes", "ete_minutes")?.toDoubleOrNull()
        val fuel = settings.aviationValue("fuel_available")?.toDoubleOrNull()
        val burn = settings.aviationValue("fuel_burn_per_hour", "fuel_burn_hour")?.toDoubleOrNull()
        val fuelUnit = when (settings.aviationValue("fuel_unit") ?: "L") {
            "us_gal", "US_gal", "US gallon", "US gallons" -> "US gal"
            "imp_gal", "Imp_gal", "Imperial gallon", "Imperial gallons" -> "Imp gal"
            else -> settings.aviationValue("fuel_unit") ?: "L"
        }
        val calculated = AviationCalculations.e6b(calculation, distance, speed, time, fuel, burn)
        val resultUnit = if (calculated.second == "fuel units") fuelUnit else calculated.second
        val value = "${e6bLabel(calculation)}: ${AviationCalculations.format(calculated.first, 2)} $resultUnit"
        val audit = JSONObject().apply {
            put("calculation", calculation); put("distance_nm", distance ?: JSONObject.NULL); put("groundspeed_kt", speed ?: JSONObject.NULL)
            put("time_minutes", time ?: JSONObject.NULL); put("fuel_available", fuel ?: JSONObject.NULL); put("fuel_burn_per_hour", burn ?: JSONObject.NULL); put("fuel_unit", fuelUnit)
        }
        linkedMapOf(
            E6bFields.VALUE to value,
            E6bFields.RESULT to AviationCalculations.format(calculated.first, 4),
            E6bFields.RESULT_UNIT to resultUnit,
            E6bFields.CALCULATION to calculation,
            E6bFields.STATUS to "succeeded",
            E6bFields.AUDIT_JSON to audit.toString(),
            E6bFields.ERROR to ""
        )
    }.getOrElse { error -> linkedMapOf(E6bFields.VALUE to "", E6bFields.RESULT to "", E6bFields.RESULT_UNIT to "", E6bFields.CALCULATION to settings.aviationValue("calculation").orEmpty(), E6bFields.STATUS to "failed", E6bFields.AUDIT_JSON to "{}", E6bFields.ERROR to (error.message ?: "E6B calculation failed.")) }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult = AviationExecutionSupport.complete(this, AVIATION_VERSION, ID, request, values, invocation, E6bFields.STATUS, E6bFields.ERROR)

    private fun e6bLabel(value: String): String = when (value) {
        "time_from_distance_speed" -> "Time"
        "distance_from_speed_time" -> "Distance"
        "speed_from_distance_time" -> "Groundspeed"
        "fuel_required" -> "Fuel required"
        "endurance" -> "Endurance"
        "range" -> "Range"
        else -> "Result"
    }
}
