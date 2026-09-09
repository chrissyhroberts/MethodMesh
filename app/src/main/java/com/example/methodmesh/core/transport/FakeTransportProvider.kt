package com.example.methodmesh.core.transport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** In-memory provider used by tests and development diagnostics. */
class FakeTransportProvider(override val transportId: String = "fake") : MethodMeshTransportProvider {
    private val mutableStatus = MutableStateFlow(TransportStatus(available = true, connected = true, detail = "Fake transport"))
    override val status: StateFlow<TransportStatus> = mutableStatus
    private var inbound: (suspend (MethodMeshTransportEnvelope) -> Unit)? = null
    val sent = mutableListOf<MethodMeshTransportEnvelope>()

    override suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit) { inbound = onEnvelope }
    override suspend fun stop() { inbound = null }
    override suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult {
        if (!status.value.available) return TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "Unavailable")
        sent += envelope
        return TransportSendResult(TransportOutboxState.SENT)
    }
    suspend fun inject(envelope: MethodMeshTransportEnvelope) { inbound?.invoke(envelope) }
    fun setAvailable(value: Boolean) { mutableStatus.value = status.value.copy(available = value, connected = value) }
}
