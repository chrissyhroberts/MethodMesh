package com.example.methodmesh.core.protocols

import android.content.Context

/** Local dashboard shortcuts, separate from preset configuration and portable contracts. */
object PresetFavoritesRepository {
    private const val PREFS = "methodmesh_preset_favorites"
    private const val KEY = "preset_ids"

    fun all(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun contains(context: Context, presetId: String): Boolean = presetId in all(context)

    @Synchronized
    fun set(context: Context, presetId: String, favorite: Boolean) {
        val next = all(context).toMutableSet().apply {
            if (favorite) add(presetId) else remove(presetId)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, next).apply()
    }

    @Synchronized
    fun toggle(context: Context, presetId: String): Boolean {
        val next = !contains(context, presetId)
        set(context, presetId, next)
        return next
    }
}
