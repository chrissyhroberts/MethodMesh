package com.example.methodmesh.transport.android

import org.json.JSONArray

/** Exact returned attachment values, not URI-like text buried in audit metadata. */
internal object ExternalReturnAttachments {
    fun contentUris(field: String, value: Any?): List<String> {
        val text = value?.toString()?.trim().orEmpty()
        if (isContentUri(text)) return listOf(text)
        if (field.endsWith("_clipdata")) {
            return text.lineSequence().map(String::trim).filter(::isContentUri).distinct().toList()
        }
        // Declared list fields retain their original JSON/keys. Do not crawl
        // arbitrary JSON: a URI recorded as evidence is not a grant request.
        if (!field.endsWith("_uris_json") && !field.endsWith("_uris")) return emptyList()
        return runCatching {
            val array = JSONArray(text)
            (0 until array.length()).mapNotNull { index ->
                (array.opt(index) as? String)?.trim()?.takeIf(::isContentUri)
            }.distinct()
        }.getOrDefault(emptyList())
    }

    fun preserveFields(fields: Map<String, Any?>, target: MutableMap<String, String?>) {
        fields.forEach { (key, value) ->
            if (contentUris(key, value).isNotEmpty()) target[key] = value?.toString()
        }
    }

    private fun isContentUri(value: String): Boolean {
        if (!value.startsWith("content://") || value.any { it.isISOControl() }) return false
        val authority = value.removePrefix("content://").takeWhile { it !in "/?#" }
        // Android Uri accepts literal spaces/Unicode in paths. Preserve those
        // historical values verbatim rather than imposing java.net.URI syntax.
        return authority.isNotBlank() && authority.none(Char::isWhitespace)
    }
}
