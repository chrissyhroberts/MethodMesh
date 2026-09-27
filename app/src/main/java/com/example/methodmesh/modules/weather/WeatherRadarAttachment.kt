package com.example.methodmesh.modules.weather

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Generates a transient radar-animation attachment for external/ODK return. */
internal object WeatherRadarAttachment {
    private val timestampFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd MMM HH:mm").withZone(ZoneId.of("UTC"))

    fun createGifIfPossible(
        context: Context,
        values: Map<String, String>,
        latitude: Double,
        longitude: Double,
        zoom: Double
    ): Pair<String, String> = runCatching {
        val host = values["weather_radar_host"].orEmpty()
        val timelineJson = values["weather_radar_timeline_json"].orEmpty()
        if (host.isBlank() || timelineJson.isBlank()) return@runCatching "" to ""
        val frames = parseFrames(timelineJson)
        if (frames.isEmpty()) return@runCatching "" to ""
        val boundedZoom = zoom.toInt().coerceIn(1, 7)
        val rendered = frames.mapNotNull { frame ->
            runCatching {
                renderFrame(host, frame.path, frame.timeIso, frame.frameClass, latitude, longitude, boundedZoom)
            }.getOrNull()
        }
        if (rendered.isEmpty()) return@runCatching "" to ""

        val outDir = File(context.cacheDir, "methodmesh/weather").apply { mkdirs() }
        val file = File(outDir, "weather_radar_${System.currentTimeMillis()}.gif")
        FileOutputStream(file).use { fos ->
            BufferedOutputStream(fos).use { bos ->
                SimpleGifWriter(bos, rendered.first().width, rendered.first().height).use { writer ->
                    rendered.forEach { writer.writeFrame(it, 45) }
                }
            }
        }
        val sha = sha256(file)
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return@runCatching "" to ""
        uri.toString() to sha
    }.getOrElse { "" to "" }

    private data class FrameSpec(val path: String, val timeIso: String, val frameClass: String)

