package com.example.methodmesh.modules.gamedeck

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayDeque

/** Pure game-state functions owned by GameDeck. No Compose or MethodMesh orchestration here. */
object GameDeckEngine {
    const val LAUNCHER = "launcher"
    const val CONNECT_FOUR = "connect_four"
    const val SNAKES_AND_LADDERS = "snakes_and_ladders"
    const val MANCALA = "mancala"
    const val MINESWEEPER = "minesweeper"
    const val TIC_TAC_TOE = GameDeckExtraEngine.TIC_TAC_TOE
    const val REVERSI = GameDeckExtraEngine.REVERSI
    const val NIM = GameDeckExtraEngine.NIM
    const val LIGHTS_OUT = GameDeckExtraEngine.LIGHTS_OUT
    const val FIFTEEN = GameDeckExtraEngine.FIFTEEN
    const val MEMORY = GameDeckExtraEngine.MEMORY
    const val SHUT_THE_BOX = GameDeckExtraEngine.SHUT_THE_BOX
    const val CODEBREAKER = GameDeckExtraEngine.CODEBREAKER

    fun newStateJson(game: String): String = when (game) {
        SNAKES_AND_LADDERS -> snakesState(intArrayOf(1, 1), 0, "", 0, 0)
        MANCALA -> mancalaState(IntArray(14).apply {
            for (i in 0..5) this[i] = 4
            for (i in 7..12) this[i] = 4
        }, 0, "", 0)
        MINESWEEPER -> minesweeperBlankState(9, 9, 10, "")
        LAUNCHER -> JSONObject().put("game", LAUNCHER).put("moves", 0).toString()
        CONNECT_FOUR -> connectFourState(List(6) { List(7) { 0 } }, 1, "", 0)
        else -> if (GameDeckExtraEngine.isSupported(game)) GameDeckExtraEngine.newStateJson(game)
            else connectFourState(List(6) { List(7) { 0 } }, 1, "", 0)
    }

    // ---------------- Connect Four ----------------

    fun connectFourDrop(stateJson: String, column: Int): String {
        val state = JSONObject(stateJson)
        val board = decodeBoard(state.getJSONArray("board"))
        val player = state.optInt("turn", 1)
        if (state.optString("winner").isNotBlank()) return stateJson
        if (column !in 0..6) return stateJson
        val row = (5 downTo 0).firstOrNull { board[it][column] == 0 } ?: return stateJson
        board[row][column] = player
        val winner = if (connectFourWinner(board, row, column, player)) player.toString() else ""
        val boardFull = board.all { r -> r.all { it != 0 } }
        val effectiveWinner = if (winner.isNotBlank()) winner else if (boardFull) "draw" else ""
        val next = if (effectiveWinner.isBlank()) if (player == 1) 2 else 1 else player
        return connectFourState(board.map { it.toList() }, next, effectiveWinner, state.optInt("moves") + 1)
    }

    fun connectFourWinningCells(stateJson: String): Set<Pair<Int, Int>> {
        val state = JSONObject(stateJson)
        val winner = state.optString("winner").toIntOrNull() ?: return emptySet()
        val board = decodeBoard(state.getJSONArray("board"))
        val dirs = arrayOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        for (r in 0..5) for (c in 0..6) {
            if (board[r][c] != winner) continue
            for ((dr, dc) in dirs) {
                val cells = (0..3).map { k -> (r + dr * k) to (c + dc * k) }
                if (cells.all { (rr, cc) -> rr in 0..5 && cc in 0..6 && board[rr][cc] == winner }) return cells.toSet()
            }
        }
        return emptySet()
    }

    // ---------------- Snakes & Ladders ----------------

