package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.methodmesh.platform.devices.PlatformDeviceBootstrap
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.calibration.CalibrationRepository
import com.example.methodmesh.settings.DisplaySettingsRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnNamespaceProjector
import com.example.methodmesh.transport.workflow.ConfirmedWorkflowStep
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.ui.theme.MethodMeshTheme

/**
 * Production execution surface for third-party callers such as ODK.
 *
 * Unlike the dashboard, this activity treats each requested action as a focused
 * capability screen with capture/retry/confirm controls. Confirmed steps are
 * written into the MethodMesh graph and the final screen shows exactly what
 * will be returned to the caller.
 */
class ExternalWorkflowActivity : FragmentActivity() {
    private lateinit var request: ExternalWorkflowRequest
    private var resultReturned = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlatformDeviceBootstrap.initialise()
        // External and scheduled launches can start the app process without
        // MainActivity ever being created. Initialise device calibration here
        // as well so calibrated-scale settings use the saved dp/mm value.
        CalibrationRepository.initialiseBlocking(applicationContext)
        DisplaySettingsRepository.initialiseBlocking(applicationContext)
        request = AndroidIntentRequestReader.workflowRequest(intent)
        ResearchRuntime.session.setInvocationContext(request.invocationContext)

        setContent {
            MethodMeshTheme {
                ExternalWorkflowScreen(
                    request = request,
                    onCancel = { finishWithCancel("Workflow cancelled.") },
                    onReturn = { confirmed -> finishWithResult(confirmed) }
                )
            }
        }
    }

    private fun finishWithResult(confirmed: List<ConfirmedWorkflowStep>) {
        if (resultReturned) return
        resultReturned = true
        val returnNamespace = ReturnNamespaceProjector.namespaceFrom(request.settings)
        runCatching { ReturnNamespaceProjector.validate(returnNamespace) }
            .onFailure {
                resultReturned = false
                finishWithCancel(it.message ?: "Invalid MethodMesh return namespace.")
                return
            }
        val combined = combineResults(confirmed.map { it.result })
        val payloadMode = request.settings["payload_mode"]
            ?: request.settings["input_payload_mode"]
            ?: request.settings["return_payload"]
            ?: request.settings["input_return_payload"]
            ?: OutputFormatter.PayloadMode.FULL
        val selectedFields = OutputFormatter.selectedFields(
            result = combined,
            selectors = request.returns,
            graph = ResearchRuntime.session.graph(),
            includeProvenance = true
        )
        val fields = OutputFormatter.projectFields(selectedFields, payloadMode, combined.status)
        val output = OutputFormatter.format(
            result = combined,
            returnMode = request.returnMode,
            includeProvenance = true,
            selectors = request.returns,
            graph = ResearchRuntime.session.graph(),
            payloadMode = payloadMode
        )
        val flatReturnFields = linkedMapOf<String, String?>(
            "methodmesh_closeout_status" to "completed",
            "methodmesh_closeout_step_count" to confirmed.size.toString(),
            "methodmesh_closeout_has_payload" to hasMeaningfulPayload(fields).toString(),
            "value" to output,
            "return_mode" to request.returnMode.id,
            "payload_mode" to OutputFormatter.PayloadMode.normalize(payloadMode)
        )
        if (OutputFormatter.PayloadMode.normalize(payloadMode) != OutputFormatter.PayloadMode.CORE) {
            flatReturnFields["methodmesh_execution_id"] = combined.request.id.value
            flatReturnFields["methodmesh_status"] = combined.status.name
            flatReturnFields["context_entity_id"] = request.invocationContext.canonicalEntityId
        }
        fields.forEach { (key, value) -> flatReturnFields[key] = value?.toString() }
        val data = Intent()
        ReturnIntentProjector.applyTo(
            intent = data,
            contentResolver = contentResolver,
            projected = ReturnIntentProjector.projectFlatReturn(flatReturnFields, returnNamespace)
        )
        setResult(RESULT_OK, data)
        finish()
    }

    private fun combineResults(results: List<ExecutionResult>): ExecutionResult {
        val first = results.firstOrNull() ?: error("No confirmed results to return.")
        val allSucceeded = results.all { it.status == TransformationStatus.Succeeded }
        return first.copy(
            status = if (allSucceeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = results.flatMap { it.entities },
            attributes = results.flatMap { it.attributes },
            observations = results.flatMap { it.observations },
            relationships = results.flatMap { it.relationships },
            classifications = results.flatMap { it.classifications },
            transformations = results.flatMap { it.transformations },
            states = results.flatMap { it.states },
            validation = results.flatMap { it.validation },
            diagnostics = results.flatMap { it.diagnostics.entries }
                .associate { (key, value) -> key to value }
                .plus("workflow_step_count" to results.size.toString())
        )
    }

    private fun finishWithCancel(message: String) {
        if (resultReturned) return
        resultReturned = true
        val returnNamespace = runCatching { ReturnNamespaceProjector.namespaceFrom(request.settings) }.getOrDefault("")
        val safeNamespace = runCatching {
            ReturnNamespaceProjector.validate(returnNamespace)
            returnNamespace
        }.getOrDefault("")
        val data = Intent()
        ReturnIntentProjector.applyTo(
            intent = data,
            contentResolver = contentResolver,
            projected = ReturnIntentProjector.projectFlatReturn(
                linkedMapOf(
                    "methodmesh_closeout_status" to "cancelled",
                    "methodmesh_closeout_step_count" to "0",
                    "methodmesh_closeout_has_payload" to "false",
                    "error" to message
                ),
                safeNamespace
            )
        )
        setResult(RESULT_CANCELED, data)
        finish()
    }
}

