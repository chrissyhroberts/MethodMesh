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
        val wire = EspMeshSecureWire(
            messageId = envelope.messageId,
            originPhoneId = envelope.source.id,
            source = envelope.source,
            destination = envelope.destination,
            ciphertext = envelope.payload,
            nonce = "test-nonce",
            keyId = "0123456789abcdef",
            createdAt = envelope.createdAt,
            expiresAt = envelope.expiresAt,
            ttl = 8
        )
        val frame = EspMeshBridgeFrame("DATA", wire = wire)
        val decoded = EspMeshBridgeFrame.fromJson(frame.toJson())
        assertEquals(wire, decoded.wire)
        assertEquals("DATA", decoded.kind)
    }
}
