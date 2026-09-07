package com.example.methodmesh.modules.electrical

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
import java.util.Locale

object ElectricalFields {
    const val RESULT = "electrical_result"
    const val STATUS = "electrical_status"
    const val ERROR = "electrical_error"
    const val TOOL = "electrical_tool"
    const val PRIMARY_VALUE = "electrical_primary_value"
    const val PRIMARY_UNIT = "electrical_primary_unit"
    const val VOLTAGE_V = "electrical_voltage_v"
    const val CURRENT_A = "electrical_current_a"
    const val RESISTANCE_OHM = "electrical_resistance_ohm"
    const val POWER_W = "electrical_power_w"
    const val APPARENT_POWER_VA = "electrical_apparent_power_va"
    const val REACTIVE_POWER_VAR = "electrical_reactive_power_var"
    const val DROP_V = "electrical_drop_v"
    const val DROP_PERCENT = "electrical_drop_percent"
    const val LOAD_VOLTAGE_V = "electrical_load_voltage_v"
    const val EQUIVALENT = "electrical_equivalent"
    const val ENERGY_WH = "electrical_energy_wh"
    const val RUNTIME_HOURS = "electrical_runtime_hours"
    const val TAU_SECONDS = "electrical_tau_seconds"
    const val CUTOFF_HZ = "electrical_cutoff_hz"
    const val TOLERANCE_PERCENT = "electrical_tolerance_percent"
    const val WARNING = "electrical_warning"

    val outputs = listOf(
        RESULT, STATUS, ERROR, TOOL, PRIMARY_VALUE, PRIMARY_UNIT,
        VOLTAGE_V, CURRENT_A, RESISTANCE_OHM, POWER_W, APPARENT_POWER_VA,
        REACTIVE_POWER_VAR, DROP_V, DROP_PERCENT, LOAD_VOLTAGE_V,
        EQUIVALENT, ENERGY_WH, RUNTIME_HOURS, TAU_SECONDS, CUTOFF_HZ,
        TOLERANCE_PERCENT, WARNING
    )
}

