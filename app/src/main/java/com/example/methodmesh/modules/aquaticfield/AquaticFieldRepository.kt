package com.example.methodmesh.modules.aquaticfield

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Small module-owned field repository.
 *
 * Local field state is deliberately distinct from MethodMesh execution-graph
 * commits. A native capture may update the active visit immediately so field
 * data survive process loss, while the persistent dashboard only emits a graph
 * result when its normal MethodMesh close-out is used.
 */
object AquaticFieldRepository {
    private const val PREFS = "methodmesh_aquaticfield"
    private const val RECORDS = "records_json"
    private const val MAX_RECORDS = 1000

    private const val ACTIVE_STATION = "active_station_id"
    private const val ACTIVE_VISIT = "active_visit_id"
    private const val VISIT_STATUS = "visit_status"
    private const val WATER_DEPTH = "water_depth_m"
    private const val PLANNED_SAMPLES = "planned_samples"
    private const val COMPLETED_SAMPLES = "completed_samples"
    private const val CAST_COUNT = "cast_count"
    private const val SECCHI_DEPTH = "secchi_depth_m"
    private const val QC_ISSUES = "qc_issues"
    private const val LAST_UPDATED = "last_updated_utc"
    private const val PLANNED_DEPTHS_JSON = "planned_depths_json"
    private const val COMPLETED_DEPTHS_JSON = "completed_depths_json"
    private const val COMPLETED_SAMPLE_IDS_JSON = "completed_sample_ids_json"
    private const val CAST_IDS_JSON = "cast_ids_json"

    fun record(context: Context, capabilityId: String, fields: Map<String, String>) {
        if (fields[AquaticCommonFields.STATUS] != "succeeded") return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = Instant.now().toString()

        // Persist the immutable-ish event record first. Re-running an identical
        // screen may create another event record, but operational counters below
        // are keyed by scientific identifiers and therefore do not double-count.
        val record = JSONObject().apply {
            put("capability_id", capabilityId)
            put("recorded_time_utc", now)
            put("fields", JSONObject(fields))
        }
        appendRecord(context, record)

        val edit = prefs.edit().putString(LAST_UPDATED, now)
        when (capabilityId) {
            As100AquaticStationVisitMethod.ID -> {
                val newStation = fields["aquatic_station_id"].orEmpty()
                val newVisit = fields["aquatic_visit_id"].orEmpty()
                val existingVisit = prefs.getString(ACTIVE_VISIT, "").orEmpty()
                edit.putString(ACTIVE_STATION, newStation)
                edit.putString(ACTIVE_VISIT, newVisit)
                edit.putString(VISIT_STATUS, fields["aquatic_visit_status"].orEmpty().ifBlank { "open" })
                edit.putString(WATER_DEPTH, fields["aquatic_station_water_depth_m"].orEmpty())
                // Only reset operational state when a genuinely different visit
                // is opened. A retry/update of the same station-visit must not
                // erase already captured samples.
                if (newVisit != existingVisit) {
                    edit.putInt(PLANNED_SAMPLES, 0)
                    edit.putInt(COMPLETED_SAMPLES, 0)
                    edit.putInt(CAST_COUNT, 0)
                    edit.putString(SECCHI_DEPTH, "")
                    edit.putInt(QC_ISSUES, 0)
                    edit.putString(PLANNED_DEPTHS_JSON, "[]")
                    edit.putString(COMPLETED_DEPTHS_JSON, "[]")
                    edit.putString(COMPLETED_SAMPLE_IDS_JSON, "[]")
                    edit.putString(CAST_IDS_JSON, "[]")
                }
            }

            As100AquaticDepthPlanMethod.ID -> {
                val planJson = fields["aquatic_depth_plan_json"].orEmpty().ifBlank { "[]" }
                edit.putString(PLANNED_DEPTHS_JSON, planJson)
                edit.putInt(PLANNED_SAMPLES, jsonArray(planJson).length())
            }

            As100AquaticSampleRecordMethod.ID -> {
                val sampleId = fields["aquatic_sample_id"].orEmpty().trim()
                val ids = stringArray(prefs.getString(COMPLETED_SAMPLE_IDS_JSON, "[]").orEmpty()).toMutableList()
                if (sampleId.isNotBlank() && sampleId !in ids) ids += sampleId
                edit.putString(COMPLETED_SAMPLE_IDS_JSON, JSONArray(ids).toString())
                edit.putInt(COMPLETED_SAMPLES, ids.size)

                val depth = fields["aquatic_sample_actual_depth_m"].orEmpty()
                    .ifBlank { fields["aquatic_sample_target_depth_m"].orEmpty() }
                    .trim()
                if (depth.isNotBlank()) {
                    val depths = stringArray(prefs.getString(COMPLETED_DEPTHS_JSON, "[]").orEmpty()).toMutableList()
                    if (depth !in depths) depths += depth
                    edit.putString(COMPLETED_DEPTHS_JSON, JSONArray(depths).toString())
                }
            }

            As100AquaticCtdCastMethod.ID -> {
                val castId = fields["aquatic_ctd_cast_id"].orEmpty().trim()
                val ids = stringArray(prefs.getString(CAST_IDS_JSON, "[]").orEmpty()).toMutableList()
                if (castId.isNotBlank() && castId !in ids) ids += castId
                edit.putString(CAST_IDS_JSON, JSONArray(ids).toString())
                edit.putInt(CAST_COUNT, ids.size)
            }

            As100AquaticSecchiMethod.ID -> {
                edit.putString(SECCHI_DEPTH, fields["aquatic_secchi_depth_m"].orEmpty())
            }

            As100AquaticFieldQcMethod.ID -> {
                val issues = fields["aquatic_qc_issue_count"]?.toIntOrNull() ?: 0
                val warnings = fields["aquatic_qc_warning_count"]?.toIntOrNull() ?: 0
                edit.putInt(QC_ISSUES, issues + warnings)
            }
        }
        edit.apply()
    }

