package com.example.methodmesh.modules.weather

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.protocols.PresetLaunchMode
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import java.io.ByteArrayInputStream
import org.json.JSONObject


/**
 * Weather preset fields are not all meaningful run-time questions.
 *
 * Run-time inputs describe the context of this invocation (where/when).
 * Analysis/display policy stays configuration: threshold, horizon, cache-only,
 * radar zoom, source policy, etc.
 */
internal fun weatherRuntimeEligibleSettingIds(methodId: String): Set<String> = when (methodId) {
    As100WeatherDashboardMethod.id ->
        setOf("latitude", "longitude")

    As100WeatherRadarMethod.id ->
        setOf("latitude", "longitude", "frame_time_iso")

    As100WeatherSnapshotMethod.id ->
        setOf("latitude", "longitude", "target_time_iso")

    else ->
        setOf("latitude", "longitude", "target_time_iso")
}

internal fun weatherRuntimeFields(
    context: CapabilityScreenContext,
    methodId: String
): Set<String> =
    context.runtimeInputFields.intersect(weatherRuntimeEligibleSettingIds(methodId))

internal fun weatherSettingShouldBeShown(
    context: CapabilityScreenContext,
    methodId: String,
    settingId: String
): Boolean {
    if (context.isNativePresetRun &&
        settingId !in weatherRuntimeEligibleSettingIds(methodId)
    ) {
        return false
    }
    return context.settingShouldBeShown(settingId)
}

/**
 * Preset authoring for Weather's immersive capability surfaces.
 *
 * The standard MethodMesh host owns the same workflow for Standard presentation,
 * but immersive capability screens deliberately own their entire surface. Weather
 * therefore projects the shared CapabilityPreset/ProtocolLibraryRepository contract
 * here rather than creating a private preset format.
 */
@Composable
internal fun WeatherPresetAuthoring(
    context: CapabilityScreenContext,
    methodId: String,
    methodName: String,
    currentSettings: Map<String, String>,
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
        WeatherPresetDialog(
            methodId = methodId,
            methodName = methodName,
            settingSchema = WeatherModule.capabilitySettings()[methodId].orEmpty(),
            initialSettings = currentSettings,
            existingRuntimeFields = context.runtimeInputFields,
            onDismiss = { open = false },
            onSave = { name, description, payloadMode, resultAction, launchMode, selected, saveToLog, logName ->
                runCatching {
                    val settings = selected.toMutableMap()
                    if (saveToLog) {
                        attachPersistentLog(appContext, name, logName, settings)
                    }
                    ProtocolLibraryRepository.savePreset(
                        appContext,
                        CapabilityPreset(
                            name = name,
                            methodId = methodId,
                            settingsJson = JSONObject(settings).toString(),
                            payloadMode = payloadMode,
                            resultAction = resultAction,
                            launchMode = launchMode,
                            description = description.ifBlank { "Weather preset for $methodName" }
                        )
                    )
                }.onSuccess { saved ->
                    status = "Saved preset: ${saved.name} (${ProtocolLibraryRepository.versionLabel(saved.versionIso)})."
                    open = false
                }.onFailure { error ->
                    status = "Preset save failed: ${error.message ?: "storage error"}"
                }
            }
        )
    }
}

