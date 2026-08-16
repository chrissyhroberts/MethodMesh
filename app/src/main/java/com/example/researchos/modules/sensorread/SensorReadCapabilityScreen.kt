package com.example.researchos.modules.sensorread

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
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
import com.example.researchos.transport.workflow.ui.CapabilityPresentationMode
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

private val sensorServiceUuid: UUID = UUID.fromString("b6f2a900-9b8f-4f4e-9a1f-4f37a0010000")
private val manifestUuid: UUID = UUID.fromString("b6f2a901-9b8f-4f4e-9a1f-4f37a0010000")
private val readingUuid: UUID = UUID.fromString("b6f2a902-9b8f-4f4e-9a1f-4f37a0010000")

private data class NearbySensor(val device: BluetoothDevice, val name: String, val address: String, val rssi: Int, val registered: RegisteredDevice? = null)

object SensorReadCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SensorReadMethod.ID
    override val title = "Read sensor"
    override val description = "Read a single value, trace, or average from a ResearchOS BLE sensor node."

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
        val focusedLaunch = context.presentationMode == CapabilityPresentationMode.IntentLaunch
        var registryDevices by remember { mutableStateOf(sensorRegistryDevices(androidContext)) }
        val suppliedDeviceId = supplied.firstPresent("device_id", "input_device_id", "sensor_device_id", "input_sensor_device_id")
        val suppliedSensorId = supplied.firstPresent("sensor_id", "input_sensor_id")
        val suppliedSensorProfile = supplied.firstPresent("sensor_profile", "input_sensor_profile", "sensor_type", "input_sensor_type")
        val suppliedMode = normalizeMode(supplied.firstPresent("sensor_read_mode", "input_sensor_read_mode", "mode", "input_mode").ifBlank { "single" })
        val suppliedDurationSeconds = supplied.firstPresent("duration_seconds", "input_duration_seconds", "sensor_duration_seconds").ifBlank { "30" }
        val suppliedIntervalSeconds = supplied.firstPresent("sample_interval_seconds", "input_sample_interval_seconds", "sensor_sample_interval_seconds").ifBlank { "5" }
        val suppliedMatchPolicy = supplied.firstPresent("device_match_policy", "input_device_match_policy", "fallback_to_nearby").ifBlank { "fallback" }
        var selectedDeviceId by rememberSaveable(suppliedDeviceId) { mutableStateOf(suppliedDeviceId) }
        var sensorId by rememberSaveable(suppliedSensorId) { mutableStateOf(suppliedSensorId) }
        var sensorProfile by rememberSaveable(suppliedSensorProfile) { mutableStateOf(suppliedSensorProfile) }
        var mode by rememberSaveable(suppliedMode) { mutableStateOf(suppliedMode) }
        var durationSeconds by rememberSaveable(suppliedDurationSeconds) { mutableStateOf(suppliedDurationSeconds) }
        var intervalSeconds by rememberSaveable(suppliedIntervalSeconds) { mutableStateOf(suppliedIntervalSeconds) }
        var matchPolicy by rememberSaveable(suppliedMatchPolicy) { mutableStateOf(suppliedMatchPolicy) }
        var status by rememberSaveable { mutableStateOf("Ready to read a registered ResearchOS sensor.") }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var deviceMenuOpen by rememberSaveable { mutableStateOf(false) }
        var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
        var policyMenuOpen by rememberSaveable { mutableStateOf(false) }
        var selectedNearby by remember { mutableStateOf<NearbySensor?>(null) }
        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var manifestCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var readingCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var manifestJson by rememberSaveable { mutableStateOf("") }
        var startedTime by rememberSaveable { mutableStateOf("") }
        var readInProgress by rememberSaveable { mutableStateOf(false) }
        var samplesRemaining by rememberSaveable { mutableStateOf(0) }
        var autoAttempted by rememberSaveable(context.action.canonicalId, suppliedDeviceId, suppliedMode, suppliedMatchPolicy) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val nearby = remember { mutableStateListOf<NearbySensor>() }
        val sampleValues = remember { mutableStateListOf<String>() }

        fun permissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        fun permissionsGranted() = permissions().all { ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            status = if (grants.values.all { it }) "Bluetooth permission granted. Press read again." else "Bluetooth permission was denied."
        }

        fun makeRequest() = As100SensorReadMethod.request(
            action = As100SensorReadMethod.ID,
            context = context.request.invocationContext.asMap(As100SensorReadMethod.ID) + supplied,
            signals = emptyList(),
            inputs = emptyList()
        )

        fun produce(values: Map<String, String>, ok: Boolean) {
            result = As100SensorReadMethod.result(makeRequest(), SensorReadOutcome(values, ok), context.request.invocationContext)
            readInProgress = false
        }

        fun baseValues(okStatus: String = "succeeded"): LinkedHashMap<String, String> {
            val actual = selectedNearby
            val registered = actual?.registered
            val requested = selectedDeviceId.trim()
            val actualId = actualDeviceId(actual, registered)
            val substituted = requested.isNotBlank() && actualId.isNotBlank() && requested != actualId
            return linkedMapOf(
                SensorReadFields.STATUS to okStatus,
                SensorReadFields.MODE to mode,
                SensorReadFields.REQUESTED_DEVICE_ID to requested,
                SensorReadFields.ACTUAL_DEVICE_ID to actualId,
                SensorReadFields.DEVICE_NAME to (registered?.name ?: actual?.name).orEmpty(),
                SensorReadFields.DEVICE_ADDRESS to (actual?.address ?: registered?.address).orEmpty(),
                SensorReadFields.REQUESTED_SENSOR_ID to sensorId.trim(),
                SensorReadFields.SENSOR_PROFILE to sensorProfile.trim(),
                SensorReadFields.DEVICE_SELECTION_MODE to selectionMode(requested, actualId, actual?.registered),
                SensorReadFields.DEVICE_SUBSTITUTION to substituted.toString(),
                SensorReadFields.DEVICE_SUBSTITUTION_REASON to if (substituted) "requested_device_not_found_or_operator_selected" else "",
                SensorReadFields.DURATION_SECONDS to durationSeconds.trim(),
                SensorReadFields.SAMPLE_INTERVAL_SECONDS to intervalSeconds.trim(),
                SensorReadFields.STARTED_TIME_ISO to startedTime,
                SensorReadFields.MANIFEST_JSON to manifestJson
            )
        }

        fun finishWithSamples() {
            val finished = Instant.now().toString()
            if (sampleValues.isEmpty()) {
                produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "No sensor samples were read.", SensorReadFields.FINISHED_TIME_ISO to finished), false)
                return
            }
            val traceArray = JSONArray().apply {
                sampleValues.forEachIndexed { index, sample ->
                    put(JSONObject().apply {
                        put("index", index + 1)
                        put("reading", JSONObject(sample))
                    })
                }
            }
            val latest = JSONObject(sampleValues.last())
            val summary = summariseSamples(sampleValues)
            val values = baseValues("succeeded").apply {
                put(SensorReadFields.SAMPLE_COUNT, sampleValues.size.toString())
                put(SensorReadFields.FINISHED_TIME_ISO, finished)
                put(SensorReadFields.READING_JSON, sampleValues.last())
                put(SensorReadFields.TRACE_JSON, if (mode == "trace") traceArray.toString() else "")
                put(SensorReadFields.SUMMARY_JSON, if (mode == "average") summary.toString() else "")
                put(SensorReadFields.ACTUAL_SENSOR_ID, latest.optString("sensor_id"))
                put(SensorReadFields.SENSOR_PROFILE, sensorProfile.ifBlank { latest.optString("sensor_profile").ifBlank { latest.optString("sensor_type") } })
                put(SensorReadFields.TEMPERATURE_C, valueFor(latest, summary, "temperature_c", mode))
                put(SensorReadFields.RELATIVE_HUMIDITY_PCT, valueFor(latest, summary, "relative_humidity_pct", mode))
                put(SensorReadFields.PAYLOAD_SHA256, latest.optString("payload_sha256"))
            }
            status = "Sensor read complete (${sampleValues.size} sample${if (sampleValues.size == 1) "" else "s"})."
            produce(values, true)
        }

        fun requestNextSample() {
            val characteristic = readingCharacteristic
            val connection = gatt
            if (characteristic == null || connection == null) {
                produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Sensor reading endpoint is not connected."), false)
                return
            }
            status = "Reading sample ${sampleValues.size + 1}…"
            if (!connection.readCharacteristic(characteristic)) {
                produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Could not queue BLE characteristic read."), false)
            }
        }

        fun beginSampling() {
            val duration = durationSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 30
            val interval = intervalSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 5
            durationSeconds = duration.toString()
            intervalSeconds = interval.toString()
            sampleValues.clear()
            startedTime = Instant.now().toString()
            samplesRemaining = when (mode) {
                "trace", "average" -> ((duration.toDouble() / interval.toDouble()).roundToInt()).coerceAtLeast(1)
                else -> 1
            }
            readInProgress = true
            requestNextSample()
        }

        val gattCallback = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                    handler.post {
                        status = if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                            handler.postDelayed({ g.discoverServices() }, 350L)
                            "Connected; discovering ResearchOS sensor service…"
                        } else {
                            if (readInProgress) produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Disconnected before read completed (status=$statusCode)."), false)
                            "Disconnected (status=$statusCode)."
                        }
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                    handler.post {
                        val service = g.getService(sensorServiceUuid)
                        manifestCharacteristic = service?.getCharacteristic(manifestUuid)
                        readingCharacteristic = service?.getCharacteristic(readingUuid)
                        if (statusCode != BluetoothGatt.GATT_SUCCESS || service == null || manifestCharacteristic == null || readingCharacteristic == null) {
                            produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Connected device does not expose the ResearchOS sensor contract."), false)
                        } else {
                            status = "Sensor endpoint ready; reading manifest…"
                            g.readCharacteristic(manifestCharacteristic)
                        }
                    }
                }

                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                    handleRead(g, characteristic, characteristic.value, statusCode)
                }

                override fun onCharacteristicRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
                    handleRead(g, characteristic, value, statusCode)
                }

                private fun handleRead(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, statusCode: Int) {
                    handler.post {
                        if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                            produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "BLE read failed with GATT status $statusCode."), false)
                            return@post
                        }
                        val text = value.toString(Charsets.UTF_8)
                        when (characteristic.uuid) {
                            manifestUuid -> {
                                manifestJson = text
                                val manifest = runCatching { JSONObject(text) }.getOrNull()
                                if (sensorProfile.isBlank()) sensorProfile = manifest?.optString("sensor_profile").orEmpty()
                                status = "Manifest read. Starting ${modeLabel(mode).lowercase()}."
                                beginSampling()
                            }
                            readingUuid -> {
                                val filtered = filterSample(text, sensorId, sensorProfile)
                                sampleValues.add(filtered)
                                samplesRemaining -= 1
                                if (samplesRemaining <= 0 || mode == "single") {
                                    finishWithSamples()
                                } else {
                                    handler.postDelayed({ requestNextSample() }, (intervalSeconds.toLongOrNull() ?: 5L).coerceAtLeast(1L) * 1000L)
                                }
                            }
                        }
                    }
                }
            }
        }

        val callback = remember {
            object : ScanCallback() {
                override fun onScanResult(callbackType: Int, scanResult: ScanResult) {
                    val device = scanResult.device ?: return
                    val address = device.address ?: return
                    val name = scanResult.scanRecord?.deviceName ?: runCatching { device.name }.getOrNull().orEmpty()
                    val services = scanResult.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                    val reg = registryDevices.firstOrNull { it.address.equals(address, true) }
                    val looksLikeSensor = services.contains(sensorServiceUuid) || reg != null || name.startsWith("ResearchOS", true)
                    if (!looksLikeSensor) return
                    val item = NearbySensor(device, name.ifBlank { reg?.name ?: "ResearchOS sensor" }, address, scanResult.rssi, reg)
                    nearby.removeAll { it.address == address }
                    nearby.add(item)
                }
            }
        }

        fun stopScan() {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            scanning = false
        }

        fun discoverOnly() {
            val array = JSONArray().apply {
                nearby.forEach { item ->
                    put(JSONObject().apply {
                        put("name", item.name)
                        put("address", item.address)
                        put("rssi", item.rssi)
                        put("registered_device_id", item.registered?.id.orEmpty())
                    })
                }
            }
            produce(
                linkedMapOf(
                    SensorReadFields.STATUS to "discovered",
                    SensorReadFields.MODE to "discover",
                    SensorReadFields.IN_RANGE_DEVICE_COUNT to nearby.size.toString(),
                    SensorReadFields.IN_RANGE_DEVICES_JSON to array.toString(),
                    SensorReadFields.FINISHED_TIME_ISO to Instant.now().toString()
                ),
                true
            )
        }

        fun connectAndRead(item: NearbySensor) {
            if (!permissionsGranted()) { permissionLauncher.launch(permissions()); return }
            stopScan()
            selectedNearby = item
            result = null
            manifestJson = ""
            sampleValues.clear()
            readInProgress = true
            status = "Connecting to ${item.name}…"
            runCatching { gatt?.close() }
            gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                item.device.connectGatt(androidContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                item.device.connectGatt(androidContext, false, gattCallback)
            }
        }

        fun startScan(autoConnect: Boolean) {
            if (adapter == null || !adapter.isEnabled) { status = "Bluetooth is not enabled."; return }
            if (!permissionsGranted()) { permissionLauncher.launch(permissions()); return }
            registryDevices = sensorRegistryDevices(androidContext)
            nearby.clear()
            selectedNearby = null
            result = null
            status = "Scanning for registered ResearchOS sensors…"
            scanning = true
            runCatching { adapter.bluetoothLeScanner?.startScan(callback) }
                .onFailure { error -> scanning = false; produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Scan failed: ${error.message.orEmpty()}"), false) }
            handler.postDelayed({
                stopScan()
                val requested = selectedDeviceId.trim()
                val requestedRegistry = registryDevices.firstOrNull { it.id == requested || it.name == requested || it.address.equals(requested, true) }
                val requestedNearby = nearby.firstOrNull { it.registered?.id == requestedRegistry?.id || it.address.equals(requestedRegistry?.address.orEmpty(), true) }
                when {
                    mode == "discover" -> discoverOnly()
                    autoConnect && requested.isNotBlank() && requestedNearby != null -> connectAndRead(requestedNearby)
                    autoConnect && requested.isNotBlank() && matchPolicy.equals("strict", true) -> produce(baseValues("failed") + mapOf(SensorReadFields.ERROR to "Requested device was not found nearby."), false)
                    autoConnect && nearby.size == 1 -> connectAndRead(nearby.first())
                    autoConnect && nearby.isNotEmpty() -> status = "Requested device not found. Choose a nearby sensor to continue."
                    else -> status = if (nearby.isEmpty()) "No ResearchOS sensors found nearby." else "Scan complete. Choose a sensor."
                }
            }, 8_000L)
        }


        fun readNow() {
            result = null
            val requested = selectedDeviceId.trim()
            val registry = registryDevices.firstOrNull { it.id == requested || it.name == requested || it.address.equals(requested, true) }
            val direct = nearby.firstOrNull { it.address.equals(registry?.address.orEmpty(), true) || it.registered?.id == registry?.id }
                ?: selectedNearby
            when {
                direct != null -> connectAndRead(direct)
                else -> startScan(autoConnect = focusedLaunch || context.startsImmediately || requested.isNotBlank())
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                stopScan()
                runCatching { gatt?.close() }
            }
        }

        LaunchedEffect(focusedLaunch, context.startsImmediately, supplied) {
            if ((focusedLaunch || context.startsImmediately) && !autoAttempted) {
                autoAttempted = true
                readNow()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; readNow() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                if (focusedLaunch) "Reading sensor using the configured request." else "Configure and read a registered ESP32 ResearchOS sensor. If a requested device is not found, fallback mode lets the operator choose a nearby sensor and records the substitution.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(8.dp))
            if (focusedLaunch) {
                SensorReadLaunchSummary(
                    device = deviceLabel(selectedDeviceId, registryDevices),
                    sensorId = sensorId,
                    sensorProfile = sensorProfile,
                    mode = mode,
                    durationSeconds = durationSeconds,
                    intervalSeconds = intervalSeconds,
                    matchPolicy = matchPolicy
                )
            } else {
                Text("Known sensor", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { deviceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(deviceLabel(selectedDeviceId, registryDevices), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(expanded = deviceMenuOpen, onDismissRequest = { deviceMenuOpen = false }, modifier = Modifier.fillMaxWidth(0.9f)) {
                    DropdownMenuItem(text = { Text("Choose nearby sensor") }, onClick = { selectedDeviceId = ""; deviceMenuOpen = false })
                    registryDevices.forEach { device ->
                        DropdownMenuItem(
                            text = { Column { Text(device.name); Text("${device.id} · ${device.address}", style = MaterialTheme.typography.bodySmall) } },
                            onClick = { selectedDeviceId = device.id; deviceMenuOpen = false }
                        )
                    }
                }
                OutlinedTextField(sensorId, { sensorId = it }, label = { Text("Sensor ID (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(sensorProfile, { sensorProfile = it }, label = { Text("Sensor profile (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Read mode", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { modeMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(modeLabel(mode), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(expanded = modeMenuOpen, onDismissRequest = { modeMenuOpen = false }) {
                    listOf("single", "trace", "average", "discover").forEach { option ->
                        DropdownMenuItem(text = { Text(modeLabel(option)) }, onClick = { mode = option; modeMenuOpen = false })
                    }
                }
                if (mode == "trace" || mode == "average") {
                    OutlinedTextField(durationSeconds, { durationSeconds = it.filter(Char::isDigit) }, label = { Text("Duration (seconds)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(intervalSeconds, { intervalSeconds = it.filter(Char::isDigit) }, label = { Text("Sample interval (seconds)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                Spacer(Modifier.height(8.dp))
                Text("Device matching", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { policyMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(matchPolicyLabel(matchPolicy), modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    Text("▼")
                }
                DropdownMenu(expanded = policyMenuOpen, onDismissRequest = { policyMenuOpen = false }) {
                    listOf("fallback", "strict", "any_nearby").forEach { option ->
                        DropdownMenuItem(text = { Text(matchPolicyLabel(option)) }, onClick = { matchPolicy = option; policyMenuOpen = false })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { readNow() }, modifier = Modifier.fillMaxWidth()) { Text(if (readInProgress) "Reading…" else if (result == null) "Read sensor" else "Read again") }
            OutlinedButton(onClick = { startScan(autoConnect = false) }, modifier = Modifier.fillMaxWidth()) { Text(if (scanning) "Scanning…" else "Choose nearby sensor") }
            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(180.dp)) {
                items(nearby, key = { it.address }) { item ->
                    ElevatedCard(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text("${item.address} · RSSI ${item.rssi}", style = MaterialTheme.typography.bodySmall)
                            Text("Registry: ${item.registered?.id ?: "not saved"}", style = MaterialTheme.typography.bodySmall)
                            Row {
                                OutlinedButton(onClick = { selectedNearby = item }) { Text("Select") }
                                Spacer(Modifier.padding(4.dp))
                                Button(onClick = { connectAndRead(item) }) { Text("Read this sensor") }
                            }
                        }
                    }
                }
            }
            if (manifestJson.isNotBlank()) {
                Text("Manifest", fontWeight = FontWeight.SemiBold)
                Text(manifestJson, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100SensorReadMethod.ID,
                examples = listOf(
                    IntentExample("Single sensor read", "Read a registered sensor once.", "com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_read_mode='single',return_mode='flat')"),
                    IntentExample("Average over time", "Average AHT20 fields over 60 seconds.", "com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_profile='aht20',input_sensor_read_mode='average',input_duration_seconds='60',input_sample_interval_seconds='5',return_mode='flat')"),
                    IntentExample("Strict device match", "Fail if the specified sensor is not nearby.", "com.example.researchos.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_device_match_policy='strict',return_mode='flat')")
                )
            )
        }
    }
}

@Composable
private fun SensorReadLaunchSummary(
    device: String,
    sensorId: String,
    sensorProfile: String,
    mode: String,
    durationSeconds: String,
    intervalSeconds: String,
    matchPolicy: String
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Configured sensor read", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Device: $device", style = MaterialTheme.typography.bodyMedium)
            Text("Mode: ${modeLabel(mode)}", style = MaterialTheme.typography.bodyMedium)
            if (sensorId.isNotBlank()) Text("Sensor ID: $sensorId", style = MaterialTheme.typography.bodyMedium)
            if (sensorProfile.isNotBlank()) Text("Sensor profile: $sensorProfile", style = MaterialTheme.typography.bodyMedium)
            if (mode == "trace" || mode == "average") {
                Text("Sampling: ${durationSeconds.ifBlank { "30" }}s every ${intervalSeconds.ifBlank { "5" }}s", style = MaterialTheme.typography.bodyMedium)
            }
            Text("Matching: ${matchPolicyLabel(matchPolicy)}", style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun sensorRegistryDevices(context: android.content.Context): List<RegisteredDevice> =
    DeviceRegistry.all(context).filter {
        it.transport == DeviceTransport.BLE &&
            !it.paused &&
            it.enabled &&
            (it.profile.contains("researchos_ble_sensor", true) || it.id.startsWith("sensor:") || it.name.contains("sensor", true))
    }.sortedBy { it.name.lowercase() }

private fun Map<String, String>.firstPresent(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun normalizeMode(value: String): String = when (value.trim().lowercase()) {
    "trace", "timeseries", "time_series" -> "trace"
    "average", "avg", "mean" -> "average"
    "discover", "scan" -> "discover"
    else -> "single"
}

private fun modeLabel(mode: String): String = when (mode) {
    "trace" -> "Trace over time"
    "average" -> "Average over time"
    "discover" -> "Discover nearby sensors"
    else -> "Single point read"
}

private fun matchPolicyLabel(policy: String): String = when (policy.trim().lowercase()) {
    "strict", "false" -> "Strict: fail if requested device is absent"
    "any_nearby" -> "Any nearby ResearchOS sensor"
    else -> "Fallback: choose nearby if requested device is absent"
}

private fun deviceLabel(deviceId: String, devices: List<RegisteredDevice>): String {
    if (deviceId.isBlank()) return "Choose nearby sensor"
    val device = devices.firstOrNull { it.id == deviceId || it.name == deviceId || it.address.equals(deviceId, true) }
    return device?.let { "${it.name} (${it.id})" } ?: deviceId
}

private fun actualDeviceId(item: NearbySensor?, registered: RegisteredDevice?): String {
    val manifestId = runCatching { JSONObject(registered?.profile.orEmpty()).optString("device_id") }.getOrNull().orEmpty()
    return manifestId.ifBlank { registered?.id.orEmpty().ifBlank { item?.address.orEmpty() } }
}

private fun selectionMode(requested: String, actual: String, registered: RegisteredDevice?): String = when {
    requested.isBlank() -> "operator_selected"
    requested == actual -> "requested_device"
    registered != null -> "fallback_registered_device"
    else -> "fallback_operator_selected"
}

private fun filterSample(text: String, requestedSensorId: String, requestedProfile: String): String {
    val root = runCatching { JSONObject(text) }.getOrElse { return text }
    val readings = root.optJSONArray("readings") ?: root.optJSONArray("sensors")
    if (readings == null) return text
    for (i in 0 until readings.length()) {
        val item = readings.optJSONObject(i) ?: continue
        val idOk = requestedSensorId.isBlank() || item.optString("sensor_id").equals(requestedSensorId, true)
        val profile = item.optString("sensor_profile").ifBlank { item.optString("sensor_type") }
        val profileOk = requestedProfile.isBlank() || profile.equals(requestedProfile, true)
        if (idOk && profileOk) return item.toString()
    }
    return text
}

private fun summariseSamples(samples: List<String>): JSONObject {
    val numeric = linkedMapOf<String, MutableList<Double>>()
    samples.forEach { sample ->
        val json = runCatching { JSONObject(sample) }.getOrNull() ?: return@forEach
        json.keys().forEach { key ->
            val value = json.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }
            if (number != null) numeric.getOrPut(key) { mutableListOf() }.add(number)
        }
    }
    return JSONObject().apply {
        put("sample_count", samples.size)
        numeric.forEach { (key, values) ->
            put("${key}_mean", values.average())
            put("${key}_min", values.minOrNull())
            put("${key}_max", values.maxOrNull())
        }
    }
}

private fun valueFor(latest: JSONObject, summary: JSONObject, key: String, mode: String): String =
    if (mode == "average") summary.optString("${key}_mean")
    else latest.optString(key)
