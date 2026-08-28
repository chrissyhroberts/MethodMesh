package com.example.methodmesh.modules.odkformlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class OdkProject(val id: String, val name: String, val packageName: String = "")

object OdkProjectRegistry {
    private const val PREFS = "odk_form_launcher"
    private const val PROJECTS = "projects"

    fun load(context: Context): List<OdkProject> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PROJECTS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id").trim()
                val name = item.optString("name").trim()
                val packageName = item.optString("packageName").trim()
                if (id.isNotBlank()) add(OdkProject(id, name.ifBlank { id }, packageName))
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, project: OdkProject) {
        val projects = load(context).filterNot { it.id == project.id } + project
        val array = JSONArray().apply {
            projects.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("packageName", it.packageName)) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PROJECTS, array.toString()).apply()
    }

    fun remove(context: Context, projectId: String) {
        val projects = load(context).filterNot { it.id == projectId }
        val array = JSONArray().apply {
            projects.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("packageName", it.packageName)) }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PROJECTS, array.toString()).apply()
    }
}
