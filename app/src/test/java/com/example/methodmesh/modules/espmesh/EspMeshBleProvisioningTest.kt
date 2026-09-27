package com.example.methodmesh.modules.espmesh

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class EspMeshBleProvisioningTest {
    private fun fixture() = JSONObject(javaClass.getResource("/espmesh_config_ack.json")!!.readText())
    private fun ack() = EspMeshBridgeFrame.fromJson(fixture().getJSONObject("frame"))

    @Test fun firmwareFragmentsReassembleAtBenchMtu() {
        val fixture = fixture()
        val packets = fixture.getJSONArray("packets")
        val reassembler = EspMeshBlePacketCodec.Reassembler()
        var completed: ByteArray? = null
        // Out of order and duplicate delivery must still produce the exact typed frame.
        assertNull(reassembler.accept(packets.getString(0).toByteArray()))
        for (i in packets.length() - 1 downTo 0) {
            val packet = packets.getString(i).toByteArray()
            assertTrue(packet.size <= 180 && packet.size < 253)
            reassembler.accept(packet)?.let { completed = it }
        }
        val decoded = EspMeshBridgeFrame.fromJson(JSONObject(String(completed!!)))
        assertEquals("CONFIG_ACK", decoded.kind)
        assertEquals("bench-test-01", decoded.body.getString("network_id"))
    }

    @Test fun maxSizeFramesRoundTripAtSupportedPayloads() {
        for (budget in listOf(180, 182, 244, 253, 480)) {
            val raw = "é".repeat(32767).toByteArray()
            val parts = EspMeshBlePacketCodec.fragment(raw, budget)
            val decoder = EspMeshBlePacketCodec.Reassembler()
            var complete: ByteArray? = null
            parts.forEach { assertTrue(it.size <= budget); decoder.accept(it)?.let { value -> complete = value } }
            assertArrayEquals(raw, complete)
        }
    }

    @Test fun resetDiscardsFragmentsFromPreviousConnection() {
        val packets = fixture().getJSONArray("packets")
        val decoder = EspMeshBlePacketCodec.Reassembler()
        assertNull(decoder.accept(packets.getString(0).toByteArray()))
        decoder.clear()
        for (i in 1 until packets.length()) assertNull(decoder.accept(packets.getString(i).toByteArray()))
    }

    @Test fun onlyMatchingConfigAckCompletesProvisioning() {
        val tracker = EspMeshProvisioningTracker()
        val ack = ack()
        assertFalse(tracker.accept(ack))
        tracker.begin(ack.requestId, "bench-test-01")
        assertFalse(tracker.accept(ack.copy(kind = "HELLO_ACK")))
        assertFalse(tracker.accept(ack.copy(kind = "SYNC_ACK")))
        assertFalse(tracker.accept(ack.copy(requestId = "stale")))
        assertNull(tracker.state.value.acknowledgement)
        assertTrue(tracker.accept(ack))
        assertFalse(tracker.state.value.pending)
        assertNotNull(tracker.state.value.acknowledgement)
        tracker.fail(ack.requestId, "late timeout")
        assertNotNull(tracker.state.value.acknowledgement)
        tracker.reset()
        assertFalse(tracker.accept(ack))
    }

    @Test fun malformedOrMismatchedAcksCannotComplete() {
        for (change in listOf<(JSONObject) -> Unit>(
            { it.put("provisioned", false) }, { it.put("provisioned", "true") },
            { it.put("network_id", "wrong") }, { it.remove("firmware") },
            { it.put("node_id", "") }, { it.put("pending_for_radio", -1) },
            { it.remove("pending_for_phone") }
        )) {
            val ack = ack()
            change(ack.body)
            val tracker = EspMeshProvisioningTracker()
            tracker.begin(ack.requestId, "bench-test-01")
            assertThrows(IllegalArgumentException::class.java) { tracker.accept(ack) }
            assertNull(tracker.state.value.acknowledgement)
        }
    }

    @Test fun failureTimeoutOrDisconnectInvalidatesPendingRequest() {
        val ack = ack()
        val tracker = EspMeshProvisioningTracker()
        tracker.begin(ack.requestId, "bench-test-01")
        tracker.fail("stale", "ignored")
        assertTrue(tracker.state.value.pending)
        tracker.fail(ack.requestId, "disconnected")
        assertFalse(tracker.state.value.pending)
        assertFalse(tracker.accept(ack))
    }

    @Test fun rejectedRetryKeepsPreviouslyConfirmedNetworkReady() {
        val ack = ack()
        val tracker = EspMeshProvisioningTracker()
        tracker.begin(ack.requestId, "bench-test-01")
        assertTrue(tracker.accept(ack))

        tracker.begin("retry", "bench-test-01")
        assertTrue(tracker.state.value.pending)
        tracker.fail("retry", "This node already has network settings.")

        assertFalse(tracker.state.value.pending)
        assertEquals("bench-test-01", tracker.state.value.networkId)
        assertSame(ack, tracker.state.value.acknowledgement)
        assertTrue(tracker.state.value.detail.contains("previously confirmed network remains active"))
    }

    @Test fun validatedConfirmationCanBeRestoredAndRejectsDifferentGatewayState() {
        val ack = ack()
        val tracker = EspMeshProvisioningTracker()
        tracker.restore(EspMeshBridgeFrame.fromJson(JSONObject(ack.toJson().toString())))

        assertNotNull(tracker.state.value.acknowledgement)
        assertEquals("bench-test-01", tracker.state.value.networkId)
        assertFalse(tracker.invalidateIfGatewayDoesNotMatch(ack.body.getString("node_id"), "bench-test-01", true))
        assertTrue(tracker.invalidateIfGatewayDoesNotMatch(ack.body.getString("node_id"), "another-network", true))
        assertNull(tracker.state.value.acknowledgement)
    }

    @Test fun genericExecutionDoesNotFabricateProvisioningSuccess() {
        val method = As100EspMeshGatewayMethod
        val request = method.request(method.id, emptyMap(), emptyList(), emptyList())
        val result = method.execute(request, null, "espmesh")
        assertTrue(com.example.methodmesh.transport.OutputFormatter.fields(result, false).values.any { it.toString() == "awaiting_config_ack" })
    }
}
