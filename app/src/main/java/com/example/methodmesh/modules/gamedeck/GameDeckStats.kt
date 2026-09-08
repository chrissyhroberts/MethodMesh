package com.example.methodmesh.modules.gamedeck

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class GameDeckStatsSnapshot(
    val games: Int,
    val evaluatedGames: Int,
    val wins: Int,
    val losses: Int,
    val draws: Int,
    val sharedTableGames: Int,
    val byGame: Map<String, Int>,
    val bestMoves: Map<String, Int>,
    val skillExposure: Map<String, Int>,
    val achievements: Set<String>
)

/**
 * Lightweight local-first player record.
 *
 * A game can be recorded without pretending that one of two local humans is "the
 * account holder". Personal W/L/D is only updated when `trackedHumanSeat` is known.
 */
class GameDeckStatsStore(context: Context) {
    private val prefs = context.getSharedPreferences("methodmesh_gamedeck_stats", Context.MODE_PRIVATE)

    fun record(
        sessionId: String,
        game: String,
        winner: String,
        trackedHumanSeat: Int?,
        moves: Int = 0,
        score: Int? = null
    ) {
        val recordedOrder = recordedSessionOrder()
        val legacyRecorded = prefs.getStringSet("recorded_sessions", emptySet()) ?: emptySet()
        if (sessionId in recordedOrder || sessionId in legacyRecorded) return

        val editor = prefs.edit()
            .putInt("games", prefs.getInt("games", 0) + 1)
            .putInt("game.$game", prefs.getInt("game.$game", 0) + 1)

        var personalOutcome: String? = null
        if (winner in setOf("cleared", "failed", "stuck", "mine")) {
            // Solo games have an implicit tracked local player.
            personalOutcome = if (winner == "cleared") "wins" else "losses"
        } else if (trackedHumanSeat != null) {
            personalOutcome = when {
                winner == "draw" -> "draws"
                winner == trackedHumanSeat.toString() -> "wins"
                winner == "1" || winner == "2" -> "losses"
                else -> null
            }
        } else if (winner == "1" || winner == "2" || winner == "draw") {
            editor.putInt("shared_table_games", prefs.getInt("shared_table_games", 0) + 1)
        }

        if (personalOutcome != null) {
            editor
                .putInt("evaluated_games", prefs.getInt("evaluated_games", 0) + 1)
                .putInt(personalOutcome, prefs.getInt(personalOutcome, 0) + 1)
        }

        val personalWin = personalOutcome == "wins"
        if (personalWin && moves > 0) {
            val old = prefs.getInt("bestmoves.$game", Int.MAX_VALUE)
            if (moves < old) editor.putInt("bestmoves.$game", moves)
        }

        score?.let {
            val old = prefs.getInt("high.$game", Int.MIN_VALUE)
            if (it > old) editor.putInt("high.$game", it)
        }

        // These are exposures, not inferred competence. A future calibrated player
        // model can consume results, opponent strength and scenario difficulty.
        skillTags(game).forEach { skill ->
            editor.putInt("exposure.$skill", prefs.getInt("exposure.$skill", 0) + 1)
        }

        val achievementSet = currentAchievements().toMutableSet()
        val gamesAfter = prefs.getInt("games", 0) + 1
        if (gamesAfter >= 1) achievementSet += "first_game"
        if (gamesAfter >= 10) achievementSet += "table_regular"
        if (gamesAfter >= 50) achievementSet += "game_shelf_veteran"
        if (personalWin) achievementSet += "first_win"
        if (prefs.getInt("game.$game", 0) + 1 >= 10) achievementSet += "specialist:$game"
        editor.putString("achievements", JSONArray(achievementSet.toList().sorted()).toString())

        val history = loadRecent().toMutableList()
        history.add(
            0,
            JSONObject()
                .put("session_id", sessionId)
                .put("game", game)
                .put("winner", winner)
                .put("tracked_human_seat", trackedHumanSeat ?: JSONObject.NULL)
                .put("moves", moves)
                .put("score", score ?: JSONObject.NULL)
                .toString()
        )
        while (history.size > 20) history.removeAt(history.lastIndex)
        editor.putString("recent", JSONArray(history).toString())

        // Chronological, bounded dedupe. SharedPreferences StringSet has no
        // ordering contract, so the ordered JSON list is authoritative.
        val migratedLegacy = legacyRecorded
            .filterNot { it == sessionId || it in recordedOrder }
        val updatedOrder = (
            listOf(sessionId) +
                recordedOrder.filterNot { it == sessionId } +
                migratedLegacy
            ).take(256)
        editor.putString("recorded_session_order", JSONArray(updatedOrder).toString())
        // Keep a bounded compatibility set for older builds that only know this key.
        editor.putStringSet("recorded_sessions", updatedOrder.toSet())

        editor.apply()
    }

