package com.example.methodmesh.modules.webactions

import android.net.Uri
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate

internal enum class CentralLinkKind(val wireValue: String, val label: String) {
    PUBLIC_ACCESS("public_access", "Public Access Link"),
    DATA_COLLECTOR("data_collector", "Data Collector link"),
    LEGACY_PUBLIC_ACCESS("legacy_public_access", "Legacy Public Access Link"),
    KOBO_ENKETO("kobo_enketo", "Kobo / Enketo web-form link"),
    CENTRAL_WEB_FORM("central_web_form", "Central web-form link")
}

internal data class CentralLinkInfo(
    val url: String,
    val host: String,
    val kind: CentralLinkKind,
    val hasPublicAccessToken: Boolean
)

internal data class CentralPublicEnketoResolution(
    val launchUrl: String,
    val responseCode: Int
)

/**
 * Resolves a modern Central Public Access /f/<enketoId>?st=... link without
 * running the Central frontend shell. Central itself first resolves the public
 * link through /v1/form-links/<enketoId>/form?st=... and, when Enketo is the
 * configured renderer, embeds /enketo-passthrough/single/<enketoId>.
 *
 * MethodMesh mirrors that flow so Android WebView never has to execute the SPA
 * path that has been observed to collapse to a blank page. The Public Access
 * credential remains opaque: its already-encoded bytes are copied from the
 * pasted URL and are never logged or included in an exception message.
 */
internal fun resolveCentralPublicEnketoLaunch(
    link: CentralLinkInfo,
    callbackUrl: String,
    allowInsecureHttp: Boolean,
    cacheBuster: String = ""
): CentralPublicEnketoResolution {
    require(link.kind == CentralLinkKind.PUBLIC_ACCESS) {
        "Only an ODK Central Public Access link can be resolved this way."
    }
    val sourceUrl = requireWebUrl(link.url, allowInsecureHttp, "ODK Central Public Access link")
    val source = Uri.parse(sourceUrl)
    val routeEnketoId = source.pathSegments.getOrNull(1).orEmpty()
    require(routeEnketoId.isNotBlank()) { "Central Public Access link is missing its form identifier." }

    val encodedSt = encodedQueryValue(source.encodedQuery.orEmpty(), "st")
    require(!encodedSt.isNullOrBlank()) { "Central Public Access link is missing its st access token." }

    val origin = centralOrigin(source)
    val metadataUrl = "$origin/v1/form-links/${Uri.encode(routeEnketoId)}/form?st=$encodedSt"
    val connection = (URL(metadataUrl).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 15_000
        readTimeout = 20_000
        instanceFollowRedirects = false
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Cache-Control", "no-store")
    }

    return try {
        val code = connection.responseCode
        if (code !in 200..299) {
            when (code) {
                401, 403 -> error("Central rejected the Public Access credential. Re-copy the Public Access link and try again.")
                404 -> error("Central could not find this Public Access form. The link may have been revoked or the form may no longer be available.")
                else -> error("Central returned HTTP $code while resolving the Enketo form.")
            }
        }

        val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val json = runCatching { JSONObject(responseText) }
            .getOrElse { error("Central returned an invalid form-metadata response.") }

        if (jsonBooleanish(json, "webformsEnabled")) {
            error("This Central Public Access link is currently configured for ODK Web Forms. Open it with the ODK Web Forms capability instead.")
        }

        // A Public Access route can carry either the normal Enketo ID or the
        // once-only Enketo ID. Accept either value returned by Central and fail
        // closed if metadata points at a different form.
        val returnedIds = listOf(
            json.optString("enketoId"),
            json.optString("enketoOnceId")
        ).filter { it.isNotBlank() }.toSet()
        if (returnedIds.isNotEmpty() && routeEnketoId !in returnedIds) {
            error("Central returned inconsistent form metadata for this Public Access link.")
        }

        val passthrough = buildCentralEnketoPassthroughUrl(
            source = source,
            routeEnketoId = routeEnketoId,
            callbackUrl = callbackUrl,
            cacheBuster = cacheBuster
        )
        requireWebUrl(passthrough, allowInsecureHttp, "ODK Enketo launch URL")
        CentralPublicEnketoResolution(passthrough, code)
    } catch (error: IllegalStateException) {
        throw error
    } catch (error: IllegalArgumentException) {
        throw error
    } catch (_: Exception) {
        // Network exception messages often include the request URL. Never copy
        // those messages because this request URL contains the st credential.
        error("MethodMesh could not resolve the Central Enketo form. Check connectivity and try the Public Access link again.")
    } finally {
        connection.disconnect()
    }
}

