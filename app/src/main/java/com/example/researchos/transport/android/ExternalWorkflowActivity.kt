package com.example.researchos.transport.android

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100LegacyMethodAdapter
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.modules.adminfingerprint.As100VerifyFingerprintMethod
import com.example.researchos.modules.gpstargetnavigator.As100LocateTargetMethod
import com.example.researchos.modules.gpstargetnavigator.GpsTargetNavigatorMethod
import com.example.researchos.modules.nfc.As100NfcReadMethod
import com.example.researchos.modules.nfc.NfcDeviceServiceEffect
import com.example.researchos.modules.nfc.NfcEvidenceFields
import com.example.researchos.modules.nfc.NfcReadEvidenceBundle
import com.example.researchos.modules.nfc.rememberNfcAvailabilityMessage
import com.example.researchos.platform.BiometricAuthHelper
import com.example.researchos.platform.biometric.AndroidBiometricDeviceService
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.workflow.ConfirmedWorkflowStep
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.ui.theme.ResearchOSTheme

/**
 * Production execution surface for third-party callers such as ODK.
 *
 * Unlike the dashboard, this activity treats each requested action as a focused
 * capability screen with capture/retry/confirm controls. Confirmed steps are
 * written into the ResearchOS graph and the final screen shows exactly what
 * will be returned to the caller.
 */
class ExternalWorkflowActivity : FragmentActivity() {
    private lateinit var request: ExternalWorkflowRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        request = AndroidIntentRequestReader.workflowRequest(intent)
        ResearchRuntime.session.setInvocationContext(request.invocationContext)

        setContent {
            ResearchOSTheme {
                ExternalWorkflowScreen(
                    request = request,
                    onCancel = { finishWithCancel("Workflow cancelled.") },
                    onReturn = { confirmed -> finishWithResult(confirmed) }
                )
            }
        }
    }

    private fun finishWithResult(confirmed: List<ConfirmedWorkflowStep>) {
        val combined = combineResults(confirmed.map { it.result })
        val fields = OutputFormatter.selectedFields(
            result = combined,
            selectors = request.returns,
            graph = ResearchRuntime.session.graph(),
            includeProvenance = true
        )
        val output = OutputFormatter.format(
            result = combined,
            returnMode = request.returnMode,
            includeProvenance = true,
            selectors = request.returns,
            graph = ResearchRuntime.session.graph()
        )
        val data = Intent().apply {
            putExtra("value", output)
            putExtra("return_mode", request.returnMode.id)
            putExtra("researchos_execution_id", combined.request.id.value)
            putExtra("researchos_status", combined.status.name)
            putExtra("context_entity_id", request.invocationContext.canonicalEntityId)
            fields.forEach { (key, value) -> putExtra(key, value?.toString()) }
        }
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
        setResult(RESULT_CANCELED, Intent().apply { putExtra("error", message) })
        finish()
    }
}

@Composable
private fun ExternalWorkflowScreen(
    request: ExternalWorkflowRequest,
    onCancel: () -> Unit,
    onReturn: (List<ConfirmedWorkflowStep>) -> Unit
) {
    val confirmed = remember { mutableStateListOf<ConfirmedWorkflowStep>() }
    var index by remember { mutableIntStateOf(0) }
    val actions = request.actions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp)
    ) {
        Text("ResearchOS external workflow", fontWeight = FontWeight.Bold)
        Text("Subject: ${request.invocationContext.canonicalEntityId}", fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(8.dp))

        if (actions.isEmpty()) {
            Text("No actions were supplied by the calling app.")
            Spacer(Modifier.height(12.dp))
            Button(onClick = onCancel) { Text("Close") }
            return@Column
        }

        if (index < actions.size) {
            val action = actions[index]
            CapabilityStepScreen(
                action = action,
                request = request,
                stepNumber = index + 1,
                totalSteps = actions.size,
                canGoBack = index > 0,
                onBack = { if (index > 0) index -= 1 },
                onConfirmed = { result ->
                    val recorded = ResearchRuntime.session.record(result.withInvocationContext(request.invocationContext))
                    if (confirmed.size > index) {
                        confirmed[index] = ConfirmedWorkflowStep(action, recorded)
                    } else {
                        confirmed.add(ConfirmedWorkflowStep(action, recorded))
                    }
                    index += 1
                },
                onCancel = onCancel
            )
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
        totalSteps = totalSteps
    )
    capabilityScreenFor(action).Render(
        context = screenContext,
        onBack = onBack,
        onConfirmed = onConfirmed,
        onCancel = onCancel
    )
}

