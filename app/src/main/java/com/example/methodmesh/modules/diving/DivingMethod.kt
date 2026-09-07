package com.example.methodmesh.modules.diving

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
import java.util.Locale

object DivingIds {
    const val SAC_RMV = "diving.sac_rmv"
    const val CYLINDER_CAPACITY = "diving.cylinder.capacity"
    const val PPO2 = "diving.ppo2"
    const val MOD = "diving.mod"
    const val BEST_MIX = "diving.nitrox.best_mix"
    const val EAD = "diving.ead"
    const val END = "diving.end"
    const val GAS_DENSITY = "diving.gas.density"
    const val GAS_PLAN = "diving.gas.plan"
    const val BAILOUT_PLAN = "diving.bailout.plan"
    const val NAVIGATION = "diving.navigation"
    const val LIFT = "diving.lift"
    const val DASHBOARD = "diving.dashboard"
    const val LOG_RECORD = "diving.log.record"
    const val LOG_DASHBOARD = "diving.log.dashboard"
    const val GAS_ANALYSIS_RECORD = "diving.gas.analysis.record"
    const val CYLINDER_RECORD = "diving.cylinder.record"
    const val GAS_DASHBOARD = "diving.gas.dashboard"
}

object DivingCommonFields {
    const val STATUS = "diving_status"
    const val WARNING = "diving_warning"
    const val AUDIT_JSON = "diving_audit_json"
    const val CALCULATED_TIME_ISO = "diving_calculated_time_iso"
    const val ERROR = "diving_error"

    val outputs = listOf(STATUS, WARNING, AUDIT_JSON, CALCULATED_TIME_ISO, ERROR)
}

object DivingFields {
    const val SAC_BAR_MIN = "diving_sac_bar_min"
    const val RMV_L_MIN = "diving_rmv_l_min"
    const val SURFACE_GAS_USED_L = "diving_surface_gas_used_l"
    const val PRESSURE_USED_BAR = "diving_pressure_used_bar"

    const val CYLINDER_TOTAL_GAS_L = "diving_cylinder_total_gas_l"
    const val CYLINDER_RESERVE_GAS_L = "diving_cylinder_reserve_gas_l"
    const val CYLINDER_USABLE_GAS_L = "diving_cylinder_usable_gas_l"

    const val PPO2_BAR = "diving_ppo2_bar"
    const val MOD_M = "diving_mod_m"
    const val BEST_MIX_FO2_PERCENT = "diving_best_mix_fo2_percent"
    const val EAD_M = "diving_ead_m"
    const val END_M = "diving_end_m"
    const val GAS_DENSITY_G_L = "diving_gas_density_g_l"
    const val GAS_MOLAR_MASS_G_MOL = "diving_gas_molar_mass_g_mol"

    const val REQUIRED_GAS_L = "diving_required_gas_l"
    const val REQUIRED_START_PRESSURE_BAR = "diving_required_start_pressure_bar"
    const val GAS_MARGIN_L = "diving_gas_margin_l"
    const val GAS_MARGIN_BAR = "diving_gas_margin_bar"
    const val RESERVE_MET = "diving_reserve_met"
    const val SEGMENTS_JSON = "diving_segments_json"

    const val BAILOUT_REQUIRED_GAS_L = "diving_bailout_required_gas_l"
    const val BAILOUT_REQUIRED_START_PRESSURE_BAR = "diving_bailout_required_start_pressure_bar"
    const val BAILOUT_MARGIN_L = "diving_bailout_margin_l"
    const val BAILOUT_MARGIN_BAR = "diving_bailout_margin_bar"
    const val BAILOUT_RESERVE_MET = "diving_bailout_reserve_met"
    const val BAILOUT_SEGMENTS_JSON = "diving_bailout_segments_json"

    const val RECIPROCAL_HEADING_DEG = "diving_reciprocal_heading_deg"
    const val TRAVEL_TIME_MIN = "diving_travel_time_min"
    const val TRAVEL_DISTANCE_M = "diving_travel_distance_m"

    const val LIFT_BAG_MIN_VOLUME_L = "diving_lift_bag_min_volume_l"
    const val LIFT_SURFACE_EQUIVALENT_GAS_L = "diving_lift_surface_equivalent_gas_l"

