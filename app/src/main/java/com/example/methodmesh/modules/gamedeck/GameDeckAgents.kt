package com.example.methodmesh.modules.gamedeck

import org.json.JSONObject

enum class GameDeckCpuLevel { Casual, Standard, Sharp }

data class GameDeckAgentProfile(
    val id: String,
    val label: String,
    val level: GameDeckCpuLevel,
    val description: String
)

data class GameDeckAgentDecision(
    val action: String?,
    val profileId: String,
    val seat: Int,
    val diagnostic: String
)

/**
 * Seat-aware CPU interface used by both interactive play and the headless arena.
 *
 * Casual intentionally introduces deterministic seeded variation.
 * Standard uses the existing game heuristics.
 * Sharp applies stronger game-specific look-ahead/position weighting where present.
 */
object GameDeckAgents {
    val CASUAL = GameDeckAgentProfile("casual", "Casual CPU", GameDeckCpuLevel.Casual, "Varied legal play with only light tactical preference.")
    val STANDARD = GameDeckAgentProfile("standard", "Standard CPU", GameDeckCpuLevel.Standard, "Uses game-specific immediate tactics and heuristics.")
    val SHARP = GameDeckAgentProfile("sharp", "Sharp CPU", GameDeckCpuLevel.Sharp, "Uses stronger look-ahead or positional evaluation where supported.")
    val profiles = listOf(CASUAL, STANDARD, SHARP)

    fun choose(
        game: String,
        stateJson: String,
        seat: Int,
        profile: GameDeckAgentProfile = STANDARD,
        decisionSeed: String = ""
    ): GameDeckAgentDecision {
        val action = when (game) {
            GameDeckExtraEngine.TIC_TAC_TOE -> chooseTicTacToe(stateJson, seat, profile, decisionSeed)
            GameDeckExtraEngine.REVERSI -> chooseReversi(stateJson, seat, profile, decisionSeed)
            GameDeckExtraEngine.NIM -> chooseNim(stateJson, seat, profile, decisionSeed)
            else -> null
        }
        return GameDeckAgentDecision(
            action = action,
            profileId = profile.id,
            seat = seat,
            diagnostic = if (action == null) "no_legal_action" else "${profile.id}:$action"
        )
    }

    private fun chooseTicTacToe(
        stateJson: String,
        seat: Int,
        profile: GameDeckAgentProfile,
        seed: String
    ): String? {
        val s = JSONObject(stateJson)
        val board = IntArray(9) { s.getJSONArray("board").optInt(it) }
        val legal = (0..8).filter { board[it] == 0 }
        if (legal.isEmpty()) return null

        val choice = when (profile.level) {
            GameDeckCpuLevel.Casual -> {
                // Still take an immediate win, otherwise vary.
                legal.firstOrNull { c ->
                    val x = board.copyOf(); x[c] = seat
                    winnerTtt(x) == seat
                } ?: legal[seedIndex(seed, legal.size)]
            }
            GameDeckCpuLevel.Standard -> GameDeckExtraEngine.ticTacToeCpuChoice(stateJson, seat)
            GameDeckCpuLevel.Sharp -> {
                legal.maxByOrNull { c ->
                    val x = board.copyOf()
                    x[c] = seat
                    minimaxTtt(x, 3 - seat, seat)
                } ?: legal.first()
            }
        }
        return choice.takeIf { it >= 0 }?.toString()
    }

    private fun minimaxTtt(board: IntArray, turn: Int, root: Int): Int {
        val winner = winnerTtt(board)
        if (winner == root) return 10
        if (winner == 3 - root) return -10
        val legal = (0..8).filter { board[it] == 0 }
        if (legal.isEmpty()) return 0
        return if (turn == root) {
            legal.maxOf { c ->
                val x = board.copyOf(); x[c] = turn
                minimaxTtt(x, 3 - turn, root)
            }
        } else {
            legal.minOf { c ->
                val x = board.copyOf(); x[c] = turn
                minimaxTtt(x, 3 - turn, root)
            }
        }
    }

    private fun winnerTtt(b: IntArray): Int {
        val lines = arrayOf(
            intArrayOf(0,1,2), intArrayOf(3,4,5), intArrayOf(6,7,8),
            intArrayOf(0,3,6), intArrayOf(1,4,7), intArrayOf(2,5,8),
            intArrayOf(0,4,8), intArrayOf(2,4,6)
        )
        for (l in lines) {
            val v = b[l[0]]
            if (v != 0 && v == b[l[1]] && v == b[l[2]]) return v
        }
        return 0
    }

    private fun chooseReversi(
        stateJson: String,
        seat: Int,
        profile: GameDeckAgentProfile,
        seed: String
    ): String? {
        val legal = GameDeckExtraEngine.reversiLegalMoves(stateJson, seat)
        if (legal.isEmpty()) return null
        val choice = when (profile.level) {
            GameDeckCpuLevel.Casual -> legal[seedIndex(seed, legal.size)]
            GameDeckCpuLevel.Standard -> GameDeckExtraEngine.reversiCpuChoice(stateJson, seat)
            GameDeckCpuLevel.Sharp -> {
                val corners = setOf(0,7,56,63)
                val dangerous = setOf(1,6,8,9,14,15,48,49,54,55,57,62)
                legal.maxByOrNull { cell ->
                    var score = 0
                    if (cell in corners) score += 1000
                    if (cell in dangerous) score -= 80
                    val next = GameDeckExtraEngine.reversiMove(stateJson, cell)
                    val nextJson = JSONObject(next)
                    val nextBoard = nextJson.getJSONArray("board")
                    val own = (0 until 64).count { nextBoard.optInt(it) == seat }
                    val opp = (0 until 64).count { nextBoard.optInt(it) == 3 - seat }
                    val mobility = GameDeckExtraEngine.reversiLegalMoves(next, 3 - seat).size
                    score + (own - opp) - mobility * 4
                } ?: legal.first()
            }
        }
        return choice.takeIf { it >= 0 }?.toString()
    }

    private fun chooseNim(
        stateJson: String,
        seat: Int,
        profile: GameDeckAgentProfile,
        seed: String
    ): String? {
        val s = JSONObject(stateJson)
        val heaps = IntArray(3) { s.getJSONArray("heaps").optInt(it) }
        val legal = buildList {
            heaps.forEachIndexed { heap, count ->
                for (take in 1..count) add("$heap:$take")
            }
        }
        if (legal.isEmpty()) return null
        return when (profile.level) {
            GameDeckCpuLevel.Casual -> legal[seedIndex(seed, legal.size)]
            GameDeckCpuLevel.Standard -> {
                // Usually make the mathematically strong move, but occasionally
                // choose another legal move. This produces a real middle tier.
                if (seedIndex("$seed|standard-nim", 5) == 0) {
                    legal[seedIndex("$seed|fallback", legal.size)]
                } else {
                    val (heap, take) = GameDeckExtraEngine.nimCpuChoice(stateJson)
                    if (heap >= 0) "$heap:$take" else null
                }
            }
            GameDeckCpuLevel.Sharp -> {
                val (heap, take) = GameDeckExtraEngine.nimCpuChoice(stateJson)
                if (heap >= 0) "$heap:$take" else null
            }
        }
    }

    private fun seedIndex(seed: String, bound: Int): Int {
        if (bound <= 1) return 0
        return (seed.hashCode() and Int.MAX_VALUE) % bound
    }
}
