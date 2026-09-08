package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun SnakeArcade(
    stateJson: String,
    paused: Boolean,
    bestScore: Int,
    onStep: () -> Unit,
    onTurn: (Int) -> Unit,
    onSpeedChange: (Int) -> Unit,
    onStart: () -> Unit
) {
    val state = JSONObject(stateJson)
    val started = state.optBoolean("started", false)
    val finished = state.optBoolean("finished", false)
    val score = state.optInt("score", 0)
    val length = state.optJSONArray("body")?.length() ?: 0
    val baseCps = ArcadeSnakeSpeed.normalizeCps(
        state.optInt("speed_cps", ArcadeSnakeSpeed.DEFAULT_CPS)
    )
    val effectiveCps = ArcadeSnakeSpeed.effectiveCps(baseCps, score)

    val latestOnStep = rememberUpdatedState(onStep)
    val latestSpeed = rememberUpdatedState(baseCps)
    val latestScore = rememberUpdatedState(score)

    LaunchedEffect(started, finished, paused) {
        while (started && !finished && !paused) {
            delay(ArcadeSnakeSpeed.delayMs(latestSpeed.value, latestScore.value))
            latestOnStep.value()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 620.dp
        val headerReserve = if (compact) 58.dp else 68.dp
        val controlsReserve = if (compact) 94.dp else 110.dp
        val gapReserve = 14.dp
        val availableHeight = (maxHeight - headerReserve - controlsReserve - gapReserve)
            .coerceAtLeast(250.dp)
        val boardRatio = ArcadeEngine.SNAKE_COLS.toFloat() / ArcadeEngine.SNAKE_ROWS.toFloat()
        val boardWidth = minOf(maxWidth, availableHeight * boardRatio)
        val boardHeight = boardWidth / boardRatio

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(headerReserve),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SNAKE",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "18 × 30 • swipe to turn",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    SnakeStat("SCORE", score.toString())
                    SnakeStat("BEST", bestScore.toString())
                    SnakeStat("LEN", length.toString())
                }
            }

            SnakeBoard(
                stateJson = stateJson,
                boardWidth = boardWidth,
                boardHeight = boardHeight,
                inputEnabled = !paused && !finished,
                onTurn = onTurn
            )

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    Modifier.padding(horizontal = 14.dp, vertical = if (compact) 7.dp else 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "SPEED  $baseCps cells/s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black
                            )
                            if (effectiveCps != baseCps) {
                                Text(
                                    "run pace: $effectiveCps cells/s",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        if (!started && !finished) {
                            Button(onClick = onStart) {
                                Text("START", fontWeight = FontWeight.Black)
                            }
                        } else if (started) {
                            Text(
                                "SWIPE TO TURN",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Slider(
                        value = baseCps.toFloat(),
                        onValueChange = {
                            onSpeedChange(
                                ArcadeSnakeSpeed.normalizeCps(it.roundToInt())
                            )
                        },
                        enabled = !finished,
                        valueRange = ArcadeSnakeSpeed.MIN_CPS.toFloat()..ArcadeSnakeSpeed.MAX_CPS.toFloat(),
                        steps = ArcadeSnakeSpeed.MAX_CPS - ArcadeSnakeSpeed.MIN_CPS - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SnakeStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SnakeBoard(
    stateJson: String,
    boardWidth: Dp,
    boardHeight: Dp,
    inputEnabled: Boolean,
    onTurn: (Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val body = state.getJSONArray("body")
    val food = state.optInt("food", 0)

    Canvas(
        modifier = Modifier
            .size(width = boardWidth, height = boardHeight)
            .pointerInput(inputEnabled) {
                if (inputEnabled) {
                    var dragX = 0f
                    var dragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDragEnd = {
                            if (abs(dragX) > 12f || abs(dragY) > 12f) {
                                val direction = if (abs(dragX) > abs(dragY)) {
                                    if (dragX > 0f) 1 else 3
                                } else {
                                    if (dragY > 0f) 2 else 0
                                }
                                onTurn(direction)
                            }
                        },
                        onDragCancel = {
                            dragX = 0f
                            dragY = 0f
                        },
                        onDrag = { _, amount ->
                            dragX += amount.x
                            dragY += amount.y
                        }
                    )
                }
            }
    ) {
        val cols = ArcadeEngine.SNAKE_COLS
        val rows = ArcadeEngine.SNAKE_ROWS
        val cell = minOf(size.width / cols.toFloat(), size.height / rows.toFloat())
        val fieldWidth = cell * cols
        val fieldHeight = cell * rows
        val offsetX = (size.width - fieldWidth) / 2f
        val offsetY = (size.height - fieldHeight) / 2f

        drawRoundRect(
            color = Color(0xFF172521),
            topLeft = Offset(offsetX, offsetY),
            size = Size(fieldWidth, fieldHeight),
            cornerRadius = CornerRadius(cell * .65f, cell * .65f)
        )

        for (c in 0..cols) {
            val x = offsetX + c * cell
            drawLine(
                Color(0x173DCE8A),
                Offset(x, offsetY),
                Offset(x, offsetY + fieldHeight)
            )
        }
        for (r in 0..rows) {
            val y = offsetY + r * cell
            drawLine(
                Color(0x173DCE8A),
                Offset(offsetX, y),
                Offset(offsetX + fieldWidth, y)
            )
        }

        for (i in 0 until body.length()) {
            val index = body.optInt(i)
            val col = index % cols
            val row = index / cols
            val inset = maxOf(1.0f, cell * .10f)
            val color = if (i == 0) Color(0xFFB9F7C6) else Color(0xFF62D889)
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    offsetX + col * cell + inset,
                    offsetY + row * cell + inset
                ),
                size = Size(cell - inset * 2f, cell - inset * 2f),
                cornerRadius = CornerRadius(cell * .22f, cell * .22f)
            )
        }

        val foodCol = food % cols
        val foodRow = food / cols
        drawCircle(
            color = Color(0xFFFFC857),
            radius = cell * .34f,
            center = Offset(
                offsetX + (foodCol + .5f) * cell,
                offsetY + (foodRow + .5f) * cell
            )
        )
        drawCircle(
            color = Color.White.copy(alpha = .62f),
            radius = cell * .10f,
            center = Offset(
                offsetX + (foodCol + .42f) * cell,
                offsetY + (foodRow + .40f) * cell
            )
        )
    }
}
