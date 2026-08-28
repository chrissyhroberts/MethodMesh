package com.example.methodmesh.platform.externalforms

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.util.Locale

data class ExternalForm(val id: String, val name: String, val uri: Uri, val projectId: String, val packageName: String)
data class ExternalProject(val id: String, val name: String, val packageName: String = "")

object ExternalProjectRegistry {
    private const val PREFS = "odk_form_launcher"
    private const val KEY = "projects"
    fun load(context: Context): List<ExternalProject> = runCatching {
        val array = org.json.JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { ExternalProject(it.optString("id"), it.optString("name"), it.optString("packageName")) } }
    }.getOrDefault(emptyList())
    fun save(context: Context, project: ExternalProject) {
        val values = load(context).filterNot { it.id == project.id } + project
        val array = org.json.JSONArray().apply { values.forEach { put(org.json.JSONObject().put("id", it.id).put("name", it.name).put("packageName", it.packageName)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}

object ExternalFormCatalog {
    private data class Provider(val forms: Uri, val newest: Uri, val packageName: String)
    private val providers = listOf(
        Provider(Uri.parse("content://org.odk.collect.android.provider.odk.forms/forms"), Uri.parse("content://org.odk.collect.android.provider.odk.forms/newest_forms_by_form_id"), "org.odk.collect.android"),
        Provider(Uri.parse("content://org.koboc.collect.android.provider.odk.forms/forms"), Uri.parse("content://org.koboc.collect.android.provider.odk.forms/newest_forms_by_form_id"), "org.koboc.collect.android")
    )
    fun describe(context: Context, uri: Uri, projectId: String): ExternalForm? = query(context, uri)?.let { rowToForm(it, projectId, uri.authority.orEmpty()) }
    fun list(context: Context, projectId: String, preferredPackage: String = ""): List<ExternalForm> = providers.sortedBy { if (it.packageName == preferredPackage) 0 else 1 }.flatMap { provider ->
        listOf(provider.newest, provider.forms).flatMap { base ->
            val queryUri = base.buildUpon().appendQueryParameter("projectId", projectId).build()
            val cursor = runCatching { context.contentResolver.query(queryUri, null, null, null, null) }.getOrNull() ?: return@flatMap emptyList()
            cursor.use { rows ->
                val columns = rows.columnNames.associateBy { normalise(it) }
                buildList {
                    while (rows.moveToNext()) {
                        val row = columns.mapValues { (_, column) -> rows.getColumnIndex(column).let { i -> if (i >= 0) rows.getString(i).orEmpty() else "" } }
                        rowToForm(row, projectId, provider.forms.authority.orEmpty(), provider)?.let { add(it) }
                    }
                }
            }
        }
    }.distinctBy { it.id.lowercase(Locale.ROOT) }

    private fun query(context: Context, uri: Uri): Map<String, String>? {
        val cursor = runCatching { context.contentResolver.query(uri, null, null, null, null) }.getOrNull() ?: return null
        cursor.use { rows ->
            if (!rows.moveToFirst()) return null
            val columns = rows.columnNames.associateBy { normalise(it) }
            return columns.mapValues { (_, column) -> rows.getColumnIndex(column).let { i -> if (i >= 0) rows.getString(i).orEmpty() else "" } }
        }
    }
    private fun rowToForm(row: Map<String, String>, projectId: String, authority: String, provider: Provider? = providers.firstOrNull { it.forms.authority == authority }): ExternalForm? {
        val id = value(row, "jrformid", "formid", "form_id", "xmlformid")
        val name = value(row, "displayname", "formname", "form_name", "name").ifBlank { id }
        val packageName = provider?.packageName ?: if (authority.startsWith("org.koboc")) "org.koboc.collect.android" else "org.odk.collect.android"
        val explicit = value(row, "formuri", "uri", "contenturi").takeIf { it.startsWith("content://") }?.let(Uri::parse)?.takeIf { it.pathSegments.lastOrNull()?.toLongOrNull() != null }
        val dbId = value(row, "_id", "id").toLongOrNull()
        val uri = explicit ?: dbId?.let { ContentUris.withAppendedId(provider?.forms ?: Uri.parse("content://$authority/forms"), it) }
        val scoped = uri?.buildUpon()?.clearQuery()?.apply { if (projectId.isNotBlank()) appendQueryParameter("projectId", projectId) }?.build()
        return if (id.isBlank() || scoped == null) null else ExternalForm(id, name, scoped, projectId, packageName)
    }
    private fun value(row: Map<String, String>, vararg keys: String) = keys.asSequence().mapNotNull { row[normalise(it)] }.firstOrNull(String::isNotBlank).orEmpty()
    private fun normalise(value: String) = value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
}
