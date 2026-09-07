package com.example.methodmesh.modules.astronomy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Module-owned persistence for astronomy-only state and deliberately cached data. */
class AstronomyRepository(context: Context) {
    data class GoodNightCalibration(
        val centroidRmsPx: Double,
        val fwhmPx: Double,
        val backgroundLuma: Double,
        val contrast: Double,
        val scintillationCv: Double,
        val savedAtIso: String,
        val note: String
    )

    data class ActiveSession(
        val id: String,
        val target: String,
        val equipment: String,
        val notes: String,
        val startIso: String,
        val runningSinceEpochMs: Long?,
        val accumulatedMs: Long,
        val state: String
    ) {
        fun activeDurationMs(nowMs: Long = System.currentTimeMillis()): Long =
            accumulatedMs + (runningSinceEpochMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L)
    }

    private val prefs = context.applicationContext.getSharedPreferences("methodmesh_astronomy", Context.MODE_PRIVATE)

    fun loadCalibration(): GoodNightCalibration? {
        val raw = prefs.getString("good_night_calibration", null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            GoodNightCalibration(
                o.getDouble("centroid_rms_px"), o.getDouble("fwhm_px"), o.getDouble("background_luma"),
                o.getDouble("contrast"), o.getDouble("scintillation_cv"), o.getString("saved_at_iso"), o.optString("note")
            )
        }.getOrNull()
    }

    fun saveCalibration(summary: StarTestSummary, note: String = "") {
        require(summary.detectionCount > 0 && summary.centroidRmsPx.isFinite() && summary.medianFwhmPx.isFinite())
        val o = JSONObject().apply {
            put("centroid_rms_px", summary.centroidRmsPx)
            put("fwhm_px", summary.medianFwhmPx)
            put("background_luma", summary.medianBackgroundLuma)
            put("contrast", summary.medianContrast)
            put("scintillation_cv", summary.scintillationCv)
            put("saved_at_iso", Instant.now().toString())
            put("note", note)
            put("meaning", "User-marked clear-night relative reference; not an absolute astronomical seeing or sky-brightness calibration")
        }
        prefs.edit().putString("good_night_calibration", o.toString()).apply()
    }

    fun clearCalibration() = prefs.edit().remove("good_night_calibration").apply()

    fun scoreAgainstCalibration(summary: StarTestSummary, calibration: GoodNightCalibration?): Map<String, Int?> {
        if (calibration == null || summary.detectionCount == 0) return mapOf("stability" to null, "transparency" to null, "darkness" to null, "focus" to null, "overall" to null)
        fun ratioGood(reference: Double, current: Double): Int? = if (!reference.isFinite() || !current.isFinite() || current <= 0) null else (100.0 * reference / current).toInt().coerceIn(0, 100)
        fun ratioHigh(reference: Double, current: Double): Int? = if (!reference.isFinite() || !current.isFinite() || reference <= 0) null else (100.0 * current / reference).toInt().coerceIn(0, 100)
        val stability = ratioGood(calibration.centroidRmsPx, summary.centroidRmsPx)
        val transparency = ratioHigh(calibration.contrast, summary.medianContrast)
        val darkness = ratioGood(calibration.backgroundLuma, summary.medianBackgroundLuma)
        val focus = ratioGood(calibration.fwhmPx, summary.medianFwhmPx)
        val vals = listOfNotNull(stability, transparency, darkness, focus)
        val overall = vals.takeIf { it.isNotEmpty() }?.average()?.toInt()?.coerceIn(0, 100)
        return mapOf("stability" to stability, "transparency" to transparency, "darkness" to darkness, "focus" to focus, "overall" to overall)
    }

    fun startSession(target: String, equipment: String, notes: String): ActiveSession {
        val now = System.currentTimeMillis()
        val session = ActiveSession(
            id = UUID.randomUUID().toString(),
            target = target,
            equipment = equipment,
            notes = notes,
            startIso = Instant.ofEpochMilli(now).toString(),
            runningSinceEpochMs = now,
            accumulatedMs = 0L,
            state = "running"
        )
        saveActiveSession(session)
        return session
    }

    fun loadActiveSession(): ActiveSession? {
        val raw = prefs.getString("active_session", null) ?: return null
        return runCatching { activeSessionFromJson(JSONObject(raw)) }.getOrNull()
    }

    fun updateActiveSession(session: ActiveSession) = saveActiveSession(session)

    fun pauseSession(session: ActiveSession): ActiveSession {
        val now = System.currentTimeMillis()
        val updated = session.copy(
            accumulatedMs = session.activeDurationMs(now),
            runningSinceEpochMs = null,
            state = "paused"
        )
        saveActiveSession(updated)
        return updated
    }

    fun resumeSession(session: ActiveSession): ActiveSession {
        val updated = session.copy(runningSinceEpochMs = System.currentTimeMillis(), state = "running")
        saveActiveSession(updated)
        return updated
    }

    fun finishSession(session: ActiveSession, saveHistory: Boolean = true): JSONObject {
        val now = System.currentTimeMillis()
        val finalDuration = session.activeDurationMs(now)
        val json = activeSessionToJson(session.copy(accumulatedMs = finalDuration, runningSinceEpochMs = null, state = "finished")).apply {
            put("end_iso", Instant.ofEpochMilli(now).toString())
            put("active_duration_seconds", finalDuration / 1000.0)
            put("created_iso", Instant.now().toString())
        }
        if (saveHistory) saveSession(json)
        prefs.edit().remove("active_session").apply()
        return json
    }

    fun discardActiveSession() = prefs.edit().remove("active_session").apply()

    private fun saveActiveSession(session: ActiveSession) {
        prefs.edit().putString("active_session", activeSessionToJson(session).toString()).apply()
    }

    private fun activeSessionToJson(session: ActiveSession): JSONObject = JSONObject().apply {
        put("session_id", session.id)
        put("target", session.target)
        put("equipment", session.equipment)
        put("notes", session.notes)
        put("start_iso", session.startIso)
        put("running_since_epoch_ms", session.runningSinceEpochMs ?: JSONObject.NULL)
        put("accumulated_ms", session.accumulatedMs)
        put("state", session.state)
    }

    private fun activeSessionFromJson(o: JSONObject): ActiveSession = ActiveSession(
        id = o.getString("session_id"),
        target = o.optString("target"),
        equipment = o.optString("equipment"),
        notes = o.optString("notes"),
        startIso = o.getString("start_iso"),
        runningSinceEpochMs = if (o.isNull("running_since_epoch_ms")) null else o.optLong("running_since_epoch_ms"),
        accumulatedMs = o.optLong("accumulated_ms", 0L),
        state = o.optString("state", "paused")
    )

    fun saveSession(json: JSONObject, maxSessions: Int = 100) {
        val arr = runCatching { JSONArray(prefs.getString("sessions", "[]")) }.getOrElse { JSONArray() }
        arr.put(json)
        val trimmed = JSONArray()
        val start = (arr.length() - maxSessions).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString("sessions", trimmed.toString()).apply()
    }
}
