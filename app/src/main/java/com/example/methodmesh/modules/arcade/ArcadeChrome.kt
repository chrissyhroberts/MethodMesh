package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.json.JSONObject


@Composable
internal fun ArcadeLauncher(
    records: ArcadeRecordSnapshot,
    onOpen: (String) -> Unit,
    onExit: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Arcade",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    "Quick games built for touch",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            OutlinedButton(onClick = onExit) {
                Text("METHODMESH")
            }
        }

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "YOUR ARCADE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ArcadeRecordStat(
                        label = "Snake",
                        value = records.snakeBestScore.toString()
                    )
                    ArcadeRecordStat(
                        label = "Wall Break",
                        value = records.breakoutBestScore.toString()
                    )
                    ArcadeRecordStat(
                        label = "Dodge",
                        value = records.dodgeBestScore.toString()
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ArcadeRecordStat(
                        label = "Pong vs CPU",
                        value = "${records.pongCpuWins}–${records.pongCpuLosses}"
                    )
                    ArcadeRecordStat(
                        label = "Wall clears",
                        value = records.breakoutClears.toString()
                    )
                    ArcadeRecordStat(
                        label = "Games",
                        value = records.totalGames.toString()
                    )
                }
            }
        }

        ArcadeUxCatalog.games.forEach { info ->
            ArcadeTile(info = info) {
                onOpen(info.id)
            }
        }

        Text(
            "Snake, Wall Break and Lane Dodge are solo. Pong supports CPU or across-the-table two-player play.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ArcadeRecordStat(
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ArcadeTile(
    info: ArcadeGameInfo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
                )
                Text(
                    info.subtitle,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .75f)
            ) {
                Text(
                    info.badge,
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
internal fun ArcadeMenu(
    open: Boolean,
    soundEnabled: Boolean,
    onToggle: () -> Unit,
    onHelp: () -> Unit,
    onSoundToggle: () -> Unit,
    onRestart: () -> Unit,
    onGames: () -> Unit,
    onDone: (() -> Unit)?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (open) {
            Surface(
                onClick = onToggle,
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = .18f)
            ) {}
        }

        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier
                .padding(top = 6.dp)
                .height(42.dp)
        ) {
            Text(
                "MENU",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Black
            )
        }

        if (open) {
            Surface(
                modifier = Modifier.padding(top = 52.dp),
                shape = RoundedCornerShape(18.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(onClick = onHelp) {
                        Text("HOW TO PLAY")
                    }
                    OutlinedButton(onClick = onSoundToggle) {
                        Text(if (soundEnabled) "SOUND  ON" else "SOUND  OFF")
                    }
                    OutlinedButton(onClick = onRestart) {
                        Text("RESTART")
                    }
                    OutlinedButton(onClick = onGames) {
                        Text("ALL GAMES")
                    }
                    if (onDone != null) {
                        Button(onClick = onDone) {
                            Text("DONE")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ArcadeHelpOverlay(
    game: String,
    onDismiss: () -> Unit
) {
    val info = ArcadeUxCatalog.info(game) ?: return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 10.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "HOW TO PLAY",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Text(
                        info.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Black
                    )
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        info.badge,
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Text(
                info.objective,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )

            info.steps.forEachIndexed { index, step ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        modifier = Modifier.size(28.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "${index + 1}",
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    Text(
                        step,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "TIP  •  ${info.tip}",
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("GOT IT")
            }
        }
    }
}

// ---------------------------------------------------------------------
// Result
// ---------------------------------------------------------------------

@Composable
internal fun ArcadeResultPanel(
    stateJson: String,
    onAgain: () -> Unit,
    onGames: () -> Unit,
    onDone: (() -> Unit)?
) {
    val state = JSONObject(stateJson)
    val game = state.optString("game")
    val title: String
    val detail: String

    when (game) {
        ArcadeEngine.SNAKE -> {
            val score = state.optInt("score", 0)
            val length = state.optJSONArray("body")?.length() ?: 0
            title =
                if (state.optString("winner") == "cleared") "BOARD CLEARED!"
                else "GAME OVER"
            detail = "Score $score  •  length $length"
        }

        ArcadeEngine.PONG -> {
            val cpu = state.optBoolean("cpu", true)
            val winner = state.optString("winner")
            title = when {
                cpu && winner == "1" -> "YOU WIN!"
                cpu && winner == "2" -> "CPU WINS!"
                winner == "1" -> "PLAYER 1 WINS!"
                winner == "2" -> "PLAYER 2 WINS!"
                else -> "MATCH OVER"
            }
            detail =
                "${state.optInt("p1_score", 0)}  –  ${state.optInt("p2_score", 0)}"
        }

        ArcadeEngine.BREAKOUT -> {
            title =
                if (state.optString("winner") == "cleared") "ALL WALLS CLEARED!"
                else "OUT OF BALLS"
            detail = "Level ${state.optInt("level", 1)}/${state.optInt("total_levels", 5)} • score ${state.optInt("score", 0)}"
        }

        ArcadeEngine.DODGE -> {
            title = "CRASH!"
            detail = "Dodged ${state.optInt("score", 0)} blocks"
        }

        else -> {
            title = "RUN COMPLETE"
            detail = ""
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        tonalElevation = 9.dp,
        shadowElevation = 9.dp
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                detail,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(onClick = onAgain) {
                    Text("AGAIN")
                }
                OutlinedButton(onClick = onGames) {
                    Text("GAMES")
                }
                if (onDone != null) {
                    Button(onClick = onDone) {
                        Text("DONE")
                    }
                }
            }
        }
    }
}
