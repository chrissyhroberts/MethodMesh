package com.example.researchos.modules.appinspector

import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
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

object AppInspectorFields {
    const val PACKAGE_NAME = "package_name"
    const val APP_LABEL = "app_label"
    const val VERSION_NAME = "version_name"
    const val EXPORTED_COMPONENTS = "exported_components"
    const val DISCOVERED_ACTIONS = "discovered_actions"
    const val MATCHED_INTENT_FILTERS = "matched_intent_filters"
    const val TEST_ACTION = "test_action"
    const val TEST_COMPONENT = "test_component"
    const val TEST_URI = "test_uri"
    const val TEST_RESULT = "test_result"
    const val INTEGRATION_DEFINITION = "integration_definition"
    const val INSPECTED_TIME_ISO = "inspected_time_iso"
    val outputFields = listOf(PACKAGE_NAME, APP_LABEL, VERSION_NAME, EXPORTED_COMPONENTS, DISCOVERED_ACTIONS, MATCHED_INTENT_FILTERS, TEST_ACTION, TEST_COMPONENT, TEST_URI, TEST_RESULT, INTEGRATION_DEFINITION, INSPECTED_TIME_ISO)
}

data class AppInspectionOutcome(
    val packageName: String,
    val appLabel: String,
    val versionName: String,
    val exportedComponents: String,
    val discoveredActions: String,
    val matchedIntentFilters: String = "",
    val testAction: String = "",
    val testComponent: String = "",
    val testUri: String = "",
    val testResult: String = "",
    val integrationDefinition: String = ""
)

object As100AppInspectorMethod : As100Method {
    const val ID = "android_app_inspector"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Inspect Android app interfaces")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Inspect Android app interfaces", version = VERSION,
        description = "Inspect public Android components and test a user-selected intent.",
        outputs = AppInspectorFields.outputFields,
        graphOutputs = listOf("android.app.interface.inspection"),
        parameters = mapOf("category" to "Interoperability", "status" to "Prototype")
    )
    override val contract = MethodContract(
        method = ref, requiredContext = listOf(AppInspectorFields.PACKAGE_NAME),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "App inspection requires the Android package boundary."))

    fun result(request: ExecutionRequest, outcome: AppInspectionOutcome, invocationContext: InvocationContext?): ExecutionResult {
        val values = linkedMapOf(
            AppInspectorFields.PACKAGE_NAME to outcome.packageName,
            AppInspectorFields.APP_LABEL to outcome.appLabel,
            AppInspectorFields.VERSION_NAME to outcome.versionName,
            AppInspectorFields.EXPORTED_COMPONENTS to outcome.exportedComponents,
            AppInspectorFields.DISCOVERED_ACTIONS to outcome.discoveredActions,
            AppInspectorFields.MATCHED_INTENT_FILTERS to outcome.matchedIntentFilters,
            AppInspectorFields.TEST_ACTION to outcome.testAction,
            AppInspectorFields.TEST_COMPONENT to outcome.testComponent,
            AppInspectorFields.TEST_URI to outcome.testUri,
            AppInspectorFields.TEST_RESULT to outcome.testResult,
            AppInspectorFields.INTEGRATION_DEFINITION to outcome.integrationDefinition,
            AppInspectorFields.INSPECTED_TIME_ISO to Instant.now().toString()
        )
        val provenance = ProvenanceContext(provider = "android.package_manager", methodId = ID, methodVersion = VERSION)
        val observation = Observation(
            phenomenon = "android.app.interface.inspection", subject = null, values = values,
            temporalContext = request.temporalContext, provenance = provenance
        )
        val transformation = Transformation(
            action = ID, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded, temporalContext = request.temporalContext, provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request, status = TransformationStatus.Succeeded,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = mapOf("package_name" to outcome.packageName)
        ).withInvocationContext(invocationContext)
    }
}
