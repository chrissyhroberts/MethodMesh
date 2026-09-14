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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.PresetLaunchMode
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.settings.MethodSetting
import org.json.JSONObject

/** AprilTag owns its immersive preset affordance but uses the shared preset contract. */
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
    var description by rememberSaveable("$methodId:description") { mutableStateOf(methodDescription) }
    var payloadMode by rememberSaveable(methodId) { mutableStateOf(ProtocolPayloadMode.CORE) }
    var resultAction by rememberSaveable(methodId) { mutableStateOf(PresetResultAction.HOME) }
    var launchMode by rememberSaveable("$methodId:launchMode") { mutableStateOf(PresetLaunchMode.AUTO) }
    var showJson by rememberSaveable("$methodId:json") { mutableStateOf(false) }

    val runtimeFromInput = remember(methodId, currentSettings["methodmesh_runtime_fields"]) {
        currentSettings["methodmesh_runtime_fields"]
            .orEmpty().split(',').map(String::trim).filter(String::isNotBlank).toSet()
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

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(14.dp).heightIn(max = 780.dp),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier.padding(18.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Create preset", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Save the current AprilTag setup as a reusable capability action.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Description · optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )
                Text(methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Only true invocation inputs can be asked when the preset runs. Detector policy and calibration stay fixed configuration.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                schema.forEach { setting ->
                    val fixed = fixedFlags[setting.id] == true
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text(setting.label, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (!setting.runtimeInputAllowed) "Fixed configuration"
                                        else if (fixed) "Fixed in preset" else "Ask when run",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (setting.runtimeInputAllowed) {
                                    OutlinedButton(onClick = { fixedFlags[setting.id] = !fixed }) {
                                        Text(if (fixed) "Fixed" else "Ask when run")
                                    }
                                }
                            }
                            if (fixed) {
                                Text(
                                    currentSettings[setting.id] ?: aprilTagDefaultString(setting),
                                    modifier = Modifier.padding(top = 6.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text("Run behaviour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceChips(
                    options = listOf(
                        PresetLaunchMode.AUTO to "Auto",
                        PresetLaunchMode.INTERACTIVE to "Show UI",
                        PresetLaunchMode.BACKGROUND to "Background"
                    ),
                    selected = launchMode,
                    onSelected = { launchMode = it }
                )
                Text(
                    when (PresetLaunchMode.normalize(launchMode)) {
                        PresetLaunchMode.INTERACTIVE -> "Always open the AprilTag interface."
                        PresetLaunchMode.BACKGROUND -> "Run only when saved/runtime inputs are sufficient for unattended execution."
                        else -> "Use AprilTag's normal presentation for this invocation."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("After completion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceChips(
                    options = listOf(
                        PresetResultAction.HOME to "Return",
                        PresetResultAction.SAVE to "Save",
                        PresetResultAction.SHARE to "Share"
                    ),
                    selected = resultAction,
                    onSelected = { resultAction = it }
                )

                Text("Returned data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                ChoiceChips(
                    options = listOf(
                        ProtocolPayloadMode.CORE to "Result",
                        ProtocolPayloadMode.AUDIT to "+ Audit",
                        ProtocolPayloadMode.FULL to "+ Full JSON"
                    ),
                    selected = payloadMode,
                    onSelected = { payloadMode = it }
                )

                TextButton(onClick = { showJson = !showJson }) {
                    Text(if (showJson) "Hide configuration JSON" else "Advanced · View configuration JSON")
                }
                if (showJson) {
                    Text(
                        aprilTagSettingsJson(selectedSettings()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
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
                                    launchMode = PresetLaunchMode.normalize(launchMode),
                                    description = description.trim()
                                )
                            )
                            onSaved(saved.name)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save preset") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChips(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelected(value) },
                label = { Text(label) },
                modifier = Modifier.weight(1f)
            )
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
