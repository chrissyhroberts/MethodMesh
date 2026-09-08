package com.example.methodmesh.modules.aviation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AviationAirfieldRepositoryTest {
    @Test
    fun csvParserPreservesQuotedComma() {
        val parsed = AviationCsv.parseLine("1,EGXX,small_airport,\"Comma, Field\",51.0,-0.1")
        assertEquals("Comma, Field", parsed[3])
    }

    @Test
    fun cacheOnlySearchFiltersAndOrdersNearestAirfields() {
        val directory = Files.createTempDirectory("methodmesh-aviation-test").toFile()
        try {
            File(directory, "airports.csv").writeText(
                "ident,type,name,latitude_deg,longitude_deg,elevation_ft,iso_country,municipality,scheduled_service,gps_code,iata_code\n" +
                    "EGLL,large_airport,London Heathrow,51.4700,-0.4543,83,GB,London,yes,EGLL,LHR\n" +
                    "EGXX,small_airport,\"Comma, Field\",51.5000,-0.3000,120,GB,Example,no,EGXX,\n" +
                    "EGYY,heliport,Far Heliport,53.0000,-1.0000,200,GB,Faraway,no,EGYY,\n"
            )

            val repository = AviationAirfieldRepository(directory)
            val result = repository.searchNearest(
                latitude = 51.48,
                longitude = -0.45,
                radiusNm = 30.0,
                allowedTypes = setOf("large_airport", "small_airport"),
                scheduledOnly = false,
                maximumResults = 10,
                refreshPolicy = "cache_only"
            )

            assertEquals(2, result.airfields.size)
            assertEquals("EGLL", result.airfields.first().ident)
            assertEquals("Comma, Field", result.airfields[1].name)
            assertFalse(result.refreshed)
            assertFalse(result.staleCacheFallback)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun scheduledOnlyExcludesUnscheduledAirfield() {
        val directory = Files.createTempDirectory("methodmesh-aviation-test").toFile()
        try {
            File(directory, "airports.csv").writeText(
                "ident,type,name,latitude_deg,longitude_deg,elevation_ft,iso_country,municipality,scheduled_service,gps_code,iata_code\n" +
                    "EGLL,large_airport,London Heathrow,51.4700,-0.4543,83,GB,London,yes,EGLL,LHR\n" +
                    "EGXX,small_airport,Local Strip,51.5000,-0.3000,120,GB,Example,no,EGXX,\n"
            )

            val result = AviationAirfieldRepository(directory).searchNearest(
                latitude = 51.48,
                longitude = -0.45,
                radiusNm = 30.0,
                allowedTypes = setOf("large_airport", "small_airport"),
                scheduledOnly = true,
                maximumResults = 10,
                refreshPolicy = "cache_only"
            )

            assertEquals(listOf("EGLL"), result.airfields.map { it.ident })
            assertTrue(result.warning.isBlank())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun cachedRunwayLookupReturnsBothRunwayEndsAndDesignatorHeadings() {
        val directory = Files.createTempDirectory("methodmesh-aviation-runway-test").toFile()
        try {
            File(directory, "runways.csv").writeText(
                "id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft\n" +
                    "1,1,EGLL,12799,164,ASP,1,0,09L,,,,89.6,,27R,,,,269.6,\n"
            )

            val repository = AviationAirfieldRepository(directory)
            val result = repository.runwaysFor("EGLL", "cache_only")

            assertEquals(2, result.runwayEnds.size)
            assertEquals(90.0, result.runwayEnds.first { it.ident == "09L" }.headingDeg, 0.001)
            assertEquals(270.0, result.runwayEnds.first { it.ident == "27R" }.headingDeg, 0.001)
            assertEquals("runway_designator", result.runwayEnds.first().headingSource)
            assertEquals("ASP", result.runwayEnds.first().surface)
            assertTrue(result.runwayEnds.first().lighted)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun nonNumericRunwayDesignatorFallsBackToPublishedTrueHeading() {
        val directory = Files.createTempDirectory("methodmesh-aviation-runway-test").toFile()
        try {
            File(directory, "runways.csv").writeText(
                "id,airport_ref,airport_ident,length_ft,width_ft,surface,lighted,closed,le_ident,le_latitude_deg,le_longitude_deg,le_elevation_ft,le_heading_degT,le_displaced_threshold_ft,he_ident,he_latitude_deg,he_longitude_deg,he_elevation_ft,he_heading_degT,he_displaced_threshold_ft\n" +
                    "1,1,EGHP,900,40,GRS,0,0,NORTH,,,,3.5,,SOUTH,,,,183.5,\n"
            )

            val result = AviationAirfieldRepository(directory).runwaysFor("EGHP", "cache_only")
            val north = result.runwayEnds.first { it.ident == "NORTH" }

            assertEquals(3.5, north.headingDeg, 0.001)
            assertEquals("true_heading_fallback", north.headingSource)
        } finally {
            directory.deleteRecursively()
        }
    }
}
