package com.example.methodmesh.modules.espmesh

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Control-plane confirmation only; HELLO/SYNC telemetry cannot complete CONFIG. */
data class EspMeshProvisioningState(
    val requestId: String = "",
    val networkId: String = "",
    val acknowledgement: EspMeshBridgeFrame? = null,
    val detail: String = "Select a gateway, then provision its network.",
    private val waitingForAcknowledgement: Boolean = false
) {
    val pending: Boolean get() = waitingForAcknowledgement
}

class EspMeshProvisioningTracker {
    private val mutableState = MutableStateFlow(EspMeshProvisioningState())
    val state: StateFlow<EspMeshProvisioningState> = mutableState

    @Synchronized fun begin(requestId: String, networkId: String) {
        mutableState.value = mutableState.value.copy(
            requestId = requestId,
            networkId = networkId,
            detail = "Network settings sent; waiting for gateway confirmation…",
            waitingForAcknowledgement = true
        )
    }

    @Synchronized fun accept(frame: EspMeshBridgeFrame): Boolean {
        val current = state.value
        if (!current.pending || frame.kind != "CONFIG_ACK" || frame.requestId != current.requestId) return false
        validate(frame, current.networkId)
        mutableState.value = current.copy(
            acknowledgement = frame,
            detail = "Mesh node ready · network configuration confirmed",
            waitingForAcknowledgement = false
        )
        return true
    }

    @Synchronized fun fail(requestId: String, detail: String) {
        if (state.value.pending && state.value.requestId == requestId) {
            val current = state.value
            val confirmed = current.acknowledgement
            mutableState.value = if (confirmed == null) {
                EspMeshProvisioningState(detail = detail)
            } else {
                current.copy(
                    requestId = "",
                    networkId = confirmed.body.optString("network_id"),
                    detail = "$detail The previously confirmed network remains active.",
                    waitingForAcknowledgement = false
                )
            }
        }
    }

    @Synchronized fun restore(frame: EspMeshBridgeFrame) {
        val networkId = frame.body.optString("network_id")
        validate(frame, networkId)
        mutableState.value = EspMeshProvisioningState(
            networkId = networkId,
            acknowledgement = frame,
            detail = "Mesh node ready · previously confirmed network restored"
        )
    }

    @Synchronized fun invalidateIfGatewayDoesNotMatch(nodeId: String, networkId: String, provisioned: Boolean): Boolean {
        val confirmed = state.value.acknowledgement ?: return false
        val body = confirmed.body
        if (provisioned && body.optString("node_id") == nodeId && body.optString("network_id") == networkId) return false
        reset()
        return true
    }

    @Synchronized fun reset() {
        mutableState.value = EspMeshProvisioningState()
    }

    companion object {
        fun validate(frame: EspMeshBridgeFrame, networkId: String) {
            require(frame.kind == "CONFIG_ACK" && frame.requestId.isNotBlank()) { "Expected CONFIG_ACK" }
            val body = frame.body
            require(body.opt("provisioned") == true && networkId.isNotBlank() && body.opt("network_id") == networkId) { "Gateway did not confirm the requested network" }
            require(listOf("node_id", "firmware").all { (body.opt(it) as? String)?.isNotBlank() == true }) { "CONFIG_ACK is missing gateway identity or firmware" }
            require(listOf("pending_for_radio", "pending_for_phone").all { (body.opt(it) as? Int)?.let { count -> count >= 0 } == true }) { "CONFIG_ACK has invalid spool counts" }
        }
    }
}
