package com.example.researchos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.calibration.CalibrationScreen
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodRegistry
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.ResearchOSModuleRegistry
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.ui.components.MethodCard
import com.example.researchos.ui.sensors.SensorDashboard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val modules = ResearchOSModuleRegistry.all()
    val methods = As100MethodRegistry.all().distinctBy { it.id }
    val legacyMethods = MethodRegistry.all().distinctBy { it.manifest.id }

    Scaffold(
        topBar = { TopAppBar(title = { Text("ResearchOS Runtime") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            item { RuntimeSummaryCard(modules.size, methods.size, legacyMethods.size) }
            item { CapabilityRegistryCard(methods, modules) }
            item { RuntimeStateCard() }
            item { DeviceServicesCard() }
            item { LegacyCompatibilityCard(legacyMethods) }
        }
    }
}

@Composable
private fun RuntimeSummaryCard(moduleCount: Int, methodCount: Int, legacyCount: Int) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        elevation = CardDefaults.elevatedCardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("ResearchOS runtime", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                text = "The dashboard now follows the canonical capability registry. Modules register methods, focused screens, RIL bindings and examples once; legacy cards are kept only as a collapsed compatibility surface.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Modules: $moduleCount • canonical methods: $methodCount • legacy UI shells: $legacyCount",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
private fun CapabilityRegistryCard(methods: List<As100Method>, modules: List<ResearchOSModule>) {
    var expanded by remember { mutableStateOf(true) }
    val screenMap = ResearchOSModuleRegistry.capabilityScreens().associateBy { it.capabilityId }
    val moduleByMethod = modules.flatMap { module -> module.as100Methods().map { it.id to module } }.toMap()

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
                text = "This is the single source of truth for dashboard execution: one row per canonical AS method, with any focused capability screen rendered here.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )

            if (expanded) {
                Spacer(Modifier.height(12.dp))
                methods.sortedBy { it.id }.forEach { method ->
                    CapabilityCard(
                        method = method,
                        module = moduleByMethod[method.id],
                        screen = screenMap[method.id]
                    )
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
    var expanded by remember(method.id) { mutableStateOf(false) }
    var runnerOpen by remember(method.id) { mutableStateOf(false) }
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
                    lastResult?.let { result ->
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
    Spacer(Modifier.height(8.dp))
    Text("Last confirmed result: ${result.status.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
    fields.entries.take(10).forEach { (key, value) ->
        Text("$key = ${value?.toString().orEmpty()}", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
    }
    if (fields.size > 10) Text("… ${fields.size - 10} more fields", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun RuntimeStateCard() {
    var expanded by remember { mutableStateOf(false) }
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
                Text("Entities: ${graph.entities.size}")
                Text("AS observations: ${graph.asObservations.size}")
                Text("Transformations: ${graph.transformations.size}")
                Text("Legacy observations: ${graph.observations.size}")
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
    var calibrationExpanded by remember { mutableStateOf(false) }
    var signalsExpanded by remember { mutableStateOf(false) }

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
private fun LegacyCompatibilityCard(legacyMethods: List<Method>) {
    var expanded by remember { mutableStateOf(false) }
    val byCategory = legacyMethods.groupBy { it.manifest.category }
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
                    text = if (expanded) "▼ Legacy UI shells" else "▶ Legacy UI shells",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(legacyMethods.size.toString(), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Collapsed by default to prevent duplicate presentation of the same capability. Use only while migrating old demos/settings panels.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
            if (expanded) {
                byCategory.entries.sortedBy { it.key.name }.forEach { (category, methods) ->
                    LegacyCategory(category, methods)
                }
            }
        }
    }
}

@Composable
private fun LegacyCategory(category: MethodCategory, methods: List<Method>) {
    var expanded by remember(category) { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(if (expanded) "▼ ${category.name}" else "▶ ${category.name}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(methods.size.toString())
    }
    if (expanded) {
        itemsForLegacy(methods)
    }
}

@Composable
private fun itemsForLegacy(methods: List<Method>) {
    methods.sortedBy { it.manifest.id }.forEach { method ->
        MethodCard(method = method, modifier = Modifier.padding(top = 12.dp))
    }
}
