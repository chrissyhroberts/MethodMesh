package com.example.methodmesh.ui.kobo

import android.content.Context

object KoboConnectionRepository {
    private const val PREFS = "methodmesh_kobo"
    private const val SERVER = "server_url"
    private const val USERNAME = "username"
    private const val DEFAULT_SERVER = "https://kf.kobotoolbox.org"

    fun load(context: Context): KoboProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return KoboProfile(
            serverUrl = prefs.getString(SERVER, DEFAULT_SERVER).orEmpty().ifBlank { DEFAULT_SERVER },
            username = prefs.getString(USERNAME, "").orEmpty()
        )
    }

    fun saveConnection(context: Context, serverUrl: String, username: String) {
        val normalized = normalizeServerUrl(serverUrl)
        val old = load(context)
        val changed = old.serverUrl != normalized || old.username != username.trim()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SERVER, normalized)
            .putString(USERNAME, username.trim())
            .apply()
        if (changed) KoboSessionStore.clear(context)
    }

    fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return DEFAULT_SERVER
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
