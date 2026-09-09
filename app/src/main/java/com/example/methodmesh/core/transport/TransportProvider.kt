package com.example.methodmesh.core.transport

import kotlinx.coroutines.flow.StateFlow

/** Adapter boundary for BLE, serial, LAN, radio gateways and future transports. */
interface MethodMeshTransportProvider {
    val transportId: String
    val status: StateFlow<TransportStatus>

    suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit)
    suspend fun stop()
    suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult
}

fun interface MethodMeshTransportConsumer {
    suspend fun consume(envelope: MethodMeshTransportEnvelope)
}
