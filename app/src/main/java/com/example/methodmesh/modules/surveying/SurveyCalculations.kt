package com.example.methodmesh.modules.surveying

import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pure Kotlin surveying mathematics shared by atomic capabilities and the
 * persistent traverse/levelling dashboards.
 *
 * Coordinate convention throughout the local-grid functions:
 *   easting = X, northing = Y, azimuth is clockwise from grid north.
 * Positive offsets are to the RIGHT of the baseline direction.
 */
object SurveyCalculations {
    const val EARTH_RADIUS_M = 6_371_008.8

    data class Point(
        val id: String,
        val easting: Double,
        val northing: Double,
        val elevation: Double? = null
    )

    data class BearingDistance(
        val deltaEasting: Double,
        val deltaNorthing: Double,
        val horizontalDistance: Double,
        val azimuthDeg: Double,
        val quadrantBearing: String,
        val elevationDifference: Double? = null,
        val slopeDistance: Double? = null,
        val slopePercent: Double? = null
    )

    data class OffsetResult(
        val easting: Double,
        val northing: Double,
        val chainage: Double,
        val offsetRight: Double
    )

    data class TraverseLeg(
        val toId: String,
        val bearingDeg: Double,
        val distance: Double
    )

    data class TraversePoint(
        val id: String,
        val rawEasting: Double,
        val rawNorthing: Double,
        val adjustedEasting: Double,
        val adjustedNorthing: Double
    )

    data class TraverseResult(
        val points: List<TraversePoint>,
        val totalDistance: Double,
        val rawEndEasting: Double,
        val rawEndNorthing: Double,
        val adjustedEndEasting: Double,
        val adjustedEndNorthing: Double,
        val misclosureEasting: Double?,
        val misclosureNorthing: Double?,
        val linearMisclosure: Double?,
        val relativePrecision: Double?,
        val adjustmentMode: String,
        val closurePass: Boolean?
    )

    data class PolygonResult(
        val signedArea: Double,
        val area: Double,
        val perimeter: Double,
        val centroidEasting: Double?,
        val centroidNorthing: Double?
    )

    data class LevelObservation(
        val station: String,
        val type: String,
        val reading: Double,
        val distanceFromPrevious: Double = 0.0
    )

    data class LevelRow(
        val index: Int,
        val station: String,
        val type: String,
        val reading: Double,
        val distanceFromPrevious: Double,
        val cumulativeDistance: Double,
        val backsight: Double?,
        val intermediateSight: Double?,
        val foresight: Double?,
        val heightOfCollimation: Double?,
        val rise: Double?,
        val fall: Double?,
        val reducedLevel: Double?,
        val correction: Double?,
        val adjustedReducedLevel: Double?
    )

    data class LevelBookResult(
        val rows: List<LevelRow>,
        val startReducedLevel: Double,
        val finalReducedLevel: Double?,
        val adjustedFinalReducedLevel: Double?,
        val knownCloseReducedLevel: Double?,
        val closureError: Double?,
        val totalDistance: Double,
        val sumBacksight: Double,
        val sumForesight: Double,
        val arithmeticDifference: Double?,
        val arithmeticCheckError: Double?,
        val valid: Boolean,
        val message: String
    )

    data class GpsFix(val latitude: Double, val longitude: Double, val accuracyM: Double? = null)

    data class GpsAverageResult(
        val latitude: Double,
        val longitude: Double,
        val weighted: Boolean,
        val sampleCount: Int,
        val rmsScatterM: Double,
        val maxScatterM: Double,
        val meanReportedAccuracyM: Double?
    )

    data class GeodesicInverse(
        val distanceM: Double,
        val initialBearingDeg: Double,
        val finalBearingDeg: Double
    )

    data class IntersectionResult(
        val valid: Boolean,
        val easting: Double?,
        val northing: Double?,
        val distanceFromA: Double?,
        val distanceFromB: Double?,
        val crossingAngleDeg: Double?,
        val message: String
    )

    fun normalizeAzimuth(degrees: Double): Double {
        val result = degrees % 360.0
        return if (result < 0.0) result + 360.0 else result
    }

