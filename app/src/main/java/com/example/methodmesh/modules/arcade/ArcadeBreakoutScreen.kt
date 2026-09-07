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
    val pace = 1 + (state.optInt("score", 0) / 50)

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
                            "BRICK BREAKER",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Drag or tap to move the paddle",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${state.optInt("score", 0)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "SCORE  •  ${state.optInt("lives", 3)} lives  •  PACE $pace",
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
                            "BREAK THE WALL",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Clear all 30 bricks. You have three balls.",
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
                        "READY",
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

            val brickGap = size.width * .008f
            val brickWidth = (size.width - brickGap * 7f) / 6f
            val brickHeight = size.height * .048f
            val startY = size.height * .11f
            val rowGap = size.height * .016f

            for (row in 0 until 5) {
                for (col in 0 until 6) {
                    val index = row * 6 + col
                    if (bricks.optInt(index, 1) == 0) continue
                    val left = brickGap + col * (brickWidth + brickGap)
                    val top = startY + row * (brickHeight + rowGap)
                    val brickColor = when (row) {
                        0 -> Color(0xFFFFC857)
                        1 -> Color(0xFFF4A261)
                        2 -> Color(0xFFE76F51)
                        3 -> Color(0xFF6EC6CA)
                        else -> Color(0xFF7BE495)
                    }
                    drawRoundRect(
                        color = brickColor,
                        topLeft = Offset(left, top),
                        size = Size(brickWidth, brickHeight),
                        cornerRadius = CornerRadius(7f, 7f)
                    )
                }
            }

            val paddleX = state.optDouble("paddle", .5).toFloat() * size.width
            val paddleWidth = size.width * .30f
            drawRoundRect(
                color = Color(0xFF7BE495),
                topLeft = Offset(
                    paddleX - paddleWidth / 2f,
                    size.height * .92f
                ),
                size = Size(paddleWidth, 14f),
                cornerRadius = CornerRadius(8f, 8f)
            )

            drawCircle(
                color = Color.Black.copy(alpha = .22f),
                radius = 11f,
                center = Offset(
                    state.optDouble("ball_x", .5).toFloat() * size.width + 2f,
                    state.optDouble("ball_y", .72).toFloat() * size.height + 3f
                )
            )
            drawCircle(
                color = Color.White,
                radius = 9f,
                center = Offset(
                    state.optDouble("ball_x", .5).toFloat() * size.width,
                    state.optDouble("ball_y", .72).toFloat() * size.height
                )
            )
        }
    }
}
