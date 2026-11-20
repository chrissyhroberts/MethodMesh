package com.example.methodmesh.modules.imageredaction

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.Build
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.CancellationException

class PdfRedactionEngineTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(run: (File) -> Unit) {
        val folder = File(app.cacheDir, "pdf-redaction-test-${UUID.randomUUID()}").apply { mkdirs() }
        try { run(folder) } finally { folder.deleteRecursively() }
    }
    companion object {
        internal fun source(file: File, pages: Int = 2, size: Int = 400) {
            val document = PdfDocument()
            try {
                repeat(pages) { index ->
                    val page = document.startPage(PdfDocument.PageInfo.Builder(size, size, index + 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    val paint = Paint().apply { color = if (index == 0) Color.RED else Color.GREEN }
                    page.canvas.drawRect(40f, 40f, 160f, 160f, paint)
                    paint.color = Color.BLUE; page.canvas.drawRect(240f, 240f, 360f, 360f, paint)
                    paint.color = Color.BLACK; paint.textSize = 10f
                    page.canvas.drawText(if (index == 0) "REMOVE-SECRET-123" else "KEEP-VISIBLE", 45f, 100f, paint)
                    document.finishPage(page)
                }
                file.outputStream().use(document::writeTo)
            } finally { document.close() }
        }
    }
    @Test fun redactionRemovesSourceTextAndPixelsAcrossAllPagesWithoutChangingOriginal() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val before = input.readBytes()
        val output = File(folder, "redacted.pdf")
        assertEquals(2, PdfRedactionEngine.redact(input, listOf(PdfRedactionRegion(0, .1f, .1f, .4f, .4f)), output))
        output.copyTo(File(app.cacheDir, "pdf-redaction-review.pdf"), overwrite = true)
        assertArrayEquals(before, input.readBytes())
        if (Build.VERSION.SDK_INT >= 35) {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page -> assertTrue(page.textContents.joinToString { it.text }.contains("REMOVE-SECRET-123")) }
            } }
        }
        ParcelFileDescriptor.open(output, ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { renderer ->
            assertEquals(2, renderer.pageCount)
            repeat(renderer.pageCount) { index -> renderer.openPage(index).use { page ->
                assertEquals(400, page.width); assertEquals(400, page.height)
                // API 37 may return an empty-text placeholder rather than an empty list.
                if (Build.VERSION.SDK_INT >= 35) assertTrue("Output text on page $index: ${page.textContents.joinToString { it.text }}", page.textContents.all { it.text.isBlank() })
            } }
        } }
        val first = PdfRedactionEngine.preview(output, 0)
        val second = PdfRedactionEngine.preview(output, 1)
        try {
            // Preview is exactly twice the source point dimensions for this fixture.
            assertEquals(Color.BLACK, first.getPixel(200, 200))
            closeColor(Color.BLUE, first.getPixel(600, 600))
            closeColor(Color.GREEN, second.getPixel(200, 240))
            closeColor(Color.BLUE, second.getPixel(600, 600))
        } finally { first.recycle(); second.recycle() }
        val raw = output.readBytes().toString(Charsets.ISO_8859_1)
        listOf("REMOVE-SECRET-123", "/AcroForm", "/EmbeddedFiles", "/JavaScript", "/Annots", "/Prev").forEach { assertFalse(it, raw.contains(it)) }
        assertEquals(64, PdfRedactionEngine.sha256(output).length)
        assertNotEquals(PdfRedactionEngine.sha256(input), PdfRedactionEngine.sha256(output))
    }
    @Test fun fullPageRedactionIsOpaqueAndOtherPageMasksStaySeparate() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val output = File(folder, "redacted.pdf")
        PdfRedactionEngine.redact(input, listOf(PdfRedactionRegion(0, 0f, 0f, 1f, 1f), PdfRedactionRegion(1, .6f, .6f, .9f, .9f)), output)
        val first = PdfRedactionEngine.preview(output, 0); val second = PdfRedactionEngine.preview(output, 1)
        try {
            assertEquals(Color.BLACK, first.getPixel(10, 10)); assertEquals(Color.BLACK, first.getPixel(700, 700))
            assertEquals(Color.BLACK, second.getPixel(600, 600)); closeColor(Color.GREEN, second.getPixel(200, 240))
        } finally { first.recycle(); second.recycle() }
    }
    @Test fun noMasksAndOutOfRangeMasksFailBeforeCreatingAnOutput() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val output = File(folder, "redacted.pdf")
        assertTrue(runCatching { PdfRedactionEngine.redact(input, emptyList(), output) }.isFailure)
        assertFalse(output.exists())
        assertTrue(runCatching { PdfRedactionEngine.redact(input, listOf(PdfRedactionRegion(9, 0f, 0f, 1f, 1f)), output) }.isFailure)
        assertFalse(output.exists())
    }
    @Test fun existingFilesCannotBeOverwrittenIncludingTheOriginal() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val originalHash = PdfRedactionEngine.sha256(input)
        val mask = listOf(PdfRedactionRegion(0, 0f, 0f, 1f, 1f))
        assertTrue(runCatching { PdfRedactionEngine.redact(input, mask, input) }.isFailure)
        assertEquals(originalHash, PdfRedactionEngine.sha256(input))
        val existing = File(folder, "existing.pdf").apply { writeText("keep existing bytes") }
        assertTrue(runCatching { PdfRedactionEngine.redact(input, mask, existing) }.isFailure)
        assertEquals("keep existing bytes", existing.readText())
    }
    @Test fun cancellationNeverPublishesAPartialPdf() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val output = File(folder, "redacted.pdf")
        var checks = 0
        assertTrue(runCatching { PdfRedactionEngine.redact(input, listOf(PdfRedactionRegion(0, 0f, 0f, 1f, 1f)), output) {
            if (++checks == 2) throw CancellationException("fixture interruption")
        } }.exceptionOrNull() is CancellationException)
        assertFalse(output.exists()); assertEquals(2, PdfRedactionEngine.inspect(input))
    }
    @Test fun oversizedPagesAndExcessPageCountsAreRejected() = fixture { folder ->
        val oversized = File(folder, "oversized.pdf"); source(oversized, 1, 10000)
        assertTrue(runCatching { PdfRedactionEngine.inspect(oversized) }.isFailure)
        val many = File(folder, "many.pdf"); source(many, PdfRedactionEngine.MAX_PAGES + 1, 100)
        assertTrue(runCatching { PdfRedactionEngine.inspect(many) }.isFailure)
    }
    @Test fun phoneContentUriIsCopiedPrivatelyAndInvalidImportsLeaveNoWorkingFile() = fixture { folder ->
        val input = File(folder, "source.pdf"); source(input)
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", input)
        val copy = File(folder, "draft/source.pdf")
        assertEquals(2, PdfRedactionEngine.import(app, uri, copy).pageCount)
        assertArrayEquals(input.readBytes(), copy.readBytes())
        val bad = File(folder, "invalid.pdf").apply { writeText("not a PDF") }
        val failed = File(folder, "failed.pdf")
        assertTrue(runCatching { PdfRedactionEngine.import(app, FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", bad), failed) }.isFailure)
        assertFalse(failed.exists())
        assertTrue(runCatching { PdfRedactionEngine.import(app, android.net.Uri.fromFile(input), failed) }.isFailure)
        assertFalse(failed.exists())
    }
    private fun closeColor(expected: Int, actual: Int) {
        assertTrue("Expected ${Integer.toHexString(expected)} but found ${Integer.toHexString(actual)}",
            listOf(Color.red(expected) - Color.red(actual), Color.green(expected) - Color.green(actual),
                Color.blue(expected) - Color.blue(actual)).all { kotlin.math.abs(it) <= 2 })
    }
}
