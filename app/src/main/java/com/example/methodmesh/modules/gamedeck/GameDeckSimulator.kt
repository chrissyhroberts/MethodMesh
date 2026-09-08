package com.example.methodmesh.modules.gamedeck

import org.json.JSONObject

enum class SimOutcome {
    SEAT_1_WIN,
    SEAT_2_WIN,
    DRAW,
    ABORTED,
    INVALID,
    UNSUPPORTED
}

data class SimMatchResult(
    val outcome: SimOutcome,
    val winner: String,
    val moves: Int,
    val passes: Int,
    val seed: String,
    val profileForSeat1: String,
    val profileForSeat2: String,
    val detail: String = ""
)

data class SimSeriesResult(
    val game: String,
    val requestedGames: Int,
    val completedGames: Int,
    val agentAWins: Int,
    val agentBWins: Int,
    val draws: Int,
    val aborted: Int,
    val invalid: Int,
    val unsupported: Int,
    val meanMoves: Double,
    val agentA: String,
    val agentB: String,
    val seat1Wins: Int,
    val seat2Wins: Int
)

/**
 * Headless CPU-vs-CPU arena.
 *
 * Series vary deterministic decision seeds and alternate seats. Engine/agent
 * failures are explicit non-results and are never folded into draw counts.
 */
object GameDeckSimulator {
    private val supportedGames = setOf(
        GameDeckExtraEngine.TIC_TAC_TOE,
        GameDeckExtraEngine.REVERSI,
        GameDeckExtraEngine.NIM
    )

    fun runSeries(
        game: String,
        games: Int = 100,
        agentA: GameDeckAgentProfile = GameDeckAgents.STANDARD,
        agentB: GameDeckAgentProfile = GameDeckAgents.CASUAL,
        seriesSeed: String = "arena"
    ): SimSeriesResult {
        val n = games.coerceIn(1, 5000)
        var aWins = 0
        var bWins = 0
        var draws = 0
        var aborted = 0
        var invalid = 0
        var unsupported = 0
        var seat1Wins = 0
        var seat2Wins = 0
        var completed = 0
        var completedMoves = 0L

        repeat(n) { index ->
            val aIsSeat1 = index % 2 == 0
            val seat1Profile = if (aIsSeat1) agentA else agentB
            val seat2Profile = if (aIsSeat1) agentB else agentA
            val match = runCatching {
                runMatch(
                    game = game,
                    seat1Profile = seat1Profile,
                    seat2Profile = seat2Profile,
                    matchSeed = "$seriesSeed|$game|$index"
                )
            }.getOrElse { error ->
                SimMatchResult(
                    outcome = SimOutcome.INVALID,
                    winner = "",
                    moves = 0,
                    passes = 0,
                    seed = "$seriesSeed|$game|$index",
                    profileForSeat1 = seat1Profile.id,
                    profileForSeat2 = seat2Profile.id,
                    detail = "match_exception:${error::class.java.simpleName}"
                )
            }

            when (match.outcome) {
                SimOutcome.SEAT_1_WIN -> {
                    completed++
                    completedMoves += match.moves
                    seat1Wins++
                    if (aIsSeat1) aWins++ else bWins++
                }
                SimOutcome.SEAT_2_WIN -> {
                    completed++
                    completedMoves += match.moves
                    seat2Wins++
                    if (aIsSeat1) bWins++ else aWins++
                }
                SimOutcome.DRAW -> {
                    completed++
                    completedMoves += match.moves
                    draws++
                }
                SimOutcome.ABORTED -> aborted++
                SimOutcome.INVALID -> invalid++
                SimOutcome.UNSUPPORTED -> unsupported++
            }
        }

        return SimSeriesResult(
            game = game,
            requestedGames = n,
            completedGames = completed,
            agentAWins = aWins,
            agentBWins = bWins,
            draws = draws,
            aborted = aborted,
            invalid = invalid,
            unsupported = unsupported,
            meanMoves = if (completed > 0) completedMoves.toDouble() / completed else 0.0,
            agentA = agentA.label,
            agentB = agentB.label,
            seat1Wins = seat1Wins,
            seat2Wins = seat2Wins
        )
    }

