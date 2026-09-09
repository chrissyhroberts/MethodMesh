package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.FilterChip
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
    val cronExpression: String = "0 9 * * *",
    val target: CronBuilderTarget = CronBuilderTarget.NOTIFICATION,
    val targetId: String = "",
    val message: String = "",
    val offsetDays: String = "0",
    val offsetTime: String = "00:00",
    val retries: String = "0",
    val retryMinutes: String = "60"
) {
    val cron: String get() = cronExpression.trim().ifBlank { "* * * * *" }
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
        var customEvent by remember { mutableStateOf(false) }
        val tasks = remember { mutableStateListOf(CronBuilderTask()) }
        var status by remember { mutableStateOf("Add one or more cron tasks.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val presets = remember { ProtocolLibraryRepository.presets(app) }
        val protocols = remember { ProtocolLibraryRepository.protocols(app) }
        val eventOptions = remember(presets, protocols) {
            buildList {
                presets.forEach { add("preset.completed:${it.id}" to "Preset completed · ${it.name}") }
                protocols.forEach { add("protocol.completed:${it.id}" to "Protocol completed · ${it.name}") }
                add("custom" to "Custom event…")
            }
        }

        fun save() {
            if (name.isBlank() || tasks.isEmpty()) { status = "Enter a schedule name and at least one task."; return }
            if (triggerMode == "EVENT" && triggerKey.isBlank()) { status = "Choose the event that starts this schedule."; return }
            val parsedStart = runCatching { LocalDateTime.parse(absoluteStart.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()) }.getOrNull()
            if (triggerMode == "ABSOLUTE" && parsedStart == null) { status = "Use YYYY-MM-DD HH:MM for the absolute start."; return }
            val anchor = parsedStart ?: ZonedDateTime.now().withSecond(0).withNano(0)
            val trigger = when (triggerMode) {
                "ABSOLUTE" -> CronTrigger.Absolute(anchor)
                "EVENT" -> CronTrigger.Event(triggerKey.trim())
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
                SchedulerRepository.save(app, ResearchSchedule(id = "${id}_$index", name = task.name, target = when (task.target) { CronTaskTarget.NOTIFICATION -> SchedulerTarget.NOTIFICATION; CronTaskTarget.PRESET -> SchedulerTarget.PRESET; CronTaskTarget.PROTOCOL -> SchedulerTarget.PROTOCOL; else -> SchedulerTarget.CLIPBOARD }, targetValue = task.targetId.ifBlank { task.notificationMessage }, frequency = SchedulerFrequency.CUSTOM, hour = 0, minute = 0, retryCount = task.retries, retryIntervalMinutes = task.retryInterval.toMinutes().toInt(), notificationTitle = task.notificationTitle, notificationMessage = task.notificationMessage, cronExpression = task.cronExpression, triggerMode = triggerMode, triggerValue = triggerKey.trim(), relativeOffsetMinutes = task.relativeOffset.toMinutes().toInt(), anchorAt = anchor.takeIf { triggerMode != "EVENT" }, chainId = id, chainOrder = index))
            }
            status = "Saved ${built.size} cron task${if (built.size == 1) "" else "s"}. JSON is in Files."
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()), SchedulerOutcome(null, "created"), context.request.invocationContext)
            result = execution
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CRON", style = MaterialTheme.typography.headlineSmall)
                Text("Choose when the schedule starts, then add the work it should repeat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Starts", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MANUAL" to "Manual", "ABSOLUTE" to "Absolute", "EVENT" to "Event").forEach { (value, label) ->
                        FilterChip(selected = triggerMode == value, onClick = { triggerMode = value }, label = { Text(label) })
                    }
                }
                when (triggerMode) {
                    "ABSOLUTE" -> OutlinedTextField(absoluteStart, { absoluteStart = it }, label = { Text("Start date and time") }, supportingText = { Text("YYYY-MM-DD HH:MM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    "EVENT" -> {
                        EventTriggerPicker(eventOptions, triggerKey, customEvent) { selected, custom ->
                            triggerKey = selected
                            customEvent = custom
                        }
                        if (customEvent) OutlinedTextField(triggerKey, { triggerKey = it }, label = { Text("Custom event key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                }
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
private fun EventTriggerPicker(
    options: List<Pair<String, String>>,
    selected: String,
    custom: Boolean,
    onSelected: (String, Boolean) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val label = if (custom) "Custom event…" else options.firstOrNull { it.first == selected }?.second ?: "Choose event…"
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("When this happens", style = MaterialTheme.typography.labelMedium)
        BoxedPicker(label, expanded, { expanded = true }, { expanded = false }) {
            options.forEach { (key, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelected(if (key == "custom") "" else key, key == "custom"); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun CronTaskEditor(
    index: Int,
    task: CronBuilderTask,
    presets: List<com.example.methodmesh.core.protocols.CapabilityPreset>,
    protocols: List<com.example.methodmesh.core.protocols.ProtocolDefinition>,
    onChanged: (CronBuilderTask) -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Task ${index + 1}", style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(task.name, { onChanged(task.copy(name = it)) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = task.timing == CronBuilderTiming.ABSOLUTE, onClick = { onChanged(task.copy(timing = CronBuilderTiming.ABSOLUTE)) }, label = { Text("Absolute") })
            FilterChip(selected = task.timing == CronBuilderTiming.RELATIVE, onClick = { onChanged(task.copy(timing = CronBuilderTiming.RELATIVE)) }, label = { Text("Relative") })
        }
        if (task.timing == CronBuilderTiming.ABSOLUTE) {
            OutlinedTextField(
                task.cronExpression,
                { onChanged(task.copy(cronExpression = it)) },
                label = { Text("Cron pattern") },
                supportingText = { Text("minute hour day month weekday · e.g. 0 9 * * *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(task.offsetDays, { onChanged(task.copy(offsetDays = it.filter(Char::isDigit))) }, label = { Text("Days after start") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(task.offsetTime, { onChanged(task.copy(offsetTime = it)) }, label = { Text("Time") }, modifier = Modifier.weight(1f), singleLine = true)
            }
        }
        TaskTargetPicker(task, presets, protocols, onChanged)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(task.retries, { onChanged(task.copy(retries = it.filter(Char::isDigit))) }, label = { Text("Retries") }, modifier = Modifier.weight(1f), singleLine = true)
            OutlinedTextField(task.retryMinutes, { onChanged(task.copy(retryMinutes = it.filter(Char::isDigit))) }, label = { Text("Retry delay (min)") }, modifier = Modifier.weight(1f), singleLine = true)
        }
    }
}

@Composable
private fun TaskTargetPicker(
    task: CronBuilderTask,
    presets: List<com.example.methodmesh.core.protocols.CapabilityPreset>,
    protocols: List<com.example.methodmesh.core.protocols.ProtocolDefinition>,
    onChanged: (CronBuilderTask) -> Unit
) {
    var targetMenu by remember(task.target) { mutableStateOf(false) }
    var itemMenu by remember(task.target, task.targetId) { mutableStateOf(false) }
    val targetLabel = when (task.target) {
        CronBuilderTarget.NOTIFICATION -> "Notification"
        CronBuilderTarget.PRESET -> "Preset"
        CronBuilderTarget.PROTOCOL -> "Protocol"
    }
    val items = when (task.target) {
        CronBuilderTarget.PRESET -> presets.map { it.id to it.name }
        CronBuilderTarget.PROTOCOL -> protocols.map { it.id to it.name }
        CronBuilderTarget.NOTIFICATION -> emptyList()
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Do this", style = MaterialTheme.typography.labelMedium)
        BoxedPicker(targetLabel, targetMenu, { targetMenu = true }, { targetMenu = false }) {
            CronBuilderTarget.entries.forEach { target ->
                DropdownMenuItem(
                    text = { Text(target.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                    onClick = { onChanged(task.copy(target = target, targetId = "")); targetMenu = false }
                )
            }
        }
        when (task.target) {
            CronBuilderTarget.NOTIFICATION -> OutlinedTextField(task.message, { onChanged(task.copy(message = it)) }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            else -> BoxedPicker(
                items.firstOrNull { it.first == task.targetId }?.second ?: "Choose ${targetLabel.lowercase()}…",
                itemMenu,
                { itemMenu = true },
                { itemMenu = false }
            ) {
                items.forEach { (id, name) ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onChanged(task.copy(targetId = id)); itemMenu = false })
                }
                if (items.isEmpty()) DropdownMenuItem(text = { Text("No saved ${targetLabel.lowercase()} items") }, onClick = { itemMenu = false })
            }
        }
    }
}

@Composable
private fun BoxedPicker(
    label: String,
    expanded: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    menu: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.foundation.layout.Box {
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, content = menu)
    }
}

private fun parseOffset(days: String, time: String): Duration {
    val dayCount = days.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val parts = time.split(":").map { it.toLongOrNull() ?: 0 }
    return Duration.ofDays(dayCount).plusHours(parts.getOrElse(0) { 0 }).plusMinutes(parts.getOrElse(1) { 0 })
}
