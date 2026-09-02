package com.example.methodmesh.modules.bluetoothprinter

import android.graphics.Bitmap
import com.example.methodmesh.settings.MethodSetting

/** Public wire-field names for the Qutie-family printer capability. */
object BluetoothPrinterFields {
    const val DEVICE_NAME = "printer_device_name"
    const val DEVICE_ADDRESS = "printer_device_address"
    const val SERVICE_UUID = "printer_service_uuid"
    const val WRITE_UUID = "printer_write_uuid"
    const val NOTIFY_UUID = "printer_notify_uuid"
    const val PAYLOAD = "printer_payload"
    const val SECOND_LINE = "printer_second_line"
    const val FORMAT = "printer_payload_format"
    const val FONT_SIZE = "printer_font_size"
    const val LINE_SPACING = "printer_line_spacing"
    const val TYPEFACE = "printer_typeface"
    const val FONT_STYLE = "printer_font_style"
    const val CODE_SIZE = "printer_code_size_px"
    const val BARCODE_HEIGHT = "printer_barcode_height_px"
    const val QR_HUMAN_READABLE = "printer_qr_human_readable"
    const val QR_HUMAN_FONT_SIZE = "printer_qr_human_font_size"
    const val QR_HUMAN_TYPEFACE = "printer_qr_human_typeface"
    const val QR_HUMAN_FONT_STYLE = "printer_qr_human_font_style"
    const val QR_HUMAN_GAP = "printer_qr_human_gap_px"
    const val CENTER_TEXT = "printer_center_text"
    const val X_OFFSET = "printer_x_offset"
    const val Y_OFFSET = "printer_y_offset"
    const val STICKER_LENGTH = "printer_sticker_length_px"
    const val LABEL_HEIGHT = "printer_label_height" // accepted as a legacy input alias only
    const val PRINT_WIDTH = "printer_print_width"
    const val PAPER_MODE = "printer_paper_mode"
    const val DENSITY = "printer_density"
    const val ROTATE_90 = "printer_rotate_90"
    const val FEED_DOTS = "printer_feed_dots"
    const val PROFILE = "printer_profile"
    const val STATUS = "printer_status"
    const val BYTES_SENT = "printer_bytes_sent"
    const val WRITE_MODE = "printer_write_mode"
    const val PRINTED_TIME_ISO = "printer_printed_time_iso"

    val outputs = listOf(
        DEVICE_NAME, DEVICE_ADDRESS, SERVICE_UUID, WRITE_UUID, NOTIFY_UUID,
        PAYLOAD, SECOND_LINE, FORMAT,
        FONT_SIZE, LINE_SPACING, TYPEFACE, FONT_STYLE,
        CODE_SIZE, BARCODE_HEIGHT,
        QR_HUMAN_READABLE, QR_HUMAN_FONT_SIZE, QR_HUMAN_TYPEFACE,
        QR_HUMAN_FONT_STYLE, QR_HUMAN_GAP,
        CENTER_TEXT, X_OFFSET, Y_OFFSET, STICKER_LENGTH, LABEL_HEIGHT, PRINT_WIDTH,
        PAPER_MODE, DENSITY, ROTATE_90, FEED_DOTS,
        PROFILE, STATUS, BYTES_SENT, WRITE_MODE, PRINTED_TIME_ISO
    )
}

internal enum class BluetoothPrinterContent(val wireName: String) {
    TEXT("text"),
    QR("qr"),
    CODE128("barcode"),
    HEX("hex");

    companion object {
        fun fromWire(value: String?): BluetoothPrinterContent = when (value?.trim()?.lowercase()) {
            "qr", "qr_code" -> QR
            "barcode", "code128", "code_128" -> CODE128
            "hex", "raw", "raw_hex" -> HEX
            else -> TEXT
        }
    }
}

/**
 * Single source of truth for all module-owned printer configuration.
 *
 * The generic MethodMesh UI only sees [BluetoothPrinterSettings.schema]. The
 * capability screen, rendering code and BLE transport all consume this model.
 * No printer-specific state is registered outside modules/bluetoothprinter.
 */
