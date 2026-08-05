package com.example.researchos.modules.providercommands

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.settings.SettingsState
import java.time.Instant

object ProviderCommandFields {
    const val COMMAND_ID = "provider_command_id"
    const val STATUS = "provider_command_status"
    const val DISPLAY_NAME = "provider_command_display_name"
    const val PROVIDER_ID = "provider_id"
    const val PACKAGE_NAME = "provider_package_name"
    const val PACKAGE_VERSION = "provider_package_version"
    const val INTERFACE_TYPE = "provider_interface_type"
    const val STABILITY = "provider_stability"
    const val OFFLINE_SUPPORTED = "provider_offline_supported"
    const val ACTION = "provider_action"
    const val DATA_URI = "provider_data_uri"
    const val INPUTS_JSON = "provider_inputs_json"
    const val RESULT_CODE = "provider_result_code"
    const val RETURNED_DATA_URI = "provider_returned_data_uri"
    const val RETURNED_TYPE = "provider_returned_type"
    const val RETURNED_VALUES_JSON = "provider_returned_values_json"
    const val RETURNED_CLIPDATA = "provider_returned_clipdata"
    const val LAUNCHED_TIME_ISO = "provider_launched_time_iso"
    const val RESULT_TIME_ISO = "provider_result_time_iso"
    const val ERROR = "provider_error"

    val outputs = listOf(
        COMMAND_ID, STATUS, DISPLAY_NAME, PROVIDER_ID, PACKAGE_NAME, PACKAGE_VERSION,
        INTERFACE_TYPE, STABILITY, OFFLINE_SUPPORTED, ACTION, DATA_URI, INPUTS_JSON,
        RESULT_CODE, RETURNED_DATA_URI, RETURNED_TYPE, RETURNED_VALUES_JSON, RETURNED_CLIPDATA, LAUNCHED_TIME_ISO,
        RESULT_TIME_ISO, ERROR
    )
}

data class ProviderCommandOutcome(val values: Map<String, String>, val succeeded: Boolean)

object As100ProviderCommandRunMethod : As100Method {
    const val ID = "provider.command.run"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Run a saved external provider command")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = "Run provider command",
        version = VERSION,
        description = "Run a saved command from the external command library and return provider metadata/result values.",
        outputs = ProviderCommandFields.outputs,
        graphOutputs = listOf("provider.command.run"),
        parameters = mapOf("category" to "External providers")
    )
    override val contract = MethodContract(
        method = ref,
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Provider commands require the Android inter-app boundary."))

    fun result(request: ExecutionRequest, outcome: ProviderCommandOutcome, invocation: InvocationContext?): ExecutionResult {
        val status = if (outcome.succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed
        val entity = Entity(ArchitectureId("provider-command:${System.currentTimeMillis()}"), "ProviderCommandExecution", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("android.intent", ID, VERSION)
        val observation = Observation(
            phenomenon = "provider.command.run",
            subject = ArchitectureRef(entity.id, entity.objectType, ID),
            values = outcome.values + (ProviderCommandFields.RESULT_TIME_ISO to (outcome.values[ProviderCommandFields.RESULT_TIME_ISO] ?: Instant.now().toString())),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = status,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (outcome.succeeded) emptyMap() else mapOf("provider_error" to (outcome.values[ProviderCommandFields.ERROR] ?: "Provider command failed."))
        ).withInvocationContext(invocation)
    }
}
