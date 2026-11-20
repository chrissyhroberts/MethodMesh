package com.example.methodmesh.modules.weather

import com.example.methodmesh.core.onlinedata.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class WeatherEvidenceTest {
    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val definition = WeatherApiDefinitions.forecast
    private fun response(status: OnlineExecutionStatus, retrieved: Instant? = null, cached: Boolean = false) = ApiExecutionResult(
        definitionId = definition.id, definitionVersion = definition.version, status = status,
        meta = ApiResultMeta(requestedAt = now, retrievedAt = retrieved, fromCache = cached, isStale = cached),
        data = ResultTree.objectNode("value" to ResultTree.number(17.0)))
    private val settings = mapOf("latitude" to "51.1", "longitude" to "-0.1", "weather_json" to
        """{"latitude":51.1,"longitude":-0.1,"current":{"time":"2026-10-21T11:00:00Z","temperature_2m":17.0,"weather_code":0}}""")

    @Test fun `network failure does not advertise request time as provider retrieval`() {
        val payload = WeatherOnlineData.fromExecutionResult(definition, response(OnlineExecutionStatus.NETWORK_ERROR), now)
        assertEquals("", payload.retrievedTimeIso)
        assertNull(payload.dataAgeHours)
        assertEquals("", payload.json)
        assertFalse(payload.succeeded)
    }

    @Test fun `failure with an actual response preserves its true retrieval time`() {
        val retrieved = now.minusSeconds(120)
        val payload = WeatherOnlineData.fromExecutionResult(definition, response(OnlineExecutionStatus.HTTP_ERROR, retrieved), now)
        assertEquals(retrieved.toString(), payload.retrievedTimeIso)
        assertEquals(120.0 / 3600, payload.dataAgeHours!!, 0.00001)
        assertFalse(payload.succeeded)
    }

    @Test fun `offline cached weather preserves original retrieval age and stale evidence`() {
        val retrieved = now.minusSeconds(7200)
        val payload = WeatherOnlineData.fromExecutionResult(definition, response(OnlineExecutionStatus.STALE_CACHE, retrieved, true), now)
        assertEquals(retrieved.toString(), payload.retrievedTimeIso)
        assertEquals(2.0, payload.dataAgeHours!!, 0.0)
        assertTrue(payload.fromCache)
        assertTrue(payload.stale)
        assertTrue(payload.succeeded)
    }

    @Test fun `supplied weather without retrieval metadata does not invent contemporaneous freshness`() {
        val values = As100WeatherConditionsMethod.calculate(settings)
        assertEquals("succeeded", values["weather_conditions_status"])
        assertEquals("", values["weather_conditions_retrieved_time_iso"])
        val audit = JSONObject(values.getValue("weather_conditions_audit_json"))
        assertEquals("", audit.getString("retrieved_time_iso"))
        assertEquals("2026-10-21T11:00:00Z", values["weather_conditions_valid_time_iso"])
    }

    @Test fun `supplied weather preserves explicit historical retrieval evidence exactly`() {
        val retrieved = "2026-10-20T18:00:00Z"
        val values = As100WeatherConditionsMethod.calculate(settings + mapOf("retrieved_time_iso" to retrieved, "from_cache" to "true", "data_age_hours" to "18"))
        assertEquals(retrieved, values["weather_conditions_retrieved_time_iso"])
        assertEquals(retrieved, JSONObject(values.getValue("weather_conditions_audit_json")).getString("retrieved_time_iso"))
    }

    @Test fun `unavailable weather does not manufacture a response or retrieval timestamp`() {
        val values = As100WeatherConditionsMethod.calculate(mapOf("latitude" to "51.1", "longitude" to "-0.1", "api_error" to "Offline with no cache"))
        assertEquals("failed", values["weather_conditions_status"])
        assertEquals("", values["weather_conditions_retrieved_time_iso"])
        assertEquals("Offline with no cache", values["weather_conditions_error"])
    }
}
