package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class LocationPrivacyTest {
    @Test
    fun roundsLocationToApproximatelyFiveKilometreGrid() {
        val rounded = roundLocationForDisclosure(
            latitude = 52.0779679,
            longitude = -0.0579428,
            radiusMeters = 5_000
        )

        assertEquals(5_000, rounded.radiusMeters)
        assertTrue(abs(rounded.latitude - 52.0779679) < 0.05)
        assertTrue(abs(rounded.longitude - -0.0579428) < 0.08)
        assertEquals(5, rounded.latitudeString.substringAfter(".").length)
        assertEquals(5, rounded.longitudeString.substringAfter(".").length)
    }
}

