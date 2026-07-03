package com.example.xlsformlab.core.researchos.runtime

import com.example.xlsformlab.core.Method
import com.example.xlsformlab.core.MethodExecutionRequest
import com.example.xlsformlab.core.MethodRuntime
import com.example.xlsformlab.core.ResearchContext
import com.example.xlsformlab.core.researchos.ArchitectureId
import com.example.xlsformlab.core.researchos.ArchitectureRef
import com.example.xlsformlab.core.researchos.ExecutionRequest
import com.example.xlsformlab.core.researchos.ExecutionResult
import com.example.xlsformlab.core.researchos.MethodObjectType
import com.example.xlsformlab.core.researchos.Observation
import com.example.xlsformlab.core.researchos.ProvenanceContext
import com.example.xlsformlab.core.researchos.Signal
import com.example.xlsformlab.core.researchos.TemporalContext
import com.example.xlsformlab.core.researchos.Transformation
import com.example.xlsformlab.core.researchos.TransformationStatus
import com.example.xlsformlab.settings.SettingsState

/**
 * AS1.00 execution bridge for existing ResearchOS methods.
 *
 * This adapter lets new AS1.00 code use ExecutionRequest / ExecutionResult while
 * current methods still run through the legacy MethodRuntime. The only
 * lossy conversion happens at the legacy boundary, where typed AS1.00 context
 * and signal payload values are converted to strings.
 */
object As100MethodRuntime {

    fun methodRef(method: Method): ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(method.manifest.id),
        type = MethodObjectType.Method.name,
        label = method.manifest.name
    )

    fun requestFor(
        method: Method,
        action: String = method.manifest.id,
        context: Map<String, String> = emptyMap(),
        signals: List<Signal> = emptyList(),
        inputs: List<ArchitectureRef> = emptyList(),
        temporalContext: TemporalContext = TemporalContext()
    ): ExecutionRequest = ExecutionRequest(
        action = action,
        method = methodRef(method),
        context = context,
        signals = signals,
        inputs = inputs,
        temporalContext = temporalContext
    )

    fun execute(
        method: Method,
        request: ExecutionRequest,
        settingsState: SettingsState? = null,
        transport: String? = null
    ): ExecutionResult {
        val legacyContext: Map<String, String> = stringifyMap(request.context)
        val legacyParameters: Map<String, String> = buildLegacyParameters(
            context = request.context,
            signals = request.signals
        )

        val legacy = MethodRuntime.execute(
            method = method,
            request = MethodExecutionRequest(
                methodId = method.manifest.id,
                context = ResearchContext(values = legacyContext),
                parameters = legacyParameters,
                transport = transport
            ),
            settingsState = settingsState
        )

        val status = if (legacy.success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val provenance = ProvenanceContext(
            provider = "researchos.method_runtime",
            methodId = method.manifest.id,
            methodVersion = method.manifest.version
        )

        val diagnostics: Map<String, String> = buildMap {
            legacy.errorMessage?.let { put("error", it) }
            if (legacy.warnings.isNotEmpty()) {
                put("warnings", legacy.warnings.joinToString(" | "))
            }
        }

        val observation = legacy.artifact?.let { artifact ->
            Observation(
                phenomenon = "method.output.${method.manifest.id}",
                values = stringifyMap(artifact.output.fields),
                temporalContext = request.temporalContext,
                provenance = provenance
            )
        }

        val outputRefs = observation?.let {
            listOf(ArchitectureRef(it.id, "Observation", it.phenomenon))
        } ?: emptyList()

        val transformation = Transformation(
            action = request.action,
            method = request.method,
            inputs = request.inputs + request.signals.map { ArchitectureRef(it.id, "Signal", it.signalType) },
            outputs = outputRefs,
            status = status,
            diagnostics = diagnostics,
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return ExecutionResult(
            request = request,
            status = status,
            observations = observation?.let { listOf(it) } ?: emptyList(),
            transformations = listOf(transformation),
            diagnostics = diagnostics
        )
    }

    private fun buildLegacyParameters(
        context: Map<String, String>,
        signals: List<Signal>
    ): Map<String, String> {
        val parameters = linkedMapOf<String, String>()
        parameters.putAll(stringifyMap(context))
        for (signal in signals) {
            parameters.putAll(prefixMap("signal.${signal.signalType}.", signal.payload))
        }
        return parameters
    }

    private fun stringifyMap(values: Map<String, *>): Map<String, String> = buildMap {
        for ((key, value) in values) {
            put(key, value?.toString().orEmpty())
        }
    }

    private fun prefixMap(prefix: String, values: Map<String, Any?>): Map<String, String> = buildMap {
        for ((key, value) in values) {
            put(prefix + key, value?.toString().orEmpty())
        }
    }
}
