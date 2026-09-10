package com.example.methodmesh.core.scheduling

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
    val recurrence: CronTaskRecurrence = CronTaskRecurrence.CRON,
    val timing: CronBuilderTiming = CronBuilderTiming.ABSOLUTE,
    val cronExpression: String = "0 9 * * *",
    val target: CronBuilderTarget = CronBuilderTarget.NOTIFICATION,
    val targetId: String = "",
    val message: String = "",
    val delayDays: String = "0",
    val delayHours: String = "0",
    val delayMinutes: String = "0",
    val delaySeconds: String = "0",
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
        val supplied = remember(context.action.settings, context.request.settings) { context.request.settings + context.action.settings }
        val existingBundle = remember(supplied["schedule_id"]) {
            supplied["schedule_id"]?.substringBeforeLast("_")?.let { CronScheduleBundleStore.get(app, it) }
        }
        var name by remember(existingBundle) { mutableStateOf(existingBundle?.name ?: supplied["schedule_name"].orEmpty()) }
        var constitutive by remember(existingBundle) { mutableStateOf(existingBundle.isConstitutive()) }
        var triggerMode by remember(existingBundle) { mutableStateOf(existingBundle.triggerMode()) }
        var absoluteStart by remember(existingBundle) { mutableStateOf(existingBundle.absoluteStartText()) }
        var triggerKey by remember(existingBundle) { mutableStateOf(existingBundle.triggerKey()) }
        var customEvent by remember(existingBundle) { mutableStateOf(existingBundle.isCustomEvent()) }
        var stopMode by remember(existingBundle) { mutableStateOf(existingBundle.stopMode()) }
        var stopAbsolute by remember(existingBundle) { mutableStateOf(existingBundle.stopAbsoluteText()) }
        var stopDays by remember(existingBundle) { mutableStateOf(existingBundle.stopDurationPart(Duration::toDays)) }
        var stopHours by remember(existingBundle) { mutableStateOf(existingBundle.stopDurationRemainder(24 * 60 * 60, 60 * 60)) }
        var stopMinutes by remember(existingBundle) { mutableStateOf(existingBundle.stopDurationRemainder(60 * 60, 60)) }
        var stopSeconds by remember(existingBundle) { mutableStateOf(existingBundle.stopDurationRemainder(60, 1)) }
        val tasks = remember(existingBundle) { mutableStateListOf(*(existingBundle?.tasks?.map(::toBuilderTask)?.toTypedArray() ?: arrayOf(CronBuilderTask()))) }
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
            if (!constitutive && triggerMode == "EVENT" && triggerKey.isBlank()) { status = "Choose the event that starts this schedule."; return }
            val parsedStart = runCatching { LocalDateTime.parse(absoluteStart.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault()) }.getOrNull()
            if (!constitutive && triggerMode == "ABSOLUTE" && parsedStart == null) { status = "Use YYYY-MM-DD HH:MM for the absolute start."; return }
            val anchor = parsedStart ?: ZonedDateTime.now().withSecond(0).withNano(0)
            val stopRule = when (stopMode) {
                "ABSOLUTE" -> runCatching { ScheduleStopRule.Absolute(LocalDateTime.parse(stopAbsolute.trim().replace(' ', 'T')).atZone(ZoneId.systemDefault())) }.getOrElse {
                    status = "Use YYYY-MM-DD HH:MM for the stop time."; return
                }
                "RELATIVE" -> ScheduleStopRule.Relative(parseDuration(stopDays, stopHours, stopMinutes, stopSeconds).takeIf { !it.isZero } ?: run {
                    status = "Set a stop delay greater than zero."; return
                })
                else -> ScheduleStopRule.Never
            }
            val trigger = if (constitutive) CronTrigger.Constitutive(anchor) else when (triggerMode) {
                "ABSOLUTE" -> CronTrigger.Absolute(anchor)
                "EVENT" -> CronTrigger.Event(triggerKey.trim())
                else -> CronTrigger.Manual
            }
            val built = tasks.mapIndexed { index, task ->
                val offset = parseDuration(task.delayDays, task.delayHours, task.delayMinutes, task.delaySeconds)
                val target = when (task.target) {
                    CronBuilderTarget.NOTIFICATION -> CronTaskTarget.NOTIFICATION
                    CronBuilderTarget.PRESET -> CronTaskTarget.PRESET
                    CronBuilderTarget.PROTOCOL -> CronTaskTarget.PROTOCOL
                }
                if (target != CronTaskTarget.NOTIFICATION && task.targetId.isBlank()) error("Choose a preset or protocol for task ${index + 1}.")
                CronTask(name = task.name.ifBlank { "Task ${index + 1}" }, recurrence = task.recurrence, timing = if (task.timing == CronBuilderTiming.RELATIVE || task.recurrence == CronTaskRecurrence.ONCE) ScheduleTimingMode.RELATIVE else ScheduleTimingMode.ABSOLUTE, cronExpression = task.cron, relativeOffset = offset, target = target, targetId = task.targetId, notificationTitle = name.trim(), notificationMessage = task.message.ifBlank { task.name.ifBlank { "Scheduled activity" } }, retries = task.retries.toIntOrNull()?.coerceAtLeast(0) ?: 0, retryInterval = Duration.ofMinutes((task.retryMinutes.toLongOrNull() ?: 60).coerceAtLeast(1)))
            }
            val id = existingBundle?.id ?: java.util.UUID.randomUUID().toString()
            val bundle = CronScheduleBundle(id = id, name = name.trim(), trigger = trigger, stopRule = stopRule, tasks = built)
            runCatching { CronScheduleBundleStore.save(app, bundle) }.onFailure { status = "Could not save schedule JSON: ${it.message ?: "storage error"}"; return }
            SchedulerRepository.all(app).filter { it.id.startsWith("${id}_") }.forEach { SchedulerRepository.remove(app, it.id) }
            built.forEachIndexed { index, task ->
                SchedulerRepository.save(app, ResearchSchedule(id = "${id}_$index", name = task.name, target = when (task.target) { CronTaskTarget.NOTIFICATION -> SchedulerTarget.NOTIFICATION; CronTaskTarget.PRESET -> SchedulerTarget.PRESET; CronTaskTarget.PROTOCOL -> SchedulerTarget.PROTOCOL; else -> SchedulerTarget.CLIPBOARD }, targetValue = task.targetId.ifBlank { task.notificationMessage }, frequency = SchedulerFrequency.CUSTOM, hour = 0, minute = 0, retryCount = task.retries, retryIntervalMinutes = task.retryInterval.toMinutes().toInt(), notificationTitle = task.notificationTitle, notificationMessage = task.notificationMessage, cronExpression = task.cronExpression, triggerMode = if (constitutive) "CONSTITUTIVE" else triggerMode, triggerValue = triggerKey.trim(), relativeOffsetSeconds = task.relativeOffset.seconds, oneShot = task.recurrence == CronTaskRecurrence.ONCE, anchorAt = anchor.takeIf { constitutive || triggerMode == "ABSOLUTE" }, stopAt = (stopRule as? ScheduleStopRule.Absolute)?.stopAt, stopAfterSeconds = (stopRule as? ScheduleStopRule.Relative)?.delay?.seconds))
            }
            status = "Saved ${built.size} scheduled task${if (built.size == 1) "" else "s"}. JSON is in Files."
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()), SchedulerOutcome(null, "created"), context.request.invocationContext)
            result = execution
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("CRON", style = MaterialTheme.typography.headlineSmall)
                Text("Choose when the schedule starts, then add the work it should repeat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(name, { name = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("Activation", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = constitutive, onClick = { constitutive = true }, label = { Text("Always on") })
                    FilterChip(selected = !constitutive, onClick = { constitutive = false }, label = { Text("Triggered") })
                }
                if (!constitutive) {
                    Text("Trigger", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("MANUAL" to "Manual", "ABSOLUTE" to "At date/time", "EVENT" to "Event").forEach { (value, label) ->
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
                }
                Text("Stops", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("NEVER" to "Never", "ABSOLUTE" to "At date/time", "RELATIVE" to "After delay").forEach { (value, label) ->
                        FilterChip(selected = stopMode == value, onClick = { stopMode = value }, label = { Text(label) })
                    }
                }
                when (stopMode) {
                    "ABSOLUTE" -> OutlinedTextField(stopAbsolute, { stopAbsolute = it }, label = { Text("Stop date and time") }, supportingText = { Text("YYYY-MM-DD HH:MM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    "RELATIVE" -> DurationFields(stopDays, stopHours, stopMinutes, stopSeconds, { stopDays = it }, { stopHours = it }, { stopMinutes = it }, { stopSeconds = it })
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
            FilterChip(selected = task.recurrence == CronTaskRecurrence.ONCE, onClick = { onChanged(task.copy(recurrence = CronTaskRecurrence.ONCE, timing = CronBuilderTiming.RELATIVE)) }, label = { Text("Once") })
            FilterChip(selected = task.recurrence == CronTaskRecurrence.CRON, onClick = { onChanged(task.copy(recurrence = CronTaskRecurrence.CRON)) }, label = { Text("Cron") })
        }
        if (task.recurrence == CronTaskRecurrence.ONCE) {
            Text("Wait after the schedule starts, then run once.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DurationFields(task.delayDays, task.delayHours, task.delayMinutes, task.delaySeconds,
                { onChanged(task.copy(delayDays = it)) }, { onChanged(task.copy(delayHours = it)) },
                { onChanged(task.copy(delayMinutes = it)) }, { onChanged(task.copy(delaySeconds = it)) })
        } else {
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
            Text("Wait after the schedule starts, then use this cron pattern.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            DurationFields(task.delayDays, task.delayHours, task.delayMinutes, task.delaySeconds,
                { onChanged(task.copy(delayDays = it)) }, { onChanged(task.copy(delayHours = it)) },
                { onChanged(task.copy(delayMinutes = it)) }, { onChanged(task.copy(delaySeconds = it)) })
            OutlinedTextField(task.cronExpression, { onChanged(task.copy(cronExpression = it)) }, label = { Text("Cron pattern after delay") }, supportingText = { Text("minute hour day month weekday · e.g. 0 9 * * *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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

@Composable
private fun DurationFields(
    days: String,
    hours: String,
    minutes: String,
    seconds: String,
    onDays: (String) -> Unit,
    onHours: (String) -> Unit,
    onMinutes: (String) -> Unit,
    onSeconds: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(days, { onDays(it.filter(Char::isDigit)) }, label = { Text("Days") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(hours, { onHours(it.filter(Char::isDigit)) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(minutes, { onMinutes(it.filter(Char::isDigit)) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true)
        OutlinedTextField(seconds, { onSeconds(it.filter(Char::isDigit)) }, label = { Text("Seconds") }, modifier = Modifier.weight(1f), singleLine = true)
    }
}

private fun parseDuration(days: String, hours: String, minutes: String, seconds: String): Duration {
    val dayCount = days.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val hourCount = hours.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val minuteCount = minutes.toLongOrNull()?.coerceAtLeast(0) ?: 0
    val secondCount = seconds.toLongOrNull()?.coerceAtLeast(0) ?: 0
    return Duration.ofDays(dayCount).plusHours(hourCount).plusMinutes(minuteCount).plusSeconds(secondCount)
}

private fun CronScheduleBundle?.triggerMode(): String = when (val trigger = this?.trigger) {
    is CronTrigger.Constitutive -> "MANUAL"
    is CronTrigger.Absolute -> "ABSOLUTE"
    is CronTrigger.Event, is CronTrigger.Preset -> "EVENT"
    else -> "MANUAL"
}

private fun CronScheduleBundle?.isConstitutive(): Boolean = this?.trigger is CronTrigger.Constitutive

private fun CronScheduleBundle?.absoluteStartText(): String = (this?.trigger as? CronTrigger.Absolute)
    ?.startAt?.toLocalDateTime()?.toString()?.replace('T', ' ') ?: LocalDateTime.now().withSecond(0).withNano(0).toString().replace('T', ' ')

private fun CronScheduleBundle?.triggerKey(): String = when (val trigger = this?.trigger) {
    is CronTrigger.Event -> trigger.eventKey
    is CronTrigger.Preset -> "preset.completed:${trigger.presetId}"
    else -> ""
}

private fun CronScheduleBundle?.isCustomEvent(): Boolean {
    val key = triggerKey()
    return key.isNotBlank() && !key.startsWith("preset.completed:") && !key.startsWith("protocol.completed:")
}

private fun CronScheduleBundle?.stopMode(): String = when (this?.stopRule) {
    is ScheduleStopRule.Absolute -> "ABSOLUTE"
    is ScheduleStopRule.Relative -> "RELATIVE"
    else -> "NEVER"
}

private fun CronScheduleBundle?.stopAbsoluteText(): String = (this?.stopRule as? ScheduleStopRule.Absolute)
    ?.stopAt?.toLocalDateTime()?.toString()?.replace('T', ' ') ?: ""

private fun CronScheduleBundle?.stopDurationPart(part: (Duration) -> Long): String = ((this?.stopRule as? ScheduleStopRule.Relative)?.delay?.let(part) ?: 0).toString()

private fun CronScheduleBundle?.stopDurationRemainder(divisor: Long, unit: Long): String {
    val seconds = (this?.stopRule as? ScheduleStopRule.Relative)?.delay?.seconds ?: 0
    return ((seconds % divisor) / unit).toString()
}

private fun toBuilderTask(task: CronTask): CronBuilderTask {
    val delay = task.relativeOffset
    return CronBuilderTask(
        name = task.name,
        recurrence = task.recurrence,
        timing = if (task.timing == ScheduleTimingMode.RELATIVE) CronBuilderTiming.RELATIVE else CronBuilderTiming.ABSOLUTE,
        cronExpression = task.cronExpression,
        target = when (task.target) { CronTaskTarget.NOTIFICATION -> CronBuilderTarget.NOTIFICATION; CronTaskTarget.PRESET -> CronBuilderTarget.PRESET; else -> CronBuilderTarget.PROTOCOL },
        targetId = task.targetId,
        message = task.notificationMessage,
        delayDays = delay.toDays().toString(),
        delayHours = (delay.toHours() % 24).toString(),
        delayMinutes = (delay.toMinutes() % 60).toString(),
        delaySeconds = (delay.seconds % 60).toString(),
        retries = task.retries.toString(),
        retryMinutes = task.retryInterval.toMinutes().toString()
    )
}
