package com.example.methodmesh.modules.imageredaction

import org.junit.Assert.*
import org.junit.Test
import com.example.methodmesh.transport.ril.RilTransportAdapter

class RedactionSourceContractsTest {
    @Test fun flatSavedPhotoInputDoesNotFallBackToCamera() {
        val parsed = RilTransportAdapter.parse(mapOf("method_id" to "image.redact", "input_source" to "file_picker"), "test")
        assertEquals("file_picker", imageRedactionInputSettings(parsed.settings)["input_source"])
    }
    @Test fun canonicalAndPrefixedPhotoSettingsKeepTheirEstablishedValues() {
        for (key in listOf("source", "input_source", "input_input_source")) {
            assertEquals("camera", imageRedactionInputSettings(mapOf(key to "camera"))["input_source"])
            assertEquals("file_picker", imageRedactionInputSettings(mapOf(key to "file_picker"))["input_source"])
        }
        assertEquals("12", imageRedactionInputSettings(mapOf("input_grid_rows" to "12"))["grid_rows"])
    }
    @Test fun explicitCanonicalSourceWinsOverAnAlias() {
        assertEquals("file_picker", imageRedactionInputSettings(mapOf("input_source" to "file_picker", "source" to "camera"))["input_source"])
    }
    @Test fun scannerAcquisitionDisablesUnredactedTextAndTimestampSidecars() {
        assertEquals("document.scan", RedactionScanContract.METHOD_ID)
        for (key in listOf("run_ocr", "return_searchable_pdf", "return_text_file")) assertEquals("false", RedactionScanContract.settings[key])
        assertEquals("disabled", RedactionScanContract.settings["timestamp_policy"])
    }
    @Test fun scannerContractConsumesOnlyTheSuccessfulPdfNotOtherAttachments() {
        assertEquals("content://fixture/scan.pdf", RedactionScanContract.pdfUri(mapOf("document_scan_status" to "succeeded",
            "methodmesh_status" to "Succeeded", "document_scan_pdf_uri" to "content://fixture/scan.pdf",
            "document_scan_ocr_text" to "sensitive", "document_scan_page_image_uris_json" to "[unredacted]")))
    }
    @Test fun cancelledFailedMissingOrNonContentScannerResultsCannotBecomeAnOutput() {
        for (fields in listOf(emptyMap(), mapOf("document_scan_status" to "failed", "document_scan_pdf_uri" to "content://fixture/scan.pdf"),
            mapOf("document_scan_status" to "succeeded", "methodmesh_status" to "Failed", "document_scan_pdf_uri" to "content://fixture/scan.pdf"),
            mapOf("document_scan_status" to "succeeded", "document_scan_pdf_uri" to "file:///private/source.pdf"))) {
            assertTrue(runCatching { RedactionScanContract.pdfUri(fields) }.isFailure)
        }
    }
}
