package com.example.methodmesh.transport.android

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnIntentProjectorTest {
    @Test fun androidUriPathsWithLiteralSpacesAndUnicodeRemainUnchanged() {
        val uri = "content://example.test/field note α.pdf?revision=1"
        val projected = ReturnIntentProjector.projectFlatReturn(mapOf("document_uri" to uri), "")
        assertEquals(uri, projected.extras["document_uri"])
        assertEquals(listOf(ProjectedClipUri("document_uri", uri)), projected.clipUris)
    }

    @Test fun providerClipDataLinesReceiveReadGrantsWithoutGrantingHttpOrPlainText() {
        val clips = "content://example.test/one\nSome text\nhttps://example.test/two\ncontent://example.test/two"
        val projected = ReturnIntentProjector.projectFlatReturn(mapOf("provider_returned_clipdata" to clips), "")
        assertEquals(clips, projected.extras["provider_returned_clipdata"])
        assertEquals(listOf("content://example.test/one", "content://example.test/two"), projected.clipUris.map { it.uri })
    }

    @Test fun uriListOutputsGrantEveryAttachmentWithoutRewritingTheField() {
        val pages = """["content://example.test/page1.jpg","content://example.test/page2.jpg"]"""
        val projected = ReturnIntentProjector.projectFlatReturn(mapOf("document_scan_page_image_uris_json" to pages), "scan")
        assertEquals(pages, projected.extras["scan_document_scan_page_image_uris_json"])
        assertEquals(listOf("content://example.test/page1.jpg", "content://example.test/page2.jpg"), projected.clipUris.map { it.uri })
        assertTrue(projected.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun duplicateStreamsAreGrantedOnceAndAllPublicFieldsRemainIntact() {
        val uri = "content://example.test/page1.jpg"
        val fields = linkedMapOf("image_uri" to uri, "page_uris" to "[\"$uri\",\"$uri\"]")
        val projected = ReturnIntentProjector.projectFlatReturn(fields, "")
        assertEquals(fields, projected.extras)
        assertEquals(listOf(ProjectedClipUri("image_uri", uri)), projected.clipUris)
    }

    @Test fun nestedAuditUrisAndOrdinaryJsonDoNotBecomeAttachmentGrants() {
        val fields = mapOf(
            "methodmesh_full_json" to """{"request":{"uri":"content://private.test/secret"}}""",
            "notes_json" to """["content://private.test/notes"]""",
            "page_uris_json" to """[{"uri":"content://private.test/nested"}]"""
        )
        val projected = ReturnIntentProjector.projectFlatReturn(fields, "")
        assertEquals(fields, projected.extras)
        assertTrue(projected.clipUris.isEmpty())
        assertEquals(0, projected.flags)
    }

    @Test fun malformedListsAndNonContentSchemesDoNotGrantAccess() {
        val fields = mapOf(
            "file_uris" to """["file:///data/private.pdf","https://example.test/file","content://",null,17]""",
            "page_uris_json" to "[not valid JSON",
            "image_uri" to "content://example.test/bad\npath"
        )
        val projected = ReturnIntentProjector.projectFlatReturn(fields, "")
        assertEquals(fields, projected.extras)
        assertTrue(projected.clipUris.isEmpty())
    }

    @Test fun compactReturnPreservesAttachmentListsWithoutWideningOtherFields() {
        val pages = """["content://example.test/page1.jpg","content://example.test/page2.jpg"]"""
        val target = mutableMapOf<String, String?>("value" to "Compact", "methodmesh_full_json" to "original evidence")
        ExternalReturnAttachments.preserveFields(mapOf("page_uris_json" to pages, "private_detail" to "not selected"), target)
        assertEquals(pages, target["page_uris_json"])
        assertEquals("original evidence", target["methodmesh_full_json"])
        assertTrue("private_detail" !in target)
    }

    @Test
    fun contentUriBinaryArtifactIsAddedToClipDataPlan() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf(
                "redacted_image_uri" to "content://com.example.methodmesh/redacted.jpg",
                "redacted_image_name" to "redacted.jpg"
            ),
            namespace = "photo"
        )

        assertEquals(
            listOf(ProjectedClipUri("photo_redacted_image_uri", "content://com.example.methodmesh/redacted.jpg")),
            projected.clipUris
        )
    }

    @Test
    fun dynamicContentUriFieldIsAddedToClipDataPlan() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf(
                "site_sketch" to "content://com.example.methodmesh/paperbridge-field-site_sketch.jpg",
                "participant_id" to "P001"
            ),
            namespace = ""
        )

        assertEquals(
            listOf(ProjectedClipUri("site_sketch", "content://com.example.methodmesh/paperbridge-field-site_sketch.jpg")),
            projected.clipUris
        )
        assertEquals("content://com.example.methodmesh/paperbridge-field-site_sketch.jpg", projected.extras["site_sketch"])
    }

    @Test
    fun contentUriBinaryArtifactGrantsReadPermission() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf("audio_file_uri" to "content://com.example.methodmesh/audio.m4a"),
            namespace = ""
        )

        assertTrue(projected.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test
    fun namespacedReturnKeyRemainsCorrect() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf(
                "redacted_image_uri" to "content://com.example.methodmesh/redacted.jpg",
                "value" to "content://com.example.methodmesh/redacted.jpg"
            ),
            namespace = "mm_image"
        )

        assertEquals("content://com.example.methodmesh/redacted.jpg", projected.extras["mm_image_redacted_image_uri"])
        assertEquals("content://com.example.methodmesh/redacted.jpg", projected.extras["mm_image_value"])
    }

    @Test
    fun ordinaryNonFileFlatReturnsAreUnchangedAndDoNotGrantUriAccess() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf(
                "barcode_payload" to "50375400",
                "temperature_c" to "23.2",
                "api_url" to "https://example.org/feed.json"
            ),
            namespace = "result"
        )

        assertEquals("50375400", projected.extras["result_barcode_payload"])
        assertEquals("23.2", projected.extras["result_temperature_c"])
        assertEquals("https://example.org/feed.json", projected.extras["result_api_url"])
        assertTrue(projected.clipUris.isEmpty())
        assertEquals(0, projected.flags)
    }

    @Test
    fun ordinaryNonFileFlatReturnsRemainUnprefixedWithoutNamespace() {
        val projected = ReturnIntentProjector.projectFlatReturn(
            fields = linkedMapOf(
                "barcode_payload" to "50375400",
                "temperature_c" to "23.2"
            ),
            namespace = ""
        )

        assertEquals(setOf("barcode_payload", "temperature_c"), projected.extras.keys)
        assertEquals("50375400", projected.extras["barcode_payload"])
        assertEquals("23.2", projected.extras["temperature_c"])
        assertTrue(projected.clipUris.isEmpty())
    }
}
