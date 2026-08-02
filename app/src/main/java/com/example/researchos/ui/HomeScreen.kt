package com.example.researchos.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import com.example.researchos.calibration.CalibrationScreen
import com.example.researchos.core.scheduling.ResearchSchedule
import com.example.researchos.core.scheduling.SchedulerCenterCard
import com.example.researchos.core.scheduling.SchedulerEditorHost
import com.example.researchos.core.scheduling.SchedulerRepository
import com.example.researchos.core.scheduling.SchedulerExportCapabilityScreen
import com.example.researchos.core.scheduling.SchedulerTransferCapabilityScreen
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.ResearchOSModuleRegistry
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.OutputExportRepository
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.ui.sensors.SensorDashboard
import com.example.researchos.platform.devices.DeviceRegistry
import com.example.researchos.platform.devices.DeviceTransport
import com.example.researchos.platform.devices.RegisteredDevice

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
            item { CapabilityRegistryCard(methods, modules) }
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            OutputExportRepository.setConfiguredFolder(context, uri)
            configured = uri.toString()
        }
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Text(if (expanded) "▼ Output storage" else "▶ Output storage", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Text("Direct-run exports include a timestamped JSON file and any returned attachments.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(if (configured.isBlank()) "Using default app Documents/ResearchOS/outputs folder" else "Selected folder: $configured", style = MaterialTheme.typography.labelSmall)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Button(onClick = { picker.launch(null) }) { Text("Choose folder") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { OutputExportRepository.setConfiguredFolder(context, null); configured = "" }) { Text("Use default") }
                }
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
private fun CapabilityRegistryCard(methods: List<As100Method>, modules: List<ResearchOSModule>) {
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
                                    screen = screenMap[method.id]
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
    screen: CapabilityScreenSpec?
) {
    var expanded by rememberSaveable(method.id) { mutableStateOf(false) }
    var runnerOpen by rememberSaveable(method.id) { mutableStateOf(false) }
    var lastResult by remember(method.id) { mutableStateOf<ExecutionResult?>(null) }

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
                Text(if (screen == null) "generic" else "screen", style = MaterialTheme.typography.labelMedium)
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
                Row {
                    Button(onClick = { runnerOpen = !runnerOpen }) {
                        Text(if (runnerOpen) "Hide runner" else "Open runner")
                    }
                    Spacer(Modifier.padding(4.dp))
                    if (lastResult != null) {
                        OutlinedButton(onClick = { lastResult = null }) { Text("Clear result") }
                    }
                }
                lastResult?.let { ResultPreview(it) }
                if (runnerOpen) {
                    Spacer(Modifier.height(12.dp))
                    DashboardCapabilityRunner(
                        method = method,
                        screen = screen,
                        onConfirmed = { result ->
                            lastResult = ResearchRuntime.session.record(result)
                            runnerOpen = false
                        },
                        onCancel = { runnerOpen = false }
                    )
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
private fun DashboardCapabilityRunner(
    method: As100Method,
    screen: CapabilityScreenSpec?,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val action = ExternalActionRequest(requestedId = method.id, canonicalId = method.id)
    val request = ExternalWorkflowRequest(
        actions = listOf(action),
        invocationContext = InvocationContext(caller = "dashboard", entityType = "participant", entityId = "P001"),
        returns = emptyList(),
        returnMode = ReturnMode.Json,
        source = "dashboard"
    )
    val context = CapabilityScreenContext(
        action = action,
        request = request,
        stepNumber = 1,
        totalSteps = 1
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