private fun buildCentralEnketoPassthroughUrl(
    source: Uri,
    routeEnketoId: String,
    callbackUrl: String,
    cacheBuster: String
): String {
    val retainedQuery = encodedQueryPairs(source.encodedQuery.orEmpty()).filterNot { pair ->
        val key = Uri.decode(pair.substringBefore('='))
        key.equals("return_url", ignoreCase = true) ||
            key.equals("returnUrl", ignoreCase = true) ||
            key.equals("single", ignoreCase = true) ||
            key.equals("parentWindowOrigin", ignoreCase = true) ||
            key.equals("_methodmesh_run", ignoreCase = true)
    }.toMutableList()

    // Hosted-form roundtrip is intentionally one-shot: the single Enketo path
    // is what makes return_url terminal rather than starting another record.
    retainedQuery += "return_url=${Uri.encode(callbackUrl)}"
    if (cacheBuster.isNotBlank()) {
        retainedQuery += "_methodmesh_run=${Uri.encode(cacheBuster)}"
    }

    return buildString {
        append(centralOrigin(source))
        append("/enketo-passthrough/single/")
        append(Uri.encode(routeEnketoId))
        if (retainedQuery.isNotEmpty()) {
            append('?')
            append(retainedQuery.joinToString("&"))
        }
    }
}

private fun centralOrigin(uri: Uri): String = buildString {
    append(uri.scheme)
    append("://")
    append(uri.host)
    if (uri.port >= 0) append(":${uri.port}")
}

private fun encodedQueryPairs(encodedQuery: String): List<String> = encodedQuery
    .split('&')
    .map(String::trim)
    .filter(String::isNotBlank)

private fun encodedQueryValue(encodedQuery: String, targetName: String): String? =
    encodedQueryPairs(encodedQuery).firstNotNullOfOrNull { pair ->
        val encodedName = pair.substringBefore('=')
        if (Uri.decode(encodedName).equals(targetName, ignoreCase = true)) {
            pair.substringAfter('=', missingDelimiterValue = "")
        } else {
            null
        }
    }

private fun jsonBooleanish(json: JSONObject, name: String): Boolean = when (val value = json.opt(name)) {
    is Boolean -> value
    is Number -> value.toInt() != 0
    is String -> value.equals("true", ignoreCase = true) || value == "1"
    else -> false
}

/**
 * Central's human-facing web links have evolved over time. Keep this parser
 * deliberately tolerant: classification improves the UX and the Data Collector
 * single=true rule, but MethodMesh does not make a renderer/version assumption.
 */
internal fun inspectCentralLink(raw: String, allowInsecureHttp: Boolean): CentralLinkInfo {
    val safe = requireWebUrl(raw, allowInsecureHttp, "ODK Central web-form link")
    val uri = Uri.parse(safe)
    val path = uri.path.orEmpty()
    val lowerPath = path.lowercase()
    val kind = when {
        lowerPath.startsWith("/f/") -> CentralLinkKind.PUBLIC_ACCESS
        Regex("^/projects/[^/]+/forms/[^/]+/submissions/new(?:/offline)?/?$", RegexOption.IGNORE_CASE)
            .matches(path) -> CentralLinkKind.DATA_COLLECTOR
        lowerPath.contains("/-/single/") || lowerPath.contains("/-/") -> CentralLinkKind.LEGACY_PUBLIC_ACCESS
        Regex("^/(?:x|single)/[^/]+/?$", RegexOption.IGNORE_CASE).matches(path) -> CentralLinkKind.KOBO_ENKETO
        else -> CentralLinkKind.CENTRAL_WEB_FORM
    }
    return CentralLinkInfo(
        url = safe,
        host = uri.host.orEmpty(),
        kind = kind,
        hasPublicAccessToken = uri.queryParameterNames.any { it.equals("st", ignoreCase = true) }
    )
}

