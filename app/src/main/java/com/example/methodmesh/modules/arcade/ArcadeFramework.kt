package com.example.methodmesh.modules.arcade

/** Input abstraction for future controller / P2P adapters. */
data class ArcadeInput(
    val x: Float = 0f,
    val y: Float = 0f,
    val action: Boolean = false
)

data class ArcadeFrameMeta(
    val tick: Long,
    val elapsedMs: Long,
    val score: Int,
    val lives: Int,
    val finished: Boolean
)

data class ArcadeSummary(
    val game: String,
    val score: Int,
    val tick: Long,
    val finished: Boolean,
    val winner: String,
    val detail: String
)

/**
 * Generic real-time game contract.
 *
 * Rendering is intentionally separate from simulation. The rules state owns the
 * result; animation/render cadence never decides game outcomes.
 */
interface ArcadeGameSpec<S> {
    val id: String
    fun initial(seed: String = ""): S
    fun step(state: S, input: ArcadeInput, dtSeconds: Float): S
    fun frameMeta(state: S): ArcadeFrameMeta
}

interface ArcadeCpuController<S> {
    fun input(state: S): ArcadeInput
}

/**
 * Fixed-step constants. A delayed frame can make the game appear slower, but it
 * does not change the size of an authoritative simulation step.
 */
object ArcadeFixedStep {
    const val STEP_MS = 16L
    const val STEP_SECONDS = 0.016f
}

/** Human-readable Snake difficulty/speed presets. */
object ArcadeSnakeSpeed {
    const val RELAXED = "relaxed"
    const val NORMAL = "normal"
    const val FAST = "fast"
    const val TURBO = "turbo"

    val modes = listOf(RELAXED, NORMAL, FAST, TURBO)

    fun normalize(value: String): String =
        value.lowercase().takeIf { it in modes } ?: RELAXED

    fun delayMs(value: String, score: Int = 0): Long {
        val base = when (normalize(value)) {
            NORMAL -> 175L
            FAST -> 130L
            TURBO -> 95L
            else -> 235L
        }
        // Every three foods trims 12 ms from the movement step. The floor keeps
        // late-game Snake fast without making touch control physically absurd.
        val acceleration = (score.coerceAtLeast(0) / 30) * 12L
        return (base - acceleration).coerceAtLeast(70L)
    }

    fun label(value: String): String = when (normalize(value)) {
        NORMAL -> "Normal"
        FAST -> "Fast"
        TURBO -> "Turbo"
        else -> "Relaxed"
    }
}
