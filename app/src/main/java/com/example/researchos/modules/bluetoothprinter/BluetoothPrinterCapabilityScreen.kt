package com.example.researchos.modules.bluetoothprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Matrix
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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

private const val QUTIE_SERVICE = "0000ff00-0000-1000-8000-00805f9b34fb"
private const val QUTIE_WRITE = "0000ff02-0000-1000-8000-00805f9b34fb"
private const val QUTIE_NOTIFY = "0000ff01-0000-1000-8000-00805f9b34fb"
private const val CLIENT_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

object BluetoothPrinterCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100BluetoothPrinterMethod.ID
    override val title = "Bluetooth printer"
    override val description = "Send a text or raw thermal payload to a paired Bluetooth printer."

    @SuppressLint("MissingPermission")
    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val manager = androidContext.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        val paired = remember { mutableStateListOf<BluetoothDevice>() }
        val supplied = remember(context.action.settings, context.request.settings) { context.request.settings + context.action.settings }
        var selectedAddress by rememberSaveable { mutableStateOf(supplied[BluetoothPrinterFields.DEVICE_ADDRESS].orEmpty()) }
        var deviceName by rememberSaveable { mutableStateOf("") }
        var serviceUuid by rememberSaveable { mutableStateOf(QUTIE_SERVICE) }
        var writeUuid by rememberSaveable { mutableStateOf(QUTIE_WRITE) }
        var payload by rememberSaveable { mutableStateOf("ResearchOS test label\n") }
        var format by rememberSaveable { mutableStateOf("text") }
        var fontSize by rememberSaveable { mutableStateOf(supplied[BluetoothPrinterFields.FONT_SIZE] ?: "22") }
        var lineSpacing by rememberSaveable { mutableStateOf(supplied[BluetoothPrinterFields.LINE_SPACING] ?: "32") }
        var labelHeight by rememberSaveable { mutableStateOf(supplied[BluetoothPrinterFields.LABEL_HEIGHT] ?: "32") }
        var status by rememberSaveable { mutableStateOf("Ready.") }
        var connected by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var gatt by remember { mutableStateOf<BluetoothGatt?>(null) }
        var writeCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var notifyCharacteristic by remember { mutableStateOf<BluetoothGattCharacteristic?>(null) }
        var negotiatedMtu by remember { mutableStateOf(23) }
        val pendingWrite = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
        val handler = remember { Handler(Looper.getMainLooper()) }
        val scope = rememberCoroutineScope()
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshPaired(adapter, paired, androidContext, statusSetter = { status = it }) }

        LaunchedEffect(selectedAddress, serviceUuid, writeUuid, payload, format, fontSize, lineSpacing, labelHeight) {
            context.onSettingsChanged(
                mapOf(
                    BluetoothPrinterFields.DEVICE_ADDRESS to selectedAddress,
                    "printer_service_uuid" to serviceUuid,
                    BluetoothPrinterFields.WRITE_UUID to writeUuid,
                    BluetoothPrinterFields.PAYLOAD to payload,
                    BluetoothPrinterFields.FORMAT to format,
                    BluetoothPrinterFields.FONT_SIZE to fontSize,
                    BluetoothPrinterFields.LINE_SPACING to lineSpacing,
                    BluetoothPrinterFields.LABEL_HEIGHT to labelHeight
                )
            )
        }

        fun permissions(): Array<String> = if (android.os.Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        fun hasPermissions() = permissions().all { ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED }
        fun refresh() {
            if (!hasPermissions()) { permissionLauncher.launch(permissions()); return }
            refreshPaired(adapter, paired, androidContext) { status = it }
            if (selectedAddress.isBlank() && paired.size == 1) {
                selectedAddress = paired.first().address
                deviceName = paired.first().name.orEmpty()
                status = "Auto-selected paired Qutie printer."
            }
            status = "Paired devices refreshed."
        }
        fun connect() {
            val device = paired.firstOrNull { it.address == selectedAddress }
            if (device == null) { status = "Select a paired printer first."; return }
            if (!hasPermissions()) { permissionLauncher.launch(permissions()); return }
            gatt?.close(); connected = false; status = "Connecting to ${device.name ?: device.address}…"
            gatt = device.connectGatt(androidContext, false, object : BluetoothGattCallback() {
                override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                    handler.post { connected = newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED; status = if (connected) { g.requestMtu(517); g.discoverServices(); "Connected; negotiating printer transport…" } else "Disconnected (status=$statusCode)" }
                }
                override fun onMtuChanged(g: BluetoothGatt, mtu: Int, statusCode: Int) {
                    if (statusCode == BluetoothGatt.GATT_SUCCESS) handler.post { negotiatedMtu = mtu; status = "Printer transport ready (MTU $mtu)." }
                }
                override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                    handler.post {
                        val service = g.services.firstOrNull { it.uuid.toString().equals(serviceUuid.trim(), true) }
                        writeCharacteristic = service?.characteristics?.firstOrNull { it.uuid.toString().equals(writeUuid.trim(), true) && it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0 }
                        notifyCharacteristic = service?.characteristics?.firstOrNull { it.uuid.toString().equals(QUTIE_NOTIFY, true) && it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
                        val notifyReady = notifyCharacteristic?.let { n ->
                            g.setCharacteristicNotification(n, true)
                            n.getDescriptor(UUID.fromString(CLIENT_CONFIG))?.let { d ->
                                d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                g.writeDescriptor(d)
                            }
                            true
                        } ?: false
                        status = if (writeCharacteristic != null) {
                            if (notifyReady) "Printer write endpoint ready; status notifications enabled." else "Printer write endpoint ready."
                        } else "Connected, but the configured write endpoint was not found."
                    }
                }
                override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                    val hex = value.joinToString(" ") { "%02X".format(it) }
                    handler.post { status = "Printer status: $hex" }
                }
                override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
                    if (statusCode != BluetoothGatt.GATT_SUCCESS) handler.post { status = "Could not enable printer status notifications (GATT $statusCode)." }
                }
                override fun onCharacteristicWrite(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
                    pendingWrite.getAndSet(null)?.complete(statusCode == BluetoothGatt.GATT_SUCCESS)
                    if (statusCode != BluetoothGatt.GATT_SUCCESS) handler.post { status = "Printer write failed (GATT status $statusCode)." }
                }
            })
        }
        fun print() {
            val g = gatt
            val characteristic = writeCharacteristic
            if (!connected || g == null || characteristic == null) { status = "Connect to the printer first."; return }
            val bytes = if (format == "hex") parseHex(payload) else qutieTextLabel(payload, fontSize.toIntOrNull() ?: 22, lineSpacing.toIntOrNull() ?: 32, labelHeight.toIntOrNull() ?: 96)
            if (bytes.isEmpty()) { status = "There is no printable payload."; return }
            // Use the negotiated ATT payload size. Qutie treats each BLE
            // write as a print stream fragment, so tiny writes produce the
            // audible chick-chick behaviour and visible gaps.
            val maxPayload = (negotiatedMtu - 3).coerceAtLeast(20)
            val chunks = bytes.toList().chunked(maxPayload).map { it.toByteArray() }
            status = "Sending ${bytes.size} bytes to the printer…"
            scope.launch {
                var accepted = true
                for (chunk in chunks) {
                    // Qutie exposes acknowledged writes. Use them whenever
                    // available: a successful Boolean only means Android
                    // queued a packet, whereas the callback confirms that
                    // the printer accepted it. This prevents partial labels
                    // and avoids reporting success after a stalled transfer.
                    val acknowledged = characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                    characteristic.writeType = if (acknowledged) BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    characteristic.value = chunk
                    val confirmation = if (acknowledged) CompletableDeferred<Boolean>().also { pendingWrite.set(it) } else null
                    val queued = g.writeCharacteristic(characteristic)
                    val confirmed = if (!queued) false else confirmation?.let { withTimeoutOrNull(3000) { it.await() } ?: false } ?: true
                    accepted = accepted && confirmed
                    if (!accepted) break
                    delay(if (chunks.size == 1) 40 else 25)
                }
                status = if (accepted) "Print payload sent (${bytes.size} bytes)." else "Printer rejected the payload."
                val request = As100BluetoothPrinterMethod.request(As100BluetoothPrinterMethod.ID, emptyMap(), emptyList(), emptyList())
                val values = mapOf(
                    BluetoothPrinterFields.DEVICE_NAME to deviceName,
                    BluetoothPrinterFields.DEVICE_ADDRESS to selectedAddress,
                    BluetoothPrinterFields.SERVICE_UUID to serviceUuid,
                    BluetoothPrinterFields.WRITE_UUID to writeUuid,
                    BluetoothPrinterFields.PAYLOAD to payload,
                    BluetoothPrinterFields.FORMAT to format,
                    BluetoothPrinterFields.FONT_SIZE to fontSize,
                    BluetoothPrinterFields.LINE_SPACING to lineSpacing,
                    BluetoothPrinterFields.LABEL_HEIGHT to labelHeight,
                    BluetoothPrinterFields.PROFILE to "qutie_label_v1",
                    BluetoothPrinterFields.STATUS to if (accepted) "succeeded" else "failed",
                    BluetoothPrinterFields.BYTES_SENT to bytes.size.toString()
                )
                result = As100BluetoothPrinterMethod.result(request, values, context.request.invocationContext)
            }
        }
        DisposableEffect(Unit) { onDispose { runCatching { gatt?.close() }; handler.removeCallbacksAndMessages(null) } }
        LaunchedRefresh(adapter, androidContext, paired, hasPermissions(), refreshPaired = {
            refreshPaired(adapter, paired, androidContext) { status = it }
            if (selectedAddress.isBlank() && paired.size == 1) {
                selectedAddress = paired.first().address
                deviceName = paired.first().name.orEmpty()
                status = "Auto-selected paired printer."
            }
        })

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Text("Qutie label mode renders text as a 96-pixel label and sends the printer's label protocol. Raw hex is available for device-specific commands.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(::refresh, Modifier.fillMaxWidth()) { Text("Refresh paired devices") }
            paired.forEach { device ->
                OutlinedButton(onClick = { selectedAddress = device.address; deviceName = device.name.orEmpty() }, Modifier.fillMaxWidth()) {
                    Text(if (selectedAddress == device.address) "✓ ${device.name ?: "Unnamed"} (${device.address})" else "${device.name ?: "Unnamed"} (${device.address})")
                }
            }
            OutlinedTextField(serviceUuid, { serviceUuid = it }, label = { Text("Printer service UUID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(writeUuid, { writeUuid = it }, label = { Text("Write characteristic UUID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(Modifier.fillMaxWidth()) {
                Button({ format = "text"; status = "Text payload selected." }, Modifier.weight(1f)) { Text(if (format == "text") "✓ Text" else "Text") }
                Spacer(Modifier.padding(4.dp))
                Button({ format = "hex"; status = "Raw hex payload selected." }, Modifier.weight(1f)) { Text(if (format == "hex") "✓ Hex" else "Raw hex") }
            }
            OutlinedTextField(payload, { payload = it }, label = { Text(if (format == "hex") "Raw payload (hex)" else "Label text") }, modifier = Modifier.fillMaxWidth().height(120.dp))
            if (format == "text") {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(fontSize, { fontSize = it.filter(Char::isDigit) }, label = { Text("Font px") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(lineSpacing, { lineSpacing = it.filter(Char::isDigit) }, label = { Text("Line spacing px") }, modifier = Modifier.weight(1f), singleLine = true)
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(labelHeight, { labelHeight = it.filter(Char::isDigit) }, label = { Text("Label length px") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
            Text("Status: $status", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 6.dp))
            Button(::connect, Modifier.fillMaxWidth()) { Text(if (connected) "Reconnect and inspect printer" else "Connect to printer") }
            Button(::print, Modifier.fillMaxWidth()) { Text("Send print payload") }
        }
    }
}

@SuppressLint("MissingPermission")
private fun refreshPaired(adapter: android.bluetooth.BluetoothAdapter?, paired: MutableList<BluetoothDevice>, context: android.content.Context, statusSetter: (String) -> Unit) {
    if (adapter == null) { statusSetter("Bluetooth is unavailable."); return }
    paired.clear(); paired.addAll(adapter.bondedDevices.orEmpty().sortedBy { it.name.orEmpty().lowercase() })
}

@Composable
private fun LaunchedRefresh(adapter: android.bluetooth.BluetoothAdapter?, context: android.content.Context, paired: MutableList<BluetoothDevice>, permitted: Boolean, refreshPaired: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(adapter, permitted) { if (permitted) refreshPaired() }
}

private fun qutieTextLabel(text: String, fontSize: Int, lineSpacing: Int, requestedHeight: Int): ByteArray {
    // Earlier Qutie experiments produced recognisable text with the label
    // length represented before rotation. This is still experimental, but it
    // is the most useful fallback until the vendor packet stream is captured.
    val sourceWidth = 96
    val sourceHeight = requestedHeight.coerceIn(32, 512)
    val source = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(source)
    canvas.drawColor(Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = fontSize.coerceIn(8, 48).toFloat(); typeface = Typeface.DEFAULT }
    text.lines().forEachIndexed { index, line -> canvas.drawText(line, 2f, (fontSize + index * lineSpacing).toFloat(), paint) }
    // Qutie feeds the printhead 90° relative to the screen orientation.
    // Rotate the raster so text is upright on the label, rather than only
    // becoming readable when the strip is held on its edge.
    val rotated = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
    source.recycle()
    val widthBytes = (rotated.width + 7) / 8
    val height = rotated.height
    val raster = ByteArray(widthBytes * height)
    for (y in 0 until height) for (xByte in 0 until widthBytes) {
        var value = 0
        for (bit in 0 until 8) {
            val x = xByte * 8 + bit
            val pixel = if (x < rotated.width) rotated.getPixel(x, y) else Color.WHITE
            if ((Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3 < 160) value = value or (1 shl (7 - bit))
        }
        raster[y * widthBytes + xByte] = value.toByte()
    }
    rotated.recycle()
    val header = byteArrayOf(
        0x10, 0xFF.toByte(), 0x10, 0x00, 0x02,
        0x10, 0xFF.toByte(), 0x84.toByte(), 0x00,
        0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
        0x10, 0xFF.toByte(), 0xFE.toByte(), 0x01,
        0x1D, 0x76, 0x30, 0x00,
        widthBytes.toByte(), 0x00, height.toByte(), (height shr 8).toByte()
    )
    // Advance to the next label before stopping. Without this, repeated
    // prints progressively migrate because the printer's media position is
    // never re-indexed.
    return header + raster + byteArrayOf(0x1D, 0x0C, 0x10, 0xFF.toByte(), 0xFE.toByte(), 0x45)
}

private fun parseHex(text: String): ByteArray = text.split(Regex("[\\s,;]+"), limit = 0).filter { it.isNotBlank() }.mapNotNull { token -> token.toIntOrNull(16)?.takeIf { token.length <= 2 }?.toByte() }.toByteArray()
