package com.example.methodmesh.modules.aquaticfield

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

object AquaticCommonFields {
    const val STATUS = "aquatic_status"
    const val RESULT = "aquatic_result"
    const val METHOD = "aquatic_method"
    const val ALGORITHM = "aquatic_algorithm"
    const val WARNINGS_JSON = "aquatic_warnings_json"
    const val METADATA_JSON = "aquatic_metadata_json"
    const val ERROR = "aquatic_error"
    val outputs = listOf(STATUS, RESULT, METHOD, ALGORITHM, WARNINGS_JSON, METADATA_JSON, ERROR)
}

object AquaticPressureDepthFields {
    const val DEPTH_M = "aquatic_depth_m"
    const val PRESSURE_DBAR = "aquatic_pressure_dbar"
    const val CORRECTION_M = "aquatic_depth_correction_m"
    const val ESTIMATED = "aquatic_depth_is_estimated"
    val outputs = AquaticCommonFields.outputs + listOf(DEPTH_M, PRESSURE_DBAR, CORRECTION_M, ESTIMATED)
}

object AquaticSalinityFields {
    const val CONDUCTIVITY_MS_CM = "aquatic_conductivity_ms_cm"
    const val SP = "aquatic_practical_salinity"
    const val SA = "aquatic_absolute_salinity_g_kg"
    const val SR = "aquatic_reference_salinity_g_kg"
    const val SPECIFIC_25 = "aquatic_specific_conductance_25_us_cm"
    const val TDS = "aquatic_estimated_tds_mg_l"
    val outputs = AquaticCommonFields.outputs + listOf(CONDUCTIVITY_MS_CM, SP, SA, SR, SPECIFIC_25, TDS)
}

object AquaticSecchiFields {
    const val DEPTH_M = "aquatic_secchi_depth_m"
    const val DISAPPEARANCE_M = "aquatic_secchi_disappearance_m"
    const val REAPPEARANCE_M = "aquatic_secchi_reappearance_m"
    const val TSI = "aquatic_secchi_tsi_sd"
    const val EUPHOTIC_M = "aquatic_secchi_estimated_euphotic_depth_m"
    const val BOTTOM_LIMITED = "aquatic_secchi_bottom_limited"
    val outputs = AquaticCommonFields.outputs + listOf(DEPTH_M, DISAPPEARANCE_M, REAPPEARANCE_M, TSI, EUPHOTIC_M, BOTTOM_LIMITED)
}

object AquaticDepthPlanFields {
    const val COUNT = "aquatic_depth_plan_count"
    const val TEXT = "aquatic_depth_plan_text"
    const val JSON = "aquatic_depth_plan_json"
    val outputs = AquaticCommonFields.outputs + listOf(COUNT, TEXT, JSON)
}

object AquaticProfileFields {
    val specific = listOf(
        "aquatic_profile_n_rows",
        "aquatic_profile_min_depth_m",
        "aquatic_profile_max_depth_m",
        "aquatic_profile_thermocline_depth_m",
        "aquatic_profile_halocline_depth_m",
        "aquatic_profile_pycnocline_depth_m",
        "aquatic_profile_oxycline_depth_m",
        "aquatic_profile_mixed_layer_depth_m",
        "aquatic_profile_metalimnion_top_m",
        "aquatic_profile_metalimnion_bottom_m",
        "aquatic_profile_max_buoyancy_frequency_s2",
        "aquatic_profile_schmidt_stability_j_m2",
        "aquatic_profile_heat_content"
    )
    val outputs = AquaticCommonFields.outputs + specific
}

object AquaticQcFields {
    val specific = listOf(
        "aquatic_qc_status",
        "aquatic_qc_issue_count",
        "aquatic_qc_warning_count",
        "aquatic_qc_error_count",
        "aquatic_qc_summary",
        "aquatic_qc_json"
    )
    val outputs = AquaticCommonFields.outputs + specific
}

object AquaticStationFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_station_id", "aquatic_visit_id", "aquatic_station_latitude", "aquatic_station_longitude",
        "aquatic_station_plus_code", "aquatic_station_water_depth_m", "aquatic_station_timestamp_utc", "aquatic_visit_status"
    )
}

object AquaticCtdFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_ctd_cast_id", "aquatic_ctd_instrument_id", "aquatic_ctd_max_pressure_dbar",
        "aquatic_ctd_max_depth_m", "aquatic_ctd_start_utc", "aquatic_ctd_end_utc",
        "aquatic_ctd_raw_file_uri", "aquatic_ctd_processed_file_uri", "aquatic_ctd_file_sha256", "aquatic_ctd_flags"
    )
}

object AquaticSampleIdFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_sample_id", "aquatic_sample_id_template"
    )
}

object AquaticSampleFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_sample_id", "aquatic_sample_station_id", "aquatic_sample_visit_id", "aquatic_sample_cast_id",
        "aquatic_sample_bottle_number", "aquatic_sample_target_depth_m", "aquatic_sample_actual_depth_m",
        "aquatic_sample_pressure_dbar", "aquatic_sample_type", "aquatic_sample_timestamp_utc"
    )
}

object AquaticRosetteFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_rosette_cast_id", "aquatic_rosette_bottle_number", "aquatic_rosette_fire_time_utc",
        "aquatic_rosette_pressure_dbar", "aquatic_rosette_depth_m", "aquatic_rosette_sample_ids_json", "aquatic_rosette_flags"
    )
}

object AquaticInstrumentCheckFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_instrument_id", "aquatic_instrument_check_type", "aquatic_instrument_check_result",
        "aquatic_instrument_check_timestamp_utc"
    )
}

object AquaticTransectFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_transect_id", "aquatic_transect_start_utc", "aquatic_transect_end_utc",
        "aquatic_transect_distance_m", "aquatic_transect_station_count"
    )
}

object AquaticMooringFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_mooring_id", "aquatic_mooring_event", "aquatic_mooring_timestamp_utc",
        "aquatic_mooring_latitude", "aquatic_mooring_longitude", "aquatic_mooring_instrument_count"
    )
}

object AquaticSedimentFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_sediment_id", "aquatic_sediment_method", "aquatic_sediment_water_depth_m",
        "aquatic_sediment_recovery_length_cm", "aquatic_sediment_subsample_count"
    )
}

object AquaticDashboardFields {
    val outputs = AquaticCommonFields.outputs + listOf(
        "aquatic_dashboard_station_id", "aquatic_dashboard_visit_id", "aquatic_dashboard_visit_status",
        "aquatic_dashboard_sample_planned_count", "aquatic_dashboard_sample_completed_count",
        "aquatic_dashboard_cast_count", "aquatic_dashboard_secchi_depth_m", "aquatic_dashboard_qc_issue_count",
        "aquatic_dashboard_summary", "aquatic_dashboard_snapshot_json"
    )
}

