package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class QrEvidenceContractTest {
    @Test
    fun `QR scan exposes generic canonical evidence fields`() {
        val request = As100QrScanMethod.request(
            action = As100QrScanMethod.ID,
            context = mapOf("qr_payload" to "lskdfjslkdfj")
        )

        val result = As100QrScanMethod.execute(request, settingsState = null, transport = "test")
        val fields = result.observations.single().values

        assertEquals(TransformationStatus.Succeeded, result.status)
        assertEquals(QrEvidenceFields.FORMAT, fields[QrEvidenceFields.FORMAT_FIELD])
        assertEquals(
            "1fd9d6dcca50ec7ff96337ee3b2a1e62dc389ea391c31d4c3be5e570ec35558a",
            fields[QrEvidenceFields.HASH_FIELD]
        )
    }
}