    const val DASHBOARD_DEPTH_M = "diving_dashboard_depth_m"
    const val DASHBOARD_AMBIENT_PRESSURE_BAR = "diving_dashboard_ambient_pressure_bar"
    const val DASHBOARD_END_PRESSURE_BAR = "diving_dashboard_end_pressure_bar"
    const val DASHBOARD_GAS_MARGIN_L = "diving_dashboard_gas_margin_l"
    const val DASHBOARD_GAS_MARGIN_BAR = "diving_dashboard_gas_margin_bar"
    const val DASHBOARD_PPO2_BAR = "diving_dashboard_ppo2_bar"
    const val DASHBOARD_MOD_M = "diving_dashboard_mod_m"
    const val DASHBOARD_END_M = "diving_dashboard_end_m"
    const val DASHBOARD_GAS_DENSITY_G_L = "diving_dashboard_gas_density_g_l"
    const val DASHBOARD_RESERVE_MET = "diving_dashboard_reserve_met"

    const val LOG_ID = "diving_log_id"
    const val LOG_SITE = "diving_log_site"
    const val LOG_DATE_TIME_ISO = "diving_log_date_time_iso"
    const val LOG_MAX_DEPTH_M = "diving_log_max_depth_m"
    const val LOG_DURATION_MIN = "diving_log_duration_min"
    const val LOG_RMV_L_MIN = "diving_log_rmv_l_min"
    const val LOG_RECORD_JSON = "diving_log_record_json"
    const val LOG_COUNT = "diving_log_count"
    const val LOG_TOTAL_TIME_MIN = "diving_log_total_time_min"
    const val LOG_DEEPEST_M = "diving_log_deepest_m"
    const val LOG_MEAN_RMV_L_MIN = "diving_log_mean_rmv_l_min"
    const val LOG_RECENT_JSON = "diving_log_recent_json"

    const val GAS_ANALYSIS_ID = "diving_gas_analysis_id"
    const val GAS_ANALYSIS_CYLINDER_ID = "diving_gas_analysis_cylinder_id"
    const val GAS_ANALYSIS_FO2_PERCENT = "diving_gas_analysis_fo2_percent"
    const val GAS_ANALYSIS_FHE_PERCENT = "diving_gas_analysis_fhe_percent"
    const val GAS_ANALYSIS_TIME_ISO = "diving_gas_analysis_time_iso"
    const val GAS_ANALYSIS_RECORD_JSON = "diving_gas_analysis_record_json"

    const val CYLINDER_ID = "diving_cylinder_id"
    const val CYLINDER_LABEL = "diving_cylinder_label"
    const val CYLINDER_WATER_VOLUME_L = "diving_cylinder_water_volume_l"
    const val CYLINDER_WORKING_PRESSURE_BAR = "diving_cylinder_working_pressure_bar"
    const val CYLINDER_CURRENT_PRESSURE_BAR = "diving_cylinder_current_pressure_bar"
    const val CYLINDER_RECORD_JSON = "diving_cylinder_record_json"

    const val GAS_CYLINDER_COUNT = "diving_gas_cylinder_count"
    const val GAS_ANALYSIS_COUNT = "diving_gas_analysis_count"
    const val GAS_TOTAL_NOMINAL_L = "diving_gas_total_nominal_l"
    const val GAS_INVENTORY_JSON = "diving_gas_inventory_json"
}

data class DivingMethodSpec(
    val id: String,
    val name: String,
    val description: String,
    val outputs: List<String>,
    val safetyClass: String,
    val calculator: (Map<String, String>) -> Map<String, String>
)

