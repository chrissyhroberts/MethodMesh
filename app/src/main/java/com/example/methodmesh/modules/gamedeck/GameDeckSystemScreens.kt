package com.example.methodmesh.modules.gamedeck

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val GAMEDECK_STATS = "player_stats"
internal const val GAMEDECK_SIMULATOR = "simulator"

@Composable
internal fun GameDeckStatsScreen(store: GameDeckStatsStore) {
    val s = store.snapshot()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("PLAYER RECORD", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "A local record of finished sessions on this device.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Surface(
            Modifier.fillMaxWidth(),
            RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("PERSONAL RESULTS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatChip("Played", s.evaluatedGames.toString())
                    StatChip("Wins", s.wins.toString())
                    StatChip("Losses", s.losses.toString())
                    StatChip("Draws", s.draws.toString())
                }
                if (s.sharedTableGames > 0) {
                    Text(
                        "${s.sharedTableGames} shared-table ${if (s.sharedTableGames == 1) "session" else "sessions"} kept separate from personal W/L/D.",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        RecordSection("GAMES PLAYED") {
            if (s.byGame.isEmpty()) {
                Text("Finish a game to start your record.")
            } else {
                s.byGame.toList()
                    .sortedByDescending { it.second }
                    .take(12)
                    .forEach { (game, count) ->
                        RecordRow(prettyGame(game), "$count")
                    }
            }
        }

        RecordSection("PERSONAL BESTS") {
            if (s.bestMoves.isEmpty()) {
                Text("No best-move records yet.")
            } else {
                s.bestMoves.toList()
                    .sortedBy { it.second }
                    .take(10)
                    .forEach { (game, moves) ->
                        RecordRow(prettyGame(game), "$moves moves")
                    }
            }
        }

        RecordSection("PLAY EXPOSURE") {
            Text(
                "This counts what you have played. It is deliberately not a skill or competence score.",
                style = MaterialTheme.typography.labelSmall
            )
            if (s.skillExposure.isEmpty()) {
                Text("No exposure recorded yet.")
            } else {
                s.skillExposure.toList()
                    .sortedByDescending { it.second }
                    .forEach { (skill, count) ->
                        RecordRow(prettyLabel(skill), "$count")
                    }
            }
        }

        RecordSection("ACHIEVEMENTS") {
            if (s.achievements.isEmpty()) {
                Text("Your first finished game unlocks the first achievement.")
            } else {
                s.achievements.sorted().forEach { achievement ->
                    Text("• ${prettyAchievement(achievement)}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RecordSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun RecordRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Text(value)
    }
}

private fun prettyLabel(value: String): String =
    value.replace('_', ' ').lowercase()
        .replaceFirstChar { it.uppercase() }

private fun prettyAchievement(value: String): String = when {
    value == "first_game" -> "First game"
    value == "first_win" -> "First personal win"
    value == "table_regular" -> "Table regular"
    value == "game_shelf_veteran" -> "Game shelf veteran"
    value.startsWith("specialist:") -> "Played 10 × ${prettyGame(value.substringAfter(':'))}"
    else -> prettyLabel(value)
}

@Composable
internal fun GameDeckSimulatorScreen() {
    var game by remember { mutableStateOf(GameDeckExtraEngine.TIC_TAC_TOE) }
    var agentA by remember { mutableStateOf(GameDeckAgents.STANDARD) }
    var agentB by remember { mutableStateOf(GameDeckAgents.CASUAL) }
    var result by remember { mutableStateOf<SimSeriesResult?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("CPU ARENA", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        Text(
            "Choose two CPU styles. They play 100 real games and swap seats every match.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SimulatorSection("1  CHOOSE A GAME") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    GameDeckExtraEngine.TIC_TAC_TOE,
                    GameDeckExtraEngine.NIM,
                    GameDeckExtraEngine.REVERSI
                ).forEach { candidate ->
                    ChoiceButton(
                        label = prettyGame(candidate),
                        selected = candidate == game,
                        enabled = !running,
                        onClick = { game = candidate; result = null }
                    )
                }
            }
        }

        SimulatorSection("2  CHOOSE AGENT A") {
            AgentSelector(
                selected = agentA,
                enabled = !running,
                onSelected = { agentA = it; result = null }
            )
            Text(agentA.description, style = MaterialTheme.typography.labelSmall)
        }

        SimulatorSection("3  CHOOSE AGENT B") {
            AgentSelector(
                selected = agentB,
                enabled = !running,
                onSelected = { agentB = it; result = null }
            )
            Text(agentB.description, style = MaterialTheme.typography.labelSmall)
        }

        Button(
            onClick = {
                scope.launch {
                    running = true
                    try {
                        result = withContext(Dispatchers.Default) {
                            GameDeckSimulator.runSeries(
                                game = game,
                                games = 100,
                                agentA = agentA,
                                agentB = agentB,
                                seriesSeed = "ui_arena"
                            )
                        }
                    } finally {
                        running = false
                    }
                }
            },
            enabled = !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (running) "SIMULATING…" else "RUN 100 MATCHES", fontWeight = FontWeight.Black)
        }

        if (running) {
            Text(
                "Running off the UI thread. The screen will update when the series finishes.",
                style = MaterialTheme.typography.labelSmall
            )
        }

        result?.let { r ->
            SimulationResultCard(r)
        }
    }
}

@Composable
private fun SimulatorSection(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun AgentSelector(
    selected: GameDeckAgentProfile,
    enabled: Boolean,
    onSelected: (GameDeckAgentProfile) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        GameDeckAgents.profiles.forEach { profile ->
            ChoiceButton(
                label = profile.level.name.lowercase().replaceFirstChar { it.uppercase() },
                selected = profile.id == selected.id,
                enabled = enabled,
                onClick = { onSelected(profile) }
            )
        }
    }
}

@Composable
private fun ChoiceButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled) {
            Text("✓ $label", fontWeight = FontWeight.Black)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(label)
        }
    }
}

@Composable
private fun SimulationResultCard(r: SimSeriesResult) {
    val completed = r.completedGames.coerceAtLeast(1)
    val aPct = (100.0 * r.agentAWins / completed).toInt()
    val bPct = (100.0 * r.agentBWins / completed).toInt()
    val drawPct = (100.0 * r.draws / completed).toInt()
    val nonResults = r.aborted + r.invalid + r.unsupported

    Surface(
        Modifier.fillMaxWidth(),
        RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Column(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("RESULT", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
            Text(prettyGame(r.game), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)

            RecordRow(r.agentA, "${r.agentAWins} wins  •  $aPct%")
            RecordRow(r.agentB, "${r.agentBWins} wins  •  $bPct%")
            RecordRow("Draws", "${r.draws}  •  $drawPct%")

            Text(
                "Seat check  •  first seat ${r.seat1Wins} wins  •  second seat ${r.seat2Wins} wins",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Mean length  •  ${"%.1f".format(r.meanMoves)} moves",
                style = MaterialTheme.typography.bodySmall
            )

            if (nonResults > 0) {
                Surface(
                    Modifier.fillMaxWidth(),
                    RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        "CHECK RUN  •  ${r.aborted} aborted, ${r.invalid} invalid, ${r.unsupported} unsupported. These were not counted as draws.",
                        Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Text(
                    "All ${r.completedGames} matches completed normally.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

internal fun prettyGame(game: String): String =
    GameDeckUxCatalog.info(game)?.title
        ?: game.split('_').joinToString(" ") {
            it.replaceFirstChar { ch -> ch.uppercase() }
        }