    fun inversePlanar(a: Point, b: Point): BearingDistance {
        val de = b.easting - a.easting
        val dn = b.northing - a.northing
        val horizontal = hypot(de, dn)
        val azimuth = if (horizontal == 0.0) 0.0 else normalizeAzimuth(Math.toDegrees(atan2(de, dn)))
        val dz = if (a.elevation != null && b.elevation != null) b.elevation - a.elevation else null
        val slopeDistance = dz?.let { hypot(horizontal, it) }
        val slopePercent = dz?.takeIf { horizontal > 0.0 }?.let { it / horizontal * 100.0 }
        return BearingDistance(
            deltaEasting = de,
            deltaNorthing = dn,
            horizontalDistance = horizontal,
            azimuthDeg = azimuth,
            quadrantBearing = quadrantBearing(azimuth),
            elevationDifference = dz,
            slopeDistance = slopeDistance,
            slopePercent = slopePercent
        )
    }

    fun forwardPlanar(start: Point, azimuthDeg: Double, horizontalDistance: Double, id: String = "target"): Point {
        require(horizontalDistance >= 0.0 && horizontalDistance.isFinite()) { "Distance must be finite and non-negative." }
        val az = Math.toRadians(normalizeAzimuth(azimuthDeg))
        return Point(
            id = id,
            easting = start.easting + horizontalDistance * sin(az),
            northing = start.northing + horizontalDistance * cos(az),
            elevation = start.elevation
        )
    }

    fun offsetPoint(start: Point, end: Point, chainage: Double, offsetRight: Double, id: String = "offset"): Point {
        val de = end.easting - start.easting
        val dn = end.northing - start.northing
        val length = hypot(de, dn)
        require(length > 0.0) { "Baseline start and end must be different points." }
        val ue = de / length
        val un = dn / length
        // Right-hand perpendicular: a north-going line has positive-right toward east.
        val re = un
        val rn = -ue
        return Point(
            id = id,
            easting = start.easting + ue * chainage + re * offsetRight,
            northing = start.northing + un * chainage + rn * offsetRight
        )
    }

    fun chainageOffset(start: Point, end: Point, point: Point): OffsetResult {
        val de = end.easting - start.easting
        val dn = end.northing - start.northing
        val length = hypot(de, dn)
        require(length > 0.0) { "Baseline start and end must be different points." }
        val ue = de / length
        val un = dn / length
        val pe = point.easting - start.easting
        val pn = point.northing - start.northing
        val chainage = pe * ue + pn * un
        val rightE = un
        val rightN = -ue
        val offset = pe * rightE + pn * rightN
        return OffsetResult(point.easting, point.northing, chainage, offset)
    }