class DivingCalculationMethod(
    val spec: DivingMethodSpec,
    private val version: String = "0.1.0"
) : As100Method {
    override val id: String = spec.id
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", spec.name)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = spec.name,
        version = version,
        description = spec.description,
        outputs = (spec.outputs + DivingCommonFields.outputs).distinct(),
        graphOutputs = listOf(id),
        parameters = mapOf(
            "category" to "Diving",
            "status" to "Development",
            "safety_class" to spec.safetyClass
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

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        spec.calculator(settings)
    }.getOrElse { error ->
        failure(settings, error.message ?: "Calculation failed.")
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[DivingCommonFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.diving", id, version)
        val observation = Observation(
            phenomenon = id,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val entity = Entity(
            ArchitectureId("diving:${id}:${System.currentTimeMillis()}"),
            "DivingCalculation",
            temporalContext = request.temporalContext
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
            diagnostics = if (ok) emptyMap() else mapOf(DivingCommonFields.ERROR to values[DivingCommonFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun failure(settings: Map<String, String>, error: String): Map<String, String> = linkedMapOf(
        DivingCommonFields.STATUS to "failed",
        DivingCommonFields.WARNING to "",
        DivingCommonFields.AUDIT_JSON to auditJson(
            methodId = id,
            safetyClass = spec.safetyClass,
            inputs = settings,
            outputs = emptyMap(),
            warnings = emptyList(),
            error = error
        ),
        DivingCommonFields.CALCULATED_TIME_ISO to Instant.now().toString(),
        DivingCommonFields.ERROR to error
    )
}

object DivingMethods {
    val sacRmv = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.SAC_RMV,
            "SAC / RMV calculator",
            "Calculate surface air consumption and respiratory minute volume from cylinder use, depth and time.",
            listOf(DivingFields.SAC_BAR_MIN, DivingFields.RMV_L_MIN, DivingFields.SURFACE_GAS_USED_L, DivingFields.PRESSURE_USED_BAR),
            safetyClass = "physics_support",
            calculator = ::calculateSacRmv
        )
    )

    val cylinderCapacity = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.CYLINDER_CAPACITY,
            "Cylinder gas capacity",
            "Estimate nominal surface-equivalent gas volume from cylinder water volume and pressure.",
            listOf(DivingFields.CYLINDER_TOTAL_GAS_L, DivingFields.CYLINDER_RESERVE_GAS_L, DivingFields.CYLINDER_USABLE_GAS_L),
            safetyClass = "physics_support",
            calculator = ::calculateCylinderCapacity
        )
    )

    val ppo2 = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.PPO2,
            "Oxygen partial pressure",
            "Calculate ppO2 for an analysed gas mix at depth and compare it with the configured limit.",
            listOf(DivingFields.PPO2_BAR),
            safetyClass = "safety_supporting",
            calculator = ::calculatePpo2
        )
    )

    val mod = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.MOD,
            "Maximum operating depth",
            "Calculate MOD from analysed oxygen fraction and a user-selected ppO2 limit.",
            listOf(DivingFields.MOD_M),
            safetyClass = "safety_supporting",
            calculator = ::calculateMod
        )
    )

    val bestMix = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.BEST_MIX,
            "Nitrox best mix",
            "Calculate the maximum oxygen fraction that meets the selected ppO2 limit at depth.",
            listOf(DivingFields.BEST_MIX_FO2_PERCENT),
            safetyClass = "safety_supporting",
            calculator = ::calculateBestMix
        )
    )

    val ead = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.EAD,
            "Equivalent air depth",
            "Calculate equivalent air depth for air/nitrox using nitrogen partial pressure.",
            listOf(DivingFields.EAD_M),
            safetyClass = "safety_supporting",
            calculator = ::calculateEad
        )
    )

    val end = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.END,
            "Equivalent narcotic depth",
            "Calculate equivalent narcotic depth for trimix using an explicitly selected narcotic-gas model.",
            listOf(DivingFields.END_M),
            safetyClass = "safety_supporting",
            calculator = ::calculateEnd
        )
    )

    val gasDensity = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.GAS_DENSITY,
            "Breathing gas density",
            "Estimate ideal-gas density for oxygen/nitrogen/helium mix at depth and temperature.",
            listOf(DivingFields.GAS_DENSITY_G_L, DivingFields.GAS_MOLAR_MASS_G_MOL),
            safetyClass = "safety_supporting",
            calculator = ::calculateGasDensity
        )
    )

    val gasPlan = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.GAS_PLAN,
            "Multi-segment gas plan",
            "Estimate gas required across depth/time segments, cylinder pressure requirement, reserve and margin.",
            listOf(
                DivingFields.REQUIRED_GAS_L,
                DivingFields.REQUIRED_START_PRESSURE_BAR,
                DivingFields.GAS_MARGIN_L,
                DivingFields.GAS_MARGIN_BAR,
                DivingFields.RESERVE_MET,
                DivingFields.SEGMENTS_JSON
            ),
            safetyClass = "safety_supporting",
            calculator = ::calculateGasPlan
        )
    )

    val bailoutPlan = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.BAILOUT_PLAN,
            "Bailout gas plan",
            "Estimate bailout gas for a simplified problem-time, ascent and optional stop profile.",
            listOf(
                DivingFields.BAILOUT_REQUIRED_GAS_L,
                DivingFields.BAILOUT_REQUIRED_START_PRESSURE_BAR,
                DivingFields.BAILOUT_MARGIN_L,
                DivingFields.BAILOUT_MARGIN_BAR,
                DivingFields.BAILOUT_RESERVE_MET,
                DivingFields.BAILOUT_SEGMENTS_JSON
            ),
            safetyClass = "safety_supporting",
            calculator = ::calculateBailoutPlan
        )
    )

    val navigation = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.NAVIGATION,
            "Dive navigation helper",
            "Calculate reciprocal heading, distance and travel time from a simple speed estimate.",
            listOf(DivingFields.RECIPROCAL_HEADING_DEG, DivingFields.TRAVEL_TIME_MIN, DivingFields.TRAVEL_DISTANCE_M),
            safetyClass = "physics_support",
            calculator = ::calculateNavigation
        )
    )

    val lift = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.LIFT,
            "Lift bag physics",
            "Estimate minimum displaced-water volume and surface-equivalent inflation gas at depth.",
            listOf(DivingFields.LIFT_BAG_MIN_VOLUME_L, DivingFields.LIFT_SURFACE_EQUIVALENT_GAS_L),
            safetyClass = "safety_supporting",
            calculator = ::calculateLift
        )
    )

    val dashboard = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.DASHBOARD,
            "Dive planner dashboard",
            "Aggregate gas, ppO2, MOD, END and gas-density calculations into a persistent planning snapshot.",
            listOf(
                DivingFields.DASHBOARD_DEPTH_M,
                DivingFields.DASHBOARD_AMBIENT_PRESSURE_BAR,
                DivingFields.DASHBOARD_END_PRESSURE_BAR,
                DivingFields.DASHBOARD_GAS_MARGIN_L,
                DivingFields.DASHBOARD_GAS_MARGIN_BAR,
                DivingFields.DASHBOARD_PPO2_BAR,
                DivingFields.DASHBOARD_MOD_M,
                DivingFields.DASHBOARD_END_M,
                DivingFields.DASHBOARD_GAS_DENSITY_G_L,
                DivingFields.DASHBOARD_RESERVE_MET
            ),
            safetyClass = "safety_supporting_dashboard",
            calculator = ::calculateDashboard
        )
    )

    val allCore = listOf(
        sacRmv,
        cylinderCapacity,
        ppo2,
        mod,
        bestMix,
        ead,
        end,
        gasDensity,
        gasPlan,
        bailoutPlan,
        navigation,
        lift,
        dashboard
    )

    fun byId(id: String): DivingCalculationMethod? = allCore.firstOrNull { it.id == id }
}

