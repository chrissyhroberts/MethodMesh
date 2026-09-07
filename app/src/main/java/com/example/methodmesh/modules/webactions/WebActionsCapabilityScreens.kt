package com.example.methodmesh.modules.webactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

object OdkCentralRoundtripCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100OdkCentralRoundtripMethod.ID
    override val title = "ODK Central form"
    override val description = "Paste a Central web-form link, complete the form, and return only after Central reaches the one-shot completion URL."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        OdkCentralRoundtripScreen(context, onBack, onConfirmed, onCancel)
    }
}

object EnketoRoundtripCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EnketoRoundtripMethod.ID
    override val title = "Precooked Enketo session"
    override val description = "Advanced: create a fresh Enketo session through its API, prefill known runtime values, and return only after the explicit completion redirect."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        EnketoRoundtripScreen(context, onBack, onConfirmed, onCancel)
    }
}

object WebRoundtripCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100WebRoundtripMethod.ID
    override val title = "Web roundtrip"
    override val description = "Run a web workflow that calls a one-shot MethodMesh return URL when complete."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        WebRoundtripScreen(context, onBack, onConfirmed, onCancel)
    }
}

object WebOpenCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100WebOpenMethod.ID
    override val title = "Open web page"
    override val description = "Open a safe HTTP(S) address in the Android browser."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        WebOpenScreen(context, onBack, onConfirmed, onCancel)
    }
}

