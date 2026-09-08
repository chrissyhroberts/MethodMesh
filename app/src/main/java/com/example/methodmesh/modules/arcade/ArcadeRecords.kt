package com.example.methodmesh.modules.arcade

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class ArcadeRecordSnapshot(
    val totalGames: Int,
    val snakeGames: Int,
    val snakeBestScore: Int,
    val snakeLongest: Int,
    val pongCpuWins: Int,
    val pongCpuLosses: Int,
    val pongSharedMatches: Int,
    val breakoutBestScore: Int,
    val breakoutClears: Int,
    val dodgeBestScore: Int
)

/**
 * Capability-local records. This is deliberately small and local-first.
 *
 * When the wider MethodMesh game system gains a shared player/record service,
 * Arcade should migrate to that public boundary rather than import GameDeck
 * persistence internals.
 */
class ArcadeRecordStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "methodmesh_arcade_records",
        Context.MODE_PRIVATE
    )

    fun record(sessionId: String, stateJson: String) {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return }
        if (!s.optBoolean("finished", false)) return

        val order = recordedOrder()
        if (sessionId in order) return

        val game = s.optString("game")
        val editor = prefs.edit()
            .putInt("total_games", prefs.getInt("total_games", 0) + 1)

        when (game) {
            ArcadeEngine.SNAKE -> {
                val score = s.optInt("score", 0)
                val length = s.optJSONArray("body")?.length() ?: 0
                editor
                    .putInt("snake_games", prefs.getInt("snake_games", 0) + 1)
                    .putInt("snake_best_score", maxOf(prefs.getInt("snake_best_score", 0), score))
                    .putInt("snake_longest", maxOf(prefs.getInt("snake_longest", 0), length))
            }

            ArcadeEngine.PONG -> {
                val cpu = s.optBoolean("cpu", true)
                val p1 = s.optInt("p1_score", 0)
                val p2 = s.optInt("p2_score", 0)
                if (cpu) {
                    if (p1 > p2) {
                        editor.putInt("pong_cpu_wins", prefs.getInt("pong_cpu_wins", 0) + 1)
                    } else {
                        editor.putInt("pong_cpu_losses", prefs.getInt("pong_cpu_losses", 0) + 1)
                    }
                } else {
                    editor.putInt(
                        "pong_shared_matches",
                        prefs.getInt("pong_shared_matches", 0) + 1
                    )
                }
            }

            ArcadeEngine.BREAKOUT -> {
                val score = s.optInt("score", 0)
                editor.putInt(
                    "breakout_best_score",
                    maxOf(prefs.getInt("breakout_best_score", 0), score)
                )
                if (s.optString("winner") == "cleared") {
                    editor.putInt(
                        "breakout_clears",
                        prefs.getInt("breakout_clears", 0) + 1
                    )
                }
            }

            ArcadeEngine.DODGE -> {
                val score = s.optInt("score", 0)
                editor.putInt(
                    "dodge_best_score",
                    maxOf(prefs.getInt("dodge_best_score", 0), score)
                )
            }
        }

        val updated = (listOf(sessionId) + order.filterNot { it == sessionId }).take(256)
        editor.putString("recorded_session_order", JSONArray(updated).toString())
        editor.apply()
    }

    fun snapshot(): ArcadeRecordSnapshot = ArcadeRecordSnapshot(
        totalGames = prefs.getInt("total_games", 0),
        snakeGames = prefs.getInt("snake_games", 0),
        snakeBestScore = prefs.getInt("snake_best_score", 0),
        snakeLongest = prefs.getInt("snake_longest", 0),
        pongCpuWins = prefs.getInt("pong_cpu_wins", 0),
        pongCpuLosses = prefs.getInt("pong_cpu_losses", 0),
        pongSharedMatches = prefs.getInt("pong_shared_matches", 0),
        breakoutBestScore = prefs.getInt("breakout_best_score", 0),
        breakoutClears = prefs.getInt("breakout_clears", 0),
        dodgeBestScore = prefs.getInt("dodge_best_score", 0)
    )


    fun toJson(): String {
        val s = snapshot()
        return JSONObject()
            .put("total_games", s.totalGames)
            .put("snake_games", s.snakeGames)
            .put("snake_best_score", s.snakeBestScore)
            .put("snake_longest", s.snakeLongest)
            .put("pong_cpu_wins", s.pongCpuWins)
            .put("pong_cpu_losses", s.pongCpuLosses)
            .put("pong_shared_matches", s.pongSharedMatches)
            .put("breakout_best_score", s.breakoutBestScore)
            .put("breakout_clears", s.breakoutClears)
            .put("dodge_best_score", s.dodgeBestScore)
            .toString()
    }

    private fun recordedOrder(): List<String> {
        val raw = prefs.getString("recorded_session_order", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrElse { JSONArray() }
        return (0 until arr.length())
            .map { arr.optString(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }
}
