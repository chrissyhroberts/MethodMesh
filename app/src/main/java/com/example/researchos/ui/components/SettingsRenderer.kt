package com.example.researchos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState

@Composable
fun SettingsRenderer(
    settings: List<MethodSetting>,
    settingsState: SettingsState
) {
    Column {
        settings.forEach { setting ->
            when (setting) {
                is MethodSetting.TextSetting -> {
                    OutlinedTextField(
                        value = settingsState.getString(setting.id),
                        onValueChange = { settingsState.setString(setting.id, it) },
                        label = { Text(setting.label) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    )
                }

                is MethodSetting.BooleanSetting -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = setting.label,
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = settingsState.getBoolean(setting.id),
                            onCheckedChange = { settingsState.setBoolean(setting.id, it) }
                        )
                    }
                }

                is MethodSetting.FloatSetting -> {
                    NumericSettingField(
                        label = setting.label,
                        value = settingsState.getFloat(setting.id),
                        minimum = setting.minimum ?: 0f,
                        maximum = setting.maximum ?: 100f,
                        step = setting.step,
                        unit = setting.unit,
                        decimals = setting.decimals,
                        onValueChange = {
                            settingsState.setFloat(setting.id, it)
                        }
                    )
                }

                is MethodSetting.IntSetting -> {
                    NumericSettingField(
                        label = setting.label,
                        value = settingsState.getInt(setting.id).toFloat(),
                        minimum = (setting.minimum ?: 0).toFloat(),
                        maximum = (setting.maximum ?: 100).toFloat(),
                        step = setting.step.toFloat(),
                        unit = setting.unit,
                        decimals = 0,
                        onValueChange = {
                            settingsState.setInt(setting.id, it.toInt())
                        }
                    )
                }

                is MethodSetting.ChoiceSetting -> {
                    Text(
                        text = "${setting.label}: ${settingsState.getString(setting.id)}",
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Text(
                        text = setting.choices.joinToString(" • "),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}