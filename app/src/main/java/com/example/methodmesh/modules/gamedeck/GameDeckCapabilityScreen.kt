package com.example.methodmesh.modules.gamedeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** GameDeck v0.056: tactile games, puzzles, CPU Arena and local-first player records. */
object GameDeckCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100GameDeckMethod.ID
    override val title = "GameDeck"
    override val description = "A lightweight shelf of tactile games and procedural game experiments."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var game by rememberSaveable {
            mutableStateOf(context.action.settings["game"] ?: context.action.settings["input_game"] ?: GameDeckEngine.LAUNCHER)
        }
        var rngMode by rememberSaveable {
            mutableStateOf(context.action.settings["rng_mode"] ?: context.action.settings["input_rng_mode"] ?: "secure_random")
        }
        var seed by rememberSaveable {
            mutableStateOf(context.action.settings["seed"] ?: context.action.settings["input_seed"] ?: "")
        }
        var soundEnabled by rememberSaveable {
            mutableStateOf(
                (context.action.settings["sound"] ?: context.action.settings["input_sound"] ?: "true")
                    .equals("true", ignoreCase = true)
            )
        }
        var stateJson by rememberSaveable(game) {
            mutableStateOf(if (GameDeckExtraEngine.isSupported(game)) GameDeckExtraEngine.newStateJson(game, rngMode, seed) else GameDeckEngine.newStateJson(game))
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var mancalaCpu by rememberSaveable { mutableStateOf(true) }
        var extraCpuOpponent by rememberSaveable(game) { mutableStateOf(false) }
        var sessionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        var tabletopMenuOpen by rememberSaveable { mutableStateOf(false) }
        var gameHelpOpen by rememberSaveable(game) { mutableStateOf(false) }
        val appContext = LocalContext.current.applicationContext
        val statsStore = remember(appContext) { GameDeckStatsStore(appContext) }
        var recordedEndKey by rememberSaveable { mutableStateOf("") }

        fun currentSession(): GameDeckSession = GameDeckSessions.forGame(
            sessionId = sessionId,
            game = game,
            rngMode = rngMode,
            seed = seed,
            mancalaCpu = mancalaCpu,
            extraCpuOpponent = extraCpuOpponent
        )

        fun buildResult(): ExecutionResult {
            val summary = GameDeckEngine.stateSummary(stateJson)
            val session = currentSession()
            val publicSeed = GameDeckSessionCodec.publicSeed(game, stateJson, seed)
            val rngCommitment = GameDeckSessionCodec.rngCommitment(game, rngMode, seed)
            val values = As100GameDeckMethod.snapshot(
                game = summary.game,
                stateJson = stateJson,
                turn = summary.turn,
                winner = summary.winner,
                moveCount = summary.moves,
                rngMode = rngMode,
                seed = seed,
                session = session
            )

            // Do not echo a hidden fixed seed into the request context while a
            // hidden-information game is active. The commitment remains public.
            val publicState = GameDeckSessionCodec.publicStateJson(stateJson)
            val safeSettings = context.action.settings.filterKeys {
                it !in setOf("seed", "input_seed", "state_json", "input_state_json")
            }
            val request = As100GameDeckMethod.request(
                action = As100GameDeckMethod.ID,
                context = context.request.invocationContext.asMap(As100GameDeckMethod.ID) +
                    safeSettings +
                    mapOf(
                        "game" to game,
                        "rng_mode" to rngMode,
                        "seed" to publicSeed,
                        "rng_commitment" to rngCommitment,
                        "session_id" to sessionId,
                        "state_json" to publicState
                    ),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100GameDeckMethod.result(request, values, context.request.invocationContext)
        }

        fun refreshSnapshot() { result = buildResult() }
        fun openGame(selected: String) {
            sessionId = UUID.randomUUID().toString()
            tabletopMenuOpen = false
            gameHelpOpen = false
            game = selected
            stateJson = if (selected == GAMEDECK_STATS || selected == GAMEDECK_SIMULATOR) {
                JSONObject().put("game", selected).put("turn", 1).put("winner", "").put("moves", 0).toString()
            } else if (GameDeckExtraEngine.isSupported(selected)) {
                GameDeckExtraEngine.newStateJson(selected, rngMode, seed)
            } else GameDeckEngine.newStateJson(selected)
            recordedEndKey = ""
            result = null
        }
        fun reset() {
            sessionId = UUID.randomUUID().toString()
            stateJson = if (game == GAMEDECK_STATS || game == GAMEDECK_SIMULATOR) {
                JSONObject().put("game", game).put("turn", 1).put("winner", "").put("moves", 0).toString()
            } else if (GameDeckExtraEngine.isSupported(game)) {
                GameDeckExtraEngine.newStateJson(game, rngMode, seed)
            } else GameDeckEngine.newStateJson(game)
            recordedEndKey = ""
            refreshSnapshot()
        }
        fun home() { openGame(GameDeckEngine.LAUNCHER) }

        LaunchedEffect(game, rngMode, seed, soundEnabled) {
            context.onSettingsChanged(
                mapOf(
                    "game" to game,
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "sound" to soundEnabled.toString()
                )
            )
        }

        LaunchedEffect(context.presentationMode, context.submitsImmediately, context.request.source) {
            val genuineExternalAutoSubmit = context.submitsImmediately && !context.request.source.equals("intent_test", ignoreCase = true)
            if (genuineExternalAutoSubmit && !launched) {
                launched = true
                onConfirmed(buildResult())
            } else if (!launched) {
                launched = true
                refreshSnapshot()
            }
        }

        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
        val scaffoldResult = if (keepLiveDashboard) null else result
        val summary = GameDeckEngine.stateSummary(stateJson)

        LaunchedEffect(summary.winner, soundEnabled) {
            if (summary.winner.isNotBlank()) GameDeckSound.win(soundEnabled)
        }

        LaunchedEffect(game, stateJson) {
            val rawWinner = runCatching { JSONObject(stateJson).optString("winner") }.getOrDefault("")
            if (rawWinner.isNotBlank() && game != GAMEDECK_STATS && game != GAMEDECK_SIMULATOR) {
                val key = "$game|$rawWinner|${JSONObject(stateJson).optInt("moves", 0)}"
                if (key != recordedEndKey) {
                    val finishedState = JSONObject(stateJson)
                    // Seat semantics come from the session contract rather than
                    // being re-derived from UI booleans at result time.
                    val trackedHumanSeat = GameDeckSessions.trackedHumanSeat(currentSession())
                    statsStore.record(
                        sessionId = sessionId,
                        game = game,
                        winner = rawWinner,
                        trackedHumanSeat = trackedHumanSeat,
                        moves = finishedState.optInt("moves", 0),
                        score = finishedState.optInt("score", Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }
                    )
                    recordedEndKey = key
                }
            }
        }

        val gameContent: @Composable () -> Unit = {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (game == GameDeckEngine.LAUNCHER) {
                    Launcher(onOpen = ::openGame, onExit = onCancel)
                } else {
                    Box(modifier = Modifier.fillMaxSize()) {
                        when (game) {
                                GameDeckEngine.CONNECT_FOUR -> ConnectFourGame(
                                    stateJson = stateJson,
                                    soundEnabled = soundEnabled,
                                    onDrop = { col ->
                                        GameDeckSound.drop(soundEnabled)
                                        stateJson = GameDeckEngine.connectFourDrop(stateJson, col)
                                        refreshSnapshot()
                                    }
                                )
                                GameDeckEngine.SNAKES_AND_LADDERS -> SnakesGame(
                                    stateJson = stateJson,
                                    rngMode = rngMode,
                                    seed = seed,
                                    onRngMode = { rngMode = it },
                                    onRoll = {
                                        GameDeckSound.roll(soundEnabled)
                                        val (next, _) = GameDeckEngine.snakesRoll(stateJson, rngMode, seed)
                                        stateJson = next; refreshSnapshot()
                                    },
                                    onMove = {
                                        GameDeckSound.move(soundEnabled)
                                        stateJson = if (JSONObject(stateJson).optString("pending_phase") == "jump") {
                                            GameDeckEngine.snakesCommitJump(stateJson)
                                        } else GameDeckEngine.snakesCommitMove(stateJson)
                                        refreshSnapshot()
                                    }
                                )
                                GameDeckEngine.MANCALA -> MancalaGame(
                                    stateJson = stateJson,
                                    cpu = mancalaCpu,
                                    onCpuChanged = { mancalaCpu = it; reset() },
                                    onPit = { pit ->
                                        GameDeckSound.move(soundEnabled)
                                        stateJson = GameDeckEngine.mancalaMove(stateJson, pit)
                                        refreshSnapshot()
                                    },
                                    onCpuPit = { pit ->
                                        GameDeckSound.cpu(soundEnabled)
                                        stateJson = GameDeckEngine.mancalaMove(stateJson, pit)
                                        refreshSnapshot()
                                    }
                                )
                                GameDeckEngine.MINESWEEPER -> Column(
                                    Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    MinesweeperGame(
                                        stateJson = stateJson,
                                        rngMode = rngMode,
                                        onPreset = { w, h, m -> stateJson = GameDeckEngine.minesweeperConfigure(w, h, m, seed); refreshSnapshot() },
                                        onReveal = { x, y ->
                                            GameDeckSound.move(soundEnabled)
                                            stateJson = GameDeckEngine.minesweeperReveal(stateJson, x, y, rngMode, seed)
                                            refreshSnapshot()
                                        },
                                        onFlag = { x, y -> stateJson = GameDeckEngine.minesweeperToggleFlag(stateJson, x, y); refreshSnapshot() }
                                    )
                                }
                                GAMEDECK_STATS -> GameDeckStatsScreen(statsStore)
                                GAMEDECK_SIMULATOR -> GameDeckSimulatorScreen()
                                else -> if (GameDeckExtraEngine.isSupported(game)) {
                                    ExtraGameScreen(
                                        game = game,
                                        stateJson = stateJson,
                                        rngMode = rngMode,
                                        seed = seed,
                                        soundEnabled = soundEnabled,
                                        cpuOpponent = extraCpuOpponent,
                                        onCpuOpponentChanged = { enabled ->
                                            extraCpuOpponent = enabled
                                            sessionId = UUID.randomUUID().toString()
                                            stateJson = GameDeckExtraEngine.newStateJson(game, rngMode, seed)
                                            recordedEndKey = ""
                                            refreshSnapshot()
                                        }
                                    ) { next ->
                                        GameDeckSound.move(soundEnabled)
                                        stateJson = next
                                        refreshSnapshot()
                                    }
                                }
                            }

                        TabletopMenu(
                            open = tabletopMenuOpen,
                            soundEnabled = soundEnabled,
                            onToggle = { tabletopMenuOpen = !tabletopMenuOpen },
                            onHelp = {
                                tabletopMenuOpen = false
                                gameHelpOpen = true
                            },
                            onSoundToggle = { soundEnabled = !soundEnabled },
                            onNew = if (game == GAMEDECK_STATS || game == GAMEDECK_SIMULATOR) null else ({
                                tabletopMenuOpen = false
                                reset()
                            }),
                            onGames = { tabletopMenuOpen = false; home() },
                            onFinish = if (keepLiveDashboard && result != null) ({
                                tabletopMenuOpen = false
                                result?.let(onConfirmed)
                            }) else null
                        )
                    }

                    // End-state is an overlay, not another vertical row that makes the table scroll.
                    if (summary.winner.isNotBlank() && game != GAMEDECK_STATS && game != GAMEDECK_SIMULATOR) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = .34f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GameResultPanel(
                                    stateJson = stateJson,
                                    session = currentSession(),
                                    onAgain = { reset() },
                                    onGames = { home() },
                                    onDone = if (keepLiveDashboard && result != null) ({
                                        result?.let(onConfirmed)
                                    }) else null
                                )
                            }
                        }
                    }

                    if (gameHelpOpen) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.scrim.copy(alpha = .38f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                GameHelpOverlay(
                                    game = game,
                                    onDismiss = { gameHelpOpen = false }
                                )
                            }
                        }
                    }

                }
            }
        }

        if (keepLiveDashboard) {
            // Immersive native presentation: the host supplies a full-bleed surface
            // and GameDeck owns all controls inside it. External/ODK runs retain
            // the normal generic result scaffold and completion contract.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                gameContent()
            }
        } else {
            CapabilityScreenScaffold(
                title = title,
                capabilityId = capabilityId,
                context = context,
                canGoBack = context.stepNumber > 1,
                capturedResult = scaffoldResult,
                resultPreview = scaffoldResult?.let {
                    mapOf(GameDeckFields.RESULT to "${summary.game}: ${summary.moves} moves", GameDeckFields.WINNER to summary.winner)
                }.orEmpty(),
                onBack = onBack,
                onRetry = { reset() },
                onConfirm = { result?.let(onConfirmed) },
                onCancel = onCancel
            ) {
                gameContent()
            }
        }
    }
}