@Composable
private fun OdkCentralRoundtripScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val settings = context.action.settings

    var url by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("url").orEmpty()) }
    var timeoutText by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("timeout_seconds") ?: "0") }
    var allowHttp by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("allow_insecure_http")?.toBooleanStrictOrNull() ?: false)
    }
    var transactionId by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(WebActionsRepository.newestPending(androidContext, As100OdkCentralRoundtripMethod.ID)?.id.orEmpty())
    }
    // Automatic launch is a one-shot affordance for preset/protocol/ODK entry.
    // Once a kiosk has been opened during this screen lifetime, closing it or
    // completing it must never re-enter the form just because the waiting branch
    // left composition and this LaunchedEffect became visible again.
    var autoLaunchConsumed by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(transactionId.isNotBlank())
    }
    var revision by rememberSaveable(context.action.canonicalId) { mutableIntStateOf(0) }
    var screenStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("Paste a Central link to begin") }
    var screenError by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val transaction = remember(transactionId, revision) {
        transactionId.takeIf { it.isNotBlank() }?.let { WebActionsRepository.get(androidContext, it) }
    }
    val linkInfo = remember(url, allowHttp) {
        runCatching { inspectCentralLink(url, allowHttp) }.getOrNull()
    }
    val restoredResult = remember(committedFieldsJson) {
        committedFieldsJson?.let(::stringMapFromJson)?.let { values ->
            As100OdkCentralRoundtripMethod.result(
                request = As100OdkCentralRoundtripMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + safeCentralRequestContext(
                        host = values[OdkCentralRoundtripFields.HOST].orEmpty(),
                        linkKind = values[OdkCentralRoundtripFields.LINK_KIND].orEmpty()
                    ),
                    signals = emptyList(),
                    inputs = emptyList()
                ),
                values = values,
                invocation = context.request.invocationContext
            )
        }
    }
    val frozenResult = committedResult ?: restoredResult

    LaunchedEffect(url, timeoutText, allowHttp) {
        // Keep the operational URL available to presets/protocols when the user
        // explicitly chooses to save it. Canonical result construction below
        // never includes the source URL or its st token.
        context.onSettingsChanged(
            mapOf(
                "url" to url,
                "timeout_seconds" to timeoutText,
                "allow_insecure_http" to allowHttp.toString()
            )
        )
    }

    LaunchedEffect(transaction?.state, transaction?.startedTimeIso) {
        while (transaction?.state == WebActionTransactionState.WAITING) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeoutSeconds = timeoutText.toIntOrNull()?.coerceIn(0, 86_400) ?: 0
    LaunchedEffect(transactionId, revision, timeoutSeconds) {
        val current = WebActionsRepository.get(androidContext, transactionId)
        if (current != null && current.state == WebActionTransactionState.WAITING && timeoutSeconds > 0) {
            val deadline = current.startedEpochMillis + timeoutSeconds * 1000L
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0L) delay(remaining)
            val stillWaiting = WebActionsRepository.get(androidContext, transactionId)
            if (stillWaiting?.state == WebActionTransactionState.WAITING) {
                WebActionsRepository.markFailed(androidContext, transactionId, "ODK Central form timed out before its return URL was reached.")
                WebActionWebViewPool.destroy(transactionId)
                WebActionsRepository.delete(androidContext, transactionId)
                transactionId = ""
                screenError = "Timed out before a successful submission return."
                screenStatus = "Session timed out"
                revision += 1
            }
        }
    }

    fun cleanupActive() {
        if (transactionId.isNotBlank()) {
            WebActionWebViewPool.destroy(transactionId)
            WebActionsRepository.delete(androidContext, transactionId)
        }
        transactionId = ""
        revision += 1
    }

    fun startCentral() {
        // Starting a kiosk consumes the automatic-start opportunity. This is set
        // before validation/network work so a failed or explicitly exited run
        // cannot fall into an automatic relaunch loop.
        autoLaunchConsumed = true
        screenError = ""
        if (!hasValidatedInternet(androidContext)) {
            screenError = "This kiosk is online-only. Connect to the Internet before opening the form."
            screenStatus = "Internet connection required"
            return
        }
        val timeout = timeoutText.toIntOrNull()
        if (timeout == null || timeout !in 0..86_400) {
            screenError = "Timeout must be between 0 and 86400 seconds."
            return
        }
        val info = runCatching { inspectCentralLink(url, allowHttp) }
            .getOrElse {
                screenError = it.message.orEmpty()
                return
            }
        cleanupActive()
        val created = WebActionsRepository.create(
            context = androidContext,
            methodId = As100OdkCentralRoundtripMethod.ID,
            originalUrl = info.url,
            launchHost = info.host,
            callbackParameter = "return_url",
            allowInsecureHttp = allowHttp
        )
        val callback = WebActionsRepository.callbackUrl(created)
        val launchUrl = runCatching { buildCentralLaunchUrl(info, callback, allowHttp) }
            .getOrElse {
                val safeMessage = it.message ?: "Cannot prepare the Central return URL."
                WebActionsRepository.markFailed(androidContext, created.id, safeMessage)
                WebActionsRepository.delete(androidContext, created.id)
                transactionId = ""
                revision += 1
                screenError = safeMessage
                return
            }
        WebActionsRepository.setLaunch(androidContext, created.id, launchUrl, info.host)
        transactionId = created.id
        screenStatus = "${info.host} · ${info.kind.label} ready"
        revision += 1
    }

    fun commitCompletion(current: WebActionTransaction) {
        val info = runCatching { inspectCentralLink(current.originalUrl.ifBlank { url }, current.allowInsecureHttp) }
            .getOrElse {
                CentralLinkInfo(
                    url = current.originalUrl,
                    host = current.launchHost,
                    kind = CentralLinkKind.CENTRAL_WEB_FORM,
                    hasPublicAccessToken = false
                )
            }
        val values = As100OdkCentralRoundtripMethod.success(
            transactionId = current.id,
            sourceUrlHash = sha256(current.originalUrl),
            host = current.launchHost.ifBlank { info.host },
            linkKind = info.kind.wireValue,
            startedIso = current.startedTimeIso,
            completedIso = current.completedTimeIso,
            durationMs = current.durationMillis,
            publicAccessTokenPresent = info.hasPublicAccessToken,
            completionSignal = current.completionSignal.ifBlank { "central_return_url" }
        )
        val execution = As100OdkCentralRoundtripMethod.result(
            request = As100OdkCentralRoundtripMethod.request(
                action = context.action.canonicalId,
                context = context.request.invocationContext.asMap(context.action.canonicalId) + safeCentralRequestContext(
                    host = current.launchHost.ifBlank { info.host },
                    linkKind = info.kind.wireValue
                ),
                signals = emptyList(),
                inputs = emptyList()
            ),
            values = values,
            invocation = context.request.invocationContext
        )
        WebActionWebViewPool.destroy(current.id)
        WebActionsRepository.delete(androidContext, current.id)
        transactionId = ""
        if (context.submitsImmediately) {
            onConfirmed(execution)
        } else {
            committedResult = execution
            committedFieldsJson = stringMapToJson(values)
        }
    }

    if (frozenResult != null) {
        val fields = OutputFormatter.fields(frozenResult, includeProvenance = false)
        CommittedWebResult(
            title = "Central form submitted",
            summary = fields[OdkCentralRoundtripFields.RESULT]?.toString().orEmpty().ifBlank { "ODK Central form submitted" },
            result = frozenResult,
            displayValues = listOf(
                "Host" to fields[OdkCentralRoundtripFields.HOST]?.toString().orEmpty(),
                "Link type" to centralLinkKindLabel(fields[OdkCentralRoundtripFields.LINK_KIND]?.toString().orEmpty()),
                "Transaction ID" to fields[OdkCentralRoundtripFields.TRANSACTION_ID]?.toString().orEmpty(),
                "Completed" to fields[OdkCentralRoundtripFields.COMPLETED_TIME_ISO]?.toString().orEmpty()
            ),
            onDone = { onConfirmed(frozenResult) },
            onEditRetry = {
                committedResult = null
                committedFieldsJson = null
                screenStatus = "Ready for another Central form"
                screenError = ""
            }
        )
        return
    }

    val isWaiting = transaction?.state == WebActionTransactionState.WAITING
    val callbackReceived = transaction?.state == WebActionTransactionState.CALLBACK_RECEIVED
    val elapsed = transaction?.let {
        val end = if (it.completedEpochMillis > 0L) it.completedEpochMillis else nowMillis
        (end - it.startedEpochMillis).coerceAtLeast(0L)
    } ?: 0L

    LaunchedEffect(callbackReceived, transaction?.id, context.submitsImmediately) {
        val completed = transaction
        if (callbackReceived && completed != null) {
            // A successful web-form return is already the terminal live result.
            // Interactive/native runs freeze it immediately and stay on the same
            // MethodMesh landing page with Done / Share / Save actions. Automatic
            // callers (ODK, protocols, external intents) are returned immediately
            // by commitCompletion via onConfirmed().
            commitCompletion(completed)
        }
    }

    WebActionHero(
        eyebrow = "ODK Central",
        title = when {
            isWaiting -> "Form in progress"
            callbackReceived -> "Submission returned"
            else -> "Paste a link and go"
        },
        subtitle = if (isWaiting) {
            "Online-only kiosk. Complete and submit the form; MethodMesh returns automatically on the Central callback or the provider submission confirmation."
        } else {
            "Paste the public web-form link and go. No API token is required. The kiosk is intentionally online-only: no draft workflow, no reusable offline cache, and no next-record loop after submission."
        },
        status = if (callbackReceived) "Submission received — preparing result" else screenStatus,
        host = transaction?.launchHost.orEmpty().ifBlank { linkInfo?.host.orEmpty() },
        active = isWaiting,
        success = callbackReceived,
        elapsedText = if (isWaiting || callbackReceived) elapsedLabel(elapsed) else ""
    )

    if (isWaiting && transaction != null) {
        WebSessionSurface(
            transaction = transaction,
            status = screenStatus,
            onStatus = { screenStatus = it },
            onCallback = { completed ->
                revision += 1
                screenStatus = if (completed.completionSignal == "provider_submission_confirmation") {
                    "Submission confirmed — returning to MethodMesh"
                } else {
                    "Submission return received"
                }
            },
            onExit = {
                cleanupActive()
                screenStatus = "Form closed before submission"
                screenError = ""
            }
        )
        SettingSection("Session", "The Central link and one-shot return token remain app-private while this run is active.") {
            CopyableValue("Host", transaction.launchHost)
            CopyableValue("Transaction ID", transaction.id)
            if (timeoutSeconds > 0) {
                Text("Timeout ${timeoutSeconds}s · elapsed ${elapsedLabel(elapsed)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { cleanupActive(); onCancel() }, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel form")
            }
        }
        return
    }

    if (callbackReceived && transaction != null) {
        val info = runCatching { inspectCentralLink(transaction.originalUrl, transaction.allowInsecureHttp) }.getOrNull()
        SettingSection(
            "Submission received",
            "MethodMesh is finalising the completed result. Interactive runs will open the normal Done / Share / Save landing page; automatic callers return directly to their origin."
        ) {
            Text("✓ Form submitted", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            CopyableValue("Host", transaction.launchHost)
            info?.let { CopyableValue("Link type", it.kind.label) }
            CopyableValue("Transaction ID", transaction.id)
            CopyableValue("Completed", transaction.completedTimeIso)
        }
        return
    }

    SettingSection(
        "Central web form",
        "Paste the public form link from ODK Central or KoboToolbox. No API token is required. MethodMesh runs it as a disposable online-only kiosk session, removes local draft/cache state on exit, and returns automatically after a confirmed submission."
    ) {
        if (context.settingShouldBeShown("url")) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("ODK Central link") },
                placeholder = { Text("https://central.example.org/f/…?st=…") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        linkInfo?.let { info ->
            Spacer(Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("✓ Link ready", fontWeight = FontWeight.Bold)
                    Text("${info.kind.label} · ${info.host}", style = MaterialTheme.typography.bodyMedium)
                    Text("Renderer: Central decides (ODK Web Forms or Enketo)", style = MaterialTheme.typography.bodySmall)
                    if (info.hasPublicAccessToken) {
                        Text(
                            "This Public Access Link contains an st access token. It is used for the run but excluded from canonical results and audit output.",
                            modifier = Modifier.padding(top = 5.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    SettingSection("Session & security") {
        if (context.settingShouldBeShown("timeout_seconds")) {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { timeoutText = it.filter(Char::isDigit).take(5) },
                label = { Text("Timeout (seconds)") },
                supportingText = { Text("0 waits indefinitely") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        if (context.settingShouldBeShown("allow_insecure_http")) {
            SecurityToggleRow(allowHttp) { allowHttp = it }
        }
    }

    if (screenError.isNotBlank()) ErrorPanel(screenError)
    Spacer(Modifier.height(12.dp))
    Button(onClick = ::startCentral, enabled = url.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Open form")
    }
    if (context.stepNumber > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { cleanupActive(); onCancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }

    val canAutoStart = context.startsImmediately && url.isNotBlank()
    LaunchedEffect(canAutoStart, autoLaunchConsumed) {
        if (canAutoStart && !autoLaunchConsumed && transactionId.isBlank() && committedFieldsJson == null) {
            autoLaunchConsumed = true
            startCentral()
        }
    }
}

@Composable
private fun EnketoRoundtripScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = context.action.settings

    var apiBaseUrl by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("api_base_url") ?: "https://enke.to/api/v2")
    }
    var serverUrl by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("server_url").orEmpty()) }
    var formId by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("form_id").orEmpty()) }
    var apiToken by remember(context.action.canonicalId) { mutableStateOf(settings.setting("api_token").orEmpty()) }
    var singleMode by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("single_mode") ?: "single") }
    var defaultsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("defaults_json") ?: "{}") }
    var prefillBindingsJson by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("prefill_bindings_json") ?: "{}")
    }
    val runtimePrefillInputs = remember(context.action.canonicalId) { mutableStateMapOf<String, String>() }
    var theme by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("theme").orEmpty()) }
    var timeoutText by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("timeout_seconds") ?: "0") }
    var allowHttp by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("allow_insecure_http")?.toBooleanStrictOrNull() ?: false)
    }

    var transactionId by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(WebActionsRepository.newestPending(androidContext, As100EnketoRoundtripMethod.ID)?.id.orEmpty())
    }
    var revision by rememberSaveable(context.action.canonicalId) { mutableIntStateOf(0) }
    // Preparation owns a live network call and must not survive configuration
    // recreation as a stale "busy" flag if that coroutine is cancelled.
    var preparing by remember(context.action.canonicalId) { mutableStateOf(false) }
    var screenStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready to prepare the form") }
    var screenError by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val transaction = remember(transactionId, revision) {
        transactionId.takeIf { it.isNotBlank() }?.let { WebActionsRepository.get(androidContext, it) }
    }
    val restoredResult = remember(committedFieldsJson) {
        committedFieldsJson?.let(::stringMapFromJson)?.let { values ->
            As100EnketoRoundtripMethod.result(
                request = As100EnketoRoundtripMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + safeEnketoRequestContext(
                        values[EnketoRoundtripFields.SERVER_URL].orEmpty(),
                        values[EnketoRoundtripFields.FORM_ID].orEmpty(),
                        singleMode
                    ),
                    signals = emptyList(),
                    inputs = emptyList()
                ),
                values = values,
                invocation = context.request.invocationContext
            )
        }
    }
    val frozenResult = committedResult ?: restoredResult
    val suppliedRuntimeValues = context.request.invocationContext.asMap(context.action.canonicalId) +
        context.request.settings + context.action.settings
    val requiredPrefillRuntimeKeys = remember(prefillBindingsJson) {
        runCatching { prefillRuntimeKeys(prefillBindingsJson) }.getOrDefault(emptyList())
    }
    val missingPrefillRuntimeKeys = requiredPrefillRuntimeKeys.filter { key ->
        key !in suppliedRuntimeValues && "input_$key" !in suppliedRuntimeValues
    }

    LaunchedEffect(requiredPrefillRuntimeKeys) {
        runtimePrefillInputs.keys.toList()
            .filterNot { it in requiredPrefillRuntimeKeys }
            .forEach(runtimePrefillInputs::remove)
    }

    LaunchedEffect(transactionId, preparing) {
        val interrupted = transactionId.takeIf { it.isNotBlank() }
            ?.let { WebActionsRepository.get(androidContext, it) }
        if (interrupted?.state == WebActionTransactionState.PREPARING && !preparing) {
            WebActionsRepository.delete(androidContext, interrupted.id)
            transactionId = ""
            revision += 1
            screenStatus = "Preparation interrupted — ready to retry"
        }
    }

    LaunchedEffect(apiBaseUrl, serverUrl, formId, singleMode, defaultsJson, prefillBindingsJson, theme, timeoutText, allowHttp) {
        // Deliberately exclude api_token. Operational credentials must not leak
        // into dashboard/preset snapshots merely because the user tested a form.
        context.onSettingsChanged(
            mapOf(
                "api_base_url" to redactedWebUrl(apiBaseUrl),
                "server_url" to redactedWebUrl(serverUrl),
                "form_id" to formId,
                "single_mode" to singleMode,
                "defaults_json" to defaultsJson,
                "prefill_bindings_json" to prefillBindingsJson,
                "theme" to theme,
                "timeout_seconds" to timeoutText,
                "allow_insecure_http" to allowHttp.toString()
            )
        )
    }

    LaunchedEffect(transaction?.state, transaction?.startedTimeIso) {
        while (transaction?.state == WebActionTransactionState.WAITING) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeoutSeconds = timeoutText.toIntOrNull()?.coerceIn(0, 86_400) ?: 0
    LaunchedEffect(transactionId, revision, timeoutSeconds) {
        val current = WebActionsRepository.get(androidContext, transactionId)
        if (current != null && current.state == WebActionTransactionState.WAITING && timeoutSeconds > 0) {
            val deadline = current.startedEpochMillis + timeoutSeconds * 1000L
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0L) delay(remaining)
            val stillWaiting = WebActionsRepository.get(androidContext, transactionId)
            if (stillWaiting?.state == WebActionTransactionState.WAITING) {
                WebActionsRepository.markFailed(androidContext, transactionId, "Web form timed out before a completion redirect was received.")
                WebActionWebViewPool.destroy(transactionId)
                WebActionsRepository.delete(androidContext, transactionId)
                transactionId = ""
                screenError = "Timed out before completion."
                screenStatus = "Session timed out"
                revision += 1
            }
        }
    }

    fun cleanupActive() {
        if (transactionId.isNotBlank()) {
            WebActionWebViewPool.destroy(transactionId)
            WebActionsRepository.delete(androidContext, transactionId)
        }
        transactionId = ""
        revision += 1
    }

    fun startEnketo() {
        if (preparing || transaction?.state == WebActionTransactionState.WAITING) return
        screenError = ""
        val timeout = timeoutText.toIntOrNull()
        if (timeout == null || timeout !in 0..86_400) {
            screenError = "Timeout must be between 0 and 86400 seconds."
            return
        }
        val runtimeValues = suppliedRuntimeValues + runtimePrefillInputs.toMap()
        val resolvedDefaults = runCatching {
            resolvePrefillDefaults(defaultsJson, prefillBindingsJson, runtimeValues)
        }.getOrElse {
            screenError = it.message ?: "Prefill configuration is invalid."
            return
        }
        runCatching {
            requireWebUrl(apiBaseUrl, allowHttp, "Enketo API base URL")
            requireWebUrl(serverUrl, allowHttp, "form server URL")
            require(formId.isNotBlank()) { "Form ID is required." }
            require(apiToken.isNotBlank()) { "API token is required for this run." }
        }.onFailure {
            screenError = it.message.orEmpty()
            return
        }

        preparing = true
        screenStatus = "Creating secure Enketo session…"
        cleanupActive()
        val created = WebActionsRepository.create(
            context = androidContext,
            methodId = As100EnketoRoundtripMethod.ID,
            formId = formId.trim(),
            serverUrl = redactedWebUrl(serverUrl.trim()),
            allowInsecureHttp = allowHttp
        )
        transactionId = created.id
        revision += 1
        val callbackUrl = WebActionsRepository.callbackUrl(created)
        scope.launch {
            val launch = runCatching {
                withContext(Dispatchers.IO) {
                    EnketoClient.createSingleSubmit(
                        apiBaseUrl = apiBaseUrl,
                        serverUrl = serverUrl,
                        formId = formId,
                        apiToken = apiToken,
                        singleMode = singleMode,
                        returnUrl = callbackUrl,
                        defaultsJson = resolvedDefaults,
                        theme = theme,
                        allowInsecureHttp = allowHttp
                    )
                }
            }
            launch.onSuccess { createdLaunch ->
                val host = hostOf(createdLaunch.url)
                WebActionsRepository.setLaunch(androidContext, created.id, createdLaunch.url, host)
                // The credential is no longer needed after Enketo has issued the
                // single-submit URL. Minimise its in-memory lifetime.
                apiToken = ""
                screenStatus = "$host · form ready"
                screenError = ""
                preparing = false
                revision += 1
            }.onFailure { error ->
                val safeMessage = redactSecret(
                    error.message ?: "Enketo session could not be created.",
                    apiToken
                )
                WebActionsRepository.markFailed(androidContext, created.id, safeMessage)
                WebActionsRepository.delete(androidContext, created.id)
                if (transactionId == created.id) transactionId = ""
                screenError = safeMessage
                screenStatus = "Could not open form"
                preparing = false
                revision += 1
            }
        }
    }

    fun commitCompletion(current: WebActionTransaction) {
        val values = As100EnketoRoundtripMethod.success(
            transactionId = current.id,
            formId = current.formId.ifBlank { formId.trim() },
            serverUrl = current.serverUrl.ifBlank { serverUrl.trim() },
            launchHost = current.launchHost,
            startedIso = current.startedTimeIso,
            completedIso = current.completedTimeIso,
            durationMs = current.durationMillis,
            singleMode = singleMode,
            defaultsCount = runCatching { mergedPrefillPathCount(defaultsJson, prefillBindingsJson) }.getOrDefault(0),
            bindingCount = runCatching { flatObjectCount(prefillBindingsJson, "Prefill mappings") }.getOrDefault(0)
        )
        val execution = As100EnketoRoundtripMethod.result(
            request = As100EnketoRoundtripMethod.request(
                action = context.action.canonicalId,
                context = context.request.invocationContext.asMap(context.action.canonicalId) + safeEnketoRequestContext(
                    current.serverUrl.ifBlank { serverUrl },
                    current.formId.ifBlank { formId },
                    singleMode
                ),
                signals = emptyList(),
                inputs = emptyList()
            ),
            values = values,
            invocation = context.request.invocationContext
        )
        WebActionWebViewPool.destroy(current.id)
        WebActionsRepository.delete(androidContext, current.id)
        transactionId = ""
        if (context.submitsImmediately) {
            onConfirmed(execution)
        } else {
            committedResult = execution
            committedFieldsJson = stringMapToJson(values)
        }
    }

    if (frozenResult != null) {
        val fields = OutputFormatter.fields(frozenResult, includeProvenance = false)
        CommittedWebResult(
            title = "Precooked form complete",
            summary = fields[EnketoRoundtripFields.RESULT]?.toString().orEmpty().ifBlank { "Precooked Enketo session completed" },
            result = frozenResult,
            displayValues = listOf(
                "Form ID" to fields[EnketoRoundtripFields.FORM_ID]?.toString().orEmpty(),
                "Host" to fields[EnketoRoundtripFields.LAUNCH_HOST]?.toString().orEmpty(),
                "Transaction ID" to fields[EnketoRoundtripFields.TRANSACTION_ID]?.toString().orEmpty(),
                "Completed" to fields[EnketoRoundtripFields.COMPLETED_TIME_ISO]?.toString().orEmpty()
            ),
            onDone = { onConfirmed(frozenResult) },
            onEditRetry = {
                committedResult = null
                committedFieldsJson = null
                screenStatus = "Ready for another form"
                screenError = ""
            }
        )
        return
    }

    val isWaiting = transaction?.state == WebActionTransactionState.WAITING
    val callbackReceived = transaction?.state == WebActionTransactionState.CALLBACK_RECEIVED
    val elapsed = transaction?.let {
        val end = if (it.completedEpochMillis > 0L) it.completedEpochMillis else nowMillis
        (end - it.startedEpochMillis).coerceAtLeast(0L)
    } ?: 0L

    LaunchedEffect(callbackReceived, transaction?.id, context.submitsImmediately) {
        val completed = transaction
        if (callbackReceived && completed != null && context.submitsImmediately) {
            commitCompletion(completed)
        }
    }

    WebActionHero(
        eyebrow = "Web Actions",
        title = if (isWaiting) "Form in progress" else if (callbackReceived) "Form completed" else "Precooked form",
        subtitle = if (isWaiting) "Finish the form below. MethodMesh is waiting for Enketo's return redirect."
        else "Create a fresh Enketo session and prefill what MethodMesh already knows before the user sees the form.",
        status = when {
            preparing -> "Preparing Enketo…"
            callbackReceived -> "Completion received — ready to commit"
            transaction?.state == WebActionTransactionState.FAILED -> "Session failed"
            else -> screenStatus
        },
        host = transaction?.launchHost.orEmpty().ifBlank { hostOf(serverUrl) },
        active = preparing || isWaiting,
        success = callbackReceived,
        elapsedText = if (isWaiting || callbackReceived) elapsedLabel(elapsed) else ""
    )

    if (isWaiting && transaction != null) {
        WebSessionSurface(
            transaction = transaction,
            status = screenStatus,
            onStatus = { screenStatus = it },
            onCallback = {
                screenStatus = "Completion received"
                screenError = ""
                revision += 1
            },
            onExit = {
                cleanupActive()
                screenStatus = "Form closed before submission"
                screenError = ""
            }
        )
        SettingSection("Session", "The transaction is durable; returning to this screen resumes the active web session where possible.") {
            CopyableValue("Transaction ID", transaction.id)
            CopyableValue("Host", transaction.launchHost)
            if (timeoutSeconds > 0) {
                Text(
                    "Timeout ${timeoutSeconds}s · elapsed ${elapsedLabel(elapsed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    cleanupActive()
                    screenStatus = "Session cancelled"
                    onCancel()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel web session") }
        }
        return
    }

    if (callbackReceived && transaction != null) {
        SettingSection("Current result", "The live web action is complete. Commit freezes the canonical result and returns it to the caller when appropriate.") {
            Text("✓ Enketo form completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            CopyableValue("Form ID", transaction.formId.ifBlank { formId })
            CopyableValue("Host", transaction.launchHost)
            CopyableValue("Transaction ID", transaction.id)
            CopyableValue("Completed", transaction.completedTimeIso)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { commitCompletion(transaction) }, modifier = Modifier.fillMaxWidth()) {
                Text("Commit")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    cleanupActive()
                    screenStatus = "Ready for another form"
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Discard and retry") }
        }
        return
    }

    EnketoConfiguration(
        context = context,
        apiBaseUrl = apiBaseUrl,
        onApiBaseUrl = { apiBaseUrl = it },
        serverUrl = serverUrl,
        onServerUrl = { serverUrl = it },
        formId = formId,
        onFormId = { formId = it },
        apiToken = apiToken,
        onApiToken = { apiToken = it },
        singleMode = singleMode,
        onSingleMode = { singleMode = it },
        defaultsJson = defaultsJson,
        onDefaultsJson = { defaultsJson = it },
        prefillBindingsJson = prefillBindingsJson,
        onPrefillBindingsJson = { prefillBindingsJson = it },
        theme = theme,
        onTheme = { theme = it },
        timeoutText = timeoutText,
        onTimeout = { timeoutText = it.filter(Char::isDigit).take(5) },
        allowHttp = allowHttp,
        onAllowHttp = { allowHttp = it }
    )

    if (missingPrefillRuntimeKeys.isNotEmpty()) {
        SettingSection(
            "Values needed for this run",
            "These precook sources were not supplied by ODK, a protocol step, or the launch context. Enter them now; they are run-time values and are not added to the saved preset."
        ) {
            missingPrefillRuntimeKeys.forEach { key ->
                OutlinedTextField(
                    value = runtimePrefillInputs[key].orEmpty(),
                    onValueChange = { runtimePrefillInputs[key] = it },
                    label = { Text(key.replace('_', ' ').replaceFirstChar { ch -> ch.uppercase() }) },
                    supportingText = { Text("Supplies {{$key}}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                )
            }
        }
    }

    if (screenError.isNotBlank()) ErrorPanel(screenError)
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = ::startEnketo,
        enabled = !preparing,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (preparing) "Preparing…" else "Open form") }
    if (context.stepNumber > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = {
            cleanupActive()
            onCancel()
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Cancel") }

    val canAutoStart = context.startsImmediately && serverUrl.isNotBlank() && formId.isNotBlank() && apiToken.isNotBlank() &&
        missingPrefillRuntimeKeys.isEmpty()
    LaunchedEffect(canAutoStart) {
        if (canAutoStart && transactionId.isBlank() && committedFieldsJson == null && !preparing) startEnketo()
    }
}

@Composable
private fun EnketoConfiguration(
    context: CapabilityScreenContext,
    apiBaseUrl: String,
    onApiBaseUrl: (String) -> Unit,
    serverUrl: String,
    onServerUrl: (String) -> Unit,
    formId: String,
    onFormId: (String) -> Unit,
    apiToken: String,
    onApiToken: (String) -> Unit,
    singleMode: String,
    onSingleMode: (String) -> Unit,
    defaultsJson: String,
    onDefaultsJson: (String) -> Unit,
    prefillBindingsJson: String,
    onPrefillBindingsJson: (String) -> Unit,
    theme: String,
    onTheme: (String) -> Unit,
    timeoutText: String,
    onTimeout: (String) -> Unit,
    allowHttp: Boolean,
    onAllowHttp: (Boolean) -> Unit
) {
    var showLiteralDefaults by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

    SettingSection(
        "Form source",
        "Advanced only: MethodMesh asks an Enketo API to create a fresh single-submit session for this run. The API token below is required only for this precooked workflow, never for an ordinary public Central link."
    ) {
        if (context.settingShouldBeShown("server_url")) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = onServerUrl,
                label = { Text("Form server URL") },
                placeholder = { Text("https://central.example.org") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (context.settingShouldBeShown("form_id")) {
            OutlinedTextField(
                value = formId,
                onValueChange = onFormId,
                label = { Text("Form ID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (context.settingShouldBeShown("single_mode")) {
            Text("Submission mode", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth()) {
                ModeButton("Single", singleMode == "single", Modifier.weight(1f)) { onSingleMode("single") }
                ModeButton("Once per browser", singleMode == "single_once", Modifier.weight(1f)) { onSingleMode("single_once") }
            }
            Spacer(Modifier.height(8.dp))
        }
        // Secret is an operational control and intentionally remains outside the
        // typed preset snapshot path. It may arrive as input_api_token from ODK,
        // RIL or another caller, or be entered for this run.
        OutlinedTextField(
            value = apiToken,
            onValueChange = onApiToken,
            label = { Text("Enketo API token") },
            supportingText = { Text("Used only while the session is created; never copied into result, audit, or committed request context.") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }

    SettingSection(
        "Precook values",
        "Fill what MethodMesh already knows before the form opens. A value such as {{participant_id}} is resolved from this run's runtime/protocol/ODK inputs."
    ) {
        if (context.settingShouldBeShown("prefill_bindings_json")) {
            PrefillMappingEditor(
                bindingsJson = prefillBindingsJson,
                onBindingsJson = onPrefillBindingsJson
            )
        }
        if (context.settingShouldBeShown("defaults_json")) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { showLiteralDefaults = !showLiteralDefaults },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showLiteralDefaults) "Hide literal defaults JSON" else "Advanced: literal defaults JSON")
            }
            if (showLiteralDefaults) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = defaultsJson,
                    onValueChange = onDefaultsJson,
                    label = { Text("Literal defaults JSON") },
                    supportingText = { Text("Backwards-compatible flat object: /node/path → literal value") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    if (context.settingShouldBeShown("theme")) {
        SettingSection("Display") {
            OutlinedTextField(
                value = theme,
                onValueChange = onTheme,
                label = { Text("Theme (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    SettingSection("Session & security") {
        if (context.settingShouldBeShown("api_base_url")) {
            OutlinedTextField(
                value = apiBaseUrl,
                onValueChange = onApiBaseUrl,
                label = { Text("Enketo API base URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (context.settingShouldBeShown("timeout_seconds")) {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = onTimeout,
                label = { Text("Timeout (seconds)") },
                supportingText = { Text("0 waits indefinitely") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        if (context.settingShouldBeShown("allow_insecure_http")) {
            SecurityToggleRow(allowHttp, onAllowHttp)
        }
    }
}

@Composable
private fun PrefillMappingEditor(
    bindingsJson: String,
    onBindingsJson: (String) -> Unit
) {
    var newPath by rememberSaveable { mutableStateOf("") }
    var newValue by rememberSaveable { mutableStateOf("") }
    var mappingError by rememberSaveable { mutableStateOf("") }
    val parsedMappings = remember(bindingsJson) { runCatching { prefillPairs(bindingsJson) } }
    val mappings = parsedMappings.getOrDefault(emptyList())
    val parseError = parsedMappings.exceptionOrNull()?.message.orEmpty()

    if (mappings.isEmpty()) {
        Text(
            "No prefill mappings yet. Add only fields that should already be known when the form opens.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    } else {
        mappings.forEach { (path, value) ->
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                shape = RoundedCornerShape(15.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(path, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = value,
                        onValueChange = { updated ->
                            runCatching { putPrefillPair(bindingsJson, path, updated) }
                                .onSuccess { onBindingsJson(it); mappingError = "" }
                                .onFailure { mappingError = it.message.orEmpty() }
                        },
                        label = { Text("Value or {{runtime_field}}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            onBindingsJson(removePrefillPair(bindingsJson, path))
                            mappingError = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) { Text("Remove mapping") }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    Text("Add field", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = newPath,
        onValueChange = { newPath = it },
        label = { Text("Form node path") },
        placeholder = { Text("/data/participant_id") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    OutlinedTextField(
        value = newValue,
        onValueChange = { newValue = it },
        label = { Text("Fixed value or runtime source") },
        placeholder = { Text("{{participant_id}}") },
        supportingText = { Text("Built-ins: {{today}} and {{now_iso}}") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    )
    OutlinedButton(
        onClick = {
            runCatching { putPrefillPair(bindingsJson, newPath, newValue) }
                .onSuccess {
                    onBindingsJson(it)
                    newPath = ""
                    newValue = ""
                    mappingError = ""
                }
                .onFailure { mappingError = it.message.orEmpty() }
        },
        enabled = newPath.isNotBlank(),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) { Text("Add mapping") }

    val visibleMappingError = mappingError.ifBlank { parseError }
    if (visibleMappingError.isNotBlank()) {
        Text(
            visibleMappingError,
            modifier = Modifier.padding(top = 7.dp),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun WebRoundtripScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val settings = context.action.settings
    var url by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("url").orEmpty()) }
    var callbackParameter by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("callback_parameter") ?: "return_url") }
    var callbackPlaceholder by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("callback_placeholder") ?: "{METHODMESH_RETURN_URL}") }
    var timeoutText by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("timeout_seconds") ?: "0") }
    var allowHttp by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("allow_insecure_http")?.toBooleanStrictOrNull() ?: false)
    }
    var transactionId by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(WebActionsRepository.newestPending(androidContext, As100WebRoundtripMethod.ID)?.id.orEmpty())
    }
    var revision by rememberSaveable(context.action.canonicalId) { mutableIntStateOf(0) }
    var screenStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready to open workflow") }
    var screenError by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var committedFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val transaction = remember(transactionId, revision) {
        transactionId.takeIf { it.isNotBlank() }?.let { WebActionsRepository.get(androidContext, it) }
    }
    val restoredResult = remember(committedFieldsJson) {
        committedFieldsJson?.let(::stringMapFromJson)?.let { values ->
            As100WebRoundtripMethod.result(
                request = As100WebRoundtripMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + safeRoundtripRequestContext(
                        values[WebRoundtripFields.URL].orEmpty(),
                        callbackParameter
                    ),
                    signals = emptyList(),
                    inputs = emptyList()
                ),
                values = values,
                invocation = context.request.invocationContext
            )
        }
    }
    val frozenResult = committedResult ?: restoredResult

    LaunchedEffect(url, callbackParameter, callbackPlaceholder, timeoutText, allowHttp) {
        context.onSettingsChanged(
            mapOf(
                "url" to redactedWebUrl(url),
                "callback_parameter" to callbackParameter,
                "callback_placeholder" to callbackPlaceholder,
                "timeout_seconds" to timeoutText,
                "allow_insecure_http" to allowHttp.toString()
            )
        )
    }

    LaunchedEffect(transaction?.state, transaction?.startedTimeIso) {
        while (transaction?.state == WebActionTransactionState.WAITING) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val timeoutSeconds = timeoutText.toIntOrNull()?.coerceIn(0, 86_400) ?: 0
    LaunchedEffect(transactionId, revision, timeoutSeconds) {
        val current = WebActionsRepository.get(androidContext, transactionId)
        if (current != null && current.state == WebActionTransactionState.WAITING && timeoutSeconds > 0) {
            val deadline = current.startedEpochMillis + timeoutSeconds * 1000L
            val remaining = deadline - System.currentTimeMillis()
            if (remaining > 0L) delay(remaining)
            val stillWaiting = WebActionsRepository.get(androidContext, transactionId)
            if (stillWaiting?.state == WebActionTransactionState.WAITING) {
                WebActionsRepository.markFailed(androidContext, transactionId, "Web workflow timed out before its callback was received.")
                WebActionWebViewPool.destroy(transactionId)
                WebActionsRepository.delete(androidContext, transactionId)
                transactionId = ""
                screenError = "Timed out before completion."
                screenStatus = "Session timed out"
                revision += 1
            }
        }
    }

    fun cleanupActive() {
        if (transactionId.isNotBlank()) {
            WebActionWebViewPool.destroy(transactionId)
            WebActionsRepository.delete(androidContext, transactionId)
        }
        transactionId = ""
        revision += 1
    }

    fun startRoundtrip() {
        screenError = ""
        val timeout = timeoutText.toIntOrNull()
        if (timeout == null || timeout !in 0..86_400) {
            screenError = "Timeout must be between 0 and 86400 seconds."
            return
        }
        val original = runCatching { requireWebUrl(url, allowHttp, "workflow URL") }
            .getOrElse {
                screenError = it.message.orEmpty()
                return
            }
        cleanupActive()
        val created = WebActionsRepository.create(
            context = androidContext,
            methodId = As100WebRoundtripMethod.ID,
            originalUrl = original,
            launchHost = hostOf(original),
            callbackParameter = callbackParameter.trim(),
            allowInsecureHttp = allowHttp
        )
        val callback = WebActionsRepository.callbackUrl(created)
        val launchUrl = runCatching {
            buildRoundtripLaunchUrl(original, callbackParameter.trim(), callbackPlaceholder, callback, allowHttp)
        }.getOrElse {
            val safeMessage = it.message ?: "Cannot construct callback URL."
            WebActionsRepository.markFailed(androidContext, created.id, safeMessage)
            WebActionsRepository.delete(androidContext, created.id)
            transactionId = ""
            revision += 1
            screenError = safeMessage
            return
        }
        WebActionsRepository.setLaunch(androidContext, created.id, launchUrl, hostOf(original))
        transactionId = created.id
        screenStatus = "${hostOf(original)} · workflow ready"
        revision += 1
    }

    fun commitCompletion(current: WebActionTransaction) {
        val safeOriginalUrl = redactedWebUrl(current.originalUrl.ifBlank { url })
        val values = As100WebRoundtripMethod.success(
            transactionId = current.id,
            originalUrl = safeOriginalUrl,
            host = current.launchHost,
            callbackParameter = current.callbackParameter.ifBlank { callbackParameter },
            startedIso = current.startedTimeIso,
            completedIso = current.completedTimeIso,
            durationMs = current.durationMillis
        )
        val execution = As100WebRoundtripMethod.result(
            request = As100WebRoundtripMethod.request(
                action = context.action.canonicalId,
                context = context.request.invocationContext.asMap(context.action.canonicalId) + safeRoundtripRequestContext(
                    safeOriginalUrl,
                    current.callbackParameter.ifBlank { callbackParameter }
                ),
                signals = emptyList(),
                inputs = emptyList()
            ),
            values = values,
            invocation = context.request.invocationContext
        )
        WebActionWebViewPool.destroy(current.id)
        WebActionsRepository.delete(androidContext, current.id)
        transactionId = ""
        if (context.submitsImmediately) {
            onConfirmed(execution)
        } else {
            committedResult = execution
            committedFieldsJson = stringMapToJson(values)
        }
    }

    if (frozenResult != null) {
        val fields = OutputFormatter.fields(frozenResult, false)
        CommittedWebResult(
            title = "Web workflow complete",
            summary = fields[WebRoundtripFields.RESULT]?.toString().orEmpty().ifBlank { "Web workflow completed" },
            result = frozenResult,
            displayValues = listOf(
                "Host" to fields[WebRoundtripFields.HOST]?.toString().orEmpty(),
                "URL" to fields[WebRoundtripFields.URL]?.toString().orEmpty(),
                "Transaction ID" to fields[WebRoundtripFields.TRANSACTION_ID]?.toString().orEmpty(),
                "Completed" to fields[WebRoundtripFields.COMPLETED_TIME_ISO]?.toString().orEmpty()
            ),
            onDone = { onConfirmed(frozenResult) },
            onEditRetry = {
                committedResult = null
                committedFieldsJson = null
                screenStatus = "Ready to open another workflow"
            }
        )
        return
    }

    val isWaiting = transaction?.state == WebActionTransactionState.WAITING
    val callbackReceived = transaction?.state == WebActionTransactionState.CALLBACK_RECEIVED
    val elapsed = transaction?.let {
        val end = if (it.completedEpochMillis > 0L) it.completedEpochMillis else nowMillis
        (end - it.startedEpochMillis).coerceAtLeast(0L)
    } ?: 0L

    LaunchedEffect(callbackReceived, transaction?.id, context.submitsImmediately) {
        val completed = transaction
        if (callbackReceived && completed != null && context.submitsImmediately) {
            commitCompletion(completed)
        }
    }

    WebActionHero(
        eyebrow = "Web Actions",
        title = if (isWaiting) "Workflow in progress" else if (callbackReceived) "Workflow completed" else "Web roundtrip",
        subtitle = if (isWaiting) "Complete the web workflow below. MethodMesh will only finish when the configured return URL is called."
        else "A generic human-facing web action with an explicit completion contract.",
        status = if (callbackReceived) "Completion received — ready to commit" else screenStatus,
        host = transaction?.launchHost.orEmpty().ifBlank { hostOf(url) },
        active = isWaiting,
        success = callbackReceived,
        elapsedText = if (isWaiting || callbackReceived) elapsedLabel(elapsed) else ""
    )

    if (isWaiting && transaction != null) {
        WebSessionSurface(
            transaction = transaction,
            status = screenStatus,
            onStatus = { screenStatus = it },
            onCallback = { revision += 1; screenStatus = "Completion received" },
            onExit = {
                cleanupActive()
                screenStatus = "Web session closed before completion"
                screenError = ""
            }
        )
        SettingSection("Session") {
            CopyableValue("Transaction ID", transaction.id)
            CopyableValue("Host", transaction.launchHost)
            if (timeoutSeconds > 0) {
                Text("Timeout ${timeoutSeconds}s · elapsed ${elapsedLabel(elapsed)}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { cleanupActive(); onCancel() },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Cancel web session") }
        }
        return
    }

    if (callbackReceived && transaction != null) {
        SettingSection("Current result", "Commit freezes the result and hands it back through the canonical MethodMesh route.") {
            Text("✓ Web workflow completed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            CopyableValue("Host", transaction.launchHost)
            CopyableValue("Transaction ID", transaction.id)
            CopyableValue("Completed", transaction.completedTimeIso)
            Spacer(Modifier.height(12.dp))
            Button(onClick = { commitCompletion(transaction) }, modifier = Modifier.fillMaxWidth()) { Text("Commit") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { cleanupActive() }, modifier = Modifier.fillMaxWidth()) { Text("Discard and retry") }
        }
        return
    }

    SettingSection("Workflow") {
        if (context.settingShouldBeShown("url")) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Workflow URL") },
                placeholder = { Text("https://service.example/workflow") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CopyableValue("Configured host", hostOf(url))
        }
    }
    SettingSection("Completion", "Use a placeholder when the service expects its return URL inside an existing URL template; otherwise MethodMesh adds/replaces the named query parameter.") {
        if (context.settingShouldBeShown("callback_parameter")) {
            OutlinedTextField(
                value = callbackParameter,
                onValueChange = { callbackParameter = it },
                label = { Text("Return URL parameter") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
        }
        if (context.settingShouldBeShown("callback_placeholder")) {
            OutlinedTextField(
                value = callbackPlaceholder,
                onValueChange = { callbackPlaceholder = it },
                label = { Text("Return URL placeholder") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
    SettingSection("Session & security") {
        if (context.settingShouldBeShown("timeout_seconds")) {
            OutlinedTextField(
                value = timeoutText,
                onValueChange = { timeoutText = it.filter(Char::isDigit).take(5) },
                label = { Text("Timeout (seconds)") },
                supportingText = { Text("0 waits indefinitely") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }
        if (context.settingShouldBeShown("allow_insecure_http")) {
            SecurityToggleRow(allowHttp) { allowHttp = it }
        }
    }
    if (screenError.isNotBlank()) ErrorPanel(screenError)
    Spacer(Modifier.height(12.dp))
    Button(onClick = ::startRoundtrip, modifier = Modifier.fillMaxWidth()) { Text("Open workflow") }
    if (context.stepNumber > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = { cleanupActive(); onCancel() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }

    val canAutoStart = context.startsImmediately && url.isNotBlank()
    LaunchedEffect(canAutoStart) {
        if (canAutoStart && transactionId.isBlank() && committedFieldsJson == null) startRoundtrip()
    }
}

@Composable
private fun WebOpenScreen(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val settings = context.action.settings
    var url by rememberSaveable(context.action.canonicalId) { mutableStateOf(settings.setting("url").orEmpty()) }
    var allowHttp by rememberSaveable(context.action.canonicalId) {
        mutableStateOf(settings.setting("allow_insecure_http")?.toBooleanStrictOrNull() ?: false)
    }
    var screenError by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var workingFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var committedFieldsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
    var workingResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }

    LaunchedEffect(url, allowHttp) {
        context.onSettingsChanged(mapOf("url" to redactedWebUrl(url), "allow_insecure_http" to allowHttp.toString()))
    }

    fun executionFrom(values: Map<String, String>): ExecutionResult = As100WebOpenMethod.result(
        request = As100WebOpenMethod.request(
            action = context.action.canonicalId,
            context = context.request.invocationContext.asMap(context.action.canonicalId) + mapOf(
                "url" to values[WebOpenFields.URL].orEmpty(),
                "host" to values[WebOpenFields.HOST].orEmpty()
            ),
            signals = emptyList(),
            inputs = emptyList()
        ),
        values = values,
        invocation = context.request.invocationContext
    )

    val restoredWorking = remember(workingFieldsJson) { workingFieldsJson?.let(::stringMapFromJson)?.let(::executionFrom) }
    val restoredCommitted = remember(committedFieldsJson) { committedFieldsJson?.let(::stringMapFromJson)?.let(::executionFrom) }
    val liveResult = workingResult ?: restoredWorking
    val frozenResult = committedResult ?: restoredCommitted

    fun openPage() {
        if (liveResult != null || frozenResult != null) return
        screenError = ""
        val safeUrl = runCatching { requireWebUrl(url, allowHttp, "web address") }
            .getOrElse {
                screenError = it.message.orEmpty()
                return
            }
        val host = hostOf(safeUrl)
        val safeResultUrl = redactedWebUrl(safeUrl)
        val values = As100WebOpenMethod.success(safeResultUrl, host, Instant.now().toString())
        val execution = executionFrom(values)
        val dispatched = runCatching {
            androidContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)))
        }
        dispatched.onFailure {
            screenError = "No browser could open this address."
            return
        }
        if (context.submitsImmediately) {
            onConfirmed(execution)
        } else {
            workingResult = execution
            workingFieldsJson = stringMapToJson(values)
        }
    }

    if (frozenResult != null) {
        val fields = OutputFormatter.fields(frozenResult, false)
        CommittedWebResult(
            title = "Web page dispatched",
            summary = fields[WebOpenFields.RESULT]?.toString().orEmpty().ifBlank { "Web page opened" },
            result = frozenResult,
            displayValues = listOf(
                "Host" to fields[WebOpenFields.HOST]?.toString().orEmpty(),
                "URL" to fields[WebOpenFields.URL]?.toString().orEmpty(),
                "Launched" to fields[WebOpenFields.LAUNCHED_TIME_ISO]?.toString().orEmpty()
            ),
            onDone = { onConfirmed(frozenResult) },
            onEditRetry = {
                committedResult = null
                committedFieldsJson = null
                workingResult = null
                workingFieldsJson = null
            }
        )
        return
    }

    val liveFields = liveResult?.let { OutputFormatter.fields(it, false) }.orEmpty()
    WebActionHero(
        eyebrow = "Web Actions",
        title = if (liveResult == null) "Open web page" else "Browser opened",
        subtitle = if (liveResult == null) "A clean browser dispatch for links that do not need a completion callback."
        else "Dispatch succeeded. This result says only that Android opened the address — not that the remote site completed an action.",
        status = if (liveResult == null) "Ready to open" else "Dispatch complete — ready to commit",
        host = if (liveResult == null) hostOf(url) else liveFields[WebOpenFields.HOST]?.toString().orEmpty(),
        active = false,
        success = liveResult != null
    )

    if (liveResult != null) {
        SettingSection("Current result", "Commit freezes this browser-dispatch result.") {
            Text("✓ Browser dispatch complete", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            CopyableValue("Host", liveFields[WebOpenFields.HOST]?.toString().orEmpty())
            CopyableValue("URL", liveFields[WebOpenFields.URL]?.toString().orEmpty())
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    committedResult = liveResult
                    committedFieldsJson = workingFieldsJson
                    workingResult = null
                    workingFieldsJson = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Commit") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    workingResult = null
                    workingFieldsJson = null
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Open another") }
        }
        return
    }

    SettingSection("Page") {
        if (context.settingShouldBeShown("url")) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Web address") },
                placeholder = { Text("https://example.org") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            CopyableValue("Configured host", hostOf(url))
        }
    }
    if (context.settingShouldBeShown("allow_insecure_http")) {
        SettingSection("Security") { SecurityToggleRow(allowHttp) { allowHttp = it } }
    }
    if (screenError.isNotBlank()) ErrorPanel(screenError)
    Spacer(Modifier.height(12.dp))
    Button(onClick = ::openPage, modifier = Modifier.fillMaxWidth()) { Text("Open in browser") }
    if (context.stepNumber > 1) {
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back") }
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }

    val canAutoStart = context.startsImmediately && url.isNotBlank()
    LaunchedEffect(canAutoStart) {
        if (canAutoStart && liveResult == null && frozenResult == null) openPage()
    }
}

@Composable
private fun WebSessionSurface(
    transaction: WebActionTransaction,
    status: String,
    onStatus: (String) -> Unit,
    onCallback: (WebActionTransaction) -> Unit,
    onExit: () -> Unit
) {
    val androidContext = LocalContext.current
    var pendingFileCallback by remember(transaction.id) { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingGeoOrigin by remember(transaction.id) { mutableStateOf("") }
    var pendingGeoCallback by remember(transaction.id) { mutableStateOf<GeolocationPermissions.Callback?>(null) }

    fun locationPermissionGranted(): Boolean =
        ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val callback = pendingGeoCallback
        val origin = pendingGeoOrigin
        pendingGeoCallback = null
        pendingGeoOrigin = ""
        callback?.invoke(origin, locationPermissionGranted(), false)
        if (!locationPermissionGranted()) onStatus("Location permission was not granted.")
    }

    fun handleFileChooser(
        callback: ValueCallback<Array<Uri>>,
        params: WebChromeClient.FileChooserParams
    ): Boolean {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        return runCatching {
            fileChooserLauncher.launch(params.createIntent())
            true
        }.getOrElse {
            pendingFileCallback = null
            callback.onReceiveValue(null)
            onStatus("No compatible file/camera picker is available.")
            true
        }
    }

    fun handleGeolocation(origin: String, callback: GeolocationPermissions.Callback) {
        if (locationPermissionGranted()) {
            callback.invoke(origin, true, false)
            return
        }
        pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
        pendingGeoOrigin = origin
        pendingGeoCallback = callback
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // The live form is intentionally separated from the MethodMesh dashboard.
    // A full-screen dialog gives ODK Web Forms/Enketo the same usable viewport as
    // a dedicated kiosk browser while preserving the transaction in this activity.
    // The dialog closes itself when the one-shot completion redirect is consumed.
    Dialog(
        onDismissRequest = { /* kiosk surface: explicit Exit only */ },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
            Column(Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = Color(0xFF0C1A24)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                transaction.launchHost.ifBlank { "Web form" },
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            Text(
                                status,
                                color = Color.White.copy(alpha = 0.68f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(onClick = onExit) {
                            Text("Exit form")
                        }
                    }
                }

                AndroidView(
                    factory = { viewContext ->
                        WebActionWebViewPool.obtain(
                            context = viewContext,
                            transactionId = transaction.id,
                            launchUrl = transaction.launchUrl,
                            onCallback = onCallback,
                            onStatus = onStatus,
                            onFileChooser = ::handleFileChooser,
                            onGeolocationRequest = ::handleGeolocation
                        )
                    },
                    update = { },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .navigationBarsPadding()
                        .background(Color.White)
                )
            }
        }
    }

    DisposableEffect(transaction.id) {
        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            pendingGeoCallback?.invoke(pendingGeoOrigin, false, false)
            pendingGeoCallback = null
            pendingGeoOrigin = ""
            WebActionWebViewPool.detach(transaction.id)
        }
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Could not continue", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ModeButton(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier.padding(2.dp)) { Text("✓ $label") }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier.padding(2.dp)) { Text(label) }
    }
}

private fun hasValidatedInternet(context: android.content.Context): Boolean {
    val manager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

private fun safeCentralRequestContext(
    host: String,
    linkKind: String
): Map<String, String> = mapOf(
    "host" to host,
    "link_kind" to linkKind,
    "renderer_mode" to "central_selected",
    "source_url_returned" to "false"
)

private fun centralLinkKindLabel(wireValue: String): String =
    CentralLinkKind.values().firstOrNull { it.wireValue == wireValue }?.label
        ?: wireValue.ifBlank { "Central web-form link" }

private fun safeEnketoRequestContext(serverUrl: String, formId: String, singleMode: String): Map<String, String> = mapOf(
    "server_url" to serverUrl,
    "form_id" to formId,
    "single_mode" to singleMode,
    "credential_present" to "true"
)

private fun safeRoundtripRequestContext(url: String, callbackParameter: String): Map<String, String> = mapOf(
    "url" to url,
    "callback_parameter" to callbackParameter
)

private fun buildRoundtripLaunchUrl(
    originalUrl: String,
    callbackParameter: String,
    callbackPlaceholder: String,
    callbackUrl: String,
    allowInsecureHttp: Boolean
): String {
    if (callbackPlaceholder.isNotBlank() && originalUrl.contains(callbackPlaceholder)) {
        val replaced = originalUrl.replace(callbackPlaceholder, Uri.encode(callbackUrl))
        requireWebUrl(replaced, allowInsecureHttp = allowInsecureHttp, label = "workflow URL")
        return replaced
    }
    require(callbackParameter.isNotBlank()) {
        "Return URL parameter is required when the workflow URL does not contain the configured placeholder."
    }
    val source = Uri.parse(originalUrl)
    val builder = source.buildUpon().clearQuery()
    source.queryParameterNames.forEach { name ->
        if (name != callbackParameter) {
            source.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
    }
    builder.appendQueryParameter(callbackParameter, callbackUrl)
    return builder.build().toString()
}

private fun Map<String, String>.setting(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun stringMapToJson(values: Map<String, String>): String = JSONObject(values).toString()

private fun stringMapFromJson(raw: String): Map<String, String> = buildMap {
    val json = JSONObject(raw)
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        put(key, json.optString(key))
    }
}

private fun redactSecret(message: String, secret: String): String =
    if (secret.isBlank()) message else message.replace(secret, "[redacted]")
