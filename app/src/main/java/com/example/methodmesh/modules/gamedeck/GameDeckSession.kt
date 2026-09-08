package com.example.methodmesh.modules.gamedeck

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Capability-local game-session contract.
 *
 * GameDeck owns this for now. Once GameDeck + Arcade settle on the same semantics,
 * the contract can move behind a shared game-system service boundary.
 */
enum class GameDeckSeatKind {
    HUMAN_LOCAL,
    CPU,
    REMOTE,
    SIMULATION
}

data class GameDeckPlayerSeat(
    val seat: Int,
    val kind: GameDeckSeatKind,
    val playerId: String? = null,
    val agentProfileId: String? = null
)

data class GameDeckSession(
    val sessionId: String,
    val gameId: String,
    val rulesVersion: String,
    val seats: List<GameDeckPlayerSeat>,
    val rngMode: String,
    /**
     * Internal replay seed. Do not serialize this directly for hidden-information
     * games while they are still in progress.
     */
    val seed: String
)

object GameDeckSessions {
    const val RULES_VERSION = "0.0.9"

    fun forGame(
        sessionId: String,
        game: String,
        rngMode: String,
        seed: String,
        mancalaCpu: Boolean = true,
        extraCpuOpponent: Boolean = false
    ): GameDeckSession {
        val seats = when (game) {
            GameDeckEngine.MINESWEEPER,
            GameDeckExtraEngine.LIGHTS_OUT,
            GameDeckExtraEngine.FIFTEEN,
            GameDeckExtraEngine.SHUT_THE_BOX,
            GameDeckExtraEngine.CODEBREAKER,
            GameDeckExtraEngine.TWENTY_FORTY_EIGHT,
            GameDeckExtraEngine.SUDOKU,
            GameDeckExtraEngine.TAKUZU ->
                listOf(GameDeckPlayerSeat(1, GameDeckSeatKind.HUMAN_LOCAL))

            GameDeckEngine.MANCALA ->
                if (mancalaCpu) humanVsCpu() else twoLocalHumans()

            GameDeckExtraEngine.TIC_TAC_TOE,
            GameDeckExtraEngine.REVERSI,
            GameDeckExtraEngine.NIM,
            GameDeckExtraEngine.CHESS,
            GameDeckExtraEngine.GO_9X9 ->
                if (extraCpuOpponent) humanVsCpu() else twoLocalHumans()

            GameDeckEngine.CONNECT_FOUR,
            GameDeckEngine.SNAKES_AND_LADDERS,
            GameDeckExtraEngine.MEMORY,
            GameDeckExtraEngine.DOTS_AND_BOXES ->
                twoLocalHumans()

            else -> emptyList()
        }

        return GameDeckSession(
            sessionId = sessionId,
            gameId = game,
            rulesVersion = RULES_VERSION,
            seats = seats,
            rngMode = rngMode,
            seed = seed
        )
    }

    fun trackedHumanSeat(session: GameDeckSession): Int? {
        val humans = session.seats.filter { it.kind == GameDeckSeatKind.HUMAN_LOCAL }
        return humans.singleOrNull()?.seat
    }

    private fun humanVsCpu() = listOf(
        GameDeckPlayerSeat(1, GameDeckSeatKind.HUMAN_LOCAL),
        GameDeckPlayerSeat(2, GameDeckSeatKind.CPU, agentProfileId = GameDeckAgents.STANDARD.id)
    )

    private fun twoLocalHumans() = listOf(
        GameDeckPlayerSeat(1, GameDeckSeatKind.HUMAN_LOCAL),
        GameDeckPlayerSeat(2, GameDeckSeatKind.HUMAN_LOCAL)
    )
}

/**
 * Public/private state boundary.
 *
 * Runtime state stays complete inside GameDeck. Snapshot/audit state is redacted
 * while a hidden-information game is active. Once the game is terminal, the seed
 * and hidden state may be revealed so a fixed-seed run can be independently
 * reproduced and checked against its commitment.
 */
object GameDeckSessionCodec {
    fun isHiddenInformationGame(game: String): Boolean = game in setOf(
        GameDeckEngine.MINESWEEPER,
        GameDeckExtraEngine.MEMORY,
        GameDeckExtraEngine.CODEBREAKER,
        GameDeckExtraEngine.SUDOKU,
        GameDeckExtraEngine.TAKUZU
    )

    fun isTerminal(stateJson: String): Boolean =
        runCatching { JSONObject(stateJson).optString("winner").isNotBlank() }.getOrDefault(false)

    fun publicStateJson(stateJson: String): String {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return stateJson }
        val game = s.optString("game")
        if (!isHiddenInformationGame(game) || s.optString("winner").isNotBlank()) {
            return stateJson
        }

