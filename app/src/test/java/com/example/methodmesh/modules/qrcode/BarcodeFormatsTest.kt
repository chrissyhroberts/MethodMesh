package com.example.methodmesh.modules.qrcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BarcodeFormatsTest {
    @Test
    fun `missing restriction leaves ZXing in automatic all-format mode`() {
        assertNull(barcodeFormats(null))
        assertNull(barcodeFormats(""))
    }

    @Test
    fun `explicit restrictions are normalized and deduplicated`() {
        assertEquals(
            listOf("DATA_MATRIX", "CODE_128"),
            barcodeFormats("data_matrix| CODE_128 |data_matrix")
        )
    }
}
