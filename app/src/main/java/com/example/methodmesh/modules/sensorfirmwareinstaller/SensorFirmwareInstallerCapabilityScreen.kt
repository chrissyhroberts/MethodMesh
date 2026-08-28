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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlin.math.ceil

private const val ACTION_USB_PERMISSION = "com.example.methodmesh.USB_SENSOR_FIRMWARE_PERMISSION"
private const val MAIN_PY_ASSET = "firmware/esp32c3_aht20_ble/main.py"
private val SENSOR_DRIVER_ASSETS = listOf(
    "firmware/esp32c3_aht20_ble/sensor_drivers/__init__.py" to "sensor_drivers/__init__.py",
    "firmware/esp32c3_aht20_ble/sensor_drivers/aht20.py" to "sensor_drivers/aht20.py",
    "firmware/esp32c3_aht20_ble/sensor_drivers/ld2410c.py" to "sensor_drivers/ld2410c.py"
)
private const val MICROPYTHON_BIN_ASSET = "firmware/esp32c3_aht20_ble/ESP32_GENERIC_C3-20260406-v1.28.0.bin"
private const val ESP_FLASH_ADDRESS = 0
private const val ESP_FLASH_BLOCK = 0x1000
private const val ESP_CLEAN_FLASH_BYTES = 4 * 1024 * 1024
private const val ESP_STATUS_BYTES = 2

object SensorFirmwareInstallerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SensorFirmwareInstallerMethod.ID
    override val title = "Install ESP32 sensor firmware"
    override val description = "Install MicroPython and bundled MethodMesh sensor firmware to an ESP32-C3 over USB."

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val usbManager = androidContext.getSystemService(Context.USB_SERVICE) as UsbManager
        val scope = remember { CoroutineScope(Dispatchers.Main) }
        var devices by remember { mutableStateOf(usbDevices(usbManager)) }
        var selected by remember { mutableStateOf<UsbDevice?>(null) }
        var status by rememberSaveable { mutableStateOf("Connect an ESP32-C3 by USB/OTG, then refresh devices.") }
        var log by rememberSaveable { mutableStateOf("") }
        var installing by rememberSaveable { mutableStateOf(false) }
        var destructiveConfirmed by rememberSaveable { mutableStateOf(false) }
        var pendingAction by rememberSaveable { mutableStateOf("main_py") }
        var permissionCheckTick by rememberSaveable { mutableStateOf(0) }
        var permissionCheckDevice by remember { mutableStateOf<UsbDevice?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val mainPy = remember { androidContext.assets.open(MAIN_PY_ASSET).bufferedReader().use { it.readText() } }
        val driverFiles = remember {
            SENSOR_DRIVER_ASSETS.map { (assetPath, targetPath) ->
                targetPath to androidContext.assets.open(assetPath).bufferedReader().use { it.readText() }
            }
        }
        val microPythonBin = remember { androidContext.assets.open(MICROPYTHON_BIN_ASSET).use { it.readBytes() } }

        fun record(outcome: SensorFirmwareInstallOutcome) {
            val request = As100SensorFirmwareInstallerMethod.request(As100SensorFirmwareInstallerMethod.ID, emptyMap(), emptyList(), emptyList())
            result = As100SensorFirmwareInstallerMethod.result(request, outcome, context.request.invocationContext)
        }

        fun append(message: String) { log = (log + "\n" + message).trim() }

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

        fun installMainPy(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                status = "USB permission needed for this MicroPython device. Android may see it as new after flashing."
                requestPermission(device, "main_py")
                return
            }
            installing = true
            result = null
            status = "Installing bundled MethodMesh main.py…"
            log = "Opening MicroPython USB REPL on ${device.deviceName}"
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeout(60000L) {
                            installMainPyFirmware(usbManager, device, mainPy, driverFiles) { progress ->
                                scope.launch { status = progress }
                            }
                        }
                    }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), error.message ?: error::class.java.simpleName) }
                }
                installing = false
                status = if (outcome.success) "MethodMesh main.py installed. Reset the board, then run BLE sensor provisioning." else "MethodMesh main.py install failed: ${outcome.message}"
                append(if (outcome.success) outcome.message else "FAILED: ${outcome.message}")
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "installed" else "failed",
                        firmwareName = "esp32c3_sensor_node/main.py",
                        firmwareVersion = "methodmesh-sensor-0.1.1",
                        firmwareBytes = (mainPy.toByteArray(Charsets.UTF_8).size + driverFiles.sumOf { it.second.toByteArray(Charsets.UTF_8).size }).toString(),
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
            result = null
            status = if (cleanErase) "Clean-installing MicroPython to ESP32-C3…" else "Flashing MicroPython to ESP32-C3…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nIf sync fails: hold BOOT, tap RESET, release BOOT, then try again." +
                if (cleanErase) "\nClean install will erase the normal 4 MB flash area before writing MicroPython." else ""
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { flashMicroPythonImage(usbManager, device, microPythonBin, cleanErase = cleanErase) }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}") }
                }
                installing = false
                status = if (outcome.success) {
                    if (cleanErase) "Clean MicroPython install complete. Reset or unplug/replug the board, refresh USB devices, then install MethodMesh main.py."
                    else "MicroPython installed. Reset or unplug/replug the board, refresh USB devices, then install MethodMesh main.py."
                } else "MicroPython flash failed."
                append(outcome.message)
                if (outcome.success) {
                    devices = usbDevices(usbManager)
                    selected = null
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

        fun installCompleteFirmware(device: UsbDevice) {
            if (!usbManager.hasPermission(device)) {
                requestPermission(device, "complete_firmware")
                return
            }
            if (!device.isLikelyEsp32Target()) {
                status = "Selected USB device is not a recognised Espressif/USB-serial target."
                return
            }
            installing = true
            result = null
            status = "Step 1: clean-installing MicroPython…"
            log = "Opening ESP32 ROM bootloader on ${device.deviceName}\nThis step writes MicroPython only. After it succeeds, reset or unplug/replug the board, refresh USB devices, then press Upload MethodMesh main.py."
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { flashMicroPythonImage(usbManager, device, microPythonBin, cleanErase = true) }
                        .getOrElse { error -> FirmwareInstallResult(false, device.usbLabel(), "${error::class.java.simpleName}: ${error.message.orEmpty()}") }
                }
                installing = false
                status = if (outcome.success) {
                    "Step 1 complete. Reset or unplug/replug the board normally, refresh USB devices, then press Upload MethodMesh main.py."
                } else {
                    "MicroPython install failed."
                }
                append(outcome.message)
                record(
                    SensorFirmwareInstallOutcome(
                        status = if (outcome.success) "installed" else "failed",
                        firmwareName = "ESP32_GENERIC_C3 MicroPython",
                        firmwareVersion = "MicroPython v1.28.0",
                        firmwareBytes = microPythonBin.size.toString(),
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
                    "micropython" -> flashMicroPython(device)
                    "clean_micropython" -> flashMicroPython(device, cleanErase = true)
                    "complete_firmware" -> installCompleteFirmware(device)
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
                            "micropython" -> flashMicroPython(device)
                            "clean_micropython" -> flashMicroPython(device, cleanErase = true)
                            "complete_firmware" -> installCompleteFirmware(device)
                            else -> installMainPy(device)
                        }
                    } else {
                        installing = false
                        status = if (pendingAction == "main_py") "USB permission denied for the MicroPython device. If the board just rebooted, unplug/replug it, refresh USB devices, select it again, and retry main.py install." else "USB permission denied."
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
                status = "Device list refreshed. Select the ESP32-C3 board before continuing."
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Install the MethodMesh sensor stack to an ESP32-C3 from the phone.", style = MaterialTheme.typography.bodyMedium)
            Text("Step 1 flashes MicroPython in ESP bootloader mode. Step 2 uploads the bundled MethodMesh main.py after the board has rebooted normally.", style = MaterialTheme.typography.bodySmall)
            Text("For blank or wiped boards: hold BOOT, tap RESET, release BOOT, then press Step 1. After Step 1 succeeds, reset or unplug/replug without holding BOOT, refresh USB devices, then press Step 2.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            Text("Bundled firmware", fontWeight = FontWeight.SemiBold)
            Text("$MICROPYTHON_BIN_ASSET · ${microPythonBin.size} bytes", style = MaterialTheme.typography.bodySmall)
            Text("$MAIN_PY_ASSET · ${mainPy.toByteArray(Charsets.UTF_8).size} bytes", style = MaterialTheme.typography.bodySmall)
            Text("${driverFiles.size} sensor driver file(s) · ${driverFiles.sumOf { it.second.toByteArray(Charsets.UTF_8).size }} bytes", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    devices = usbDevices(usbManager)
                    selected = null
                    status = "Found ${devices.size} USB device(s). Select the ESP32-C3 board below."
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Refresh USB devices") }
            Spacer(Modifier.height(8.dp))
            if (devices.isEmpty()) {
                Text("No USB devices found. Connect the ESP32-C3 by USB/OTG, then refresh.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Select USB device", fontWeight = FontWeight.SemiBold)
            }
            devices.forEach { device ->
                OutlinedButton(onClick = { selected = device; status = "Selected ${device.usbLabel()} ${device.guardLabel()}" }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (selected == device) "Selected: ${device.usbLabel()}" else "Use ${device.usbLabel()}")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Usual sequence: choose board → confirm overwrite → Step 1. After flashing succeeds, reset or unplug/replug normally, refresh, choose the board again, then Step 2.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row {
                Checkbox(checked = destructiveConfirmed, onCheckedChange = { destructiveConfirmed = it })
                Text("I understand installation erases and overwrites the selected ESP32-C3.", style = MaterialTheme.typography.bodySmall)
            }
            Button(onClick = { selected?.let { installCompleteFirmware(it) } ?: run { status = "No USB device selected." } }, modifier = Modifier.fillMaxWidth(), enabled = !installing && destructiveConfirmed && selected?.isLikelyEsp32Target() == true) {
                Text(if (installing) "Working…" else "Step 1: install MicroPython")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { selected?.let { installMainPy(it) } ?: run { status = "No USB device selected." } }, modifier = Modifier.fillMaxWidth(), enabled = !installing) {
                Text(if (installing) "Working…" else "Step 2: upload MethodMesh main.py")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { selected?.let { flashMicroPython(it) } ?: run { status = "No USB device selected." } }, modifier = Modifier.fillMaxWidth(), enabled = !installing && destructiveConfirmed && selected?.isLikelyEsp32Target() == true) {
                Text(if (installing) "Working…" else "Advanced: install MicroPython only")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { selected?.let { flashMicroPython(it, cleanErase = true) } ?: run { status = "No USB device selected." } }, modifier = Modifier.fillMaxWidth(), enabled = !installing && destructiveConfirmed && selected?.isLikelyEsp32Target() == true) {
                Text(if (installing) "Working…" else "Advanced: clean install MicroPython only")
            }
            Text(status, style = MaterialTheme.typography.bodySmall)
            if (log.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Installer log", fontWeight = FontWeight.SemiBold)
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100SensorFirmwareInstallerMethod.ID,
                examples = listOf(IntentExample("Install bundled firmware", "Open the USB firmware installer.", "com.example.methodmesh.EXECUTE_METHOD(method_id='sensor_firmware_installer',return_mode='flat')"))
            )
        }
    }
}

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

private fun installMainPyFirmware(
    usbManager: UsbManager,
    device: UsbDevice,
    firmware: String,
    driverFiles: List<Pair<String, String>> = emptyList(),
    onProgress: (String) -> Unit = {}
): FirmwareInstallResult {
    val serial = openMicroPythonSerial(usbManager, device)
        ?: return FirmwareInstallResult(false, device.usbLabel(), "No Android USB serial driver could open this device for the MicroPython REPL.")
    serial.use {
        runCatching {
            // Native ESP32-C3 USB CDC consoles often stay quiet until the host
            // asserts DTR. Do this once, without toggling RTS, so we do not
            // intentionally kick the board back into download/reset mode.
            it.setTerminalReady()
            Thread.sleep(250)
            // For main.py upload, do not toggle DTR/RTS. On many ESP32-C3
            // boards those lines are wired to reset/boot and can put the chip
            // back into ROM download mode. Just interrupt any running script
            // and probe the normal MicroPython REPL.
            it.write(byteArrayOf(3, 3, 13, 10))
            Thread.sleep(700)
        }.onFailure { error -> throw IllegalStateException("MicroPython interrupt failed: ${error.message.orEmpty()}") }
        runCatching { friendlyReplProbe(it) }
            .recoverCatching { firstError ->
                // If the board is still in download mode or the console has not
                // been woken by Android's USB stack, a gentle normal-mode reset
                // sometimes brings MicroPython up. This intentionally avoids the
                // BOOT/download-mode line state used by flashing.
                it.resetToNormalRun()
                friendlyReplProbe(it)
            }
            .onFailure { error ->
                throw IllegalStateException(
                    "MicroPython friendly REPL probe failed: ${error.message.orEmpty()} " +
                        "If the response is blank, tap RESET once without holding BOOT, unplug/replug the board, refresh USB devices, and retry main.py install."
                )
            }
        runCatching { rawExec(it, "print('MethodMesh MicroPython link ready')", "raw probe") }
            .onFailure { error -> throw IllegalStateException("MicroPython raw REPL probe failed: ${error.message.orEmpty()}") }

        if (driverFiles.isNotEmpty()) {
            onProgress("Preparing sensor driver folder…")
            runCatching { rawExec(it, "import os\ntry:\n    os.mkdir('sensor_drivers')\nexcept OSError:\n    pass", "create sensor driver folder") }
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
        uploadPythonFile(
            serial = it,
            path = "main.py",
            contents = firmware,
            label = "MethodMesh main.py",
            onProgress = onProgress
        )
        onProgress("Resetting board after main.py upload…")
        runCatching { rawExec(it, "import machine; machine.reset()", "reset", requirePrompt = false) }
    }
    return FirmwareInstallResult(true, device.usbLabel(), "Bundled main.py was copied to the board and reset was requested.")
}

private fun uploadPythonFile(
    serial: PythonSerialLink,
    path: String,
    contents: String,
    label: String,
    onProgress: (String) -> Unit
) {
    onProgress("Preparing $label…")
    runCatching { rawExec(serial, "f=open('$path','wb');f.write(b'');f.close()", "create $path") }
        .onFailure { error -> throw IllegalStateException("Could not create $path: ${error.message.orEmpty()}") }
    val chunks = contents.toByteArray(Charsets.UTF_8).toList().chunked(512)
    chunks.forEachIndexed { index, chunk ->
        onProgress("Writing $label chunk ${index + 1}/${chunks.size}…")
        val literal = chunk.toByteArray().joinToString("", prefix = "b'", postfix = "'") { "\\x%02x".format(it) }
        runCatching { rawExec(serial, "f=open('$path','ab');f.write($literal);f.close()", "write $path chunk $index") }
            .onFailure { error -> throw IllegalStateException("$path chunk $index failed: ${error.message.orEmpty()}") }
        Thread.sleep(25)
    }
}

private fun friendlyReplProbe(serial: PythonSerialLink): String {
    serial.drain()
    serial.setTerminalReady()
    serial.breakIntoRepl()
    serial.write("print('MethodMesh friendly REPL ready')\r\n".toByteArray(Charsets.UTF_8))
    val response = serial.readAvailable(4500)
    if (!response.contains("MethodMesh friendly REPL ready")) {
        throw IllegalStateException(
            "No friendly REPL echo/print response. Response was: ${response.ifBlank { "<blank>" }.take(220)}"
        )
    }
    return response
}

private fun rawExec(serial: PythonSerialLink, code: String, label: String, requirePrompt: Boolean = true): String {
    serial.write(byteArrayOf(3, 3, 13, 10))
    Thread.sleep(200)
    serial.drain()
    serial.write(byteArrayOf(1))
    Thread.sleep(200)
    serial.drain()
    serial.write(code.toByteArray(Charsets.UTF_8))
    serial.write(byteArrayOf(4))
    val response = serial.readAvailable(2500)
    if (requirePrompt && response.isBlank()) throw IllegalStateException("No response from MicroPython during $label")
    return response
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

private fun flashMicroPythonImage(usbManager: UsbManager, device: UsbDevice, image: ByteArray, cleanErase: Boolean = false): FirmwareInstallResult {
    if (!device.isLikelyEsp32Target()) return FirmwareInstallResult(false, device.usbLabel(), "Refusing to flash an unrecognised USB device.")
    val serial = openUsbSerial(usbManager, device) ?: return FirmwareInstallResult(false, device.usbLabel(), "USB serial open failed: no bulk USB serial interface was found.")
    serial.use {
        runCatching {
            it.setDtrRts(dtr = false, rts = true)
            Thread.sleep(100)
            it.setDtrRts(dtr = true, rts = false)
            Thread.sleep(100)
            it.setDtrRts(dtr = false, rts = false)
            Thread.sleep(700)
            it.drain()
        }.onFailure { error -> throw IllegalStateException("Bootloader reset sequence failed: ${error.message.orEmpty()}") }
        val rom = Esp32RomBootloader(it)
        runCatching { rom.sync() }.onFailure { error -> throw IllegalStateException("Bootloader sync failed: ${error.message.orEmpty()}") }
        runCatching { rom.flashImage(address = ESP_FLASH_ADDRESS, image = image, cleanEraseBytes = if (cleanErase) ESP_CLEAN_FLASH_BYTES else null) }
            .onFailure { error -> throw IllegalStateException("Flash write failed: ${error.message.orEmpty()}") }
        runCatching { rom.finish(reboot = true) }.onFailure { error -> throw IllegalStateException("Flash finish failed: ${error.message.orEmpty()}") }
    }
    return FirmwareInstallResult(
        true,
        device.usbLabel(),
        if (cleanErase) "MicroPython v1.28.0 was clean-installed after erasing the normal 4 MB flash area. Reset the board, then install MethodMesh main.py."
        else "MicroPython v1.28.0 was written at flash address 0. Reset the board, then install MethodMesh main.py."
    )
}

private class Esp32RomBootloader(private val serial: UsbSerialLink) {
    fun sync() {
        val syncPayload = byteArrayOf(0x07, 0x07, 0x12, 0x20) + ByteArray(32) { 0x55.toByte() }
        var ok = false
        repeat(16) {
            runCatching { command(0x08, syncPayload, 0, 1500) }.onSuccess { ok = true }
            if (ok) return@repeat
            Thread.sleep(250)
        }
        if (!ok) throw IllegalStateException("ESP32 ROM bootloader did not respond. For a blank/wiped board, hold BOOT, tap RESET, release BOOT, then press Install complete MethodMesh sensor firmware again. If it still times out, unplug/replug while holding BOOT, grant USB permission again, then retry.")
    }

    fun flashImage(address: Int, image: ByteArray, cleanEraseBytes: Int? = null) {
        val blocks = ceil(image.size / ESP_FLASH_BLOCK.toDouble()).toInt()
        val eraseSize = maxOf(blocks * ESP_FLASH_BLOCK, cleanEraseBytes ?: 0)
        runCatching { command(0x02, le32(eraseSize) + le32(blocks) + le32(ESP_FLASH_BLOCK) + le32(address), 0, if (cleanEraseBytes != null) 30000 else 5000) }
            .onFailure { error -> throw IllegalStateException("flash begin failed: ${error.message.orEmpty()}") }
        for (seq in 0 until blocks) {
            val start = seq * ESP_FLASH_BLOCK
            val block = ByteArray(ESP_FLASH_BLOCK) { 0xFF.toByte() }
            val length = minOf(ESP_FLASH_BLOCK, image.size - start)
            image.copyInto(block, 0, start, start + length)
            val payload = le32(ESP_FLASH_BLOCK) + le32(seq) + le32(0) + le32(0) + block
            runCatching { command(0x03, payload, checksum(block), 5000) }
                .onFailure { error -> throw IllegalStateException("block $seq/$blocks failed: ${error.message.orEmpty()}") }
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

    private fun checksum(data: ByteArray): Int = data.fold(0xEF) { acc, byte -> acc xor (byte.toInt() and 0xff) }
}

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
    fun resetToNormalRun()
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

    override fun breakIntoRepl() {
        // Stop any already-running main.py. If the script is noisy during
        // startup, send several interrupts and then press Enter to surface >>>.
        repeat(8) {
            write(byteArrayOf(3))
            Thread.sleep(120)
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(500)
        drain()
    }

    override fun resetToNormalRun() {
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(true) }
        Thread.sleep(150)
        runCatching { port.setDTR(true) }
        runCatching { port.setRTS(false) }
        setTerminalReady()
        // Interrupt during boot so MicroPython does not immediately run the old
        // main.py again. This is the normal recovery path when replacing a
        // device that already has a long-running script installed.
        repeat(24) {
            Thread.sleep(120)
            runCatching { write(byteArrayOf(3)) }
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(500)
        drain()
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

    override fun breakIntoRepl() {
        repeat(8) {
            write(byteArrayOf(3))
            Thread.sleep(120)
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(500)
        drain()
    }

    override fun resetToNormalRun() {
        // Native ESP32-C3 USB line polarity varies by driver stack. On the
        // test ESP32-C3, DTR=false + RTS pulse holds the chip in ROM download
        // mode. DTR=true keeps BOOT deasserted while RTS pulses reset/EN.
        setDtrRts(dtr = true, rts = true)
        Thread.sleep(150)
        setDtrRts(dtr = true, rts = false)
        setTerminalReady()
        repeat(24) {
            Thread.sleep(120)
            runCatching { write(byteArrayOf(3)) }
        }
        write(byteArrayOf(13, 10))
        Thread.sleep(500)
        drain()
    }

    fun setDtrRts(dtr: Boolean, rts: Boolean) {
        val value = (if (dtr) 0x01 else 0x00) or (if (rts) 0x02 else 0x00)
        connection.controlTransfer(0x21, 0x22, value, controlInterfaceId, null, 0, 500)
    }

    override fun close() { runCatching { connection.releaseInterface(intf) }; connection.close() }
}
