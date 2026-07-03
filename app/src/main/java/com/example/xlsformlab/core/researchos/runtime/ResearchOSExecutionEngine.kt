package com.example.xlsformlab.core.researchos.runtime

import com.example.xlsformlab.core.Method
import com.example.xlsformlab.core.researchos.ArchitectureId
import com.example.xlsformlab.core.researchos.ArchitectureRef
import com.example.xlsformlab.core.researchos.Entity
import com.example.xlsformlab.core.researchos.ExecutionRequest
import com.example.xlsformlab.core.researchos.ExecutionResult
import com.example.xlsformlab.core.researchos.Observation
import com.example.xlsformlab.core.researchos.Signal
import com.example.xlsformlab.core.researchos.State
import com.example.xlsformlab.core.researchos.TemporalContext
import com.example.xlsformlab.core.researchos.Transformation
import com.example.xlsformlab.core.researchos.TransformationStatus
import com.example.xlsformlab.settings.SettingsState

/**
 * Canonical AS1.00 execution entry point.
 *
 * For now this engine has two safe responsibilities:
 * 1. Run legacy methods through the AS1.00 bridge.
 * 2. Assemble AS1.00-native results for code paths that already interpret
 *    signals directly, such as NFC tag reads.
 *
 * The important architectural rule is that callers should depend on this
 * engine rather than directly constructing runtime results or directly calling
 * the legacy MethodRuntime.
 */
object As100ExecutionEngine {



    fun request(
        action: String,
        method: ArchitectureRef,
        id: ArchitectureId = ArchitectureId(),
        context: Map<String, String> = emptyMap(),
        signals: List<Signal> = emptyList(),
        inputs: List<ArchitectureRef> = emptyList(),
        temporalContext: TemporalContext = TemporalContext()
    ): ExecutionRequest = ExecutionRequest(
        id = id,
        action = action,
        method = method,
        context = context,
        signals = signals,
        inputs = inputs,
        temporalContext = temporalContext
    )

    fun requestFor(
        method: Method,
        action: String = method.manifest.id,
        context: Map<String, String> = emptyMap(),
        signals: List<Signal> = emptyList(),
        inputs: List<ArchitectureRef> = emptyList()
    ): ExecutionRequest = As100MethodRuntime.requestFor(
        method = method,
        action = action,
        context = context,
        signals = signals,
        inputs = inputs
    )

    fun executeMethod(
        method: Method,
        request: ExecutionRequest,
        settingsState: SettingsState? = null,
        transport: String? = null
    ): ExecutionResult = As100MethodRuntime.execute(
        method = method,
        request = request,
        settingsState = settingsState,
        transport = transport
    )

    fun complete(
        request: ExecutionRequest,
        status: TransformationStatus,
        observations: List<Observation> = emptyList(),
        transformations: List<Transformation> = emptyList(),
        entities: List<Entity> = emptyList(),
        states: List<State> = emptyList(),
        diagnostics: Map<String, String> = emptyMap()
    ): ExecutionResult = ExecutionResult(
        request = request,
        status = status,
        entities = entities,
        observations = observations,
        transformations = transformations,
        states = states,
        diagnostics = diagnostics
    )

    fun methodFor(methodId: String): As100Method =
        As100MethodRegistry.require(methodId)

    fun executeMethod(
        method: As100Method,
        request: ExecutionRequest = method.request(),
        settingsState: SettingsState? = null,
        transport: String? = null
    ): ExecutionResult = method.execute(
        request = request,
        settingsState = settingsState,
        transport = transport
    )

}