private val PlayerOneColor = Color(0xFFE84A5F)
private val PlayerTwoColor = Color(0xFFFFC857)
private val LadderColor = Color(0xFF2E9D70)
private val SnakeColor = Color(0xFF8D5BA6)
private val BeanColor = Color(0xFF9B633D)
private val BeanLight = Color(0xFFD9A66D)

@Composable
private fun Launcher(onOpen: (String) -> Unit, onExit: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("GameDeck", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text("Choose a game", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onExit) { Text("METHODMESH") }
        }

        Text(
            "No account needed. Two-player games are designed to sit between the players like a small tabletop.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        GameDeckShelfSection.values().forEach { section ->
            val games = GameDeckUxCatalog.section(section)
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(section.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                        Text(section.subtitle, style = MaterialTheme.typography.labelMedium)
                    }
                    Text("${games.size} games", style = MaterialTheme.typography.labelMedium)
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val gap = 10.dp
                    val tileWidth = (maxWidth - gap) / 2
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        games.chunked(2).forEach { pair ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap)
                            ) {
                                pair.forEach { info ->
                                    GameTile(
                                        info = info,
                                        modifier = Modifier.width(tileWidth),
                                        onClick = { onOpen(info.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun GameTile(
    info: GameDeckGameInfo,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(14.dp).height(108.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
                Text(
                    info.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    info.badge,
                    Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
private fun TabletopMenu(
    open: Boolean,
    soundEnabled: Boolean,
    onToggle: () -> Unit,
    onHelp: () -> Unit,
    onSoundToggle: () -> Unit,
    onNew: (() -> Unit)?,
    onGames: () -> Unit,
    onFinish: (() -> Unit)?
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        if (open) {
            Surface(
                onClick = onToggle,
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = .16f)
            ) {}
        }

        OutlinedButton(
            onClick = onToggle,
            modifier = Modifier.padding(top = 6.dp).height(42.dp)
        ) {
            Text("MENU", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black)
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
                    Button(onClick = onHelp) { Text("HOW TO PLAY") }
                    OutlinedButton(onClick = onSoundToggle) {
                        Text(if (soundEnabled) "SOUND  ON" else "SOUND  OFF")
                    }
                    if (onNew != null) {
                        OutlinedButton(onClick = onNew) { Text("RESTART") }
                    }
                    OutlinedButton(onClick = onGames) { Text("ALL GAMES") }
                    if (onFinish != null) {
                        Button(onClick = onFinish) { Text("DONE") }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameHelpOverlay(
    game: String,
    onDismiss: () -> Unit
) {
    val info = GameDeckUxCatalog.info(game) ?: return

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
                    Text("HOW TO PLAY", style = MaterialTheme.typography.labelMedium)
                    Text(info.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
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

            Text(info.objective, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)

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
                            Text("${index + 1}", fontWeight = FontWeight.Black)
                        }
                    }
                    Text(step, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "TIP  •  ${info.tip}",
                    Modifier.fillMaxWidth().padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("GOT IT")
            }
        }
    }
}

@Composable
private fun TabletopPlayerRail(
    label: String,
    active: Boolean,
    color: Color,
    symbol: String,
    detail: String = "",
    oppositeHuman: Boolean = false,
    controls: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = if (oppositeHuman) 180f else 0f },
        shape = RoundedCornerShape(22.dp),
        color = if (active) color.copy(alpha = .18f) else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = if (active) 5.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Surface(shape = CircleShape, color = color, modifier = Modifier.size(30.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(symbol, color = Color.Black, fontWeight = FontWeight.Black)
                    }
                }
                Column {
                    Text(label, fontWeight = FontWeight.Black)
                    if (detail.isNotBlank()) {
                        Text(
                            detail,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            controls?.invoke()
        }
    }
}

/**
 * Capability-local fixed tabletop. It consumes the measured viewport exactly:
 * far-player rail, board rectangle, near-player rail. No scroll container.
 * In two-human play the far rail is rotated 180 degrees.
 */
@Composable
private fun TabletopFrame(
    topLabel: String,
    topActive: Boolean,
    topDetail: String,
    topIsHuman: Boolean,
    bottomLabel: String,
    bottomActive: Boolean,
    bottomDetail: String,
    topControls: (@Composable () -> Unit)? = null,
    bottomControls: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val railHeight = 92.dp
        val gap = 6.dp
        val centreHeight = (maxHeight - railHeight - railHeight - gap - gap).coerceAtLeast(120.dp)
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.fillMaxWidth().height(railHeight), contentAlignment = Alignment.Center) {
                TabletopPlayerRail(
                    topLabel, topActive, PlayerTwoColor, "◆", topDetail,
                    oppositeHuman = topIsHuman, controls = topControls
                )
            }
            Spacer(Modifier.height(gap))
            Box(
                modifier = Modifier.fillMaxWidth().height(centreHeight),
                contentAlignment = Alignment.Center
            ) { content() }
            Spacer(Modifier.height(gap))
            Box(Modifier.fillMaxWidth().height(railHeight), contentAlignment = Alignment.Center) {
                TabletopPlayerRail(
                    bottomLabel, bottomActive, PlayerOneColor, "●", bottomDetail,
                    controls = bottomControls
                )
            }
        }
    }
}

@Composable
private fun ConnectFourGame(stateJson: String, soundEnabled: Boolean, onDrop: (Int) -> Unit) {
    val state = JSONObject(stateJson)
    val board = state.getJSONArray("board")
    val winnerCells = GameDeckEngine.connectFourWinningCells(stateJson)
    val turn = state.optInt("turn", 1)
    val winner = state.optString("winner")
    var dragPos by remember(stateJson) { mutableStateOf<Offset?>(null) }
    var dragColumn by remember(stateJson) { mutableStateOf(-1) }
    var draggingFromPile by remember(stateJson) { mutableStateOf(false) }

    TabletopFrame(
        topLabel = "PLAYER 2",
        topActive = turn == 2 && winner.isBlank(),
        topDetail = if (turn == 2 && winner.isBlank()) "YOUR TURN" else "◆ yellow",
        topIsHuman = true,
        bottomLabel = "PLAYER 1",
        bottomActive = turn == 1 && winner.isBlank(),
        bottomDetail = if (turn == 1 && winner.isBlank()) "YOUR TURN" else "● red"
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (winner.isBlank()) "Tap a column — or drag the active counter into it." else "Game complete.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val shortSide = if (maxWidth < maxHeight) maxWidth else maxHeight
                val boardSize = if (shortSide < 430.dp) shortSide else 430.dp
    val empty = MaterialTheme.colorScheme.surface
    val boardColor = Color(0xFF3159A8)
    val winStroke = MaterialTheme.colorScheme.onSurface
    val activeColor = if (turn == 1) PlayerOneColor else PlayerTwoColor

    Canvas(
        modifier = Modifier
            .size(boardSize)
            .pointerInput(stateJson, winner) {
                if (winner.isBlank()) {
                    detectTapGestures { tap ->
                        val rowHeight = size.height / 7f
                        if (tap.y >= rowHeight) {
                            val col = (tap.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                            onDrop(col)
                        }
                    }
                }
            }
            .pointerInput(stateJson, turn, winner) {
                if (winner.isBlank()) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val rowHeight = size.height / 7f
                            val supply = Offset(size.width / 2f, rowHeight * .50f)
                            val dx = start.x - supply.x
                            val dy = start.y - supply.y
                            draggingFromPile = sqrt(dx * dx + dy * dy) <= rowHeight * .70f
                            if (draggingFromPile) {
                                GameDeckSound.pickup(soundEnabled)
                                dragPos = start
                                dragColumn = (start.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                            }
                        },
                        onDragEnd = {
                            val drop = dragPos
                            val rowHeight = size.height / 7f
                            val col = dragColumn
                            val shouldDrop = draggingFromPile && drop != null && drop.y >= rowHeight * .78f && col in 0..6
                            dragPos = null
                            dragColumn = -1
                            draggingFromPile = false
                            if (shouldDrop) onDrop(col)
                        },
                        onDragCancel = {
                            dragPos = null
                            dragColumn = -1
                            draggingFromPile = false
                        },
                        onDrag = { change, _ ->
                            if (draggingFromPile) {
                                dragPos = change.position
                                dragColumn = (change.position.x / (size.width / 7f)).toInt().coerceIn(0, 6)
                            }
                        }
                    )
                }
            }
    ) {
        val cw = size.width / 7f
        val ch = size.height / 7f
        val boardTop = ch
        val pieceRadius = minOf(cw, ch) * .34f

        // Visible pickup pile. Pieces enter the board from here instead of appearing magically.
        val supplyCenter = Offset(size.width / 2f, ch * .50f)
        repeat(4) { index ->
            val offset = Offset((index - 1.5f) * cw * .08f, (3 - index) * ch * .045f)
            val center = supplyCenter + offset
            drawCircle(Color.Black.copy(alpha = .16f), pieceRadius, center + Offset(0f, 4f))
            if (turn == 1) {
                drawCircle(PlayerOneColor, pieceRadius, center)
            } else {
                val rr = pieceRadius
                val diamond = Path().apply {
                    moveTo(center.x, center.y - rr)
                    lineTo(center.x + rr, center.y)
                    lineTo(center.x, center.y + rr)
                    lineTo(center.x - rr, center.y)
                    close()
                }
                drawPath(diamond, PlayerTwoColor)
            }
        }

        if (draggingFromPile && dragColumn in 0..6) {
            val left = dragColumn * cw
            drawRoundRect(
                color = activeColor.copy(alpha = .18f),
                topLeft = Offset(left, boardTop),
                size = Size(cw, size.height - boardTop),
                cornerRadius = CornerRadius(cw * .16f, cw * .16f)
            )
        }

        drawRoundRect(
            color = boardColor,
            topLeft = Offset(0f, boardTop),
            size = Size(size.width, size.height - boardTop),
            cornerRadius = CornerRadius(cw * .24f, cw * .24f)
        )

        for (r in 0..5) for (c in 0..6) {
            val center = Offset((c + .5f) * cw, boardTop + (r + .5f) * ch)
            val value = board.getJSONArray(r).optInt(c)
            val color = when (value) { 1 -> PlayerOneColor; 2 -> PlayerTwoColor; else -> empty }
            drawCircle(Color.Black.copy(alpha = 0.20f), radius = pieceRadius * 1.08f, center = center + Offset(0f, 4f))
            if (value == 2) {
                drawCircle(empty, radius = pieceRadius, center = center)
                val rr = pieceRadius * .72f
                val diamond = Path().apply {
                    moveTo(center.x, center.y - rr)
                    lineTo(center.x + rr, center.y)
                    lineTo(center.x, center.y + rr)
                    lineTo(center.x - rr, center.y)
                    close()
                }
                drawPath(diamond, PlayerTwoColor)
            } else {
                drawCircle(color, radius = pieceRadius, center = center)
            }
            if ((r to c) in winnerCells) {
                drawCircle(winStroke, radius = pieceRadius * 1.16f, center = center, style = Stroke(width = 6f))
            }
        }

        dragPos?.let { p ->
            if (turn == 1) {
                drawCircle(PlayerOneColor, pieceRadius, p)
            } else {
                val rr = pieceRadius
                val diamond = Path().apply {
                    moveTo(p.x, p.y - rr)
                    lineTo(p.x + rr, p.y)
                    lineTo(p.x, p.y + rr)
                    lineTo(p.x - rr, p.y)
                    close()
                }
                drawPath(diamond, PlayerTwoColor)
            }
            drawCircle(Color.White.copy(alpha = .58f), pieceRadius * 1.06f, p, style = Stroke(4f))
        }
    }
            }
        }
    }
}

@Composable
private fun SnakesGame(
    stateJson: String,
    rngMode: String,
    seed: String,
    onRngMode: (String) -> Unit,
    onRoll: () -> Unit,
    onMove: () -> Unit
) {
    val state = JSONObject(stateJson)
    val positions = state.getJSONArray("positions")
    val lastRoll = state.optInt("last_roll", 1).coerceIn(1, 6)
    val pendingPhase = state.optString("pending_phase")
    val turn = state.optInt("turn", 0)
    val winner = state.optString("winner")
    var frame by remember { mutableStateOf(0) }
    var rolling by remember { mutableStateOf(false) }
    var revealReady by remember { mutableStateOf(true) }
    val rollScope = rememberCoroutineScope()

    fun doRoll() {
        if (!rolling && pendingPhase.isBlank() && winner.isBlank()) {
            rolling = true
            revealReady = false
            frame = 0
            onRoll()
            rollScope.launch {
                repeat(20) { i -> frame = i + 1; delay(48L) }
                rolling = false
                delay(180L)
                revealReady = true
            }
        }
    }

    val instruction = when {
        rolling -> "Rolling…"
        !revealReady && pendingPhase.isNotBlank() -> "Die settled…"
        pendingPhase == "move" -> "Move to ${state.optInt("pending_to")}" 
        pendingPhase == "jump" -> if (state.optInt("pending_to") > state.optInt("pending_from")) "Climb!" else "Ride snake!"
        else -> "Roll D6"
    }

    val topControl: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChanceStyleD6(finalValue = lastRoll, frame = frame, rolling = rolling, dieSize = 54.dp)
            Button(
                onClick = { if (pendingPhase.isBlank()) doRoll() else if (revealReady && !rolling) onMove() },
                enabled = turn == 1 && winner.isBlank() && !rolling
            ) { Text(if (pendingPhase.isBlank()) "ROLL" else instruction, fontWeight = FontWeight.Black) }
        }
    }
    val bottomControl: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChanceStyleD6(finalValue = lastRoll, frame = frame, rolling = rolling, dieSize = 54.dp)
            Button(
                onClick = { if (pendingPhase.isBlank()) doRoll() else if (revealReady && !rolling) onMove() },
                enabled = turn == 0 && winner.isBlank() && !rolling
            ) { Text(if (pendingPhase.isBlank()) "ROLL" else instruction, fontWeight = FontWeight.Black) }
        }
    }

    TabletopFrame(
        topLabel = "PLAYER 2",
        topActive = turn == 1 && winner.isBlank(),
        topDetail = if (turn == 1 && winner.isBlank()) "YOUR TURN  •  square ${positions.optInt(1, 1)}" else "Square ${positions.optInt(1, 1)}",
        topIsHuman = true,
        bottomLabel = "PLAYER 1",
        bottomActive = turn == 0 && winner.isBlank(),
        bottomDetail = if (turn == 0 && winner.isBlank()) "YOUR TURN  •  square ${positions.optInt(0, 1)}" else "Square ${positions.optInt(0, 1)}",
        topControls = topControl,
        bottomControls = bottomControl
    ) {
        SnakesBoard(stateJson = stateJson, rolling = rolling, revealPending = revealReady, onMove = onMove)
    }

    // RNG mode is configuration, not tabletop chrome. Keep it out of the play surface;
    // external/native settings continue to carry rng_mode and seed.
    @Suppress("UNUSED_VARIABLE") val retainedRngMode = rngMode
    @Suppress("UNUSED_VARIABLE") val retainedSeed = seed
    @Suppress("UNUSED_VARIABLE") val retainedSetter = onRngMode
}