    fun traverse(
        start: Point,
        legs: List<TraverseLeg>,
        knownClose: Point? = null,
        adjustmentMode: String = "none",
        minimumRelativePrecision: Double? = null
    ): TraverseResult {
        require(legs.isNotEmpty()) { "At least one traverse leg is required." }
        require(legs.all { it.distance >= 0.0 && it.distance.isFinite() && it.bearingDeg.isFinite() }) {
            "Traverse bearings and distances must be finite and distances non-negative."
        }
        val rawIncrements = legs.map { leg ->
            val az = Math.toRadians(normalizeAzimuth(leg.bearingDeg))
            leg.distance * sin(az) to leg.distance * cos(az)
        }
        val totalDistance = legs.sumOf { it.distance }
        val rawEndE = start.easting + rawIncrements.sumOf { it.first }
        val rawEndN = start.northing + rawIncrements.sumOf { it.second }
        val misE = knownClose?.let { rawEndE - it.easting }
        val misN = knownClose?.let { rawEndN - it.northing }
        val linear = if (misE != null && misN != null) hypot(misE, misN) else null
        val relative = if (linear != null && linear > 0.0 && totalDistance > 0.0) totalDistance / linear
        else if (linear == 0.0 && totalDistance > 0.0) Double.POSITIVE_INFINITY
        else null

        val mode = adjustmentMode.trim().lowercase(Locale.US).ifBlank { "none" }
        val corrections = when {
            knownClose == null || mode == "none" -> rawIncrements.map { 0.0 to 0.0 }
            mode == "bowditch" || mode == "compass" -> {
                if (totalDistance <= 0.0) rawIncrements.map { 0.0 to 0.0 }
                else legs.map { leg ->
                    val f = leg.distance / totalDistance
                    -(misE ?: 0.0) * f to -(misN ?: 0.0) * f
                }
            }
            mode == "transit" -> {
                val sumAbsE = rawIncrements.sumOf { abs(it.first) }
                val sumAbsN = rawIncrements.sumOf { abs(it.second) }
                rawIncrements.map { (de, dn) ->
                    val ce = if (sumAbsE > 0.0) -(misE ?: 0.0) * abs(de) / sumAbsE else 0.0
                    val cn = if (sumAbsN > 0.0) -(misN ?: 0.0) * abs(dn) / sumAbsN else 0.0
                    ce to cn
                }
            }
            else -> error("Unknown traverse adjustment mode '$adjustmentMode'.")
        }

        var rawE = start.easting
        var rawN = start.northing
        var adjE = start.easting
        var adjN = start.northing
        val points = mutableListOf(
            TraversePoint(start.id, start.easting, start.northing, start.easting, start.northing)
        )
        legs.forEachIndexed { index, leg ->
            rawE += rawIncrements[index].first
            rawN += rawIncrements[index].second
            adjE += rawIncrements[index].first + corrections[index].first
            adjN += rawIncrements[index].second + corrections[index].second
            points += TraversePoint(leg.toId, rawE, rawN, adjE, adjN)
        }

        val pass = if (minimumRelativePrecision != null && relative != null) {
            relative >= minimumRelativePrecision
        } else null

        return TraverseResult(
            points = points,
            totalDistance = totalDistance,
            rawEndEasting = rawEndE,
            rawEndNorthing = rawEndN,
            adjustedEndEasting = adjE,
            adjustedEndNorthing = adjN,
            misclosureEasting = misE,
            misclosureNorthing = misN,
            linearMisclosure = linear,
            relativePrecision = relative,
            adjustmentMode = mode,
            closurePass = pass
        )
    }

