package com.example.methodmesh.modules.textdocuments

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class TextDocumentPresetBehavior(val contractValue: String) {
    LIBRARY("library"),
    NEW("new"),
    TEMPLATE("template");

    companion object {
        fun fromContract(value: String?): TextDocumentPresetBehavior = when (value?.trim()?.lowercase()) {
            "new" -> NEW
            "template" -> TEMPLATE
            else -> LIBRARY
        }
    }
}

data class TextDocumentPresetDraft(
    val name: String,
    val behavior: TextDocumentPresetBehavior
)

@Composable
fun SaveTextDocumentPresetDialog(
    defaultName: String,
    defaultBehavior: TextDocumentPresetBehavior,
    allowTemplate: Boolean,
    format: DocumentFormat,
    onDismiss: () -> Unit,
    onSave: (TextDocumentPresetDraft) -> Unit
) {
    var name by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var behaviorName by rememberSaveable(defaultBehavior.contractValue) {
        mutableStateOf(defaultBehavior.contractValue)
    }
    val selected = TextDocumentPresetBehavior.fromContract(behaviorName)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as preset") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Create a MethodMesh shortcut. File saving is separate from presets.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Preset name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                PresetBehaviorRow(
                    selected = selected == TextDocumentPresetBehavior.LIBRARY,
                    title = "Open Text documents",
                    description = "Open the document library and choose what to do.",
                    onClick = { behaviorName = TextDocumentPresetBehavior.LIBRARY.contractValue }
                )
                PresetBehaviorRow(
                    selected = selected == TextDocumentPresetBehavior.NEW,
                    title = "New ${format.label.lowercase()} document",
                    description = "Jump straight into a new blank ${format.label.lowercase()} document.",
                    onClick = { behaviorName = TextDocumentPresetBehavior.NEW.contractValue }
                )
                if (allowTemplate) {
                    PresetBehaviorRow(
                        selected = selected == TextDocumentPresetBehavior.TEMPLATE,
                        title = "Use current text as a template",
                        description = "Open a new editable copy prefilled with the current text.",
                        onClick = { behaviorName = TextDocumentPresetBehavior.TEMPLATE.contractValue }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.trim().isNotEmpty(),
                onClick = {
                    onSave(TextDocumentPresetDraft(name.trim(), selected))
                }
            ) { Text("Save preset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun PresetBehaviorRow(
    selected: Boolean,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 4.dp, top = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
