package com.example.methodmesh.core.transport

import kotlinx.coroutines.flow.StateFlow

/** Adapter boundary for BLE, serial, LAN, radio gateways and future transports. */
interface MethodMeshTransportProvider {
    val transportId: String
    val capabilities: TransportCapabilities get() = TransportCapabilities()
    val status: StateFlow<TransportStatus>

    /** Providers with their own durable/secure queue may opt out of the generic plaintext journal. */
    val ownsDurability: Boolean get() = false

    suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit)
    suspend fun stop()
    /** Durable message submission. Live frames use a separate provider stream path. */
    suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult
}

fun interface MethodMeshTransportConsumer {
    suspend fun consume(envelope: MethodMeshTransportEnvelope)
}
