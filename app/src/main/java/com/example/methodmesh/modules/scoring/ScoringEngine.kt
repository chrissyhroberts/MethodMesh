package com.example.methodmesh.modules.scoring

import org.json.JSONObject
import java.time.Instant
import java.util.UUID
import kotlin.math.abs

object ScoringEngine {
    fun create(methodId: String, settings: Map<String, String>): ScoreSession {
        val now = Instant.now().toString()
        val names = participantNames(settings)
        val ruleset = settings.value("ruleset") ?: when (methodId) {
            ScoringMethods.Sports.id -> "football"
            ScoringMethods.SetMatch.id -> "tennis"
            else -> "generic"
        }
        val config = JSONObject().apply {
            put("increment", settings.value("increment")?.toIntOrNull() ?: 1)
            put("starting_value", settings.value("starting_value")?.toIntOrNull() ?: 0)
            put("target", settings.value("target")?.toIntOrNull() ?: defaultTarget(ruleset))
            put("win_by", settings.value("win_by")?.toIntOrNull() ?: 2)
            put("best_of", settings.value("best_of")?.toIntOrNull() ?: 3)
            put("highest_wins", settings.value("highest_wins")?.toBooleanStrictOrNull() ?: true)
            put("allow_negative", settings.value("allow_negative")?.toBooleanStrictOrNull() ?: true)
        }
        val start = settings.value("starting_value")?.toIntOrNull() ?: 0
        val participants = names.mapIndexed { i, label -> ScoreParticipant("p${i + 1}", label, start) }
        val initialState = SportsRules.initialState(ruleset, config).apply {
            if (methodId == ScoringMethods.Rounds.id) put("current_round", 1)
        }
        return ScoreSession(
            id = UUID.randomUUID().toString(), methodId = methodId,
            title = settings.value("title") ?: defaultTitle(methodId, ruleset),
            ruleset = ruleset, status = ScoreSessionStatus.ACTIVE,
            participants = participants,
            events = listOf(ScoreEvent(type = ScoreEventType.STATUS, payloadJson = JSONObject().put("status", "active").toString())),
            stateJson = initialState.toString(), configJson = config.toString(),
            startedAtIso = now, updatedAtIso = now
        )
    }

    fun delta(session: ScoreSession, participantId: String, delta: Int): ScoreSession {
        require(session.status == ScoreSessionStatus.ACTIVE) { "Session is not active." }
        val config = JSONObject(session.configJson)
        val allowNegative = config.optBoolean("allow_negative", true)
        val participants = session.participants.map { p ->
            if (p.id != participantId) p else p.copy(score = if (allowNegative) p.score + delta else (p.score + delta).coerceAtLeast(0))
        }
        val event = ScoreEvent(type = ScoreEventType.DELTA, participantId = participantId, delta = delta)
        var updated = session.copy(participants = participants, events = session.events + event, updatedAtIso = Instant.now().toString())
        if (session.methodId == ScoringMethods.RaceTo.id) {
            val target = config.optInt("target", 1).coerceAtLeast(1)
            if (updated.participants.any { it.score >= target }) updated = complete(updated)
        }
        return updated
    }

    fun sportAction(session: ScoreSession, participantId: String, actionId: String): ScoreSession {
        val rule = SportsRules.byId(session.ruleset)
        val simple = rule.actions.firstOrNull { it.id == actionId }?.delta
        if (!rule.structured) return delta(session, participantId, simple ?: 1)
        val index = session.participants.indexOfFirst { it.id == participantId }
        require(index in 0..1) { "Structured sport requires two participants." }
        return when (session.ruleset) {
            "tennis", "padel" -> tennisPoint(session, index)
            "badminton", "table_tennis", "volleyball", "squash" -> rallyPoint(session, index)
            else -> delta(session, participantId, 1)
        }
    }

