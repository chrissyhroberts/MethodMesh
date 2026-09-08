package com.example.methodmesh.modules.visualacuity

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.tan

/** Pure visual-acuity geometry, notation and staircase logic. */
object VisualAcuityMath {
    const val DISTANCE_MM = 2000.0
    const val NEAR_MM = 400.0

    /**
     * Full 5x5 tumbling-E size in millimetres.
     * One stroke/gap is MAR arcminutes; the complete E subtends 5 x MAR arcminutes.
     */
    fun optotypeSizeMm(logMar: Double, distanceMm: Double): Double {
        require(distanceMm > 0.0)
        val marArcMin = 10.0.pow(logMar)
        val fullAngleRad = (5.0 * marArcMin) * PI / (180.0 * 60.0)
        return 2.0 * distanceMm * tan(fullAngleRad / 2.0)
    }

    fun strokeSizeMm(logMar: Double, distanceMm: Double): Double =
        optotypeSizeMm(logMar, distanceMm) / 5.0

    fun decimalAcuity(logMar: Double): Double = 10.0.pow(-logMar)

    fun exactMetricSnellenDenominator(logMar: Double): Double = 6.0 * 10.0.pow(logMar)
    fun exactImperialSnellenDenominator(logMar: Double): Double = 20.0 * 10.0.pow(logMar)

    /** Conventional labels for the 0.1-logMAR sequence used by this capability. */
    fun metricSnellen(levelTenth: Int): String = when (levelTenth.coerceIn(0, 10)) {
        0 -> "6/6"
        1 -> "6/7.5"
        2 -> "6/9.5"
        3 -> "6/12"
        4 -> "6/15"
        5 -> "6/19"
        6 -> "6/24"
        7 -> "6/30"
        8 -> "6/38"
        9 -> "6/48"
        else -> "6/60"
    }

    fun imperialSnellen(levelTenth: Int): String = when (levelTenth.coerceIn(0, 10)) {
        0 -> "20/20"
        1 -> "20/25"
        2 -> "20/32"
        3 -> "20/40"
        4 -> "20/50"
        5 -> "20/63"
        6 -> "20/80"
        7 -> "20/100"
        8 -> "20/125"
        9 -> "20/160"
        else -> "20/200"
    }

    /** Normalize a measured logMAR if the actual distance differed from the nominal distance. */
    fun normalizeForDistance(measuredLogMar: Double, actualDistanceMm: Double, nominalDistanceMm: Double): Double {
        require(actualDistanceMm > 0.0 && nominalDistanceMm > 0.0)
        return measuredLogMar - log10(actualDistanceMm / nominalDistanceMm)
    }
}

enum class EOrientation(val degreesClockwise: Int) {
    RIGHT(0),
    DOWN(90),
    LEFT(180),
    UP(270);

    companion object {
        fun fromSwipe(dx: Float, dy: Float): EOrientation? {
            if (dx == 0f && dy == 0f) return null
            return if (kotlin.math.abs(dx) >= kotlin.math.abs(dy)) {
                if (dx > 0f) RIGHT else LEFT
            } else {
                if (dy > 0f) DOWN else UP
            }
        }
    }
}

enum class StaircasePhase { COARSE, REFINE, COMPLETE }

data class VisualAcuitySession(
    val currentLevelTenth: Int = 10,
    val phase: StaircasePhase = StaircasePhase.COARSE,
    val coarseIndex: Int = 0,
    val lastPassTenth: Int? = null,
    val failedBoundaryTenth: Int? = null,
    val correctAtLevel: Int = 0,
    val incorrectAtLevel: Int = 0,
    val trialAtLevel: Int = 0,
    val totalTrials: Int = 0,
    val completed: Boolean = false,
    val poorerThanOne: Boolean = false,
    val thresholdTenth: Int? = null
) {
    val currentLogMar: Double get() = currentLevelTenth / 10.0
}

data class VisualAcuityStep(
    val state: VisualAcuitySession,
    val levelResolved: Boolean,
    val levelPassed: Boolean?
)

/**
 * V@home/WHOeyes-inspired fast staircase.
 *
 * Published coarse levels are 1.0, 0.8, 0.5, 0.2 and 0.0 logMAR, with 4/5
 * required at a level. If a coarse level fails, this implementation fills the
 * bracket in 0.1-logMAR steps until the smallest passing level is identified.
 * The bracket-refinement rule is an explicit MethodMesh v1 implementation
 * choice; it is versioned and must not be silently changed.
 */
object VisualAcuityStaircase {
    const val ALGORITHM_ID = "methodmesh.vahome_fast_staircase"
    const val ALGORITHM_VERSION = "1.0.0"
    val coarseLevelsTenth = intArrayOf(10, 8, 5, 2, 0)

    fun newSession(): VisualAcuitySession = VisualAcuitySession()

