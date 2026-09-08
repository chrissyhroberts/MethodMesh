package com.example.methodmesh.modules.bluetoothprinter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.io.ByteArrayOutputStream

/**
 * Qutie / FF00-family BLE thermal-printer protocol implementation.
 *
 * Interoperability and protocol behaviour were informed by public reverse-engineering work:
 * - ChiaraCannolee/thermal-pocket-printer-basic (MIT):
 *   https://github.com/ChiaraCannolee/thermal-pocket-printer-basic
 * - thomashermine/makeid-labelprinter-l1-bluetooth (MIT):
 *   https://github.com/thomashermine/makeid-labelprinter-l1-bluetooth
 * - tomLadder/thermoprint (MIT):
 *   https://github.com/tomLadder/thermoprint (corroborating research only)
 *
 * MethodMesh is an independent Kotlin implementation; no source from those projects is
 * incorporated here. See docs/ATTRIBUTION.md and docs/THIRD_PARTY_NOTICES.md.
 */
internal object BluetoothPrinterProtocol {
    const val DEFAULT_SERVICE_UUID = "0000ff00-0000-1000-8000-00805f9b34fb"
    const val DEFAULT_WRITE_UUID = "0000ff02-0000-1000-8000-00805f9b34fb"
    const val DEFAULT_NOTIFY_UUID = "0000ff01-0000-1000-8000-00805f9b34fb"
    const val CLIENT_CONFIG_UUID = "00002902-0000-1000-8000-00805f9b34fb"

    enum class PaperMode(val wireName: String) {
        LABEL("label"),
        CONTINUOUS("continuous"),
        RAW("raw")
    }

    enum class TypefaceFamily(val wireName: String, val androidFamily: String) {
        SANS("sans", "sans-serif"),
        SERIF("serif", "serif"),
        MONOSPACE("monospace", "monospace")
    }

    enum class TextStyle(val wireName: String, val androidStyle: Int) {
        NORMAL("normal", Typeface.NORMAL),
        BOLD("bold", Typeface.BOLD),
        ITALIC("italic", Typeface.ITALIC),
        BOLD_ITALIC("bold_italic", Typeface.BOLD_ITALIC)
    }

    enum class CodeKind { QR, CODE128 }

    data class RenderOptions(
        val printWidthPx: Int = 96,
        val labelHeightPx: Int = 160,
        val fontSizePx: Int = 22,
        val lineSpacingPx: Int = 28,
        val typefaceFamily: TypefaceFamily = TypefaceFamily.SANS,
        val textStyle: TextStyle = TextStyle.NORMAL,
        val codeSizePx: Int = 350,
        val barcodeHeightPx: Int = 48,
        val showQrHumanReadable: Boolean = false,
        val qrHumanReadableFontSizePx: Int = 14,
        val qrHumanReadableTypefaceFamily: TypefaceFamily = TypefaceFamily.SANS,
        val qrHumanReadableTextStyle: TextStyle = TextStyle.NORMAL,
        val qrHumanReadableGapPx: Int = 6,
        val centerText: Boolean = true,
        val xOffsetPx: Int = 0,
        val yOffsetPx: Int = 2,
        val rotate90: Boolean = true,
        val density: Int = 1,
        val feedDots: Int = 150,
        val bottomMarginPx: Int = 8,
        val paperMode: PaperMode = PaperMode.CONTINUOUS
    )

    /**
     * Builds a LuckPrinter-family job from protocol-level facts rather than
     * vendor SDK code. The FF00/FF01/FF02 service layout and command sequence
     * are documented in docs/ATTRIBUTION.md.
     */
    fun buildTextJob(text: String, options: RenderOptions): ByteArray {
        val bitmap = renderText(text, options)
        return try { buildBitmapJob(bitmap, options) } finally { bitmap.recycle() }
    }

    fun buildCodeJob(payload: String, kind: CodeKind, options: RenderOptions): ByteArray {
        val bitmap = renderCode(payload, kind, options)
        return try { buildBitmapJob(bitmap, options) } finally { bitmap.recycle() }
    }

