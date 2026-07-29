package com.example.researchos.modules.appinspector

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AppIntegrationDefinition(
    val id: String,
    val label: String,
    val packageName: String,
    val componentName: String,
    val action: String,
    val uri: String,
    val extras: String
)

object AppInspectorRepository {
    private const val PREFS = "researchos_android_app_integrations"
    private const val KEY = "definitions"

    fun all(context: Context): List<AppIntegrationDefinition> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { index ->
            array.optJSONObject(index)?.let { json ->
                AppIntegrationDefinition(
                    id = json.optString("id"),
                    label = json.optString("label"),
                    packageName = json.optString("package_name"),
                    componentName = json.optString("component_name"),
                    action = json.optString("action"),
                    uri = json.optString("uri"),
                    extras = json.optString("extras")
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(context: Context, definition: AppIntegrationDefinition) {
        val retained = all(context).filterNot { it.id == definition.id } + definition
        val array = JSONArray().apply {
            retained.forEach { item ->
                put(JSONObject().apply {
                    put("id", item.id)
                    put("label", item.label)
                    put("package_name", item.packageName)
                    put("component_name", item.componentName)
                    put("action", item.action)
                    put("uri", item.uri)
                    put("extras", item.extras)
                })
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }
}
