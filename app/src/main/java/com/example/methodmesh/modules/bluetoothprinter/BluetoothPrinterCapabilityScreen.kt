package com.example.methodmesh.modules.bluetoothprinter

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.launch

/**
 * Complete operator UI for the Qutie-family printer.
 *
 * Everything printer-specific lives in this module. The screen consumes and
 * emits only ordinary capability settings through CapabilityScreenContext.
 */
object BluetoothPrinterCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100BluetoothPrinterMethod.ID
    override val title = "Qutie-family thermal printer"
    override val description = "Print text, QR codes and Code 128 labels on Qutie-compatible FF00 BLE thermal printers."

    @SuppressLint("MissingPermission")
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val adapter = androidContext.getSystemService(BluetoothManager::class.java)?.adapter
        val paired = remember { mutableStateListOf<BluetoothDevice>() }
        val supplied = remember(context.action.settings, context.request.settings) {
            context.request.settings + context.action.settings
        }
        val initial = remember(supplied) { BluetoothPrinterConfig.from(supplied) }

        var selectedAddress by rememberSaveable { mutableStateOf(initial.deviceAddress) }
        var deviceName by rememberSaveable { mutableStateOf("") }
        var deviceMenuOpen by remember { mutableStateOf(false) }
        var compatibilityOpen by rememberSaveable { mutableStateOf(false) }

        var payload by rememberSaveable { mutableStateOf(initial.payload) }
        var secondLine by rememberSaveable { mutableStateOf(initial.secondLine) }
        var format by rememberSaveable { mutableStateOf(initial.content.wireName) }
        var fontSize by rememberSaveable { mutableStateOf(initial.fontSizePx.toString()) }
        var lineSpacing by rememberSaveable { mutableStateOf(initial.lineSpacingPx.toString()) }
        var typefaceFamily by rememberSaveable { mutableStateOf(initial.typeface.wireName) }
        var fontStyle by rememberSaveable { mutableStateOf(initial.fontStyle.wireName) }
        var codeSize by rememberSaveable { mutableStateOf(initial.codeSizePx.toString()) }
        var barcodeHeight by rememberSaveable { mutableStateOf(initial.barcodeHeightPx.toString()) }
        var qrHumanReadable by rememberSaveable { mutableStateOf(initial.showQrHumanReadable) }
        var qrHumanFontSize by rememberSaveable { mutableStateOf(initial.qrHumanFontSizePx.toString()) }
        var qrHumanTypeface by rememberSaveable { mutableStateOf(initial.qrHumanTypeface.wireName) }
        var qrHumanFontStyle by rememberSaveable { mutableStateOf(initial.qrHumanFontStyle.wireName) }
        var qrHumanGap by rememberSaveable { mutableStateOf(initial.qrHumanGapPx.toString()) }
        var centerText by rememberSaveable { mutableStateOf(initial.centerText) }
        var xOffset by rememberSaveable { mutableStateOf(initial.xOffsetPx.toString()) }
        var yOffset by rememberSaveable { mutableStateOf(initial.yOffsetPx.toString()) }
        var stickerLength by rememberSaveable { mutableStateOf(initial.minimumLengthPx.toString()) }
        var printWidth by rememberSaveable { mutableStateOf(initial.printWidthPx.toString()) }
        var paperMode by rememberSaveable { mutableStateOf(initial.paperMode.wireName) }
        var density by rememberSaveable { mutableStateOf(initial.density.toString()) }
        var alongLabel by rememberSaveable { mutableStateOf(initial.alongLabel) }
        var ejectPx by rememberSaveable { mutableStateOf(initial.ejectPx.toString()) }

        var status by rememberSaveable { mutableStateOf("Ready.") }
        var connected by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val scope = rememberCoroutineScope()
        val bleClient = remember {
            BluetoothPrinterBleClient(
                context = androidContext.applicationContext,
                onStatus = { status = it },
                onConnectedChanged = { connected = it }
            )
        }

        fun intValue(raw: String, fallback: Int, minimum: Int, maximum: Int): Int =
            raw.toIntOrNull()?.coerceIn(minimum, maximum) ?: fallback

        fun currentConfig(): BluetoothPrinterConfig = BluetoothPrinterConfig(
            deviceAddress = selectedAddress,
            payload = payload,
            secondLine = secondLine,
            content = BluetoothPrinterContent.fromWire(format),
            fontSizePx = intValue(fontSize, 22, 8, 96),
            lineSpacingPx = intValue(lineSpacing, 28, 8, 128),
            typeface = typefaceFromWire(typefaceFamily),
            fontStyle = textStyleFromWire(fontStyle),
            codeSizePx = intValue(codeSize, 350, 24, 832),
            barcodeHeightPx = intValue(barcodeHeight, 48, 16, 512),
            showQrHumanReadable = qrHumanReadable,
            qrHumanFontSizePx = intValue(qrHumanFontSize, 14, 8, 64),
            qrHumanTypeface = typefaceFromWire(qrHumanTypeface),
            qrHumanFontStyle = textStyleFromWire(qrHumanFontStyle),
            qrHumanGapPx = intValue(qrHumanGap, 6, 0, 128),
            centerText = centerText,
            xOffsetPx = intValue(xOffset, 0, 0, 831),
            yOffsetPx = intValue(yOffset, 2, 0, 4095),
            minimumLengthPx = intValue(stickerLength, 160, 24, 4096),
            printWidthPx = intValue(printWidth, 96, 48, 832),
            paperMode = if (paperMode == "label") BluetoothPrinterProtocol.PaperMode.LABEL else BluetoothPrinterProtocol.PaperMode.CONTINUOUS,
            density = intValue(density, 1, 0, 2),
            alongLabel = alongLabel,
            ejectPx = intValue(ejectPx, 150, 0, 2048)
        )

        fun requiredPermissions(): Array<String> = if (android.os.Build.VERSION.SDK_INT >= 31) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        fun hasPermissions(): Boolean = requiredPermissions().all {
            ContextCompat.checkSelfPermission(androidContext, it) == PackageManager.PERMISSION_GRANTED
        }

        fun refreshPaired() {
            if (adapter == null) {
                status = "Bluetooth is unavailable."
                return
            }
            paired.clear()
            paired.addAll(adapter.bondedDevices.orEmpty().sortedBy { it.name.orEmpty().lowercase() })
            if (selectedAddress.isBlank() && paired.size == 1) {
                selectedAddress = paired.first().address
                deviceName = paired.first().name.orEmpty()
            } else if (selectedAddress.isNotBlank()) {
                paired.firstOrNull { it.address == selectedAddress }?.let { deviceName = it.name.orEmpty() }
            }
            status = if (paired.isEmpty()) "No paired Bluetooth devices found." else "Paired devices refreshed."
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            if (hasPermissions()) refreshPaired() else status = "Bluetooth permission is required."
        }

        LaunchedEffect(
            selectedAddress, payload, secondLine, format,
            fontSize, lineSpacing, typefaceFamily, fontStyle,
            codeSize, barcodeHeight,
            qrHumanReadable, qrHumanFontSize, qrHumanTypeface, qrHumanFontStyle, qrHumanGap,
            centerText, xOffset, yOffset, stickerLength, printWidth,
            paperMode, density, alongLabel, ejectPx
        ) {
            context.onSettingsChanged(currentConfig().settingsMap())
        }

        LaunchedEffect(adapter) {
            if (hasPermissions()) refreshPaired()
        }

        DisposableEffect(bleClient) {
            onDispose { bleClient.close() }
        }

        fun connect() {
            if (!hasPermissions()) {
                permissionLauncher.launch(requiredPermissions())
                return
            }
            val device = paired.firstOrNull { it.address == selectedAddress }
            if (device == null) {
                status = "Select a paired printer first."
                return
            }
            deviceName = device.name.orEmpty()
            bleClient.connect(device)
        }

        fun printPayload() {
            if (!bleClient.isConnected) {
                status = "Connect to the printer first."
                return
            }
            val config = currentConfig()
            val bytes = runCatching { config.buildPrintJob() }.getOrElse { error ->
                status = "Could not render label: ${error.message ?: error::class.java.simpleName}"
                return
            }
            if (bytes.isEmpty()) {
                status = "There is no printable payload."
                return
            }

            scope.launch {
                val send = bleClient.send(bytes)
                val request = As100BluetoothPrinterMethod.request(
                    As100BluetoothPrinterMethod.ID,
                    config.settingsMap(),
                    emptyList(),
                    emptyList()
                )
                val values = config.resultValues(
                    deviceName = deviceName,
                    status = if (send.succeeded) "succeeded" else "failed",
                    bytesSent = send.bytesSent,
                    writeMode = send.writeMode,
                    profile = "qutie_ff00_v2_12_standalone"
                )
                result = As100BluetoothPrinterMethod.result(
                    request,
                    values,
                    context.request.invocationContext
                )
            }
        }

        val previewConfig = currentConfig()
        val previewBitmap = remember(previewConfig) {
            runCatching { previewConfig.preview() }.getOrNull()
        }
        DisposableEffect(previewBitmap) {
            onDispose { previewBitmap?.recycle() }
        }
        fun showSetting(field: String): Boolean = context.settingShouldBeShown(field)

        CapabilityScreenScaffold(
            title,
            capabilityId,
            context,
            context.stepNumber > 1,
            result,
            result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack,
            { result = null },
            { result?.let(onConfirmed) },
            onCancel
        ) {
            Text(
                "Hardware-tested Qutie driver using the FF00 / FF02 / FF01 LuckPrinter-style BLE transport. Defaults target a 96-pixel print head and 150-pixel raster eject.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedButton(
                onClick = { compatibilityOpen = !compatibilityOpen },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (compatibilityOpen) "Hide compatibility & attribution" else "Compatibility & attribution")
            }
            if (compatibilityOpen) {
                Text(
                    "Compatible printers must expose the same FF00-family service/characteristics or equivalent endpoints. Published reverse engineering includes C&Co 3128 / DP-L1S-class and MakeID L1 devices; head width and control behaviour vary between models.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Protocol research attribution is retained in this module's docs/ATTRIBUTION.md and THIRD_PARTY_NOTICES.md. MethodMesh contains an independent Kotlin implementation.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(10.dp))
            SectionTitle("Printer")
            Button(
                onClick = {
                    if (hasPermissions()) refreshPaired() else permissionLauncher.launch(requiredPermissions())
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh paired devices")
            }
            PrinterDevicePicker(
                devices = paired,
                selectedAddress = selectedAddress,
                expanded = deviceMenuOpen,
                onExpandedChanged = { deviceMenuOpen = it },
                onSelected = { device ->
                    selectedAddress = device.address
                    deviceName = device.name.orEmpty()
                    deviceMenuOpen = false
                }
            )

            Spacer(Modifier.height(10.dp))
            val contentHasRuntimeFields = listOf(
                BluetoothPrinterFields.FORMAT,
                BluetoothPrinterFields.PAYLOAD,
                BluetoothPrinterFields.SECOND_LINE,
                BluetoothPrinterFields.QR_HUMAN_READABLE,
                BluetoothPrinterFields.QR_HUMAN_FONT_SIZE,
                BluetoothPrinterFields.QR_HUMAN_TYPEFACE,
                BluetoothPrinterFields.QR_HUMAN_FONT_STYLE,
                BluetoothPrinterFields.QR_HUMAN_GAP
            ).any(::showSetting)
            if (contentHasRuntimeFields) {
                SectionTitle("Content")
                if (showSetting(BluetoothPrinterFields.FORMAT)) {
                    ContentSelector(
                        selected = format,
                        onSelected = { selected ->
                            format = selected
                            if (selected == "qr") xOffset = "0"
                        }
                    )
                }

                when (BluetoothPrinterContent.fromWire(format)) {
                    BluetoothPrinterContent.HEX -> {
                        if (showSetting(BluetoothPrinterFields.PAYLOAD)) {
                            OutlinedTextField(
                                value = payload,
                                onValueChange = { payload = it },
                                label = { Text("Raw payload (hex)") },
                                modifier = Modifier.fillMaxWidth().height(120.dp)
                            )
                            Text(
                                "Raw hex is sent exactly as entered. Label composition, density and eject settings do not modify it.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    BluetoothPrinterContent.QR -> {
                        if (showSetting(BluetoothPrinterFields.PAYLOAD)) {
                            OutlinedTextField(
                                value = payload,
                                onValueChange = { payload = it },
                                label = { Text("QR payload") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                        }
                        if (showSetting(BluetoothPrinterFields.QR_HUMAN_READABLE)) {
                            ToggleRow("Show human-readable text", qrHumanReadable) { qrHumanReadable = it }
                        }
                        val qrReadableHasRuntimeFields = listOf(
                            BluetoothPrinterFields.QR_HUMAN_FONT_SIZE,
                            BluetoothPrinterFields.QR_HUMAN_GAP,
                            BluetoothPrinterFields.QR_HUMAN_TYPEFACE,
                            BluetoothPrinterFields.QR_HUMAN_FONT_STYLE
                        ).any(::showSetting)
                        if (qrHumanReadable && qrReadableHasRuntimeFields) {
                            SectionTitle("QR readable text")
                            Row(Modifier.fillMaxWidth()) {
                                if (showSetting(BluetoothPrinterFields.QR_HUMAN_FONT_SIZE)) {
                                    NumberField("Font px", qrHumanFontSize, Modifier.weight(1f)) { qrHumanFontSize = it }
                                }
                                if (showSetting(BluetoothPrinterFields.QR_HUMAN_FONT_SIZE) && showSetting(BluetoothPrinterFields.QR_HUMAN_GAP)) {
                                    Spacer(Modifier.width(6.dp))
                                }
                                if (showSetting(BluetoothPrinterFields.QR_HUMAN_GAP)) {
                                    NumberField("Gap px", qrHumanGap, Modifier.weight(1f)) { qrHumanGap = it }
                                }
                            }
                            if (showSetting(BluetoothPrinterFields.QR_HUMAN_TYPEFACE)) {
                                ChoiceButtons(
                                    title = "Typeface",
                                    selected = qrHumanTypeface,
                                    choices = listOf("sans" to "Sans", "serif" to "Serif", "monospace" to "Mono")
                                ) { qrHumanTypeface = it }
                            }
                            if (showSetting(BluetoothPrinterFields.QR_HUMAN_FONT_STYLE)) {
                                ChoiceButtons(
                                    title = "Font style",
                                    selected = qrHumanFontStyle,
                                    choices = listOf(
                                        "normal" to "Regular",
                                        "bold" to "Bold",
                                        "italic" to "Italic",
                                        "bold_italic" to "Bold italic"
                                    ),
                                    twoRows = true
                                ) { qrHumanFontStyle = it }
                            }
                        }
                    }
                    BluetoothPrinterContent.CODE128 -> {
                        if (showSetting(BluetoothPrinterFields.PAYLOAD)) {
                            OutlinedTextField(
                                value = payload,
                                onValueChange = { payload = it.replace("\n", " ") },
                                label = { Text("Code 128 payload") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                    BluetoothPrinterContent.TEXT -> {
                        if (showSetting(BluetoothPrinterFields.PAYLOAD)) {
                            OutlinedTextField(
                                value = payload,
                                onValueChange = { payload = it.replace("\n", " ") },
                                label = { Text("Line 1") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        if (showSetting(BluetoothPrinterFields.SECOND_LINE)) {
                            OutlinedTextField(
                                value = secondLine,
                                onValueChange = { secondLine = it.replace("\n", " ") },
                                label = { Text("Line 2 (optional)") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            if (previewConfig.content != BluetoothPrinterContent.HEX) {
                Spacer(Modifier.height(10.dp))
                SectionTitle("Label preview")
                val previewHeight = previewConfig.resolvedPreviewHeight()
                val headWidth = previewConfig.printWidthPx.coerceIn(48, 832)
                val previewScale = minOf(1.5f, 720f / previewHeight.coerceAtLeast(1).toFloat())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    previewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Thermal label preview",
                            modifier = Modifier
                                .width((headWidth * previewScale).dp)
                                .height((previewHeight * previewScale).dp),
                            contentScale = ContentScale.FillBounds
                        )
                    } ?: Text("Preview unavailable for this payload.", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "${headWidth} × ${previewHeight} px · minimum length grows automatically to fit content.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            when (BluetoothPrinterContent.fromWire(format)) {
                BluetoothPrinterContent.TEXT -> {
                    val typographyHasRuntimeFields = listOf(
                        BluetoothPrinterFields.CENTER_TEXT,
                        BluetoothPrinterFields.FONT_SIZE,
                        BluetoothPrinterFields.LINE_SPACING,
                        BluetoothPrinterFields.TYPEFACE,
                        BluetoothPrinterFields.FONT_STYLE,
                        BluetoothPrinterFields.ROTATE_90
                    ).any(::showSetting)
                    if (typographyHasRuntimeFields) {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle("Typography")
                        if (showSetting(BluetoothPrinterFields.CENTER_TEXT)) {
                            ToggleRow("Centre text across label", centerText) { centerText = it }
                        }
                        if (showSetting(BluetoothPrinterFields.FONT_SIZE) || showSetting(BluetoothPrinterFields.LINE_SPACING)) {
                            Row(Modifier.fillMaxWidth()) {
                                if (showSetting(BluetoothPrinterFields.FONT_SIZE)) {
                                    NumberField("Font px", fontSize, Modifier.weight(1f)) { fontSize = it }
                                }
                                if (showSetting(BluetoothPrinterFields.FONT_SIZE) && showSetting(BluetoothPrinterFields.LINE_SPACING)) {
                                    Spacer(Modifier.width(6.dp))
                                }
                                if (showSetting(BluetoothPrinterFields.LINE_SPACING)) {
                                    NumberField("Line px", lineSpacing, Modifier.weight(1f)) { lineSpacing = it }
                                }
                            }
                        }
                        if (showSetting(BluetoothPrinterFields.TYPEFACE)) {
                            ChoiceButtons(
                                "Typeface",
                                typefaceFamily,
                                listOf("sans" to "Sans", "serif" to "Serif", "monospace" to "Mono")
                            ) { typefaceFamily = it }
                        }
                        if (showSetting(BluetoothPrinterFields.FONT_STYLE)) {
                            ChoiceButtons(
                                "Font style",
                                fontStyle,
                                listOf(
                                    "normal" to "Regular",
                                    "bold" to "Bold",
                                    "italic" to "Italic",
                                    "bold_italic" to "Bold italic"
                                ),
                                twoRows = true
                            ) { fontStyle = it }
                        }
                        if (showSetting(BluetoothPrinterFields.ROTATE_90)) {
                            ChoiceButtons(
                                "Text direction",
                                if (alongLabel) "along" else "across",
                                listOf("along" to "Along label", "across" to "Across label")
                            ) { alongLabel = it == "along" }
                        }
                    }
                }
                BluetoothPrinterContent.QR -> {
                    if (showSetting(BluetoothPrinterFields.CODE_SIZE)) {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle("QR size")
                        NumberField("QR size px", codeSize, Modifier.fillMaxWidth()) { codeSize = it }
                    }
                }
                BluetoothPrinterContent.CODE128 -> {
                    if (showSetting(BluetoothPrinterFields.CODE_SIZE) || showSetting(BluetoothPrinterFields.BARCODE_HEIGHT)) {
                        Spacer(Modifier.height(10.dp))
                        SectionTitle("Barcode size")
                        Row(Modifier.fillMaxWidth()) {
                            if (showSetting(BluetoothPrinterFields.CODE_SIZE)) {
                                NumberField("Width px", codeSize, Modifier.weight(1f)) { codeSize = it }
                            }
                            if (showSetting(BluetoothPrinterFields.CODE_SIZE) && showSetting(BluetoothPrinterFields.BARCODE_HEIGHT)) {
                                Spacer(Modifier.width(6.dp))
                            }
                            if (showSetting(BluetoothPrinterFields.BARCODE_HEIGHT)) {
                                NumberField("Height px", barcodeHeight, Modifier.weight(1f)) { barcodeHeight = it }
                            }
                        }
                    }
                }
                BluetoothPrinterContent.HEX -> Unit
            }

            if (previewConfig.content != BluetoothPrinterContent.HEX) {
                val layoutHasRuntimeFields = listOf(
                    BluetoothPrinterFields.X_OFFSET,
                    BluetoothPrinterFields.Y_OFFSET,
                    BluetoothPrinterFields.STICKER_LENGTH,
                    BluetoothPrinterFields.FEED_DOTS,
                    BluetoothPrinterFields.PRINT_WIDTH,
                    BluetoothPrinterFields.DENSITY,
                    BluetoothPrinterFields.PAPER_MODE
                ).any(::showSetting)
                if (layoutHasRuntimeFields) {
                    Spacer(Modifier.height(10.dp))
                    SectionTitle("Layout & media")
                    if (showSetting(BluetoothPrinterFields.X_OFFSET) || showSetting(BluetoothPrinterFields.Y_OFFSET)) {
                        Row(Modifier.fillMaxWidth()) {
                            if (showSetting(BluetoothPrinterFields.X_OFFSET)) {
                                if (previewConfig.content == BluetoothPrinterContent.QR ||
                                    (previewConfig.content == BluetoothPrinterContent.TEXT && centerText)
                                ) {
                                    OutlinedTextField(
                                        value = if (previewConfig.content == BluetoothPrinterContent.QR) "0" else "centred",
                                        onValueChange = {},
                                        label = { Text("X offset") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true,
                                        enabled = false
                                    )
                                } else {
                                    NumberField("X offset", xOffset, Modifier.weight(1f)) { xOffset = it }
                                }
                            }
                            if (showSetting(BluetoothPrinterFields.X_OFFSET) && showSetting(BluetoothPrinterFields.Y_OFFSET)) {
                                Spacer(Modifier.width(6.dp))
                            }
                            if (showSetting(BluetoothPrinterFields.Y_OFFSET)) {
                                NumberField("Y offset", yOffset, Modifier.weight(1f)) { yOffset = it }
                            }
                        }
                    }
                    if (showSetting(BluetoothPrinterFields.STICKER_LENGTH) || showSetting(BluetoothPrinterFields.FEED_DOTS)) {
                        Row(Modifier.fillMaxWidth()) {
                            if (showSetting(BluetoothPrinterFields.STICKER_LENGTH)) {
                                NumberField("Minimum length px", stickerLength, Modifier.weight(1f)) { stickerLength = it }
                            }
                            if (showSetting(BluetoothPrinterFields.STICKER_LENGTH) && showSetting(BluetoothPrinterFields.FEED_DOTS)) {
                                Spacer(Modifier.width(6.dp))
                            }
                            if (showSetting(BluetoothPrinterFields.FEED_DOTS)) {
                                NumberField("Eject px", ejectPx, Modifier.weight(1f)) { ejectPx = it }
                            }
                        }
                    }
                    if (showSetting(BluetoothPrinterFields.FEED_DOTS)) {
                        Text(
                            "Eject is blank raster advance rather than ESC J. Hardware calibration on the tested Qutie gives 150 px as the best default.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (showSetting(BluetoothPrinterFields.PRINT_WIDTH) || showSetting(BluetoothPrinterFields.DENSITY)) {
                        Row(Modifier.fillMaxWidth()) {
                            if (showSetting(BluetoothPrinterFields.PRINT_WIDTH)) {
                                NumberField("Head px", printWidth, Modifier.weight(1f)) { printWidth = it }
                            }
                            if (showSetting(BluetoothPrinterFields.PRINT_WIDTH) && showSetting(BluetoothPrinterFields.DENSITY)) {
                                Spacer(Modifier.width(6.dp))
                            }
                            if (showSetting(BluetoothPrinterFields.DENSITY)) {
                                NumberField("Density 0–2", density, Modifier.weight(1f)) { density = it }
                            }
                        }
                    }
                    if (showSetting(BluetoothPrinterFields.PAPER_MODE)) {
                        ChoiceButtons(
                            "Paper mode",
                            paperMode,
                            listOf("continuous" to "Continuous", "label" to "Label")
                        ) { paperMode = it }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Driver endpoints: FF00 service · FF02 write · FF01 notify. Other Bluetooth printer protocols should be implemented as separate drivers.",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                "Status: $status",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Button(onClick = ::connect, modifier = Modifier.fillMaxWidth()) {
                Text(if (connected) "Reconnect / inspect printer" else "Connect / inspect printer")
            }
            Button(onClick = ::printPayload, modifier = Modifier.fillMaxWidth()) {
                Text("Send print job")
            }
        }
    }
}

@Composable
private fun PrinterDevicePicker(
    devices: List<BluetoothDevice>,
    selectedAddress: String,
    expanded: Boolean,
    onExpandedChanged: (Boolean) -> Unit,
    onSelected: (BluetoothDevice) -> Unit
) {
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onExpandedChanged(true) },
            modifier = Modifier.fillMaxWidth(),
            enabled = devices.isNotEmpty()
        ) {
            val selected = devices.firstOrNull { it.address == selectedAddress }
            Text(
                selected?.let { "${it.name ?: "Unnamed"} (${it.address})" }
                    ?: if (devices.isEmpty()) "No paired devices" else "Select paired printer"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChanged(false) }
        ) {
            devices.forEach { device ->
                DropdownMenuItem(
                    text = { Text("${device.name ?: "Unnamed"} (${device.address})") },
                    onClick = { onSelected(device) }
                )
            }
        }
    }
}

@Composable
private fun ContentSelector(selected: String, onSelected: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        ModeButton("Text", selected == "text", Modifier.weight(1f)) { onSelected("text") }
        Spacer(Modifier.width(4.dp))
        ModeButton("QR", selected == "qr", Modifier.weight(1f)) { onSelected("qr") }
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth()) {
        ModeButton("Code 128", selected == "barcode", Modifier.weight(1f)) { onSelected("barcode") }
        Spacer(Modifier.width(4.dp))
        ModeButton("Raw hex", selected == "hex", Modifier.weight(1f)) { onSelected("hex") }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChanged)
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text("✓ $label") }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label) }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChanged: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChanged(it.filter(Char::isDigit)) },
        label = { Text(label) },
        modifier = modifier,
        singleLine = true
    )
}

@Composable
private fun ChoiceButtons(
    title: String,
    selected: String,
    choices: List<Pair<String, String>>,
    twoRows: Boolean = false,
    onSelected: (String) -> Unit
) {
    Text(title, style = MaterialTheme.typography.bodySmall)
    val rows = if (twoRows) choices.chunked(2) else listOf(choices)
    rows.forEachIndexed { rowIndex, rowChoices ->
        if (rowIndex > 0) Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth()) {
            rowChoices.forEachIndexed { index, (value, label) ->
                ModeButton(label, selected == value, Modifier.weight(1f)) { onSelected(value) }
                if (index < rowChoices.lastIndex) Spacer(Modifier.width(4.dp))
            }
            if (twoRows && rowChoices.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
