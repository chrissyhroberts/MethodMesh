package com.example.methodmesh.modules.time_tools.notifications

import android.content.Context
import android.os.Build
import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat
import com.example.methodmesh.modules.time_tools.timing.AlertProfile
import org.json.JSONArray
import org.json.JSONObject

/** Durable scheduling/control state only. MethodMesh result persistence remains owned by the caller. */
class ActiveTimerStore(context: Context) {
    private val storageContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        context.applicationContext.createDeviceProtectedStorageContext()
    } else context.applicationContext
    private val prefs = storageContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun put(record: ActiveTimerRecord) {
        prefs.edit()
            .putString(PREFIX + record.id, encode(record).toString())
            .putStringSet(KEY_IDS, (prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()) + record.id)
            .apply()
    }

    fun get(id: String): ActiveTimerRecord? = prefs.getString(PREFIX + id, null)?.let { raw ->
        runCatching { decode(JSONObject(raw)) }.getOrNull()
    }

    fun all(): List<ActiveTimerRecord> =
        (prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()).mapNotNull(::get).sortedBy { it.startedWallClockMs }

    fun remove(id: String) {
        val ids = (prefs.getStringSet(KEY_IDS, emptySet()) ?: emptySet()) - id
        prefs.edit().remove(PREFIX + id).putStringSet(KEY_IDS, ids).apply()
    }

    private fun encode(r: ActiveTimerRecord) = JSONObject().apply {
        put("id", r.id); put("kind", r.kind.name); put("label", r.label); put("message", r.message)
        put("startedWallClockMs", r.startedWallClockMs)
        putNullable("targetWallClockMs", r.targetWallClockMs)
        putNullable("startedElapsedRealtimeMs", r.startedElapsedRealtimeMs)
        putNullable("targetElapsedRealtimeMs", r.targetElapsedRealtimeMs)
        putNullable("pausedAtElapsedRealtimeMs", r.pausedAtElapsedRealtimeMs)
        put("accumulatedPauseMs", r.accumulatedPauseMs); putNullable("finalElapsedMs", r.finalElapsedMs)
        put("lapTotalsMs", JSONArray(r.lapTotalsMs))
        put("phaseLabels", JSONArray(r.phaseLabels)); put("phaseDurationsMs", JSONArray(r.phaseDurationsMs)); put("intervalCycles", r.intervalCycles)
        put("sound", r.alertProfile.sound); put("vibration", r.alertProfile.vibration); put("lights", r.alertProfile.lights)
        put("showOnLockScreen", r.alertProfile.showOnLockScreen); put("showFullContentOnLockScreen", r.alertProfile.showFullContentOnLockScreen)
        put("highPriority", r.alertProfile.highPriority)
        put("ongoingNotification", r.ongoingNotification); put("ongoingLockScreen", r.ongoingLockScreen)
        put("showMessageOnLockScreen", r.showMessageOnLockScreen); put("completionAlert", r.completionAlert)
        put("requireConfirmation", r.requireConfirmation); put("followUpCount", r.followUpCount)
        put("followUpIntervalMinutes", r.followUpIntervalMinutes); put("snoozeMinutes", r.snoozeMinutes)
        putNullable("zoneId", r.zoneId); put("paused", r.paused); put("completed", r.completed); put("cancelled", r.cancelled)
        putNullable("alarmTime", r.alarmTime); putNullable("alarmDate", r.alarmDate)
        putNullable("alarmRepeat", r.alarmRepeat?.name); put("alarmWeekdays", JSONArray(r.alarmWeekdays.sorted()))
        putNullable("payloadJson", r.payloadJson)
    }

    private fun decode(o: JSONObject): ActiveTimerRecord = ActiveTimerRecord(
        id = o.getString("id"),
        kind = ActiveTimerKind.valueOf(o.getString("kind")),
        label = o.optString("label", "Timer"),
        message = o.optString("message"),
        startedWallClockMs = o.optLong("startedWallClockMs"),
        targetWallClockMs = o.optLongOrNull("targetWallClockMs"),
        startedElapsedRealtimeMs = o.optLongOrNull("startedElapsedRealtimeMs"),
        targetElapsedRealtimeMs = o.optLongOrNull("targetElapsedRealtimeMs"),
        pausedAtElapsedRealtimeMs = o.optLongOrNull("pausedAtElapsedRealtimeMs"),
        accumulatedPauseMs = o.optLong("accumulatedPauseMs"),
        finalElapsedMs = o.optLongOrNull("finalElapsedMs"),
        lapTotalsMs = o.optLongList("lapTotalsMs"),
        phaseLabels = o.optStringList("phaseLabels"),
        phaseDurationsMs = o.optLongList("phaseDurationsMs"),
        intervalCycles = o.optInt("intervalCycles", 1).coerceAtLeast(1),
        alertProfile = AlertProfile(
            sound = o.optBoolean("sound", true), vibration = o.optBoolean("vibration", true), lights = o.optBoolean("lights", true),
            showOnLockScreen = o.optBoolean("showOnLockScreen", true), showFullContentOnLockScreen = o.optBoolean("showFullContentOnLockScreen", false),
            highPriority = o.optBoolean("highPriority", true)
        ),
        ongoingNotification = o.optBoolean("ongoingNotification", true),
        ongoingLockScreen = o.optBoolean("ongoingLockScreen", true),
        showMessageOnLockScreen = o.optBoolean("showMessageOnLockScreen", false),
        completionAlert = o.optBoolean("completionAlert", true),
        requireConfirmation = o.optBoolean("requireConfirmation", false),
        followUpCount = o.optInt("followUpCount", 0).coerceAtLeast(0),
        followUpIntervalMinutes = o.optInt("followUpIntervalMinutes", 10).coerceAtLeast(1),
        snoozeMinutes = o.optInt("snoozeMinutes", 10).coerceAtLeast(0),
        zoneId = o.optStringOrNull("zoneId"), paused = o.optBoolean("paused"), completed = o.optBoolean("completed"), cancelled = o.optBoolean("cancelled"),
        alarmTime = o.optStringOrNull("alarmTime"), alarmDate = o.optStringOrNull("alarmDate"),
        alarmRepeat = o.optStringOrNull("alarmRepeat")?.let { runCatching { AlarmRepeat.valueOf(it) }.getOrNull() },
        alarmWeekdays = o.optIntSet("alarmWeekdays"), payloadJson = o.optStringOrNull("payloadJson")
    )

    private fun JSONObject.putNullable(key: String, value: Any?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
    private fun JSONObject.optLongOrNull(key: String): Long? = if (has(key) && !isNull(key)) optLong(key) else null
    private fun JSONObject.optStringOrNull(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
    private fun JSONObject.optLongList(key: String): List<Long> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optLong(it) } } ?: emptyList()
    private fun JSONObject.optStringList(key: String): List<String> = optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) } } ?: emptyList()
    private fun JSONObject.optIntSet(key: String): Set<Int> = optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 1..7 }.toSet() } ?: emptySet()

    companion object { private const val PREFS = "methodmesh_time_tools_active_timers_v2"; private const val KEY_IDS = "ids"; private const val PREFIX = "timer." }
}
