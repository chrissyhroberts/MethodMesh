package com.example.methodmesh.transport

import org.json.JSONArray
import org.json.JSONObject

/** Portable package references, without rewriting canonical audit JSON or widening native media selection. */
internal object ExportAttachmentProjection {
    data class Attachment(val field: String, val source: String, val filename: String)
    data class Plan(val fields: JSONObject, val attachments: List<Attachment>)

    fun plan(fields: Map<String, Any?>, filename: (String, String) -> String): Plan {
        val attachments = ResultShare.shareableMediaAttachmentValues(fields).map {
            Attachment(it.field, it.uri, filename(it.field, it.uri))
        }
        val names = attachments.associate { it.source to it.filename }
        val projected = JSONObject()
        fields.toSortedMap().forEach { (key, value) ->
            val raw = value?.toString()?.trim().orEmpty()
            val array = if (ResultShare.isLikelyMediaFieldName(key) && raw.startsWith("[")) {
                runCatching { JSONArray(raw) }.getOrNull()
            } else null
            val next = when {
                value == null -> null
                names.containsKey(raw) -> names.getValue(raw)
                key.endsWith("_clipdata", ignoreCase = true) && raw.lineSequence().any { it.trim() in names } ->
                    raw.lineSequence().joinToString("\n") { names[it.trim()] ?: it }
                array != null -> {
                    val portable = JSONArray()
                    var changed = false
                    for (index in 0 until array.length()) {
                        val item = array.get(index)
                        val name = if (item is String) names[item.trim()] else null
                        if (name != null) changed = true
                        portable.put(name ?: item)
                    }
                    if (!changed) value else if (value is String) portable.toString() else portable
                }
                else -> value
            }
            projected.put(key, next ?: JSONObject.NULL)
        }
        return Plan(projected, attachments)
    }
}