object AquaticInputFields {
    val PRESSURE_DEPTH = listOf("mode", "pressure_dbar", "depth_m", "latitude_deg", "pressure_offset_dbar", "vertical_offset_m", "correction_factor", "wire_length_m", "cable_angle_deg", "angle_convention")
    val SALINITY = listOf("mode", "conductivity", "conductivity_unit", "temperature_c", "pressure_dbar", "latitude_deg", "longitude_deg", "temperature_coefficient_per_c", "tds_coefficient")
    val SECCHI = listOf("disappearance_depth_m", "reappearance_depth_m", "water_depth_m", "disk_diameter_cm", "bottom_reached_before_disappearance", "calculate_carlson_tsi", "estimate_euphotic_depth", "euphotic_multiplier", "direction_difference_warning_m", "observer", "conditions")
    val DEPTH_PLAN = listOf("strategy", "water_depth_m", "maximum_depth_m", "bottom_clearance_m", "interval_m", "explicit_depths_m", "depth_fractions", "thermocline_depth_m", "stratification_offset_m")
    val PROFILE = listOf("profile_data", "delimiter", "depth_column", "temperature_column", "salinity_column", "oxygen_column", "density_column", "metalimnion_gradient_threshold_c_per_m", "mixed_layer_mode", "mixed_layer_threshold", "mixed_layer_reference_depth_m")
    val QC = listOf("station_water_depth_m", "secchi_depth_m", "sample_depth_m", "ctd_max_depth_m", "depth_tolerance_m", "planned_sample_count", "completed_sample_count", "blank_required", "blank_recorded", "duplicate_required", "duplicate_recorded", "calibration_current", "gps_present")
    val STATION = listOf("station_id", "visit_id", "station_name", "waterbody", "latitude", "longitude", "plus_code", "water_depth_m", "timestamp_utc", "operators", "platform", "weather", "wind", "surface_state", "tide", "ice", "purpose", "notes")
    val CTD = listOf("cast_id", "station_id", "visit_id", "instrument_id", "make", "model", "serial", "configuration_id", "calibration_date", "start_utc", "end_utc", "surface_soak", "cast_direction", "max_pressure_dbar", "max_depth_m", "nominal_rate_m_s", "operator", "bottle_fire_count", "raw_file_uri", "processed_file_uri", "file_sha256", "flags", "notes")
    val SAMPLE_ID = listOf("template", "project", "campaign", "station", "visit", "cast", "depth", "bottle", "type", "replicate", "sequence", "date")
    val SAMPLE = listOf("sample_id", "station_id", "visit_id", "cast_id", "bottle_number", "target_depth_m", "actual_depth_m", "pressure_dbar", "timestamp_utc", "sample_type", "analyte", "volume", "container", "filtration", "filter_id", "pore_size", "preservative", "preservation", "storage", "replicate_id", "blank_type", "operator", "notes")
    val ROSETTE = listOf("cast_id", "rosette_id", "bottle_number", "firing_sequence", "fire_time_utc", "pressure_dbar", "depth_m", "status", "sample_ids", "flags", "notes")
    val INSTRUMENT_CHECK = listOf("instrument_id", "check_type", "timestamp_utc", "reference", "expected_value", "observed_value", "tolerance", "result", "corrective_action", "next_due", "operator", "notes")
    val TRANSECT = listOf("transect_id", "start_utc", "end_utc", "start_latitude", "start_longitude", "end_latitude", "end_longitude", "station_count", "platform", "notes")
    val MOORING = listOf("mooring_id", "event", "timestamp_utc", "latitude", "longitude", "water_depth_m", "line_length_m", "sensor_depths_m", "instrument_ids", "instrument_count", "sampling_interval", "expected_recovery_date", "condition", "file_hashes", "notes")
    val SEDIMENT = listOf("sediment_id", "method", "station_id", "visit_id", "deployment_utc", "recovery_utc", "water_depth_m", "target_penetration_cm", "observed_penetration_cm", "recovery_length_cm", "core_diameter_cm", "integrity", "sediment_description", "subsample_count", "storage", "notes")
}

