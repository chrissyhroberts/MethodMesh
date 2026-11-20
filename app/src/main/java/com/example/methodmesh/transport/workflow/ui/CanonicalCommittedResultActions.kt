package com.example.methodmesh.transport.workflow.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.LegacyDownloadsPermission
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode

/**
 * Canonical post-Commit actions for capability-specific screens that do not use
 * [CapabilityScreenScaffold]. This keeps exceptional UIs on the same native
 * Share/Save contract instead of making each module reconstruct Android intents.
 */
@Composable
internal fun CanonicalCommittedResultActions(
    result: ExecutionResult,
    label: String,
    screenContext: CapabilityScreenContext,
    onDone: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    primaryText: String? = null,
    attachments: List<ResultShare.Attachment>? = null,
    onBusyChanged: ((Boolean) -> Unit)? = null,
    completionLabel: String? = null,
    shareButtonLabel: String = "Share"
) {
    val appContext = LocalContext.current
    // Module-owned primary text/attachment policy is presentation only; FULL stays canonical.
    val projection = remember(result, primaryText, attachments) {
        NativeCommittedResultProjection.from(result).let { original ->
            original.copy(text = primaryText ?: original.text,
                attachments = attachments?.map { it.name to it.uri } ?: original.attachments)
        }
    }
    val owner = checkNotNull(LocalView.current.findViewTreeViewModelStoreOwner())
    val store = remember(owner) { ViewModelProvider(owner)[CommittedActionStore::class.java] }
    val actionKey = rememberSaveable(result.request.id.value) { UUID.randomUUID().toString() }
    val state = remember(store, actionKey) { store.slot(actionKey) }
    val activity = appContext.actionHostActivity()
    DisposableEffect(store, actionKey, activity) {
        onDispose { if (activity != null && !activity.isChangingConfigurations) store.forget(actionKey) }
    }
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }

    val copiedText = ResultShare.buildShareText(
        projection.text,
        if (includeFullJson) projection.fullJson else ""
    )

    val selectedJson = if (includeFullJson) projection.fullJson else ""
    val hasPayload = projection.text.isNotBlank() || projection.attachments.isNotEmpty() || selectedJson.isNotBlank()
    val enabled = !state.busy && !state.finishPending && !state.finished
    SideEffect { onBusyChanged?.invoke(!enabled) }
    val presetAction = if (screenContext.isNativePresetRun && screenContext.isLastStep && !screenContext.submitsImmediately) {
        PresetResultAction.normalize(screenContext.request.settings["methodmesh_preset_result_action"]
            ?: screenContext.request.settings["input_methodmesh_preset_result_action"] ?: PresetResultAction.HOME)
    } else PresetResultAction.HOME
    val doneLabel = completionLabel ?: when (presetAction) {
        PresetResultAction.SHARE -> "Share and finish"
        PresetResultAction.SAVE -> "Save and finish"
        else -> "Done"
    }

    LaunchedEffect(state.finishPending, onDone) {
        if (onDone != null && state.takeFinish()) onDone()
    }

    fun startAction(finishing: Boolean, failureLabel: String, before: () -> Unit = {}, operation: suspend () -> String) {
        store.start(state, finishing, failureLabel, before, operation)
        // Fence parent reacquisition immediately, before another queued input event.
        // The effect above also reports asynchronous completion and recreation state.
        onBusyChanged?.invoke(state.busy || state.finishPending || state.finished)
    }

    val recipientHint = screenContext.request.settings["methodmesh_share_recipient"]
        ?: screenContext.request.settings["input_methodmesh_share_recipient"].orEmpty()

    fun share(finishing: Boolean) {
        startAction(finishing, "Share failed") {
            ResultShare.share(appContext, "Share $label", projection.text,
                projection.attachments.map { (name, uri) -> ResultShare.Attachment(name, uri) },
                jsonText = selectedJson, fileLabel = label, recipientHint = recipientHint)
            "Sharing committed result…"
        }
    }
    fun save(finishing: Boolean) {
        // Permission-only UI must be launched on the UI thread before any write.
        // The long-lived IO operation uses the application, not a departing Activity.
        val storageContext = appContext.applicationContext
        startAction(finishing, "Save failed", before = { LegacyDownloadsPermission.requireAccess(appContext) }) {
            val saved = withContext(Dispatchers.IO) {
                OutputExportRepository.saveToDownloads(storageContext, label, projection.text,
                    projection.attachments.map { it.second.toString() }, selectedJson)
            }
            "Saved "+saved.summary
        }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Include full JSON / audit", style = MaterialTheme.typography.titleSmall)
                Text(if (includeFullJson) "Share includes audit in the text payload; Save adds a metadata file. Media stays unchanged."
                    else "Off by default for manual runs.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it }, enabled = enabled)
        }
        Button(onClick = { share(false) }, enabled = enabled && hasPayload, modifier = Modifier.fillMaxWidth()) { Text(shareButtonLabel) }
        OutlinedButton(onClick = {
            startAction(false, "Copy failed") {
                check(copiedText.isNotBlank()) { "No text result to copy." }
                appContext.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("MethodMesh result", copiedText))
                "Copied committed result."
            }
        }, enabled = enabled && copiedText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Copy") }
        OutlinedButton(onClick = { save(false) }, enabled = enabled && hasPayload, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.busy) "Working…" else "Save to Downloads")
        }
        if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onDone != null || onEdit != null) Spacer(Modifier.height(2.dp))
        if (onDone != null) Button(onClick = {
            when (presetAction) {
                PresetResultAction.SHARE -> share(true)
                PresetResultAction.SAVE -> save(true)
                else -> startAction(true, "Completion failed") { "" }
            }
        }, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text(doneLabel) }
        if (onEdit != null) OutlinedButton(onClick = onEdit, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Edit / new run") }
    }
}

private tailrec fun Context.actionHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actionHostActivity()
    else -> null
}

internal data class NativeCommittedResultProjection(
    val text: String,
    val attachments: List<Pair<String, Uri>>,
    val fullJson: String
) {
    companion object {
        fun from(result: ExecutionResult): NativeCommittedResultProjection {
            val coreFields = OutputFormatter.projectFields(
                OutputFormatter.fields(result, includeProvenance = false),
                OutputFormatter.PayloadMode.CORE,
                result.status
            )
            val allFields = OutputFormatter.fields(result, includeProvenance = false)
            val attachments = ResultShare.shareableMediaAttachments(allFields)
                .map { it.name to it.uri }
            val text = humanText(coreFields)
            val fullJson = OutputFormatter.format(
                result = result,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = OutputFormatter.PayloadMode.FULL
            )
            return NativeCommittedResultProjection(text, attachments, fullJson)
        }

        private fun humanText(fields: Map<String, Any?>): String {
            listOf("barcode_payload", "plus_code").forEach { key ->
                fields[key]?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
            }
            val textLike = fields.entries
                .filterNot { (key, value) -> ResultShare.isShareableMediaField(key, value?.toString().orEmpty()) }
                .filterNot { (key, _) -> key.endsWith("_name") || key.endsWith("_filename") }
            if (textLike.size == 1) return textLike.first().value?.toString().orEmpty()
            return textLike.joinToString("\n") { (key, value) ->
                "${friendlyLabel(key)}: ${value?.toString().orEmpty()}"
            }
        }

        private fun friendlyLabel(key: String): String = key
            .removePrefix("methodmesh_")
            .replace("_pct", " %")
            .replace("_c", " °C")
            .replace("_cm", " cm")
            .replace("_uri", "")
            .replace('_', ' ')
            .trim()
            .split(' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
