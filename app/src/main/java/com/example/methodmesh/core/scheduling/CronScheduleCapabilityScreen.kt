package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

private enum class CronBuilderTarget { NOTIFICATION, PRESET, PROTOCOL }
private enum class CronBuilderTiming { ABSOLUTE, RELATIVE }

private data class CronBuilderTask(
    val name: String = "Scheduled activity",
    val timing: CronBuilderTiming = CronBuilderTiming.ABSOLUTE,
    val minute: String = "0",
    val hour: String = "9",
    val day: String = "*",
    val month: String = "*",
    val weekday: String = "*",
    val target: CronBuilderTarget = CronBuilderTarget.NOTIFICATION,
    val targetId: String = "",
    val message: String = "",
    val offsetDays: String = "0",
    val offsetTime: String = "00:00",
    val retries: String = "0",
    val retryMinutes: String = "60"
) {
    val cron: String get() = listOf(minute, hour, day, month, weekday).joinToString(" ") { it.ifBlank { "*" } }
}

object CronScheduleCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Cron schedule"
    override val description = "Build a JSON-backed schedule from aligned cron tasks."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val app = LocalContext.current.applicationContext
        var name by remember { mutableStateOf(context.request.settings["schedule_name"].orEmpty()) }
        var triggerMode by remember { mutableStateOf("MANUAL") }
        var absoluteStart by remember { mutableStateOf(LocalDateTime.now().withSecond(0).withNano(0).toString().replace('T', ' ')) }
        var triggerKey by remember { mutableStateOf("") }
        val tasks = remember { mutableStateListOf(CronBuilderTask()) }
        var status by remember { mutableStateOf("Add one or more cron tasks.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val presets = remember { ProtocolLibraryRepository.presets(app) }
        val protocols = remember { ProtocolLibraryRepository.protocols(app) }

        fun save() {
            if (name.isBlank() || tasks.isEmpty()) { status = "Enter a schedule name and at least one task."; return }
            val parsedStart = runCatching { LocalDateTime.parse(absoluteStart.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()) }.getOrNull()
            if (triggerMode == "ABSOLUTE" && parsedStart == null) { status = "Use YYYY-MM-DD HH:MM for the absolute start."; return }
            val anchor = parsedStart ?: ZonedDateTime.now().withSecond(0).withNano(0)
            val trigger = when (triggerMode) {
                "ABSOLUTE" -> CronTrigger.Absolute(anchor)
                "EVENT" -> CronTrigger.Event(triggerKey.trim().ifBlank { "preset.completed" })
                "PRESET" -> CronTrigger.Preset(triggerKey.trim())
                else -> CronTrigger.Manual
            }
            val built = tasks.mapIndexed { index, task ->
                val offset = parseOffset(task.offsetDays, task.offsetTime)
                val target = when (task.target) {
                    CronBuilderTarget.NOTIFICATION -> CronTaskTarget.NOTIFICATION
                    CronBuilderTarget.PRESET -> CronTaskTarget.PRESET
                    CronBuilderTarget.PROTOCOL -> CronTaskTarget.PROTOCOL
                }
                if (target != CronTaskTarget.NOTIFICATION && task.targetId.isBlank()) error("Choose a preset or protocol for task ${index + 1}.")
                CronTask(name = task.name.ifBlank { "Task ${index + 1}" }, timing = if (task.timing == CronBuilderTiming.RELATIVE) ScheduleTimingMode.RELATIVE else ScheduleTimingMode.ABSOLUTE, cronExpression = task.cron, relativeOffset = offset, target = target, targetId = task.targetId, notificationTitle = name.trim(), notificationMessage = task.message.ifBlank { task.name.ifBlank { "Scheduled activity" } }, retries = task.retries.toIntOrNull()?.coerceAtLeast(0) ?: 0, retryInterval = Duration.ofMinutes((task.retryMinutes.toLongOrNull() ?: 60).coerceAtLeast(1)))
            }
            val id = java.util.UUID.randomUUID().toString()
            val bundle = CronScheduleBundle(id = id, name = name.trim(), trigger = trigger, tasks = built)
            runCatching { CronScheduleBundleStore.save(app, bundle) }.onFailure { status = "Could not save schedule JSON: ${it.message ?: "storage error"}"; return }
            built.forEachIndexed { index, task ->
                SchedulerRepository.save(app, ResearchSchedule(id = "${id}_$index", name = task.name, target = when (task.target) { CronTaskTarget.NOTIFICATION -> SchedulerTarget.NOTIFICATION; CronTaskTarget.PRESET -> SchedulerTarget.PRESET; CronTaskTarget.PROTOCOL -> SchedulerTarget.PROTOCOL; else -> SchedulerTarget.CLIPBOARD }, targetValue = task.targetId.ifBlank { task.notificationMessage }, frequency = SchedulerFrequency.CUSTOM, hour = 0, minute = 0, retryCount = task.retries, retryIntervalMinutes = task.retryInterval.toMinutes().toInt(), notificationTitle = task.notificationTitle, notificationMessage = task.notificationMessage, cronExpression = task.cronExpression, triggerMode = triggerMode, triggerValue = when (triggerMode) { "PRESET" -> "preset.completed:${triggerKey.trim()}" else -> triggerKey.trim() }, relativeOffsetMinutes = task.relativeOffset.toMinutes().toInt(), anchorAt = anchor.takeIf { triggerMode != "EVENT" && triggerMode != "PRESET" }, chainId = id, chainOrder = index))
            }
            status = "Saved ${built.size} cron task${if (built.size == 1) "" else "s"}. JSON is in Files."
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()), SchedulerOutcome(null, "created"), context.request.invocationContext)
            result = execution
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CRON", style = MaterialTheme.typography.headlineSmall)
                Text("A schedule is a bundle of one or more tasks. Each task can run on an absolute or relative cron timeline.", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(name, { name = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Initiation", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("MANUAL" to "Manual", "ABSOLUTE" to "Absolute", "EVENT" to "Event", "PRESET" to "Preset").forEach { (value, label) -> OutlinedButton(onClick = { triggerMode = value }) { Text(if (triggerMode == value) "✓ $label" else label) } }
                }
                if (triggerMode == "ABSOLUTE") OutlinedTextField(absoluteStart, { absoluteStart = it }, label = { Text("Start at (YYYY-MM-DD HH:MM)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                if (triggerMode == "EVENT" || triggerMode == "PRESET") OutlinedTextField(triggerKey, { triggerKey = it }, label = { Text(if (triggerMode == "EVENT") "Event key" else "Triggering preset ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                tasks.forEachIndexed { index, task ->
                    CronTaskEditor(index, task, presets, protocols) { updated -> tasks[index] = updated }
                    OutlinedButton(onClick = { tasks.removeAt(index) }, enabled = tasks.size > 1, modifier = Modifier.fillMaxWidth()) { Text("Remove task") }
                }
                OutlinedButton(onClick = { tasks += CronBuilderTask(name = "Scheduled activity ${tasks.size + 1}") }, modifier = Modifier.fillMaxWidth()) { Text("+ Add task") }
                Button(onClick = ::save, modifier = Modifier.fillMaxWidth()) { Text("Save schedule") }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CronTaskEditor(index: Int, task: CronBuilderTask, presets: List<com.example.methodmesh.core.protocols.CapabilityPreset>, protocols: List<com.example.methodmesh.core.protocols.ProtocolDefinition>, onChanged: (CronBuilderTask) -> Unit) {
    var targetMenu by remember(index) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Scheduled activity ${index + 1}", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(task.name, { onChanged(task.copy(name = it)) }, label = { Text("Task name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onChanged(task.copy(timing = CronBuilderTiming.ABSOLUTE)) }) { Text(if (task.timing == CronBuilderTiming.ABSOLUTE) "✓ Absolute" else "Absolute") }
            OutlinedButton(onClick = { onChanged(task.copy(timing = CronBuilderTiming.RELATIVE)) }) { Text(if (task.timing == CronBuilderTiming.RELATIVE) "✓ Relative" else "Relative") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("Minute" to task.minute, "Hour" to task.hour, "Day" to task.day, "Month" to task.month, "Weekday" to task.weekday).forEach { (label, value) ->
                OutlinedTextField(value, { next -> onChanged(when (label) { "Minute" -> task.copy(minute = next); "Hour" -> task.copy(hour = next); "Day" -> task.copy(day = next); "Month" -> task.copy(month = next); else -> task.copy(weekday = next) }) }, label = { Text(label) }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
        if (task.timing == CronBuilderTiming.RELATIVE) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(task.offsetDays, { onChanged(task.copy(offsetDays = it.filter(Char::isDigit))) }, label = { Text("Days after anchor") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(task.offsetTime, { onChanged(task.copy(offsetTime = it)) }, label = { Text("Time after anchor") }, modifier = Modifier.weight(1f), singleLine = true)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { targetMenu = true }, modifier = Modifier.weight(1f)) { Text(task.target.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
            DropdownMenu(expanded = targetMenu, onDismissRequest = { targetMenu = false }) {
                CronBuilderTarget.entries.forEach { target -> DropdownMenuItem(text = { Text(target.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }, onClick = { onChanged(task.copy(target = target, targetId = "")); targetMenu = false }) }
            }
        }
        when (task.target) {
            CronBuilderTarget.NOTIFICATION -> OutlinedTextField(task.message, { onChanged(task.copy(message = it)) }, label = { Text("Notification message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            CronBuilderTarget.PRESET -> ChoiceButtons("Preset", presets.map { it.id to it.name }, task.targetId) { onChanged(task.copy(targetId = it)) }
            CronBuilderTarget.PROTOCOL -> ChoiceButtons("Protocol", protocols.map { it.id to it.name }, task.targetId) { onChanged(task.copy(targetId = it)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(task.retries, { onChanged(task.copy(retries = it.filter(Char::isDigit))) }, label = { Text("Retries") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(task.retryMinutes, { onChanged(task.copy(retryMinutes = it.filter(Char::isDigit))) }, label = { Text("Retry min") }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}

@Composable
private fun ChoiceButtons(label: String, options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        options.forEach { (id, name) -> OutlinedButton(onClick = { onSelected(id) }, modifier = Modifier.fillMaxWidth()) { Text(if (id == selected) "✓ $name" else name) } }
        if (options.isEmpty()) Text("No saved ${label.lowercase()} items yet.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun parseOffset(days: String, time: String): Duration {
    val dayCount = days.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val parts = time.split(":").map { it.toLongOrNull() ?: 0 }
    return Duration.ofDays(dayCount).plusHours(parts.getOrElse(0) { 0 }).plusMinutes(parts.getOrElse(1) { 0 })
}
