package com.example.methodmesh.modules.odkformlauncher

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
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
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

object OdkFormLaunchFields {
    const val FORM_SELECTOR = "form_selector"
    const val PROJECT_ID = "odk_project_id"
    const val FORM_ID = "odk_form_id"
    const val FORM_NAME = "odk_form_name"
    const val FORM_URI = "odk_form_uri"
    const val INSTANCE_URI = "odk_instance_uri"
    const val LAUNCH_STATUS = "odk_launch_status"
    const val LAUNCH_TIME_ISO = "odk_launch_time_iso"
    const val ERROR = "odk_launch_error"

    val outputFields = listOf(PROJECT_ID, FORM_SELECTOR, FORM_ID, FORM_NAME, FORM_URI, INSTANCE_URI, LAUNCH_STATUS, LAUNCH_TIME_ISO, ERROR)
}

data class OdkFormLaunchOutcome(
    val projectId: String = "",
    val selector: String,
    val formId: String = "",
    val formName: String = "",
    val formUri: String = "",
    val instanceUri: String = "",
    val status: String,
    val error: String = ""
)

object As100OdkFormLauncherMethod : As100Method {
    const val ID = "odk_form_launcher"
    private const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Open named ODK form")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Open named ODK form", version = VERSION,
        description = "Open a locally available ODK Collect form by its form ID or display name.",
        outputs = OdkFormLaunchFields.outputFields,
        graphOutputs = listOf("odk.form.launch"),
        parameters = mapOf("category" to "Interoperability", "status" to "Production")
    )
    override val contract = MethodContract(
        method = ref, requiredContext = listOf(OdkFormLaunchFields.FORM_SELECTOR),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Unsupported, diagnostics = mapOf("reason" to "Opening ODK forms requires the Android activity boundary."))

    fun result(request: ExecutionRequest, outcome: OdkFormLaunchOutcome, invocationContext: InvocationContext?): ExecutionResult {
        val values = linkedMapOf<String, String>(
            OdkFormLaunchFields.PROJECT_ID to outcome.projectId,
            OdkFormLaunchFields.FORM_SELECTOR to outcome.selector,
            OdkFormLaunchFields.FORM_ID to outcome.formId,
            OdkFormLaunchFields.FORM_NAME to outcome.formName,
            OdkFormLaunchFields.FORM_URI to outcome.formUri,
            OdkFormLaunchFields.INSTANCE_URI to outcome.instanceUri,
            OdkFormLaunchFields.LAUNCH_STATUS to outcome.status,
            OdkFormLaunchFields.LAUNCH_TIME_ISO to Instant.now().toString(),
            OdkFormLaunchFields.ERROR to outcome.error
        )
        val succeeded = outcome.status == "returned" || outcome.status == "launched"
        val provenance = ProvenanceContext(provider = "android.odk_collect", methodId = ID, methodVersion = VERSION)
        val observation = Observation(
            phenomenon = "odk.form.launch", subject = null, values = values,
            temporalContext = request.temporalContext, provenance = provenance
        )
        val transformation = Transformation(
            action = ID, method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext, provenance = provenance,
            diagnostics = mapOf("status" to outcome.status, "error" to outcome.error)
        )
        return As100ExecutionEngine.complete(
            request = request, status = transformation.status,
            observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = mapOf("status" to outcome.status, "error" to outcome.error)
        ).withInvocationContext(invocationContext)
    }
}
