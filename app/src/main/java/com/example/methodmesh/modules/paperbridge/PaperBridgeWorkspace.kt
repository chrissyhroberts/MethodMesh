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
    private const val PREFS = "paperbridge_workspace_v2"
    private const val LEGACY_PREFS = "paperbridge_workspace_v1"
    private const val KEY_ACTIVE = "active_template_key"
    private const val KEY_RECENT = "recent_activity_json"
    private const val KEY_LEGACY_TEMPLATES = "templates_json"
    private const val MAX_RECENT = 8

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun legacyPrefs(context: Context) = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
    private fun schemaDir(context: Context) = java.io.File(context.filesDir, "paperbridge/schemas").apply { mkdirs() }

    fun templateKey(template: PaperTemplate): String = "${template.templateId}::${template.version}"
    fun isBundled(template: PaperTemplate): Boolean = PaperBridgeBuiltInExamples.isDemo(template)

    /**
     * Durable user schemas live as ordinary named files under the module Files area.
     * SharedPreferences keeps only the active pointer and recent non-identifying activity.
     */
    fun templates(context: Context): List<PaperTemplate> {
        migrateLegacyTemplates(context)
        val demo = PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON)
        val custom = schemaDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".paperbridge.json") }
            ?.mapNotNull { file ->
                runCatching { PaperTemplate.parse(normalizeManifest(file.readText(Charsets.UTF_8))) }.getOrNull()
            }
            .orEmpty()
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

    fun schemaFile(context: Context, template: PaperTemplate): java.io.File? {
        if (isBundled(template)) return null
        return schemaDir(context).listFiles()?.firstOrNull { file ->
            file.isFile && file.name.endsWith(".paperbridge.json") && runCatching {
                templateKey(PaperTemplate.parse(file.readText(Charsets.UTF_8))) == templateKey(template)
            }.getOrDefault(false)
        }
    }

    fun saveAndActivateTemplate(context: Context, manifestJson: String): PaperTemplate {
        val parsed = PaperTemplate.parse(normalizeManifest(manifestJson))
        if (!isBundled(parsed)) {
            // One durable file per schema key. Rename replaces the old filename rather than
            // creating hidden duplicates merely because the human-facing schema name changed.
            schemaFile(context, parsed)?.delete()
            val file = java.io.File(schemaDir(context), schemaFilename(parsed))
            file.writeText(parsed.rawJson, Charsets.UTF_8)
        }
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(parsed)).apply()
        return parsed
    }

    fun activateTemplate(context: Context, template: PaperTemplate) {
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(template)).apply()
    }

    fun duplicateTemplate(context: Context, template: PaperTemplate): PaperTemplate {
        val root = JSONObject(template.rawJson)
        val suffix = java.util.UUID.randomUUID().toString().substring(0, 6)
        val title = "${template.title} copy"
        root.put("template_id", "${safeId(template.templateId)}_copy_$suffix")
        root.put("title", title)
        root.put("derived_from", templateKey(template))
        root.optJSONObject("registration")?.let { registration ->
            val type = registration.optString("type")
            if (type.equals("qr4", ignoreCase = true) || type.equals("apriltag8", ignoreCase = true)) {
                registration.put("schema_key", PaperQrFiducial.newSchemaKey())
            }
        }
        val duplicate = saveAndActivateTemplate(context, root.toString())
        PaperDesignSourceStore.copyBinding(context, template, duplicate)
        return duplicate
    }

    fun renameTemplate(context: Context, template: PaperTemplate, newName: String): PaperTemplate {
        require(!isBundled(template)) { "The built-in example cannot be renamed. Duplicate it first." }
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Schema name cannot be blank." }
        val oldFile = schemaFile(context, template)
        val root = JSONObject(template.rawJson).put("title", trimmed)
        val renamed = PaperTemplate.parse(normalizeManifest(root.toString()))
        oldFile?.delete()
        val file = java.io.File(schemaDir(context), schemaFilename(renamed))
        file.writeText(renamed.rawJson, Charsets.UTF_8)
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(renamed)).apply()
        return renamed
    }

    fun removeTemplate(context: Context, template: PaperTemplate) {
        if (isBundled(template)) return
        schemaFile(context, template)?.delete()
        PaperDesignSourceStore.removeBinding(context, template)
        if (prefs(context).getString(KEY_ACTIVE, null) == templateKey(template)) {
            prefs(context).edit().remove(KEY_ACTIVE).apply()
        }
    }

    fun resetExample(context: Context) {
        // The bundled example is immutable. Reset means discard any tutorial-derived
        // working state and make the staged example the current schema again.
        val demo = PaperTemplate.parse(PaperTemplateSamples.DEMO_MANIFEST_JSON)
        prefs(context).edit().putString(KEY_ACTIVE, templateKey(demo)).apply()
    }

    private fun schemaFilename(template: PaperTemplate): String =
        "${safeFilePart(template.title)}--${safeFilePart(template.templateId)}--v${safeFilePart(template.version)}.paperbridge.json"

    private fun safeFilePart(value: String): String = value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "schema" }
        .take(80)

    private fun safeId(value: String): String = value
        .replace(Regex("[^A-Za-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "paper_schema" }

    private fun migrateLegacyTemplates(context: Context) {
        if (prefs(context).getBoolean("legacy_migrated", false)) return
        val raw = legacyPrefs(context).getString(KEY_LEGACY_TEMPLATES, "[]").orEmpty()
        runCatching {
            val array = JSONArray(raw.ifBlank { "[]" })
            for (index in 0 until array.length()) {
                val manifest = array.optString(index).takeIf(String::isNotBlank) ?: continue
                val parsed = runCatching { PaperTemplate.parse(normalizeManifest(manifest)) }.getOrNull() ?: continue
                if (!isBundled(parsed) && schemaFile(context, parsed) == null) {
                    java.io.File(schemaDir(context), schemaFilename(parsed)).writeText(parsed.rawJson, Charsets.UTF_8)
                }
            }
        }
        prefs(context).edit().putBoolean("legacy_migrated", true).apply()
    }

    /**
     * Early Paper Bridge development builds normalised ROI coordinates to the physical
     * page edge. The corrected model uses the rectangle between registration-target
     * centres. Migrate only persisted schemas that do not already declare the new frame.
     */
    fun normalizeManifest(raw: String): String = runCatching {
        val root = JSONObject(raw)
        val registrationType = root.optJSONObject("registration")?.optString("type")?.lowercase().orEmpty()
        if (registrationType in setOf("apriltag8", "apriltag", "tag36h11", "april_tags")) {
            if (root.optString("coordinate_frame") != "canonical_page") root.put("coordinate_frame", "canonical_page")
            return@runCatching root.toString()
        }
        if (root.optString("coordinate_frame") == "anchor_centres") return@runCatching raw
        val anchors = root.optJSONObject("anchors")
        val targetArray = anchors?.optJSONArray("targets")
        val targets = if (targetArray != null && targetArray.length() == 4) {
            (0 until 4).map { i ->
                val p = targetArray.getJSONArray(i)
                p.getDouble(0) to p.getDouble(1)
            }
        } else {
            PaperAnchorSpec().targets.map { it.first.toDouble() to it.second.toDouble() }
        }
        val left = targets.minOf { it.first }
        val right = targets.maxOf { it.first }
        val top = targets.minOf { it.second }
        val bottom = targets.maxOf { it.second }
        val width = right - left
        val height = bottom - top
        if (width <= 0.0 || height <= 0.0) return@runCatching raw

        fun migrateRoi(array: JSONArray) {
            if (array.length() != 4) return
            fun x(value: Double) = ((value - left) / width).coerceIn(0.0, 1.0)
            fun y(value: Double) = ((value - top) / height).coerceIn(0.0, 1.0)
            val l = x(array.getDouble(0)); val t = y(array.getDouble(1))
            val r = x(array.getDouble(2)); val b = y(array.getDouble(3))
            if (r > l && b > t) { array.put(0, l); array.put(1, t); array.put(2, r); array.put(3, b) }
        }

        val fields = root.optJSONArray("fields") ?: JSONArray()
        for (i in 0 until fields.length()) {
            val field = fields.optJSONObject(i) ?: continue
            field.optJSONArray("roi")?.let(::migrateRoi)
            val options = field.optJSONArray("options") ?: continue
            for (j in 0 until options.length()) options.optJSONObject(j)?.optJSONArray("roi")?.let(::migrateRoi)
        }
        root.put("coordinate_frame", "anchor_centres")
        root.toString()
    }.getOrDefault(raw)

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

    fun clearRecent(context: Context) { prefs(context).edit().remove(KEY_RECENT).apply() }
}
