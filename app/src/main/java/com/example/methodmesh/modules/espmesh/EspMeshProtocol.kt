package com.example.methodmesh.modules.espmesh

import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import org.json.JSONObject
import java.util.UUID

/** Versioned Android↔gateway framing; ESP-NOW frames are a separate firmware concern. */
data class EspMeshBridgeFrame(
    val kind: String,
    val requestId: String = UUID.randomUUID().toString(),
    val envelope: MethodMeshTransportEnvelope? = null,
    val body: JSONObject = JSONObject()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("protocol", PROTOCOL)
        put("version", VERSION)
        put("kind", kind)
        put("request_id", requestId)
        putOpt("envelope", envelope?.toJson())
        put("body", body)
    }

    companion object {
        const val PROTOCOL = "methodmesh.gateway"
        const val VERSION = 1
        const val MAX_FRAME_BYTES = 64 * 1024

        fun fromJson(root: JSONObject): EspMeshBridgeFrame {
            require(root.optString("protocol") == PROTOCOL) { "Unknown gateway protocol" }
            require(root.optInt("version") == VERSION) { "Unsupported gateway protocol version" }
            val encoded = root.toString().toByteArray(Charsets.UTF_8)
            require(encoded.size <= MAX_FRAME_BYTES) { "Gateway frame exceeds bounded bridge size" }
            return EspMeshBridgeFrame(
                kind = root.getString("kind"),
                requestId = root.getString("request_id"),
                envelope = root.optJSONObject("envelope")?.let(MethodMeshTransportEnvelope::fromJson),
                body = root.optJSONObject("body") ?: JSONObject()
            )
        }
    }
}