internal data class BluetoothPrinterConfig(
    val deviceAddress: String = "",
    val payload: String = "MethodMesh test label",
    val secondLine: String = "",
    val content: BluetoothPrinterContent = BluetoothPrinterContent.TEXT,
    val fontSizePx: Int = 22,
    val lineSpacingPx: Int = 28,
    val typeface: BluetoothPrinterProtocol.TypefaceFamily = BluetoothPrinterProtocol.TypefaceFamily.SANS,
    val fontStyle: BluetoothPrinterProtocol.TextStyle = BluetoothPrinterProtocol.TextStyle.NORMAL,
    val codeSizePx: Int = 350,
    val barcodeHeightPx: Int = 48,
    val showQrHumanReadable: Boolean = false,
    val qrHumanFontSizePx: Int = 14,
    val qrHumanTypeface: BluetoothPrinterProtocol.TypefaceFamily = BluetoothPrinterProtocol.TypefaceFamily.SANS,
    val qrHumanFontStyle: BluetoothPrinterProtocol.TextStyle = BluetoothPrinterProtocol.TextStyle.NORMAL,
    val qrHumanGapPx: Int = 6,
    val centerText: Boolean = true,
    val xOffsetPx: Int = 0,
    val yOffsetPx: Int = 2,
    val minimumLengthPx: Int = 160,
    val printWidthPx: Int = 96,
    val paperMode: BluetoothPrinterProtocol.PaperMode = BluetoothPrinterProtocol.PaperMode.CONTINUOUS,
    val density: Int = 1,
    val alongLabel: Boolean = true,
    val ejectPx: Int = 150
) {
    val displayPayload: String
        get() = if (content == BluetoothPrinterContent.TEXT && secondLine.isNotBlank()) {
            "$payload\n$secondLine"
        } else payload

    fun renderOptions(): BluetoothPrinterProtocol.RenderOptions = BluetoothPrinterProtocol.RenderOptions(
        printWidthPx = printWidthPx,
        labelHeightPx = minimumLengthPx,
        fontSizePx = fontSizePx,
        lineSpacingPx = lineSpacingPx,
        typefaceFamily = typeface,
        textStyle = fontStyle,
        codeSizePx = codeSizePx,
        barcodeHeightPx = barcodeHeightPx,
        showQrHumanReadable = showQrHumanReadable,
        qrHumanReadableFontSizePx = qrHumanFontSizePx,
        qrHumanReadableTypefaceFamily = qrHumanTypeface,
        qrHumanReadableTextStyle = qrHumanFontStyle,
        qrHumanReadableGapPx = qrHumanGapPx,
        centerText = centerText,
        xOffsetPx = if (content == BluetoothPrinterContent.QR) 0 else xOffsetPx,
        yOffsetPx = yOffsetPx,
        rotate90 = alongLabel,
        density = density,
        feedDots = ejectPx,
        paperMode = paperMode
    )

    fun buildPrintJob(): ByteArray = when (content) {
        BluetoothPrinterContent.HEX -> BluetoothPrinterProtocol.parseHex(payload)
        BluetoothPrinterContent.QR -> BluetoothPrinterProtocol.buildCodeJob(
            payload,
            BluetoothPrinterProtocol.CodeKind.QR,
            renderOptions()
        )
        BluetoothPrinterContent.CODE128 -> BluetoothPrinterProtocol.buildCodeJob(
            payload,
            BluetoothPrinterProtocol.CodeKind.CODE128,
            renderOptions()
        )
        BluetoothPrinterContent.TEXT -> BluetoothPrinterProtocol.buildTextJob(displayPayload, renderOptions())
    }

    fun preview(): Bitmap? = when (content) {
        BluetoothPrinterContent.HEX -> null
        BluetoothPrinterContent.QR -> BluetoothPrinterProtocol.renderCodePreview(
            payload,
            BluetoothPrinterProtocol.CodeKind.QR,
            renderOptions()
        )
        BluetoothPrinterContent.CODE128 -> BluetoothPrinterProtocol.renderCodePreview(
            payload,
            BluetoothPrinterProtocol.CodeKind.CODE128,
            renderOptions()
        )
        BluetoothPrinterContent.TEXT -> BluetoothPrinterProtocol.renderTextPreview(displayPayload, renderOptions())
    }

    fun resolvedPreviewHeight(): Int = when (content) {
        BluetoothPrinterContent.QR -> BluetoothPrinterProtocol.resolvedCodeLabelHeight(
            BluetoothPrinterProtocol.CodeKind.QR,
            renderOptions(),
            payload
        )
        BluetoothPrinterContent.CODE128 -> BluetoothPrinterProtocol.resolvedCodeLabelHeight(
            BluetoothPrinterProtocol.CodeKind.CODE128,
            renderOptions(),
            payload
        )
        BluetoothPrinterContent.TEXT -> BluetoothPrinterProtocol.resolvedLabelHeight(displayPayload, renderOptions())
        BluetoothPrinterContent.HEX -> minimumLengthPx
    }

    /**
     * Values persisted by the normal MethodMesh capability-settings contract.
     * Protocol endpoint UUIDs are fixed by this driver profile and are therefore
     * not capability settings. A different BLE protocol belongs in a different driver.
     */
    fun settingsMap(): Map<String, String> = linkedMapOf<String, String>().apply {
        put(BluetoothPrinterFields.DEVICE_ADDRESS, deviceAddress)
        put(BluetoothPrinterFields.PAYLOAD, displayPayload)
        put(BluetoothPrinterFields.SECOND_LINE, secondLine)
        put(BluetoothPrinterFields.FORMAT, content.wireName)
        put(BluetoothPrinterFields.FONT_SIZE, fontSizePx.toString())
        put(BluetoothPrinterFields.LINE_SPACING, lineSpacingPx.toString())
        put(BluetoothPrinterFields.TYPEFACE, typeface.wireName)
        put(BluetoothPrinterFields.FONT_STYLE, fontStyle.wireName)
        put(BluetoothPrinterFields.CODE_SIZE, codeSizePx.toString())
        put(BluetoothPrinterFields.BARCODE_HEIGHT, barcodeHeightPx.toString())
        put(BluetoothPrinterFields.QR_HUMAN_READABLE, showQrHumanReadable.toString())
        put(BluetoothPrinterFields.QR_HUMAN_FONT_SIZE, qrHumanFontSizePx.toString())
        put(BluetoothPrinterFields.QR_HUMAN_TYPEFACE, qrHumanTypeface.wireName)
        put(BluetoothPrinterFields.QR_HUMAN_FONT_STYLE, qrHumanFontStyle.wireName)
        put(BluetoothPrinterFields.QR_HUMAN_GAP, qrHumanGapPx.toString())
        put(BluetoothPrinterFields.CENTER_TEXT, centerText.toString())
        put(BluetoothPrinterFields.X_OFFSET, xOffsetPx.toString())
        put(BluetoothPrinterFields.Y_OFFSET, yOffsetPx.toString())
        put(BluetoothPrinterFields.STICKER_LENGTH, minimumLengthPx.toString())
        put(BluetoothPrinterFields.PRINT_WIDTH, printWidthPx.toString())
        put(BluetoothPrinterFields.PAPER_MODE, paperMode.wireName)
        put(BluetoothPrinterFields.DENSITY, density.toString())
        put(BluetoothPrinterFields.ROTATE_90, alongLabel.toString())
        put(BluetoothPrinterFields.FEED_DOTS, ejectPx.toString())


    }

    fun resultValues(
        deviceName: String,
        status: String,
        bytesSent: Int,
        writeMode: String,
        profile: String
    ): Map<String, String> = settingsMap() + mapOf(
        BluetoothPrinterFields.DEVICE_NAME to deviceName,
        BluetoothPrinterFields.SERVICE_UUID to BluetoothPrinterProtocol.DEFAULT_SERVICE_UUID,
        BluetoothPrinterFields.WRITE_UUID to BluetoothPrinterProtocol.DEFAULT_WRITE_UUID,
        BluetoothPrinterFields.NOTIFY_UUID to BluetoothPrinterProtocol.DEFAULT_NOTIFY_UUID,
        BluetoothPrinterFields.LABEL_HEIGHT to minimumLengthPx.toString(),
        BluetoothPrinterFields.PROFILE to profile,
        BluetoothPrinterFields.STATUS to status,
        BluetoothPrinterFields.BYTES_SENT to bytesSent.toString(),
        BluetoothPrinterFields.WRITE_MODE to writeMode
    )

    companion object {
        fun from(settings: Map<String, String>): BluetoothPrinterConfig {
            val rawPayload = settings[BluetoothPrinterFields.PAYLOAD] ?: "MethodMesh test label"
            val content = BluetoothPrinterContent.fromWire(settings[BluetoothPrinterFields.FORMAT])
            val lineOne = if (content == BluetoothPrinterContent.TEXT) {
                rawPayload.lineSequence().firstOrNull().orEmpty()
            } else rawPayload
            val lineTwo = settings[BluetoothPrinterFields.SECOND_LINE]
                ?: if (content == BluetoothPrinterContent.TEXT) rawPayload.lineSequence().drop(1).firstOrNull().orEmpty() else ""

            return BluetoothPrinterConfig(
                deviceAddress = settings[BluetoothPrinterFields.DEVICE_ADDRESS].orEmpty(),
                payload = lineOne,
                secondLine = lineTwo,
                content = content,
                fontSizePx = settings.int(BluetoothPrinterFields.FONT_SIZE, 22, 8, 96),
                lineSpacingPx = settings.int(BluetoothPrinterFields.LINE_SPACING, 28, 8, 128),
                typeface = typefaceFromWire(settings[BluetoothPrinterFields.TYPEFACE]),
                fontStyle = textStyleFromWire(settings[BluetoothPrinterFields.FONT_STYLE]),
                codeSizePx = settings.int(BluetoothPrinterFields.CODE_SIZE, 350, 24, 832),
                barcodeHeightPx = settings.int(BluetoothPrinterFields.BARCODE_HEIGHT, 48, 16, 512),
                showQrHumanReadable = settings.bool(BluetoothPrinterFields.QR_HUMAN_READABLE, false),
                qrHumanFontSizePx = settings.int(BluetoothPrinterFields.QR_HUMAN_FONT_SIZE, 14, 8, 64),
                qrHumanTypeface = typefaceFromWire(settings[BluetoothPrinterFields.QR_HUMAN_TYPEFACE]),
                qrHumanFontStyle = textStyleFromWire(settings[BluetoothPrinterFields.QR_HUMAN_FONT_STYLE]),
                qrHumanGapPx = settings.int(BluetoothPrinterFields.QR_HUMAN_GAP, 6, 0, 128),
                centerText = settings.bool(BluetoothPrinterFields.CENTER_TEXT, true),
                xOffsetPx = settings.int(BluetoothPrinterFields.X_OFFSET, 0, 0, 831),
                yOffsetPx = settings.int(BluetoothPrinterFields.Y_OFFSET, 2, 0, 4095),
                minimumLengthPx = (
                    settings[BluetoothPrinterFields.STICKER_LENGTH]
                        ?: settings[BluetoothPrinterFields.LABEL_HEIGHT]
                ).toIntOrNullClamped(160, 24, 4096),
                printWidthPx = settings.int(BluetoothPrinterFields.PRINT_WIDTH, 96, 48, 832),
                paperMode = when (settings[BluetoothPrinterFields.PAPER_MODE]?.lowercase()) {
                    "label" -> BluetoothPrinterProtocol.PaperMode.LABEL
                    "raw" -> BluetoothPrinterProtocol.PaperMode.RAW
                    else -> BluetoothPrinterProtocol.PaperMode.CONTINUOUS
                },
                density = settings.int(BluetoothPrinterFields.DENSITY, 1, 0, 2),
                alongLabel = settings.bool(BluetoothPrinterFields.ROTATE_90, true),
                ejectPx = settings.int(BluetoothPrinterFields.FEED_DOTS, 150, 0, 2048)
            )
        }
    }
}

