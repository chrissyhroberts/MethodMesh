package com.example.methodmesh.ui.odkcentral

import android.content.Context

object OdkCentralConnectionRepository {
    private const val PREFS = "methodmesh_odk_central"
    private const val SERVER = "server_url"
    private const val EMAIL = "email"
    private const val PROJECT_ID = "project_id"
    private const val PROJECT_NAME = "project_name"
    private const val APP_USER_ID = "app_user_id"
    private const val APP_USER_NAME = "app_user_name"

    fun load(context: Context): OdkCentralProfile {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OdkCentralProfile(
            serverUrl = prefs.getString(SERVER, "").orEmpty(),
            email = prefs.getString(EMAIL, "").orEmpty(),
            projectId = prefs.getLong(PROJECT_ID, -1L).takeIf { it >= 0L },
            projectName = prefs.getString(PROJECT_NAME, "").orEmpty(),
            appUserId = prefs.getLong(APP_USER_ID, -1L).takeIf { it >= 0L },
            appUserName = prefs.getString(APP_USER_NAME, "").orEmpty()
        )
    }

    fun saveConnection(context: Context, serverUrl: String, email: String) {
        val normalized = normalizeServerUrl(serverUrl)
        val old = load(context)
        val changedAccount = (old.serverUrl.isNotBlank() && old.serverUrl != normalized) ||
            (old.email.isNotBlank() && old.email != email.trim())
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SERVER, normalized)
            .putString(EMAIL, email.trim())
        if (changedAccount) {
            editor.remove(PROJECT_ID)
            editor.remove(PROJECT_NAME)
            editor.remove(APP_USER_ID)
            editor.remove(APP_USER_NAME)
        }
        editor.apply()
    }

    fun selectProject(context: Context, project: OdkCentralProject) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(PROJECT_ID, project.id)
            .putString(PROJECT_NAME, project.name)
            .remove(APP_USER_ID)
            .remove(APP_USER_NAME)
            .apply()
    }

    fun selectAppUser(context: Context, user: OdkCentralAppUser) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(APP_USER_ID, user.id)
            .putString(APP_USER_NAME, user.displayName)
            .apply()
    }

    fun clearSelection(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(PROJECT_ID)
            .remove(PROJECT_NAME)
            .remove(APP_USER_ID)
            .remove(APP_USER_NAME)
            .apply()
    }

    fun normalizeServerUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
