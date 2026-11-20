package com.example.methodmesh.modules.astronomy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Module-owned persistence for astronomy-only state and deliberately cached data. */
class AstronomyRepository(context: Context, private val nowMs: () -> Long = System::currentTimeMillis) {
    private companion object { val sessionLock = Any() }
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

    fun startSession(target: String, equipment: String, notes: String): ActiveSession = synchronized(sessionLock) {
        check(loadActiveSession() == null) { "An unfinished session already exists. Resume or discard it first." }
        val now = nowMs()
        val session = ActiveSession(UUID.randomUUID().toString(), target, equipment, notes,
            Instant.ofEpochMilli(now).toString(), now, 0L, "running")
        writeSession { putString("active_session", activeSessionToJson(session).toString()) }
        session
    }

    fun loadActiveSession(): ActiveSession? = synchronized(sessionLock) {
        val raw = prefs.getString("active_session", null) ?: return@synchronized null
        try { activeSessionFromJson(JSONObject(raw)) }
        catch (failure: Exception) { throw IllegalStateException("The unfinished session could not be read; it has not been overwritten.", failure) }
    }

    fun updateActiveSession(session: ActiveSession) = synchronized(sessionLock) {
        val current = requireCurrent(session.id)
        val updated = current.copy(target = session.target, equipment = session.equipment, notes = session.notes)
        writeSession { putString("active_session", activeSessionToJson(updated).toString()) }
        updated
    }

    fun pauseSession(session: ActiveSession): ActiveSession = synchronized(sessionLock) {
        val current = requireCurrent(session.id).copy(target = session.target, equipment = session.equipment, notes = session.notes)
        val updated = if (current.state == "running")
            current.copy(accumulatedMs = current.activeDurationMs(nowMs()), runningSinceEpochMs = null, state = "paused")
        else current
        writeSession { putString("active_session", activeSessionToJson(updated).toString()) }
        updated
    }

    fun resumeSession(session: ActiveSession): ActiveSession = synchronized(sessionLock) {
        val current = requireCurrent(session.id).copy(target = session.target, equipment = session.equipment, notes = session.notes)
        val updated = if (current.state != "running") current.copy(runningSinceEpochMs = nowMs(), state = "running") else current
        writeSession { putString("active_session", activeSessionToJson(updated).toString()) }
        updated
    }

    fun finishSession(session: ActiveSession, saveHistory: Boolean = true): JSONObject =
        completeSession(session, saveHistory) { it to it }

    /** Prepare the exact return payload before one durable history/active-session transaction. */
    internal fun <T> completeSession(session: ActiveSession, saveHistory: Boolean,
        prepare: (JSONObject) -> Pair<JSONObject, T>): T = synchronized(sessionLock) {
        val current = requireCurrent(session.id).copy(target = session.target, equipment = session.equipment, notes = session.notes)
        val now = nowMs()
        val duration = current.activeDurationMs(now)
        val finished = activeSessionToJson(current.copy(accumulatedMs = duration, runningSinceEpochMs = null, state = "finished")).apply {
            put("end_iso", Instant.ofEpochMilli(now).toString())
            put("active_duration_seconds", duration / 1000.0)
            put("created_iso", Instant.ofEpochMilli(now).toString())
        }
        val (domainRecord, result) = prepare(finished)
        val historyRecord = JSONObject(finished.toString()).apply {
            domainRecord.keys().forEach { key -> put(key, domainRecord.get(key)) }
            // Keep the existing workspace-history numeric representation.
            put("active_duration_seconds", finished.getDouble("active_duration_seconds"))
        }
        val history = if (saveHistory) historyWith(historyRecord, 100) else null
        writeSession {
            if (history != null) putString("sessions", history)
            remove("active_session")
        }
        result
    }

    fun discardActiveSession(expectedId: String? = null) = synchronized(sessionLock) {
        if (expectedId != null) requireCurrent(expectedId)
        writeSession { remove("active_session") }
    }

    private fun requireCurrent(id: String): ActiveSession = checkNotNull(loadActiveSession()?.takeIf { it.id == id }) {
        "This session is no longer active. Reload before making changes."
    }

    private fun writeSession(change: android.content.SharedPreferences.Editor.() -> Unit) {
        val before = listOf("active_session", "sessions").associateWith { prefs.getString(it, null) }
        val editor = prefs.edit().apply(change)
        var writeFailure: Exception? = null
        val committed = try { editor.commit() } catch (failure: Exception) { writeFailure = failure; false }
        if (!committed) {
            // A failed SharedPreferences commit may already have changed its memory cache.
            // Restore this operation's exact prior session/history values, not unrelated preferences.
            val rollback = prefs.edit()
            before.forEach { (key, value) -> if (value == null) rollback.remove(key) else rollback.putString(key, value) }
            runCatching { rollback.commit() }
            throw IllegalStateException("Session save failed. Keep this screen open and retry.", writeFailure)
        }
    }

    private fun activeSessionToJson(session: ActiveSession): JSONObject = JSONObject().apply {
        put("session_id", session.id); put("target", session.target); put("equipment", session.equipment)
        put("notes", session.notes); put("start_iso", session.startIso)
        put("running_since_epoch_ms", session.runningSinceEpochMs ?: JSONObject.NULL)
        put("accumulated_ms", session.accumulatedMs); put("state", session.state)
    }

    private fun activeSessionFromJson(o: JSONObject): ActiveSession = ActiveSession(
        o.getString("session_id"), o.optString("target"), o.optString("equipment"), o.optString("notes"),
        o.getString("start_iso"), if (o.isNull("running_since_epoch_ms")) null else o.optLong("running_since_epoch_ms"),
        o.optLong("accumulated_ms", 0L), o.optString("state", "paused"))

    private fun historyWith(json: JSONObject, maxSessions: Int): String {
        require(maxSessions > 0)
        val arr = try { JSONArray(prefs.getString("sessions", "[]")) }
            catch (failure: Exception) { throw IllegalStateException("Stored session history could not be read; it has not been replaced.", failure) }
        val id = json.optString("session_id")
        check(id.isBlank() || (0 until arr.length()).none { arr.optJSONObject(it)?.optString("session_id") == id }) {
            "This session is already in history; it has not been duplicated."
        }
        arr.put(json)
        return JSONArray().apply { for (i in (arr.length() - maxSessions).coerceAtLeast(0) until arr.length()) put(arr.get(i)) }.toString()
    }

    fun saveSession(json: JSONObject, maxSessions: Int = 100) = synchronized(sessionLock) {
        val history = historyWith(json, maxSessions)
        writeSession { putString("sessions", history) }
    }
}
