package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeEvidenceContractTest {
    @Test
    fun `barcode scan exposes generic canonical evidence fields`() {
        val request = As100BarcodeScanMethod.request(
            action = As100BarcodeScanMethod.ID,
            context = mapOf("barcode_payload" to "lskdfjslkdfj")
        )

        val result = As100BarcodeScanMethod.execute(request, settingsState = null, transport = "test")
        val fields = result.observations.single().values

        assertEquals(TransformationStatus.Succeeded, result.status)
        assertEquals(BarcodeEvidenceFields.FORMAT, fields[BarcodeEvidenceFields.FORMAT_FIELD])
        assertEquals(
            "1fd9d6dcca50ec7ff96337ee3b2a1e62dc389ea391c31d4c3be5e570ec35558a",
            fields[BarcodeEvidenceFields.HASH_FIELD]
        )
    }
}
