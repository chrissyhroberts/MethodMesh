package com.example.methodmesh.modules.astronomy

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.*

/** Pure, dependency-free astronomy and observing calculations. */
object AstronomyMath {
    const val ALGORITHM_VERSION = "0.1.0"
    private const val DEG = Math.PI / 180.0

    data class Equatorial(val raDeg: Double, val decDeg: Double)
    data class Horizontal(val altitudeDeg: Double, val azimuthDeg: Double)
    data class MoonHorizontal(val altitudeDeg: Double, val azimuthDeg: Double, val illuminationPct: Double)
    data class ImageScaleResult(
        val arcsecPerPixel: Double,
        val fieldWidthDeg: Double,
        val fieldHeightDeg: Double,
        val effectiveFocalLengthMm: Double,
        val focalRatio: Double?
    )
    data class DewResult(val dewPointC: Double, val marginC: Double, val risk: String)
    data class ConditionsResult(
        val overall: Int,
        val cloud: Int,
        val transparency: Int,
        val wind: Int,
        val dew: Int,
        val darkness: Int,
        val label: String
    )
    data class WindowSample(
        val instant: Instant,
        val score: Int,
        val sunAltitudeDeg: Double,
        val targetAltitudeDeg: Double?,
        val moonAltitudeDeg: Double,
        val moonIlluminationPct: Double,
        val moonSeparationDeg: Double?
    )

    fun normaliseDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    fun clamp(value: Double, low: Double, high: Double): Double = max(low, min(high, value))

    fun julianDate(instant: Instant): Double = instant.toEpochMilli() / 86400000.0 + 2440587.5

    fun gmstDegrees(instant: Instant): Double {
        val jd = julianDate(instant)
        val t = (jd - 2451545.0) / 36525.0
        return normaliseDegrees(
            280.46061837 + 360.98564736629 * (jd - 2451545.0) +
                0.000387933 * t * t - t * t * t / 38710000.0
        )
    }

    fun localSiderealDegrees(instant: Instant, longitudeDeg: Double): Double =
        normaliseDegrees(gmstDegrees(instant) + longitudeDeg)

    fun horizontal(
        equatorial: Equatorial,
        latitudeDeg: Double,
        longitudeDeg: Double,
        instant: Instant
    ): Horizontal {
        val lat = latitudeDeg * DEG
        val dec = equatorial.decDeg * DEG
        val hourAngle = normaliseDegrees(localSiderealDegrees(instant, longitudeDeg) - equatorial.raDeg) * DEG
        val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(hourAngle)
        val alt = asin(clamp(sinAlt, -1.0, 1.0))
        val y = -sin(hourAngle) * cos(dec)
        val x = sin(dec) * cos(lat) - cos(dec) * sin(lat) * cos(hourAngle)
        val az = atan2(y, x)
        return Horizontal(alt / DEG, normaliseDegrees(az / DEG))
    }

    /** Low-precision solar position, easily sufficient for twilight planning. */
    fun sunEquatorial(instant: Instant): Equatorial {
        val n = julianDate(instant) - 2451545.0
        val l = normaliseDegrees(280.460 + 0.9856474 * n)
        val g = normaliseDegrees(357.528 + 0.9856003 * n) * DEG
        val lambda = normaliseDegrees(l + 1.915 * sin(g) + 0.020 * sin(2 * g)) * DEG
        val epsilon = (23.439 - 0.0000004 * n) * DEG
        val ra = atan2(cos(epsilon) * sin(lambda), cos(lambda)) / DEG
        val dec = asin(sin(epsilon) * sin(lambda)) / DEG
        return Equatorial(normaliseDegrees(ra), dec)
    }

    /**
     * Compact low-precision lunar ephemeris adapted from standard orbital-element
     * formulae. Accuracy is intended for observing-window decisions, not occultations.
     */
    fun moonEquatorial(instant: Instant): Equatorial {
        val d = julianDate(instant) - 2451543.5
        val n = normaliseDegrees(125.1228 - 0.0529538083 * d) * DEG
        val i = 5.1454 * DEG
        val w = normaliseDegrees(318.0634 + 0.1643573223 * d) * DEG
        val a = 60.2666
        val e = 0.054900
        val m = normaliseDegrees(115.3654 + 13.0649929509 * d) * DEG
        var eAnom = m + e * sin(m) * (1.0 + e * cos(m))
        repeat(5) {
            eAnom -= (eAnom - e * sin(eAnom) - m) / (1.0 - e * cos(eAnom))
        }
        val xv = a * (cos(eAnom) - e)
        val yv = a * (sqrt(1.0 - e * e) * sin(eAnom))
        val v = atan2(yv, xv)
        val r = sqrt(xv * xv + yv * yv)
        val xh = r * (cos(n) * cos(v + w) - sin(n) * sin(v + w) * cos(i))
        val yh = r * (sin(n) * cos(v + w) + cos(n) * sin(v + w) * cos(i))
        val zh = r * sin(v + w) * sin(i)
        val lon = atan2(yh, xh)
        val lat = atan2(zh, sqrt(xh * xh + yh * yh))
        val epsilon = (23.4393 - 3.563E-7 * d) * DEG
        val xe = cos(lon) * cos(lat)
        val ye = sin(lon) * cos(lat) * cos(epsilon) - sin(lat) * sin(epsilon)
        val ze = sin(lon) * cos(lat) * sin(epsilon) + sin(lat) * cos(epsilon)
        return Equatorial(normaliseDegrees(atan2(ye, xe) / DEG), asin(clamp(ze, -1.0, 1.0)) / DEG)
    }

