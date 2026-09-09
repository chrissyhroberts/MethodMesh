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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class BuilderMode { SEQUENCE, WEEKLY }
private enum class BuilderStartMode { MANUAL_DAY_ONE, CALENDAR_NOW, ABSOLUTE }

private data class BuilderLane(val name: String, val hour: Int = 9, val minute: Int = 0, val actionType: ScheduleActionType = ScheduleActionType.NOTIFIER, val message: String = "", val presetId: String = "", val snoozeMinutes: Int = 10, val followUpCount: Int = 0, val followUpIntervalMinutes: Int = 30, val missedStartPolicy: ScheduleMissedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED, val days: Set<Int> = setOf(1)) {
    val time: String get() = "%02d:%02d".format(hour, minute)
}

object SchedulePlanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Composite timed sequence"
    override val description = "Paint a reusable schedule as a set of timed swimlanes."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        val planId = context.request.settings["schedule_plan_id"]
        val existingPlan = remember(planId) { planId?.let { SchedulePlanStore.plan(app, it) } }
        var name by remember(existingPlan?.id) { mutableStateOf(existingPlan?.name.orEmpty()) }
        var sequenceDays by remember(existingPlan?.id) { mutableStateOf(existingPlan?.rules?.flatMap { (it.timing as? ScheduleTimingRule.RelativeDays)?.days.orEmpty() }?.maxOrNull()?.coerceAtLeast(1)?.toString() ?: "22") }
        var durationDays by remember(existingPlan?.id) { mutableStateOf(existingPlan?.termination?.duration?.toDays()?.toString() ?: "22") }
        var endMode by remember(existingPlan?.id) { mutableStateOf(existingPlan?.termination?.mode ?: ScheduleEndMode.DURATION) }
        var mode by remember(existingPlan?.id) { mutableStateOf(if (existingPlan?.rules?.firstOrNull()?.timing is ScheduleTimingRule.Weekly) BuilderMode.WEEKLY else BuilderMode.SEQUENCE) }
        var startMode by remember(existingPlan?.id) { mutableStateOf(when (existingPlan?.activation) { ScheduleActivation.ABSOLUTE_START -> BuilderStartMode.ABSOLUTE; ScheduleActivation.CALENDAR_RULE -> BuilderStartMode.CALENDAR_NOW; else -> BuilderStartMode.MANUAL_DAY_ONE }) }
        var startDate by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()?.toString() ?: LocalDate.now().toString()) }
        var startHour by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.hour ?: 9) }
        var startMinute by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.minute ?: 0) }
        val lanes = remember(existingPlan?.id) {
            mutableStateListOf<BuilderLane>().apply {
                if (existingPlan == null) add(BuilderLane("Activity"))
                else existingPlan.rules.forEach { rule ->
                    val lane = existingPlan.lanes.firstOrNull { it.id == rule.laneId } ?: return@forEach
                    val timing = rule.timing
                    val action = lane.defaultActions.firstOrNull()
                    val selectedDays = when (timing) {
                        is ScheduleTimingRule.RelativeDays -> timing.days
                        is ScheduleTimingRule.Weekly -> timing.weekdays
                        else -> setOf(1)
                    }
                    add(BuilderLane(lane.name, lane.defaultTime.hour, lane.defaultTime.minute, action?.type ?: ScheduleActionType.NOTIFIER, action?.message.orEmpty(), action?.presetId.orEmpty(), action?.snoozeMinutes ?: 10, action?.followUpCount ?: 0, action?.followUpIntervalMinutes ?: 30, lane.missedStartPolicy, selectedDays))
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
            val parsedStart = startDate.trim().let { runCatching { LocalDate.parse(it) }.getOrNull() }?.atTime(startHour, startMinute)?.atZone(ZoneId.systemDefault())
            if (name.isBlank() || (mode == BuilderMode.SEQUENCE && totalDays == null) || (endMode == ScheduleEndMode.DURATION && duration == null) || (startMode == BuilderStartMode.ABSOLUTE && parsedStart == null)) { status = "Enter a plan name, sequence length, and valid start/end settings."; return }
            val built = lanes.mapNotNull { lane ->
                val time = LocalTime.of(lane.hour, lane.minute)
                val action = when {
                    lane.actionType == ScheduleActionType.NOTIFIER -> ScheduleAction(type = ScheduleActionType.NOTIFIER, title = lane.name, message = lane.message.ifBlank { lane.name }, snoozeMinutes = lane.snoozeMinutes, followUpCount = lane.followUpCount, followUpIntervalMinutes = lane.followUpIntervalMinutes)
                    lane.presetId.isNotBlank() -> ScheduleAction(type = ScheduleActionType.PRESET, presetId = lane.presetId, title = presets.firstOrNull { it.id == lane.presetId }?.name.orEmpty(), snoozeMinutes = lane.snoozeMinutes, followUpCount = lane.followUpCount, followUpIntervalMinutes = lane.followUpIntervalMinutes)
                    else -> null
                } ?: return@mapNotNull null
                val scheduleLane = ScheduleLane(name = lane.name.ifBlank { "Activity" }, defaultTime = time, defaultActions = listOf(action), missedStartPolicy = lane.missedStartPolicy)
                val timing = if (mode == BuilderMode.WEEKLY) {
                    ScheduleTimingRule.Weekly(lane.days.filter { it in 1..7 }.toSet().ifEmpty { setOf(1) }, time)
                } else {
                    ScheduleTimingRule.RelativeDays(lane.days.filter { it <= (totalDays ?: 1) }.toSet().ifEmpty { setOf(1) }, time)
                }
                scheduleLane to timing
            }
            if (built.size != lanes.size) { status = "Every lane needs a valid time and action."; return }
            val planLanes = built.map { it.first }
            val termination = if (endMode == ScheduleEndMode.FOREVER) ScheduleTermination(ScheduleEndMode.FOREVER) else ScheduleTermination(ScheduleEndMode.DURATION, duration = duration)
            val activation = when (startMode) {
                BuilderStartMode.MANUAL_DAY_ONE -> ScheduleActivation.MANUAL_DAY_ONE
                BuilderStartMode.CALENDAR_NOW -> ScheduleActivation.CALENDAR_RULE
                BuilderStartMode.ABSOLUTE -> ScheduleActivation.ABSOLUTE_START
            }
            val plan = SchedulePlan(id = existingPlan?.id ?: java.util.UUID.randomUUID().toString(), name = name.trim(), activation = activation, startAt = parsedStart.takeIf { startMode == BuilderStartMode.ABSOLUTE }, termination = termination, lanes = planLanes, rules = built.mapIndexed { index, pair -> ScheduleRule(planLanes[index].id, pair.second) }, version = (existingPlan?.version ?: 0) + 1, enabled = existingPlan?.enabled ?: true, createdAt = existingPlan?.createdAt ?: java.time.ZonedDateTime.now(), updatedAt = java.time.ZonedDateTime.now())
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Schedule type:", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = { mode = BuilderMode.SEQUENCE }) { Text(if (mode == BuilderMode.SEQUENCE) "✓ Day sequence" else "Day sequence") }
                    androidx.compose.material3.TextButton(onClick = { mode = BuilderMode.WEEKLY }) { Text(if (mode == BuilderMode.WEEKLY) "✓ Weekly" else "Weekly") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Starts:", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = { startMode = BuilderStartMode.MANUAL_DAY_ONE }) { Text(if (startMode == BuilderStartMode.MANUAL_DAY_ONE) "✓ Start Day 1" else "Start Day 1") }
                    androidx.compose.material3.TextButton(onClick = { startMode = BuilderStartMode.CALENDAR_NOW }) { Text(if (startMode == BuilderStartMode.CALENDAR_NOW) "✓ Calendar now" else "Calendar now") }
                    androidx.compose.material3.TextButton(onClick = { startMode = BuilderStartMode.ABSOLUTE }) { Text(if (startMode == BuilderStartMode.ABSOLUTE) "✓ On date" else "On date") }
                }
                if (startMode == BuilderStartMode.ABSOLUTE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(startDate, { startDate = it }, label = { Text("Start date (YYYY-MM-DD)") }, modifier = Modifier.width(220.dp), singleLine = true)
                        OutlinedButton(onClick = { dialogHour = startHour; dialogMinute = startMinute; timeDialogLane = -1 }, modifier = Modifier.height(52.dp)) { Text("%02d:%02d".format(startHour, startMinute)) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (mode == BuilderMode.SEQUENCE) OutlinedTextField(sequenceDays, { sequenceDays = it.filter(Char::isDigit) }, label = { Text("Days") }, modifier = Modifier.width(120.dp), singleLine = true)
                    if (endMode == ScheduleEndMode.DURATION) OutlinedTextField(durationDays, { durationDays = it.filter(Char::isDigit) }, label = { Text("Ends after days") }, modifier = Modifier.width(170.dp), singleLine = true)
                    else Text("Runs until stopped", modifier = Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Ends:", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.TextButton(onClick = { endMode = ScheduleEndMode.DURATION }) { Text(if (endMode == ScheduleEndMode.DURATION) "✓ Duration" else "Duration") }
                    androidx.compose.material3.TextButton(onClick = { endMode = ScheduleEndMode.FOREVER }) { Text(if (endMode == ScheduleEndMode.FOREVER) "✓ Forever" else "Forever") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
                    Text("SWIMLANE", modifier = Modifier.width(140.dp).padding(4.dp), fontSize = 11.sp)
                    (1..if (mode == BuilderMode.WEEKLY) 7 else days).forEach { day -> Text(if (mode == BuilderMode.WEEKLY) DayOfWeek.of(day).name.take(3) else day.toString(), modifier = Modifier.width(38.dp).padding(4.dp), fontSize = 11.sp) }
                }
                lanes.forEachIndexed { index, lane ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(140.dp).padding(end = 4.dp)) {
                                OutlinedTextField(lane.name, { lanes[index] = lane.copy(name = it) }, label = { Text("Lane") }, singleLine = true, modifier = Modifier.fillMaxWidth().height(52.dp))
                                OutlinedButton(onClick = { dialogHour = lane.hour; dialogMinute = lane.minute; timeDialogLane = index }, modifier = Modifier.fillMaxWidth().height(42.dp)) { Text(lane.time) }
                            }
                            (1..if (mode == BuilderMode.WEEKLY) 7 else days).forEach { day ->
                                val selected = day in lane.days
                                Box(Modifier.width(38.dp).height(52.dp).padding(2.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant).background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                                    androidx.compose.material3.TextButton(onClick = { val next = lane.days.toMutableSet().apply { if (!remove(day)) add(day) }; lanes[index] = lane.copy(days = next) }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(day.toString(), fontSize = 9.sp, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant); Text(if (selected) "■" else "·", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) } }
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            androidx.compose.material3.TextButton(onClick = { lanes.add(index + 1, lane.copy(hour = (lane.hour + 4) % 24)) }) { Text("+ Add run") }
                            androidx.compose.material3.TextButton(onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.NOTIFIER) }) { Text(if (lane.actionType == ScheduleActionType.NOTIFIER) "✓ Notifier" else "Notifier") }
                            androidx.compose.material3.TextButton(onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.PRESET) }) { Text(if (lane.actionType == ScheduleActionType.PRESET) "✓ Preset" else "Preset") }
                            androidx.compose.material3.TextButton(onClick = { lanes.removeAt(index); expandedLanes = expandedLanes.filter { it != index }.map { if (it > index) it - 1 else it }.toSet() }) { Text("Remove lane / run") }
                        }
                        androidx.compose.material3.TextButton(onClick = { expandedLanes = if (index in expandedLanes) expandedLanes - index else expandedLanes + index }) { Text(if (index in expandedLanes) "Collapse details" else "Expand details") }
                        if (index in expandedLanes) {
                            if (lane.actionType == ScheduleActionType.NOTIFIER) OutlinedTextField(lane.message, { lanes[index] = lane.copy(message = it) }, label = { Text("Reminder message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            else presets.take(8).forEach { preset -> OutlinedButton(onClick = { lanes[index] = lane.copy(presetId = preset.id) }, modifier = Modifier.fillMaxWidth()) { Text(if (lane.presetId == preset.id) "✓ ${preset.name}" else preset.name) } }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(lane.snoozeMinutes.toString(), { lanes[index] = lane.copy(snoozeMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Snooze min") }, modifier = Modifier.width(120.dp), singleLine = true)
                                OutlinedTextField(lane.followUpCount.toString(), { lanes[index] = lane.copy(followUpCount = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Follow-ups") }, modifier = Modifier.width(120.dp), singleLine = true)
                                OutlinedTextField(lane.followUpIntervalMinutes.toString(), { lanes[index] = lane.copy(followUpIntervalMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0) }, label = { Text("Repeat min") }, modifier = Modifier.width(120.dp), singleLine = true)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("If started after this lane's time:", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = { lanes[index] = lane.copy(missedStartPolicy = ScheduleMissedStartPolicy.RUN_MISSED) }) { Text(if (lane.missedStartPolicy == ScheduleMissedStartPolicy.RUN_MISSED) "✓ Run missed" else "Run missed") }
                                OutlinedButton(onClick = { lanes[index] = lane.copy(missedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED) }) { Text(if (lane.missedStartPolicy == ScheduleMissedStartPolicy.SKIP_MISSED) "✓ Skip missed" else "Skip missed") }
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
                        Button(onClick = {
                            if (laneIndex == -1) { startHour = dialogHour; startMinute = dialogMinute } else lanes[laneIndex] = lanes[laneIndex].copy(hour = dialogHour, minute = dialogMinute)
                            timeDialogLane = null
                        }) { Text("Use time") }
                    }
                }
            }
        }
    }
}
