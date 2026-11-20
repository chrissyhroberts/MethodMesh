package com.example.methodmesh.modules.screentools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import org.json.JSONObject

private fun CapabilityScreenContext.memorySetting(key: String, default: String = ""): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

object ScreenMemoryCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = ScreenMemoryMethod.ID
    override val title = "Selection memory"
    override val description = "Encrypt, version, load and explicitly delete reusable visual selections."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        var entries by remember { mutableStateOf(ScreenMemoryStore.list(appContext)) }
        var memoryId by rememberSaveable { mutableStateOf(context.memorySetting("memory_id")) }
        var name by rememberSaveable { mutableStateOf(context.memorySetting("memory_name", "Screen selections")) }
        var revisionText by rememberSaveable { mutableStateOf("") }
        var status by rememberSaveable { mutableStateOf("Selection memory stores definitions only, never screenshots or OCR text.") }
        var pendingDelete by remember { mutableStateOf<String?>(null) }
        var result by rememberRetainedExecutionResult("${context.action.canonicalId}:memory")

        fun resultFor(definition: JSONObject, operation: String): ExecutionResult {
            val request = ScreenMemoryMethod.request(
                action = ScreenMemoryMethod.ID,
                context = context.request.invocationContext.asMap(ScreenMemoryMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            return ScreenMemoryMethod.result(request, mapOf(
                ScreenMemoryFields.STATUS to "succeeded",
                ScreenMemoryFields.ERROR to "",
                ScreenMemoryFields.MEMORY_ID to definition.optString("id"),
                ScreenMemoryFields.MEMORY_VERSION to definition.optInt("version").toString(),
                ScreenMemoryFields.MEMORY_NAME to definition.optString("name"),
                ScreenMemoryFields.SELECTIONS_JSON to definition.optJSONArray("fields")?.toString().orEmpty(),
                ScreenMemoryFields.RETENTION to "encrypted_no_backup_memory:$operation"
            ), context.request.invocationContext)
        }

        fun load(id: String, version: Int? = null) {
            val definition = ScreenMemoryStore.get(appContext, id, version)
            if (definition == null) {
                status = "Selection memory or revision not found."
                return
            }
            runCatching { ScreenMemoryStore.selections(definition) }
                .onSuccess { selections ->
                    ScreenToolsSession.selections.clear()
                    ScreenToolsSession.selections.addAll(selections)
                    memoryId = definition.optString("id")
                    name = definition.optString("name", "Screen selections")
                    status = "Loaded ${definition.optString("id")} v${definition.optInt("version")}."
                    result = resultFor(definition, "load")
                }
                .onFailure { status = "Invalid selection memory: ${it.message.orEmpty()}" }
        }

        fun save() {
            runCatching {
                ScreenMemoryStore.save(appContext, name, ScreenToolsSession.selections.toList(), memoryId)
            }.onSuccess { definition ->
                memoryId = definition.optString("id")
                entries = ScreenMemoryStore.list(appContext)
                status = "Saved ${definition.optString("id")} v${definition.optInt("version")}."
                result = resultFor(definition, "save")
            }.onFailure { status = "Could not save selection memory: ${it.message.orEmpty()}" }
        }

        pendingDelete?.let { id ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete selection memory?") },
                text = { Text("Delete '$id' and every saved revision? This cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        val deleted = ScreenMemoryStore.delete(appContext, id)
                        entries = ScreenMemoryStore.list(appContext)
                        if (memoryId == id) memoryId = ""
                        status = if (deleted) "Deleted $id and all revisions." else "Nothing was deleted."
                        pendingDelete = null
                    }) { Text("Delete all revisions") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Encrypted definitions remain in no-backup app storage; screenshots and OCR values are excluded.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Memory name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(memoryId, { memoryId = it }, label = { Text("Memory ID (blank creates new)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(revisionText, { revisionText = it.filter(Char::isDigit) }, label = { Text("Revision to load (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = ::save, enabled = ScreenToolsSession.selections.isNotEmpty()) { Text(if (memoryId.isBlank()) "Save new" else "Save new version") }
                OutlinedButton(onClick = { load(memoryId, revisionText.toIntOrNull()) }, enabled = memoryId.isNotBlank()) { Text("Load") }
            }
            Spacer(Modifier.height(8.dp))
            Text("${ScreenToolsSession.selections.size} current selection(s)")
            Spacer(Modifier.height(8.dp))
            entries.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(entry.optString("name"))
                        Text("${entry.optString("id")} · v${entry.optInt("version")}", style = MaterialTheme.typography.bodySmall)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedButton(onClick = { load(entry.optString("id")) }) { Text("Load") }
                        OutlinedButton(onClick = { pendingDelete = entry.optString("id") }) { Text("Delete") }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
