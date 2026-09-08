package com.example.methodmesh.modules.gamedeck

import com.example.methodmesh.modules.chance.As100DiceSimulationMethod
import com.example.methodmesh.modules.chance.DiceSimulationFields
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/**
 * Compact perfect-information board-game engines.
 *
 * CPU play is intentionally lightweight. Rules/state transitions remain the same
 * for CPU and human play; the CPU merely chooses from the legal action set.
 */
internal object GameDeckBoardEngine {

    // =================================================================
    // Chess
    // =================================================================

    private const val PAWN = 1
    private const val KNIGHT = 2
    private const val BISHOP = 3
    private const val ROOK = 4
    private const val QUEEN = 5
    private const val KING = 6

    private data class ChessMove(
        val from: Int,
        val to: Int,
        val enPassantCapture: Int = -1,
        val rookFrom: Int = -1,
        val rookTo: Int = -1,
        val promotion: Int = QUEEN
    ) {
        fun action(): String = "$from:$to"
    }

    fun chessInitial(): String {
        val board = MutableList(64) { 0 }

        // Black / Player 2 at the far side.
        listOf(-ROOK,-KNIGHT,-BISHOP,-QUEEN,-KING,-BISHOP,-KNIGHT,-ROOK)
            .forEachIndexed { c, piece -> board[c] = piece }
        for (c in 0..7) board[8 + c] = -PAWN

        // White / Player 1 at the near side.
        for (c in 0..7) board[48 + c] = PAWN
        listOf(ROOK,KNIGHT,BISHOP,QUEEN,KING,BISHOP,KNIGHT,ROOK)
            .forEachIndexed { c, piece -> board[56 + c] = piece }

        return JSONObject()
            .put("game", GameDeckExtraEngine.CHESS)
            .put("board", JSONArray(board))
            .put("turn", 1)
            .put("winner", "")
            .put("moves", 0)
            .put("castling", "KQkq")
            .put("en_passant", -1)
            .put("halfmove", 0)
            .put("last_from", -1)
            .put("last_to", -1)
            .put("move_log", JSONArray())
            .toString()
    }

