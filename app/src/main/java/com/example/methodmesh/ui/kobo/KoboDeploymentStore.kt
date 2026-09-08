package com.example.methodmesh.ui.kobo

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

object KoboDeploymentStore {
    private const val PREFS = "methodmesh_kobo_deployments"

    fun get(context: Context, serverUrl: String, templateId: String): KoboTemplateDeployment? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(serverUrl, templateId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            KoboTemplateDeployment(
                serverUrl = json.getString("serverUrl"),
                templateId = json.getString("templateId"),
                assetUid = json.getString("assetUid"),
                localSha256 = json.optString("localSha256"),
                active = json.optBoolean("active", false),
                namespacedCompatibilityCopy = json.optBoolean("namespacedCompatibilityCopy", false),
                lastSyncedAt = json.optString("lastSyncedAt")
            )
        }.getOrNull()
    }

    fun put(context: Context, deployment: KoboTemplateDeployment) {
        val json = JSONObject()
            .put("serverUrl", deployment.serverUrl)
            .put("templateId", deployment.templateId)
            .put("assetUid", deployment.assetUid)
            .put("localSha256", deployment.localSha256)
            .put("active", deployment.active)
            .put("namespacedCompatibilityCopy", deployment.namespacedCompatibilityCopy)
            .put("lastSyncedAt", deployment.lastSyncedAt)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(deployment.serverUrl, deployment.templateId), json.toString())
            .apply()
    }

    fun remove(context: Context, serverUrl: String, templateId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(serverUrl, templateId))
            .apply()
    }

    private fun key(serverUrl: String, templateId: String): String = sha256("${serverUrl.trimEnd('/')}|$templateId")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
