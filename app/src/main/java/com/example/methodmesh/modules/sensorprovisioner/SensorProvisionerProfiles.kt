package com.example.methodmesh.modules.sensorprovisioner

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
            description = "UART LD2410C on TX GPIO 21 / RX GPIO 4.",
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

    fun byId(id: String): SensorProvisioningProfile =
        all.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) } ?: all.first()

    fun fromManifestOrSelected(manifestJson: String, selectedProfileId: String): SensorProvisioningProfile {
        val manifest = extractJsonObject(manifestJson)
        val manifestProfile = manifest
            ?.optString("sensor_profile")
            ?.ifBlank { manifest.optString("sensor_type") }
            .orEmpty()
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
