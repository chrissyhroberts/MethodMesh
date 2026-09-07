package com.example.methodmesh.modules.labbench

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
import org.json.JSONObject

class LabBenchMethod(
    override val id: String,
    val calculatorKey: String,
    name: String,
    description: String,
    val outputs: List<String>,
    val statusField: String,
    val errorField: String,
    private val calculator: (Map<String, String>) -> Map<String, String>
) : As100Method {
    override val ref = ArchitectureRef(ArchitectureId(id), "Method", name)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = name,
        version = LabBenchEngine.VERSION,
        description = description,
        outputs = outputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Development", "status" to "Development", "domain" to "Laboratory")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    fun calculate(settings: Map<String, String>): Map<String, String> = calculator(settings)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[statusField] == "succeeded"
        val entity = Entity(ArchitectureId("labbench:$calculatorKey:${System.currentTimeMillis()}"), "LaboratoryCalculation", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("methodmesh.labbench", id, LabBenchEngine.VERSION)
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
            diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }
}

object LabBenchMethods {
    private fun fields(prefix: String, vararg core: String): List<String> =
        listOf("${prefix}_status") + core.toList() + listOf("${prefix}_instruction", "${prefix}_audit_json", "${prefix}_error")

    val Dilution = LabBenchMethod(
        id = "labbench.dilution", calculatorKey = "dilution", name = "Dilution calculator",
        description = "Calculate stock and diluent volumes with unit-aware C1V1=C2V2 logic.",
        outputs = fields("lab_dilution", "lab_dilution_stock_volume", "lab_dilution_stock_volume_unit", "lab_dilution_diluent_volume", "lab_dilution_diluent_volume_unit", "lab_dilution_final_volume", "lab_dilution_final_volume_unit", "lab_dilution_factor"),
        statusField = "lab_dilution_status", errorField = "lab_dilution_error", calculator = LabBenchEngine::dilution
    )
    val MolarSolution = LabBenchMethod(
        id = "labbench.molar_solution", calculatorKey = "molar_solution", name = "Molar solution",
        description = "Calculate mass, molarity or volume for preparing molar solutions.",
        outputs = fields("lab_molar_solution", "lab_molar_solution_mode", "lab_molar_solution_mass", "lab_molar_solution_mass_unit", "lab_molar_solution_molarity", "lab_molar_solution_molarity_unit", "lab_molar_solution_volume", "lab_molar_solution_volume_unit", "lab_molar_solution_moles"),
        statusField = "lab_molar_solution_status", errorField = "lab_molar_solution_error", calculator = LabBenchEngine::molarSolution
    )
    val Reconstitute = LabBenchMethod(
        id = "labbench.reconstitute", calculatorKey = "reconstitute", name = "Reagent reconstitution",
        description = "Calculate the final solvent volume required to reconstitute a reagent to a target concentration.",
        outputs = fields("lab_reconstitute", "lab_reconstitute_final_volume", "lab_reconstitute_final_volume_unit"),
        statusField = "lab_reconstitute_status", errorField = "lab_reconstitute_error", calculator = LabBenchEngine::reconstitute
    )
    val SerialDilution = LabBenchMethod(
        id = "labbench.serial_dilution", calculatorKey = "serial_dilution", name = "Serial dilution planner",
        description = "Plan a factor-based serial dilution series with transfer and diluent volumes.",
        outputs = fields("lab_serial_dilution", "lab_serial_dilution_step_count", "lab_serial_dilution_start_concentration", "lab_serial_dilution_end_concentration", "lab_serial_dilution_concentration_unit", "lab_serial_dilution_transfer_volume", "lab_serial_dilution_transfer_volume_unit", "lab_serial_dilution_diluent_per_step", "lab_serial_dilution_diluent_per_step_unit", "lab_serial_dilution_total_diluent", "lab_serial_dilution_total_diluent_unit", "lab_serial_dilution_steps_json"),
        statusField = "lab_serial_dilution_status", errorField = "lab_serial_dilution_error", calculator = LabBenchEngine::serialDilution
    )
    val MasterMix = LabBenchMethod(
        id = "labbench.master_mix", calculatorKey = "master_mix", name = "Master-mix calculator",
        description = "Scale multi-reagent reaction mixes with overage and excluded per-sample components.",
        outputs = fields("lab_master_mix", "lab_master_mix_effective_reaction_count", "lab_master_mix_master_mix_per_reaction", "lab_master_mix_master_mix_total", "lab_master_mix_component_volume_unit", "lab_master_mix_reaction_total_volume", "lab_master_mix_components_json", "lab_master_mix_warning"),
        statusField = "lab_master_mix_status", errorField = "lab_master_mix_error", calculator = LabBenchEngine::masterMix
    )
    val Centrifuge = LabBenchMethod(
        id = "labbench.centrifuge", calculatorKey = "centrifuge", name = "Centrifuge RCF / RPM",
        description = "Convert between relative centrifugal force and RPM using rotational radius.",
        outputs = fields("lab_centrifuge", "lab_centrifuge_rpm", "lab_centrifuge_rcf", "lab_centrifuge_radius_cm"),
        statusField = "lab_centrifuge_status", errorField = "lab_centrifuge_error", calculator = LabBenchEngine::centrifuge
    )
    val Concentration = LabBenchMethod(
        id = "labbench.concentration", calculatorKey = "concentration", name = "Concentration converter",
        description = "Convert laboratory concentration units with explicit molecular-weight and density requirements for cross-dimension conversions.",
        outputs = fields("lab_concentration", "lab_concentration_input_value", "lab_concentration_input_unit", "lab_concentration_value", "lab_concentration_unit", "lab_concentration_assumptions"),
        statusField = "lab_concentration_status", errorField = "lab_concentration_error", calculator = LabBenchEngine::concentration
    )
    val NucleicAcid = LabBenchMethod(
        id = "labbench.nucleic_acid", calculatorKey = "nucleic_acid", name = "Nucleic-acid calculator",
        description = "Convert DNA/RNA mass, molarity and approximate molecule copy number using explicit molecular-weight conventions.",
        outputs = fields("lab_nucleic_acid", "lab_nucleic_acid_mode", "lab_nucleic_acid_molecular_weight_g_mol", "lab_nucleic_acid_assumptions", "lab_nucleic_acid_molarity", "lab_nucleic_acid_molarity_unit", "lab_nucleic_acid_mass_concentration", "lab_nucleic_acid_mass_concentration_unit", "lab_nucleic_acid_copy_number", "lab_nucleic_acid_copies_per_ul", "lab_nucleic_acid_final_volume", "lab_nucleic_acid_final_volume_unit"),
        statusField = "lab_nucleic_acid_status", errorField = "lab_nucleic_acid_error", calculator = LabBenchEngine::nucleicAcid
    )
    val CellDilution = LabBenchMethod(
        id = "labbench.cell_dilution", calculatorKey = "cell_dilution", name = "Cell / OD dilution",
        description = "Calculate sample and medium volumes for cell concentration or OD dilutions.",
        outputs = fields("lab_cell_dilution", "lab_cell_dilution_sample_volume", "lab_cell_dilution_sample_volume_unit", "lab_cell_dilution_medium_volume", "lab_cell_dilution_medium_volume_unit", "lab_cell_dilution_final_volume", "lab_cell_dilution_final_volume_unit", "lab_cell_dilution_concentration_unit"),
        statusField = "lab_cell_dilution_status", errorField = "lab_cell_dilution_error", calculator = LabBenchEngine::cellDilution
    )
    val Hemocytometer = LabBenchMethod(
        id = "labbench.hemocytometer", calculatorKey = "hemocytometer", name = "Hemocytometer calculator",
        description = "Convert counted cells, chamber volume and dilution into cells/mL with optional viability.",
        outputs = fields("lab_hemocytometer", "lab_hemocytometer_cells_per_ml", "lab_hemocytometer_mean_cells_per_region", "lab_hemocytometer_viability_percent"),
        statusField = "lab_hemocytometer_status", errorField = "lab_hemocytometer_error", calculator = LabBenchEngine::hemocytometer
    )
    val Aliquot = LabBenchMethod(
        id = "labbench.aliquot", calculatorKey = "aliquot", name = "Aliquot planner",
        description = "Plan aliquot count or aliquot size while preserving reserve and dead-volume allowances.",
        outputs = fields("lab_aliquot", "lab_aliquot_count", "lab_aliquot_aliquot_volume", "lab_aliquot_aliquot_volume_unit", "lab_aliquot_dispense_volume", "lab_aliquot_dispense_volume_unit", "lab_aliquot_remainder", "lab_aliquot_remainder_unit", "lab_aliquot_reserve", "lab_aliquot_reserve_unit"),
        statusField = "lab_aliquot_status", errorField = "lab_aliquot_error", calculator = LabBenchEngine::aliquot
    )
    val PercentSolution = LabBenchMethod(
        id = "labbench.percent_solution", calculatorKey = "percent_solution", name = "Percent solution",
        description = "Prepare % w/v, % v/v and % w/w solutions without silently assuming density.",
        outputs = fields("lab_percent_solution", "lab_percent_solution_component_amount", "lab_percent_solution_component_unit", "lab_percent_solution_remainder_amount", "lab_percent_solution_remainder_unit"),
        statusField = "lab_percent_solution_status", errorField = "lab_percent_solution_error", calculator = LabBenchEngine::percentSolution
    )

