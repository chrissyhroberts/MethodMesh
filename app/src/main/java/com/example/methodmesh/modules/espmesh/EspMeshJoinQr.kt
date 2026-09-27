package com.example.methodmesh.modules.espmesh

import org.json.JSONObject
import java.util.Base64

data class EspMeshJoinBundle(
    val networkId: String,
    val networkKey: String,
    val e2eGroupKey: String
) {
    fun encode(): String = JSONObject().apply {
        put("protocol", PROTOCOL)
        put("version", VERSION)
        put("network_id", networkId)
        put("network_key", networkKey)
        put("e2e_group_key", e2eGroupKey)
    }.toString()

    companion object {
        const val PROTOCOL = "methodmesh.mesh.join"
        const val VERSION = 1

        fun decode(payload: String): EspMeshJoinBundle {
            val root = JSONObject(payload)
            require(root.optString("protocol") == PROTOCOL && root.optInt("version") == VERSION) { "This is not a MethodMesh mesh-join QR code." }
            val networkId = root.getString("network_id").trim()
            val networkKey = root.getString("network_key").trim()
            val e2eGroupKey = root.getString("e2e_group_key").trim()
            require(networkId.isNotBlank() && networkId.length <= 64) { "The QR code has an invalid network ID." }
            require(networkKey.isNotBlank() && networkKey.length <= 128) { "The QR code has an invalid ESP network key." }
            require(runCatching { Base64.getUrlDecoder().decode(e2eGroupKey).size == 32 }.getOrDefault(false)) { "The QR code has an invalid E2E group key." }
            return EspMeshJoinBundle(networkId, networkKey, e2eGroupKey)
        }
    }
}