private fun calculateSacRmv(s: Map<String, String>): Map<String, String> {
    val cylinder = s.d("cylinder_volume_l", 12.0)
    val start = s.d("start_pressure_bar", 200.0)
    val end = s.d("end_pressure_bar", 100.0)
    val depth = s.d("average_depth_m", 20.0)
    val duration = s.d("duration_min", 40.0)
    val density = s.waterDensity()
    val surface = s.surfacePressure()
    val result = DivingAlgorithms.sacRmv(cylinder, start, end, depth, duration, density, surface)
    return success(
        DivingIds.SAC_RMV,
        "physics_support",
        s,
        linkedMapOf(
            DivingFields.SAC_BAR_MIN to f(result.sacBarMin, 2),
            DivingFields.RMV_L_MIN to f(result.rmvLMin, 1),
            DivingFields.SURFACE_GAS_USED_L to f(result.surfaceGasUsedL, 0),
            DivingFields.PRESSURE_USED_BAR to f(result.pressureUsedBar, 1)
        ),
        warnings = emptyList(),
        assumptions = listOf("Cylinder gas volume uses pressure x water-volume nominal planning units.")
    )
}

private fun calculateCylinderCapacity(s: Map<String, String>): Map<String, String> {
    val cylinder = s.d("cylinder_volume_l", 12.0)
    val start = s.d("start_pressure_bar", 200.0)
    val reserve = s.d("reserve_pressure_bar", 50.0)
    val result = DivingAlgorithms.cylinderCapacity(cylinder, start, reserve)
    val warnings = buildList {
        if (start >= 200.0) add("Nominal pressure x water-volume estimate; real-gas compressibility is not modelled at high pressure.")
    }
    return success(
        DivingIds.CYLINDER_CAPACITY,
        "physics_support",
        s,
        linkedMapOf(
            DivingFields.CYLINDER_TOTAL_GAS_L to f(result.totalGasAtStartL, 0),
            DivingFields.CYLINDER_RESERVE_GAS_L to f(result.reserveGasL, 0),
            DivingFields.CYLINDER_USABLE_GAS_L to f(result.usableGasL, 0)
        ),
        warnings,
        assumptions = listOf("Nominal idealised cylinder-volume planning approximation.")
    )
}