private fun hasMeaningfulPayload(fields: Map<String, Any?>): Boolean =
    fields.any { (key, value) ->
        value?.toString()?.isNotBlank() == true &&
            key !in setOf("return_mode", "payload_mode") &&
            !key.startsWith("methodmesh_") &&
            !key.startsWith("diagnostic_")
    }

@Composable
private fun ExternalWorkflowScreen(
    request: ExternalWorkflowRequest,
    onCancel: () -> Unit,
    onReturn: (List<ConfirmedWorkflowStep>) -> Unit
) {
    val confirmed = remember { mutableStateListOf<ConfirmedWorkflowStep>() }
    var index by remember { mutableIntStateOf(0) }
    var acceptedIndex by remember { mutableIntStateOf(-1) }
    val actions = request.actions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        if (actions.isEmpty()) {
            Text(
                "MethodMesh",
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text("No actions were supplied by the calling app.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCancel) { Text("Close") }
            return@Column
        }

        if (index < actions.size) {
            val action = actions[index].withPipeSettings(confirmed)
            if (actions.size > 1 && acceptedIndex != index) {
                ExternalStepIntroScreen(
                    stepNumber = index + 1,
                    totalSteps = actions.size,
                    title = action.requestedId,
                    lastCompleted = confirmed.lastOrNull()?.let(::externalLastCompletedSummary).orEmpty(),
                    onGo = { acceptedIndex = index },
                    onCancel = onCancel
                )
            } else {
                CapabilityStepScreen(
                    action = action,
                    request = request,
                    stepNumber = index + 1,
                    totalSteps = actions.size,
                    canGoBack = index > 0,
                    onBack = { if (index > 0) index -= 1 },
                    onConfirmed = { result ->
                        val recorded = ResearchRuntime.session.record(result.withInvocationContext(request.invocationContext))
                        val completedStep = ConfirmedWorkflowStep(action, recorded)
                        if (confirmed.size > index) {
                            confirmed[index] = completedStep
                        } else {
                            confirmed.add(completedStep)
                        }
                        if (index == actions.lastIndex) {
                            onReturn(confirmed.toList())
                        } else {
                            index += 1
                        }
                    },
                    onCancel = onCancel
                )
            }
        } else {
            ReturnSummaryScreen(
                request = request,
                confirmed = confirmed,
                onBack = { index = (actions.size - 1).coerceAtLeast(0) },
                onReturn = { onReturn(confirmed.toList()) },
                onCancel = onCancel
            )
        }
    }
}

@Composable
private fun ExternalStepIntroScreen(
    stepNumber: Int,
    totalSteps: Int,
    title: String,
    lastCompleted: String,
    onGo: () -> Unit,
    onCancel: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Step $stepNumber of $totalSteps", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(10.dp))
        if (lastCompleted.isNotBlank()) {
            Text(lastCompleted, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
        }
        Text("Next step", style = MaterialTheme.typography.labelLarge)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onGo, modifier = Modifier.fillMaxWidth()) { Text("Go") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel run") }
    }
}

private fun externalLastCompletedSummary(step: ConfirmedWorkflowStep): String {
    val fields = OutputFormatter.projectFields(
        OutputFormatter.fields(step.result, includeProvenance = false),
        OutputFormatter.PayloadMode.CORE,
        step.result.status
    ).filterValues { it?.toString()?.isNotBlank() == true }
    val first = fields.entries.firstOrNull()
    return if (first == null) {
        "Last step completed."
    } else {
        "Last step completed: ${first.key.replace('_', ' ')} ${first.value}"
    }
}

private fun ExternalActionRequest.withPipeSettings(confirmed: List<ConfirmedWorkflowStep>): ExternalActionRequest {
    if (confirmed.isEmpty()) return this
    return copy(settings = pipeSettings(confirmed) + settings)
}

private fun pipeSettings(confirmed: List<ConfirmedWorkflowStep>): Map<String, String> {
    val piped = linkedMapOf<String, String>()
    confirmed.forEachIndexed { index, step ->
        val fields = OutputFormatter.fields(step.result, includeProvenance = false)
        fields.forEach { (key, value) ->
            val text = value?.toString().orEmpty()
            if (text.isNotBlank()) {
                piped["step_${index + 1}_$key"] = text
                piped["previous_$key"] = text
                piped.putIfAbsent(key, text)
            }
        }
    }
    return piped
}


@Composable
private fun RequestDebugSummary(request: ExternalWorkflowRequest) {
    Spacer(Modifier.height(6.dp))
    Text("Request", fontWeight = FontWeight.Bold)
    Text("Source: ${request.source}", fontFamily = FontFamily.Monospace)
    Text(
        "Actions: ${request.actions.joinToString { it.requestedId }}",
        fontFamily = FontFamily.Monospace
    )
    Text(
        "Returns: ${request.returns.joinToString { "${it.path} as ${it.alias}" }}",
        fontFamily = FontFamily.Monospace
    )
    Text("Format: ${request.returnMode.id}", fontFamily = FontFamily.Monospace)
    request.warnings.takeIf { it.isNotEmpty() }?.let { warnings ->
        Spacer(Modifier.height(4.dp))
        Text("Warnings", fontWeight = FontWeight.Bold)
        warnings.forEach { warning -> Text("• $warning") }
    }
}

@Composable
private fun CapabilityStepScreen(
    action: ExternalActionRequest,
    request: ExternalWorkflowRequest,
    stepNumber: Int,
    totalSteps: Int,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val screenContext = CapabilityScreenContext(
        action = action,
        request = request,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        completionMode = if (
            (request.settings["methodmesh_native_preset_run"] == "true" || request.settings["input_methodmesh_native_preset_run"] == "true") &&
            request.settings["methodmesh_protocol_step_run"] != "true" &&
            request.settings["input_methodmesh_protocol_step_run"] != "true" &&
            request.settings["methodmesh_sequence_step_run"] != "true" &&
            request.settings["input_methodmesh_sequence_step_run"] != "true"
        ) {
            CapabilityCompletionMode.ManualConfirmation
        } else {
            CapabilityCompletionMode.AutomaticReturn
        }
    )
    capabilityScreenFor(action).Render(
        context = screenContext,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

private fun capabilityScreenFor(action: ExternalActionRequest): CapabilityScreenSpec =
    MethodMeshModuleRegistry.screenFor(action.canonicalId)
        ?: GenericCapabilityScreen(action.canonicalId)


private class GenericCapabilityScreen(
    override val capabilityId: String
) : CapabilityScreenSpec {
    override val title: String = "Capability action"
    override val description: String = "Run a registered MethodMesh method and confirm its graph result."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        GenericMethodWorkflowStep(context, context.stepNumber > 1, onBack, onConfirmed, onCancel)
    }
}

@Composable
private fun GenericMethodWorkflowStep(
    screenContext: CapabilityScreenContext,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val action = screenContext.action
    val request = screenContext.request
    val method = As100MethodRegistry.find(action.canonicalId)
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var status by remember { mutableStateOf(if (method == null) "Unknown method: ${action.canonicalId}" else "Ready.") }

    fun runAction() {
        val runnable = method ?: return
        val execution = runnable.execute(
            request = runnable.request(
                action = action.canonicalId,
                context = request.invocationContext.asMap(action.canonicalId) + action.settings
            ),
            settingsState = null,
            transport = request.source
        ).withInvocationContext(request.invocationContext)
        result = execution
        status = "Execution complete: ${execution.status.name}"
    }

    LaunchedEffect(action.canonicalId) { runAction() }

    com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold(
        title = "Capability action",
        capabilityId = action.canonicalId,
        context = screenContext,
        canGoBack = canGoBack,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { runAction() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text(status)
        Spacer(Modifier.height(10.dp))
        if (method == null) {
            Text("No registered capability screen or AS method was found for this action.")
        } else {
            Button(onClick = { runAction() }) { Text(if (result == null) "Run action" else "Run again") }
        }
    }
}

@Composable
private fun ReturnSummaryScreen(
    request: ExternalWorkflowRequest,
    confirmed: List<ConfirmedWorkflowStep>,
    onBack: () -> Unit,
    onReturn: () -> Unit,
    onCancel: () -> Unit
) {
    val combined = remember(confirmed.size) {
        confirmed.first().result.copy(
            status = if (confirmed.all { it.result.status == TransformationStatus.Succeeded }) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = confirmed.flatMap { it.result.entities },
            attributes = confirmed.flatMap { it.result.attributes },
            observations = confirmed.flatMap { it.result.observations },
            relationships = confirmed.flatMap { it.result.relationships },
            classifications = confirmed.flatMap { it.result.classifications },
            transformations = confirmed.flatMap { it.result.transformations },
            states = confirmed.flatMap { it.result.states },
            validation = confirmed.flatMap { it.result.validation }
        )
    }
    val fields = OutputFormatter.selectedFields(
        result = combined,
        selectors = request.returns,
        graph = ResearchRuntime.session.graph(),
        includeProvenance = true
    )

    Column(Modifier.fillMaxWidth()) {
        Text("Return summary", fontWeight = FontWeight.Bold)
        Text("Confirmed steps: ${confirmed.size}")
        Text("Return mode: ${request.returnMode.label}")
        Spacer(Modifier.height(10.dp))
        ResultPreview(fields)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text("Back") }
            OutlinedButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = onReturn) { Text("Return to calling app") }
        }
    }
}

@Composable
private fun ResultPreview(fields: Map<String, Any?>) {
    var expanded by remember(fields) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text("Data preview", fontWeight = FontWeight.SemiBold)
        if (fields.isEmpty()) {
            Text("No values available.")
        } else {
            val visibleFields = if (expanded) fields.entries else fields.entries.take(24)
            SelectionContainer {
                Column {
                    visibleFields.forEach { (key, value) ->
                        Text("$key = ${value?.toString().orEmpty()}", fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (fields.size > 24) {
                Text(
                    text = if (expanded) "▲ Show fewer fields" else "▼ ${fields.size - 24} more fields",
                    modifier = Modifier.clickable { expanded = !expanded }.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
