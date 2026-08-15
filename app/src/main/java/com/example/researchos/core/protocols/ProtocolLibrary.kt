package com.example.researchos.core.protocols

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

data class CapabilityPreset(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val methodId: String,
    val settingsJson: String = "{}",
    val description: String = "",
    val createdAtIso: String = Instant.now().toString(),
    val updatedAtIso: String = Instant.now().toString()
)

data class ProtocolStep(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val presetId: String,
    val order: Int
)

data class ProtocolDefinition(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val steps: List<ProtocolStep> = emptyList(),
    val createdAtIso: String = Instant.now().toString(),
    val updatedAtIso: String = Instant.now().toString()
)

/** Local, transportable library of reusable capability configurations and chains. */
object ProtocolLibraryRepository {
    private const val PREFS = "researchos_protocol_library"
    private const val PRESETS = "capability_presets_json"
    private const val PROTOCOLS = "protocol_definitions_json"
    const val BUNDLE_VERSION = "1"

    data class Imported(val presetCount: Int, val protocolCount: Int, val hash: String)

    fun presets(context: Context): List<CapabilityPreset> =
        readArray(context, PRESETS).map { decodePreset(it) }.sortedBy { it.name.lowercase() }

    fun protocols(context: Context): List<ProtocolDefinition> =
        readArray(context, PROTOCOLS).map { decodeProtocol(it) }.sortedBy { it.name.lowercase() }

    fun preset(context: Context, id: String): CapabilityPreset? =
        presets(context).firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }

    fun protocol(context: Context, id: String): ProtocolDefinition? =
        protocols(context).firstOrNull { it.id == id || it.name.equals(id, ignoreCase = true) }

    fun savePreset(context: Context, preset: CapabilityPreset) {
        val now = Instant.now().toString()
        val replacement = preset.copy(updatedAtIso = now)
        val merged = presets(context).filterNot { it.id == replacement.id } + replacement
        writeArray(context, PRESETS, JSONArray().apply { merged.sortedBy { it.id }.forEach { put(encodePreset(it)) } })
    }

    fun saveProtocol(context: Context, protocol: ProtocolDefinition) {
        val now = Instant.now().toString()
        val replacement = protocol.copy(updatedAtIso = now)
        val merged = protocols(context).filterNot { it.id == replacement.id } + replacement
        writeArray(context, PROTOCOLS, JSONArray().apply { merged.sortedBy { it.id }.forEach { put(encodeProtocol(it)) } })
    }

    fun removePreset(context: Context, id: String) {
        writeArray(context, PRESETS, JSONArray().apply { presets(context).filterNot { it.id == id }.forEach { put(encodePreset(it)) } })
    }

    fun removeProtocol(context: Context, id: String) {
        writeArray(context, PROTOCOLS, JSONArray().apply { protocols(context).filterNot { it.id == id }.forEach { put(encodeProtocol(it)) } })
    }

    fun export(context: Context): String {
        val presets = presets(context).sortedBy { it.id }
        val protocols = protocols(context).sortedBy { it.id }
        val canonical = canonical(presets, protocols)
        return JSONObject().apply {
            put("researchos_protocol_library_version", BUNDLE_VERSION)
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
        require(root.optString("researchos_protocol_library_version") == BUNDLE_VERSION) { "Unsupported protocol library version." }
        val importedPresets = root.optJSONArray("presets").orEmptyObjects().map { decodePreset(it) }
        val importedProtocols = root.optJSONArray("protocols").orEmptyObjects().map { decodeProtocol(it) }
        val expected = root.optString("payload_sha256")
        val canonical = canonical(importedPresets.sortedBy { it.id }, importedProtocols.sortedBy { it.id })
        require(expected.equals(sha256(canonical), ignoreCase = true)) { "Protocol library bundle hash verification failed." }

        val mergedPresets = (presets(context).associateBy { it.id } + importedPresets.associateBy { it.id }).values.sortedBy { it.id }
        val mergedProtocols = (protocols(context).associateBy { it.id } + importedProtocols.associateBy { it.id }).values.sortedBy { it.id }
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

    private fun encodePreset(preset: CapabilityPreset) = JSONObject().apply {
        put("id", preset.id)
        put("name", preset.name)
        put("method_id", preset.methodId)
        put("settings_json", preset.settingsJson.ifBlank { "{}" })
        put("description", preset.description)
        put("created_at_iso", preset.createdAtIso)
        put("updated_at_iso", preset.updatedAtIso)
    }

    private fun decodePreset(o: JSONObject) = CapabilityPreset(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        methodId = o.optString("method_id"),
        settingsJson = o.optString("settings_json", "{}"),
        description = o.optString("description"),
        createdAtIso = o.optString("created_at_iso", Instant.now().toString()),
        updatedAtIso = o.optString("updated_at_iso", Instant.now().toString())
    )

    private fun encodeProtocol(protocol: ProtocolDefinition) = JSONObject().apply {
        put("id", protocol.id)
        put("name", protocol.name)
        put("description", protocol.description)
        put("created_at_iso", protocol.createdAtIso)
        put("updated_at_iso", protocol.updatedAtIso)
        put("steps", JSONArray().apply { protocol.steps.sortedBy { it.order }.forEach { put(encodeStep(it)) } })
    }

    private fun decodeProtocol(o: JSONObject) = ProtocolDefinition(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        description = o.optString("description"),
        steps = o.optJSONArray("steps").orEmptyObjects().map { decodeStep(it) }.sortedBy { it.order },
        createdAtIso = o.optString("created_at_iso", Instant.now().toString()),
        updatedAtIso = o.optString("updated_at_iso", Instant.now().toString())
    )

    private fun encodeStep(step: ProtocolStep) = JSONObject().apply {
        put("id", step.id)
        put("name", step.name)
        put("preset_id", step.presetId)
        put("order", step.order)
    }

    private fun decodeStep(o: JSONObject) = ProtocolStep(
        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
        name = o.optString("name"),
        presetId = o.optString("preset_id"),
        order = o.optInt("order")
    )

    private fun JSONArray?.orEmptyObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
