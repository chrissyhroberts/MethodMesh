package com.example.methodmesh.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnNamespaceProjectorTest {
    @Test
    fun `blank namespace keeps old keys`() {
        val fields = mapOf(
            "status" to "ok",
            "result" to "42",
            "methodmesh_full_json" to "{\"result\":\"42\"}"
        )

        assertEquals(fields, ReturnNamespaceProjector.fields(fields, ""))
    }

    @Test
    fun `namespace prefixes every flat field`() {
        val projected = ReturnNamespaceProjector.fields(
            mapOf(
                "status" to "ok",
                "result" to "42",
                "methodmesh_full_json" to "{\"result\":\"42\"}"
            ),
            "foo"
        )

        assertEquals("ok", projected["foo_status"])
        assertEquals("42", projected["foo_result"])
        assertEquals("{\"result\":\"42\"}", projected["foo_methodmesh_full_json"])
        assertFalse(projected.containsKey("status"))
        assertFalse(projected.containsKey("result"))
        assertFalse(projected.containsKey("methodmesh_full_json"))
    }

    @Test
    fun `canonical json value is not changed by namespace`() {
        val json = "{\"clinical_score\":\"3\",\"clinical_classification\":\"urgent\"}"
        val projected = ReturnNamespaceProjector.fields(
            mapOf("methodmesh_full_json" to json),
            "fourat"
        )

        assertEquals(json, projected["fourat_methodmesh_full_json"])
    }

    @Test
    fun `multiple namespaces can coexist without duplicate keys`() {
        val before = ReturnNamespaceProjector.fields(mapOf("redaction_result" to "ok", "methodmesh_full_json" to "{}"), "photo_before")
        val after = ReturnNamespaceProjector.fields(mapOf("redaction_result" to "ok", "methodmesh_full_json" to "{}"), "photo_after")
        val combinedKeys = before.keys + after.keys

        assertEquals(4, combinedKeys.size)
        assertTrue(combinedKeys.contains("photo_before_redaction_result"))
        assertTrue(combinedKeys.contains("photo_after_redaction_result"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `spaces are rejected`() {
        ReturnNamespaceProjector.validate("photo 1")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `periods are rejected`() {
        ReturnNamespaceProjector.validate("photo.one")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slashes are rejected`() {
        ReturnNamespaceProjector.validate("photo/one")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `leading digits are rejected`() {
        ReturnNamespaceProjector.validate("1photo")
    }
}
