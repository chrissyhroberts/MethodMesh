package com.example.researchos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.Method
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.MethodOutputValidator
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsRepository
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.LaunchConfigParser
import com.example.researchos.transport.LaunchTarget
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.ReturnMode
import com.example.researchos.transport.android.AndroidIntentUriBuilder
import com.example.researchos.transport.android.KotlinIntentSnippetBuilder
import com.example.researchos.transport.odk.OdkAppearanceBuilder
import com.example.researchos.transport.odk.OdkIntentColumnBuilder

@Composable
fun MethodCard(
    method: Method,
    modifier: Modifier = Modifier
) {
    val settingsState = remember(method.manifest.id) {
        SettingsState(
            settings = method.settings,
            onValueChanged = { settingId, value ->
                SettingsRepository.save(
                    methodId = method.manifest.id,
                    settingId = settingId,
                    value = value
                )
            }
        )
    }

    LaunchedEffect(method.manifest.id) {
        val restoredSettings = SettingsRepository.load(
            methodId = method.manifest.id,
            settings = method.settings
        )
        settingsState.restore(restoredSettings)
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = method.manifest.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = method.manifest.description,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "${method.manifest.status} • v${method.manifest.version}",
                style = MaterialTheme.typography.labelMedium
            )

            ExpandableSection(
                title = "Demo",
                initiallyExpanded = false
            ) {
                method.Demo(
                    settingsState = settingsState
                )
            }

            ExpandableSection(
                title = "Settings"
            ) {
                SettingsRenderer(
                    settings = method.settings,
                    settingsState = settingsState
                )
            }

            ExpandableSection(
                title = "Help"
            ) {
                method.Help()
            }

            ExpandableSection(
                title = "ResearchOS panels",
                initiallyExpanded = false
            ) {
                ResearchOSPanels(
                    method = method,
                    settingsState = settingsState
                )
            }

            ExpandableSection(
                title = "Transport/debug output"
            ) {
                MethodOutputPanel(
                    method = method,
                    settingsState = settingsState
                )
            }

            ExpandableSection(
                title = "Diagnostics"
            ) {
                Text("ID: ${method.manifest.id}")
                Text("Version: ${method.manifest.version}")
                Text("Category: ${method.manifest.category}")
                Text("Status: ${method.manifest.status}")
                Text("Settings: ${settingsState.asMap()}")
            }
        }
    }
}

