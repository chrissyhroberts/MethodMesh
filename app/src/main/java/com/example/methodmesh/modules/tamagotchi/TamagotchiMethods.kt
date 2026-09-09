package com.example.methodmesh.modules.tamagotchi

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
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

object TamagotchiFields {
    const val STATUS = "tamagotchi_status"
    const val OPERATION = "tamagotchi_operation"
    const val SESSION_ID = "tamagotchi_session_id"
    const val CREATURE_ID = "tamagotchi_creature_id"
    const val CREATURE_NAME = "tamagotchi_creature_name"
    const val SCENARIO_ID = "tamagotchi_scenario_id"
    const val SIMULATION_MINUTE = "tamagotchi_simulation_minute"
    const val PHENOTYPE = "tamagotchi_phenotype"
    const val VISIBLE_STATE_JSON = "tamagotchi_visible_state_json"
    const val LATENT_STATE_JSON = "tamagotchi_latent_state_json"
    const val EVENT_JSON = "tamagotchi_event_json"
    const val HISTORY_ARTIFACT_REF = "tamagotchi_history_artifact_ref"
    const val ANALYSIS_UNLOCKED = "tamagotchi_analysis_unlocked"
    const val EXPORT_URI = "tamagotchi_export_uri"
    const val EXPORT_SHA256 = "tamagotchi_export_sha256"
    const val EXPORT_ARTIFACT_REF = "tamagotchi_export_artifact_ref"
    const val CONFIG_JSON = "tamagotchi_config_json"
    const val ERROR = "tamagotchi_error"

    val outputs = listOf(
        STATUS, OPERATION, SESSION_ID, CREATURE_ID, CREATURE_NAME, SCENARIO_ID,
        SIMULATION_MINUTE, PHENOTYPE, VISIBLE_STATE_JSON, LATENT_STATE_JSON,
        EVENT_JSON, HISTORY_ARTIFACT_REF, ANALYSIS_UNLOCKED, EXPORT_URI,
        EXPORT_SHA256, EXPORT_ARTIFACT_REF, CONFIG_JSON, ERROR
    )
}

abstract class TamagotchiMethod(
    final override val id: String,
    private val humanName: String,
    private val descriptionText: String,
    private val version: String = "0.1.2"
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", humanName)
    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = humanName,
        version = version,
        description = descriptionText,
        outputs = TamagotchiFields.outputs,
        graphOutputs = listOf(id),
        parameters = mapOf("category" to "Teaching", "status" to "Development")
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
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    final override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val suppliedStatus = request.context[TamagotchiFields.STATUS]
        val values = if (suppliedStatus != null) request.context else mapOf(
            TamagotchiFields.STATUS to "failed",
            TamagotchiFields.OPERATION to id,
            TamagotchiFields.ERROR to "This Tamagotchi capability requires its module-owned Android screen/runtime."
        )
        return result(request, values, InvocationContext.from(request.context))
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[TamagotchiFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.tamagotchi", id, version)
        val observation = Observation(
            phenomenon = id,
            subject = invocation?.subjectRef(),
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
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(TamagotchiFields.ERROR to values[TamagotchiFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}

object As100TamagotchiSessionMethod : TamagotchiMethod(
    "teaching.tamagotchi.session", "Tamagotchi care session",
    "Start, resume or end a persistent longitudinal Tamagotchi teaching session."
) { const val ID = "teaching.tamagotchi.session" }

object As100TamagotchiInterveneMethod : TamagotchiMethod(
    "teaching.tamagotchi.intervene", "Tamagotchi intervention",
    "Apply one configured or procedurally generated intervention to a Tamagotchi session."
) { const val ID = "teaching.tamagotchi.intervene" }

object As100TamagotchiObserveMethod : TamagotchiMethod(
    "teaching.tamagotchi.observe", "Tamagotchi observation",
    "Record a purposive observation of the current visible creature phenotype."
) { const val ID = "teaching.tamagotchi.observe" }

object As100TamagotchiMeasureMethod : TamagotchiMethod(
    "teaching.tamagotchi.measure", "Tamagotchi measurement",
    "Record one scenario-authorised measurement without exposing latent state."
) { const val ID = "teaching.tamagotchi.measure" }

object As100TamagotchiAdvanceMethod : TamagotchiMethod(
    "teaching.tamagotchi.advance", "Advance Tamagotchi simulation",
    "Advance classroom/turn-based simulation time and record resulting state transitions."
) { const val ID = "teaching.tamagotchi.advance" }

object As100TamagotchiHistoryMethod : TamagotchiMethod(
    "teaching.tamagotchi.history", "Tamagotchi history",
    "Inspect the longitudinal care-visible history and live trajectories."
) { const val ID = "teaching.tamagotchi.history" }

object As100TamagotchiAnalyseMethod : TamagotchiMethod(
    "teaching.tamagotchi.analyse", "Analyse Tamagotchi session",
    "Reveal and inspect latent state only after the care period has ended."
) { const val ID = "teaching.tamagotchi.analyse" }

object As100TamagotchiExportMethod : TamagotchiMethod(
    "teaching.tamagotchi.export", "Export Tamagotchi session",
    "Export a reproducible ZIP teaching dataset containing authoritative history plus derived CSV tables."
) { const val ID = "teaching.tamagotchi.export" }

object As100TamagotchiConfigureMethod : TamagotchiMethod(
    "teaching.tamagotchi.configure", "Configure Tamagotchi scenario",
    "Inspect/validate built-in scenario configuration and expose a stable configuration payload."
) { const val ID = "teaching.tamagotchi.configure" }
