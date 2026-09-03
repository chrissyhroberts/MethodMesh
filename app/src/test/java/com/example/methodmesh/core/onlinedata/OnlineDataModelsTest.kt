package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OnlineDataModelsTest {
    @Test
    fun apiDefinitionsAreDeclarativeAndRegistryAddressable() {
        val definition = ApiDefinition(
            id = "openmeteo.current_weather",
            name = "Current Weather",
            origin = ApiDefinitionOrigin.BUNDLED,
            editable = false,
            cloneable = true,
            urlTemplate = "https://api.open-meteo.com/v1/forecast",
            inputs = listOf(
                ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
                ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE)
            ),
            privacy = ApiPrivacy(sendsLocation = true, locationMode = LocationDisclosureMode.EXACT)
        )

        val registry = InMemoryApiDefinitionRegistry(listOf(definition))

        assertEquals(definition, registry.find("openmeteo.current_weather"))
        assertFalse(registry.find("openmeteo.current_weather")!!.editable)
        assertTrue(registry.find("openmeteo.current_weather")!!.cloneable)
        assertTrue(registry.find("openmeteo.current_weather")!!.privacy.sendsLocation)
    }

    @Test
    fun cacheRecordSeparatesResearchLinkedResultsFromDisposableCache() {
        val now = Instant.parse("2026-09-02T12:00:00Z")
        val record = CacheRecord(
            apiDefinitionId = "gdacs.nearby",
            apiDefinitionVersion = 1,
            resultId = "result-1",
            retrievedAt = now,
            freshUntil = now.plusSeconds(900),
            researchLinked = true
        )

        assertFalse(record.isStale(now.plusSeconds(60)))
        assertTrue(record.isStale(now.plusSeconds(901)))
        assertTrue(record.researchLinked)
    }

    @Test
    fun providerHealthDistinguishesRateLimitFromGenericFailure() {
        val health = ProviderHealth(
            providerId = "gdacs",
            status = ProviderHealthStatus.RATE_LIMITED,
            consecutiveFailures = 2,
            statusCode = 429,
            lastError = "Retry later"
        )

        assertEquals(ProviderHealthStatus.RATE_LIMITED, health.status)
        assertEquals(429, health.statusCode)
    }
}