abstract class AquaticMethodBase(
    private val methodId: String,
    private val methodName: String,
    private val methodDescription: String,
    private val methodType: MethodObjectType,
    private val outputFields: List<String>,
    private val graphOutput: String,
    private val inputFields: List<String> = emptyList()
) : As100Method {
    companion object {
        const val VERSION = "0.1.0"
    }

    override val id: String = methodId
    override val ref = ArchitectureRef(ArchitectureId(methodId), "Method", methodName)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(methodId),
        methodType = methodType,
        name = methodName,
        version = VERSION,
        description = methodDescription,
        inputs = inputFields,
        outputs = outputFields,
        graphOutputs = listOf(graphOutput),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development",
            "offline" to "true",
            "module" to "aquaticfield"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    protected abstract fun calculate(settings: Map<String, String>): Map<String, String>

    fun calculateValues(settings: Map<String, String>): Map<String, String> = normalise(calculate(settings))

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculateValues(request.context), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val normal = normalise(values)
        val ok = normal[AquaticCommonFields.STATUS] == "succeeded"
        val entity = Entity(
            id = ArchitectureId("aquatic:${methodId.substringAfterLast('.')}:${System.currentTimeMillis()}"),
            entityType = "AquaticFieldRecord",
            attributes = normal,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.aquaticfield", methodId, VERSION)
        val observation = Observation(
            phenomenon = graphOutput,
            subject = ArchitectureRef(entity.id, entity.objectType, methodName),
            values = normal,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = methodId,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(AquaticCommonFields.ERROR to normal[AquaticCommonFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun normalise(values: Map<String, String>): Map<String, String> =
        outputFields.associateWith { "" }.toMutableMap().apply { putAll(values) }
}

private fun outcome(methodId: String, calculation: AquaticAlgorithms.Outcome): Map<String, String> {
    val metadata = linkedMapOf(
        "method_id" to methodId,
        "algorithm" to calculation.algorithm,
        "generated_time_utc" to Instant.now().toString()
    )
    return linkedMapOf<String, String>().apply {
        put(AquaticCommonFields.STATUS, calculation.status)
        put(AquaticCommonFields.RESULT, calculation.result)
        put(AquaticCommonFields.METHOD, methodId)
        put(AquaticCommonFields.ALGORITHM, calculation.algorithm)
        put(AquaticCommonFields.WARNINGS_JSON, AquaticAlgorithms.jsonStringList(calculation.warnings))
        put(AquaticCommonFields.METADATA_JSON, AquaticAlgorithms.jsonObject(metadata))
        put(AquaticCommonFields.ERROR, calculation.error)
        putAll(calculation.values)
    }
}

private fun recordOutcome(
    methodId: String,
    settings: Map<String, String>,
    result: String,
    specific: Map<String, String>,
    warnings: List<String> = emptyList(),
    error: String = ""
): Map<String, String> {
    val ok = error.isBlank()
    val cleanSettings = settings
        .filterKeys { !it.startsWith("methodmesh_") && !it.startsWith("input_methodmesh_") }
        .mapKeys { (k, _) -> k.removePrefix("input_") }
    return linkedMapOf<String, String>().apply {
        put(AquaticCommonFields.STATUS, if (ok) "succeeded" else "failed")
        put(AquaticCommonFields.RESULT, if (ok) result else "")
        put(AquaticCommonFields.METHOD, methodId)
        put(AquaticCommonFields.ALGORITHM, "field_record_v0.1")
        put(AquaticCommonFields.WARNINGS_JSON, AquaticAlgorithms.jsonStringList(warnings))
        put(AquaticCommonFields.METADATA_JSON, AquaticAlgorithms.jsonObject(cleanSettings + mapOf("recorded_time_utc" to Instant.now().toString())))
        put(AquaticCommonFields.ERROR, error)
        putAll(specific)
    }
}

private fun Map<String, String>.v(key: String): String =
    (this[key] ?: this["input_$key"]).orEmpty().trim()

private fun required(settings: Map<String, String>, vararg keys: String): String? =
    keys.firstOrNull { settings.v(it).isBlank() }?.let { "Required field is missing: $it" }

object As100AquaticPressureDepthMethod : AquaticMethodBase(
    "aquatic.pressure_depth.convert", "Pressure / depth conversion",
    "Convert pressure and depth and apply explicit instrument or cable geometry corrections.",
    MethodObjectType.Calculation, AquaticPressureDepthFields.outputs, "aquatic.pressure_depth", AquaticInputFields.PRESSURE_DEPTH
) {
    const val ID = "aquatic.pressure_depth.convert"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.pressureDepth(settings))
}

object As100AquaticSalinityMethod : AquaticMethodBase(
    "aquatic.salinity.convert", "Salinity and conductivity",
    "Convert conductivity units, calculate Practical Salinity, normalise specific conductance, or estimate TDS using an explicit coefficient.",
    MethodObjectType.Calculation, AquaticSalinityFields.outputs, "aquatic.salinity", AquaticInputFields.SALINITY
) {
    const val ID = "aquatic.salinity.convert"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.salinity(settings))
}

object As100AquaticSecchiMethod : AquaticMethodBase(
    "aquatic.secchi.measure", "Secchi depth",
    "Record a guided Secchi observation with optional transparency-derived indices.",
    MethodObjectType.Calculation, AquaticSecchiFields.outputs, "aquatic.secchi", AquaticInputFields.SECCHI
) {
    const val ID = "aquatic.secchi.measure"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.secchi(settings))
}

object As100AquaticDepthPlanMethod : AquaticMethodBase(
    "aquatic.depth_plan.generate", "Sampling depth plan",
    "Generate a defensible list of planned sampling depths.",
    MethodObjectType.Calculation, AquaticDepthPlanFields.outputs, "aquatic.depth_plan", AquaticInputFields.DEPTH_PLAN
) {
    const val ID = "aquatic.depth_plan.generate"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.depthPlan(settings))
}

