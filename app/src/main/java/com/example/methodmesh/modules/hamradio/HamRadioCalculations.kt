package com.example.methodmesh.modules.hamradio

import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.*

object HamRadioCalculations {
    private const val EARTH_RADIUS_KM = 6371.0088
    private const val C = 299_792_458.0

    data class Coordinate(val latitude: Double, val longitude: Double)
    data class MaidenheadCell(
        val locator: String,
        val centreLatitude: Double,
        val centreLongitude: Double,
        val latitudeSpanDeg: Double,
        val longitudeSpanDeg: Double
    )
    data class PathResult(
        val distanceKm: Double,
        val initialBearingDeg: Double,
        val reverseBearingDeg: Double,
        val longPathBearingDeg: Double
    )
    data class AntennaResult(
        val wavelengthM: Double,
        val totalLengthM: Double,
        val elementLengthM: Double,
        val design: String,
        val factor: Double
    )
    data class SwrResult(
        val reflectionCoefficient: Double,
        val swr: Double,
        val returnLossDb: Double?,
        val mismatchLossDb: Double
    )
    data class LinkResult(
        val fsplDb: Double,
        val opticalHorizonKm: Double,
        val radioHorizonKm: Double,
        val fresnelMidpointM: Double,
        val eirpDbm: Double?,
        val freeSpaceReceivedPowerDbm: Double?
    )
    data class BandCandidate(
        val band: String,
        val representativeMhz: Double,
        val score: Int,
        val reasons: List<String>
    )
    data class BandAdvice(
        val ranked: List<BandCandidate>,
        val solarElevationDeg: Double,
        val localSolarHour: Double,
        val pathDistanceKm: Double
    )

    fun encodeMaidenhead(latitude: Double, longitude: Double, precision: Int = 6): String {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        require(precision in listOf(2, 4, 6, 8)) { "Maidenhead precision must be 2, 4, 6 or 8 characters." }

        var lon = if (longitude == 180.0) 179.999999999 else longitude
        var lat = if (latitude == 90.0) 89.999999999 else latitude
        lon += 180.0
        lat += 90.0
        val out = StringBuilder()

        val fieldLon = floor(lon / 20.0).toInt().coerceIn(0, 17)
        val fieldLat = floor(lat / 10.0).toInt().coerceIn(0, 17)
        out.append(('A'.code + fieldLon).toChar())
        out.append(('A'.code + fieldLat).toChar())
        if (precision == 2) return out.toString()

        lon -= fieldLon * 20.0
        lat -= fieldLat * 10.0
        val squareLon = floor(lon / 2.0).toInt().coerceIn(0, 9)
        val squareLat = floor(lat / 1.0).toInt().coerceIn(0, 9)
        out.append(('0'.code + squareLon).toChar())
        out.append(('0'.code + squareLat).toChar())
        if (precision == 4) return out.toString()

        lon -= squareLon * 2.0
        lat -= squareLat
        val subLonSize = 2.0 / 24.0
        val subLatSize = 1.0 / 24.0
        val subLon = floor(lon / subLonSize).toInt().coerceIn(0, 23)
        val subLat = floor(lat / subLatSize).toInt().coerceIn(0, 23)
        out.append(('a'.code + subLon).toChar())
        out.append(('a'.code + subLat).toChar())
        if (precision == 6) return out.toString()

        lon -= subLon * subLonSize
        lat -= subLat * subLatSize
        val extLonSize = subLonSize / 10.0
        val extLatSize = subLatSize / 10.0
        val extLon = floor(lon / extLonSize).toInt().coerceIn(0, 9)
        val extLat = floor(lat / extLatSize).toInt().coerceIn(0, 9)
        out.append(('0'.code + extLon).toChar())
        out.append(('0'.code + extLat).toChar())
        return out.toString()
    }

