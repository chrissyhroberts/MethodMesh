package com.example.methodmesh.modules.documentscanner

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipFile

class DocumentScannerOdkFormContractTest {
    @Test
    fun `document scanner example returns document media text and audit json`() {
        val docs = listOf(
            File("src/main/java/com/example/methodmesh/modules/documentscanner/docs"),
            File("app/src/main/java/com/example/methodmesh/modules/documentscanner/docs")
        ).firstOrNull(File::isDirectory) ?: error("Cannot locate document-scanner docs")
        val workbook = File(docs, "example_odk_document.scan.xlsx")

        assertTrue("Expected example_odk_document.scan.xlsx", workbook.isFile)

        val xml = ZipFile(workbook).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml") }
                .joinToString("\n") { entry ->
                    zip.getInputStream(entry).bufferedReader().readText()
                }
        }

        assertTrue("Workbook must invoke document.scan", "method_id='document.scan'" in xml)
        assertTrue("Workbook must request full payload metadata", "input_payload_mode='FULL'" in xml)
        assertTrue("Workbook must return the searchable PDF URI", ">document_scan_searchable_pdf_uri<" in xml)
        assertTrue("Workbook must return OCR text", ">document_scan_ocr_text<" in xml)
        assertTrue("Workbook must return the background audit JSON", ">methodmesh_full_json<" in xml)
        assertTrue("Workbook must save the canonical form title", ">document.scan<" in xml)
    }
}
