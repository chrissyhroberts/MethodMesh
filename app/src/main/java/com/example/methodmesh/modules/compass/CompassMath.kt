package com.example.methodmesh.modules.compass

import kotlin.math.abs
import kotlin.math.roundToInt

object CompassMath {
    private val cardinal16 = listOf(
        "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
        "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
    )

    fun normaliseDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f

    /** Signed shortest turn from heading to target: positive = turn right/clockwise. */
    fun signedErrorDegrees(targetDegrees: Float, headingDegrees: Float): Float =
        ((normaliseDegrees(targetDegrees) - normaliseDegrees(headingDegrees) + 540f) % 360f) - 180f

    fun isAligned(targetDegrees: Float, headingDegrees: Float, toleranceDegrees: Float): Boolean =
        abs(signedErrorDegrees(targetDegrees, headingDegrees)) <= toleranceDegrees.coerceAtLeast(0f)

    fun cardinalDirection(headingDegrees: Float): String {
        val normalised = normaliseDegrees(headingDegrees)
        val index = ((normalised + 11.25f) / 22.5f).toInt() % cardinal16.size
        return cardinal16[index]
    }

    fun headingLabel(headingDegrees: Float): String =
        "${(normaliseDegrees(headingDegrees).roundToInt() % 360).toString().padStart(3, '0')}° ${cardinalDirection(headingDegrees)}"

    fun alignmentInstruction(errorDegrees: Float, toleranceDegrees: Float): String {
        val absError = abs(errorDegrees)
        return when {
            absError <= toleranceDegrees -> "On target"
            errorDegrees > 0f -> "Turn right ${absError.roundToInt()}°"
            else -> "Turn left ${absError.roundToInt()}°"
        }
    }
}