@Composable
private fun MethodOutputPanel(
    method: Method,
    settingsState: SettingsState
) {
    val clipboardManager = LocalClipboardManager.current
    val output = method.buildOutput(settingsState)
    val validation = MethodOutputValidator.validate(
        schema = method.outputSchema,
        output = output
    )

    var returnMode by remember {
        mutableStateOf(ReturnMode.Json)
    }

    var launchTarget by remember {
        mutableStateOf(LaunchTarget.Appearance)
    }

    var pastedLaunchText by remember {
        mutableStateOf("")
    }

    var parserMessage by remember {
        mutableStateOf("")
    }

    val returnPreview = OutputFormatter.format(
        output = output,
        returnMode = returnMode
    )

    val launchPreview = when (launchTarget) {
        LaunchTarget.Appearance -> OdkAppearanceBuilder.build(
            method = method,
            settingsState = settingsState,
            returnMode = returnMode
        )

        LaunchTarget.IntentColumn -> OdkIntentColumnBuilder.build(
            method = method,
            settingsState = settingsState,
            returnMode = returnMode
        )

        LaunchTarget.AndroidIntentUri -> AndroidIntentUriBuilder.build(
            method = method,
            settingsState = settingsState,
            returnMode = returnMode
        )

        LaunchTarget.KotlinIntent -> KotlinIntentSnippetBuilder.build(
            method = method,
            settingsState = settingsState,
            returnMode = returnMode
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Current output",
            fontWeight = FontWeight.Bold
        )

        if (output.fields.isEmpty()) {
            Text(
                text = "No output has been generated yet.",
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            output.fields.forEach { (key, value) ->
                Text(
                    text = "$key = $value",
                    modifier = Modifier.padding(top = 2.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Text(
            text = if (validation.valid) {
                "Validation: OK"
            } else {
                "Validation: ${validation.messages.joinToString("; ")}"
            },
            modifier = Modifier.padding(top = 8.dp),
            fontWeight = FontWeight.SemiBold
        )

        ExpandableSection(
            title = "Declared output schema",
            initiallyExpanded = false
        ) {
            if (method.outputSchema.graphOutputs.isEmpty() && method.outputSchema.fields.isEmpty()) {
                Text(
                    text = "No output schema declared.",
                    modifier = Modifier.padding(top = 4.dp)
                )
            } else {
                if (method.outputSchema.graphOutputs.isNotEmpty()) {
                    Text(
                        text = "ResearchOS graph outputs",
                        modifier = Modifier.padding(top = 4.dp),
                        fontWeight = FontWeight.Bold
                    )
                    method.outputSchema.graphOutputs.forEach { graphOutput ->
                        GraphOutputSummary(graphOutput)
                    }
                }

                if (method.outputSchema.fields.isNotEmpty()) {
                    Text(
                        text = "Returned fields",
                        modifier = Modifier.padding(top = 8.dp),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "These are the flat values returned to ODK or Android intents. They are mapped back to the graph outputs above.",
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    method.outputSchema.fields.forEach { field ->
                        TransportFieldSummary(field)
                    }
                }
            }
        }

        Text(
            text = "Return format",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Row {
                ModeButton(ReturnMode.Single.label, ReturnMode.Single, returnMode) { returnMode = it }
                Spacer(Modifier.width(6.dp))
                ModeButton(ReturnMode.Fields.label, ReturnMode.Fields, returnMode) { returnMode = it }
            }

            Row(
                modifier = Modifier.padding(top = 6.dp)
            ) {
                ModeButton(ReturnMode.Json.label, ReturnMode.Json, returnMode) { returnMode = it }
                Spacer(Modifier.width(6.dp))
                ModeButton(ReturnMode.Datapoints.label, ReturnMode.Datapoints, returnMode) { returnMode = it }
            }
        }

        Text(
            text = "Launch target",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Bold
        )

        Column(
            modifier = Modifier.padding(top = 6.dp)
        ) {
            Row {
                ModeButton(LaunchTarget.Appearance.label, LaunchTarget.Appearance, launchTarget) { launchTarget = it }
                Spacer(Modifier.width(6.dp))
                ModeButton(LaunchTarget.IntentColumn.label, LaunchTarget.IntentColumn, launchTarget) { launchTarget = it }
            }

            Row(
                modifier = Modifier.padding(top = 6.dp)
            ) {
                ModeButton(LaunchTarget.AndroidIntentUri.label, LaunchTarget.AndroidIntentUri, launchTarget) { launchTarget = it }
                Spacer(Modifier.width(6.dp))
                ModeButton(LaunchTarget.KotlinIntent.label, LaunchTarget.KotlinIntent, launchTarget) { launchTarget = it }
            }
        }

        Text(
            text = "Import existing launch string",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = pastedLaunchText,
            onValueChange = {
                pastedLaunchText = it
            },
            label = {
                Text("Paste appearance or intent")
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            minLines = 2,
            maxLines = 5
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                val parsed = LaunchConfigParser.parse(pastedLaunchText)

                parserMessage = applyParsedLaunchConfig(
                    method = method,
                    settingsState = settingsState,
                    parsedMethodId = parsed.methodId,
                    parsedReturnMode = parsed.returnMode,
                    parsedSettings = parsed.settings
                )

                parsed.returnMode?.let {
                    returnMode = it
                }

                if (parsed.warnings.isNotEmpty()) {
                    parserMessage += " ${parsed.warnings.joinToString(" ")}"
                }
            }
        ) {
            Text("Apply pasted config")
        }

        if (parserMessage.isNotBlank()) {
            Text(
                text = parserMessage,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Text(
            text = "Launch preview",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Bold
        )

        Text(
            text = launchPreview,
            modifier = Modifier.padding(top = 4.dp),
            fontFamily = FontFamily.Monospace
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                clipboardManager.setText(
                    AnnotatedString(launchPreview)
                )
            }
        ) {
            Text("Copy launch preview")
        }

        Text(
            text = "Return preview",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.Bold
        )

        Text(
            text = returnPreview,
            modifier = Modifier.padding(top = 4.dp),
            fontFamily = FontFamily.Monospace
        )

        Button(
            modifier = Modifier.padding(top = 8.dp),
            onClick = {
                clipboardManager.setText(
                    AnnotatedString(returnPreview)
                )
            }
        ) {
            Text("Copy return preview")
        }
    }
}

private fun applyParsedLaunchConfig(
    method: Method,
    settingsState: SettingsState,
    parsedMethodId: String?,
    parsedReturnMode: ReturnMode?,
    parsedSettings: Map<String, String>
): String {
    if (parsedMethodId != null && parsedMethodId != method.manifest.id) {
        return "Parsed method '${parsedMethodId}' does not match '${method.manifest.id}'."
    }

    var applied = 0
    var skipped = 0

    method.settings.forEach { setting ->
        val value = parsedSettings[setting.id]

        if (value == null) {
            return@forEach
        }

        val success = applySettingValue(
            setting = setting,
            settingsState = settingsState,
            value = value
        )

        if (success) {
            applied += 1
        } else {
            skipped += 1
        }
    }

    val unknownCount = parsedSettings.keys
        .count { settingId ->
            method.settings.none { it.id == settingId }
        }

    val returnModeText = if (parsedReturnMode != null) {
        " Return mode: ${parsedReturnMode.label}."
    } else {
        ""
    }

    return "Applied $applied setting(s). Skipped $skipped invalid value(s). Unknown setting(s): $unknownCount.$returnModeText"
}

private fun applySettingValue(
    setting: MethodSetting,
    settingsState: SettingsState,
    value: String
): Boolean {
    return when (setting) {
        is MethodSetting.BooleanSetting -> {
            when (value.lowercase()) {
                "true", "1", "yes" -> {
                    settingsState.setBoolean(setting.id, true)
                    true
                }

                "false", "0", "no" -> {
                    settingsState.setBoolean(setting.id, false)
                    true
                }

                else -> false
            }
        }

        is MethodSetting.IntSetting -> {
            value.toIntOrNull()?.let {
                settingsState.setInt(
                    setting.id,
                    it.coerceIn(
                        setting.minimum ?: Int.MIN_VALUE,
                        setting.maximum ?: Int.MAX_VALUE
                    )
                )
                true
            } ?: false
        }

        is MethodSetting.FloatSetting -> {
            value.toFloatOrNull()?.let {
                settingsState.setFloat(
                    setting.id,
                    it.coerceIn(
                        setting.minimum ?: -Float.MAX_VALUE,
                        setting.maximum ?: Float.MAX_VALUE
                    )
                )
                true
            } ?: false
        }

        is MethodSetting.TextSetting -> {
            settingsState.setString(setting.id, value)
            true
        }

        is MethodSetting.ChoiceSetting -> {
            if (setting.choices.contains(value)) {
                settingsState.setString(setting.id, value)
                true
            } else {
                false
            }
        }
    }
}

@Composable
private fun GraphOutputSummary(graphOutput: GraphOutput) {
    Text(
        text = graphOutput.displayTitle(),
        modifier = Modifier.padding(top = 6.dp),
        fontWeight = FontWeight.SemiBold
    )

    graphOutput.description?.takeIf { it.isNotBlank() }?.let { description ->
        Text(
            text = description,
            modifier = Modifier.padding(top = 2.dp)
        )
    }

    graphOutput.fields.forEach { field ->
        val label = field.description?.takeIf { it.isNotBlank() } ?: field.id.readableIdentifier()
        Text(
            text = "• $label — ${field.type.displayName()}, ${field.requiredWhen.displayName()}",
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun TransportFieldSummary(field: MethodField) {
    val requirement = field.requiredWhen.displayName()
    val mapping = field.graphPath?.let { " Maps to ${it.readablePath()}." } ?: ""
    Text(
        text = "• ${field.label}: ${field.type.displayName()}, $requirement.$mapping",
        modifier = Modifier.padding(top = 2.dp)
    )
}

private fun GraphOutput.displayTitle(): String {
    val kind = objectType.name.readableIdentifier()
    val target = phenomenon ?: stateType ?: relationshipType ?: entityType
    return if (target.isNullOrBlank()) kind else "$kind: ${target.readableIdentifier()}"
}

private fun MethodFieldType.displayName(): String = when (this) {
    MethodFieldType.Text -> "text"
    MethodFieldType.Integer -> "integer"
    MethodFieldType.Float -> "number"
    MethodFieldType.Boolean -> "yes/no"
    MethodFieldType.Json -> "structured data"
}

private fun RequiredWhen.displayName(): String = when (this) {
    RequiredWhen.Always -> "required"
    RequiredWhen.OnSuccessfulCapture -> "required after capture"
    RequiredWhen.IfAvailable -> "optional when available"
    RequiredWhen.PreviewOnly -> "preview only"
    RequiredWhen.TransportOnly -> "transport only"
}

private fun String.readableIdentifier(): String =
    replace('.', ' ')
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun String.readablePath(): String =
    removePrefix("Observation.values.")
        .removePrefix("State.values.")
        .removePrefix("Entity.attributes.")
        .readableIdentifier()

@Composable
private fun <T> ModeButton(
    label: String,
    value: T,
    selectedValue: T,
    onSelected: (T) -> Unit
) {
    Button(
        onClick = {
            onSelected(value)
        },
        enabled = value != selectedValue
    ) {
        Text(label)
    }
}
