package com.example.methodmesh.ui.odkcentral

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

object OdkCentralDeploymentStore {
    private const val PREFS = "methodmesh_odk_central_deployments"

    fun get(context: Context, serverUrl: String, projectId: Long, templateId: String): OdkTemplateDeployment? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(serverUrl, projectId, templateId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            OdkTemplateDeployment(
                serverUrl = json.getString("serverUrl"),
                projectId = json.getLong("projectId"),
                templateId = json.getString("templateId"),
                xmlFormId = json.getString("xmlFormId"),
                localSha256 = json.optString("localSha256"),
                assignedAppUserId = json.optLong("assignedAppUserId", -1L).takeIf { it >= 0L },
                lastSyncedAt = json.optString("lastSyncedAt")
            )
        }.getOrNull()
    }

    fun put(context: Context, deployment: OdkTemplateDeployment) {
        val json = JSONObject()
            .put("serverUrl", deployment.serverUrl)
            .put("projectId", deployment.projectId)
            .put("templateId", deployment.templateId)
            .put("xmlFormId", deployment.xmlFormId)
            .put("localSha256", deployment.localSha256)
            .put("lastSyncedAt", deployment.lastSyncedAt)
        deployment.assignedAppUserId?.let { json.put("assignedAppUserId", it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key(deployment.serverUrl, deployment.projectId, deployment.templateId), json.toString())
            .apply()
    }

    fun remove(context: Context, serverUrl: String, projectId: Long, templateId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(key(serverUrl, projectId, templateId))
            .apply()
    }

    private fun key(serverUrl: String, projectId: Long, templateId: String): String =
        sha256("${serverUrl.trimEnd('/')}|$projectId|$templateId")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