    private fun tennisPoint(session: ScoreSession, scorer: Int): ScoreSession {
        require(session.status == ScoreSessionStatus.ACTIVE) { "Session is not active." }
        val beforeState = session.stateJson
        val s = JSONObject(session.stateJson)
        if (s.optBoolean("tiebreak", false)) {
            val key = if (scorer == 0) "tiebreak_a" else "tiebreak_b"
            s.put(key, s.optInt(key) + 1)
            val a = s.optInt("tiebreak_a"); val b = s.optInt("tiebreak_b")
            if (maxOf(a, b) >= 7 && abs(a - b) >= 2) {
                winTennisSet(s, if (a > b) 0 else 1)
            }
        } else {
            val a = s.optInt("points_a"); val b = s.optInt("points_b")
            if (a >= 3 && b >= 3) {
                if (a == b) {
                    s.put(if (scorer == 0) "points_a" else "points_b", 4)
                } else {
                    val advantaged = if (a > b) 0 else 1
                    if (scorer == advantaged) winTennisGame(s, scorer)
                    else { s.put("points_a", 3); s.put("points_b", 3) }
                }
            } else {
                val key = if (scorer == 0) "points_a" else "points_b"
                val next = s.optInt(key) + 1
                if (next >= 4) winTennisGame(s, scorer) else s.put(key, next)
            }
        }
        val event = ScoreEvent(
            type = ScoreEventType.POINT,
            participantId = session.participants[scorer].id,
            payloadJson = JSONObject().put("ruleset", session.ruleset).put("before_state", JSONObject(beforeState)).toString()
        )
        var out = session.copy(stateJson = s.toString(), events = session.events + event, updatedAtIso = Instant.now().toString())
        if (isTennisMatchComplete(s)) out = complete(out)
        return syncStructuredParticipantScores(out)
    }

    private fun winTennisGame(s: JSONObject, scorer: Int) {
        val key = if (scorer == 0) "games_a" else "games_b"
        s.put(key, s.optInt(key) + 1)
        s.put("points_a", 0); s.put("points_b", 0)
        val a = s.optInt("games_a"); val b = s.optInt("games_b")
        if (a == 6 && b == 6) {
            s.put("tiebreak", true); s.put("tiebreak_a", 0); s.put("tiebreak_b", 0)
        } else if (maxOf(a, b) >= 6 && abs(a - b) >= 2) {
            winTennisSet(s, if (a > b) 0 else 1)
        }
    }

    private fun winTennisSet(s: JSONObject, scorer: Int) {
        val key = if (scorer == 0) "sets_a" else "sets_b"
        s.put(key, s.optInt(key) + 1)
        s.put("games_a", 0); s.put("games_b", 0)
        s.put("points_a", 0); s.put("points_b", 0)
        s.put("tiebreak", false); s.put("tiebreak_a", 0); s.put("tiebreak_b", 0)
    }

    private fun isTennisMatchComplete(s: JSONObject): Boolean {
        val needed = s.optInt("best_of", 3) / 2 + 1
        return s.optInt("sets_a") >= needed || s.optInt("sets_b") >= needed
    }

    private fun rallyPoint(session: ScoreSession, scorer: Int): ScoreSession {
        require(session.status == ScoreSessionStatus.ACTIVE) { "Session is not active." }
        val beforeState = session.stateJson
        val s = JSONObject(session.stateJson)
        val key = if (scorer == 0) "points_a" else "points_b"
        s.put(key, s.optInt(key) + 1)
        val a = s.optInt("points_a"); val b = s.optInt("points_b")
        val target = s.optInt("target", 21); val winBy = s.optInt("win_by", 2)
        if (maxOf(a, b) >= target && abs(a - b) >= winBy) {
            val gameWinner = if (a > b) 0 else 1
            val gameKey = if (gameWinner == 0) "games_a" else "games_b"
            s.put(gameKey, s.optInt(gameKey) + 1)
            s.put("points_a", 0); s.put("points_b", 0)
        }
        val event = ScoreEvent(
            type = ScoreEventType.POINT,
            participantId = session.participants[scorer].id,
            payloadJson = JSONObject().put("before_state", JSONObject(beforeState)).toString()
        )
        var out = session.copy(stateJson = s.toString(), events = session.events + event, updatedAtIso = Instant.now().toString())
        val needed = s.optInt("best_of", 3) / 2 + 1
        if (s.optInt("games_a") >= needed || s.optInt("games_b") >= needed) out = complete(out)
        return syncStructuredParticipantScores(out)
    }

