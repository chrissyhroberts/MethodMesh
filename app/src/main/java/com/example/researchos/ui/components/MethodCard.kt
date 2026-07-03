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
import com.example.researchos.core.Method
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
                initiallyExpanded = true
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
                title = "Output"
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
            text = "Output schema",
            fontWeight = FontWeight.Bold
        )

        if (method.outputSchema.fields.isEmpty()) {
            Text(
                text = "No output schema declared.",
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            method.outputSchema.fields.forEach { field ->
                Text(
                    text = "${field.id}: ${field.type}${if (field.required) " required" else " optional"}",
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