    private fun parseFrames(timelineJson: String): List<FrameSpec> {
        val root = JSONObject(timelineJson)
        val list = mutableListOf<FrameSpec>()
        fun append(arr: org.json.JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                list += FrameSpec(
                    path = item.optString("path", ""),
                    timeIso = item.optString("time_iso", ""),
                    frameClass = item.optString("class", "")
                )
            }
        }
        append(root.optJSONArray("observed"))
        append(root.optJSONArray("nowcast"))
        return list.filter { it.path.isNotBlank() }.takeLast(12)
    }

    private fun renderFrame(
        host: String,
        path: String,
        timeIso: String,
        frameClass: String,
        latitude: Double,
        longitude: Double,
        zoom: Int
    ): Bitmap {
        val tileXFloat = lonToTileX(longitude, zoom)
        val tileYFloat = latToTileY(latitude, zoom)
        val tileXBase = floor(tileXFloat).toInt()
        val tileYBase = floor(tileYFloat).toInt()
        val fracX = tileXFloat - floor(tileXFloat)
        val fracY = tileYFloat - floor(tileYFloat)

        val composite = Bitmap.createBitmap(768, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(composite)
        for (dy in -1..1) for (dx in -1..1) {
            val tile = downloadTile("${host}${path}/256/${zoom}/${tileXBase + dx}/${tileYBase + dy}/2/1_1.png")
            if (tile != null) canvas.drawBitmap(tile, ((dx + 1) * 256).toFloat(), ((dy + 1) * 256).toFloat(), null)
        }

        val cropLeft = (256 + fracX * 256 - 128).toInt().coerceIn(0, 512)
        val cropTop = (256 + fracY * 256 - 128).toInt().coerceIn(0, 512)
        val cropped = Bitmap.createBitmap(composite, cropLeft, cropTop, 256, 256)
        Canvas(cropped).also { overlay ->
            drawCrosshair(overlay, 128f, 128f)
            drawLabel(overlay, timeIso, frameClass)
        }
        return cropped
    }

    private fun downloadTile(url: String): Bitmap? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 7_000
            setRequestProperty("User-Agent", "MethodMesh-Weather")
        }
        return runCatching { connection.inputStream.use { input -> BitmapFactory.decodeStream(input) } }
            .getOrNull()
            .also { connection.disconnect() }
    }

    private fun drawCrosshair(canvas: Canvas, x: Float, y: Float) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 3f }
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#176B52") }
        canvas.drawLine(x - 16f, y, x + 16f, y, line)
        canvas.drawLine(x, y - 16f, x, y + 16f, line)
        canvas.drawCircle(x, y, 8f, line)
        canvas.drawCircle(x, y, 5f, dot)
    }

    private fun drawLabel(canvas: Canvas, timeIso: String, frameClass: String) {
        val bg = Paint().apply { color = 0xAA000000.toInt() }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 18f }
        canvas.drawRect(0f, 224f, 256f, 256f, bg)
        val stamp = runCatching { timestampFormatter.format(Instant.parse(timeIso)) }.getOrElse { timeIso }
        val cls = if (frameClass.equals("NOWCAST", ignoreCase = true)) "NOWCAST" else "OBSERVED"
        canvas.drawText("$cls  $stamp UTC", 12f, 245f, text)
    }

    private fun lonToTileX(longitude: Double, zoom: Int): Double = (longitude + 180.0) / 360.0 * 2.0.pow(zoom)
    private fun latToTileY(latitude: Double, zoom: Int): Double {
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val n = 2.0.pow(zoom)
        return (1.0 - ln(tan(latRad) + 1.0 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { String.format("%02x", it.toInt() and 0xFF) }
    }
}

private class SimpleGifWriter(
    private val out: OutputStream,
    private val width: Int,
    private val height: Int,
    private val loopCount: Int = 0
) : AutoCloseable {
    private val palette = ByteArray(256 * 3).apply {
        var i = 0
        for (r in 0 until 8) for (g in 0 until 8) for (b in 0 until 4) {
            this[i++] = ((r * 255) / 7).toByte()
            this[i++] = ((g * 255) / 7).toByte()
            this[i++] = ((b * 255) / 3).toByte()
        }
    }

    init {
        writeAscii("GIF89a")
        writeShort(width); writeShort(height)
        out.write(0xF7); out.write(0); out.write(0); out.write(palette)
        out.write(0x21); out.write(0xFF); out.write(11); writeAscii("NETSCAPE2.0")
        out.write(3); out.write(1); writeShort(loopCount); out.write(0)
    }

    fun writeFrame(bitmap: Bitmap, delayCs: Int) {
        val indexed = quantize(bitmap)
        out.write(0x21); out.write(0xF9); out.write(4); out.write(0); writeShort(delayCs); out.write(0); out.write(0)
        out.write(0x2C); writeShort(0); writeShort(0); writeShort(width); writeShort(height); out.write(0)
        out.write(8)
        writeSubBlocks(lzwEncode(indexed, 8))
    }

    override fun close() { out.write(0x3B); out.flush() }

    private fun quantize(bitmap: Bitmap): ByteArray {
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return ByteArray(pixels.size) { i ->
            val c = pixels[i]
            (((Color.red(c) shr 5) shl 5) or ((Color.green(c) shr 5) shl 2) or (Color.blue(c) shr 6)).toByte()
        }
    }

    private fun lzwEncode(indices: ByteArray, minCodeSize: Int): ByteArray {
        val clear = 1 shl minCodeSize
        val end = clear + 1
        var next = end + 1
        var codeSize = minCodeSize + 1
        val dict = HashMap<String, Int>(4096)
        for (i in 0 until clear) dict[i.toChar().toString()] = i
        val packed = BitPacker()
        packed.write(clear, codeSize)
        var w = (indices[0].toInt() and 0xFF).toChar().toString()
        for (i in 1 until indices.size) {
            val c = (indices[i].toInt() and 0xFF).toChar()
            val wc = w + c
            if (dict.containsKey(wc)) {
                w = wc
            } else {
                packed.write(dict.getValue(w), codeSize)
                if (next < 4096) {
                    dict[wc] = next++
                    if (next > (1 shl codeSize) - 1 && codeSize < 12) codeSize++
                } else {
                    packed.write(clear, codeSize)
                    dict.clear(); for (j in 0 until clear) dict[j.toChar().toString()] = j
                    codeSize = minCodeSize + 1; next = end + 1
                }
                w = c.toString()
            }
        }
        packed.write(dict.getValue(w), codeSize)
        packed.write(end, codeSize)
        return packed.toByteArray()
    }

    private fun writeSubBlocks(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val size = minOf(255, data.size - offset)
            out.write(size); out.write(data, offset, size); offset += size
        }
        out.write(0)
    }

    private fun writeAscii(text: String) = out.write(text.toByteArray(Charsets.US_ASCII))
    private fun writeShort(value: Int) { out.write(value and 0xFF); out.write((value shr 8) and 0xFF) }
}

private class BitPacker {
    private val out = ByteArrayOutputStream()
    private var current = 0
    private var bits = 0
    fun write(code: Int, size: Int) {
        var value = code
        repeat(size) {
            current = current or ((value and 1) shl bits)
            bits++; value = value shr 1
            if (bits == 8) { out.write(current); current = 0; bits = 0 }
        }
    }
    fun toByteArray(): ByteArray { if (bits > 0) out.write(current); return out.toByteArray() }
}
