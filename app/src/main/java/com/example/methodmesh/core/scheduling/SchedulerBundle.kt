package com.example.methodmesh.core.scheduling

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** Portable, deterministic schedule package used by RIL and QR/NFC transport. */
object SchedulerBundle {
    const val VERSION = "1"

    data class Decoded(val schedules: List<ResearchSchedule>, val hash: String)

    private const val COMPACT_PREFIX = "ROSCHED1:"

    fun export(context: Context, scheduleId: String? = null): String {
        val selected = scheduleId?.let { SchedulerRepository.get(context, it) }
        val schedules = SchedulerRepository.all(context)
            .filter { scheduleId.isNullOrBlank() || (selected?.chainId?.isNotBlank() == true && it.chainId == selected.chainId) || it.id == scheduleId }
            .sortedBy { it.id }
        val canonical = canonicalSchedules(schedules)
        val hash = sha256(canonical)
        return JSONObject().apply {
            put("methodmesh_schedule_bundle_version", VERSION)
            put("schedule_count", schedules.size)
            put("schedules", JSONArray().apply { canonicalSchedulesArray(schedules).forEach { put(JSONObject(it)) } })
            put("payload_sha256", hash)
        }.toString()
    }

    fun import(context: Context, payload: String): Decoded {
        val root = JSONObject(if (payload.startsWith(COMPACT_PREFIX)) decompress(payload.removePrefix(COMPACT_PREFIX)) else payload)
        require(root.optString("methodmesh_schedule_bundle_version") == VERSION) { "Unsupported schedule bundle version." }
        val schedulesJson = root.optJSONArray("schedules") ?: error("Schedule bundle has no schedules.")
        val schedules = (0 until schedulesJson.length()).map { decode(schedulesJson.getJSONObject(it)) }
        val canonical = canonicalSchedules(schedules.sortedBy { it.id })
        val expected = root.optString("payload_sha256")
        require(expected.equals(sha256(canonical), ignoreCase = true)) { "Schedule bundle hash verification failed." }
        require(schedules.map { it.id }.distinct().size == schedules.size) { "Schedule bundle contains duplicate IDs." }
        SchedulerRepository.importSchedules(context, schedules)
        return Decoded(schedules, expected.lowercase())
    }

    /** Compressed transport form intended for NFC tags and other small channels. */
    fun exportCompact(context: Context, scheduleId: String? = null): String {
        val full = export(context, scheduleId)
        return COMPACT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compress(full))
    }

    fun isCompact(payload: String): Boolean = payload.startsWith(COMPACT_PREFIX)

    private fun compress(value: String): ByteArray = ByteArrayOutputStream().use { output ->
        DeflaterOutputStream(output).use { it.write(value.toByteArray(Charsets.UTF_8)) }
        output.toByteArray()
    }

    private fun decompress(value: String): String {
        val padding = "=".repeat((4 - value.length % 4) % 4)
        val bytes = Base64.getUrlDecoder().decode(value + padding)
        return InflaterInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun canonicalSchedules(schedules: List<ResearchSchedule>): String = canonicalSchedulesArray(schedules)
        .joinToString(separator = "", prefix = "[", postfix = "]")

    private fun canonicalSchedulesArray(schedules: List<ResearchSchedule>): List<String> = schedules.sortedBy { it.id }.map { s ->
        JSONObject().apply {
            put("id", s.id); put("name", s.name); put("target", s.target.name); put("targetValue", s.targetValue); put("targetSettings", s.targetSettings)
            put("projectId", s.projectId); put("packageName", s.packageName); put("chainId", s.chainId); put("chainOrder", s.chainOrder)
            put("frequency", s.frequency.name); put("hour", s.hour); put("minute", s.minute); put("dayOfWeek", s.dayOfWeek)
            put("dayOfMonth", s.dayOfMonth); put("ordinal", s.ordinal); put("customWeekday", s.customWeekday)
            put("retryCount", s.retryCount); put("retryIntervalMinutes", s.retryIntervalMinutes); put("retryWindowMinutes", s.retryWindowMinutes)
            put("notificationTitle", s.notificationTitle); put("notificationMessage", s.notificationMessage); put("enabled", s.enabled)
            put("cronExpression", s.cronExpression)
        }.toString()
    }

    private fun decode(o: JSONObject) = ResearchSchedule(
        id = o.optString("id"), name = o.optString("name"), target = SchedulerTarget.valueOf(o.optString("target")), targetValue = o.optString("targetValue"), targetSettings = o.optString("targetSettings"),
        projectId = o.optString("projectId"), packageName = o.optString("packageName"), chainId = o.optString("chainId"), chainOrder = o.optInt("chainOrder"),
        frequency = SchedulerFrequency.valueOf(o.optString("frequency")), hour = o.optInt("hour"), minute = o.optInt("minute"),
        dayOfWeek = o.optInt("dayOfWeek", 1), dayOfMonth = o.optInt("dayOfMonth", 1), ordinal = o.optInt("ordinal", 1), customWeekday = o.optInt("customWeekday", 1),
        retryCount = o.optInt("retryCount"), retryIntervalMinutes = o.optInt("retryIntervalMinutes", 60), retryWindowMinutes = o.optInt("retryWindowMinutes", 1440),
        notificationTitle = o.optString("notificationTitle", "MethodMesh reminder"), notificationMessage = o.optString("notificationMessage", "A scheduled task is due."), enabled = o.optBoolean("enabled", true)
        ,cronExpression = o.optString("cronExpression")
    )

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
