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
fun SchedulerCenterCard(onCreate: () -> Unit, onEditPlan: (SchedulePlan) -> Unit = {}) {
    val context = LocalContext.current
    var plans by remember { mutableStateOf(SchedulePlanStore.allPlans(context)) }
    var runtimeRefresh by remember { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf("") }
    var importedText by remember { mutableStateOf("") }
    LaunchedEffect(expanded) {
        while (expanded) {
            plans = SchedulePlanStore.allPlans(context)
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
                        Text("${plan.activation.name.replace('_', ' ')} · ${plan.termination.mode.name.replace('_', ' ')} · ${plan.lanes.size} lane(s)", style = MaterialTheme.typography.bodySmall)
                        plan.rules.filter { it.timing is ScheduleTimingRule.RelativeDays }.forEach { rule ->
                            val timing = rule.timing as ScheduleTimingRule.RelativeDays
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (1..(timing.days.maxOrNull()?.coerceAtMost(31) ?: 1)).forEach { day ->
                                    Text(if (day in timing.days) "■" else "·", modifier = Modifier.padding(horizontal = 3.dp), color = if (day in timing.days) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        val running = SchedulePlanStore.allInstances(context).firstOrNull { it.planId == plan.id && it.stoppedAt == null }
                        if (running == null && plan.activation == ScheduleActivation.MANUAL_DAY_ONE) {
                            Button(onClick = { SchedulePlanRuntime.start(context, plan); plans = SchedulePlanStore.allPlans(context) }) { Text("Start Day 1") }
                        } else if (running != null) {
                            Text("Running · ${running.occurrences.count { it.state == ScheduleOccurrenceState.COMPLETED }} completed", style = MaterialTheme.typography.bodySmall)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                SchedulePlanCapabilityScreen.Render(CapabilityScreenContext(action, request, 1, 1), onBack = onCancel, onConfirmed = { onDone() }, onCancel = onCancel)
            }
        }
    }
}
