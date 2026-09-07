package com.example.methodmesh.modules.aviation

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

object AviationCalculations {
    private const val STANDARD_QNH_HPA = 1013.25
    private const val EARTH_RADIUS_NM = 3440.065

    data class WindComponents(
        val headwindKt: Double,
        val tailwindKt: Double,
        val crosswindKt: Double,
        val signedCrosswindKt: Double,
        val crosswindSide: String
    )

    data class AltitudeResult(
        val qnhHpa: Double,
        val pressureAltitudeFt: Double,
        val isaTemperatureC: Double,
        val densityAltitudeFt: Double,
        val isaDeviationC: Double
    )

    data class DistanceBearing(
        val distanceNm: Double,
        val initialBearingDeg: Double
    )

    fun runwayWind(
        runwayHeadingDeg: Double,
        windDirectionDeg: Double,
        windSpeedKt: Double
    ): WindComponents {
        require(runwayHeadingDeg in 0.0..360.0) { "Runway heading must be between 0 and 360 degrees." }
        require(windDirectionDeg in 0.0..360.0) { "Wind direction must be between 0 and 360 degrees." }
        require(windSpeedKt >= 0.0) { "Wind speed cannot be negative." }

        val relativeRadians = Math.toRadians(normaliseSignedDegrees(windDirectionDeg - runwayHeadingDeg))
        val signedHeadwind = windSpeedKt * cos(relativeRadians)
        val signedCrosswind = windSpeedKt * sin(relativeRadians)
        val headwind = signedHeadwind.coerceAtLeast(0.0)
        val tailwind = (-signedHeadwind).coerceAtLeast(0.0)
        val crosswind = abs(signedCrosswind)
        val side = when {
            crosswind < 0.05 -> "none"
            signedCrosswind > 0.0 -> "right"
            else -> "left"
        }
        return WindComponents(
            headwindKt = headwind,
            tailwindKt = tailwind,
            crosswindKt = crosswind,
            signedCrosswindKt = signedCrosswind,
            crosswindSide = side
        )
    }

    /**
     * General-aviation planning approximation. Not a substitute for an AFM/POH
     * performance calculation or certified air-data system.
     */
    fun altitude(
        fieldElevationFt: Double,
        qnhValue: Double,
        qnhUnit: String,
        oatC: Double
    ): AltitudeResult {
        val qnhHpa = when (qnhUnit.lowercase()) {
            "hpa", "mb" -> qnhValue
            "inhg" -> qnhValue * 33.8638866667
            else -> error("QNH unit must be hPa or inHg.")
        }
        require(qnhHpa in 800.0..1100.0) { "QNH is outside the supported planning range (800–1100 hPa)." }
        require(fieldElevationFt in -2000.0..60000.0) { "Elevation is outside the supported range." }
        require(oatC in -100.0..80.0) { "Temperature is outside the supported range." }

        val pressureCorrectionFt = 145366.45 * (1.0 - (qnhHpa / STANDARD_QNH_HPA).pow(0.190284))
        val pressureAltitudeFt = fieldElevationFt + pressureCorrectionFt
        val isaTemperatureC = 15.0 - 1.9812 * (pressureAltitudeFt / 1000.0)
        val isaDeviationC = oatC - isaTemperatureC
        val densityAltitudeFt = pressureAltitudeFt + 120.0 * isaDeviationC

        return AltitudeResult(
            qnhHpa = qnhHpa,
            pressureAltitudeFt = pressureAltitudeFt,
            isaTemperatureC = isaTemperatureC,
            densityAltitudeFt = densityAltitudeFt,
            isaDeviationC = isaDeviationC
        )
    }

    fun e6b(
        calculation: String,
        distanceNm: Double? = null,
        groundspeedKt: Double? = null,
        timeMinutes: Double? = null,
        fuelAvailable: Double? = null,
        fuelBurnPerHour: Double? = null
    ): Pair<Double, String> = when (calculation) {
        "time_from_distance_speed" -> {
            val distance = positive(distanceNm, "Distance")
            val speed = positive(groundspeedKt, "Groundspeed")
            (distance / speed * 60.0) to "min"
        }
        "distance_from_speed_time" -> {
            val speed = positive(groundspeedKt, "Groundspeed")
            val minutes = positive(timeMinutes, "Time")
            (speed * minutes / 60.0) to "NM"
        }
        "speed_from_distance_time" -> {
            val distance = positive(distanceNm, "Distance")
            val minutes = positive(timeMinutes, "Time")
            (distance / (minutes / 60.0)) to "kt"
        }
        "fuel_required" -> {
            val burn = positive(fuelBurnPerHour, "Fuel burn")
            val minutes = positive(timeMinutes, "Time")
            (burn * minutes / 60.0) to "fuel units"
        }
        "endurance" -> {
            val fuel = nonNegative(fuelAvailable, "Fuel available")
            val burn = positive(fuelBurnPerHour, "Fuel burn")
            (fuel / burn * 60.0) to "min"
        }
        "range" -> {
            val fuel = nonNegative(fuelAvailable, "Fuel available")
            val burn = positive(fuelBurnPerHour, "Fuel burn")
            val speed = positive(groundspeedKt, "Groundspeed")
            (fuel / burn * speed) to "NM"
        }
        else -> error("Unsupported E6B calculation: $calculation")
    }

    fun distanceAndBearing(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): DistanceBearing {
        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distance = EARTH_RADIUS_NM * c

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val bearing = (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        return DistanceBearing(distance, bearing)
    }

    fun format(value: Double, decimals: Int = 1): String {
        val scale = 10.0.pow(decimals)
        val rounded = round(value * scale) / scale
        return String.format(Locale.US, "%.${decimals}f", rounded).trim()
    }

    private fun positive(value: Double?, label: String): Double =
        value?.takeIf { it > 0.0 } ?: error("$label must be greater than zero.")

    private fun nonNegative(value: Double?, label: String): Double =
        value?.takeIf { it >= 0.0 } ?: error("$label cannot be negative.")

    private fun normaliseSignedDegrees(value: Double): Double {
        var result = value % 360.0
        if (result > 180.0) result -= 360.0
        if (result < -180.0) result += 360.0
        return result
    }
}
