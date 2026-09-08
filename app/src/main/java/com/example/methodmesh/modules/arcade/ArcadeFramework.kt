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

/** Granular Snake movement-rate control. */
object ArcadeSnakeSpeed {
    const val MIN_CPS = 3
    const val MAX_CPS = 16
    const val DEFAULT_CPS = 6

    fun normalizeCps(value: Int): Int = value.coerceIn(MIN_CPS, MAX_CPS)

    /** Backward compatibility for v0.03 preset strings. */
    fun fromLegacy(value: String?): Int = when (value?.lowercase()) {
        "relaxed" -> 4
        "normal" -> 6
        "fast" -> 8
        "turbo" -> 11
        else -> value?.toIntOrNull()?.let(::normalizeCps) ?: DEFAULT_CPS
    }

    /**
     * Selected speed is the baseline. Long runs accelerate slowly: one extra
     * cell/second for each five foods, capped so the game stays steerable.
     */
    fun effectiveCps(baseCps: Int, score: Int = 0): Int =
        (normalizeCps(baseCps) + score.coerceAtLeast(0) / 50)
            .coerceAtMost(20)

    fun delayMs(baseCps: Int, score: Int = 0): Long =
        (1000.0 / effectiveCps(baseCps, score).toDouble())
            .toLong()
            .coerceAtLeast(50L)
}
