package com.example.methodmesh.modules.astronomy

import java.time.Instant
import kotlin.math.abs

fun main() {
    val scale = AstronomyMath.imageScale(
        focalLengthMm = 400.0,
        pixelSizeMicron = 3.76,
        sensorWidthMm = 17.7,
        sensorHeightMm = 13.4,
        apertureMm = 80.0
    )
    check(abs(scale.arcsecPerPixel - 1.939) < 0.01) { "Unexpected image scale ${scale.arcsecPerPixel}" }
    check(abs(scale.focalRatio!! - 5.0) < 1e-9)

    val dew = AstronomyMath.dewRisk(10.0, 90.0)
    check(dew.dewPointC in 8.0..9.0)
    check(dew.risk in setOf("VERY HIGH", "HIGH"))

    val equatorLimit = AstronomyMath.exposureLimitSeconds(50.0, 4.0, 0.0).first
    val highDecLimit = AstronomyMath.exposureLimitSeconds(50.0, 4.0, 80.0).first
    check(highDecLimit > equatorLimit)

    val good = AstronomyMath.conditionsScore(0.0, 40000.0, 3.0, 5.0, 10.0, 50.0, null, 0.05, 2.0, -20.0, 10.0)
    val bad = AstronomyMath.conditionsScore(95.0, 2000.0, 50.0, 70.0, 10.0, 99.0, null, 1.0, 100.0, 60.0, 100.0)
    check(good.overall > bad.overall)

    val midnight = Instant.parse("2026-09-05T00:00:00Z")
    val polaris = AstronomyMath.polarisClockAngleDeg(midnight, -0.1)
    check(polaris in 0.0..<360.0)

    val sun = AstronomyMath.sunEquatorial(midnight)
    val moon = AstronomyMath.moonEquatorial(midnight)
    check(sun.decDeg in -24.0..24.0)
    check(moon.decDeg in -30.0..30.0)

    println("AstronomyMath smoke tests passed")
}
