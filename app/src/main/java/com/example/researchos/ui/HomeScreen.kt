package com.example.researchos.ui

import android.content.Intent
import android.content.ClipData
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.researchos.calibration.CalibrationScreen
import com.example.researchos.core.scheduling.ResearchSchedule
import com.example.researchos.core.scheduling.SchedulerCenterCard
import com.example.researchos.core.scheduling.SchedulerDispatchActivity
import com.example.researchos.core.scheduling.SchedulerEditorHost
import com.example.researchos.core.scheduling.SchedulerRepository
import com.example.researchos.core.scheduling.SchedulerExportCapabilityScreen
import com.example.researchos.core.scheduling.SchedulerTransferCapabilityScreen
import com.example.researchos.core.protocols.CapabilityPreset
import com.example.researchos.core.protocols.ProtocolDefinition
import com.example.researchos.core.protocols.ProtocolLibraryRepository
import com.example.researchos.core.protocols.ProtocolStep
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.core.researchos.runtime.CapabilityConfigurationRegistry
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.ResearchOSModuleRegistry
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.OutputExportRepository
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.ParsedLaunchConfig
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.android.AndroidIntentRequestReader
import com.example.researchos.transport.android.IntentRouterActivity
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest
import com.example.researchos.transport.workflow.ui.CapabilityCompletionMode
import com.example.researchos.transport.workflow.ui.CapabilityPresentationMode
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.ui.components.SettingsRenderer
import com.example.researchos.ui.sensors.SensorDashboard
import com.example.researchos.platform.devices.DeviceRegistry
import com.example.researchos.platform.devices.DeviceTransport
import com.example.researchos.platform.devices.RegisteredDevice
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val modules = ResearchOSModuleRegistry.all()
    val methods = As100MethodRegistry.all().filterNot { it.id.startsWith("scheduler.") }.distinctBy { it.id }
    val appContext = LocalContext.current
    var schedules by remember { mutableStateOf(SchedulerRepository.all(appContext)) }
    var editingSchedule by remember { mutableStateOf<ResearchSchedule?>(null) }
    var schedulerEditorOpen by remember { mutableStateOf(false) }
    var schedulerTransferMode by remember { mutableStateOf<String?>(null) }
    var protocolLibraryRevision by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, appContext) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) schedules = SchedulerRepository.all(appContext)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ResearchOS Runtime") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { RuntimeSummaryCard(modules.size, methods.size) }
            item { OutputFolderCard() }
            item { RunProtocolCard(protocolLibraryRevision) }
            item { ProtocolLibraryCard(protocolLibraryRevision) }
            item {
                SchedulerCenterCard(
                    schedules = schedules,
                    onCreate = { editingSchedule = null; schedulerEditorOpen = true },
                    onEdit = { editingSchedule = it; schedulerEditorOpen = true },
                    onChanged = { schedules = SchedulerRepository.all(appContext) },
                    onExportSchedule = { schedule ->
                        val payload = com.example.researchos.core.scheduling.SchedulerBundle.export(appContext, schedule.id)
                        appContext.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("ResearchOS schedule", payload))
                    },
                    onAdvancedExport = { schedulerTransferMode = "export" },
                    onAdvancedImport = { schedulerTransferMode = "import" }
                )
            }
            item { DeviceRegistryCard() }
            if (schedulerEditorOpen) {
                item {
                    SchedulerEditorHost(
                        schedule = editingSchedule,
                        onDone = { schedules = SchedulerRepository.all(appContext); schedulerEditorOpen = false },
                        onCancel = { schedulerEditorOpen = false }
                    )
                }
            }
            schedulerTransferMode?.let { mode ->
                item {
                    val action = ExternalActionRequest(
                        requestedId = if (mode == "export") "scheduler.export" else "scheduler.import",
                        canonicalId = if (mode == "export") "scheduler.export" else "scheduler.import"
                    )
                    val request = ExternalWorkflowRequest(listOf(action), InvocationContext(caller = "dashboard"), emptyList(), ReturnMode.Json, source = "dashboard")
                    val screen = if (mode == "export") SchedulerExportCapabilityScreen else SchedulerTransferCapabilityScreen
                    screen.Render(CapabilityScreenContext(action, request, 1, 1), onBack = { schedulerTransferMode = null }, onConfirmed = { schedulerTransferMode = null; schedules = SchedulerRepository.all(appContext) }, onCancel = { schedulerTransferMode = null })
                }
            }
            item { CapabilityRegistryCard(methods, modules, onPresetSaved = { protocolLibraryRevision += 1 }) }
            item { RuntimeStateCard() }
            item { DeviceServicesCard() }
        }
    }
}

