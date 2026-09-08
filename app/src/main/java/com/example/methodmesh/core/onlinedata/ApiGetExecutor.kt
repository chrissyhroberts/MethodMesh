package com.example.methodmesh.core.onlinedata

import java.net.URLEncoder
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class ApiGetRequest(
    val definitionId: String,
    val inputs: Map<String, String> = emptyMap(),
    val networkPolicy: CacheMode? = null,
    val researchLinked: Boolean = false
)

data class PreparedApiRequest(
    val url: String,
    val redactedUrl: String,
    val headers: Map<String, String>,
    val cacheKey: String
)

data class OnlineHttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap()
)

data class OnlineHttpResponse(
    val statusCode: Int,
    val body: String,
    val contentType: String = "",
    val headers: Map<String, String> = emptyMap(),
    val durationMs: Long = 0
)

interface OnlineHttpClient {
    fun get(request: OnlineHttpRequest): OnlineHttpResponse
}

interface CredentialResolver {
    fun resolve(credentialId: String): String?
}

object EmptyCredentialResolver : CredentialResolver {
    override fun resolve(credentialId: String): String? = null
}

data class CachedApiResult(
    val result: ApiExecutionResult,
    val record: CacheRecord
)

interface ApiResultCache {
    fun find(cacheKey: String): CachedApiResult?
    fun save(cacheKey: String, result: ApiExecutionResult, record: CacheRecord)
}

class InMemoryApiResultCache : ApiResultCache {
    private val values = linkedMapOf<String, CachedApiResult>()

    override fun find(cacheKey: String): CachedApiResult? =
        values[cacheKey]

    override fun save(cacheKey: String, result: ApiExecutionResult, record: CacheRecord) {
        values[cacheKey] = CachedApiResult(result = result, record = record)
    }
}

object SharedApiResultCache : ApiResultCache {
    private val values = linkedMapOf<String, CachedApiResult>()

    override fun find(cacheKey: String): CachedApiResult? =
        synchronized(values) { values[cacheKey] }

    override fun save(cacheKey: String, result: ApiExecutionResult, record: CacheRecord) {
        synchronized(values) {
            values[cacheKey] = CachedApiResult(result = result, record = record)
        }
    }
}

