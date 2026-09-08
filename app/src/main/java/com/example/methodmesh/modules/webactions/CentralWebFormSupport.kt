package com.example.methodmesh.modules.webactions

import android.net.Uri
import org.json.JSONObject
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
    allowInsecureHttp: Boolean
): String {
    val parsed = Uri.parse(link.url)
    // Central <=2025.2.1 used /-/... Enketo routes. For an authenticated
    // Data Collector link without an st token, the documented redirect-after-
    // submit form is /-/single/<enketo-id>. Preserve already-single/public/
    // offline-style legacy routes unchanged.
    val source = when {
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
    val forceSingleQuery = link.kind == CentralLinkKind.PUBLIC_ACCESS ||
        link.kind == CentralLinkKind.DATA_COLLECTOR

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
    builder.appendQueryParameter("return_url", callbackUrl)
    val result = builder.build().toString()
    requireWebUrl(result, allowInsecureHttp, "ODK Central web-form link")
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