private fun capabilityScreenFor(action: ExternalActionRequest): CapabilityScreenSpec =
    when (action.canonicalId) {
        As100NfcReadMethod.ID -> NfcReadCapabilityScreen
        As100VerifyFingerprintMethod.ID -> FingerprintCapabilityScreen
        As100LocateTargetMethod.ID -> GpsNavigateCapabilityScreen
        else -> GenericCapabilityScreen(action.canonicalId)
    }

private object NfcReadCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100NfcReadMethod.ID
    override val title: String = "NFC tag read"
    override val description: String = "Capture an NFC tag, review the evidence, then confirm or retry."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        NfcReadWorkflowStep(context, context.stepNumber > 1, onBack, onConfirmed, onCancel)
    }
}

private object FingerprintCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100VerifyFingerprintMethod.ID
    override val title: String = "Identity verification"
    override val description: String = "Run device verification, review the outcome, then confirm or retry."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        FingerprintWorkflowStep(context, context.stepNumber > 1, onBack, onConfirmed, onCancel)
    }
}

private object GpsNavigateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100LocateTargetMethod.ID
    override val title: String = "GPS target navigation"
    override val description: String = "Navigate to a configured target and confirm the navigation result."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        GpsNavigateWorkflowStep(context, context.stepNumber > 1, onBack, onConfirmed, onCancel)
    }
}

private class GenericCapabilityScreen(
    override val capabilityId: String
) : CapabilityScreenSpec {
    override val title: String = "Capability action"
    override val description: String = "Run a registered ResearchOS method and confirm its graph result."

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
private fun NfcReadWorkflowStep(
    screenContext: CapabilityScreenContext,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val request = screenContext.request
    val initialStatus = rememberNfcAvailabilityMessage()
    var active by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(initialStatus) }
    var bundle by remember { mutableStateOf<NfcReadEvidenceBundle?>(null) }
    val execution = bundle?.executionResult?.withInvocationContext(request.invocationContext)

    fun startCapture() {
        active = true
        bundle = null
        status = "Waiting for NFC tag…"
    }

    NfcDeviceServiceEffect(
        enabled = active,
        onStatus = { status = it },
        onSignal = { tagSignal ->
            val read = As100NfcReadMethod.readBundle(tagSignal, request.invocationContext)
            bundle = read
            active = false
            status = "Tag captured: ${read.evidence.values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()}"
        }
    )

    CapabilityScreenScaffold(
        title = "NFC tag read",
        capabilityId = screenContext.action.canonicalId,
        context = screenContext,
        canGoBack = canGoBack,
        capturedResult = execution,
        resultPreview = execution?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { startCapture() },
        onConfirm = { execution?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text("Tap an NFC tag to capture its UID and payload evidence.")
        Text(status)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { startCapture() }) { Text(if (bundle == null) "Start capture" else "Capture again") }
            if (active) {
                OutlinedButton(onClick = { active = false; status = "NFC capture stopped." }) { Text("Stop") }
            }
        }
    }
}