    val Dashboard = LabBenchMethod(
        id = "labbench.dashboard", calculatorKey = "dashboard", name = "Lab Bench dashboard",
        description = "Persistent native dashboard for MethodMesh laboratory calculations; external callers can select one calculator for a single-shot result.",
        outputs = listOf("labbench_dashboard_status", "labbench_dashboard_tool", "labbench_dashboard_value", "labbench_dashboard_instruction", "labbench_dashboard_audit_json", "labbench_dashboard_error"),
        statusField = "labbench_dashboard_status", errorField = "labbench_dashboard_error",
        calculator = { settings -> dashboard(settings) }
    )

    val atomic = listOf(Dilution, MolarSolution, Reconstitute, SerialDilution, MasterMix, Centrifuge, Concentration, NucleicAcid, CellDilution, Hemocytometer, Aliquot, PercentSolution)
    val all = atomic + Dashboard

    private fun dashboard(settings: Map<String, String>): Map<String, String> {
        val tool = settings["calculator"] ?: settings["input_calculator"] ?: "dilution"
        val source = LabBenchEngine.calculate(tool, settings)
        val status = source.entries.firstOrNull { it.key.endsWith("_status") }?.value ?: "failed"
        val instruction = source.entries.firstOrNull { it.key.endsWith("_instruction") }?.value.orEmpty()
        val error = source.entries.firstOrNull { it.key.endsWith("_error") }?.value.orEmpty()
        val json = JSONObject()
            .put("method", "labbench.dashboard")
            .put("version", LabBenchEngine.VERSION)
            .put("calculator", tool)
            .put("result", JSONObject(source))
            .toString()
        return linkedMapOf(
            "labbench_dashboard_status" to status,
            "labbench_dashboard_tool" to tool,
            "labbench_dashboard_value" to instruction,
            "labbench_dashboard_instruction" to instruction,
            "labbench_dashboard_audit_json" to json,
            "labbench_dashboard_error" to error
        )
    }
}
