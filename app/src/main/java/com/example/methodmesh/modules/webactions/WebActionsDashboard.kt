package com.example.methodmesh.modules.webactions

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.onlinedata.LocationDisclosureMode
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.android.ExternalWorkflowActivity
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object As100WebActionsDashboardMethod : As100Method {
    const val ID = "webactions.dashboard"
    private const val VERSION = "0.2.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Web Actions dashboard")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Web Actions dashboard", version = VERSION,
        description = "Fetch bundled online data streams in one dashboard.",
        graphOutputs = listOf("webactions.dashboard"),
        parameters = mapOf("category" to "Web tools", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Succeeded)
}

object As100WebActionsWorkflowsDashboardMethod : As100Method {
    const val ID = "webactions.workflows_dashboard"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Web workflows dashboard")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Workflow,
        name = "Web workflows dashboard", version = VERSION,
        description = "Choose an ODK form, Enketo session, web workflow, or browser page.",
        graphOutputs = listOf("webactions.workflows_dashboard"),
        parameters = mapOf("category" to "Web tools", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        As100ExecutionEngine.complete(request, TransformationStatus.Succeeded)
}

object WebActionsDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100WebActionsDashboardMethod.ID
    override val title = "Web Actions"
    override val description = "Fetch a complete online data stream from one page."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val methods = remember { WebApiMethods.all }
        var selectedId by rememberSaveable { mutableStateOf(methods.firstOrNull()?.id.orEmpty()) }
        val selected = methods.firstOrNull { it.id == selectedId } ?: methods.first()
        val inputs = remember { mutableStateMapOf<String, String>() }
        var locationMode by rememberSaveable { mutableStateOf("manual") }
        var locationStatus by rememberSaveable { mutableStateOf("") }
        var menuOpen by remember { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Choose a data stream, set its inputs, then fetch.") }
        var running by rememberSaveable { mutableStateOf(false) }
        val hasLocation = selected.definition.inputs.any { it.id == "latitude" } && selected.definition.inputs.any { it.id == "longitude" }

        LaunchedEffect(selected.id) {
            inputs.clear()
            selected.definition.inputs.forEach { input -> inputs[input.id] = input.defaultValue }
            locationMode = "manual"
            locationStatus = ""
            result = null
            status = "Ready to fetch ${selected.definition.name}."
        }

        var acquireGps: () -> Unit = {}
        val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) acquireGps()
            else { locationMode = "manual"; locationStatus = "Location permission was not granted; enter coordinates manually." }
        }
        acquireGps = {
            if (hasLocation) {
                locationStatus = "Getting a current GPS fix…"
                LocationServices.getFusedLocationProviderClient(androidContext)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
                    .addOnSuccessListener { location ->
                        if (location == null) { locationMode = "manual"; locationStatus = "No current GPS fix was available." }
                        else {
                            inputs["latitude"] = location.latitude.toString()
                            inputs["longitude"] = location.longitude.toString()
                            locationMode = "gps"
                            locationStatus = "GPS fix ready: %.5f, %.5f".format(location.latitude, location.longitude)
                        }
                    }
                    .addOnFailureListener { error -> locationMode = "manual"; locationStatus = error.message ?: "GPS lookup failed." }
            }
        }

        fun fetch() {
            if (hasLocation && (inputs["latitude"].orEmpty().toDoubleOrNull() == null || inputs["longitude"].orEmpty().toDoubleOrNull() == null)) {
                status = "Choose GPS or enter a valid latitude and longitude."
                return
            }
            running = true
            status = "Fetching ${selected.definition.name}…"
            scope.launch {
                val execution = withContext(Dispatchers.IO) {
                    selected.execute(selected.request(context = context.request.invocationContext.asMap(selected.id) + inputs.toMap()))
                }
                result = execution
                running = false
                val fields = OutputFormatter.fields(execution, includeProvenance = false)
                status = fields[WebApiFields.ERROR]?.toString()?.takeIf { it.isNotBlank() } ?: "Data ready."
            }
        }

        val preview = result?.let { execution ->
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            linkedMapOf<String, Any?>(
                "Data" to fields[WebApiFields.VALUE],
                "Provider" to fields[WebApiFields.PROVIDER],
                "Updated" to fields[WebApiFields.RETRIEVED_TIME_ISO],
                "Status" to fields[WebApiFields.STATUS]
            ).filterValues { it?.toString().orEmpty().isNotBlank() }
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context,
            canGoBack = context.stepNumber > 1, capturedResult = result, resultPreview = preview,
            onBack = onBack, onRetry = { fetch() }, onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Select one function, choose GPS or enter coordinates where needed, then fetch. The complete response remains available through the normal FULL JSON sidecar.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Box {
                OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) { Text(selected.definition.name) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    methods.forEach { method ->
                        DropdownMenuItem(text = { Text(method.definition.name) }, onClick = { selectedId = method.id; menuOpen = false })
                    }
                }
            }
            Text(selected.definition.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Spacer(Modifier.height(10.dp))
            if (selected.definition.privacy.sendsLocation) {
                val precision = when (selected.definition.privacy.locationMode) {
                    LocationDisclosureMode.ROUNDED -> "rounded to about ${selected.definition.privacy.roundedLocationRadiusMeters / 1_000} km"
                    LocationDisclosureMode.EXACT -> "exact"
                    else -> "manually supplied"
                }
                Text("This request sends $precision location to ${selected.definition.attribution.providerName}.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
            }
            if (hasLocation) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        locationMode = "gps"
                        val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (fine || coarse) acquireGps() else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }, modifier = Modifier.weight(1f)) { Text(if (locationMode == "gps") "✓ Use GPS" else "Use GPS") }
                    OutlinedButton(onClick = { locationMode = "manual" }, modifier = Modifier.weight(1f)) { Text(if (locationMode == "manual") "✓ Manual" else "Enter manually") }
                }
                if (locationMode == "manual") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("latitude", "longitude").forEach { id ->
                            val input = selected.definition.inputs.first { it.id == id }
                            OutlinedTextField(inputs[id].orEmpty(), { inputs[id] = it }, label = { Text(input.name) }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                    }
                }
                if (locationStatus.isNotBlank()) Text(locationStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            selected.definition.inputs.filterNot { hasLocation && it.id in setOf("latitude", "longitude") }.forEach { input ->
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(inputs[input.id].orEmpty(), { inputs[input.id] = it }, label = { Text(input.name) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { fetch() }, enabled = !running, modifier = Modifier.fillMaxWidth()) { Text(if (running) "Fetching…" else "Fetch data") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

object WebActionsWorkflowsDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100WebActionsWorkflowsDashboardMethod.ID
    override val title = "Web workflows"
    override val description = "Choose an ODK form, Enketo session, web workflow, or browser page."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current
        var query by rememberSaveable { mutableStateOf("") }
        val methods = remember { WebActionsModule.as100Methods().filter { !it.id.startsWith("web.api.") && it.id !in setOf(As100WebActionsDashboardMethod.ID, capabilityId) } }
        val filtered = methods.filter { method -> query.isBlank() || listOf(method.descriptor.name, method.id, method.descriptor.description.orEmpty()).any { it.contains(query.trim(), true) } }
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("WEB WORKFLOWS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text("Forms and browser actions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("These actions open their own explicit workflow surface and completion controls.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(query, { query = it }, label = { Text("Find a workflow") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                items(filtered, key = { it.id }) { method ->
                    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(method.descriptor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { openWorkflow(app, method) }) { Text("Open") }
                            }
                            Text(method.descriptor.description.orEmpty(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Close") } }
            }
        }
    }

    private fun openWorkflow(context: android.content.Context, method: As100Method) {
        val runtimeFields = method.descriptor.inputs.joinToString("|")
        context.startActivity(Intent(context, ExternalWorkflowActivity::class.java).apply {
            action = "com.example.methodmesh.EXECUTE_METHOD(method_id='${method.id}',caller='webactions_dashboard',source='dashboard',input_methodmesh_dashboard='true',input_payload_mode='FULL',return_mode='flat',methodmesh_native_preset_run='true',methodmesh_runtime_fields='$runtimeFields')"
        })
    }
}
