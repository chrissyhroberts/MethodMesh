package com.example.methodmesh.modules.arcade

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.util.UUID

/** Arcade v0.035: fixed-step real-time games with GameDeck-aligned UX. */
object ArcadeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ArcadeMethod.ID
    override val title = "Arcade"
    override val description = "Lightweight real-time games on a fixed-step local-first runtime."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var game by rememberSaveable {
            mutableStateOf(
                context.action.settings["game"]
                    ?: context.action.settings["input_game"]
                    ?: ArcadeEngine.LAUNCHER
            )
        }
        var rngMode by rememberSaveable {
            mutableStateOf(
                context.action.settings["rng_mode"]
                    ?: context.action.settings["input_rng_mode"]
                    ?: "secure_random"
            )
        }
        var seed by rememberSaveable {
            mutableStateOf(
                context.action.settings["seed"]
                    ?: context.action.settings["input_seed"]
                    ?: ""
            )
        }
        var snakeSpeedCps by rememberSaveable {
            mutableStateOf(
                ArcadeSnakeSpeed.normalizeCps(
                    (context.action.settings["snake_speed_cps"]
                        ?: context.action.settings["input_snake_speed_cps"])
                        ?.toIntOrNull()
                        ?: ArcadeSnakeSpeed.fromLegacy(
                            context.action.settings["snake_speed"]
                                ?: context.action.settings["input_snake_speed"]
                        )
                )
            )
        }
        var pongCpuDifficulty by rememberSaveable {
            mutableStateOf(
                (
                    context.action.settings["pong_cpu_difficulty"]
                        ?: context.action.settings["input_pong_cpu_difficulty"]
                        ?: "standard"
                ).lowercase().takeIf { it in setOf("casual", "standard", "sharp") } ?: "standard"
            )
        }
        var breakoutStartLevel by rememberSaveable {
            mutableStateOf(
                (
                    context.action.settings["breakout_start_level"]
                        ?: context.action.settings["input_breakout_start_level"]
                )?.toIntOrNull()?.coerceIn(1, 5) ?: 1
            )
        }
        var dodgeDifficulty by rememberSaveable {
            mutableStateOf(
                (
                    context.action.settings["dodge_difficulty"]
                        ?: context.action.settings["input_dodge_difficulty"]
                        ?: "normal"
                ).lowercase().takeIf { it in setOf("easy", "normal", "hard") } ?: "normal"
            )
        }

        var soundEnabled by rememberSaveable {
            mutableStateOf(
                (
                    context.action.settings["sound"]
                        ?: context.action.settings["input_sound"]
                        ?: "true"
                    ).equals("true", ignoreCase = true)
            )
        }
        var stateJson by rememberSaveable(game) {
            mutableStateOf(
                ArcadeEngine.newState(
                    game = game,
                    rngMode = rngMode,
                    seed = seed,
                    snakeSpeedCps = snakeSpeedCps,
                    pongCpuDifficulty = pongCpuDifficulty,
                    breakoutStartLevel = breakoutStartLevel,
                    dodgeDifficulty = dodgeDifficulty
                )
            )
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var menuOpen by rememberSaveable { mutableStateOf(false) }
        var helpOpen by rememberSaveable(game) { mutableStateOf(false) }
        var sessionId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var returnedSessionId by rememberSaveable { mutableStateOf("") }

        val appContext = LocalContext.current.applicationContext
        val records = remember(appContext) { ArcadeRecordStore(appContext) }
        val externalInteractive =
            context.submitsImmediately &&
                !context.request.source.equals("intent_test", ignoreCase = true)

        fun buildResult(): ExecutionResult {
            val values = As100ArcadeMethod.snapshot(
                game = game,
                stateJson = stateJson,
                sessionId = sessionId,
                rngMode = rngMode,
                playerStatsJson = records.toJson()
            )
            val safeSettings = context.action.settings.filterKeys {
                it !in setOf("state_json", "input_state_json", "seed", "input_seed")
            }
            val request = As100ArcadeMethod.request(
                action = As100ArcadeMethod.ID,
                context = context.request.invocationContext.asMap(As100ArcadeMethod.ID) +
                    safeSettings +
                    mapOf(
                        "game" to game,
                        "state_json" to stateJson,
                        "session_id" to sessionId,
                        "rng_mode" to rngMode,
                        "snake_speed_cps" to snakeSpeedCps.toString(),
                        "pong_cpu_difficulty" to pongCpuDifficulty,
                        "breakout_start_level" to breakoutStartLevel.toString(),
                        "dodge_difficulty" to dodgeDifficulty
                    ),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100ArcadeMethod.result(
                request,
                values,
                context.request.invocationContext
            )
        }

        fun refreshSnapshot() {
            result = buildResult()
        }

        fun openGame(selected: String) {
            sessionId = UUID.randomUUID().toString()
            returnedSessionId = ""
            game = selected
            stateJson = ArcadeEngine.newState(
                game = selected,
                rngMode = rngMode,
                seed = seed,
                snakeSpeedCps = snakeSpeedCps,
                pongCpuDifficulty = pongCpuDifficulty,
                breakoutStartLevel = breakoutStartLevel,
                dodgeDifficulty = dodgeDifficulty
            )
            result = null
            menuOpen = false
            helpOpen = false
        }

        fun reset() {
            val previousPongCpu = if (game == ArcadeEngine.PONG) {
                runCatching { JSONObject(stateJson).optBoolean("cpu", true) }.getOrDefault(true)
            } else {
                true
            }

            sessionId = UUID.randomUUID().toString()
            returnedSessionId = ""
            stateJson = ArcadeEngine.newState(
                game = game,
                rngMode = rngMode,
                seed = seed,
                snakeSpeedCps = snakeSpeedCps,
                pongCpuDifficulty = pongCpuDifficulty,
                breakoutStartLevel = breakoutStartLevel,
                dodgeDifficulty = dodgeDifficulty
            )

            if (game == ArcadeEngine.PONG) {
                stateJson = ArcadeEngine.pongSetCpu(stateJson, previousPongCpu)
            }

            menuOpen = false
            helpOpen = false
            refreshSnapshot()
        }

        fun home() {
            openGame(ArcadeEngine.LAUNCHER)
        }

        val live =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun

        LaunchedEffect(
            game,
            rngMode,
            seed,
            snakeSpeedCps,
            pongCpuDifficulty,
            breakoutStartLevel,
            dodgeDifficulty,
            soundEnabled
        ) {
            context.onSettingsChanged(
                mapOf(
                    "game" to game,
                    "rng_mode" to rngMode,
                    "seed" to seed,
                    "snake_speed_cps" to snakeSpeedCps.toString(),
                    "pong_cpu_difficulty" to pongCpuDifficulty,
                    "breakout_start_level" to breakoutStartLevel.toString(),
                    "dodge_difficulty" to dodgeDifficulty,
                    "sound" to soundEnabled.toString()
                )
            )
        }

        LaunchedEffect(
            context.presentationMode,
            context.submitsImmediately,
            context.request.source
        ) {
            if (!launched) {
                launched = true

                // Interactive external callers (ODK/Kobo/etc.) are requests to
                // PLAY, not requests for the initial snapshot. Never complete the
                // workflow merely because the capability screen opened.
                if (!externalInteractive && game != ArcadeEngine.LAUNCHER) {
                    refreshSnapshot()
                }
            }
        }

        val state = runCatching { JSONObject(stateJson) }.getOrElse { JSONObject() }
        val summary = ArcadeEngine.summary(stateJson)
        val scoreEvent = when (game) {
            ArcadeEngine.PONG ->
                state.optInt("p1_score", 0) + state.optInt("p2_score", 0)
            ArcadeEngine.SNAKE -> state.optInt("score", 0) / 10
            ArcadeEngine.BREAKOUT -> state.optInt("score", 0) / 10
            ArcadeEngine.DODGE -> state.optInt("score", 0)
            else -> 0
        }

        LaunchedEffect(game, scoreEvent, summary.finished, soundEnabled) {
            when {
                summary.finished -> ArcadeSound.gameOver(soundEnabled)
                scoreEvent > 0 -> ArcadeSound.score(soundEnabled)
            }
        }

        LaunchedEffect(sessionId, summary.finished, externalInteractive) {
            if (summary.finished) {
                // Persist the just-finished session first so the player-stats
                // payload returned to ODK includes this game.
                records.record(sessionId, stateJson)

                if (externalInteractive && returnedSessionId != sessionId) {
                    returnedSessionId = sessionId
                    val finalResult = buildResult()
                    result = finalResult

                    // Let the terminal frame/result overlay render before Android
                    // returns control to the external form.
                    delay(450)
                    onConfirmed(finalResult)
                }
            }
        }

        val paused = menuOpen || helpOpen

        val content: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 9.dp, vertical = 8.dp)
            ) {
                when (game) {
                    ArcadeEngine.LAUNCHER -> ArcadeLauncher(
                        records = runCatching {
                            records.snapshot()
                        }.getOrElse {
                            ArcadeRecordSnapshot(
                                totalGames = 0,
                                snakeGames = 0,
                                snakeBestScore = 0,
                                snakeLongest = 0,
                                pongCpuWins = 0,
                                pongCpuLosses = 0,
                                pongSharedMatches = 0,
                                breakoutBestScore = 0,
                                breakoutClears = 0,
                                dodgeBestScore = 0
                            )
                        },
                        onOpen = ::openGame,
                        onExit = onCancel
                    )

                    ArcadeEngine.SNAKE -> SnakeArcade(
                        stateJson = stateJson,
                        paused = paused,
                        bestScore = runCatching {
                            records.snapshot().snakeBestScore
                        }.getOrDefault(0),
                        onStep = {
                            stateJson = ArcadeEngine.snakeStep(
                                stateJson,
                                rngMode,
                                seed
                            )
                        },
                        onTurn = { direction ->
                            val next = ArcadeEngine.snakeTurn(stateJson, direction)
                            if (next != stateJson) ArcadeSound.turn(soundEnabled)
                            stateJson = next
                        },
                        onSpeedChange = { cps ->
                            val normalized = ArcadeSnakeSpeed.normalizeCps(cps)
                            snakeSpeedCps = normalized
                            stateJson = ArcadeEngine.snakeSetSpeed(stateJson, normalized)
                        },
                        onStart = {
                            ArcadeSound.turn(soundEnabled)
                            stateJson = ArcadeEngine.snakeStart(stateJson)
                        }
                    )

                    ArcadeEngine.PONG -> PongArcade(
                        stateJson = stateJson,
                        paused = paused,
                        onStep = {
                            stateJson = ArcadeEngine.pongStep(stateJson)
                        },
                        onPaddle = { player, x ->
                            stateJson = ArcadeEngine.pongPaddle(
                                stateJson,
                                player,
                                x
                            )
                        },
                        onMode = { cpu ->
                            stateJson = ArcadeEngine.pongSetCpu(stateJson, cpu)
                            stateJson = ArcadeEngine.pongSetCpuDifficulty(
                                stateJson,
                                pongCpuDifficulty
                            )
                        },
                        onStart = {
                            ArcadeSound.turn(soundEnabled)
                            stateJson = ArcadeEngine.pongStart(stateJson)
                        }
                    )

                    ArcadeEngine.BREAKOUT -> BreakoutArcade(
                        stateJson = stateJson,
                        paused = paused,
                        onStep = {
                            stateJson = ArcadeEngine.breakoutStep(stateJson)
                        },
                        onPaddle = { x ->
                            stateJson = ArcadeEngine.breakoutPaddle(stateJson, x)
                        },
                        onStart = {
                            ArcadeSound.turn(soundEnabled)
                            stateJson = ArcadeEngine.breakoutStart(stateJson)
                        }
                    )

                    ArcadeEngine.DODGE -> LaneDodgeArcade(
                        stateJson = stateJson,
                        paused = paused,
                        onStep = {
                            stateJson = ArcadeEngine.dodgeStep(
                                stateJson,
                                rngMode,
                                seed
                            )
                        },
                        onLane = { lane ->
                            stateJson = ArcadeEngine.dodgeLane(stateJson, lane)
                        },
                        onStart = {
                            ArcadeSound.turn(soundEnabled)
                            stateJson = ArcadeEngine.dodgeStart(stateJson)
                        }
                    )
                }

                if (game != ArcadeEngine.LAUNCHER) {
                    ArcadeMenu(
                        open = menuOpen,
                        soundEnabled = soundEnabled,
                        onToggle = { menuOpen = !menuOpen },
                        onHelp = {
                            menuOpen = false
                            helpOpen = true
                        },
                        onSoundToggle = { soundEnabled = !soundEnabled },
                        onRestart = {
                            menuOpen = false
                            reset()
                        },
                        onGames = {
                            menuOpen = false
                            home()
                        },
                        onDone = if (live) ({
                            menuOpen = false
                            onConfirmed(buildResult())
                        }) else null
                    )
                }

                if (summary.finished && game != ArcadeEngine.LAUNCHER) {
                    Surface(
                        onClick = {},
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = .38f)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ArcadeResultPanel(
                                stateJson = stateJson,
                                onAgain = { reset() },
                                onGames = { home() },
                                onDone = if (live) ({
                                    onConfirmed(buildResult())
                                }) else null
                            )
                        }
                    }
                }

                if (helpOpen) {
                    Surface(
                        onClick = {},
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = .40f)
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 22.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            ArcadeHelpOverlay(
                                game = game,
                                onDismiss = { helpOpen = false }
                            )
                        }
                    }
                }
            }
        }

        if (live) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                content()
            }
        } else {
            CapabilityScreenScaffold(
                title = title,
                capabilityId = capabilityId,
                context = context,
                canGoBack = context.stepNumber > 1,
                capturedResult = if (externalInteractive) null else result,
                resultPreview = mapOf(
                    ArcadeFields.RESULT to summary.detail,
                    ArcadeFields.SCORE to summary.score.toString()
                ),
                onBack = onBack,
                onRetry = { reset() },
                onConfirm = {
                    if (!externalInteractive) onConfirmed(buildResult())
                },
                onCancel = onCancel
            ) {
                content()
            }
        }
    }
}