@Composable
private fun SnakesBoard(stateJson: String, rolling: Boolean, revealPending: Boolean, onMove: () -> Unit) {
    val state = JSONObject(stateJson)
    val positions = state.getJSONArray("positions")
    val (ladders, snakes) = GameDeckEngine.snakesTransitions()
    val pendingPhase = state.optString("pending_phase")
    val turn = state.optInt("turn", 0)
    val targetSquare = state.optInt("pending_to", 0)
    var ghost by remember(stateJson) { mutableStateOf<Offset?>(null) }
    var dragging by remember(stateJson) { mutableStateOf(false) }
    var releaseValid by remember(stateJson) { mutableStateOf(false) }
    val grid = MaterialTheme.colorScheme.outlineVariant
    val bg = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val shortSide = if (maxWidth < maxHeight) maxWidth else maxHeight
        val boardSize = if (shortSide < 520.dp) shortSide else 520.dp
    Canvas(
        Modifier.size(boardSize)
            .pointerInput(stateJson, rolling, revealPending) {
                if (pendingPhase.isNotBlank() && !rolling && revealPending) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val current = snakeSquareCenter(positions.optInt(turn, 1), size.width.toFloat())
                            val cell = size.width / 10f
                            val distance = sqrt((start.x-current.x)*(start.x-current.x) + (start.y-current.y)*(start.y-current.y))
                            dragging = distance < cell * .8f
                            if (dragging) ghost = start
                        },
                        onDragEnd = {
                            if (dragging && releaseValid) onMove()
                            dragging = false; ghost = null; releaseValid = false
                        },
                        onDragCancel = { dragging = false; ghost = null; releaseValid = false },
                        onDrag = { change, _ ->
                            if (dragging) {
                                ghost = change.position
                                val target = snakeSquareCenter(targetSquare, size.width.toFloat())
                                val cell = size.width / 10f
                                val dx = change.position.x-target.x; val dy = change.position.y-target.y
                                releaseValid = sqrt(dx*dx + dy*dy) < cell * .85f
                            }
                        }
                    )
                }
            }
            .pointerInput(stateJson, rolling, revealPending, "tap") {
                if (pendingPhase.isNotBlank() && !rolling && revealPending) {
                    detectTapGestures { tap ->
                        val target = snakeSquareCenter(targetSquare, size.width.toFloat())
                        val cell = size.width / 10f
                        val dx = tap.x-target.x; val dy = tap.y-target.y
                        if (sqrt(dx*dx + dy*dy) < cell * .75f) onMove()
                    }
                }
            }
    ) {
        val cell = size.width / 10f
        drawRoundRect(bg, cornerRadius = CornerRadius(24f, 24f))
        for (i in 0..10) {
            drawLine(grid, Offset(i * cell, 0f), Offset(i * cell, size.height), 1.5f)
            drawLine(grid, Offset(0f, i * cell), Offset(size.width, i * cell), 1.5f)
        }
        if (targetSquare in 1..100 && pendingPhase.isNotBlank() && revealPending) {
            drawCircle(Color.White.copy(alpha = .28f), cell * .43f, snakeSquareCenter(targetSquare, size.width.toFloat()))
            drawCircle(if (turn == 0) PlayerOneColor else PlayerTwoColor, cell * .43f, snakeSquareCenter(targetSquare, size.width.toFloat()), style = Stroke(cell * .07f))
        }
        ladders.forEach { (a, b) ->
            val start = snakeSquareCenter(a, size.width); val end = snakeSquareCenter(b, size.width)
            drawLine(LadderColor, start, end, cell * .15f)
            drawCircle(LadderColor, cell * .12f, start); drawCircle(LadderColor, cell * .12f, end)
        }
        snakes.forEach { (a, b) ->
            val start = snakeSquareCenter(a, size.width); val end = snakeSquareCenter(b, size.width)
            val path = Path().apply { moveTo(start.x, start.y); cubicTo(start.x + cell, start.y, end.x - cell, end.y, end.x, end.y) }
            drawPath(path, SnakeColor, style = Stroke(width = cell * .16f))
            drawCircle(SnakeColor, cell * .17f, start)
            drawCircle(Color.White, cell * .035f, start + Offset(-cell*.055f,-cell*.025f))
            drawCircle(Color.White, cell * .035f, start + Offset(cell*.055f,-cell*.025f))
        }
        val c1 = snakeSquareCenter(positions.optInt(0, 1), size.width)
        val c2 = snakeSquareCenter(positions.optInt(1, 1), size.width)
        drawCircle(Color.Black.copy(alpha=.18f), cell*.27f, c1 + Offset(-cell*.14f, 3f))
        drawCircle(PlayerOneColor, cell*.24f, c1 + Offset(-cell*.14f,0f))
        val p2 = c2 + Offset(cell*.14f,0f)
        val rr = cell*.24f
        val diamond = Path().apply { moveTo(p2.x,p2.y-rr); lineTo(p2.x+rr,p2.y); lineTo(p2.x,p2.y+rr); lineTo(p2.x-rr,p2.y); close() }
        drawPath(diamond, PlayerTwoColor)
        ghost?.let { p ->
            if (turn == 0) drawCircle(PlayerOneColor.copy(alpha=.88f), cell*.28f, p)
            else {
                val gr = cell*.28f
                val gp = Path().apply { moveTo(p.x,p.y-gr); lineTo(p.x+gr,p.y); lineTo(p.x,p.y+gr); lineTo(p.x-gr,p.y); close() }
                drawPath(gp, PlayerTwoColor.copy(alpha=.88f))
            }
            drawCircle(if (releaseValid) LadderColor else Color.White, cell*.31f, p, style=Stroke(cell*.05f))
        }
    }
    }
}