    /** Roll first, move second: the rules engine validates the move but the player performs it. */
    fun snakesRoll(stateJson: String, rngMode: String, seed: String): Pair<String, Int> {
        val state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank()) return stateJson to 0
        if (state.optInt("pending_roll", 0) > 0 || state.optString("pending_phase").isNotBlank()) return stateJson to 0
        val turn = state.optInt("turn", 0).coerceIn(0, 1)
        val move = state.optInt("moves", 0)
        val effectiveSeed = if (rngMode == "fixed_seed") {
            "${seed.ifBlank { "gamedeck-snakes" }}|move:$move|player:$turn"
        } else ""
        val roll = rollDice("d6", rngMode, effectiveSeed).coerceIn(1, 6)
        val positions = state.getJSONArray("positions")
        val from = positions.optInt(turn, 1)
        val rawTarget = from + roll
        val target = if (rawTarget <= 100) rawTarget else from
        state.put("last_roll", roll)
            .put("pending_roll", roll)
            .put("pending_from", from)
            .put("pending_to", target)
            .put("pending_phase", "move")
        return state.toString() to roll
    }

    fun snakesCommitMove(stateJson: String): String {
        val state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank() || state.optString("pending_phase") != "move") return stateJson
        val turn = state.optInt("turn", 0).coerceIn(0, 1)
        val positions = state.getJSONArray("positions")
        val p = intArrayOf(positions.optInt(0, 1), positions.optInt(1, 1))
        val target = state.optInt("pending_to", p[turn]).coerceIn(1, 100)
        p[turn] = target
        val jump = snakesJump(target)
        if (jump != target) {
            return snakesState(
                p, turn, "", state.optInt("moves", 0), state.optInt("last_roll", 0),
                pendingRoll = state.optInt("pending_roll", 0), pendingPhase = "jump", pendingFrom = target, pendingTo = jump
            )
        }
        val winner = if (target == 100) (turn + 1).toString() else ""
        val nextTurn = if (winner.isBlank()) 1 - turn else turn
        return snakesState(p, nextTurn, winner, state.optInt("moves", 0) + 1, state.optInt("last_roll", 0))
    }

    fun snakesCommitJump(stateJson: String): String {
        val state = JSONObject(stateJson)
        if (state.optString("pending_phase") != "jump") return stateJson
        val turn = state.optInt("turn", 0).coerceIn(0, 1)
        val positions = state.getJSONArray("positions")
        val p = intArrayOf(positions.optInt(0, 1), positions.optInt(1, 1))
        val target = state.optInt("pending_to", p[turn]).coerceIn(1, 100)
        p[turn] = target
        val winner = if (target == 100) (turn + 1).toString() else ""
        val nextTurn = if (winner.isBlank()) 1 - turn else turn
        return snakesState(p, nextTurn, winner, state.optInt("moves", 0) + 1, state.optInt("last_roll", 0))
    }

    fun snakesTransitions(): Pair<Map<Int, Int>, Map<Int, Int>> {
        val ladders = mapOf(4 to 14, 9 to 31, 20 to 38, 28 to 84, 40 to 59, 51 to 67, 63 to 81, 71 to 91)
        val snakes = mapOf(17 to 7, 54 to 34, 62 to 19, 64 to 60, 87 to 24, 93 to 73, 95 to 75, 99 to 78)
        return ladders to snakes
    }

    // ---------------- Mancala / Kalah ----------------

    fun mancalaMove(stateJson: String, pit: Int): String {
        val state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank()) return stateJson
        val pitsJson = state.getJSONArray("pits")
        val pits = IntArray(14) { pitsJson.optInt(it, 0) }
        val player = state.optInt("turn", 0).coerceIn(0, 1)
        val ownRange = if (player == 0) 0..5 else 7..12
        if (pit !in ownRange || pits[pit] <= 0) return stateJson

        var stones = pits[pit]
        pits[pit] = 0
        var cursor = pit
        while (stones > 0) {
            cursor = (cursor + 1) % 14
            if (player == 0 && cursor == 13) continue
            if (player == 1 && cursor == 6) continue
            pits[cursor]++
            stones--
        }

        val ownStore = if (player == 0) 6 else 13
        val ownLandingRange = if (player == 0) 0..5 else 7..12
        if (cursor in ownLandingRange && pits[cursor] == 1) {
            val opposite = 12 - cursor
            if (pits[opposite] > 0) {
                pits[ownStore] += pits[opposite] + 1
                pits[opposite] = 0
                pits[cursor] = 0
            }
        }

        var nextTurn = if (cursor == ownStore) player else 1 - player
        var winner = ""
        val side0Empty = (0..5).all { pits[it] == 0 }
        val side1Empty = (7..12).all { pits[it] == 0 }
        if (side0Empty || side1Empty) {
            pits[6] += (0..5).sumOf { pits[it] }
            pits[13] += (7..12).sumOf { pits[it] }
            for (i in 0..5) pits[i] = 0
            for (i in 7..12) pits[i] = 0
            winner = when {
                pits[6] > pits[13] -> "1"
                pits[13] > pits[6] -> "2"
                else -> "draw"
            }
            nextTurn = player
        }
        return mancalaState(pits, nextTurn, winner, state.optInt("moves", 0) + 1)
    }

    fun mancalaCpuChoice(stateJson: String): Int {
        val state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank()) return -1
        val player = state.optInt("turn", 0)
        val range = if (player == 0) 0..5 else 7..12
        val legal = range.filter { state.getJSONArray("pits").optInt(it, 0) > 0 }
        if (legal.isEmpty()) return -1
        val ownStore = if (player == 0) 6 else 13
        val beforeStore = state.getJSONArray("pits").optInt(ownStore, 0)
        return legal.maxByOrNull { pit ->
            val next = JSONObject(mancalaMove(stateJson, pit))
            val storeGain = next.getJSONArray("pits").optInt(ownStore, 0) - beforeStore
            val extraTurn = if (next.optInt("turn") == player && next.optString("winner").isBlank()) 20 else 0
            storeGain * 10 + extraTurn + state.getJSONArray("pits").optInt(pit, 0)
        } ?: legal.first()
    }

    fun mancalaCpuMove(stateJson: String): String {
        val best = mancalaCpuChoice(stateJson)
        return if (best >= 0) mancalaMove(stateJson, best) else stateJson
    }

    // ---------------- Minesweeper / GameLab ----------------

    fun minesweeperConfigure(width: Int, height: Int, mines: Int, seed: String): String =
        minesweeperBlankState(width.coerceIn(6, 16), height.coerceIn(6, 16), mines.coerceIn(5, 60), seed)

    fun minesweeperReveal(stateJson: String, x: Int, y: Int, rngMode: String, seed: String): String {
        var state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank()) return stateJson
        val w = state.optInt("width", 9)
        val h = state.optInt("height", 9)
        if (x !in 0 until w || y !in 0 until h) return stateJson

        val idx = y * w + x
        val existingFlags = jsonIntSet(state.getJSONArray("flags"))
        if (idx in existingFlags) return stateJson

        if (!state.optBoolean("generated", false)) {
            state = JSONObject(generateNoGuessMinesweeper(
                w, h, state.optInt("mine_count", 10), x, y, rngMode,
                seed.ifBlank { state.optString("seed") }
            ))
            // Flags placed before the first reveal are player state, not generator
            // state, and therefore survive board generation.
            state.put("flags", JSONArray(existingFlags.sorted()))
        }

        val mines = jsonIntSet(state.getJSONArray("mines"))
        val revealed = jsonIntSet(state.getJSONArray("revealed")).toMutableSet()
        if (idx in mines) {
            return state.put("winner", "mine").put("revealed", JSONArray((revealed + mines).sorted())).put("moves", state.optInt("moves") + 1).toString()
        }
        floodReveal(w, h, mines, revealed, idx)
        val won = revealed.size >= w * h - mines.size
        state.put("revealed", JSONArray(revealed.sorted()))
            .put("moves", state.optInt("moves") + 1)
            .put("winner", if (won) "cleared" else "")
        return state.toString()
    }

    fun minesweeperToggleFlag(stateJson: String, x: Int, y: Int): String {
        val state = JSONObject(stateJson)
        if (state.optString("winner").isNotBlank()) return stateJson
        val w = state.optInt("width", 9)
        val h = state.optInt("height", 9)
        if (x !in 0 until w || y !in 0 until h) return stateJson
        val idx = y * w + x
        val revealed = jsonIntSet(state.getJSONArray("revealed"))
        if (idx in revealed) return stateJson
        val flags = jsonIntSet(state.getJSONArray("flags")).toMutableSet()
        if (!flags.add(idx)) flags.remove(idx)
        return state.put("flags", JSONArray(flags.sorted())).toString()
    }

    fun minesweeperCell(stateJson: String, x: Int, y: Int): MinesweeperCell {
        val state = JSONObject(stateJson)
        val w = state.optInt("width", 9)
        val h = state.optInt("height", 9)
        val idx = y * w + x
        val mines = jsonIntSet(state.getJSONArray("mines"))
        val revealed = jsonIntSet(state.getJSONArray("revealed"))
        val flags = jsonIntSet(state.getJSONArray("flags"))
        val mine = idx in mines
        return MinesweeperCell(
            revealed = idx in revealed,
            flagged = idx in flags,
            mine = mine,
            adjacent = if (mine) -1 else neighbors(idx, w, h).count { it in mines }
        )
    }

    fun minesweeperMetrics(stateJson: String): GameLabMetrics {
        val state = JSONObject(stateJson)
        val w = state.optInt("width", 9)
        val h = state.optInt("height", 9)
        val mines = state.optInt("mine_count", 10)
        return GameLabMetrics(
            width = w,
            height = h,
            mineCount = mines,
            density = mines.toDouble() / (w * h).toDouble(),
            noGuessValidated = state.optBoolean("no_guess_validated", false),
            generationAttempts = state.optInt("generation_attempts", 0),
            solverSteps = state.optInt("solver_steps", 0)
        )
    }

    private fun generateNoGuessMinesweeper(width: Int, height: Int, mineCount: Int, firstX: Int, firstY: Int, rngMode: String, seed: String): String {
        val total = width * height
        val excluded = (neighbors(firstY * width + firstX, width, height) + (firstY * width + firstX)).toSet()
        val targetMines = mineCount.coerceAtMost(total - excluded.size - 1)
        var best: Set<Int> = emptySet()
        var bestSteps = 0
        var attempts = 0
        for (attempt in 0 until 120) {
            attempts = attempt + 1
            val mines = mutableSetOf<Int>()
            var batch = 0
            while (mines.size < targetMines && batch < 8) {
                val drawsNeeded = ((targetMines - mines.size) * 4).coerceAtLeast(16)
                val expression = "${drawsNeeded}d$total"
                val effectiveSeed = if (rngMode == "fixed_seed") "${seed.ifBlank { "gamedeck-mines" }}|attempt:$attempt|batch:$batch" else ""
                val values = rollDiceValues(expression, rngMode, effectiveSeed)
                values.forEach { value ->
                    val idx = value - 1
                    if (idx !in excluded && idx in 0 until total && mines.size < targetMines) mines += idx
                }
                batch++
            }
            if (mines.size != targetMines) continue
            val solve = noGuessSolve(width, height, mines, firstY * width + firstX)
            if (solve.solved) {
                return minesweeperState(width, height, targetMines, mines, emptySet(), emptySet(), "", 0, true, seed, attempts, solve.steps)
            }
            if (best.isEmpty()) {
                best = mines.toSet(); bestSteps = solve.steps
            }
        }
        // Fallback remains playable, but the metric is explicit rather than pretending validation succeeded.
        if (best.isEmpty()) {
            best = (0 until total).filter { it !in excluded }.take(targetMines).toSet()
        }
        return minesweeperState(width, height, targetMines, best, emptySet(), emptySet(), "", 0, true, seed, attempts, bestSteps, noGuessValidated = false)
    }

    private data class SolveResult(val solved: Boolean, val steps: Int)

    private fun noGuessSolve(width: Int, height: Int, mines: Set<Int>, first: Int): SolveResult {
        val revealed = mutableSetOf<Int>()
        val flagged = mutableSetOf<Int>()
        floodReveal(width, height, mines, revealed, first)
        var changed = true
        var steps = 0
        while (changed && steps < width * height * 4) {
            changed = false
            steps++
            val snapshot = revealed.toList()
            for (cell in snapshot) {
                val number = neighbors(cell, width, height).count { it in mines }
                if (number <= 0) continue
                val ns = neighbors(cell, width, height)
                val hidden = ns.filter { it !in revealed && it !in flagged }
                val knownMines = ns.count { it in flagged }
                if (hidden.isNotEmpty() && number - knownMines == hidden.size) {
                    hidden.forEach { if (flagged.add(it)) changed = true }
                } else if (hidden.isNotEmpty() && number == knownMines) {
                    hidden.forEach { safe ->
                        val before = revealed.size
                        floodReveal(width, height, mines, revealed, safe)
                        if (revealed.size > before) changed = true
                    }
                }
            }
            if (revealed.size >= width * height - mines.size) return SolveResult(true, steps)
        }
        return SolveResult(revealed.size >= width * height - mines.size, steps)
    }

    private fun floodReveal(width: Int, height: Int, mines: Set<Int>, revealed: MutableSet<Int>, start: Int) {
        if (start in mines || start !in 0 until width * height) return
        val queue = ArrayDeque<Int>()
        if (revealed.add(start)) queue.add(start)
        while (queue.isNotEmpty()) {
            val cell = queue.removeFirst()
            val count = neighbors(cell, width, height).count { it in mines }
            if (count != 0) continue
            neighbors(cell, width, height).forEach { n ->
                if (n !in mines && revealed.add(n)) queue.add(n)
            }
        }
    }

    private fun neighbors(idx: Int, width: Int, height: Int): List<Int> {
        val x = idx % width
        val y = idx / width
        return buildList {
            for (dy in -1..1) for (dx in -1..1) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until width && ny in 0 until height) add(ny * width + nx)
            }
        }
    }

    // ---------------- Summary / shared ----------------

    fun stateSummary(stateJson: String): GameStateSummary {
        val state = JSONObject(stateJson)
        val game = state.optString("game")
        val winnerRaw = state.optString("winner")
        val turn = when (game) {
            SNAKES_AND_LADDERS -> "Player ${state.optInt("turn", 0) + 1}"
            MANCALA -> "Player ${state.optInt("turn", 0) + 1}"
            MINESWEEPER -> if (winnerRaw.isBlank()) "In progress" else ""
            LAUNCHER -> "Choose a game"
            else -> "Player ${state.optInt("turn", 1)}"
        }
        val winner = when {
            winnerRaw == "draw" -> "Draw"
            winnerRaw == "mine" -> "Mine hit"
            winnerRaw == "cleared" -> "Cleared"
            winnerRaw == "failed" -> "Not cracked"
            winnerRaw == "stuck" -> "Round over"
            winnerRaw.isNotBlank() -> "Player $winnerRaw"
            else -> ""
        }
        return GameStateSummary(game, turn, winner, state.optInt("moves", 0))
    }

    private fun rollDice(expression: String, rngMode: String, seed: String): Int =
        As100DiceSimulationMethod.generate(
            mapOf(
                "expression" to expression,
                "roll_count" to "1",
                "player_count" to "1",
                "history_output" to "false",
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to "off"
            )
        )[DiceSimulationFields.TOTAL]?.toIntOrNull() ?: 1

    private fun rollDiceValues(expression: String, rngMode: String, seed: String): List<Int> {
        val out = As100DiceSimulationMethod.generate(
            mapOf(
                "expression" to expression,
                "roll_count" to "1",
                "player_count" to "1",
                "history_output" to "false",
                "rng_mode" to rngMode,
                "seed" to seed,
                "animation_mode" to "off"
            )
        )
        return out[DiceSimulationFields.LAST_VALUES_CSV].orEmpty()
            .split(',').mapNotNull { it.trim().toIntOrNull() }
    }

    private fun connectFourState(board: List<List<Int>>, turn: Int, winner: String, moves: Int): String {
        val rows = JSONArray(); board.forEach { row -> rows.put(JSONArray(row)) }
        return JSONObject().put("game", CONNECT_FOUR).put("board", rows).put("turn", turn).put("winner", winner).put("moves", moves).toString()
    }

    private fun snakesState(
        positions: IntArray,
        turn: Int,
        winner: String,
        moves: Int,
        lastRoll: Int,
        pendingRoll: Int = 0,
        pendingPhase: String = "",
        pendingFrom: Int = 0,
        pendingTo: Int = 0
    ): String = JSONObject()
        .put("game", SNAKES_AND_LADDERS).put("positions", JSONArray().put(positions[0]).put(positions[1]))
        .put("turn", turn).put("winner", winner).put("moves", moves).put("last_roll", lastRoll)
        .put("pending_roll", pendingRoll).put("pending_phase", pendingPhase)
        .put("pending_from", pendingFrom).put("pending_to", pendingTo).toString()

    private fun mancalaState(pits: IntArray, turn: Int, winner: String, moves: Int): String = JSONObject()
        .put("game", MANCALA).put("pits", JSONArray(pits.toList())).put("turn", turn).put("winner", winner).put("moves", moves).toString()

    private fun minesweeperBlankState(width: Int, height: Int, mines: Int, seed: String): String = JSONObject()
        .put("game", MINESWEEPER).put("width", width).put("height", height).put("mine_count", mines)
        .put("mines", JSONArray()).put("revealed", JSONArray()).put("flags", JSONArray())
        .put("generated", false).put("no_guess_validated", false).put("generation_attempts", 0).put("solver_steps", 0)
        .put("seed", seed).put("winner", "").put("moves", 0).toString()

    private fun minesweeperState(width: Int, height: Int, mineCount: Int, mines: Set<Int>, revealed: Set<Int>, flags: Set<Int>, winner: String, moves: Int, generated: Boolean, seed: String, attempts: Int, solverSteps: Int, noGuessValidated: Boolean = true): String = JSONObject()
        .put("game", MINESWEEPER).put("width", width).put("height", height).put("mine_count", mineCount)
        .put("mines", JSONArray(mines.sorted())).put("revealed", JSONArray(revealed.sorted())).put("flags", JSONArray(flags.sorted()))
        .put("generated", generated).put("no_guess_validated", noGuessValidated).put("generation_attempts", attempts).put("solver_steps", solverSteps)
        .put("seed", seed).put("winner", winner).put("moves", moves).toString()

    private fun decodeBoard(json: JSONArray): Array<IntArray> = Array(6) { r -> IntArray(7) { c -> json.getJSONArray(r).optInt(c, 0) } }

    private fun connectFourWinner(board: Array<IntArray>, row: Int, col: Int, player: Int): Boolean {
        val directions = arrayOf(1 to 0, 0 to 1, 1 to 1, 1 to -1)
        return directions.any { (dr, dc) -> 1 + count(board, row, col, dr, dc, player) + count(board, row, col, -dr, -dc, player) >= 4 }
    }

    private fun count(board: Array<IntArray>, row: Int, col: Int, dr: Int, dc: Int, player: Int): Int {
        var r = row + dr; var c = col + dc; var n = 0
        while (r in 0..5 && c in 0..6 && board[r][c] == player) { n++; r += dr; c += dc }
        return n
    }

    private fun snakesJump(position: Int): Int = when (position) {
        4 -> 14; 9 -> 31; 20 -> 38; 28 -> 84; 40 -> 59; 51 -> 67; 63 -> 81; 71 -> 91
        17 -> 7; 54 -> 34; 62 -> 19; 64 -> 60; 87 -> 24; 93 -> 73; 95 -> 75; 99 -> 78
        else -> position
    }

    private fun jsonIntSet(array: JSONArray): Set<Int> = (0 until array.length()).map { array.optInt(it) }.toSet()
}

data class GameStateSummary(val game: String, val turn: String, val winner: String, val moves: Int)
data class MinesweeperCell(val revealed: Boolean, val flagged: Boolean, val mine: Boolean, val adjacent: Int)
data class GameLabMetrics(val width: Int, val height: Int, val mineCount: Int, val density: Double, val noGuessValidated: Boolean, val generationAttempts: Int, val solverSteps: Int)