private fun calculatePpo2(s: Map<String, String>): Map<String, String> {
    val gas = s.gasMix()
    val depth = s.d("depth_m", 30.0)
    val limit = s.d("ppo2_limit_bar", 1.4)
    val value = DivingAlgorithms.ppo2Bar(gas, depth, s.waterDensity(), s.surfacePressure())
    val warnings = buildList {
        addAll(ppo2LimitWarnings(limit))
        if (value > limit + 1e-9) add("Calculated ppO2 exceeds the configured limit.")
    }
    return success(
        DivingIds.PPO2,
        "safety_supporting",
        s,
        linkedMapOf(DivingFields.PPO2_BAR to f(value, 2)),
        warnings,
        assumptions = listOf("Dalton-law calculation using analysed gas fractions and hydrostatic ambient pressure.")
    )
}

private fun calculateMod(s: Map<String, String>): Map<String, String> {
    val gas = s.gasMix()
    val limit = s.d("ppo2_limit_bar", 1.4)
    val value = DivingAlgorithms.modM(gas, limit, s.waterDensity(), s.surfacePressure())
    return success(
        DivingIds.MOD,
        "safety_supporting",
        s,
        linkedMapOf(DivingFields.MOD_M to f(value, 1)),
        ppo2LimitWarnings(limit),
        assumptions = listOf("MOD is a mathematical ppO2 boundary, not a statement that the resulting depth is safe for a diver or dive plan.")
    )
}

private fun calculateBestMix(s: Map<String, String>): Map<String, String> {
    val depth = s.d("depth_m", 30.0)
    val limit = s.d("ppo2_limit_bar", 1.4)
    val fo2 = DivingAlgorithms.bestMixFo2(depth, limit, s.waterDensity(), s.surfacePressure())
    return success(
        DivingIds.BEST_MIX,
        "safety_supporting",
        s,
        linkedMapOf(DivingFields.BEST_MIX_FO2_PERCENT to f(fo2 * 100.0, 1)),
        ppo2LimitWarnings(limit),
        assumptions = listOf("Best mix means the maximum FO2 satisfying only the configured ppO2 boundary at the selected depth; decompression and gas-density considerations are separate.")
    )
}

private fun calculateEad(s: Map<String, String>): Map<String, String> {
    val gas = s.gasMix()
    val depth = s.d("depth_m", 30.0)
    val ead = DivingAlgorithms.equivalentAirDepthM(gas, depth, s.waterDensity(), s.surfacePressure())
    return success(
        DivingIds.EAD,
        "safety_supporting",
        s,
        linkedMapOf(DivingFields.EAD_M to f(ead, 1)),
        warnings = listOf("EAD is an inert-gas equivalence calculation, not a decompression schedule or no-decompression-limit calculation."),
        assumptions = listOf("Nitrox/air only; helium must be zero.", "Air nitrogen fraction = ${DivingAlgorithms.AIR_NITROGEN_FRACTION}.")
    )
}

private fun calculateEnd(s: Map<String, String>): Map<String, String> {
    val gas = s.gasMix()
    val depth = s.d("depth_m", 40.0)
    val model = s.v("narcotic_model") ?: "oxygen_and_nitrogen"
    val end = DivingAlgorithms.equivalentNarcoticDepthM(gas, depth, model, s.waterDensity(), s.surfacePressure())
    return success(
        DivingIds.END,
        "safety_supporting",
        s,
        linkedMapOf(DivingFields.END_M to f(end, 1)),
        warnings = listOf("END depends on the selected narcotic-gas assumption; it is not a validated impairment threshold."),
        assumptions = listOf("Narcotic model: $model")
    )
}

private fun calculateGasDensity(s: Map<String, String>): Map<String, String> {
    val gas = s.gasMix()
    val depth = s.d("depth_m", 40.0)
    val temperature = s.d("gas_temperature_c", 20.0)
    val result = DivingAlgorithms.gasDensity(gas, depth, temperature, s.waterDensity(), s.surfacePressure())
    val warnings = buildList {
        when {
            result.densityGL > 6.2 -> add("Estimated gas density exceeds 6.2 g/L, a commonly cited upper planning boundary; review the gas/depth and applicable guidance.")
            result.densityGL > 5.2 -> add("Estimated gas density exceeds 5.2 g/L, a commonly cited preferred planning boundary; review work of breathing and applicable guidance.")
        }
        add("Ideal-gas estimate; regulator/rebreather work of breathing and diver physiology are not modelled.")
    }
    return success(
        DivingIds.GAS_DENSITY,
        "safety_supporting",
        s,
        linkedMapOf(
            DivingFields.GAS_DENSITY_G_L to f(result.densityGL, 2),
            DivingFields.GAS_MOLAR_MASS_G_MOL to f(result.molarMassGmol, 2)
        ),
        warnings,
        assumptions = listOf("Ideal-gas law with O2/N2/He molar fractions.")
    )
}

