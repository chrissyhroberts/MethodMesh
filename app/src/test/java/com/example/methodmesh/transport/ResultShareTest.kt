package com.example.methodmesh.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultShareTest {
    @Test fun textOnlyResultsRetainTheirMessageAndAuditText() {
        val text = ResultShare.buildShareText("Useful result", "{\"audit\":true}")
        assertEquals(text, ResultShare.messageTextForShare(text, 0))
    }
    @Test fun filesNeverAdvertiseACompetingFilenameSummaryAsMessageText() {
        val text = ResultShare.buildShareText("Proof ZIP: proof.zip", "{\"audit\":true}")
        for (files in listOf(1, 2, 5)) assertEquals(null, ResultShare.messageTextForShare(text, files))
        assertEquals("Proof ZIP: proof.zip\n\nmetadata.json\n{\"audit\":true}", text)
    }
    @Test fun blankTextDoesNotCreateAnEmptyMessageRoute() {
        assertEquals(null, ResultShare.messageTextForShare("", 0))
    }

    @Test
    fun `shareable media attachments include uri arrays from document scan fields`() {
        val fields = mapOf(
            "document_scan_ocr_text" to "Page 1\nOCR text",
            "document_scan_page_image_uris_json" to "[\"content://com.example.methodmesh.fileprovider/shared_cache/page-1.jpg\"]",
            "document_scan_searchable_pdf_uri" to "content://com.example.methodmesh.fileprovider/shared_cache/searchable.pdf"
        )

        val attachments = ResultShare.shareableMediaAttachmentValues(fields)

        assertEquals(
            listOf(
                "content://com.example.methodmesh.fileprovider/shared_cache/searchable.pdf"
            ),
            attachments.map { it.uri }
        )
        assertEquals(
            listOf("document_scan_searchable_pdf.pdf"),
            attachments.map { it.name }
        )
    }

    @Test
    fun `document scan falls back to scanner pdf when searchable pdf is absent`() {
        val fields = mapOf(
            "document_scan_pdf_uri" to "content://com.example.methodmesh/file/scanner.pdf",
            "document_scan_searchable_pdf_uri" to ""
        )

        assertEquals(
            listOf("content://com.example.methodmesh/file/scanner.pdf"),
            ResultShare.shareableMediaAttachmentValues(fields).map { it.uri }
        )
    }
}
