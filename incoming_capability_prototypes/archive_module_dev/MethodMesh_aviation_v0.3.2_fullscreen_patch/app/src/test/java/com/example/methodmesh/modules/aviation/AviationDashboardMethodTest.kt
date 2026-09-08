package com.example.methodmesh.modules.aviation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AviationDashboardMethodTest {
    @Test
    fun snapshotUsesSharedWindAndAltitudeEngines() {
        val airfield = AviationAirfieldRepository.Airfield(
            ident = "EGXX",
            type = "small_airport",
            name = "Example Field",
            latitude = 51.0,
            longitude = -0.1,
            elevationFt = 500.0,
            isoCountry = "GB",
            municipality = "Example",
            scheduledService = false,
            gpsCode = "EGXX",
            iataCode = "",
            distanceNm = 4.2,
            bearingDeg = 90.0
        )
        val search = AviationAirfieldRepository.SearchResult(
            airfields = listOf(airfield),
            sourceUrl = AviationAirfieldRepository.DEFAULT_SOURCE_URL,
            catalogUpdatedIso = "2026-09-04T12:00:00Z",
            refreshed = false,
            staleCacheFallback = false
        )
        val runway = AviationAirfieldRepository.RunwayEnd(
            airportIdent = "EGXX",
            ident = "09",
            reciprocalIdent = "27",
            headingDeg = 90.0,
            headingSource = "runway_designator",
            lengthFt = 2500.0,
            widthFt = 60.0,
            surface = "GRS",
            lighted = false
        )
        val runwayResult = AviationAirfieldRepository.RunwayResult(
            runwayEnds = listOf(runway),
            sourceUrl = AviationAirfieldRepository.DEFAULT_RUNWAY_SOURCE_URL,
            catalogUpdatedIso = "2026-09-04T12:00:00Z",
            refreshed = false,
            staleCacheFallback = false
        )

        val values = As100AviationDashboardMethod.values(
            search = search,
            runwayResult = runwayResult,
            selectedAirfield = airfield,
            selectedRunway = runway,
            latitude = 51.0,
            longitude = -0.2,
            locationSource = "supplied",
            telemetry = AviationDashboardTelemetry(accuracyM = 5.0, groundSpeedKt = 82.0, trackDeg = 91.0),
            settings = mapOf(
                "conditions_enabled" to "true",
                "wind_direction_deg" to "180",
                "wind_speed_kt" to "20",
                "qnh_unit" to "hpa",
                "qnh_value" to "1013.25",
                "oat_c" to "15"
            )
        )

        assertEquals("succeeded", values[AviationDashboardFields.STATUS])
        assertEquals("EGXX", values[AviationDashboardFields.SELECTED_AIRFIELD_IDENT])
        assertEquals("09", values[AviationDashboardFields.SELECTED_RUNWAY_IDENT])
        assertEquals("20.00", values[AviationDashboardFields.CROSSWIND_KT])
        assertTrue(values[AviationDashboardFields.DENSITY_ALTITUDE_FT].orEmpty().isNotBlank())
        assertEquals("82.0", values[AviationDashboardFields.GROUND_SPEED_KT])
    }
}