    fun chessLegalMoves(stateJson: String): List<String> {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return emptyList() }
        return chessLegalMoveObjects(s).map { it.action() }
    }

    fun chessLegalMovesFrom(stateJson: String, from: Int): List<Int> =
        chessLegalMoves(stateJson)
            .mapNotNull { action ->
                val parts = action.split(":")
                if (parts.size != 2 || parts[0].toIntOrNull() != from) null
                else parts[1].toIntOrNull()
            }

    fun chessInCheck(stateJson: String, seat: Int): Boolean {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return false }
        val board = intBoard(s, 64)
        return kingInCheck(board, seat)
    }

    fun chessMove(stateJson: String, action: String): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank()) return stateJson

        val legal = chessLegalMoveObjects(s)
        val move = legal.firstOrNull { it.action() == action } ?: return stateJson
        val currentSeat = s.optInt("turn", 1).coerceIn(1, 2)
        val boardBefore = intBoard(s, 64)
        val movingPiece = boardBefore[move.from]
        val capturedPiece = if (move.enPassantCapture >= 0) {
            boardBefore[move.enPassantCapture]
        } else {
            boardBefore[move.to]
        }

        val applied = applyChessMove(
            board = boardBefore,
            move = move,
            castling = s.optString("castling", "KQkq"),
            enPassant = s.optInt("en_passant", -1)
        )

        val nextSeat = 3 - currentSeat
        val halfmove = if (abs(movingPiece) == PAWN || capturedPiece != 0) {
            0
        } else {
            s.optInt("halfmove", 0) + 1
        }

        val moveNumber = s.optInt("moves", 0) + 1
        val moveLog = JSONArray(s.optJSONArray("move_log")?.toString() ?: "[]")
        moveLog.put(
            JSONObject()
                .put("move", moveNumber)
                .put("seat", currentSeat)
                .put("from_index", move.from)
                .put("to_index", move.to)
                .put("from", chessSquare(move.from))
                .put("to", chessSquare(move.to))
                .put("piece", chessPieceSymbol(movingPiece))
                .put("captured_piece", if (capturedPiece == 0) JSONObject.NULL else chessPieceSymbol(capturedPiece))
                .put("castle", move.rookFrom >= 0)
                .put("en_passant", move.enPassantCapture >= 0)
                .put("promotion", if (move.promotion != 0) chessPieceSymbol(if (currentSeat == 1) move.promotion else -move.promotion) else JSONObject.NULL)
        )

        val out = JSONObject()
            .put("game", GameDeckExtraEngine.CHESS)
            .put("board", JSONArray(applied.board))
            .put("turn", nextSeat)
            .put("winner", "")
            .put("moves", moveNumber)
            .put("move_log", moveLog)
            .put("castling", applied.castling)
            .put("en_passant", applied.enPassant)
            .put("halfmove", halfmove)
            .put("last_from", move.from)
            .put("last_to", move.to)

        if (halfmove >= 100) {
            out.put("winner", "draw")
            return out.toString()
        }

        val replies = chessLegalMoveObjects(out)
        if (replies.isEmpty()) {
            out.put(
                "winner",
                if (kingInCheck(applied.board, nextSeat)) currentSeat.toString() else "draw"
            )
        }

        return out.toString()
    }

    fun chessChooseMove(
        stateJson: String,
        profile: String,
        rngMode: String,
        seed: String
    ): String? {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank()) return null
        val legal = chessLegalMoveObjects(s)
        if (legal.isEmpty()) return null

        val normalized = profile.lowercase()
        if (normalized == "casual") {
            return legal[chanceInt(legal.size, rngMode, "$seed|chess|casual")].action()
        }

        data class Scored(val move: ChessMove, val score: Int)
        val immediate = legal.map { move ->
            Scored(move, chessImmediateScore(s, move))
        }

        if (normalized != "sharp") {
            val best = immediate.maxOf { it.score }
            val top = immediate.filter { it.score >= best - 15 }
            return top[chanceInt(top.size, rngMode, "$seed|chess|standard")].move.action()
        }

        var bestMove = legal.first()
        var bestScore = Int.MIN_VALUE

        for (move in legal) {
            val after = chessStateAfterForSearch(s, move)
            val replies = chessLegalMoveObjects(after)

            val score = if (replies.isEmpty()) {
                if (kingInCheck(intBoard(after, 64), 1)) 100_000 else 0
            } else {
                // CPU is seat 2 (black): assume Player 1 chooses the reply that
                // is worst for the CPU.
                replies.minOf { reply ->
                    val replyState = chessStateAfterForSearch(after, reply)
                    chessPositionScore(intBoard(replyState, 64), cpuSeat = 2)
                }
            }

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
        }

        return bestMove.action()
    }

    private data class AppliedChess(
        val board: List<Int>,
        val castling: String,
        val enPassant: Int
    )

    private fun chessLegalMoveObjects(s: JSONObject): List<ChessMove> {
        if (s.optString("winner").isNotBlank()) return emptyList()
        val board = intBoard(s, 64)
        val seat = s.optInt("turn", 1).coerceIn(1, 2)
        val castling = s.optString("castling", "KQkq")
        val enPassant = s.optInt("en_passant", -1)

        return pseudoChessMoves(board, seat, castling, enPassant)
            .filter { move ->
                val applied = applyChessMove(board, move, castling, enPassant)
                !kingInCheck(applied.board, seat)
            }
    }

    private fun pseudoChessMoves(
        board: List<Int>,
        seat: Int,
        castling: String,
        enPassant: Int
    ): List<ChessMove> {
        val out = mutableListOf<ChessMove>()

        fun belongs(piece: Int): Boolean =
            if (seat == 1) piece > 0 else piece < 0

        fun opponent(piece: Int): Boolean =
            (if (seat == 1) piece < 0 else piece > 0) && abs(piece) != KING

        for (from in 0 until 64) {
            val piece = board[from]
            if (!belongs(piece)) continue

            val type = abs(piece)
            val r = from / 8
            val c = from % 8

            when (type) {
                PAWN -> {
                    val dr = if (seat == 1) -1 else 1
                    val startRow = if (seat == 1) 6 else 1
                    val promotionRow = if (seat == 1) 0 else 7

                    val oneR = r + dr
                    if (oneR in 0..7) {
                        val one = oneR * 8 + c
                        if (board[one] == 0) {
                            out += ChessMove(
                                from,
                                one,
                                promotion = if (oneR == promotionRow) QUEEN else 0
                            )
                            if (r == startRow) {
                                val twoR = r + 2 * dr
                                val two = twoR * 8 + c
                                if (board[two] == 0) out += ChessMove(from, two)
                            }
                        }

                        for (dc in listOf(-1, 1)) {
                            val nc = c + dc
                            if (nc !in 0..7) continue
                            val to = oneR * 8 + nc
                            if (opponent(board[to])) {
                                out += ChessMove(
                                    from,
                                    to,
                                    promotion = if (oneR == promotionRow) QUEEN else 0
                                )
                            } else if (to == enPassant) {
                                val captured = r * 8 + nc
                                if (captured in 0..63 &&
                                    abs(board[captured]) == PAWN &&
                                    opponent(board[captured])
                                ) {
                                    out += ChessMove(from, to, enPassantCapture = captured)
                                }
                            }
                        }
                    }
                }

                KNIGHT -> {
                    val jumps = listOf(
                        -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                        1 to -2, 1 to 2, 2 to -1, 2 to 1
                    )
                    for ((dr, dc) in jumps) {
                        val nr = r + dr
                        val nc = c + dc
                        if (nr !in 0..7 || nc !in 0..7) continue
                        val to = nr * 8 + nc
                        if (board[to] == 0 || (!belongs(board[to]) && abs(board[to]) != KING)) {
                            out += ChessMove(from, to)
                        }
                    }
                }

                BISHOP -> addSliding(
                    out, board, seat, from,
                    listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
                )

                ROOK -> addSliding(
                    out, board, seat, from,
                    listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
                )

                QUEEN -> addSliding(
                    out, board, seat, from,
                    listOf(
                        -1 to -1, -1 to 1, 1 to -1, 1 to 1,
                        -1 to 0, 1 to 0, 0 to -1, 0 to 1
                    )
                )

                KING -> {
                    for (dr in -1..1) for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val nr = r + dr
                        val nc = c + dc
                        if (nr !in 0..7 || nc !in 0..7) continue
                        val to = nr * 8 + nc
                        if (board[to] == 0 || (!belongs(board[to]) && abs(board[to]) != KING)) {
                            out += ChessMove(from, to)
                        }
                    }

                    val enemy = 3 - seat
                    if (!kingInCheck(board, seat)) {
                        if (seat == 1 && from == 60) {
                            if ('K' in castling &&
                                board[61] == 0 && board[62] == 0 &&
                                board[63] == ROOK &&
                                !isSquareAttacked(board, 61, enemy) &&
                                !isSquareAttacked(board, 62, enemy)
                            ) {
                                out += ChessMove(60, 62, rookFrom = 63, rookTo = 61)
                            }
                            if ('Q' in castling &&
                                board[59] == 0 && board[58] == 0 && board[57] == 0 &&
                                board[56] == ROOK &&
                                !isSquareAttacked(board, 59, enemy) &&
                                !isSquareAttacked(board, 58, enemy)
                            ) {
                                out += ChessMove(60, 58, rookFrom = 56, rookTo = 59)
                            }
                        }

                        if (seat == 2 && from == 4) {
                            if ('k' in castling &&
                                board[5] == 0 && board[6] == 0 &&
                                board[7] == -ROOK &&
                                !isSquareAttacked(board, 5, enemy) &&
                                !isSquareAttacked(board, 6, enemy)
                            ) {
                                out += ChessMove(4, 6, rookFrom = 7, rookTo = 5)
                            }
                            if ('q' in castling &&
                                board[3] == 0 && board[2] == 0 && board[1] == 0 &&
                                board[0] == -ROOK &&
                                !isSquareAttacked(board, 3, enemy) &&
                                !isSquareAttacked(board, 2, enemy)
                            ) {
                                out += ChessMove(4, 2, rookFrom = 0, rookTo = 3)
                            }
                        }
                    }
                }
            }
        }

        return out
    }

    private fun addSliding(
        out: MutableList<ChessMove>,
        board: List<Int>,
        seat: Int,
        from: Int,
        directions: List<Pair<Int, Int>>
    ) {
        val r = from / 8
        val c = from % 8

        fun belongs(piece: Int): Boolean =
            if (seat == 1) piece > 0 else piece < 0

        for ((dr, dc) in directions) {
            var nr = r + dr
            var nc = c + dc
            while (nr in 0..7 && nc in 0..7) {
                val to = nr * 8 + nc
                if (belongs(board[to])) break
                if (board[to] != 0 && abs(board[to]) == KING) break
                out += ChessMove(from, to)
                if (board[to] != 0) break
                nr += dr
                nc += dc
            }
        }
    }

    private fun applyChessMove(
        board: List<Int>,
        move: ChessMove,
        castling: String,
        enPassant: Int
    ): AppliedChess {
        val out = board.toMutableList()
        val piece = out[move.from]
        val captured = out[move.to]
        var rights = castling

        out[move.from] = 0
        out[move.to] = if (move.promotion != 0 && abs(piece) == PAWN) {
            if (piece > 0) move.promotion else -move.promotion
        } else {
            piece
        }

        if (move.enPassantCapture >= 0) out[move.enPassantCapture] = 0

        if (move.rookFrom >= 0 && move.rookTo >= 0) {
            out[move.rookTo] = out[move.rookFrom]
            out[move.rookFrom] = 0
        }

        // Moving king/rook or capturing a rook removes the corresponding right.
        if (abs(piece) == KING) {
            rights = if (piece > 0) {
                rights.replace("K", "").replace("Q", "")
            } else {
                rights.replace("k", "").replace("q", "")
            }
        }
        if (move.from == 63 || move.to == 63) rights = rights.replace("K", "")
        if (move.from == 56 || move.to == 56) rights = rights.replace("Q", "")
        if (move.from == 7 || move.to == 7) rights = rights.replace("k", "")
        if (move.from == 0 || move.to == 0) rights = rights.replace("q", "")

        val nextEp = if (abs(piece) == PAWN && abs(move.to - move.from) == 16) {
            (move.to + move.from) / 2
        } else {
            -1
        }

        // Keep parameter to make this transition contract explicit; the current
        // en-passant target is consumed by move generation rather than mutation.
        @Suppress("UNUSED_VARIABLE")
        val consumedEnPassant = enPassant
        @Suppress("UNUSED_VARIABLE")
        val capturedOnDestination = captured

        return AppliedChess(out, rights, nextEp)
    }

    private fun kingInCheck(board: List<Int>, seat: Int): Boolean {
        val kingPiece = if (seat == 1) KING else -KING
        val square = board.indexOf(kingPiece)
        if (square < 0) return true
        return isSquareAttacked(board, square, 3 - seat)
    }

    private fun isSquareAttacked(
        board: List<Int>,
        square: Int,
        bySeat: Int
    ): Boolean {
        val r = square / 8
        val c = square % 8
        val sign = if (bySeat == 1) 1 else -1

        // Pawns.
        val pawnSourceRow = r + if (bySeat == 1) 1 else -1
        if (pawnSourceRow in 0..7) {
            for (dc in listOf(-1, 1)) {
                val nc = c + dc
                if (nc in 0..7 &&
                    board[pawnSourceRow * 8 + nc] == sign * PAWN
                ) return true
            }
        }

        // Knights.
        val jumps = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((dr, dc) in jumps) {
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 &&
                board[nr * 8 + nc] == sign * KNIGHT
            ) return true
        }

        fun ray(directions: List<Pair<Int, Int>>, types: Set<Int>): Boolean {
            for ((dr, dc) in directions) {
                var nr = r + dr
                var nc = c + dc
                while (nr in 0..7 && nc in 0..7) {
                    val piece = board[nr * 8 + nc]
                    if (piece != 0) {
                        if (piece * sign > 0 && abs(piece) in types) return true
                        break
                    }
                    nr += dr
                    nc += dc
                }
            }
            return false
        }

        if (ray(
                listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1),
                setOf(BISHOP, QUEEN)
            )
        ) return true

        if (ray(
                listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1),
                setOf(ROOK, QUEEN)
            )
        ) return true

        // King.
        for (dr in -1..1) for (dc in -1..1) {
            if (dr == 0 && dc == 0) continue
            val nr = r + dr
            val nc = c + dc
            if (nr in 0..7 && nc in 0..7 &&
                board[nr * 8 + nc] == sign * KING
            ) return true
        }

        return false
    }

    private fun chessImmediateScore(
        s: JSONObject,
        move: ChessMove
    ): Int {
        val board = intBoard(s, 64)
        val before = chessPositionScore(board, cpuSeat = 2)
        val after = applyChessMove(
            board,
            move,
            s.optString("castling", "KQkq"),
            s.optInt("en_passant", -1)
        ).board
        var score = chessPositionScore(after, cpuSeat = 2) - before

        val toR = move.to / 8
        val toC = move.to % 8
        val centreDistance = abs(toR - 3) + abs(toC - 3)
        score += (7 - centreDistance) * 2

        if (kingInCheck(after, 1)) score += 30
        return score
    }

    private fun chessPositionScore(
        board: List<Int>,
        cpuSeat: Int
    ): Int {
        val values = intArrayOf(0, 100, 320, 330, 500, 900, 20_000)
        var score = 0
        for (i in board.indices) {
            val piece = board[i]
            if (piece == 0) continue
            val value = values[abs(piece)]
            val belongsCpu = if (cpuSeat == 1) piece > 0 else piece < 0
            score += if (belongsCpu) value else -value
        }
        return score
    }

    private fun chessStateAfterForSearch(
        s: JSONObject,
        move: ChessMove
    ): JSONObject {
        val board = intBoard(s, 64)
        val seat = s.optInt("turn", 1).coerceIn(1, 2)
        val applied = applyChessMove(
            board,
            move,
            s.optString("castling", "KQkq"),
            s.optInt("en_passant", -1)
        )
        return JSONObject()
            .put("game", GameDeckExtraEngine.CHESS)
            .put("board", JSONArray(applied.board))
            .put("turn", 3 - seat)
            .put("winner", "")
            .put("moves", s.optInt("moves", 0) + 1)
            .put("castling", applied.castling)
            .put("en_passant", applied.enPassant)
            .put("halfmove", s.optInt("halfmove", 0) + 1)
    }

    private fun chessSquare(index: Int): String {
        if (index !in 0..63) return ""
        val file = ('a'.code + (index % 8)).toChar()
        val rank = 8 - (index / 8)
        return "$file$rank"
    }

    fun chessPieceSymbol(piece: Int): String = when (piece) {
        PAWN -> "♙"
        KNIGHT -> "♘"
        BISHOP -> "♗"
        ROOK -> "♖"
        QUEEN -> "♕"
        KING -> "♔"
        -PAWN -> "♟"
        -KNIGHT -> "♞"
        -BISHOP -> "♝"
        -ROOK -> "♜"
        -QUEEN -> "♛"
        -KING -> "♚"
        else -> ""
    }

    // =================================================================
    // Go 9x9
    // =================================================================

    fun goInitial(): String = JSONObject()
        .put("game", GameDeckExtraEngine.GO_9X9)
        .put("size", 9)
        .put("board", JSONArray(List(81) { 0 }))
        .put("turn", 1)
        .put("winner", "")
        .put("moves", 0)
        .put("ko", -1)
        .put("passes", 0)
        .put("captures", JSONArray(listOf(0, 0)))
        .put("komi", 6.5)
        .put("last", -1)
        .put("black_score", JSONObject.NULL)
        .put("white_score", JSONObject.NULL)
        .put("move_log", JSONArray())
        .toString()

    fun goLegalMoves(stateJson: String): List<Int> {
        val s = runCatching { JSONObject(stateJson) }.getOrElse { return emptyList() }
        if (s.optString("winner").isNotBlank()) return emptyList()
        val board = intBoard(s, 81)
        val seat = s.optInt("turn", 1).coerceIn(1, 2)
        val ko = s.optInt("ko", -1)

        return board.indices.filter { index ->
            board[index] == 0 && goApplied(board, seat, index, ko) != null
        }
    }

    fun goMove(stateJson: String, index: Int): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank()) return stateJson

        val board = intBoard(s, 81)
        val seat = s.optInt("turn", 1).coerceIn(1, 2)
        val applied = goApplied(
            board,
            seat,
            index,
            s.optInt("ko", -1)
        ) ?: return stateJson

        val captures = intArrayOf(
            s.optJSONArray("captures")?.optInt(0) ?: 0,
            s.optJSONArray("captures")?.optInt(1) ?: 0
        )
        captures[seat - 1] += applied.captured
        val moveNumber = s.optInt("moves", 0) + 1
        val moveLog = JSONArray(s.optJSONArray("move_log")?.toString() ?: "[]")
        moveLog.put(
            JSONObject()
                .put("move", moveNumber)
                .put("seat", seat)
                .put("point_index", index)
                .put("row", index / 9 + 1)
                .put("column", index % 9 + 1)
                .put("captured", applied.captured)
                .put("pass", false)
        )

        return JSONObject(s.toString())
            .put("board", JSONArray(applied.board))
            .put("turn", 3 - seat)
            .put("moves", moveNumber)
            .put("move_log", moveLog)
            .put("ko", applied.ko)
            .put("passes", 0)
            .put("captures", JSONArray(captures.toList()))
            .put("last", index)
            .toString()
    }

    fun goPass(stateJson: String): String {
        val s = JSONObject(stateJson)
        if (s.optString("winner").isNotBlank()) return stateJson
        val passes = s.optInt("passes", 0) + 1
        val seat = s.optInt("turn", 1).coerceIn(1, 2)
        val moveNumber = s.optInt("moves", 0) + 1
        val moveLog = JSONArray(s.optJSONArray("move_log")?.toString() ?: "[]")
        moveLog.put(
            JSONObject()
                .put("move", moveNumber)
                .put("seat", seat)
                .put("pass", true)
        )

        val out = JSONObject(s.toString())
            .put("turn", 3 - seat)
            .put("moves", moveNumber)
            .put("move_log", moveLog)
            .put("ko", -1)
            .put("passes", passes)
            .put("last", -1)

        if (passes >= 2) {
            val score = goAreaScore(intBoard(out, 81), out.optDouble("komi", 6.5))
            out.put("black_score", score.first)
                .put("white_score", score.second)
                .put(
                    "winner",
                    when {
                        score.first > score.second -> "1"
                        score.second > score.first -> "2"
                        else -> "draw"
                    }
                )
        }

        return out.toString()
    }

    fun goChooseAction(
        stateJson: String,
        profile: String,
        rngMode: String,
        seed: String
    ): String {
        val s = JSONObject(stateJson)
        val legal = goLegalMoves(stateJson)
        if (legal.isEmpty()) return "pass"

        val normalized = profile.lowercase()
        if (normalized == "casual") {
            return legal[chanceInt(legal.size, rngMode, "$seed|go|casual")].toString()
        }

        val board = intBoard(s, 81)
        val seat = s.optInt("turn", 2).coerceIn(1, 2)
        val ko = s.optInt("ko", -1)

        data class Scored(val index: Int, val score: Int)
        val scored = legal.map { index ->
            val applied = goApplied(board, seat, index, ko)!!
            val group = groupAndLiberties(applied.board, index)
            val r = index / 9
            val c = index % 9
            val edgeDistance = minOf(r, c, 8 - r, 8 - c)
            var score =
                applied.captured * 120 +
                group.liberties.size * 8 +
                edgeDistance * 3

            if (normalized == "sharp") {
                val opponent = 3 - seat
                val opponentCaptures = applied.board.indices
                    .asSequence()
                    .filter { applied.board[it] == 0 }
                    .mapNotNull { goApplied(applied.board, opponent, it, applied.ko) }
                    .maxOfOrNull { it.captured } ?: 0
                score -= opponentCaptures * 80
            }
            Scored(index, score)
        }

        val best = scored.maxOf { it.score }

        // In late, settled positions, allow a basic CPU to pass instead of filling
        // every last neutral point forever.
        if (s.optInt("moves", 0) > 65 && best <= 12) return "pass"

        val top = scored.filter { it.score >= best - if (normalized == "sharp") 0 else 6 }
        return top[chanceInt(top.size, rngMode, "$seed|go|$normalized")].index.toString()
    }

    private data class GoApplied(
        val board: List<Int>,
        val captured: Int,
        val ko: Int
    )

    private data class GroupInfo(
        val stones: Set<Int>,
        val liberties: Set<Int>
    )

    private fun goApplied(
        board: List<Int>,
        seat: Int,
        index: Int,
        ko: Int
    ): GoApplied? {
        if (index !in board.indices || board[index] != 0 || index == ko) return null

        val out = board.toMutableList()
        out[index] = seat
        val opponent = 3 - seat
        val capturedPoints = mutableListOf<Int>()

        for (neighbor in goNeighbors(index)) {
            if (out[neighbor] != opponent) continue
            val group = groupAndLiberties(out, neighbor)
            if (group.liberties.isEmpty()) {
                group.stones.forEach {
                    out[it] = 0
                    capturedPoints += it
                }
            }
        }

        val own = groupAndLiberties(out, index)
        if (own.liberties.isEmpty()) return null

        val nextKo = if (
            capturedPoints.size == 1 &&
            own.stones.size == 1 &&
            own.liberties.size == 1
        ) {
            capturedPoints.first()
        } else {
            -1
        }

        return GoApplied(out, capturedPoints.size, nextKo)
    }

    private fun groupAndLiberties(
        board: List<Int>,
        start: Int
    ): GroupInfo {
        val color = board[start]
        if (color == 0) return GroupInfo(emptySet(), emptySet())

        val stones = mutableSetOf<Int>()
        val liberties = mutableSetOf<Int>()
        val queue = ArrayDeque<Int>()
        queue.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!stones.add(current)) continue
            for (neighbor in goNeighbors(current)) {
                when (board[neighbor]) {
                    0 -> liberties += neighbor
                    color -> if (neighbor !in stones) queue.add(neighbor)
                }
            }
        }
        return GroupInfo(stones, liberties)
    }

    private fun goNeighbors(index: Int): List<Int> {
        val r = index / 9
        val c = index % 9
        return buildList {
            if (r > 0) add(index - 9)
            if (r < 8) add(index + 9)
            if (c > 0) add(index - 1)
            if (c < 8) add(index + 1)
        }
    }

    private fun goAreaScore(
        board: List<Int>,
        komi: Double
    ): Pair<Double, Double> {
        var black = board.count { it == 1 }.toDouble()
        var white = board.count { it == 2 }.toDouble() + komi
        val seen = mutableSetOf<Int>()

        for (start in board.indices) {
            if (board[start] != 0 || start in seen) continue

            val region = mutableSetOf<Int>()
            val borders = mutableSetOf<Int>()
            val queue = ArrayDeque<Int>()
            queue.add(start)

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                if (!region.add(current)) continue
                seen += current

                for (neighbor in goNeighbors(current)) {
                    when (board[neighbor]) {
                        0 -> if (neighbor !in region) queue.add(neighbor)
                        1 -> borders += 1
                        2 -> borders += 2
                    }
                }
            }

            if (borders == setOf(1)) black += region.size
            if (borders == setOf(2)) white += region.size
        }

        return black to white
    }

    // =================================================================
    // Shared helpers
    // =================================================================

    private fun intBoard(s: JSONObject, expected: Int): List<Int> {
        val a = s.optJSONArray("board") ?: JSONArray()
        return List(expected) { a.optInt(it) }
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
