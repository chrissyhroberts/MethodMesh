package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ApiDefinitionCodecTest {
    @Test
    fun roundTripsApiDefinitionWithoutFlatteningStructure() {
        val definition = ApiDefinition(
            id = "myproject.case_counts",
            name = "Case counts",
            description = "Project-specific case count endpoint.",
            origin = ApiDefinitionOrigin.USER,
            version = 3,
            urlTemplate = "https://example.org/cases/{district}",
            queryParameters = mapOf("date" to "{date}", "format" to "json"),
            headers = mapOf("Accept" to "application/json"),
            inputs = listOf(
                ApiInputDefinition("district", "District"),
                ApiInputDefinition("date", "Date", required = false)
            ),
            auth = ApiAuthDefinition.BearerToken("project-token"),
            response = ApiResponseDefinition(
                expectedPaths = listOf("records", "records[0].count")
            ),
            cache = CachePolicy(CacheMode.CACHE_PREFERRED, ttlSeconds = 600),
            privacy = ApiPrivacy(sendsIdentifiers = true),
            attribution = ApiAttribution(providerName = "Example Health Team")
        )

        val decoded = ApiDefinitionCodec.decode(ApiDefinitionCodec.encode(definition))

        assertEquals(definition.id, decoded.id)
        assertEquals(definition.version, decoded.version)
        assertEquals(definition.queryParameters, decoded.queryParameters)
        assertEquals(definition.headers, decoded.headers)
        assertEquals(ApiAuthDefinition.BearerToken("project-token"), decoded.auth)
        assertEquals(listOf("records", "records[0].count"), decoded.response.expectedPaths)
        assertEquals(CacheMode.CACHE_PREFERRED, decoded.cache.mode)
        assertTrue(decoded.privacy.sendsIdentifiers)
    }

    @Test
    fun exportBundleVerifiesIntegrityOnImport() {
        val bundle = ApiDefinitionCodec.exportBundle(
            definitions = listOf(BundledApiDefinitions.openMeteoCurrentWeather),
            exportedAt = Instant.parse("2026-09-02T12:00:00Z")
        )

        val imported = ApiDefinitionCodec.importBundle(bundle)

        assertEquals(1, imported.definitions.size)
        assertEquals("openmeteo.current_weather", imported.definitions.single().id)
        assertTrue(imported.hash.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun exportedDefinitionsDoNotContainCredentialSecretValues() {
        val definition = BundledApiDefinitions.openMeteoCurrentWeather.copy(
            auth = ApiAuthDefinition.ApiKeyQuery(parameterName = "apikey", credentialId = "weather-key")
        )
        val bundle = ApiDefinitionCodec.exportBundle(listOf(definition))

        val importedDefinition = ApiDefinitionCodec.importBundle(bundle).definitions.single()

        assertEquals(ApiAuthDefinition.ApiKeyQuery("apikey", "weather-key"), importedDefinition.auth)
        assertFalse(bundle.contains("SECRET"))
    }

    @Test
    fun rejectsTamperedBundle() {
        val bundle = ApiDefinitionCodec.exportBundle(listOf(BundledApiDefinitions.openMeteoCurrentWeather))
        val tampered = bundle.replace("Open-Meteo current weather", "Sneaky weather")

        val result = runCatching { ApiDefinitionCodec.importBundle(tampered) }

        assertTrue(result.isFailure)
    }
}
