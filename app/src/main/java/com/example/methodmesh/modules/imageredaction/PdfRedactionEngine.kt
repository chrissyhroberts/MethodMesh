package com.example.methodmesh.modules.imageredaction

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.security.MessageDigest
import kotlin.math.ceil

internal data class PdfRedactionDocument(val file: File, val pageCount: Int)

/** No source PDF objects, metadata, text streams, annotations or attachments enter the new document. */
internal object PdfRedactionEngine {
    const val DPI = 150
    const val MAX_PAGES = 50
    const val MAX_SOURCE_BYTES = 64L * 1024 * 1024
    private const val MAX_TOTAL_PIXELS = 40_000_000L
    private const val MAX_PAGE_PIXELS = 12_000_000L

    fun import(context: Context, uri: Uri, destination: File, checkActive: () -> Unit = {}): PdfRedactionDocument {
        require(uri.scheme == "content") { "Choose a PDF using the phone's file picker or supply a readable content URI." }
        check(!destination.exists()) { "The working document already exists" }
        try {
            destination.parentFile?.mkdirs()
            val input = context.contentResolver.openInputStream(uri) ?: error("The selected PDF cannot be read")
            input.use { source -> destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    checkActive()
                    val count = source.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_SOURCE_BYTES) { "This PDF exceeds the 64 MB working-file limit. Split it first." }
                    output.write(buffer, 0, count)
                }
            } }
            return PdfRedactionDocument(destination, inspect(destination))
        } catch (error: Throwable) { destination.delete(); throw error }
    }

    fun inspect(file: File): Int = withRenderer(file) { renderer ->
        require(renderer.pageCount in 1..MAX_PAGES) { "PDF redaction supports 1–50 pages. Split this document first." }
        var pixels = 0L
        for (index in 0 until renderer.pageCount) renderer.openPage(index).use { page ->
            val (width, height) = dimensions(page, DPI / 72.0)
            val count = width.toLong() * height
            require(count <= MAX_PAGE_PIXELS) { "This PDF contains an oversized page. Resize or split it first." }
            pixels += count
            require(pixels <= MAX_TOTAL_PIXELS) { "This PDF is too large to redact safely on this device. Split it into smaller documents first." }
        }
        renderer.pageCount
    }

    fun preview(file: File, pageIndex: Int): Bitmap = withRenderer(file) { renderer ->
        require(pageIndex in 0 until renderer.pageCount) { "Page is unavailable" }
        renderer.openPage(pageIndex).use { page -> render(page, minOf(2.0, 1800.0 / maxOf(page.width, page.height))) }
    }

    fun redact(source: File, regions: List<PdfRedactionRegion>, output: File, checkActive: () -> Unit = {}): Int {
        require(source.canonicalFile != output.canonicalFile) { "The original PDF cannot be overwritten" }
        require(!output.exists()) { "The output file already exists" }
        require(regions.isNotEmpty() && regions.size <= PdfRedactionRegions.MAX_REGIONS) { "Mark at least one region to remove" }
        val count = inspect(source)
        require(regions.all { it.page < count }) { "A redaction region refers to a missing page" }
        output.parentFile?.mkdirs()
        try {
            val document = PdfDocument()
            try { withRenderer(source) { renderer ->
                for (index in 0 until count) {
                    checkActive()
                    renderer.openPage(index).use { page ->
                        val bitmap = render(page, DPI / 72.0)
                        try {
                            val canvas = Canvas(bitmap)
                            val paint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL; isAntiAlias = false }
                            regions.filter { it.page == index }.forEach { region ->
                                val r = region.pixels(bitmap.width, bitmap.height)
                                canvas.drawRect(r[0].toFloat(), r[1].toFloat(), r[2].toFloat(), r[3].toFloat(), paint)
                            }
                            // Only the already-masked bitmap is ever drawn into the new PDF.
                            val target = document.startPage(PdfDocument.PageInfo.Builder(page.width, page.height, index + 1).create())
                            try {
                                target.canvas.drawColor(Color.WHITE)
                                target.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, page.width.toFloat(), page.height.toFloat()), null)
                            } finally { document.finishPage(target) }
                        } finally { bitmap.recycle() }
                    }
                }
                checkActive()
                output.outputStream().use(document::writeTo)
            } } finally { document.close() }
            checkActive()
            check(withRenderer(output) { it.pageCount } == count) { "The redacted PDF could not be verified" }
            return count
        } catch (error: Throwable) { output.delete(); throw error }
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun dimensions(page: PdfRenderer.Page, scale: Double): Pair<Int, Int> {
        require(page.width > 0 && page.height > 0)
        return ceil(page.width * scale).toInt().coerceAtLeast(1) to ceil(page.height * scale).toInt().coerceAtLeast(1)
    }
    private fun render(page: PdfRenderer.Page, scale: Double): Bitmap {
        val (width, height) = dimensions(page, scale)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return bitmap
        } catch (error: Throwable) { bitmap.recycle(); throw error }
    }
    private fun <T> withRenderer(file: File, block: (PdfRenderer) -> T): T =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor -> PdfRenderer(descriptor).use(block) }
}
