package com.example.methodmesh.modules.sensorfirmwareinstaller

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.ceil

private const val ACTION_USB_PERMISSION = "com.example.methodmesh.USB_SENSOR_FIRMWARE_PERMISSION"
private const val MAIN_PY_ASSET = "firmware/esp32c3_aht20_ble/main.py"
private const val SENSOR_CONFIG_TARGET = "methodmesh_sensor_config.json"
private val SENSOR_DRIVER_ASSETS = listOf(
    "firmware/esp32c3_aht20_ble/sensor_drivers/__init__.py" to "sensor_drivers/__init__.py",
    "firmware/esp32c3_aht20_ble/sensor_drivers/aht20.py" to "sensor_drivers/aht20.py",
    "firmware/esp32c3_aht20_ble/sensor_drivers/ld2410c.py" to "sensor_drivers/ld2410c.py"
)
private const val MICROPYTHON_BIN_ASSET = "firmware/esp32c3_aht20_ble/ESP32_GENERIC_C3-20260406-v1.28.0.bin"
private const val ESP_FLASH_ADDRESS = 0
private const val ESP_FLASH_BLOCK = 0x1000
private const val ESP_STATUS_BYTES = 2
private const val ESP_BOOT_NUKE_BYTES = 2 * 1024 * 1024
private val TERMINAL_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

private data class SensorFirmwareProfile(
    val id: String,
    val label: String,
    val description: String,
    val sampleIntervalMs: Int,
    val fullImageAsset: String
)

private data class PreparedFlashBlock(
    val sequence: Int,
    val bytes: ByteArray,
    val checksum: Int
)

private data class PreparedFlashImage(
    val sourceBytes: Int,
    val eraseSize: Int,
    val blocks: List<PreparedFlashBlock>
) {
    companion object {
        fun from(image: ByteArray): PreparedFlashImage {
            val blockCount = ceil(image.size / ESP_FLASH_BLOCK.toDouble()).toInt()
            val blocks = (0 until blockCount).map { sequence ->
                val start = sequence * ESP_FLASH_BLOCK
                val block = ByteArray(ESP_FLASH_BLOCK) { 0xFF.toByte() }
                val length = minOf(ESP_FLASH_BLOCK, image.size - start)
                image.copyInto(block, 0, start, start + length)
                PreparedFlashBlock(sequence, block, espChecksum(block))
            }
            return PreparedFlashImage(
                sourceBytes = image.size,
                eraseSize = blockCount * ESP_FLASH_BLOCK,
                blocks = blocks
            )
        }
    }
}

private val SENSOR_FIRMWARE_PROFILES = listOf(
    SensorFirmwareProfile(
        id = "aht20",
        label = "AHT20 temperature/humidity",
        description = "I2C AHT20 on GPIO 8 SDA / GPIO 9 SCL.",
        sampleIntervalMs = 5000,
        fullImageAsset = "firmware/esp32c3_images/methodmesh_esp32c3_aht20.bin"
    ),
    SensorFirmwareProfile(
        id = "ld2410c",
        label = "LD2410C mmWave presence",
        description = "UART LD2410C on TX GPIO 21 / RX GPIO 20.",
        sampleIntervalMs = 1000,
        fullImageAsset = "firmware/esp32c3_images/methodmesh_esp32c3_ld2410c.bin"
    )
)

private fun sensorFirmwareProfileById(id: String): SensorFirmwareProfile =
    SENSOR_FIRMWARE_PROFILES.firstOrNull { it.id == id } ?: SENSOR_FIRMWARE_PROFILES.first()

private fun Map<String, String>.firstPresent(vararg keys: String): String =
    keys.firstNotNullOfOrNull { key -> get(key)?.trim()?.takeIf(String::isNotBlank) }.orEmpty()

private fun esp32FirmwareExamples(capabilityId: String): List<IntentExample> = when (capabilityId) {
    As100Esp32SensorProfileInstallMethod.id -> listOf(
        IntentExample("Install AHT20 image", "Erase and install the complete AHT20 temperature/humidity ESP32-C3 image.", "com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='aht20',return_mode='flat')"),
        IntentExample("Install LD2410C image", "Erase and install the complete LD2410C radar ESP32-C3 image.", "com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',input_sensor_profile='ld2410c',return_mode='flat')")
    )
    else -> listOf(
        IntentExample("Install ESP32 sensor image", "Open the ESP32 sensor image installer.", "com.example.methodmesh.EXECUTE_METHOD(method_id='esp32.sensor_profile_install',return_mode='flat')")
    )
}

private fun esp32FirmwareMethodFor(capabilityId: String) = when (capabilityId) {
    As100Esp32BoardWipeMethod.id -> As100Esp32BoardWipeMethod
    As100Esp32RuntimeInstallMethod.id -> As100Esp32RuntimeInstallMethod
    As100Esp32SensorProfileInstallMethod.id -> As100Esp32SensorProfileInstallMethod
    else -> As100Esp32SensorProfileInstallMethod
}

