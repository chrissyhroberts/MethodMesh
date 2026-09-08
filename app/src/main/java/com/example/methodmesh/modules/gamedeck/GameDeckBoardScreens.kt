package com.example.methodmesh.modules.gamedeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject

@Composable
internal fun ChessScreen(
    stateJson: String,
    rngMode: String,
    seed: String,
    soundEnabled: Boolean,
    cpu: Boolean,
    onCpuChanged: (Boolean) -> Unit,
    onStateChange: (String) -> Unit
) {
    val s = JSONObject(stateJson)
    val board = s.getJSONArray("board")
    val turn = s.optInt("turn", 1)
    val moves = s.optInt("moves", 0)
    val terminal = s.optString("winner").isNotBlank()
    val p1Check = GameDeckBoardEngine.chessInCheck(stateJson, 1)
    val p2Check = GameDeckBoardEngine.chessInCheck(stateJson, 2)
    val lastFrom = s.optInt("last_from", -1)
    val lastTo = s.optInt("last_to", -1)

    var selected by rememberSaveable { mutableStateOf(-1) }
    val legalTargets = if (selected >= 0) {
        GameDeckBoardEngine.chessLegalMovesFrom(stateJson, selected).toSet()
    } else {
        emptySet()
    }

    LaunchedEffect(stateJson, cpu) {
        if (cpu && turn == 2 && !terminal) {
            delay(480)
            val action = GameDeckBoardEngine.chessChooseMove(
                stateJson = stateJson,
                profile = "standard",
                rngMode = rngMode,
                seed = "$seed|interactive|chess|$moves"
            )
            if (action != null) {
                GameDeckSound.cpu(soundEnabled)
                selected = -1
                onStateChange(GameDeckBoardEngine.chessMove(stateJson, action))
            }
        }
    }

    val topDetail = when {
        terminal -> if (p2Check) "IN CHECK" else "$moves half-moves"
        turn == 2 && cpu -> if (p2Check) "CPU THINKING… • CHECK" else "CPU THINKING…"
        turn == 2 -> if (p2Check) "YOUR TURN • CHECK" else "YOUR TURN"
        else -> if (p2Check) "CHECK" else "$moves half-moves"
    }

    val bottomDetail = when {
        terminal -> if (p1Check) "IN CHECK" else "$moves half-moves"
        turn == 1 -> if (p1Check) "YOUR TURN • CHECK" else "YOUR TURN"
        else -> if (p1Check) "CHECK" else "$moves half-moves"
    }

    ExtraTabletop(
        turn = turn,
        topHuman = !cpu,
        topDetail = topDetail,
        bottomDetail = bottomDetail,
        bottomLabel = if (cpu) "YOU • WHITE" else "PLAYER 1 • WHITE",
        topControl = {
            CpuModeButton(
                cpu = cpu,
                enabled = moves == 0,
                onChange = onCpuChanged
            )
        }
    ) {
        SquareBoard(8) { cell, side ->
            val piece = board.optInt(cell)
            val ownPiece = when (turn) {
                1 -> piece > 0
                else -> piece < 0
            }
            val selectedCell = cell == selected
            val legal = cell in legalTargets
            val recent = cell == lastFrom || cell == lastTo
            val row = cell / 8
            val col = cell % 8
            val dark = (row + col) % 2 == 1

            Box(
                modifier = Modifier
                    .size(side)
                    .clickable(
                        enabled = !terminal && !(cpu && turn == 2)
                    ) {
                        when {
                            selected >= 0 && legal -> {
                                val next = GameDeckBoardEngine.chessMove(
                                    stateJson,
                                    "$selected:$cell"
                                )
                                if (next != stateJson) {
                                    GameDeckSound.move(soundEnabled)
                                    selected = -1
                                    onStateChange(next)
                                }
                            }

                            ownPiece -> selected = cell
                            else -> selected = -1
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(2.dp),
                    color = when {
                        selectedCell -> MaterialTheme.colorScheme.primaryContainer
                        recent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .72f)
                        dark -> Color(0xFF7E8A68)
                        else -> Color(0xFFE8E1C8)
                    }
                ) {}

                if (legal) {
                    Surface(
                        modifier = Modifier.size(side * .24f),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = .70f)
                    ) {}
                }

                if (piece != 0) {
                    Text(
                        GameDeckBoardEngine.chessPieceSymbol(piece),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black,
                        color = if (piece > 0) Color(0xFF2B2A27) else Color(0xFF111111)
                    )
                }
            }
        }
    }
}