    fun snapshot(): GameDeckStatsSnapshot {
        val all = prefs.all
        fun ints(prefix: String) = all.filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
            .mapValues { (it.value as? Int) ?: 0 }

        // Migrate/display legacy v0.050/051 counters as exposure rather than skill.
        val exposure = ints("exposure.").toMutableMap()
        ints("skill.").forEach { (k, v) ->
            exposure[k] = maxOf(exposure[k] ?: 0, v)
        }

        return GameDeckStatsSnapshot(
            games = prefs.getInt("games",0),
            evaluatedGames = prefs.getInt("evaluated_games", prefs.getInt("wins",0)+prefs.getInt("losses",0)+prefs.getInt("draws",0)),
            wins = prefs.getInt("wins",0),
            losses = prefs.getInt("losses",0),
            draws = prefs.getInt("draws",0),
            sharedTableGames = prefs.getInt("shared_table_games",0),
            byGame = ints("game."),
            bestMoves = ints("bestmoves."),
            skillExposure = exposure,
            achievements = currentAchievements()
        )
    }

    fun toJson(): String {
        val s=snapshot()
        return JSONObject()
            .put("games",s.games)
            .put("evaluated_games",s.evaluatedGames)
            .put("wins",s.wins)
            .put("losses",s.losses)
            .put("draws",s.draws)
            .put("shared_table_games",s.sharedTableGames)
            .put("by_game",JSONObject(s.byGame))
            .put("best_moves",JSONObject(s.bestMoves))
            .put("skill_exposure",JSONObject(s.skillExposure))
            .put("achievements",JSONArray(s.achievements.toList()))
            .put("recent",JSONArray(loadRecent()))
            .toString()
    }

    private fun recordedSessionOrder(): List<String> {
        val raw = prefs.getString("recorded_session_order", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length())
            .map { arr.optString(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun currentAchievements(): Set<String> {
        val raw = prefs.getString("achievements", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }.toSet()
    }

    private fun loadRecent(): List<String> {
        val arr = runCatching { JSONArray(prefs.getString("recent", "[]") ?: "[]") }.getOrElse { JSONArray() }
        return (0 until arr.length()).map { arr.optString(it) }
    }

    private fun skillTags(game:String)=when(game){
        GameDeckEngine.CONNECT_FOUR, GameDeckExtraEngine.TIC_TAC_TOE, GameDeckExtraEngine.REVERSI -> listOf("planning","tactics","spatial")
        GameDeckEngine.MANCALA, GameDeckExtraEngine.NIM, GameDeckExtraEngine.SHUT_THE_BOX -> listOf("planning","resource_management")
        GameDeckEngine.MINESWEEPER, GameDeckExtraEngine.LIGHTS_OUT, GameDeckExtraEngine.CODEBREAKER -> listOf("deduction","planning")
        GameDeckExtraEngine.MEMORY -> listOf("memory")
        GameDeckExtraEngine.FIFTEEN, GameDeckExtraEngine.TWENTY_FORTY_EIGHT -> listOf("spatial","planning")
        GameDeckExtraEngine.SUDOKU -> listOf("logic","deduction","number")
        GameDeckExtraEngine.TAKUZU -> listOf("logic","deduction","pattern")
        GameDeckExtraEngine.DOTS_AND_BOXES -> listOf("planning","tactics","spatial")
        GameDeckExtraEngine.CHESS -> listOf("planning","tactics","spatial","strategy")
        GameDeckExtraEngine.GO_9X9 -> listOf("strategy","spatial","pattern","planning")
        else -> listOf("probability")
    }
}
