package com.example.methodmesh.modules.digitalsigning

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Xml
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/** Small offline converter for text-based Word documents used by the signing workspace. */
object DigitalSigningDocumentConverter {
    fun isPdf(name: String, mimeType: String?): Boolean =
        mimeType.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)

    fun isSupportedWord(name: String, mimeType: String?): Boolean =
        mimeType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ignoreCase = true) ||
            name.endsWith(".docx", ignoreCase = true)

    fun convert(input: File, output: File, name: String, mimeType: String?) {
        require(isSupportedWord(name, mimeType)) { "Only PDF and DOCX files can be signed." }
        val text = input.inputStream().use(::extractDocxText)
        renderTextPdf(text, output)
    }

    private fun extractDocxText(input: InputStream): String {
        val documentXml = ZipInputStream(input).use { zip ->
            var found: ByteArray? = null
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "word/document.xml") {
                    found = zip.readBytes()
                    break
                }
            }
            found
        } ?: error("The Word document has no readable document body.")
        val parser = Xml.newPullParser().apply { setInput(documentXml.inputStream(), "UTF-8") }
        val text = StringBuilder()
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> when (parser.name) {
                    "t" -> text.append(parser.nextText())
                    "br", "cr" -> text.append('\n')
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> if (parser.name == "p") text.append('\n')
            }
            event = parser.next()
        }
        return text.toString().trim().ifBlank { "(The Word document contains no readable text.)" }
    }

    private fun renderTextPdf(text: String, output: File) {
        val pageWidth = 612
        val pageHeight = 792
        val margin = 48f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            textSize = 11f
        }
        val lines = text.lines().flatMap { line -> if (line.isBlank()) listOf("") else line.chunked(92) }
        val pdf = PdfDocument()
        var lineIndex = 0
        var pageNumber = 1
        while (lineIndex < lines.size) {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber++).create())
            val canvas: Canvas = page.canvas
            var y = margin + paint.textSize
            while (lineIndex < lines.size && y <= pageHeight - margin) {
                canvas.drawText(lines[lineIndex++], margin, y, paint)
                y += 16f
            }
            pdf.finishPage(page)
        }
        output.parentFile?.mkdirs()
        output.outputStream().use { pdf.writeTo(it) }
        pdf.close()
    }
}
