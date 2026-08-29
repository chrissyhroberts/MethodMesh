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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
    @OptIn(ExperimentalMaterial3Api::class)
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
        var deviceId by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_id", "input_sensor_device_id", "device_id").ifBlank { "clinic_room_01_sensor" }) }
        var deviceName by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_name", "input_sensor_device_name", "device_name").ifBlank { "Clinic room 01 sensor" }) }
        var sensorProfileId by rememberSaveable { mutableStateOf(SensorProvisioningProfiles.byId(supplied.firstPresent("sensor_profile", "input_sensor_profile", "sensor_type")).id) }
        var sampleInterval by rememberSaveable {
            mutableStateOf(
                supplied.firstPresent("sensor_sample_interval_ms", "input_sensor_sample_interval_ms", "sample_interval_ms")
                    .ifBlank { SensorProvisioningProfiles.byId(sensorProfileId).defaultSampleIntervalMs.toString() }
            )
        }
        var sensorProfilePickerOpen by rememberSaveable { mutableStateOf(false) }
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

        fun currentProfile() = SensorProvisioningProfiles.fromManifestOrSelected(manifestJson, sensorProfileId)

        fun saveRegistryAndRecord() {
            val candidate = selected ?: run {
                record(SensorProvisioningOutcome(status = "failed", error = "No sensor node selected."))
                return
            }
            val interval = sampleInterval.trim().toIntOrNull() ?: currentProfile().defaultSampleIntervalMs
            val normalisedReading = SensorProvisioningProfiles.normaliseReading(readingJson, sensorProfileId)
            val profile = SensorProvisioningProfiles.registryProfile(
                deviceId = deviceId.trim(),
                deviceName = deviceName.trim(),
                sampleIntervalMs = interval,
                sensorProfileId = sensorProfileId,
                manifestJson = manifestJson,
                latestReadingJson = normalisedReading
            )
            val registryId = "sensor:${deviceId.trim().ifBlank { candidate.address }}"
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
                    val name = scanResult.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull().orEmpty()
                    val looksLikeSensor = advertisedServices.contains(sensorServiceUuid) || name.startsWith("MethodMesh", ignoreCase = true)
                    if (!looksLikeSensor) return
                    val address = device.address ?: return
                    if (candidates.none { it.address == address }) {
                        candidates.add(SensorCandidate(device, name.ifBlank { "MethodMesh sensor" }, address, scanResult.rssi))
                    }
                }
            }
        }

        val gattCallback = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                    handler.post {
                        status = if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            handler.postDelayed({
                                runCatching { g.discoverServices() }
                                    .onFailure { error -> status = "Connected, but service discovery failed: ${error.message.orEmpty()}" }
                            }, 350L)
                            "Connected; discovering MethodMesh sensor service…"
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
                                    val profile = currentProfile()
                                    sensorProfileId = profile.id
                                    sampleInterval = sampleInterval.ifBlank { profile.defaultSampleIntervalMs.toString() }
                                }
                                status = if (text.isBlank()) "Manifest read failed." else "Manifest read. Test a measurement, then name and save."
                            }
                            commandUuid -> {
                                commandResponseJson = text
                                status = "Configuration accepted. Reading final confirmation sample…"
                                handler.postDelayed({ g.readCharacteristic(readingCharacteristic) }, 400L)
                            }
                            readingUuid -> {
                                readingJson = SensorProvisioningProfiles.normaliseReading(text, sensorProfileId)
                                if (commandResponseJson.isNotBlank()) {
                                    saveRegistryAndRecord()
                                } else {
                                    status = "Test measurement read. Name the device, then save it to the registry."
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
            connectedReady = false
            status = "Connecting to ${candidate.name}…"
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            scanning = false
            runCatching { gatt?.close() }
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                candidate.device.connectGatt(androidContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                candidate.device.connectGatt(androidContext, false, gattCallback)
            }
        }

        fun provision() {
            val characteristic = commandCharacteristic
            val connection = gatt
            if (connection == null || characteristic == null) { status = "Connect to a MethodMesh sensor node first."; return }
            val interval = (sampleInterval.trim().toIntOrNull() ?: 60000).coerceIn(1000, 3600000)
            sampleInterval = interval.toString()
            val command = JSONObject().apply {
                put("command", "configure")
                put("device_id", deviceId.trim().ifBlank { "methodmesh_sensor" })
                put("device_name", deviceName.trim().ifBlank { "MethodMesh sensor" })
                put("sample_interval_ms", interval)
                put("sensor_profile", sensorProfileId)
            }.toString().toByteArray(Charsets.UTF_8)
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = command
            status = "Writing provisioning command…"
            val queued = connection.writeCharacteristic(characteristic)
            if (!queued) {
                status = "Could not queue provisioning write."
                record(SensorProvisioningOutcome(status = "failed", deviceAddress = selected?.address.orEmpty(), error = status, manifestJson = manifestJson))
            }
        }

        fun testMeasurement() {
            val characteristic = readingCharacteristic
            val connection = gatt
            if (connection == null || characteristic == null) {
                status = "Connect to a MethodMesh sensor node before testing a measurement."
                return
            }
            commandResponseJson = ""
            status = "Reading test measurement…"
            if (!connection.readCharacteristic(characteristic)) {
                status = "Could not queue test measurement read."
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
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
            Text("Configure an already-flashed MethodMesh ESP32-C3 sensor node and save it to the device registry.", style = MaterialTheme.typography.bodyMedium)
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
            if (manifestJson.isNotBlank()) {
                Text("Detected: ${currentProfile().label}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            Text("3. Test measurement", fontWeight = FontWeight.SemiBold)
            Button(
                onClick = { testMeasurement() },
                modifier = Modifier.fillMaxWidth(),
                enabled = readingCharacteristic != null
            ) { Text("Test measurement") }
            if (readingJson.isNotBlank()) {
                Text(SensorProvisioningProfiles.normaliseReading(readingJson, sensorProfileId), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            Text("4. Name and configure", fontWeight = FontWeight.SemiBold)
            OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(sampleInterval, { sampleInterval = it.filter(Char::isDigit) }, label = { Text("Sample interval (ms)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            val selectedProfile = SensorProvisioningProfiles.byId(sensorProfileId)
            ExposedDropdownMenuBox(
                expanded = sensorProfilePickerOpen,
                onExpandedChange = { sensorProfilePickerOpen = !sensorProfilePickerOpen }
            ) {
                OutlinedTextField(
                    value = selectedProfile.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Attached sensor") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sensorProfilePickerOpen) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = sensorProfilePickerOpen,
                    onDismissRequest = { sensorProfilePickerOpen = false }
                ) {
                    SensorProvisioningProfiles.all.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(profile.label)
                                    Text(profile.id, style = MaterialTheme.typography.labelSmall)
                                    Text(profile.description, style = MaterialTheme.typography.bodySmall)
                                }
                            },
                            onClick = {
                                sensorProfileId = profile.id
                                sampleInterval = profile.defaultSampleIntervalMs.toString()
                                sensorProfilePickerOpen = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("5. Save to device registry", fontWeight = FontWeight.SemiBold)
            Button(onClick = { provision() }, modifier = Modifier.fillMaxWidth(), enabled = commandCharacteristic != null && readingJson.isNotBlank()) { Text("Save sensor to registry") }
            if (commandResponseJson.isNotBlank() || readingJson.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Latest sensor payload", fontWeight = FontWeight.SemiBold)
                Text(listOf(commandResponseJson, SensorProvisioningProfiles.normaliseReading(readingJson, sensorProfileId)).filter(String::isNotBlank).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun Map<String, String>.firstPresent(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

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
