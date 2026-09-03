package com.example.methodmesh.modules.psychomotorvigilance

import kotlin.math.ceil

/** Published PVT configurations implemented by this capability. */
data class PvtProtocol(
    val key: String,
    val displayName: String,
    val taskDurationMs: Long,
    val minIsiMs: Long,
    val maxIsiMs: Long,
    val lapseThresholdMs: Long,
    val falseStartThresholdMs: Long = 100L,
    val responseTimeoutMs: Long = 30_000L,
    val feedbackDurationMs: Long = 1_000L
) {
    companion object {
        val STANDARD_10 = PvtProtocol(
            key = "pvt_10_standard",
            displayName = "Standard PVT — 10 minutes",
            taskDurationMs = 600_000L,
            minIsiMs = 2_000L,
            maxIsiMs = 10_000L,
            lapseThresholdMs = 500L
        )

        val BRIEF_3 = PvtProtocol(
            key = "pvt_b_3",
            displayName = "PVT-B — 3 minutes",
            taskDurationMs = 180_000L,
            minIsiMs = 1_000L,
            maxIsiMs = 4_000L,
            lapseThresholdMs = 355L
        )

        val all = listOf(STANDARD_10, BRIEF_3)

        fun fromKey(raw: String?): PvtProtocol =
            all.firstOrNull { it.key == raw } ?: STANDARD_10
    }
}

enum class PvtTrialOutcome {
    VALID,
    LAPSE,
    FALSE_START,
    TIMEOUT
}

data class PvtTrial(
    val sequence: Int,
    val isiMs: Long?,
    val stimulusOnsetUptimeMs: Long?,
    val responseUptimeMs: Long?,
    val reactionTimeMs: Long?,
    val outcome: PvtTrialOutcome,
    val stimulusOnsetTimeIso: String? = null
)

data class PvtSession(
    val protocol: PvtProtocol,
    val countdownSeconds: Int,
    val startedTimeIso: String,
    val endedTimeIso: String,
    val startedUptimeMs: Long,
    val endedUptimeMs: Long,
    val trials: List<PvtTrial>,
    val manufacturer: String,
    val model: String,
    val sdkInt: Int,
    val displayRefreshRateHz: Float,
    val screenWidthPx: Int,
    val screenHeightPx: Int,
    val responseModality: String = "touchscreen_anywhere_action_down",
    val timingClock: String = "SystemClock.uptimeMillis",
    val stimulusTimestampMethod: String = "first View.onDraw after stimulus state",
    val responseTimestampMethod: String = "MotionEvent.ACTION_DOWN eventTime"
) {
    val actualDurationMs: Long get() = (endedUptimeMs - startedUptimeMs).coerceAtLeast(0L)
}

data class PvtScores(
    val validResponses: Int,
    val lapses: Int,
    val falseStarts: Int,
    val timeouts: Int,
    val lapsesPlusFalseStarts: Int,
    val responseSpeedPerSecond: Double?,
    val lapseProbability: Double?,
    val performanceScorePercent: Double?,
    val meanRtMs: Double?,
    val medianRtMs: Double?,
    val fastest10PctMeanRtMs: Double?,
    val slowest10PctResponseSpeedPerSecond: Double?
)

object PvtScoring {
    fun score(session: PvtSession): PvtScores {
        val responseTrials = session.trials.filter {
            it.outcome == PvtTrialOutcome.VALID ||
                it.outcome == PvtTrialOutcome.LAPSE ||
                it.outcome == PvtTrialOutcome.TIMEOUT
        }
        val rts = responseTrials.mapNotNull { it.reactionTimeMs?.takeIf { rt -> rt > 0L } }
        val lapses = responseTrials.count {
            it.outcome == PvtTrialOutcome.LAPSE || it.outcome == PvtTrialOutcome.TIMEOUT
        }
        val falseStarts = session.trials.count { it.outcome == PvtTrialOutcome.FALSE_START }
        val timeouts = session.trials.count { it.outcome == PvtTrialOutcome.TIMEOUT }
        val responseSpeed = rts.takeIf { it.isNotEmpty() }?.map { 1000.0 / it.toDouble() }?.average()
        val lapseProbability = responseTrials.takeIf { it.isNotEmpty() }?.let {
            lapses.toDouble() / it.size.toDouble()
        }
        val denominator = responseTrials.size + falseStarts
        val performanceScore = denominator.takeIf { it > 0 }?.let {
            (100.0 * (1.0 - (lapses + falseStarts).toDouble() / it.toDouble())).coerceIn(0.0, 100.0)
        }
        val meanRt = rts.takeIf { it.isNotEmpty() }?.average()
        val medianRt = rts.takeIf { it.isNotEmpty() }?.sorted()?.let(::median)
        val decileCount = rts.takeIf { it.isNotEmpty() }?.let { ceil(it.size * 0.10).toInt().coerceAtLeast(1) }
        val fastest = decileCount?.let { n -> rts.sorted().take(n).average() }
        val slowestSpeed = decileCount?.let { n ->
            rts.sortedDescending().take(n).map { 1000.0 / it.toDouble() }.average()
        }

        return PvtScores(
            validResponses = responseTrials.size,
            lapses = lapses,
            falseStarts = falseStarts,
            timeouts = timeouts,
            lapsesPlusFalseStarts = lapses + falseStarts,
            responseSpeedPerSecond = responseSpeed,
            lapseProbability = lapseProbability,
            performanceScorePercent = performanceScore,
            meanRtMs = meanRt,
            medianRtMs = medianRt,
            fastest10PctMeanRtMs = fastest,
            slowest10PctResponseSpeedPerSecond = slowestSpeed
        )
    }

    private fun median(sorted: List<Long>): Double {
        val n = sorted.size
        if (n == 0) return Double.NaN
        return if (n % 2 == 1) sorted[n / 2].toDouble()
        else (sorted[n / 2 - 1].toDouble() + sorted[n / 2].toDouble()) / 2.0
    }
}
