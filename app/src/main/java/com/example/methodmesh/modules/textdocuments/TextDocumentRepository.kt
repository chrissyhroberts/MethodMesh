package com.example.methodmesh.modules.textdocuments

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentTextDocument(
    val uri: String,
    val title: String,
    val mimeType: String,
    val openedAt: Long
)

class TextDocumentRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("textdocuments_recent_v1", Context.MODE_PRIVATE)

    /**
     * Recent files are intentionally stronger than a simple activity log:
     * every returned item must still have a persisted Android read grant.
     *
     * This silently migrates/prunes older transient entries such as messaging
     * app attachment URIs which were valid only for the original launch.
     */
    fun recent(): List<RecentTextDocument> {
        val raw = rawRecent()
        val durable = raw.filter { item ->
            runCatching {
                DocumentIo.hasPersistedReadAccess(appContext, Uri.parse(item.uri))
            }.getOrDefault(false)
        }
        if (durable.size != raw.size) write(durable)
        return durable.sortedByDescending { it.openedAt }
    }

    /**
     * Add a URI only when Android has granted durable read access.
     * Returns true when the entry was retained.
     */
    fun remember(uri: Uri, title: String, mimeType: String): Boolean {
        if (!DocumentIo.hasPersistedReadAccess(appContext, uri)) {
            forget(uri.toString())
            return false
        }

        val next = listOf(
            RecentTextDocument(uri.toString(), title, mimeType, System.currentTimeMillis())
        ) + rawRecent().filterNot { it.uri == uri.toString() }

        write(next.take(20))
        return true
    }

    fun forget(uri: String) {
        write(rawRecent().filterNot { it.uri == uri })
    }

    fun clear() {
        prefs.edit().remove("items").apply()
    }

    private fun rawRecent(): List<RecentTextDocument> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]").orEmpty())
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { item ->
                RecentTextDocument(
                    uri = item.optString("uri"),
                    title = item.optString("title"),
                    mimeType = item.optString("mime"),
                    openedAt = item.optLong("openedAt")
                )
            }
        }.filter { it.uri.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun write(items: List<RecentTextDocument>) {
        val array = JSONArray()
        items.take(20).forEach { document ->
            array.put(
                JSONObject()
                    .put("uri", document.uri)
                    .put("title", document.title)
                    .put("mime", document.mimeType)
                    .put("openedAt", document.openedAt)
            )
        }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
