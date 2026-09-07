package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.json.JSONObject


@Composable
internal fun PongArcade(
    stateJson: String,
    paused: Boolean,
    onStep: () -> Unit,
    onPaddle: (Int, Float) -> Unit,
    onMode: (Boolean) -> Unit,
    onStart: () -> Unit
) {
    val state = JSONObject(stateJson)
    val cpu = state.optBoolean("cpu", true)
    val started = state.optBoolean("started", false)
    val finished = state.optBoolean("finished", false)
    val serveTicks = state.optInt("serve_ticks", 0)
    val rally = state.optInt("rally", 0)

    val latestOnStep = rememberUpdatedState(onStep)

    LaunchedEffect(started, finished, paused) {
        while (started && !finished && !paused) {
            delay(ArcadeFixedStep.STEP_MS)
            latestOnStep.value()
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val railHeight = 78.dp
        val gap = 5.dp
        val playHeight =
            (maxHeight - railHeight - railHeight - gap - gap)
                .coerceAtLeast(190.dp)

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PongRail(
                label = if (cpu) "CPU" else "PLAYER 2",
                score = state.optInt("p2_score", 0),
                detail = if (cpu) "AUTOMATIC" else "DRAG TOP HALF",
                top = true,
                human = !cpu
            )

            Spacer(Modifier.height(gap))

            PongCourt(
                stateJson = stateJson,
                courtHeight = playHeight,
                inputEnabled = !paused && !finished && started,
                onPaddle = onPaddle
            )

            Spacer(Modifier.height(gap))

            PongRail(
                label = if (cpu) "YOU" else "PLAYER 1",
                score = state.optInt("p1_score", 0),
                detail = if (rally > 0) "DRAG BOTTOM HALF  •  RALLY $rally" else "DRAG BOTTOM HALF",
                top = false,
                human = true
            )
        }

        if (!started && !finished) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                PongSetupCard(
                    cpu = cpu,
                    onMode = onMode,
                    onStart = onStart
                )
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
private fun PongRail(
    label: String,
    score: Int,
    detail: String,
    top: Boolean,
    human: Boolean
) {
    val color = if (top) Color(0xFFFFC857) else Color(0xFFE84A5F)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .graphicsLayer {
                rotationZ = if (top && human) 180f else 0f
            },
        shape = RoundedCornerShape(22.dp),
        color = color.copy(alpha = .18f),
        tonalElevation = 4.dp
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(label, fontWeight = FontWeight.Black)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                score.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun PongCourt(
    stateJson: String,
    courtHeight: Dp,
    inputEnabled: Boolean,
    onPaddle: (Int, Float) -> Unit
) {
    val state = JSONObject(stateJson)
    val cpu = state.optBoolean("cpu", true)

    Box(
        Modifier
            .fillMaxWidth()
            .height(courtHeight)
            .pointerInput(cpu, inputEnabled) {
                if (inputEnabled) {
                    detectTapGestures { tap ->
                        val topHalf = tap.y < size.height / 2f
                        if (!cpu || !topHalf) {
                            val player = if (!cpu && topHalf) 2 else 1
                            onPaddle(
                                player,
                                (tap.x / size.width).coerceIn(0f, 1f)
                            )
                        }
                    }
                }
            }
            .pointerInput(cpu, inputEnabled) {
                if (inputEnabled) {
                    var dragPlayer = 0
                    detectDragGestures(
                        onDragStart = { start ->
                            val topHalf = start.y < size.height / 2f
                            dragPlayer = when {
                                cpu && topHalf -> 0
                                !cpu && topHalf -> 2
                                else -> 1
                            }
                        },
                        onDragEnd = {
                            dragPlayer = 0
                        },
                        onDragCancel = {
                            dragPlayer = 0
                        },
                        onDrag = { change, _ ->
                            if (dragPlayer != 0) {
                                onPaddle(
                                    dragPlayer,
                                    (change.position.x / size.width)
                                        .coerceIn(0f, 1f)
                                )
                            }
                        }
                    )
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF132723),
                cornerRadius = CornerRadius(22f, 22f)
            )

            drawLine(
                Color(0x557BE495),
                Offset(0f, size.height / 2f),
                Offset(size.width, size.height / 2f),
                2f
            )

            val p1 = state.optDouble("p1", .5).toFloat() * size.width
            val p2 = state.optDouble("p2", .5).toFloat() * size.width
            val paddleWidth = size.width * .28f
            val paddleHeight = 13f

            drawRoundRect(
                color = Color(0xFFE84A5F),
                topLeft = Offset(
                    p1 - paddleWidth / 2f,
                    size.height * .94f
                ),
                size = Size(paddleWidth, paddleHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            drawRoundRect(
                color = Color(0xFFFFC857),
                topLeft = Offset(
                    p2 - paddleWidth / 2f,
                    size.height * .04f
                ),
                size = Size(paddleWidth, paddleHeight),
                cornerRadius = CornerRadius(8f, 8f)
            )

            drawCircle(
                color = Color.Black.copy(alpha = .22f),
                radius = 12f,
                center = Offset(
                    state.optDouble("ball_x", .5).toFloat() * size.width + 2f,
                    state.optDouble("ball_y", .5).toFloat() * size.height + 3f
                )
            )
            drawCircle(
                color = Color.White,
                radius = 10f,
                center = Offset(
                    state.optDouble("ball_x", .5).toFloat() * size.width,
                    state.optDouble("ball_y", .5).toFloat() * size.height
                )
            )
        }
    }
}

@Composable
private fun PongSetupCard(
    cpu: Boolean,
    onMode: (Boolean) -> Unit,
    onStart: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "PONG TABLE",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black
            )
            Text(
                "First to 7",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (cpu) {
                    Button(onClick = { onMode(true) }) {
                        Text("✓ CPU")
                    }
                    OutlinedButton(onClick = { onMode(false) }) {
                        Text("2 PLAYERS")
                    }
                } else {
                    OutlinedButton(onClick = { onMode(true) }) {
                        Text("CPU")
                    }
                    Button(onClick = { onMode(false) }) {
                        Text("✓ 2 PLAYERS")
                    }
                }
            }
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("START MATCH", fontWeight = FontWeight.Black)
            }
            Text(
                "Drag or tap your half of the court to move.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}