object As100AquaticProfileSummaryMethod : AquaticMethodBase(
    "aquatic.profile.summarise", "Vertical profile summary",
    "Summarise processed vertical-profile data and expose explicit gradient-based stratification features.",
    MethodObjectType.Calculation, AquaticProfileFields.outputs, "aquatic.profile.summary", AquaticInputFields.PROFILE
) {
    const val ID = "aquatic.profile.summarise"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.profileSummary(settings))
}

object As100AquaticFieldQcMethod : AquaticMethodBase(
    "aquatic.field_qc.run", "Aquatic field QC",
    "Run completeness and plausibility checks without mutating original field observations.",
    MethodObjectType.Rule, AquaticQcFields.outputs, "aquatic.field.qc", AquaticInputFields.QC
) {
    const val ID = "aquatic.field_qc.run"
    override fun calculate(settings: Map<String, String>) = outcome(ID, AquaticAlgorithms.fieldQc(settings))
}

object As100AquaticStationVisitMethod : AquaticMethodBase(
    "aquatic.station.visit", "Station visit",
    "Record one occupation of a persistent aquatic sampling station.",
    MethodObjectType.Workflow, AquaticStationFields.outputs, "aquatic.station.visit", AquaticInputFields.STATION
) {
    const val ID = "aquatic.station.visit"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "station_id", "visit_id")
        val ts = settings.v("timestamp_utc").ifBlank { Instant.now().toString() }
        val station = settings.v("station_id")
        val visit = settings.v("visit_id")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "$station · visit $visit" else "",
            specific = mapOf(
                "aquatic_station_id" to station,
                "aquatic_visit_id" to visit,
                "aquatic_station_latitude" to settings.v("latitude"),
                "aquatic_station_longitude" to settings.v("longitude"),
                "aquatic_station_plus_code" to settings.v("plus_code"),
                "aquatic_station_water_depth_m" to settings.v("water_depth_m"),
                "aquatic_station_timestamp_utc" to ts,
                "aquatic_visit_status" to settings.v("visit_status").ifBlank { "open" }
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticCtdCastMethod : AquaticMethodBase(
    "aquatic.ctd.cast", "CTD / sonde cast record",
    "Record the field operation and provenance of a CTD or multiparameter-sonde cast.",
    MethodObjectType.Workflow, AquaticCtdFields.outputs, "aquatic.ctd.cast", AquaticInputFields.CTD
) {
    const val ID = "aquatic.ctd.cast"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "cast_id", "instrument_id")
        val castId = settings.v("cast_id")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "CTD cast $castId" else "",
            specific = mapOf(
                "aquatic_ctd_cast_id" to castId,
                "aquatic_ctd_instrument_id" to settings.v("instrument_id"),
                "aquatic_ctd_max_pressure_dbar" to settings.v("max_pressure_dbar"),
                "aquatic_ctd_max_depth_m" to settings.v("max_depth_m"),
                "aquatic_ctd_start_utc" to settings.v("start_utc"),
                "aquatic_ctd_end_utc" to settings.v("end_utc"),
                "aquatic_ctd_raw_file_uri" to settings.v("raw_file_uri"),
                "aquatic_ctd_processed_file_uri" to settings.v("processed_file_uri"),
                "aquatic_ctd_file_sha256" to settings.v("file_sha256"),
                "aquatic_ctd_flags" to settings.v("flags")
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticSampleIdMethod : AquaticMethodBase(
    "aquatic.sample_id.generate", "Sample ID generator",
    "Generate a stable human-readable sample identifier from configurable components.",
    MethodObjectType.Calculation, AquaticSampleIdFields.outputs, "aquatic.sample.id", AquaticInputFields.SAMPLE_ID
) {
    const val ID = "aquatic.sample_id.generate"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val template = settings.v("template").ifBlank { "{project}-{station}-{cast}-{depth}M-{type}-{replicate}" }
        val tokens = mapOf(
            "project" to settings.v("project"),
            "campaign" to settings.v("campaign"),
            "station" to settings.v("station"),
            "visit" to settings.v("visit"),
            "cast" to settings.v("cast"),
            "depth" to settings.v("depth"),
            "bottle" to settings.v("bottle"),
            "type" to settings.v("sample_type").ifBlank { settings.v("type") },
            "replicate" to settings.v("replicate").ifBlank { "01" },
            "sequence" to settings.v("sequence").ifBlank { "01" },
            "date" to settings.v("date")
        )
        var id = template
        tokens.forEach { (k, v) -> id = id.replace("{$k}", v) }
        id = id.replace(Regex("-+"), "-").trim('-').uppercase()
        val unresolved = Regex("\\{[^}]+}").find(id)?.value
        val error = when {
            id.isBlank() -> "Sample ID template produced an empty identifier."
            unresolved != null -> "Sample ID contains unresolved token $unresolved."
            else -> ""
        }
        return recordOutcome(
            ID, settings,
            result = if (error.isBlank()) id else "",
            specific = mapOf("aquatic_sample_id" to id, "aquatic_sample_id_template" to template),
            error = error
        )
    }
}

object As100AquaticSampleRecordMethod : AquaticMethodBase(
    "aquatic.sample.record", "Discrete water sample",
    "Record one discrete water sample and its field provenance.",
    MethodObjectType.Workflow, AquaticSampleFields.outputs, "aquatic.sample", AquaticInputFields.SAMPLE
) {
    const val ID = "aquatic.sample.record"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "sample_id")
        val id = settings.v("sample_id")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "Sample $id" else "",
            specific = mapOf(
                "aquatic_sample_id" to id,
                "aquatic_sample_station_id" to settings.v("station_id"),
                "aquatic_sample_visit_id" to settings.v("visit_id"),
                "aquatic_sample_cast_id" to settings.v("cast_id"),
                "aquatic_sample_bottle_number" to settings.v("bottle_number"),
                "aquatic_sample_target_depth_m" to settings.v("target_depth_m"),
                "aquatic_sample_actual_depth_m" to settings.v("actual_depth_m"),
                "aquatic_sample_pressure_dbar" to settings.v("pressure_dbar"),
                "aquatic_sample_type" to settings.v("sample_type"),
                "aquatic_sample_timestamp_utc" to settings.v("timestamp_utc").ifBlank { Instant.now().toString() }
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticRosetteFireMethod : AquaticMethodBase(
    "aquatic.rosette.fire", "Rosette firing log",
    "Record one water-sampler bottle firing event and its linked samples.",
    MethodObjectType.Workflow, AquaticRosetteFields.outputs, "aquatic.rosette.fire", AquaticInputFields.ROSETTE
) {
    const val ID = "aquatic.rosette.fire"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "cast_id", "bottle_number")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "Bottle ${settings.v("bottle_number")} · ${settings.v("cast_id")}" else "",
            specific = mapOf(
                "aquatic_rosette_cast_id" to settings.v("cast_id"),
                "aquatic_rosette_bottle_number" to settings.v("bottle_number"),
                "aquatic_rosette_fire_time_utc" to settings.v("fire_time_utc").ifBlank { Instant.now().toString() },
                "aquatic_rosette_pressure_dbar" to settings.v("pressure_dbar"),
                "aquatic_rosette_depth_m" to settings.v("depth_m"),
                "aquatic_rosette_sample_ids_json" to AquaticAlgorithms.jsonStringList(
                    settings.v("sample_ids").split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }
                ),
                "aquatic_rosette_flags" to settings.v("flags")
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticInstrumentCheckMethod : AquaticMethodBase(
    "aquatic.instrument.check", "Instrument check",
    "Record a calibration, verification, cleaning or operational instrument check.",
    MethodObjectType.Rule, AquaticInstrumentCheckFields.outputs, "aquatic.instrument.check", AquaticInputFields.INSTRUMENT_CHECK
) {
    const val ID = "aquatic.instrument.check"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "instrument_id", "check_type")
        val expected = settings.v("expected_value").toDoubleOrNull()
        val observed = settings.v("observed_value").toDoubleOrNull()
        val tolerance = settings.v("tolerance").toDoubleOrNull()
        val explicit = settings.v("result")
        val result = when {
            explicit.isNotBlank() -> explicit
            expected != null && observed != null && tolerance != null ->
                if (kotlin.math.abs(observed - expected) <= tolerance) "PASS" else "FAIL"
            else -> "RECORDED"
        }
        return recordOutcome(
            ID, settings,
            result = if (error == null) "${settings.v("instrument_id")} · $result" else "",
            specific = mapOf(
                "aquatic_instrument_id" to settings.v("instrument_id"),
                "aquatic_instrument_check_type" to settings.v("check_type"),
                "aquatic_instrument_check_result" to result,
                "aquatic_instrument_check_timestamp_utc" to settings.v("timestamp_utc").ifBlank { Instant.now().toString() }
            ),
            warnings = if (result == "FAIL") listOf("Instrument check failed the configured tolerance.") else emptyList(),
            error = error.orEmpty()
        )
    }
}

