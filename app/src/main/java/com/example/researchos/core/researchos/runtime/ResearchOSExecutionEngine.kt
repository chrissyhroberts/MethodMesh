package com.example.researchos.core.researchos.runtime

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Attribute
import com.example.researchos.core.researchos.Classification
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.Relationship
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.State
import com.example.researchos.core.researchos.TemporalContext
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.ValidationFinding
import com.example.researchos.core.researchos.QualityAssessment
import com.example.researchos.settings.SettingsState

/**
 * Canonical AS1.00 execution entry point.
 *
 * All executable capabilities implement [As100Method].
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

    fun complete(
        request: ExecutionRequest,
        status: TransformationStatus,
        observations: List<Observation> = emptyList(),
        transformations: List<Transformation> = emptyList(),
        entities: List<Entity> = emptyList(),
        attributes: List<Attribute> = emptyList(),
        relationships: List<Relationship> = emptyList(),
        classifications: List<Classification> = emptyList(),
        states: List<State> = emptyList(),
        validation: List<ValidationFinding> = emptyList(),
        quality: QualityAssessment? = null,
        diagnostics: Map<String, String> = emptyMap()
    ): ExecutionResult = ExecutionResult(
        request = request,
        status = status,
        entities = entities,
        attributes = attributes,
        observations = observations,
        relationships = relationships,
        classifications = classifications,
        transformations = transformations,
        states = states,
        validation = validation,
        quality = quality,
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
