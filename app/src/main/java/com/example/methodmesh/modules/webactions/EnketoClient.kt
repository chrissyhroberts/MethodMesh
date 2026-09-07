package com.example.methodmesh.modules.webactions

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class EnketoLaunchResult(
    val url: String,
    val responseCode: Int,
    val defaultsCount: Int
)

internal object EnketoClient {
    fun createSingleSubmit(
        apiBaseUrl: String,
        serverUrl: String,
        formId: String,
        apiToken: String,
        singleMode: String,
        returnUrl: String,
        defaultsJson: String,
        theme: String,
        allowInsecureHttp: Boolean
    ): EnketoLaunchResult {
        val base = requireWebUrl(apiBaseUrl, allowInsecureHttp, "Enketo API base URL")
        requireWebUrl(serverUrl, allowInsecureHttp, "form server URL")
        require(formId.isNotBlank()) { "Form ID is required." }
        require(apiToken.isNotBlank()) { "Enketo API token is required." }

        val defaults = parseDefaults(defaultsJson)
        val endpoint = when (singleMode.trim().lowercase()) {
            "single_once" -> "/survey/single/once"
            else -> "/survey/single"
        }
        val responseField = if (singleMode.trim().lowercase() == "single_once") {
            "single_once_url"
        } else {
            "single_url"
        }
        val target = URL(base.trimEnd('/') + endpoint)
        val form = mutableListOf(
            "server_url" to serverUrl.trim(),
            "form_id" to formId.trim(),
            "return_url" to returnUrl
        )
        if (theme.isNotBlank()) form += "theme" to theme.trim()
        defaults.forEach { (path, value) -> form += "defaults[$path]" to value }
        val body = form.joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

        val connection = (target.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            // Do not forward the Basic API credential through an HTTP redirect.
            // A moved/misconfigured API endpoint must fail closed and be corrected explicitly.
            instanceFollowRedirects = false
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json")
            val basic = Base64.encodeToString("$apiToken:".toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
            setRequestProperty("Authorization", "Basic $basic")
        }

        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                // Remote error bodies can echo submitted parameters. Keep the user-facing
                // diagnostic useful without copying form defaults, URLs or credentials into
                // MethodMesh state/audit.
                error("Enketo API returned HTTP $code. Check the API endpoint, form server, form ID and credential.")
            }
            val json = JSONObject(responseText)
            val launchUrl = json.optString(responseField).ifBlank {
                if (responseField == "single_url") json.optString("single_url") else ""
            }
            require(launchUrl.isNotBlank()) { "Enketo API response did not contain $responseField." }
            requireWebUrl(launchUrl, allowInsecureHttp, "Enketo launch URL")
            EnketoLaunchResult(launchUrl, code, defaults.size)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDefaults(raw: String): Map<String, String> {
        if (raw.isBlank() || raw.trim() == "{}") return emptyMap()
        val json = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("Default values must be a flat JSON object.") }
        val result = linkedMapOf<String, String>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            require(key.startsWith("/")) { "Default value keys must be node paths beginning with /." }
            val value = json.get(key)
            require(value !is JSONObject && value !is org.json.JSONArray) {
                "Default values JSON must be flat; nested objects/arrays are not supported."
            }
            result[key] = if (value == JSONObject.NULL) "" else value.toString()
        }
        return result
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
}

internal fun requireWebUrl(raw: String, allowInsecureHttp: Boolean, label: String = "URL"): String {
    val uri = runCatching { android.net.Uri.parse(raw.trim()) }.getOrNull()
        ?: throw IllegalArgumentException("$label is invalid.")
    val scheme = uri.scheme?.lowercase().orEmpty()
    require(scheme == "https" || (allowInsecureHttp && scheme == "http")) {
        if (allowInsecureHttp) "$label must use HTTP or HTTPS." else "$label must use HTTPS. Enable Allow HTTP only for a trusted local/test service."
    }
    require(!uri.host.isNullOrBlank()) { "$label must include a host." }
    require(!uri.encodedAuthority.orEmpty().contains("@")) {
        "$label must not embed a username or password in the URL."
    }
    return raw.trim()
}

internal fun hostOf(raw: String): String = runCatching { android.net.Uri.parse(raw).host.orEmpty() }.getOrDefault("")

/**
 * Safe representation for outputs, audit-adjacent request context and UI.
 * The exact caller URL remains available only while it is needed to launch the
 * web action; obvious credential-bearing query values and fragments are not
 * copied into canonical MethodMesh results.
 */
internal fun redactedWebUrl(raw: String): String {
    val uri = runCatching { android.net.Uri.parse(raw.trim()) }.getOrNull() ?: return raw
    val host = uri.host.orEmpty()
    if (host.isBlank()) return raw
    val authority = buildString {
        append(host)
        if (uri.port >= 0) append(":${uri.port}")
    }
    val builder = uri.buildUpon().encodedAuthority(authority).clearQuery()
    uri.queryParameterNames.forEach { name ->
        val replacement = isSensitiveQueryName(name)
        uri.getQueryParameters(name).forEach { value ->
            builder.appendQueryParameter(name, if (replacement) "[redacted]" else value)
        }
    }
    if (!uri.fragment.isNullOrBlank()) builder.fragment("[redacted]")
    return builder.build().toString()
}

private fun isSensitiveQueryName(name: String): Boolean {
    val key = name.lowercase().replace("-", "_")
    if (key == "st" || key == "k" || key == "return_url") return true
    if (key.startsWith("d[") || key.startsWith("defaults[") || key.startsWith("prefill[")) return true
    return listOf(
        "token", "secret", "password", "passwd", "credential", "authorization",
        "auth", "api_key", "apikey", "access_key", "session", "signature", "refresh_token",
        "callback", "redirect_uri"
    ).any { marker -> key == marker || key.contains(marker) }
}
