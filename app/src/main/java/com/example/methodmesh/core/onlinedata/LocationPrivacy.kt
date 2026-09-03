package com.example.methodmesh.core.onlinedata

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt

data class RoundedLocation(
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
) {
    val latitudeString: String get() = "%.5f".format(latitude)
    val longitudeString: String get() = "%.5f".format(longitude)
}

fun roundLocationForDisclosure(
    latitude: Double,
    longitude: Double,
    radiusMeters: Int = 5_000
): RoundedLocation {
    val radius = radiusMeters.coerceAtLeast(1)
    val latitudeStep = radius / METERS_PER_DEGREE_LATITUDE
    val metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE * cos(latitude * PI / 180.0)
    val longitudeStep = if (metersPerDegreeLongitude.isFinite() && metersPerDegreeLongitude > 0.0) {
        radius / metersPerDegreeLongitude
    } else {
        180.0
    }

    return RoundedLocation(
        latitude = (latitude / latitudeStep).roundToInt() * latitudeStep,
        longitude = (longitude / longitudeStep).roundToInt() * longitudeStep,
        radiusMeters = radius
    )
}

private const val METERS_PER_DEGREE_LATITUDE = 111_320.0
