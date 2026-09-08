package com.example.methodmesh.modules.gpstargetnavigator

import android.content.Context
import org.json.JSONObject

/**
 * Module-local persistence for an in-progress navigation session.
 *
 * This is intentionally active-session state only. Committed results are not
 * archived here; the store is cleared on Commit, Stop, or target mismatch.
 */
internal class GpsTargetNavigatorStateStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        "methodmesh_gps_target_navigator_active_session",
        Context.MODE_PRIVATE
    )

    fun load(): NavigationSessionSnapshot? {
        val raw = preferences.getString(KEY_SNAPSHOT, null) ?: return null
        return NavigationSessionSnapshot.fromJson(raw)
    }

    fun save(snapshot: NavigationSessionSnapshot) {
        preferences.edit().putString(KEY_SNAPSHOT, snapshot.toJson()).apply()
    }

    fun clear() {
        preferences.edit().remove(KEY_SNAPSHOT).apply()
    }

    private companion object {
        const val KEY_SNAPSHOT = "snapshot"
    }
}

internal data class NavigationSessionSnapshot(
    val targetName: String,
    val targetPlusCode: String,
    val targetLatitude: Double,
    val targetLongitude: Double,
    val arrivalRadiusM: Float,
    val lifecycle: String,
    val startedAtMs: Long,
    val hasLocationFix: Boolean,
    val currentLatitude: Double,
    val currentLongitude: Double,
    val accuracyM: Float,
    val distanceM: Float,
    val bearingDeg: Float,
    val headingDeg: Float,
    val relativeBearingDeg: Float,
    val updateCount: Int,
    val firstFixLatitude: Double?,
    val firstFixLongitude: Double?,
    val lastFixLatitude: Double?,
    val lastFixLongitude: Double?,
    val minDistanceM: Float?,
    val accuracySum: Double,
    val accuracyCount: Int,
    val maxAccuracyM: Float?
) {
    fun matchesTarget(latitude: Double, longitude: Double, arrivalRadius: Float): Boolean =
        kotlin.math.abs(targetLatitude - latitude) < 0.0000005 &&
            kotlin.math.abs(targetLongitude - longitude) < 0.0000005 &&
            kotlin.math.abs(arrivalRadiusM - arrivalRadius) < 0.01f

    fun toJson(): String = JSONObject().apply {
        put("target_name", targetName)
        put("target_plus_code", targetPlusCode)
        put("target_latitude", targetLatitude)
        put("target_longitude", targetLongitude)
        put("arrival_radius_m", arrivalRadiusM)
        put("lifecycle", lifecycle)
        put("started_at_ms", startedAtMs)
        put("has_location_fix", hasLocationFix)
        put("current_latitude", currentLatitude)
        put("current_longitude", currentLongitude)
        put("accuracy_m", accuracyM)
        put("distance_m", distanceM)
        put("bearing_deg", bearingDeg)
        put("heading_deg", headingDeg)
        put("relative_bearing_deg", relativeBearingDeg)
        put("update_count", updateCount)
        putNullable("first_fix_latitude", firstFixLatitude)
        putNullable("first_fix_longitude", firstFixLongitude)
        putNullable("last_fix_latitude", lastFixLatitude)
        putNullable("last_fix_longitude", lastFixLongitude)
        putNullable("min_distance_m", minDistanceM)
        put("accuracy_sum", accuracySum)
        put("accuracy_count", accuracyCount)
        putNullable("max_accuracy_m", maxAccuracyM)
    }.toString()

    companion object {
        fun fromJson(raw: String): NavigationSessionSnapshot? = runCatching {
            val json = JSONObject(raw)
            NavigationSessionSnapshot(
                targetName = json.optString("target_name"),
                targetPlusCode = json.optString("target_plus_code"),
                targetLatitude = json.getDouble("target_latitude"),
                targetLongitude = json.getDouble("target_longitude"),
                arrivalRadiusM = json.getDouble("arrival_radius_m").toFloat(),
                lifecycle = json.optString("lifecycle", "Navigating"),
                startedAtMs = json.optLong("started_at_ms", System.currentTimeMillis()),
                hasLocationFix = json.optBoolean("has_location_fix", false),
                currentLatitude = json.optDouble("current_latitude", 0.0),
                currentLongitude = json.optDouble("current_longitude", 0.0),
                accuracyM = json.optDouble("accuracy_m", 0.0).toFloat(),
                distanceM = json.optDouble("distance_m", 0.0).toFloat(),
                bearingDeg = json.optDouble("bearing_deg", 0.0).toFloat(),
                headingDeg = json.optDouble("heading_deg", 0.0).toFloat(),
                relativeBearingDeg = json.optDouble("relative_bearing_deg", 0.0).toFloat(),
                updateCount = json.optInt("update_count", 0),
                firstFixLatitude = json.optNullableDouble("first_fix_latitude"),
                firstFixLongitude = json.optNullableDouble("first_fix_longitude"),
                lastFixLatitude = json.optNullableDouble("last_fix_latitude"),
                lastFixLongitude = json.optNullableDouble("last_fix_longitude"),
                minDistanceM = json.optNullableDouble("min_distance_m")?.toFloat(),
                accuracySum = json.optDouble("accuracy_sum", 0.0),
                accuracyCount = json.optInt("accuracy_count", 0),
                maxAccuracyM = json.optNullableDouble("max_accuracy_m")?.toFloat()
            )
        }.getOrNull()
    }
}

private fun JSONObject.putNullable(key: String, value: Any?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableDouble(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key)
