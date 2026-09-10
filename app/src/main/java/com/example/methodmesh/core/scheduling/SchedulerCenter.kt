package com.example.methodmesh.core.scheduling

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.ResearchRuntime
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ExternalActionRequest
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext

@Composable
fun SchedulerCenterCard(onCreate: () -> Unit, onEditPlan: (SchedulePlan) -> Unit = {}, onEditSchedule: (ResearchSchedule) -> Unit = {}) {
    val context = LocalContext.current
    var plans by remember { mutableStateOf(SchedulePlanStore.allPlans(context)) }
    var schedules by remember { mutableStateOf(SchedulerRepository.all(context)) }
    var runtimeRefresh by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf("") }
    var importedText by remember { mutableStateOf("") }
    LaunchedEffect(expanded) {
        while (expanded) {
            plans = SchedulePlanStore.allPlans(context)
            schedules = SchedulerRepository.all(context)
            runtimeRefresh++
            delay(1000)
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { it.write(SchedulerBundle.export(context).toByteArray(Charsets.UTF_8)) }
    }
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), elevation = CardDefaults.elevatedCardElevation(2.dp)) {
        Column(Modifier.padding(16.dp)) {
            Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
                Text(if (expanded) "▼ Schedules" else "▶ Schedules", style = MaterialTheme.typography.titleMedium)
                Text("Recurring form and web-form tasks on this device.", style = MaterialTheme.typography.bodySmall)
            }
            if (expanded) {
            runtimeRefresh
            Spacer(Modifier.height(8.dp))
            Text("Schedule plans", style = MaterialTheme.typography.titleSmall)
            plans.forEach { plan ->
                ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.padding(10.dp)) {
                        Text(plan.name, style = MaterialTheme.typography.titleSmall)
                        if (plan.description.isNotBlank()) Text(plan.description, style = MaterialTheme.typography.bodySmall)
                        Text("${plan.activation.name.replace('_', ' ')} · ${plan.termination.mode.name.replace('_', ' ')} · ${plan.lanes.size} lane(s)", style = MaterialTheme.typography.bodySmall)
                        plan.rules.forEach { rule ->
                            val daysForPreview = when (val timing = rule.timing) {
                                is ScheduleTimingRule.RelativeDays -> timing.days
                                is ScheduleTimingRule.Weekly -> timing.weekdays
                                else -> emptySet()
                            }
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..(daysForPreview.maxOrNull()?.coerceAtMost(31) ?: 1)).forEach { day ->
                                    Text(if (day in daysForPreview) "■" else "·", modifier = Modifier.padding(horizontal = 3.dp), color = if (day in daysForPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        val instances = SchedulePlanStore.allInstances(context).filter { it.planId == plan.id }
                        val running = instances.firstOrNull { it.stoppedAt == null }
                        val completed = instances.sumOf { instance -> instance.occurrences.count { it.state == ScheduleOccurrenceState.COMPLETED } }
                        val failed = instances.sumOf { instance -> instance.occurrences.count { it.state == ScheduleOccurrenceState.FAILED || it.state == ScheduleOccurrenceState.MISSED } }
                        val lastActivity = instances.flatMap { it.occurrences }.filter { it.state in setOf(ScheduleOccurrenceState.COMPLETED, ScheduleOccurrenceState.FAILED, ScheduleOccurrenceState.MISSED, ScheduleOccurrenceState.SKIPPED) }.maxByOrNull { it.completedAt ?: it.scheduledAt }
                        if (instances.isNotEmpty()) {
                            Text("History · $completed completed · $failed missed/failed" + (lastActivity?.let { " · last ${it.laneName} ${it.state.name.lowercase()}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                        }
                        if (running == null && (!plan.enabled || plan.activation == ScheduleActivation.MANUAL_DAY_ONE)) {
                            Button(onClick = { val resumed = plan.copy(enabled = true, updatedAt = java.time.ZonedDateTime.now()); SchedulePlanStore.savePlan(context, resumed); SchedulePlanRuntime.start(context, resumed); plans = SchedulePlanStore.allPlans(context) }) { Text(if (plan.activation == ScheduleActivation.MANUAL_DAY_ONE) "Start Day 1" else "Start") }
                        } else if (running != null) {
                            Text("Running · ${running.occurrences.count { it.state == ScheduleOccurrenceState.COMPLETED }} completed", style = MaterialTheme.typography.bodySmall)
                            running.occurrences.firstOrNull { it.state == ScheduleOccurrenceState.UPCOMING || it.state == ScheduleOccurrenceState.WINDOW_OPEN || it.state == ScheduleOccurrenceState.DUE }?.let { next ->
                                Text("Next: ${next.laneName} · ${next.scheduledAt.toLocalDate()} ${next.scheduledAt.toLocalTime()}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (running != null) OutlinedButton(onClick = { SchedulePlanRuntime.cancel(context, running); SchedulePlanStore.removeInstancesForPlan(context, plan.id); SchedulePlanStore.savePlan(context, plan.copy(enabled = false, updatedAt = java.time.ZonedDateTime.now())); plans = SchedulePlanStore.allPlans(context) }) { Text("Stop") }
                            OutlinedButton(onClick = { onEditPlan(plan) }) { Text("Edit") }
                            OutlinedButton(onClick = {
                                SchedulePlanStore.allInstances(context).filter { it.planId == plan.id }.forEach { SchedulePlanRuntime.cancel(context, it) }
                                SchedulePlanStore.removeInstancesForPlan(context, plan.id)
                                SchedulePlanStore.removePlan(context, plan.id)
                                plans = SchedulePlanStore.allPlans(context)
                            }) { Text("Delete") }
                        }
                    }
                }
            }
            if (schedules.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Cron schedules", style = MaterialTheme.typography.titleSmall)
                schedules.sortedWith(compareBy<ResearchSchedule> { it.chainId.ifBlank { it.id } }.thenBy { it.chainOrder })
                    .groupBy { it.chainId.ifBlank { it.id } }.values.forEach { group ->
                        val schedule = group.first()
                        ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(if (group.size > 1) schedule.name.removeSuffix(" 1") else schedule.name, style = MaterialTheme.typography.titleSmall)
                                        val state = when {
                                            !schedule.enabled -> "Paused"
                                            schedule.anchorAt == null -> "Ready for trigger"
                                            else -> "Running"
                                        }
                                        Text(state, style = MaterialTheme.typography.labelMedium, color = if (schedule.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = schedule.enabled, onCheckedChange = { SchedulerRepository.setChainEnabled(context, schedule, it); schedules = SchedulerRepository.all(context) })
                                }
                                Text("cron ${schedule.cronExpression} · ${group.size} task${if (group.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall)
                                group.forEachIndexed { index, task -> Text("${index + 1}. ${task.target.name.lowercase()}: ${task.targetValue}", style = MaterialTheme.typography.bodySmall) }
                                SchedulerRepository.events(context, schedule.id).firstOrNull()?.let { event -> Text("Last: ${event.event}", style = MaterialTheme.typography.labelSmall) }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(onClick = {
                                        ScheduleTriggerRuntime.start(context, schedule.id.substringBeforeLast("_"))
                                        schedules = SchedulerRepository.all(context)
                                    }) { Text("Start") }
                                    OutlinedButton(onClick = { onEditSchedule(schedule) }) { Text("Edit") }
                                    OutlinedButton(onClick = { SchedulerRepository.removeChain(context, schedule); schedules = SchedulerRepository.all(context) }) { Text("Delete") }
                                    OutlinedButton(onClick = { context.startActivity(Intent(context, SchedulerDispatchActivity::class.java).setAction("com.example.methodmesh.TEST_SCHEDULE").putExtra("schedule_id", schedule.id).putExtra("test_chain", true)) }) { Text("Test") }
                                }
                            }
                        }
                    }
            }
            Button(onClick = onCreate, Modifier.fillMaxWidth()) { Text("Create schedule") }
            }
        }
    }
}

@Composable
fun SchedulerEditorHost(schedule: ResearchSchedule?, plan: SchedulePlan? = null, onDone: () -> Unit, onCancel: () -> Unit) {
    val settings = buildMap {
        schedule?.let {
            put("schedule_id", it.id); put("schedule_name", it.name); put("schedule_target", it.target.name); put("schedule_target_value", it.targetValue); put("schedule_target_settings", it.targetSettings)
            put("input_project_id", it.projectId); put("input_project_package", it.packageName); put("schedule_frequency", it.frequency.name)
            put("schedule_chain_id", it.chainId); put("schedule_chain_order", it.chainOrder.toString())
            put("schedule_time", "%02d:%02d".format(it.hour, it.minute)); put("schedule_day_of_week", it.dayOfWeek.toString()); put("schedule_day_of_month", it.dayOfMonth.toString())
            put("schedule_ordinal", it.ordinal.toString()); put("schedule_custom_weekday", it.customWeekday.toString()); put("schedule_retry_count", it.retryCount.toString()); put("schedule_retry_interval_minutes", it.retryIntervalMinutes.toString())
            put("schedule_notification_title", it.notificationTitle); put("schedule_notification_message", it.notificationMessage)
            put("schedule_cron", it.cronExpression)
        }
        plan?.let { put("schedule_plan_id", it.id) }
    }
    val action = ExternalActionRequest(requestedId = As100SchedulerMethod.ID, canonicalId = As100SchedulerMethod.ID, settings = settings)
    val request = ExternalWorkflowRequest(actions = listOf(action), invocationContext = InvocationContext(caller = "dashboard"), returns = emptyList(), returnMode = ReturnMode.Json, source = "dashboard")
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            androidx.compose.runtime.key(plan?.id ?: schedule?.id ?: "new") {
                val screen = if (schedule != null || plan == null) CronScheduleCapabilityScreen else SchedulePlanCapabilityScreen
                screen.Render(CapabilityScreenContext(action, request, 1, 1), onBack = onCancel, onConfirmed = { onDone() }, onCancel = onCancel)
            }
        }
    }
}
