package com.example.methodmesh.core.protocols

import android.content.Context

/** Local shortcuts to active protocol IDs; never part of protocol versions or bundle hashes. */
object ProtocolFavoritesRepository {
    private const val PREFS = "methodmesh_protocol_favorites"
    private const val KEY = "protocol_ids"

    fun all(context: Context): Set<String> = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getStringSet(KEY, emptySet()).orEmpty().toSet()

    fun contains(context: Context, protocolId: String): Boolean = protocolId in all(context)

    @Synchronized
    fun set(context: Context, protocolId: String, favorite: Boolean) {
        val next = all(context).toMutableSet().apply {
            if (favorite) add(protocolId) else remove(protocolId)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY, next).apply()
    }

    @Synchronized
    fun toggle(context: Context, protocolId: String): Boolean {
        val next = !contains(context, protocolId)
        set(context, protocolId, next)
        return next
    }
}