object As100AquaticTransectMethod : AquaticMethodBase(
    "aquatic.transect.record", "Transect record",
    "Record a waypoint-based aquatic transect and derive start/end geodesic distance when coordinates are available.",
    MethodObjectType.Workflow, AquaticTransectFields.outputs, "aquatic.transect", AquaticInputFields.TRANSECT
) {
    const val ID = "aquatic.transect.record"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "transect_id")
        val lat1 = settings.v("start_latitude").toDoubleOrNull()
        val lon1 = settings.v("start_longitude").toDoubleOrNull()
        val lat2 = settings.v("end_latitude").toDoubleOrNull()
        val lon2 = settings.v("end_longitude").toDoubleOrNull()
        val distance = if (lat1 != null && lon1 != null && lat2 != null && lon2 != null)
            AquaticAlgorithms.geodesicDistanceMeters(lat1, lon1, lat2, lon2) else null
        return recordOutcome(
            ID, settings,
            result = if (error == null) "Transect ${settings.v("transect_id")}" else "",
            specific = mapOf(
                "aquatic_transect_id" to settings.v("transect_id"),
                "aquatic_transect_start_utc" to settings.v("start_utc"),
                "aquatic_transect_end_utc" to settings.v("end_utc"),
                "aquatic_transect_distance_m" to distance?.let { AquaticAlgorithms.fmt(it, 3) }.orEmpty(),
                "aquatic_transect_station_count" to settings.v("station_count")
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticMooringMethod : AquaticMethodBase(
    "aquatic.mooring.record", "Mooring event",
    "Record deployment, service, inspection or recovery of a mooring or fixed aquatic platform.",
    MethodObjectType.Workflow, AquaticMooringFields.outputs, "aquatic.mooring", AquaticInputFields.MOORING
) {
    const val ID = "aquatic.mooring.record"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "mooring_id", "event")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "${settings.v("mooring_id")} · ${settings.v("event")}" else "",
            specific = mapOf(
                "aquatic_mooring_id" to settings.v("mooring_id"),
                "aquatic_mooring_event" to settings.v("event"),
                "aquatic_mooring_timestamp_utc" to settings.v("timestamp_utc").ifBlank { Instant.now().toString() },
                "aquatic_mooring_latitude" to settings.v("latitude"),
                "aquatic_mooring_longitude" to settings.v("longitude"),
                "aquatic_mooring_instrument_count" to settings.v("instrument_count")
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticSedimentMethod : AquaticMethodBase(
    "aquatic.sediment.record", "Sediment sampling record",
    "Record a sediment core or grab operation and its resulting subsamples.",
    MethodObjectType.Workflow, AquaticSedimentFields.outputs, "aquatic.sediment", AquaticInputFields.SEDIMENT
) {
    const val ID = "aquatic.sediment.record"
    override fun calculate(settings: Map<String, String>): Map<String, String> {
        val error = required(settings, "sediment_id", "method")
        return recordOutcome(
            ID, settings,
            result = if (error == null) "${settings.v("method")} · ${settings.v("sediment_id")}" else "",
            specific = mapOf(
                "aquatic_sediment_id" to settings.v("sediment_id"),
                "aquatic_sediment_method" to settings.v("method"),
                "aquatic_sediment_water_depth_m" to settings.v("water_depth_m"),
                "aquatic_sediment_recovery_length_cm" to settings.v("recovery_length_cm"),
                "aquatic_sediment_subsample_count" to settings.v("subsample_count")
            ),
            error = error.orEmpty()
        )
    }
}

object As100AquaticStationDashboardMethod : AquaticMethodBase(
    "aquatic.station.dashboard", "Aquatic station dashboard",
    "Return a compact snapshot of the current locally persisted aquatic station state.",
    MethodObjectType.Workflow, AquaticDashboardFields.outputs, "aquatic.station.dashboard"
) {
    const val ID = "aquatic.station.dashboard"

    override fun calculate(settings: Map<String, String>): Map<String, String> {
        // Non-UI/RIL callers can supply a snapshot explicitly. The native screen
        // injects the persisted repository state before creating the result.
        val station = settings.v("station_id")
        val visit = settings.v("visit_id")
        val planned = settings.v("planned_sample_count").ifBlank { "0" }
        val completed = settings.v("completed_sample_count").ifBlank { "0" }
        val casts = settings.v("cast_count").ifBlank { "0" }
        val secchi = settings.v("secchi_depth_m")
        val qc = settings.v("qc_issue_count").ifBlank { "0" }
        val visitStatus = settings.v("visit_status").ifBlank { if (station.isBlank()) "none" else "open" }
        val summary = if (station.isBlank()) {
            "No active aquatic station"
        } else {
            "$station · $completed/$planned samples · $casts cast(s) · QC $qc"
        }
        val snapshot = linkedMapOf(
            "station_id" to station,
            "visit_id" to visit,
            "visit_status" to visitStatus,
            "water_depth_m" to settings.v("water_depth_m"),
            "planned_sample_count" to planned,
            "completed_sample_count" to completed,
            "cast_count" to casts,
            "secchi_depth_m" to secchi,
            "qc_issue_count" to qc,
            "planned_depths_json" to settings.v("planned_depths_json").ifBlank { "[]" },
            "completed_depths_json" to settings.v("completed_depths_json").ifBlank { "[]" },
            "completed_sample_ids_json" to settings.v("completed_sample_ids_json").ifBlank { "[]" },
            "cast_ids_json" to settings.v("cast_ids_json").ifBlank { "[]" },
            "last_updated_utc" to settings.v("last_updated_utc")
        )
        return recordOutcome(
            ID, settings,
            result = summary,
            specific = mapOf(
                "aquatic_dashboard_station_id" to station,
                "aquatic_dashboard_visit_id" to visit,
                "aquatic_dashboard_visit_status" to visitStatus,
                "aquatic_dashboard_sample_planned_count" to planned,
                "aquatic_dashboard_sample_completed_count" to completed,
                "aquatic_dashboard_cast_count" to casts,
                "aquatic_dashboard_secchi_depth_m" to secchi,
                "aquatic_dashboard_qc_issue_count" to qc,
                "aquatic_dashboard_summary" to summary,
                "aquatic_dashboard_snapshot_json" to AquaticAlgorithms.jsonObject(snapshot)
            )
        )
    }
}
