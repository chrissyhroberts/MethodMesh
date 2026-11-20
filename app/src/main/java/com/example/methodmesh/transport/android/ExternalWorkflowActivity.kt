package com.example.methodmesh.transport.android

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.methodmesh.platform.devices.PlatformDeviceBootstrap
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.calibration.CalibrationRepository
import com.example.methodmesh.settings.DisplaySettingsRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnNamespaceProjector
import com.example.methodmesh.transport.workflow.ConfirmedWorkflowStep
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.transport.workflow.WorkflowResultProjection
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.PresetLogWriter
import com.example.methodmesh.transport.workflow.NativePresetCloseoutPolicy
import com.example.methodmesh.MainActivity
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.ui.theme.MethodMeshTheme

/**
 * Production execution surface for third-party callers such as ODK.
 *
 * Hosts canonical capability screens, retains workflow progress across activity
 * recreation, and projects this run's committed results back to its caller.
 */
class ExternalWorkflowActivity : FragmentActivity() {
    private lateinit var request: ExternalWorkflowRequest
    private lateinit var workflow: ExternalWorkflowViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        workflow = ViewModelProvider(this)[ExternalWorkflowViewModel::class.java]
        workflow.initialise(restoringActivity = savedInstanceState != null)
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
                if (workflow.interrupted) {
                    Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Text("Run interrupted", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(12.dp))
                        Text("This run could not be restored. Close it and retry from the caller. Any device or external actions already completed may have taken effect.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { finishWithCancel("Workflow interrupted; caller retry required.") }) { Text("Close") }
                    }
                    BackHandler { finishWithCancel("Workflow interrupted; caller retry required.") }
                } else {
                    ExternalWorkflowScreen(
                        request = request,
                        workflow = workflow,
                        onCancel = { finishWithCancel("Workflow cancelled.") },
                        onReturn = { confirmed -> finishWithResult(confirmed) }
                    )
                }
            }
        }
    }

    private fun finishWithResult(confirmed: List<ConfirmedWorkflowStep>) {
        if (workflow.resultReturned) return
        workflow.resultReturned = true
        val returnNamespace = ReturnNamespaceProjector.namespaceFrom(request.settings)
        runCatching { ReturnNamespaceProjector.validate(returnNamespace) }
            .onFailure {
                workflow.resultReturned = false
                finishWithCancel(it.message ?: "Invalid MethodMesh return namespace.")
                return
            }
        val combined = WorkflowResultProjection.combine(confirmed.map { it.result })
        val payloadMode = request.settings["payload_mode"]
            ?: request.settings["input_payload_mode"]
            ?: request.settings["return_payload"]
            ?: request.settings["input_return_payload"]
            ?: OutputFormatter.PayloadMode.FULL
        val selectedFields = OutputFormatter.selectedFields(
            result = combined,
            selectors = request.returns,
            includeProvenance = true
        )
        val fields = OutputFormatter.projectFields(selectedFields, payloadMode, combined.status)
        val output = OutputFormatter.format(
            result = combined,
            returnMode = request.returnMode,
            includeProvenance = true,
            selectors = request.returns,
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

        // ODK/XLSForm group intents send child question names as Android extras.
        // Canonical input fields use the input_ / input64_ namespaces; ordinary
        // child names are therefore explicit caller-declared return placeholders.
        //
        // Re-project any caller-declared canonical output from the unfiltered
        // compact result before building the FULL JSON sidecar. This preserves
        // useful identifiers, hashes and timestamps such as credential_id without
        // globally widening CORE presentation or making capability-specific
        // exceptions in OutputFormatter.
        val completeFields = OutputFormatter.fields(combined, includeProvenance = true)
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = intent.extras?.keySet().orEmpty(),
            canonicalFields = completeFields,
            selectedFields = selectedFields,
            target = flatReturnFields,
            returnNamespace = returnNamespace
        )

        // An external form always receives the complete provenance JSON and
        // every binary result URI. Payload projection may reduce ordinary
        // fields, but it must not silently separate the result's beef from
        // its media or audit trail.
        flatReturnFields["methodmesh_full_json"] = WorkflowResultProjection.fullJson(confirmed.map { it.result })
        ExternalReturnAttachments.preserveFields(completeFields, flatReturnFields)
        addLogWarning(flatReturnFields)

        // Stock Enketo/browser callers cannot receive Android Activity results.
        // Browser deep links therefore receive the same projected external
        // return scope through the browser bridge. This deliberately uses the
        // already-built flatReturnFields rather than completeFields so browser
        // transport cannot widen a caller's selected/payload return contract.
        // Binary URI fields remain included because the existing external
        // contract already preserves them independently of scalar projection.
        BrowserReturnBridge.handleIfRequested(
            context = this,
            request = request,
            fields = flatReturnFields,
            browserDeepLink = isBrowserDeepLink()
        )

        val data = Intent()
        ReturnIntentProjector.applyTo(
            intent = data,
            contentResolver = contentResolver,
            projected = ReturnIntentProjector.projectFlatReturn(flatReturnFields, returnNamespace)
        )
        setResult(RESULT_OK, data)
        if (NativePresetCloseoutPolicy.opensHome(request.settings)) {
            // Never clear the host's task before its confirmation/log coroutine
            // has finished. Module screens delegate native Home to this point.
            runCatching {
                startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Toast.makeText(this, "Result completed. Home could not be opened.", Toast.LENGTH_LONG).show() }
        }
        finish()
    }

    private fun finishWithCancel(message: String) {
        if (workflow.resultReturned) return
        workflow.resultReturned = true
        val returnNamespace = runCatching { ReturnNamespaceProjector.namespaceFrom(request.settings) }.getOrDefault("")
        val safeNamespace = runCatching {
            ReturnNamespaceProjector.validate(returnNamespace)
            returnNamespace
        }.getOrDefault("")
        val cancelFields = WorkflowResultProjection.cancellationFields(
            request = request,
            currentIndex = workflow.progress.index,
            completedStepCount = workflow.progress.confirmed.take(workflow.progress.index).size,
            message = message
        ).toMutableMap()
        addLogWarning(cancelFields)
        BrowserReturnBridge.handleIfRequested(
            context = this,
            request = request,
            fields = cancelFields,
            browserDeepLink = isBrowserDeepLink()
        )
        val data = Intent()
        ReturnIntentProjector.applyTo(
            intent = data,
            contentResolver = contentResolver,
            projected = ReturnIntentProjector.projectFlatReturn(cancelFields, safeNamespace)
        )
        setResult(RESULT_CANCELED, data)
        finish()
    }

    private fun addLogWarning(fields: MutableMap<String, String?>) {
        workflow.logWarning()?.let { warning ->
            // Storage metadata must not rewrite a method's status or FULL evidence.
            fields["methodmesh_log_warning"] = warning
            Toast.makeText(this, "Persistent log incomplete. Captured result returned unchanged.", Toast.LENGTH_LONG).show()
        }
    }

    private fun isBrowserDeepLink(): Boolean =
        intent.action == Intent.ACTION_VIEW &&
            intent.data?.scheme.equals("methodmesh", ignoreCase = true)
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
    workflow: ExternalWorkflowViewModel,
    onCancel: () -> Unit,
    onReturn: (List<ConfirmedWorkflowStep>) -> Unit
) {
    val progress = workflow.progress
    val application = LocalContext.current.applicationContext
    val confirmed = progress.confirmed
    val index = progress.index
    val acceptedIndex = progress.acceptedIndex
    val actions = request.actions
    val stepState = rememberSaveableStateHolder()

    BackHandler {
        if (index > 0) workflow.progress = workflow.progress.back() else onCancel()
    }
    LaunchedEffect(progress.stepKey) {
        if (actions.isNotEmpty() && index == actions.size) onReturn(confirmed)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        workflow.logWarning()?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
            val action = progress.actionFor(request)
            if (actions.size > 1 && acceptedIndex != index) {
                ExternalStepIntroScreen(
                    stepNumber = index + 1,
                    totalSteps = actions.size,
                    title = action.requestedId,
                    lastCompleted = confirmed.take(index).lastOrNull()?.let(::externalLastCompletedSummary).orEmpty(),
                    onGo = { workflow.progress = workflow.progress.accept() },
                    onCancel = onCancel
                )
            } else {
                stepState.SaveableStateProvider(progress.stepKey) {
                    CapabilityStepScreen(
                        action = action,
                        request = request,
                        stepNumber = index + 1,
                        totalSteps = actions.size,
                        canGoBack = index > 0,
                        genericResults = workflow.genericResults,
                        stepKey = progress.stepKey,
                        onBack = { workflow.progress = workflow.progress.back() },
                        onConfirmed = { result ->
                            workflow.confirmStep(progress.stepKey, action, request, result) {
                                PresetLogWriter.record(application, request, it).warning
                            }
                        },
                        onCancel = onCancel
                    )
                }
            }
        } else {
            ReturnSummaryScreen(
                request = request,
                confirmed = confirmed,
                onBack = { workflow.progress = workflow.progress.back() },
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
    genericResults: MutableMap<String, ExecutionResult>,
    stepKey: String,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val dashboardRequest = request.source.equals("dashboard", ignoreCase = true) ||
        request.source.equals("controller", ignoreCase = true) ||
        request.settings["methodmesh_dashboard"].orEmpty().equals("true", ignoreCase = true) ||
        request.settings["methodmesh_controller"].orEmpty().equals("true", ignoreCase = true) ||
        request.invocationContext.caller.equals("dashboard", ignoreCase = true) ||
        request.invocationContext.caller.equals("controller", ignoreCase = true) ||
        request.invocationContext.caller.endsWith("_dashboard", ignoreCase = true) ||
        request.invocationContext.caller.endsWith("_controller", ignoreCase = true) ||
        request.invocationContext.caller.endsWith(" dashboard", ignoreCase = true)
        val screenContext = CapabilityScreenContext(
        action = action,
        request = request,
        stepNumber = stepNumber,
        totalSteps = totalSteps,
        completionMode = if (
            dashboardRequest ||
            request.settings["methodmesh_headless"] != "true" &&
            request.settings["input_methodmesh_headless"] != "true" &&
            (request.settings["methodmesh_native_preset_run"] == "true" || request.settings["input_methodmesh_native_preset_run"] == "true") &&
            request.settings["methodmesh_protocol_step_run"] != "true" &&
            request.settings["input_methodmesh_protocol_step_run"] != "true" &&
            request.settings["methodmesh_sequence_step_run"] != "true" &&
            request.settings["input_methodmesh_sequence_step_run"] != "true"
        ) {
            CapabilityCompletionMode.ManualConfirmation
        } else {
            CapabilityCompletionMode.AutomaticReturn
        },
        hostOwnsNativePresetCloseout = true
    )
    capabilityScreenFor(action, genericResults, stepKey).Render(
        context = screenContext,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

private fun capabilityScreenFor(
    action: ExternalActionRequest,
    genericResults: MutableMap<String, ExecutionResult>,
    stepKey: String
): CapabilityScreenSpec =
    MethodMeshModuleRegistry.screenFor(action.canonicalId)
        ?: GenericCapabilityScreen(action.canonicalId, genericResults, stepKey)


private class GenericCapabilityScreen(
    override val capabilityId: String,
    private val genericResults: MutableMap<String, ExecutionResult>,
    private val stepKey: String
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
        GenericMethodWorkflowStep(context, context.stepNumber > 1, genericResults, stepKey, onBack, onConfirmed, onCancel)
    }
}

@Composable
private fun GenericMethodWorkflowStep(
    screenContext: CapabilityScreenContext,
    canGoBack: Boolean,
    genericResults: MutableMap<String, ExecutionResult>,
    stepKey: String,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val action = screenContext.action
    val request = screenContext.request
    val method = As100MethodRegistry.find(action.canonicalId)
    val result = genericResults[stepKey]
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
        genericResults[stepKey] = execution
        status = "Execution complete: ${execution.status.name}"
    }

    LaunchedEffect(stepKey) {
        if (genericResults[stepKey] == null) runAction()
    }

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
    val combined = remember(confirmed) {
        WorkflowResultProjection.combine(confirmed.map { it.result })
    }
    val fields = OutputFormatter.selectedFields(
        result = combined,
        selectors = request.returns,
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
