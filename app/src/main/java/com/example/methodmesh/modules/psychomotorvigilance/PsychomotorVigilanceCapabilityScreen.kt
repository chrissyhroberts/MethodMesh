package com.example.methodmesh.modules.psychomotorvigilance

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

object PsychomotorVigilanceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PsychomotorVigilanceMethod.ID
    override val title = "Psychomotor vigilance test (PVT)"
    override val description = "Measure simple reaction time and sustained attention using a published PVT paradigm."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var protocolKey by rememberSaveable {
            mutableStateOf(
                context.action.settings["protocol"]
                    ?: context.action.settings["input_protocol"]
                    ?: PvtProtocol.STANDARD_10.key
            )
        }
        var countdownText by rememberSaveable {
            mutableStateOf(
                context.action.settings["countdown_seconds"]
                    ?: context.action.settings["input_countdown_seconds"]
                    ?: "3"
            )
        }
        var active by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var liveResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }

        val protocol = PvtProtocol.fromKey(protocolKey)
        val countdownSeconds = countdownText.toIntOrNull()?.coerceIn(0, 10) ?: 3

        val restoredResult = remember(resultValuesJson) {
            resultValuesJson
                ?.let(::valuesFromJson)
                ?.let { values ->
                    val request = As100PsychomotorVigilanceMethod.request(
                        action = As100PsychomotorVigilanceMethod.ID,
                        context = context.request.invocationContext.asMap(As100PsychomotorVigilanceMethod.ID) +
                            context.action.settings +
                            mapOf("protocol" to protocolKey, "countdown_seconds" to countdownSeconds.toString()),
                        signals = emptyList(),
                        inputs = emptyList()
                    )
                    As100PsychomotorVigilanceMethod.result(request, values, context.request.invocationContext)
                }
        }
        val capturedResult = liveResult ?: restoredResult

        LaunchedEffect(protocolKey, countdownSeconds) {
            context.onSettingsChanged(
                mapOf(
                    "protocol" to protocolKey,
                    "countdown_seconds" to countdownSeconds.toString()
                )
            )
        }

        LaunchedEffect(context.presentationMode, context.startsImmediately, capturedResult) {
            if (
                (context.presentationMode == CapabilityPresentationMode.IntentLaunch || context.startsImmediately) &&
                !launched && capturedResult == null
            ) {
                launched = true
                active = true
                status = "Test started."
            }
        }

        LockActivityForTiming(active)

        fun finishSession(session: PvtSession) {
            val values = As100PsychomotorVigilanceMethod.valuesForSession(session)
            val request = As100PsychomotorVigilanceMethod.request(
                action = As100PsychomotorVigilanceMethod.ID,
                context = context.request.invocationContext.asMap(As100PsychomotorVigilanceMethod.ID) +
                    context.action.settings +
                    mapOf("protocol" to protocol.key, "countdown_seconds" to countdownSeconds.toString()),
                signals = emptyList(),
                inputs = emptyList()
            )
            val execution = As100PsychomotorVigilanceMethod.result(
                request = request,
                values = values,
                invocation = context.request.invocationContext
            )
            liveResult = execution
            resultValuesJson = valuesToJson(values)
            active = false
            status = values[PsychomotorVigilanceFields.RESULT].orEmpty().ifBlank { "Complete." }
            if (context.submitsImmediately) onConfirmed(execution)
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult?.let {
                val fields = OutputFormatter.fields(it, includeProvenance = false)
                fields[PsychomotorVigilanceFields.RESULT]
                    ?.let { value -> mapOf(PsychomotorVigilanceFields.RESULT to value) }
                    .orEmpty()
            }.orEmpty(),
            onBack = onBack,
            onRetry = {
                liveResult = null
                resultValuesJson = null
                status = "Ready to repeat."
                active = true
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "A simple visual reaction-time task based on the Psychomotor Vigilance Test. Tap the screen as soon as the millisecond counter appears; do not anticipate it.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Development note: the published PVT paradigm is validated, but this MethodMesh implementation has not yet been physically calibrated for display/touch latency on each Android device.",
                style = MaterialTheme.typography.bodySmall
            )

            if (context.settingShouldBeShown("protocol")) {
                Spacer(Modifier.height(12.dp))
                Text("Protocol", style = MaterialTheme.typography.titleSmall)
                PvtProtocol.all.forEach { option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = protocolKey == option.key,
                            onClick = { protocolKey = option.key }
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(option.displayName)
                            Text(
                                if (option == PvtProtocol.STANDARD_10) "2–10 s random interval; lapse ≥500 ms" else "1–4 s random interval; lapse ≥355 ms",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            if (context.settingShouldBeShown("countdown_seconds")) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = countdownText,
                    onValueChange = { value -> countdownText = value.filter(Char::isDigit).take(2) },
                    label = { Text("Pre-test countdown (0–10 seconds)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                "Selected: ${protocol.displayName}. The task runs locally and sends no test data over the network.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(10.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    liveResult = null
                    resultValuesJson = null
                    status = "Test started."
                    active = true
                }
            ) {
                Text("Start ${if (protocol == PvtProtocol.STANDARD_10) "10-minute PVT" else "3-minute PVT-B"}")
            }
            Text(status, modifier = Modifier.padding(top = 8.dp))
        }

        if (active) {
            PvtTestDialog(
                androidContext = androidContext,
                protocol = protocol,
                countdownSeconds = countdownSeconds,
                onComplete = ::finishSession,
                onCancel = {
                    active = false
                    status = "Test cancelled."
                    if (context.submitsImmediately) onCancel()
                }
            )
        }
    }
}

@Composable
private fun PvtTestDialog(
    androidContext: Context,
    protocol: PvtProtocol,
    countdownSeconds: Int,
    onComplete: (PvtSession) -> Unit,
    onCancel: () -> Unit
) {
    var reactionView by remember { mutableStateOf<PvtReactionView?>(null) }
    Dialog(
        onDismissRequest = {
            reactionView?.cancelTest(notify = false)
            onCancel()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    PvtReactionView(
                        context = androidContext,
                        protocol = protocol,
                        countdownSeconds = countdownSeconds,
                        listener = object : PvtReactionView.Listener {
                            override fun onSessionComplete(session: PvtSession) = onComplete(session)
                            override fun onCancelled() = onCancel()
                        }
                    ).also { view ->
                        reactionView = view
                        view.start()
                    }
                }
            )
        }
    }
    DisposableEffect(Unit) {
        onDispose { reactionView?.cancelTest(notify = false) }
    }
}

@Composable
private fun LockActivityForTiming(active: Boolean) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, active) {
        if (!active || activity == null) {
            onDispose { }
        } else {
            val previousOrientation = activity.requestedOrientation
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose {
                activity.requestedOrientation = previousOrientation
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun valuesToJson(values: Map<String, String>): String =
    JSONObject().apply { values.forEach { (key, value) -> put(key, value) } }.toString()

private fun valuesFromJson(json: String): Map<String, String> =
    JSONObject(json).let { obj ->
        buildMap {
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                put(key, obj.optString(key, ""))
            }
        }
    }
