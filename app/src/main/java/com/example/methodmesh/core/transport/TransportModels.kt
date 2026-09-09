package com.example.methodmesh.core.transport

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Transport-neutral address. Transport-local addresses never belong here. */
data class TransportEndpoint(
    val kind: String,
    val id: String
) {
    init {
        require(kind.isNotBlank() && kind.length <= 64)
        require(id.isNotBlank() && id.length <= 256)
    }

    fun asKey(): String = "$kind:$id"
}

/** Opaque, versioned MethodMesh data carried by any registered transport. */
data class MethodMeshTransportEnvelope(
    val messageId: String = UUID.randomUUID().toString(),
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val source: TransportEndpoint,
    val destination: TransportEndpoint,
    val messageType: String,
    val moduleId: String? = null,
    val capabilityId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null,
    val correlationId: String? = null,
    val replyTo: String? = null,
    val payloadType: String,
    val payload: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun validate(now: Long = System.currentTimeMillis()) {
        require(schemaVersion in 1..CURRENT_SCHEMA_VERSION) { "Unsupported transport schema: $schemaVersion" }
        require(messageId.length in 1..128)
        require(messageType.length in 1..64)
        require(payloadType.length in 1..128)
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES)
        require(metadata.size <= MAX_METADATA_ENTRIES)
        require(metadata.entries.all { it.key.length <= 64 && it.value.length <= 512 })
        require(expiresAt == null || expiresAt >= createdAt)
        require(expiresAt == null || expiresAt > now) { "Transport message has expired" }
    }

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean = expiresAt?.let { it <= now } == true

    fun toJson(): JSONObject = JSONObject().apply {
        put("message_id", messageId)
        put("schema_version", schemaVersion)
        put("source", endpointJson(source))
        put("destination", endpointJson(destination))
        put("message_type", messageType)
        putOpt("module_id", moduleId)
        putOpt("capability_id", capabilityId)
        put("created_at", createdAt)
        putOpt("expires_at", expiresAt)
        putOpt("correlation_id", correlationId)
        putOpt("reply_to", replyTo)
        put("payload_type", payloadType)
        put("payload", payload)
        put("metadata", JSONObject(metadata))
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val MAX_PAYLOAD_BYTES = 256 * 1024
        const val MAX_METADATA_ENTRIES = 32

        fun fromJson(root: JSONObject): MethodMeshTransportEnvelope {
            val metadata = buildMap {
                val json = root.optJSONObject("metadata") ?: JSONObject()
                json.keys().forEach { put(it, json.optString(it)) }
            }
            return MethodMeshTransportEnvelope(
                messageId = root.getString("message_id"),
                schemaVersion = root.optInt("schema_version", 0),
                source = endpointFromJson(root.getJSONObject("source")),
                destination = endpointFromJson(root.getJSONObject("destination")),
                messageType = root.getString("message_type"),
                moduleId = root.optString("module_id").ifBlank { null },
                capabilityId = root.optString("capability_id").ifBlank { null },
                createdAt = root.getLong("created_at"),
                expiresAt = root.optLong("expires_at").takeIf { root.has("expires_at") },
                correlationId = root.optString("correlation_id").ifBlank { null },
                replyTo = root.optString("reply_to").ifBlank { null },
                payloadType = root.getString("payload_type"),
                payload = root.getString("payload"),
                metadata = metadata
            )
        }

        private fun endpointJson(endpoint: TransportEndpoint) = JSONObject()
            .put("kind", endpoint.kind)
            .put("id", endpoint.id)

        private fun endpointFromJson(root: JSONObject) = TransportEndpoint(
            kind = root.getString("kind"),
            id = root.getString("id")
        )
    }
}

enum class TransportOutboxState { QUEUED, IN_PROGRESS, SENT, DELIVERED, FAILED_RETRYABLE, FAILED_PERMANENT, EXPIRED }
enum class TransportInboxState { RECEIVED, DISPATCH_PENDING, DISPATCHED, CONSUMED, FAILED }

data class TransportSendResult(
    val state: TransportOutboxState,
    val detail: String = ""
)

data class TransportStatus(
    val available: Boolean,
    val connected: Boolean,
    val detail: String = ""
)