        return when (game) {
            GameDeckExtraEngine.CODEBREAKER -> codebreakerPublic(s).toString()
            GameDeckEngine.MINESWEEPER -> minesweeperPublic(s).toString()
            GameDeckExtraEngine.MEMORY -> memoryPublic(s).toString()
            GameDeckExtraEngine.SUDOKU,
            GameDeckExtraEngine.TAKUZU -> puzzlePublic(s).toString()
            else -> stateJson
        }
    }

    /**
     * Fixed seeds are hidden while hidden-information games are active. For all
     * other cases the supplied seed is safe to emit as ordinary provenance.
     */
    fun publicSeed(game: String, stateJson: String, seed: String): String =
        if (isHiddenInformationGame(game) && !isTerminal(stateJson)) "" else seed

    /**
     * Commitment allows later fixed-seed verification without revealing the seed
     * during play. Secure-random runs do not claim deterministic replay.
     */
    fun rngCommitment(game: String, rngMode: String, seed: String): String {
        if (!isHiddenInformationGame(game) || !rngMode.equals("fixed_seed", ignoreCase = true)) {
            return ""
        }
        return sha256(commitmentCanonical(game, rngMode, seed))
    }

    fun verifyRngCommitment(
        game: String,
        rngMode: String,
        seed: String,
        commitment: String
    ): Boolean {
        if (commitment.isBlank()) return false
        return rngCommitment(game, rngMode, seed).equals(commitment, ignoreCase = true)
    }

    private fun commitmentCanonical(game: String, rngMode: String, seed: String): String =
        "methodmesh.gamedeck|commitment-v1|rules:${GameDeckSessions.RULES_VERSION}|$game|${rngMode.lowercase()}|$seed"

    fun publicSessionJson(session: GameDeckSession, stateJson: String): String {
        val publicSeed = publicSeed(session.gameId, stateJson, session.seed)
        val commitment = rngCommitment(session.gameId, session.rngMode, session.seed)
        val seats = JSONArray()
        session.seats.sortedBy { it.seat }.forEach { seat ->
            seats.put(
                JSONObject()
                    .put("seat", seat.seat)
                    .put("kind", seat.kind.name.lowercase())
                    .put("player_id", seat.playerId ?: JSONObject.NULL)
                    .put("agent_profile_id", seat.agentProfileId ?: JSONObject.NULL)
            )
        }
        return JSONObject()
            .put("session_id", session.sessionId)
            .put("game_id", session.gameId)
            .put("rules_version", session.rulesVersion)
            .put("seats", seats)
            .put("rng_mode", session.rngMode)
            .put("rng_commitment", commitment.ifBlank { JSONObject.NULL })
            .put("seed_revealed", publicSeed.ifBlank { JSONObject.NULL })
            .put("hidden_information", isHiddenInformationGame(session.gameId))
            .toString()
    }

    private fun codebreakerPublic(s: JSONObject): JSONObject =
        JSONObject()
            .put("game", s.optString("game"))
            .put("guess", copyArray(s.optJSONArray("guess")))
            .put("history", copyArray(s.optJSONArray("history")))
            .put("turn", s.optInt("turn", 1))
            .put("winner", s.optString("winner"))
            .put("moves", s.optInt("moves", 0))
            .put("private_state_redacted", true)

    private fun minesweeperPublic(s: JSONObject): JSONObject {
        val out = JSONObject(s.toString())
        val mines = intSet(s.optJSONArray("mines"))
        val revealed = intSet(s.optJSONArray("revealed"))
        val width = s.optInt("width", 9)
        val height = s.optInt("height", 9)

        val counts = JSONObject()
        revealed.sorted().forEach { idx ->
            if (idx !in mines) {
                counts.put(idx.toString(), neighbors(idx, width, height).count { it in mines })
            }
        }

        out.put("mines", JSONArray())
            .remove("seed")
        out.put("revealed_counts", counts)
            .put("private_state_redacted", true)
        return out
    }

    private fun puzzlePublic(s: JSONObject): JSONObject {
        val out = JSONObject(s.toString())
        out.remove("solution")
        out.put("private_state_redacted", true)
        return out
    }

    private fun memoryPublic(s: JSONObject): JSONObject {
        val cards = s.optJSONArray("cards") ?: JSONArray()
        val matched = s.optJSONArray("matched") ?: JSONArray()
        val face = intSet(s.optJSONArray("face_up"))
        val masked = JSONArray()
        for (i in 0 until cards.length()) {
            val visible = matched.optInt(i, 0) == 1 || i in face
            masked.put(if (visible) cards.optInt(i) else 0)
        }
        return JSONObject(s.toString())
            .put("cards", masked)
            .put("private_state_redacted", true)
    }

    private fun intSet(array: JSONArray?): Set<Int> {
        if (array == null) return emptySet()
        return (0 until array.length()).map { array.optInt(it) }.toSet()
    }

    private fun copyArray(array: JSONArray?): JSONArray =
        if (array == null) JSONArray() else JSONArray(array.toString())

    private fun neighbors(idx: Int, width: Int, height: Int): List<Int> {
        val x = idx % width
        val y = idx / width
        return buildList {
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until width && ny in 0 until height) add(ny * width + nx)
            }
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