/** Module-owned typed settings; this is the only settings metadata exported to MethodMesh. */
internal object BluetoothPrinterSettings {
    val schema: List<MethodSetting> = listOf(
        MethodSetting.TextSetting(
            BluetoothPrinterFields.DEVICE_ADDRESS,
            "Printer device address",
            "Paired Bluetooth address. The capability screen provides the device picker.",
            "Printer",
            ""
        ),
        MethodSetting.ChoiceSetting(
            BluetoothPrinterFields.FORMAT,
            "Content",
            "Text, QR code, Code 128 barcode, or raw hexadecimal bytes.",
            "Content",
            "text",
            listOf("text", "qr", "barcode", "hex")
        ),
        MethodSetting.TextSetting(
            BluetoothPrinterFields.PAYLOAD,
            "Payload",
            "Line 1 for text, or the QR/barcode/raw payload.",
            "Content",
            "MethodMesh test label"
        ),
        MethodSetting.TextSetting(
            BluetoothPrinterFields.SECOND_LINE,
            "Line 2",
            "Optional second text line.",
            "Content",
            ""
        ),
        MethodSetting.BooleanSetting(
            BluetoothPrinterFields.CENTER_TEXT,
            "Centre text across label",
            "Enabled by default so ordinary text is centred on the 96-pixel print head.",
            "Typography",
            true
        ),
        MethodSetting.IntSetting(BluetoothPrinterFields.FONT_SIZE, "Font size", "Text size.", "Typography", 22, 8, 96, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.LINE_SPACING, "Line spacing", "Distance between text lanes.", "Typography", 28, 8, 128, 1, "px"),
        MethodSetting.ChoiceSetting(BluetoothPrinterFields.TYPEFACE, "Typeface", null, "Typography", "sans", listOf("sans", "serif", "monospace")),
        MethodSetting.ChoiceSetting(BluetoothPrinterFields.FONT_STYLE, "Font style", null, "Typography", "normal", listOf("normal", "bold", "italic", "bold_italic")),
        MethodSetting.BooleanSetting(BluetoothPrinterFields.ROTATE_90, "Text runs along label", "On = along the paper strip; off = across the print head.", "Typography", true),
        MethodSetting.BooleanSetting(BluetoothPrinterFields.QR_HUMAN_READABLE, "Show QR human-readable text", "Print the QR payload after the code.", "QR", false),
        MethodSetting.IntSetting(BluetoothPrinterFields.QR_HUMAN_FONT_SIZE, "QR readable font size", null, "QR", 14, 8, 64, 1, "px"),
        MethodSetting.ChoiceSetting(BluetoothPrinterFields.QR_HUMAN_TYPEFACE, "QR readable typeface", null, "QR", "sans", listOf("sans", "serif", "monospace")),
        MethodSetting.ChoiceSetting(BluetoothPrinterFields.QR_HUMAN_FONT_STYLE, "QR readable font style", null, "QR", "normal", listOf("normal", "bold", "italic", "bold_italic")),
        MethodSetting.IntSetting(BluetoothPrinterFields.QR_HUMAN_GAP, "QR readable gap", "Space between the QR code and readable payload.", "QR", 6, 0, 128, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.CODE_SIZE, "QR / barcode width", null, "Codes", 350, 24, 832, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.BARCODE_HEIGHT, "Barcode height", null, "Codes", 48, 16, 512, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.X_OFFSET, "X offset", "Ignored when centred text is enabled and forced to zero for QR.", "Layout", 0, 0, 831, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.Y_OFFSET, "Y offset", null, "Layout", 2, 0, 4095, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.STICKER_LENGTH, "Minimum label length", "Grows automatically when content needs more room.", "Layout", 160, 24, 4096, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.FEED_DOTS, "Eject", "Blank raster advance; 150 px is calibrated for the tested Qutie.", "Printer", 150, 0, 2048, 1, "px"),
        MethodSetting.IntSetting(BluetoothPrinterFields.PRINT_WIDTH, "Print-head width", "96 px for the tested Qutie.", "Printer", 96, 48, 832, 1, "px"),
        MethodSetting.ChoiceSetting(BluetoothPrinterFields.PAPER_MODE, "Paper mode", null, "Printer", "continuous", listOf("continuous", "label")),
        MethodSetting.IntSetting(BluetoothPrinterFields.DENSITY, "Density", "Printer density level 0–2.", "Printer", 1, 0, 2, 1, null)
    )
}