    fun decodeMaidenhead(locatorRaw: String): MaidenheadCell {
        val locator = locatorRaw.trim()
        require(locator.length in listOf(2, 4, 6, 8)) { "Maidenhead locator must contain 2, 4, 6 or 8 characters." }
        val upper = locator.uppercase()
        require(upper[0] in 'A'..'R' && upper[1] in 'A'..'R') { "Invalid Maidenhead field." }

        var lon = (upper[0] - 'A') * 20.0 - 180.0
        var lat = (upper[1] - 'A') * 10.0 - 90.0
        var lonSpan = 20.0
        var latSpan = 10.0

        if (locator.length >= 4) {
            require(locator[2].isDigit() && locator[3].isDigit()) { "Invalid Maidenhead square." }
            lon += locator[2].digitToInt() * 2.0
            lat += locator[3].digitToInt() * 1.0
            lonSpan = 2.0
            latSpan = 1.0
        }
        if (locator.length >= 6) {
            val c4 = locator[4].lowercaseChar()
            val c5 = locator[5].lowercaseChar()
            require(c4 in 'a'..'x' && c5 in 'a'..'x') { "Invalid Maidenhead subsquare." }
            lonSpan = 2.0 / 24.0
            latSpan = 1.0 / 24.0
            lon += (c4 - 'a') * lonSpan
            lat += (c5 - 'a') * latSpan
        }
        if (locator.length >= 8) {
            require(locator[6].isDigit() && locator[7].isDigit()) { "Invalid Maidenhead extended square." }
            lonSpan /= 10.0
            latSpan /= 10.0
            lon += locator[6].digitToInt() * lonSpan
            lat += locator[7].digitToInt() * latSpan
        }
        return MaidenheadCell(
            locator = normalizeLocator(locator),
            centreLatitude = lat + latSpan / 2.0,
            centreLongitude = lon + lonSpan / 2.0,
            latitudeSpanDeg = latSpan,
            longitudeSpanDeg = lonSpan
        )
    }

    fun normalizeLocator(locator: String): String {
        val value = locator.trim()
        if (value.length < 2) return value.uppercase()
        val b = StringBuilder()
        value.forEachIndexed { i, c ->
            b.append(
                when (i) {
                    0, 1 -> c.uppercaseChar()
                    4, 5 -> c.lowercaseChar()
                    else -> c
                }
            )
        }
        return b.toString()
    }

    fun path(origin: Coordinate, destination: Coordinate): PathResult {
        val lat1 = Math.toRadians(origin.latitude)
        val lat2 = Math.toRadians(destination.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(destination.longitude - origin.longitude)
        val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt((1 - a).coerceAtLeast(0.0)))
        val distance = EARTH_RADIUS_KM * c
        val bearing = initialBearing(origin, destination)
        val reverse = initialBearing(destination, origin)
        return PathResult(distance, bearing, reverse, (bearing + 180.0) % 360.0)
    }

    fun pathFromLocators(origin: String, destination: String): PathResult =
        path(
            decodeMaidenhead(origin).let { Coordinate(it.centreLatitude, it.centreLongitude) },
            decodeMaidenhead(destination).let { Coordinate(it.centreLatitude, it.centreLongitude) }
        )

