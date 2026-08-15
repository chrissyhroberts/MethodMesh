package com.example.researchos.modules.sensorprovisioner

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.platform.devices.DeviceRegistry
import com.example.researchos.platform.devices.DeviceTransport
import com.example.researchos.platform.devices.RegisteredDevice
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONObject
import java.util.UUID

private val sensorServiceUuid: UUID = UUID.fromString("b6f2a900-9b8f-4f4e-9a1f-4f37a0010000")
private val manifestUuid: UUID = UUID.fromString("b6f2a901-9b8f-4f4e-9a1f-4f37a0010000")
private val readingUuid: UUID = UUID.fromString("b6f2a902-9b8f-4f4e-9a1f-4f37a0010000")
private val commandUuid: UUID = UUID.fromString("b6f2a903-9b8f-4f4e-9a1f-4f37a0010000")

private data class SensorCandidate(val device: BluetoothDevice, val name: String, val address: String, val rssi: Int)
private data class SensorProfileOption(
    val id: String,
    val label: String,
    val description: String,
    val status: String
)

private val sensorProfileOptions = listOf(
    SensorProfileOption(
        id = "aht20",
        label = "AHT20 temperature/humidity",
        description = "I2C AHT20 on GPIO 8/9 by default.",
        status = "implemented"
    ),
    SensorProfileOption(
        id = "ld2410c",
        label = "LD2410C mmWave presence",
        description = "UART mmWave radar on TX GPIO 21 / RX GPIO 20 by default.",
        status = "implemented"
    )
)

private fun sensorProfileById(id: String): SensorProfileOption =
    sensorProfileOptions.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: sensorProfileOptions.first()

object SensorProvisionerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SensorProvisionerMethod.ID
    override val title = "Provision BLE sensor node"
    override val description = "Configure a ResearchOS ESP32-C3 sensor node and save it into the device registry."

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
        var deviceId by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_id", "input_sensor_device_id", "device_id").ifBlank { "clinic_room_01_sensor" }) }
        var deviceName by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_device_name", "input_sensor_device_name", "device_name").ifBlank { "Clinic room 01 sensor" }) }
        var sampleInterval by rememberSaveable { mutableStateOf(supplied.firstPresent("sensor_sample_interval_ms", "input_sensor_sample_interval_ms", "sample_interval_ms").ifBlank { "60000" }) }
        var sensorProfileId by rememberSaveable { mutableStateOf(sensorProfileById(supplied.firstPresent("sensor_profile", "input_sensor_profile", "sensor_type")).id) }
        var sensorProfilePickerOpen by rememberSaveable { mutableStateOf(false) }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready to scan for ResearchOS sensor nodes.") }
        var selected by remember { mutableStateOf<SensorCandidate?>(null) }
        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var manifestCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var readingCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var commandCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var manifestJson by rememberSaveable { mutableStateOf("") }
        var commandResponseJson by rememberSaveable { mutableStateOf("") }
        var readingJson by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val candidates = remember { mutableStateListOf<SensorCandidate>() }

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

        fun saveRegistryAndRecord() {
            val candidate = selected ?: run {
                record(SensorProvisioningOutcome(status = "failed", error = "No sensor node selected."))
                return
            }
            val profile = JSONObject().apply {
                put("profile_type", "researchos_ble_sensor_node")
                put("service_uuid", sensorServiceUuid.toString())
                put("manifest_uuid", manifestUuid.toString())
                put("reading_uuid", readingUuid.toString())
                put("command_uuid", commandUuid.toString())
                put("device_id", deviceId.trim())
                put("device_name", deviceName.trim())
                put("sample_interval_ms", sampleInterval.trim().toIntOrNull() ?: 60000)
                put("sensor_profile", sensorProfileId)
                put("manifest", manifestJson)
                put("latest_reading", readingJson)
            }.toString()
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
            status = "Provisioned and saved to device registry."
            record(successfulOutcome(registryId))
        }

        val callback = remember {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
                    val device = scanResult.device ?: return
                    val advertisedServices = scanResult.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                    val name = scanResult.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull().orEmpty()
                    val looksLikeSensor = advertisedServices.contains(sensorServiceUuid) || name.startsWith("ResearchOS", ignoreCase = true)
                    if (!looksLikeSensor) return
                    val address = device.address ?: return
                    if (candidates.none { it.address == address }) {
                        candidates.add(SensorCandidate(device, name.ifBlank { "ResearchOS sensor" }, address, scanResult.rssi))
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
                            "Connected; discovering ResearchOS sensor service…"
                        } else {
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
                            status = "Connected, but this is not a complete ResearchOS sensor node."
                            record(SensorProvisioningOutcome(status = "failed", deviceAddress = selected?.address.orEmpty(), error = status))
                        } else {
                            status = "Sensor endpoint ready; reading manifest…"
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
                                status = if (text.isBlank()) "Manifest read failed." else "Manifest read. Ready to provision."
                            }
                            commandUuid -> {
                                commandResponseJson = text
                                status = "Provisioning command accepted; reading confirmation sample…"
                                handler.postDelayed({ g.readCharacteristic(readingCharacteristic) }, 400L)
                            }
                            readingUuid -> {
                                readingJson = text
                                saveRegistryAndRecord()
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
            result = null
            status = "Scanning for ResearchOS sensor nodes…"
            scanning = true
            runCatching { adapter.bluetoothLeScanner?.startScan(callback) }
                .onFailure { error -> status = "Scan failed: ${error.message.orEmpty()}"; scanning = false }
            handler.postDelayed({
                runCatching { adapter.bluetoothLeScanner?.stopScan(callback) }
                scanning = false
                if (status == "Scanning for ResearchOS sensor nodes…") status = "Scan complete."
            }, 10_000L)
        }

        fun connect(candidate: SensorCandidate) {
            if (!hasPermission()) { permissionLauncher.launch(bluetoothPermissions()); return }
            selected = candidate
            result = null
            manifestJson = ""
            commandResponseJson = ""
            readingJson = ""
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
            if (connection == null || characteristic == null) { status = "Connect to a ResearchOS sensor node first."; return }
            val interval = (sampleInterval.trim().toIntOrNull() ?: 60000).coerceIn(1000, 3600000)
            sampleInterval = interval.toString()
            val command = JSONObject().apply {
                put("command", "configure")
                put("device_id", deviceId.trim().ifBlank { "researchos_sensor" })
                put("device_name", deviceName.trim().ifBlank { "ResearchOS sensor" })
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
            Text("Configure an already-flashed ResearchOS ESP32-C3 sensor node. The node advertises even if an attached sensor is missing; sensor status is reported in the manifest and readings.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(deviceId, { deviceId = it }, label = { Text("Device ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(deviceName, { deviceName = it }, label = { Text("Display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(sampleInterval, { sampleInterval = it.filter(Char::isDigit) }, label = { Text("Sample interval (ms)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            val selectedProfile = sensorProfileById(sensorProfileId)
            Text("Sensor profile", fontWeight = FontWeight.SemiBold)
            OutlinedButton(
                onClick = { sensorProfilePickerOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${selectedProfile.label} (${selectedProfile.status})", modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("▼")
            }
            DropdownMenu(
                expanded = sensorProfilePickerOpen,
                onDismissRequest = { sensorProfilePickerOpen = false },
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                sensorProfileOptions.forEach { profile ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(profile.label)
                                Text("${profile.id} · ${profile.status}", style = MaterialTheme.typography.labelSmall)
                                Text(profile.description, style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        onClick = {
                            sensorProfileId = profile.id
                            sensorProfilePickerOpen = false
                        }
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Scan for sensor nodes") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                items(candidates, key = { it.address }) { candidate ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(candidate.name, fontWeight = FontWeight.SemiBold)
                            Text("${candidate.address} · RSSI ${candidate.rssi}", style = MaterialTheme.typography.bodySmall)
                            Row {
                                OutlinedButton(onClick = { connect(candidate) }) { Text(if (selected?.address == candidate.address) "Reconnect" else "Connect") }
                            }
                        }
                    }
                }
            }
            if (manifestJson.isNotBlank()) {
                Text("Manifest", fontWeight = FontWeight.SemiBold)
                Text(manifestJson, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
            }
            Button(onClick = { provision() }, modifier = Modifier.fillMaxWidth(), enabled = commandCharacteristic != null) { Text("Provision and save sensor") }
            if (commandResponseJson.isNotBlank() || readingJson.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Provisioning response", fontWeight = FontWeight.SemiBold)
                Text(listOf(commandResponseJson, readingJson).filter(String::isNotBlank).joinToString("\n"), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100SensorProvisionerMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Provision ResearchOS BLE sensor",
                        description = "Scan for a ResearchOS BLE sensor node and configure its persisted identity.",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='sensor_node_provisioner',input_sensor_device_id='clinic_room_01_sensor',input_sensor_device_name='Clinic room 01 sensor',input_sensor_sample_interval_ms='60000',return_mode='flat')"
                    )
                )
            )
        }
    }
}

private fun Map<String, String>.firstPresent(vararg keys: String): String = keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun bluetoothPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
} else {
    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
}
