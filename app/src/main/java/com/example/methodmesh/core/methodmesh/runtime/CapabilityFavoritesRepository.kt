package com.example.methodmesh.core.methodmesh.runtime

import android.content.Context

/** Persistent user shortcuts for the capabilities they use most often. */
object CapabilityFavoritesRepository {
    private const val PREFS = "methodmesh_capability_favorites"
    private const val KEY = "capability_ids"

    fun all(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet())
        .orEmpty()

    fun contains(context: Context, capabilityId: String): Boolean = capabilityId in all(context)

    fun set(context: Context, capabilityId: String, favorite: Boolean) {
        val next = all(context).toMutableSet().apply {
            if (favorite) add(capabilityId) else remove(capabilityId)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, next)
            .apply()
    }

    fun toggle(context: Context, capabilityId: String): Boolean {
        val next = !contains(context, capabilityId)
        set(context, capabilityId, next)
        return next
    }
}