private fun calculateGasPlan(s: Map<String, String>): Map<String, String> {
    val segmentsText = s.v("segments") ?: "20:20;5:3"
    val segments = DivingAlgorithms.parseSegments(segmentsText)
    val result = DivingAlgorithms.gasPlan(
        baseRmvLMin = s.d("rmv_l_min", 18.0),
        cylinderWaterVolumeL = s.d("cylinder_volume_l", 12.0),
        startPressureBar = s.d("start_pressure_bar", 200.0),
        reservePressureBar = s.d("reserve_pressure_bar", 50.0),
        segments = segments,
        waterDensityKgM3 = s.waterDensity(),
        surfacePressureBar = s.surfacePressure()
    )
    val segmentJson = segmentArray(result.segments).toString()
    val warnings = buildList {
        if (!result.meetsReserve) add("Planned gas demand exceeds usable gas above the configured reserve.")
        add("This is a gas-volume calculation only; it does not generate decompression obligations, contingency stops or team gas-sharing rules.")
    }
    return success(
        DivingIds.GAS_PLAN,
        "safety_supporting",
        s,
        linkedMapOf(
            DivingFields.REQUIRED_GAS_L to f(result.totalSurfaceGasL, 0),
            DivingFields.REQUIRED_START_PRESSURE_BAR to f(result.requiredStartPressureBar, 1),
            DivingFields.GAS_MARGIN_L to f(result.marginGasL, 0),
            DivingFields.GAS_MARGIN_BAR to f(result.marginPressureBar, 1),
            DivingFields.RESERVE_MET to result.meetsReserve.toString(),
            DivingFields.SEGMENTS_JSON to segmentJson
        ),
        warnings,
        assumptions = listOf("Each segment uses its stated depth, duration and RMV multiplier.", "Cylinder volume uses nominal pressure x water-volume planning units.")
    )
}

private fun calculateBailoutPlan(s: Map<String, String>): Map<String, String> {
    val result = DivingAlgorithms.bailoutPlan(
        maxDepthM = s.d("max_depth_m", 30.0),
        problemTimeMin = s.d("problem_time_min", 1.0),
        stressedRmvLMin = s.d("stressed_rmv_l_min", 30.0),
        ascentRateMMin = s.d("ascent_rate_m_min", 9.0),
        stopDepthM = s.d("stop_depth_m", 5.0),
        stopTimeMin = s.d("stop_time_min", 3.0),
        cylinderWaterVolumeL = s.d("cylinder_volume_l", 7.0),
        startPressureBar = s.d("start_pressure_bar", 200.0),
        reservePressureBar = s.d("reserve_pressure_bar", 30.0),
        waterDensityKgM3 = s.waterDensity(),
        surfacePressureBar = s.surfacePressure()
    )
    val warnings = buildList {
        if (!result.meetsReserve) add("Calculated bailout demand exceeds usable gas above the configured reserve.")
        add("Simplified bailout gas arithmetic only. It is not a decompression schedule, emergency procedure, team plan or equipment-specific bailout standard.")
    }
    return success(
        DivingIds.BAILOUT_PLAN,
        "safety_supporting",
        s,
        linkedMapOf(
            DivingFields.BAILOUT_REQUIRED_GAS_L to f(result.totalSurfaceGasL, 0),
            DivingFields.BAILOUT_REQUIRED_START_PRESSURE_BAR to f(result.requiredStartPressureBar, 1),
            DivingFields.BAILOUT_MARGIN_L to f(result.marginGasL, 0),
            DivingFields.BAILOUT_MARGIN_BAR to f(result.marginPressureBar, 1),
            DivingFields.BAILOUT_RESERVE_MET to result.meetsReserve.toString(),
            DivingFields.BAILOUT_SEGMENTS_JSON to segmentArray(result.segments).toString()
        ),
        warnings,
        assumptions = listOf("Ascent gas uses average depth for each ascent leg.", "Only the explicitly entered stop is represented.")
    )
}

private fun calculateNavigation(s: Map<String, String>): Map<String, String> {
    val heading = s.d("heading_deg", 0.0)
    val distance = s.d("distance_m", 0.0)
    val time = s.d("time_min", 0.0)
    val speed = s.d("speed_m_min", 15.0)
    val reciprocal = DivingAlgorithms.reciprocalHeadingDeg(heading)
    val travelTime = if (distance > 0.0) DivingAlgorithms.travelTimeMin(distance, speed) else time
    val travelDistance = if (time > 0.0) DivingAlgorithms.travelDistanceM(time, speed) else distance
    return success(
        DivingIds.NAVIGATION,
        "physics_support",
        s,
        linkedMapOf(
            DivingFields.RECIPROCAL_HEADING_DEG to f(reciprocal, 0),
            DivingFields.TRAVEL_TIME_MIN to f(travelTime, 1),
            DivingFields.TRAVEL_DISTANCE_M to f(travelDistance, 1)
        ),
        warnings = listOf("Does not account for current, magnetic variation, compass error, terrain or diver-specific kick-count calibration."),
        assumptions = listOf("Constant entered swim speed.")
    )
}

