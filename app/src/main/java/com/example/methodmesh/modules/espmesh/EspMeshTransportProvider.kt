package com.example.methodmesh.modules.espmesh

import android.content.Context
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.core.transport.TransportOutboxState
import com.example.methodmesh.core.transport.TransportSendResult
import com.example.methodmesh.core.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Android-side gateway boundary for the ESP mesh module.
 *
 * BLE framing and gateway discovery are deliberately owned here. The first
 * slice exposes a safe queued provider until a gateway is provisioned.
 */
class EspMeshTransportProvider private constructor(private val context: Context) : MethodMeshTransportProvider {
    override val transportId: String = TRANSPORT_ID
    private val mutableStatus = MutableStateFlow(TransportStatus(false, false, "No ESP mesh gateway provisioned"))
    override val status: StateFlow<TransportStatus> = mutableStatus
    private var inbound: (suspend (MethodMeshTransportEnvelope) -> Unit)? = null

    override suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit) {
        inbound = onEnvelope
    }

    override suspend fun stop() {
        inbound = null
    }

    override suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult =
        TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "ESP mesh gateway is not provisioned")

    /** Called by the eventual BLE gateway adapter after a frame is decoded. */
    suspend fun deliverFromGateway(envelope: MethodMeshTransportEnvelope) {
        inbound?.invoke(envelope)
    }

    companion object {
        const val TRANSPORT_ID = "espmesh.gateway"
        fun create(context: Context): EspMeshTransportProvider = EspMeshTransportProvider(context.applicationContext)
    }
}