    fun runMatch(
        game: String,
        seat1Profile: GameDeckAgentProfile = GameDeckAgents.STANDARD,
        seat2Profile: GameDeckAgentProfile = GameDeckAgents.CASUAL,
        matchSeed: String = "sim",
        maxMoves: Int = 512
    ): SimMatchResult {
        if (game !in supportedGames) {
            return result(
                SimOutcome.UNSUPPORTED, "", 0, 0, matchSeed,
                seat1Profile, seat2Profile, "unsupported_game:$game"
            )
        }

        var state = GameDeckExtraEngine.newStateJson(game, "fixed_seed", matchSeed)
        var decisions = 0

        while (true) {
            val current = runCatching { JSONObject(state) }.getOrElse {
                return result(
                    SimOutcome.INVALID, "", decisions, 0, matchSeed,
                    seat1Profile, seat2Profile, "invalid_json_state"
                )
            }

            val winner = current.optString("winner")
            if (winner.isNotBlank()) {
                return terminalResult(
                    winner = winner,
                    state = current,
                    seed = matchSeed,
                    seat1Profile = seat1Profile,
                    seat2Profile = seat2Profile
                )
            }

            if (decisions >= maxMoves) {
                return result(
                    SimOutcome.ABORTED, "",
                    current.optInt("moves", decisions),
                    current.optInt("passes", 0),
                    matchSeed, seat1Profile, seat2Profile,
                    "max_moves_exceeded"
                )
            }

            val seat = current.optInt("turn", 1).coerceIn(1, 2)
            val profile = if (seat == 1) seat1Profile else seat2Profile
            val decision = runCatching {
                GameDeckAgents.choose(
                    game = game,
                    stateJson = state,
                    seat = seat,
                    profile = profile,
                    decisionSeed = "$matchSeed|decision|$decisions|seat$seat"
                )
            }.getOrElse { error ->
                return result(
                    SimOutcome.INVALID, "",
                    current.optInt("moves", decisions),
                    current.optInt("passes", 0),
                    matchSeed, seat1Profile, seat2Profile,
                    "agent_exception:${error::class.java.simpleName}"
                )
            }

            if (decision.action == null) {
                if (game == GameDeckExtraEngine.REVERSI) {
                    val resolved = GameDeckExtraEngine.reversiResolveBlockedTurn(state)
                    if (resolved != state) {
                        state = resolved
                        continue
                    }
                }
                return result(
                    SimOutcome.INVALID, "",
                    current.optInt("moves", decisions),
                    current.optInt("passes", 0),
                    matchSeed, seat1Profile, seat2Profile,
                    "agent_returned_no_action:${decision.diagnostic}"
                )
            }

            val next = applyAction(game, state, decision.action)
                ?: return result(
                    SimOutcome.INVALID, "",
                    current.optInt("moves", decisions),
                    current.optInt("passes", 0),
                    matchSeed, seat1Profile, seat2Profile,
                    "malformed_action:${decision.action}"
                )

            if (next == state) {
                return result(
                    SimOutcome.INVALID, "",
                    current.optInt("moves", decisions),
                    current.optInt("passes", 0),
                    matchSeed, seat1Profile, seat2Profile,
                    "action_did_not_change_state:${decision.action}"
                )
            }

            state = next
            decisions++
        }
    }

    private fun applyAction(game: String, stateJson: String, action: String): String? {
        return runCatching {
            when (game) {
                GameDeckExtraEngine.TIC_TAC_TOE ->
                    GameDeckExtraEngine.ticTacToeMove(stateJson, action.toInt())

                GameDeckExtraEngine.REVERSI ->
                    GameDeckExtraEngine.reversiMove(stateJson, action.toInt())

                GameDeckExtraEngine.NIM -> {
                    val parts = action.split(":")
                    if (parts.size != 2) {
                        null
                    } else {
                        GameDeckExtraEngine.nimMove(
                            stateJson,
                            parts[0].toInt(),
                            parts[1].toInt()
                        )
                    }
                }

                else -> null
            }
        }.getOrNull()
    }

    private fun terminalResult(
        winner: String,
        state: JSONObject,
        seed: String,
        seat1Profile: GameDeckAgentProfile,
        seat2Profile: GameDeckAgentProfile
    ): SimMatchResult {
        val outcome = when (winner) {
            "1" -> SimOutcome.SEAT_1_WIN
            "2" -> SimOutcome.SEAT_2_WIN
            "draw" -> SimOutcome.DRAW
            else -> SimOutcome.INVALID
        }
        return result(
            outcome = outcome,
            winner = winner,
            moves = state.optInt("moves", 0),
            passes = state.optInt("passes", 0),
            seed = seed,
            seat1Profile = seat1Profile,
            seat2Profile = seat2Profile,
            detail = if (outcome == SimOutcome.INVALID) "unknown_terminal:$winner" else ""
        )
    }

    private fun result(
        outcome: SimOutcome,
        winner: String,
        moves: Int,
        passes: Int,
        seed: String,
        seat1Profile: GameDeckAgentProfile,
        seat2Profile: GameDeckAgentProfile,
        detail: String
    ) = SimMatchResult(
        outcome = outcome,
        winner = winner,
        moves = moves,
        passes = passes,
        seed = seed,
        profileForSeat1 = seat1Profile.id,
        profileForSeat2 = seat2Profile.id,
        detail = detail
    )
}
