package com.example.researchos.core.scheduling

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.workflow.ExternalActionRequest
import com.example.researchos.transport.workflow.ExternalWorkflowRequest
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext

@Composable
fun SchedulerCenterCard(schedules: List<ResearchSchedule>, onCreate: () -> Unit, onEdit: (ResearchSchedule) -> Unit, onChanged: () -> Unit, onExportSchedule: (ResearchSchedule) -> Unit = {}, onAdvancedExport: () -> Unit = {}, onAdvancedImport: () -> Unit = {}) {
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    var transferStatus by remember { mutableStateOf("") }
    var importedText by remember { mutableStateOf("") }
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
            Spacer(Modifier.height(8.dp))
            if (schedules.isEmpty()) Text("No schedules stored.", style = MaterialTheme.typography.bodyMedium)
            val scheduleGroups = schedules.sortedWith(compareBy<ResearchSchedule> { it.chainId.ifBlank { it.id } }.thenBy { it.chainOrder })
                .groupBy { it.chainId.ifBlank { it.id } }.values
            scheduleGroups.forEach { group ->
                val schedule = group.first()
                val running = schedule.enabled
                ElevatedCard(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (running) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        }
                    )
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (group.size > 1) schedule.name.removeSuffix(" 1") else schedule.name, style = MaterialTheme.typography.titleSmall)
                                Text(if (running) "Running" else "Paused", style = MaterialTheme.typography.labelMedium,
                                    color = if (running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = running,
                                onCheckedChange = {
                                    SchedulerRepository.setChainEnabled(context, schedule, it)
                                    onChanged()
                                }
                            )
                        }
                        val timing = schedule.cronExpression.takeIf { it.isNotBlank() }?.let { "cron: $it" }
                            ?: "${schedule.frequency} • ${"%02d:%02d".format(schedule.hour, schedule.minute)}"
                        Text("$timing • ${if (group.size > 1) "${group.size}-step chain" else schedule.target}", style = MaterialTheme.typography.bodySmall)
                        group.forEachIndexed { index, step ->
                            Text("${index + 1}. ${step.target}: ${step.targetValue}", style = MaterialTheme.typography.bodySmall)
                        }
                        group.asSequence().flatMap { SchedulerRepository.events(context, it.id).asSequence() }.firstOrNull()?.let { event ->
                            Text("Last event: ${event.event} (${event.timeIso})", style = MaterialTheme.typography.labelSmall)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Button(onClick = { onEdit(schedule) }) { Text("Edit") }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = { SchedulerRepository.removeChain(context, schedule); onChanged() }) { Text("Remove") }
                            Spacer(Modifier.padding(4.dp))
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(context, SchedulerDispatchActivity::class.java)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    .putExtra("schedule_id", schedule.id)
                                    .putExtra("test_chain", true))
                            }) { Text("Test") }
                        }
                        OutlinedButton(onClick = { onExportSchedule(schedule) }, Modifier.fillMaxWidth()) { Text("Export this schedule") }
                    }
                }
            }
            Button(onClick = onCreate, Modifier.fillMaxWidth()) { Text("Create schedule") }
            Spacer(Modifier.height(8.dp))
            Text("Schedule transfer", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = {
                    val payload = SchedulerBundle.export(context)
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ResearchOS schedules", payload))
                    transferStatus = "All schedules copied to clipboard."
                }) { Text("Copy schedules") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = { fileLauncher.launch("researchos-schedules.json") }) { Text("Save file") }
            }
            OutlinedButton(onClick = {
                importedText = context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                if (importedText.isBlank()) transferStatus = "Clipboard is empty." else runCatching {
                    val decoded = SchedulerBundle.import(context, importedText)
                    transferStatus = "Imported ${decoded.schedules.size} schedule(s)."
                    onChanged()
                }.onFailure { transferStatus = it.message ?: "Import failed." }
            }, Modifier.fillMaxWidth()) { Text("Paste and import clipboard bundle") }
            Row(Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onAdvancedExport) { Text("QR / NFC export") }
                Spacer(Modifier.padding(4.dp))
                OutlinedButton(onClick = onAdvancedImport) { Text("QR / NFC import") }
            }
            if (transferStatus.isNotBlank()) Text(transferStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SchedulerEditorHost(schedule: ResearchSchedule?, onDone: () -> Unit, onCancel: () -> Unit) {
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
    }
    val action = ExternalActionRequest(requestedId = As100SchedulerMethod.ID, canonicalId = As100SchedulerMethod.ID, settings = settings)
    val request = ExternalWorkflowRequest(actions = listOf(action), invocationContext = InvocationContext(caller = "dashboard"), returns = emptyList(), returnMode = ReturnMode.Json, source = "dashboard")
    SchedulerCapabilityScreen.Render(CapabilityScreenContext(action, request, 1, 1), onBack = onCancel, onConfirmed = { onDone() }, onCancel = onCancel)
}
