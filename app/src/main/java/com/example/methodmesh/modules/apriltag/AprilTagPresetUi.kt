package com.example.methodmesh.modules.apriltag

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.settings.MethodSetting
import org.json.JSONObject

/**
 * Preset UI deliberately owned by the AprilTag capability package.
 *
 * The capability owns the affordance and the operator workflow; the shared
 * protocol library remains the generic storage/execution mechanism. No host
 * or HomeScreen behaviour is required for immersive AprilTag screens.
 */
@Composable
internal fun AprilTagSavePresetDialog(
    methodId: String,
    methodTitle: String,
    methodDescription: String,
    schema: List<MethodSetting>,
    currentSettings: Map<String, String>,
    onDismiss: () -> Unit,
    onSaved: (String) -> Unit
) {
    val appContext = LocalContext.current
    var name by rememberSaveable(methodId) { mutableStateOf("$methodTitle preset") }
    var payloadMode by rememberSaveable(methodId) { mutableStateOf(ProtocolPayloadMode.CORE) }
    var resultAction by rememberSaveable(methodId) { mutableStateOf(PresetResultAction.HOME) }
    var confirmName by rememberSaveable(methodId) { mutableStateOf(false) }

    val runtimeFromInput = remember(methodId, currentSettings["methodmesh_runtime_fields"]) {
        currentSettings["methodmesh_runtime_fields"]
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val fixedFlags = remember(methodId, runtimeFromInput, schema) {
        mutableStateMapOf<String, Boolean>().apply {
            schema.forEach { setting ->
                put(setting.id, setting.id !in runtimeFromInput || !setting.runtimeInputAllowed)
            }
        }
    }

    fun selectedSettings(): Map<String, String> {
        val selected = linkedMapOf<String, String>()
        val runtime = mutableListOf<String>()
        schema.forEach { setting ->
            val value = currentSettings[setting.id] ?: aprilTagDefaultString(setting)
            if (fixedFlags[setting.id] == true || !setting.runtimeInputAllowed) {
                if (value.isNotBlank()) selected[setting.id] = value
            } else {
                runtime += setting.id
            }
        }
        if (runtime.isNotEmpty()) selected["methodmesh_runtime_fields"] = runtime.joinToString(",")
        return selected
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(max = 760.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Save current setup as preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Text(
                    "The values shown are the live AprilTag setup. Mark a field as runtime when the preset should ask for it later instead of fixing the current value.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                AprilTagPresetChoice(
                    label = "Return payload",
                    value = payloadMode,
                    choices = listOf(ProtocolPayloadMode.CORE, ProtocolPayloadMode.AUDIT, ProtocolPayloadMode.FULL),
                    onChange = { payloadMode = it }
                )
                AprilTagPresetChoice(
                    label = "After run",
                    value = resultAction,
                    choices = listOf(PresetResultAction.HOME, PresetResultAction.SAVE, PresetResultAction.SHARE),
                    onChange = { resultAction = it }
                )

                Text("Preset fields", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                schema.forEach { setting ->
                    val value = currentSettings[setting.id] ?: aprilTagDefaultString(setting)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = fixedFlags[setting.id] == true,
                                onCheckedChange = { fixedFlags[setting.id] = it },
                                enabled = setting.runtimeInputAllowed
                            )
                            Column(Modifier.weight(1f)) {
                                Text(setting.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    if (!setting.runtimeInputAllowed) {
                                        "Fixed configuration · ${value.ifBlank { "(empty)" }}"
                                    } else if (fixedFlags[setting.id] == true) {
                                        "Fixed · ${value.ifBlank { "(empty)" }}"
                                    } else {
                                        "Ask at runtime"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                val preview = remember(currentSettings.toMap(), fixedFlags.toMap()) {
                    aprilTagSettingsJson(selectedSettings())
                }
                Text("Saved settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Text(preview, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)

                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { confirmName = true }, modifier = Modifier.weight(1f)) { Text("Save…") }
                }
            }
        }
    }

    if (confirmName) {
        AlertDialog(
            onDismissRequest = { confirmName = false },
            title = { Text("Name preset") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Preset name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val saved = ProtocolLibraryRepository.savePreset(
                            appContext,
                            CapabilityPreset(
                                name = name.trim(),
                                methodId = methodId,
                                settingsJson = aprilTagSettingsJson(selectedSettings()),
                                payloadMode = ProtocolPayloadMode.normalize(payloadMode),
                                resultAction = PresetResultAction.normalize(resultAction),
                                description = methodDescription
                            )
                        )
                        confirmName = false
                        onSaved(saved.name)
                    }
                ) { Text("Save preset") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmName = false }) { Text("Back") }
            }
        )
    }
}

@Composable
private fun AprilTagPresetChoice(
    label: String,
    value: String,
    choices: List<String>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label · ${value.lowercase().replace('_', ' ')}")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.lowercase().replace('_', ' ')) },
                    onClick = {
                        onChange(choice)
                        expanded = false
                    }
                )
            }
        }
    }
}

internal fun aprilTagDefaultString(setting: MethodSetting): String = when (setting) {
    is MethodSetting.BooleanSetting -> setting.defaultValue.toString()
    is MethodSetting.IntSetting -> setting.defaultValue.toString()
    is MethodSetting.FloatSetting -> setting.defaultValue.toString()
    is MethodSetting.TextSetting -> setting.defaultValue
    is MethodSetting.ChoiceSetting -> setting.defaultValue
    is MethodSetting.MultiChoiceSetting -> setting.defaultValue
}

private fun aprilTagSettingsJson(settings: Map<String, String>): String = JSONObject().apply {
    settings.toSortedMap().forEach { (key, value) -> put(key, value) }
}.toString()
