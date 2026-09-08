package com.example.methodmesh.modules.earthscience

import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

internal data class GeodesicInverseResult(
    val distanceM: Double,
    val initialBearingDeg: Double,
    val finalBearingDeg: Double,
    val converged: Boolean
)

internal data class GeodesicDirectResult(
    val latitude: Double,
    val longitude: Double,
    val finalBearingDeg: Double,
    val converged: Boolean
)

internal data class UtmCoordinate(
    val zone: Int,
    val hemisphere: String,
    val eastingM: Double,
    val northingM: Double
)

internal data class LatLon(val latitude: Double, val longitude: Double)

internal data class StructuralPlane(
    val strikeDeg: Double,
    val dipDeg: Double,
    val dipDirectionDeg: Double,
    val poleTrendDeg: Double,
    val polePlungeDeg: Double
)

internal data class StructuralLine(val trendDeg: Double, val plungeDeg: Double)

internal data class GrainSizeResult(
    val diameterMm: Double,
    val phi: Double,
    val wentworthClass: String,
    val broadClass: String
)

internal data class GeoTimeResult(
    val ageMa: Double,
    val eon: String,
    val era: String,
    val period: String,
    val subdivision: String,
    val sourceVersion: String
)

internal data class GnssFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double,
    val altitudeM: Double? = null,
    val elapsedRealtimeNanos: Long? = null
)

internal data class GnssAverage(
    val latitude: Double,
    val longitude: Double,
    val altitudeM: Double?,
    val acceptedFixes: Int,
    val meanReportedAccuracyM: Double,
    val rmsSpreadM: Double,
    val maxSpreadM: Double,
    val weighting: String
)

internal object EarthScienceMath {
    private const val WGS84_A = 6378137.0
    private const val WGS84_F = 1.0 / 298.257223563
    private const val WGS84_B = (1.0 - WGS84_F) * WGS84_A
    private const val UTM_K0 = 0.9996
    private const val ICS_VERSION = "2026/06"

