package com.example.methodmesh.modules.astronomy

import com.example.methodmesh.platform.location.OpenLocationCode
import org.junit.Assert.*
import org.junit.Test

class AstronomyAutoPolicyTest {
    @Test fun manualSourceIsOfflineDespiteUpstreamDataOrUnspecifiedSite() {
        for (kind in listOf(AstronomyAutoKind.Conditions, AstronomyAutoKind.Dew)) {
            val plan = astronomyAutoPlan(kind, mapOf("source_mode" to "manual",
                "temperature_c" to "12", "previous_temperature_c" to "30", "previous_humidity_pct" to "99"))
            assertFalse(plan.needsDeviceLocation); assertFalse(plan.needsCurrent)
            assertEquals("12", plan.inputs["temperature_c"])
            assertEquals("manual", plan.inputs["data_source"])
        }
    }

    @Test fun humidityAliasFromPriorStepOverridesPresetDefault() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Dew, mapOf("relative_humidity_pct" to "70",
            "previous_temperature_c" to "18", "previous_humidity_pct" to "45"))
        assertFalse(plan.needsCurrent); assertFalse(plan.needsDeviceLocation)
        assertEquals("45", plan.inputs["relative_humidity_pct"])
        assertEquals("18", plan.inputs["temperature_c"])
    }

    @Test fun upstreamJsonRoutesOfflineAndIsNotMaskedByPresetScalars() {
        for (kind in listOf(AstronomyAutoKind.Conditions, AstronomyAutoKind.Dew)) {
            val plan = astronomyAutoPlan(kind, mapOf("temperature_c" to "10", "relative_humidity_pct" to "70",
                "previous_weather_json" to """{"current":{"temperature_2m":18,"relative_humidity_2m":45}}"""))
            assertFalse(plan.needsCurrent); assertFalse(plan.needsDeviceLocation)
            assertNull(plan.inputs["temperature_c"]); assertNull(plan.inputs["relative_humidity_pct"])
            assertTrue(plan.inputs.getValue("weather_json").contains("18"))
        }
    }

    @Test fun nearestUpstreamStepAndPreviousAliasPreserveExistingPrecedence() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Dew, mapOf(
            "step_2_temperature_c" to "4", "step_12_temperature_c" to "18",
            "step_12_humidity_pct" to "45", "previous_temperature_c" to "21"))
        assertEquals("21", plan.inputs["temperature_c"])
        assertEquals("45", plan.inputs["relative_humidity_pct"])
    }

    @Test fun suppliedApiJsonDoesNotNeedGpsPermission() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Conditions, mapOf("air_quality_json" to "{}"))
        assertFalse(plan.needsDeviceLocation); assertFalse(plan.needsCurrent)
        assertEquals("api", plan.inputs["data_source"])
    }

    @Test fun explicitUpstreamModeNeverRequestsLiveDataEvenWhenIncomplete() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Dew, mapOf("source_mode" to "upstream"))
        assertFalse(plan.needsDeviceLocation); assertFalse(plan.needsCurrent)
        assertEquals("upstream", plan.inputs["data_source"])
    }

    @Test fun suppliedForecastKeepsChosenSiteAndSkipsCurrentProvider() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Window, mapOf("latitude" to "51", "longitude" to "-1",
            "previous_forecast_json" to "upstream", "forecast_json" to "direct"))
        assertFalse(plan.needsDeviceLocation); assertFalse(plan.needsCurrent)
        assertEquals("upstream", plan.inputs["forecast_json"])
        assertEquals(51.0, plan.location!!.latitude, 0.00001)
    }

    @Test fun manualCoordinatesWinOverStalePlusCodeAndUpstreamSite() {
        val plan = astronomyAutoPlan(AstronomyAutoKind.Window, mapOf("location_source" to "manual",
            "latitude" to "0", "longitude" to "0", "plus_code" to "invalid",
            "previous_latitude" to "70", "previous_longitude" to "30"))
        assertEquals(0.0, plan.location!!.latitude, 0.0)
        assertFalse(plan.needsDeviceLocation)
    }

    @Test fun suppliedCoordinatesAndFullPlusCodeRetainAutomaticPrecedenceBeforeGps() {
        val coords = astronomyAutoPlan(AstronomyAutoKind.Window, mapOf("latitude" to "51", "longitude" to "-1"))
        assertFalse(coords.needsDeviceLocation)
        val plus = astronomyAutoPlan(AstronomyAutoKind.Window,
            mapOf("plus_code" to OpenLocationCode.encode(51.1, -0.1)))
        assertFalse(plus.needsDeviceLocation)
        assertEquals(51.1, plus.location!!.latitude, 0.001)
    }

    @Test fun invalidOrIncompleteSitesAreNotSilentlyReplacedWithGps() {
        for (bad in listOf(mapOf("latitude" to "NaN", "longitude" to "1"),
            mapOf("latitude" to "91", "longitude" to "1"), mapOf("latitude" to "2"),
            mapOf("latitude" to "", "longitude" to "-1"), mapOf("plus_code" to "22"),
            mapOf("previous_latitude" to "Infinity", "previous_longitude" to "1"))) {
            assertThrows(IllegalArgumentException::class.java) { astronomyAutoPlan(AstronomyAutoKind.Window, bad) }
        }
    }

    @Test fun zeroZeroAutoSentinelUsesGpsButManualZeroZeroRemainsValid() {
        val raw = mapOf("latitude" to "0", "longitude" to "0")
        assertTrue(astronomyAutoPlan(AstronomyAutoKind.Window, raw).needsDeviceLocation)
        assertFalse(astronomyAutoPlan(AstronomyAutoKind.Window, raw + ("location_source" to "manual")).needsDeviceLocation)
    }

    @Test fun routedInputsAreFrozenAndGeneratedMetadataDoesNotLeakIntoTheNextRun() {
        val raw = mutableMapOf("source_mode" to "manual", "temperature_c" to "10",
            "forecast_warning" to "stale", "data_source" to "old")
        val plan = astronomyAutoPlan(AstronomyAutoKind.Dew, raw)
        raw["temperature_c"] = "20"
        assertEquals("10", plan.inputs["temperature_c"])
        assertEquals("manual", plan.inputs["data_source"])
        assertNull(plan.inputs["forecast_warning"])
    }

    @Test fun invalidResolvedUpstreamNumbersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            astronomyAutoPlan(AstronomyAutoKind.Dew,
                mapOf("previous_temperature_c" to "18", "previous_humidity_pct" to "NaN"))
        }
    }
}