    fun recordResponse(state: VisualAcuitySession, correct: Boolean): VisualAcuityStep {
        require(!state.completed) { "Visual acuity session is already complete." }

        val nextCorrect = state.correctAtLevel + if (correct) 1 else 0
        val nextIncorrect = state.incorrectAtLevel + if (correct) 0 else 1
        val nextTrial = state.trialAtLevel + 1
        val base = state.copy(
            correctAtLevel = nextCorrect,
            incorrectAtLevel = nextIncorrect,
            trialAtLevel = nextTrial,
            totalTrials = state.totalTrials + 1
        )

        // The published V@home protocol describes four correct out of five.
        // Present all five optotypes so this version does not silently shorten
        // the validated presentation count even when the outcome is known early.
        if (nextTrial >= 5) return resolveLevel(base, passed = nextCorrect >= 4)
        return VisualAcuityStep(base, levelResolved = false, levelPassed = null)
    }

    private fun resolveLevel(state: VisualAcuitySession, passed: Boolean): VisualAcuityStep {
        val advanced = if (passed) advanceAfterPass(state) else advanceAfterFail(state)
        return VisualAcuityStep(advanced, levelResolved = true, levelPassed = passed)
    }

    private fun resetLevel(state: VisualAcuitySession, nextLevelTenth: Int, phase: StaircasePhase, coarseIndex: Int = state.coarseIndex): VisualAcuitySession =
        state.copy(
            currentLevelTenth = nextLevelTenth,
            phase = phase,
            coarseIndex = coarseIndex,
            correctAtLevel = 0,
            incorrectAtLevel = 0,
            trialAtLevel = 0
        )

    private fun advanceAfterPass(state: VisualAcuitySession): VisualAcuitySession {
        val passedLevel = state.currentLevelTenth
        val withPass = state.copy(lastPassTenth = passedLevel)

        if (passedLevel == 0) {
            return withPass.copy(
                phase = StaircasePhase.COMPLETE,
                completed = true,
                thresholdTenth = 0,
                correctAtLevel = 0,
                incorrectAtLevel = 0,
                trialAtLevel = 0
            )
        }

        return when (state.phase) {
            StaircasePhase.COARSE -> {
                val nextIndex = state.coarseIndex + 1
                val next = coarseLevelsTenth.getOrNull(nextIndex)
                    ?: return withPass.copy(phase = StaircasePhase.COMPLETE, completed = true, thresholdTenth = passedLevel)
                resetLevel(withPass, next, StaircasePhase.COARSE, nextIndex)
            }
            StaircasePhase.REFINE -> {
                val failed = state.failedBoundaryTenth
                    ?: return withPass.copy(phase = StaircasePhase.COMPLETE, completed = true, thresholdTenth = passedLevel)
                val next = passedLevel - 1
                if (next <= failed) {
                    withPass.copy(
                        phase = StaircasePhase.COMPLETE,
                        completed = true,
                        thresholdTenth = passedLevel,
                        correctAtLevel = 0,
                        incorrectAtLevel = 0,
                        trialAtLevel = 0
                    )
                } else {
                    resetLevel(withPass, next, StaircasePhase.REFINE)
                }
            }
            StaircasePhase.COMPLETE -> withPass
        }
    }

    private fun advanceAfterFail(state: VisualAcuitySession): VisualAcuitySession {
        val failedLevel = state.currentLevelTenth

        if (failedLevel == 10 && state.lastPassTenth == null) {
            return state.copy(
                phase = StaircasePhase.COMPLETE,
                completed = true,
                poorerThanOne = true,
                thresholdTenth = null,
                failedBoundaryTenth = 10,
                correctAtLevel = 0,
                incorrectAtLevel = 0,
                trialAtLevel = 0
            )
        }

        return when (state.phase) {
            StaircasePhase.COARSE -> {
                val lastPass = state.lastPassTenth
                    ?: return state.copy(phase = StaircasePhase.COMPLETE, completed = true, poorerThanOne = true)
                val firstRefine = lastPass - 1
                if (firstRefine <= failedLevel) {
                    state.copy(
                        phase = StaircasePhase.COMPLETE,
                        completed = true,
                        thresholdTenth = lastPass,
                        failedBoundaryTenth = failedLevel,
                        correctAtLevel = 0,
                        incorrectAtLevel = 0,
                        trialAtLevel = 0
                    )
                } else {
                    resetLevel(
                        state.copy(failedBoundaryTenth = failedLevel),
                        firstRefine,
                        StaircasePhase.REFINE
                    )
                }
            }
            StaircasePhase.REFINE -> {
                state.copy(
                    phase = StaircasePhase.COMPLETE,
                    completed = true,
                    thresholdTenth = state.lastPassTenth,
                    failedBoundaryTenth = failedLevel,
                    correctAtLevel = 0,
                    incorrectAtLevel = 0,
                    trialAtLevel = 0
                )
            }
            StaircasePhase.COMPLETE -> state
        }
    }
}