    private fun buildBitmapJob(bitmap: Bitmap, options: RenderOptions): ByteArray {
        if (options.paperMode == PaperMode.RAW) {
            return buildRasterJob(escPosRasterBanded(bitmap), options.copy(feedDots = 0))
        }

        // Qutie hardware testing showed that ESC J feed commands are ignored
        // after the image has finished printing. Advance paper as part of the
        // raster stream instead: print through the last inked row, then append
        // zero-valued GS v 0 raster rows for both designed whitespace and the
        // mechanical head-to-exit distance. This keeps the motor in raster
        // processing until the complete label has physically emerged.
        val printedHeight = lastInkRow(bitmap)?.plus(1) ?: 0
        val printedRaster = if (printedHeight > 0) {
            val printable = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, printedHeight)
            try { escPosRasterBanded(printable) } finally { printable.recycle() }
        } else byteArrayOf()
        val designedTail = (bitmap.height - printedHeight).coerceAtLeast(0)
        val advanceRows = designedTail + options.feedDots.coerceAtLeast(0)
        val advanceRaster = blankRasterBanded(bitmap.width, advanceRows)
        return buildRasterJob(printedRaster + advanceRaster, options.copy(feedDots = 0))
    }

    fun buildRasterJob(gsV0Raster: ByteArray, options: RenderOptions): ByteArray {
        val out = ByteArrayOutputStream(gsV0Raster.size + 64)

        // LuckPrinter-family enable + wake.
        out.write(byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x03))
        out.write(ByteArray(12))

        // Density 0..2 is supported by the documented family. Clamp rather
        // than sending undefined values to unknown rebadged firmware.
        out.write(byteArrayOf(0x10, 0xFF.toByte(), 0x10, 0x00, options.density.coerceIn(0, 2).toByte()))

        when (options.paperMode) {
            PaperMode.LABEL -> out.write(byteArrayOf(0x1F, 0x11, 0x51))
            PaperMode.CONTINUOUS, PaperMode.RAW -> Unit
        }

        out.write(gsV0Raster)

        when (options.paperMode) {
            PaperMode.LABEL -> {
                // Designed whitespace and mechanical eject have already been
                // emitted as blank raster rows. Label mode now only asks the
                // firmware to index to the next media boundary before stop.
                out.write(byteArrayOf(0x1D, 0x0C))
                out.write(byteArrayOf(0x1F, 0x11, 0x50))
            }
            PaperMode.CONTINUOUS -> Unit
            PaperMode.RAW -> Unit
        }

        out.write(byteArrayOf(0x10, 0xFF.toByte(), 0xF1.toByte(), 0x45))
        return out.toByteArray()
    }

    private fun lastInkRow(bitmap: Bitmap): Int? {
        for (y in bitmap.height - 1 downTo 0) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (luminance < 160) return y
            }
        }
        return null
    }


    /**
     * Encode a tall fixed-width bitmap as a sequence of small GS v 0 raster
     * commands. Some FF00/LuckPrinter-family firmware accepts the first part of
     * a tall raster but silently truncates the remainder. Keeping each raster
     * block short avoids that firmware limit while preserving one continuous
     * printed image.
     */
    /**
     * Create zero-valued raster rows used as physical paper advance. Unlike
     * ESC J, these rows remain inside the image-processing path that this
     * Qutie firmware demonstrably advances for.
     */
    fun blankRasterBanded(widthPx: Int, rows: Int, bandHeightRows: Int = 64): ByteArray {
        if (rows <= 0) return byteArrayOf()
        val widthBytes = (widthPx.coerceAtLeast(1) + 7) / 8
        val safeBandHeight = bandHeightRows.coerceIn(8, 96)
        val out = ByteArrayOutputStream(widthBytes * rows + 16 * ((rows + safeBandHeight - 1) / safeBandHeight))
        var remaining = rows
        while (remaining > 0) {
            val bandHeight = minOf(safeBandHeight, remaining)
            out.write(byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
                (bandHeight and 0xFF).toByte(), ((bandHeight shr 8) and 0xFF).toByte()
            ))
            out.write(ByteArray(widthBytes * bandHeight))
            remaining -= bandHeight
        }
        return out.toByteArray()
    }

    fun escPosRasterBanded(bitmap: Bitmap, bandHeightRows: Int = 64): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val safeBandHeight = bandHeightRows.coerceIn(8, 96)
        val out = ByteArrayOutputStream(widthBytes * bitmap.height + 16 * ((bitmap.height + safeBandHeight - 1) / safeBandHeight))

        var startY = 0
        while (startY < bitmap.height) {
            val bandHeight = minOf(safeBandHeight, bitmap.height - startY)
            val data = ByteArray(widthBytes * bandHeight)

            for (localY in 0 until bandHeight) {
                val y = startY + localY
                for (byteX in 0 until widthBytes) {
                    var packed = 0
                    for (bit in 0 until 8) {
                        val x = byteX * 8 + bit
                        if (x >= bitmap.width) continue
                        val pixel = bitmap.getPixel(x, y)
                        val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                        if (luminance < 160) packed = packed or (1 shl (7 - bit))
                    }
                    data[localY * widthBytes + byteX] = packed.toByte()
                }
            }

            out.write(byteArrayOf(
                0x1D, 0x76, 0x30, 0x00,
                (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
                (bandHeight and 0xFF).toByte(), ((bandHeight shr 8) and 0xFF).toByte()
            ))
            out.write(data)
            startY += bandHeight
        }

        return out.toByteArray()
    }

    fun escPosRaster(bitmap: Bitmap): ByteArray {
        val widthBytes = (bitmap.width + 7) / 8
        val height = bitmap.height
        val data = ByteArray(widthBytes * height)

        for (y in 0 until height) {
            for (byteX in 0 until widthBytes) {
                var packed = 0
                for (bit in 0 until 8) {
                    val x = byteX * 8 + bit
                    if (x >= bitmap.width) continue
                    val pixel = bitmap.getPixel(x, y)
                    val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                    if (luminance < 160) packed = packed or (1 shl (7 - bit))
                }
                data[y * widthBytes + byteX] = packed.toByte()
            }
        }

        return byteArrayOf(
            0x1D, 0x76, 0x30, 0x00,
            (widthBytes and 0xFF).toByte(), ((widthBytes shr 8) and 0xFF).toByte(),
            (height and 0xFF).toByte(), ((height shr 8) and 0xFF).toByte()
        ) + data
    }

    /**
     * Resolve the physical sticker length. labelHeightPx is a minimum, not a
     * clipping boundary: the canvas grows whenever the rendered content would
     * otherwise run past it.
     */
    private fun textPaint(options: RenderOptions): Paint =
        makeTextPaint(options.fontSizePx, options.typefaceFamily, options.textStyle, 8, 96)

    private fun qrHumanReadablePaint(options: RenderOptions): Paint =
        makeTextPaint(
            options.qrHumanReadableFontSizePx,
            options.qrHumanReadableTypefaceFamily,
            options.qrHumanReadableTextStyle,
            8,
            64
        )

    private fun makeTextPaint(
        sizePx: Int,
        family: TypefaceFamily,
        style: TextStyle,
        minimumPx: Int,
        maximumPx: Int
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = sizePx.coerceIn(minimumPx, maximumPx).toFloat()
        typeface = Typeface.create(family.androidFamily, style.androidStyle)
    }

    /** Sticker length is a minimum and grows automatically to fit content. */
    fun resolvedLabelHeight(text: String, options: RenderOptions): Int {
        val requested = options.labelHeightPx.coerceIn(24, 4096)
        val width = options.printWidthPx.coerceIn(48, 832)
        val fontSize = options.fontSizePx.coerceIn(8, 96)
        val spacing = options.lineSpacingPx.coerceAtLeast(fontSize)
        val yOffset = options.yOffsetPx.coerceAtLeast(0)
        val margin = options.bottomMarginPx.coerceIn(0, 512)
        val lines = text.lines().take(2).ifEmpty { listOf("") }
        val paint = textPaint(options)

        val required = if (options.rotate90) {
            val longest = lines.maxOfOrNull { paint.measureText(it) } ?: 0f
            yOffset + kotlin.math.ceil(longest.toDouble()).toInt() + margin
        } else {
            yOffset + fontSize + ((lines.size - 1).coerceAtLeast(0) * spacing) + margin
        }
        return maxOf(requested, required).coerceIn(24, 4096)
    }

    fun resolvedCodeLabelHeight(kind: CodeKind, options: RenderOptions, payload: String = ""): Int {
        val requested = options.labelHeightPx.coerceIn(24, 4096)
        val width = options.printWidthPx.coerceIn(48, 832)
        val y = options.yOffsetPx.coerceAtLeast(0)
        val margin = options.bottomMarginPx.coerceIn(0, 512)
        val graphicHeight = when (kind) {
            CodeKind.QR -> options.codeSizePx.coerceIn(24, width)
            CodeKind.CODE128 -> options.barcodeHeightPx.coerceIn(16, 512)
        }
        var required = y + graphicHeight + margin
        if (kind == CodeKind.QR && options.showQrHumanReadable && payload.isNotBlank()) {
            val paint = qrHumanReadablePaint(options)
            val textRun = kotlin.math.ceil(paint.measureText(payload).toDouble()).toInt()
            required = y + graphicHeight + options.qrHumanReadableGapPx.coerceIn(0, 128) + textRun + margin
        }
        return maxOf(requested, required).coerceIn(24, 4096)
    }

    private fun thresholdPreview(source: Bitmap): Bitmap {
        val preview = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val pixel = source.getPixel(x, y)
            val luminance = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            preview.setPixel(x, y, if (luminance < 160) Color.BLACK else Color.WHITE)
        }
        return preview
    }

    /** Preview and printing deliberately share the same renderer. */
    fun renderTextPreview(text: String, options: RenderOptions): Bitmap {
        val source = renderText(text, options)
        return try { thresholdPreview(source) } finally { source.recycle() }
    }

    fun renderCodePreview(payload: String, kind: CodeKind, options: RenderOptions): Bitmap {
        val source = renderCode(payload, kind, options)
        return try { thresholdPreview(source) } finally { source.recycle() }
    }

    private fun renderText(text: String, options: RenderOptions): Bitmap {
        val width = options.printWidthPx.coerceIn(48, 832)
        val height = resolvedLabelHeight(text, options)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        val fontSize = options.fontSizePx.coerceIn(8, 96)
        val paint = textPaint(options)
        val spacing = options.lineSpacingPx.coerceAtLeast(fontSize)
        val xOffset = options.xOffsetPx.coerceIn(0, width - 1)
        val yOffset = options.yOffsetPx.coerceIn(0, height - 1)
        val lines = text.lines().take(2)

        if (options.rotate90) {
            // Logical X becomes physical Y. Centred mode positions the whole
            // one/two-line block around the centre of the physical print head.
            canvas.save()
            canvas.translate(width.toFloat(), 0f)
            canvas.rotate(90f)
            val count = lines.size.coerceAtLeast(1)
            val metricCentre = (paint.ascent() + paint.descent()) / 2f
            lines.forEachIndexed { index, line ->
                val logicalBaseline = if (options.centerText) {
                    val desiredPhysicalCentre = width / 2f +
                        (index - (count - 1) / 2f) * spacing.toFloat()
                    width.toFloat() - desiredPhysicalCentre - metricCentre
                } else {
                    val lane = if (count > 1) count - 1 - index else 0
                    (width - xOffset - lane * spacing).toFloat()
                }
                canvas.drawText(line, yOffset.toFloat(), logicalBaseline, paint)
            }
            canvas.restore()
        } else {
            lines.forEachIndexed { index, line ->
                val drawX = if (options.centerText) {
                    ((width - paint.measureText(line)) / 2f).coerceAtLeast(0f)
                } else {
                    xOffset.toFloat()
                }
                canvas.drawText(line, drawX, (yOffset + fontSize + index * spacing).toFloat(), paint)
            }
        }
        return bitmap
    }

    private fun renderCode(payload: String, kind: CodeKind, options: RenderOptions): Bitmap {
        val width = options.printWidthPx.coerceIn(48, 832)
        val height = resolvedCodeLabelHeight(kind, options, payload)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        if (payload.isBlank()) return bitmap

        val x = options.xOffsetPx.coerceIn(0, width - 1)
        val y = options.yOffsetPx.coerceIn(0, height - 1)
        val availableWidth = (width - x).coerceAtLeast(1)
        val requestedWidth = options.codeSizePx.coerceIn(24, width)
        val graphicWidth = minOf(requestedWidth, availableWidth)
        val graphicHeight = when (kind) {
            CodeKind.QR -> graphicWidth
            CodeKind.CODE128 -> options.barcodeHeightPx.coerceIn(16, 512)
        }.coerceAtMost(height - y)
        if (graphicWidth <= 0 || graphicHeight <= 0) return bitmap

        val format = if (kind == CodeKind.QR) BarcodeFormat.QR_CODE else BarcodeFormat.CODE_128
        val matrix = MultiFormatWriter().encode(payload, format, graphicWidth, graphicHeight)
        for (gy in 0 until graphicHeight) for (gx in 0 until graphicWidth) {
            if (matrix[gx, gy]) bitmap.setPixel(x + gx, y + gy, Color.BLACK)
        }

        if (kind == CodeKind.QR && options.showQrHumanReadable && payload.isNotBlank()) {
            val gap = options.qrHumanReadableGapPx.coerceIn(0, 128)
            val startY = y + graphicHeight + gap
            val paint = qrHumanReadablePaint(options)
            // A 96 px head leaves no useful room beside a full-width QR. Keep
            // the readable payload after the QR, along the strip, and centre it
            // across the head. Typeface, style and size are independently set.
            canvas.save()
            canvas.translate(width.toFloat(), startY.toFloat())
            canvas.rotate(90f)
            val metricCentre = (paint.ascent() + paint.descent()) / 2f
            val baseline = width / 2f - metricCentre
            canvas.drawText(payload, 0f, baseline, paint)
            canvas.restore()
        }
        return bitmap
    }

    fun parseHex(text: String): ByteArray = text
        .split(Regex("[\\s,;:]+"))
        .filter { it.isNotBlank() }
        .mapNotNull { token ->
            val clean = token.removePrefix("0x").removePrefix("0X")
            clean.toIntOrNull(16)?.takeIf { clean.length in 1..2 }?.toByte()
        }
        .toByteArray()
}
