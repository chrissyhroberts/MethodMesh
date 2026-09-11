package com.example.methodmesh.modules.time_tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.modules.time_tools.notifications.ActiveTimerKind
import com.example.methodmesh.modules.time_tools.notifications.TimeToolsTimerRuntime
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay

object TimeToolsDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DashboardMethod.ID
    override val title = "Time & Alarms"
    override val description = "Manage active timers and launch every time capability."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val android = LocalContext.current
        val runtime = remember(android) { TimeToolsTimerRuntime(android) }
        var selectedMethodId by rememberSaveable { mutableStateOf<String?>(null) }

        val selectedMethod = selectedMethodId
        if (selectedMethod != null) {
            val screen = timeToolScreen(selectedMethod)
            if (screen != null) {
                val action = remember(selectedMethod) {
                    ExternalActionRequest(
                        requestedId = selectedMethod,
                        canonicalId = selectedMethod,
                        settings = emptyMap()
                    )
                }
                val request = remember(selectedMethod, context.request.invocationContext) {
                    ExternalWorkflowRequest(
                        actions = listOf(action),
                        invocationContext = context.request.invocationContext,
                        returns = emptyList(),
                        returnMode = ReturnMode.Json,
                        settings = emptyMap(),
                        source = "dashboard"
                    )
                }
                screen.Render(
                    context = CapabilityScreenContext(action, request, 1, 1),
                    onBack = { selectedMethodId = null },
                    onConfirmed = { selectedMethodId = null },
                    onCancel = { selectedMethodId = null }
                )
                return
            }
        }

        var records by remember { mutableStateOf(runtime.allRecords()) }
        var liveTick by remember { mutableLongStateOf(0L) }
        LaunchedEffect(runtime) {
            while (true) {
                records = runtime.allRecords()
                liveTick += 1L
                delay(250L)
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text("Time & Alarms", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Light)
            Text("Active now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
            if (records.isEmpty()) Text("Nothing running", color = MaterialTheme.colorScheme.onSurfaceVariant)
            val refreshTick = liveTick
            records.forEach { record ->
                // The durable record is intentionally immutable while a timer runs.
                // Key the live projection to an explicit UI tick so Compose redraws
                // remaining/elapsed time even when repository contents are unchanged.
                val snap = remember(record.id, refreshTick) { runtime.snapshot(record.id) ?: runtime.snapshot(record) }
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) { Text(record.label, style = MaterialTheme.typography.titleMedium); Text(snap.formatted, style = MaterialTheme.typography.headlineSmall) }
                            Text(record.kind.name.lowercase().replace('_',' '), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            when (record.kind) {
                                ActiveTimerKind.STOPWATCH -> { OutlinedButton({ runtime.lap(record.id) }) { Text("Lap") }; Button({ if (record.paused) runtime.resume(record.id) else runtime.pause(record.id) }) { Text(if(record.paused)"Resume" else "Pause") } }
                                ActiveTimerKind.COUNTDOWN, ActiveTimerKind.INTERVAL -> { OutlinedButton({ runtime.addMinute(record.id) }) { Text("+1:00") }; Button({ if (record.paused) runtime.resume(record.id) else runtime.pause(record.id) }) { Text(if(record.paused)"Resume" else "Pause") } }
                                else -> Unit
                            }
                            OutlinedButton({ runtime.cancel(record.id) }) { Text(if(record.kind==ActiveTimerKind.ALARM)"Disable" else "Stop") }
                        }
                    }
                }
            }
            Text("Start something", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            TimeToolsDashboardScreen.items.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item ->
                        Card(Modifier.weight(1f).padding(vertical = 5.dp).clickable { selectedMethodId = item.methodId }, shape = RoundedCornerShape(26.dp)) {
                            Column(Modifier.padding(18.dp).heightIn(min = 96.dp)) { Text(item.label, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(6.dp)); Text(item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            OutlinedButton(onBack, Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Done") }
        }
    }

    private fun timeToolScreen(methodId: String): CapabilityScreenSpec? = when (methodId) {
        As100CountdownMethod.ID -> CountdownMethodMeshCapabilityScreen
        As100StopwatchMethod.ID -> StopwatchMethodMeshCapabilityScreen
        As100AlarmMethod.ID -> AlarmMethodMeshCapabilityScreen
        As100IntervalMethod.ID -> IntervalMethodMeshCapabilityScreen
        As100UntilMethod.ID -> UntilMethodMeshCapabilityScreen
        As100ElapsedMethod.ID -> ElapsedMethodMeshCapabilityScreen
        As100DurationCalculateMethod.ID -> DurationCalculatorMethodMeshCapabilityScreen
        else -> null
    }
}