private fun Map<String, String>.int(key: String, fallback: Int, minimum: Int, maximum: Int): Int =
    this[key].toIntOrNullClamped(fallback, minimum, maximum)

private fun Map<String, String>.bool(key: String, fallback: Boolean): Boolean =
    this[key]?.trim()?.lowercase()?.let { it == "true" || it == "1" || it == "yes" || it == "on" } ?: fallback

private fun String?.toIntOrNullClamped(fallback: Int, minimum: Int, maximum: Int): Int =
    this?.toIntOrNull()?.coerceIn(minimum, maximum) ?: fallback

internal fun typefaceFromWire(value: String?): BluetoothPrinterProtocol.TypefaceFamily = when (value?.trim()?.lowercase()) {
    "serif" -> BluetoothPrinterProtocol.TypefaceFamily.SERIF
    "monospace", "mono" -> BluetoothPrinterProtocol.TypefaceFamily.MONOSPACE
    else -> BluetoothPrinterProtocol.TypefaceFamily.SANS
}

internal fun textStyleFromWire(value: String?): BluetoothPrinterProtocol.TextStyle = when (value?.trim()?.lowercase()) {
    "bold" -> BluetoothPrinterProtocol.TextStyle.BOLD
    "italic" -> BluetoothPrinterProtocol.TextStyle.ITALIC
    "bold_italic", "bolditalic", "bold italic" -> BluetoothPrinterProtocol.TextStyle.BOLD_ITALIC
    else -> BluetoothPrinterProtocol.TextStyle.NORMAL
}