private class SensorFirmwareInstallerCapabilityScreenSpec(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val initialInstallStage: String = "bootloader",
    private val initialFlashEraseCompleted: Boolean = false
) : CapabilityScreenSpec {

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val usbManager = androidContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val scope = remember { CoroutineScope(Dispatchers.Main) }
        val supplied = remember(context.request.settings, context.action.settings, context.request.invocationContext) {
            context.request.invocationContext.asMap(context.action.canonicalId) + context.request.settings + context.action.settings
        }
        var devices by remember { mutableStateOf(usbDevices(usbManager)) }
        var selected by remember { mutableStateOf<UsbDevice?>(null) }
        var status by rememberSaveable { mutableStateOf("Connect an ESP32-C3 by USB/OTG, then refresh devices.") }
        var log by rememberSaveable { mutableStateOf("") }
        var installing by rememberSaveable { mutableStateOf(false) }
        var destructiveConfirmed by rememberSaveable { mutableStateOf(false) }
        var pendingAction by rememberSaveable { mutableStateOf("main_py") }
        var permissionCheckTick by rememberSaveable { mutableStateOf(0) }
        var permissionCheckDevice by remember { mutableStateOf<UsbDevice?>(null) }
        var selectedSensorProfileId by rememberSaveable {
            mutableStateOf(
                sensorFirmwareProfileById(
                    supplied.firstPresent("sensor_profile", "input_sensor_profile", "sensor_type", "input_sensor_type")
                ).id
            )
        }
        var installStage by rememberSaveable { mutableStateOf(initialInstallStage) }
        var installerProgress by rememberSaveable { mutableStateOf<Float?>(null) }
        var installerProgressNote by rememberSaveable { mutableStateOf("") }
        var bootloaderCheckSummary by rememberSaveable { mutableStateOf("") }
        var bootloaderCheckDeviceName by rememberSaveable { mutableStateOf("") }
        var microPythonReadyDeviceName by rememberSaveable { mutableStateOf("") }
        var flashEraseCompleted by rememberSaveable { mutableStateOf(initialFlashEraseCompleted) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val logScrollState = rememberScrollState()
        val selectedSensorProfile = sensorFirmwareProfileById(selectedSensorProfileId)
        val mainPy = remember { androidContext.assets.open(MAIN_PY_ASSET).bufferedReader().use { it.readText() } }
        val driverFiles = remember {
            SENSOR_DRIVER_ASSETS.map { (assetPath, targetPath) ->
                targetPath to androidContext.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
        }
        val microPythonBin = remember { androidContext.assets.open(MICROPYTHON_BIN_ASSET).use { it.readBytes() } }
        val microPythonFlashImage = remember(microPythonBin) { PreparedFlashImage.from(microPythonBin) }
        val bootNukeImage = remember { PreparedFlashImage.from(ByteArray(ESP_BOOT_NUKE_BYTES) { 0xFF.toByte() }) }
        val selectedFullImageBytes = remember(selectedSensorProfile.fullImageAsset) {
            runCatching { androidContext.assets.open(selectedSensorProfile.fullImageAsset).use { it.readBytes() } }.getOrNull()
        }
        val selectedFullFlashImage = remember(selectedFullImageBytes) {
            selectedFullImageBytes?.let { PreparedFlashImage.from(it) }
        }

        fun record(outcome: SensorFirmwareInstallOutcome) {
            val method = esp32FirmwareMethodFor(capabilityId)
            val request = method.request(method.id, emptyMap(), emptyList(), emptyList())
            result = sensorFirmwareInstallResult(method, request, outcome, context.request.invocationContext)
        }

        fun append(message: String) {
            val stamp = LocalTime.now().format(TERMINAL_TIME_FORMAT)
            log = (log + "\n[$stamp] $message").trim().lines().takeLast(220).joinToString("\n")
        }

        fun setInstallerProgress(message: String) {
            status = message
            append(message)
            when {
                message.startsWith("Synchronising") -> {
                    installerProgress = 0.05f
                    installerProgressNote = "Checking bootloader connection."
                }
                message.startsWith("Nuking ESP32 boot/app flash") -> {
                    installerProgress = 0.08f
                    installerProgressNote = "Erasing the boot/app/settings region that lets old firmware start."
                    scope.launch {
                        val totalSeconds = 180
                        repeat(totalSeconds) { second ->
                            delay(1000L)
                            if (!installing || !status.startsWith("Nuking ESP32 boot/app flash")) return@launch
                            installerProgress = (0.08f + (second + 1) / totalSeconds.toFloat() * 0.72f).coerceAtMost(0.80f)
                            installerProgressNote = "Nuking old firmware… about ${second + 1}s elapsed. Keep the phone awake and cable still."
                        }
                    }
                }
                message.startsWith("Using bundled MicroPython") -> {
                    installerProgress = 0.81f
                    installerProgressNote = "Using the pre-bundled board image; sensor choice is applied later in MethodMesh config."
                }
                message.startsWith("Using selected MethodMesh") -> {
                    installerProgress = 0.20f
                    installerProgressNote = "Using the selected prebuilt sensor image; no REPL upload is needed."
                }
                message.startsWith("Requesting flash write") -> {
                    installerProgress = 0.82f
                    installerProgressNote = "Asking the ESP32 bootloader to accept the bundled image blocks."
                }
                message.startsWith("Flash write accepted") -> {
                    installerProgress = 0.84f
                    installerProgressNote = message
                }
                message.startsWith("Writing MicroPython block") -> {
                    val parts = Regex("""block (\d+)/(\d+)""").find(message)?.groupValues
                    val current = parts?.getOrNull(1)?.toFloatOrNull()
                    val total = parts?.getOrNull(2)?.toFloatOrNull()
                    installerProgress = if (current != null && total != null && total > 0f) {
                        (0.84f + (current / total) * 0.14f).coerceAtMost(0.98f)
                    } else 0.9f
                    installerProgressNote = message
                }
                message.startsWith("Writing MethodMesh") -> {
                    val parts = Regex("""block (\d+)/(\d+)""").find(message)?.groupValues
                    val current = parts?.getOrNull(1)?.toFloatOrNull()
                    val total = parts?.getOrNull(2)?.toFloatOrNull()
                    installerProgress = if (current != null && total != null && total > 0f) {
                        (0.20f + (current / total) * 0.78f).coerceAtMost(0.98f)
                    } else 0.5f
                    installerProgressNote = message
                }
                message.startsWith("Finishing") -> {
                    installerProgress = 0.99f
                    installerProgressNote = "Finishing and rebooting."
                }
            }
        }

        LaunchedEffect(log) {
            if (log.isNotBlank()) logScrollState.animateScrollTo(logScrollState.maxValue)
        }

        fun selectedSensorConfig(): String = JSONObject().apply {
            put("device_id", "methodmesh_sensor")
            put("device_name", "MethodMesh-Sensor")
            put("sample_interval_ms", selectedSensorProfile.sampleIntervalMs)
            put("sensor_profile", selectedSensorProfile.id)
            put("provisioned", false)
        }.toString()

        fun requestPermission(device: UsbDevice, action: String) {
            pendingAction = action
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val intent = Intent(ACTION_USB_PERMISSION).setPackage(androidContext.packageName)
            val pendingIntent = PendingIntent.getBroadcast(androidContext, 0, intent, flags)
            usbManager.requestPermission(device, pendingIntent)
            permissionCheckDevice = device
            permissionCheckTick += 1
            status = "Waiting for USB permission…"
        }

        fun clearBootloaderCheck() {
            bootloaderCheckSummary = ""
            bootloaderCheckDeviceName = ""
        }

        fun clearMicroPythonCheck() {
            microPythonReadyDeviceName = ""
        }

        fun checkBootloader(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed before checking the ESP32-C3 bootloader."
                requestPermission(device, "bootloader_check")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                clearBootloaderCheck()
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                return
            }
            installing = true
            installerProgress = 0.03f
            installerProgressNote = "Checking that the selected device is really in ESP32 ROM bootloader mode."
            status = "Checking ESP32-C3 bootloader…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nThis is a safe preflight check. It does not erase or write flash."
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { probeEsp32Bootloader(usbManager, device) { progress -> scope.launch { setInstallerProgress(progress) } } }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}") }
                }
                installing = false
                if (outcome.success) {
                    bootloaderCheckDeviceName = device.deviceName
                    bootloaderCheckSummary = outcome.message
                    installerProgress = 1f
                    installerProgressNote = "Bootloader preflight passed."
                    if (flashEraseCompleted && installStage == "bootloader") {
                        installStage = "write_micropython"
                        status = "Bootloader detected on erased board. Ready to write bundled MicroPython."
                    } else {
                        status = "Bootloader detected. You can now clean replace with MicroPython."
                    }
                } else {
                    clearBootloaderCheck()
                    installerProgress = null
                    installerProgressNote = ""
                    status = "Bootloader check failed: ${outcome.message}"
                }
                append(if (outcome.success) "BOOTLOADER OK: ${outcome.message}" else "BOOTLOADER CHECK FAILED: ${outcome.message}")
            }
        }

        fun detectBoardState(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed before detecting board state."
                requestPermission(device, "detect_state")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                clearBootloaderCheck()
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                return
            }
            installing = true
            installerProgress = 0.02f
            installerProgressNote = "Trying bootloader first, then MicroPython REPL if needed."
            status = "Detecting board state…"
            log = "Detecting ESP32 state on ${device.deviceName}\nThis safe check does not erase or write flash."
            scope.launch {
                val bootloader = withContext(Dispatchers.IO) {
                    runCatching { probeEsp32Bootloader(usbManager, device) { progress -> scope.launch { setInstallerProgress(progress) } } }
                }
                if (bootloader.isSuccess && bootloader.getOrThrow().success) {
                    val outcome = bootloader.getOrThrow()
                    bootloaderCheckDeviceName = device.deviceName
                    bootloaderCheckSummary = outcome.message
                    installing = false
                    installerProgress = 1f
                    installerProgressNote = "Board is in ESP32 ROM bootloader mode."
                    if (flashEraseCompleted) {
                        installStage = "write_micropython"
                        status = "Detected erased/bootloader board. Ready to write bundled MicroPython."
                    } else {
                        installStage = "bootloader"
                        status = "Detected ESP32 bootloader. Ready to erase, or continue if you know erase already completed."
                    }
                    append("DETECTED: ESP32 ROM bootloader.")
                    return@launch
                }
                val repl = withContext(Dispatchers.IO) {
                    runCatching { probeMicroPythonRepl(usbManager, device) { progress -> scope.launch { setInstallerProgress(progress) } } }
                }
                installing = false
                if (repl.isSuccess && repl.getOrThrow().success) {
                    clearBootloaderCheck()
                    clearMicroPythonCheck()
                    installerProgress = 1f
                    installerProgressNote = "Board is running MicroPython."
                    installStage = "sensor"
                    status = "Detected MicroPython REPL. Choose the attached sensor, then upload MethodMesh firmware."
                    append("DETECTED: MicroPython REPL.")
                } else {
                    installerProgress = null
                    installerProgressNote = ""
                    val bootloaderMessage = bootloader.exceptionOrNull()?.message ?: bootloader.getOrNull()?.message ?: "no bootloader response"
                    val replMessage = repl.exceptionOrNull()?.message ?: repl.getOrNull()?.message ?: "no MicroPython response"
                    status = if (looksLikeEspIdfFirmware(replMessage)) {
                        flashEraseCompleted = false
                        installStage = "bootloader"
                        "Old ESP-IDF/Home Assistant-style firmware is still running. Put the board in bootloader mode and erase/write MicroPython again."
                    } else {
                        "Could not detect board state. If you already erased the board, skip to MicroPython write, then put the board in bootloader mode and check again."
                    }
                    append("DETECT FAILED: bootloader: ${bootloaderMessage.take(180)} | MicroPython: ${replMessage.take(180)}")
                }
            }
        }

        fun checkMicroPythonForProfileInstall(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed before checking MicroPython."
                requestPermission(device, "micropython_check")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                return
            }
            installing = true
            installerProgress = 0.02f
            installerProgressNote = "Checking normal MicroPython REPL only. This step does not use bootloader mode."
            status = "Checking MicroPython REPL…"
            log = ""
            append("Opening normal MicroPython USB device on ${device.deviceName}.")
            append("If this loops: tap RESET without BOOT, wait for USB to reappear, refresh devices, then retry.")
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { probeMicroPythonRepl(usbManager, device) { progress -> scope.launch { setInstallerProgress(progress) } } }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), error.message ?: error::class.java.simpleName) }
                }
                installing = false
                if (outcome.success) {
                    clearBootloaderCheck()
                    microPythonReadyDeviceName = device.deviceName
                    installerProgress = 1f
                    installerProgressNote = "MicroPython is ready for profile upload."
                    installStage = "sensor"
                    status = "MicroPython detected. Choose the attached sensor, then upload MethodMesh firmware."
                    append("MICROPYTHON OK: ${outcome.message}")
                } else {
                    installerProgress = null
                    installerProgressNote = ""
                    status = "MicroPython check failed. Reset normally without BOOT, refresh USB devices, and retry. ${outcome.message}"
                    append("MICROPYTHON CHECK FAILED: ${outcome.message}")
                }
            }
        }

        fun installMainPy(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed for this MicroPython device. Android may see it as new after flashing."
                requestPermission(device, "main_py")
                return
            }
            installing = true
            installerProgress = null
            installerProgressNote = ""
            result = null
            status = "Uploading MethodMesh firmware for ${selectedSensorProfile.label}…"
            log = "Opening MicroPython USB REPL on ${device.deviceName}\nAttached sensor: ${selectedSensorProfile.label}"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeout(60000L) {
                            installMainPyFirmware(
                                usbManager = usbManager,
                                device = device,
                                firmware = mainPy,
                                driverFiles = driverFiles,
                                configFiles = listOf(SENSOR_CONFIG_TARGET to selectedSensorConfig())
                            ) { progress ->
                                scope.launch { setInstallerProgress(progress) }
                            }
                        }
                    }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), error.message ?: error::class.java.simpleName) }
                }
                installing = false
                installerProgress = if (outcome.success) 1f else installerProgress
                status = if (outcome.success) "MethodMesh firmware uploaded for ${selectedSensorProfile.label}. Reset the board, then run BLE sensor provisioning." else "Sensor profile upload failed: ${outcome.message}"
                if (outcome.success) installStage = "done"
                if (!outcome.success && looksLikeEspIdfFirmware(outcome.message)) {
                    flashEraseCompleted = false
                    installStage = "bootloader"
                    status = "Old ESP-IDF/Home Assistant-style firmware is still running. Return to bootloader, erase flash, then write MicroPython before uploading MethodMesh firmware."
                }
                append(if (outcome.success) outcome.message else "FAILED: ${outcome.message}")
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "installed" else "failed",
                        firmwareName = "esp32c3_sensor_node/main.py:${selectedSensorProfile.id}",
                        firmwareVersion = "methodmesh-sensor-0.1.6",
                        firmwareBytes = (
                            mainPy.toByteArray(Charsets.UTF_8).size +
                                driverFiles.sumOf { it.second.toByteArray(Charsets.UTF_8).size } +
                                selectedSensorConfig().toByteArray(Charsets.UTF_8).size
                            ).toString(),
                        usbDevice = device.usbLabel(),
                        error = if (outcome.success) "" else outcome.message
                    )
                )
            }
        }

        fun flashMicroPython(device: UsbDevice, cleanErase: Boolean = false) {
            val actionName = if (cleanErase) "clean_micropython" else "micropython"
            if (!usbManager.hasPermission(device)) { requestPermission(device, actionName); return }
            if (!device.isLikelyEsp32Target()) {
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                record(SensorFirmwareInstallOutcome(status = "failed", firmwareName = "ESP32_GENERIC_C3-20260406-v1.28.0.bin", firmwareVersion = "MicroPython v1.28.0", firmwareBytes = microPythonBin.size.toString(), usbDevice = device.usbLabel(), error = status))
                return
            }
            installing = true
            installerProgress = null
            installerProgressNote = ""
            result = null
            status = if (cleanErase) "Clean-installing MicroPython to ESP32-C3…" else "Flashing MicroPython to ESP32-C3…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nIf sync fails: hold BOOT, tap RESET, release BOOT, then try again." +
                if (cleanErase) "\nClean install will erase the normal 4 MB flash area before writing MicroPython." else ""
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { flashMicroPythonImage(usbManager, device, microPythonFlashImage, cleanErase = cleanErase) { progress -> scope.launch { setInstallerProgress(progress) } } }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}") }
                }
                installing = false
                installerProgress = if (outcome.success) 1f else installerProgress
                status = if (outcome.success) {
                    if (cleanErase) "Clean MicroPython install complete. Reset or unplug/replug the board, refresh USB devices, then install the MethodMesh sensor profile."
                    else "MicroPython installed. Reset or unplug/replug the board, refresh USB devices, then install the MethodMesh sensor profile."
                } else "MicroPython flash failed: ${outcome.message}"
                append(outcome.message)
                if (outcome.success) {
                    installStage = "reset"
                    flashEraseCompleted = false
                    devices = usbDevices(usbManager)
                    selected = null
                    clearBootloaderCheck()
                }
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "installed" else "failed",
                        firmwareName = "ESP32_GENERIC_C3-20260406-v1.28.0.bin",
                        firmwareVersion = "MicroPython v1.28.0",
                        firmwareBytes = microPythonBin.size.toString(),
                        usbDevice = device.usbLabel(),
                        error = if (outcome.success) "" else outcome.message
                    )
                )
            }
        }

        fun flashSelectedSensorImage(device: UsbDevice) {
            val image = selectedFullFlashImage ?: run {
                status = "No bundled full flash image found for ${selectedSensorProfile.label}. Expected asset: ${selectedSensorProfile.fullImageAsset}."
                append("MISSING IMAGE: ${selectedSensorProfile.fullImageAsset}")
                record(
                    SensorFirmwareInstallOutcome(
                        status = "failed",
                        firmwareName = selectedSensorProfile.fullImageAsset,
                        firmwareVersion = "methodmesh-sensor-0.1.6",
                        usbDevice = device.usbLabel(),
                        error = status
                    )
                )
                return
            }
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed before flashing the selected sensor image."
                requestPermission(device, "sensor_image")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                record(
                    SensorFirmwareInstallOutcome(
                        status = "failed",
                        firmwareName = selectedSensorProfile.fullImageAsset,
                        firmwareVersion = "methodmesh-sensor-0.1.6",
                        firmwareBytes = selectedFullImageBytes?.size?.toString().orEmpty(),
                        usbDevice = device.usbLabel(),
                        error = status
                    )
                )
                return
            }
            installing = true
            installerProgress = null
            installerProgressNote = ""
            result = null
            status = "Installing ${selectedSensorProfile.label} image…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nSelected image: ${selectedSensorProfile.fullImageAsset}"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        flashEsp32FullSensorImage(
                            usbManager = usbManager,
                            device = device,
                            image = image,
                            imageLabel = selectedSensorProfile.label
                        ) { progress ->
                            scope.launch { setInstallerProgress(progress) }
                        }
                    }.getOrElse { error ->
                        FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}")
                    }
                }
                installing = false
                installerProgress = if (outcome.success) 1f else installerProgress
                status = if (outcome.success) {
                    installStage = "done"
                    "${selectedSensorProfile.label} image installed. Reset the board normally, then provision over BLE."
                } else {
                    "Sensor image install failed: ${outcome.message}"
                }
                append(if (outcome.success) outcome.message else "FAILED: ${outcome.message}")
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "installed" else "failed",
                        firmwareName = selectedSensorProfile.fullImageAsset,
                        firmwareVersion = "methodmesh-sensor-0.1.6",
                        firmwareBytes = selectedFullImageBytes?.size?.toString().orEmpty(),
                        usbDevice = device.usbLabel(),
                        error = if (outcome.success) "" else outcome.message
                    )
                )
            }
        }

        fun wipeBoard(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                requestPermission(device, "board_wipe")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                return
            }
            installing = true
            installerProgress = null
            installerProgressNote = ""
            result = null
            status = "Nuking ESP32 boot/app flash…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nThis step erases/overwrites the lower 2 MB boot/app/settings region so old firmware cannot start. After it succeeds, keep or return the board in BOOT mode, refresh USB devices, check bootloader again, then write MicroPython."
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { eraseEsp32FlashOnly(usbManager, device, bootNukeImage) { progress -> scope.launch { setInstallerProgress(progress) } } }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}") }
                }
                installing = false
                installerProgress = if (outcome.success) 1f else installerProgress
                status = if (outcome.success) {
                    "Erase complete. Refresh/reselect the ESP32-C3 in bootloader mode, check bootloader again, then write MicroPython."
                } else {
                    "ESP32 flash erase failed: ${outcome.message}"
                }
                if (outcome.success) {
                    flashEraseCompleted = true
                    installStage = "write_micropython"
                    devices = usbDevices(usbManager)
                    selected = null
                    clearBootloaderCheck()
                }
                append(outcome.message)
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "erased" else "failed",
                        firmwareName = "ESP32-C3 flash erase",
                        firmwareVersion = "MicroPython v1.28.0",
                        firmwareBytes = "0",
                        usbDevice = device.usbLabel(),
                        error = if (outcome.success) "" else outcome.message
                    )
                )
            }
        }

        androidx.compose.runtime.LaunchedEffect(permissionCheckTick) {
            val device = permissionCheckDevice ?: return@LaunchedEffect
            if (permissionCheckTick <= 0) return@LaunchedEffect
            delay(1200L)
            if (usbManager.hasPermission(device) && !installing) {
                status = "USB permission granted. Continuing…"
                when (pendingAction) {
                    "detect_state" -> detectBoardState(device)
                    "micropython_check" -> checkMicroPythonForProfileInstall(device)
                    "bootloader_check" -> checkBootloader(device)
                    "micropython" -> flashMicroPython(device)
                    "clean_micropython" -> flashMicroPython(device, cleanErase = true)
                    "sensor_image" -> flashSelectedSensorImage(device)
                    "board_wipe" -> wipeBoard(device)
                    else -> installMainPy(device)
                }
            }
        }

        val receiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action != ACTION_USB_PERMISSION) return
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && device != null) {
                        when (pendingAction) {
                            "detect_state" -> detectBoardState(device)
                            "micropython_check" -> checkMicroPythonForProfileInstall(device)
                            "bootloader_check" -> checkBootloader(device)
                            "micropython" -> flashMicroPython(device)
                            "clean_micropython" -> flashMicroPython(device, cleanErase = true)
                            "sensor_image" -> flashSelectedSensorImage(device)
                            "board_wipe" -> wipeBoard(device)
                            else -> installMainPy(device)
                        }
                    } else {
                        installing = false
                        status = if (pendingAction == "main_py") "USB permission denied for the MicroPython device. If the board just rebooted, unplug/replug it, refresh USB devices, select it again, and retry sensor profile upload." else "USB permission denied."
                    }
                }
            }
        }
        DisposableEffect(Unit) {
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) androidContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else androidContext.registerReceiver(receiver, filter)
            onDispose { runCatching { androidContext.unregisterReceiver(receiver) } }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                devices = usbDevices(usbManager)
                selected = null
                clearBootloaderCheck()
                clearMicroPythonCheck()
                installStage = initialInstallStage
                flashEraseCompleted = initialFlashEraseCompleted
                status = "Device list refreshed. Select the ESP32-C3 board before continuing."
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Install the MethodMesh sensor stack to an ESP32-C3 from the phone.", style = MaterialTheme.typography.bodyMedium)
            Text(
                when (installStage) {
                    "bootloader" -> "Wipe old ESP32 firmware."
                    "write_micropython" -> "Install the MethodMesh ESP32 runtime."
                    "reset" -> "Check the MicroPython USB connection."
                    "sensor" -> "Choose the attached sensor."
                    "upload" -> "Upload the selected sensor profile."
                    "done" -> "ESP32 sensor setup complete."
                    else -> "ESP32 sensor installer."
                },
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Text("Bundled firmware", fontWeight = FontWeight.SemiBold)
            Text("$MICROPYTHON_BIN_ASSET · ${microPythonBin.size} bytes · board-level precompiled MicroPython image", style = MaterialTheme.typography.bodySmall)
            Text("${microPythonFlashImage.blocks.size} ready flash block(s) cached in app memory; sensor choice does not rebuild this image.", style = MaterialTheme.typography.bodySmall)
            Text("$MAIN_PY_ASSET · ${mainPy.toByteArray(Charsets.UTF_8).size} bytes · generic multi-sensor runtime", style = MaterialTheme.typography.bodySmall)
            Text("${driverFiles.size} sensor driver file(s) · ${driverFiles.sumOf { it.second.toByteArray(Charsets.UTF_8).size }} bytes", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))

            if (installStage == "bootloader") {
                Text("Put the board into bootloader mode:", fontWeight = FontWeight.SemiBold)
                Text(
                    if (flashEraseCompleted) "Flash erase has already completed. Hold BOOT/tap RESET if needed, refresh/select the ESP32-C3, then check bootloader to continue to MicroPython write."
                    else "Hold BOOT, tap RESET, then release BOOT. Refresh devices and select the ESP32-C3.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        devices = usbDevices(usbManager)
                        selected = null
                        clearBootloaderCheck()
                        clearMicroPythonCheck()
                        status = "Found ${devices.size} USB device(s). Select the ESP32-C3 board below."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Refresh USB devices") }
                Spacer(Modifier.height(8.dp))
                if (devices.isEmpty()) {
                    Text("No USB devices found. Connect the ESP32-C3 by USB/OTG, then refresh.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("Select ESP32-C3 bootloader device", fontWeight = FontWeight.SemiBold)
                }
                devices.forEach { device ->
                    OutlinedButton(onClick = { selected = device; clearBootloaderCheck(); clearMicroPythonCheck(); status = "Selected ${device.usbLabel()} ${device.guardLabel()}" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected == device) "✓ ${device.usbLabel()}" else device.usbLabel())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selected?.let { detectBoardState(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing && selected?.isLikelyEsp32Target() == true
                ) {
                    Text(if (installing && status.startsWith("Detecting")) "Detecting…" else "Detect ESP32 state")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { selected?.let { checkBootloader(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing && selected?.isLikelyEsp32Target() == true
                ) {
                    Text(if (installing && status.startsWith("Checking ESP32")) "Checking bootloader…" else "Confirm bootloader mode")
                }
                if (bootloaderCheckSummary.isNotBlank() && bootloaderCheckDeviceName == selected?.deviceName) {
                    Text("✓ $bootloaderCheckSummary", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Flash controls stay locked until this selected device answers the ESP32 ROM bootloader handshake.", style = MaterialTheme.typography.bodySmall)
                }
                if (flashEraseCompleted && bootloaderCheckSummary.isNotBlank() && bootloaderCheckDeviceName == selected?.deviceName) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { installStage = "write_micropython"; status = "Ready to write bundled MicroPython image." },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !installing
                    ) {
                        Text("Continue to MicroPython write")
                    }
                }
                if (!flashEraseCompleted && selected?.isLikelyEsp32Target() == true) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            flashEraseCompleted = true
                            installStage = "write_micropython"
                            status = "Marked erase as already complete. Put/check the board in bootloader mode, then write bundled MicroPython."
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !installing
                    ) {
                        Text("I already erased this board — skip to MicroPython write")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(checked = destructiveConfirmed, onCheckedChange = { destructiveConfirmed = it })
                    Text("I understand this erases and overwrites the selected ESP32-C3.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { selected?.let { wipeBoard(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing &&
                        destructiveConfirmed &&
                        selected?.isLikelyEsp32Target() == true &&
                        bootloaderCheckSummary.isNotBlank() &&
                        bootloaderCheckDeviceName == selected?.deviceName
                ) {
                    Text(if (installing) "Wiping old firmware…" else "Wipe old firmware")
                }
            } else if (installStage == "write_micropython") {
                Text("Write bundled MicroPython image.", fontWeight = FontWeight.SemiBold)
                Text("Keep the board in bootloader mode. If Android refreshed the USB connection after erase, refresh and select the ESP32-C3 again, then run the bootloader check before writing.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        devices = usbDevices(usbManager)
                        selected = null
                        clearBootloaderCheck()
                        clearMicroPythonCheck()
                        status = "Found ${devices.size} USB device(s). Select the ESP32-C3 bootloader device again."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Refresh USB devices") }
                devices.forEach { device ->
                    OutlinedButton(onClick = { selected = device; clearBootloaderCheck(); clearMicroPythonCheck(); status = "Selected ${device.usbLabel()} ${device.guardLabel()}" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected == device) "✓ ${device.usbLabel()}" else device.usbLabel())
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selected?.let { detectBoardState(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing && selected?.isLikelyEsp32Target() == true
                ) {
                    Text(if (installing && status.startsWith("Detecting")) "Detecting…" else "Detect ESP32 state")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { selected?.let { checkBootloader(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing && selected?.isLikelyEsp32Target() == true
                ) {
                    Text(if (installing && status.startsWith("Checking ESP32")) "Checking bootloader…" else "Confirm bootloader mode")
                }
                if (bootloaderCheckSummary.isNotBlank() && bootloaderCheckDeviceName == selected?.deviceName) {
                    Text("✓ $bootloaderCheckSummary", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("MicroPython write stays locked until the selected device answers the ESP32 ROM bootloader handshake.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { selected?.let { flashMicroPython(it, cleanErase = false) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing &&
                        selected?.isLikelyEsp32Target() == true &&
                        bootloaderCheckSummary.isNotBlank() &&
                        bootloaderCheckDeviceName == selected?.deviceName
                ) {
                    Text(if (installing) "Installing runtime…" else "Install MethodMesh runtime")
                }
                OutlinedButton(onClick = { installStage = "bootloader"; clearBootloaderCheck(); status = "Back to wipe screen." }, modifier = Modifier.fillMaxWidth(), enabled = !installing) {
                    Text("Back to wipe step")
                }
            } else if (installStage == "reset") {
                Text("Verify MicroPython before sensor upload.", fontWeight = FontWeight.SemiBold)
                Text("Reset the board normally: tap RESET without holding BOOT, or unplug/replug it. Then refresh/select it and run the MicroPython check. If old Wi‑Fi firmware is still running, the app will send you back to erase/write.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        devices = usbDevices(usbManager)
                        selected = null
                        clearMicroPythonCheck()
                        status = "Found ${devices.size} USB device(s). Select the normal MicroPython USB device."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Refresh USB devices") }
                devices.forEach { device ->
                    OutlinedButton(onClick = { selected = device; clearMicroPythonCheck(); status = "Selected normal device: ${device.usbLabel()}" }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected == device) "✓ ${device.usbLabel()}" else device.usbLabel())
                    }
                }
                Button(
                    onClick = { selected?.let { checkMicroPythonForProfileInstall(it) } ?: run { status = "Select the normal MicroPython USB device first." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected != null && !installing
                ) { Text(if (installing && status.startsWith("Checking MicroPython")) "Checking MicroPython…" else "Check MicroPython connection") }
            } else if (installStage == "sensor") {
                Text("1. Select sensor image to flash.", fontWeight = FontWeight.SemiBold)
                SENSOR_FIRMWARE_PROFILES.forEach { profile ->
                    val selectedProfile = profile.id == selectedSensorProfileId
                    if (selectedProfile) {
                        Button(onClick = { selectedSensorProfileId = profile.id }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("✓ ${profile.label}")
                                Text(profile.description, style = MaterialTheme.typography.bodySmall)
                                Text(profile.fullImageAsset, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                selectedSensorProfileId = profile.id
                                status = "Selected attached sensor: ${profile.label}."
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth()) {
                                Text(profile.label)
                                Text(profile.description, style = MaterialTheme.typography.bodySmall)
                                Text(profile.fullImageAsset, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (selectedFullFlashImage != null) "✓ Bundled image ready: ${selectedFullImageBytes?.size ?: 0} bytes."
                    else "No bundled image found for ${selectedSensorProfile.label}. Regenerate firmware images before flashing.",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                Text("2. Put the ESP32-C3 into bootloader mode.", fontWeight = FontWeight.SemiBold)
                Text("Hold BOOT, tap RESET, release BOOT, then refresh and select the ESP32-C3. This route erases/overwrites from bootloader mode and does not use the MicroPython REPL.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        devices = usbDevices(usbManager)
                        selected = null
                        clearBootloaderCheck()
                        status = "Found ${devices.size} USB device(s). Select the ESP32-C3 bootloader device."
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing
                ) { Text("Refresh USB devices") }
                devices.forEach { device ->
                    OutlinedButton(
                        onClick = { selected = device; clearBootloaderCheck(); status = "Selected ${device.usbLabel()} ${device.guardLabel()}" },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !installing
                    ) {
                        Text(if (selected == device) "✓ ${device.usbLabel()}" else device.usbLabel())
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { selected?.let { checkBootloader(it) } ?: run { status = "Select the ESP32-C3 bootloader device first." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selected?.isLikelyEsp32Target() == true && !installing
                ) { Text(if (installing && status.startsWith("Checking ESP32")) "Checking bootloader…" else "Confirm bootloader mode") }
                if (bootloaderCheckSummary.isNotBlank() && bootloaderCheckDeviceName == selected?.deviceName) {
                    Text("✓ $bootloaderCheckSummary", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Install stays locked until this selected device answers the ESP32 ROM bootloader handshake.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                Row {
                    Checkbox(checked = destructiveConfirmed, onCheckedChange = { destructiveConfirmed = it })
                    Text("I understand this erases and overwrites the selected ESP32-C3.", style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { selected?.let { flashSelectedSensorImage(it) } ?: run { status = "No USB device selected." } },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !installing &&
                        destructiveConfirmed &&
                        selectedFullFlashImage != null &&
                        selected?.isLikelyEsp32Target() == true &&
                        bootloaderCheckSummary.isNotBlank() &&
                        bootloaderCheckDeviceName == selected?.deviceName
                ) { Text(if (installing) "Installing ${selectedSensorProfile.label} image…" else "Erase and install ${selectedSensorProfile.label}") }
            } else if (installStage == "upload") {
                Text("Legacy profile upload.", fontWeight = FontWeight.SemiBold)
                Text("This fallback uses the MicroPython REPL. Prefer the selected-image bootloader installer above for field use.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { selected?.let { installMainPy(it) } ?: run { status = "No USB device selected. Go back and refresh/select the normal board." } }, modifier = Modifier.fillMaxWidth(), enabled = !installing) {
                    Text(if (installing) "Uploading sensor profile…" else "Upload sensor profile to board")
                }
                OutlinedButton(onClick = { installStage = "sensor"; status = "Choose the attached sensor and normal USB device." }, modifier = Modifier.fillMaxWidth(), enabled = !installing) {
                    Text("Back to sensor and device selection")
                }
            } else {
                Text("MethodMesh sensor image was installed. Reset the board, then use BLE sensor provisioning.", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = { installStage = "bootloader"; flashEraseCompleted = false; result = null; selected = null; status = "Starting a new install." }, modifier = Modifier.fillMaxWidth()) {
                    Text("Start another install")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (installerProgress != null || installing) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { installerProgress ?: 0.1f },
                    modifier = Modifier.fillMaxWidth()
                )
                if (installerProgressNote.isNotBlank()) {
                    Text(installerProgressNote, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (log.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Installer terminal", fontWeight = FontWeight.SemiBold)
                Text(
                    log,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .verticalScroll(logScrollState)
                        .padding(10.dp),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = esp32FirmwareExamples(capabilityId)
            )
        }
    }
}

object Esp32BoardWipeCapabilityScreen : CapabilityScreenSpec by SensorFirmwareInstallerCapabilityScreenSpec(
    capabilityId = As100Esp32BoardWipeMethod.id,
    title = "Wipe ESP32 board",
    description = "Erase an ESP32-C3 and check that old firmware is no longer running."
)

object Esp32RuntimeInstallCapabilityScreen : CapabilityScreenSpec by SensorFirmwareInstallerCapabilityScreenSpec(
    capabilityId = As100Esp32RuntimeInstallMethod.id,
    title = "Install MethodMesh ESP32 runtime",
    description = "Write the bundled board-level MicroPython image to an ESP32-C3.",
    initialInstallStage = "write_micropython",
    initialFlashEraseCompleted = true
)

object Esp32SensorProfileInstallCapabilityScreen : CapabilityScreenSpec by SensorFirmwareInstallerCapabilityScreenSpec(
    capabilityId = As100Esp32SensorProfileInstallMethod.id,
    title = "Install ESP32 sensor image",
    description = "Erase and install a complete MethodMesh ESP32-C3 image for the selected sensor.",
    initialInstallStage = "sensor"
)

private data class FirmwareInstallResult(val success: Boolean, val usbDevice: String, val message: String)

private fun usbDevices(manager: UsbManager): List<UsbDevice> = manager.deviceList.values.sortedBy { it.usbLabel() }

private fun UsbDevice.usbLabel(): String = "${manufacturerName.orEmpty()} ${productName.orEmpty()} ${vendorId.toString(16)}:${productId.toString(16)} ${deviceName}".trim()

private fun UsbDevice.guardLabel(): String = if (isLikelyEsp32Target()) "· recognised ESP/serial target" else "· not enabled for raw flashing"

private fun UsbDevice.isLikelyEsp32Target(): Boolean {
    val vid = vendorId
    val pid = productId
    return vid == 0x303a || // Espressif native USB/JTAG/serial, including ESP32-C3.
        vid == 0x10c4 && pid == 0xea60 || // Silicon Labs CP210x bridge.
        vid == 0x1a86 && (pid == 0x7523 || pid == 0x55d4) || // WCH CH340/CH9102 family.
        vid == 0x0403 // FTDI USB serial adapters.
}

private fun probeEsp32Bootloader(
    usbManager: UsbManager,
    device: UsbDevice,
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    if (!device.isLikelyEsp32Target()) {
        return FirmwareInstallResult(false, device.usbLabel(), "Refusing to probe an unrecognised USB device.")
    }
    val serial = openUsbSerial(usbManager, device)
        ?: return FirmwareInstallResult(false, device.usbLabel(), "USB serial open failed: no bulk USB serial interface was found.")
    serial.use {
        it.drain()
        onProgress("Synchronising with ESP32 ROM bootloader…")
        Esp32RomBootloader(it).sync(onProgress)
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        "ESP32-C3 ROM bootloader responded on ${device.usbLabel()}."
    )
}

private fun eraseEsp32FlashOnly(
    usbManager: UsbManager,
    device: UsbDevice,
    nukeImage: PreparedFlashImage,
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    if (!device.isLikelyEsp32Target()) {
        return FirmwareInstallResult(false, device.usbLabel(), "Refusing to wipe an unrecognised USB device.")
    }
    val serial = openUsbSerial(usbManager, device)
        ?: return FirmwareInstallResult(false, device.usbLabel(), "USB serial open failed: no bulk USB serial interface was found.")
    serial.use {
        it.drain()
        onProgress("Synchronising with ESP32 ROM bootloader…")
        val rom = Esp32RomBootloader(it)
        rom.sync(onProgress)
        rom.nukeBootAndAppFlash(nukeImage, onProgress)
        runCatching { rom.finish(reboot = false) }
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        "ESP32 boot/app/settings region was erased and overwritten. Old firmware should no longer be able to start; re-check bootloader before writing the bundled MicroPython image."
    )
}

private fun probeMicroPythonRepl(
    usbManager: UsbManager,
    device: UsbDevice,
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    onProgress("Checking for MicroPython REPL…")
    val serial = openManagedMicroPythonSerial(usbManager, device, onProgress)
        ?: return FirmwareInstallResult(false, device.usbLabel(), "No Android USB serial driver could open this device for the MicroPython REPL.")
    serial.use {
        managedReplProbe(it, onProgress)
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        "MicroPython REPL responded on ${device.usbLabel()}."
    )
}

private fun installMainPyFirmware(
    usbManager: UsbManager,
    device: UsbDevice,
    firmware: String,
    driverFiles: List<Pair<String, String>> = emptyList(),
    configFiles: List<Pair<String, String>> = emptyList(),
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    val serial = openManagedMicroPythonSerial(usbManager, device, onProgress)
        ?: return FirmwareInstallResult(false, device.usbLabel(), "No Android USB serial driver could open this device for the MicroPython REPL.")
    serial.use {
        managedReplProbe(it, onProgress)
        runCatching {
            rawExecExpecting(
                serial = it,
                code = "print('__METHODMESH_RAW_READY__')",
                label = "raw probe",
                expected = "__METHODMESH_RAW_READY__"
            )
        }
            .onFailure { error -> throw IllegalStateException("MicroPython raw REPL probe failed: ${error.message.orEmpty()}") }
        quietExistingSensorRuntime(it, onProgress)
        purgeExistingMicroPythonFiles(it, onProgress)

        if (driverFiles.isNotEmpty()) {
            onProgress("Preparing sensor driver folder…")
            runCatching {
                rawExecExpecting(
                    serial = it,
                    code = "import os\ntry:\n    os.mkdir('sensor_drivers')\nexcept OSError:\n    pass\nprint('__METHODMESH_DRIVER_DIR_OK__')",
                    label = "create sensor driver folder",
                    expected = "__METHODMESH_DRIVER_DIR_OK__"
                )
            }
                .onFailure { error -> throw IllegalStateException("Could not prepare sensor_drivers folder: ${error.message.orEmpty()}") }
        }
        driverFiles.forEachIndexed { index, (path, contents) ->
            uploadPythonFile(
                serial = it,
                path = path,
                contents = contents,
                label = "sensor driver ${index + 1}/${driverFiles.size}",
                onProgress = onProgress
            )
        }
        configFiles.forEachIndexed { index, (path, contents) ->
            uploadPythonFile(
                serial = it,
                path = path,
                contents = contents,
                label = "sensor configuration ${index + 1}/${configFiles.size}",
                onProgress = onProgress
            )
        }
        uploadPythonFile(
            serial = it,
            path = "main.py",
            contents = firmware,
            label = "MethodMesh runtime",
            onProgress = onProgress
        )
        onProgress("Verifying installed MethodMesh sensor profile…")
        configFiles.firstOrNull { config -> config.first == SENSOR_CONFIG_TARGET }?.second?.let { expectedConfig ->
            val expectedProfile = runCatching { JSONObject(expectedConfig).optString("sensor_profile") }.getOrNull().orEmpty()
            if (expectedProfile.isNotBlank()) {
                rawExecExpecting(
                    serial = it,
                    code = "import json\ncfg=json.loads(open('$SENSOR_CONFIG_TARGET').read())\nprint('__METHODMESH_PROFILE__' + str(cfg.get('sensor_profile','')))",
                    label = "verify selected sensor profile",
                    expected = "__METHODMESH_PROFILE__$expectedProfile"
                )
            }
        }
        onProgress("Resetting board after profile upload…")
        runCatching { rawExec(it, "import machine; machine.reset()", "reset", requirePrompt = false) }
    }
    return FirmwareInstallResult(true, device.usbLabel(), "MethodMesh runtime, drivers and selected sensor configuration were copied to the board; reset was requested.")
}

private fun quietExistingSensorRuntime(
    serial: PythonSerialLink,
    onProgress: (String) -> Unit
) {
    onProgress("Stopping previous sensor firmware before upload…")
    val code = """
import gc
try:
    import network
    network.WLAN(network.STA_IF).active(False)
    network.WLAN(network.AP_IF).active(False)
except Exception:
    pass
try:
    import bluetooth
    bluetooth.BLE().active(False)
except Exception:
    pass
try:
    gc.collect()
except Exception:
    pass
print('__METHODMESH_QUIET_OK__')
""".trimIndent()
    rawExecExpecting(
        serial = serial,
        code = code,
        label = "quiet previous firmware",
        expected = "__METHODMESH_QUIET_OK__"
    )
    Thread.sleep(500)
    serial.drain()
}

private fun purgeExistingMicroPythonFiles(
    serial: PythonSerialLink,
    onProgress: (String) -> Unit
) {
    onProgress("Removing old MicroPython startup/config files before upload…")
    val code = """
import os
def rm(path):
    try:
        mode = os.stat(path)[0]
        is_dir = bool(mode & 0x4000)
    except Exception:
        return
    if is_dir:
        try:
            for name in os.listdir(path):
                rm(path + '/' + name)
        except Exception:
            pass
        try:
            os.rmdir(path)
        except Exception:
            pass
    else:
        try:
            os.remove(path)
        except Exception:
            pass
for p in ('main.py','boot.py','methodmesh_sensor_config.json','sensor_drivers'):
    rm(p)
print('__METHODMESH_PURGE_OK__')
""".trimIndent()
    rawExecExpecting(
        serial = serial,
        code = code,
        label = "purge old MicroPython startup files",
        expected = "__METHODMESH_PURGE_OK__"
    )
    Thread.sleep(300)
    serial.drain()
}

private fun openManagedMicroPythonSerial(
    manager: UsbManager,
    device: UsbDevice,
    onProgress: (String) -> Unit
): PythonSerialLink? {
    onProgress("Opening MicroPython serial console…")
    openMicroPythonSerial(manager, device)?.let { return it }
    onProgress("Default serial driver did not open; trying native USB CDC path…")
    return openUsbSerial(manager, device)
}

private fun managedReplProbe(
    serial: PythonSerialLink,
    onProgress: (String) -> Unit
) {
    val attempts = listOf(
        "interrupt running script" to {
            serial.setTerminalReady()
            serial.interruptRunningScript()
            friendlyReplProbe(serial, "interrupt")
        },
        "soft reset MicroPython" to {
            serial.softResetMicroPython()
            friendlyReplProbe(serial, "soft reset")
        },
        "quiet raw REPL fallback" to {
            rawExecExpecting(
                serial = serial,
                code = "print('__METHODMESH_FRIENDLY_READY__')",
                label = "quiet raw probe",
                expected = "__METHODMESH_FRIENDLY_READY__"
            )
        }
    )
    val errors = mutableListOf<String>()
    attempts.forEachIndexed { index, (label, attempt) ->
        onProgress("MicroPython recovery ${index + 1}/${attempts.size}: $label…")
        val response = runCatching { attempt() }
            .onSuccess { return }
            .onFailure { error -> errors += "$label: ${error.message.orEmpty().ifBlank { error::class.java.simpleName }}" }
        response.exceptionOrNull()
        Thread.sleep(350)
    }
    throw IllegalStateException(
        "MicroPython REPL did not respond after managed recovery attempts. " +
            "Tap RESET once without holding BOOT, wait for Android to rediscover USB, refresh devices, then retry this step. " +
            "Attempts: ${errors.joinToString(" | ").take(600)}"
    )
}

private fun uploadPythonFile(
    serial: PythonSerialLink,
    path: String,
    contents: String,
    label: String,
    onProgress: (String) -> Unit
) {
    onProgress("Preparing $label…")
    runCatching {
        rawExecExpecting(
            serial = serial,
            code = "f=open('$path','wb');f.write(b'');f.close();print('__METHODMESH_CREATE_OK__')",
            label = "create $path",
            expected = "__METHODMESH_CREATE_OK__"
        )
    }
        .onFailure { error -> throw IllegalStateException("Could not create $path: ${error.message.orEmpty()}") }
    val chunks = contents.toByteArray(Charsets.UTF_8).toList().chunked(192)
    chunks.forEachIndexed { index, chunk ->
        onProgress("Writing $label chunk ${index + 1}/${chunks.size}…")
        val literal = chunk.toByteArray().joinToString("", prefix = "b'", postfix = "'") { "\\x%02x".format(it) }
        val marker = "__METHODMESH_WRITE_${index}_OK__"
        val code = "f=open('$path','ab');f.write($literal);f.close();print('$marker')"
        val outcome = retryMicroPythonWrite(
            attempts = 3,
            onRetry = { retry ->
                onProgress("Retrying $label chunk ${index + 1}/${chunks.size} ($retry/3)…")
                serial.interruptRunningScript()
            }
        ) {
            rawExecExpecting(
                serial = serial,
                code = code,
                label = "write $path chunk $index",
                expected = marker
            )
        }
        outcome.onFailure { error -> throw IllegalStateException("$path chunk $index failed: ${error.message.orEmpty()}") }
        Thread.sleep(60)
    }
    runCatching {
        rawExecExpecting(
            serial = serial,
            code = "print('__METHODMESH_SIZE__%d' % len(open('$path','rb').read()))",
            label = "verify $path size",
            expected = "__METHODMESH_SIZE__${contents.toByteArray(Charsets.UTF_8).size}"
        )
    }.onFailure { error -> throw IllegalStateException("$path size verification failed: ${error.message.orEmpty()}") }
}

private fun retryMicroPythonWrite(
    attempts: Int,
    onRetry: (Int) -> Unit,
    block: () -> String
): Result<String> {
    var last: Throwable? = null
    repeat(attempts) { index ->
        val result = runCatching { block() }
        if (result.isSuccess) return result
        last = result.exceptionOrNull()
        if (index < attempts - 1) {
            onRetry(index + 1)
            Thread.sleep(250L * (index + 1))
        }
    }
    return Result.failure(last ?: IllegalStateException("MicroPython write failed"))
}

private fun rawExecExpecting(
    serial: PythonSerialLink,
    code: String,
    label: String,
    expected: String
): String {
    val response = StringBuilder(rawExec(serial, code, label, requirePrompt = false, timeoutMs = 6500))
    val deadline = System.currentTimeMillis() + 9000
    while (!response.contains(expected) && System.currentTimeMillis() < deadline) {
        Thread.sleep(180)
        response.append(serial.readAvailable(500))
    }
    val text = response.toString()
    if (!text.contains(expected)) {
        throw IllegalStateException("Expected $expected during $label; response was ${text.ifBlank { "<blank>" }.take(360)}")
    }
    return text
}

private fun looksLikeEspIdfFirmware(text: String): Boolean {
    val lower = text.lowercase()
    return lower.contains("wifi_esp32") ||
        lower.contains("[wifi:") ||
        lower.contains("connecting to network failed") ||
        lower.contains("association expired") ||
        lower.contains("found networks")
}

private fun friendlyReplProbe(serial: PythonSerialLink, label: String = "friendly probe"): String {
    serial.drain()
    serial.setTerminalReady()
    serial.breakIntoRepl()
    serial.write("print('MethodMesh friendly REPL ready')\r\n".toByteArray(Charsets.UTF_8))
    val response = serial.readAvailable(5500)
    if (!response.contains("MethodMesh friendly REPL ready")) {
        if (looksLikeEspIdfFirmware(response)) {
            throw IllegalStateException(
                "Old ESP-IDF/Home Assistant-style firmware is still running, not MicroPython. " +
                    "Put the board in ESP32 bootloader mode, erase flash, then write the bundled MicroPython image. " +
                    "Response was: ${response.ifBlank { "<blank>" }.take(260)}"
            )
        }
        throw IllegalStateException(
            "No friendly REPL echo/print response during $label. Response was: ${response.ifBlank { "<blank>" }.take(220)}"
        )
    }
    return response
}

private fun rawExec(
    serial: PythonSerialLink,
    code: String,
    label: String,
    requirePrompt: Boolean = true,
    timeoutMs: Int = 2500
): String {
    enterRawRepl(serial, label)
    serial.write(code.toByteArray(Charsets.UTF_8))
    serial.write(byteArrayOf(4))
    val response = serial.readAvailable(timeoutMs)
    if (requirePrompt && response.isBlank()) throw IllegalStateException("No response from MicroPython during $label")
    return response
}

private fun enterRawRepl(serial: PythonSerialLink, label: String) {
    var last = ""
    repeat(4) { attempt ->
        serial.write(byteArrayOf(3, 3, 13, 10))
        Thread.sleep(450L + attempt * 150L)
        last = serial.readAvailable(700)
        serial.write(byteArrayOf(1))
        Thread.sleep(300)
        val raw = serial.readAvailable(900)
        last += raw
        if (last.contains("raw REPL") || last.contains("raw paste mode") || last.contains(">")) {
            serial.drain()
            return
        }
    }
    throw IllegalStateException(
        "Could not enter MicroPython raw REPL during $label. Last response was ${last.ifBlank { "<blank>" }.take(260)}"
    )
}

private fun openMicroPythonSerial(manager: UsbManager, device: UsbDevice): MicroPythonSerialLink? {
    val drivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)
    val driver = drivers.firstOrNull { it.device.deviceName == device.deviceName || it.device.deviceId == device.deviceId }
        ?: drivers.firstOrNull { it.device.vendorId == device.vendorId && it.device.productId == device.productId }
        ?: return null
    val connection = manager.openDevice(driver.device) ?: return null
    val port = driver.ports.firstOrNull() ?: run {
        connection.close()
        return null
    }
    return runCatching {
        port.open(connection)
        port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(false) }
        MicroPythonSerialLink(connection, port)
    }.getOrElse {
        runCatching { port.close() }
        connection.close()
        null
    }
}

private fun flashMicroPythonImage(
    usbManager: UsbManager,
    device: UsbDevice,
    image: PreparedFlashImage,
    cleanErase: Boolean = false,
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    if (!device.isLikelyEsp32Target()) return FirmwareInstallResult(false, device.usbLabel(), "Refusing to flash an unrecognised USB device.")
    var serial: UsbSerialLink? = null
    try {
        onProgress("Opening ESP32 USB serial bootloader connection…")
        serial = openUsbSerial(usbManager, device) ?: return FirmwareInstallResult(false, device.usbLabel(), "USB serial open failed: no bulk USB serial interface was found.")
        onProgress("Using the selected ESP32 bootloader connection; no automatic reset pulse will be sent.")
        serial.drain()
        val rom = Esp32RomBootloader(serial)
        onProgress("Synchronising with ESP32 ROM bootloader…")
        runCatching { rom.sync { progress -> onProgress(progress) } }.onFailure { error -> throw IllegalStateException("Bootloader sync failed: ${error.message.orEmpty()}") }
        if (cleanErase) {
            onProgress("Clean install requested. Full-chip erase is handled by the separate ESP32 board wipe step; writing MicroPython now.")
        }
        runCatching {
            rom.flashImage(
                address = ESP_FLASH_ADDRESS,
                image = image,
                cleanErase = cleanErase
            ) { progress -> onProgress(progress) }
        }
            .onFailure { error -> throw IllegalStateException("Flash write failed: ${error.message.orEmpty()}") }
        onProgress("Finishing flash and rebooting board…")
        runCatching { rom.finish(reboot = true) }.onFailure { error -> throw IllegalStateException("Flash finish failed: ${error.message.orEmpty()}") }
    } finally {
        runCatching { serial?.close() }
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        if (cleanErase) "MicroPython v1.28.0 was installed. Reset the board normally, then install the MethodMesh runtime or sensor profile."
        else "MicroPython v1.28.0 was written at flash address 0. Reset the board normally, then install the MethodMesh runtime or sensor profile."
    )
}

private fun flashEsp32FullSensorImage(
    usbManager: UsbManager,
    device: UsbDevice,
    image: PreparedFlashImage,
    imageLabel: String,
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    if (!device.isLikelyEsp32Target()) return FirmwareInstallResult(false, device.usbLabel(), "Refusing to flash an unrecognised USB device.")
    var serial: UsbSerialLink? = null
    try {
        onProgress("Opening ESP32 USB serial bootloader connection…")
        serial = openUsbSerial(usbManager, device) ?: return FirmwareInstallResult(false, device.usbLabel(), "USB serial open failed: no bulk USB serial interface was found.")
        serial.drain()
        val rom = Esp32RomBootloader(serial)
        onProgress("Synchronising with ESP32 ROM bootloader…")
        runCatching { rom.sync { progress -> onProgress(progress) } }.onFailure { error -> throw IllegalStateException("Bootloader sync failed: ${error.message.orEmpty()}") }
        runCatching {
            rom.flashImage(
                address = ESP_FLASH_ADDRESS,
                image = image,
                cleanErase = false,
                label = imageLabel
            ) { progress -> onProgress(progress) }
        }.onFailure { error -> throw IllegalStateException("Flash write failed: ${error.message.orEmpty()}") }
        onProgress("Finishing flash and rebooting board…")
        runCatching { rom.finish(reboot = true) }.onFailure { error -> throw IllegalStateException("Flash finish failed: ${error.message.orEmpty()}") }
    } finally {
        runCatching { serial?.close() }
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        "$imageLabel full flash image was written at flash address 0. Reset the board normally, then provision over BLE."
    )
}

private class Esp32RomBootloader(private val serial: UsbSerialLink) {
    fun sync(onProgress: (String) -> Unit = {}) {
        val syncPayload = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55.toByte() }
        var ok = false
        repeat(24) { attempt ->
            if (attempt == 0 || attempt % 4 == 3) onProgress("Synchronising with bootloader (${attempt + 1}/24)…")
            runCatching { command(0x08, syncPayload, 0, 1500) }.onSuccess { ok = true }
            if (ok) return@repeat
            Thread.sleep(250)
        }
        if (!ok) throw IllegalStateException("ESP32 ROM bootloader did not respond. For a blank/wiped board, hold BOOT, tap RESET, release BOOT, refresh USB devices, then retry this step. If it still times out, unplug/replug while holding BOOT, grant USB permission again, then retry.")
    }

    fun nukeBootAndAppFlash(image: PreparedFlashImage, onProgress: (String) -> Unit = {}) {
        onProgress("Nuking ESP32 boot/app flash (${image.sourceBytes / 1024} KB) so old firmware cannot start…")
        flashImage(ESP_FLASH_ADDRESS, image, cleanErase = false, label = "ESP32 boot/app nuke", onProgress = onProgress)
        Thread.sleep(700)
    }

    fun flashImage(address: Int, image: PreparedFlashImage, cleanErase: Boolean = false, label: String = "MicroPython", onProgress: (String) -> Unit = {}) {
        val blocks = image.blocks.size
        onProgress(
            if (cleanErase) "Using selected $label after full erase (${image.sourceBytes} byte image; $blocks ready flash block(s))."
            else "Using selected $label (${image.sourceBytes} byte image; $blocks ready flash block(s))."
        )
        onProgress("Requesting flash write for $blocks prebuilt $label block(s)…")
        runCatching { command(0x02, le32(image.eraseSize) + le32(blocks) + le32(ESP_FLASH_BLOCK) + le32(address), 0, 10000) }
            .onFailure { error -> throw IllegalStateException("flash begin failed: ${error.message.orEmpty()}") }
        onProgress("Flash write accepted; sending $label blocks…")
        image.blocks.forEach { block ->
            if (block.sequence == 0 || block.sequence == blocks - 1 || block.sequence % 32 == 31) {
                onProgress("Writing $label block ${block.sequence + 1}/$blocks…")
            }
            val payload = le32(ESP_FLASH_BLOCK) + le32(block.sequence) + le32(0) + le32(0) + block.bytes
            runCatching { command(0x03, payload, block.checksum, 5000) }
                .onFailure { error -> throw IllegalStateException("block ${block.sequence}/$blocks failed: ${error.message.orEmpty()}") }
        }
    }

    fun finish(reboot: Boolean) {
        command(0x04, le32(if (reboot) 0 else 1), 0, 1500)
    }

    private fun command(op: Int, data: ByteArray, checksum: Int, timeoutMs: Int): ByteArray {
        val packet = ByteArray(8 + data.size)
        packet[0] = 0x00
        packet[1] = op.toByte()
        packet[2] = (data.size and 0xff).toByte()
        packet[3] = ((data.size shr 8) and 0xff).toByte()
        le32(checksum).copyInto(packet, 4)
        data.copyInto(packet, 8)
        serial.write(slipEncode(packet))
        val deadline = System.currentTimeMillis() + timeoutMs
        var lastOp = "none"
        var lastFrameSize = 0
        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(80).toInt()
            val response = serial.readSlip(remaining)
            lastFrameSize = response.size
            if (response.size < 8) continue
            val direction = response[0].toInt() and 0xff
            val responseOp = response[1].toInt() and 0xff
            lastOp = "0x${responseOp.toString(16)}"
            if (direction == 0x01 && responseOp == op) {
                checkStatus(op, response)
                return response
            }
            // The ROM often has one or more SYNC replies buffered. Ignore
            // stale frames and keep waiting for the response to this command.
        }
        throw IllegalStateException("Unexpected ESP response for op 0x${op.toString(16)}; last=$lastOp size=$lastFrameSize")
    }

    private fun checkStatus(op: Int, response: ByteArray) {
        val dataSize = response.size - 8
        if (dataSize < ESP_STATUS_BYTES) {
            throw IllegalStateException("ESP response for op 0x${op.toString(16)} did not include status bytes")
        }
        val status = response[response.size - ESP_STATUS_BYTES].toInt() and 0xff
        val reason = response[response.size - ESP_STATUS_BYTES + 1].toInt() and 0xff
        if (status != 0) {
            throw IllegalStateException("ESP command op 0x${op.toString(16)} failed with status=$status reason=$reason")
        }
    }

}

private fun espChecksum(data: ByteArray): Int = data.fold(0xEF) { acc, byte -> acc xor (byte.toInt() and 0xff) }

private fun le32(value: Int): ByteArray = byteArrayOf(
    (value and 0xff).toByte(),
    ((value shr 8) and 0xff).toByte(),
    ((value shr 16) and 0xff).toByte(),
    ((value shr 24) and 0xff).toByte()
)

private fun slipEncode(packet: ByteArray): ByteArray {
    val out = ArrayList<Byte>(packet.size + 8)
    out.add(0xC0.toByte())
    packet.forEach { b ->
        when (b.toInt() and 0xff) {
            0xC0 -> { out.add(0xDB.toByte()); out.add(0xDC.toByte()) }
            0xDB -> { out.add(0xDB.toByte()); out.add(0xDD.toByte()) }
            else -> out.add(b)
        }
    }
    out.add(0xC0.toByte())
    return out.toByteArray()
}

private fun slipDecode(packet: ByteArray): ByteArray {
    val out = ArrayList<Byte>(packet.size)
    var esc = false
    packet.forEach { b ->
        val v = b.toInt() and 0xff
        if (esc) {
            out.add((if (v == 0xDC) 0xC0 else if (v == 0xDD) 0xDB else v).toByte())
            esc = false
        } else if (v == 0xDB) esc = true else out.add(b)
    }
    return out.toByteArray()
}

private fun openUsbSerial(manager: UsbManager, device: UsbDevice): UsbSerialLink? {
    val connection = manager.openDevice(device) ?: return null
    val interfaces = (0 until device.interfaceCount).map { device.getInterface(it) }
    val candidates = (0 until device.interfaceCount).map { device.getInterface(it) }
        .sortedWith(compareByDescending<UsbInterface> { it.interfaceClass == UsbConstants.USB_CLASS_CDC_DATA }
            .thenByDescending { it.interfaceClass == UsbConstants.USB_CLASS_COMM }
            .thenBy { it.id })
    for (intf in candidates) {
        var bulkIn: UsbEndpoint? = null
        var bulkOut: UsbEndpoint? = null
        for (e in 0 until intf.endpointCount) {
            val endpoint = intf.getEndpoint(e)
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_IN) bulkIn = endpoint
            if (endpoint.type == UsbConstants.USB_ENDPOINT_XFER_BULK && endpoint.direction == UsbConstants.USB_DIR_OUT) bulkOut = endpoint
        }
        if (bulkIn != null && bulkOut != null && connection.claimInterface(intf, true)) {
            val controlInterfaceId = cdcControlInterfaceId(interfaces, intf)
            runCatching { configureCdcLine(connection, controlInterfaceId) }
            return UsbSerialLink(connection, intf, bulkIn, bulkOut, controlInterfaceId)
        }
    }
    connection.close()
    return null
}

private fun cdcControlInterfaceId(interfaces: List<UsbInterface>, dataInterface: UsbInterface): Int {
    // For USB CDC ACM devices the bulk endpoints are usually on a DATA
    // interface, while SET_LINE_CODING and SET_CONTROL_LINE_STATE must be sent
    // to the paired COMM/control interface. On ESP32-C3 native USB this is the
    // difference between a silent port and a working MicroPython REPL.
    val precedingControl = interfaces
        .filter { it.interfaceClass == UsbConstants.USB_CLASS_COMM && it.id <= dataInterface.id }
        .maxByOrNull { it.id }
    if (precedingControl != null) return precedingControl.id
    return interfaces.firstOrNull { it.interfaceClass == UsbConstants.USB_CLASS_COMM }?.id ?: dataInterface.id
}

private fun configureCdcLine(connection: UsbDeviceConnection, interfaceId: Int) {
    val lineCoding = byteArrayOf(0x00, 0xC2.toByte(), 0x01, 0x00, 0x00, 0x00, 0x08) // 115200 8N1.
    connection.controlTransfer(0x21, 0x20, 0, interfaceId, lineCoding, lineCoding.size, 1000)
    connection.controlTransfer(0x21, 0x22, 0x01, interfaceId, null, 0, 1000)
}

private interface PythonSerialLink : AutoCloseable {
    fun write(bytes: ByteArray)
    fun readAvailable(timeoutMs: Int): String
    fun drain()
    fun setTerminalReady()
    fun interruptRunningScript()
    fun softResetMicroPython()
    fun breakIntoRepl()
}

private class MicroPythonSerialLink(
    private val connection: UsbDeviceConnection,
    private val port: UsbSerialPort
) : PythonSerialLink {
    override fun write(bytes: ByteArray) {
        port.write(bytes, 1000)
    }

    override fun readAvailable(timeoutMs: Int): String {
        val buffer = ByteArray(512)
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = runCatching { port.read(buffer, 120) }.getOrDefault(0)
            if (read > 0) out.append(buffer.copyOf(read).toString(Charsets.UTF_8))
        }
        return out.toString()
    }

    override fun drain() {
        readAvailable(250)
    }

    override fun setTerminalReady() {
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(false) }
    }

    override fun interruptRunningScript() {
        repeat(12) {
            write(byteArrayOf(3))
            Thread.sleep(100)
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(700)
        drain()
    }

    override fun softResetMicroPython() {
        setTerminalReady()
        interruptRunningScript()
        write(byteArrayOf(4))
        Thread.sleep(1800)
        interruptRunningScript()
    }

    override fun breakIntoRepl() {
        // Stop any already-running main.py. If the script is noisy during
        // startup, send several interrupts and then press Enter to surface >>>.
        interruptRunningScript()
    }

    override fun close() {
        runCatching { port.close() }
        connection.close()
    }
}

private class UsbSerialLink(
    private val connection: UsbDeviceConnection,
    private val intf: UsbInterface,
    private val input: UsbEndpoint,
    private val output: UsbEndpoint,
    private val controlInterfaceId: Int
) : PythonSerialLink {
    override fun write(bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val length = minOf(256, bytes.size - offset)
            val written = connection.bulkTransfer(output, bytes, offset, length, 1000)
            if (written <= 0) throw IllegalStateException("USB serial write failed")
            offset += written
        }
    }

    override fun readAvailable(timeoutMs: Int): String {
        val buffer = ByteArray(512)
        val out = StringBuilder()
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = connection.bulkTransfer(input, buffer, buffer.size, 80)
            if (read > 0) out.append(buffer.copyOf(read).toString(Charsets.UTF_8))
        }
        return out.toString()
    }

    fun readSlip(timeoutMs: Int): ByteArray {
        val buffer = ByteArray(256)
        val packet = ArrayList<Byte>()
        var started = false
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val read = connection.bulkTransfer(input, buffer, buffer.size, 80)
            if (read <= 0) continue
            for (i in 0 until read) {
                val v = buffer[i].toInt() and 0xff
                if (v == 0xC0) {
                    if (started && packet.isNotEmpty()) return slipDecode(packet.toByteArray())
                    started = true
                    packet.clear()
                } else if (started) packet.add(buffer[i])
            }
        }
        throw IllegalStateException("Timed out waiting for ESP32 bootloader response")
    }

    override fun drain() { readAvailable(250) }

    override fun setTerminalReady() {
        connection.controlTransfer(0x21, 0x22, 0x01, controlInterfaceId, null, 0, 500)
    }

    override fun interruptRunningScript() {
        repeat(12) {
            write(byteArrayOf(3))
            Thread.sleep(100)
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(700)
        drain()
    }

    override fun softResetMicroPython() {
        setTerminalReady()
        interruptRunningScript()
        write(byteArrayOf(4))
        Thread.sleep(1800)
        interruptRunningScript()
    }

    override fun breakIntoRepl() {
        interruptRunningScript()
    }

    fun setDtrRts(dtr: Boolean, rts: Boolean) {
        val value = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
        connection.controlTransfer(0x21, 0x22, value, controlInterfaceId, null, 0, 500)
    }

    override fun close() { runCatching { connection.releaseInterface(intf) }; connection.close() }
}
