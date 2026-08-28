package com.example.methodmesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.platform.devices.DeviceRegistry
import com.example.methodmesh.platform.devices.DeviceTransport
import com.example.methodmesh.platform.devices.RegisteredDevice
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRenderer(
    settings: List<MethodSetting>,
    settingsState: SettingsState,
    capabilityId: String? = null
) {
    Column {
        settings.forEach { setting ->
            if (capabilityId == "sensor.read" && setting.id == "device_id") {
                RegisteredSensorSetting(setting, settingsState)
                return@forEach
            }
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
                    var expanded by remember(setting.id) { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        OutlinedTextField(
                            value = choiceLabel(settingsState.getString(setting.id)),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(setting.label) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            setting.choices.forEach { choice ->
                                DropdownMenuItem(
                                    text = { Text(choiceLabel(choice)) },
                                    onClick = {
                                        settingsState.setString(setting.id, choice)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun choiceLabel(value: String): String =
    when (value) {
        "" -> "Automatic / default"
        "android.intent.action.MAIN" -> "Open app"
        "android.intent.action.VIEW" -> "View / open URI"
        "android.intent.action.SEND" -> "Send"
        "android.intent.action.SENDTO" -> "Send to"
        "android.intent.action.GET_CONTENT" -> "Pick content"
        "android.intent.action.EDIT" -> "Edit"
        "full" -> "Full document scanner"
        "base_with_filter" -> "Basic scanner + image filters"
        "base" -> "Basic scanner"
        "single" -> "Single read"
        "trace" -> "Trace over time"
        "average" -> "Average over time"
        "discover" -> "Discover available sensors"
        "fallback" -> "Use selected sensor, otherwise choose nearby"
        "strict" -> "Only use selected sensor"
        "any_nearby" -> "Choose nearby sensor"
        "aht20" -> "AHT20 temperature/humidity"
        "ld2410c" -> "LD2410C radar/presence"
        "generic_ble_sensor" -> "Generic BLE sensor"
        "generic" -> "Generic sensor payload"
        "secure_random" -> "Secure random seed"
        "fixed_seed" -> "Fixed reproducible seed"
        "disabled" -> "Off / disabled"
        "preferred" -> "Preferred"
        "required" -> "Required"
        "Fingerprint" -> "Fingerprint / biometric"
        "Pin" -> "Device PIN / pattern"
        "Qr" -> "QR / camera code"
        "Nfc" -> "NFC token"
        "Password" -> "Study password/token"
        "replace" -> "Replace existing content"
        "empty_only" -> "Only if tag is empty"
        "camera" -> "Camera"
        "file_picker" -> "Choose file"
        "ocr" -> "OCR text"
        "barcodes" -> "Barcodes only"
        "ocr_and_barcodes" -> "OCR + barcodes"
        "download" -> "Download language model"
        "delete" -> "Delete language model"
        "list" -> "List language models"
        else -> value
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisteredSensorSetting(
    setting: MethodSetting,
    settingsState: SettingsState
) {
    val context = LocalContext.current
    val devices = remember {
        DeviceRegistry.all(context).filter {
            it.transport == DeviceTransport.BLE &&
                it.enabled &&
                !it.paused &&
                (it.profile.contains("methodmesh_ble_sensor", true) || it.id.startsWith("sensor:") || it.name.contains("sensor", true))
        }.sortedBy { it.name.lowercase() }
    }
    var expanded by remember(setting.id) { mutableStateOf(false) }
    val selected = settingsState.getString(setting.id)
    val selectedDevice = devices.firstOrNull { it.id == selected || it.name == selected || it.address.equals(selected, true) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        OutlinedTextField(
            value = selectedDevice?.let { "${it.name} (${it.id})" } ?: if (selected.isBlank()) "Choose nearby sensor" else selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(setting.label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Choose nearby sensor", fontWeight = FontWeight.SemiBold)
                        Text("Scan and choose from MethodMesh sensors in range")
                    }
                },
                onClick = {
                    settingsState.setString(setting.id, "")
                    expanded = false
                }
            )
            devices.forEach { device ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(device.name, fontWeight = FontWeight.SemiBold)
                            Text("${device.id} · ${device.address}")
                        }
                    },
                    onClick = {
                        settingsState.setString(setting.id, device.id)
                        expanded = false
                    }
                )
            }
            if (devices.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No registered sensors found") },
                    onClick = { expanded = false }
                )
            }
        }
    }
}
