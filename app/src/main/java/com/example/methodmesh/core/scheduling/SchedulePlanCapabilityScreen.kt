package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Duration
import java.time.LocalTime

object SchedulePlanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Create schedule plan"
    override val description = "Create a reusable schedule of notifications and saved presets."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        var name by remember { mutableStateOf("") }
        var laneName by remember { mutableStateOf("Activity") }
        var activation by remember { mutableStateOf(ScheduleActivation.MANUAL_DAY_ONE) }
        var endMode by remember { mutableStateOf(ScheduleEndMode.DURATION) }
        var durationDays by remember { mutableStateOf("22") }
        var days by remember { mutableStateOf("1") }
        var time by remember { mutableStateOf("09:00") }
        var actionType by remember { mutableStateOf(ScheduleActionType.NOTIFIER) }
        var message by remember { mutableStateOf("") }
        var selectedPreset by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Build the plan, then save it.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val presets = remember { ProtocolLibraryRepository.presets(app) }

        fun save() {
            val parsedTime = runCatching { LocalTime.parse(time.trim()) }.getOrNull()
            val parsedDays = days.split(',', ' ', ';').mapNotNull { it.trim().toIntOrNull() }.filter { it > 0 }.toSet()
            val duration = durationDays.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofDays)
            if (name.isBlank() || parsedTime == null || parsedDays.isEmpty() || (endMode == ScheduleEndMode.DURATION && duration == null)) {
                status = "Enter a name, time, at least one day, and a valid ending."; return
            }
            if (actionType == ScheduleActionType.PRESET && selectedPreset.isBlank()) { status = "Choose a preset."; return }
            val action = if (actionType == ScheduleActionType.NOTIFIER) ScheduleAction(type = actionType, title = laneName, message = message.ifBlank { laneName }) else ScheduleAction(type = actionType, presetId = selectedPreset, title = presets.firstOrNull { it.id == selectedPreset }?.name.orEmpty())
            val lane = ScheduleLane(name = laneName.ifBlank { "Activity" }, defaultTime = parsedTime, defaultActions = listOf(action))
            val timing = if (activation == ScheduleActivation.MANUAL_DAY_ONE) ScheduleTimingRule.RelativeDays(parsedDays, parsedTime) else ScheduleTimingRule.Weekly(parsedDays.map { ((it - 1) % 7) + 1 }.toSet(), parsedTime)
            val termination = if (endMode == ScheduleEndMode.FOREVER) ScheduleTermination(ScheduleEndMode.FOREVER) else ScheduleTermination(ScheduleEndMode.DURATION, duration = duration)
            val plan = SchedulePlan(name = name.trim(), activation = activation, termination = termination, lanes = listOf(lane), rules = listOf(ScheduleRule(lane.id, timing)))
            SchedulePlanStore.savePlan(app, plan)
            if (activation != ScheduleActivation.MANUAL_DAY_ONE) SchedulePlanRuntime.start(app, plan)
            status = "Saved ${plan.name}."
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()), SchedulerOutcome(null, "created"), context.request.invocationContext)
            result = execution
            if (context.submitsImmediately) onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Text("A plan is reusable. Starting it later creates a separate running instance.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(laneName, { laneName = it }, label = { Text("Lane / activity") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Activation: ${activation.name.replace('_', ' ')}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { activation = ScheduleActivation.MANUAL_DAY_ONE }) { Text("Manual Day 1") }
                OutlinedButton(onClick = { activation = ScheduleActivation.CALENDAR_RULE }) { Text("Calendar rule") }
            }
            OutlinedTextField(days, { days = it }, label = { Text(if (activation == ScheduleActivation.MANUAL_DAY_ONE) "Days (e.g. 1,5,10)" else "Weekdays (1=Mon, 7=Sun)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(time, { time = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Ending: ${endMode.name.replace('_', ' ')}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { endMode = ScheduleEndMode.DURATION }) { Text("Duration") }
                OutlinedButton(onClick = { endMode = ScheduleEndMode.FOREVER }) { Text("Forever") }
            }
            if (endMode == ScheduleEndMode.DURATION) OutlinedTextField(durationDays, { durationDays = it }, label = { Text("Duration in days") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Action: ${actionType.name}", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actionType = ScheduleActionType.NOTIFIER }) { Text("Notifier") }
                OutlinedButton(onClick = { actionType = ScheduleActionType.PRESET }) { Text("Preset") }
            }
            if (actionType == ScheduleActionType.NOTIFIER) {
                OutlinedTextField(message, { message = it }, label = { Text("Reminder message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            } else {
                presets.take(12).forEach { preset -> OutlinedButton(onClick = { selectedPreset = preset.id }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedPreset == preset.id) "✓ ${preset.name}" else preset.name) } }
            }
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save plan") }
            Text(status, style = MaterialTheme.typography.bodySmall)
        }
    }
}
