package com.example.methodmesh.modules.gamedeck

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object GameDeckPuzzleDifficulty {
    const val EASY = "easy"
    const val MEDIUM = "medium"
    const val HARD = "hard"
    const val EXPERT = "expert"

    val values = listOf(EASY, MEDIUM, HARD, EXPERT)

    fun normalize(value: String): String =
        value.lowercase().takeIf { it in values } ?: MEDIUM

    fun next(value: String): String {
        val current = normalize(value)
        val index = values.indexOf(current)
        return values[(index + 1) % values.size]
    }

    fun label(value: String): String =
        normalize(value).replaceFirstChar { it.titlecase() }
}

/**
 * Procedural single-player logic puzzles.
 *
 * Generation follows the GameLab pattern:
 *
 *     generate complete valid state
 *       -> remove clues
 *       -> solve/count solutions
 *       -> reject removals that destroy uniqueness
 *       -> accept puzzle
 *
 * The solution remains capability-private while play is active.
 */
internal object GameDeckPuzzleEngine {

    // -----------------------------------------------------------------
    // Sudoku
    // -----------------------------------------------------------------

    fun sudokuInitial(
        rngMode: String,
        seed: String,
        difficulty: String = GameDeckPuzzleDifficulty.MEDIUM,
        nonce: Int = 0
    ): String {
        val normalizedDifficulty = GameDeckPuzzleDifficulty.normalize(difficulty)
        val solved = randomizedSudokuSolution(
            rngMode,
            "$seed|sudoku|$normalizedDifficulty|$nonce"
        )
        val puzzle = solved.toMutableList()

        val targetClues = when (normalizedDifficulty) {
            GameDeckPuzzleDifficulty.EASY -> 42
            GameDeckPuzzleDifficulty.HARD -> 31
            GameDeckPuzzleDifficulty.EXPERT -> 28
            else -> 36
        }

        val order = modularOrder(
            size = 81,
            rngMode = rngMode,
            seed = "$seed|sudoku|$normalizedDifficulty|$nonce|removal"
        )

        var clues = 81
        for (index in order) {
            if (clues <= targetClues) break
            val old = puzzle[index]
            puzzle[index] = 0
            if (countSudokuSolutions(puzzle, limit = 2) != 1) {
                puzzle[index] = old
            } else {
                clues -= 1
            }
        }

        val givens = puzzle.map { if (it == 0) 0 else 1 }

        return JSONObject()
            .put("game", GameDeckExtraEngine.SUDOKU)
            .put("difficulty", normalizedDifficulty)
            .put("nonce", nonce)
            .put("grid", JSONArray(puzzle))
            .put("givens", JSONArray(givens))
            .put("solution", JSONArray(solved))
            .put("clues", clues)
            .put("turn", 1)
            .put("winner", "")
            .put("moves", 0)
            .toString()
    }

