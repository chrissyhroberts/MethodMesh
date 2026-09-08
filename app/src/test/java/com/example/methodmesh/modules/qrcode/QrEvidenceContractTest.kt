package com.example.methodmesh.modules.qrcode

import com.example.methodmesh.core.methodmesh.TransformationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class QrEvidenceContractTest {
    @Test
    fun `barcode scan exposes generic canonical evidence fields`() {
        val request = As100BarcodeScanMethod.request(
            action = As100BarcodeScanMethod.ID,
            context = mapOf("barcode_payload" to "lskdfjslkdfj", "barcode_format" to "CODE_128")
        )

        val result = As100BarcodeScanMethod.execute(request, settingsState = null, transport = "test")
        val fields = result.observations.single().values

        assertEquals(TransformationStatus.Succeeded, result.status)
        assertEquals("lskdfjslkdfj", fields["barcode_payload"])
        assertEquals(BarcodePayloadSemantics.KIND_TEXT, fields["barcode_payload_kind"])
        assertEquals("CODE_128", fields["barcode_format"])
        assertEquals(BarcodeEvidenceFields.FORMAT, fields[BarcodeEvidenceFields.FORMAT_FIELD])
        assertEquals(
            "1fd9d6dcca50ec7ff96337ee3b2a1e62dc389ea391c31d4c3be5e570ec35558a",
            fields[BarcodeEvidenceFields.HASH_FIELD]
        )
        assertNull(fields["barcode_payload_url"])
        assertFalse(fields.containsKey("qr_payload"))
    }

    @Test
    fun `safe HTTP payload is returned unchanged as a link`() {
        val payload = "https://example.org/specimens/0042?view=full"
        val request = As100BarcodeScanMethod.request(
            action = As100BarcodeScanMethod.ID,
            context = mapOf("barcode_payload" to payload, "barcode_format" to "QR_CODE")
        )

        val fields = As100BarcodeScanMethod.execute(request, null, "test").observations.single().values

        assertEquals(payload, fields["barcode_payload"])
        assertEquals(BarcodePayloadSemantics.KIND_URL, fields["barcode_payload_kind"])
        assertEquals(payload, fields["barcode_payload_url"])
    }

    @Test
    fun `unsafe schemes and leading zero identifiers remain exact text`() {
        assertNull(BarcodePayloadSemantics.safeHttpUrl("javascript:alert(1)"))
        assertNull(BarcodePayloadSemantics.safeHttpUrl(" https://example.org"))

        val payload = "0012345678905"
        val request = As100BarcodeScanMethod.request(
            action = As100BarcodeScanMethod.ID,
            context = mapOf("barcode_payload" to payload, "barcode_format" to "EAN_13")
        )
        val fields = As100BarcodeScanMethod.execute(request, null, "test").observations.single().values

        assertEquals(payload, fields["barcode_payload"])
        assertEquals(BarcodePayloadSemantics.KIND_TEXT, fields["barcode_payload_kind"])
    }

    @Test
    fun `decoded whitespace is preserved rather than treated as missing`() {
        val request = As100BarcodeScanMethod.request(
            action = As100BarcodeScanMethod.ID,
            context = mapOf("barcode_payload" to "  ", "barcode_format" to "QR_CODE")
        )

        val result = As100BarcodeScanMethod.execute(request, null, "test")

        assertEquals(TransformationStatus.Succeeded, result.status)
        assertEquals("  ", result.observations.single().values["barcode_payload"])
    }

    @Test
    fun `deprecated qr scan preserves its request identity and legacy result fields`() {
        val request = As100QrScanMethod.request(
            action = As100QrScanMethod.ID,
            context = mapOf("qr_payload" to "legacy-token", "barcode_format" to "QR_CODE")
        )

        val result = As100QrScanMethod.execute(request, null, "test")
        val fields = result.observations.single().values

        assertEquals(As100QrScanMethod.ID, result.request.method.id.value)
        assertEquals("legacy-token", fields["qr_payload"])
        assertEquals(QrEvidenceFields.FORMAT, fields[QrEvidenceFields.FORMAT_FIELD])
        assertFalse(fields.containsKey("barcode_payload"))
    }

    @Test
    fun `all scanner RIL phrases resolve to the canonical barcode method`() {
        QrCodeModule.rilBindings().forEach { binding ->
            assertEquals(As100BarcodeScanMethod.ID, binding.actionId)
        }
    }
}
