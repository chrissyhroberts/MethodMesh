package com.example.methodmesh.modules.odkformlauncher

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import java.util.Locale

data class OdkFormDescriptor(val id: String, val name: String, val uri: Uri, val projectId: String = "", val packageName: String = "")

sealed interface OdkFormLookupResult {
    data class Found(val form: OdkFormDescriptor) : OdkFormLookupResult
    data class NotFound(val available: List<String>) : OdkFormLookupResult
    data class ProviderUnavailable(val reason: String) : OdkFormLookupResult
}

object OdkFormLauncherRepository {
    private data class ProviderUris(val forms: Uri, val newest: Uri, val packageName: String)
    private val providers = listOf(
        ProviderUris(
            Uri.parse("content://org.odk.collect.android.provider.odk.forms/forms"),
            Uri.parse("content://org.odk.collect.android.provider.odk.forms/newest_forms_by_form_id"),
            "org.odk.collect.android"
        ),
        ProviderUris(
            Uri.parse("content://org.koboc.collect.android.provider.odk.forms/forms"),
            Uri.parse("content://org.koboc.collect.android.provider.odk.forms/newest_forms_by_form_id"),
            "org.koboc.collect.android"
        )
    )

    fun find(context: Context, selector: String, projectId: String = "", preferredPackage: String = ""): OdkFormDescriptor? {
        return when (val result = lookup(context, selector, projectId, preferredPackage)) {
            is OdkFormLookupResult.Found -> result.form
            else -> null
        }
    }

    fun list(context: Context, projectId: String, preferredPackage: String = ""): List<OdkFormDescriptor> {
        if (projectId.isBlank()) return emptyList()
        return providers.sortedBy { if (it.packageName == preferredPackage) 0 else 1 }.flatMap { provider ->
            val cursors = listOf(provider.newest, provider.forms).mapNotNull { baseUri ->
                val queryUri = baseUri.buildUpon().appendQueryParameter("projectId", projectId).build()
                runCatching { context.contentResolver.query(queryUri, null, null, null, null) }.getOrNull()
            }
            cursors.flatMap { cursor ->
                cursor.use { rows ->
                    val columns = rows.columnNames.associateBy { normalise(it) }
                    buildList {
                        while (rows.moveToNext()) {
                            val row = columns.mapValues { (_, indexName) ->
                                rows.getColumnIndex(indexName).let { index -> if (index >= 0) rows.getString(index).orEmpty() else "" }
                            }
                            rowToDescriptor(row, projectId, provider.forms, provider.packageName)?.let { add(it) }
                        }
                    }
                }
            }
        }.distinctBy { it.id.trim().lowercase(Locale.ROOT) }
    }

    fun describe(context: Context, uri: Uri, projectId: String): OdkFormDescriptor? {
        val cursor = runCatching { context.contentResolver.query(uri, null, null, null, null) }.getOrNull() ?: return null
        cursor.use { rows ->
            if (!rows.moveToFirst()) return null
            val columns = rows.columnNames.associateBy { normalise(it) }
            val row = columns.mapValues { (_, indexName) ->
                rows.getColumnIndex(indexName).let { index -> if (index >= 0) rows.getString(index).orEmpty() else "" }
            }
            val providerPackage = if (uri.authority.orEmpty().startsWith("org.koboc")) {
                "org.koboc.collect.android"
            } else {
                "org.odk.collect.android"
            }
            return rowToDescriptor(row, projectId, uri.buildUpon().path("/forms").build(), providerPackage)
        }
    }

