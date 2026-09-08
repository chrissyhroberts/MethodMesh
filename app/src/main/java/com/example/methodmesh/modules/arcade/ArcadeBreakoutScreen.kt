package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject
import kotlin.math.sqrt

@Composable
internal fun BreakoutArcade(
    stateJson: String,
    paused: Boolean,
    onStep: () -> Unit,
    onPaddle: (Float) -> Unit,
    onStart: () -> Unit
) {
    val state = JSONObject(stateJson)
    val started = state.optBoolean("started", false)
    val finished = state.optBoolean("finished", false)
    val serveTicks = state.optInt("serve_ticks", 0)
    val level = state.optInt("level", 1)
    val totalLevels = state.optInt("total_levels", 5)
    val levelName = state.optString("level_name", "WALL")
    val speed = sqrt(
        state.optDouble("vx", 0.0) * state.optDouble("vx", 0.0) +
            state.optDouble("vy", 0.0) * state.optDouble("vy", 0.0)
    )

    val latestOnStep = rememberUpdatedState(onStep)

    LaunchedEffect(started, finished, paused) {
        while (started && !finished && !paused) {
            delay(ArcadeFixedStep.STEP_MS)
            latestOnStep.value()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val header = 72.dp
        val gap = 7.dp
        val courtHeight = (maxHeight - header - gap).coerceAtLeast(230.dp)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(header),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "WALL BREAK",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "L$level/$totalLevels • $levelName",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${state.optInt("score", 0)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "${state.optInt("lives", 3)} lives • ball ${"%.2f".format(speed)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(gap))

            BreakoutCourt(
                stateJson = stateJson,
                height = courtHeight,
                inputEnabled = started && !paused && !finished,
                onPaddle = onPaddle
            )
        }

        if (!started && !finished) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f),
                    tonalElevation = 10.dp,
                    shadowElevation = 10.dp
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "BREAK FIVE WALLS",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Where the ball hits — and how the paddle is moving — changes the return angle. Clear five different layouts.",
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onStart) {
                            Text("START", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        } else if (started && serveTicks > 0 && !paused) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .88f),
                    tonalElevation = 6.dp
                ) {
                    Text(
                        "LEVEL $level • $levelName",
                        Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakoutCourt(
    stateJson: String,
    height: androidx.compose.ui.unit.Dp,
    inputEnabled: Boolean,
    onPaddle: (Float) -> Unit
) {
    val state = JSONObject(stateJson)
    val bricks = state.getJSONArray("bricks")
    val rows = state.optInt("rows", 5).coerceAtLeast(1)
    val cols = state.optInt("cols", 6).coerceAtLeast(1)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(inputEnabled) {
                if (inputEnabled) {
                    detectTapGestures { tap ->
                        onPaddle((tap.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(inputEnabled) {
                if (inputEnabled) {
                    detectDragGestures { change, _ ->
                        onPaddle(
                            (change.position.x / size.width).coerceIn(0f, 1f)
                        )
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF12201E),
                cornerRadius = CornerRadius(22f, 22f)
            )

            for (index in 0 until rows * cols) {
                val hp = bricks.optInt(index, 0)
                if (hp <= 0) continue
                val rect = ArcadeBreakoutRules.brickRect(index, rows, cols)
                val row = index / cols
                val baseColor = when (row % 5) {
                    0 -> Color(0xFFFFC857)
                    1 -> Color(0xFFF4A261)
                    2 -> Color(0xFFE76F51)
                    3 -> Color(0xFF6EC6CA)
                    else -> Color(0xFF7BE495)
                }
                drawRoundRect(
                    color = if (hp >= 2) baseColor.copy(alpha = .72f) else baseColor,
                    topLeft = Offset(
                        (rect.left * size.width).toFloat(),
                        (rect.top * size.height).toFloat()
                    ),
                    size = Size(
                        ((rect.right - rect.left) * size.width).toFloat(),
                        ((rect.bottom - rect.top) * size.height).toFloat()
                    ),
                    cornerRadius = CornerRadius(7f, 7f)
                )
                if (hp >= 2) {
                    val cx = ((rect.left + rect.right) * .5 * size.width).toFloat()
                    val cy = ((rect.top + rect.bottom) * .5 * size.height).toFloat()
                    drawCircle(
                        color = Color.White.copy(alpha = .72f),
                        radius = 3.5f,
                        center = Offset(cx, cy)
                    )
                }
            }

            val paddleX = state.optDouble("paddle", .5).toFloat() * size.width
            val paddleWidth = size.width * (ArcadeBreakoutRules.PADDLE_HALF * 2.0).toFloat()
            val paddleTop = size.height * ArcadeBreakoutRules.PADDLE_Y.toFloat()
            val paddleHeight = size.height * ArcadeBreakoutRules.PADDLE_HEIGHT.toFloat()
            drawRoundRect(
                color = Color(0xFF7BE495),
                topLeft = Offset(
                    paddleX - paddleWidth / 2f,
                    paddleTop
                ),
                size = Size(paddleWidth, paddleHeight),
                cornerRadius = CornerRadius(paddleHeight * .5f, paddleHeight * .5f)
            )

            val ballX = state.optDouble("ball_x", .5).toFloat() * size.width
            val ballY = state.optDouble("ball_y", .72).toFloat() * size.height
            val radius = minOf(size.width, size.height) * ArcadeBreakoutRules.BALL_RADIUS.toFloat()
            drawCircle(
                color = Color.Black.copy(alpha = .22f),
                radius = radius * 1.18f,
                center = Offset(ballX + 2f, ballY + 3f)
            )
            drawCircle(
                color = Color.White,
                radius = radius,
                center = Offset(ballX, ballY)
            )
        }
    }
}