private fun snakeSquareCenter(square: Int, boardWidth: Float): Offset {
    val safe = square.coerceIn(1,100)
    val zero = safe - 1
    val rowFromBottom = zero / 10
    val inRow = zero % 10
    val col = if (rowFromBottom % 2 == 0) inRow else 9 - inRow
    val row = 9 - rowFromBottom
    val cell = boardWidth / 10f
    return Offset((col + .5f) * cell, (row + .5f) * cell)
}

@Composable
private fun ChanceStyleD6(finalValue: Int, frame: Int, rolling: Boolean, dieSize: Dp = 92.dp) {
    val line = MaterialTheme.colorScheme.onSurface
    val face = if (rolling) ((frame * 37 + 11) % 6) + 1 else finalValue.coerceIn(1,6)
    val cube = remember { normalizedCubeVertices() }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp, modifier = Modifier.size(dieSize)) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val centre = Offset(size.width/2f, size.height/2f)
                drawOval(line.copy(alpha=.10f), topLeft=Offset(size.width*.18f,size.height*.78f), size=Size(size.width*.64f,size.height*.11f))
                val phase = if (rolling) frame * .24 else 3.1
                val projected = projectCube(cube, phase, phase*.73 + .6, phase*.41, centre, size.minDimension*.39f)
                cubeEdges().forEach { edge ->
                    val a=projected[edge.first]; val b=projected[edge.second]
                    drawLine(line.copy(alpha=.72f), a, b, if (rolling) 3.2f else 2.6f)
                }
            }
            Text(face.toString(), style=MaterialTheme.typography.displayMedium, fontWeight=FontWeight.Black)
        }
    }
}

