package com.example.methodmesh.modules.svgselector

import com.example.methodmesh.core.crypto.Digests
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class SvgSelectorEvent(
    val type: String,
    val polygonId: String,
    val sequenceIndex: Int?,
    val timeIso: String
)

object SvgSelectorCodec {
    fun auditHash(eventsJson: String): String = Digests.sha256Hex(eventsJson)

    fun now(): String = Instant.now().toString()

    fun selectionsJson(values: List<Pair<String, Int>>): String = JSONArray().apply {
        values.forEach { (polygonId, index) ->
            put(JSONObject().apply {
                put("polygon_id", polygonId)
                put("sequence_index", index)
            })
        }
    }.toString()

    fun eventsJson(values: List<SvgSelectorEvent>): String = JSONArray().apply {
        values.forEach { value ->
            put(JSONObject().apply {
                put("type", value.type)
                put("polygon_id", value.polygonId)
                value.sequenceIndex?.let { put("sequence_index", it) }
                put("time_iso", value.timeIso)
            })
        }
    }.toString()
}