@Composable
private fun WeatherPresetDialog(
    methodId: String,
    methodName: String,
    settingSchema: List<MethodSetting>,
    initialSettings: Map<String, String>,
    existingRuntimeFields: Set<String>,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, Map<String, String>, Boolean, String) -> Unit
) {
    var name by rememberSaveable(methodId) { mutableStateOf("$methodName preset") }
    var description by rememberSaveable("$methodId:presetDescription") { mutableStateOf("") }
    var payloadMode by rememberSaveable(methodId) { mutableStateOf(ProtocolPayloadMode.CORE) }
    var resultAction by rememberSaveable(methodId) { mutableStateOf(PresetResultAction.HOME) }
    var launchMode by rememberSaveable("$methodId:launchMode") { mutableStateOf(PresetLaunchMode.AUTO) }
    var saveToLog by rememberSaveable(methodId) { mutableStateOf(false) }
    var logName by rememberSaveable(methodId) { mutableStateOf("") }

    val values = remember(methodId, initialSettings) {
        mutableStateMapOf<String, String>().apply {
            settingSchema.forEach { setting ->
                put(setting.id, initialSettings[setting.id] ?: weatherSettingDefault(setting))
            }
        }
    }
    val runtimeEligible = remember(methodId) {
        weatherRuntimeEligibleSettingIds(methodId)
    }
    val fixed = remember(methodId, initialSettings, existingRuntimeFields, runtimeEligible) {
        mutableStateMapOf<String, Boolean>().apply {
            settingSchema.forEach { setting ->
                put(
                    setting.id,
                    setting.id !in existingRuntimeFields || setting.id !in runtimeEligible
                )
            }
        }
    }

    fun selectedSettings(): Map<String, String> {
        val selected = linkedMapOf<String, String>()
        val runtime = mutableListOf<String>()
        settingSchema.forEach { setting ->
            if (fixed[setting.id] == true || setting.id !in runtimeEligible) {
                val value = values[setting.id].orEmpty()
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
                Text("Create preset", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Save this Weather setup as a reusable action.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                Text("Preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description · optional") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 3
                )

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("Configuration", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Location and time may be asked when the preset runs. Analysis and display settings stay fixed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                settingSchema.forEach { setting ->
                    WeatherPresetSettingRow(
                        setting = setting,
                        value = values[setting.id].orEmpty(),
                        fixed = fixed[setting.id] == true,
                        runtimeEligible = setting.id in runtimeEligible,
                        onValueChanged = { values[setting.id] = it },
                        onFixedChanged = { fixed[setting.id] = it }
                    )
                    Spacer(Modifier.height(10.dp))
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))

                Text("Run behaviour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "Auto is recommended. Background is useful for scheduled logging; Show UI keeps the Weather dashboard visible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = PresetLaunchMode.normalize(launchMode) == PresetLaunchMode.AUTO,
                        onClick = { launchMode = PresetLaunchMode.AUTO },
                        label = { Text("Auto") }
                    )
                    FilterChip(
                        selected = PresetLaunchMode.normalize(launchMode) == PresetLaunchMode.INTERACTIVE,
                        onClick = { launchMode = PresetLaunchMode.INTERACTIVE },
                        label = { Text("Show UI") }
                    )
                    FilterChip(
                        selected = PresetLaunchMode.normalize(launchMode) == PresetLaunchMode.BACKGROUND,
                        onClick = { launchMode = PresetLaunchMode.BACKGROUND },
                        label = { Text("Background") }
                    )
                }
                Text(
                    when (PresetLaunchMode.normalize(launchMode)) {
                        PresetLaunchMode.INTERACTIVE -> "Always open the Weather interface."
                        PresetLaunchMode.BACKGROUND -> "Run from saved/runtime inputs without opening the Weather interface."
                        else -> "Use Weather's normal presentation for the caller."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(16.dp))

                Text("After completion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = PresetResultAction.normalize(resultAction) == PresetResultAction.HOME,
                        onClick = { resultAction = PresetResultAction.HOME },
                        label = { Text("Return") }
                    )
                    FilterChip(
                        selected = PresetResultAction.normalize(resultAction) == PresetResultAction.SHARE,
                        onClick = { resultAction = PresetResultAction.SHARE },
                        label = { Text("Share") }
                    )
                    FilterChip(
                        selected = PresetResultAction.normalize(resultAction) == PresetResultAction.SAVE,
                        onClick = { resultAction = PresetResultAction.SAVE },
                        label = { Text("Save") }
                    )
                }
                Spacer(Modifier.height(16.dp))

                Text("Returned data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "The useful Weather result is always primary. Add audit detail or the complete JSON only when needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ProtocolPayloadMode.normalize(payloadMode) == ProtocolPayloadMode.CORE,
                        onClick = { payloadMode = ProtocolPayloadMode.CORE },
                        label = { Text("Result") }
                    )
                    FilterChip(
                        selected = ProtocolPayloadMode.normalize(payloadMode) == ProtocolPayloadMode.AUDIT,
                        onClick = { payloadMode = ProtocolPayloadMode.AUDIT },
                        label = { Text("+ Audit") }
                    )
                    FilterChip(
                        selected = ProtocolPayloadMode.normalize(payloadMode) == ProtocolPayloadMode.FULL,
                        onClick = { payloadMode = ProtocolPayloadMode.FULL },
                        label = { Text("+ Full JSON") }
                    )
                }
                Text(
                    when (ProtocolPayloadMode.normalize(payloadMode)) {
                        ProtocolPayloadMode.AUDIT -> "Useful Weather values plus provenance/audit fields."
                        ProtocolPayloadMode.FULL -> "Useful Weather values plus the canonical complete JSON representation."
                        else -> "Practical Weather result values only."
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = saveToLog, onCheckedChange = { saveToLog = it })
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("Save each run to a log", fontWeight = FontWeight.SemiBold)
                                Text("Keep results from this preset in a persistent Files log.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (saveToLog) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = logName,
                                onValueChange = { logName = it },
                                label = { Text("Log name") },
                                placeholder = { Text("$methodName log") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(
                        onClick = {
                            onSave(
                                name.trim(),
                                description.trim(),
                                ProtocolPayloadMode.normalize(payloadMode),
                                PresetResultAction.normalize(resultAction),
                                PresetLaunchMode.normalize(launchMode),
                                selectedSettings(),
                                saveToLog,
                                logName.trim()
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
private fun WeatherPresetSettingRow(
    setting: MethodSetting,
    value: String,
    fixed: Boolean,
    runtimeEligible: Boolean,
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
                if (runtimeEligible) {
                    OutlinedButton(onClick = { onFixedChanged(!fixed) }) {
                        Text(if (fixed) "Fixed" else "Ask when run")
                    }
                } else {
                    Text(
                        "Fixed configuration",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            OutlinedButton(
                                onClick = { onValueChanged(choice) },
                                enabled = fixed
                            ) {
                                Text(if (value == choice) "✓ ${choice.replace('_', ' ')}" else choice.replace('_', ' '))
                            }
                        }
                    }
                }
                is MethodSetting.MultiChoiceSetting -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        label = { Text("Values (${setting.delimiter})") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                else -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherPresetChoiceRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        options.forEach { (value, label) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = selected == value, onClick = { onSelected(value) })
                Text(label)
            }
        }
    }
}

private fun attachPersistentLog(
    context: Context,
    presetName: String,
    requestedLogName: String,
    settings: MutableMap<String, String>
) {
    val logName = requestedLogName.trim().ifBlank { "$presetName log" }
    val service = AndroidArtifacts.service(context)
    val logRef = service.createPersistent(
        name = "$logName/data.jsonl",
        mime = "application/jsonl",
        input = ByteArrayInputStream(byteArrayOf())
    )
    val summaryRef = service.createPersistent(
        name = "$logName.txt",
        mime = "text/plain",
        input = ByteArrayInputStream("$logName\n\n".toByteArray())
    )
    service.setCollection(logRef, logRef.id)
    settings["methodmesh_save_to_log"] = "true"
    settings["methodmesh_log_ref"] = logRef.id
    settings["methodmesh_log_summary_ref"] = summaryRef.id
    settings["methodmesh_log_name"] = logName
}

private fun weatherSettingDefault(setting: MethodSetting): String = when (setting) {
    is MethodSetting.BooleanSetting -> setting.defaultValue.toString()
    is MethodSetting.IntSetting -> setting.defaultValue.toString()
    is MethodSetting.FloatSetting -> setting.defaultValue.toString()
    is MethodSetting.TextSetting -> setting.defaultValue
    is MethodSetting.ChoiceSetting -> setting.defaultValue
    is MethodSetting.MultiChoiceSetting -> setting.defaultValue
}

/** Same-screen post-Commit actions for Weather's immersive capability UI. */
@Composable
internal fun WeatherCommittedActions(
    context: CapabilityScreenContext,
    label: String,
    result: ExecutionResult,
    workingChanged: Boolean,
    onDone: () -> Unit
) {
    val appContext = LocalContext.current
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    var actionStatus by rememberSaveable(result.request.id.value) { mutableStateOf("") }

    val coreFields = remember(result.request.id.value) {
        OutputFormatter.projectFields(
            OutputFormatter.fields(result, includeProvenance = false),
            OutputFormatter.PayloadMode.CORE,
            result.status
        )
    }
    val fullJson = remember(result.request.id.value) {
        OutputFormatter.format(
            result = result,
            returnMode = ReturnMode.Json,
            includeProvenance = true,
            payloadMode = OutputFormatter.PayloadMode.FULL
        )
    }
    val shareText = remember(coreFields) { weatherHumanResultText(coreFields) }
    val presetAction = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher =
        context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"

    fun finishNativePresetHome() {
        if (finishToLauncher) {
            onDone()
            return
        }
        // Match CapabilityScreenScaffold native-preset HOME semantics. Returning only
        // to the transient dispatcher can leave no visible MethodMesh activity/task.
        appContext.startActivity(
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun copyResult() {
        val text = ResultShare.buildShareText(shareText, if (includeFullJson) fullJson else "")
        if (text.isBlank()) return
        appContext.getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("$label result", text))
        actionStatus = "Copied committed result."
    }

    fun shareResult() {
        if (shareText.isBlank() && (!includeFullJson || fullJson.isBlank())) return
        runCatching {
            ResultShare.share(
                context = appContext,
                chooserTitle = "Share $label result",
                text = shareText,
                attachments = emptyList(),
                jsonText = if (includeFullJson) fullJson else ""
            )
        }.onSuccess {
            actionStatus = "Sharing committed result…"
        }.onFailure {
            actionStatus = "Share failed: ${it.message ?: "no sharing app available"}"
        }
    }

    fun saveResult() {
        runCatching {
            OutputExportRepository.saveToDownloads(
                context = appContext,
                label = label,
                text = shareText,
                mediaUris = emptyList(),
                jsonText = if (includeFullJson) fullJson else ""
            )
        }.onSuccess {
            actionStatus = "Saved ${it.summary}"
        }.onFailure {
            actionStatus = "Downloads save failed: ${it.message ?: "storage error"}"
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("COMMITTED", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text("This frozen payload will not change when the working Weather view is refreshed or edited.", style = MaterialTheme.typography.bodySmall)
            if (workingChanged) {
                Text("Working settings/result have changed since Commit.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
                Text("Include full JSON", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = ::copyResult, modifier = Modifier.weight(1f)) { Text("Copy") }
                OutlinedButton(onClick = ::shareResult, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = ::saveResult, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (!context.isNativePresetRun) {
                        onDone()
                    } else {
                        when (presetAction) {
                            PresetResultAction.SAVE -> {
                                saveResult()
                                onDone()
                            }
                            PresetResultAction.SHARE -> {
                                shareResult()
                                finishNativePresetHome()
                            }
                            else -> finishNativePresetHome()
                        }
                    }
                }
            ) {
                Text(
                    if (context.isNativePresetRun && presetAction == PresetResultAction.SAVE) "Save and finish"
                    else if (context.isNativePresetRun && presetAction == PresetResultAction.SHARE) "Share and finish"
                    else "Done"
                )
            }
            if (actionStatus.isNotBlank()) {
                Text(actionStatus, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun weatherHumanResultText(fields: Map<String, Any?>): String =
    fields.entries
        .filter { (key, value) ->
            value?.toString().orEmpty().isNotBlank() &&
                !key.startsWith("methodmesh_") &&
                !key.endsWith("_audit_json") &&
                !key.endsWith("_json")
        }
        .joinToString("\n") { (key, value) ->
            "${key.substringAfter("weather_").replace('_', ' ')}: $value"
        }
