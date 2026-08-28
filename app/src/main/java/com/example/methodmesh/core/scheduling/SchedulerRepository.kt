package com.example.methodmesh.core.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZonedDateTime

object SchedulerRepository {
    private const val PREFS = "methodmesh_scheduler"
    private const val KEY = "schedules"
    private const val EVENTS = "events"

    data class SchedulerEvent(val scheduleId: String, val event: String, val timeIso: String)

    fun all(context: Context): List<ResearchSchedule> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { decode(array.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun importSchedules(context: Context, schedules: List<ResearchSchedule>): Int {
        schedules.forEach { save(context, it) }
        return schedules.size
    }

    fun rescheduleAll(context: Context) {
        // Re-arm only chain owners, and cancel stale alarms left by earlier
        // scheduler versions that registered every chain member separately.
        all(context).forEach { schedule ->
            if (schedule.enabled && isAlarmOwner(schedule)) SchedulerAlarm.schedule(context, schedule)
            else cancel(context, schedule.id)
        }
    }

    private fun isAlarmOwner(schedule: ResearchSchedule): Boolean = schedule.chainId.isBlank() || schedule.chainOrder <= 0

    fun get(context: Context, id: String): ResearchSchedule? = all(context).firstOrNull { it.id == id }

    fun nextInChain(context: Context, current: ResearchSchedule): ResearchSchedule? =
        all(context).filter { it.enabled && current.chainId.isNotBlank() && it.chainId == current.chainId && it.chainOrder > current.chainOrder }
            .minByOrNull { it.chainOrder }

    fun save(context: Context, schedule: ResearchSchedule) {
        val values = all(context).filterNot { it.id == schedule.id } + schedule
        val array = JSONArray().apply { values.forEach { put(encode(it)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
        if (schedule.enabled && isAlarmOwner(schedule)) SchedulerAlarm.schedule(context, schedule)
        else cancel(context, schedule.id)
    }

    fun setChainEnabled(context: Context, schedule: ResearchSchedule, enabled: Boolean) {
        val members = if (schedule.chainId.isBlank()) listOf(schedule) else all(context).filter { it.chainId == schedule.chainId }
        members.forEach { save(context, it.copy(enabled = enabled)) }
    }

    fun remove(context: Context, id: String) {
        cancel(context, id)
        val array = JSONArray().apply { all(context).filterNot { it.id == id }.forEach { put(encode(it)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun removeChain(context: Context, schedule: ResearchSchedule) {
        val members = if (schedule.chainId.isBlank()) listOf(schedule) else all(context).filter { it.chainId == schedule.chainId }
        members.forEach { cancel(context, it.id) }
        val ids = members.map { it.id }.toSet()
        val array = JSONArray().apply { all(context).filterNot { it.id in ids }.forEach { put(encode(it)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply()
    }

    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        listOf("primary", "retry").forEach { kind ->
            val intent = Intent(context, SchedulerAlarmReceiver::class.java).setAction(SchedulerAlarmReceiver.ACTION).putExtra("schedule_id", id).putExtra("kind", kind)
            alarm.cancel(PendingIntent.getBroadcast(context, requestCode(id, kind), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
    }

    fun recordEvent(context: Context, scheduleId: String, event: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString(EVENTS, "[]")) }.getOrDefault(JSONArray())
        array.put(JSONObject().put("scheduleId", scheduleId).put("event", event).put("timeIso", java.time.Instant.now().toString()))
        while (array.length() > 100) array.remove(0)
        prefs.edit().putString(EVENTS, array.toString()).apply()
    }

    fun events(context: Context, scheduleId: String): List<SchedulerEvent> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EVENTS, "[]"))
        (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.let { SchedulerEvent(it.optString("scheduleId"), it.optString("event"), it.optString("timeIso")) } }
            .filter { it.scheduleId == scheduleId }.reversed()
    }.getOrDefault(emptyList())

    fun markCompleted(context: Context, schedule: ResearchSchedule) {
        cancel(context, schedule.id)
        recordEvent(context, schedule.id, "completed")
        // Only the first member owns the recurring alarm. A later chain step
        // must not create a second recurring notification for itself.
        if (isAlarmOwner(schedule)) SchedulerAlarm.schedule(context, schedule)
    }

    internal fun requestCode(id: String, kind: String): Int = ("$id:$kind").hashCode()

    private fun encode(s: ResearchSchedule) = JSONObject().apply {
        put("id", s.id); put("name", s.name); put("target", s.target.name); put("targetValue", s.targetValue); put("targetSettings", s.targetSettings)
        put("projectId", s.projectId); put("packageName", s.packageName); put("frequency", s.frequency.name)
        put("chainId", s.chainId); put("chainOrder", s.chainOrder)
        put("hour", s.hour); put("minute", s.minute); put("dayOfWeek", s.dayOfWeek); put("dayOfMonth", s.dayOfMonth)
        put("ordinal", s.ordinal); put("customWeekday", s.customWeekday); put("retryCount", s.retryCount)
        put("retryIntervalMinutes", s.retryIntervalMinutes); put("retryWindowMinutes", s.retryWindowMinutes); put("enabled", s.enabled)
        put("notificationTitle", s.notificationTitle); put("notificationMessage", s.notificationMessage)
        put("cronExpression", s.cronExpression)
    }

    private fun decode(o: JSONObject) = runCatching {
        ResearchSchedule(
            id = o.optString("id"), name = o.optString("name"), target = SchedulerTarget.valueOf(o.optString("target")), targetValue = o.optString("targetValue"), targetSettings = o.optString("targetSettings"),
            projectId = o.optString("projectId"), packageName = o.optString("packageName"), chainId = o.optString("chainId"), chainOrder = o.optInt("chainOrder"), frequency = SchedulerFrequency.valueOf(o.optString("frequency")),
            hour = o.optInt("hour"), minute = o.optInt("minute"), dayOfWeek = o.optInt("dayOfWeek", 1), dayOfMonth = o.optInt("dayOfMonth", 1),
            ordinal = o.optInt("ordinal", 1), customWeekday = o.optInt("customWeekday", 1), retryCount = o.optInt("retryCount"),
            retryIntervalMinutes = o.optInt("retryIntervalMinutes", 60), retryWindowMinutes = o.optInt("retryWindowMinutes", 1440),
            notificationTitle = o.optString("notificationTitle", "MethodMesh reminder"), notificationMessage = o.optString("notificationMessage", "A scheduled task is due."), enabled = o.optBoolean("enabled", true)
            ,cronExpression = o.optString("cronExpression")
        )
    }.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() && it.targetValue.isNotBlank() }
}

object SchedulerAlarm {
    fun schedule(context: Context, schedule: ResearchSchedule) {
        val whenMillis = schedule.nextOccurrence().toInstant().toEpochMilli()
        val intent = Intent(context, SchedulerAlarmReceiver::class.java).setAction(SchedulerAlarmReceiver.ACTION)
            .putExtra("schedule_id", schedule.id).putExtra("kind", "primary")
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(context, SchedulerRepository.requestCode(schedule.id, "primary"), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        // Allow the receiver to run while the app process is stopped or the
        // device is idle. Notification priority/channel policy still controls
        // whether the user is disturbed by DND settings.
        runCatching {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }.onFailure {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pending)
        }
    }
}
