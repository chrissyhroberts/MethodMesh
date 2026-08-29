package com.example.methodmesh.core.protocols

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

data class CapabilityPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val methodId: String,
    val settingsJson: String = "{}",
    val description: String = "",
    val createdAtIso: String = Instant.now().toString(),
    val updatedAtIso: String = Instant.now().toString(),
    val versionIso: String = Instant.now().toString()
)

data class ProtocolStep(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val presetId: String,
    val order: Int,
    val outputMode: String = ProtocolOutputMode.SAVE
)

object ProtocolOutputMode {
    const val SAVE = "SAVE"
    const val NONE = "NONE"
    const val SHARE = "SHARE"

    fun normalize(value: String): String = when (value.trim().uppercase(Locale.ROOT)) {
        NONE -> NONE
        SHARE -> SHARE
        else -> SAVE
    }
}

data class ProtocolDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<ProtocolStep> = emptyList(),
    val createdAtIso: String = Instant.now().toString(),
    val updatedAtIso: String = Instant.now().toString(),
    val versionIso: String = Instant.now().toString()
)

/** Local, transportable library of reusable capability configurations and chains. */
object ProtocolLibraryRepository {
    private const val PREFS = "methodmesh_protocol_library"
    private const val PRESETS = "capability_presets_json"
    private const val PROTOCOLS = "protocol_definitions_json"
    private const val ARCHIVED_PROTOCOLS = "archived_protocol_definitions_json"
    const val BUNDLE_VERSION = "1"

    data class Imported(val presetCount: Int, val protocolCount: Int, val hash: String)

    fun presets(context: Context): List<CapabilityPreset> =
        readArray(context, PRESETS).map { decodePreset(it) }.sortedBy { it.name.lowercase() }

    fun protocols(context: Context): List<ProtocolDefinition> =
        readArray(context, PROTOCOLS).map { decodeProtocol(it) }.sortedBy { it.name.lowercase() }

    fun archivedProtocols(context: Context): List<ProtocolDefinition> =
        readArray(context, ARCHIVED_PROTOCOLS).map { decodeProtocol(it) }.sortedByDescending { it.versionIso }