private fun calculateLift(s: Map<String, String>): Map<String, String> {
    val result = DivingAlgorithms.liftBag(
        liftMassKg = s.d("lift_mass_kg", 10.0),
        depthM = s.d("depth_m", 20.0),
        waterDensityKgM3 = s.waterDensity(),
        surfacePressureBar = s.surfacePressure()
    )
    return success(
        DivingIds.LIFT,
        "safety_supporting",
        s,
        linkedMapOf(
            DivingFields.LIFT_BAG_MIN_VOLUME_L to f(result.minimumBagVolumeL, 1),
            DivingFields.LIFT_SURFACE_EQUIVALENT_GAS_L to f(result.surfaceEquivalentFillGasL, 0)
        ),
        warnings = listOf("Minimum physics estimate only; bag rating, rigging, dynamic lift, trapped gas, entanglement and controlled venting are not modelled."),
        assumptions = listOf("Neutral lifting estimate from displaced water; no safety factor is added.")
    )
}

private fun calculateDashboard(s: Map<String, String>): Map<String, String> {
    val depth = s.d("depth_m", 30.0)
    val duration = s.d("duration_min", 25.0)
    val cylinder = s.d("cylinder_volume_l", 12.0)
    val start = s.d("start_pressure_bar", 200.0)
    val reserve = s.d("reserve_pressure_bar", 50.0)
    val rmv = s.d("rmv_l_min", 18.0)
    val gas = s.gasMix()
    val ppo2Limit = s.d("ppo2_limit_bar", 1.4)
    val densityWater = s.waterDensity()
    val surface = s.surfacePressure()
    val gasTemp = s.d("gas_temperature_c", 20.0)
    val narcoticModel = s.v("narcotic_model") ?: "oxygen_and_nitrogen"

    val ambient = DivingAlgorithms.ambientPressureBar(depth, densityWater, surface)
    val ppo2 = DivingAlgorithms.ppo2Bar(gas, depth, densityWater, surface)
    val mod = DivingAlgorithms.modM(gas, ppo2Limit, densityWater, surface)
    val end = DivingAlgorithms.equivalentNarcoticDepthM(gas, depth, narcoticModel, densityWater, surface)
    val gasDensity = DivingAlgorithms.gasDensity(gas, depth, gasTemp, densityWater, surface).densityGL
    val plan = DivingAlgorithms.gasPlan(
        rmv,
        cylinder,
        start,
        reserve,
        listOf(DivingAlgorithms.Segment("planned_bottom", depth, duration)),
        densityWater,
        surface
    )
    val endPressure = start - plan.plannedPressureDropBar

    val warnings = buildList {
        addAll(ppo2LimitWarnings(ppo2Limit))
        if (ppo2 > ppo2Limit + 1e-9) add("Current depth/mix exceeds the configured ppO2 limit.")
        if (!plan.meetsReserve) add("Planned bottom segment consumes gas below the configured reserve.")
        when {
            gasDensity > 6.2 -> add("Estimated gas density exceeds 6.2 g/L.")
            gasDensity > 5.2 -> add("Estimated gas density exceeds 5.2 g/L.")
        }
        add("Dashboard is planning support only and does not calculate decompression obligations or certify a dive as safe.")
    }
    return success(
        DivingIds.DASHBOARD,
        "safety_supporting_dashboard",
        s,
        linkedMapOf(
            DivingFields.DASHBOARD_DEPTH_M to f(depth, 1),
            DivingFields.DASHBOARD_AMBIENT_PRESSURE_BAR to f(ambient, 2),
            DivingFields.DASHBOARD_END_PRESSURE_BAR to f(endPressure, 1),
            DivingFields.DASHBOARD_GAS_MARGIN_L to f(plan.marginGasL, 0),
            DivingFields.DASHBOARD_GAS_MARGIN_BAR to f(plan.marginPressureBar, 1),
            DivingFields.DASHBOARD_PPO2_BAR to f(ppo2, 2),
            DivingFields.DASHBOARD_MOD_M to f(mod, 1),
            DivingFields.DASHBOARD_END_M to f(end, 1),
            DivingFields.DASHBOARD_GAS_DENSITY_G_L to f(gasDensity, 2),
            DivingFields.DASHBOARD_RESERVE_MET to plan.meetsReserve.toString()
        ),
        warnings,
        assumptions = listOf(
            "Gas card uses one constant-depth bottom segment.",
            "Narcotic model: $narcoticModel.",
            "Gas density uses ideal-gas law."
        )
    )
}

