package com.example.methodmesh.modules.sensorprovisioner

import org.json.JSONArray
import org.json.JSONObject

data class SensorProvisioningProfile(
    val id: String,
    val label: String,
    val description: String,
    val defaultSampleIntervalMs: Int,
    val fields: List<String>
)

object SensorProvisioningProfiles {
    val all = listOf(
        SensorProvisioningProfile(
            id = "aht20",
            label = "AHT20 temperature/humidity",
            description = "I2C AHT20 on GPIO 8 SDA / GPIO 9 SCL.",
            defaultSampleIntervalMs = 5000,
            fields = listOf("temperature_c", "relative_humidity_pct")
        ),
        SensorProvisioningProfile(
            id = "ld2410c",
            label = "LD2410C mmWave presence",
            description = "UART LD2410C on TX GPIO 21 / RX GPIO 20.",
            defaultSampleIntervalMs = 1000,
            fields = listOf(
                "presence",
                "target_state",
                "moving_distance_cm",
                "moving_energy",
                "stationary_distance_cm",
                "stationary_energy",
                "detection_distance_cm"
            )
        )
    )

    private fun canonicalId(value: String): String {
        val compact = value.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        return when {
            compact.contains("ld2410") -> "ld2410c"
            compact.contains("aht20") -> "aht20"
            else -> compact
        }
    }

    fun byId(id: String): SensorProvisioningProfile {
        val canonical = canonicalId(id)
        return all.firstOrNull { it.id == canonical } ?: all.first()
    }

    fun knownIdOrBlank(id: String): String {
        val canonical = canonicalId(id)
        return all.firstOrNull { it.id == canonical }?.id.orEmpty()
    }

    fun readingProfileId(readingJson: String): String {
        val reading = extractJsonObject(readingJson)
        if (reading == null) return profileIdFromPartialJson(readingJson)
        return firstKnownProfile(
            reading.optString("installed_sensor_profile"),
            reading.optString("active_sensor_profile"),
            reading.optString("image_profile"),
            reading.optString("sensor_profile"),
            reading.optString("sensor_type"),
            reading.optString("sensor_id")
        )
    }

    fun manifestProfileId(manifestJson: String): String {
        val manifest = extractJsonObject(manifestJson)
        if (manifest == null) return profileIdFromPartialJson(manifestJson)
        firstKnownProfile(
            manifest.optString("installed_sensor_profile"),
            manifest.optString("active_sensor_profile"),
            manifest.optString("image_profile"),
            manifest.optString("sensor_profile"),
            manifest.optString("sensor_type"),
            manifest.optString("sensor_id")
        ).takeIf(String::isNotBlank)?.let { return it }
        val sensors = manifest.optJSONArray("sensors") ?: JSONArray()
        for (index in 0 until sensors.length()) {
            val sensor = sensors.optJSONObject(index) ?: continue
            firstKnownProfile(
                sensor.optString("installed_sensor_profile"),
                sensor.optString("active_sensor_profile"),
                sensor.optString("sensor_profile"),
                sensor.optString("sensor_type"),
                sensor.optString("sensor_id")
            ).takeIf(String::isNotBlank)?.let { return it }
        }
        return ""
    }

    private fun firstKnownProfile(vararg values: String): String = values
        .asSequence()
        .map(::knownIdOrBlank)
        .firstOrNull(String::isNotBlank)
        .orEmpty()

    /**
     * Identity fields are intentionally near the front of the BLE payload. Some
     * ESP32/Android combinations return only the first ATT value fragment for a
     * long characteristic, so recover the installed profile without pretending
     * that the truncated payload is otherwise valid JSON.
     */
    private fun profileIdFromPartialJson(raw: String): String {
        val keys = listOf(
            "installed_sensor_profile",
            "active_sensor_profile",
            "image_profile",
            "sensor_profile",
            "sensor_type",
            "sensor_id"
        )
        for (key in keys) {
            val match = Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
                .find(raw)
            val profileId = knownIdOrBlank(match?.groupValues?.getOrNull(1).orEmpty())
            if (profileId.isNotBlank()) return profileId
        }
        return ""
    }

    fun fromManifestOrSelected(manifestJson: String, selectedProfileId: String): SensorProvisioningProfile {
        val manifestProfile = manifestProfileId(manifestJson)
        return byId(manifestProfile.ifBlank { selectedProfileId })
    }

    fun normaliseReading(raw: String, selectedProfileId: String): String {
        val root = extractJsonObject(raw) ?: return raw.trim()
        val profile = byId(root.optString("sensor_profile").ifBlank { root.optString("sensor_type").ifBlank { selectedProfileId } })
        return JSONObject().apply {
            put("methodmesh_sensor_reading_version", root.optString("methodmesh_sensor_reading_version").ifBlank { "1" })
            put("sensor_profile", profile.id)
            put("sensor_type", root.optString("sensor_type").ifBlank { profile.id })
            put("sensor_id", root.optString("sensor_id").ifBlank { "${profile.id}_1" })
            put("status", root.optString("status").ifBlank { "ok" })
            put("sample_time_ms", root.optLong("sample_time_ms"))
            put("firmware_version", root.optString("firmware_version"))
            put("payload_sha256", root.optString("payload_sha256"))
            profile.fields.forEach { field ->
                if (root.has(field)) put(field, root.opt(field))
            }
            if (root.optString("status").equals("error", ignoreCase = true)) {
                put("error", root.optString("error"))
            }
        }.toString()
    }

    fun registryProfile(
        deviceId: String,
        deviceName: String,
        sampleIntervalMs: Int,
        sensorProfileId: String,
        manifestJson: String,
        latestReadingJson: String
    ): String {
        val profile = byId(sensorProfileId)
        return JSONObject().apply {
            put("profile_type", "methodmesh_ble_sensor_node")
            put("device_id", deviceId)
            put("device_name", deviceName)
            put("sample_interval_ms", sampleIntervalMs)
            put("sensor_profile", profile.id)
            put("sensor_label", profile.label)
            put("sensor_fields", profile.fields)
            put("manifest", extractJsonObject(manifestJson) ?: manifestJson)
            put("latest_reading", extractJsonObject(latestReadingJson) ?: latestReadingJson)
        }.toString()
    }
}

fun extractJsonObject(raw: String): JSONObject? {
    val text = raw.trim()
    if (text.isBlank()) return null
    runCatching { return JSONObject(text) }
    val start = text.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until text.length) {
        val c = text[i]
        if (escaped) {
            escaped = false
            continue
        }
        if (c == '\\' && inString) {
            escaped = true
            continue
        }
        if (c == '"') inString = !inString
        if (inString) continue
        if (c == '{') depth += 1
        if (c == '}') {
            depth -= 1
            if (depth == 0) {
                return runCatching { JSONObject(text.substring(start, i + 1)) }.getOrNull()
            }
        }
    }
    return null
}
