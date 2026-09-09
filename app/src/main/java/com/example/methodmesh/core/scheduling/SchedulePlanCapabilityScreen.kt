package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Button
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.NumberPicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Duration
import java.time.LocalTime

private data class BuilderLane(val name: String, val hour: Int = 9, val minute: Int = 0, val actionType: ScheduleActionType = ScheduleActionType.NOTIFIER, val message: String = "", val presetId: String = "", val snoozeMinutes: Int = 10, val followUpCount: Int = 0, val followUpIntervalMinutes: Int = 30, val days: Set<Int> = setOf(1)) {
    val time: String get() = "%02d:%02d".format(hour, minute)
}

object SchedulePlanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Composite timed sequence"
    override val description = "Paint a reusable schedule as a set of timed swimlanes."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val existingPlan = remember { context.request.settings["schedule_plan_id"]?.let { SchedulePlanStore.plan(app, it) } }
        var name by remember { mutableStateOf(existingPlan?.name.orEmpty()) }
        var sequenceDays by remember { mutableStateOf(existingPlan?.rules?.flatMap { (it.timing as? ScheduleTimingRule.RelativeDays)?.days.orEmpty() }?.maxOrNull()?.coerceAtLeast(1)?.toString() ?: "22") }
        var durationDays by remember { mutableStateOf(existingPlan?.termination?.duration?.toDays()?.toString() ?: "22") }
        val lanes = remember {
            mutableStateListOf<BuilderLane>().apply {
                if (existingPlan == null) add(BuilderLane("Activity"))
                else existingPlan.rules.forEach { rule ->
                    val lane = existingPlan.lanes.firstOrNull { it.id == rule.laneId } ?: return@forEach
                    val timing = rule.timing as? ScheduleTimingRule.RelativeDays
                    val action = lane.defaultActions.firstOrNull()
                    add(BuilderLane(lane.name, lane.defaultTime.hour, lane.defaultTime.minute, action?.type ?: ScheduleActionType.NOTIFIER, action?.message.orEmpty(), action?.presetId.orEmpty(), action?.snoozeMinutes ?: 10, action?.followUpCount ?: 0, action?.followUpIntervalMinutes ?: 30, timing?.days ?: setOf(1)))
                }
            }
        }
        var status by remember { mutableStateOf("Tap cells to paint each swimlane.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var timeDialogLane by remember { mutableStateOf<Int?>(null) }
        var dialogHour by remember { mutableStateOf(9) }
        var dialogMinute by remember { mutableStateOf(0) }
        var expandedLanes by remember { mutableStateOf(emptySet<Int>()) }
        val presets = remember { ProtocolLibraryRepository.presets(app) }
        val days = sequenceDays.toIntOrNull()?.coerceIn(1, 366) ?: 22
        val gridScroll = rememberScrollState()
        val bodyScroll = rememberScrollState()

        fun save() {
            val totalDays = sequenceDays.toIntOrNull()?.takeIf { it in 1..366 }
            val duration = durationDays.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofDays)
            if (name.isBlank() || totalDays == null || duration == null) { status = "Enter a plan name, sequence length, and duration."; return }
            val built = lanes.mapNotNull { lane ->
                val time = LocalTime.of(lane.hour, lane.minute)
                val action = when {
                    lane.actionType == ScheduleActionType.NOTIFIER -> ScheduleAction(type = ScheduleActionType.NOTIFIER, title = lane.name, message = lane.message.ifBlank { lane.name }, snoozeMinutes = lane.snoozeMinutes, followUpCount = lane.followUpCount, followUpIntervalMinutes = lane.followUpIntervalMinutes)
                    lane.presetId.isNotBlank() -> ScheduleAction(type = ScheduleActionType.PRESET, presetId = lane.presetId, title = presets.firstOrNull { it.id == lane.presetId }?.name.orEmpty(), snoozeMinutes = lane.snoozeMinutes, followUpCount = lane.followUpCount, followUpIntervalMinutes = lane.followUpIntervalMinutes)
                    else -> null
                } ?: return@mapNotNull null
                val scheduleLane = ScheduleLane(name = lane.name.ifBlank { "Activity" }, defaultTime = time, defaultActions = listOf(action))
                scheduleLane to ScheduleTimingRule.RelativeDays(lane.days.filter { it <= totalDays }.toSet().ifEmpty { setOf(1) }, time)
            }
            if (built.size != lanes.size) { status = "Every lane needs a valid time and action."; return }
            val planLanes = built.map { it.first }
            val plan = SchedulePlan(id = existingPlan?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), activation = ScheduleActivation.MANUAL_DAY_ONE, termination = ScheduleTermination(ScheduleEndMode.DURATION, duration = duration), lanes = planLanes, rules = built.mapIndexed { index, pair -> ScheduleRule(planLanes[index].id, pair.second) }, createdAt = existingPlan?.createdAt ?: java.time.ZonedDateTime.now(), updatedAt = java.time.ZonedDateTime.now(), version = (existingPlan?.version ?: 0) + 1)
            SchedulePlanStore.savePlan(app, plan)
            status = "Saved ${plan.name}. Use Start Day 1 when you are ready."
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()), SchedulerOutcome(null, "created"), context.request.invocationContext)
            result = execution
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(Modifier.fillMaxSize().verticalScroll(bodyScroll).imePadding().padding(8.dp)) {
                Text("Composite Timed Sequence", style = MaterialTheme.typography.headlineSmall)
                Text("Paint the plan first. Each row is a swimlane and each square is a scheduled occurrence.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Plan name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(sequenceDays, { sequenceDays = it.filter(Char::isDigit) }, label = { Text("Days") }, modifier = Modifier.width(120.dp), singleLine = true)
                    OutlinedTextField(durationDays, { durationDays = it.filter(Char::isDigit) }, label = { Text("Ends after days") }, modifier = Modifier.width(170.dp), singleLine = true)
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
                    Text("SWIMLANE", modifier = Modifier.width(140.dp).padding(4.dp), fontSize = 11.sp)
                    (1..days).forEach { day -> Text(day.toString(), modifier = Modifier.width(38.dp).padding(4.dp), fontSize = 11.sp) }
                }
                lanes.forEachIndexed { index, lane ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(140.dp).padding(end = 4.dp)) {
                                OutlinedTextField(lane.name, { lanes[index] = lane.copy(name = it) }, label = { Text("Lane") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                OutlinedButton(onClick = { dialogHour = lane.hour; dialogMinute = lane.minute; timeDialogLane = index }, modifier = Modifier.fillMaxWidth()) { Text(lane.time) }
                            }
                            (1..days).forEach { day ->
                                val selected = day in lane.days
                                Box(Modifier.width(38.dp).height(62.dp).padding(2.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.TextButton(onClick = { val next = lane.days.toMutableSet().apply { if (!remove(day)) add(day) }; lanes[index] = lane.copy(days = next) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(day.toString(), fontSize = 9.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant); Text(if (selected) "■" else "·", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { lanes.add(index + 1, lane.copy(hour = (lane.hour + 4) % 24)) }) { Text("+ Add run") }
                            OutlinedButton(onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.NOTIFIER) }) { Text(if (lane.actionType == ScheduleActionType.NOTIFIER) "✓ Notifier" else "Notifier") }
                            OutlinedButton(onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.PRESET) }) { Text(if (lane.actionType == ScheduleActionType.PRESET) "✓ Preset" else "Preset") }
                            OutlinedButton(onClick = { lanes.removeAt(index); expandedLanes = expandedLanes.filter { it != index }.map { if (it > index) it - 1 else it }.toSet() }) { Text("Remove lane / run") }
                        }
                        OutlinedButton(onClick = { expandedLanes = if (index in expandedLanes) expandedLanes - index else expandedLanes + index }) { Text(if (index in expandedLanes) "Collapse details" else "Expand details") }
                        if (index in expandedLanes) {
                            if (lane.actionType == ScheduleActionType.NOTIFIER) OutlinedTextField(lane.message, { lanes[index] = lane.copy(message = it) }, label = { Text("Reminder message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            else presets.take(8).forEach { preset -> OutlinedButton(onClick = { lanes[index] = lane.copy(presetId = preset.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (lane.presetId == preset.id) "✓ ${preset.name}" else preset.name) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(lane.snoozeMinutes.toString(), { lanes[index] = lane.copy(snoozeMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Snooze min") }, modifier = Modifier.width(120.dp), singleLine = true)
                                OutlinedTextField(lane.followUpCount.toString(), { lanes[index] = lane.copy(followUpCount = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Follow-ups") }, modifier = Modifier.width(120.dp), singleLine = true)
                                OutlinedTextField(lane.followUpIntervalMinutes.toString(), { lanes[index] = lane.copy(followUpIntervalMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Repeat min") }, modifier = Modifier.width(120.dp), singleLine = true)
                            }
                        }
                    }
                }
                OutlinedButton(onClick = { lanes += BuilderLane("Activity ${lanes.size + 1}") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add swimlane") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Store schedule") }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        timeDialogLane?.let { laneIndex ->
            Dialog(onDismissRequest = { timeDialogLane = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                androidx.compose.material3.Surface(Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Choose time", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AndroidView(factory = { NumberPicker(it).apply { minValue = 0; maxValue = 23; value = dialogHour; setOnValueChangedListener { _, _, new -> dialogHour = new } } }, update = { it.value = dialogHour }, modifier = Modifier.width(90.dp).height(150.dp))
                            AndroidView(factory = { NumberPicker(it).apply { minValue = 0; maxValue = 59; value = dialogMinute; setOnValueChangedListener { _, _, new -> dialogMinute = new } } }, update = { it.value = dialogMinute }, modifier = Modifier.width(90.dp).height(150.dp))
                        }
                        Button(onClick = { lanes[laneIndex] = lanes[laneIndex].copy(hour = dialogHour, minute = dialogMinute); timeDialogLane = null }) { Text("Use time") }
                    }
                }
            }
        }
    }
}