internal fun success(
    methodId: String,
    safetyClass: String,
    inputs: Map<String, String>,
    outputs: Map<String, String>,
    warnings: List<String>,
    assumptions: List<String> = emptyList()
): Map<String, String> {
    val warningText = warnings.filter { it.isNotBlank() }.distinct().joinToString(" | ")
    val values = linkedMapOf<String, String>()
    values.putAll(outputs)
    values[DivingCommonFields.STATUS] = "succeeded"
    values[DivingCommonFields.WARNING] = warningText
    values[DivingCommonFields.CALCULATED_TIME_ISO] = Instant.now().toString()
    values[DivingCommonFields.ERROR] = ""
    values[DivingCommonFields.AUDIT_JSON] = auditJson(methodId, safetyClass, inputs, outputs, warnings, assumptions = assumptions)
    return values
}

internal fun auditJson(
    methodId: String,
    safetyClass: String,
    inputs: Map<String, String>,
    outputs: Map<String, String>,
    warnings: List<String>,
    error: String = "",
    assumptions: List<String> = emptyList()
): String = JSONObject().apply {
    put("method_id", methodId)
    put("module", "diving")
    put("prototype_version", "0.1.0")
    put("safety_class", safetyClass)
    put("calculated_time_iso", Instant.now().toString())
    put("inputs", JSONObject(inputs))
    put("outputs", JSONObject(outputs))
    put("warnings", JSONArray(warnings))
    put("assumptions", JSONArray(assumptions))
    put("not_implemented", JSONArray(listOf(
        "decompression schedules",
        "no-decompression limits",
        "repetitive-dive tissue loading",
        "omitted-decompression procedures",
        "treatment tables",
        "equipment/manufacturer-specific supply settings"
    )))
    if (error.isNotBlank()) put("error", error)
}.toString()

private fun segmentArray(segments: List<DivingAlgorithms.SegmentGas>) = JSONArray().apply {
    segments.forEach { segment ->
        put(JSONObject().apply {
            put("label", segment.label)
            put("depth_m", segment.depthM)
            put("minutes", segment.minutes)
            put("rmv_multiplier", segment.rmvMultiplier)
            put("ambient_pressure_bar", segment.ambientPressureBar)
            put("surface_gas_l", segment.surfaceGasL)
        })
    }
}

private fun ppo2LimitWarnings(limit: Double): List<String> = buildList {
    if (limit > 1.6 + 1e-9) add("Configured ppO2 limit exceeds 1.6 bar; this is outside the recreational reference range used by this prototype documentation.")
    else if (limit > 1.4 + 1e-9) add("Configured ppO2 limit exceeds the commonly used 1.4 bar recreational working reference; apply your training, organisation and exposure conditions.")
}

internal fun Map<String, String>.v(key: String): String? =
    (this[key] ?: this["input_$key"])?.trim()?.takeIf { it.isNotBlank() }

internal fun Map<String, String>.d(key: String, default: Double): Double =
    v(key)?.toDoubleOrNull() ?: default

internal fun Map<String, String>.waterDensity(): Double = when ((v("water_type") ?: "seawater").lowercase()) {
    "freshwater" -> d("water_density_kg_m3", DivingAlgorithms.DEFAULT_FRESHWATER_DENSITY_KG_M3)
    "custom" -> d("water_density_kg_m3", DivingAlgorithms.DEFAULT_SEAWATER_DENSITY_KG_M3)
    else -> d("water_density_kg_m3", DivingAlgorithms.DEFAULT_SEAWATER_DENSITY_KG_M3)
}

internal fun Map<String, String>.surfacePressure(): Double = d("surface_pressure_bar", DivingAlgorithms.STANDARD_SURFACE_PRESSURE_BAR)

internal fun Map<String, String>.gasMix(): DivingAlgorithms.GasMix {
    val fo2 = d("fo2_percent", 21.0) / 100.0
    val fhe = d("fhe_percent", 0.0) / 100.0
    return DivingAlgorithms.GasMix(fo2, fhe)
}

internal fun f(value: Double, decimals: Int): String = String.format(Locale.US, "%.${decimals}f", value)
