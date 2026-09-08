package com.example.methodmesh.modules.sensorprovisioner

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.devices.DeviceRegistry
import com.example.methodmesh.platform.devices.DeviceTransport
import com.example.methodmesh.platform.devices.RegisteredDevice
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.util.UUID

private val sensorServiceUuid: UUID = UUID.fromString("b6f2a900-9b8f-4f4e-9a1f-4f37a0010000")
private val manifestUuid: UUID = UUID.fromString("b6f2a901-9b8f-4f4e-9a1f-4f37a0010000")
private val readingUuid: UUID = UUID.fromString("b6f2a902-9b8f-4f4e-9a1f-4f37a0010000")
private val commandUuid: UUID = UUID.fromString("b6f2a903-9b8f-4f4e-9a1f-4f37a0010000")

private data class SensorCandidate(val device: BluetoothDevice, val name: String, val address: String, val rssi: Int)

object SensorProvisionerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SensorProvisionerMethod.ID
    override val title = "Provision BLE sensor node"
    override val description = "Configure a MethodMesh ESP32-C3 sensor node and save it into the device registry."

    @SuppressLint("MissingPermission")
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val handler = remember { Handler(Looper.getMainLooper()) }
        val manager = androidContext.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        val supplied = remember(context.request.settings, context.action.settings, context.request.invocationContext) {
            context.request.invocationContext.asMap(context.action.canonicalId) + context.request.settings + context.action.settings
        }
        var deviceId by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_id", "input_sensor_device_id", "device_id")) }
        var deviceName by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_name", "input_sensor_device_name", "device_name").ifBlank { "MethodMesh sensor" }) }
        var sensorProfileId by rememberSaveable { mutableStateOf("") }
        var sampleInterval by rememberSaveable {
            mutableStateOf(
                supplied.firstPresent("sensor_sample_interval_ms", "input_sensor_sample_interval_ms", "sample_interval_ms")
                    .ifBlank { "" }
            )
        }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to scan for MethodMesh sensor nodes.") }
        var selected by remember { mutableStateOf<SensorCandidate?>(null) }
        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var manifestCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var readingCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var commandCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var manifestJson by rememberSaveable { mutableStateOf("") }
        var commandResponseJson by rememberSaveable { mutableStateOf("") }
        var readingJson by rememberSaveable { mutableStateOf("") }
        var saveAfterNextReading by rememberSaveable { mutableStateOf(false) }
        var connectedReady by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var registeredSensors by remember { mutableStateOf(sensorRegistryDevices(androidContext)) }
        val candidates = remember { mutableStateListOf<SensorCandidate>() }

        LaunchedEffect(deviceId, deviceName, sampleInterval, sensorProfileId) {
            context.onSettingsChanged(
                mapOf(
                    "sensor_device_id" to deviceId,
                    "sensor_device_name" to deviceName,
                    "sensor_sample_interval_ms" to sampleInterval,
                    "sensor_profile" to sensorProfileId
                )
            )
        }

        fun hasPermission(): Boolean = bluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(androidContext, permission) == PackageManager.PERMISSION_GRANTED
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            status = if (grants.values.all { it }) "Bluetooth permission granted." else "Bluetooth permission was denied."
        }

        fun record(outcome: SensorProvisioningOutcome) {
            val request = As100SensorProvisionerMethod.request(
                action = As100SensorProvisionerMethod.ID,
                context = context.request.invocationContext.asMap(As100SensorProvisionerMethod.ID) + supplied,
                signals = emptyList(),
                inputs = emptyList()
            )
            result = As100SensorProvisionerMethod.result(request, outcome, context.request.invocationContext)
        }

        fun successfulOutcome(registryId: String) = SensorProvisioningOutcome(
            status = "provisioned",
            deviceId = deviceId.trim(),
            deviceName = deviceName.trim(),
            deviceAddress = selected?.address.orEmpty(),
            sampleIntervalMs = sampleInterval.trim(),
            manifestJson = manifestJson,
            commandResponseJson = commandResponseJson,
            confirmationReadingJson = readingJson,
            registryDeviceId = registryId
        )

        fun configuredProfileId() = SensorProvisioningProfiles.manifestProfileId(manifestJson)
        fun readingProfileId() = SensorProvisioningProfiles.readingProfileId(readingJson)
        fun installedProfileId() = configuredProfileId().ifBlank { readingProfileId() }.ifBlank { sensorProfileId }
        fun installedProfile() = SensorProvisioningProfiles.byId(installedProfileId())
        fun configuredProfileLabel(): String {
            val profileId = installedProfileId()
            return if (profileId.isBlank()) "not reported by this firmware" else SensorProvisioningProfiles.byId(profileId).label
        }
        fun profileMismatchHint(): String {
            val profileId = installedProfileId()
            val reading = extractJsonObject(readingJson) ?: return ""
            val error = reading.optString("error")
            return when {
                profileId == "aht20" && error.contains("AHT20 not found", ignoreCase = true) ->
                    "This board is running the AHT20 profile, but no AHT20 is present. If this node is meant to be radar, reflash the LD2410C image and try again."
                profileId == "ld2410c" && reading.optString("status").equals("error", ignoreCase = true) ->
                    "This board is running the LD2410C radar profile, but the radar read failed. Check TX GPIO 21, RX GPIO 20, power, and ground."
                else -> ""
            }
        }

        fun saveRegistryAndRecord() {
            val candidate = selected ?: run {
                record(SensorProvisioningOutcome(status = "failed", error = "No sensor node selected."))
                return
            }
            val localId = deviceId.trim().ifBlank { localSensorId(deviceName, candidate.address) }
            deviceId = localId
            val interval = sampleInterval.trim().toIntOrNull() ?: installedProfile().defaultSampleIntervalMs
            val normalisedReading = SensorProvisioningProfiles.normaliseReading(readingJson, sensorProfileId)
            val profile = SensorProvisioningProfiles.registryProfile(
                deviceId = localId,
                deviceName = deviceName.trim(),
                sampleIntervalMs = interval,
                sensorProfileId = installedProfileId(),
                manifestJson = manifestJson,
                latestReadingJson = normalisedReading
            )
            val registryId = "sensor:$localId"
            DeviceRegistry.save(
                androidContext,
                RegisteredDevice(
                    id = registryId,
                    name = deviceName.trim().ifBlank { candidate.name },
                    transport = DeviceTransport.BLE,
                    address = candidate.address,
                    profile = profile,
                    lastSeenEpochMillis = System.currentTimeMillis(),
                    lastConnectedEpochMillis = System.currentTimeMillis()
                )
            )
            registeredSensors = sensorRegistryDevices(androidContext)
            readingJson = normalisedReading
            status = "Provisioned and saved to device registry."
            record(successfulOutcome(registryId))
        }

        val callback = remember {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
                    val device = scanResult.device ?: return
                    val advertisedServices = scanResult.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                    val advertisedName = scanResult.scanRecord?.deviceName.orEmpty()
                    val cachedName = runCatching { device.name }.getOrNull().orEmpty()
                    val looksLikeSensor = advertisedServices.contains(sensorServiceUuid) || advertisedName.startsWith("MethodMesh", ignoreCase = true) || cachedName.startsWith("MethodMesh", ignoreCase = true)
                    if (!looksLikeSensor) return
                    val address = device.address ?: return
                    if (candidates.none { it.address == address }) {
                        val displayName = advertisedName.ifBlank { "MethodMesh sensor ${address.takeLast(5)}" }
                        candidates.add(SensorCandidate(device, displayName, address, scanResult.rssi))
                    }
                }
            }
        }

        val gattCallback = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                    handler.post {
                        status = if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            runCatching { g.requestMtu(517) }
                            handler.postDelayed({
                                runCatching { g.discoverServices() }
                                    .onFailure { error -> status = "Connected, but service discovery failed: ${error.message.orEmpty()}" }
                            }, 700L)
                            "Connected; preparing BLE link for sensor payloads…"
                        } else {
                            connectedReady = false
                            "Disconnected (status=$statusCode)."
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                    handler.post {
                        val service = g.getService(sensorServiceUuid)
                        manifestCharacteristic = service?.getCharacteristic(manifestUuid)
                        readingCharacteristic = service?.getCharacteristic(readingUuid)
                        commandCharacteristic = service?.getCharacteristic(commandUuid)
                        if (statusCode != BluetoothGatt.GATT_SUCCESS || service == null || manifestCharacteristic == null || readingCharacteristic == null || commandCharacteristic == null) {
                            connectedReady = false
                            status = "Connected, but this is not a complete MethodMesh sensor node."
                            record(SensorProvisioningOutcome(status = "failed", deviceAddress = selected?.address.orEmpty(), error = status))
                        } else {
                            connectedReady = true
                            status = "Connected. Reading sensor manifest…"
                            g.readCharacteristic(manifestCharacteristic)
                        }
                    }
                }

                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                    handleCharacteristicRead(g, characteristic, characteristic.value, statusCode)
                }

                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
                    handleCharacteristicRead(g, characteristic, value, statusCode)
                }

                private fun handleCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
                    handler.post {
                        val text = if (statusCode == BluetoothGatt.GATT_SUCCESS) value.toString(Charsets.UTF_8) else ""
                        when (characteristic.uuid) {
                            manifestUuid -> {
                                manifestJson = text
                                if (text.isNotBlank()) {
                                    val profileId = configuredProfileId()
                                    if (profileId.isNotBlank()) sensorProfileId = profileId
                                    if (sampleInterval.isBlank()) {
                                        sampleInterval = SensorProvisioningProfiles.byId(profileId.ifBlank { sensorProfileId }).defaultSampleIntervalMs.toString()
                                    }
                                }
                                status = if (text.isBlank()) "Manifest read failed." else "Sensor detected. Name it, test a measurement, then save."
                            }
                            commandUuid -> {
                                commandResponseJson = text
                                status = "Sensor profile applied. Reading confirmation sample…"
                                handler.postDelayed({ g.readCharacteristic(readingCharacteristic) }, 400L)
                            }
                            readingUuid -> {
                                val profileHint = installedProfileId()
                                readingJson = SensorProvisioningProfiles.normaliseReading(text, profileHint)
                                SensorProvisioningProfiles.readingProfileId(readingJson).takeIf(String::isNotBlank)?.let { sensorProfileId = it }
                                if (saveAfterNextReading && commandResponseJson.isNotBlank()) {
                                    saveAfterNextReading = false
                                    saveRegistryAndRecord()
                                } else {
                                    status = "Test measurement read for ${installedProfile().label}. Save when ready."
                                }
                            }
                        }
                    }
                }

                override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                    handler.post {
                        if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                            status = "Provisioning command written; reading response…"
                            handler.postDelayed({ g.readCharacteristic(commandCharacteristic) }, 250L)
                        } else {
                            status = "Provisioning write failed (GATT $statusCode)."
                            record(SensorProvisioningOutcome(status = "failed", deviceAddress = selected?.address.orEmpty(), error = status, manifestJson = manifestJson))
                        }
                    }
                }
            }
        }

        fun startScan() {
            if (adapter == null || !adapter.isEnabled) { status = "Bluetooth is not enabled."; return }
            if (!hasPermission()) { permissionLauncher.launch(bluetoothPermissions()); return }
            candidates.clear()
            selected = null
            connectedReady = false
            result = null
            status = "Scanning for MethodMesh sensor nodes…"
            scanning = true
            runCatching { adapter.bluetoothLeScanner?.startScan(callback) }
                .onFailure { error -> status = "Scan failed: ${error.message.orEmpty()}"; scanning = false }
            handler.postDelayed({
                runCatching { adapter.bluetoothLeScanner?.stopScan(callback) }
                scanning = false
                if (status == "Scanning for MethodMesh sensor nodes…") status = "Scan complete."
            }, 10_000L)
        }

        fun connect(candidate: SensorCandidate) {
            if (!hasPermission()) { permissionLauncher.launch(bluetoothPermissions()); return }
            selected = candidate
            result = null
            manifestJson = ""
            commandResponseJson = ""
            readingJson = ""
            saveAfterNextReading = false
            connectedReady = false
            status = "Connecting to ${candidate.name}…"
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            scanning = false
            runCatching { gatt?.refreshGattCacheQuietly() }
            runCatching { gatt?.close() }
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                candidate.device.connectGatt(androidContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                candidate.device.connectGatt(androidContext, false, gattCallback)
            }
        }

        fun writeConfiguration(saveAfterReading: Boolean) {
            val characteristic = commandCharacteristic
            val connection = gatt
            if (connection == null || characteristic == null) { status = "Connect to a MethodMesh sensor node first."; return }
            val interval = (sampleInterval.trim().toIntOrNull() ?: installedProfile().defaultSampleIntervalMs).coerceIn(1000, 3600000)
            sampleInterval = interval.toString()
            saveAfterNextReading = saveAfterReading
            val command = JSONObject().apply {
                put("command", "configure")
                put("device_id", deviceId.trim().ifBlank { selected?.let { localSensorId(deviceName, it.address) } ?: "methodmesh_sensor" })
                put("device_name", deviceName.trim().ifBlank { "MethodMesh sensor" })
                put("sample_interval_ms", interval)
            }.toString().toByteArray(Charsets.UTF_8)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = command
            status = if (saveAfterReading) "Saving sensor to registry…" else "Refreshing sensor configuration before test…"
            val queued = connection.writeCharacteristic(characteristic)
            if (!queued) {
                saveAfterNextReading = false
                status = "Could not queue sensor configuration write."
                record(SensorProvisioningOutcome(status = "failed", deviceAddress = selected?.address.orEmpty(), error = status, manifestJson = manifestJson))
            }
        }

        fun provision() = writeConfiguration(saveAfterReading = true)

        fun testMeasurement() {
            val characteristic = readingCharacteristic
            val connection = gatt
            if (connection == null || characteristic == null) {
                status = "Connect to a MethodMesh sensor node before testing a measurement."
                return
            }
            commandResponseJson = ""
            saveAfterNextReading = false
            status = "Reading test measurement…"
            if (!connection.readCharacteristic(characteristic)) {
                status = "Could not queue test measurement read."
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
                runCatching { gatt?.refreshGattCacheQuietly() }
            runCatching { gatt?.close() }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately) startScan()
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; startScan() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Find an installed MethodMesh sensor, give it a local name, test it, and save it to the registry.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (registeredSensors.isNotEmpty()) {
                Text("Provisioned sensors", fontWeight = FontWeight.SemiBold)
                registeredSensors.forEach { device ->
                    Text("• ${device.name} · ${device.id}", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("1. Scan for devices", fontWeight = FontWeight.SemiBold)
            Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Scan for MethodMesh sensors") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                items(candidates, key = { it.address }) { candidate ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(candidate.name, fontWeight = FontWeight.SemiBold)
                            Text("${candidate.address} · RSSI ${candidate.rssi}", style = MaterialTheme.typography.bodySmall)
                            OutlinedButton(onClick = { connect(candidate) }) { Text(if (selected?.address == candidate.address) "Reconnect" else "Connect") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("2. Connect", fontWeight = FontWeight.SemiBold)
            Text(
                if (connectedReady) "✓ Connected to ${selected?.name.orEmpty()}." else "Select a scanned device and press Connect.",
                style = MaterialTheme.typography.bodySmall
            )
            if (manifestJson.isNotBlank() || readingJson.isNotBlank()) {
                Text("Installed sensor: ${configuredProfileLabel()}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (configuredProfileId().isBlank() && manifestJson.isNotBlank()) {
                    Text(
                        "The node did not advertise a sensor profile. Reflash with a MethodMesh sensor image v0.1.6 or newer, then reset and scan again.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                profileMismatchHint().takeIf(String::isNotBlank)?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("3. Name this sensor", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Sensor name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (selected != null) {
                Text("Local ID will be ${deviceId.trim().ifBlank { localSensorId(deviceName, selected?.address.orEmpty()) }}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Text("4. Test measurement", fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { testMeasurement() },
                modifier = Modifier.fillMaxWidth(),
                enabled = readingCharacteristic != null
            ) { Text("Test measurement") }
            if (readingJson.isNotBlank()) {
                Text(sensorReadingSummary(SensorProvisioningProfiles.normaliseReading(readingJson, installedProfileId())), style = MaterialTheme.typography.bodyMedium)
                profileMismatchHint().takeIf(String::isNotBlank)?.let { hint ->
                    Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("5. Save to device registry", fontWeight = FontWeight.SemiBold)
            Button(onClick = { provision() }, modifier = Modifier.fillMaxWidth(), enabled = commandCharacteristic != null && readingJson.isNotBlank()) { Text("Save sensor to registry") }
            if (result != null) {
                Text("✓ Provisioning complete. This sensor is saved and ready for sensor.read.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            if (readingJson.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Latest reading", fontWeight = FontWeight.SemiBold)
                Text(
                    sensorReadingSummary(SensorProvisioningProfiles.normaliseReading(readingJson, installedProfileId())),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun sensorReadingSummary(readingJson: String): String {
    val reading = extractJsonObject(readingJson) ?: return readingJson
    val profile = reading.optString("sensor_profile").ifBlank { reading.optString("sensor_type") }
    val status = reading.optString("status")
    val lines = mutableListOf<String>()
    if (profile.isNotBlank()) lines += "Sensor: $profile"
    if (status.isNotBlank()) lines += "Status: $status"
    listOf(
        "temperature_c" to "Temperature °C",
        "relative_humidity_pct" to "Relative humidity %",
        "presence" to "Presence",
        "target_state" to "Target state",
        "moving_distance_cm" to "Moving distance cm",
        "moving_energy" to "Moving energy",
        "stationary_distance_cm" to "Stationary distance cm",
        "stationary_energy" to "Stationary energy",
        "detection_distance_cm" to "Detection distance cm"
    ).forEach { (key, label) ->
        if (reading.has(key) && !reading.isNull(key)) lines += "$label: ${reading.opt(key)}"
    }
    val error = reading.optString("error")
    if (error.isNotBlank()) lines += "Error: $error"
    return lines.joinToString("\n").ifBlank { readingJson }
}

private fun localSensorId(name: String, address: String): String {
    val slug = name.trim()
        .lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "methodmesh_sensor" }
    val suffix = address.replace(":", "").takeLast(6).lowercase().ifBlank { "local" }
    return "${slug}_$suffix"
}

private fun Map<String, String>.firstPresent(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun BluetoothGatt.refreshGattCacheQuietly() {
    runCatching {
        val method = javaClass.getMethod("refresh")
        method.invoke(this)
    }
}

private fun sensorRegistryDevices(context: Context): List<RegisteredDevice> =
    DeviceRegistry.all(context).filter { device ->
        device.transport == DeviceTransport.BLE &&
            runCatching { JSONObject(device.profile).optString("profile_type") == "methodmesh_ble_sensor_node" }.getOrDefault(false)
    }.sortedBy { it.name.lowercase() }

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
