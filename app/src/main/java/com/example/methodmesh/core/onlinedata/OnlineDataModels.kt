package com.example.methodmesh.core.onlinedata

import java.time.Instant
import java.util.UUID

data class ApiDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val origin: ApiDefinitionOrigin = ApiDefinitionOrigin.USER,
    val version: Int = 1,
    val editable: Boolean = true,
    val cloneable: Boolean = true,
    val method: HttpMethod = HttpMethod.GET,
    val urlTemplate: String,
    val queryParameters: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val inputs: List<ApiInputDefinition> = emptyList(),
    val auth: ApiAuthDefinition = ApiAuthDefinition.None,
    val response: ApiResponseDefinition = ApiResponseDefinition(),
    val cache: CachePolicy = CachePolicy.ttl(seconds = 900),
    val privacy: ApiPrivacy = ApiPrivacy(),
    val attribution: ApiAttribution = ApiAttribution(),
    val documentationUrl: String = ""
)

enum class ApiDefinitionOrigin {
    BUNDLED,
    USER,
    IMPORTED,
    ORGANISATION
}

enum class HttpMethod {
    GET
}

data class ApiInputDefinition(
    val id: String,
    val name: String,
    val description: String = "",
    val type: ApiInputType = ApiInputType.STRING,
    val required: Boolean = true,
    val defaultValue: String = "",
    val sensitive: Boolean = false
)

enum class ApiInputType {
    STRING,
    NUMBER,
    BOOLEAN,
    LATITUDE,
    LONGITUDE,
    PLUS_CODE,
    OBJECT,
    ARRAY
}

sealed interface ApiAuthDefinition {
    data object None : ApiAuthDefinition
    data class ApiKeyQuery(val parameterName: String, val credentialId: String) : ApiAuthDefinition
    data class ApiKeyHeader(val headerName: String, val credentialId: String) : ApiAuthDefinition
    data class BearerToken(val credentialId: String) : ApiAuthDefinition
    data class Basic(val credentialId: String) : ApiAuthDefinition
}

data class CredentialReference(
    val id: String,
    val label: String,
    val providerId: String = "",
    val authType: String = ""
)

data class ApiResponseDefinition(
    val type: ApiResponseType = ApiResponseType.JSON,
    val expectedPaths: List<String> = emptyList(),
    val sampleShape: ResultTree? = null
)

enum class ApiResponseType {
    JSON,
    XML,
    TEXT,
    BINARY
}

data class CachePolicy(
    val mode: CacheMode,
    val ttlSeconds: Long = 0,
    val minimumRefreshIntervalSeconds: Long = 0
) {
    companion object {
        fun ttl(seconds: Long): CachePolicy =
            CachePolicy(mode = CacheMode.TTL, ttlSeconds = seconds.coerceAtLeast(0))

        fun noStore(): CachePolicy =
            CachePolicy(mode = CacheMode.NO_STORE)
    }
}

enum class CacheMode {
    NO_STORE,
    TTL,
    FRESH_REQUIRED,
    FRESH_PREFERRED,
    CACHE_PREFERRED,
    OFFLINE_ONLY
}

data class ApiPrivacy(
    val sendsLocation: Boolean = false,
    val sendsIdentifiers: Boolean = false,
    val locationMode: LocationDisclosureMode = LocationDisclosureMode.DISABLED,
    val roundedLocationRadiusMeters: Int = 5_000
)

enum class LocationDisclosureMode {
    EXACT,
    ROUNDED,
    MANUAL,
    DISABLED
}

data class ApiAttribution(
    val providerName: String = "",
    val providerUrl: String = "",
    val license: String = "",
    val requiredText: String = ""
)

data class ApiExecutionResult(
    val resultId: String = UUID.randomUUID().toString(),
    val definitionId: String,
    val definitionVersion: Int,
    val status: OnlineExecutionStatus,
    val meta: ApiResultMeta,
    val data: ResultTree,
    val raw: RawResponse? = null,
    val error: OnlineDataError? = null
)

data class ApiResultMeta(
    val requestedAt: Instant,
    val retrievedAt: Instant? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val fromCache: Boolean = false,
    val isStale: Boolean = false,
    val providerHealth: ProviderHealthStatus = ProviderHealthStatus.UNKNOWN,
    val contentType: String = "",
    val sourceUrlRedacted: String = ""
)

data class RawResponse(
    val body: String,
    val contentType: String = "",
    val compressed: Boolean = false,
    val sha256: String = ""
)

data class CacheRecord(
    val cacheId: String = UUID.randomUUID().toString(),
    val apiDefinitionId: String,
    val apiDefinitionVersion: Int,
    val resultId: String,
    val retrievedAt: Instant,
    val freshUntil: Instant? = null,
    val researchLinked: Boolean = false,
    val rawSha256: String = ""
) {
    fun isStale(now: Instant = Instant.now()): Boolean =
        freshUntil?.isBefore(now) ?: false
}

data class ProviderHealth(
    val providerId: String,
    val status: ProviderHealthStatus = ProviderHealthStatus.UNKNOWN,
    val lastSuccess: Instant? = null,
    val lastFailure: Instant? = null,
    val consecutiveFailures: Int = 0,
    val statusCode: Int? = null,
    val latencyMs: Long? = null,
    val lastError: String = "",
    val schemaState: SchemaHealthStatus = SchemaHealthStatus.UNKNOWN
)

enum class ProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    FAILING,
    AUTHENTICATION_REQUIRED,
    RATE_LIMITED,
    SCHEMA_CHANGED,
    DISABLED,
    UNKNOWN
}

enum class SchemaHealthStatus {
    OK,
    WARNING,
    CHANGED,
    INVALID,
    UNKNOWN
}

data class OnlineDataError(
    val code: OnlineExecutionStatus,
    val message: String,
    val detail: String = "",
    val path: String = ""
)

enum class OnlineExecutionStatus {
    SUCCESS,
    STALE_CACHE,
    PARTIAL,
    HTTP_ERROR,
    NETWORK_ERROR,
    PARSE_ERROR,
    SCHEMA_WARNING,
    AUTH_ERROR,
    RATE_LIMITED
}

data class ApiDefinitionRegistrySnapshot(
    val definitions: List<ApiDefinition>
) {
    fun find(id: String): ApiDefinition? =
        definitions.firstOrNull { it.id == id }
}

interface ApiDefinitionRegistry {
    fun all(): List<ApiDefinition>
    fun find(id: String): ApiDefinition?
}

class InMemoryApiDefinitionRegistry(
    definitions: List<ApiDefinition> = emptyList()
) : ApiDefinitionRegistry {
    private val values = definitions.associateBy { it.id }

    override fun all(): List<ApiDefinition> =
        values.values.sortedWith(compareBy<ApiDefinition> { it.origin.name }.thenBy { it.name.lowercase() })

    override fun find(id: String): ApiDefinition? =
        values[id]
}
