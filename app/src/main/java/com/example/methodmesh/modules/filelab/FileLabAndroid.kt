package com.example.methodmesh.modules.filelab

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object FileLabAndroid {
    private const val KOREADER_PACKAGE = "org.koreader.launcher"

    fun sourceInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        var size = -1L
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty().ifBlank { name }
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                }
            }
        }
        return name to size.coerceAtLeast(0L)
    }

    fun inspect(context: Context, uri: Uri, nameHint: String? = null): FileInspection {
        val info = sourceInfo(context, uri)
        val name = nameHint?.takeIf { it.isNotBlank() } ?: info.first
        val size = info.second.takeIf { it > 0 } ?: runCatching { context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: 0L }.getOrDefault(0L)
        return FileLabEngine.inspect(name, size) {
            requireNotNull(context.contentResolver.openInputStream(uri)) { "The selected file could not be opened." }
        }
    }

    fun convert(context: Context, sourceUri: Uri, sourceName: String, targetFormat: String, maxDimension: Int, jpegQuality: Int): ConversionArtifact {
        val inspection = inspect(context, sourceUri, sourceName)
        val route = requireNotNull(ConversionGraph.find(inspection.formatId, targetFormat.lowercase())) {
            "No executable File Lab route exists from ${inspection.formatId} to ${targetFormat.lowercase()}."
        }
        val directory = File(context.cacheDir, "filelab").apply { mkdirs() }
        val base = sourceName.substringBeforeLast('.', sourceName).sanitizeFileName().ifBlank { "filelab" }
        val output = uniqueFile(directory, "$base.${route.targetFormat}")
        try {
            when {
                route.sourceFormat == "cbz" && route.targetFormat == "pdf" -> AndroidComicPdfConverter.cbzToPdf(
                    openCbz = { requireNotNull(context.contentResolver.openInputStream(sourceUri)) },
                    output = output
                )
                route.sourceFormat == "pdf" && route.targetFormat == "cbz" -> {
                    val tempPdf = File.createTempFile("filelab_source_", ".pdf", context.cacheDir)
                    try {
                        context.contentResolver.openInputStream(sourceUri)?.use { input -> tempPdf.outputStream().use(input::copyTo) }
                            ?: error("The PDF could not be opened.")
                        AndroidComicPdfConverter.pdfToCbz(tempPdf, output, maxDimension, jpegQuality)
                    } finally { tempPdf.delete() }
                }
                route.targetFormat == "pdf" && ConversionGraph.isTextToPdf(route.sourceFormat) -> AndroidTextPdfConverter.textToPdf(
                    openText = { requireNotNull(context.contentResolver.openInputStream(sourceUri)) },
                    output = output
                )
                route.targetFormat == "pdf" && ConversionGraph.isImageToPdf(route.sourceFormat) -> AndroidImagePdfConverter.imageToPdf(
                    openImage = { requireNotNull(context.contentResolver.openInputStream(sourceUri)) },
                    output = output,
                    maxDimension = maxDimension
                )
                else -> error("Route exists but has no implementation: ${route.sourceFormat} -> ${route.targetFormat}")
            }
            require(output.exists() && output.length() > 0L) { "Conversion produced an empty output file." }
            val verified = FileLabEngine.inspect(output.name, output.length()) { output.inputStream() }
            require(verified.formatId == route.targetFormat) {
                "Conversion verification failed: requested ${route.targetFormat}, generated ${verified.formatId}."
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", output).toString()
            val mime = when (route.targetFormat) {
                "pdf" -> "application/pdf"
                "cbz" -> "application/vnd.comicbook+zip"
                else -> "application/octet-stream"
            }
            return ConversionArtifact(
                sourceFormat = inspection.formatId,
                targetFormat = route.targetFormat,
                outputName = output.name,
                outputUri = uri,
                outputMimeType = mime,
                outputSizeBytes = output.length(),
                outputSha256 = output.inputStream().use(FileLabEngine::sha256),
                warnings = listOfNotNull(route.notes)
            )
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    fun copyUri(context: Context, source: Uri, destination: Uri) {
        val input = requireNotNull(context.contentResolver.openInputStream(source)) { "Converted output could not be opened." }
        val output = requireNotNull(context.contentResolver.openOutputStream(destination, "w")) { "Destination could not be opened." }
        input.use { from -> output.use { to -> from.copyTo(to) } }
    }

    fun shareOutput(context: Context, uri: Uri, mimeType: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "File Lab output", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share converted file"))
    }

    fun tryTakeReadPermission(context: Context, uri: Uri) {
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    }


    fun installedOpenWithLabels(context: Context, uri: Uri, mimeType: String?): List<String> {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching {
            context.packageManager.queryIntentActivities(intent, 0)
                .map { it.loadLabel(context.packageManager).toString().trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sortedBy { it.lowercase() }
        }.getOrDefault(emptyList())
    }

    fun openWithChooser(context: Context, uri: Uri, mimeType: String?): Boolean {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (intent.resolveActivity(context.packageManager) == null) return false
        context.startActivity(Intent.createChooser(intent, "Open with"))
        return true
    }

    fun openReader(context: Context, uri: Uri, mimeType: String?): Boolean {
        val base = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeType ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val ko = Intent(base).setPackage(KOREADER_PACKAGE)
        if (ko.resolveActivity(context.packageManager) != null) {
            context.startActivity(ko)
            return true
        }
        if (base.resolveActivity(context.packageManager) != null) {
            context.startActivity(Intent.createChooser(base, "Read with"))
            return true
        }
        return false
    }

    private fun uniqueFile(directory: File, requestedName: String): File {
        var candidate = File(directory, requestedName)
        var index = 2
        while (candidate.exists()) {
            val stem = requestedName.substringBeforeLast('.')
            val extension = requestedName.substringAfterLast('.', "")
            candidate = File(directory, if (extension.isBlank()) "$stem-$index" else "$stem-$index.$extension")
            index++
        }
        return candidate
    }

    private fun String.sanitizeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
}

internal object AndroidTextPdfConverter {
    private const val MAX_TEXT_BYTES = 16 * 1024 * 1024
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 36f
    private const val FONT_SIZE = 10f
    private const val LINE_HEIGHT = 14f

    fun textToPdf(openText: () -> InputStream, output: File): File {
        val bytes = readBounded(openText(), MAX_TEXT_BYTES)
        val text = decode(bytes)
        require(text.isNotEmpty()) { "Text file is empty." }
        val wrapped = buildList {
            text.lineSequence().forEach { sourceLine ->
                val expanded = sourceLine.replace("\t", "    ")
                if (expanded.isEmpty()) add("") else addAll(wrap(expanded, 88))
            }
        }
        val linesPerPage = ((PAGE_HEIGHT - 2 * MARGIN) / LINE_HEIGHT).toInt().coerceAtLeast(1)
        val pdf = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = FONT_SIZE
            typeface = Typeface.MONOSPACE
        }
        try {
            wrapped.chunked(linesPerPage).forEachIndexed { pageIndex, lines ->
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create()
                val page = pdf.startPage(pageInfo)
                page.canvas.drawColor(Color.WHITE)
                var y = MARGIN + FONT_SIZE
                lines.forEach { line ->
                    page.canvas.drawText(line, MARGIN, y, paint)
                    y += LINE_HEIGHT
                }
                pdf.finishPage(page)
            }
            FileOutputStream(output).use(pdf::writeTo)
            return output
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally { pdf.close() }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray = input.use { source ->
        val out = java.io.ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(64 * 1024)
        var total = 0
        while (true) {
            val n = source.read(buffer)
            if (n <= 0) break
            total += n
            require(total <= maxBytes) { "Text-to-PDF is limited to ${maxBytes / (1024 * 1024)} MiB per file." }
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }

    private fun decode(bytes: ByteArray): String = when {
        bytes.size >= 3 && bytes[0] == 0xef.toByte() && bytes[1] == 0xbb.toByte() && bytes[2] == 0xbf.toByte() ->
            bytes.copyOfRange(3, bytes.size).toString(Charsets.UTF_8)
        bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16LE)
        bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() ->
            bytes.copyOfRange(2, bytes.size).toString(Charsets.UTF_16BE)
        else -> bytes.toString(Charsets.UTF_8)
    }

    private fun wrap(line: String, width: Int): List<String> {
        if (line.length <= width) return listOf(line)
        val result = mutableListOf<String>()
        var remaining = line
        while (remaining.length > width) {
            val candidate = remaining.take(width)
            val split = candidate.lastIndexOf(' ').takeIf { it >= width / 2 } ?: width
            result += remaining.take(split).trimEnd()
            remaining = remaining.drop(split).trimStart()
        }
        result += remaining
        return result
    }
}

internal object AndroidImagePdfConverter {
    private const val MAX_SOURCE_BYTES = 128L * 1024 * 1024
    private const val MAX_DECODED_PIXELS = 24_000_000L

    fun imageToPdf(openImage: () -> InputStream, output: File, maxDimension: Int = 2400): File {
        require(maxDimension in 256..12_000) { "maxDimension out of range" }
        val temp = File.createTempFile("filelab_image_", ".img", output.parentFile)
        try {
            openImage().use { input ->
                FileOutputStream(temp).use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        total += n
                        require(total <= MAX_SOURCE_BYTES) { "Image exceeds 128 MiB input safety limit." }
                        out.write(buffer, 0, n)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Android could not decode this image format." }
            var sample = 1
            while ((bounds.outWidth / sample).toLong() * (bounds.outHeight / sample).toLong() > MAX_DECODED_PIXELS ||
                maxOf(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("Android could not decode this image.")
            try {
                val scale = minOf(1f, maxDimension.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat())
                val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
                val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
                val pdf = PdfDocument()
                try {
                    val page = pdf.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
                    page.canvas.drawColor(Color.WHITE)
                    val dst = android.graphics.Rect(0, 0, width, height)
                    page.canvas.drawBitmap(bitmap, null, dst, null)
                    pdf.finishPage(page)
                    FileOutputStream(output).use(pdf::writeTo)
                } finally { pdf.close() }
            } finally { bitmap.recycle() }
            return output
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally { temp.delete() }
    }
}

internal object AndroidComicPdfConverter {
    private val imageExt = Regex(".*\\.(png|jpe?g|webp|gif)$", RegexOption.IGNORE_CASE)
    private const val MAX_PAGES = 20_000
    private const val MAX_PAGE_BYTES = 128L * 1024 * 1024
    private const val MAX_TOTAL_EXTRACTED_BYTES = 2L * 1024 * 1024 * 1024
    private const val MAX_DECODED_PIXELS = 24_000_000L

    fun cbzToPdf(openCbz: () -> InputStream, output: File, workDir: File = output.parentFile ?: output.absoluteFile.parentFile ?: error("Output file has no parent directory")): File {
        val temp = createTempDir(prefix = "filelab_cbz_", directory = workDir)
        try {
            val pages = mutableListOf<Pair<String, File>>()
            var totalExtracted = 0L
            openCbz().use { raw -> ZipInputStream(raw).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory || !imageExt.matches(entry.name)) continue
                    require(pages.size < MAX_PAGES) { "CBZ exceeds $MAX_PAGES page safety limit" }
                    val pageFile = File(temp, "p_${pages.size.toString().padStart(6, '0')}")
                    FileOutputStream(pageFile).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = zip.read(buffer)
                            if (n <= 0) break
                            total += n
                            totalExtracted += n
                            require(total <= MAX_PAGE_BYTES) { "CBZ page exceeds 128 MiB safety limit: ${entry.name}" }
                            require(totalExtracted <= MAX_TOTAL_EXTRACTED_BYTES) { "CBZ exceeds 2 GiB extracted-data safety limit" }
                            out.write(buffer, 0, n)
                        }
                    }
                    pages += entry.name to pageFile
                }
            } }
            pages.sortWith { a, b -> naturalCompare(a.first, b.first) }
            require(pages.isNotEmpty()) { "CBZ contains no supported image pages" }
            val pdf = PdfDocument()
            try {
                pages.forEachIndexed { index, (_, file) ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Cannot decode CBZ image page ${index + 1}" }
                    require(bounds.outWidth.toLong() * bounds.outHeight.toLong() <= MAX_DECODED_PIXELS) { "CBZ page ${index + 1} exceeds decoded-pixel safety limit" }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("Cannot decode CBZ image page ${index + 1}")
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                        val page = pdf.startPage(pageInfo)
                        page.canvas.drawColor(Color.WHITE)
                        page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                        pdf.finishPage(page)
                    } finally { bitmap.recycle() }
                }
                FileOutputStream(output).use(pdf::writeTo)
            } finally { pdf.close() }
            return output
        } catch (t: Throwable) {
            output.delete()
            throw t
        } finally { temp.deleteRecursively() }
    }

    fun pdfToCbz(pdfFile: File, output: File, maxDimension: Int = 2400, jpegQuality: Int = 92): File {
        require(maxDimension in 256..12_000) { "maxDimension out of range" }
        require(jpegQuality in 1..100) { "jpegQuality out of range" }
        try {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "PDF contains no pages" }
                    require(renderer.pageCount <= MAX_PAGES) { "PDF exceeds $MAX_PAGES page safety limit" }
                    ZipOutputStream(FileOutputStream(output)).use { zip ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { page ->
                                val scale = minOf(1f, maxDimension.toFloat() / maxOf(page.width, page.height).toFloat())
                                val width = (page.width * scale).toInt().coerceAtLeast(1)
                                val height = (page.height * scale).toInt().coerceAtLeast(1)
                                require(width.toLong() * height.toLong() <= MAX_DECODED_PIXELS) { "Rendered PDF page exceeds decoded-pixel safety limit" }
                                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                try {
                                    Canvas(bitmap).drawColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    zip.putNextEntry(ZipEntry("page_${(index + 1).toString().padStart(4, '0')}.jpg"))
                                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, zip)) { "JPEG encoding failed" }
                                    zip.closeEntry()
                                } finally { bitmap.recycle() }
                            }
                        }
                    }
                }
            }
            return output
        } catch (t: Throwable) {
            output.delete()
            throw t
        }
    }

    private fun naturalCompare(a: String, b: String): Int {
        val rx = Regex("(\\d+|\\D+)")
        val left = rx.findAll(a.lowercase()).map { it.value }.toList()
        val right = rx.findAll(b.lowercase()).map { it.value }.toList()
        for (i in 0 until minOf(left.size, right.size)) {
            val x = left[i]
            val y = right[i]
            val cmp = if (x.firstOrNull()?.isDigit() == true && y.firstOrNull()?.isDigit() == true) {
                val xn = x.trimStart('0').ifBlank { "0" }
                val yn = y.trimStart('0').ifBlank { "0" }
                if (xn.length != yn.length) xn.length.compareTo(yn.length) else xn.compareTo(yn)
            } else x.compareTo(y)
            if (cmp != 0) return cmp
        }
        return left.size.compareTo(right.size)
    }
}
