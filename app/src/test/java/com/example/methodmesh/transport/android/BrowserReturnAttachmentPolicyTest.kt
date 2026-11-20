package com.example.methodmesh.transport.android

import org.junit.Assert.*
import org.junit.Test

class BrowserReturnAttachmentPolicyTest {
    @Test fun onlyActualReturnedAttachmentsCanTriggerPublicMediaPublication() {
        val fields = mapOf(
            "document_uri" to "content://example.test/document.pdf",
            "page_uris_json" to """["content://example.test/page1.jpg","content://example.test/page2.jpg"]""",
            "document_audit_json" to """{"input_uri":"content://private.test/unrelated"}""",
            "methodmesh_full_json" to """{"uri":"content://private.test/full"}""",
            "value" to "content://private.test/metadata"
        )
        val discovered = BrowserReturnBridge.collectContentUriFields(fields)
        assertEquals(setOf("content://example.test/document.pdf", "content://example.test/page1.jpg", "content://example.test/page2.jpg"), discovered.keys)
        assertEquals(listOf("document_uri"), discovered["content://example.test/document.pdf"])
    }

    @Test fun exactUriValuesPreserveEncodedCharactersAndTrackAliasesWithoutDuplicateCopies() {
        val uri = "content://example.test/path%20with%20spaces/file%2Cname.pdf?version=1"
        val discovered = BrowserReturnBridge.collectContentUriFields(mapOf("document_uri" to uri, "attachment_uris_json" to "[\"$uri\"]"))
        assertEquals(setOf(uri), discovered.keys)
        assertEquals(listOf("document_uri", "attachment_uris_json"), discovered[uri])
    }
}