    fun sudokuSetCell(
        stateJson: String,
        index: Int,
        value: Int
    ): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank() || index !in 0..80 || value !in 0..9) {
            return stateJson
        }

        val givens = s.getJSONArray("givens")
        if (givens.optInt(index) == 1) return stateJson

        val gridJson = s.getJSONArray("grid")
        val grid = MutableList(81) { gridJson.optInt(it) }
        if (grid[index] == value) return stateJson

        grid[index] = value
        val solution = s.getJSONArray("solution")
        val solved = (0 until 81).all { grid[it] == solution.optInt(it) }

        return JSONObject(s.toString())
            .put("grid", JSONArray(grid))
            .put("moves", s.optInt("moves", 0) + 1)
            .put("winner", if (solved) "cleared" else "")
            .toString()
    }

    fun sudokuConflictCells(stateJson: String): Set<Int> {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return emptySet() }
        val grid = s.optJSONArray("grid") ?: return emptySet()
        val out = mutableSetOf<Int>()

        fun markDuplicates(indices: List<Int>) {
            val byValue = indices
                .filter { grid.optInt(it) != 0 }
                .groupBy { grid.optInt(it) }
            byValue.values.filter { it.size > 1 }.forEach { out += it }
        }

        for (r in 0 until 9) {
            markDuplicates((0 until 9).map { c -> r * 9 + c })
        }
        for (c in 0 until 9) {
            markDuplicates((0 until 9).map { r -> r * 9 + c })
        }
        for (br in 0 until 3) for (bc in 0 until 3) {
            markDuplicates(
                buildList {
                    for (dr in 0 until 3) for (dc in 0 until 3) {
                        add((br * 3 + dr) * 9 + (bc * 3 + dc))
                    }
                }
            )
        }
        return out
    }

    private fun randomizedSudokuSolution(
        rngMode: String,
        seed: String
    ): List<Int> {
        val digits = shuffled((1..9).toList(), rngMode, "$seed|digits")
        val bands = shuffled(listOf(0, 1, 2), rngMode, "$seed|bands")
        val stacks = shuffled(listOf(0, 1, 2), rngMode, "$seed|stacks")

        val rows = bands.flatMap { band ->
            shuffled(listOf(0, 1, 2), rngMode, "$seed|rows|$band")
                .map { within -> band * 3 + within }
        }
        val cols = stacks.flatMap { stack ->
            shuffled(listOf(0, 1, 2), rngMode, "$seed|cols|$stack")
                .map { within -> stack * 3 + within }
        }

        return buildList(81) {
            for (r in rows) for (c in cols) {
                val canonical = (r * 3 + r / 3 + c) % 9
                add(digits[canonical])
            }
        }
    }

    private fun countSudokuSolutions(
        puzzle: List<Int>,
        limit: Int
    ): Int {
        val cells = puzzle.toIntArray()
        val rowMask = IntArray(9)
        val colMask = IntArray(9)
        val boxMask = IntArray(9)

        for (i in cells.indices) {
            val value = cells[i]
            if (value == 0) continue
            val bit = 1 shl value
            val r = i / 9
            val c = i % 9
            val b = (r / 3) * 3 + c / 3
            if ((rowMask[r] and bit) != 0 ||
                (colMask[c] and bit) != 0 ||
                (boxMask[b] and bit) != 0
            ) return 0
            rowMask[r] = rowMask[r] or bit
            colMask[c] = colMask[c] or bit
            boxMask[b] = boxMask[b] or bit
        }

        var found = 0

        fun search() {
            if (found >= limit) return

            var bestIndex = -1
            var bestMask = 0
            var bestCount = 10

            for (i in cells.indices) {
                if (cells[i] != 0) continue
                val r = i / 9
                val c = i % 9
                val b = (r / 3) * 3 + c / 3
                val used = rowMask[r] or colMask[c] or boxMask[b]
                val mask = 0x3FE and used.inv()
                val count = Integer.bitCount(mask)
                if (count == 0) return
                if (count < bestCount) {
                    bestCount = count
                    bestMask = mask
                    bestIndex = i
                    if (count == 1) break
                }
            }

            if (bestIndex == -1) {
                found += 1
                return
            }

            val r = bestIndex / 9
            val c = bestIndex % 9
            val b = (r / 3) * 3 + c / 3
            var mask = bestMask

            while (mask != 0 && found < limit) {
                val bit = mask and -mask
                val value = Integer.numberOfTrailingZeros(bit)
                cells[bestIndex] = value
                rowMask[r] = rowMask[r] or bit
                colMask[c] = colMask[c] or bit
                boxMask[b] = boxMask[b] or bit

                search()

                cells[bestIndex] = 0
                rowMask[r] = rowMask[r] and bit.inv()
                colMask[c] = colMask[c] and bit.inv()
                boxMask[b] = boxMask[b] and bit.inv()
                mask -= bit
            }
        }

        search()
        return found
    }

    // -----------------------------------------------------------------
    // Takuzu / Binary Puzzle (6x6)
    // -----------------------------------------------------------------

    fun takuzuInitial(
        rngMode: String,
        seed: String,
        difficulty: String = GameDeckPuzzleDifficulty.MEDIUM,
        nonce: Int = 0
    ): String {
        val normalizedDifficulty = GameDeckPuzzleDifficulty.normalize(difficulty)
        val solution = transformedTakuzuSolution(
            rngMode,
            "$seed|takuzu|$normalizedDifficulty|$nonce"
        )
        val puzzle = solution.toMutableList()

        val targetClues = when (normalizedDifficulty) {
            GameDeckPuzzleDifficulty.EASY -> 26
            GameDeckPuzzleDifficulty.HARD -> 18
            GameDeckPuzzleDifficulty.EXPERT -> 16
            else -> 22
        }

        val order = modularOrder(
            size = 36,
            rngMode = rngMode,
            seed = "$seed|takuzu|$normalizedDifficulty|$nonce|removal"
        )

        var clues = 36
        for (index in order) {
            if (clues <= targetClues) break
            val old = puzzle[index]
            puzzle[index] = -1
            if (countTakuzuSolutions(puzzle, limit = 2) != 1) {
                puzzle[index] = old
            } else {
                clues -= 1
            }
        }

        val givens = puzzle.map { if (it == -1) 0 else 1 }
        val publicGrid = puzzle.map { if (it == -1) 0 else it + 1 }
        val encodedSolution = solution.map { it + 1 }

        return JSONObject()
            .put("game", GameDeckExtraEngine.TAKUZU)
            .put("difficulty", normalizedDifficulty)
            .put("nonce", nonce)
            .put("size", 6)
            .put("grid", JSONArray(publicGrid))
            .put("givens", JSONArray(givens))
            .put("solution", JSONArray(encodedSolution))
            .put("clues", clues)
            .put("turn", 1)
            .put("winner", "")
            .put("moves", 0)
            .toString()
    }

    fun takuzuCycleCell(
        stateJson: String,
        index: Int
    ): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank() || index !in 0..35) return stateJson
        if (s.getJSONArray("givens").optInt(index) == 1) return stateJson

        val gridJson = s.getJSONArray("grid")
        val grid = MutableList(36) { gridJson.optInt(it) }
        grid[index] = when (grid[index]) {
            0 -> 1
            1 -> 2
            else -> 0
        }

        val solution = s.getJSONArray("solution")
        val solved = (0 until 36).all { grid[it] == solution.optInt(it) }

        return JSONObject(s.toString())
            .put("grid", JSONArray(grid))
            .put("moves", s.optInt("moves", 0) + 1)
            .put("winner", if (solved) "cleared" else "")
            .toString()
    }

    fun takuzuConflictCells(stateJson: String): Set<Int> {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return emptySet() }
        val grid = s.optJSONArray("grid") ?: return emptySet()
        val size = 6
        val out = mutableSetOf<Int>()

        fun line(indices: List<Int>) {
            // Three identical adjacent symbols are illegal.
            for (i in 0..3) {
                val a = grid.optInt(indices[i])
                val b = grid.optInt(indices[i + 1])
                val c = grid.optInt(indices[i + 2])
                if (a != 0 && a == b && b == c) {
                    out += indices[i]
                    out += indices[i + 1]
                    out += indices[i + 2]
                }
            }

            // A completed/partial line cannot contain more than three of either.
            for (value in 1..2) {
                val positions = indices.filter { grid.optInt(it) == value }
                if (positions.size > size / 2) out += positions
            }
        }

        for (r in 0 until size) line((0 until size).map { c -> r * size + c })
        for (c in 0 until size) line((0 until size).map { r -> r * size + c })

        // Completed rows and columns must be unique.
        val rows = mutableMapOf<String, MutableList<Int>>()
        for (r in 0 until size) {
            val indices = (0 until size).map { c -> r * size + c }
            if (indices.all { grid.optInt(it) != 0 }) {
                val key = indices.joinToString("") { grid.optInt(it).toString() }
                rows.getOrPut(key) { mutableListOf() }.addAll(indices)
            }
        }
        rows.values.filter { it.size > size }.forEach { out += it }

        val cols = mutableMapOf<String, MutableList<Int>>()
        for (c in 0 until size) {
            val indices = (0 until size).map { r -> r * size + c }
            if (indices.all { grid.optInt(it) != 0 }) {
                val key = indices.joinToString("") { grid.optInt(it).toString() }
                cols.getOrPut(key) { mutableListOf() }.addAll(indices)
            }
        }
        cols.values.filter { it.size > size }.forEach { out += it }

        return out
    }

    private fun transformedTakuzuSolution(
        rngMode: String,
        seed: String
    ): List<Int> {
        val base = listOf(
            0,0,1,0,1,1,
            0,0,1,1,0,1,
            1,1,0,0,1,0,
            0,1,0,0,1,1,
            1,0,1,1,0,0,
            1,1,0,1,0,0
        )

        var grid = base
        val transform = chanceInt(8, rngMode, "$seed|transform")
        repeat(transform % 4) { grid = rotateSquare(grid, 6) }
        if (transform >= 4) grid = transposeSquare(grid, 6)
        if (chanceInt(2, rngMode, "$seed|complement") == 1) {
            grid = grid.map { 1 - it }
        }
        return grid
    }

    private fun countTakuzuSolutions(
        puzzle: List<Int>,
        limit: Int
    ): Int {
        val cells = puzzle.toIntArray()
        var found = 0

        fun lineValid(values: IntArray): Boolean {
            for (i in 0 until values.size - 2) {
                if (values[i] != -1 &&
                    values[i] == values[i + 1] &&
                    values[i + 1] == values[i + 2]
                ) return false
            }
            val zeros = values.count { it == 0 }
            val ones = values.count { it == 1 }
            if (zeros > 3 || ones > 3) return false
            if (values.none { it == -1 } && (zeros != 3 || ones != 3)) return false
            return true
        }

        fun valid(): Boolean {
            val completedRows = mutableSetOf<String>()
            for (r in 0 until 6) {
                val row = IntArray(6) { c -> cells[r * 6 + c] }
                if (!lineValid(row)) return false
                if (row.none { it == -1 }) {
                    val key = row.joinToString("")
                    if (!completedRows.add(key)) return false
                }
            }

            val completedCols = mutableSetOf<String>()
            for (c in 0 until 6) {
                val col = IntArray(6) { r -> cells[r * 6 + c] }
                if (!lineValid(col)) return false
                if (col.none { it == -1 }) {
                    val key = col.joinToString("")
                    if (!completedCols.add(key)) return false
                }
            }
            return true
        }

        fun search() {
            if (found >= limit || !valid()) return
            val index = cells.indexOfFirst { it == -1 }
            if (index == -1) {
                found += 1
                return
            }
            for (value in 0..1) {
                cells[index] = value
                search()
                cells[index] = -1
                if (found >= limit) return
            }
        }

        search()
        return found
    }

    // -----------------------------------------------------------------
    // Shared deterministic-random helpers
    // -----------------------------------------------------------------

    private fun modularOrder(
        size: Int,
        rngMode: String,
        seed: String
    ): List<Int> {
        val start = chanceInt(size, rngMode, "$seed|start")
        val validSteps = (1 until size).filter { gcd(it, size) == 1 }
        val step = validSteps[
            chanceInt(validSteps.size, rngMode, "$seed|step")
        ]
        return List(size) { k -> (start + k * step) % size }
    }

    private fun <T> shuffled(
        source: List<T>,
        rngMode: String,
        seed: String
    ): List<T> {
        val out = source.toMutableList()
        for (i in out.lastIndex downTo 1) {
            val j = chanceInt(i + 1, rngMode, "$seed|$i")
            val tmp = out[i]
            out[i] = out[j]
            out[j] = tmp
        }
        return out
    }

    private fun rotateSquare(source: List<Int>, size: Int): List<Int> =
        List(size * size) { index ->
            val r = index / size
            val c = index % size
            source[(size - 1 - c) * size + r]
        }

    private fun transposeSquare(source: List<Int>, size: Int): List<Int> =
        List(size * size) { index ->
            val r = index / size
            val c = index % size
            source[c * size + r]
        }

    private fun gcd(a: Int, b: Int): Int {
        var x = abs(a)
        var y = abs(b)
        while (y != 0) {
            val t = x % y
            x = y
            y = t
        }
        return x
    }

    private fun chanceInt(
        bound: Int,
        rngMode: String,
        seed: String
    ): Int {
        if (bound <= 1) return 0
        val mode = if (rngMode.equals("fixed_seed", ignoreCase = true)) {
            "fixed_seed"
        } else {
            "secure_random"
        }
        val out = As100DiceSimulationMethod.generate(
            mapOf(
                "expression" to "d$bound",
                "roll_count" to "1",
                "player_count" to "1",
                "history_output" to "false",
                "rng_mode" to mode,
                "seed" to if (mode == "fixed_seed") seed else "",
                "animation_mode" to "off"
            )
        )
        return ((out[DiceSimulationFields.TOTAL]?.toIntOrNull() ?: 1) - 1)
            .coerceIn(0, bound - 1)
    }
}
