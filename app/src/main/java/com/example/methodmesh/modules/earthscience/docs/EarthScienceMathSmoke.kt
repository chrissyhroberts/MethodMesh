package com.example.methodmesh.modules.earthscience

import kotlin.math.abs

fun main() {
    val inverse = EarthScienceMath.inverseVincenty(51.5074, -0.1278, 48.8566, 2.3522)
    check(inverse.distanceM in 340_000.0..350_000.0) { "London-Paris distance unexpected: ${inverse.distanceM}" }

    val destination = EarthScienceMath.directVincenty(51.5074, -0.1278, inverse.initialBearingDeg, inverse.distanceM)
    check(abs(destination.latitude - 48.8566) < 0.001) { "Direct geodesic latitude mismatch" }
    check(abs(destination.longitude - 2.3522) < 0.001) { "Direct geodesic longitude mismatch" }

    val utm = EarthScienceMath.wgs84ToUtm(51.5074, -0.1278)
    check(utm.zone == 30 && utm.hemisphere == "N") { "London UTM zone mismatch: $utm" }
    val roundTrip = EarthScienceMath.utmToWgs84(utm.zone, utm.hemisphere, utm.eastingM, utm.northingM)
    check(abs(roundTrip.latitude - 51.5074) < 0.0001) { "UTM round-trip latitude mismatch" }
    check(abs(roundTrip.longitude + 0.1278) < 0.0001) { "UTM round-trip longitude mismatch" }

    val plane = EarthScienceMath.normalisePlane(0.0, 30.0, "right_hand_rule", null)
    check(abs(plane.dipDirectionDeg - 90.0) < 1e-9)
    check(abs(plane.poleTrendDeg - 270.0) < 1e-9)
    check(abs(plane.polePlungeDeg - 60.0) < 1e-9)

    val verticalIntersection = EarthScienceMath.planeIntersection(0.0, 90.0, 90.0, 90.0)
    check(abs(verticalIntersection.plungeDeg - 90.0) < 1e-6) { "Expected vertical intersection: $verticalIntersection" }

    check(EarthScienceMath.classifySoilTexture(40.0, 40.0, 20.0, 1.0) == "Loam")
    val grain = EarthScienceMath.grainSize("diameter_mm", 1.0, null)
    check(grain.wentworthClass == "Very coarse sand")
    check(abs(grain.phi) < 1e-12)

    val time = EarthScienceMath.geologicalTime(152.0)
    check(time.eon == "Phanerozoic" && time.era == "Mesozoic" && time.period == "Jurassic" && time.subdivision == "Upper Jurassic") { "Geotime mismatch: $time" }

    val average = EarthScienceMath.averageGnss(
        listOf(
            GnssFix(51.500000, -0.120000, 5.0, 20.0),
            GnssFix(51.500010, -0.120010, 5.0, 22.0),
            GnssFix(51.499990, -0.119990, 5.0, 21.0)
        ),
        "equal"
    )
    check(abs(average.latitude - 51.5) < 0.00002)
    check(abs(average.longitude + 0.12) < 0.00002)
    check(average.acceptedFixes == 3)

    println("EarthScienceMath smoke tests passed")
}