@Composable
private fun FingerprintWorkflowStep(
    screenContext: CapabilityScreenContext,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val action = screenContext.action
    val request = screenContext.request
    val context = LocalContext.current
    val method = As100MethodRegistry.require(As100VerifyFingerprintMethod.ID)
    val settings = settingsStateFor(method, action.settings)
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var status by remember { mutableStateOf("Ready for verification.") }
    val allowDeviceCredential = settings?.getBoolean("allow_device_credential") ?: true
    val availability = AndroidBiometricDeviceService.availability(context, allowDeviceCredential)

    fun startVerification() {
        BiometricAuthHelper.authenticate(
            context = context,
            title = settings?.getString("prompt_title") ?: "Confirmation required",
            subtitle = settings?.getString("prompt_subtitle") ?: "Use fingerprint, face unlock, PIN, pattern, or password to continue",
            description = settings?.getString("prompt_description") ?: "Confirm that the expected participant or operator is present.",
            cancelText = settings?.getString("cancel_text") ?: "Cancel",
            confirmationRequired = settings?.getBoolean("confirmation_required") ?: true,
            allowDeviceCredential = allowDeviceCredential,
            onSuccess = { authMethod ->
                val signal = AndroidBiometricDeviceService.authenticationSignal(
                    verified = true,
                    authMethod = authMethod,
                    message = "Confirmed"
                )
                val execution = As100VerifyFingerprintMethod.execute(
                    request = As100VerifyFingerprintMethod.request(
                        action = action.canonicalId,
                        context = request.invocationContext.asMap(action.canonicalId) + action.settings,
                        signals = listOf(signal.signal)
                    ),
                    settingsState = null,
                    transport = request.source
                ).withInvocationContext(request.invocationContext)
                result = execution
                status = "Verified using $authMethod."
            },
            onFailure = { message ->
                val signal = AndroidBiometricDeviceService.authenticationSignal(
                    verified = false,
                    authMethod = "none",
                    message = message
                )
                val execution = As100VerifyFingerprintMethod.execute(
                    request = As100VerifyFingerprintMethod.request(
                        action = action.canonicalId,
                        context = request.invocationContext.asMap(action.canonicalId) + action.settings,
                        signals = listOf(signal.signal)
                    ),
                    settingsState = null,
                    transport = request.source
                ).withInvocationContext(request.invocationContext)
                result = execution
                status = message
            }
        )
    }

    CapabilityScreenScaffold(
        title = "Identity verification",
        capabilityId = action.canonicalId,
        context = screenContext,
        canGoBack = canGoBack,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { if (availability.available) startVerification() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text(availability.message)
        Text(status)
        Spacer(Modifier.height(10.dp))
        Button(enabled = availability.available, onClick = { startVerification() }) {
            Text(if (result == null) "Start verification" else "Verify again")
        }
    }
}

@Composable
private fun GpsNavigateWorkflowStep(
    screenContext: CapabilityScreenContext,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val action = screenContext.action
    val request = screenContext.request
    val method = GpsTargetNavigatorMethod()
    val settings = remember { SettingsState(method.settings).also { applyParameters(it, method.settings, action.settings) } }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }

    fun refreshResult() {
        val execution = As100LocateTargetMethod.execute(
            request = As100LocateTargetMethod.request(
                action = action.canonicalId,
                context = request.invocationContext.asMap(action.canonicalId) + action.settings
            ),
            settingsState = settings,
            transport = request.source
        ).withInvocationContext(request.invocationContext)
        result = execution
    }

    CapabilityScreenScaffold(
        title = "GPS target navigation",
        capabilityId = action.canonicalId,
        context = screenContext,
        canGoBack = canGoBack,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = onBack,
        onRetry = { refreshResult() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text("Navigate to the configured target, then review and confirm the saved navigation result.")
        Spacer(Modifier.height(10.dp))
        method.Demo(settings)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { refreshResult() }) { Text(if (result == null) "Review GPS result" else "Refresh GPS result") }
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
        val settings = settingsStateFor(runnable, action.settings)
        val execution = runnable.execute(
            request = runnable.request(
                action = action.canonicalId,
                context = request.invocationContext.asMap(action.canonicalId) + action.settings
            ),
            settingsState = settings,
            transport = request.source
        ).withInvocationContext(request.invocationContext)
        result = execution
        status = "Execution complete: ${execution.status.name}"
    }

    CapabilityScreenScaffold(
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
    Column(Modifier.fillMaxWidth()) {
        Text("Data preview", fontWeight = FontWeight.SemiBold)
        if (fields.isEmpty()) {
            Text("No values available.")
        } else {
            fields.entries.take(24).forEach { (key, value) ->
                Text("$key = ${value?.toString().orEmpty()}", fontFamily = FontFamily.Monospace)
            }
            if (fields.size > 24) Text("… ${fields.size - 24} more fields")
        }
    }
}

private fun settingsStateFor(method: As100Method, parameters: Map<String, String>): SettingsState? {
    val legacySettings = legacySettingsFor(method)
    return if (legacySettings.isEmpty()) null else SettingsState(legacySettings).also {
        applyParameters(it, legacySettings, parameters)
    }
}

private fun legacySettingsFor(method: As100Method): List<MethodSetting> =
    (method as? As100LegacyMethodAdapter)?.method?.settings
        ?: As100MethodRegistry.legacyFind(method.id)?.settings
        ?: emptyList()

private fun applyParameters(
    settingsState: SettingsState,
    settings: List<MethodSetting>,
    parameters: Map<String, String>
) {
    settings.forEach { setting ->
        val raw = parameters[setting.id] ?: return@forEach
        when (setting) {
            is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: raw == "1")
            is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
            is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
            is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
            is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
        }
    }
}
