package com.example.researchos.platform.devices

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Persisted, transport-neutral device profiles used by capabilities and schedules. */
data class RegisteredDevice(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val transport: DeviceTransport,
    val address: String = "",
    val profile: String = "",
    val credentialsRef: String = "",
    val enabled: Boolean = true,
    val paused: Boolean = false,
    val lastSeenEpochMillis: Long? = null,
    val lastConnectedEpochMillis: Long? = null,
    val lastError: String = ""
)

object DeviceRegistry {
    private const val PREFS = "researchos_device_registry"
    private const val KEY = "devices"

    fun all(context: Context): List<RegisteredDevice> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.toDevice() }
    }.getOrDefault(emptyList())

    fun save(context: Context, device: RegisteredDevice) {
        val values = all(context).filterNot { it.id == device.id } + device
        persist(context, values)
    }

    fun remove(context: Context, id: String) = persist(context, all(context).filterNot { it.id == id })

    fun setPaused(context: Context, id: String, paused: Boolean) {
        persist(context, all(context).map { if (it.id == id) it.copy(paused = paused) else it })
    }

    fun setEnabled(context: Context, id: String, enabled: Boolean) {
        persist(context, all(context).map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    fun recordSeen(context: Context, id: String, error: String = "") {
        val now = System.currentTimeMillis()
        persist(context, all(context).map { if (it.id == id) it.copy(lastSeenEpochMillis = now, lastError = error) else it })
    }

    private fun persist(context: Context, devices: List<RegisteredDevice>) {
        val array = JSONArray().apply { devices.forEach { put(it.toJson()) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    private fun RegisteredDevice.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("transport", transport.name); put("address", address)
        put("profile", profile); put("credentials_ref", credentialsRef); put("enabled", enabled); put("paused", paused)
        put("last_seen_epoch_ms", lastSeenEpochMillis ?: JSONObject.NULL)
        put("last_connected_epoch_ms", lastConnectedEpochMillis ?: JSONObject.NULL)
        put("last_error", lastError)
    }

    private fun JSONObject.toDevice() = RegisteredDevice(
        id = optString("id").ifBlank { UUID.randomUUID().toString() }, name = optString("name"),
        transport = runCatching { DeviceTransport.valueOf(optString("transport")) }.getOrDefault(DeviceTransport.WIFI),
        address = optString("address"), profile = optString("profile"), credentialsRef = optString("credentials_ref"),
        enabled = optBoolean("enabled", true), paused = optBoolean("paused", false),
        lastSeenEpochMillis = optLong("last_seen_epoch_ms").takeIf { it > 0 },
        lastConnectedEpochMillis = optLong("last_connected_epoch_ms").takeIf { it > 0 }, lastError = optString("last_error")
    )
}
