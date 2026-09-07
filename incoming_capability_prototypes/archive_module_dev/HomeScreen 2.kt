package com.example.methodmesh.ui

import android.content.Intent
import android.content.ClipData
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.calibration.CalibrationScreen
import com.example.methodmesh.core.scheduling.ResearchSchedule
import com.example.methodmesh.core.scheduling.SchedulerCenterCard
import com.example.methodmesh.core.scheduling.SchedulerDispatchActivity
import com.example.methodmesh.core.scheduling.SchedulerEditorHost
import com.example.methodmesh.core.scheduling.SchedulerRepository
import com.example.methodmesh.core.scheduling.SchedulerExportCapabilityScreen
import com.example.methodmesh.core.scheduling.SchedulerTransferCapabilityScreen
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.ProtocolDefinition
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.ProtocolOutputMode
import com.example.methodmesh.core.protocols.ProtocolPayloadMode
import com.example.methodmesh.core.protocols.ProtocolStep
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.core.methodmesh.runtime.CapabilityConfigurationRegistry
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.ApiDefinitionOrigin
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.onlinedata.ApiExecutionResult
import com.example.methodmesh.core.onlinedata.ApiGetExecutor
import com.example.methodmesh.core.onlinedata.ApiGetRequest
import com.example.methodmesh.core.onlinedata.ApiInputType
import com.example.methodmesh.core.onlinedata.HttpUrlConnectionOnlineHttpClient
import com.example.methodmesh.core.onlinedata.InMemoryApiDefinitionRegistry
import com.example.methodmesh.core.onlinedata.OnlineHttpClient
import com.example.methodmesh.core.onlinedata.OnlineHttpRequest
import com.example.methodmesh.core.onlinedata.OnlineHttpResponse
import com.example.methodmesh.core.onlinedata.OnlineExecutionStatus
import com.example.methodmesh.core.onlinedata.ResultTree
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.modules.mlkittranslate.MlKitLanguageCatalog
import com.example.methodmesh.modules.odkformlauncher.As100OdkFormLauncherMethod
import com.example.methodmesh.platform.externalforms.ExternalFormCatalog
import com.example.methodmesh.platform.externalforms.ExternalProjectRegistry
import com.example.methodmesh.settings.DisplaySettingsScreen
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.modules.sensorread.As100SensorReadMethod
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.android.IntentRouterActivity
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityCompletionMode
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.ui.components.SettingsRenderer
import com.example.methodmesh.ui.components.MethodMeshMark
import com.example.methodmesh.ui.sensors.SensorDashboard
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.example.methodmesh.platform.devices.DeviceRegistry
import com.example.methodmesh.platform.devices.DeviceTransport
import com.example.methodmesh.platform.devices.RegisteredDevice
import org.json.JSONObject
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class ProtocolStepDraft(
    val presetId: String,
    val outputMode: String = ProtocolOutputMode.SAVE
)

private enum class CapabilityUiClass {
    ProtocolPrimitive,
    WorkbenchTool
}

private enum class CapabilityLifecycle(val label: String) {
    Production("Production"),
    Development("Development")
}

private enum class DashboardDestination(val label: String) {
    Dashboard("Dashboard"),
    Outputs("Outputs"),
    RunProtocol("Run protocol"),
    Presets("Preset library"),
    Protocols("Protocol library"),
    Scheduler("Scheduler"),
    Devices("Device registry"),
    Workbench("Workbench"),
    Capabilities("Capabilities"),
    State("Runtime state"),
    Services("Settings")
}

private fun capabilityUiClass(method: As100Method, module: MethodMeshModule?): CapabilityUiClass {
    val id = method.id
    val moduleId = module?.moduleId.orEmpty()
    return when {
        id in setOf(
            "android_app_inspector",
            "bluetooth_device_inspector",
            "sensor_node_provisioner",
            "esp32.board_wipe",
            "esp32.runtime_install",
            "esp32.sensor_profile_install"
        ) -> CapabilityUiClass.WorkbenchTool
        moduleId in setOf(
            "appinspector",
            "bluetoothinspector",
            "sensorfirmwareinstaller",
            "sensorprovisioner"
        ) -> CapabilityUiClass.WorkbenchTool
        else -> CapabilityUiClass.ProtocolPrimitive
    }
}

private fun capabilityLifecycle(method: As100Method): CapabilityLifecycle {
    // Promotion is deliberately explicit. New or unreviewed capabilities stay
    // in Development until their behaviour, ODK contract, docs and examples
    // have been reviewed together.
    val productionCapabilityIds = setOf(
        "admin_fingerprint_confirmation",
        "barcode.scan",
        "calibrated_scale",
        "document.scan",
        "gps_target_navigator",
        "plus_code.capture",
        "conversation.translate",
        "bluetooth_print",
        "mlkit.translate",
        "mlkit.vision.analyze",
        "odk_form_launcher",
        "random.number.generate"
    )
    return if (method.id in productionCapabilityIds) CapabilityLifecycle.Production else CapabilityLifecycle.Development
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val modules = MethodMeshModuleRegistry.all()
    val methods = As100MethodRegistry.all().filterNot { it.id.startsWith("scheduler.") }.distinctBy { it.id }
    val appContext = LocalContext.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedDestination by rememberSaveable { mutableStateOf(DashboardDestination.Dashboard) }
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MethodMeshMark(size = 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("MethodMesh", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Do Stuff", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                DashboardDestination.entries.forEach { destination ->
                    NavigationDrawerItem(
                        label = { Text(destination.label) },
                        selected = selectedDestination == destination,
                        onClick = {
                            scope.launch {
                                selectedDestination = destination
                                drawerState.close()
                            }
                        }
                    )
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        if (selectedDestination == DashboardDestination.Dashboard) {
                            Text("")
                        } else {
                            Text(selectedDestination.label)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        actionIconContentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            MethodMeshMark(size = 34.dp)
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (selectedDestination) {
                    DashboardDestination.Dashboard -> {
                        item { RuntimeSummaryCard(modules.size, methods.size) }
                        item {
                            ActiveSchedulesCard(
                                schedules = schedules,
                                onChanged = { schedules = SchedulerRepository.all(appContext) },
                                onOpenScheduler = { selectedDestination = DashboardDestination.Scheduler }
                            )
                        }
                        item { FindCapabilityCard(methods, modules) }
                        item { RunProtocolCard(protocolLibraryRevision, expandedByDefault = true) }
                        item { FindPresetCard(protocolLibraryRevision) }
                        item { PresetShortcutsCard(protocolLibraryRevision) }
                    }
                    DashboardDestination.Outputs -> item { OutputFolderCard(expandedByDefault = true) }
                    DashboardDestination.RunProtocol -> item { RunProtocolCard(protocolLibraryRevision, expandedByDefault = true) }
                    DashboardDestination.Presets -> item { ProtocolLibraryCard(protocolLibraryRevision, showPresets = true, showProtocols = false, expandedByDefault = true) }
                    DashboardDestination.Protocols -> item { ProtocolLibraryCard(protocolLibraryRevision, showPresets = false, showProtocols = true, expandedByDefault = true) }
                    DashboardDestination.Scheduler -> {
                        item {
                            SchedulerCenterCard(
                                schedules = schedules,
                                onCreate = { editingSchedule = null; schedulerEditorOpen = true },
                                onEdit = { editingSchedule = it; schedulerEditorOpen = true },
                                onChanged = { schedules = SchedulerRepository.all(appContext) },
                                onExportSchedule = { schedule ->
                                    val payload = com.example.methodmesh.core.scheduling.SchedulerBundle.export(appContext, schedule.id)
                                    appContext.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("MethodMesh schedule", payload))
                                },
                                onAdvancedExport = { schedulerTransferMode = "export" },
                                onAdvancedImport = { schedulerTransferMode = "import" }
                            )
                        }
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
                    }
                    DashboardDestination.Devices -> item { DeviceRegistryCard(expandedByDefault = true) }
                    DashboardDestination.Workbench -> item { WorkbenchCard(methods, modules, expandedByDefault = true) }
                    DashboardDestination.Capabilities -> item { CapabilityRegistryCard(methods, modules, expandedByDefault = true, onPresetSaved = { protocolLibraryRevision += 1 }) }
                    DashboardDestination.State -> item { RuntimeStateCard(expandedByDefault = true) }
                    DashboardDestination.Services -> item { DeviceServicesCard(expandedByDefault = true) }
                }
            }
        }
    }
}

@Composable
private fun OutputFolderCard(expandedByDefault: Boolean = false) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
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
            .setAction(if (testMode) "com.example.methodmesh.TEST_PROTOCOL" else "com.example.methodmesh.RUN_PROTOCOL")
            .putExtra("protocol_id", protocol.id)
            .putExtra("transient_protocol_run", true)
            .putExtra("suppress_output", !saveOutput)
            .putExtra("test_chain", testMode)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