    fun angularSeparationDeg(a: Equatorial, b: Equatorial): Double {
        val ra1 = a.raDeg * DEG; val ra2 = b.raDeg * DEG
        val d1 = a.decDeg * DEG; val d2 = b.decDeg * DEG
        val c = sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(ra1 - ra2)
        return acos(clamp(c, -1.0, 1.0)) / DEG
    }

    fun moonIlluminationPct(instant: Instant): Double {
        val sep = angularSeparationDeg(sunEquatorial(instant), moonEquatorial(instant))
        return (1.0 - cos(sep * DEG)) * 50.0
    }

    /** Moon altitude/azimuth plus illuminated fraction for observing-condition calculations. */
    fun moonHorizontal(instant: Instant, latitudeDeg: Double, longitudeDeg: Double): MoonHorizontal {
        val horizontal = horizontal(moonEquatorial(instant), latitudeDeg, longitudeDeg, instant)
        return MoonHorizontal(
            altitudeDeg = horizontal.altitudeDeg,
            azimuthDeg = horizontal.azimuthDeg,
            illuminationPct = moonIlluminationPct(instant)
        )
    }

    /** Clock-face angle of Polaris around the north celestial pole: 0 = 12 o'clock. */
    fun polarisClockAngleDeg(instant: Instant, longitudeDeg: Double): Double {
        val polarisRaDeg = (2.0 + 31.0 / 60.0 + 49.09 / 3600.0) * 15.0
        val hourAngle = normaliseDegrees(localSiderealDegrees(instant, longitudeDeg) - polarisRaDeg)
        return normaliseDegrees(180.0 - hourAngle)
    }

    fun dewPointC(temperatureC: Double, humidityPct: Double): Double {
        val rh = clamp(humidityPct, 1.0, 100.0) / 100.0
        val a = 17.62
        val b = 243.12
        val gamma = ln(rh) + a * temperatureC / (b + temperatureC)
        return b * gamma / (a - gamma)
    }

    fun dewRisk(temperatureC: Double, humidityPct: Double, suppliedDewPointC: Double? = null): DewResult {
        val dew = suppliedDewPointC ?: dewPointC(temperatureC, humidityPct)
        val margin = temperatureC - dew
        val risk = when {
            margin <= 1.5 -> "VERY HIGH"
            margin <= 3.0 -> "HIGH"
            margin <= 5.0 -> "MODERATE"
            margin <= 8.0 -> "LOW"
            else -> "VERY LOW"
        }
        return DewResult(dew, margin, risk)
    }

    fun imageScale(
        focalLengthMm: Double,
        pixelSizeMicron: Double,
        sensorWidthMm: Double,
        sensorHeightMm: Double,
        apertureMm: Double? = null,
        multiplier: Double = 1.0
    ): ImageScaleResult {
        require(focalLengthMm > 0 && pixelSizeMicron > 0 && sensorWidthMm > 0 && sensorHeightMm > 0 && multiplier > 0)
        val eff = focalLengthMm * multiplier
        val arcsecPerPixel = 206.265 * pixelSizeMicron / eff
        val width = 2.0 * atan(sensorWidthMm / (2.0 * eff)) / DEG
        val height = 2.0 * atan(sensorHeightMm / (2.0 * eff)) / DEG
        return ImageScaleResult(arcsecPerPixel, width, height, eff, apertureMm?.takeIf { it > 0 }?.let { eff / it })
    }

    /** Pixel-aware untracked exposure limit plus the familiar 500-rule comparator. */
    fun exposureLimitSeconds(
        focalLengthMm: Double,
        pixelSizeMicron: Double,
        declinationDeg: Double,
        maxTrailPixels: Double = 1.0,
        cropFactor: Double = 1.0
    ): Pair<Double, Double> {
        val scale = 206.265 * pixelSizeMicron / focalLengthMm
        val angularRate = 15.041067 * max(0.03, abs(cos(declinationDeg * DEG)))
        val pixelLimited = maxTrailPixels * scale / angularRate
        val rule500 = 500.0 / (focalLengthMm * cropFactor.coerceAtLeast(0.1))
        return pixelLimited to rule500
    }

