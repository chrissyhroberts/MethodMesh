package com.example.methodmesh.modules.qrcode

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.json.JSONObject

/**
 * Capability-owned preset authoring for the Barcode module.
 *
 * Presets remain projections of the canonical barcode.scan / barcode.generate /
 * barcode.clone contracts. No private preset implementation is introduced.
 */
@Composable
internal fun BarcodePresetAuthoring(
    context: CapabilityScreenContext,
    methodId: String,
    methodName: String,
    currentSettings: Map<String, String>,
    defaultRuntimeFields: Set<String> = emptySet(),
    modifier: Modifier = Modifier
) {
    if (context.submitsImmediately || context.isNativePresetRun) return

    val appContext = LocalContext.current
    var open by rememberSaveable(methodId) { mutableStateOf(false) }
    var status by rememberSaveable(methodId) { mutableStateOf("") }

    Column(modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save current setup as preset")
        }
        if (status.isNotBlank()) {
            Text(
                status,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }

    if (open) {
        BarcodePresetDialog(
            methodId = methodId,
            methodName = methodName,
            settingSchema = QrCodeModule.capabilitySettings()[methodId].orEmpty(),
            initialSettings = currentSettings,
            existingRuntimeFields = context.runtimeInputFields,
            defaultRuntimeFields = defaultRuntimeFields,
            onDismiss = { open = false },
            onSave = { name, payloadMode, resultAction, selected ->
                runCatching {
                    ProtocolLibraryRepository.savePreset(
                        appContext,
                        CapabilityPreset(
                            name = name,
                            methodId = methodId,
                            settingsJson = JSONObject(selected).toString(),
                            payloadMode = payloadMode,
                            resultAction = resultAction,
                            description = "Barcode preset for $methodName"
                        )
                    )
                }.onSuccess { saved ->
                    status = "Saved preset: ${saved.name}."
                    open = false
                }.onFailure { error ->
                    status = "Preset save failed: ${error.message ?: "storage error"}"
                }
            }
        )
    }
}

@Composable
private fun BarcodePresetDialog(
    methodId: String,
    methodName: String,
    settingSchema: List<MethodSetting>,
    initialSettings: Map<String, String>,
    existingRuntimeFields: Set<String>,
    defaultRuntimeFields: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Map<String, String>) -> Unit
) {
    var name by rememberSaveable(methodId) { mutableStateOf("$methodName preset") }
    var payloadMode by rememberSaveable(methodId) { mutableStateOf(ProtocolPayloadMode.CORE) }
    var resultAction by rememberSaveable(methodId) { mutableStateOf(PresetResultAction.HOME) }

    val values = remember(methodId, initialSettings) {
        mutableStateMapOf<String, String>().apply {
            settingSchema.forEach { setting ->
                put(setting.id, initialSettings[setting.id] ?: barcodeSettingDefault(setting))
            }
        }
    }
    val fixed = remember(methodId, initialSettings, existingRuntimeFields, defaultRuntimeFields) {
        mutableStateMapOf<String, Boolean>().apply {
            settingSchema.forEach { setting ->
                val runtime = when {
                    setting.id in existingRuntimeFields -> true
                    existingRuntimeFields.isNotEmpty() -> false
                    setting.id in defaultRuntimeFields -> true
                    else -> false
                }
                put(setting.id, !runtime)
            }
        }
    }

    fun selectedSettings(): Map<String, String> {
        val selected = linkedMapOf<String, String>()
        val runtime = mutableListOf<String>()
        settingSchema.forEach { setting ->
            if (fixed[setting.id] == true) {
                val value = values[setting.id].orEmpty()
                if (value.isNotBlank() || setting is MethodSetting.BooleanSetting) {
                    selected[setting.id] = value
                }
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
            modifier = Modifier.fillMaxWidth(0.94f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text("Save capability preset", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Choose what stays fixed and what MethodMesh asks for each time this preset runs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Run behaviour · Show UI", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Barcode presets open this same scanner / generator / clone instrument. Camera-driven runs are interactive rather than background tasks.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))

                settingSchema.forEach { setting ->
                    BarcodePresetSettingRow(
                        setting = setting,
                        value = values[setting.id].orEmpty(),
                        fixed = fixed[setting.id] == true,
                        onValueChanged = { values[setting.id] = it },
                        onFixedChanged = { fixed[setting.id] = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                Text("Returned data", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                BarcodePresetChoiceRow(
                    options = listOf(
                        ProtocolPayloadMode.CORE to "Result",
                        ProtocolPayloadMode.AUDIT to "+ Audit",
                        ProtocolPayloadMode.FULL to "+ Full JSON"
                    ),
                    selected = payloadMode,
                    onSelected = { payloadMode = it }
                )
                Spacer(Modifier.height(12.dp))

                Text("After result", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                BarcodePresetChoiceRow(
                    options = listOf(
                        PresetResultAction.HOME to "Return",
                        PresetResultAction.SHARE to "Share",
                        PresetResultAction.SAVE to "Save"
                    ),
                    selected = resultAction,
                    onSelected = { resultAction = it }
                )
                Spacer(Modifier.height(14.dp))

                Text("Preset name", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                ProtocolPayloadMode.normalize(payloadMode),
                                PresetResultAction.normalize(resultAction),
                                selectedSettings()
                            )
                        },
                        enabled = name.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Save preset") }
                }
            }
        }
    }
}

@Composable
private fun BarcodePresetSettingRow(
    setting: MethodSetting,
    value: String,
    fixed: Boolean,
    onValueChanged: (String) -> Unit,
    onFixedChanged: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(setting.label, fontWeight = FontWeight.SemiBold)
                    setting.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = { onFixedChanged(!fixed) }) {
                    Text(if (fixed) "Fixed" else "Ask when run")
                }
            }
            Spacer(Modifier.height(8.dp))
            when (setting) {
                is MethodSetting.BooleanSetting -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = value.equals("true", ignoreCase = true),
                            onCheckedChange = { onValueChanged(it.toString()) },
                            enabled = fixed
                        )
                        Text(if (value.equals("true", ignoreCase = true)) "On" else "Off", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                is MethodSetting.ChoiceSetting -> {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        setting.choices.forEach { choice ->
                            OutlinedButton(onClick = { onValueChanged(choice) }, enabled = fixed) {
                                Text(if (value == choice) "✓ ${choice.replace('_', ' ')}" else choice.replace('_', ' '))
                            }
                        }
                    }
                }
                is MethodSetting.MultiChoiceSetting -> {
                    val selected = value.split(setting.delimiter).map { it.trim() }.filter { it.isNotBlank() }.toSet()
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        setting.choices.forEach { choice ->
                            val checked = choice in selected
                            OutlinedButton(
                                onClick = {
                                    val next = selected.toMutableSet().apply {
                                        if (checked) remove(choice) else add(choice)
                                    }
                                    onValueChanged(setting.choices.filter { it in next }.joinToString(setting.delimiter))
                                },
                                enabled = fixed
                            ) {
                                Text(if (checked) "✓ ${choice.replace('_', ' ')}" else choice.replace('_', ' '))
                            }
                        }
                    }
                }
                else -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 4
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodePresetChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected == value, onClick = { onSelected(value) })
                Text(label)
            }
        }
    }
}

private fun barcodeSettingDefault(setting: MethodSetting): String = when (setting) {
    is MethodSetting.BooleanSetting -> setting.defaultValue.toString()
    is MethodSetting.IntSetting -> setting.defaultValue.toString()
    is MethodSetting.FloatSetting -> setting.defaultValue.toString()
    is MethodSetting.TextSetting -> setting.defaultValue
    is MethodSetting.ChoiceSetting -> setting.defaultValue
    is MethodSetting.MultiChoiceSetting -> setting.defaultValue
}