@Composable
private fun OutputFolderCard() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var configured by remember { mutableStateOf(OutputExportRepository.configuredFolder(context)) }
    var folderStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            OutputExportRepository.setConfiguredFolder(context, uri)
            configured = uri.toString()
            folderStatus = "Output folder selected."
        }
    }
    val latestOutput = OutputExportRepository.lastOutputLabel(context)
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "▼ Output storage" else "▶ Output storage",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (latestOutput.isBlank()) "No exports yet" else "Latest ready",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Where results are saved", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (configured.isBlank()) {
                                "Default: ${OutputExportRepository.defaultOutputsPathLabel()}"
                            } else {
                                "Custom folder selected"
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (configured.isNotBlank()) {
                            SelectionContainer {
                                Text(configured, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Latest export", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (latestOutput.isBlank()) {
                            Text("Run a preset, protocol, or capability export and it will appear here.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            SelectionContainer {
                                Text(latestOutput, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    runCatching { OutputExportRepository.openLatestOutput(context) }
                                        .onSuccess { folderStatus = "Opening latest export…" }
                                        .onFailure { folderStatus = "Could not open latest export: ${it.message ?: "no file app available"}" }
                                }
                            ) { Text("Open latest export") }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Button(modifier = Modifier.fillMaxWidth(), onClick = { picker.launch(null) }) { Text("Choose output folder") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        runCatching { OutputExportRepository.openOutputs(context) }
                            .onSuccess { folderStatus = "Opening ${if (configured.isBlank()) OutputExportRepository.defaultOutputsPathLabel() else "selected output folder"}…" }
                            .onFailure { folderStatus = "Could not open folder: ${it.message ?: "no file app available"}" }
                    }
                ) { Text("Open outputs folder") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        OutputExportRepository.setConfiguredFolder(context, null)
                        configured = ""
                        folderStatus = "Using default output folder."
                    }
                ) { Text("Use default folder") }
                folderStatus?.let {
                    Text(it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}


private fun launchProtocolRun(
    context: android.content.Context,
    protocol: ProtocolDefinition,
    saveOutput: Boolean,
    testMode: Boolean
) {
    context.startActivity(
        Intent(context, SchedulerDispatchActivity::class.java)
            .setAction(if (testMode) "com.example.researchos.TEST_PROTOCOL" else "com.example.researchos.RUN_PROTOCOL")
            .putExtra("protocol_id", protocol.id)
            .putExtra("transient_protocol_run", true)
            .putExtra("suppress_output", !saveOutput)
            .putExtra("test_chain", testMode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
private fun RunProtocolCard(revision: Int) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val protocols by remember(revision) { mutableStateOf(ProtocolLibraryRepository.protocols(context)) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(3.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (expanded) "▼ Run protocol" else "▶ Run protocol",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${protocols.size} available", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                "Run a saved protocol as a real capture. Outputs are saved as one protocol JSON plus attachments in one folder.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))
                if (protocols.isEmpty()) {
                    Text("No saved protocols yet. Build one in the Protocol library below.", style = MaterialTheme.typography.bodySmall)
                } else {
                    protocols.forEach { protocol ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(protocol.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text("${protocol.steps.size} step${if (protocol.steps.size == 1) "" else "s"}", style = MaterialTheme.typography.labelMedium)
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    onClick = { launchProtocolRun(context, protocol, saveOutput = true, testMode = false) }
                                ) { Text("Run for real and save output") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolLibraryCard(revision: Int) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var presets by remember(revision) { mutableStateOf(ProtocolLibraryRepository.presets(context)) }
    var protocols by remember(revision) { mutableStateOf(ProtocolLibraryRepository.protocols(context)) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var importPayload by rememberSaveable { mutableStateOf("") }
    var protocolName by rememberSaveable { mutableStateOf("") }
    var protocolPresetIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    var addPresetDialogOpen by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        presets = ProtocolLibraryRepository.presets(context)
        protocols = ProtocolLibraryRepository.protocols(context)
    }

    fun runPreset(preset: CapabilityPreset) {
        context.startActivity(Intent(context, SchedulerDispatchActivity::class.java).apply {
            action = "com.example.researchos.RUN_PRESET"
            putExtra("preset_id", preset.id)
            putExtra("transient_preset_run", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Text(
                    if (expanded) "▼ Protocol library" else "▶ Protocol library",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${presets.size} presets · ${protocols.size} protocols", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                "Save reusable capability configurations, chain them into protocols, and transport the library between devices.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))

                Text("Saved presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (presets.isEmpty()) {
                    Text("No presets yet. Expand a capability card below, configure it, then press Save as preset.", style = MaterialTheme.typography.bodySmall)
                } else {
                    presets.forEach { preset ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(preset.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(preset.methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    Button(onClick = { runPreset(preset) }, modifier = Modifier.weight(1f)) { Text("Run") }
                                    Spacer(Modifier.width(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            ProtocolLibraryRepository.removePreset(context, preset.id)
                                            protocolPresetIds = protocolPresetIds.filterNot { it == preset.id }
                                            refresh()
                                            status = "Removed preset: ${preset.name}"
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Remove") }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Create protocol", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Build a chain from saved presets. Press Add step, pick a preset, repeat, then save.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(protocolName, { protocolName = it }, label = { Text("Protocol name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                protocolPresetIds.forEachIndexed { index, presetId ->
                    val preset = presets.firstOrNull { it.id == presetId }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${index + 1}. ${preset?.name ?: presetId}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { protocolPresetIds = protocolPresetIds.toMutableList().also { it.removeAt(index) } }) {
                                Text("Remove")
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    OutlinedButton(onClick = { addPresetDialogOpen = true }, modifier = Modifier.weight(1f), enabled = presets.isNotEmpty()) {
                        Text("Add step")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val selected = protocolPresetIds.mapNotNull { id -> presets.firstOrNull { it.id == id } }
                            if (protocolName.isBlank() || selected.isEmpty()) {
                                status = "Protocol name and at least one preset are required."
                            } else {
                                ProtocolLibraryRepository.saveProtocol(
                                    context,
                                    ProtocolDefinition(
                                        name = protocolName.trim(),
                                        steps = selected.mapIndexed { index, preset ->
                                            ProtocolStep(name = preset.name, presetId = preset.id, order = index)
                                        }
                                    )
                                )
                                protocolName = ""
                                protocolPresetIds = emptyList()
                                refresh()
                                status = "Protocol saved."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = protocolName.isNotBlank() && protocolPresetIds.isNotEmpty()
                    ) { Text("Save protocol") }
                }
                if (addPresetDialogOpen) {
                    Dialog(onDismissRequest = { addPresetDialogOpen = false }) {
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Add protocol step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                presets.forEach { preset ->
                                    OutlinedButton(
                                        onClick = {
                                            protocolPresetIds = protocolPresetIds + preset.id
                                            addPresetDialogOpen = false
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                    ) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(preset.name)
                                            Text(preset.methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Saved protocols", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (protocols.isEmpty()) {
                    Text("No protocols yet.", style = MaterialTheme.typography.bodySmall)
                } else {
                    protocols.forEach { protocol ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(protocol.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                protocol.steps.sortedBy { it.order }.forEachIndexed { index, step ->
                                    val preset = presets.firstOrNull { it.id == step.presetId }
                                    Text("${index + 1}. ${preset?.name ?: step.name}", style = MaterialTheme.typography.bodySmall)
                                }
                                Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    Button(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = false, testMode = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Test flow only — no output") }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = true, testMode = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Test and save output") }
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = true, testMode = false) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Run for real") }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            ProtocolLibraryRepository.removeProtocol(context, protocol.id)
                                            refresh()
                                            status = "Removed protocol: ${protocol.name}"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Remove protocol") }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text("Transfer library", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth()) {
                    Button(onClick = {
                        val payload = ProtocolLibraryRepository.export(context)
                        context.getSystemService(android.content.ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("ResearchOS protocol library", payload))
                        status = "Protocol library copied to clipboard."
                    }) { Text("Copy export") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { importPayload = "" }) { Text("Clear import") }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    importPayload,
                    { importPayload = it },
                    label = { Text("Paste protocol library bundle") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                Button(
                    onClick = {
                        runCatching { ProtocolLibraryRepository.import(context, importPayload.trim()) }
                            .onSuccess {
                                refresh()
                                status = "Imported ${it.presetCount} preset(s) and ${it.protocolCount} protocol(s)."
                            }
                            .onFailure { status = "Import failed: ${it.message ?: "invalid bundle"}" }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = importPayload.isNotBlank()
                ) { Text("Import without replacing existing") }
                status?.let { Text(it, modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun RuntimeSummaryCard(moduleCount: Int, methodCount: Int) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ResearchOS runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "The dashboard now follows the canonical capability registry. Modules register methods, focused screens, RIL bindings and examples once.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Modules: $moduleCount • canonical methods: $methodCount",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}


@Composable
private fun CapabilityRegistryCard(
    methods: List<As100Method>,
    modules: List<ResearchOSModule>,
    onPresetSaved: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val screenMap = ResearchOSModuleRegistry.capabilityScreens().associateBy { it.capabilityId }
    val moduleByMethod = modules.flatMap { module -> module.as100Methods().map { it.id to module } }.toMap()
    val filteredMethods = methods.filter { method ->
        val moduleName = moduleByMethod[method.id]?.displayName.orEmpty()
        query.isBlank() || listOf(method.id, method.descriptor.name, method.descriptor.description.orEmpty(), moduleName)
            .any { it.contains(query.trim(), ignoreCase = true) }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = if (expanded) "▼ Capabilities" else "▶ Capabilities",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(methods.size.toString(), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "This is the single source of truth for dashboard execution: one row per canonical AS method, including DCE/choice experiment capabilities. Focused screens render in-place here rather than in a separate debug runner.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Find a capability") },
                    placeholder = { Text("Search by name, method ID, or module") }
                )
                Text(
                    "Showing ${filteredMethods.size} of ${methods.size}",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                filteredMethods.sortedBy { it.id }
                    .groupBy { moduleByMethod[it.id]?.displayName ?: "Other methods" }
                    .toSortedMap()
                    .forEach { (moduleName, moduleMethods) ->
                        // Keep the dashboard compact on entry; expand a module when its
                        // capabilities are needed rather than opening every subtree.
                        var moduleExpanded by rememberSaveable(moduleName) { mutableStateOf(false) }
                        Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { moduleExpanded = !moduleExpanded },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (moduleExpanded) "▼ $moduleName" else "▶ $moduleName",
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(moduleMethods.size.toString(), style = MaterialTheme.typography.labelMedium)
                            }
                            if (moduleExpanded) moduleMethods.forEach { method ->
                                CapabilityCard(
                                    method = method,
                                    module = moduleByMethod[method.id],
                                    screen = screenMap[method.id],
                                    onPresetSaved = onPresetSaved
                                )
                            }
                        }
                    }
                if (filteredMethods.isEmpty()) {
                    Text("No capabilities match this search.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    method: As100Method,
    module: ResearchOSModule?,
    screen: CapabilityScreenSpec?,
    onPresetSaved: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(method.id) { mutableStateOf(false) }
    var runnerOpen by rememberSaveable(method.id) { mutableStateOf(false) }
    var intentTestOpen by rememberSaveable("${method.id}:intentTest") { mutableStateOf(false) }
    var lastResult by remember(method.id) { mutableStateOf<ExecutionResult?>(null) }
    var presetDialogOpen by rememberSaveable("${method.id}:presetDialog") { mutableStateOf(false) }
    var presetStatus by rememberSaveable("${method.id}:presetStatus") { mutableStateOf<String?>(null) }
    val settingSchema = remember(method.id) { CapabilityConfigurationRegistry.settingsFor(method.id) }
    val settingsState = remember(method.id) { SettingsState(settingSchema) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        elevation = CardDefaults.elevatedCardElevation(1.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = if (expanded) "▼ ${method.descriptor.name}" else "▶ ${method.descriptor.name}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (screen == null) {
                    Text("generic", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(method.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            method.descriptor.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            }
            module?.let { Text("Module: ${it.displayName}", style = MaterialTheme.typography.labelSmall) }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                CapabilityMetadata(method, module)
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text("Preset settings", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        if (settingSchema.isEmpty()) {
                            Text(
                                "This capability has no typed settings registered yet. Saving a preset will still preserve the capability identity.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            SettingsRenderer(settingSchema, settingsState)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Button(onClick = { runnerOpen = !runnerOpen }) {
                        Text(if (runnerOpen) "Hide runner" else "Open runner")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { intentTestOpen = !intentTestOpen }) {
                        Text(if (intentTestOpen) "Hide intent test" else "Test intent launch")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { presetDialogOpen = true }) {
                        Text("Save as preset")
                    }
                    Spacer(Modifier.width(8.dp))
                    if (lastResult != null) {
                        OutlinedButton(onClick = { lastResult = null }) { Text("Clear result") }
                    }
                }
                presetStatus?.let {
                    Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
                if (presetDialogOpen) {
                    SavePresetDialog(
                        defaultName = defaultPresetName(method.id),
                        methodId = method.id,
                        settingsJson = settingsJsonFromMap(settingsState.asMap()),
                        onDismiss = { presetDialogOpen = false },
                        onSave = { name ->
                            ProtocolLibraryRepository.savePreset(
                                context,
                                CapabilityPreset(
                                    name = name,
                                    methodId = method.id,
                                    settingsJson = settingsJsonFromMap(settingsState.asMap()),
                                    description = method.descriptor.description.orEmpty()
                                )
                            )
                            presetDialogOpen = false
                            presetStatus = "Saved preset: $name"
                            onPresetSaved()
                        }
                    )
                }
                lastResult?.let { ResultPreview(it) }
                if (runnerOpen) {
                    Spacer(Modifier.height(12.dp))
                    DashboardCapabilityRunner(
                        method = method,
                        module = module,
                        screen = screen,
                        intentTest = false,
                        settingsJson = settingsJsonFromMap(settingsState.asMap()),
                        onConfirmed = { result ->
                            lastResult = ResearchRuntime.session.record(result)
                            runnerOpen = false
                        },
                        onCancel = { runnerOpen = false }
                    )
                }
                if (intentTestOpen) {
                    Dialog(
                        onDismissRequest = { intentTestOpen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false)
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(18.dp)
                            ) {
                                DashboardCapabilityRunner(
                                    method = method,
                                    module = module,
                                    screen = screen,
                                    intentTest = true,
                                    settingsJson = settingsJsonFromMap(settingsState.asMap()),
                                    onConfirmed = { result ->
                                        lastResult = ResearchRuntime.session.record(result)
                                        intentTestOpen = false
                                    },
                                    onCancel = { intentTestOpen = false }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CapabilityMetadata(method: As100Method, module: ResearchOSModule?) {
    val bindings = module?.rilBindings().orEmpty().filter { it.actionId == method.id }
    val examples = module?.examples().orEmpty().filter { example ->
        bindings.any { binding -> example.ril.contains(binding.phrase) || example.ril.contains(method.id) }
    }
    Text("Outputs: ${method.descriptor.outputs.joinToString().ifBlank { "none declared" }}", style = MaterialTheme.typography.bodySmall)
    Text("Graph outputs: ${method.descriptor.graphOutputs.joinToString().ifBlank { "none declared" }}", style = MaterialTheme.typography.bodySmall)
    if (bindings.isNotEmpty()) {
        Text("RIL: ${bindings.take(5).joinToString { it.phrase }}${if (bindings.size > 5) " …" else ""}", style = MaterialTheme.typography.bodySmall)
    }
    if (examples.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Text("Examples", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        examples.take(3).forEach { ExampleSummary(it) }
    }
}

@Composable
private fun ExampleSummary(example: ModuleExample) {
    Text(example.title, modifier = Modifier.padding(top = 4.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    Text(example.ril, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
    if (example.notes.isNotBlank()) Text(example.notes, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    methodId: String,
    settingsJson: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by rememberSaveable(methodId) { mutableStateOf(defaultName) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(18.dp)) {
                Text("Save capability preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    name,
                    { name = it },
                    label = { Text("Preset name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Text("Settings to save", style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(settingsJson, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { if (name.isNotBlank()) onSave(name.trim()) },
                        modifier = Modifier.weight(1f),
                        enabled = name.isNotBlank()
                    ) { Text("Save") }
                }
            }
        }
    }
}

private fun defaultPresetName(methodId: String): String =
    "capability_${methodId.replace(Regex("[^A-Za-z0-9]+"), "_").trim('_')}_v1"

private fun settingsJsonFromMap(values: Map<String, Any>): String =
    JSONObject().apply { values.toSortedMap().forEach { (key, value) -> put(key, value) } }.toString()

private fun settingsMapFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())

@Composable
private fun DashboardCapabilityRunner(
    method: As100Method,
    module: ResearchOSModule?,
    screen: CapabilityScreenSpec?,
    intentTest: Boolean,
    settingsJson: String,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val request = if (intentTest) {
        testIntentWorkflowRequest(method, module, settingsJson)
    } else {
        val action = ExternalActionRequest(requestedId = method.id, canonicalId = method.id, settings = settingsMapFromJson(settingsJson))
        ExternalWorkflowRequest(
            actions = listOf(action),
            invocationContext = InvocationContext(caller = "dashboard"),
            returns = emptyList(),
            returnMode = ReturnMode.Json,
            settings = settingsMapFromJson(settingsJson),
            source = "dashboard"
        )
    }
    val action = request.actions.firstOrNull() ?: ExternalActionRequest(requestedId = method.id, canonicalId = method.id)
    val context = CapabilityScreenContext(
        action = action,
        request = request,
        stepNumber = 1,
        totalSteps = 1,
        completionMode = if (intentTest) CapabilityCompletionMode.AutomaticReturn else CapabilityCompletionMode.ManualConfirmation,
        presentationMode = if (intentTest) CapabilityPresentationMode.IntentLaunch else CapabilityPresentationMode.Dashboard
    )

    if (screen != null) {
        screen.Render(
            context = context,
            onBack = {},
            onConfirmed = { result -> onConfirmed(result.withInvocationContext(request.invocationContext)) },
            onCancel = onCancel
        )
    } else {
        GenericDashboardRunner(method, context, onConfirmed, onCancel)
    }
}

private fun testIntentWorkflowRequest(method: As100Method, module: ResearchOSModule?, settingsJson: String): ExternalWorkflowRequest {
    val cardSettings = settingsMapFromJson(settingsJson)
    val example = testIntentExampleFor(method, module)
    val request = when {
        example?.ril?.contains("EXECUTE_METHOD", ignoreCase = true) == true ->
            AndroidIntentRequestReader.workflowRequest(Intent(example.ril))
        example?.ril?.isNotBlank() == true ->
            workflowRequestFromParsed(LaunchConfigParser.parse(example.ril))
        else ->
            AndroidIntentRequestReader.workflowRequest(Intent("com.example.researchos.EXECUTE_METHOD(method_id='${method.id}',return_mode='flat')"))
    }

    val resolved = if (request.actions.any { it.canonicalId == method.id || it.requestedId == method.id }) {
        request
    } else {
        AndroidIntentRequestReader.workflowRequest(Intent("com.example.researchos.EXECUTE_METHOD(method_id='${method.id}',return_mode='flat')"))
    }
    return resolved.copy(
        actions = resolved.actions.map { action ->
            if (action.canonicalId == method.id || action.requestedId == method.id) {
                action.copy(settings = action.settings + cardSettings)
            } else {
                action
            }
        },
        settings = resolved.settings + cardSettings,
        source = "intent_test"
    )
}

private fun testIntentExampleFor(method: As100Method, module: ResearchOSModule?): ModuleExample? {
    val bindings = module?.rilBindings().orEmpty().filter { it.actionId == method.id }
    val examples = module?.examples().orEmpty()
    return examples.firstOrNull { example ->
        example.ril.contains(method.id, ignoreCase = true) ||
            bindings.any { binding -> example.ril.contains(binding.phrase, ignoreCase = true) }
    } ?: examples.firstOrNull { it.ril.contains("EXECUTE_METHOD", ignoreCase = true) }
}

private fun workflowRequestFromParsed(parsed: ParsedLaunchConfig): ExternalWorkflowRequest {
    val context = AndroidIntentRequestReader.invocationContextFrom(parsed)
    return ExternalWorkflowRequest.from(parsed.copy(source = "intent_test"), context)
}

@Composable
private fun GenericDashboardRunner(
    method: As100Method,
    context: CapabilityScreenContext,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    var result by remember(method.id) { mutableStateOf<ExecutionResult?>(null) }
    var status by remember(method.id) { mutableStateOf("Ready.") }

    fun runMethod() {
        val execution = method.execute(
            request = method.request(
                action = method.id,
                context = context.request.invocationContext.asMap(method.id) + context.action.settings
            ),
            settingsState = null,
            transport = "dashboard"
        ).withInvocationContext(context.request.invocationContext)
        result = execution
        status = "Execution complete: ${execution.status.name}"
    }

    CapabilityScreenScaffold(
        title = method.descriptor.name,
        capabilityId = method.id,
        context = context,
        canGoBack = false,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
        onBack = {},
        onRetry = { runMethod() },
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Text(status)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { runMethod() }) { Text(if (result == null) "Run action" else "Run again") }
    }
}

@Composable
private fun ResultPreview(result: ExecutionResult) {
    val fields = OutputFormatter.fields(result, includeProvenance = false)
    var expanded by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Text("Last confirmed result: ${result.status.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    val visibleFields = if (expanded) fields.entries else fields.entries.take(10)
    SelectionContainer {
        Column {
            visibleFields.forEach { (key, value) ->
                Text("$key = ${value?.toString().orEmpty()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    if (fields.size > 10) {
        Text(
            text = if (expanded) "▲ Show fewer fields" else "▼ ${fields.size - 10} more fields",
            modifier = Modifier.clickable { expanded = !expanded }.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun RuntimeStateCard() {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val graph = ResearchRuntime.session.graph()
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Text(
                    text = if (expanded) "▼ Knowledge graph state" else "▶ Knowledge graph state",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("${graph.asObservations.size} obs", style = MaterialTheme.typography.titleMedium)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Entities: ${graph.asEntities.size}")
                Text("AS observations: ${graph.asObservations.size}")
                Text("Transformations: ${graph.transformations.size}")
                Spacer(Modifier.height(8.dp))
                graph.asObservations.values.take(10).forEach { observation ->
                    Text("${observation.phenomenon}: ${observation.id.value}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun DeviceServicesCard() {
    var calibrationExpanded by rememberSaveable { mutableStateOf(false) }
    var signalsExpanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Device services", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Signal-source independent services used by capabilities.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { calibrationExpanded = !calibrationExpanded }
            ) {
                Text(if (calibrationExpanded) "▼ Device calibration" else "▶ Device calibration", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }
            if (calibrationExpanded) {
                Spacer(Modifier.height(8.dp))
                CalibrationScreen()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { signalsExpanded = !signalsExpanded }
            ) {
                Text(if (signalsExpanded) "▼ Device signals" else "▶ Device signals", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }
            if (signalsExpanded) {
                Spacer(Modifier.height(8.dp))
                SensorDashboard()
            }
        }
    }
}

@Composable
private fun DeviceRegistryCard() {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var devices by remember { mutableStateOf(DeviceRegistry.all(context)) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(DeviceTransport.BLE.name) }
    var address by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("") }
    var credentialsRef by remember { mutableStateOf("") }

    fun refresh() { devices = DeviceRegistry.all(context) }
    fun openEditor(device: RegisteredDevice?) {
        editingId = device?.id.orEmpty(); name = device?.name.orEmpty(); transport = device?.transport?.name ?: DeviceTransport.BLE.name
        address = device?.address.orEmpty(); profile = device?.profile.orEmpty(); credentialsRef = device?.credentialsRef.orEmpty(); editorOpen = true
    }

    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Text(if (expanded) "▼ Device registry" else "▶ Device registry", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Saved Bluetooth, Wi-Fi, USB, and other device profiles.", style = MaterialTheme.typography.bodySmall)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) Text("No registered devices.", style = MaterialTheme.typography.bodyMedium)
                devices.forEach { device ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(device.name.ifBlank { "Unnamed device" }, style = MaterialTheme.typography.titleSmall)
                            Text("${device.transport} · ${device.address.ifBlank { "address not set" }}", style = MaterialTheme.typography.bodySmall)
                            Text(if (!device.enabled) "Disabled" else if (device.paused) "Paused" else "Enabled", style = MaterialTheme.typography.labelMedium)
                            if (device.lastError.isNotBlank()) Text("Last error: ${device.lastError}", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { DeviceRegistry.setPaused(context, device.id, !device.paused); refresh() }) { Text(if (device.paused) "Resume" else "Pause") }
                                Spacer(Modifier.padding(3.dp))
                                OutlinedButton(onClick = { openEditor(device) }) { Text("Edit") }
                                Spacer(Modifier.padding(3.dp))
                                OutlinedButton(onClick = { DeviceRegistry.remove(context, device.id); refresh() }) { Text("Delete") }
                            }
                        }
                    }
                }
                Button(onClick = { openEditor(null) }, Modifier.fillMaxWidth()) { Text("Add device profile") }
            }
        }
    }

    if (editorOpen) Dialog(onDismissRequest = { editorOpen = false }) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp)) {
                Text(if (editingId.isBlank()) "Add device profile" else "Edit device profile", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Device name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(transport, { transport = it.uppercase() }, label = { Text("Transport (BLE, WIFI, USB, etc.)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Address or identifier") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(profile, { profile = it }, label = { Text("Profile or service mapping") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(credentialsRef, { credentialsRef = it }, label = { Text("Credential reference (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { editorOpen = false }) { Text("Cancel") }
                    Spacer(Modifier.padding(4.dp))
                    Button(onClick = {
                        val parsed = runCatching { DeviceTransport.valueOf(transport.trim().uppercase()) }.getOrDefault(DeviceTransport.WIFI)
                        DeviceRegistry.save(context, RegisteredDevice(id = editingId.ifBlank { java.util.UUID.randomUUID().toString() }, name = name.trim(), transport = parsed, address = address.trim(), profile = profile.trim(), credentialsRef = credentialsRef.trim()))
                        refresh(); editorOpen = false
                    }) { Text("Save") }
                }
            }
        }
    }
}
