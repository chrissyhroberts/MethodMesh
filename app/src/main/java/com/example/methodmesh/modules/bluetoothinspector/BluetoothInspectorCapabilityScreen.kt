package com.example.methodmesh.modules.bluetoothinspector

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class NearbyBluetoothDevice(val device: BluetoothDevice, val name: String, val address: String, val rssi: Int, val paired: Boolean = false)

private data class GattCharacteristicInfo(
    val serviceUuid: String,
    val characteristic: BluetoothGattCharacteristic,
    val label: String,
    val readable: Boolean,
    val writable: Boolean,
    val notifiable: Boolean
)

private val standardBluetoothLabels = mapOf(
    "00001800-0000-1000-8000-00805f9b34fb" to "Generic Access",
    "00001801-0000-1000-8000-00805f9b34fb" to "Generic Attribute",
    "00001805-0000-1000-8000-00805f9b34fb" to "Current Time Service",
    "0000180a-0000-1000-8000-00805f9b34fb" to "Device Information Service",
    "0000180d-0000-1000-8000-00805f9b34fb" to "Heart Rate Service",
    "0000180f-0000-1000-8000-00805f9b34fb" to "Battery Service",
    "00002a00-0000-1000-8000-00805f9b34fb" to "Device Name",
    "00002a01-0000-1000-8000-00805f9b34fb" to "Appearance",
    "00002a05-0000-1000-8000-00805f9b34fb" to "Service Changed",
    "00002a19-0000-1000-8000-00805f9b34fb" to "Battery Level",
    "00002a24-0000-1000-8000-00805f9b34fb" to "Model Number",
    "00002a25-0000-1000-8000-00805f9b34fb" to "Serial Number",
    "00002a26-0000-1000-8000-00805f9b34fb" to "Firmware Revision",
    "00002a27-0000-1000-8000-00805f9b34fb" to "Hardware Revision",
    "00002a28-0000-1000-8000-00805f9b34fb" to "Software Revision",
    "00002a29-0000-1000-8000-00805f9b34fb" to "Manufacturer Name",
    "00002a37-0000-1000-8000-00805f9b34fb" to "Heart Rate Measurement"
)

private val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

private fun bluetoothLabel(uuid: UUID): String = standardBluetoothLabels[uuid.toString().lowercase()] ?: "Custom endpoint"

object BluetoothInspectorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100BluetoothInspectorMethod.ID
    override val title = "Inspect Bluetooth devices"
    override val description = "Discover nearby devices and inspect authorised BLE endpoints."

    @SuppressLint("MissingPermission")
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val manager = androidContext.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        val devices = remember { mutableStateListOf<NearbyBluetoothDevice>() }
        val pairedDevices = remember { mutableStateListOf<NearbyBluetoothDevice>() }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var pendingScan by remember { mutableStateOf(false) }
        var selectedAddress by rememberSaveable { mutableStateOf("") }
        var connectionStatus by rememberSaveable { mutableStateOf("Idle") }
        var endpoints by rememberSaveable { mutableStateOf("") }
        var captured by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var savedStatus by remember { mutableStateOf("") }
        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var gattCharacteristics by remember { mutableStateOf(emptyList<GattCharacteristicInfo>()) }
        var expandedServices by remember { mutableStateOf(emptySet<String>()) }
        var probeRunning by rememberSaveable { mutableStateOf(false) }
        var probeStatus by rememberSaveable { mutableStateOf("") }
        var dedupeReads by rememberSaveable { mutableStateOf(false) }
        val readSeen = remember { mutableSetOf<String>() }
        var readRequestToken by remember { mutableStateOf(0) }
        val readQueue = remember { ArrayDeque<BluetoothGattCharacteristic>() }
        val notificationQueue = remember { ArrayDeque<Pair<BluetoothGattCharacteristic, BluetoothGattDescriptor>>() }
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        val probeScope = rememberCoroutineScope()

        fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        fun permissionsGranted() = permissions().all { ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED }
        fun produceResult() {
            val selected = (pairedDevices + devices).firstOrNull { it.address == selectedAddress }
            val scanText = devices.joinToString("\n") { "${it.name} | ${it.address} | rssi=${it.rssi}" }
            val serial = pairedDevices.joinToString("\n") { device -> pairedEndpointSummary(device.device) }
            val request = As100BluetoothInspectorMethod.request(As100BluetoothInspectorMethod.ID, emptyMap(), emptyList(), emptyList())
            val outcome = BluetoothInspectionOutcome(scanText, selected?.let { "${it.name}|${it.address}" }.orEmpty(), connectionStatus, endpoints, captured, serial, endpoints)
            result = As100BluetoothInspectorMethod.result(request, outcome, context.request.invocationContext)
        }

        fun refreshPaired() {
            if (adapter == null || !permissionsGranted()) return
            pairedDevices.clear()
            pairedDevices.addAll(adapter.bondedDevices.orEmpty().map { device ->
                NearbyBluetoothDevice(device, device.name.orEmpty().ifBlank { "Paired Bluetooth device" }, device.address, -999, paired = true)
            }.sortedBy { it.name.lowercase() })
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { if (permissionsGranted()) pendingScan = true }
        val callback = remember(adapter) {
            object : ScanCallback() {
                override fun onScanResult(type: Int, scanResult: ScanResult) {
                    val device = scanResult.device
                    mainHandler.post {
                        val item = NearbyBluetoothDevice(device, scanResult.scanRecord?.deviceName ?: device.name.orEmpty().ifBlank { "Unnamed BLE device" }, device.address, scanResult.rssi, paired = device.bondState == BluetoothDevice.BOND_BONDED)
                        devices.removeAll { it.address == item.address }
                        devices.add(item)
                    }
                }
                override fun onScanFailed(errorCode: Int) { mainHandler.post { scanning = false; connectionStatus = "Scan failed: $errorCode" } }
            }
        }

        fun beginScan() {
            if (adapter == null) { connectionStatus = "Bluetooth is unavailable."; return }
            if (!permissionsGranted()) { permissionLauncher.launch(permissions()); return }
            refreshPaired()
            devices.clear(); scanning = true; connectionStatus = "Scanning…"
            runCatching { adapter.bluetoothLeScanner?.startScan(callback) }.onFailure { scanning = false; connectionStatus = "Scan failed: ${it.message.orEmpty()}" }
            mainHandler.postDelayed({ runCatching { adapter.bluetoothLeScanner?.stopScan(callback) }; scanning = false; if (connectionStatus == "Scanning…") connectionStatus = "Scan complete" }, 10_000L)
        }

        LaunchedEffect(pendingScan) { if (pendingScan) { pendingScan = false; beginScan() } }
        LaunchedEffect(adapter) { if (adapter != null && permissionsGranted()) refreshPaired() }

        fun connectSelected() {
            val device = (pairedDevices + devices).firstOrNull { it.address == selectedAddress }?.device ?: return
            if (!permissionsGranted()) { permissionLauncher.launch(permissions()); return }
            if (device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) {
                endpoints = pairedEndpointSummary(device)
                connectionStatus = "Paired classic Bluetooth device selected; RFCOMM endpoints listed."
                return
            }
            gatt?.close(); connectionStatus = "Connecting…"
            gatt = device.connectGatt(androidContext, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
                    mainHandler.post { connectionStatus = if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) { g.discoverServices(); "Connected; discovering services…" } else "Disconnected (status=$status)" }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
                    mainHandler.post {
                        gattCharacteristics = g.services.flatMap { service ->
                            service.characteristics.map { characteristic ->
                                val p = characteristic.properties
                                GattCharacteristicInfo(
                                    service.uuid.toString(), characteristic, bluetoothLabel(characteristic.uuid),
                                    p and BluetoothGattCharacteristic.PROPERTY_READ != 0,
                                    p and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0,
                                    p and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                                )
                            }
                        }
                        endpoints = gattCharacteristics.groupBy { it.serviceUuid }.entries.joinToString("\n") { (serviceUuid, chars) ->
                            val serviceLabel = bluetoothLabel(UUID.fromString(serviceUuid))
                            "[$serviceLabel] $serviceUuid\n" + chars.joinToString("\n") {
                                "  ${it.label} (${it.characteristic.uuid}) read=${it.readable} write=${it.writable} notify=${it.notifiable}"
                            }
                        }
                        connectionStatus = if (status == BluetoothGatt.GATT_SUCCESS) "Connected; services discovered" else "Service discovery failed: $status"
                    }
                }
                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                    mainHandler.post {
                        readRequestToken += 1
                        val outcome = if (status == BluetoothGatt.GATT_SUCCESS) "ok" else "failed (GATT status $status)"
                        val value = characteristic.value.toDisplayValue()
                        val key = "${characteristic.uuid}:$status:$value"
                        if (!dedupeReads || readSeen.add(key)) captured = "$captured\n${bluetoothLabel(characteristic.uuid)} (${characteristic.uuid}) status=$status $outcome value=$value".trim()
                        readQueue.removeFirstOrNull()?.let { g.readCharacteristic(it) }
                    }
                }
                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                    mainHandler.post {
                        readRequestToken += 1
                        val outcome = if (status == BluetoothGatt.GATT_SUCCESS) "ok" else "failed (GATT status $status)"
                        val display = value.toDisplayValue()
                        val key = "${characteristic.uuid}:$status:$display"
                        if (!dedupeReads || readSeen.add(key)) captured = "$captured\n${bluetoothLabel(characteristic.uuid)} (${characteristic.uuid}) status=$status $outcome value=$display".trim()
                        readQueue.removeFirstOrNull()?.let { g.readCharacteristic(it) }
                    }
                }
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                    mainHandler.post { captured = "$captured\n${bluetoothLabel(characteristic.uuid)} (${characteristic.uuid}) notification=${characteristic.value.toDisplayValue()}".trim() }
                }
                override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                    mainHandler.post {
                        captured = "$captured\nCCCD ${descriptor.characteristic?.uuid} status=$status ${if (status == BluetoothGatt.GATT_SUCCESS) "enabled" else "failed (GATT status $status)"}".trim()
                        notificationQueue.removeFirstOrNull()?.let { (characteristic, nextDescriptor) ->
                            g.setCharacteristicNotification(characteristic, true)
                            nextDescriptor.value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(nextDescriptor)
                        }
                    }
                }
            })
        }

        fun readReadable() {
            val connection = gatt ?: return
            readQueue.clear()
            readSeen.clear(); dedupeReads = true; captured = ""
            connection.services.flatMap { it.characteristics }.filter { it.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0 }.forEach { readQueue.add(it) }
            readQueue.removeFirstOrNull()?.let { connection.readCharacteristic(it) }
        }

        fun readEndpoint(characteristic: BluetoothGattCharacteristic) {
            val connection = gatt ?: return
            if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) return
            readQueue.clear()
            readSeen.clear(); dedupeReads = true; captured = ""
            readQueue.add(characteristic)
            probeStatus = "Reading ${bluetoothLabel(characteristic.uuid)}…"
            val token = ++readRequestToken
            val accepted = connection.readCharacteristic(characteristic)
            if (!accepted) {
                readRequestToken += 1
                probeStatus = "Read request rejected by the device or connection."
            } else {
                mainHandler.postDelayed({
                    if (readRequestToken == token) {
                        readRequestToken += 1
                        readQueue.clear()
                        probeStatus = "Read timed out; the endpoint may require pairing or encryption."
                    }
                }, 5_000L)
            }
        }

        fun sampleEndpoint(characteristic: BluetoothGattCharacteristic) {
            if (probeRunning || characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) return
            val connection = gatt ?: return
            probeRunning = true
            dedupeReads = false
            probeStatus = "Sampling ${bluetoothLabel(characteristic.uuid)} every 1 second (10 samples)…"
            probeScope.launch {
                repeat(10) {
                    connection.readCharacteristic(characteristic)
                    delay(1_000L)
                }
                probeRunning = false
                probeStatus = "Sampling complete"
            }
        }

        fun listenToNotifications() {
            val connection = gatt ?: return
            notificationQueue.clear()
            connection.services.flatMap { it.characteristics }
                .filter { it.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 }
                .mapNotNull { characteristic -> characteristic.getDescriptor(cccdUuid)?.let { characteristic to it } }
                .forEach { notificationQueue.add(it) }
            val first = notificationQueue.removeFirstOrNull()
            if (first == null) {
                connectionStatus = "No notification endpoints with CCCD support found"
            } else {
                connection.setCharacteristicNotification(first.first, true)
                first.second.value = if (first.first.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) BluetoothGattDescriptor.ENABLE_INDICATION_VALUE else BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                connection.writeDescriptor(first.second)
                connectionStatus = "Subscribing to notification streams…"
            }
        }

        DisposableEffect(Unit) { onDispose { runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }; gatt?.close() } }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { produceResult() }, { result?.let(onConfirmed) }, onCancel) {
            Text("Only nearby Bluetooth devices and public BLE services are inspected. Reads and notifications are user initiated.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::beginScan, Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Scan nearby Bluetooth") }
            OutlinedButton(onClick = {
                if (!permissionsGranted()) permissionLauncher.launch(permissions()) else { refreshPaired(); connectionStatus = "Paired devices refreshed" }
            }, Modifier.fillMaxWidth()) { Text("Refresh paired devices") }
            Text("Status: $connectionStatus", style = MaterialTheme.typography.bodySmall)
            if (pairedDevices.isNotEmpty()) {
                Text("Paired devices", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                pairedDevices.forEach { device ->
                    OutlinedButton(onClick = { selectedAddress = device.address }, Modifier.fillMaxWidth()) {
                        Text(if (selectedAddress == device.address) "✓ ${device.name} (${device.address})" else "${device.name} (${device.address})")
                    }
                }
            }
            if (devices.isNotEmpty()) Text("Nearby devices", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            devices.forEach { device ->
                OutlinedButton(onClick = { selectedAddress = device.address }, Modifier.fillMaxWidth()) {
                    Text(if (selectedAddress == device.address) "✓ ${device.name} (${device.address}) · ${device.rssi} dBm" else "${device.name} (${device.address}) · ${device.rssi} dBm")
                }
            }
            if (selectedAddress.isNotBlank()) {
                Button(onClick = ::connectSelected, Modifier.fillMaxWidth()) { Text("Connect and discover services") }
                Row(Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = ::readReadable) { Text("Read endpoints") }
                    Spacer(Modifier.padding(3.dp))
                    OutlinedButton(onClick = ::listenToNotifications) { Text("Listen to streams") }
                }
                OutlinedButton(onClick = {
                    val selected = (pairedDevices + devices).firstOrNull { it.address == selectedAddress } ?: return@OutlinedButton
                    DeviceRegistry.save(androidContext, RegisteredDevice(name = selected.name, transport = if (selected.device.type == BluetoothDevice.DEVICE_TYPE_CLASSIC) DeviceTransport.BLUETOOTH_CLASSIC else DeviceTransport.BLE, address = selected.address, profile = endpoints))
                    savedStatus = "Saved to device registry."
                }, Modifier.fillMaxWidth()) { Text("Save device profile") }
            }
            if (savedStatus.isNotBlank()) Text(savedStatus, style = MaterialTheme.typography.bodySmall)
            if (gattCharacteristics.isNotEmpty()) {
                Text("GATT services and endpoints", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                gattCharacteristics.groupBy { it.serviceUuid }.forEach { (serviceUuid, characteristics) ->
                    val open = serviceUuid in expandedServices
                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.elevatedCardColors()
                    ) {
                        OutlinedButton(
                            onClick = {
                                expandedServices = if (open) expandedServices - serviceUuid else expandedServices + serviceUuid
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${if (open) "▲" else "▼"} ${bluetoothLabel(UUID.fromString(serviceUuid))} ($serviceUuid) · ${characteristics.size} endpoints")
                        }
                        if (open) {
                            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                                characteristics.forEach { item ->
                                    Text(item.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        "${item.characteristic.uuid}\n${if (item.readable) "read" else ""}${if (item.writable) " · write" else ""}${if (item.notifiable) " · notify/indicate" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Row(Modifier.fillMaxWidth()) {
                                        if (item.readable) {
                                            OutlinedButton(onClick = { readEndpoint(item.characteristic) }) { Text("Read") }
                                            Spacer(Modifier.padding(2.dp))
                                            OutlinedButton(onClick = { sampleEndpoint(item.characteristic) }, enabled = !probeRunning) { Text("Sample") }
                                        }
                                        if (item.notifiable) {
                                            Spacer(Modifier.padding(2.dp))
                                            OutlinedButton(onClick = { listenToNotifications() }) { Text("Subscribe") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (probeStatus.isNotBlank()) Text(probeStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            if (captured.isNotBlank()) Surface(Modifier.fillMaxWidth().padding(top = 8.dp), tonalElevation = 1.dp) { Text(captured, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

private fun ByteArray?.toHex(): String = this?.joinToString("") { "%02x".format(it) }.orEmpty()

@SuppressLint("MissingPermission")
private fun pairedEndpointSummary(device: BluetoothDevice): String {
    val uuids = device.uuids.orEmpty().joinToString(",") { it.uuid.toString() }
    val spp = device.uuids.orEmpty().any { it.uuid.toString().equals("00001101-0000-1000-8000-00805f9b34fb", true) }
    return "${device.name.orEmpty().ifBlank { "Paired Bluetooth device" }} | ${device.address} | type=${device.type} | bonded=true | classic_spp_candidate=$spp | uuids=$uuids"
}

private fun ByteArray?.toDisplayValue(): String {
    if (this == null || this.isEmpty()) return "<empty>"
    val hex = toHex()
    val text = runCatching { this.toString(Charsets.UTF_8) }.getOrDefault("")
    val printable = text.isNotBlank() && text.all { it == '\n' || it == '\r' || it == '\t' || it in ' '..'~' }
    return if (printable) "$hex (UTF-8: ${text.replace("\n", "\\n")})" else hex
}