/**
 * Adds MethodMesh's one-shot completion URL while preserving all Central-owned
 * query parameters (including st). For authenticated Data Collector /new links,
 * Central documents single=true as the switch that enables a post-submit
 * return_url; ODK Web Forms safely ignores single while still honouring the
 * return destination.
 */
internal fun buildCentralLaunchUrl(
    link: CentralLinkInfo,
    callbackUrl: String,
    allowInsecureHttp: Boolean,
    disposableOnlineSession: Boolean = true,
    cacheBuster: String = ""
): String {
    if (link.kind == CentralLinkKind.PUBLIC_ACCESS) {
        // Public Access /f links carry an opaque st credential. Do not parse and
        // rebuild this URL because provider tokens may contain punctuation that
        // must remain byte-for-byte equivalent to the pasted link.
        return requireWebUrl(link.url, allowInsecureHttp, "ODK Central web-form link")
    }
    val parsed = Uri.parse(link.url)
    // Central <=2025.2.1 used /-/... Enketo routes. For an authenticated
    // Data Collector link without an st token, the documented redirect-after-
    // submit form is /-/single/<enketo-id>. Preserve already-single/public/
    // offline-style legacy routes unchanged.
    val source = when {
        disposableOnlineSession &&
            link.kind == CentralLinkKind.DATA_COLLECTOR &&
            parsed.path.orEmpty().endsWith("/offline", ignoreCase = true) -> {
            parsed.buildUpon().path(parsed.path.orEmpty().dropLast("/offline".length)).build()
        }
        link.kind == CentralLinkKind.LEGACY_PUBLIC_ACCESS &&
            Regex("^/-/[^/]+/?$", RegexOption.IGNORE_CASE).matches(parsed.path.orEmpty()) -> {
            // Legacy Central multi-submit Enketo routes reload a blank record after
            // submission. The /single route is the documented redirect-capable
            // equivalent and is therefore required for a MethodMesh roundtrip.
            val enketoId = parsed.pathSegments.getOrNull(1).orEmpty()
            parsed.buildUpon().path("/-/single/$enketoId").build()
        }
        else -> parsed
    }
    val builder = source.buildUpon().clearQuery()
    val forceSingleQuery = link.kind == CentralLinkKind.DATA_COLLECTOR

    source.queryParameterNames.forEach { name ->
        when {
            name.equals("return_url", ignoreCase = true) -> Unit
            name.equals("single", ignoreCase = true) && forceSingleQuery -> Unit
            else -> source.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
    }

    // Ask Central for a terminating return where its documented route supports it.
    // Kobo collection mode is selected by the link issued by Kobo itself; do not
    // invent a /single/ path for a pasted /x/ link.
    if (forceSingleQuery) builder.appendQueryParameter("single", "true")
    if (disposableOnlineSession && link.kind == CentralLinkKind.DATA_COLLECTOR) {
        builder.appendQueryParameter("offline", "false")
    }
    if (cacheBuster.isNotBlank() && link.kind !in setOf(CentralLinkKind.PUBLIC_ACCESS, CentralLinkKind.LEGACY_PUBLIC_ACCESS)) {
        builder.appendQueryParameter("_methodmesh_run", cacheBuster)
    }
    if (link.kind != CentralLinkKind.PUBLIC_ACCESS) {
        builder.appendQueryParameter("return_url", callbackUrl)
    }
    val result = builder.build().toString()
    requireWebUrl(result, allowInsecureHttp, "ODK Central web-form link")
    return result
}

internal fun appendWebActionCacheBuster(
    raw: String,
    cacheBuster: String,
    allowInsecureHttp: Boolean
): String {
    if (cacheBuster.isBlank()) return raw
    val source = Uri.parse(requireWebUrl(raw, allowInsecureHttp, "web form URL"))
    val builder = source.buildUpon().clearQuery()
    source.queryParameterNames.forEach { name ->
        if (!name.equals("_methodmesh_run", ignoreCase = true)) {
            source.getQueryParameters(name).forEach { value -> builder.appendQueryParameter(name, value) }
        }
    }
    builder.appendQueryParameter("_methodmesh_run", cacheBuster)
    val result = builder.build().toString()
    requireWebUrl(result, allowInsecureHttp, "web form URL")
    return result
}

/**
 * Resolve a flat prefill binding object into Enketo defaults. Bindings are
 * intentionally simple strings so they work from native use, presets, ODK and
 * protocol pipe values without introducing another expression language.
 *
 * Example:
 *   {"/data/participant_id":"{{participant_id}}", "/data/site":"MGB01"}
 */
internal fun resolvePrefillDefaults(
    literalDefaultsJson: String,
    bindingsJson: String,
    runtimeValues: Map<String, String>
): String {
    val merged = parseFlatObject(literalDefaultsJson, "Default values")
    val bindings = parseFlatObject(bindingsJson, "Prefill mappings")
    bindings.forEach { (path, template) ->
        merged[path] = resolvePrefillTemplate(template, runtimeValues)
    }
    return JSONObject(merged.toMap()).toString()
}

internal fun prefillPairs(raw: String): List<Pair<String, String>> =
    parseFlatObject(raw, "Prefill mappings").entries.map { it.key to it.value }

internal fun putPrefillPair(raw: String, path: String, value: String): String {
    val normalizedPath = path.trim()
    require(normalizedPath.startsWith("/")) { "Field path must begin with /." }
    val values = parseFlatObject(raw, "Prefill mappings")
    values[normalizedPath] = value
    return JSONObject(values.toMap()).toString()
}

internal fun removePrefillPair(raw: String, path: String): String {
    val values = parseFlatObject(raw, "Prefill mappings")
    values.remove(path)
    return JSONObject(values.toMap()).toString()
}

internal fun flatObjectCount(raw: String, label: String = "Values"): Int = parseFlatObject(raw, label).size
internal fun mergedPrefillPathCount(literalDefaultsJson: String, bindingsJson: String): Int =
    (parseFlatObject(literalDefaultsJson, "Default values").keys +
        parseFlatObject(bindingsJson, "Prefill mappings").keys).toSet().size


internal fun prefillRuntimeKeys(raw: String): List<String> {
    val builtIns = setOf("today", "now_iso")
    return prefillPairs(raw)
        .flatMap { (_, template) ->
            Regex("\\{\\{([A-Za-z0-9_.:-]+)}}").findAll(template).map { it.groupValues[1] }.toList()
        }
        .filterNot { it.lowercase() in builtIns }
        .distinct()
}

private fun parseFlatObject(raw: String, label: String): LinkedHashMap<String, String> {
    if (raw.isBlank() || raw.trim() == "{}") return linkedMapOf()
    val json = runCatching { JSONObject(raw) }
        .getOrElse { throw IllegalArgumentException("$label must be a flat JSON object.") }
    val result = linkedMapOf<String, String>()
    val keys = json.keys()
    while (keys.hasNext()) {
        val key = keys.next()
        require(key.startsWith("/")) { "$label field paths must begin with /." }
        val value = json.get(key)
        require(value !is JSONObject && value !is org.json.JSONArray) { "$label must be flat." }
        result[key] = if (value == JSONObject.NULL) "" else value.toString()
    }
    return result
}

private fun resolvePrefillTemplate(template: String, runtimeValues: Map<String, String>): String {
    val exact = Regex("^\\{\\{([A-Za-z0-9_.:-]+)}}$").matchEntire(template.trim())
    if (exact != null) return resolvePrefillKey(exact.groupValues[1], runtimeValues)

    return Regex("\\{\\{([A-Za-z0-9_.:-]+)}}").replace(template) { match ->
        resolvePrefillKey(match.groupValues[1], runtimeValues)
    }
}

private fun resolvePrefillKey(key: String, runtimeValues: Map<String, String>): String = when (key.lowercase()) {
    "now_iso" -> Instant.now().toString()
    "today" -> LocalDate.now().toString()
    else -> runtimeValues[key]
        ?: runtimeValues["input_$key"]
        ?: throw IllegalArgumentException("Prefill source {{$key}} is not available in this run.")
}