    fun normaliseDegrees(value: Double): Double = ((value % 360.0) + 360.0) % 360.0
    fun normaliseSignedDegrees(value: Double): Double = ((value + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    fun inverseVincenty(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): GeodesicInverseResult {
        require(lat1Deg in -90.0..90.0 && lat2Deg in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
        require(lon1Deg in -180.0..180.0 && lon2Deg in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
        if (lat1Deg == lat2Deg && lon1Deg == lon2Deg) return GeodesicInverseResult(0.0, 0.0, 0.0, true)

        val phi1 = Math.toRadians(lat1Deg)
        val phi2 = Math.toRadians(lat2Deg)
        val l = Math.toRadians(lon2Deg - lon1Deg)
        val u1 = atan((1.0 - WGS84_F) * tan(phi1))
        val u2 = atan((1.0 - WGS84_F) * tan(phi2))
        val sinU1 = sin(u1)
        val cosU1 = cos(u1)
        val sinU2 = sin(u2)
        val cosU2 = cos(u2)
        var lambda = l
        var lambdaPrev: Double
        var sinSigma = 0.0
        var cosSigma = 0.0
        var sigma = 0.0
        var sinAlpha = 0.0
        var cosSqAlpha = 0.0
        var cos2SigmaM = 0.0
        var converged = false

        repeat(200) {
            val sinLambda = sin(lambda)
            val cosLambda = cos(lambda)
            sinSigma = sqrt(
                (cosU2 * sinLambda).pow(2) +
                    (cosU1 * sinU2 - sinU1 * cosU2 * cosLambda).pow(2)
            )
            if (sinSigma == 0.0) return GeodesicInverseResult(0.0, 0.0, 0.0, true)
            cosSigma = sinU1 * sinU2 + cosU1 * cosU2 * cosLambda
            sigma = atan2(sinSigma, cosSigma)
            sinAlpha = cosU1 * cosU2 * sinLambda / sinSigma
            cosSqAlpha = 1.0 - sinAlpha * sinAlpha
            cos2SigmaM = if (cosSqAlpha > 1e-16) cosSigma - 2.0 * sinU1 * sinU2 / cosSqAlpha else 0.0
            val c = WGS84_F / 16.0 * cosSqAlpha * (4.0 + WGS84_F * (4.0 - 3.0 * cosSqAlpha))
            lambdaPrev = lambda
            lambda = l + (1.0 - c) * WGS84_F * sinAlpha *
                (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2))))
            if (abs(lambda - lambdaPrev) < 1e-12) {
                converged = true
                return@repeat
            }
        }

        if (!converged) return sphericalFallback(lat1Deg, lon1Deg, lat2Deg, lon2Deg)

        val uSq = cosSqAlpha * (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_B * WGS84_B)
        val aCoeff = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val bCoeff = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))
        val deltaSigma = bCoeff * sinSigma * (
            cos2SigmaM + bCoeff / 4.0 * (
                cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2)) -
                    bCoeff / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma.pow(2)) * (-3.0 + 4.0 * cos2SigmaM.pow(2))
                )
            )
        val distance = WGS84_B * aCoeff * (sigma - deltaSigma)
        val initial = atan2(
            cosU2 * sin(lambda),
            cosU1 * sinU2 - sinU1 * cosU2 * cos(lambda)
        )
        val final = atan2(
            cosU1 * sin(lambda),
            -sinU1 * cosU2 + cosU1 * sinU2 * cos(lambda)
        )
        return GeodesicInverseResult(
            distanceM = distance,
            initialBearingDeg = normaliseDegrees(Math.toDegrees(initial)),
            finalBearingDeg = normaliseDegrees(Math.toDegrees(final)),
            converged = true
        )
    }

    fun directVincenty(latDeg: Double, lonDeg: Double, initialBearingDeg: Double, distanceM: Double): GeodesicDirectResult {
        require(latDeg in -90.0..90.0) { "Latitude must be between -90 and 90 degrees." }
        require(lonDeg in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
        require(distanceM >= 0.0) { "Distance cannot be negative." }

        val alpha1 = Math.toRadians(normaliseDegrees(initialBearingDeg))
        val phi1 = Math.toRadians(latDeg)
        val lambda1 = Math.toRadians(lonDeg)
        val tanU1 = (1.0 - WGS84_F) * tan(phi1)
        val cosU1 = 1.0 / sqrt(1.0 + tanU1 * tanU1)
        val sinU1 = tanU1 * cosU1
        val sigma1 = atan2(tanU1, cos(alpha1))
        val sinAlpha = cosU1 * sin(alpha1)
        val cosSqAlpha = 1.0 - sinAlpha * sinAlpha
        val uSq = cosSqAlpha * (WGS84_A * WGS84_A - WGS84_B * WGS84_B) / (WGS84_B * WGS84_B)
        val aCoeff = 1.0 + uSq / 16384.0 * (4096.0 + uSq * (-768.0 + uSq * (320.0 - 175.0 * uSq)))
        val bCoeff = uSq / 1024.0 * (256.0 + uSq * (-128.0 + uSq * (74.0 - 47.0 * uSq)))
        var sigma = if (distanceM == 0.0) 0.0 else distanceM / (WGS84_B * aCoeff)
        var previous: Double
        var converged = distanceM == 0.0
        repeat(200) {
            val cos2SigmaM = cos(2.0 * sigma1 + sigma)
            val sinSigma = sin(sigma)
            val cosSigma = cos(sigma)
            val deltaSigma = bCoeff * sinSigma * (
                cos2SigmaM + bCoeff / 4.0 * (
                    cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2)) -
                        bCoeff / 6.0 * cos2SigmaM * (-3.0 + 4.0 * sinSigma.pow(2)) * (-3.0 + 4.0 * cos2SigmaM.pow(2))
                    )
                )
            previous = sigma
            sigma = distanceM / (WGS84_B * aCoeff) + deltaSigma
            if (abs(sigma - previous) < 1e-12) {
                converged = true
                return@repeat
            }
        }

        val sinSigma = sin(sigma)
        val cosSigma = cos(sigma)
        val cos2SigmaM = cos(2.0 * sigma1 + sigma)
        val tmp = sinU1 * sinSigma - cosU1 * cosSigma * cos(alpha1)
        val phi2 = atan2(
            sinU1 * cosSigma + cosU1 * sinSigma * cos(alpha1),
            (1.0 - WGS84_F) * sqrt(sinAlpha * sinAlpha + tmp * tmp)
        )
        val lambda = atan2(
            sinSigma * sin(alpha1),
            cosU1 * cosSigma - sinU1 * sinSigma * cos(alpha1)
        )
        val c = WGS84_F / 16.0 * cosSqAlpha * (4.0 + WGS84_F * (4.0 - 3.0 * cosSqAlpha))
        val l = lambda - (1.0 - c) * WGS84_F * sinAlpha *
            (sigma + c * sinSigma * (cos2SigmaM + c * cosSigma * (-1.0 + 2.0 * cos2SigmaM.pow(2))))
        val lambda2 = lambda1 + l
        val alpha2 = atan2(sinAlpha, -tmp)
        return GeodesicDirectResult(
            latitude = Math.toDegrees(phi2),
            longitude = normaliseLongitude(Math.toDegrees(lambda2)),
            finalBearingDeg = normaliseDegrees(Math.toDegrees(alpha2)),
            converged = converged
        )
    }

    private fun sphericalFallback(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): GeodesicInverseResult {
        val r = 6371008.8
        val phi1 = Math.toRadians(lat1Deg)
        val phi2 = Math.toRadians(lat2Deg)
        val dPhi = phi2 - phi1
        val dLambda = Math.toRadians(lon2Deg - lon1Deg)
        val h = sin(dPhi / 2).pow(2) + cos(phi1) * cos(phi2) * sin(dLambda / 2).pow(2)
        val distance = 2.0 * r * atan2(sqrt(h), sqrt(1.0 - h))
        val initial = atan2(sin(dLambda) * cos(phi2), cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda))
        val reverse = atan2(-sin(dLambda) * cos(phi1), cos(phi2) * sin(phi1) - sin(phi2) * cos(phi1) * cos(dLambda))
        return GeodesicInverseResult(distance, normaliseDegrees(Math.toDegrees(initial)), normaliseDegrees(Math.toDegrees(reverse) + 180.0), false)
    }

    fun wgs84ToUtm(latitude: Double, longitude: Double): UtmCoordinate {
        require(latitude in -80.0..84.0) { "UTM is defined here only between 80°S and 84°N." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees." }
        var zone = floor((longitude + 180.0) / 6.0).toInt() + 1
        if (longitude == 180.0) zone = 60
        // Standard UTM exceptions for Norway and Svalbard.
        if (latitude in 56.0..<64.0 && longitude in 3.0..<12.0) zone = 32
        if (latitude in 72.0..<84.0) {
            zone = when {
                longitude in 0.0..<9.0 -> 31
                longitude in 9.0..<21.0 -> 33
                longitude in 21.0..<33.0 -> 35
                longitude in 33.0..<42.0 -> 37
                else -> zone
            }
        }

        val e2 = WGS84_F * (2.0 - WGS84_F)
        val ePrimeSq = e2 / (1.0 - e2)
        val phi = Math.toRadians(latitude)
        val lambda = Math.toRadians(longitude)
        val lambda0 = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val sinPhi = sin(phi)
        val cosPhi = cos(phi)
        val tanPhi = tan(phi)
        val n = WGS84_A / sqrt(1.0 - e2 * sinPhi * sinPhi)
        val t = tanPhi * tanPhi
        val c = ePrimeSq * cosPhi * cosPhi
        val a = cosPhi * (lambda - lambda0)
        val m = WGS84_A * (
            (1.0 - e2 / 4.0 - 3.0 * e2.pow(2) / 64.0 - 5.0 * e2.pow(3) / 256.0) * phi -
                (3.0 * e2 / 8.0 + 3.0 * e2.pow(2) / 32.0 + 45.0 * e2.pow(3) / 1024.0) * sin(2.0 * phi) +
                (15.0 * e2.pow(2) / 256.0 + 45.0 * e2.pow(3) / 1024.0) * sin(4.0 * phi) -
                (35.0 * e2.pow(3) / 3072.0) * sin(6.0 * phi)
            )
        val easting = UTM_K0 * n * (
            a + (1.0 - t + c) * a.pow(3) / 6.0 +
                (5.0 - 18.0 * t + t * t + 72.0 * c - 58.0 * ePrimeSq) * a.pow(5) / 120.0
            ) + 500000.0
        var northing = UTM_K0 * (
            m + n * tanPhi * (
                a * a / 2.0 +
                    (5.0 - t + 9.0 * c + 4.0 * c * c) * a.pow(4) / 24.0 +
                    (61.0 - 58.0 * t + t * t + 600.0 * c - 330.0 * ePrimeSq) * a.pow(6) / 720.0
                )
            )
        val hemisphere = if (latitude >= 0.0) "N" else "S"
        if (latitude < 0.0) northing += 10000000.0
        return UtmCoordinate(zone, hemisphere, easting, northing)
    }

    fun utmToWgs84(zone: Int, hemisphere: String, eastingM: Double, northingM: Double): LatLon {
        require(zone in 1..60) { "UTM zone must be 1 to 60." }
        val hemi = hemisphere.trim().uppercase()
        require(hemi == "N" || hemi == "S") { "Hemisphere must be N or S." }
        require(eastingM in 100000.0..1000000.0) { "UTM easting is outside the supported range." }
        require(northingM in 0.0..10000000.0) { "UTM northing is outside the supported range." }

        val e2 = WGS84_F * (2.0 - WGS84_F)
        val e1 = (1.0 - sqrt(1.0 - e2)) / (1.0 + sqrt(1.0 - e2))
        val ePrimeSq = e2 / (1.0 - e2)
        val x = eastingM - 500000.0
        var y = northingM
        if (hemi == "S") y -= 10000000.0
        val m = y / UTM_K0
        val mu = m / (WGS84_A * (1.0 - e2 / 4.0 - 3.0 * e2.pow(2) / 64.0 - 5.0 * e2.pow(3) / 256.0))
        val phi1 = mu +
            (3.0 * e1 / 2.0 - 27.0 * e1.pow(3) / 32.0) * sin(2.0 * mu) +
            (21.0 * e1.pow(2) / 16.0 - 55.0 * e1.pow(4) / 32.0) * sin(4.0 * mu) +
            (151.0 * e1.pow(3) / 96.0) * sin(6.0 * mu) +
            (1097.0 * e1.pow(4) / 512.0) * sin(8.0 * mu)
        val sinPhi1 = sin(phi1)
        val cosPhi1 = cos(phi1)
        val tanPhi1 = tan(phi1)
        val n1 = WGS84_A / sqrt(1.0 - e2 * sinPhi1 * sinPhi1)
        val r1 = WGS84_A * (1.0 - e2) / (1.0 - e2 * sinPhi1 * sinPhi1).pow(1.5)
        val t1 = tanPhi1 * tanPhi1
        val c1 = ePrimeSq * cosPhi1 * cosPhi1
        val d = x / (n1 * UTM_K0)
        val latitude = phi1 - (n1 * tanPhi1 / r1) * (
            d * d / 2.0 -
                (5.0 + 3.0 * t1 + 10.0 * c1 - 4.0 * c1 * c1 - 9.0 * ePrimeSq) * d.pow(4) / 24.0 +
                (61.0 + 90.0 * t1 + 298.0 * c1 + 45.0 * t1 * t1 - 252.0 * ePrimeSq - 3.0 * c1 * c1) * d.pow(6) / 720.0
            )
        val longitude0 = Math.toRadians((zone - 1) * 6 - 180 + 3.0)
        val longitude = longitude0 + (
            d - (1.0 + 2.0 * t1 + c1) * d.pow(3) / 6.0 +
                (5.0 - 2.0 * c1 + 28.0 * t1 - 3.0 * c1 * c1 + 8.0 * ePrimeSq + 24.0 * t1 * t1) * d.pow(5) / 120.0
            ) / cosPhi1
        return LatLon(Math.toDegrees(latitude), normaliseLongitude(Math.toDegrees(longitude)))
    }

    fun normalisePlane(strikeDeg: Double, dipDeg: Double, convention: String, explicitDipDirectionDeg: Double?): StructuralPlane {
        require(dipDeg in 0.0..90.0) { "Dip must be between 0 and 90 degrees." }
        var strike = normaliseDegrees(strikeDeg)
        val dipDirection = when (convention.lowercase()) {
            "right_hand_rule" -> normaliseDegrees(strike + 90.0)
            "explicit_dip_direction" -> {
                val dd = explicitDipDirectionDeg ?: error("Dip direction is required for explicit_dip_direction.")
                val candidateA = normaliseDegrees(dd - 90.0)
                val candidateB = normaliseDegrees(dd + 90.0)
                strike = if (angularDistance(strike, candidateA) <= angularDistance(strike, candidateB)) candidateA else candidateB
                normaliseDegrees(dd)
            }
            else -> error("Convention must be right_hand_rule or explicit_dip_direction.")
        }
        return StructuralPlane(
            strikeDeg = strike,
            dipDeg = dipDeg,
            dipDirectionDeg = dipDirection,
            poleTrendDeg = normaliseDegrees(dipDirection + 180.0),
            polePlungeDeg = 90.0 - dipDeg
        )
    }

    fun normaliseLine(trendDeg: Double, plungeDeg: Double): StructuralLine {
        var trend = normaliseDegrees(trendDeg)
        var plunge = plungeDeg
        require(plunge in -90.0..90.0) { "Plunge must be between -90 and 90 degrees." }
        if (plunge < 0.0) {
            trend = normaliseDegrees(trend + 180.0)
            plunge = -plunge
        }
        return StructuralLine(trend, plunge)
    }

    fun planeIntersection(strike1Deg: Double, dip1Deg: Double, strike2Deg: Double, dip2Deg: Double): StructuralLine {
        val p1 = normalisePlane(strike1Deg, dip1Deg, "right_hand_rule", null)
        val p2 = normalisePlane(strike2Deg, dip2Deg, "right_hand_rule", null)
        val n1 = lineToVector(p1.poleTrendDeg, p1.polePlungeDeg)
        val n2 = lineToVector(p2.poleTrendDeg, p2.polePlungeDeg)
        var x = n1[1] * n2[2] - n1[2] * n2[1]
        var y = n1[2] * n2[0] - n1[0] * n2[2]
        var z = n1[0] * n2[1] - n1[1] * n2[0]
        val norm = sqrt(x * x + y * y + z * z)
        require(norm > 1e-10) { "Planes are parallel or too nearly parallel to define a stable intersection." }
        x /= norm; y /= norm; z /= norm
        if (z > 0.0) { x = -x; y = -y; z = -z }
        val horizontal = sqrt(x * x + y * y)
        val trend = normaliseDegrees(Math.toDegrees(atan2(x, y)))
        val plunge = Math.toDegrees(atan2(-z, horizontal))
        return StructuralLine(trend, plunge)
    }

    private fun lineToVector(trendDeg: Double, plungeDeg: Double): DoubleArray {
        val t = Math.toRadians(trendDeg)
        val p = Math.toRadians(plungeDeg)
        return doubleArrayOf(cos(p) * sin(t), cos(p) * cos(t), -sin(p)) // east, north, up
    }

    fun classifySoilTexture(sandPct: Double, siltPct: Double, clayPct: Double, tolerancePct: Double): String {
        require(sandPct >= 0 && siltPct >= 0 && clayPct >= 0) { "Sand, silt and clay percentages cannot be negative." }
        require(sandPct <= 100 && siltPct <= 100 && clayPct <= 100) { "Sand, silt and clay percentages cannot exceed 100." }
        val total = sandPct + siltPct + clayPct
        require(abs(total - 100.0) <= tolerancePct) { "Sand, silt and clay must total 100% within the configured tolerance." }
        val scale = 100.0 / total
        val sand = sandPct * scale
        val silt = siltPct * scale
        val clay = clayPct * scale
        return when {
            sand >= 85.0 && (silt + 1.5 * clay) < 15.0 -> "Sand"
            sand >= 70.0 && sand < 90.0 && (silt + 1.5 * clay) >= 15.0 && (silt + 2.0 * clay) < 30.0 -> "Loamy sand"
            (clay >= 7.0 && clay < 20.0 && sand > 52.0 && (silt + 2.0 * clay) >= 30.0) ||
                (clay < 7.0 && silt < 50.0 && (silt + 2.0 * clay) >= 30.0) -> "Sandy loam"
            clay >= 7.0 && clay < 27.0 && silt >= 28.0 && silt < 50.0 && sand <= 52.0 -> "Loam"
            (silt >= 50.0 && clay >= 12.0 && clay < 27.0) || (silt >= 50.0 && silt < 80.0 && clay < 12.0) -> "Silt loam"
            silt >= 80.0 && clay < 12.0 -> "Silt"
            clay >= 20.0 && clay < 35.0 && silt < 28.0 && sand > 45.0 -> "Sandy clay loam"
            clay >= 27.0 && clay < 40.0 && sand > 20.0 && sand <= 45.0 -> "Clay loam"
            clay >= 27.0 && clay < 40.0 && sand <= 20.0 -> "Silty clay loam"
            clay >= 35.0 && sand > 45.0 -> "Sandy clay"
            clay >= 40.0 && silt >= 40.0 -> "Silty clay"
            clay >= 40.0 && sand <= 45.0 && silt < 40.0 -> "Clay"
            else -> error("Composition lies on an unclassified boundary; review the input precision.")
        }
    }

    fun grainSize(inputMode: String, diameterMm: Double?, phi: Double?): GrainSizeResult {
        val mm = when (inputMode.lowercase()) {
            "diameter_mm" -> diameterMm ?: error("Diameter in millimetres is required.")
            "phi" -> 2.0.pow(-(phi ?: error("Phi is required.")))
            else -> error("Input mode must be diameter_mm or phi.")
        }
        require(mm > 0.0) { "Grain diameter must be greater than zero." }
        val phiValue = -ln(mm) / ln(2.0)
        val wentworth = when {
            mm >= 256.0 -> "Boulder"
            mm >= 64.0 -> "Cobble"
            mm >= 4.0 -> "Pebble"
            mm >= 2.0 -> "Granule"
            mm >= 1.0 -> "Very coarse sand"
            mm >= 0.5 -> "Coarse sand"
            mm >= 0.25 -> "Medium sand"
            mm >= 0.125 -> "Fine sand"
            mm >= 0.0625 -> "Very fine sand"
            mm >= 0.03125 -> "Coarse silt"
            mm >= 0.015625 -> "Medium silt"
            mm >= 0.0078125 -> "Fine silt"
            mm >= 0.00390625 -> "Very fine silt"
            else -> "Clay"
        }
        val broad = when {
            mm >= 2.0 -> "Gravel"
            mm >= 0.0625 -> "Sand"
            mm >= 0.00390625 -> "Silt"
            else -> "Clay"
        }
        return GrainSizeResult(mm, phiValue, wentworth, broad)
    }

    /**
     * Compact hierarchy derived from the International Commission on Stratigraphy
     * International Chronostratigraphic Chart, version 2026/06.
     * © International Commission on Stratigraphy, 2026; CC BY 4.0.
     * Source: https://github.com/i-c-stratigraphy/chart
     */
    fun geologicalTime(ageMa: Double): GeoTimeResult {
        require(ageMa in 0.0..4600.0) { "Age must be between 0 and 4600 Ma." }
        val eon = when {
            ageMa < 538.8 -> "Phanerozoic"
            ageMa < 2500.0 -> "Proterozoic"
            ageMa < 4031.0 -> "Archean"
            else -> "Hadean"
        }
        val era = when {
            ageMa < 66.0 -> "Cenozoic"
            ageMa < 251.902 -> "Mesozoic"
            ageMa < 538.8 -> "Paleozoic"
            else -> ""
        }
        val period = when {
            ageMa < 2.58 -> "Quaternary"
            ageMa < 23.04 -> "Neogene"
            ageMa < 66.0 -> "Paleogene"
            ageMa < 143.1 -> "Cretaceous"
            ageMa < 201.4 -> "Jurassic"
            ageMa < 251.902 -> "Triassic"
            ageMa < 298.9 -> "Permian"
            ageMa < 358.86 -> "Carboniferous"
            ageMa < 419.62 -> "Devonian"
            ageMa < 443.1 -> "Silurian"
            ageMa < 486.85 -> "Ordovician"
            ageMa < 538.8 -> "Cambrian"
            else -> ""
        }
        val subdivision = when {
            ageMa < 0.0117 -> "Holocene"
            ageMa < 2.58 -> "Pleistocene"
            ageMa < 5.333 -> "Pliocene"
            ageMa < 23.04 -> "Miocene"
            ageMa < 33.9 -> "Oligocene"
            ageMa < 56.0 -> "Eocene"
            ageMa < 66.0 -> "Paleocene"
            ageMa < 100.5 -> "Upper Cretaceous"
            ageMa < 143.1 -> "Lower Cretaceous"
            ageMa < 161.5 -> "Upper Jurassic"
            ageMa < 174.7 -> "Middle Jurassic"
            ageMa < 201.4 -> "Lower Jurassic"
            ageMa < 237.0 -> "Upper Triassic"
            ageMa < 247.0 -> "Middle Triassic"
            ageMa < 251.902 -> "Lower Triassic"
            ageMa < 259.857 -> "Lopingian"
            ageMa < 274.4 -> "Guadalupian"
            ageMa < 298.9 -> "Cisuralian"
            ageMa < 323.4 -> "Pennsylvanian"
            ageMa < 358.86 -> "Mississippian"
            ageMa < 382.31 -> "Upper Devonian"
            ageMa < 393.47 -> "Middle Devonian"
            ageMa < 419.62 -> "Lower Devonian"
            ageMa < 422.7 -> "Pridoli"
            ageMa < 426.7 -> "Ludlow"
            ageMa < 432.9 -> "Wenlock"
            ageMa < 443.1 -> "Llandovery"
            ageMa < 458.2 -> "Upper Ordovician"
            ageMa < 471.3 -> "Middle Ordovician"
            ageMa < 486.85 -> "Lower Ordovician"
            ageMa < 497.0 -> "Furongian"
            ageMa < 506.5 -> "Miaolingian"
            ageMa < 521.0 -> "Cambrian Series 2"
            ageMa < 538.8 -> "Terreneuvian"
            else -> ""
        }
        return GeoTimeResult(ageMa, eon, era, period, subdivision, ICS_VERSION)
    }

    fun averageGnss(fixes: List<GnssFix>, weighting: String): GnssAverage {
        require(fixes.size >= 2) { "At least two accepted GNSS fixes are required." }
        val mode = weighting.lowercase()
        require(mode == "equal" || mode == "inverse_variance") { "Weighting must be equal or inverse_variance." }
        var x = 0.0; var y = 0.0; var z = 0.0; var totalWeight = 0.0
        var altitudeWeighted = 0.0; var altitudeWeight = 0.0
        fixes.forEach { fix ->
            val weight = if (mode == "inverse_variance") 1.0 / fix.accuracyM.coerceAtLeast(0.5).pow(2) else 1.0
            val lat = Math.toRadians(fix.latitude)
            val lon = Math.toRadians(fix.longitude)
            x += weight * cos(lat) * cos(lon)
            y += weight * cos(lat) * sin(lon)
            z += weight * sin(lat)
            totalWeight += weight
            fix.altitudeM?.let { altitudeWeighted += weight * it; altitudeWeight += weight }
        }
        x /= totalWeight; y /= totalWeight; z /= totalWeight
        val lon = atan2(y, x)
        val hyp = sqrt(x * x + y * y)
        val lat = atan2(z, hyp)
        val meanLat = Math.toDegrees(lat)
        val meanLon = normaliseLongitude(Math.toDegrees(lon))
        val spreads = fixes.map { inverseVincenty(meanLat, meanLon, it.latitude, it.longitude).distanceM }
        return GnssAverage(
            latitude = meanLat,
            longitude = meanLon,
            altitudeM = if (altitudeWeight > 0.0) altitudeWeighted / altitudeWeight else null,
            acceptedFixes = fixes.size,
            meanReportedAccuracyM = fixes.map { it.accuracyM }.average(),
            rmsSpreadM = sqrt(spreads.map { it * it }.average()),
            maxSpreadM = spreads.maxOrNull() ?: 0.0,
            weighting = mode
        )
    }

    private fun angularDistance(a: Double, b: Double): Double = abs(normaliseSignedDegrees(a - b))
    private fun normaliseLongitude(value: Double): Double = ((value + 540.0) % 360.0) - 180.0
}