    fun polygon(points: List<Point>): PolygonResult {
        require(points.size >= 3) { "At least three coordinates are required for an area." }
        var crossSum = 0.0
        var perimeter = 0.0
        var cxNumerator = 0.0
        var cyNumerator = 0.0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            val cross = a.easting * b.northing - b.easting * a.northing
            crossSum += cross
            cxNumerator += (a.easting + b.easting) * cross
            cyNumerator += (a.northing + b.northing) * cross
            perimeter += hypot(b.easting - a.easting, b.northing - a.northing)
        }
        val signedArea = crossSum / 2.0
        val area = abs(signedArea)
        val centroidE = if (abs(crossSum) > 1e-12) cxNumerator / (3.0 * crossSum) else null
        val centroidN = if (abs(crossSum) > 1e-12) cyNumerator / (3.0 * crossSum) else null
        return PolygonResult(signedArea, area, perimeter, centroidE, centroidN)
    }

    /**
     * Reduce a standard differential-levelling observation sequence.
     * Input is one staff reading per row: station,type(BS|IS|FS),reading,distance.
     * A change point is therefore normally represented by FS followed by BS at
     * the same station ID.
     */
    fun reduceLevelBook(
        startReducedLevel: Double,
        observations: List<LevelObservation>,
        knownCloseReducedLevel: Double? = null,
        distributeClosure: Boolean = true
    ): LevelBookResult {
        if (observations.isEmpty()) {
            return LevelBookResult(
                rows = emptyList(), startReducedLevel = startReducedLevel,
                finalReducedLevel = null, adjustedFinalReducedLevel = null,
                knownCloseReducedLevel = knownCloseReducedLevel, closureError = null,
                totalDistance = 0.0, sumBacksight = 0.0, sumForesight = 0.0,
                arithmeticDifference = null, arithmeticCheckError = null,
                valid = false, message = "No levelling observations supplied."
            )
        }

        val pointRls = mutableMapOf<String, Double>()
        var currentHi: Double? = null
        var lastPointRl: Double? = null
        var lastStation: String? = null
        var cumulativeDistance = 0.0
        var sumBs = 0.0
        var sumFs = 0.0
        var valid = true
        var message = "OK"
        val temp = mutableListOf<LevelRow>()

        observations.forEachIndexed { index, obsRaw ->
            val type = obsRaw.type.trim().uppercase(Locale.US)
            val obs = obsRaw.copy(type = type)
            if (!obs.reading.isFinite() || obs.reading < 0.0 || !obs.distanceFromPrevious.isFinite() || obs.distanceFromPrevious < 0.0) {
                valid = false
                message = "Reading and distance values must be finite and non-negative."
                return@forEachIndexed
            }
            cumulativeDistance += obs.distanceFromPrevious
            when (type) {
                "BS" -> {
                    val stationRl = pointRls[obs.station]
                        ?: if (index == 0) startReducedLevel
                        else if (obs.station == lastStation) lastPointRl
                        else null
                    if (stationRl == null) {
                        valid = false
                        message = "Backsight at '${obs.station}' has no known reduced level. Use the same station ID for a change point FS/BS pair."
                    } else {
                        currentHi = stationRl + obs.reading
                        pointRls[obs.station] = stationRl
                        lastPointRl = stationRl
                        lastStation = obs.station
                    }
                    sumBs += obs.reading
                    temp += LevelRow(
                        index + 1, obs.station, type, obs.reading, obs.distanceFromPrevious,
                        cumulativeDistance, obs.reading, null, null, currentHi,
                        null, null, stationRl, null, null
                    )
                }
                "IS", "FS" -> {
                    val hi = currentHi
                    if (hi == null) {
                        valid = false
                        message = "An IS/FS reading was encountered before a valid backsight established height of collimation."
                        temp += LevelRow(index + 1, obs.station, type, obs.reading, obs.distanceFromPrevious, cumulativeDistance,
                            null, if (type == "IS") obs.reading else null, if (type == "FS") obs.reading else null,
                            null, null, null, null, null, null)
                    } else {
                        val rl = hi - obs.reading
                        val delta = lastPointRl?.let { rl - it }
                        val rise = delta?.takeIf { it > 0.0 }
                        val fall = delta?.takeIf { it < 0.0 }?.let { -it }
                        pointRls[obs.station] = rl
                        lastPointRl = rl
                        lastStation = obs.station
                        if (type == "FS") sumFs += obs.reading
                        temp += LevelRow(
                            index + 1, obs.station, type, obs.reading, obs.distanceFromPrevious, cumulativeDistance,
                            null, if (type == "IS") obs.reading else null, if (type == "FS") obs.reading else null,
                            hi, rise, fall, rl, null, null
                        )
                    }
                }
                else -> {
                    valid = false
                    message = "Unknown levelling observation type '$type'; use BS, IS or FS."
                    temp += LevelRow(index + 1, obs.station, type, obs.reading, obs.distanceFromPrevious, cumulativeDistance,
                        null, null, null, currentHi, null, null, null, null, null)
                }
            }
        }

        val finalRl = temp.lastOrNull { it.reducedLevel != null && it.type != "BS" }?.reducedLevel
            ?: temp.lastOrNull { it.reducedLevel != null }?.reducedLevel
        val closure = if (finalRl != null && knownCloseReducedLevel != null) finalRl - knownCloseReducedLevel else null
        val totalDistance = temp.maxOfOrNull { it.cumulativeDistance } ?: 0.0
        val finalIndex = temp.indexOfLast { it.reducedLevel != null }.coerceAtLeast(0)
        val adjustedRows = temp.mapIndexed { idx, row ->
            val correction = if (
                distributeClosure && closure != null && row.reducedLevel != null
            ) {
                val fraction = if (totalDistance > 0.0) {
                    row.cumulativeDistance / totalDistance
                } else if (finalIndex > 0) {
                    min(idx, finalIndex).toDouble() / finalIndex.toDouble()
                } else 1.0
                -closure * fraction.coerceIn(0.0, 1.0)
            } else null
            row.copy(
                correction = correction,
                adjustedReducedLevel = row.reducedLevel?.let { it + (correction ?: 0.0) }
            )
        }
        val adjustedFinal = adjustedRows.lastOrNull { it.adjustedReducedLevel != null && it.type != "BS" }?.adjustedReducedLevel
            ?: adjustedRows.lastOrNull { it.adjustedReducedLevel != null }?.adjustedReducedLevel
        val arithmeticDifference = finalRl?.let { it - startReducedLevel }
        val arithmeticCheck = arithmeticDifference?.let { (sumBs - sumFs) - it }
        if (valid && arithmeticCheck != null && abs(arithmeticCheck) > 1e-7) {
            valid = false
            message = "Levelling arithmetic check failed: ΣBS − ΣFS does not equal last RL − first RL."
        }
        return LevelBookResult(
            rows = adjustedRows,
            startReducedLevel = startReducedLevel,
            finalReducedLevel = finalRl,
            adjustedFinalReducedLevel = adjustedFinal,
            knownCloseReducedLevel = knownCloseReducedLevel,
            closureError = closure,
            totalDistance = totalDistance,
            sumBacksight = sumBs,
            sumForesight = sumFs,
            arithmeticDifference = arithmeticDifference,
            arithmeticCheckError = arithmeticCheck,
            valid = valid,
            message = message
        )
    }

    fun grade(rise: Double, horizontalDistance: Double): Map<String, Double> {
        require(horizontalDistance > 0.0) { "Horizontal distance must be greater than zero." }
        val ratio = rise / horizontalDistance
        val angle = Math.toDegrees(atan2(rise, horizontalDistance))
        return mapOf(
            "rise" to rise,
            "horizontal_distance" to horizontalDistance,
            "grade_percent" to ratio * 100.0,
            "angle_deg" to angle,
            "one_in_n" to if (rise == 0.0) Double.POSITIVE_INFINITY else abs(horizontalDistance / rise)
        )
    }

    fun averageGps(fixes: List<GpsFix>, accuracyWeighted: Boolean = true): GpsAverageResult {
        require(fixes.isNotEmpty()) { "At least one GPS fix is required." }
        require(fixes.all { it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }) { "Invalid latitude or longitude." }
        val useWeights = accuracyWeighted && fixes.any { (it.accuracyM ?: 0.0) > 0.0 }
        val weights = fixes.map {
            if (useWeights) 1.0 / max(it.accuracyM ?: 1000.0, 0.5).pow(2.0) else 1.0
        }
        val sumW = weights.sum()
        val lat = fixes.indices.sumOf { fixes[it].latitude * weights[it] } / sumW
        // Circular longitude averaging avoids failure around ±180°.
        val sinLon = fixes.indices.sumOf { sin(Math.toRadians(fixes[it].longitude)) * weights[it] } / sumW
        val cosLon = fixes.indices.sumOf { cos(Math.toRadians(fixes[it].longitude)) * weights[it] } / sumW
        val lon = Math.toDegrees(atan2(sinLon, cosLon))
        val distances = fixes.map { geodesicInverse(lat, lon, it.latitude, it.longitude).distanceM }
        val rms = sqrt(distances.map { it * it }.average())
        val maxScatter = distances.maxOrNull() ?: 0.0
        val reported = fixes.mapNotNull { it.accuracyM?.takeIf { a -> a.isFinite() && a >= 0.0 } }.takeIf { it.isNotEmpty() }?.average()
        return GpsAverageResult(lat, lon, useWeights, fixes.size, rms, maxScatter, reported)
    }

    /** Spherical WGS84-style great-circle inverse for field navigation/GPS QC, not cadastral geodesy. */
    fun geodesicInverse(lat1Deg: Double, lon1Deg: Double, lat2Deg: Double, lon2Deg: Double): GeodesicInverse {
        require(lat1Deg in -90.0..90.0 && lat2Deg in -90.0..90.0) { "Latitude out of range." }
        require(lon1Deg in -180.0..180.0 && lon2Deg in -180.0..180.0) { "Longitude out of range." }
        val lat1 = Math.toRadians(lat1Deg)
        val lat2 = Math.toRadians(lat2Deg)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(lon2Deg - lon1Deg)
        val h = sin(dLat / 2).pow(2.0) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2.0)
        val central = 2.0 * atan2(sqrt(h), sqrt(max(0.0, 1.0 - h)))
        val distance = EARTH_RADIUS_M * central
        val y1 = sin(dLon) * cos(lat2)
        val x1 = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val initial = normalizeAzimuth(Math.toDegrees(atan2(y1, x1)))
        val y2 = sin(-dLon) * cos(lat1)
        val x2 = cos(lat2) * sin(lat1) - sin(lat2) * cos(lat1) * cos(-dLon)
        val reverse = normalizeAzimuth(Math.toDegrees(atan2(y2, x2)))
        val finalBearing = normalizeAzimuth(reverse + 180.0)
        return GeodesicInverse(distance, initial, finalBearing)
    }

    fun bearingBearingIntersection(a: Point, bearingA: Double, b: Point, bearingB: Double): IntersectionResult {
        val aRad = Math.toRadians(normalizeAzimuth(bearingA))
        val bRad = Math.toRadians(normalizeAzimuth(bearingB))
        val vaE = sin(aRad)
        val vaN = cos(aRad)
        val vbE = sin(bRad)
        val vbN = cos(bRad)
        val det = vaE * (-vbN) - vaN * (-vbE)
        val crossing = acuteAngleBetween(bearingA, bearingB)
        if (abs(det) < 1e-10) {
            return IntersectionResult(false, null, null, null, null, crossing, "Bearings are parallel or nearly parallel.")
        }
        val rhsE = b.easting - a.easting
        val rhsN = b.northing - a.northing
        val t = (rhsE * (-vbN) - rhsN * (-vbE)) / det
        val u = (vaE * rhsN - vaN * rhsE) / det
        val e = a.easting + t * vaE
        val n = a.northing + t * vaN
        val forward = t >= 0.0 && u >= 0.0
        return IntersectionResult(
            valid = true,
            easting = e,
            northing = n,
            distanceFromA = abs(t),
            distanceFromB = abs(u),
            crossingAngleDeg = crossing,
            message = if (forward) "Forward rays intersect." else "Infinite bearing lines intersect, but at least one intersection lies behind its station."
        )
    }

    fun quadrantBearing(azimuthDeg: Double): String {
        val az = normalizeAzimuth(azimuthDeg)
        val (ns, angle, ew) = when {
            az <= 90.0 -> Triple("N", az, "E")
            az <= 180.0 -> Triple("S", 180.0 - az, "E")
            az <= 270.0 -> Triple("S", az - 180.0, "W")
            else -> Triple("N", 360.0 - az, "W")
        }
        return "$ns ${formatDms(angle)} $ew"
    }

    fun formatDms(degrees: Double): String {
        val d = abs(degrees)
        val whole = d.toInt()
        val minutesRaw = (d - whole) * 60.0
        val minutes = minutesRaw.toInt()
        val seconds = (minutesRaw - minutes) * 60.0
        return String.format(Locale.US, "%d°%02d′%05.2f″", whole, minutes, seconds)
    }

    fun parsePoints(text: String): List<Point> = splitRows(text).mapIndexed { index, row ->
        val p = row.split(',').map { it.trim() }
        require(p.size >= 2) { "Coordinate row ${index + 1} must be id,easting,northing or easting,northing." }
        val hasId = p.size >= 3 && p[0].toDoubleOrNull() == null
        val id = if (hasId) p[0].ifBlank { "P${index + 1}" } else "P${index + 1}"
        val offset = if (hasId) 1 else 0
        require(p.size >= offset + 2) { "Coordinate row ${index + 1} is incomplete." }
        val e = p[offset].toDoubleOrNull() ?: error("Invalid easting on coordinate row ${index + 1}.")
        val n = p[offset + 1].toDoubleOrNull() ?: error("Invalid northing on coordinate row ${index + 1}.")
        val z = p.getOrNull(offset + 2)?.toDoubleOrNull()
        Point(id, e, n, z)
    }

    fun parseTraverseLegs(text: String): List<TraverseLeg> = splitRows(text).mapIndexed { index, row ->
        val p = row.split(',').map { it.trim() }
        require(p.size >= 2) { "Traverse row ${index + 1} must be point,bearing,distance or bearing,distance." }
        val hasId = p.size >= 3 && p[0].toDoubleOrNull() == null
        val id = if (hasId) p[0].ifBlank { "P${index + 1}" } else "P${index + 1}"
        val offset = if (hasId) 1 else 0
        val bearing = p.getOrNull(offset)?.toDoubleOrNull() ?: error("Invalid bearing on traverse row ${index + 1}.")
        val distance = p.getOrNull(offset + 1)?.toDoubleOrNull() ?: error("Invalid distance on traverse row ${index + 1}.")
        TraverseLeg(id, bearing, distance)
    }

    fun parseLevelObservations(text: String): List<LevelObservation> = splitRows(text).mapIndexed { index, row ->
        val p = row.split(',').map { it.trim() }
        require(p.size >= 3) { "Levelling row ${index + 1} must be station,type,reading[,distance]." }
        LevelObservation(
            station = p[0].ifBlank { "P${index + 1}" },
            type = p[1],
            reading = p[2].toDoubleOrNull() ?: error("Invalid staff reading on levelling row ${index + 1}."),
            distanceFromPrevious = p.getOrNull(3)?.toDoubleOrNull() ?: 0.0
        )
    }

    fun parseGpsFixes(text: String): List<GpsFix> = splitRows(text).mapIndexed { index, row ->
        val p = row.split(',').map { it.trim() }
        require(p.size >= 2) { "GPS row ${index + 1} must be latitude,longitude[,accuracy_m]." }
        GpsFix(
            latitude = p[0].toDoubleOrNull() ?: error("Invalid latitude on GPS row ${index + 1}."),
            longitude = p[1].toDoubleOrNull() ?: error("Invalid longitude on GPS row ${index + 1}."),
            accuracyM = p.getOrNull(2)?.toDoubleOrNull()
        )
    }

    fun traverseCsv(result: TraverseResult): String = buildString {
        appendLine("point_id,raw_easting,raw_northing,adjusted_easting,adjusted_northing")
        result.points.forEach {
            appendLine(listOf(it.id.csv(), it.rawEasting.f6(), it.rawNorthing.f6(), it.adjustedEasting.f6(), it.adjustedNorthing.f6()).joinToString(","))
        }
    }.trimEnd()

    fun levelCsv(result: LevelBookResult): String = buildString {
        appendLine("index,station,type,reading_m,distance_m,cumulative_distance_m,backsight_m,intermediate_sight_m,foresight_m,height_of_collimation_m,rise_m,fall_m,reduced_level_m,correction_m,adjusted_reduced_level_m")
        result.rows.forEach { r ->
            appendLine(
                listOf(
                    r.index.toString(), r.station.csv(), r.type, r.reading.f4(), r.distanceFromPrevious.f3(), r.cumulativeDistance.f3(),
                    r.backsight.f4(), r.intermediateSight.f4(), r.foresight.f4(), r.heightOfCollimation.f4(),
                    r.rise.f4(), r.fall.f4(), r.reducedLevel.f4(), r.correction.f4(), r.adjustedReducedLevel.f4()
                ).joinToString(",")
            )
        }
    }.trimEnd()

    fun pointsCsv(points: List<Point>): String = buildString {
        appendLine("point_id,easting,northing,elevation")
        points.forEach { p -> appendLine(listOf(p.id.csv(), p.easting.f6(), p.northing.f6(), p.elevation.f4()).joinToString(",")) }
    }.trimEnd()

    private fun splitRows(text: String): List<String> = text
        .replace("\r\n", "\n")
        .replace(';', '\n')
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .toList()

    private fun acuteAngleBetween(a: Double, b: Double): Double {
        val diff = abs(normalizeAzimuth(a) - normalizeAzimuth(b)) % 360.0
        val small = min(diff, 360.0 - diff)
        return min(small, 180.0 - small)
    }

    private fun String.csv(): String = if (contains(',') || contains('"') || contains('\n')) {
        "\"${replace("\"", "\"\"")}\""
    } else this

    private fun Double.f6(): String = String.format(Locale.US, "%.6f", this)
    private fun Double.f4(): String = String.format(Locale.US, "%.4f", this)
    private fun Double.f3(): String = String.format(Locale.US, "%.3f", this)
    private fun Double?.f4(): String = this?.takeIf { it.isFinite() }?.let { String.format(Locale.US, "%.4f", it) }.orEmpty()
}
