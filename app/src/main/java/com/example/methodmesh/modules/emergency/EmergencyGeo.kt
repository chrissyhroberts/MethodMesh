package com.example.methodmesh.modules.emergency

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object EmergencyGeo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dPhi = Math.toRadians(lat2 - lat1)
        val dLambda = Math.toRadians(normalizeLongitudeDelta(lon2 - lon1))
        val a = sin(dPhi / 2) * sin(dPhi / 2) +
            cos(phi1) * cos(phi2) * sin(dLambda / 2) * sin(dLambda / 2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(a), sqrt(1 - a))
    }

    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(normalizeLongitudeDelta(lon2 - lon1))
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    fun rankExitOptions(
        latitude: Double,
        longitude: Double,
        pois: List<EmergencyStrategicPoi>,
        nationalityIso2: String? = null
    ): List<EmergencyRankedPoi> {
        val ranked = pois.map { poi ->
            EmergencyRankedPoi(
                poi = poi,
                distanceM = distanceM(latitude, longitude, poi.latitude, poi.longitude),
                bearingDeg = bearingDeg(latitude, longitude, poi.latitude, poi.longitude)
            )
        }
        val selected = mutableListOf<EmergencyRankedPoi>()
        selected += ranked.nearest(EmergencyPoiCategory.AIRPORT, 3)
        selected += ranked.nearest(EmergencyPoiCategory.PORT_OR_FERRY, 2)
        selected += ranked.nearest(EmergencyPoiCategory.LAND_BORDER, 2)

        val diplomatic = ranked
            .filter { it.poi.category in setOf(EmergencyPoiCategory.EMBASSY, EmergencyPoiCategory.CONSULATE_GENERAL, EmergencyPoiCategory.CONSULATE) }
            .let { candidates ->
                val iso = EmergencyCountries.normalizeCode(nationalityIso2)
                if (iso.isBlank()) candidates
                else candidates.filter { iso in it.poi.representedCountryCodes }
            }
            .sortedBy { it.distanceM }
            .take(1)
        selected += diplomatic
        return selected.distinctBy { it.poi.id }
    }

    private fun List<EmergencyRankedPoi>.nearest(category: EmergencyPoiCategory, count: Int): List<EmergencyRankedPoi> =
        filter { it.poi.category == category }.sortedBy { it.distanceM }.take(count)

    private fun normalizeLongitudeDelta(delta: Double): Double {
        var value = delta % 360.0
        if (value > 180.0) value -= 360.0
        if (value < -180.0) value += 360.0
        return value
    }
}