    fun snapshot(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return linkedMapOf(
            "station_id" to prefs.getString(ACTIVE_STATION, "").orEmpty(),
            "visit_id" to prefs.getString(ACTIVE_VISIT, "").orEmpty(),
            "visit_status" to prefs.getString(VISIT_STATUS, "").orEmpty(),
            "water_depth_m" to prefs.getString(WATER_DEPTH, "").orEmpty(),
            "planned_sample_count" to prefs.getInt(PLANNED_SAMPLES, 0).toString(),
            "completed_sample_count" to prefs.getInt(COMPLETED_SAMPLES, 0).toString(),
            "cast_count" to prefs.getInt(CAST_COUNT, 0).toString(),
            "secchi_depth_m" to prefs.getString(SECCHI_DEPTH, "").orEmpty(),
            "qc_issue_count" to prefs.getInt(QC_ISSUES, 0).toString(),
            "planned_depths_json" to prefs.getString(PLANNED_DEPTHS_JSON, "[]").orEmpty(),
            "completed_depths_json" to prefs.getString(COMPLETED_DEPTHS_JSON, "[]").orEmpty(),
            "completed_sample_ids_json" to prefs.getString(COMPLETED_SAMPLE_IDS_JSON, "[]").orEmpty(),
            "cast_ids_json" to prefs.getString(CAST_IDS_JSON, "[]").orEmpty(),
            "last_updated_utc" to prefs.getString(LAST_UPDATED, "").orEmpty()
        )
    }

    fun finishVisit(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(VISIT_STATUS, "completed")
            .putString(LAST_UPDATED, Instant.now().toString())
            .apply()
    }

    fun clearActiveVisit(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(ACTIVE_STATION)
            .remove(ACTIVE_VISIT)
            .remove(VISIT_STATUS)
            .remove(WATER_DEPTH)
            .remove(PLANNED_SAMPLES)
            .remove(COMPLETED_SAMPLES)
            .remove(CAST_COUNT)
            .remove(SECCHI_DEPTH)
            .remove(QC_ISSUES)
            .remove(PLANNED_DEPTHS_JSON)
            .remove(COMPLETED_DEPTHS_JSON)
            .remove(COMPLETED_SAMPLE_IDS_JSON)
            .remove(CAST_IDS_JSON)
            .putString(LAST_UPDATED, Instant.now().toString())
            .apply()
    }

    fun readRecords(context: Context): JSONArray {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(RECORDS, "[]").orEmpty()
        return jsonArray(raw)
    }

    private fun appendRecord(context: Context, record: JSONObject) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = readRecords(context)
        val start = (current.length() - (MAX_RECORDS - 1)).coerceAtLeast(0)
        val next = JSONArray()
        for (i in start until current.length()) next.put(current.get(i))
        next.put(record)
        prefs.edit().putString(RECORDS, next.toString()).apply()
    }

    private fun jsonArray(raw: String): JSONArray =
        runCatching { JSONArray(raw.ifBlank { "[]" }) }.getOrElse { JSONArray() }

    private fun stringArray(raw: String): List<String> {
        val arr = jsonArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }
}