    fun lookup(context: Context, selector: String, projectId: String = "", preferredPackage: String = ""): OdkFormLookupResult {
        val wanted = selector.trim()
        if (wanted.isBlank()) return OdkFormLookupResult.NotFound(emptyList())
        val wantedExact = wanted.lowercase(Locale.ROOT)
        val wantedLoose = loose(wanted)
        var providerError: Throwable? = null
        var providerQuerySucceeded = false
        val available = linkedSetOf<String>()
        for (provider in providers.sortedBy { if (it.packageName == preferredPackage) 0 else 1 }) for (lookupUri in listOf(provider.newest, provider.forms)) {
            // Collect selects the project database from this URI query parameter.
            // Without it, Collect defaults to the first configured project.
            val projectScopedUri = lookupUri.buildUpon()
                .apply { if (projectId.isNotBlank()) appendQueryParameter("projectId", projectId) }
                .build()
            val exactQuery = runCatching {
                context.contentResolver.query(projectScopedUri, null, "jrFormId = ?", arrayOf(wanted), null)
            }.onFailure { providerError = it }
            val allQuery = runCatching {
                context.contentResolver.query(projectScopedUri, null, null, null, null)
            }.onFailure { providerError = it }
            if (exactQuery.getOrNull() != null || allQuery.getOrNull() != null) providerQuerySucceeded = true
            val cursors = listOfNotNull(exactQuery.getOrNull(), allQuery.getOrNull())
            for (cursor in cursors.distinctBy { it }) cursor.use { rows ->
                val columns = rows.columnNames.associateBy { normalise(it) }
                while (rows.moveToNext()) {
                    val row = columns.mapValues { (_, indexName) ->
                        rows.getColumnIndex(indexName).let { index -> if (index >= 0) rows.getString(index).orEmpty() else "" }
                    }
                    val id = value(row, "jrformid", "formid", "form_id", "xmlformid")
                    val name = value(row, "displayname", "formname", "form_name", "name")
                    if (id.isNotBlank()) available += id
                    if (name.isNotBlank() && !name.equals(id, ignoreCase = true)) available += name
                    val idMatches = id.lowercase(Locale.ROOT) == wantedExact || loose(id) == wantedLoose
                    val nameMatches = name.lowercase(Locale.ROOT) == wantedExact || loose(name) == wantedLoose
                    if (idMatches || nameMatches) {
                        rowToDescriptor(row, projectId, provider.forms, provider.packageName)?.let { return OdkFormLookupResult.Found(it) }
                    }
                }
            }
        }
        return if (!providerQuerySucceeded) {
            OdkFormLookupResult.ProviderUnavailable(
                providerError?.message?.takeIf(String::isNotBlank)
                    ?: "ODK Collect did not expose its forms provider."
            )
        } else {
            OdkFormLookupResult.NotFound(available.toList())
        }
    }

    private fun value(row: Map<String, String>, vararg keys: String): String =
        keys.asSequence().mapNotNull { row[normalise(it)] }.firstOrNull(String::isNotBlank).orEmpty()

    private fun rowToDescriptor(row: Map<String, String>, projectId: String, formsUri: Uri, packageName: String): OdkFormDescriptor? {
        val id = value(row, "jrformid", "formid", "form_id", "xmlformid")
        val name = value(row, "displayname", "formname", "form_name", "name")
        val databaseId = value(row, "_id", "id")
        // Prefer the provider's canonical item URI when it is exposed. Collect's
        // FormUriActivity validates both the path and MIME type, so rebuilding a
        // URI from a view's _id can yield a queryable but unrecognised URI.
        val explicitUri = value(row, "formuri", "uri", "contenturi")
            .takeIf { it.startsWith("content://") }
            ?.let(Uri::parse)
            ?.takeIf { uri -> uri.pathSegments.lastOrNull()?.toLongOrNull() != null }
        val uri = explicitUri?.buildUpon()?.clearQuery()?.apply {
            if (projectId.isNotBlank()) appendQueryParameter("projectId", projectId)
        }?.build() ?: databaseId.toLongOrNull()?.let {
            ContentUris.withAppendedId(formsUri, it).buildUpon()
                .apply { if (projectId.isNotBlank()) appendQueryParameter("projectId", projectId) }
                .build()
        }
        return if (id.isBlank() || uri == null) null else OdkFormDescriptor(id, name.ifBlank { id }, uri, projectId, packageName)
    }

    private fun normalise(value: String): String = value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)

    private fun loose(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)
}