private data class GDV3(val x: Double, val y: Double, val z: Double)
private fun normalizedCubeVertices(): List<GDV3> {
    val raw = mutableListOf<GDV3>()
    listOf(-1.0,1.0).forEach { x -> listOf(-1.0,1.0).forEach { y -> listOf(-1.0,1.0).forEach { z -> raw += GDV3(x,y,z) } } }
    val radius = sqrt(3.0)
    return raw.map { GDV3(it.x/radius,it.y/radius,it.z/radius) }
}
private fun cubeEdges(): List<Pair<Int,Int>> = listOf(0 to 1,0 to 2,0 to 4,1 to 3,1 to 5,2 to 3,2 to 6,3 to 7,4 to 5,4 to 6,5 to 7,6 to 7)
private fun projectCube(vertices: List<GDV3>, rx: Double, ry: Double, rz: Double, centre: Offset, scale: Float): List<Offset> = vertices.map { original ->
    var x=original.x; var y=original.y; var z=original.z
    val y1=y*cos(rx)-z*sin(rx); val z1=y*sin(rx)+z*cos(rx); y=y1; z=z1
    val x2=x*cos(ry)+z*sin(ry); val z2=-x*sin(ry)+z*cos(ry); x=x2; z=z2
    val x3=x*cos(rz)-y*sin(rz); val y3=x*sin(rz)+y*cos(rz); x=x3; y=y3
    val perspective=1.0/(1.0+(z+2.8)*.10)
    Offset(centre.x+(x*perspective*scale).toFloat(), centre.y+(y*perspective*scale).toFloat())
}

