package com.example.methodmesh.transport

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ExportAttachmentProjectionTest {
    private fun plan(fields: Map<String, Any?>) = ExportAttachmentProjection.plan(fields) { field, _ -> "$field.pdf" }

    @Test fun `scalar aliases share the single copied filename`() {
        val plan = plan(linkedMapOf("document_uri" to "content://test/a", "alias_uri" to "content://test/a"))
        assertEquals(1, plan.attachments.size)
        assertEquals(plan.attachments.single().filename, plan.fields.getString("document_uri"))
        assertEquals(plan.fields.getString("document_uri"), plan.fields.getString("alias_uri"))
    }

    @Test fun `declared media arrays reference actual copied names including scalar aliases`() {
        val plan = plan(linkedMapOf("photo_uri" to "content://test/a", "photo_uris_json" to "[\"content://test/a\",\"content://test/b\",\"caption\"]"))
        assertEquals(2, plan.attachments.size)
        val array = JSONArray(plan.fields.getString("photo_uris_json"))
        assertEquals(plan.attachments[0].filename, array.getString(0))
        assertEquals(plan.attachments[1].filename, array.getString(1))
        assertEquals("caption", array.getString(2))
    }

    @Test fun `native scanner exclusions do not become fabricated filenames`() {
        val plan = plan(mapOf("document_scan_searchable_pdf_uri" to "content://test/searchable",
            "document_scan_pdf_uri" to "content://test/original", "document_scan_page_image_uris_json" to "[\"content://test/page\"]"))
        assertEquals(1, plan.attachments.size)
        assertEquals("content://test/original", plan.fields.getString("document_scan_pdf_uri"))
        assertEquals("content://test/page", JSONArray(plan.fields.getString("document_scan_page_image_uris_json")).getString(0))
    }

    @Test fun `canonical evidence and domain JSON are not recursively rewritten`() {
        val audit = "{\"original\":\"content://test/a\"}"
        val plan = plan(mapOf("file_uri" to "content://test/a", "methodmesh_full_json" to audit, "answers_json" to audit))
        assertEquals(audit, plan.fields.getString("methodmesh_full_json"))
        assertEquals(audit, plan.fields.getString("answers_json"))
    }

    @Test fun `typed non media fields and null remain typed`() {
        val plan = plan(mapOf("count" to 42, "ok" to true, "missing" to null, "nested" to JSONObject().put("answer", 3)))
        assertEquals(42, plan.fields.getInt("count"))
        assertTrue(plan.fields.getBoolean("ok"))
        assertTrue(plan.fields.isNull("missing"))
        assertEquals(3, plan.fields.getJSONObject("nested").getInt("answer"))
        assertTrue(plan.attachments.isEmpty())
    }

    @Test fun `generic declared URI lists are recognised without media words in their names`() {
        val plan = plan(mapOf("document_page_uris_json" to "[\"content://test/a\",\"content://test/b\"]"))
        assertEquals(2, plan.attachments.size)
        val values = JSONArray(plan.fields.getString("document_page_uris_json"))
        assertEquals(plan.attachments[0].filename, values.getString(0))
        assertEquals(plan.attachments[1].filename, values.getString(1))
    }

    @Test fun `returned clipdata references every selected file without crawling audit JSON`() {
        val plan = plan(mapOf("result_clipdata" to "content://test/a\ncontent://test/b", "methodmesh_full_json" to "{\"uri\":\"content://test/private\"}"))
        assertEquals(2, plan.attachments.size)
        assertEquals(plan.attachments.joinToString("\n") { it.filename }, plan.fields.getString("result_clipdata"))
    }

    @Test fun `unselected scanner lists and nullable clipdata are preserved exactly`() {
        val pages = "[ \"content://test/page\" ]"
        val plan = plan(mapOf("document_scan_page_image_uris_json" to pages, "result_clipdata" to null))
        assertEquals(pages, plan.fields.getString("document_scan_page_image_uris_json"))
        assertTrue(plan.fields.isNull("result_clipdata"))
    }
}
