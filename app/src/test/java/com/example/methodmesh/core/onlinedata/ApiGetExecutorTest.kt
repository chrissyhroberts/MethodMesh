package com.example.methodmesh.core.onlinedata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ApiGetExecutorTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-02T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun preparesGetRequestWithRuntimeInputsAndQueryEncoding() {
        val definition = weatherDefinition()
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(definition)),
            httpClient = FakeHttpClient(),
            clock = clock
        )

        val prepared = executor.prepare(
            definition,
            mapOf("latitude" to "52.0779", "longitude" to "-0.0580", "note" to "clinic room")
        )

        assertEquals(
            "https://api.example.test/weather?latitude=52.0779&longitude=-0.0580&note=clinic%20room",
            prepared.url
        )
    }

    @Test
    fun roundedLocationDefinitionsDoNotSendExactCoordinates() {
        val definition = weatherDefinition().copy(
            privacy = ApiPrivacy(
                sendsLocation = true,
                locationMode = LocationDisclosureMode.ROUNDED,
                roundedLocationRadiusMeters = 5_000
            )
        )
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(definition)),
            httpClient = FakeHttpClient(),
            clock = clock
        )

        val prepared = executor.prepare(
            definition,
            mapOf("latitude" to "52.0779679", "longitude" to "-0.0579428")
        )

        assertFalse(prepared.url.contains("52.0779679"))
        assertFalse(prepared.url.contains("-0.0579428"))
        assertTrue(prepared.url.contains("latitude=52.05713"))
        assertTrue(prepared.url.contains("longitude=-0.07308"))
    }

    @Test
    fun executesJsonGetAndPreservesMetaDataAndRawBody() {
        val http = FakeHttpClient(
            OnlineHttpResponse(
                statusCode = 200,
                body = """{"current":{"temperature_2m":18.4,"relative_humidity_2m":71}}""",
                contentType = "application/json",
                durationMs = 184
            )
        )
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(weatherDefinition())),
            httpClient = http,
            clock = clock
        )

        val result = executor.execute(
            ApiGetRequest(
                definitionId = "openmeteo.current_weather",
                inputs = mapOf("latitude" to "52.0779", "longitude" to "-0.0580")
            )
        )

        val temperature = ResultPath.parse("current.temperature_2m").resolve(result.data)

        assertEquals(OnlineExecutionStatus.SUCCESS, result.status)
        assertEquals(200, result.meta.statusCode)
        assertFalse(result.meta.fromCache)
        assertNotNull(result.raw)
        assertTrue(temperature is ResultLookup.Value)
        assertEquals(18.4, ((temperature as ResultLookup.Value).value as ResultTree.NumberNode).value, 0.0)
    }

    @Test
    fun returnsFreshCacheWithoutCallingHttpAgain() {
        val cache = InMemoryApiResultCache()
        val http = FakeHttpClient(
            OnlineHttpResponse(statusCode = 200, body = """{"ok":true}""")
        )
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(weatherDefinition())),
            httpClient = http,
            cache = cache,
            clock = clock
        )
        val request = ApiGetRequest(
            definitionId = "openmeteo.current_weather",
            inputs = mapOf("latitude" to "52.0779", "longitude" to "-0.0580")
        )

        val first = executor.execute(request)
        val second = executor.execute(request)

        assertEquals(1, http.calls.size)
        assertEquals(first.resultId, second.resultId)
        assertTrue(second.meta.fromCache)
        assertFalse(second.meta.isStale)
    }

    @Test
    fun returnsStaleCacheWhenFreshPreferredNetworkFails() {
        val definition = weatherDefinition().copy(cache = CachePolicy(CacheMode.FRESH_PREFERRED, ttlSeconds = 1))
        val cache = InMemoryApiResultCache()
        val successfulHttp = FakeHttpClient(
            OnlineHttpResponse(statusCode = 200, body = """{"ok":true}""")
        )
        val firstExecutor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(definition)),
            httpClient = successfulHttp,
            cache = cache,
            clock = clock
        )
        val request = ApiGetRequest(
            definitionId = definition.id,
            inputs = mapOf("latitude" to "52.0779", "longitude" to "-0.0580")
        )
        firstExecutor.execute(request)

        val laterClock = Clock.fixed(Instant.parse("2026-09-02T12:00:10Z"), ZoneOffset.UTC)
        val failingExecutor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(definition)),
            httpClient = ThrowingHttpClient,
            cache = cache,
            clock = laterClock
        )

        val second = failingExecutor.execute(request)

        assertEquals(OnlineExecutionStatus.STALE_CACHE, second.status)
        assertTrue(second.meta.fromCache)
        assertTrue(second.meta.isStale)
    }

    @Test
    fun doesNotSilentlyTreatHttpErrorAsEmptySuccess() {
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(weatherDefinition())),
            httpClient = FakeHttpClient(OnlineHttpResponse(statusCode = 429, body = "slow down")),
            clock = clock
        )

        val result = executor.execute(
            ApiGetRequest(
                definitionId = "openmeteo.current_weather",
                inputs = mapOf("latitude" to "52.0779", "longitude" to "-0.0580")
            )
        )

        assertEquals(OnlineExecutionStatus.RATE_LIMITED, result.status)
        assertEquals(429, result.meta.statusCode)
        assertEquals("slow down", result.raw!!.body)
    }

    @Test
    fun redactsApiKeyQueryCredentialInDisplayedUrl() {
        val definition = weatherDefinition().copy(
            auth = ApiAuthDefinition.ApiKeyQuery(parameterName = "apikey", credentialId = "weather-key")
        )
        val executor = ApiGetExecutor(
            registry = InMemoryApiDefinitionRegistry(listOf(definition)),
            httpClient = FakeHttpClient(),
            credentials = object : CredentialResolver {
                override fun resolve(credentialId: String): String? = "SECRET"
            },
            clock = clock
        )

        val prepared = executor.prepare(
            definition,
            mapOf("latitude" to "52.0779", "longitude" to "-0.0580")
        )

        assertTrue(prepared.url.contains("apikey=SECRET"))
        assertTrue(prepared.redactedUrl.contains("apikey=REDACTED"))
        assertFalse(prepared.redactedUrl.contains("SECRET"))
    }

    private fun weatherDefinition(): ApiDefinition =
        ApiDefinition(
            id = "openmeteo.current_weather",
            name = "Current Weather",
            origin = ApiDefinitionOrigin.BUNDLED,
            editable = false,
            urlTemplate = "https://api.example.test/weather",
            queryParameters = mapOf(
                "latitude" to "{latitude}",
                "longitude" to "{longitude}",
                "note" to "{note}"
            ),
            inputs = listOf(
                ApiInputDefinition("latitude", "Latitude", type = ApiInputType.LATITUDE),
                ApiInputDefinition("longitude", "Longitude", type = ApiInputType.LONGITUDE),
                ApiInputDefinition("note", "Note", required = false)
            ),
            cache = CachePolicy.ttl(900)
        )

    private class FakeHttpClient(
        private val response: OnlineHttpResponse = OnlineHttpResponse(statusCode = 200, body = """{"ok":true}""")
    ) : OnlineHttpClient {
        val calls = mutableListOf<OnlineHttpRequest>()

        override fun get(request: OnlineHttpRequest): OnlineHttpResponse {
            calls += request
            return response
        }
    }

    private object ThrowingHttpClient : OnlineHttpClient {
        override fun get(request: OnlineHttpRequest): OnlineHttpResponse {
            error("network unavailable")
        }
    }
}
