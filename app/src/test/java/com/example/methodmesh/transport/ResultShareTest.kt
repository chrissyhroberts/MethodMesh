package com.example.methodmesh.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class ResultShareTest {
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
                "content://com.example.methodmesh.fileprovider/shared_cache/page-1.jpg",
                "content://com.example.methodmesh.fileprovider/shared_cache/searchable.pdf"
            ),
            attachments.map { it.uri }
        )
        assertEquals(
            listOf("document_scan_page_image_1.jpg", "document_scan_searchable_pdf.pdf"),
            attachments.map { it.name }
        )
    }
}
