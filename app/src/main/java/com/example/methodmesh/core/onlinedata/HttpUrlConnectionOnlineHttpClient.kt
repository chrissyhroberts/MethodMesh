package com.example.methodmesh.core.onlinedata

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Clock
import java.time.Duration
import java.time.Instant

class HttpUrlConnectionOnlineHttpClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 20_000,
    private val userAgent: String = "MethodMesh Android"
) : OnlineHttpClient {
    override fun get(request: OnlineHttpRequest): OnlineHttpResponse {
        val started = Instant.now(Clock.systemUTC())
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", userAgent)
            request.headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }

        return try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }.orEmpty()
            OnlineHttpResponse(
                statusCode = status,
                body = body,
                contentType = connection.contentType.orEmpty(),
                headers = connection.headerFields
                    .filterKeys { it != null }
                    .mapKeys { it.key.orEmpty() }
                    .mapValues { it.value.orEmpty().joinToString(", ") },
                durationMs = Duration.between(started, Instant.now(Clock.systemUTC())).toMillis()
            )
        } finally {
            connection.disconnect()
        }
    }
}

