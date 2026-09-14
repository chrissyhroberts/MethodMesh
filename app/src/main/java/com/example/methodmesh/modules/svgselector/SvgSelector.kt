package com.example.methodmesh.modules.svgselector

import com.example.methodmesh.core.crypto.Digests
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

data class SvgSelectorEvent(
    val type: String,
    val polygonId: String,
    val sequenceIndex: Int?,
    val timeIso: String,
    val level: Int? = null,
    val previousLevel: Int? = null
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

    fun heatmapSelectionsJson(values: List<Pair<String, Int>>): String = JSONArray().apply {
        values.forEach { (polygonId, level) ->
            put(JSONObject().apply {
                put("polygon_id", polygonId)
                put("level", level)
            })
        }
    }.toString()


    fun selectedPolygonIdsJson(selectionsJson: String): String = runCatching {
        val array = JSONArray(selectionsJson)
        JSONArray().apply {
            for (index in 0 until array.length()) {
                val selection = array.optJSONObject(index) ?: continue
                val id = selection.optString("polygon_id")
                if (id.isNotBlank()) put(id)
            }
        }.toString()
    }.getOrDefault("[]")

    fun selectedCount(selectionsJson: String): Int = runCatching { JSONArray(selectionsJson).length() }.getOrDefault(0)

    fun selectionSummary(selectionsJson: String, mode: String): String = runCatching {
        val array = JSONArray(selectionsJson)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("polygon_id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            when (mode.lowercase()) {
                "heatmap" -> "$id=${item.optInt("level", 0)}"
                "sequence" -> "${item.optInt("sequence_index", index + 1)}:$id"
                else -> id
            }
        }.joinToString(if (mode.equals("sequence", true)) " → " else ", ")
    }.getOrDefault("")

    fun polygonLevelsJson(selectionsJson: String): String = runCatching {
        val array = JSONArray(selectionsJson)
        JSONObject().apply {
            for (index in 0 until array.length()) {
                val selection = array.optJSONObject(index) ?: continue
                if (selection.has("level")) put(selection.optString("polygon_id"), selection.optInt("level"))
            }
        }.toString()
    }.getOrDefault("{}")

    fun eventsJson(values: List<SvgSelectorEvent>): String = JSONArray().apply {
        values.forEach { value ->
            put(JSONObject().apply {
                put("type", value.type)
                put("polygon_id", value.polygonId)
                value.sequenceIndex?.let { put("sequence_index", it) }
                value.level?.let { put("level", it) }
                value.previousLevel?.let { put("previous_level", it) }
                put("time_iso", value.timeIso)
            })
        }
    }.toString()
}
