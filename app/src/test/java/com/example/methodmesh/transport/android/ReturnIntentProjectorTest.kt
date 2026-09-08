package com.example.methodmesh.transport.android

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReturnIntentProjectorTest {
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