class ApiGetExecutor(
    private val registry: ApiDefinitionRegistry,
    private val httpClient: OnlineHttpClient,
    private val cache: ApiResultCache = InMemoryApiResultCache(),
    private val credentials: CredentialResolver = EmptyCredentialResolver,
    private val clock: Clock = Clock.systemUTC()
) {
    fun execute(request: ApiGetRequest): ApiExecutionResult {
        val requestedAt = Instant.now(clock)
        val definition = registry.find(request.definitionId)
            ?: return failure(
                definitionId = request.definitionId,
                definitionVersion = 0,
                requestedAt = requestedAt,
                status = OnlineExecutionStatus.SCHEMA_WARNING,
                message = "API definition was not found.",
                detail = request.definitionId
            )

        if (definition.method != HttpMethod.GET) {
            return failure(
                definitionId = definition.id,
                definitionVersion = definition.version,
                requestedAt = requestedAt,
                status = OnlineExecutionStatus.SCHEMA_WARNING,
                message = "Only HTTP GET API definitions are currently supported."
            )
        }

        val prepared = runCatching { prepare(definition, request.inputs) }
            .getOrElse { error ->
                return failure(
                    definitionId = definition.id,
                    definitionVersion = definition.version,
                    requestedAt = requestedAt,
                    status = OnlineExecutionStatus.SCHEMA_WARNING,
                    message = error.message ?: "API request could not be prepared."
                )
            }

        val effectivePolicy = request.networkPolicy ?: definition.cache.mode
        val cached = cache.find(prepared.cacheKey)
        val stale = cached?.record?.isStale(requestedAt) ?: true

        if (cached != null && effectivePolicy == CacheMode.OFFLINE_ONLY) {
            return cached.result.asCachedCopy(requestedAt, stale)
        }

        if (cached != null && !stale && effectivePolicy != CacheMode.FRESH_REQUIRED) {
            return cached.result.asCachedCopy(requestedAt, isStale = false)
        }

        if (cached != null && effectivePolicy == CacheMode.CACHE_PREFERRED) {
            return cached.result.asCachedCopy(requestedAt, stale)
        }

        if (effectivePolicy == CacheMode.OFFLINE_ONLY) {
            return failure(
                definitionId = definition.id,
                definitionVersion = definition.version,
                requestedAt = requestedAt,
                status = OnlineExecutionStatus.NETWORK_ERROR,
                message = "No cached API result is available for offline-only execution.",
                detail = prepared.redactedUrl
            )
        }

        val response = runCatching {
            httpClient.get(OnlineHttpRequest(url = prepared.url, headers = prepared.headers))
        }.getOrElse { error ->
            if (cached != null && effectivePolicy == CacheMode.FRESH_PREFERRED) {
                return cached.result.asCachedCopy(requestedAt, isStale = true, status = OnlineExecutionStatus.STALE_CACHE)
            }
            return failure(
                definitionId = definition.id,
                definitionVersion = definition.version,
                requestedAt = requestedAt,
                status = OnlineExecutionStatus.NETWORK_ERROR,
                message = "API request failed before a response was received.",
                detail = error.message.orEmpty(),
                redactedUrl = prepared.redactedUrl
            )
        }

        val retrievedAt = Instant.now(clock)
        if (response.statusCode == 401 || response.statusCode == 403) {
            return httpFailure(definition, requestedAt, retrievedAt, response, prepared, OnlineExecutionStatus.AUTH_ERROR)
        }
        if (response.statusCode == 429) {
            return httpFailure(definition, requestedAt, retrievedAt, response, prepared, OnlineExecutionStatus.RATE_LIMITED)
        }
        if (response.statusCode !in 200..299) {
            return httpFailure(definition, requestedAt, retrievedAt, response, prepared, OnlineExecutionStatus.HTTP_ERROR)
        }

        val data = runCatching { parseResponse(definition.response.type, response.body) }
            .getOrElse { error ->
                return ApiExecutionResult(
                    definitionId = definition.id,
                    definitionVersion = definition.version,
                    status = OnlineExecutionStatus.PARSE_ERROR,
                    meta = ApiResultMeta(
                        requestedAt = requestedAt,
                        retrievedAt = retrievedAt,
                        statusCode = response.statusCode,
                        durationMs = response.durationMs,
                        contentType = response.contentType,
                        sourceUrlRedacted = prepared.redactedUrl
                    ),
                    data = ResultTree.NullNode,
                    raw = RawResponse(
                        body = response.body,
                        contentType = response.contentType,
                        sha256 = sha256(response.body)
                    ),
                    error = OnlineDataError(
                        code = OnlineExecutionStatus.PARSE_ERROR,
                        message = "API response could not be parsed as ${definition.response.type.name.lowercase(Locale.ROOT)}.",
                        detail = error.message.orEmpty()
                    )
                )
            }

        val raw = RawResponse(
            body = response.body,
            contentType = response.contentType,
            sha256 = sha256(response.body)
        )
        val result = ApiExecutionResult(
            definitionId = definition.id,
            definitionVersion = definition.version,
            status = OnlineExecutionStatus.SUCCESS,
            meta = ApiResultMeta(
                requestedAt = requestedAt,
                retrievedAt = retrievedAt,
                statusCode = response.statusCode,
                durationMs = response.durationMs,
                fromCache = false,
                isStale = false,
                providerHealth = ProviderHealthStatus.HEALTHY,
                contentType = response.contentType,
                sourceUrlRedacted = prepared.redactedUrl
            ),
            data = data,
            raw = raw
        )

        if (definition.cache.mode != CacheMode.NO_STORE) {
            val freshUntil = when (definition.cache.mode) {
                CacheMode.TTL,
                CacheMode.FRESH_PREFERRED,
                CacheMode.CACHE_PREFERRED -> retrievedAt.plusSeconds(definition.cache.ttlSeconds.coerceAtLeast(0))

                else -> null
            }
            cache.save(
                prepared.cacheKey,
                result,
                CacheRecord(
                    apiDefinitionId = definition.id,
                    apiDefinitionVersion = definition.version,
                    resultId = result.resultId,
                    retrievedAt = retrievedAt,
                    freshUntil = freshUntil,
                    researchLinked = request.researchLinked,
                    rawSha256 = raw.sha256
                )
            )
        }

        return result
    }

    fun prepare(definition: ApiDefinition, inputs: Map<String, String>): PreparedApiRequest {
        val resolvedInputs = applyLocationPrivacy(definition, resolveInputs(definition, inputs))
        var url = replaceTokens(definition.urlTemplate, resolvedInputs)
        val query = linkedMapOf<String, String>()
        definition.queryParameters.forEach { (key, template) ->
            val value = replaceTokens(template, resolvedInputs)
            if (value.isNotBlank()) {
                query[key] = value
            }
        }
        val auth = applyAuth(definition.auth, query, definition.headers)
        val headers = auth.headers.mapValues { (_, value) -> replaceTokens(value, resolvedInputs) }
        url = appendQuery(url, query + auth.queryParameters)
        val redactedUrl = appendQuery(replaceTokens(definition.urlTemplate, resolvedInputs), query + auth.redactedQueryParameters)

        return PreparedApiRequest(
            url = url,
            redactedUrl = redactedUrl,
            headers = headers,
            cacheKey = sha256("${definition.id}|${definition.version}|$url|${headers.toSortedMap()}")
        )
    }

    private fun resolveInputs(definition: ApiDefinition, inputs: Map<String, String>): Map<String, String> {
        val resolved = linkedMapOf<String, String>()
        definition.inputs.forEach { input ->
            val value = inputs[input.id]?.takeIf { it.isNotBlank() } ?: input.defaultValue
            if (input.required && value.isBlank()) {
                error("Required API input '${input.id}' is missing.")
            }
            resolved[input.id] = value
        }
        inputs.forEach { (key, value) ->
            resolved.putIfAbsent(key, value)
        }
        return resolved
    }

    private fun applyLocationPrivacy(
        definition: ApiDefinition,
        inputs: Map<String, String>
    ): Map<String, String> {
        val privacy = definition.privacy
        if (!privacy.sendsLocation || privacy.locationMode != LocationDisclosureMode.ROUNDED) return inputs

        val latitudeKey = definition.inputs.firstOrNull { it.type == ApiInputType.LATITUDE }?.id ?: "latitude"
        val longitudeKey = definition.inputs.firstOrNull { it.type == ApiInputType.LONGITUDE }?.id ?: "longitude"
        val latitude = inputs[latitudeKey]?.toDoubleOrNull() ?: return inputs
        val longitude = inputs[longitudeKey]?.toDoubleOrNull() ?: return inputs
        val rounded = roundLocationForDisclosure(latitude, longitude, privacy.roundedLocationRadiusMeters)

        return inputs.toMutableMap().apply {
            put(latitudeKey, rounded.latitudeString)
            put(longitudeKey, rounded.longitudeString)
        }
    }

    private data class AppliedAuth(
        val headers: Map<String, String>,
        val queryParameters: Map<String, String>,
        val redactedQueryParameters: Map<String, String>
    )

    private fun applyAuth(
        auth: ApiAuthDefinition,
        query: Map<String, String>,
        headers: Map<String, String>
    ): AppliedAuth {
        val mutableHeaders = linkedMapOf<String, String>()
        mutableHeaders.putAll(headers)
        val authQuery = linkedMapOf<String, String>()
        val redactedAuthQuery = linkedMapOf<String, String>()

        when (auth) {
            ApiAuthDefinition.None -> Unit
            is ApiAuthDefinition.ApiKeyQuery -> {
                val secret = credentials.resolve(auth.credentialId)
                    ?: error("Credential '${auth.credentialId}' is required.")
                authQuery[auth.parameterName] = secret
                redactedAuthQuery[auth.parameterName] = "REDACTED"
            }

            is ApiAuthDefinition.ApiKeyHeader -> {
                val secret = credentials.resolve(auth.credentialId)
                    ?: error("Credential '${auth.credentialId}' is required.")
                mutableHeaders[auth.headerName] = secret
            }

            is ApiAuthDefinition.BearerToken -> {
                val secret = credentials.resolve(auth.credentialId)
                    ?: error("Credential '${auth.credentialId}' is required.")
                mutableHeaders["Authorization"] = "Bearer $secret"
            }

            is ApiAuthDefinition.Basic -> {
                val secret = credentials.resolve(auth.credentialId)
                    ?: error("Credential '${auth.credentialId}' is required.")
                mutableHeaders["Authorization"] = "Basic $secret"
            }
        }

        return AppliedAuth(
            headers = mutableHeaders,
            queryParameters = authQuery,
            redactedQueryParameters = redactedAuthQuery
        )
    }

    private fun parseResponse(type: ApiResponseType, body: String): ResultTree = when (type) {
        ApiResponseType.JSON -> {
            val trimmed = body.trim()
            JsonResultTreeParser(trimmed).parse()
        }

        ApiResponseType.TEXT,
        ApiResponseType.XML -> ResultTree.string(body)

        ApiResponseType.BINARY -> ResultTree.string(body)
    }

    private fun httpFailure(
        definition: ApiDefinition,
        requestedAt: Instant,
        retrievedAt: Instant,
        response: OnlineHttpResponse,
        prepared: PreparedApiRequest,
        status: OnlineExecutionStatus
    ): ApiExecutionResult = ApiExecutionResult(
        definitionId = definition.id,
        definitionVersion = definition.version,
        status = status,
        meta = ApiResultMeta(
            requestedAt = requestedAt,
            retrievedAt = retrievedAt,
            statusCode = response.statusCode,
            durationMs = response.durationMs,
            contentType = response.contentType,
            sourceUrlRedacted = prepared.redactedUrl
        ),
        data = ResultTree.NullNode,
        raw = RawResponse(
            body = response.body,
            contentType = response.contentType,
            sha256 = sha256(response.body)
        ),
        error = OnlineDataError(
            code = status,
            message = "API returned HTTP ${response.statusCode}.",
            detail = response.body.take(500)
        )
    )

    private fun failure(
        definitionId: String,
        definitionVersion: Int,
        requestedAt: Instant,
        status: OnlineExecutionStatus,
        message: String,
        detail: String = "",
        redactedUrl: String = ""
    ): ApiExecutionResult = ApiExecutionResult(
        resultId = UUID.randomUUID().toString(),
        definitionId = definitionId,
        definitionVersion = definitionVersion,
        status = status,
        meta = ApiResultMeta(requestedAt = requestedAt, sourceUrlRedacted = redactedUrl),
        data = ResultTree.NullNode,
        error = OnlineDataError(code = status, message = message, detail = detail)
    )
}

private fun ApiExecutionResult.asCachedCopy(
    requestedAt: Instant,
    isStale: Boolean,
    status: OnlineExecutionStatus? = null
): ApiExecutionResult = copy(
    status = status ?: if (isStale) OnlineExecutionStatus.STALE_CACHE else this.status,
    meta = meta.copy(
        requestedAt = requestedAt,
        fromCache = true,
        isStale = isStale
    )
)

private fun replaceTokens(template: String, values: Map<String, String>): String {
    var output = template
    values.forEach { (key, value) ->
        output = output.replace("{$key}", value).replace("\${$key}", value)
    }
    return output
}

private fun appendQuery(url: String, parameters: Map<String, String>): String {
    if (parameters.isEmpty()) return url
    val separator = if (url.contains("?")) "&" else "?"
    val query = parameters.entries.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }
    return "$url$separator$query"
}

private fun encode(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}