@Composable
private fun MancalaGame(
    stateJson: String,
    cpu: Boolean,
    onCpuChanged: (Boolean) -> Unit,
    onPit: (Int) -> Unit,
    onCpuPit: (Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val pits = state.getJSONArray("pits")
    val turn = state.optInt("turn",0)
    val terminal = state.optString("winner").isNotBlank()
    var cpuThinking by remember(stateJson) { mutableStateOf(false) }
    var cpuChoice by remember(stateJson) { mutableStateOf(-1) }
    var lastCpuChoice by rememberSaveable { mutableStateOf(-1) }

    LaunchedEffect(stateJson, cpu) {
        if (cpu && !terminal && turn == 1) {
            val choice = GameDeckEngine.mancalaCpuChoice(stateJson)
            if (choice >= 7) {
                cpuChoice = choice; cpuThinking = true
                delay(850L)
                lastCpuChoice = choice
                onCpuPit(choice)
                cpuThinking = false; cpuChoice = -1
            }
        }
    }

    val topModeControl: @Composable () -> Unit = {
        OutlinedButton(
            onClick = { onCpuChanged(!cpu) },
            enabled = state.optInt("moves", 0) == 0
        ) {
            Text(if (cpu) "MODE: CPU" else "MODE: 2P", fontWeight = FontWeight.Black)
        }
    }

    TabletopFrame(
        topLabel = if (cpu) "CPU" else "PLAYER 2",
        topActive = turn == 1 && !terminal,
        topDetail = when {
            terminal -> "Store ${pits.optInt(13)}"
            turn == 1 && cpuThinking -> "THINKING…  •  store ${pits.optInt(13)}"
            turn == 1 && cpu -> "CPU TURN  •  store ${pits.optInt(13)}"
            turn == 1 -> "YOUR TURN  •  store ${pits.optInt(13)}"
            else -> "Store ${pits.optInt(13)}"
        },
        topIsHuman = !cpu,
        bottomLabel = if (cpu) "YOU" else "PLAYER 1",
        bottomActive = turn == 0 && !terminal,
        bottomDetail = if (turn == 0 && !terminal) "YOUR TURN  •  store ${pits.optInt(6)}" else "Store ${pits.optInt(6)}",
        topControls = topModeControl
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            if (cpuThinking) {
                Surface(
                    shape=RoundedCornerShape(18.dp),
                    color=PlayerTwoColor.copy(alpha=.22f),
                    modifier=Modifier.fillMaxWidth()
                ) {
                    Text(
                        "CPU is choosing a pit…",
                        Modifier.padding(10.dp),
                        textAlign=TextAlign.Center,
                        fontWeight=FontWeight.Black
                    )
                }
            } else if (lastCpuChoice >= 7 && turn == 0 && !terminal) {
                Text(
                    "CPU moved. Your turn.",
                    modifier=Modifier.fillMaxWidth(),
                    textAlign=TextAlign.Center,
                    fontWeight=FontWeight.Bold
                )
            }

            Surface(
                shape=RoundedCornerShape(30.dp),
                color=Color(0xFFB77A4A).copy(alpha=.28f),
                modifier=Modifier.fillMaxWidth(),
                tonalElevation=3.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal=5.dp, vertical=10.dp),
                    horizontalArrangement=Arrangement.SpaceEvenly,
                    verticalAlignment=Alignment.CenterVertically
                ) {
                    MancalaSideStoreVisual(
                        stones=pits.optInt(13),
                        label=if(cpu) "CPU" else "P2",
                        color=PlayerTwoColor
                    )

                    Column(
                        horizontalAlignment=Alignment.CenterHorizontally,
                        verticalArrangement=Arrangement.spacedBy(9.dp)
                    ) {
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            (12 downTo 7).forEach { pit ->
                                MancalaCompactPit(
                                    stones=pits.optInt(pit),
                                    label=(13-pit).toString(),
                                    enabled=!cpu && turn==1 && !terminal,
                                    highlighted=cpuChoice==pit,
                                    color=PlayerTwoColor
                                ) { onPit(pit) }
                            }
                        }
                        Row(horizontalArrangement=Arrangement.spacedBy(3.dp)) {
                            (0..5).forEach { pit ->
                                MancalaCompactPit(
                                    stones=pits.optInt(pit),
                                    label=(pit+1).toString(),
                                    enabled=turn==0 && !terminal,
                                    highlighted=false,
                                    color=PlayerOneColor
                                ) { onPit(pit) }
                            }
                        }
                    }

                    MancalaSideStoreVisual(
                        stones=pits.optInt(6),
                        label=if(cpu) "YOU" else "P1",
                        color=PlayerOneColor
                    )
                }
            }

            Text(
                when {
                    terminal -> "Game complete."
                    turn == 0 -> "Your row is the lower row. Tap any non-empty pit."
                    !cpu -> "Player 2 uses the upper row."
                    else -> "CPU uses the upper row."
                },
                textAlign=TextAlign.Center,
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold,
                modifier=Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MancalaCompactPit(
    stones: Int,
    label: String,
    enabled: Boolean,
    highlighted: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(
            label,
            style=MaterialTheme.typography.labelSmall,
            fontWeight=FontWeight.Bold,
            color=if(highlighted) color else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            onClick=onClick,
            enabled=enabled && stones>0,
            modifier=Modifier.size(38.dp),
            shape=CircleShape,
            color=if(highlighted) color.copy(alpha=.38f)
                else Color(0xFF70442D).copy(alpha=.30f),
            tonalElevation=if(highlighted)7.dp else 2.dp
        ) {
            BeanPile(stones=stones)
        }
    }
}

