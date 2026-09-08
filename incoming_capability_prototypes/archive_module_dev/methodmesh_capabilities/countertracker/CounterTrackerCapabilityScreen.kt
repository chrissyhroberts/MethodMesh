package com.example.methodmesh.modules.countertracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.*
import java.util.UUID

object CounterTrackerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CounterTrackerMethod.ID
    override val title = "Counter / Tracker"
    override val description = "Persistent interactive counters and status flags."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current.applicationContext
        val persistState = (context.action.settings["persist_state"] ?: context.action.settings["input_persist_state"])?.toBooleanStrictOrNull() ?: true
        val persistHistory = (context.action.settings["persist_history"] ?: context.action.settings["input_persist_history"])?.toBooleanStrictOrNull() ?: false
        val interactiveCapture = (context.action.settings["interactive_capture"] ?: context.action.settings["input_interactive_capture"])?.toBooleanStrictOrNull() ?: true
        val stateSaver = remember {
            Saver<CounterWorkspaceState, String>(
                save = { CounterTrackerRepository.encode(it).toString() },
                restore = { encoded -> runCatching { CounterTrackerRepository.decode(org.json.JSONObject(encoded)) }.getOrDefault(CounterWorkspaceState()) }
            )
        }
        var state by rememberSaveable(stateSaver = stateSaver) {
            mutableStateOf(if (persistState) CounterTrackerRepository.load(appContext) else CounterWorkspaceState())
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var menuOpen by rememberSaveable { mutableStateOf(false) }
        var addCounterOpen by rememberSaveable { mutableStateOf(false) }
        var addFlagOpen by rememberSaveable { mutableStateOf(false) }
        var newName by rememberSaveable { mutableStateOf("") }
        var newKind by rememberSaveable { mutableStateOf("tally") }
        var newValue by rememberSaveable { mutableStateOf("0") }
        var newStep by rememberSaveable { mutableStateOf("1") }
        var newMin by rememberSaveable { mutableStateOf("") }
        var newMax by rememberSaveable { mutableStateOf("") }

        fun commit(next: CounterWorkspaceState) {
            state = next
            result = null
            if (persistState) CounterTrackerRepository.save(appContext, next, persistHistory)
        }

        fun capture(): ExecutionResult {
            val snapshot = CounterTrackerRepository.encode(state).toString()
            val request = As100CounterTrackerMethod.request(
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf("snapshot_json" to snapshot),
                action = capabilityId, signals = emptyList(), inputs = emptyList()
            )
            return As100CounterTrackerMethod.result(request, As100CounterTrackerMethod.snapshotValues(mapOf("snapshot_json" to snapshot)), context.request.invocationContext)
                .also { result = it }
        }

        LaunchedEffect(context.presentationMode, interactiveCapture) {
            val supplied = context.action.settings["snapshot_json"] ?: context.action.settings["input_snapshot_json"]
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !interactiveCapture && !supplied.isNullOrBlank()) {
                val request = As100CounterTrackerMethod.request(
                    action = capabilityId,
                    context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf("snapshot_json" to supplied),
                    signals = emptyList(),
                    inputs = emptyList()
                )
                val execution = As100CounterTrackerMethod.result(
                    request,
                    As100CounterTrackerMethod.snapshotValues(mapOf("snapshot_json" to supplied)),
                    context.request.invocationContext
                )
                result = execution
                onConfirmed(execution)
            }
        }

        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (context.stepNumber > 1) TextButton(onClick = onBack) { Text("Back") }
                    TextButton(onClick = { menuOpen = true }) { Text("Menu") }
                    OutlinedButton(onClick = onCancel) { Text("Close") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.counters, key = { it.id }) { counter ->
                        ElevatedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(counter.name, style = MaterialTheme.typography.titleMedium)
                                        Text(counter.kind.uppercase(), style = MaterialTheme.typography.labelSmall)
                                    }
                                    Text(counter.value.toString(), style = MaterialTheme.typography.headlineMedium)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        val next = (counter.value - counter.step).let { v -> counter.minimum?.let { maxOf(v, it) } ?: v }
                                        commit(state.copy(counters = state.counters.map { if (it.id == counter.id) it.copy(value = next) else it }))
                                    }, modifier = Modifier.weight(1f)) { Text("−${counter.step}") }
                                    Button(onClick = {
                                        val next = (counter.value + counter.step).let { v -> counter.maximum?.let { minOf(v, it) } ?: v }
                                        commit(state.copy(counters = state.counters.map { if (it.id == counter.id) it.copy(value = next) else it }))
                                    }, modifier = Modifier.weight(1f)) { Text("+${counter.step}") }
                                    TextButton(onClick = { commit(state.copy(counters = state.counters.filterNot { it.id == counter.id })) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                    if (state.flags.isNotEmpty()) {
                        item { Text("Status", style = MaterialTheme.typography.titleMedium) }
                        items(state.flags, key = { it.id }) { flag ->
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(flag.name, Modifier.weight(1f))
                                    Switch(flag.value, onCheckedChange = { checked -> commit(state.copy(flags = state.flags.map { if (it.id == flag.id) it.copy(value = checked) else it })) })
                                    TextButton(onClick = { commit(state.copy(flags = state.flags.filterNot { it.id == flag.id })) }) { Text("Remove") }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { newName = ""; addCounterOpen = true }, modifier = Modifier.weight(1f)) { Text("Add counter") }
                    OutlinedButton(onClick = { newName = ""; addFlagOpen = true }, modifier = Modifier.weight(1f)) { Text("Add flag") }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { onConfirmed(capture()) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (context.isNativePresetRun) "Finish with snapshot" else "Use this snapshot")
                }
            }

            if (menuOpen) {
                ModalOverlay(onDismiss = { menuOpen = false }) {
                    Text("Workspace", style = MaterialTheme.typography.titleLarge)
                    Text("Changes are ${if (persistState) "persisted locally" else "session-only"}. History is ${if (persistHistory) "enabled" else "disabled"}.")
                    if (persistHistory) Text("History entries: ${CounterTrackerRepository.history(appContext).length()}")
                    OutlinedButton(onClick = { CounterTrackerRepository.clearHistory(appContext) }, modifier = Modifier.fillMaxWidth(), enabled = persistHistory) { Text("Clear history") }
                    Button(onClick = { menuOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Back") }
                }
            }
            if (addCounterOpen) {
                ModalOverlay(onDismiss = { addCounterOpen = false }) {
                    Text("Add counter", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newValue, { newValue = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Starting value") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newStep, { newStep = it.filter(Char::isDigit) }, label = { Text("Increment/decrement") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newMin, { newMin = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Minimum (optional)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(newMax, { newMax = it.filter { c -> c.isDigit() || c == '-' } }, label = { Text("Maximum (optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth()) {
                        listOf("tally", "hp", "exp", "resource").forEach { kind ->
                            FilterChip(selected = newKind == kind, onClick = { newKind = kind }, label = { Text(kind.uppercase()) }, modifier = Modifier.padding(2.dp))
                        }
                    }
                    Button(onClick = {
                        val item = CounterItem(UUID.randomUUID().toString(), newName.ifBlank { "Counter ${state.counters.size + 1}" }, newValue.toLongOrNull() ?: 0, (newStep.toLongOrNull() ?: 1).coerceAtLeast(1), newMin.toLongOrNull(), newMax.toLongOrNull(), newKind)
                        commit(state.copy(counters = state.counters + item)); addCounterOpen = false
                    }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
                }
            }
            if (addFlagOpen) {
                ModalOverlay(onDismiss = { addFlagOpen = false }) {
                    Text("Add status flag", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(newName, { newName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { commit(state.copy(flags = state.flags + StatusFlag(UUID.randomUUID().toString(), newName.ifBlank { "Flag ${state.flags.size + 1}" }, false))); addFlagOpen = false }, modifier = Modifier.fillMaxWidth()) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun ModalOverlay(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f), onClick = onDismiss) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ElevatedCard(onClick = {}, modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
        }
    }
}