private fun runPresetFromDashboard(
    context: android.content.Context,
    preset: CapabilityPreset
) {
    context.startActivity(Intent(context, SchedulerDispatchActivity::class.java).apply {
        action = "com.example.methodmesh.RUN_PRESET"
        putExtra("preset_id", preset.id)
        putExtra("transient_preset_run", true)
        putExtra("suppress_output", true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

@Composable
private fun ActiveSchedulesCard(
    schedules: List<ResearchSchedule>,
    onChanged: () -> Unit,
    onOpenScheduler: () -> Unit
) {
    val context = LocalContext.current
    val scheduleGroups = remember(schedules) {
        schedules
            .sortedWith(compareBy<ResearchSchedule> { it.chainId.ifBlank { it.id } }.thenBy { it.chainOrder })
            .groupBy { it.chainId.ifBlank { it.id } }
            .values
            .map { it.sortedBy { schedule -> schedule.chainOrder } }
            .sortedWith(compareByDescending<List<ResearchSchedule>> { it.firstOrNull()?.enabled == true }.thenBy { it.firstOrNull()?.name.orEmpty() })
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Active schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (schedules.isEmpty()) "No scheduled tasks yet." else "${scheduleGroups.count { it.firstOrNull()?.enabled == true }} running · ${scheduleGroups.size} total",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = onOpenScheduler) {
                    Text(if (schedules.isEmpty()) "Create" else "Manage")
                }
            }
            if (scheduleGroups.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                scheduleGroups.take(5).forEach { group ->
                    val schedule = group.first()
                    val running = schedule.enabled
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = if (running) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (group.size > 1) schedule.name.removeSuffix(" 1") else schedule.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${if (running) "Running" else "Paused"} · ${scheduleTimingLabel(schedule)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    if (group.size > 1) "${group.size}-step chain" else "${schedule.target}: ${schedule.targetValue}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Switch(
                                checked = running,
                                onCheckedChange = {
                                    SchedulerRepository.setChainEnabled(context, schedule, it)
                                    onChanged()
                                }
                            )
                        }
                    }
                }
                if (scheduleGroups.size > 5) {
                    Text(
                        "+ ${scheduleGroups.size - 5} more in Scheduler",
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private fun scheduleTimingLabel(schedule: ResearchSchedule): String =
    schedule.cronExpression.takeIf { it.isNotBlank() }?.let { "cron $it" }
        ?: "${schedule.frequency.name.lowercase().replaceFirstChar { it.titlecase() }} ${"%02d:%02d".format(schedule.hour, schedule.minute)}"

@Composable
private fun RunProtocolCard(revision: Int, expandedByDefault: Boolean = false) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
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
private fun FindCapabilityCard(
    methods: List<As100Method>,
    modules: List<MethodMeshModule>
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCapabilityId by rememberSaveable { mutableStateOf<String?>(null) }
    val screenMap = remember { MethodMeshModuleRegistry.capabilityScreens().associateBy { it.capabilityId } }
    val moduleByMethod = remember(modules) {
        modules.flatMap { module -> module.as100Methods().map { it.id to module } }.toMap()
    }
    val searchableMethods = remember(methods, modules) {
        methods
            .filter { method ->
                val uiClass = capabilityUiClass(method, moduleByMethod[method.id])
                uiClass == CapabilityUiClass.ProtocolPrimitive || uiClass == CapabilityUiClass.WorkbenchTool
            }
            .sortedWith(compareBy<As100Method> { capabilityLifecycle(it) != CapabilityLifecycle.Production }.thenBy { it.descriptor.name })
    }
    val trimmedQuery = query.trim()
    val matches = remember(searchableMethods, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList() else searchableMethods.filter { method ->
            val moduleName = moduleByMethod[method.id]?.displayName.orEmpty()
            listOf(method.descriptor.name, method.id, method.descriptor.description.orEmpty(), moduleName)
                .any { it.contains(trimmedQuery, ignoreCase = true) }
        }.take(8)
    }
    val selectedMethod = selectedCapabilityId?.let { id -> searchableMethods.firstOrNull { it.id == id } }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Find a capability", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                placeholder = { Text("Barcode, translate, GPS, document…") }
            )
            if (trimmedQuery.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                if (matches.isEmpty()) {
                    Text("No matching capabilities.", style = MaterialTheme.typography.bodySmall)
                } else {
                    matches.forEach { method ->
                        val module = moduleByMethod[method.id]
                        val uiClass = capabilityUiClass(method, module)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clickable { selectedCapabilityId = method.id },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        method.descriptor.name,
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        when (uiClass) {
                                            CapabilityUiClass.WorkbenchTool -> "Workbench"
                                            CapabilityUiClass.ProtocolPrimitive -> capabilityLifecycle(method).label
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(method.id, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                module?.displayName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMethod?.let { method ->
        FullScreenCapabilityDialog(onDismiss = { selectedCapabilityId = null }) {
            DashboardCapabilityRunner(
                method = method,
                screen = screenMap[method.id],
                saveOutput = false,
                settingsJson = "{}",
                onConfirmed = { selectedCapabilityId = null },
                onCancel = { selectedCapabilityId = null }
            )
        }
    }
}

@Composable
private fun FindPresetCard(revision: Int) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val presets = remember(revision) { ProtocolLibraryRepository.presets(context) }
    val trimmedQuery = query.trim()
    val matches = remember(presets, trimmedQuery) {
        if (trimmedQuery.isBlank()) emptyList() else presets.filter { preset ->
            listOf(preset.name, preset.methodId, preset.description, preset.settingsJson)
                .any { it.contains(trimmedQuery, ignoreCase = true) }
        }.take(8)
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Find a preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                placeholder = { Text("Search saved toolbox actions") }
            )
            if (trimmedQuery.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                when {
                    presets.isEmpty() -> Text("No presets yet.", style = MaterialTheme.typography.bodySmall)
                    matches.isEmpty() -> Text("No matching presets.", style = MaterialTheme.typography.bodySmall)
                    else -> matches.forEach { preset ->
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            onClick = { runPresetFromDashboard(context, preset) }
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(preset.name)
                                Text(preset.methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                                Text(presetResultActionLabel(preset.resultAction), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetShortcutsCard(revision: Int) {
    val context = LocalContext.current
    val presets = remember(revision) { ProtocolLibraryRepository.presets(context) }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Preset shortcuts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Quick access to saved toolbox actions.", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (presets.isEmpty()) {
                Text("No presets yet. Create one from Capabilities.", style = MaterialTheme.typography.bodySmall)
            } else {
                presets.take(6).forEach { preset ->
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        onClick = { runPresetFromDashboard(context, preset) }
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text(preset.name)
                            Text(preset.methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                            Text(presetResultActionLabel(preset.resultAction), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtocolLibraryCard(
    revision: Int,
    showPresets: Boolean = true,
    showProtocols: Boolean = true,
    expandedByDefault: Boolean = false
) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var presets by remember(revision) { mutableStateOf(ProtocolLibraryRepository.presets(context)) }
    var protocols by remember(revision) { mutableStateOf(ProtocolLibraryRepository.protocols(context)) }
    var archivedProtocols by remember(revision) { mutableStateOf(ProtocolLibraryRepository.archivedProtocols(context)) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var importPayload by rememberSaveable { mutableStateOf("") }
    var protocolName by rememberSaveable { mutableStateOf("") }
    var protocolStepDrafts by rememberSaveable { mutableStateOf(listOf<ProtocolStepDraft>()) }
    var editingProtocolId by rememberSaveable { mutableStateOf<String?>(null) }
    var addPresetDialogOpen by rememberSaveable { mutableStateOf(false) }
    var addOdkStepDialogOpen by rememberSaveable { mutableStateOf(false) }
    var showArchive by rememberSaveable { mutableStateOf(false) }

    fun refresh() {
        presets = ProtocolLibraryRepository.presets(context)
        protocols = ProtocolLibraryRepository.protocols(context)
        archivedProtocols = ProtocolLibraryRepository.archivedProtocols(context)
    }

    fun runPreset(preset: CapabilityPreset) {
        context.startActivity(Intent(context, SchedulerDispatchActivity::class.java).apply {
            action = "com.example.methodmesh.RUN_PRESET"
            putExtra("preset_id", preset.id)
            putExtra("transient_preset_run", true)
            putExtra("suppress_output", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                val title = when {
                    showPresets && !showProtocols -> "Preset library"
                    showProtocols && !showPresets -> "Protocol library"
                    else -> "Presets & protocols"
                }
                Text(
                    if (expanded) "▼ $title" else "▶ $title",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when {
                        showPresets && !showProtocols -> "${presets.size} presets"
                        showProtocols && !showPresets -> "${protocols.size} protocols"
                        else -> "${presets.size} presets · ${protocols.size} protocols"
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Text(
                if (showProtocols && !showPresets) "Chain presets into repeatable runs." else "Saved capability configurations.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                Spacer(Modifier.height(10.dp))

                if (showPresets) {
                    Text("Saved presets", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (presets.isEmpty()) {
                        Text("No presets yet. Create one from Capabilities.", style = MaterialTheme.typography.bodySmall)
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
                                    Text(
                                        "${ProtocolLibraryRepository.versionLabel(preset.versionIso)} · updated ${preset.updatedAtIso}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    Text(payloadModeLabel(preset.payloadMode), style = MaterialTheme.typography.labelSmall)
                                    Text(presetResultActionLabel(preset.resultAction), style = MaterialTheme.typography.labelSmall)
                                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                        Button(onClick = { runPreset(preset) }, modifier = Modifier.weight(1f)) { Text("Run") }
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(
                                            onClick = {
                                                ProtocolLibraryRepository.removePreset(context, preset.id)
                                                protocolStepDrafts = protocolStepDrafts.filterNot { it.presetId == preset.id }
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
                }

                if (showProtocols) {
                Text("Protocol library", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Chain presets into repeatable runs.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Text("Create protocol", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text("Build a chain from saved presets. Press Add step, pick a preset, repeat, then save.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(protocolName, { protocolName = it }, label = { Text("Protocol name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                protocolStepDrafts.forEachIndexed { index, stepDraft ->
                    val preset = presets.firstOrNull { it.id == stepDraft.presetId }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${index + 1}. ${preset?.name ?: stepDraft.presetId}", style = MaterialTheme.typography.bodySmall)
                                Text(protocolOutputLabel(stepDraft.outputMode), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { protocolStepDrafts = protocolStepDrafts.toMutableList().also { it.removeAt(index) } }) {
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
                    OutlinedButton(onClick = { addOdkStepDialogOpen = true }, modifier = Modifier.weight(1f)) {
                        Text("Add ODK form")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(
                        onClick = {
                            val selected = protocolStepDrafts.mapNotNull { draft -> presets.firstOrNull { it.id == draft.presetId }?.let { draft to it } }
                            if (protocolName.isBlank() || selected.isEmpty()) {
                                status = "Protocol name and at least one preset are required."
                            } else {
                                val saved = ProtocolLibraryRepository.saveProtocol(
                                    context,
                                    ProtocolDefinition(
                                        id = editingProtocolId ?: java.util.UUID.randomUUID().toString(),
                                        name = protocolName.trim(),
                                        steps = selected.mapIndexed { index, (draft, preset) ->
                                            ProtocolStep(
                                                name = preset.name,
                                                presetId = preset.id,
                                                order = index,
                                                outputMode = ProtocolOutputMode.normalize(draft.outputMode)
                                            )
                                        }
                                    )
                                )
                                protocolName = ""
                                protocolStepDrafts = emptyList()
                                editingProtocolId = null
                                refresh()
                                status = "Protocol saved: ${saved.name} (${ProtocolLibraryRepository.versionLabel(saved.versionIso)})."
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = protocolName.isNotBlank() && protocolStepDrafts.isNotEmpty()
                    ) { Text("Save protocol") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            protocolName = ""
                            protocolStepDrafts = emptyList()
                            editingProtocolId = null
                            status = "Protocol edit cancelled."
                        },
                        modifier = Modifier.weight(1f),
                        enabled = editingProtocolId != null || protocolName.isNotBlank() || protocolStepDrafts.isNotEmpty()
                    ) { Text("Clear") }
                }
                if (addPresetDialogOpen) {
                    Dialog(onDismissRequest = { addPresetDialogOpen = false }) {
                        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                            var selectedOutputMode by rememberSaveable { mutableStateOf(ProtocolOutputMode.SAVE) }
                            Column(Modifier.padding(16.dp)) {
                                Text("Add protocol step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Choose the preset and what should happen to its output.", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(8.dp))
                                ProtocolOutputModeSelector(
                                    selected = selectedOutputMode,
                                    onSelected = { selectedOutputMode = it }
                                )
                                Spacer(Modifier.height(8.dp))
                                presets.forEach { preset ->
                                    OutlinedButton(
                                        onClick = {
                                            protocolStepDrafts = protocolStepDrafts + ProtocolStepDraft(preset.id, selectedOutputMode)
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
                if (addOdkStepDialogOpen) {
                    OdkProtocolStepDialog(
                        onDismiss = { addOdkStepDialogOpen = false },
                        onStepCreated = { preset, outputMode ->
                            val saved = ProtocolLibraryRepository.savePreset(context, preset)
                            protocolStepDrafts = protocolStepDrafts + ProtocolStepDraft(saved.id, outputMode)
                            refresh()
                            addOdkStepDialogOpen = false
                            status = "Added ODK form step: ${saved.name}"
                        }
                    )
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
                                Text(
                                    "${ProtocolLibraryRepository.versionLabel(protocol.versionIso)} · updated ${protocol.updatedAtIso}",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                protocol.steps.sortedBy { it.order }.forEachIndexed { index, step ->
                                    val preset = presets.firstOrNull { it.id == step.presetId }
                                    Text(
                                        "${index + 1}. ${preset?.name ?: step.name} · ${protocolOutputLabel(step.outputMode)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            protocolName = protocol.name
                                            protocolStepDrafts = protocol.steps.sortedBy { it.order }.map { ProtocolStepDraft(it.presetId, it.outputMode) }
                                            editingProtocolId = protocol.id
                                            status = "Editing ${protocol.name}. Saving will create a new version and archive the previous version."
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Edit protocol") }
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = false, testMode = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Test flow only — no output") }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = true, testMode = true) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Test with step outputs") }
                                    Spacer(Modifier.height(6.dp))
                                    Button(
                                        onClick = { launchProtocolRun(context, protocol, saveOutput = true, testMode = false) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Run for real") }
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(
                                        onClick = {
                                            ProtocolLibraryRepository.archiveProtocol(context, protocol.id)
                                            refresh()
                                            status = "Archived protocol: ${protocol.name}"
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Archive protocol") }
                                }
                            }
                        }
                    }
                }
                if (archivedProtocols.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { showArchive = !showArchive }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showArchive) "Hide archived protocol versions" else "Show archived protocol versions (${archivedProtocols.size})")
                    }
                    if (showArchive) {
                        archivedProtocols.forEach { protocol ->
                            Surface(
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(protocol.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    Text("${ProtocolLibraryRepository.versionLabel(protocol.versionIso)} · archived version", style = MaterialTheme.typography.labelSmall)
                                    OutlinedButton(
                                        onClick = {
                                            ProtocolLibraryRepository.unarchiveProtocol(context, protocol.id)
                                            refresh()
                                            status = "Unarchived protocol version: ${protocol.name}"
                                        },
                                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                                    ) { Text("Unarchive as active copy") }
                                }
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
                            .setPrimaryClip(ClipData.newPlainText("MethodMesh protocol library", payload))
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
private fun ProtocolOutputModeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Output handling", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OptionRow("Save", ProtocolOutputMode.normalize(selected) == ProtocolOutputMode.SAVE) { onSelected(ProtocolOutputMode.SAVE) }
        OptionRow("Don't save", ProtocolOutputMode.normalize(selected) == ProtocolOutputMode.NONE) { onSelected(ProtocolOutputMode.NONE) }
        OptionRow("Share", ProtocolOutputMode.normalize(selected) == ProtocolOutputMode.SHARE) { onSelected(ProtocolOutputMode.SHARE) }
        Text(protocolOutputDescription(selected), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun OptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun protocolOutputLabel(mode: String): String = when (ProtocolOutputMode.normalize(mode)) {
    ProtocolOutputMode.NONE -> "No MethodMesh output"
    ProtocolOutputMode.SHARE -> "Share result"
    else -> "Save to MethodMesh outputs"
}

private fun protocolOutputDescription(mode: String): String = when (ProtocolOutputMode.normalize(mode)) {
    ProtocolOutputMode.NONE -> "Do not write a MethodMesh output package for this step; useful when ODK/Kobo/Central owns the data."
    ProtocolOutputMode.SHARE -> "Create a MethodMesh package and open Android share for the result."
    else -> "Write this step into the MethodMesh output folder."
}

@Composable
private fun PayloadModeSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("Returned payload", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        listOf(
            ProtocolPayloadMode.CORE to "Core result",
            ProtocolPayloadMode.AUDIT to "Core + audit",
            ProtocolPayloadMode.FULL to "Everything"
        ).forEach { (mode, label) ->
            val active = ProtocolPayloadMode.normalize(selected) == mode
            OptionRow(label, active) { onSelected(mode) }
        }
        Text(payloadModeDescription(selected), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun payloadModeLabel(mode: String): String = when (ProtocolPayloadMode.normalize(mode)) {
    ProtocolPayloadMode.AUDIT -> "Returns: core + audit fields"
    ProtocolPayloadMode.FULL -> "Returns: everything"
    else -> "Returns: core result only"
}

private fun payloadModeDescription(mode: String): String = when (ProtocolPayloadMode.normalize(mode)) {
    ProtocolPayloadMode.AUDIT -> "Return core values plus ALCOA-style IDs, times, device details, hashes and warnings."
    ProtocolPayloadMode.FULL -> "Return the full capability output, including raw JSON, manifests, traces and success metadata."
    else -> "Return only the practical answer values, such as readings, answers, selected labels and file names."
}

@Composable
private fun PresetResultActionSelector(
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text("After result", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        OptionRow("Home", PresetResultAction.normalize(selected) == PresetResultAction.HOME) { onSelected(PresetResultAction.HOME) }
        OptionRow("Share", PresetResultAction.normalize(selected) == PresetResultAction.SHARE) { onSelected(PresetResultAction.SHARE) }
        OptionRow("Save", PresetResultAction.normalize(selected) == PresetResultAction.SAVE) { onSelected(PresetResultAction.SAVE) }
        Text(presetResultActionDescription(selected), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    }
}

private fun presetResultActionLabel(action: String): String = when (PresetResultAction.normalize(action)) {
    PresetResultAction.SHARE -> "After result: share"
    PresetResultAction.SAVE -> "After result: save"
    else -> "After result: home"
}

private fun presetResultActionDescription(action: String): String = when (PresetResultAction.normalize(action)) {
    PresetResultAction.SHARE -> "Open Android share for the core result when the preset finishes."
    PresetResultAction.SAVE -> "Save a MethodMesh output package when the preset finishes."
    else -> "Return to MethodMesh without saving when the preset finishes."
}

@Composable
private fun OdkProtocolStepDialog(
    onDismiss: () -> Unit,
    onStepCreated: (CapabilityPreset, String) -> Unit
) {
    val context = LocalContext.current
    var selectedProjectId by rememberSaveable { mutableStateOf("") }
    var selectedPackage by rememberSaveable { mutableStateOf("org.odk.collect.android") }
    var manualFormId by rememberSaveable { mutableStateOf("") }
    var selectedOutputMode by rememberSaveable { mutableStateOf(ProtocolOutputMode.NONE) }
    val projects = remember { ExternalProjectRegistry.load(context) }
    val forms = remember(selectedProjectId, selectedPackage) {
        if (selectedProjectId.isBlank()) emptyList() else ExternalFormCatalog.list(context, selectedProjectId, selectedPackage)
    }

    fun presetFor(formSelector: String, displayName: String = formSelector): CapabilityPreset =
        CapabilityPreset(
            name = "odk_form_${displayName.ifBlank { formSelector }}",
            methodId = As100OdkFormLauncherMethod.ID,
            settingsJson = JSONObject().apply {
                put("project_id", selectedProjectId)
                put("package_name", selectedPackage)
                put("form_selector", formSelector)
            }.toString()
        )

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp)) {
                Text("Add ODK/XLSForm step", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Pick a known project and form, or enter a form ID manually.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                ProtocolOutputModeSelector(
                    selected = selectedOutputMode,
                    onSelected = { selectedOutputMode = it }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    if (selectedPackage == "org.odk.collect.android") {
                        Button(onClick = { selectedPackage = "org.odk.collect.android" }, modifier = Modifier.weight(1f)) { Text("✓ ODK") }
                    } else {
                        OutlinedButton(onClick = { selectedPackage = "org.odk.collect.android" }, modifier = Modifier.weight(1f)) { Text("ODK") }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (selectedPackage == "org.koboc.collect.android") {
                        Button(onClick = { selectedPackage = "org.koboc.collect.android" }, modifier = Modifier.weight(1f)) { Text("✓ Kobo") }
                    } else {
                        OutlinedButton(onClick = { selectedPackage = "org.koboc.collect.android" }, modifier = Modifier.weight(1f)) { Text("Kobo") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Known projects", style = MaterialTheme.typography.labelLarge)
                if (projects.isEmpty()) {
                    Text("No saved projects yet. Use the ODK form launcher once to discover/save a project, or enter the form ID manually.", style = MaterialTheme.typography.bodySmall)
                } else {
                    projects.filter { it.packageName.isBlank() || it.packageName == selectedPackage }.forEach { project ->
                        OutlinedButton(
                            onClick = { selectedProjectId = project.id; selectedPackage = project.packageName.ifBlank { selectedPackage } },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) { Text(if (selectedProjectId == project.id) "✓ ${project.name}" else project.name) }
                    }
                }
                if (forms.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("Forms in selected project", style = MaterialTheme.typography.labelLarge)
                    forms.forEach { form ->
                        OutlinedButton(
                            onClick = { onStepCreated(presetFor(form.id, form.name), selectedOutputMode) },
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) { Text("${form.name} (${form.id})") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = manualFormId,
                    onValueChange = { manualFormId = it },
                    label = { Text("Manual form ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    onClick = { onStepCreated(presetFor(manualFormId.trim()), selectedOutputMode) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    enabled = manualFormId.isNotBlank()
                ) { Text("Add manual form step") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Cancel") }
            }
        }
    }
}

@Composable
private fun RuntimeSummaryCard(moduleCount: Int, methodCount: Int) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MethodMeshMark(size = 58.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("MethodMesh", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Do Stuff", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("A practical toolkit for doing stuff you need to do.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("$methodCount methods", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Text("$moduleCount modules", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}


@Composable
private fun WorkbenchCard(
    methods: List<As100Method>,
    modules: List<MethodMeshModule>,
    expandedByDefault: Boolean = false
) {
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    val screenMap = MethodMeshModuleRegistry.capabilityScreens().associateBy { it.capabilityId }
    val moduleByMethod = modules.flatMap { module -> module.as100Methods().map { it.id to module } }.toMap()
    val workbenchMethods = methods.filter { method ->
        capabilityUiClass(method, moduleByMethod[method.id]) == CapabilityUiClass.WorkbenchTool
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
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "▼ Workbench" else "▶ Workbench",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(workbenchMethods.size.toString(), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "Set up hardware, inspect apps and check Bluetooth devices.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                WorkbenchApiLinksPanel()
                workbenchMethods.sortedBy { it.id }
                    .groupBy { moduleByMethod[it.id]?.displayName ?: "Other" }
                    .toSortedMap()
                    .forEach { (moduleName, moduleMethods) ->
                        var moduleExpanded by rememberSaveable("Workbench:$moduleName") { mutableStateOf(false) }
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
                                    uiClass = CapabilityUiClass.WorkbenchTool,
                                    onPresetSaved = {}
                                )
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun WorkbenchApiLinksPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var definitions by remember { mutableStateOf(ApiDefinitionRepository.all(context)) }
    var selectedId by rememberSaveable { mutableStateOf(definitions.firstOrNull()?.id.orEmpty()) }
    val selected = definitions.firstOrNull { it.id == selectedId } ?: definitions.firstOrNull()
    val inputValues = remember { mutableStateMapOf<String, String>() }
    var previewText by rememberSaveable { mutableStateOf("") }
    var confirmDefinition by remember { mutableStateOf<ApiDefinition?>(null) }
    var testing by rememberSaveable { mutableStateOf(false) }

    fun refreshDefinitions() {
        definitions = ApiDefinitionRepository.all(context)
        if (selectedId.isBlank() || definitions.none { it.id == selectedId }) {
            selectedId = definitions.firstOrNull()?.id.orEmpty()
        }
    }

    LaunchedEffect(selected?.id) {
        selected?.inputs.orEmpty().forEach { input ->
            inputValues.putIfAbsent(input.id, input.defaultValue.ifBlank { sampleValueFor(input.type) })
        }
        previewText = ""
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Online API links", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Inspect definitions and preview requests. Live testing will require explicit send confirmation.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = { refreshDefinitions() }) {
                    Text("Refresh")
                }
            }

            if (definitions.isEmpty()) {
                Text("No API definitions found.", style = MaterialTheme.typography.bodySmall)
                return@Column
            }

            definitions.forEach { definition ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedId = definition.id
                            previewText = ""
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = definition.id == selected?.id,
                        onClick = {
                            selectedId = definition.id
                            previewText = ""
                        }
                    )
                    Column(Modifier.weight(1f)) {
                        Text(definition.name, style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${definition.id} · ${definition.origin.name.lowercase()} · v${definition.version}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            selected?.let { definition ->
                if (definition.privacy.sendsLocation) {
                    Text(
                        apiLocationDisclosureText(definition),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                definition.inputs.forEach { input ->
                    OutlinedTextField(
                        value = inputValues[input.id].orEmpty(),
                        onValueChange = { inputValues[input.id] = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(input.name) },
                        placeholder = { Text(input.id) }
                    )
                }

                OutlinedButton(
                    onClick = { previewText = apiDefinitionPreview(definition, inputValues.toMap()) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Preview request")
                }

                if (definition.origin == ApiDefinitionOrigin.BUNDLED) {
                    Button(
                        onClick = {
                            previewText = apiDefinitionPreview(definition, inputValues.toMap())
                            confirmDefinition = definition
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !testing
                    ) {
                        Text(if (testing) "Sending…" else "Send test request")
                    }
                } else {
                    Text(
                        "Live testing for saved or imported API links will be enabled after the API editor has trust controls.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (definition.response.expectedPaths.isNotEmpty()) {
                    Text("Expected paths", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    definition.response.expectedPaths.forEach { path ->
                        Text(path, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                    }
                }

                if (previewText.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            previewText,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(top = 4.dp),
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }

    confirmDefinition?.let { definition ->
        AlertDialog(
            onDismissRequest = { confirmDefinition = null },
            title = { Text("Send API request?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MethodMesh will send this bundled test request to ${definition.attribution.providerName}.")
                    if (definition.privacy.sendsLocation) {
                        Text(apiLocationDisclosureText(definition), color = MaterialTheme.colorScheme.error)
                    }
                    Text("Saved and imported API links remain preview-only until the API editor has trust controls.")
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val snapshot = definition
                        val inputs = inputValues.toMap()
                        confirmDefinition = null
                        testing = true
                        previewText = "Sending request…"
                        scope.launch {
                            val resultText = withContext(Dispatchers.IO) {
                                val result = ApiGetExecutor(
                                    registry = InMemoryApiDefinitionRegistry(listOf(snapshot)),
                                    httpClient = HttpUrlConnectionOnlineHttpClient()
                                ).execute(
                                    ApiGetRequest(
                                        definitionId = snapshot.id,
                                        inputs = inputs
                                    )
                                )
                                apiResultPreview(snapshot, result)
                            }
                            testing = false
                            previewText = resultText
                        }
                    }
                ) {
                    Text("Send")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmDefinition = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private fun apiDefinitionPreview(definition: ApiDefinition, inputs: Map<String, String>): String {
    val executor = ApiGetExecutor(
        registry = InMemoryApiDefinitionRegistry(listOf(definition)),
        httpClient = object : OnlineHttpClient {
            override fun get(request: OnlineHttpRequest): OnlineHttpResponse =
                error("Offline preview does not send network requests.")
        }
    )
    return runCatching {
        val prepared = executor.prepare(definition, inputs)
        buildString {
            appendLine("Method: ${definition.method.name}")
            appendLine("URL: ${prepared.redactedUrl}")
            appendLine("Headers: ${prepared.headers.keys.sorted().joinToString().ifBlank { "none" }}")
            appendLine("Cache: ${definition.cache.mode.name.lowercase()} · ${definition.cache.ttlSeconds}s")
            appendLine("Response: ${definition.response.type.name.lowercase()}")
        }.trim()
    }.getOrElse { error ->
        "Cannot prepare request: ${error.message.orEmpty()}"
    }
}

private fun apiLocationDisclosureText(definition: ApiDefinition): String {
    val provider = definition.attribution.providerName.ifBlank { "the provider" }
    return when (definition.privacy.locationMode) {
        com.example.methodmesh.core.onlinedata.LocationDisclosureMode.ROUNDED ->
            "This API sends rounded location to $provider, about ${definition.privacy.roundedLocationRadiusMeters / 1_000} km precision."
        com.example.methodmesh.core.onlinedata.LocationDisclosureMode.EXACT ->
            "This API sends exact location to $provider."
        com.example.methodmesh.core.onlinedata.LocationDisclosureMode.MANUAL ->
            "This API sends the manually entered location to $provider."
        com.example.methodmesh.core.onlinedata.LocationDisclosureMode.DISABLED ->
            "This API is marked as not sending location."
    }
}

private fun sampleValueFor(type: ApiInputType): String = when (type) {
    ApiInputType.LATITUDE -> "52.0779"
    ApiInputType.LONGITUDE -> "-0.0580"
    ApiInputType.BOOLEAN -> "true"
    ApiInputType.NUMBER -> "0"
    else -> ""
}

private fun apiResultPreview(
    definition: ApiDefinition,
    result: ApiExecutionResult
): String = buildString {
    appendLine("Status: ${result.status.name.lowercase()}")
    appendLine("Provider: ${definition.attribution.providerName}")
    result.meta.statusCode?.let { appendLine("HTTP: $it") }
    appendLine("Cache: ${if (result.meta.fromCache) "hit" else "miss"}")
    result.meta.sourceUrlRedacted.takeIf { it.isNotBlank() }?.let { appendLine("URL: $it") }
    result.error?.message?.takeIf { it.isNotBlank() }?.let { appendLine("Error: $it") }
    result.error?.detail?.takeIf { it.isNotBlank() }?.let { appendLine("Detail: $it") }
    appendLine()
    appendLine("Data")
    appendLine(resultTreePreview(result.data))
}.trim()

private fun resultTreePreview(tree: ResultTree, indent: String = "", maxDepth: Int = 4): String {
    if (maxDepth <= 0) return "$indent…"
    return when (tree) {
        is ResultTree.ObjectNode -> {
            if (tree.values.isEmpty()) {
                "${indent}{}"
            } else {
                tree.values.entries.take(12).joinToString("\n") { (key, value) ->
                    when (value) {
                        is ResultTree.ObjectNode,
                        is ResultTree.ArrayNode -> "$indent$key:\n${resultTreePreview(value, "$indent  ", maxDepth - 1)}"
                        else -> "$indent$key: ${resultTreeScalar(value)}"
                    }
                } + if (tree.values.size > 12) "\n$indent…" else ""
            }
        }
        is ResultTree.ArrayNode -> {
            if (tree.values.isEmpty()) {
                "${indent}[]"
            } else {
                tree.values.take(6).mapIndexed { index, value ->
                    when (value) {
                        is ResultTree.ObjectNode,
                        is ResultTree.ArrayNode -> "$indent[$index]\n${resultTreePreview(value, "$indent  ", maxDepth - 1)}"
                        else -> "$indent[$index] ${resultTreeScalar(value)}"
                    }
                }.joinToString("\n") + if (tree.values.size > 6) "\n$indent…" else ""
            }
        }
        else -> "$indent${resultTreeScalar(tree)}"
    }
}

private fun resultTreeScalar(tree: ResultTree): String = when (tree) {
    is ResultTree.StringNode -> tree.value
    is ResultTree.NumberNode -> tree.value.toString()
    is ResultTree.BooleanNode -> tree.value.toString()
    ResultTree.NullNode -> "null"
    is ResultTree.ObjectNode -> "{…}"
    is ResultTree.ArrayNode -> "[…]"
}

@Composable
private fun CapabilityRegistryCard(
    methods: List<As100Method>,
    modules: List<MethodMeshModule>,
    expandedByDefault: Boolean = false,
    onPresetSaved: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var query by rememberSaveable { mutableStateOf("") }
    val screenMap = MethodMeshModuleRegistry.capabilityScreens().associateBy { it.capabilityId }
    val moduleByMethod = modules.flatMap { module -> module.as100Methods().map { it.id to module } }.toMap()
    val filteredMethods = methods.filter { method ->
        val moduleName = moduleByMethod[method.id]?.displayName.orEmpty()
        capabilityUiClass(method, moduleByMethod[method.id]) == CapabilityUiClass.ProtocolPrimitive &&
            (query.isBlank() || listOf(method.id, method.descriptor.name, method.descriptor.description.orEmpty(), moduleName)
                .any { it.contains(query.trim(), ignoreCase = true) })
    }
    val protocolCount = methods.count { method -> capabilityUiClass(method, moduleByMethod[method.id]) == CapabilityUiClass.ProtocolPrimitive }
    val productionMethods = filteredMethods.filter { capabilityLifecycle(it) == CapabilityLifecycle.Production }
    val developmentMethods = filteredMethods.filter { capabilityLifecycle(it) == CapabilityLifecycle.Development }

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
                text = "Build, test and save reusable MethodMesh actions.",
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
                    "Showing ${filteredMethods.size} of $protocolCount",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall
                )
                CapabilityMethodSection(
                    title = "Production",
                    subtitle = "Reviewed, tested, documented and ready for protocols/ODK use.",
                    methods = productionMethods,
                    moduleByMethod = moduleByMethod,
                    screenMap = screenMap,
                    uiClass = CapabilityUiClass.ProtocolPrimitive,
                    onPresetSaved = onPresetSaved
                )
                CapabilityMethodSection(
                    title = "Development",
                    subtitle = "Unreviewed or still being tuned. Everything starts here until promoted.",
                    methods = developmentMethods,
                    moduleByMethod = moduleByMethod,
                    screenMap = screenMap,
                    uiClass = CapabilityUiClass.ProtocolPrimitive,
                    onPresetSaved = onPresetSaved,
                    expandedByDefault = true
                )
                if (filteredMethods.isEmpty()) {
                    Text("No capabilities match this search.", modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun CapabilityMethodSection(
    title: String,
    subtitle: String,
    methods: List<As100Method>,
    moduleByMethod: Map<String, MethodMeshModule>,
    screenMap: Map<String, CapabilityScreenSpec>,
    uiClass: CapabilityUiClass,
    onPresetSaved: () -> Unit,
    expandedByDefault: Boolean = uiClass == CapabilityUiClass.ProtocolPrimitive
) {
    var sectionExpanded by rememberSaveable(title) { mutableStateOf(expandedByDefault) }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { sectionExpanded = !sectionExpanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (sectionExpanded) "▼ $title" else "▶ $title",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(methods.size.toString(), style = MaterialTheme.typography.labelMedium)
        }
        Text(subtitle, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall)
        if (sectionExpanded) {
            if (methods.isEmpty()) {
                Text("No matching items in this section.", modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
            }
            methods.sortedBy { it.id }
                .groupBy { moduleByMethod[it.id]?.displayName ?: "Other methods" }
                .toSortedMap()
                .forEach { (moduleName, moduleMethods) ->
                    var moduleExpanded by rememberSaveable("$title:$moduleName") { mutableStateOf(false) }
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
                                uiClass = uiClass,
                                onPresetSaved = onPresetSaved
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun CapabilityCard(
    method: As100Method,
    module: MethodMeshModule?,
    screen: CapabilityScreenSpec?,
    uiClass: CapabilityUiClass,
    onPresetSaved: () -> Unit
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(method.id) { mutableStateOf(false) }
    var quickTestOpen by rememberSaveable("${method.id}:quickTest") { mutableStateOf(false) }
    var quickTestSaveOpen by rememberSaveable("${method.id}:quickTestSave") { mutableStateOf(false) }
    var lastResult by remember(method.id) { mutableStateOf<ExecutionResult?>(null) }
    var lastResultStatus by rememberSaveable("${method.id}:lastResultStatus") { mutableStateOf<String?>(null) }
    var presetDialogOpen by rememberSaveable("${method.id}:presetDialog") { mutableStateOf(false) }
    var presetStatus by rememberSaveable("${method.id}:presetStatus") { mutableStateOf<String?>(null) }
    var intentCopyStatus by rememberSaveable("${method.id}:intentCopyStatus") { mutableStateOf<String?>(null) }
    var returnPayloadMode by rememberSaveable("${method.id}:returnPayloadMode") { mutableStateOf(ProtocolPayloadMode.CORE) }
    val settingSchema = remember(method.id) { CapabilityConfigurationRegistry.settingsFor(method.id) }
    val settingsState = remember(method.id) { SettingsState(settingSchema) }
    val isProtocolPrimitive = uiClass == CapabilityUiClass.ProtocolPrimitive
    fun acceptResult(result: ExecutionResult, saveOutput: Boolean) {
        lastResult = result
        lastResultStatus = if (saveOutput) {
            runCatching { OutputExportRepository.exportPackage(context, result) }
                .onSuccess { OutputExportRepository.notifySaved(context, it) }
                .fold(
                    onSuccess = { "Saved output package: ${it.summary}" },
                    onFailure = { "Save failed: ${it.message ?: "storage error"}" }
                )
        } else {
            "Test only — not saved."
        }
    }

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
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
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
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { quickTestOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isProtocolPrimitive) "Configure / test" else "Open tool")
            }
            if (isProtocolPrimitive) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { quickTestSaveOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Test and save output")
                }
            }
            lastResult?.let { ResultPreview(it, lastResultStatus) }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                CapabilityOutputsSection(method)
                if (screen == null) {
                    CollapsibleCapabilitySection(
                        title = "Settings",
                        subtitle = if (settingSchema.isEmpty()) "No typed settings" else "${settingSchema.size} setting${if (settingSchema.size == 1) "" else "s"}"
                    ) {
                        if (settingSchema.isEmpty()) {
                            Text(
                                "This capability has no typed settings registered yet. Saving a preset will still preserve the capability identity.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        } else {
                            SettingsRenderer(settingSchema, settingsState, capabilityId = method.id)
                        }
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text("Configuration", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Text(
                                "Use Test to configure and preview.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                if (isProtocolPrimitive) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            CollapsibleCapabilitySection(
                                title = "Return payload",
                                subtitle = when (ProtocolPayloadMode.normalize(returnPayloadMode)) {
                                    ProtocolPayloadMode.AUDIT -> "core + audit"
                                    ProtocolPayloadMode.FULL -> "everything"
                                    else -> "core"
                                }
                            ) {
                                PayloadModeSelector(
                                    selected = returnPayloadMode,
                                    onSelected = { returnPayloadMode = it }
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val intentText = odkIntentFromSettings(method.id, settingsState.asMap(), returnPayloadMode)
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh ODK intent", intentText))
                                    intentCopyStatus = "Copied ODK intent for ${method.id}."
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            ) {
                                Text("Copy ODK intent")
                            }
                            if (lastResult != null) {
                                OutlinedButton(
                                    onClick = {
                                        lastResult = null
                                        lastResultStatus = null
                                    },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                ) {
                                    Text("Clear last result")
                                }
                            }
                        }
                    }
                }
                intentCopyStatus?.let {
                    Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                }
            }
            presetStatus?.let {
                Text(it, modifier = Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            }
            if (quickTestOpen) {
                FullScreenCapabilityDialog(onDismiss = { quickTestOpen = false }) {
                    DashboardCapabilityRunner(
                        method = method,
                        screen = screen,
                        saveOutput = false,
                        settingsJson = settingsJsonFromMap(settingsState.asMap()),
                        onSettingsChanged = { updated -> updated.forEach { (key, value) -> settingsState.setString(key, value) } },
                        onConfirmed = { result ->
                            acceptResult(result, saveOutput = false)
                            quickTestOpen = false
                        },
                        onCancel = { quickTestOpen = false }
                    )
                    if (isProtocolPrimitive) {
                        Spacer(Modifier.height(12.dp))
                        presetStatus?.let {
                            Text(it, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { presetDialogOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save current setup as preset")
                        }
                    }
                }
            }
            if (quickTestSaveOpen) {
                FullScreenCapabilityDialog(onDismiss = { quickTestSaveOpen = false }) {
                    DashboardCapabilityRunner(
                        method = method,
                        screen = screen,
                        saveOutput = true,
                        settingsJson = settingsJsonFromMap(settingsState.asMap()),
                        onSettingsChanged = { updated -> updated.forEach { (key, value) -> settingsState.setString(key, value) } },
                        onConfirmed = { result ->
                            acceptResult(result, saveOutput = true)
                            quickTestSaveOpen = false
                        },
                        onCancel = { quickTestSaveOpen = false }
                    )
                    if (isProtocolPrimitive) {
                        Spacer(Modifier.height(12.dp))
                        presetStatus?.let {
                            Text(it, modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                        OutlinedButton(
                            onClick = { presetDialogOpen = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save current setup as preset")
                        }
                    }
                }
            }
            if (presetDialogOpen) {
                val presetSettings = settingsState.asMap()
                SavePresetDialog(
                    defaultName = defaultPresetName(method.descriptor.name),
                    methodId = method.id,
                    settingSchema = settingSchema,
                    initialSettings = presetSettings,
                    defaultPayloadMode = returnPayloadMode,
                    onDismiss = { presetDialogOpen = false },
                    onSave = { name, payloadMode, resultAction, savedSettings ->
                        returnPayloadMode = payloadMode
                        val saved = ProtocolLibraryRepository.savePreset(
                            context,
                            CapabilityPreset(
                                name = name,
                                methodId = method.id,
                                settingsJson = settingsJsonFromMap(savedSettings),
                                payloadMode = payloadMode,
                                resultAction = resultAction,
                                description = method.descriptor.description.orEmpty()
                            )
                        )
                        presetDialogOpen = false
                        presetStatus = "Saved preset: ${saved.name} (${ProtocolLibraryRepository.versionLabel(saved.versionIso)})."
                        onPresetSaved()
                    }
                )
            }
        }
    }
}

@Composable
private fun FullScreenCapabilityDialog(
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
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
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Home")
                }
                Spacer(Modifier.height(12.dp))
                content()
            }
        }
    }
}

@Composable
private fun CollapsibleCapabilitySection(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (expanded) "▼ $title" else "▶ $title",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                content()
            }
        }
    }
}

@Composable
private fun CapabilityOutputsSection(method: As100Method) {
    CollapsibleCapabilitySection(
        title = "Outputs",
        subtitle = "${method.descriptor.outputs.size} output${if (method.descriptor.outputs.size == 1) "" else "s"}"
    ) {
        Text(method.descriptor.outputs.joinToString().ifBlank { "none declared" }, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SavePresetDialog(
    defaultName: String,
    methodId: String,
    settingSchema: List<MethodSetting>,
    initialSettings: Map<String, Any>,
    defaultPayloadMode: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, Map<String, Any>) -> Unit
) {
    var name by rememberSaveable(methodId) { mutableStateOf(defaultName) }
    var payloadMode by rememberSaveable(methodId) { mutableStateOf(ProtocolPayloadMode.normalize(defaultPayloadMode)) }
    var resultAction by rememberSaveable(methodId) { mutableStateOf(PresetResultAction.HOME) }
    var nameDialogOpen by rememberSaveable(methodId) { mutableStateOf(false) }
    val usesTypedSchema = settingSchema.isNotEmpty()
    val fieldSpecs = remember(methodId, initialSettings, settingSchema) {
        if (usesTypedSchema) emptyList() else presetFieldSpecs(initialSettings)
    }
    val schemaDefaults = remember(methodId, initialSettings, settingSchema) {
        settingSchema.associate { setting -> setting.id to presetDefaultString(setting, initialSettings[setting.id]) }
    }
    val editableKeys = remember(methodId, fieldSpecs, settingSchema) {
        if (usesTypedSchema) settingSchema.map { it.id } else fieldSpecs.map { it.key }
    }
    val existingRuntimeFields = remember(methodId, initialSettings) {
        initialSettings["methodmesh_runtime_fields"]
            ?.toString()
            .orEmpty()
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }
    val editableValues = remember(methodId, initialSettings) {
        mutableStateMapOf<String, String>().apply {
            schemaDefaults.forEach { (key, value) -> put(key, value) }
            fieldSpecs.forEach { spec -> put(spec.key, initialSettings[spec.key]?.toString() ?: spec.defaultValue) }
            initialSettings.forEach { (key, value) -> putIfAbsent(key, value.toString()) }
        }
    }
    val fixedFlags = remember(methodId, initialSettings) {
        mutableStateMapOf<String, Boolean>().apply {
            if (usesTypedSchema) {
                settingSchema.forEach { setting ->
                    put(setting.id, setting.id !in existingRuntimeFields)
                }
            } else {
                fieldSpecs.forEach { spec ->
                    val current = editableValues[spec.key].orEmpty()
                    put(spec.key, spec.defaultFixed || current.isNotBlank() && !spec.runtimeInput)
                }
            }
        }
    }

    fun selectedSettings(): Map<String, Any> {
        if (editableKeys.isEmpty()) return presetSettingsFor(editableValues.toMap())
        val selected = linkedMapOf<String, Any>()
        val runtimeFields = mutableListOf<String>()
        editableKeys.forEach { key ->
            if (fixedFlags[key] == true) {
                val value = editableValues[key].orEmpty()
                if (value.isNotBlank()) selected[key] = value
            } else {
                runtimeFields += key
            }
        }
        initialSettings.forEach { (key, value) ->
            if (key !in editableKeys && key != "methodmesh_runtime_fields" && value.toString().isNotBlank()) selected[key] = value
        }
        if (runtimeFields.isNotEmpty()) selected["methodmesh_runtime_fields"] = runtimeFields.joinToString(",")
        return selected
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
                .heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(
                Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Save capability preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(methodId, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(8.dp))
                PayloadModeSelector(
                    selected = payloadMode,
                    onSelected = { payloadMode = it }
                )
                Spacer(Modifier.height(8.dp))
                PresetResultActionSelector(
                    selected = resultAction,
                    onSelected = { resultAction = it }
                )
                Spacer(Modifier.height(8.dp))
                Text("Preset fields", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                if (editableKeys.isEmpty()) {
                    Text("No editable preset fields for this capability.", style = MaterialTheme.typography.bodySmall)
                } else if (usesTypedSchema) {
                    settingSchema.forEach { setting ->
                        PresetMethodSettingRow(
                            setting = setting,
                            value = editableValues[setting.id].orEmpty(),
                            fixed = fixedFlags[setting.id] == true,
                            onValueChanged = { editableValues[setting.id] = it },
                            onFixedChanged = { fixedFlags[setting.id] = it }
                        )
                    }
                } else {
                    fieldSpecs.forEach { spec ->
                        PresetFieldRow(
                            spec = spec,
                            value = editableValues[spec.key].orEmpty(),
                            fixed = fixedFlags[spec.key] == true,
                            onValueChanged = { editableValues[spec.key] = it },
                            onFixedChanged = { fixedFlags[spec.key] = it }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Saved settings preview", style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Text(settingsJsonFromMap(selectedSettings()), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { nameDialogOpen = true },
                        modifier = Modifier.weight(1f)
                    ) { Text("Save…") }
                }
                if (nameDialogOpen) {
                    AlertDialog(
                        onDismissRequest = { nameDialogOpen = false },
                        title = { Text("Name preset") },
                        text = {
                            OutlinedTextField(
                                name,
                                { name = it },
                                label = { Text("Preset name") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    if (name.isNotBlank()) {
                                        onSave(
                                            name.trim(),
                                            ProtocolPayloadMode.normalize(payloadMode),
                                            PresetResultAction.normalize(resultAction),
                                            selectedSettings()
                                        )
                                    }
                                },
                                enabled = name.isNotBlank()
                            ) { Text("Save preset") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { nameDialogOpen = false }) { Text("Back") }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetMethodSettingRow(
    setting: MethodSetting,
    value: String,
    fixed: Boolean,
    onValueChanged: (String) -> Unit,
    onFixedChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = fixed, onCheckedChange = onFixedChanged)
                Column(Modifier.weight(1f)) {
                    Text(setting.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (fixed) "Fixed in preset" else "Ask at runtime / supplied by ODK",
                        style = MaterialTheme.typography.labelSmall
                    )
                    setting.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            when (setting) {
                is MethodSetting.BooleanSetting -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (value.equals("true", true)) "On" else "Off", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = value.equals("true", true),
                            enabled = fixed,
                            onCheckedChange = { onValueChanged(it.toString()) }
                        )
                    }
                }
                is MethodSetting.ChoiceSetting -> {
                    val spec = PresetFieldSpec(
                        key = setting.id,
                        label = setting.label,
                        defaultValue = setting.defaultValue,
                        singleChoices = setting.choices.map { choice ->
                            PresetChoiceSpec(choice, presetChoiceLabel(choice))
                        }
                    )
                    PresetSingleChoiceField(
                        spec = spec,
                        value = value,
                        enabled = fixed,
                        onValueChanged = onValueChanged
                    )
                }
                is MethodSetting.MultiChoiceSetting -> {
                    val spec = PresetFieldSpec(
                        key = setting.id,
                        label = setting.label,
                        defaultValue = setting.defaultValue,
                        multiChoices = setting.choices.map { choice ->
                            PresetChoiceSpec(choice, presetChoiceLabel(choice))
                        }
                    )
                    PresetMultiChoiceField(
                        spec = spec,
                        value = value,
                        enabled = fixed,
                        onValueChanged = onValueChanged
                    )
                }
                is MethodSetting.IntSetting -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        label = { Text(setting.unit?.let { "Saved value ($it)" } ?: "Saved value") },
                        supportingText = { Text(settingRangeLabel(setting.minimum, setting.maximum, setting.step)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                is MethodSetting.FloatSetting -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        label = { Text(setting.unit?.let { "Saved value ($it)" } ?: "Saved value") },
                        supportingText = { Text(settingRangeLabel(setting.minimum, setting.maximum, setting.step)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
                is MethodSetting.TextSetting -> {
                    OutlinedTextField(
                        value = value,
                        onValueChange = onValueChanged,
                        enabled = fixed,
                        label = { Text(if (fixed) "Saved value" else "Runtime input") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = if (setting.id.contains("message") || setting.id.contains("text") || setting.id.contains("payload")) 2 else 1
                    )
                }
            }
        }
    }
}

private data class PresetFieldSpec(
    val key: String,
    val label: String,
    val defaultValue: String = "",
    val runtimeInput: Boolean = false,
    val defaultFixed: Boolean = !runtimeInput,
    val singleChoices: List<PresetChoiceSpec> = emptyList(),
    val multiChoices: List<PresetChoiceSpec> = emptyList()
)

private data class PresetChoiceSpec(
    val value: String,
    val label: String,
    val description: String = ""
)

@Composable
private fun PresetFieldRow(
    spec: PresetFieldSpec,
    value: String,
    fixed: Boolean,
    onValueChanged: (String) -> Unit,
    onFixedChanged: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = fixed, onCheckedChange = onFixedChanged)
                Column(Modifier.weight(1f)) {
                    Text(spec.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (fixed) "Fixed in preset" else "Ask at runtime / supplied by ODK",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            if (spec.singleChoices.isNotEmpty()) {
                PresetSingleChoiceField(
                    spec = spec,
                    value = value,
                    enabled = fixed,
                    onValueChanged = onValueChanged
                )
            } else if (spec.multiChoices.isNotEmpty()) {
                PresetMultiChoiceField(
                    spec = spec,
                    value = value,
                    enabled = fixed,
                    onValueChanged = onValueChanged
                )
            } else {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChanged,
                    label = { Text(if (fixed) "Saved value" else "Optional test/default value") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = if (spec.key.contains("message") || spec.key.contains("text")) 2 else 1
                )
            }
        }
    }
}

@Composable
private fun PresetSingleChoiceField(
    spec: PresetFieldSpec,
    value: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit
) {
    val selected = value.ifBlank { spec.defaultValue }
    Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
        spec.singleChoices.forEach { choice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { onValueChanged(choice.value) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == choice.value,
                    enabled = enabled,
                    onClick = { onValueChanged(choice.value) }
                )
                Column(Modifier.padding(start = 6.dp)) {
                    Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                    if (choice.description.isNotBlank()) {
                        Text(choice.description, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetMultiChoiceField(
    spec: PresetFieldSpec,
    value: String,
    enabled: Boolean,
    onValueChanged: (String) -> Unit
) {
    val selected = value
        .split('|', ',', ';')
        .map { it.trim().uppercase() }
        .filter(String::isNotBlank)
        .toSet()
    val automatic = selected.isEmpty()

    Column(Modifier.fillMaxWidth().padding(start = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = automatic,
                enabled = enabled,
                onCheckedChange = { checked ->
                    if (checked) {
                        onValueChanged("")
                    } else {
                        onValueChanged(spec.multiChoices.firstOrNull()?.value.orEmpty())
                    }
                }
            )
            Text("Automatic detection", style = MaterialTheme.typography.bodyMedium)
        }
        Text(
            if (automatic) "All supported formats will be accepted." else "Only checked formats will be accepted.",
            style = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            spec.multiChoices.forEach { choice ->
                val checked = choice.value in selected
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = checked,
                        enabled = enabled && !automatic,
                        onCheckedChange = { nowChecked ->
                            val next = selected.toMutableSet()
                            if (nowChecked) next += choice.value else next -= choice.value
                            onValueChanged(next.joinToString("|"))
                        }
                    )
                    Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun defaultPresetName(methodName: String): String =
    methodName.trim().ifBlank { "New preset" }

private fun presetDefaultString(setting: MethodSetting, currentValue: Any?): String =
    currentValue?.toString() ?: when (setting) {
        is MethodSetting.BooleanSetting -> setting.defaultValue.toString()
        is MethodSetting.IntSetting -> setting.defaultValue.toString()
        is MethodSetting.FloatSetting -> setting.defaultValue.toString()
        is MethodSetting.TextSetting -> setting.defaultValue
        is MethodSetting.ChoiceSetting -> setting.defaultValue
        is MethodSetting.MultiChoiceSetting -> setting.defaultValue
    }

private fun presetChoiceLabel(value: String): String =
    when (value) {
        "" -> "Automatic / default"
        "true" -> "Yes"
        "false" -> "No"
        "android.intent.action.MAIN" -> "Open app"
        "android.intent.action.VIEW" -> "View / open URI"
        "android.intent.action.SEND" -> "Send"
        "android.intent.action.SENDTO" -> "Send to"
        "android.intent.action.GET_CONTENT" -> "Pick content"
        "android.intent.action.EDIT" -> "Edit"
        "full" -> "Full document scanner"
        "base_with_filter" -> "Basic scanner + image filters"
        "base" -> "Basic scanner"
        "single" -> "Single read"
        "trace" -> "Trace over time"
        "average" -> "Average over time"
        "discover" -> "Discover available sensors"
        "fallback" -> "Use selected sensor, otherwise choose nearby"
        "strict" -> "Only use selected sensor"
        "any_nearby" -> "Choose nearby sensor"
        "aht20" -> "AHT20 temperature/humidity"
        "ld2410c" -> "LD2410C radar/presence"
        "generic_ble_sensor" -> "Generic BLE sensor"
        "generic" -> "Generic sensor payload"
        "secure_random" -> "Secure random seed"
        "fixed_seed" -> "Fixed reproducible seed"
        "disabled" -> "Off / disabled"
        "preferred" -> "Preferred"
        "required" -> "Required"
        "Fingerprint" -> "Fingerprint / biometric"
        "Pin" -> "Device PIN / pattern"
        "Qr" -> "QR / camera code"
        "Nfc" -> "NFC token"
        "Password" -> "Study password/token"
        "replace" -> "Replace existing content"
        "empty_only" -> "Only if tag is empty"
        "camera" -> "Camera"
        "file_picker" -> "Choose file"
        "ocr" -> "OCR text"
        "barcodes" -> "Barcodes only"
        "ocr_and_barcodes" -> "OCR + barcodes"
        "download" -> "Download language model"
        "delete" -> "Delete language model"
        "list" -> "List language models"
        else -> value.replace('_', ' ').replace("|", " + ")
    }

private fun settingRangeLabel(minimum: Number?, maximum: Number?, step: Number?): String {
    val range = when {
        minimum != null && maximum != null -> "$minimum–$maximum"
        minimum != null -> "Minimum $minimum"
        maximum != null -> "Maximum $maximum"
        else -> ""
    }
    val stepLabel = step?.let { "Step $it" }.orEmpty()
    return listOf(range, stepLabel).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun presetFieldSpecs(values: Map<String, Any>): List<PresetFieldSpec> =
    values
        .filterKeys { it != "methodmesh_runtime_fields" }
        .keys
        .sorted()
        .map { key ->
            PresetFieldSpec(
                key = key,
                label = key.replace('_', ' '),
                defaultValue = values[key]?.toString().orEmpty(),
                runtimeInput = false
            )
        }

private fun presetSettingsFor(values: Map<String, Any>): Map<String, Any> =
    values
        .filterKeys { it != "methodmesh_runtime_fields" }
        .filterValues { value -> value.toString().isNotBlank() }

private fun settingsJsonFromMap(values: Map<String, Any>): String =
    JSONObject().apply { values.toSortedMap().forEach { (key, value) -> put(key, value) } }.toString()

private fun settingsMapFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())

private fun odkIntentFromSettings(methodId: String, settings: Map<String, Any>, payloadMode: String): String {
    val parameters = mutableListOf("method_id=${quoteIntentValue(methodId)}")
    settings.toSortedMap().forEach { (key, value) ->
        val stringValue = value.toString()
        if (stringValue.isNotBlank()) {
            val intentKey = if (key.startsWith("input_")) key else "input_$key"
            parameters += "$intentKey=${quoteIntentValue(stringValue)}"
        }
    }
    parameters += "input_payload_mode=${quoteIntentValue(ProtocolPayloadMode.normalize(payloadMode))}"
    parameters += "return_mode='flat'"
    return "com.example.methodmesh.EXECUTE_METHOD(${parameters.joinToString(",")})"
}

private fun quoteIntentValue(value: String): String =
    "'${value.replace("\\", "\\\\").replace("'", "\\'")}'"

@Composable
private fun DashboardCapabilityRunner(
    method: As100Method,
    screen: CapabilityScreenSpec?,
    saveOutput: Boolean,
    settingsJson: String,
    onSettingsChanged: (Map<String, String>) -> Unit = {},
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val request = capabilityTestWorkflowRequest(method, settingsJson, saveOutput)
    val action = request.actions.firstOrNull() ?: ExternalActionRequest(requestedId = method.id, canonicalId = method.id)
    val context = CapabilityScreenContext(
        action = action,
        request = request,
        stepNumber = 1,
        totalSteps = 1,
        completionMode = CapabilityCompletionMode.ManualConfirmation,
        presentationMode = CapabilityPresentationMode.Dashboard,
        onSettingsChanged = onSettingsChanged
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

private fun capabilityTestWorkflowRequest(method: As100Method, settingsJson: String, saveOutput: Boolean): ExternalWorkflowRequest {
    val cardSettings = settingsMapFromJson(settingsJson)
    val source = if (saveOutput) "dashboard_save" else "intent_test"
    val action = ExternalActionRequest(
        requestedId = method.id,
        canonicalId = method.id,
        settings = cardSettings
    )
    return ExternalWorkflowRequest(
        actions = listOf(action),
        invocationContext = InvocationContext(caller = source),
        returns = emptyList(),
        returnMode = ReturnMode.Json,
        settings = cardSettings,
        source = source
    )
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
private fun ResultPreview(result: ExecutionResult, statusNote: String?) {
    val fields = OutputFormatter.fields(result, includeProvenance = false, payloadMode = OutputFormatter.PayloadMode.CORE)
    var expanded by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    Spacer(Modifier.height(8.dp))
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Last confirmed result",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                "Status: ${result.status.name}",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            statusNote?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            ResultMediaPreview(fields)
            Spacer(Modifier.height(8.dp))
            val visibleFields = if (expanded) fields.entries else fields.entries.take(10)
            SelectionContainer {
                Column {
                    visibleFields.forEach { (key, value) ->
                        Text(
                            "$key = ${value?.toString().orEmpty()}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
            if (fields.size > 10) {
                Text(
                    text = if (expanded) "▲ Show fewer fields" else "▼ ${fields.size - 10} more fields",
                    modifier = Modifier
                        .clickable { expanded = !expanded }
                        .padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ResultMediaPreview(fields: Map<String, Any?>) {
    val context = LocalContext.current
    val mediaFields = fields
        .filter { (key, value) ->
            key.endsWith("_uri") &&
                value?.toString()?.isNotBlank() == true &&
                value.toString().looksLikeImageReference()
        }
        .entries
        .take(4)
        .toList()
    if (mediaFields.isEmpty()) return
    Spacer(Modifier.height(10.dp))
    Text("Media preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    mediaFields.forEach { (key, value) ->
        val uriText = value?.toString().orEmpty()
        val bitmap = remember(uriText) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uriText))?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
        if (bitmap != null) {
            Spacer(Modifier.height(6.dp))
            Text(key, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = key,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private fun String.looksLikeImageReference(): Boolean {
    val lower = lowercase()
    return lower.startsWith("content://") ||
        lower.startsWith("file://") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".png") ||
        lower.endsWith(".webp")
}

@Composable
private fun RuntimeStateCard(expandedByDefault: Boolean = false) {
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
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
private fun DeviceServicesCard(expandedByDefault: Boolean = false) {
    var displayExpanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var languageExpanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var calibrationExpanded by rememberSaveable { mutableStateOf(false) }
    var signalsExpanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Shared device settings and services used by capabilities.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { displayExpanded = !displayExpanded }
            ) {
                Text(if (displayExpanded) "▼ Display accessibility" else "▶ Display accessibility", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }
            if (displayExpanded) {
                Spacer(Modifier.height(8.dp))
                DisplaySettingsScreen()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { languageExpanded = !languageExpanded }
            ) {
                Text(if (languageExpanded) "▼ Language packs" else "▶ Language packs", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            }
            if (languageExpanded) {
                Spacer(Modifier.height(8.dp))
                MlKitLanguagePacksScreen()
            }
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
private fun MlKitLanguagePacksScreen() {
    val context = LocalContext.current
    var downloadedCodes by rememberSaveable { mutableStateOf("") }
    var busyCode by rememberSaveable { mutableStateOf<String?>(null) }
    var busySeconds by rememberSaveable { mutableStateOf(0) }
    var status by rememberSaveable { mutableStateOf("Checking language packs…") }
    var debugLog by rememberSaveable { mutableStateOf(timestampedLog("Opened language packs.")) }
    val supportedCodes = remember { MlKitLanguageCatalog.supportedCodes() }
    val allLanguages = remember { MlKitLanguageCatalog.allKnownLanguages() }

    fun log(message: String) {
        debugLog = (timestampedLog(message) + "\n" + debugLog)
            .lineSequence()
            .take(30)
            .joinToString("\n")
    }

    fun refresh() {
        log("Refresh requested. ${languagePackConnectivityStatus(context)}")
        RemoteModelManager.getInstance()
            .getDownloadedModels(TranslateRemoteModel::class.java)
            .addOnSuccessListener { models ->
                downloadedCodes = models.mapNotNull { it.language }
                    .map { MlKitLanguageCatalog.canonicalCode(it, it) }
                    .distinct()
                    .sortedBy { MlKitLanguageCatalog.label(it).lowercase() }
                    .joinToString(",")
                status = if (downloadedCodes.isBlank()) "No language packs downloaded." else "Language packs ready."
                log("Downloaded models: ${downloadedCodes.ifBlank { "none" }}")
            }
            .addOnFailureListener { error ->
                status = "Could not check language packs: ${error.message.orEmpty()}"
                log("Refresh failed: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
            }
    }

    fun download(code: String) {
        val canonical = MlKitLanguageCatalog.canonicalCode(code)
        if (canonical !in supportedCodes) {
            status = "${MlKitLanguageCatalog.label(code)} is not available in ML Kit translation."
            log("Download blocked: ${MlKitLanguageCatalog.label(code)} is not in the canonical ML Kit translation list.")
            return
        }
        busyCode = canonical
        busySeconds = 0
        status = "Downloading ${MlKitLanguageCatalog.label(canonical)}…"
        val model = TranslateRemoteModel.Builder(canonical).build()
        log("Download started via RemoteModelManager: ${MlKitLanguageCatalog.label(canonical)}. ${languagePackConnectivityStatus(context)}")
        RemoteModelManager.getInstance()
            .download(model, DownloadConditions.Builder().build())
            .addOnSuccessListener {
                busyCode = null
                status = "${MlKitLanguageCatalog.label(canonical)} downloaded."
                log("Download callback succeeded for ${MlKitLanguageCatalog.label(canonical)}.")
                refresh()
            }
            .addOnFailureListener { error ->
                busyCode = null
                status = "Download failed for ${MlKitLanguageCatalog.label(canonical)}: ${error.message.orEmpty()}"
                log("Download failed for ${MlKitLanguageCatalog.label(canonical)}: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
            }
    }

    fun delete(code: String) {
        val canonical = MlKitLanguageCatalog.canonicalCode(code)
        if (canonical !in supportedCodes) {
            status = "${MlKitLanguageCatalog.label(code)} is not available in ML Kit translation."
            log("Remove blocked: ${MlKitLanguageCatalog.label(code)} is not in the canonical ML Kit translation list.")
            return
        }
        busyCode = canonical
        busySeconds = 0
        status = "Removing ${MlKitLanguageCatalog.label(canonical)}…"
        log("Remove started: ${MlKitLanguageCatalog.label(canonical)}.")
        RemoteModelManager.getInstance()
            .deleteDownloadedModel(TranslateRemoteModel.Builder(canonical).build())
            .addOnSuccessListener {
                busyCode = null
                status = "${MlKitLanguageCatalog.label(canonical)} removed."
                log("Remove callback succeeded: ${MlKitLanguageCatalog.label(canonical)}.")
                refresh()
            }
            .addOnFailureListener { error ->
                busyCode = null
                status = "Remove failed for ${MlKitLanguageCatalog.label(canonical)}: ${error.message.orEmpty()}"
                log("Remove failed for ${MlKitLanguageCatalog.label(canonical)}: ${error.message.orEmpty().ifBlank { error.javaClass.simpleName }}")
            }
    }

    LaunchedEffect(Unit) { refresh() }
    LaunchedEffect(busyCode) {
        val active = busyCode ?: return@LaunchedEffect
        while (busyCode == active) {
            delay(1000)
            busySeconds += 1
            if (busySeconds > 0 && busySeconds % 10 == 0) {
                val diagnosis = if (busySeconds >= 120) {
                    "Likely blocked by network, Google Play Services, or model delivery."
                } else {
                    "Waiting for ML Kit callback."
                }
                log("Still waiting for ${MlKitLanguageCatalog.label(active)} download/remove callback at ${busySeconds}s. $diagnosis ${languagePackConnectivityStatus(context)}")
            }
        }
    }

    val downloaded = downloadedCodes.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
    val downloadedLanguages = allLanguages.filter { it.code in downloaded }
    val availableLanguages = allLanguages.filter { it.code !in downloaded && it.code in supportedCodes }
    val unsupportedLanguages = allLanguages.filter { it.code !in supportedCodes }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Shared by ML Kit translation capabilities. Translation is powered by Google ML Kit. Download before going off-grid.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = ::refresh, modifier = Modifier.fillMaxWidth()) { Text("Refresh language packs") }
        Spacer(Modifier.height(8.dp))
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        busyCode?.let { code ->
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                "Downloading ${MlKitLanguageCatalog.label(code)} · ${busySeconds}s elapsed. Keep this screen open.",
                style = MaterialTheme.typography.bodySmall
            )
            if (busySeconds >= 120) {
                Text(
                    "No ML Kit callback after 2 minutes. This usually means the device cannot reach Google’s model delivery service, or Google Play Services is unavailable/stuck.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text("Download log\n$debugLog", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }
        Spacer(Modifier.height(10.dp))
        Text("Downloaded", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        if (downloadedLanguages.isEmpty()) {
            Text("None yet.", style = MaterialTheme.typography.bodySmall)
        } else {
            downloadedLanguages.forEach { language ->
                LanguagePackRow(
                    label = "${language.label} · stored",
                    action = if (busyCode == language.code) "…" else "🗑",
                    enabled = busyCode == null,
                    onAction = { delete(language.code) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Tap to download", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        availableLanguages.forEach { language ->
            LanguagePackRow(
                label = language.label,
                action = if (busyCode == language.code) "…" else "↓",
                enabled = busyCode == null,
                onAction = { download(language.code) }
            )
        }
        if (unsupportedLanguages.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("Not available in ML Kit on this device", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            unsupportedLanguages.forEach { language ->
                LanguagePackRow(
                    label = language.label,
                    action = "—",
                    enabled = false,
                    onAction = {}
                )
            }
        }
    }
}

@Composable
private fun LanguagePackRow(
    label: String,
    action: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = onAction, enabled = enabled, modifier = Modifier.width(64.dp)) {
            Text(action)
        }
    }
}

@Composable
private fun DeviceRegistryCard(expandedByDefault: Boolean = false) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    var devices by remember { mutableStateOf(DeviceRegistry.all(context)) }
    var editorOpen by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(DeviceTransport.BLE.name) }
    var address by remember { mutableStateOf("") }
    var profile by remember { mutableStateOf("") }
    var credentialsRef by remember { mutableStateOf("") }
    var liveReadDevice by remember { mutableStateOf<RegisteredDevice?>(null) }
    var liveReadResult by remember { mutableStateOf<ExecutionResult?>(null) }

    fun refresh() { devices = DeviceRegistry.all(context) }
    fun openEditor(device: RegisteredDevice?) {
        editingId = device?.id.orEmpty(); name = device?.name.orEmpty(); transport = device?.transport?.name ?: DeviceTransport.BLE.name
        address = device?.address.orEmpty(); profile = device?.profile.orEmpty(); credentialsRef = device?.credentialsRef.orEmpty(); editorOpen = true
    }

    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Column(
                Modifier.fillMaxWidth().clickable {
                    if (!expanded) refresh()
                    expanded = !expanded
                }
            ) {
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
                                if (device.canReadAsSensor()) {
                                    OutlinedButton(onClick = { liveReadDevice = device; liveReadResult = null }) { Text("Read") }
                                    Spacer(Modifier.padding(3.dp))
                                }
                                OutlinedButton(onClick = { DeviceRegistry.setPaused(context, device.id, !device.paused); refresh() }) { Text(if (device.paused) "Resume" else "Pause") }
                                Spacer(Modifier.padding(3.dp))
                                OutlinedButton(onClick = { openEditor(device) }) { Text("Edit") }
                                Spacer(Modifier.padding(3.dp))
                                OutlinedButton(onClick = { DeviceRegistry.remove(context, device.id); refresh() }) { Text("Delete") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    OutlinedButton(onClick = { refresh() }, modifier = Modifier.weight(1f)) { Text("Refresh") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { openEditor(null) }, modifier = Modifier.weight(1f)) { Text("Add device") }
                }
            }
        }
    }

    liveReadDevice?.let { device ->
        val method = As100MethodRegistry.all().firstOrNull { it.id == As100SensorReadMethod.ID }
        val screen = MethodMeshModuleRegistry.screenFor(As100SensorReadMethod.ID)
        if (method != null && screen != null) {
            FullScreenCapabilityDialog(onDismiss = { liveReadDevice = null }) {
                Text("Live reading", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(device.name.ifBlank { device.id }, style = MaterialTheme.typography.bodyMedium)
                liveReadResult?.let { ResultPreview(it, "Latest sensor reading.") }
                DashboardCapabilityRunner(
                    method = method,
                    screen = screen,
                    saveOutput = false,
                    settingsJson = settingsJsonFromMap(
                        mapOf(
                            "device_id" to device.id,
                            "sensor_read_mode" to "single",
                            "device_match_policy" to "fallback"
                        )
                    ),
                    onConfirmed = { result -> liveReadResult = result },
                    onCancel = { liveReadDevice = null }
                )
            }
        } else {
            liveReadDevice = null
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

private fun RegisteredDevice.canReadAsSensor(): Boolean {
    val haystack = listOf(id, name, profile, address).joinToString(" ").lowercase()
    return transport == DeviceTransport.BLE && (
        haystack.contains("sensor") ||
            haystack.contains("aht20") ||
            haystack.contains("ld2410") ||
            haystack.contains("methodmesh")
        )
}

private fun timestampedLog(message: String): String =
    "${Instant.now()}  $message"

private fun languagePackConnectivityStatus(context: android.content.Context): String {
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val network = connectivity?.activeNetwork
    val capabilities = network?.let { connectivity.getNetworkCapabilities(it) }
    val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    val validated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    val transport = when {
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ethernet"
        capabilities != null -> "other"
        else -> "none"
    }
    val playServices = runCatching {
        val info = context.packageManager.getPackageInfo("com.google.android.gms", 0)
        "play_services=${info.versionName ?: info.longVersionCode.toString()}"
    }.getOrDefault("play_services=not_found")
    return "network=$transport internet=$hasInternet validated=$validated $playServices"
}