@Composable
private fun MancalaSideStoreVisual(
    stones: Int,
    label: String,
    color: Color
) {
    Column(horizontalAlignment=Alignment.CenterHorizontally) {
        Text(label, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Black)
        Surface(
            modifier=Modifier.size(width=42.dp, height=112.dp),
            shape=RoundedCornerShape(30.dp),
            color=color.copy(alpha=.18f),
            tonalElevation=3.dp
        ) {
            Box(contentAlignment=Alignment.Center) {
                BeanPile(stones=stones, store=true)
                Surface(
                    shape=CircleShape,
                    color=MaterialTheme.colorScheme.surface.copy(alpha=.82f)
                ) {
                    Text(
                        stones.toString(),
                        Modifier.padding(horizontal=7.dp, vertical=3.dp),
                        fontWeight=FontWeight.Black
                    )
                }
            }
        }
    }
}

@Composable
private fun BeanPile(stones: Int, store: Boolean=false) {
    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
        val shown=stones.coerceAtMost(if(store) 24 else 12)
        repeat(shown) { i ->
            val cols=if(store) 8 else 4
            val rows=if(store) 3 else 3
            val c=i%cols; val r=(i/cols)%rows
            val x=(c+.5f)*(size.width/cols) + (((i*7)%5)-2)*1.2f
            val y=(r+.5f)*(size.height/rows) + (((i*11)%5)-2)*1.1f
            val beanW=if(store) size.width*.075f else size.width*.17f
            val beanH=if(store) size.height*.25f else size.height*.17f
            drawOval(BeanColor, topLeft=Offset(x-beanW/2f,y-beanH/2f), size=Size(beanW,beanH))
            drawArc(BeanLight, 190f, 110f, false, topLeft=Offset(x-beanW*.34f,y-beanH*.30f), size=Size(beanW*.68f,beanH*.60f), style=Stroke(1.5f))
        }
        if(stones>shown) {
            drawCircle(Color.Black.copy(alpha=.12f), radius=size.minDimension*.12f, center=Offset(size.width*.86f,size.height*.18f))
        }
    }
}

