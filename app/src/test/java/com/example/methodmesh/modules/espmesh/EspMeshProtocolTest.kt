package com.example.methodmesh.modules.espmesh

import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.TransportEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class EspMeshProtocolTest {
    @Test
    fun bridgeFrameRoundTripsEnvelope() {
        val envelope = MethodMeshTransportEnvelope(
            source = TransportEndpoint("installation", "phone"),
            destination = TransportEndpoint("logical", "field-team"),
            messageType = "TEXT",
            payloadType = "text/plain",
            payload = "hello"
        )
        val frame = EspMeshBridgeFrame("OUTBOUND", envelope = envelope)
        val decoded = EspMeshBridgeFrame.fromJson(frame.toJson())
        assertEquals(envelope, decoded.envelope)
        assertEquals("OUTBOUND", decoded.kind)
    }
}
