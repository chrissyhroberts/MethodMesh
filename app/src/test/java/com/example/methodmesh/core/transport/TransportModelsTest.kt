package com.example.methodmesh.core.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TransportModelsTest {
    @Test
    fun envelopeRoundTrips() {
        val original = MethodMeshTransportEnvelope(
            source = TransportEndpoint("installation", "a"),
            destination = TransportEndpoint("logical", "team"),
            messageType = "OBSERVATION",
            moduleId = "sensorread",
            payloadType = "application/json",
            payload = "{\"temperature_c\":21.5}",
            metadata = mapOf("priority" to "normal")
        )
        val decoded = MethodMeshTransportEnvelope.fromJson(original.toJson())
        assertEquals(original, decoded)
    }

    @Test
    fun unsupportedSchemaIsRejected() {
        val envelope = MethodMeshTransportEnvelope(
            schemaVersion = MethodMeshTransportEnvelope.CURRENT_SCHEMA_VERSION + 1,
            source = TransportEndpoint("installation", "a"),
            destination = TransportEndpoint("logical", "team"),
            messageType = "DATA",
            payloadType = "text/plain",
            payload = "hello"
        )
        assertThrows(IllegalArgumentException::class.java) { envelope.validate() }
    }

    @Test
    fun payloadLimitIsExplicit() {
        val envelope = MethodMeshTransportEnvelope(
            source = TransportEndpoint("installation", "a"),
            destination = TransportEndpoint("logical", "team"),
            messageType = "DATA",
            payloadType = "text/plain",
            payload = "x".repeat(MethodMeshTransportEnvelope.MAX_PAYLOAD_BYTES + 1)
        )
        assertThrows(IllegalArgumentException::class.java) { envelope.validate() }
    }
}