@Composable
internal fun GoScreen(
    stateJson: String,
    rngMode: String,
    seed: String,
    soundEnabled: Boolean,
    cpu: Boolean,
    onCpuChanged: (Boolean) -> Unit,
    onStateChange: (String) -> Unit
) {
    val s = JSONObject(stateJson)
    val board = s.getJSONArray("board")
    val turn = s.optInt("turn", 1)
    val moves = s.optInt("moves", 0)
    val terminal = s.optString("winner").isNotBlank()
    val captures = s.getJSONArray("captures")
    val passes = s.optInt("passes", 0)
    val last = s.optInt("last", -1)
    val legal = if (terminal) emptySet() else GameDeckBoardEngine.goLegalMoves(stateJson).toSet()

    LaunchedEffect(stateJson, cpu) {
        if (cpu && turn == 2 && !terminal) {
            delay(520)
            val action = GameDeckBoardEngine.goChooseAction(
                stateJson = stateJson,
                profile = "standard",
                rngMode = rngMode,
                seed = "$seed|interactive|go|$moves"
            )
            GameDeckSound.cpu(soundEnabled)
            onStateChange(
                if (action == "pass") {
                    GameDeckBoardEngine.goPass(stateJson)
                } else {
                    GameDeckBoardEngine.goMove(stateJson, action.toInt())
                }
            )
        }
    }

    val topDetail = when {
        terminal -> "White • ${captures.optInt(1)} captures"
        turn == 2 && cpu -> "CPU THINKING… • ${captures.optInt(1)} captures"
        turn == 2 -> "YOUR TURN • ${captures.optInt(1)} captures"
        else -> "White • ${captures.optInt(1)} captures"
    }
    val bottomDetail = when {
        terminal -> "Black • ${captures.optInt(0)} captures"
        turn == 1 -> "YOUR TURN • ${captures.optInt(0)} captures"
        else -> "Black • ${captures.optInt(0)} captures"
    }

    val topControl: @Composable () -> Unit = {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CpuModeButton(
                cpu = cpu,
                enabled = moves == 0,
                onChange = onCpuChanged
            )
            if (!cpu) {
                OutlinedButton(
                    onClick = {
                        if (turn == 2 && !terminal) {
                            GameDeckSound.move(soundEnabled)
                            onStateChange(GameDeckBoardEngine.goPass(stateJson))
                        }
                    },
                    enabled = turn == 2 && !terminal
                ) {
                    Text("PASS")
                }
            }
        }
    }

    val bottomControl: @Composable () -> Unit = {
        Button(
            onClick = {
                if (turn == 1 && !terminal) {
                    GameDeckSound.move(soundEnabled)
                    onStateChange(GameDeckBoardEngine.goPass(stateJson))
                }
            },
            enabled = turn == 1 && !terminal
        ) {
            Text("PASS")
        }
    }

    ExtraTabletop(
        turn = turn,
        topHuman = !cpu,
        topDetail = topDetail,
        bottomDetail = bottomDetail,
        bottomLabel = if (cpu) "YOU • BLACK" else "PLAYER 1 • BLACK",
        topControl = topControl,
        bottomControl = bottomControl
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val side = minOf(maxWidth, maxHeight)
            val cell = side / 9

            Box(
                modifier = Modifier.size(side),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFD9B66F),
                    shape = RoundedCornerShape(8.dp)
                ) {}

                Canvas(Modifier.fillMaxSize()) {
                    val step = size.width / 9f

                    for (i in 0 until 9) {
                        val x = (i + .5f) * step
                        val y = (i + .5f) * step
                        drawLine(
                            color = Color(0xFF4B3823),
                            start = Offset(x, .5f * step),
                            end = Offset(x, 8.5f * step),
                            strokeWidth = 2f
                        )
                        drawLine(
                            color = Color(0xFF4B3823),
                            start = Offset(.5f * step, y),
                            end = Offset(8.5f * step, y),
                            strokeWidth = 2f
                        )
                    }

                    listOf(
                        2 to 2, 2 to 6,
                        4 to 4,
                        6 to 2, 6 to 6
                    ).forEach { (r, c) ->
                        drawCircle(
                            color = Color(0xFF4B3823),
                            radius = step * .075f,
                            center = Offset((c + .5f) * step, (r + .5f) * step)
                        )
                    }
                }

                Column {
                    for (r in 0 until 9) {
                        Row {
                            for (c in 0 until 9) {
                                val index = r * 9 + c
                                val stone = board.optInt(index)
                                Box(
                                    modifier = Modifier
                                        .size(cell)
                                        .clickable(
                                            enabled =
                                                index in legal &&
                                                !terminal &&
                                                !(cpu && turn == 2)
                                        ) {
                                            val next = GameDeckBoardEngine.goMove(
                                                stateJson,
                                                index
                                            )
                                            if (next != stateJson) {
                                                GameDeckSound.move(soundEnabled)
                                                onStateChange(next)
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (stone != 0) {
                                        Surface(
                                            modifier = Modifier.size(cell * .78f),
                                            shape = CircleShape,
                                            color = if (stone == 1) Color(0xFF181818) else Color(0xFFF2F0E9),
                                            tonalElevation = 4.dp,
                                            shadowElevation = 2.dp
                                        ) {
                                            if (index == last) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Surface(
                                                        modifier = Modifier.size(cell * .16f),
                                                        shape = CircleShape,
                                                        color = if (stone == 1) Color.White else Color.Black
                                                    ) {}
                                                }
                                            }
                                        }
                                    } else if (index in legal && !(cpu && turn == 2)) {
                                        Surface(
                                            modifier = Modifier.size(cell * .12f),
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = .35f)
                                        ) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (passes == 1 && !terminal) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        "ONE PASS • another pass ends and scores the game",
                        modifier = Modifier,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
