package com.example.methodmesh.modules.music

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.json.JSONObject

/** Module-local v1.05 presentation helpers. No shared UI needs music-specific knowledge. */
internal object MusicV105 {
    fun shouldReturnImmediately(context: CapabilityScreenContext): Boolean =
        context.submitsImmediately || context.completionMode == CapabilityCompletionMode.AutomaticReturn

    fun valuesToJson(values: Map<String, String>): String = JSONObject(values).toString()

    fun valuesFromJson(fields: MusicFieldSet, json: String): Map<String, String> {
        val obj = JSONObject(json)
        return fields.outputs.associateWith { obj.optString(it, "") }
    }

    fun humanLabel(fieldName: String): String = fieldName
        .substringAfterLast("music_")
        .replace('_', ' ')
        .split(' ')
        .joinToString(" ") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

    fun shareText(context: Context, text: String) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        runCatching { context.startActivity(Intent.createChooser(intent, "Share result")) }
            .onFailure { Toast.makeText(context, "Unable to open sharing.", Toast.LENGTH_SHORT).show() }
    }
}


@Composable
internal fun CopyableMusicText(
    value: String,
    label: String = "value",
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    fontWeight: FontWeight = FontWeight.Normal,
) {
    if (value.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Text(
        text = value,
        style = style ?: MaterialTheme.typography.bodyLarge,
        fontWeight = fontWeight,
        modifier = modifier.clickable {
            clipboard.setText(AnnotatedString(value))
            Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
        },
    )
}

@Composable
internal fun CopyableMusicValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
) {
    if (value.isBlank()) return
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(value))
                Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
            }
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                fontWeight = if (prominent) FontWeight.Bold else FontWeight.Normal,
            )
        }
    }
}

@Composable
internal fun MusicWorkingResult(
    method: MusicPureMethod,
    values: Map<String, String>,
    heading: String = "Current result",
    secondaryLimit: Int = 4,
) {
    val succeeded = values[method.fields.status] == "succeeded"
    if (!succeeded) {
        val error = values[method.fields.error].orEmpty()
        if (error.isNotBlank()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Check the current inputs", fontWeight = FontWeight.Bold)
                    CopyableMusicText(value = error, label = "error")
                }
            }
        }
        return
    }

    val primary = values[method.fields.result].orEmpty()
    CopyableMusicValue(heading, primary, prominent = true)

    method.fields.core
        .filterNot { it.contains("json", ignoreCase = true) || it.contains("payload", ignoreCase = true) }
        .filter { core -> values[method.fields.field(core)].orEmpty().isNotBlank() && values[method.fields.field(core)] != primary }
        .take(secondaryLimit)
        .forEach { core ->
            val field = method.fields.field(core)
            CopyableMusicValue(MusicV105.humanLabel(core), values[field].orEmpty())
        }
}

@Composable
internal fun MusicCommittedPanel(
    method: MusicPureMethod,
    values: Map<String, String>,
    execution: ExecutionResult,
    onDone: (ExecutionResult) -> Unit,
    onEdit: () -> Unit,
) {
    val androidContext = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var includeAudit by rememberSaveable(method.id) { mutableStateOf(false) }
    var showTechnical by rememberSaveable(method.id) { mutableStateOf(false) }
    val beef = values[method.fields.result].orEmpty()
    val fullJson = MusicV105.valuesToJson(values)
    val exportText = if (includeAudit) "$beef\n\n$fullJson" else beef

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Committed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (beef.isNotBlank()) {
                CopyableMusicValue("Committed result", beef, prominent = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = includeAudit, onCheckedChange = { includeAudit = it })
                Text("Include full JSON / audit when sharing or copying", modifier = Modifier.padding(top = 12.dp))
            }
            Button(
                onClick = { MusicV105.shareText(androidContext, exportText) },
                enabled = exportText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share") }
            OutlinedButton(
                onClick = {
                    clipboard.setText(AnnotatedString(exportText))
                    Toast.makeText(androidContext, "Copied committed result", Toast.LENGTH_SHORT).show()
                },
                enabled = exportText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Copy") }
            OutlinedButton(onClick = { showTechnical = !showTechnical }, modifier = Modifier.fillMaxWidth()) {
                Text(if (showTechnical) "Hide technical details" else "Technical details")
            }
            if (showTechnical) {
                method.fields.outputs.forEach { field ->
                    val value = values[field].orEmpty()
                    if (value.isNotBlank()) CopyableMusicValue(field, value)
                }
            }
            Button(onClick = { onDone(execution) }, modifier = Modifier.fillMaxWidth()) { Text("Done") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit / new run") }
        }
    }
}
