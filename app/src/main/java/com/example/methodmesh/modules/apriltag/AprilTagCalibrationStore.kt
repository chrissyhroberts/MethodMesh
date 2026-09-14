package com.example.methodmesh.modules.apriltag

import android.content.Context
import org.json.JSONObject
import java.time.Instant

/**
 * Module-owned persistent metric calibration for the rear-camera AprilTag instrument.
 *
 * The detector still receives the measured physical tag size. The saved scale is a
 * dimensionless correction applied to pose translations after detection:
 *
 *     corrected distance = raw pose distance × distanceScale
 *
 * Because AprilTag pose translation is linear in tag size, this is equivalent to
 * using an effective pose size of physicalTagSize × distanceScale while leaving
 * planar measurements tied to the true physical tag size.
 */
internal object AprilTagCalibrationStore {
    private const val PREFS = "methodmesh_apriltag_metric_calibration"
    private const val KEY_SCALE = "distance_scale"
    private const val KEY_METADATA = "metadata_json"

    data class Profile(
        val distanceScale: Double,
        val metadataJson: String = ""
    )

    fun load(context: Context): Profile {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val scale = prefs.getString(KEY_SCALE, null)?.toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: AprilTagContractMetadata.DEFAULT_DISTANCE_SCALE.toDouble()
        return Profile(scale, prefs.getString(KEY_METADATA, "").orEmpty())
    }

    fun save(
        context: Context,
        distanceScale: Double,
        tagSizeMm: Double,
        rawDistanceM: Double,
        trueDistanceMm: Double,
        tagId: Int,
        intrinsicsSource: String
    ) {
        if (!distanceScale.isFinite() || distanceScale <= 0.0) return
        val metadata = JSONObject()
            .put("distance_scale", distanceScale)
            .put("tag_size_mm", tagSizeMm)
            .put("effective_pose_tag_size_mm", tagSizeMm * distanceScale)
            .put("raw_distance_m", rawDistanceM)
            .put("true_distance_mm", trueDistanceMm)
            .put("tag_id", tagId)
            .put("intrinsics_source", intrinsicsSource)
            .put("saved_at_iso", Instant.now().toString())
            .toString()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCALE, distanceScale.toString())
            .putString(KEY_METADATA, metadata)
            .apply()
    }
    fun saveManual(context: Context, distanceScale: Double, tagSizeMm: Double) {
        if (!distanceScale.isFinite() || distanceScale <= 0.0) return
        val metadata = JSONObject()
            .put("distance_scale", distanceScale)
            .put("tag_size_mm", tagSizeMm)
            .put("effective_pose_tag_size_mm", tagSizeMm * distanceScale)
            .put("source", "manual_adjustment")
            .put("saved_at_iso", Instant.now().toString())
            .toString()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SCALE, distanceScale.toString())
            .putString(KEY_METADATA, metadata)
            .apply()
    }

}