object As100ElectricalWorkbenchMethod : As100Method {
    const val ID = "electrical.workbench"
    const val VERSION = "0.2.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Electrical workbench")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Electrical workbench",
        version = VERSION,
        description = "Offline electrical calculator covering Ohm's law, AC power, voltage drop, networks, energy, time constants and resistor colour codes.",
        inputs = listOf(
            "tool", "voltage_v", "current_a", "resistance_ohm", "power_w",
            "phase", "power_factor", "length_m", "conductor_area_mm2",
            "material", "temperature_c", "arrangement", "network_type", "values",
            "duration_hours", "capacity_ah", "capacitance_f", "inductance_h", "bands"
        ),
        outputs = ElectricalFields.outputs,
        graphOutputs = listOf("electrical.calculation"),
        parameters = mapOf("category" to "Engineering", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(
        ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult {
        val values = calculate(request.context)
        val ok = values[ElectricalFields.STATUS] == "succeeded"
        val entity = Entity(
            id = ArchitectureId("electrical:${System.currentTimeMillis()}"),
            entityType = "ElectricalCalculation",
            attributes = values,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.electrical", ID, VERSION)
        val observation = Observation(
            phenomenon = "electrical.calculation",
            subject = ArchitectureRef(entity.id, entity.objectType, "Electrical calculation"),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
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
            diagnostics = if (ok) emptyMap() else mapOf(ElectricalFields.ERROR to values[ElectricalFields.ERROR].orEmpty())
        ).withInvocationContext(InvocationContext.from(request.context))
    }

    fun calculate(context: Map<String, String>): Map<String, String> = runCatching {
        val tool = context["tool"]?.trim()?.lowercase().orEmpty().ifBlank { "ohms_law" }
        val out = ElectricalFields.outputs.associateWith { "" }.toMutableMap()
        out[ElectricalFields.STATUS] = "succeeded"
        out[ElectricalFields.TOOL] = tool
        out[ElectricalFields.WARNING] = safetyNote(tool)
        when (tool) {
            "ohms_law" -> {
                val r = ElectricalAlgorithms.ohmsLaw(d(context,"voltage_v"), d(context,"current_a"), d(context,"resistance_ohm"), d(context,"power_w"))
                out[ElectricalFields.VOLTAGE_V] = f(r.voltageV); out[ElectricalFields.CURRENT_A] = f(r.currentA)
                out[ElectricalFields.RESISTANCE_OHM] = f(r.resistanceOhm); out[ElectricalFields.POWER_W] = f(r.powerW)
                out[ElectricalFields.PRIMARY_VALUE] = f(r.powerW); out[ElectricalFields.PRIMARY_UNIT] = "W"
                out[ElectricalFields.RESULT] = "${f(r.voltageV)} V · ${f(r.currentA)} A · ${f(r.resistanceOhm)} Ω · ${f(r.powerW)} W"
            }
            "ac_power" -> {
                val v = req(context,"voltage_v"); val i = req(context,"current_a")
                val r = ElectricalAlgorithms.acPower(context["phase"] ?: "single_phase", v, i, d(context,"power_factor") ?: 1.0)
                out[ElectricalFields.VOLTAGE_V] = f(v); out[ElectricalFields.CURRENT_A] = f(i)
                out[ElectricalFields.POWER_W] = f(r.realPowerW); out[ElectricalFields.APPARENT_POWER_VA] = f(r.apparentPowerVa); out[ElectricalFields.REACTIVE_POWER_VAR] = f(r.reactivePowerVar)
                out[ElectricalFields.PRIMARY_VALUE] = f(r.realPowerW); out[ElectricalFields.PRIMARY_UNIT] = "W"
                out[ElectricalFields.RESULT] = "${f(r.realPowerW)} W · ${f(r.apparentPowerVa)} VA · ${f(r.reactivePowerVar)} var"
            }
            "voltage_drop" -> {
                val v = req(context,"voltage_v")
                val r = ElectricalAlgorithms.voltageDrop(v, req(context,"current_a"), req(context,"length_m"), req(context,"conductor_area_mm2"), context["phase"] ?: "single_phase", context["material"] ?: "copper", d(context,"temperature_c") ?: 20.0, d(context,"power_factor") ?: 1.0)
                out[ElectricalFields.DROP_V] = f(r.dropV); out[ElectricalFields.DROP_PERCENT] = f(r.dropPercent); out[ElectricalFields.LOAD_VOLTAGE_V] = f(r.loadVoltageV)
                out[ElectricalFields.PRIMARY_VALUE] = f(r.dropPercent); out[ElectricalFields.PRIMARY_UNIT] = "%"
                out[ElectricalFields.RESULT] = "${f(r.dropV)} V drop · ${f(r.dropPercent)}% · ${f(r.loadVoltageV)} V at load"
            }
            "network" -> {
                val values = ElectricalAlgorithms.parseNumberList(context["values"].orEmpty())
                val type = context["network_type"]?.lowercase() ?: "resistance"
                val r = if (type == "capacitance") ElectricalAlgorithms.equivalentCapacitance(values, context["arrangement"] ?: "parallel") else ElectricalAlgorithms.equivalentResistance(values, context["arrangement"] ?: "series")
                out[ElectricalFields.EQUIVALENT] = f(r.equivalent); out[ElectricalFields.PRIMARY_VALUE] = f(r.equivalent)
                out[ElectricalFields.PRIMARY_UNIT] = if (type == "capacitance") "F" else "Ω"
                out[ElectricalFields.RESULT] = "Equivalent: ${f(r.equivalent)} ${out[ElectricalFields.PRIMARY_UNIT]}"
            }
            "energy" -> {
                val r = ElectricalAlgorithms.energy(d(context,"voltage_v"), d(context,"current_a"), d(context,"power_w"), d(context,"duration_hours"), d(context,"capacity_ah"))
                out[ElectricalFields.POWER_W] = f(r.powerW); out[ElectricalFields.ENERGY_WH] = f(r.energyWh); out[ElectricalFields.CURRENT_A] = r.currentA?.let(::f).orEmpty(); out[ElectricalFields.RUNTIME_HOURS] = r.runtimeHours?.let(::f).orEmpty()
                out[ElectricalFields.PRIMARY_VALUE] = f(r.energyWh); out[ElectricalFields.PRIMARY_UNIT] = "Wh"
                out[ElectricalFields.RESULT] = "${f(r.powerW)} W · ${f(r.energyWh)} Wh" + (r.runtimeHours?.let { " · ${f(it)} h" } ?: "")
            }
            "rc" -> {
                val r = ElectricalAlgorithms.rcTimeConstant(req(context,"resistance_ohm"), req(context,"capacitance_f"))
                out[ElectricalFields.TAU_SECONDS] = f(r.tauSeconds); out[ElectricalFields.CUTOFF_HZ] = r.cutoffHz?.let(::f).orEmpty(); out[ElectricalFields.PRIMARY_VALUE] = f(r.tauSeconds); out[ElectricalFields.PRIMARY_UNIT] = "s"
                out[ElectricalFields.RESULT] = "τ ${f(r.tauSeconds)} s · fc ${r.cutoffHz?.let(::f)} Hz"
            }
            "rl" -> {
                val r = ElectricalAlgorithms.rlTimeConstant(req(context,"inductance_h"), req(context,"resistance_ohm"))
                out[ElectricalFields.TAU_SECONDS] = f(r.tauSeconds); out[ElectricalFields.PRIMARY_VALUE] = f(r.tauSeconds); out[ElectricalFields.PRIMARY_UNIT] = "s"; out[ElectricalFields.RESULT] = "τ ${f(r.tauSeconds)} s"
            }
            "resistor_code" -> {
                val bands = context["bands"].orEmpty().split(',', ';').map(String::trim).filter(String::isNotBlank)
                val r = ElectricalAlgorithms.resistorBands(bands)
                out[ElectricalFields.RESISTANCE_OHM] = f(r.resistanceOhm); out[ElectricalFields.TOLERANCE_PERCENT] = f(r.tolerancePercent); out[ElectricalFields.PRIMARY_VALUE] = f(r.resistanceOhm); out[ElectricalFields.PRIMARY_UNIT] = "Ω"; out[ElectricalFields.RESULT] = r.label
            }
            else -> error("Unknown electrical tool '$tool'.")
        }
        out
    }.getOrElse { error ->
        ElectricalFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[ElectricalFields.STATUS] = "failed"
            this[ElectricalFields.ERROR] = error.message ?: "Electrical calculation failed."
            this[ElectricalFields.RESULT] = this[ElectricalFields.ERROR].orEmpty()
        }
    }

    private fun d(m: Map<String,String>, key: String) = m[key]?.trim()?.takeIf(String::isNotBlank)?.toDoubleOrNull()
    private fun req(m: Map<String,String>, key: String) = d(m,key) ?: error("$key is required.")
    private fun f(x: Double) = if (x.isInfinite()) "∞" else String.format(Locale.US, "%.6g", x)
    private fun safetyNote(tool: String) = when (tool) {
        "voltage_drop" -> "Resistance-only engineering estimate. It does not model conductor reactance or establish cable selection / installation compliance. Use the applicable wiring standard and verify installation method, grouping, ambient conditions and protective-device requirements."
        else -> "Calculation aid only. Verify assumptions and isolate hazardous circuits before physical work."
    }
}