    private fun syncStructuredParticipantScores(session: ScoreSession): ScoreSession {
        if (session.participants.size < 2) return session
        val s = JSONObject(session.stateJson)
        val scores = when (session.ruleset) {
            "tennis", "padel" -> s.optInt("sets_a") to s.optInt("sets_b")
            else -> s.optInt("games_a") to s.optInt("games_b")
        }
        return session.copy(participants = listOf(session.participants[0].copy(score = scores.first), session.participants[1].copy(score = scores.second)) + session.participants.drop(2))
    }

    fun undo(session: ScoreSession): ScoreSession {
        val alreadyUndone = session.events.filter { it.type == ScoreEventType.UNDO }
            .mapNotNull { runCatching { JSONObject(it.payloadJson).optString("target_event_id") }.getOrNull() }
            .toSet()
        val target = session.events.lastOrNull {
            it.id !in alreadyUndone && it.type in setOf(ScoreEventType.DELTA, ScoreEventType.POINT, ScoreEventType.CORRECTION)
        } ?: return session
        val event = ScoreEvent(type = ScoreEventType.UNDO, payloadJson = JSONObject().put("target_event_id", target.id).toString())
        val withUndo = session.copy(events = session.events + event, updatedAtIso = Instant.now().toString())
        if (target.type == ScoreEventType.POINT || target.type == ScoreEventType.CORRECTION) {
            val before = runCatching { JSONObject(target.payloadJson).optJSONObject("before_state") }.getOrNull()
            if (before != null) return syncStructuredParticipantScores(withUndo.copy(stateJson = before.toString(), status = ScoreSessionStatus.ACTIVE, finishedAtIso = null))
        }
        return rebuild(withUndo.copy(status = ScoreSessionStatus.ACTIVE, finishedAtIso = null))
    }

    fun correct(session: ScoreSession, values: Map<String, Int>): ScoreSession {
        val payload = JSONObject().apply {
            values.forEach { (k, v) -> put(k, v) }
            if (SportsRules.byId(session.ruleset).structured) put("before_state", JSONObject(session.stateJson))
        }
        var stateJson = session.stateJson
        if (SportsRules.byId(session.ruleset).structured && session.participants.size >= 2) {
            val state = JSONObject(session.stateJson)
            val a = values[session.participants[0].id]
            val b = values[session.participants[1].id]
            when (session.ruleset) {
                "tennis", "padel" -> { a?.let { state.put("sets_a", it) }; b?.let { state.put("sets_b", it) } }
                else -> { a?.let { state.put("games_a", it) }; b?.let { state.put("games_b", it) } }
            }
            stateJson = state.toString()
        }
        val participants = session.participants.map { p -> values[p.id]?.let { p.copy(score = it) } ?: p }
        return session.copy(
            participants = participants,
            stateJson = stateJson,
            events = session.events + ScoreEvent(type = ScoreEventType.CORRECTION, payloadJson = payload.toString()),
            updatedAtIso = Instant.now().toString()
        )
    }

    private fun rebuild(session: ScoreSession): ScoreSession {
        val undone = session.events.filter { it.type == ScoreEventType.UNDO }.mapNotNull { JSONObject(it.payloadJson).optString("target_event_id") }.toSet()
        val config = JSONObject(session.configJson)
        val start = config.optInt("starting_value", 0)
        var participants = session.participants.map { it.copy(score = start) }
        session.events.filter { it.id !in undone }.forEach { e ->
            when (e.type) {
                ScoreEventType.DELTA -> participants = participants.map { p -> if (p.id == e.participantId) p.copy(score = p.score + (e.delta ?: 0)) else p }
                ScoreEventType.CORRECTION -> {
                    val payload = JSONObject(e.payloadJson)
                    participants = participants.map { p -> if (payload.has(p.id)) p.copy(score = payload.getInt(p.id)) else p }
                }
                else -> Unit
            }
        }
        return session.copy(participants = participants)
    }

