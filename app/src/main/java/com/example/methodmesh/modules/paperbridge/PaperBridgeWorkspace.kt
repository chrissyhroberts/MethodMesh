package com.example.methodmesh.modules.paperbridge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class PaperBridgeRecentActivity(
    val scanTimeIso: String,
    val templateId: String,
    val templateVersion: String,
    val templateTitle: String,
    val fieldCount: Int,
    val autoAcceptedCount: Int,
    val reviewedCount: Int
)

/**
 * Small module-owned workspace index for the native Paper Bridge dashboard.
 *
 * This is deliberately not a questionnaire data store. It persists only:
 *  - paper template manifests selected/imported by the operator; and
 *  - non-identifying activity metadata used by the dashboard.
 *
 * Committed field values and participant identifiers are never written here.
 * ODK/calling workflows remain responsible for instance persistence/submission.
 */
internal object PaperBridgeWorkspace {
    private const val PREFS = "paperbridge_workspace_v1"
    private const val KEY_TEMPLATES = "templates_json"
    private const val KEY_ACTIVE = "active_template_key"
    private const val KEY_RECENT = "recent_activity_json"
    private const val MAX_RECENT = 8

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun templateKey(template: PaperTemplate): String = "${template.templateId}::${template.version}"

    fun isBundled(template: PaperTemplate): Boolean = PaperBridgeBuiltInExamples.isDemo(template)

    fun templates(context: Context): List<PaperTemplate> {
        val demo = PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON)
        val raw = prefs(context).getString(KEY_TEMPLATES, "[]").orEmpty()
        val custom = runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            (0 until array.length()).mapNotNull { index ->
                array.optString(index).takeIf(String::isNotBlank)?.let { manifest ->
                    runCatching { PaperTemplate.parse(manifest) }.getOrNull()
                }
            }
        }.getOrDefault(emptyList())
        return (listOf(demo) + custom)
            .distinctBy(::templateKey)
            .sortedWith(compareBy<PaperTemplate> { if (isBundled(it)) 0 else 1 }.thenBy { it.title.lowercase() })
    }

    fun activeTemplateJson(context: Context): String {
        val all = templates(context)
        val activeKey = prefs(context).getString(KEY_ACTIVE, null)
        return all.firstOrNull { templateKey(it) == activeKey }?.rawJson
            ?: all.first().rawJson
    }

    fun saveAndActivateTemplate(context: Context, manifestJson: String): PaperTemplate {
        val parsed = PaperTemplate.parse(manifestJson)
        if (!isBundled(parsed)) {
            val existing = templates(context).filterNot(::isBundled).toMutableList()
            val key = templateKey(parsed)
            val replaced = existing.map { if (templateKey(it) == key) parsed else it }.toMutableList()
            if (replaced.none { templateKey(it) == key }) replaced += parsed
            val array = JSONArray().apply { replaced.forEach { put(it.rawJson) } }
            prefs(context).edit().putString(KEY_TEMPLATES, array.toString()).apply()
        }
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(parsed)).apply()
        return parsed
    }

    fun activateTemplate(context: Context, template: PaperTemplate) {
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(template)).apply()
    }

    fun removeTemplate(context: Context, template: PaperTemplate) {
        if (isBundled(template)) return
        val remaining = templates(context)
            .filterNot(::isBundled)
            .filterNot { templateKey(it) == templateKey(template) }
        val array = JSONArray().apply { remaining.forEach { put(it.rawJson) } }
        val edit = prefs(context).edit().putString(KEY_TEMPLATES, array.toString())
        if (prefs(context).getString(KEY_ACTIVE, null) == templateKey(template)) {
            val demo = PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON)
            edit.putString(KEY_ACTIVE, templateKey(demo))
        }
        edit.apply()
    }

    fun recordCommit(
        context: Context,
        template: PaperTemplate,
        scanTimeIso: String,
        fieldCount: Int,
        autoAcceptedCount: Int,
        reviewedCount: Int
    ) {
        val entry = JSONObject()
            .put("scan_time_iso", scanTimeIso)
            .put("template_id", template.templateId)
            .put("template_version", template.version)
            .put("template_title", template.title)
            .put("field_count", fieldCount)
            .put("auto_accepted_count", autoAcceptedCount)
            .put("reviewed_count", reviewedCount)
        val previous = prefs(context).getString(KEY_RECENT, "[]").orEmpty()
        val output = JSONArray().put(entry)
        runCatching {
            val old = JSONArray(previous.ifBlank { "[]" })
            for (index in 0 until minOf(old.length(), MAX_RECENT - 1)) output.put(old.get(index))
        }
        prefs(context).edit().putString(KEY_RECENT, output.toString()).apply()
    }

    fun recent(context: Context): List<PaperBridgeRecentActivity> {
        val raw = prefs(context).getString(KEY_RECENT, "[]").orEmpty()
        return runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            (0 until minOf(array.length(), MAX_RECENT)).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                PaperBridgeRecentActivity(
                    scanTimeIso = item.optString("scan_time_iso"),
                    templateId = item.optString("template_id"),
                    templateVersion = item.optString("template_version"),
                    templateTitle = item.optString("template_title"),
                    fieldCount = item.optInt("field_count", 0),
                    autoAcceptedCount = item.optInt("auto_accepted_count", 0),
                    reviewedCount = item.optInt("reviewed_count", 0)
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clearRecent(context: Context) {
        prefs(context).edit().remove(KEY_RECENT).apply()
    }
}