    private fun initialBearing(origin: Coordinate, destination: Coordinate): Double {
        val lat1 = Math.toRadians(origin.latitude)
        val lat2 = Math.toRadians(destination.latitude)
        val dLon = Math.toRadians(destination.longitude - origin.longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun antenna(frequencyMhz: Double, designRaw: String, factor: Double): AntennaResult {
        require(frequencyMhz > 0) { "Frequency must be greater than zero." }
        require(factor > 0) { "Velocity/end-effect factor must be greater than zero." }
        val wavelength = C / (frequencyMhz * 1_000_000.0)
        val design = designRaw.lowercase()
        val multiplier = when (design) {
            "quarter_wave" -> 0.25
            "half_wave" -> 0.5
            "dipole" -> 0.5
            "five_eighths" -> 0.625
            "full_wave" -> 1.0
            else -> error("Unsupported antenna design: $designRaw")
        }
        val total = wavelength * multiplier * factor
        val element = if (design == "dipole") total / 2.0 else total
        return AntennaResult(wavelength, total, element, design, factor)
    }

    fun swr(forwardPowerW: Double, reflectedPowerW: Double): SwrResult {
        require(forwardPowerW > 0) { "Forward power must be greater than zero." }
        require(reflectedPowerW >= 0) { "Reflected power must not be negative." }
        require(reflectedPowerW < forwardPowerW) { "Reflected power must be lower than forward power for a finite SWR." }
        val gamma = sqrt(reflectedPowerW / forwardPowerW)
        val swr = (1 + gamma) / (1 - gamma)
        val returnLoss = if (gamma == 0.0) null else -20.0 * log10(gamma)
        val mismatchLoss = -10.0 * log10(1.0 - gamma * gamma)
        return SwrResult(gamma, swr, returnLoss, mismatchLoss)
    }

    fun link(
        frequencyMhz: Double,
        distanceKm: Double,
        txHeightM: Double,
        rxHeightM: Double,
        txPowerW: Double = 0.0,
        antennaGainDbi: Double = 0.0,
        feedlineLossDb: Double = 0.0
    ): LinkResult {
        require(frequencyMhz > 0 && distanceKm > 0) { "Frequency and distance must be greater than zero." }
        require(txHeightM >= 0 && rxHeightM >= 0) { "Antenna heights must not be negative." }
        val fspl = 32.44 + 20 * log10(frequencyMhz) + 20 * log10(distanceKm)
        val optical = 3.57 * (sqrt(txHeightM) + sqrt(rxHeightM))
        val radio = 4.12 * (sqrt(txHeightM) + sqrt(rxHeightM))
        val dMetres = distanceKm * 1000.0
        val lambda = C / (frequencyMhz * 1_000_000.0)
        val fresnel = sqrt(lambda * dMetres / 4.0)
        val eirpDbm = if (txPowerW > 0) 10 * log10(txPowerW * 1000.0) + antennaGainDbi - feedlineLossDb else null
        val received = eirpDbm?.minus(fspl)
        return LinkResult(fspl, optical, radio, fresnel, eirpDbm, received)
    }

    fun solarGeometry(coordinate: Coordinate, whenInstant: Instant): Pair<Double, Double> {
        val zdt = whenInstant.atZone(ZoneOffset.UTC)
        val day = zdt.dayOfYear.toDouble()
        val hour = zdt.hour + zdt.minute / 60.0 + zdt.second / 3600.0
        val gamma = 2.0 * Math.PI / 365.0 * (day - 1.0 + (hour - 12.0) / 24.0)
        val eqTime = 229.18 * (0.000075 + 0.001868 * cos(gamma) - 0.032077 * sin(gamma) - 0.014615 * cos(2 * gamma) - 0.040849 * sin(2 * gamma))
        val decl = 0.006918 - 0.399912 * cos(gamma) + 0.070257 * sin(gamma) - 0.006758 * cos(2 * gamma) + 0.000907 * sin(2 * gamma) - 0.002697 * cos(3 * gamma) + 0.00148 * sin(3 * gamma)
        val trueSolarMinutes = ((hour * 60.0 + eqTime + 4.0 * coordinate.longitude) % 1440.0 + 1440.0) % 1440.0
        val hourAngleDeg = trueSolarMinutes / 4.0 - 180.0
        val latRad = Math.toRadians(coordinate.latitude)
        val haRad = Math.toRadians(hourAngleDeg)
        val cosZenith = (sin(latRad) * sin(decl) + cos(latRad) * cos(decl) * cos(haRad)).coerceIn(-1.0, 1.0)
        val elevation = 90.0 - Math.toDegrees(acos(cosZenith))
        return elevation to trueSolarMinutes / 60.0
    }

    fun recommendBands(
        origin: Coordinate,
        pathDistanceKm: Double,
        whenInstant: Instant,
        kp: Double,
        f107: Double,
        rScale: Int,
        mode: String
    ): BandAdvice {
        require(pathDistanceKm >= 0) { "Path distance cannot be negative." }
        val (solarElevation, solarHour) = solarGeometry(origin, whenInstant)
        val daylight = solarElevation > 3.0
        val twilight = solarElevation in -9.0..3.0
        val dx = pathDistanceKm >= 2500
        val regional = pathDistanceKm < 800
        val solarStrength = ((f107 - 70.0) / 130.0).coerceIn(0.0, 1.0)
        val stormPenalty = when {
            kp >= 7 -> 32
            kp >= 5 -> 20
            kp >= 4 -> 10
            else -> 0
        }
        val highLatPenalty = if (abs(origin.latitude) >= 55 && kp >= 4) 8 else 0
        val rPenalty = if (daylight) rScale.coerceIn(0, 5) * 7 else 0
        val modeBonus = when (mode.lowercase()) {
            "ft8", "ft4", "cw", "digital" -> 5
            else -> 0
        }

        val bands = listOf(
            Triple("160m", 1.90, 0), Triple("80m", 3.60, 1), Triple("60m", 5.35, 2),
            Triple("40m", 7.10, 3), Triple("30m", 10.12, 4), Triple("20m", 14.15, 5),
            Triple("17m", 18.10, 6), Triple("15m", 21.20, 7), Triple("12m", 24.95, 8), Triple("10m", 28.40, 9)
        )

        val ranked = bands.map { (band, mhz, index) ->
            var score = 42
            val reasons = mutableListOf<String>()
            if (daylight) {
                val highBand = index >= 5
                if (highBand) {
                    val bonus = (10 + solarStrength * 28).roundToInt()
                    score += bonus
                    reasons += "daylight supports higher HF"
                } else if (index <= 2) {
                    score -= 12
                    reasons += "daylight D-layer absorption penalises lower HF"
                }
            } else {
                if (index <= 3) {
                    score += 22
                    reasons += "night favours lower HF"
                }
                if (index >= 7) score -= 22
            }
            if (twilight && index in 3..6) {
                score += 10
                reasons += "grey-line/twilight bonus"
            }
            when {
                dx -> {
                    if (index in 5..9) score += 18 else if (index <= 2) score -= 8
                    reasons += "DX path"
                }
                regional -> {
                    if (index in 1..4) score += 18
                    if (index >= 7) score -= 8
                    reasons += "short/regional path"
                }
                else -> if (index in 3..7) score += 10
            }
            if (index >= 7) score += (solarStrength * 14).roundToInt()
            if (index <= 2 && f107 < 100) score += 5
            val stormWeight = if (index >= 5) stormPenalty else stormPenalty / 2
            score -= stormWeight + highLatPenalty + rPenalty
            if (stormWeight > 0) reasons += "geomagnetic disturbance penalty"
            if (rPenalty > 0) reasons += "sunlit radio-blackout penalty"
            score += modeBonus
            if (modeBonus > 0) reasons += "weak-signal mode tolerance"
            BandCandidate(band, mhz, score.coerceIn(0, 100), reasons.distinct())
        }.sortedByDescending { it.score }

        return BandAdvice(ranked, solarElevation, solarHour, pathDistanceKm)
    }

    fun bandForFrequencyHz(frequencyHz: Long): String {
        val mhz = frequencyHz / 1_000_000.0
        return when {
            mhz in 1.8..2.0 -> "160m"
            mhz in 3.4..4.1 -> "80m"
            mhz in 5.0..5.6 -> "60m"
            mhz in 7.0..7.4 -> "40m"
            mhz in 10.0..10.2 -> "30m"
            mhz in 14.0..14.5 -> "20m"
            mhz in 18.0..18.3 -> "17m"
            mhz in 21.0..21.6 -> "15m"
            mhz in 24.8..25.1 -> "12m"
            mhz in 28.0..30.0 -> "10m"
            mhz in 50.0..54.5 -> "6m"
            mhz in 69.0..71.0 -> "4m"
            mhz in 144.0..148.0 -> "2m"
            mhz in 420.0..450.0 -> "70cm"
            else -> "other"
        }
    }
}
