package com.example.methodmesh.core.scheduling

import android.content.Context
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** JSON-backed repository for schedule bundles. Each bundle also appears in Files as a JSON artifact. */
object CronScheduleBundleStore {
    private const val PREFS = "methodmesh_cron_schedule_bundles"
    private const val KEY = "bundles"

    fun all(context: Context): List<CronScheduleBundle> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until array.length()).mapNotNull { decode(array.optJSONObject(it) ?: return@mapNotNull null) }
    }.getOrDefault(emptyList())

    fun get(context: Context, id: String): CronScheduleBundle? = all(context).firstOrNull { it.id == id }

    @Synchronized fun save(context: Context, bundle: CronScheduleBundle): CronScheduleBundle {
        val json = encode(bundle).toString(2)
        val service = AndroidArtifacts.service(context)
        val artifactId = bundle.jsonArtifactId?.takeIf { runCatching { service.resolve(ArtifactRef(it)) }.isSuccess }
        val persistedArtifactId = if (artifactId != null) {
            val ref = ArtifactRef(artifactId)
            service.replacePersistent(ref, json.toByteArray(Charsets.UTF_8))
            artifactId
        } else {
            service.createPersistent("schedules/${bundle.id}.json", "application/json", ByteArrayInputStream(json.toByteArray(Charsets.UTF_8))).id
        }
        val saved = bundle.copy(jsonArtifactId = persistedArtifactId)
        if (artifactId == null) {
            service.replacePersistent(ArtifactRef(persistedArtifactId), encode(saved).toString(2).toByteArray(Charsets.UTF_8))
        }
        val values = all(context).filterNot { it.id == saved.id } + saved
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray().apply { values.forEach { put(encode(it)) } }.toString())
            .apply()
        return saved
    }

    fun remove(context: Context, id: String) {
        val existing = get(context, id)
        existing?.jsonArtifactId?.let { ref -> runCatching { AndroidArtifacts.service(context).delete(ArtifactRef(ref)) } }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, JSONArray().apply { all(context).filterNot { it.id == id }.forEach { put(encode(it)) } }.toString())
            .apply()
    }

    fun json(context: Context, id: String): String? = get(context, id)?.let { encode(it).toString(2) }

    private fun encode(bundle: CronScheduleBundle) = JSONObject().apply {
        put("schema", "methodmesh.cron.schedule.v1")
        put("id", bundle.id); put("name", bundle.name); put("timezone", bundle.timezone.id); put("enabled", bundle.enabled)
        put("version", bundle.version); put("json_artifact_id", bundle.jsonArtifactId)
        put("created_at", bundle.createdAt.toString()); put("updated_at", bundle.updatedAt.toString())
        put("trigger", encodeTrigger(bundle.trigger))
        put("stop_rule", encodeStopRule(bundle.stopRule))
        put("tasks", JSONArray().apply { bundle.tasks.forEach { put(encodeTask(it)) } })
    }

    private fun encodeTrigger(trigger: CronTrigger) = JSONObject().apply {
        when (trigger) {
            is CronTrigger.Constitutive -> { put("type", "constitutive"); put("started_at", trigger.startedAt.toString()) }
            CronTrigger.Manual -> put("type", "manual")
            is CronTrigger.Absolute -> { put("type", "absolute"); put("start_at", trigger.startAt.toString()) }
            is CronTrigger.Event -> { put("type", "event"); put("event_key", trigger.eventKey) }
            is CronTrigger.Preset -> { put("type", "preset"); put("preset_id", trigger.presetId) }
        }
    }

    private fun encodeTask(task: CronTask) = JSONObject().apply {
        put("id", task.id); put("name", task.name); put("timing", task.timing.name); put("recurrence", task.recurrence.name); put("cron", task.cronExpression)
        put("relative_offset_seconds", task.relativeOffset.seconds); put("target", task.target.name); put("target_id", task.targetId)
        put("notification_title", task.notificationTitle); put("notification_message", task.notificationMessage)
        put("retries", task.retries); put("retry_interval_seconds", task.retryInterval.seconds); put("max_occurrences", task.maxOccurrences)
    }

    private fun encodeStopRule(rule: ScheduleStopRule) = JSONObject().apply {
        when (rule) {
            ScheduleStopRule.Never -> put("type", "never")
            is ScheduleStopRule.Absolute -> { put("type", "absolute"); put("stop_at", rule.stopAt.toString()) }
            is ScheduleStopRule.Relative -> { put("type", "relative"); put("delay_seconds", rule.delay.seconds) }
        }
    }

    private fun decode(value: JSONObject): CronScheduleBundle? = runCatching {
        val timezone = ZoneId.of(value.optString("timezone", ZoneId.systemDefault().id))
        CronScheduleBundle(
            id = value.getString("id"), name = value.getString("name"), timezone = timezone,
            trigger = decodeTrigger(value.getJSONObject("trigger")),
            stopRule = decodeStopRule(value.optJSONObject("stop_rule")),
            tasks = (0 until value.getJSONArray("tasks").length()).map { decodeTask(value.getJSONArray("tasks").getJSONObject(it)) },
            enabled = value.optBoolean("enabled", true), jsonArtifactId = value.optString("json_artifact_id").takeIf { it.isNotBlank() && it != "null" },
            version = value.optInt("version", 1), createdAt = ZonedDateTime.parse(value.getString("created_at")), updatedAt = ZonedDateTime.parse(value.getString("updated_at"))
        )
    }.getOrNull()

    private fun decodeTrigger(value: JSONObject): CronTrigger = when (value.getString("type")) {
        "constitutive" -> CronTrigger.Constitutive(ZonedDateTime.parse(value.getString("started_at")))
        "manual" -> CronTrigger.Manual
        "absolute" -> CronTrigger.Absolute(ZonedDateTime.parse(value.getString("start_at")))
        "event" -> CronTrigger.Event(value.getString("event_key"))
        "preset" -> CronTrigger.Preset(value.getString("preset_id"))
        else -> error("Unknown schedule trigger")
    }

    private fun decodeStopRule(value: JSONObject?): ScheduleStopRule = when (value?.optString("type", "never")) {
        "absolute" -> ScheduleStopRule.Absolute(ZonedDateTime.parse(value.getString("stop_at")))
        "relative" -> ScheduleStopRule.Relative(Duration.ofSeconds(value.optLong("delay_seconds", 0).coerceAtLeast(1)))
        else -> ScheduleStopRule.Never
    }

    private fun decodeTask(value: JSONObject) = CronTask(
        id = value.getString("id"), name = value.getString("name"), timing = ScheduleTimingMode.valueOf(value.optString("timing", ScheduleTimingMode.ABSOLUTE.name)), recurrence = CronTaskRecurrence.valueOf(value.optString("recurrence", CronTaskRecurrence.CRON.name)), cronExpression = value.optString("cron"),
        relativeOffset = Duration.ofSeconds(value.optLong("relative_offset_seconds", 0)), target = CronTaskTarget.valueOf(value.getString("target")), targetId = value.optString("target_id"),
        notificationTitle = value.optString("notification_title", "MethodMesh reminder"), notificationMessage = value.optString("notification_message", "A scheduled task is due."),
        retries = value.optInt("retries", 0), retryInterval = Duration.ofSeconds(value.optLong("retry_interval_seconds", 3600)), maxOccurrences = value.optInt("max_occurrences").takeIf { it > 0 }
    )
}
