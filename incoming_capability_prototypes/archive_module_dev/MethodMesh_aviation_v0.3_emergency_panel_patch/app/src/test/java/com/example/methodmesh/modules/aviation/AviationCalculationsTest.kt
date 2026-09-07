package com.example.methodmesh.modules.aviation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AviationCalculationsTest {
    @Test
    fun directWindProducesHeadwind() {
        val result = AviationCalculations.runwayWind(90.0, 90.0, 20.0)
        assertEquals(20.0, result.headwindKt, 0.001)
        assertEquals(0.0, result.tailwindKt, 0.001)
        assertEquals(0.0, result.crosswindKt, 0.001)
    }

    @Test
    fun ninetyDegreeWindProducesCrosswindFromRight() {
        val result = AviationCalculations.runwayWind(90.0, 180.0, 20.0)
        assertEquals(0.0, result.headwindKt, 0.001)
        assertEquals(20.0, result.crosswindKt, 0.001)
        assertEquals("right", result.crosswindSide)
    }

    @Test
    fun reciprocalWindProducesTailwind() {
        val result = AviationCalculations.runwayWind(90.0, 270.0, 15.0)
        assertEquals(15.0, result.tailwindKt, 0.001)
        assertEquals(0.0, result.crosswindKt, 0.001)
    }

    @Test
    fun standardSeaLevelAtmosphereIsApproximatelyZeroAltitude() {
        val result = AviationCalculations.altitude(0.0, 1013.25, "hpa", 15.0)
        assertEquals(0.0, result.pressureAltitudeFt, 1.0)
        assertEquals(0.0, result.densityAltitudeFt, 2.0)
        assertEquals(15.0, result.isaTemperatureC, 0.1)
    }

    @Test
    fun e6bTimeFromDistanceAndSpeed() {
        val (value, unit) = AviationCalculations.e6b(
            calculation = "time_from_distance_speed",
            distanceNm = 120.0,
            groundspeedKt = 120.0
        )
        assertEquals(60.0, value, 0.001)
        assertEquals("min", unit)
    }

    @Test
    fun e6bRangeFromFuelBurnAndSpeed() {
        val (value, unit) = AviationCalculations.e6b(
            calculation = "range",
            groundspeedKt = 100.0,
            fuelAvailable = 30.0,
            fuelBurnPerHour = 10.0
        )
        assertEquals(300.0, value, 0.001)
        assertEquals("NM", unit)
    }

    @Test
    fun greatCircleDistanceAndBearingArePlausible() {
        val result = AviationCalculations.distanceAndBearing(
            latitude1 = 51.4700,
            longitude1 = -0.4543,
            latitude2 = 51.1537,
            longitude2 = -0.1821
        )
        assertTrue(result.distanceNm in 20.0..30.0)
        assertTrue(result.initialBearingDeg in 120.0..180.0)
    }
}
