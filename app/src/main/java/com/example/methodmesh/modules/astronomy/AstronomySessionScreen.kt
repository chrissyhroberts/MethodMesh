package com.example.methodmesh.modules.astronomy

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

@Composable
internal fun AstronomySessionScreen(
    context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit,
    repository: (Context) -> AstronomyRepository = { AstronomyRepository(it) }
) {
    val app = LocalContext.current.applicationContext
    val repo = remember { repository(app) }
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
    val store = remember(owner) { ViewModelProvider(owner)[AstronomySessionStore::class.java] }
    val key = rememberSaveable { UUID.randomUUID().toString() }
    val state = remember(store, key) { store.run(key, repo) }
    val activity = LocalContext.current.sessionActivity()
    var hadResult by rememberSaveable { mutableStateOf(false) }
    var discardRequested by rememberSaveable { mutableStateOf(false) }
    var loadedSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedVersion by rememberSaveable { mutableStateOf(-1) }
    val initial = remember {
        astronomyEditableInputs(As100SessionMethod, context.action.settings)
    }
    var settingsJson by rememberSaveable { mutableStateOf(JSONObject(initial).toString()) }
    val settings = settingsMap(settingsJson)
    val actionId = context.action.canonicalId
    val invocation = context.request.invocationContext
    val returnData = remember(context.action, context.request) {
        AstronomySessionReturn(actionId, invocation.asMap(actionId) + context.action.settings.toMap(), invocation)
    }
    var tickMs by remember { mutableStateOf(System.currentTimeMillis()) }
    DisposableEffect(store, key, activity) {
        onDispose { if (activity != null && !activity.isChangingConfigurations) store.forget(key) }
    }
    LaunchedEffect(Unit) { if (!hadResult && !state.initialized && !state.busy) state.load() }
    SideEffect { if (state.result != null) hadResult = true }
    LaunchedEffect(state.active?.id, state.loadVersion) {
        val current = state.active
        if (current != null && (loadedSessionId != current.id || loadedVersion != state.loadVersion)) {
            loadedSessionId = current.id
            loadedVersion = state.loadVersion
            settingsJson = JSONObject(settingsMap(settingsJson) + mapOf("target" to current.target,
                "equipment" to current.equipment, "notes" to current.notes)).toString()
        }
    }
    LaunchedEffect(settingsJson, state.active?.id, loadedSessionId, loadedVersion) {
        val current = state.active
        val frozen = settingsMap(settingsJson)
        if (current != null && loadedSessionId == current.id && loadedVersion == state.loadVersion) state.saveFields(current.id, frozen)
        context.onSettingsChanged(frozen)
    }
    LaunchedEffect(state.active?.id, state.active?.state) {
        while (state.active?.state == "running") { tickMs = System.currentTimeMillis(); delay(1000) }
    }
    if (hadResult && state.result == null) {
        CapturedExecutionRecovery({ hadResult = false; state.load() }, onCancel)
        return
    }
    if (discardRequested) AlertDialog(onDismissRequest = { discardRequested = false },
        title = { Text("Discard unfinished session?") },
        text = { Text("Its timer and notes will be removed from unfinished sessions.") },
        confirmButton = { TextButton(onClick = { discardRequested = false; state.discard() }) { Text("Discard") } },
        dismissButton = { TextButton(onClick = { discardRequested = false }) { Text("Keep session") } })
    val result = state.result
    CapabilityScreenScaffold("Astronomy session", As100SessionMethod.id, context, context.stepNumber > 1, result,
        result?.let { mapOf(SessionFields.RESULT to OutputFormatter.fields(it, false)[SessionFields.RESULT]) }.orEmpty(),
        { if (!state.busy) onBack() }, { if (!state.busy) { state.newRun(); hadResult = false } },
        { state.result?.let(onConfirmed) }, { if (!state.busy) onCancel() }) {
        Text("Start, pause/resume and stop an observing session. Unfinished sessions remain stored until explicitly completed or discarded.")
        state.active?.let { Text("Current session: " + it.target) }
        listOf("target" to "Target", "equipment" to "Equipment", "notes" to "Notes",
            "conditions_summary" to "Conditions summary", "conditions_score" to "Conditions score",
            "sky_test_summary" to "Sky-test summary").forEach { (field, label) ->
            if (context.settingShouldBeShown(field)) OutlinedTextField(settings[field].orEmpty(), { entered ->
                settingsJson = JSONObject(settingsMap(settingsJson) + (field to entered)).toString()
            }, label = { Text(label) }, enabled = state.initialized && !state.busy, modifier = Modifier.fillMaxWidth())
        }
        if (context.settingShouldBeShown("save_session")) Row {
            Text("Save to local session history", Modifier.weight(1f))
            Switch(settings["save_session"]?.toBooleanStrictOrNull() ?: false, { checked ->
                settingsJson = JSONObject(settingsMap(settingsJson) + ("save_session" to checked.toString())).toString()
            }, enabled = state.initialized && !state.busy)
        }
        Spacer(Modifier.height(8.dp))
        val active = state.active
        if (!state.initialized) Button(onClick = state::load, enabled = !state.busy) { Text("Reload session") }
        else if (active == null) Button(onClick = { state.start(settingsMap(settingsJson)) }, enabled = !state.busy) { Text("Start session") }
        else {
            val seconds = active.activeDurationMs(tickMs) / 1000
            Text(String.format(Locale.US, "%02d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60))
            Text("Started " + active.startIso)
            Row {
                OutlinedButton(onClick = {
                    if (state.active?.state == "running") state.pause(settingsMap(settingsJson)) else state.resume(settingsMap(settingsJson))
                }, enabled = !state.busy) { Text(if (active.state == "running") "Pause" else "Resume") }
                Button(onClick = { state.stop(settingsMap(settingsJson), returnData) }, enabled = !state.busy) { Text("Stop") }
            }
            OutlinedButton(onClick = { discardRequested = true }, enabled = !state.busy) { Text("Discard session") }
        }
        if (state.busy) Text("Saving session…")
        if (state.initialized) OutlinedButton(onClick = state::load, enabled = !state.busy) { Text("Reload unfinished session") }
        Text(state.status)
    }
}

private tailrec fun Context.sessionActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.sessionActivity()
    else -> null
}