    fun nextRound(session: ScoreSession): ScoreSession {
        require(session.status == ScoreSessionStatus.ACTIVE) { "Session is not active." }
        val state = JSONObject(session.stateJson)
        val next = state.optInt("current_round", 1) + 1
        state.put("current_round", next)
        return session.copy(
            stateJson = state.toString(),
            events = session.events + ScoreEvent(type = ScoreEventType.ROUND, payloadJson = JSONObject().put("round", next).toString()),
            updatedAtIso = Instant.now().toString()
        )
    }

    fun setStatus(session: ScoreSession, status: ScoreSessionStatus): ScoreSession {
        val now = Instant.now().toString()
        return session.copy(
            status = status,
            events = session.events + ScoreEvent(type = ScoreEventType.STATUS, payloadJson = JSONObject().put("status", status.name.lowercase()).toString()),
            updatedAtIso = now,
            finishedAtIso = if (status in setOf(ScoreSessionStatus.COMPLETED, ScoreSessionStatus.ABANDONED)) now else session.finishedAtIso
        )
    }

    fun complete(session: ScoreSession) = setStatus(session, ScoreSessionStatus.COMPLETED)
    fun pause(session: ScoreSession) = setStatus(session, ScoreSessionStatus.PAUSED)
    fun resume(session: ScoreSession) = setStatus(session, ScoreSessionStatus.ACTIVE)
    fun abandon(session: ScoreSession) = setStatus(session, ScoreSessionStatus.ABANDONED)

    fun resultText(session: ScoreSession): String {
        if (session.participants.isEmpty()) return "No score"
        if (session.participants.size == 1) return "${session.participants[0].label}: ${session.participants[0].score}"
        return session.participants.joinToString(" · ") { "${it.label} ${it.score}" }
    }

    fun winner(session: ScoreSession): String {
        if (session.participants.isEmpty()) return ""
        val config = JSONObject(session.configJson)
        val highest = config.optBoolean("highest_wins", true)
        val target = if (highest) session.participants.maxOf { it.score } else session.participants.minOf { it.score }
        val leaders = session.participants.filter { it.score == target }
        return if (leaders.size == 1) leaders.first().label else ""
    }

    fun structuredScoreLabel(session: ScoreSession, index: Int): String {
        val s = JSONObject(session.stateJson)
        return when (session.ruleset) {
            "tennis", "padel" -> {
                if (s.optBoolean("tiebreak")) (if (index == 0) s.optInt("tiebreak_a") else s.optInt("tiebreak_b")).toString()
                else tennisPointLabel(if (index == 0) s.optInt("points_a") else s.optInt("points_b"), if (index == 0) s.optInt("points_b") else s.optInt("points_a"))
            }
            "badminton", "table_tennis", "volleyball", "squash" -> (if (index == 0) s.optInt("points_a") else s.optInt("points_b")).toString()
            else -> session.participants.getOrNull(index)?.score?.toString().orEmpty()
        }
    }

    private fun tennisPointLabel(value: Int, other: Int): String = when {
        value <= 0 -> "0"
        value == 1 -> "15"
        value == 2 -> "30"
        value == 3 -> "40"
        value >= 4 && value > other -> "AD"
        else -> "40"
    }

    private fun participantNames(settings: Map<String, String>): List<String> {
        val raw = settings.value("participant_names") ?: settings.value("players") ?: "Player 1|Player 2"
        val names = raw.split('|', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
        val count = settings.value("participant_count")?.toIntOrNull()?.coerceIn(1, 12) ?: names.size.coerceAtLeast(2)
        return (0 until count).map { i -> names.getOrNull(i) ?: "Player ${i + 1}" }
    }

    private fun defaultTarget(ruleset: String) = when (ruleset) { "badminton" -> 21; "table_tennis", "squash" -> 11; "volleyball" -> 25; else -> 0 }
    private fun defaultTitle(methodId: String, ruleset: String) = if (methodId == ScoringMethods.Sports.id) SportsRules.byId(ruleset).displayName else ScoringMethods.byId(methodId)?.descriptor?.name ?: "Score"
    private fun Map<String, String>.value(key: String): String? = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
}
