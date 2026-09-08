package com.example.methodmesh.modules.sensorread

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
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.sensorprovisioner.SensorProvisioningProfiles
import com.example.methodmesh.modules.sensorprovisioner.extractJsonObject
import com.example.methodmesh.platform.devices.DeviceRegistry
import com.example.methodmesh.platform.devices.DeviceTransport
import com.example.methodmesh.platform.devices.RegisteredDevice
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

private val sensorServiceUuid: UUID = UUID.fromString("b6f2a900-9b8f-4f4e-9a1f-4f37a0010000")
private val manifestUuid: UUID = UUID.fromString("b6f2a901-9b8f-4f4e-9a1f-4f37a0010000")
private val readingUuid: UUID = UUID.fromString("b6f2a902-9b8f-4f4e-9a1f-4f37a0010000")
private val commandUuid: UUID = UUID.fromString("b6f2a903-9b8f-4f4e-9a1f-4f37a0010000")

private const val liveWindowMaxSamples = 30
private const val minimumLiveRefreshMs = 250L
private const val maximumLiveRefreshMs = 60_000L

private data class NearbySensor(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
    val registered: RegisteredDevice? = null
)

object SensorReadCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SensorReadMethod.ID
    override val title = "Read sensor"
    override val description = "Read a single value, trace, average, or live stream from a MethodMesh BLE sensor node."

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

        val supplied = remember(
            context.request.settings,
            context.action.settings,
            context.request.invocationContext
        ) {
            context.request.invocationContext.asMap(context.action.canonicalId) +
                context.request.settings +
                context.action.settings
        }

        val focusedLaunch = context.presentationMode == CapabilityPresentationMode.IntentLaunch
        var registryDevices by remember { mutableStateOf(sensorRegistryDevices(androidContext)) }

        val suppliedDeviceId =
            supplied.firstPresent("device_id", "input_device_id", "sensor_device_id", "input_sensor_device_id")
        val suppliedSensorId = supplied.firstPresent("sensor_id", "input_sensor_id")
        val suppliedSensorProfile =
            supplied.firstPresent("sensor_profile", "input_sensor_profile", "sensor_type", "input_sensor_type")
        val requestedMode =
            normalizeMode(supplied.firstPresent("sensor_read_mode", "input_sensor_read_mode", "mode", "input_mode").ifBlank { "single" })
        // Live/freeze is an interactive operator workflow. External/headless launches
        // fall back to a single point so they cannot wait indefinitely for Freeze.
        val suppliedMode = if (focusedLaunch && requestedMode == "live") "single" else requestedMode
        val suppliedDurationSeconds =
            supplied.firstPresent("duration_seconds", "input_duration_seconds", "sensor_duration_seconds").ifBlank { "30" }
        val suppliedIntervalSeconds =
            supplied.firstPresent("sample_interval_seconds", "input_sample_interval_seconds", "sensor_sample_interval_seconds").ifBlank { "5" }
        val suppliedMatchPolicy =
            supplied.firstPresent("device_match_policy", "input_device_match_policy", "fallback_to_nearby").ifBlank { "fallback" }

        var selectedDeviceId by rememberSaveable(suppliedDeviceId) { mutableStateOf(suppliedDeviceId) }
        var sensorId by rememberSaveable(suppliedSensorId) { mutableStateOf(suppliedSensorId) }
        var sensorProfile by rememberSaveable(suppliedSensorProfile) { mutableStateOf(suppliedSensorProfile) }
        var mode by rememberSaveable(suppliedMode) { mutableStateOf(suppliedMode) }
        var durationSeconds by rememberSaveable(suppliedDurationSeconds) { mutableStateOf(suppliedDurationSeconds) }
        var intervalSeconds by rememberSaveable(suppliedIntervalSeconds) { mutableStateOf(suppliedIntervalSeconds) }
        var matchPolicy by rememberSaveable(suppliedMatchPolicy) { mutableStateOf(suppliedMatchPolicy) }

        var status by rememberSaveable { mutableStateOf("Ready to read a registered MethodMesh sensor.") }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var modeMenuOpen by rememberSaveable { mutableStateOf(false) }
        var selectedNearby by remember { mutableStateOf<NearbySensor?>(null) }

        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var manifestCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var readingCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var commandCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }

        var manifestJson by rememberSaveable { mutableStateOf("") }
        var startedTime by rememberSaveable { mutableStateOf("") }
        var readInProgress by rememberSaveable { mutableStateOf(false) }
        var samplesRemaining by rememberSaveable { mutableStateOf(0) }
        var liveFrozen by rememberSaveable { mutableStateOf(false) }
        var livePollRunnable by remember { mutableStateOf<Runnable?>(null) }

        var autoAttempted by rememberSaveable(
            context.action.canonicalId,
            suppliedDeviceId,
            suppliedMode,
            suppliedMatchPolicy
        ) { mutableStateOf(false) }

        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val nearby = remember { mutableStateListOf<NearbySensor>() }
        val sampleValues = remember { mutableStateListOf<String>() }

        LaunchedEffect(
            selectedDeviceId,
            sensorId,
            sensorProfile,
            mode,
            durationSeconds,
            intervalSeconds,
            matchPolicy
        ) {
            context.onSettingsChanged(
                mapOf(
                    "device_id" to selectedDeviceId,
                    "sensor_id" to sensorId,
                    "sensor_profile" to sensorProfile,
                    "sensor_read_mode" to mode,
                    "duration_seconds" to durationSeconds,
                    "sample_interval_seconds" to intervalSeconds,
                    "device_match_policy" to matchPolicy
                )
            )
        }

        fun permissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun permissionsGranted(): Boolean =
            permissions().all {
                ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED
            }

        val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
                status =
                    if (grants.values.all { it }) {
                        "Bluetooth permission granted. Press read again."
                    } else {
                        "Bluetooth permission was denied."
                    }
            }

        fun makeRequest() = As100SensorReadMethod.request(
            action = As100SensorReadMethod.ID,
            context = context.request.invocationContext.asMap(As100SensorReadMethod.ID) + supplied,
            signals = emptyList(),
            inputs = emptyList()
        )

        fun cancelLivePoll() {
            livePollRunnable?.let(handler::removeCallbacks)
            livePollRunnable = null
        }

        fun closeGattAfterRead() {
            cancelLivePoll()
            val connection = gatt ?: return
            handler.postDelayed({
                runCatching { connection.disconnect() }
                runCatching { connection.refreshGattCacheQuietly() }
                runCatching { connection.close() }
                if (gatt === connection) {
                    gatt = null
                    manifestCharacteristic = null
                    readingCharacteristic = null
                    commandCharacteristic = null
                }
            }, 250L)
        }

        fun produce(values: Map<String, String>, ok: Boolean) {
            result = As100SensorReadMethod.result(
                makeRequest(),
                SensorReadOutcome(values, ok),
                context.request.invocationContext
            )
            readInProgress = false
            closeGattAfterRead()
        }

        fun baseValues(okStatus: String = "succeeded"): LinkedHashMap<String, String> {
            val actual = selectedNearby
            val registered = actual?.registered
            val requested = selectedDeviceId.trim()
            val actualId = actualDeviceId(actual, registered)
            val substituted =
                requested.isNotBlank() && actualId.isNotBlank() && requested != actualId

            return linkedMapOf(
                SensorReadFields.STATUS to okStatus,
                SensorReadFields.MODE to mode,
                SensorReadFields.REQUESTED_DEVICE_ID to requested,
                SensorReadFields.ACTUAL_DEVICE_ID to actualId,
                SensorReadFields.DEVICE_NAME to (registered?.name ?: actual?.name).orEmpty(),
                SensorReadFields.DEVICE_ADDRESS to (actual?.address ?: registered?.address).orEmpty(),
                SensorReadFields.REQUESTED_SENSOR_ID to sensorId.trim(),
                SensorReadFields.SENSOR_PROFILE to canonicalSensorProfile(sensorProfile),
                SensorReadFields.DEVICE_SELECTION_MODE to selectionMode(requested, actualId, actual?.registered),
                SensorReadFields.DEVICE_SUBSTITUTION to substituted.toString(),
                SensorReadFields.DEVICE_SUBSTITUTION_REASON to
                    if (substituted) "requested_device_not_found_or_operator_selected" else "",
                SensorReadFields.DURATION_SECONDS to durationSeconds.trim(),
                SensorReadFields.SAMPLE_INTERVAL_SECONDS to intervalSeconds.trim(),
                SensorReadFields.STARTED_TIME_ISO to startedTime,
                SensorReadFields.MANIFEST_JSON to manifestJson
            )
        }

        fun finishWithSamples(captureMode: String = mode) {
            val finished = Instant.now().toString()
            if (sampleValues.isEmpty()) {
                produce(
                    baseValues("failed") + mapOf(
                        SensorReadFields.ERROR to "No sensor samples were read.",
                        SensorReadFields.FINISHED_TIME_ISO to finished
                    ),
                    false
                )
                return
            }

            val traceArray = JSONArray().apply {
                sampleValues.forEachIndexed { index, sample ->
                    val reading = extractJsonObject(sample)
                    put(JSONObject().apply {
                        put("index", index + 1)
                        if (reading != null) put("reading", reading) else put("reading_text", sample)
                    })
                }
            }

            val latest = extractJsonObject(sampleValues.last()) ?: run {
                produce(
                    baseValues("failed") + mapOf(
                        SensorReadFields.ERROR to "Sensor returned unreadable JSON.",
                        SensorReadFields.READING_JSON to sampleValues.last(),
                        SensorReadFields.FINISHED_TIME_ISO to finished
                    ),
                    false
                )
                return
            }

            val summary = summariseSamples(sampleValues)
            val sensorStatus = latest.optString("status")

            if (sensorStatus.equals("error", ignoreCase = true)) {
                produce(
                    baseValues("failed") + mapOf(
                        SensorReadFields.SAMPLE_COUNT to sampleValues.size.toString(),
                        SensorReadFields.FINISHED_TIME_ISO to finished,
                        SensorReadFields.READING_JSON to sampleValues.last(),
                        SensorReadFields.ERROR to
                            latest.optString("error").ifBlank { "Sensor reported an error." },
                        SensorReadFields.ACTUAL_SENSOR_ID to latest.optString("sensor_id"),
                        SensorReadFields.SENSOR_PROFILE to canonicalSensorProfile(
                            sensorProfile.ifBlank {
                                latest.optString("sensor_profile").ifBlank { latest.optString("sensor_type") }
                            }
                        ),
                        SensorReadFields.PAYLOAD_SHA256 to latest.optString("payload_sha256")
                    ),
                    false
                )
                return
            }

            val values = baseValues("succeeded").apply {
                put(SensorReadFields.MODE, captureMode)
                put(SensorReadFields.SAMPLE_COUNT, sampleValues.size.toString())
                put(SensorReadFields.FINISHED_TIME_ISO, finished)
                put(SensorReadFields.READING_JSON, sampleValues.last())
                put(SensorReadFields.TRACE_JSON, if (captureMode == "trace") traceArray.toString() else "")
                put(SensorReadFields.SUMMARY_JSON, if (captureMode == "average") summary.toString() else "")
                put(SensorReadFields.ACTUAL_SENSOR_ID, latest.optString("sensor_id"))
                put(
                    SensorReadFields.SENSOR_PROFILE,
                    canonicalSensorProfile(
                        sensorProfile.ifBlank {
                            latest.optString("sensor_profile").ifBlank { latest.optString("sensor_type") }
                        }
                    )
                )
                put(SensorReadFields.TEMPERATURE_C, valueFor(latest, summary, "temperature_c", captureMode))
                put(SensorReadFields.RELATIVE_HUMIDITY_PCT, valueFor(latest, summary, "relative_humidity_pct", captureMode))
                put(SensorReadFields.PRESENCE, valueFor(latest, summary, "presence", captureMode))
                put(SensorReadFields.TARGET_STATE, valueFor(latest, summary, "target_state", captureMode))
                put(SensorReadFields.MOVING_DISTANCE_CM, valueFor(latest, summary, "moving_distance_cm", captureMode))
                put(SensorReadFields.MOVING_ENERGY, valueFor(latest, summary, "moving_energy", captureMode))
                put(SensorReadFields.STATIONARY_DISTANCE_CM, valueFor(latest, summary, "stationary_distance_cm", captureMode))
                put(SensorReadFields.STATIONARY_ENERGY, valueFor(latest, summary, "stationary_energy", captureMode))
                put(SensorReadFields.DETECTION_DISTANCE_CM, valueFor(latest, summary, "detection_distance_cm", captureMode))
                put(SensorReadFields.PAYLOAD_SHA256, latest.optString("payload_sha256"))
            }

            status = "Sensor read complete (${sampleValues.size} sample${if (sampleValues.size == 1) "" else "s"})."
            produce(values, true)
        }

        fun requestNextSample() {
            val characteristic = readingCharacteristic
            val connection = gatt
            if (characteristic == null || connection == null) {
                produce(
                    baseValues("failed") + mapOf(
                        SensorReadFields.ERROR to "Sensor reading endpoint is not connected."
                    ),
                    false
                )
                return
            }

            status =
                if (mode == "live") {
                    "Live sensor reading…"
                } else {
                    "Reading sample ${sampleValues.size + 1}…"
                }

            if (!connection.readCharacteristic(characteristic)) {
                produce(
                    baseValues("failed") + mapOf(
                        SensorReadFields.ERROR to "Could not queue BLE characteristic read."
                    ),
                    false
                )
            }
        }

        @Suppress("DEPRECATION")
        fun requestFreshLiveSample() {
            val connection = gatt
            val command = commandCharacteristic
            if (connection == null || command == null) {
                // Older firmware/endpoints may not expose the command characteristic.
                // Fall back to the cached reading path rather than breaking live mode.
                requestNextSample()
                return
            }

            status = "Requesting fresh sensor sample…"
            command.value = "sample".toByteArray(Charsets.UTF_8)
            val queued = connection.writeCharacteristic(command)
            if (!queued) {
                status = "Could not queue sample command; reading latest cached value."
                handler.postDelayed({ requestNextSample() }, 80L)
            }
        }

        fun liveRefreshMillis(): Long {
            val manifestInterval = runCatching {
                JSONObject(manifestJson).optLong("sample_interval_ms", 0L)
            }.getOrDefault(0L)

            if (manifestInterval > 0L) {
                return manifestInterval.coerceIn(minimumLiveRefreshMs, maximumLiveRefreshMs)
            }

            val profileInterval = runCatching {
                SensorProvisioningProfiles.byId(sensorProfile).defaultSampleIntervalMs.toLong()
            }.getOrDefault(1_000L)

            return profileInterval.coerceIn(minimumLiveRefreshMs, maximumLiveRefreshMs)
        }

        fun scheduleLivePoll(delayMs: Long = liveRefreshMillis()) {
            cancelLivePoll()
            val runnable = Runnable {
                livePollRunnable = null
                if (mode == "live" && readInProgress && !liveFrozen) {
                    requestFreshLiveSample()
                }
            }
            livePollRunnable = runnable
            handler.postDelayed(runnable, delayMs)
        }

        fun appendLiveSample(sample: String, shortened: Boolean = false) {
            sampleValues.add(sample)
            while (sampleValues.size > liveWindowMaxSamples) {
                sampleValues.removeAt(0)
            }

            status =
                if (shortened) {
                    "Live payload was shortened; recoverable fields are updating."
                } else {
                    "Live sensor reading · ${sampleValues.size} reading${if (sampleValues.size == 1) "" else "s"} in rolling window."
                }

            if (!liveFrozen) {
                scheduleLivePoll()
            }
        }

        fun beginSampling() {
            val duration = durationSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 30
            val interval = intervalSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 5
            durationSeconds = duration.toString()
            intervalSeconds = interval.toString()

            cancelLivePoll()
            sampleValues.clear()
            liveFrozen = false
            startedTime = Instant.now().toString()

            samplesRemaining = when (mode) {
                "live" -> Int.MAX_VALUE
                "trace", "average" ->
                    ((duration.toDouble() / interval.toDouble()).roundToInt()).coerceAtLeast(1)
                else -> 1
            }

            readInProgress = true
            if (mode == "live") requestFreshLiveSample() else requestNextSample()
        }

        val gattCallback = remember {
            object : BluetoothGattCallback() {
                override fun onConnectionStateChange(
                    g: BluetoothGatt,
                    statusCode: Int,
                    newState: Int
                ) {
                    handler.post {
                        status =
                            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                                val mtuRequested = runCatching { g.requestMtu(517) }.getOrDefault(false)
                                if (!mtuRequested) {
                                    handler.postDelayed({ g.discoverServices() }, 250L)
                                }
                                "Connected; preparing BLE link for sensor payloads…"
                            } else {
                                if (readInProgress) {
                                    produce(
                                        baseValues("failed") + mapOf(
                                            SensorReadFields.ERROR to
                                                "Disconnected before read completed (status=$statusCode)."
                                        ),
                                        false
                                    )
                                }
                                "Disconnected (status=$statusCode)."
                            }
                    }
                }

                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, statusCode: Int) {
                    handler.post {
                        status =
                            if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                                "BLE link ready at MTU $mtu; discovering sensor services…"
                            } else {
                                "BLE MTU request returned $statusCode; discovering sensor services…"
                            }
                        handler.postDelayed({ g.discoverServices() }, 200L)
                    }
                }

                override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                    handler.post {
                        val service = g.getService(sensorServiceUuid)
                        manifestCharacteristic = service?.getCharacteristic(manifestUuid)
                        readingCharacteristic = service?.getCharacteristic(readingUuid)
                        commandCharacteristic = service?.getCharacteristic(commandUuid)

                        if (
                            statusCode != BluetoothGatt.GATT_SUCCESS ||
                            service == null ||
                            manifestCharacteristic == null ||
                            readingCharacteristic == null
                        ) {
                            produce(
                                baseValues("failed") + mapOf(
                                    SensorReadFields.ERROR to
                                        "Connected device does not expose the MethodMesh sensor contract."
                                ),
                                false
                            )
                        } else {
                            status = "Sensor endpoint ready; reading manifest…"
                            g.readCharacteristic(manifestCharacteristic)
                        }
                    }
                }

                @Suppress("DEPRECATION")
                override fun onCharacteristicRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    statusCode: Int
                ) {
                    handleRead(g, characteristic, characteristic.value, statusCode)
                }

                override fun onCharacteristicRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    statusCode: Int
                ) {
                    handleRead(g, characteristic, value, statusCode)
                }

                override fun onCharacteristicWrite(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    statusCode: Int
                ) {
                    if (characteristic.uuid != commandUuid) return
                    handler.post {
                        if (mode != "live" || !readInProgress || liveFrozen) return@post
                        if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                            // The ESP32 command handler calls sample() before acknowledging
                            // the write, so the reading characteristic should now be fresh.
                            handler.postDelayed({ requestNextSample() }, 80L)
                        } else {
                            status = "Sample command failed ($statusCode); reading latest cached value."
                            handler.postDelayed({ requestNextSample() }, 80L)
                        }
                    }
                }

                private fun handleRead(
                    g: BluetoothGatt,
                    characteristic: BluetoothGattCharacteristic,
                    value: ByteArray,
                    statusCode: Int
                ) {
                    handler.post {
                        if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                            produce(
                                baseValues("failed") + mapOf(
                                    SensorReadFields.ERROR to
                                        "BLE read failed with GATT status $statusCode."
                                ),
                                false
                            )
                            return@post
                        }

                        val text = value.toString(Charsets.UTF_8)
                        if (text.isBlank()) {
                            produce(
                                baseValues("failed") + mapOf(
                                    SensorReadFields.ERROR to "BLE endpoint returned an empty payload."
                                ),
                                false
                            )
                            return@post
                        }

                        when (characteristic.uuid) {
                            manifestUuid -> {
                                manifestJson = text
                                if (sensorProfile.isBlank()) {
                                    sensorProfile = canonicalSensorProfile(
                                        SensorProvisioningProfiles.manifestProfileId(text)
                                    )
                                }
                                status = "Manifest read. Starting ${modeLabel(mode).lowercase()}."
                                beginSampling()
                            }

                            readingUuid -> {
                                val filtered = filterSample(text, sensorId, sensorProfile)

                                if (extractJsonObject(filtered) == null) {
                                    val firmwareVersion = runCatching {
                                        JSONObject(manifestJson).optString("firmware_version")
                                    }.getOrNull().orEmpty()

                                    val likelyTruncated =
                                        !filtered.trim().endsWith("}") && filtered.contains("{")

                                    val error = when {
                                        likelyTruncated && firmwareVersion == "methodmesh-sensor-0.1.2" ->
                                            "Sensor reading was truncated by older firmware $firmwareVersion. Reflash the sensor image so the board reports methodmesh-sensor-0.1.3 or newer."
                                        likelyTruncated ->
                                            "Sensor reading was truncated before the closing brace. Try again; if it persists, reflash the latest sensor image."
                                        else ->
                                            "Sensor reading was not valid JSON."
                                    }

                                    val recovered = recoverPartialSensorReading(filtered)
                                    if (recovered != null) {
                                        if (mode == "live") {
                                            appendLiveSample(recovered.toString(), shortened = true)
                                            return@post
                                        }

                                        sampleValues.add(recovered.toString())
                                        samplesRemaining -= 1
                                        status =
                                            "Sensor read returned a shortened payload; recovered values were captured."

                                        if (samplesRemaining <= 0 || mode == "single") {
                                            finishWithSamples()
                                        } else {
                                            handler.postDelayed(
                                                { requestNextSample() },
                                                (intervalSeconds.toLongOrNull() ?: 5L)
                                                    .coerceAtLeast(1L) * 1000L
                                            )
                                        }
                                        return@post
                                    }

                                    produce(
                                        baseValues("failed") + mapOf(
                                            SensorReadFields.ERROR to error,
                                            SensorReadFields.READING_JSON to filtered
                                        ),
                                        false
                                    )
                                    return@post
                                }

                                if (mode == "live") {
                                    appendLiveSample(filtered)
                                    return@post
                                }

                                sampleValues.add(filtered)
                                samplesRemaining -= 1

                                if (samplesRemaining <= 0 || mode == "single") {
                                    finishWithSamples()
                                } else {
                                    handler.postDelayed(
                                        { requestNextSample() },
                                        (intervalSeconds.toLongOrNull() ?: 5L)
                                            .coerceAtLeast(1L) * 1000L
                                    )
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
                    val advertisedName = scanResult.scanRecord?.deviceName.orEmpty()
                    val cachedName = runCatching { device.name }.getOrNull().orEmpty()
                    val services = scanResult.scanRecord?.serviceUuids?.map { it.uuid } ?: emptyList()
                    val registered = registryDevices.firstOrNull {
                        it.address.equals(address, true)
                    }

                    val looksLikeSensor =
                        services.contains(sensorServiceUuid) ||
                            registered != null ||
                            advertisedName.startsWith("MethodMesh", true) ||
                            cachedName.startsWith("MethodMesh", true)

                    if (!looksLikeSensor) return

                    val displayName =
                        advertisedName.ifBlank {
                            registered?.name ?: "MethodMesh sensor ${address.takeLast(5)}"
                        }

                    val item = NearbySensor(
                        device = device,
                        name = displayName,
                        address = address,
                        rssi = scanResult.rssi,
                        registered = registered
                    )

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
            if (!permissionsGranted()) {
                permissionLauncher.launch(permissions())
                return
            }

            stopScan()
            cancelLivePoll()
            selectedNearby = item
            result = null
            manifestJson = ""
            sampleValues.clear()
            liveFrozen = false
            readInProgress = true
            manifestCharacteristic = null
            readingCharacteristic = null
            commandCharacteristic = null
            status = "Connecting to ${item.name}…"

            runCatching { gatt?.refreshGattCacheQuietly() }
            runCatching { gatt?.close() }

            gatt =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    item.device.connectGatt(
                        androidContext,
                        false,
                        gattCallback,
                        BluetoothDevice.TRANSPORT_LE
                    )
                } else {
                    item.device.connectGatt(androidContext, false, gattCallback)
                }
        }

        fun startScan(autoConnect: Boolean) {
            if (adapter == null || !adapter.isEnabled) {
                status = "Bluetooth is not enabled."
                return
            }

            if (!permissionsGranted()) {
                permissionLauncher.launch(permissions())
                return
            }

            cancelLivePoll()
            registryDevices = sensorRegistryDevices(androidContext)
            nearby.clear()
            selectedNearby = null
            result = null
            status = "Scanning for registered MethodMesh sensors…"
            scanning = true

            runCatching { adapter.bluetoothLeScanner?.startScan(callback) }
                .onFailure { error ->
                    scanning = false
                    produce(
                        baseValues("failed") + mapOf(
                            SensorReadFields.ERROR to "Scan failed: ${error.message.orEmpty()}"
                        ),
                        false
                    )
                }

            handler.postDelayed({
                stopScan()

                val requested = selectedDeviceId.trim()
                val requestedRegistry = registryDevices.firstOrNull {
                    it.id == requested ||
                        it.name == requested ||
                        it.address.equals(requested, true)
                }

                val requestedNearby = nearby.firstOrNull {
                    it.registered?.id == requestedRegistry?.id ||
                        it.address.equals(requestedRegistry?.address.orEmpty(), true)
                }

                when {
                    mode == "discover" ->
                        discoverOnly()

                    autoConnect && requested.isNotBlank() && requestedNearby != null ->
                        connectAndRead(requestedNearby)

                    autoConnect &&
                        requested.isNotBlank() &&
                        matchPolicy.equals("strict", true) ->
                        produce(
                            baseValues("failed") + mapOf(
                                SensorReadFields.ERROR to "Requested device was not found nearby."
                            ),
                            false
                        )

                    autoConnect && nearby.size == 1 ->
                        connectAndRead(nearby.first())

                    autoConnect && nearby.isNotEmpty() ->
                        status = "Requested device not found. Choose a nearby sensor to continue."

                    else ->
                        status =
                            if (nearby.isEmpty()) {
                                "No MethodMesh sensors found nearby."
                            } else {
                                "Scan complete. Choose a sensor."
                            }
                }
            }, 8_000L)
        }

        fun readNow() {
            result = null
            val requested = selectedDeviceId.trim()
            val registry = registryDevices.firstOrNull {
                it.id == requested ||
                    it.name == requested ||
                    it.address.equals(requested, true)
            }

            val direct =
                nearby.firstOrNull {
                    it.address.equals(registry?.address.orEmpty(), true) ||
                        it.registered?.id == registry?.id
                } ?: selectedNearby

            when {
                direct != null -> connectAndRead(direct)
                else -> startScan(
                    autoConnect =
                        focusedLaunch ||
                            context.startsImmediately ||
                            requested.isNotBlank()
                )
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                cancelLivePoll()
                stopScan()
                runCatching { gatt?.refreshGattCacheQuietly() }
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
            resultPreview = result
                ?.let { OutputFormatter.fields(it, includeProvenance = false) }
                .orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                readNow()
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                if (focusedLaunch) {
                    "Reading sensor using the configured request."
                } else {
                    "Search for nearby MethodMesh sensors, then read the selected sensor. The sensor decides which data fields it returns."
                },
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
                Text("Read mode", fontWeight = FontWeight.SemiBold)

                OutlinedButton(
                    onClick = { modeMenuOpen = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        modeLabel(mode),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    Text("▼")
                }

                DropdownMenu(
                    expanded = modeMenuOpen,
                    onDismissRequest = { modeMenuOpen = false }
                ) {
                    listOf("single", "live", "trace", "average", "discover")
                        .forEach { option ->
                            DropdownMenuItem(
                                text = { Text(modeLabel(option)) },
                                onClick = {
                                    if (mode == "live" && option != "live") {
                                        cancelLivePoll()
                                        liveFrozen = false
                                    }
                                    mode = option
                                    modeMenuOpen = false
                                }
                            )
                        }
                }

                if (mode == "trace" || mode == "average") {
                    OutlinedTextField(
                        value = durationSeconds,
                        onValueChange = { durationSeconds = it.filter(Char::isDigit) },
                        label = { Text("Duration (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = intervalSeconds,
                        onValueChange = { intervalSeconds = it.filter(Char::isDigit) },
                        label = { Text("Sample interval (seconds)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (mode == "live") {
                    Text(
                        "Request a fresh sensor sample on each cycle, update the values live, then freeze the rolling window when you want to capture it.",
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(10.dp))

            Button(
                onClick = { startScan(autoConnect = false) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (scanning) "Searching…" else "Search for sensors")
            }

            if (selectedNearby != null || selectedDeviceId.isNotBlank()) {
                OutlinedButton(
                    onClick = { readNow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !(mode == "live" && readInProgress && !liveFrozen)
                ) {
                    Text(
                        when {
                            mode == "live" && readInProgress && !liveFrozen -> "Live reading…"
                            mode == "live" && liveFrozen -> "Live reading frozen"
                            mode == "live" -> "Start live reading"
                            readInProgress -> "Reading…"
                            result == null -> "Read selected sensor"
                            else -> "Read again"
                        }
                    )
                }
            }

            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (mode == "live" && sampleValues.isNotEmpty() && result == null) {
                LiveSensorPanel(
                    samples = sampleValues.toList(),
                    frozen = liveFrozen,
                    refreshMs = liveRefreshMillis(),
                    onFreeze = {
                        cancelLivePoll()
                        liveFrozen = true
                        status =
                            "Live reading frozen at ${sampleValues.size} reading${if (sampleValues.size == 1) "" else "s"}."
                    },
                    onResume = {
                        liveFrozen = false
                        status = "Live reading resumed."
                        if (gatt != null && readingCharacteristic != null) {
                            cancelLivePoll()
                            requestFreshLiveSample()
                        } else {
                            readNow()
                        }
                    },
                    onUseCurrent = { finishWithSamples("single") },
                    onUseSummary = { finishWithSamples("average") },
                    onUseTrace = { finishWithSamples("trace") }
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(nearby, key = { it.address }) { item ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(10.dp)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${item.address} · RSSI ${item.rssi}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Registry: ${item.registered?.id ?: "not saved"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Button(
                                onClick = { connectAndRead(item) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (mode == "live") "Live read this sensor" else "Read this sensor")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            IntentExampleDropdown(
                capabilityId = As100SensorReadMethod.ID,
                examples = listOf(
                    IntentExample(
                        "Single sensor read",
                        "Read a registered sensor once.",
                        "com.example.methodmesh.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_read_mode='single',return_mode='flat')"
                    ),
                    IntentExample(
                        "Average over time",
                        "Average AHT20 fields over 60 seconds.",
                        "com.example.methodmesh.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_sensor_profile='aht20',input_sensor_read_mode='average',input_duration_seconds='60',input_sample_interval_seconds='5',return_mode='flat')"
                    ),
                    IntentExample(
                        "Strict device match",
                        "Fail if the specified sensor is not nearby.",
                        "com.example.methodmesh.EXECUTE_METHOD(method_id='sensor.read',input_device_id='clinic_room_01_sensor',input_device_match_policy='strict',return_mode='flat')"
                    )
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
            if (sensorId.isNotBlank()) {
                Text("Sensor ID: $sensorId", style = MaterialTheme.typography.bodyMedium)
            }
            if (sensorProfile.isNotBlank()) {
                Text("Sensor profile: $sensorProfile", style = MaterialTheme.typography.bodyMedium)
            }
            if (mode == "trace" || mode == "average") {
                Text(
                    "Sampling: ${durationSeconds.ifBlank { "30" }}s every ${intervalSeconds.ifBlank { "5" }}s",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                "Matching: ${matchPolicyLabel(matchPolicy)}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun sensorRegistryDevices(context: android.content.Context): List<RegisteredDevice> =
    DeviceRegistry.all(context)
        .filter {
            it.transport == DeviceTransport.BLE &&
                !it.paused &&
                it.enabled &&
                (
                    it.profile.contains("methodmesh_ble_sensor", true) ||
                        it.id.startsWith("sensor:") ||
                        it.name.contains("sensor", true)
                    )
        }
        .sortedBy { it.name.lowercase() }

private fun Map<String, String>.firstPresent(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key ->
        get(key)?.trim()?.takeIf(String::isNotBlank)
    }.orEmpty()

private fun BluetoothGatt.refreshGattCacheQuietly() {
    runCatching {
        val method = javaClass.getMethod("refresh")
        method.invoke(this)
    }
}

private fun normalizeMode(value: String): String =
    when (value.trim().lowercase()) {
        "live", "stream", "streaming" -> "live"
        "trace", "timeseries", "time_series" -> "trace"
        "average", "avg", "mean" -> "average"
        "discover", "scan" -> "discover"
        else -> "single"
    }

private fun modeLabel(mode: String): String =
    when (mode) {
        "live" -> "Live / freeze"
        "trace" -> "Trace over time"
        "average" -> "Average over time"
        "discover" -> "Discover nearby sensors"
        else -> "Single point read"
    }

private fun matchPolicyLabel(policy: String): String =
    when (policy.trim().lowercase()) {
        "strict", "false" -> "Strict: fail if requested device is absent"
        "any_nearby" -> "Any nearby MethodMesh sensor"
        else -> "Fallback: choose nearby if requested device is absent"
    }

private fun deviceLabel(
    deviceId: String,
    devices: List<RegisteredDevice>
): String {
    if (deviceId.isBlank()) return "Choose nearby sensor"
    val device = devices.firstOrNull {
        it.id == deviceId ||
            it.name == deviceId ||
            it.address.equals(deviceId, true)
    }
    return device?.let { "${it.name} (${it.id})" } ?: deviceId
}

private fun actualDeviceId(
    item: NearbySensor?,
    registered: RegisteredDevice?
): String {
    val manifestId = runCatching {
        JSONObject(registered?.profile.orEmpty()).optString("device_id")
    }.getOrNull().orEmpty()

    return manifestId.ifBlank {
        registered?.id.orEmpty().ifBlank { item?.address.orEmpty() }
    }
}

private fun selectionMode(
    requested: String,
    actual: String,
    registered: RegisteredDevice?
): String =
    when {
        requested.isBlank() -> "operator_selected"
        requested == actual -> "requested_device"
        registered != null -> "fallback_registered_device"
        else -> "fallback_operator_selected"
    }

private fun canonicalSensorProfile(value: String): String {
    val compact =
        value.trim()
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "")

    return when {
        compact.contains("ld2410") -> "ld2410c"
        compact.contains("aht20") -> "aht20"
        compact.isBlank() -> ""
        else -> compact
    }
}

private fun filterSample(
    text: String,
    requestedSensorId: String,
    requestedProfile: String
): String {
    val root = extractJsonObject(text) ?: return text
    val readings = root.optJSONArray("readings") ?: root.optJSONArray("sensors")
    if (readings == null) return root.toString()

    for (i in 0 until readings.length()) {
        val item = readings.optJSONObject(i) ?: continue
        val idOk =
            requestedSensorId.isBlank() ||
                item.optString("sensor_id").equals(requestedSensorId, true)

        val profile =
            canonicalSensorProfile(
                item.optString("sensor_profile")
                    .ifBlank { item.optString("sensor_type") }
            )

        val profileOk =
            requestedProfile.isBlank() ||
                profile == canonicalSensorProfile(requestedProfile)

        if (idOk && profileOk) return item.toString()
    }

    return root.toString()
}

private fun recoverPartialSensorReading(text: String): JSONObject? {
    if (!text.contains("{") || text.trim().endsWith("}")) return null

    val recovered = JSONObject()
    val keys = listOf(
        "methodmesh_sensor_reading_version",
        "device_id",
        "device_name",
        "firmware_version",
        "sensor_profile",
        "sensor_type",
        "sensor_id",
        "status",
        "sample_time_ms",
        "payload_sha256",
        "temperature_c",
        "relative_humidity_pct",
        "presence",
        "target_state",
        "moving_distance_cm",
        "moving_energy",
        "stationary_distance_cm",
        "stationary_energy",
        "detection_distance_cm",
        "radar_frame_sequence",
        "radar_frame_length",
        "radar_decode_offset",
        "radar_prefix_hex"
    )

    keys.forEach { key ->
        val quoted =
            Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"([^\"]*)\"")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        if (quoted != null) {
            recovered.put(key, quoted)
            return@forEach
        }

        val bool =
            Regex("\"${Regex.escape(key)}\"\\s*:\\s*(true|false)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        if (bool != null) {
            recovered.put(key, bool.toBoolean())
            return@forEach
        }

        val number =
            Regex("\"${Regex.escape(key)}\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
                .find(text)
                ?.groupValues
                ?.getOrNull(1)

        if (number != null) {
            val numericValue = number.toDoubleOrNull()
            recovered.put(
                key,
                if (numericValue != null && numericValue % 1.0 == 0.0) {
                    numericValue.toLong()
                } else {
                    numericValue ?: number
                }
            )
        }
    }

    val hasIdentity =
        recovered.has("sensor_profile") ||
            recovered.has("sensor_type") ||
            recovered.has("sensor_id")

    val hasData =
        listOf(
            "temperature_c",
            "relative_humidity_pct",
            "presence",
            "target_state",
            "moving_distance_cm",
            "moving_energy",
            "stationary_distance_cm",
            "stationary_energy",
            "detection_distance_cm"
        ).any { recovered.has(it) }

    return if (hasIdentity && hasData) recovered else null
}

private fun summariseSamples(samples: List<String>): JSONObject {
    val numeric = linkedMapOf<String, MutableList<Double>>()

    samples.forEach { sample ->
        val json = extractJsonObject(sample) ?: return@forEach

        json.keys().forEach { key ->
            val value = json.opt(key)
            val number = when (value) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull()
                else -> null
            }

            if (number != null) {
                numeric.getOrPut(key) { mutableListOf() }.add(number)
            }
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

private fun valueFor(
    latest: JSONObject,
    summary: JSONObject,
    key: String,
    mode: String
): String =
    if (mode == "average") summary.optString("${key}_mean")
    else latest.optString(key)