    fun preset(context: Context, id: String): CapabilityPreset? =
        presets(context).firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }

    fun protocol(context: Context, id: String): ProtocolDefinition? =
        protocols(context).firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }

    fun savePreset(context: Context, preset: CapabilityPreset): CapabilityPreset {
        val existing = presets(context)
        val now = Instant.now()
        val nowIso = now.toString()
        val prior = existing.firstOrNull { it.id == preset.id }
        val baseName = preset.name.trim().ifBlank { preset.methodId.ifBlank { "capability_preset" } }
        val replacement = preset.copy(
            name = uniqueName(baseName, existing.filterNot { it.id == preset.id }, now),
            createdAtIso = prior?.createdAtIso ?: preset.createdAtIso.ifBlank { nowIso },
            updatedAtIso = nowIso,
            versionIso = nowIso
        )
        val merged = existing.filterNot { it.id == replacement.id } + replacement
        writeArray(context, PRESETS, JSONArray().apply { merged.sortedBy { it.id }.forEach { put(encodePreset(it)) } })
        return replacement
    }

    fun saveProtocol(context: Context, protocol: ProtocolDefinition): ProtocolDefinition {
        val existing = protocols(context)
        val now = Instant.now()
        val nowIso = now.toString()
        val prior = existing.firstOrNull { it.id == protocol.id }
        val baseName = protocol.name.trim().ifBlank { "protocol" }
        val replacement = protocol.copy(
            name = uniqueName(baseName, existing.filterNot { it.id == protocol.id }, now),
            createdAtIso = prior?.createdAtIso ?: protocol.createdAtIso.ifBlank { nowIso },
            updatedAtIso = nowIso,
            versionIso = nowIso
        )
        if (prior != null) archiveProtocolVersion(context, prior)
        val merged = existing.filterNot { it.id == replacement.id } + replacement
        writeArray(context, PROTOCOLS, JSONArray().apply { merged.sortedBy { it.id }.forEach { put(encodeProtocol(it)) } })
        return replacement
    }

    fun removePreset(context: Context, id: String) {
        writeArray(context, PRESETS, JSONArray().apply { presets(context).filterNot { it.id == id }.forEach { put(encodePreset(it)) } })
    }

    fun removeProtocol(context: Context, id: String) {
        writeArray(context, PROTOCOLS, JSONArray().apply { protocols(context).filterNot { it.id == id }.forEach { put(encodeProtocol(it)) } })
    }

    fun archiveProtocol(context: Context, id: String): Boolean {
        val active = protocols(context)
        val target = active.firstOrNull { it.id == id } ?: return false
        archiveProtocolVersion(context, target)
        writeArray(context, PROTOCOLS, JSONArray().apply { active.filterNot { it.id == id }.forEach { put(encodeProtocol(it)) } })
        return true
    }

    fun unarchiveProtocol(context: Context, id: String): Boolean {
        val archived = archivedProtocols(context)
        val target = archived.firstOrNull { it.id == id } ?: return false
        val restored = saveProtocol(context, target.copy(id = UUID.randomUUID().toString()))
        writeArray(context, ARCHIVED_PROTOCOLS, JSONArray().apply { archived.filterNot { it.id == id }.forEach { put(encodeProtocol(it)) } })
        return restored.id.isNotBlank()
    }

    fun export(context: Context): String {
        val presets = presets(context).sortedBy { it.id }
        val protocols = protocols(context).sortedBy { it.id }
        val canonical = canonical(presets, protocols)
        return JSONObject().apply {
            put("methodmesh_protocol_library_version", BUNDLE_VERSION)
            put("exported_at", Instant.now().toString())
            put("preset_count", presets.size)
            put("protocol_count", protocols.size)
            put("presets", JSONArray().apply { presets.forEach { put(encodePreset(it)) } })
            put("protocols", JSONArray().apply { protocols.forEach { put(encodeProtocol(it)) } })
            put("payload_sha256", sha256(canonical))
        }.toString()
    }

    fun import(context: Context, payload: String): Imported {
        val root = JSONObject(payload)
        require(root.optString("methodmesh_protocol_library_version") == BUNDLE_VERSION) { "Unsupported protocol library version." }
        val importedPresets = root.optJSONArray("presets").orEmptyObjects().map { decodePreset(it) }
        val importedProtocols = root.optJSONArray("protocols").orEmptyObjects().map { decodeProtocol(it) }
        val expected = root.optString("payload_sha256")
        val canonical = canonical(importedPresets.sortedBy { it.id }, importedProtocols.sortedBy { it.id })
        require(expected.equals(sha256(canonical), ignoreCase = true)) { "Protocol library bundle hash verification failed." }

        val mergedPresets = uniquePresetNames(
            (presets(context).associateBy { it.id } + importedPresets.associateBy { it.id }).values.sortedBy { it.id }
        )
        val mergedProtocols = uniqueProtocolNames(
            (protocols(context).associateBy { it.id } + importedProtocols.associateBy { it.id }).values.sortedBy { it.id }
        )
        writeArray(context, PRESETS, JSONArray().apply { mergedPresets.forEach { put(encodePreset(it)) } })
        writeArray(context, PROTOCOLS, JSONArray().apply { mergedProtocols.forEach { put(encodeProtocol(it)) } })
        return Imported(importedPresets.size, importedProtocols.size, expected.lowercase())
    }

    fun settingsMap(json: String): Map<String, String> = runCatching {
        val root = JSONObject(json.ifBlank { "{}" })
        buildMap {
            root.keys().forEach { key -> put(key, root.optString(key)) }
        }
    }.getOrDefault(emptyMap())

    private fun canonical(presets: List<CapabilityPreset>, protocols: List<ProtocolDefinition>): String =
        JSONObject().apply {
            put("presets", JSONArray().apply { presets.sortedBy { it.id }.forEach { put(encodePreset(it)) } })
            put("protocols", JSONArray().apply { protocols.sortedBy { it.id }.forEach { put(encodeProtocol(it)) } })
        }.toString()

    private fun readArray(context: Context, key: String): List<JSONObject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]").orEmpty()
        return JSONArray(raw).orEmptyObjects()
    }

    private fun writeArray(context: Context, key: String, array: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }

    private fun archiveProtocolVersion(context: Context, protocol: ProtocolDefinition) {
        val archived = archivedProtocols(context).filterNot { it.id == protocol.id && it.versionIso == protocol.versionIso } + protocol
        writeArray(context, ARCHIVED_PROTOCOLS, JSONArray().apply { archived.sortedBy { it.versionIso }.forEach { put(encodeProtocol(it)) } })
    }

    private fun encodePreset(preset: CapabilityPreset) = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("method_id", preset.methodId)
        put("settings_json", preset.settingsJson.ifBlank { "{}" })
        put("description", preset.description)
        put("created_at_iso", preset.createdAtIso)
        put("updated_at_iso", preset.updatedAtIso)
        put("version_iso", preset.versionIso)
        put("version", versionStamp(preset.versionIso))
    }

    private fun decodePreset(o: JSONObject) = CapabilityPreset(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        methodId = o.optString("method_id"),
        settingsJson = o.optString("settings_json", "{}"),
        description = o.optString("description"),
        createdAtIso = o.optString("created_at_iso", Instant.now().toString()),
        updatedAtIso = o.optString("updated_at_iso", Instant.now().toString()),
        versionIso = o.optString("version_iso").ifBlank { o.optString("updated_at_iso", Instant.now().toString()) }
    )

    private fun encodeProtocol(protocol: ProtocolDefinition) = JSONObject().apply {
        put("id", protocol.id)
        put("name", protocol.name)
        put("description", protocol.description)
        put("created_at_iso", protocol.createdAtIso)
        put("updated_at_iso", protocol.updatedAtIso)
        put("version_iso", protocol.versionIso)
        put("version", versionStamp(protocol.versionIso))
        put("steps", JSONArray().apply { protocol.steps.sortedBy { it.order }.forEach { put(encodeStep(it)) } })
    }

    private fun decodeProtocol(o: JSONObject) = ProtocolDefinition(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        description = o.optString("description"),
        steps = o.optJSONArray("steps").orEmptyObjects().map { decodeStep(it) }.sortedBy { it.order },
        createdAtIso = o.optString("created_at_iso", Instant.now().toString()),
        updatedAtIso = o.optString("updated_at_iso", Instant.now().toString()),
        versionIso = o.optString("version_iso").ifBlank { o.optString("updated_at_iso", Instant.now().toString()) }
    )

    private fun encodeStep(step: ProtocolStep) = JSONObject().apply {
        put("id", step.id)
        put("name", step.name)
        put("preset_id", step.presetId)
        put("order", step.order)
        put("output_mode", ProtocolOutputMode.normalize(step.outputMode))
    }

    private fun decodeStep(o: JSONObject) = ProtocolStep(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        presetId = o.optString("preset_id"),
        order = o.optInt("order"),
        outputMode = ProtocolOutputMode.normalize(o.optString("output_mode", ProtocolOutputMode.SAVE))
    )

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun versionLabel(iso: String): String = "v${versionStamp(iso)}"

    private fun uniquePresetNames(items: List<CapabilityPreset>): List<CapabilityPreset> =
        enforceUniqueNames(items) { item, name -> item.copy(name = name) }

    private fun uniqueProtocolNames(items: List<ProtocolDefinition>): List<ProtocolDefinition> =
        enforceUniqueNames(items) { item, name -> item.copy(name = name) }

    private fun <T> enforceUniqueNames(items: List<T>, rename: (T, String) -> T): List<T> {
        val seen = mutableSetOf<String>()
        return items.map { item ->
            val original = when (item) {
                is CapabilityPreset -> item.name
                is ProtocolDefinition -> item.name
                else -> ""
            }.ifBlank { "unnamed" }
            var candidate = original
            var counter = 2
            while (!seen.add(candidate.normalizedNameKey())) {
                candidate = "${original}__v${versionStamp(Instant.now())}${if (counter > 2) "_$counter" else ""}"
                counter += 1
            }
            if (candidate == original) item else rename(item, candidate)
        }
    }

    private fun uniqueName(baseName: String, existing: List<Any>, now: Instant): String {
        val existingNames = existing.mapNotNull {
            when (it) {
                is CapabilityPreset -> it.name
                is ProtocolDefinition -> it.name
                else -> null
            }
        }.map { it.normalizedNameKey() }.toSet()
        if (baseName.normalizedNameKey() !in existingNames) return baseName
        val suffix = "__v${versionStamp(now)}"
        var candidate = "$baseName$suffix"
        var counter = 2
        while (candidate.normalizedNameKey() in existingNames) {
            candidate = "$baseName${suffix}_$counter"
            counter += 1
        }
        return candidate
    }

    private fun String.normalizedNameKey(): String = trim().lowercase(Locale.ROOT)

    private fun versionStamp(iso: String): String =
        runCatching { versionStamp(Instant.parse(iso)) }.getOrElse { iso.replace(Regex("[^0-9A-Za-z]+"), "_").trim('_') }

    private fun versionStamp(instant: Instant): String =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(instant)
}
