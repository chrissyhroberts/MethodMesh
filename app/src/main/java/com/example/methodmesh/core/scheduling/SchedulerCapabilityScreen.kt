package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100MethodRegistry
import com.example.methodmesh.core.methodmesh.runtime.CapabilityConfigurationRegistry
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.settings.MethodSetting
import org.json.JSONObject
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import com.example.methodmesh.platform.externalforms.ExternalForm
import com.example.methodmesh.platform.externalforms.ExternalFormCatalog
import com.example.methodmesh.platform.externalforms.ExternalProject
import com.example.methodmesh.platform.externalforms.ExternalProjectRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalTime

object SchedulerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Create schedule"
    override val description = "Schedule an ODK form, web form, or MethodMesh process."

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val supplied = remember(context.action.settings, context.request.settings) { context.request.settings + context.action.settings }
        fun suppliedValue(key: String): String = supplied[key].orEmpty().ifBlank { supplied["input_$key"].orEmpty() }
        var name by remember { mutableStateOf(suppliedValue("schedule_name")) }
        val existingId = suppliedValue("schedule_id")
        var target by remember { mutableStateOf(suppliedValue("schedule_target").ifBlank { "ODK_FORM" }) }
        var targetValue by remember { mutableStateOf(suppliedValue("schedule_target_value")) }
        var projectId by remember { mutableStateOf(supplied["input_project_id"].orEmpty()) }
        var packageName by remember { mutableStateOf(supplied["input_project_package"].orEmpty()) }
        var chainId by remember { mutableStateOf(suppliedValue("schedule_chain_id")) }
        var targetMenuOpen by remember { mutableStateOf(false) }
        var retries by remember { mutableStateOf(suppliedValue("schedule_retry_count").ifBlank { "0" }) }
        var retryInterval by remember { mutableStateOf(suppliedValue("schedule_retry_interval_minutes").ifBlank { "60" }) }
        var notificationTitle by remember { mutableStateOf(suppliedValue("schedule_notification_title")) }
        var notificationMessage by remember { mutableStateOf(suppliedValue("schedule_notification_message")) }
        val initialCronParts = remember { suppliedValue("schedule_cron").trim().split(Regex("\\s+")).let { if (it.size == 5) it else List(5) { "" } } }
        var cronMinute by remember { mutableStateOf(initialCronParts[0]) }
        var cronHour by remember { mutableStateOf(initialCronParts[1]) }
        var cronDayOfMonth by remember { mutableStateOf(initialCronParts[2]) }
        var cronMonth by remember { mutableStateOf(initialCronParts[3]) }
        var cronDayOfWeek by remember { mutableStateOf(initialCronParts[4]) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Configure the schedule, then save it.") }
        var savedProjects by remember { mutableStateOf(ExternalProjectRegistry.load(androidContext)) }
        var availableForms by remember { mutableStateOf(emptyList<ExternalForm>()) }
        val installedCapabilities = remember { As100MethodRegistry.all().map { it.id }.filterNot { it.startsWith("scheduler.") }.distinct().sorted() }
        var savedPresets by remember { mutableStateOf(ProtocolLibraryRepository.presets(androidContext)) }
        var savedProtocols by remember { mutableStateOf(ProtocolLibraryRepository.protocols(androidContext)) }
        var actionTypes by remember { mutableStateOf(List(5) { index -> if (index == 0) target else "ODK_FORM" }) }
        var actionValues by remember { mutableStateOf(List(5) { index -> if (index == 0) targetValue else "" }) }
        var actionModifiers by remember { mutableStateOf(List(5) { index -> if (index == 0) suppliedValue("schedule_target_settings") else "" }) }
        var expandedActions by remember { mutableStateOf(setOf(0)) }

        val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            val uri = returned.data?.data ?: return@rememberLauncherForActivityResult
            val selectedProject = uri.getQueryParameter("projectId").orEmpty()
            if (selectedProject.isBlank()) {
                status = "The selected app did not return a project ID."
            } else {
                val selected = ExternalFormCatalog.describe(androidContext, uri, selectedProject)
                projectId = selectedProject
                packageName = selected?.packageName.orEmpty()
                selected?.let {
                    targetValue = it.id
                    actionValues = actionValues.toMutableList().also { values -> values[0] = it.id }
                }
                ExternalProjectRegistry.save(androidContext, ExternalProject(selectedProject, selectedProject, packageName))
                savedProjects = ExternalProjectRegistry.load(androidContext)
                status = "Project selected. Choose a form below."
            }
        }

        LaunchedEffect(projectId, packageName, target) {
            availableForms = if (target == "ODK_FORM" && projectId.isNotBlank()) {
                withContext(Dispatchers.IO) { ExternalFormCatalog.list(androidContext, projectId, packageName) }
            } else emptyList()
        }

        fun create() {
            val selectedActions = actionValues.mapIndexedNotNull { index, value -> value.trim().takeIf { it.isNotBlank() }?.let { Triple(actionTypes[index], it, actionModifiers[index].trim()) } }
            if (name.isBlank() || selectedActions.isEmpty()) { status = "Name and at least one configured action are required."; return }
            val cronParts = listOf(cronMinute, cronHour, cronDayOfMonth, cronMonth, cronDayOfWeek).map(String::trim)
            val requestedCron = if (cronParts.all(String::isBlank)) "" else cronParts.joinToString(" ") { it.ifBlank { "*" } }
            if (requestedCron.isNotBlank()) {
                val cronCheck = runCatching { CronSchedule.next(requestedCron, java.time.ZonedDateTime.now()) }
                if (cronCheck.isFailure) {
                    status = "Invalid cron expression. Use five fields, for example: */5 * * * *"
                    return
                }
            }
            if (requestedCron.isBlank()) { status = "Enter all five cron fields."; return }
            val effectiveChainId = if (selectedActions.size > 1) chainId.ifBlank { java.util.UUID.randomUUID().toString() } else chainId
            val schedules = selectedActions.mapIndexed { index, action -> ResearchSchedule(
                id = if (index == 0) existingId.ifBlank { java.util.UUID.randomUUID().toString() } else java.util.UUID.randomUUID().toString(),
                name = if (selectedActions.size > 1) "$name ${index + 1}" else name,
                target = SchedulerTarget.valueOf(action.first), targetValue = action.second, targetSettings = action.third,
                projectId = projectId, packageName = packageName, frequency = SchedulerFrequency.DAILY,
                chainId = effectiveChainId, chainOrder = index,
                hour = 0, minute = 0, dayOfWeek = 1, dayOfMonth = 1, ordinal = 1, customWeekday = 1,
                retryCount = retries.toIntOrNull() ?: 0, retryIntervalMinutes = retryInterval.toIntOrNull() ?: 60,
                notificationTitle = notificationTitle, notificationMessage = notificationMessage, cronExpression = requestedCron
            ) }
            runCatching { schedules.forEach { SchedulerRepository.save(androidContext, it) } }.onFailure {
                status = it.message ?: "Could not save schedule."
                return
            }
            if (Build.VERSION.SDK_INT >= 33 && androidContext is Activity) androidContext.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7401)
            val execution = As100SchedulerMethod.result(As100SchedulerMethod.request(capabilityId, context.request.invocationContext.asMap(capabilityId) + context.action.settings), SchedulerOutcome(schedules.first(), "created"), context.request.invocationContext)
            if (context.submitsImmediately) onConfirmed(execution) else result = execution
            status = "${schedules.size} schedule(s) created. Next run: ${runCatching { schedules.first().nextOccurrence() }.getOrElse { "unavailable" }}"
        }

        LaunchedEffect(context.startsImmediately) { if (context.startsImmediately) create() }
        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Text("Schedules use Android alarms and remain local to this device.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(name, { name = it }, label = { Text("Schedule name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Actions (in order)", style = MaterialTheme.typography.titleSmall)
            actionTypes.forEachIndexed { index, actionType ->
                val expanded = index in expandedActions
                OutlinedButton(
                    onClick = { expandedActions = if (expanded) expandedActions - index else expandedActions + index },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (expanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Text("Action ${index + 1}  ·  ${actionTypeLabel(actionType)}")
                }
                if (expanded) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            SchedulerDropdownField(
                                "Action type",
                                actionTypeLabel(actionType),
                                listOf(
                                    "XLSForm" to "ODK_FORM",
                                    "Web form" to "WEB_FORM",
                                    "MethodMesh capability" to "CAPABILITY",
                                    "Saved capability preset" to "PRESET",
                                    "Saved protocol" to "PROTOCOL",
                                    "Clipboard text" to "CLIPBOARD"
                                )
                            ) { selected ->
                                actionTypes = actionTypes.toMutableList().also { it[index] = selected }
                        actionValues = actionValues.toMutableList().also { it[index] = "" }
                        actionModifiers = actionModifiers.toMutableList().also { it[index] = "" }
                            }
                            when (actionType) {
                        "ODK_FORM" -> {
                            SchedulerDropdownField("Project", savedProjects.firstOrNull { it.id == projectId }?.name.orEmpty().ifBlank { "Choose project" }, savedProjects.map { it.name to it.id }) { selectedId ->
                                savedProjects.firstOrNull { it.id == selectedId }?.let {
                                    projectId = it.id
                                    packageName = it.packageName
                                    availableForms = emptyList()
                                    actionValues = actionValues.toMutableList().also { values -> values[index] = "" }
                                }
                            }
                            Button(onClick = { projectPicker.launch(Intent(Intent.ACTION_PICK).setType("vnd.android.cursor.dir/vnd.odk.form")) }, modifier = Modifier.fillMaxWidth()) { Text("Find project/form in ODK or Kobo") }
                            SchedulerDropdownField("Form", availableForms.firstOrNull { it.id == actionValues[index] }?.name ?: actionValues[index].ifBlank { "Choose form" }, availableForms.map { it.name to it.id }) { value -> actionValues = actionValues.toMutableList().also { it[index] = value } }
                        }
                        "CAPABILITY" -> {
                            SchedulerDropdownField(
                                "Capability",
                                actionValues[index].ifBlank { "Choose installed capability" },
                                installedCapabilities.map { it to it }
                            ) { value -> actionValues = actionValues.toMutableList().also { it[index] = value } }
                            CapabilitySettingsCard(actionValues[index], actionModifiers[index]) { value ->
                                actionModifiers = actionModifiers.toMutableList().also { it[index] = value }
                            }
                        }
                        "PRESET" -> {
                            SchedulerDropdownField(
                                "Preset",
                                savedPresets.firstOrNull { it.id == actionValues[index] }?.name ?: actionValues[index].ifBlank { "Choose saved preset" },
                                savedPresets.map { it.name to it.id }
                            ) { value -> actionValues = actionValues.toMutableList().also { it[index] = value } }
                            if (savedPresets.isEmpty()) Text("No saved presets yet. Create one from the Protocol library or import a library bundle.", style = MaterialTheme.typography.bodySmall)
                        }
                        "PROTOCOL" -> {
                            SchedulerDropdownField(
                                "Protocol",
                                savedProtocols.firstOrNull { it.id == actionValues[index] }?.name ?: actionValues[index].ifBlank { "Choose saved protocol" },
                                savedProtocols.map { it.name to it.id }
                            ) { value -> actionValues = actionValues.toMutableList().also { it[index] = value } }
                            if (savedProtocols.isEmpty()) Text("No saved protocols yet. Import a library bundle or create one in the Protocol library.", style = MaterialTheme.typography.bodySmall)
                        }
                        else -> OutlinedTextField(actionValues[index], { value -> actionValues = actionValues.toMutableList().also { it[index] = value } }, label = { Text(if (actionType == "WEB_FORM") "Web form URL" else "Clipboard text") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            OutlinedTextField(retries, { retries = it }, label = { Text("Reminder retries") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(retryInterval, { retryInterval = it }, label = { Text("Minutes between reminders") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notificationTitle, { notificationTitle = it }, label = { Text("Notification title (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notificationMessage, { notificationMessage = it }, label = { Text("Notification message (optional)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Text("Cron schedule (optional; overrides frequency fields)", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(cronMinute, { cronMinute = it }, label = { Text("Minute") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.padding(2.dp))
                OutlinedTextField(cronHour, { cronHour = it }, label = { Text("Hour") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.padding(2.dp))
                OutlinedTextField(cronDayOfMonth, { cronDayOfMonth = it }, label = { Text("Day") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(cronMonth, { cronMonth = it }, label = { Text("Month") }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.padding(2.dp))
                OutlinedTextField(cronDayOfWeek, { cronDayOfWeek = it }, label = { Text("Weekday") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Text("Example: minute */5, hour *, day *, month *, weekday *", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { create() }, modifier = Modifier.fillMaxWidth()) { Text("Save schedule") }
            Spacer(Modifier.height(8.dp)); Text(status)
            IntentExampleDropdown(capabilityId, listOf(IntentExample("Create a daily ODK schedule", "Schedule an ODK form", "com.example.methodmesh.EXECUTE_METHOD(method_id='scheduler.create',schedule_name='Daily check',schedule_target='ODK_FORM',schedule_target_value='my_form_id',input_project_id='my_project_id',schedule_frequency='DAILY',schedule_time='09:00',schedule_retry_count='2',return_mode='flat')")))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulerDropdownOptions(
    label: String,
    selectedLabel: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    SchedulerDropdownField(label, selectedLabel, options.map { it to it }, onSelected)
}

private fun actionTypeLabel(type: String): String = when (type) {
    "ODK_FORM" -> "XLSForm"
    "WEB_FORM" -> "Web form"
    "CAPABILITY" -> "MethodMesh capability"
    "PRESET" -> "Saved preset"
    "PROTOCOL" -> "Saved protocol"
    "CLIPBOARD" -> "Clipboard text"
    else -> "Choose action type"
}

@Composable
private fun CapabilitySettingsCard(methodId: String, raw: String, onChanged: (String) -> Unit) {
    val schema = remember(methodId) { CapabilityConfigurationRegistry.settingsFor(methodId) }
    var values by remember(methodId, raw) {
        mutableStateOf(buildMap {
            val existing = runCatching { JSONObject(raw) }.getOrNull()
            if (schema.isEmpty() && existing != null) {
                existing.keys().forEach { key -> put(key, existing.optString(key)) }
            }
            schema.forEach { setting ->
                val value = existing?.takeIf { it.has(setting.id) }?.optString(setting.id) ?: when (setting) {
                    is MethodSetting.BooleanSetting -> setting.defaultValue.toString()
                    is MethodSetting.IntSetting -> setting.defaultValue.toString()
                    is MethodSetting.FloatSetting -> setting.defaultValue.toString()
                    is MethodSetting.TextSetting -> setting.defaultValue
                    is MethodSetting.ChoiceSetting -> setting.defaultValue
                    is MethodSetting.MultiChoiceSetting -> setting.defaultValue
                }
                put(setting.id, value)
            }
        }.toMutableMap())
    }
    fun update(id: String, value: String) {
        values = values.toMutableMap().also { it[id] = value }
        onChanged(JSONObject(values as Map<*, *>).toString())
    }
    Surface(Modifier.fillMaxWidth().padding(top = 6.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(8.dp)) {
            Text(if (schema.isEmpty()) "Capability parameters" else "Capability settings", style = MaterialTheme.typography.labelLarge)
            if (schema.isEmpty()) {
                Text("This capability has no typed settings card yet. Add the input parameters it accepts.", style = MaterialTheme.typography.bodySmall)
                values.entries.toList().forEach { (key, value) ->
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedTextField(key, {}, label = { Text("Parameter") }, modifier = Modifier.weight(1f), readOnly = true, singleLine = true)
                        Spacer(Modifier.padding(2.dp))
                        OutlinedTextField(value, { update(key, it) }, label = { Text("Value") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                }
                var newKey by remember(methodId, raw) { mutableStateOf("") }
                var newValue by remember(methodId, raw) { mutableStateOf("") }
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(newKey, { newKey = it }, label = { Text("New parameter") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.padding(2.dp))
                    OutlinedTextField(newValue, { newValue = it }, label = { Text("Value") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedButton(onClick = {
                    if (newKey.isNotBlank()) {
                        update(newKey.trim(), newValue)
                        newKey = ""
                        newValue = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("Add parameter") }
            }
            schema.forEach { setting ->
                when (setting) {
                    is MethodSetting.BooleanSetting -> Row(Modifier.fillMaxWidth()) {
                        Checkbox(values[setting.id].toBoolean(), { update(setting.id, it.toString()) })
                        Text(setting.label, modifier = Modifier.padding(top = 12.dp))
                    }
                    is MethodSetting.ChoiceSetting -> SchedulerDropdownField(setting.label, values[setting.id].orEmpty(), setting.choices.map { it to it }) { update(setting.id, it) }
                    is MethodSetting.MultiChoiceSetting -> SchedulerMultiChoiceField(setting, values[setting.id].orEmpty()) { update(setting.id, it) }
                    else -> OutlinedTextField(
                        values[setting.id].orEmpty(),
                        { update(setting.id, it) },
                        label = { Text(setting.label) },
                        supportingText = { setting.description?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchedulerDropdownField(
    label: String,
    selectedLabel: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: $selectedLabel")
    }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    items(options) { (display, value) ->
                        DropdownMenuItem(text = { Text(display) }, onClick = { onSelected(value); expanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun SchedulerMultiChoiceField(
    setting: MethodSetting.MultiChoiceSetting,
    value: String,
    onChanged: (String) -> Unit
) {
    val selected = value
        .split(setting.delimiter, ",", ";")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toSet()
    Column(Modifier.fillMaxWidth()) {
        Text(setting.label, style = MaterialTheme.typography.labelLarge)
        if (setting.emptyMeansAll) {
            Row(Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = selected.isEmpty(),
                    onCheckedChange = { checked -> if (checked) onChanged("") }
                )
                Text("Automatic / all", modifier = Modifier.padding(top = 12.dp))
            }
        }
        setting.choices.forEach { choice ->
            Row(Modifier.fillMaxWidth()) {
                Checkbox(
                    checked = choice in selected,
                    enabled = !(setting.emptyMeansAll && selected.isEmpty()),
                    onCheckedChange = { nowChecked ->
                        val next = selected.toMutableSet()
                        if (nowChecked) next += choice else next -= choice
                        onChanged(next.joinToString(setting.delimiter))
                    }
                )
                Text(choice, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}
