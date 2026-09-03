package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledApiDefinitionsTest {
    @Test
    fun bundledDefinitionsAreAvailableThroughSameRegistryInterface() {
        val registry = BundledApiDefinitions.registry()

        assertNotNull(registry.find("openmeteo.current_weather"))
        assertNotNull(registry.find("gdacs.all_events_geojson"))
        assertNotNull(registry.find("openmeteo.daily_forecast"))
        assertNotNull(registry.find("openmeteo.air_quality_current"))
        assertNotNull(registry.find("usgs.earthquakes_day"))
        assertNotNull(registry.find("worldbank.indicator_latest"))
        assertNotNull(registry.find("frankfurter.latest_rates"))
        assertNotNull(registry.find("gbif.country_occurrences"))
        assertEquals(8, registry.all().size)
    }

    @Test
    fun openMeteoDefinitionDeclaresLocationDisclosureAndExpectedCurrentFields() {
        val definition = BundledApiDefinitions.openMeteoCurrentWeather

        assertFalse(definition.editable)
        assertTrue(definition.cloneable)
        assertTrue(definition.privacy.sendsLocation)
        assertEquals(LocationDisclosureMode.ROUNDED, definition.privacy.locationMode)
        assertEquals(5_000, definition.privacy.roundedLocationRadiusMeters)
        assertTrue(definition.queryParameters.getValue("current").contains("temperature_2m"))
        assertTrue(definition.response.expectedPaths.contains("current.temperature_2m"))
    }

    @Test
    fun gdacsDefinitionPreservesGeoJsonFeatureTree() {
        val definition = BundledApiDefinitions.gdacsAllEventsGeoJson

        assertFalse(definition.editable)
        assertEquals(ApiResponseType.JSON, definition.response.type)
        assertEquals("https://www.gdacs.org/contentdata/xml/gdacs_app_feed.json", definition.urlTemplate)
        assertTrue(definition.response.expectedPaths.contains("features[0].properties"))
    }
}
