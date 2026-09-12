package com.example.methodmesh.modules.espmesh

import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Versioned Android↔gateway control/data frame. Payload data is always E2E ciphertext. */
data class EspMeshBridgeFrame(
    val kind: String,
    val requestId: String = UUID.randomUUID().toString(),
    val wire: EspMeshSecureWire? = null,
    val body: JSONObject = JSONObject()
) {
    init { require(kind in KINDS) { "Unknown ESP mesh bridge kind: $kind" } }

    fun toJson(): JSONObject = JSONObject().apply {
        put("protocol", PROTOCOL)
        put("version", VERSION)
        put("kind", kind)
        put("request_id", requestId)
        putOpt("wire", wire?.toJson())
        put("body", body)
    }

    fun encode(): ByteArray = toJson().toString().toByteArray(Charsets.UTF_8)

    companion object {
        const val PROTOCOL = "methodmesh.gateway"
        const val VERSION = 2
        const val MAX_FRAME_BYTES = 65_535
        val KINDS = setOf(
            "HELLO", "HELLO_ACK", "CONFIG", "CONFIG_ACK", "DATA", "LOCAL_STORED",
            "REMOTE_STORED", "PHONE_STORED", "SYNC_REQUEST", "SYNC_ACK",
            "VOICE_LISTEN", "VOICE_LISTEN_ACK", "LIVE_VOICE", "LIVE_VOICE_RX",
            "RADIO_ERROR", "ERROR"
        )

        fun fromJson(root: JSONObject): EspMeshBridgeFrame {
            require(root.optString("protocol") == PROTOCOL) { "Unknown gateway protocol" }
            require(root.optInt("version") == VERSION) { "Unsupported gateway protocol version" }
            require(root.toString().toByteArray(Charsets.UTF_8).size <= MAX_FRAME_BYTES) { "Gateway frame exceeds bounded bridge size" }
            val kind = root.getString("kind")
            require(kind in KINDS) { "Unsupported gateway frame kind: $kind" }
            return EspMeshBridgeFrame(
                kind = kind,
                requestId = root.optString("request_id"),
                wire = root.optJSONObject("wire")?.let(EspMeshSecureWire::fromJson),
                body = root.optJSONObject("body") ?: JSONObject()
            )
        }
    }
}

/**
 * BLE packetisation below the gateway-frame layer. GATT carries bounded JSON
 * packets; larger bridge frames are fragmented/reassembled transparently.
 */
object EspMeshBlePacketCodec {
    const val PROTOCOL = "methodmesh.blefrag"
    const val VERSION = 1
    fun fragment(frame: ByteArray, maxPacketBytes: Int): List<ByteArray> {
        require(frame.size <= EspMeshBridgeFrame.MAX_FRAME_BYTES)
        if (frame.size <= maxPacketBytes) return listOf(frame)
        require(maxPacketBytes >= 140) { "Negotiated BLE MTU is too small for mesh packetisation" }
        val transferId = UUID.randomUUID().toString()
        var rawChunkBytes = (((maxPacketBytes - 125) * 3) / 4).coerceIn(8, 240)
        while (rawChunkBytes >= 8) {
            val chunks = frame.asList().chunked(rawChunkBytes).map { list -> list.toByteArray() }
            val packets = chunks.mapIndexed { index, chunk ->
                JSONObject().apply {
                    put("protocol", PROTOCOL); put("version", VERSION); put("id", transferId)
                    put("seq", index); put("total", chunks.size)
                    put("data", Base64.encodeToString(chunk, Base64.NO_WRAP))
                }.toString().toByteArray(Charsets.UTF_8)
            }
            if (packets.all { it.size <= maxPacketBytes }) return packets
            rawChunkBytes -= 8
        }
        error("Negotiated BLE MTU is too small for mesh packetisation")
    }

    class Reassembler {
        private data class Partial(val total: Int, val chunks: MutableMap<Int, ByteArray>, val created: Long)
        private val partials = ConcurrentHashMap<String, Partial>()
        private val MAX_PARTIALS = 16

        /** Returns a complete bridge frame, or null while a fragmented frame is incomplete. */
        fun accept(packet: ByteArray): ByteArray? {
            val text = packet.toString(Charsets.UTF_8)
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return packet
            if (root.optString("protocol") != PROTOCOL) return packet
            require(root.optInt("version") == VERSION) { "Unsupported BLE fragment version" }
            val id = root.getString("id")
            val seq = root.getInt("seq")
            val total = root.getInt("total")
            require(total in 1..2048 && seq in 0 until total) { "Invalid BLE fragment bounds" }
            val chunk = Base64.decode(root.getString("data"), Base64.DEFAULT)
            val now = System.currentTimeMillis()
            partials.entries.removeIf { now - it.value.created > 60_000L }
            if (!partials.containsKey(id) && partials.size >= MAX_PARTIALS) {
                partials.entries.minByOrNull { it.value.created }?.key?.let(partials::remove)
            }
            val partial = partials.compute(id) { _, existing ->
                if (existing == null || existing.total != total) Partial(total, mutableMapOf(), now) else existing
            }!!
            val prior = partial.chunks[seq]?.size ?: 0
            val projected = partial.chunks.values.sumOf { it.size } - prior + chunk.size
            require(projected <= EspMeshBridgeFrame.MAX_FRAME_BYTES) { "BLE fragment set exceeds bounded bridge size" }
            partial.chunks[seq] = chunk
            if (partial.chunks.size != total) return null
            val output = ByteArrayOutputStream(projected)
            repeat(total) { output.write(partial.chunks[it] ?: error("BLE fragment missing")) }
            partials.remove(id)
            return output.toByteArray().also {
                require(it.size <= EspMeshBridgeFrame.MAX_FRAME_BYTES) { "Reassembled BLE frame exceeds bounded bridge size" }
            }
        }
    }
}