@Composable
private fun MinesweeperGame(
    stateJson: String,
    rngMode: String,
    onPreset: (Int, Int, Int) -> Unit,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val metrics = GameDeckEngine.minesweeperMetrics(stateJson)
    val flags = state.optJSONArray("flags")?.length() ?: 0
    val revealed = state.optJSONArray("revealed")?.length() ?: 0
    val safeTotal = metrics.width * metrics.height - metrics.mineCount
    var flagMode by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        val compact = availableHeight < 560.dp
        val controlsReserve = if (compact) 170.dp else 285.dp
        val boardSide = minOf(
            maxWidth,
            (availableHeight - controlsReserve).coerceAtLeast(150.dp)
        )

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)
        ) {
            if (!compact) {
                Text(
                    "MINESWEEPER",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MinesweeperPresetButton(
                    label = "CALM",
                    selected = metrics.width == 8 && metrics.height == 8 && metrics.mineCount == 8,
                    onClick = { flagMode = false; onPreset(8, 8, 8) }
                )
                MinesweeperPresetButton(
                    label = "CLASSIC",
                    selected = metrics.width == 9 && metrics.height == 9 && metrics.mineCount == 10,
                    onClick = { flagMode = false; onPreset(9, 9, 10) }
                )
                MinesweeperPresetButton(
                    label = "DENSE",
                    selected = metrics.width == 12 && metrics.height == 12 && metrics.mineCount == 22,
                    onClick = { flagMode = false; onPreset(12, 12, 22) }
                )
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (flagMode) MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(
                        horizontal = 12.dp,
                        vertical = if (compact) 5.dp else 8.dp
                    ),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (flagMode) "FLAG MODE" else "REVEAL MODE",
                            fontWeight = FontWeight.Black
                        )
                        if (!compact) {
                            Text(
                                if (flagMode) "Tap squares to add or remove flags."
                                else "Tap a square to reveal it.",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    OutlinedButton(onClick = { flagMode = !flagMode }) {
                        Text(if (flagMode) "REVEAL" else "FLAG")
                    }
                }
            }

            Box(
                Modifier.size(boardSide),
                contentAlignment = Alignment.Center
            ) {
                MinesweeperBoard(
                    stateJson = stateJson,
                    flagMode = flagMode,
                    onReveal = onReveal,
                    onFlag = onFlag
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(if (compact) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Safe ${revealed.coerceAtMost(safeTotal)} / $safeTotal",
                            fontWeight = FontWeight.Black
                        )
                        Text("Flags $flags / ${metrics.mineCount}", fontWeight = FontWeight.Black)
                    }
                    if (!compact) {
                        Text(
                            when {
                                !state.optBoolean("generated") ->
                                    "First reveal fixes the opening; GameLab then seeks a no-guess board."
                                metrics.noGuessValidated ->
                                    "No-guess board validated  •  ${metrics.generationAttempts} generator attempts"
                                else ->
                                    "Fallback board  •  solver could not validate a no-guess route"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (state.optBoolean("generated")) {
                            Text(
                                "${metrics.width}×${metrics.height}  •  ${(metrics.density * 100).toInt()}% mines  •  RNG $rngMode",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }

            if (!compact) {
                Text(
                    "Long-press always toggles a flag.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MinesweeperPresetButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(onClick = onClick) { Text(label, fontWeight = FontWeight.Black) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun MinesweeperBoard(
    stateJson: String,
    flagMode: Boolean,
    onReveal: (Int, Int) -> Unit,
    onFlag: (Int, Int) -> Unit
) {
    val state = JSONObject(stateJson)
    val w = state.optInt("width", 9)
    val h = state.optInt("height", 9)
    val terminal = state.optString("winner").isNotBlank()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellSize = maxWidth / w
        Column {
            repeat(h) { y ->
                Row {
                    repeat(w) { x ->
                        val cell = GameDeckEngine.minesweeperCell(stateJson, x, y)
                        val bg = when {
                            cell.revealed -> MaterialTheme.colorScheme.surfaceVariant
                            cell.flagged -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                        Surface(
                            modifier = Modifier
                                .size(cellSize)
                                .padding(1.dp)
                                .pointerInput(stateJson, x, y, flagMode, terminal) {
                                    if (!terminal) {
                                        detectTapGestures(
                                            onTap = {
                                                if (flagMode) onFlag(x, y) else onReveal(x, y)
                                            },
                                            onLongPress = { onFlag(x, y) }
                                        )
                                    }
                                },
                            shape = RoundedCornerShape(7.dp),
                            color = bg,
                            tonalElevation = if (cell.revealed) 0.dp else 3.dp
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val label = when {
                                    cell.flagged && !cell.revealed -> "⚑"
                                    cell.revealed && cell.mine -> "✹"
                                    cell.revealed && cell.adjacent > 0 -> cell.adjacent.toString()
                                    else -> ""
                                }
                                if (label.isNotBlank()) {
                                    Text(
                                        label,
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun winnerTitle(winner: String, session: GameDeckSession): String {
    if (winner == "draw") return "DRAW"
    val seatNumber = winner.toIntOrNull() ?: return winner.uppercase()
    val seat = session.seats.firstOrNull { it.seat == seatNumber }
    val soleHuman = GameDeckSessions.trackedHumanSeat(session)
    return when {
        seat?.kind == GameDeckSeatKind.CPU -> "CPU WINS!"
        soleHuman == seatNumber -> "YOU WIN!"
        else -> "PLAYER $seatNumber WINS!"
    }
}

@Composable
private fun GameResultPanel(
    stateJson: String,
    session: GameDeckSession,
    onAgain: () -> Unit,
    onGames: () -> Unit,
    onDone: (() -> Unit)?
) {
    val state=JSONObject(stateJson)
    val winner=state.optString("winner")
    if(winner.isBlank()) return
    val game=state.optString("game")
    val title: String
    val score: String
    when(game) {
        GameDeckEngine.CONNECT_FOUR -> {
            val board=state.getJSONArray("board")
            var p1=0; var p2=0
            for(r in 0 until board.length()) for(c in 0 until board.getJSONArray(r).length()) {
                when(board.getJSONArray(r).optInt(c)) { 1->p1++; 2->p2++ }
            }
            title=if(winner=="draw") "DRAW" else "PLAYER $winner WINS!"
            score="Counters played  •  P1 $p1 : $p2 P2"
        }
        GameDeckEngine.SNAKES_AND_LADDERS -> {
            val p=state.getJSONArray("positions")
            title="PLAYER $winner WINS!"
            score="Final positions  •  P1 ${p.optInt(0)} : ${p.optInt(1)} P2"
        }
        GameDeckEngine.MANCALA -> {
            val pits=state.getJSONArray("pits")
            title=winnerTitle(winner, session)
            score=if (GameDeckSessions.trackedHumanSeat(session) == 1) {
                "Final score  •  You ${pits.optInt(6)} : ${pits.optInt(13)} CPU"
            } else {
                "Final score  •  P1 ${pits.optInt(6)} : ${pits.optInt(13)} P2"
            }
        }
        GameDeckEngine.MINESWEEPER -> {
            val w=state.optInt("width",9); val h=state.optInt("height",9); val mines=state.optInt("mine_count",10)
            val revealed=state.optJSONArray("revealed")?.length() ?: 0
            title=if(winner=="cleared") "BOARD CLEARED!" else "BOOM — MINE HIT"
            score="Safe cells revealed  •  ${revealed.coerceAtMost(w*h-mines)} / ${w*h-mines}"
        }
        GameDeckExtraEngine.CODEBREAKER -> {
            val secret=state.getJSONArray("secret")
            title=if(winner=="cleared") "CODE CRACKED!" else "OUT OF GUESSES"
            score="Code  •  ${(0 until secret.length()).joinToString(" ") { secret.optInt(it).toString() }}"
        }
        else -> {
            title = when {
                winner == "draw" -> "DRAW"
                winner == "cleared" -> "CLEARED!"
                winner == "failed" && game == GameDeckExtraEngine.CODEBREAKER -> "OUT OF GUESSES"
                winner == "failed" -> "RUN COMPLETE"
                winner == "stuck" -> "NO MOVE — RUN OVER"
                winner == "1" || winner == "2" -> winnerTitle(winner, session)
                else -> winner.uppercase()
            }
            score = GameDeckExtraEngine.resultDetail(stateJson)
        }
    }
    Surface(
        shape=RoundedCornerShape(26.dp),
        color=MaterialTheme.colorScheme.primaryContainer,
        tonalElevation=8.dp,
        shadowElevation=8.dp,
        modifier=Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment=Alignment.CenterHorizontally,
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style=MaterialTheme.typography.headlineSmall,
                fontWeight=FontWeight.Black,
                textAlign=TextAlign.Center
            )
            Text(
                score,
                style=MaterialTheme.typography.titleMedium,
                fontWeight=FontWeight.Bold,
                textAlign=TextAlign.Center
            )
            Row(
                horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalAlignment=Alignment.CenterVertically
            ) {
                Button(onClick=onAgain) { Text("AGAIN") }
                OutlinedButton(onClick=onGames) { Text("GAMES") }
                if(onDone!=null) Button(onClick=onDone) { Text("DONE") }
            }
        }
    }
}
