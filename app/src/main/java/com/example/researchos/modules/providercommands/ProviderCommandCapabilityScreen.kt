package com.example.researchos.modules.providercommands

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONObject
import java.time.Instant

object ProviderCommandCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ProviderCommandRunMethod.ID
    override val title = "External command library"
    override val description = "Save, test, import, export, and run named commands for external Android apps."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val packageManager = appContext.packageManager
        val clipboard = appContext.getSystemService(ClipboardManager::class.java)
        val supplied = remember(context.request.settings, context.action.settings, context.request.invocationContext) {
            context.request.invocationContext.asMap(context.action.canonicalId) + context.request.settings + context.action.settings
        }
        var commands by remember { mutableStateOf(ProviderCommandRegistry.all(appContext)) }
        var selectedCommandId by rememberSaveable { mutableStateOf(supplied.firstPresent("provider_command_id", "input_provider_command_id", "command_id", "input_command_id")) }
        var selectedProviderId by rememberSaveable { mutableStateOf("") }
        var providerMenuOpen by rememberSaveable { mutableStateOf(false) }
        var commandMenuOpen by rememberSaveable { mutableStateOf(false) }
        var activeView by rememberSaveable { mutableStateOf("run") }
        var status by rememberSaveable { mutableStateOf("Choose a saved command, or teach ResearchOS a new one.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var pendingCommand by remember { mutableStateOf<ProviderCommand?>(null) }
        var pendingInputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

        var commandId by rememberSaveable { mutableStateOf("") }
        var displayName by rememberSaveable { mutableStateOf("") }
        var providerId by rememberSaveable { mutableStateOf("") }
        var packageName by rememberSaveable { mutableStateOf("") }
        var action by rememberSaveable { mutableStateOf(Intent.ACTION_VIEW) }
        var dataUriTemplate by rememberSaveable { mutableStateOf("") }
        var mimeType by rememberSaveable { mutableStateOf("") }
        var defaultsText by rememberSaveable { mutableStateOf("latitude=-1.2864\nlongitude=36.8172\nlabel=Clinic entrance") }
        var extrasText by rememberSaveable { mutableStateOf("") }
        var stability by rememberSaveable { mutableStateOf("experimental") }
        var offlineSupported by rememberSaveable { mutableStateOf(true) }

        LaunchedEffect(
            selectedCommandId,
            selectedProviderId,
            commandId,
            displayName,
            providerId,
            packageName,
            action,
            dataUriTemplate,
            mimeType,
            defaultsText,
            extrasText,
            stability,
            offlineSupported
        ) {
            context.onSettingsChanged(
                mapOf(
                    "provider_command_id" to selectedCommandId.ifBlank { commandId },
                    "provider_id" to selectedProviderId.ifBlank { providerId },
                    "command_id" to commandId,
                    "display_name" to displayName,
                    "package_name" to packageName,
                    "action" to action,
                    "data_uri_template" to dataUriTemplate,
                    "mime_type" to mimeType,
                    "default_values" to defaultsText,
                    "extras_template" to extrasText,
                    "stability" to stability,
                    "offline_supported" to offlineSupported.toString()
                )
            )
        }

        fun refresh() { commands = ProviderCommandRegistry.all(appContext) }

        fun load(command: ProviderCommand) {
            selectedCommandId = command.commandId
            selectedProviderId = command.providerId.ifBlank { "other" }
            commandId = command.commandId
            displayName = command.displayName
            providerId = command.providerId
            packageName = command.packageName
            action = command.action
            dataUriTemplate = command.dataUriTemplate
            mimeType = command.mimeType
            defaultsText = formatKeyValueLines(command.defaultValues)
            extrasText = formatKeyValueLines(command.extrasTemplate)
            stability = command.stability
            offlineSupported = command.offlineSupported
            status = "Loaded ${command.commandId}."
        }

        fun currentCommand(): ProviderCommand = ProviderCommand(
            commandId = commandId.trim(),
            displayName = displayName.trim(),
            providerId = providerId.trim(),
            packageName = packageName.trim(),
            action = action.trim().ifBlank { Intent.ACTION_VIEW },
            dataUriTemplate = dataUriTemplate.trim(),
            mimeType = mimeType.trim(),
            extrasTemplate = parseKeyValueLines(extrasText),
            defaultValues = parseKeyValueLines(defaultsText),
            stability = stability.trim().ifBlank { "experimental" },
            offlineSupported = offlineSupported
        )

        fun record(values: Map<String, String>, ok: Boolean) {
            val request = As100ProviderCommandRunMethod.request(
                action = As100ProviderCommandRunMethod.ID,
                context = context.request.invocationContext.asMap(As100ProviderCommandRunMethod.ID) + supplied,
                signals = emptyList(),
                inputs = emptyList()
            )
            result = As100ProviderCommandRunMethod.result(request, ProviderCommandOutcome(values, ok), context.request.invocationContext)
        }

        fun resultValues(
            command: ProviderCommand,
            inputs: Map<String, String>,
            outcome: String,
            resultCode: Int = 0,
            returnedValues: Map<String, String> = emptyMap(),
            returnedDataUri: String = "",
            returnedType: String = "",
            clip: String = "",
            error: String = ""
        ): Map<String, String> {
            val dataUri = applyTemplate(command.dataUriTemplate, command.defaultValues + inputs)
            val base = linkedMapOf(
                ProviderCommandFields.COMMAND_ID to command.commandId,
                ProviderCommandFields.STATUS to outcome,
                ProviderCommandFields.DISPLAY_NAME to command.displayName,
                ProviderCommandFields.PROVIDER_ID to command.providerId,
                ProviderCommandFields.PACKAGE_NAME to command.packageName,
                ProviderCommandFields.PACKAGE_VERSION to providerVersion(packageManager, command.packageName),
                ProviderCommandFields.INTERFACE_TYPE to command.interfaceType,
                ProviderCommandFields.STABILITY to command.stability,
                ProviderCommandFields.OFFLINE_SUPPORTED to command.offlineSupported.toString(),
                ProviderCommandFields.ACTION to command.action,
                ProviderCommandFields.DATA_URI to dataUri,
                ProviderCommandFields.INPUTS_JSON to JSONObject(command.defaultValues + inputs).toString(),
                ProviderCommandFields.RESULT_CODE to resultCode.toString(),
                ProviderCommandFields.RETURNED_DATA_URI to returnedDataUri,
                ProviderCommandFields.RETURNED_TYPE to returnedType,
                ProviderCommandFields.RETURNED_VALUES_JSON to JSONObject(returnedValues).toString(),
                ProviderCommandFields.RETURNED_CLIPDATA to clip,
                ProviderCommandFields.LAUNCHED_TIME_ISO to Instant.now().toString(),
                ProviderCommandFields.RESULT_TIME_ISO to Instant.now().toString(),
                ProviderCommandFields.ERROR to error
            )
            returnedValues.forEach { (key, value) ->
                base["provider_return_${key.safeReturnKey()}"] = value
            }
            return base
        }

        fun providerIntent(command: ProviderCommand, inputs: Map<String, String>, packageOverride: String?): Intent =
            Intent(command.action.ifBlank { Intent.ACTION_VIEW }).apply {
                packageOverride?.takeIf(String::isNotBlank)?.let { setPackage(it) }
                val dataUri = applyTemplate(command.dataUriTemplate, inputs)
                when {
                    dataUri.isNotBlank() && command.mimeType.isNotBlank() -> setDataAndType(Uri.parse(dataUri), command.mimeType)
                    dataUri.isNotBlank() -> data = Uri.parse(dataUri)
                    command.mimeType.isNotBlank() -> type = command.mimeType
                }
                command.extrasTemplate.forEach { (key, template) -> putExtra(key, applyTemplate(template, inputs)) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { activityResult: ActivityResult ->
            val command = pendingCommand
            if (command == null) {
                status = "Provider returned, but no pending command was active."
                return@rememberLauncherForActivityResult
            }
            val returned = activityResult.data?.extras?.toFlatStringMap().orEmpty()
            val returnedDataUri = activityResult.data?.dataString.orEmpty()
            val returnedType = activityResult.data?.type.orEmpty()
            val clipText = activityResult.data?.clipData?.let { clip ->
                (0 until clip.itemCount).joinToString("\n") { i -> clip.getItemAt(i).coerceToText(appContext).toString() }
            }.orEmpty()
            val ok = activityResult.resultCode != android.app.Activity.RESULT_CANCELED
            val hasReturnedPayload = returned.isNotEmpty() || returnedDataUri.isNotBlank() || clipText.isNotBlank()
            val outcome = when {
                ok -> "returned"
                hasReturnedPayload -> "returned_with_cancelled_code"
                else -> "cancelled"
            }
            ProviderCommandRegistry.recordTest(appContext, command.commandId, outcome)
            refresh()
            status = "Command ${command.commandId} $outcome${if (returned.isNotEmpty()) " with ${returned.size} returned field${if (returned.size == 1) "" else "s"}" else ""}."
            record(
                resultValues(
                    command = command,
                    inputs = pendingInputs,
                    outcome = outcome,
                    resultCode = activityResult.resultCode,
                    returnedValues = returned,
                    returnedDataUri = returnedDataUri,
                    returnedType = returnedType,
                    clip = clipText
                ),
                ok || hasReturnedPayload
            )
        }

        fun runCommand(command: ProviderCommand, suppliedInputs: Map<String, String> = emptyMap()) {
            if (!command.enabled) {
                status = "Command is disabled."
                record(resultValues(command, suppliedInputs, "failed", error = "Command is disabled."), false)
                return
            }
            val inputs = command.defaultValues + suppliedInputs
            val packageAttempts = (command.packageName.packageAlternatives() + "").distinct()
            var lastError = ""
            for (packageAttempt in packageAttempts) {
                val intent = providerIntent(command, inputs, packageAttempt.takeIf(String::isNotBlank))
                val resolvedPackage = intent.resolveActivity(packageManager)?.packageName.orEmpty()
                val launchedCommand = command.copy(
                    packageName = packageAttempt.ifBlank { resolvedPackage.ifBlank { command.packageName } }
                )
                pendingCommand = launchedCommand
                pendingInputs = suppliedInputs
                status = "Launching ${command.commandId}${packageAttempt.takeIf(String::isNotBlank)?.let { " via $it" }.orEmpty()}…"
                try {
                    launcher.launch(intent)
                    return
                } catch (error: ActivityNotFoundException) {
                    lastError = error.message ?: error::class.java.simpleName
                } catch (error: IllegalArgumentException) {
                    lastError = error.message ?: error::class.java.simpleName
                }
            }
            pendingCommand = null
            pendingInputs = emptyMap()
            val tried = packageAttempts.filter(String::isNotBlank).joinToString(", ").ifBlank { "package-free Android resolution" }
            val error = "No installed app can handle this command. Tried: $tried."
            status = error
            ProviderCommandRegistry.recordTest(appContext, command.commandId, "failed", lastError.ifBlank { error })
            refresh()
            record(resultValues(command, suppliedInputs, "failed", error = lastError.ifBlank { error }), false)
        }

        fun runSelected() {
            val id = selectedCommandId.trim()
            val command = commands.firstOrNull { it.commandId == id } ?: run {
                status = "Choose a saved command first."
                return
            }
            val externalInputs = supplied.filterKeys { it.startsWith("input_") }
                .mapKeys { (key, _) -> key.removePrefix("input_") }
                .filterKeys { it !in setOf("provider_command_id", "command_id") }
            runCommand(command, externalInputs)
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && selectedCommandId.isNotBlank()) runSelected()
        }

        LaunchedEffect(commands, selectedCommandId) {
            if (selectedProviderId.isBlank()) {
                selectedProviderId = commands.firstOrNull { it.commandId == selectedCommandId }?.providerId?.ifBlank { "other" }
                    ?: commands.firstOrNull()?.providerId?.ifBlank { "other" }
                    ?: ""
            }
        }

        val providerOptions = commands
            .map { it.providerId.ifBlank { "other" } }
            .distinct()
            .sorted()
        val filteredCommands = commands.filter { command ->
            selectedProviderId.isBlank() || command.providerId.ifBlank { "other" } == selectedProviderId
        }
        val selectedCommand = commands.firstOrNull { it.commandId == selectedCommandId }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; runSelected() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Teach ResearchOS an app command once, save it under a stable name, then run it by name from forms, schedules, or the debug screen. ResearchOS also includes a small built-in command catalogue for common apps such as OsmAnd.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))

            Row {
                if (activeView == "run") {
                    Button(onClick = { activeView = "run" }, modifier = Modifier.weight(1f)) { Text("Run command") }
                } else {
                    OutlinedButton(onClick = { activeView = "run" }, modifier = Modifier.weight(1f)) { Text("Run command") }
                }
                Spacer(Modifier.padding(4.dp))
                if (activeView == "build") {
                    Button(onClick = { activeView = "build" }, modifier = Modifier.weight(1f)) { Text("Build/edit") }
                } else {
                    OutlinedButton(onClick = { activeView = "build" }, modifier = Modifier.weight(1f)) { Text("Build/edit") }
                }
            }
            Spacer(Modifier.height(12.dp))

            Text("App / provider", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { providerMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedProviderId.ifBlank { "Choose app/provider" }, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false }, modifier = Modifier.fillMaxWidth(0.92f)) {
                providerOptions.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider) },
                        onClick = {
                            selectedProviderId = provider
                            val first = commands.firstOrNull { it.providerId.ifBlank { "other" } == provider }
                            if (first != null) load(first)
                            providerMenuOpen = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Text("Command", fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = { commandMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selectedCommandId.ifBlank { "Choose saved command" }, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(expanded = commandMenuOpen, onDismissRequest = { commandMenuOpen = false }, modifier = Modifier.fillMaxWidth(0.92f)) {
                filteredCommands.forEach { command ->
                    DropdownMenuItem(
                        text = { Column { Text(command.displayName.ifBlank { command.commandId }); Text("${command.commandId} · ${command.modeLabel()}", style = MaterialTheme.typography.bodySmall) } },
                        onClick = { load(command); commandMenuOpen = false }
                    )
                }
            }
            selectedCommand?.let { command ->
                Text(command.modeDescription(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(8.dp))

            if (activeView == "run") {
                Row {
                    Button(onClick = { runSelected() }, modifier = Modifier.weight(1f)) { Text("Run / test") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = {
                        val payload = ProviderCommandRegistry.exportBundle(appContext, selectedCommandId.takeIf(String::isNotBlank))
                        clipboard.setPrimaryClip(ClipData.newPlainText("ResearchOS provider command", payload))
                        status = "Command copied to clipboard."
                    }, modifier = Modifier.weight(1f)) { Text("Copy command") }
                }
                Spacer(Modifier.height(10.dp))
                LazyColumn(modifier = Modifier.fillMaxWidth().height(250.dp)) {
                    items(filteredCommands, key = { it.commandId }) { command ->
                        ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(command.displayName.ifBlank { command.commandId }, fontWeight = FontWeight.SemiBold)
                                Text(command.commandId, style = MaterialTheme.typography.bodySmall)
                                Text(command.modeDescription(), style = MaterialTheme.typography.bodySmall)
                                Text("Last test: ${command.lastTestStatus.ifBlank { "never" }} ${command.lastTestError}", style = MaterialTheme.typography.bodySmall)
                                Row {
                                    OutlinedButton(onClick = { load(command) }) { Text("Select") }
                                    Spacer(Modifier.padding(4.dp))
                                    Button(onClick = { load(command); runCommand(command) }) { Text("Run") }
                                }
                            }
                        }
                    }
                }
            } else {
                Text("Command builder", fontWeight = FontWeight.SemiBold)
                Text("Use this when learning or repairing an app command. The run view stays clean for everyday use.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(commandId, { commandId = it }, label = { Text("Command ID, e.g. OsmAnd::navigate_to") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(providerId, { providerId = it }, label = { Text("Provider ID, e.g. osmand") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(packageName, { packageName = it }, label = { Text("Package name(s), separated by |") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                ProviderIntentActionChooser(action = action, onActionSelected = { action = it })
                OutlinedTextField(action, { action = it }, label = { Text("Intent action") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(dataUriTemplate, { dataUriTemplate = it }, label = { Text("URI template") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(mimeType, { mimeType = it }, label = { Text("MIME type (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(defaultsText, { defaultsText = it }, label = { Text("Default values, one key=value per line") }, modifier = Modifier.fillMaxWidth().height(110.dp))
                OutlinedTextField(extrasText, { extrasText = it }, label = { Text("Extras template, one key=value per line") }, modifier = Modifier.fillMaxWidth().height(90.dp))
                CommandStabilityChooser(stability = stability, onStabilitySelected = { stability = it })
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text("Offline supported", modifier = Modifier.weight(1f))
                    Switch(checked = offlineSupported, onCheckedChange = { offlineSupported = it })
                }
                Row {
                    Button(onClick = {
                        val saved = currentCommand()
                        ProviderCommandRegistry.save(appContext, saved)
                        refresh()
                        selectedCommandId = saved.stableId
                        selectedProviderId = saved.providerId.ifBlank { "other" }
                        status = "Saved ${saved.stableId}."
                    }, modifier = Modifier.weight(1f)) { Text("Save") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = {
                        if (selectedCommandId.isNotBlank()) {
                            ProviderCommandRegistry.remove(appContext, selectedCommandId)
                            refresh()
                            selectedCommandId = ""
                            status = "Saved override removed."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Remove saved") }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    OutlinedButton(onClick = {
                        if (selectedCommandId.isNotBlank()) {
                            ProviderCommandRegistry.restoreBuiltIn(appContext, selectedCommandId)
                            refresh()
                            commands.firstOrNull { it.commandId == selectedCommandId }?.let(::load)
                            status = "Restored bundled command."
                        }
                    }, modifier = Modifier.weight(1f)) { Text("Restore bundled") }
                    Spacer(Modifier.padding(4.dp))
                    Button(onClick = { runCommand(currentCommand()) }, modifier = Modifier.weight(1f)) { Text("Test edited") }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    OutlinedButton(onClick = {
                        val payload = ProviderCommandRegistry.exportBundle(appContext)
                        clipboard.setPrimaryClip(ClipData.newPlainText("ResearchOS provider command registry", payload))
                        status = "Whole command registry copied to clipboard."
                    }, modifier = Modifier.weight(1f)) { Text("Copy registry") }
                    Spacer(Modifier.padding(4.dp))
                    OutlinedButton(onClick = {
                        val payload = clipboard.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString().orEmpty()
                        if (payload.isBlank()) status = "Clipboard is empty." else runCatching {
                            val count = ProviderCommandRegistry.importBundle(appContext, payload)
                            refresh()
                            status = "Imported $count command${if (count == 1) "" else "s"}."
                        }.onFailure { error -> status = "Import failed: ${error.message.orEmpty()}" }
                    }, modifier = Modifier.weight(1f)) { Text("Import") }
                }
            }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100ProviderCommandRunMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Run saved command",
                        description = "Run a named external command with values from the caller.",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='provider.command.run',input_provider_command_id='OsmAnd::navigate_to',input_latitude='-1.2864',input_longitude='36.8172',input_label='Clinic entrance',return_mode='flat')"
                    )
                )
            )
        }
    }
}

private fun Map<String, String>.firstPresent(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun ProviderCommand.modeLabel(): String = when (interfaceType) {
    "activity_result_intent" -> "returns data"
    "launch_only_intent" -> "launch-only"
    else -> interfaceType.ifBlank { "intent" }
}

private fun ProviderCommand.modeDescription(): String = when (interfaceType) {
    "activity_result_intent" -> "Returns data to ResearchOS when the external app finishes."
    "launch_only_intent" -> "Launch-only: opens the external app, but completion is not confirmed by ResearchOS."
    else -> "Generic intent command."
}

@Composable
private fun ProviderIntentActionChooser(action: String, onActionSelected: (String) -> Unit) {
    val rows = listOf(
        listOf(Intent.ACTION_VIEW to "View URI", Intent.ACTION_MAIN to "Open app"),
        listOf(Intent.ACTION_SEND to "Send", Intent.ACTION_SENDTO to "Send to"),
        listOf(Intent.ACTION_GET_CONTENT to "Pick content", Intent.ACTION_EDIT to "Edit")
    )
    Text("Intent action preset", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (value, label) ->
                val selected = action == value
                if (selected) {
                    Button(onClick = { onActionSelected(value) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ $label") }
                } else {
                    OutlinedButton(onClick = { onActionSelected(value) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun CommandStabilityChooser(stability: String, onStabilitySelected: (String) -> Unit) {
    val options = listOf(
        "stable" to "Stable",
        "experimental" to "Experimental",
        "launch_only" to "Launch-only",
        "unknown" to "Unknown"
    )
    Text("Command status", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
    Row(Modifier.fillMaxWidth()) {
        options.take(2).forEach { (value, label) ->
            StabilityButton(value, label, stability, onStabilitySelected, Modifier.weight(1f).padding(2.dp))
        }
    }
    Row(Modifier.fillMaxWidth()) {
        options.drop(2).forEach { (value, label) ->
            StabilityButton(value, label, stability, onStabilitySelected, Modifier.weight(1f).padding(2.dp))
        }
    }
}

@Composable
private fun StabilityButton(
    value: String,
    label: String,
    selectedValue: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (selectedValue == value) {
        Button(onClick = { onSelected(value) }, modifier = modifier) { Text("✓ $label") }
    } else {
        OutlinedButton(onClick = { onSelected(value) }, modifier = modifier) { Text(label) }
    }
}

private fun Bundle.toFlatStringMap(): Map<String, String> =
    keySet().associateWith { key -> bundleValueToString(get(key)) }

private fun bundleValueToString(value: Any?): String = when (value) {
    null -> ""
    is Bundle -> JSONObject(value.toFlatStringMap()).toString()
    is Array<*> -> value.joinToString(",") { bundleValueToString(it) }
    is BooleanArray -> value.joinToString(",")
    is ByteArray -> value.joinToString(",")
    is CharArray -> value.joinToString(",")
    is DoubleArray -> value.joinToString(",")
    is FloatArray -> value.joinToString(",")
    is IntArray -> value.joinToString(",")
    is LongArray -> value.joinToString(",")
    is ShortArray -> value.joinToString(",")
    else -> value.toString()
}

private fun String.safeReturnKey(): String =
    lowercase()
        .replace(Regex("[^a-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "value" }

private fun String.packageAlternatives(): List<String> =
    split("|", ",", ";", "\n")
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

@Suppress("DEPRECATION")
private fun providerVersion(packageManager: PackageManager, packageName: String): String {
    val candidates = packageName.packageAlternatives()
    if (candidates.isEmpty()) return ""
    return candidates.firstNotNullOfOrNull { candidate -> runCatching {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(candidate, PackageManager.PackageInfoFlags.of(0)).versionName.orEmpty()
        } else {
            packageManager.getPackageInfo(candidate, 0).versionName.orEmpty()
        }
    }.getOrNull()?.takeIf(String::isNotBlank) }.orEmpty()
}