    fun conditionsScore(
        cloudCoverPct: Double?,
        visibilityM: Double?,
        windKmh: Double?,
        gustKmh: Double?,
        temperatureC: Double?,
        humidityPct: Double?,
        dewPointC: Double?,
        aerosolOpticalDepth: Double?,
        pm25: Double?,
        moonAltitudeDeg: Double?,
        moonIlluminationPct: Double?
    ): ConditionsResult {
        val cloud = scoreLowerBetter(cloudCoverPct, good = 5.0, bad = 80.0)
        val visibility = visibilityM?.let { scoreHigherBetter(it, good = 30000.0, bad = 5000.0) } ?: 70
        val aod = aerosolOpticalDepth?.let { scoreLowerBetter(it, good = 0.08, bad = 0.6) } ?: 70
        val pm = pm25?.let { scoreLowerBetter(it, good = 5.0, bad = 50.0) } ?: 70
        val transparency = ((visibility * 0.35 + aod * 0.40 + pm * 0.25)).roundToInt().coerceIn(0, 100)
        val windBase = scoreLowerBetter(windKmh, good = 5.0, bad = 35.0)
        val gustBase = scoreLowerBetter(gustKmh, good = 10.0, bad = 55.0)
        val wind = (windBase * 0.55 + gustBase * 0.45).roundToInt().coerceIn(0, 100)
        val dew = if (temperatureC != null && humidityPct != null) {
            val margin = dewRisk(temperatureC, humidityPct, dewPointC).marginC
            scoreHigherBetter(margin, good = 8.0, bad = 1.0)
        } else 70
        val darkness = if (moonAltitudeDeg == null || moonIlluminationPct == null || moonAltitudeDeg < -5.0) 100 else {
            val penalty = (moonIlluminationPct * clamp((moonAltitudeDeg + 5.0) / 65.0, 0.0, 1.0)).roundToInt()
            (100 - penalty).coerceIn(0, 100)
        }
        val overall = (cloud * 0.32 + transparency * 0.25 + wind * 0.17 + dew * 0.13 + darkness * 0.13).roundToInt().coerceIn(0, 100)
        val label = when {
            overall >= 85 -> "EXCELLENT"
            overall >= 70 -> "GOOD"
            overall >= 55 -> "MARGINAL"
            overall >= 35 -> "POOR"
            else -> "VERY POOR"
        }
        return ConditionsResult(overall, cloud, transparency, wind, dew, darkness, label)
    }

    fun imagingWindowSample(
        instant: Instant,
        latitudeDeg: Double,
        longitudeDeg: Double,
        target: Equatorial?,
        minimumTargetAltitudeDeg: Double,
        cloudCoverPct: Double? = null,
        windKmh: Double? = null,
        transparencyScore: Int? = null
    ): WindowSample {
        val sunAlt = horizontal(sunEquatorial(instant), latitudeDeg, longitudeDeg, instant).altitudeDeg
        val moonEq = moonEquatorial(instant)
        val moonAlt = horizontal(moonEq, latitudeDeg, longitudeDeg, instant).altitudeDeg
        val moonIll = moonIlluminationPct(instant)
        val targetAlt = target?.let { horizontal(it, latitudeDeg, longitudeDeg, instant).altitudeDeg }
        val moonSep = target?.let { angularSeparationDeg(it, moonEq) }
        val darkness = when {
            sunAlt <= -18.0 -> 100.0
            sunAlt >= -6.0 -> 0.0
            else -> (-6.0 - sunAlt) / 12.0 * 100.0
        }
        val targetScore = when {
            targetAlt == null -> 100.0
            targetAlt < minimumTargetAltitudeDeg -> 0.0
            else -> clamp((targetAlt - minimumTargetAltitudeDeg) / (70.0 - minimumTargetAltitudeDeg), 0.0, 1.0) * 100.0
        }
        val moonScore = if (moonAlt < -5) 100.0 else {
            val sepFactor = moonSep?.let { clamp((it - 20.0) / 80.0, 0.0, 1.0) } ?: 0.5
            100.0 - moonIll * clamp((moonAlt + 5.0) / 70.0, 0.0, 1.0) * (1.0 - 0.65 * sepFactor)
        }
        val cloud = scoreLowerBetter(cloudCoverPct, 5.0, 80.0).toDouble()
        val wind = scoreLowerBetter(windKmh, 5.0, 35.0).toDouble()
        val trans = (transparencyScore ?: 70).toDouble()
        val score = (darkness * 0.28 + targetScore * 0.25 + moonScore * 0.12 + cloud * 0.20 + wind * 0.07 + trans * 0.08)
            .roundToInt().coerceIn(0, 100)
        return WindowSample(instant, score, sunAlt, targetAlt, moonAlt, moonIll, moonSep)
    }

    private fun scoreLowerBetter(value: Double?, good: Double, bad: Double): Int {
        if (value == null || !value.isFinite()) return 70
        if (value <= good) return 100
        if (value >= bad) return 0
        return (((bad - value) / (bad - good)) * 100.0).roundToInt().coerceIn(0, 100)
    }

    private fun scoreHigherBetter(value: Double?, good: Double, bad: Double): Int {
        if (value == null || !value.isFinite()) return 70
        if (value >= good) return 100
        if (value <= bad) return 0
        return (((value - bad) / (good - bad)) * 100.0).roundToInt().coerceIn(0, 100)
    }

    fun isoLocalLabel(instant: Instant): String =
        ZonedDateTime.ofInstant(instant, ZoneOffset.UTC).toString()
}
