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
internal fun LaneDodgeArcade(
    stateJson: String,
    paused: Boolean,
    onStep: () -> Unit,
    onLane: (Int) -> Unit,
    onStart: () -> Unit
) {
    val state = JSONObject(stateJson)
    val started = state.optBoolean("started", false)
    val finished = state.optBoolean("finished", false)
    val difficulty = state.optString("difficulty", "normal").uppercase()

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
                            "LANE DODGE",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Tap or drag across five lanes",
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
                            "$difficulty  •  DODGED  •  LEVEL ${state.optInt("level", 1)}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.height(gap))

            DodgeCourt(
                stateJson = stateJson,
                height = courtHeight,
                inputEnabled = started && !paused && !finished,
                onLane = onLane
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
                            "STAY IN THE GAP",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            "Move between five lanes and let the falling blocks pass.",
                            textAlign = TextAlign.Center
                        )
                        Button(onClick = onStart) {
                            Text("START", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DodgeCourt(
    stateJson: String,
    height: androidx.compose.ui.unit.Dp,
    inputEnabled: Boolean,
    onLane: (Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val hazards = state.optJSONArray("hazards") ?: org.json.JSONArray()

    fun laneFor(x: Float, width: Int): Int =
        ((x / width.toFloat()) * 5f).toInt().coerceIn(0, 4)

    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(inputEnabled) {
                if (inputEnabled) {
                    detectTapGestures { tap ->
                        onLane(laneFor(tap.x, size.width))
                    }
                }
            }
            .pointerInput(inputEnabled) {
                if (inputEnabled) {
                    detectDragGestures { change, _ ->
                        onLane(laneFor(change.position.x, size.width))
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = Color(0xFF111A24),
                cornerRadius = CornerRadius(22f, 22f)
            )

            val laneWidth = size.width / 5f
            for (i in 1 until 5) {
                drawLine(
                    color = Color.White.copy(alpha = .12f),
                    start = Offset(i * laneWidth, 0f),
                    end = Offset(i * laneWidth, size.height),
                    strokeWidth = 2f
                )
            }

            for (i in 0 until hazards.length()) {
                val h = hazards.optJSONObject(i) ?: continue
                val lane = h.optInt("lane", 0).coerceIn(0, 4)
                val y = h.optDouble("y", 0.0).toFloat() * size.height
                val blockWidth = laneWidth * .62f
                val blockHeight = size.height * .055f
                val left = lane * laneWidth + (laneWidth - blockWidth) / 2f
                drawRoundRect(
                    color = Color(0xFFE84A5F),
                    topLeft = Offset(left, y),
                    size = Size(blockWidth, blockHeight),
                    cornerRadius = CornerRadius(9f, 9f)
                )
            }

            val playerLane = state.optInt("lane", 2).coerceIn(0, 4)
            val playerWidth = laneWidth * .56f
            val playerHeight = size.height * .045f
            val playerLeft =
                playerLane * laneWidth + (laneWidth - playerWidth) / 2f
            drawRoundRect(
                color = Color(0xFF7BE495),
                topLeft = Offset(
                    playerLeft,
                    size.height * .88f
                ),
                size = Size(playerWidth, playerHeight),
                cornerRadius = CornerRadius(10f, 10f)
            )
        }
    }
}
