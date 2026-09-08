package com.example.methodmesh.modules.scoring

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

enum class ScoreSessionStatus { ACTIVE, PAUSED, COMPLETED, ABANDONED }

enum class ScoreEventType { DELTA, POINT, UNDO, CORRECTION, STATUS, ROUND, SET, NOTE }

data class ScoreParticipant(
    val id: String,
    val label: String,
    val score: Int = 0
)

data class ScoreEvent(
    val id: String = UUID.randomUUID().toString(),
    val timeIso: String = Instant.now().toString(),
    val type: ScoreEventType,
    val participantId: String? = null,
    val delta: Int? = null,
    val payloadJson: String = "{}"
)

data class ScoreSession(
    val id: String,
    val methodId: String,
    val title: String,
    val ruleset: String,
    val status: ScoreSessionStatus,
    val participants: List<ScoreParticipant>,
    val events: List<ScoreEvent>,
    val stateJson: String,
    val configJson: String,
    val startedAtIso: String,
    val updatedAtIso: String,
    val finishedAtIso: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("schema", "methodmesh.scoring.session.v1")
        put("id", id)
        put("method_id", methodId)
        put("title", title)
        put("ruleset", ruleset)
        put("status", status.name.lowercase())
        put("participants", JSONArray().apply {
            participants.forEach { p ->
                put(JSONObject().apply {
                    put("id", p.id)
                    put("label", p.label)
                    put("score", p.score)
                })
            }
        })
        put("events", JSONArray().apply {
            events.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("time_iso", e.timeIso)
                    put("type", e.type.name.lowercase())
                    e.participantId?.let { put("participant_id", it) }
                    e.delta?.let { put("delta", it) }
                    put("payload", JSONObject(e.payloadJson))
                })
            }
        })
        put("state", JSONObject(stateJson))
        put("config", JSONObject(configJson))
        put("started_at", startedAtIso)
        put("updated_at", updatedAtIso)
        finishedAtIso?.let { put("finished_at", it) }
    }

    companion object {
        fun fromJson(json: JSONObject): ScoreSession {
            val participants = json.getJSONArray("participants").let { arr ->
                List(arr.length()) { i ->
                    val p = arr.getJSONObject(i)
                    ScoreParticipant(p.getString("id"), p.getString("label"), p.optInt("score", 0))
                }
            }
            val events = json.optJSONArray("events")?.let { arr ->
                List(arr.length()) { i ->
                    val e = arr.getJSONObject(i)
                    ScoreEvent(
                        id = e.getString("id"),
                        timeIso = e.getString("time_iso"),
                        type = ScoreEventType.valueOf(e.getString("type").uppercase()),
                        participantId = e.optString("participant_id").takeIf { it.isNotBlank() },
                        delta = if (e.has("delta")) e.getInt("delta") else null,
                        payloadJson = e.optJSONObject("payload")?.toString() ?: "{}"
                    )
                }
            } ?: emptyList()
            return ScoreSession(
                id = json.getString("id"),
                methodId = json.getString("method_id"),
                title = json.optString("title"),
                ruleset = json.optString("ruleset", "generic"),
                status = ScoreSessionStatus.valueOf(json.optString("status", "active").uppercase()),
                participants = participants,
                events = events,
                stateJson = json.optJSONObject("state")?.toString() ?: "{}",
                configJson = json.optJSONObject("config")?.toString() ?: "{}",
                startedAtIso = json.getString("started_at"),
                updatedAtIso = json.getString("updated_at"),
                finishedAtIso = json.optString("finished_at").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class HighScoreRecord(
    val id: String = UUID.randomUUID().toString(),
    val activity: String,
    val participant: String,
    val score: Int,
    val recordedAtIso: String = Instant.now().toString(),
    val note: String = ""
) {
    fun toJson() = JSONObject().apply {
        put("id", id); put("activity", activity); put("participant", participant)
        put("score", score); put("recorded_at", recordedAtIso); put("note", note)
    }

    companion object {
        fun fromJson(o: JSONObject) = HighScoreRecord(
            id = o.getString("id"), activity = o.getString("activity"),
            participant = o.getString("participant"), score = o.getInt("score"),
            recordedAtIso = o.getString("recorded_at"), note = o.optString("note")
        )
    }
}
