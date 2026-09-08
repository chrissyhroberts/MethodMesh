package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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


@Composable
internal fun SnakeArcade(
    stateJson: String,
    paused: Boolean,
    bestScore: Int,
    onStep: () -> Unit,
    onTurn: (Int) -> Unit,
    onSpeedChange: (String) -> Unit,
    onStart: () -> Unit
) {
    val state = JSONObject(stateJson)
    val started = state.optBoolean("started", false)
    val finished = state.optBoolean("finished", false)
    val score = state.optInt("score", 0)
    val length = state.optJSONArray("body")?.length() ?: 0
    val speed = ArcadeSnakeSpeed.normalize(
        state.optString("speed", ArcadeSnakeSpeed.RELAXED)
    )

    val latestOnStep = rememberUpdatedState(onStep)
    val latestSpeed = rememberUpdatedState(speed)
    val latestScore = rememberUpdatedState(score)

    LaunchedEffect(started, finished, paused) {
        while (started && !finished && !paused) {
            delay(ArcadeSnakeSpeed.delayMs(latestSpeed.value, latestScore.value))
            latestOnStep.value()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxHeight < 590.dp
        val controlsReserve = if (compact) 150.dp else 178.dp
        val boardSide = minOf(
            maxWidth,
            (maxHeight - controlsReserve - 64.dp).coerceAtLeast(150.dp)
        )

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "SNAKE SPRINT",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        "Swipe or use the arrows",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    SnakeStat("SCORE", score.toString())
                    SnakeStat("BEST", bestScore.toString())
                    SnakeStat("LENGTH", length.toString())
                }
            }

            SnakeBoard(
                stateJson = stateJson,
                boardSide = boardSide,
                inputEnabled = !paused && !finished,
                onTurn = onTurn
            )

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = if (compact) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SnakeDPad(
                        enabled = !paused && !finished,
                        onTurn = onTurn
                    )

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when {
                            !started && !finished -> {
                                Text(
                                    "Choose speed and direction.",
                                    fontWeight = FontWeight.Bold
                                )
                                SnakeSpeedChooser(
                                    selected = speed,
                                    onSelected = onSpeedChange
                                )
                                Button(onClick = onStart) {
                                    Text("START", fontWeight = FontWeight.Black)
                                }
                            }
                            started -> {
                                Text(
                                    "Keep moving.",
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    "Food is worth 10 points.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            else -> Text(
                                "Run complete.",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SnakeSpeedChooser(
    selected: String,
    onSelected: (String) -> Unit
) {
    val normalized = ArcadeSnakeSpeed.normalize(selected)
    val index = ArcadeSnakeSpeed.modes.indexOf(normalized).coerceAtLeast(0)
    val next = ArcadeSnakeSpeed.modes[(index + 1) % ArcadeSnakeSpeed.modes.size]

    OutlinedButton(
        onClick = { onSelected(next) }
    ) {
        Text(
            "SPEED: ${ArcadeSnakeSpeed.label(normalized).uppercase()}  ›",
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun SnakeStat(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SnakeBoard(
    stateJson: String,
    boardSide: Dp,
    inputEnabled: Boolean,
    onTurn: (Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val body = state.getJSONArray("body")
    val food = state.optInt("food", 0)

    Canvas(
        modifier = Modifier
            .size(boardSide)
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
                            if (abs(dragX) > 20f || abs(dragY) > 20f) {
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
        val cell = size.width / 10f
        drawRoundRect(
            color = Color(0xFF172521),
            cornerRadius = CornerRadius(24f, 24f)
        )

        for (i in 0..10) {
            drawLine(
                Color(0x223DCE8A),
                Offset(i * cell, 0f),
                Offset(i * cell, size.height)
            )
            drawLine(
                Color(0x223DCE8A),
                Offset(0f, i * cell),
                Offset(size.width, i * cell)
            )
        }

        for (i in 0 until body.length()) {
            val index = body.optInt(i)
            val col = index % 10
            val row = index / 10
            val color = if (i == 0) Color(0xFFB9F7C6) else Color(0xFF62D889)
            drawRoundRect(
                color = color,
                topLeft = Offset(col * cell + 2.5f, row * cell + 2.5f),
                size = Size(cell - 5f, cell - 5f),
                cornerRadius = CornerRadius(cell * .22f, cell * .22f)
            )
        }

        drawCircle(
            color = Color(0xFFFFC857),
            radius = cell * .31f,
            center = Offset(
                (food % 10 + .5f) * cell,
                (food / 10 + .5f) * cell
            )
        )
        drawCircle(
            color = Color.White.copy(alpha = .58f),
            radius = cell * .09f,
            center = Offset(
                (food % 10 + .42f) * cell,
                (food / 10 + .40f) * cell
            )
        )
    }
}

@Composable
private fun SnakeDPad(
    enabled: Boolean,
    onTurn: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        DirectionButton("↑", enabled) { onTurn(0) }
        Row(horizontalArrangement = Arrangement.spacedBy(38.dp)) {
            DirectionButton("←", enabled) { onTurn(3) }
            DirectionButton("→", enabled) { onTurn(1) }
        }
        DirectionButton("↓", enabled) { onTurn(2) }
    }
}

@Composable
private fun DirectionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(50.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
    }
}

