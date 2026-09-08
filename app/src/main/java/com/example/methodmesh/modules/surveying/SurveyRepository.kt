package com.example.methodmesh.modules.surveying

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Capability-owned local field-book persistence. No network service is used. */
object SurveyRepository {
    private const val PREFS = "methodmesh_surveying"
    private const val TRAVERSE_JOBS = "traverse_jobs"
    private const val LEVEL_JOBS = "level_jobs"

    data class TraverseJob(
        val id: String,
        val name: String,
        val startPointId: String,
        val startEasting: Double,
        val startNorthing: Double,
        val closeEasting: Double?,
        val closeNorthing: Double?,
        val adjustmentMode: String,
        val minimumRelativePrecision: Double?,
        val legs: List<SurveyCalculations.TraverseLeg>,
        val createdIso: String,
        val updatedIso: String
    )

    data class LevelJob(
        val id: String,
        val name: String,
        val startReducedLevel: Double,
        val knownCloseReducedLevel: Double?,
        val distributeClosure: Boolean,
        val observations: List<SurveyCalculations.LevelObservation>,
        val createdIso: String,
        val updatedIso: String
    )

    fun newTraverseJob(
        name: String = "Traverse",
        startPointId: String = "START",
        startEasting: Double = 0.0,
        startNorthing: Double = 0.0,
        closeEasting: Double? = null,
        closeNorthing: Double? = null,
        adjustmentMode: String = "none",
        minimumRelativePrecision: Double? = null
    ): TraverseJob {
        val now = Instant.now().toString()
        return TraverseJob(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Traverse" },
            startPointId = startPointId.ifBlank { "START" },
            startEasting = startEasting,
            startNorthing = startNorthing,
            closeEasting = closeEasting,
            closeNorthing = closeNorthing,
            adjustmentMode = adjustmentMode,
            minimumRelativePrecision = minimumRelativePrecision,
            legs = emptyList(),
            createdIso = now,
            updatedIso = now
        )
    }

    fun newLevelJob(
        name: String = "Levelling",
        startReducedLevel: Double = 0.0,
        knownCloseReducedLevel: Double? = null,
        distributeClosure: Boolean = true
    ): LevelJob {
        val now = Instant.now().toString()
        return LevelJob(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Levelling" },
            startReducedLevel = startReducedLevel,
            knownCloseReducedLevel = knownCloseReducedLevel,
            distributeClosure = distributeClosure,
            observations = emptyList(),
            createdIso = now,
            updatedIso = now
        )
    }

    fun traverseJobs(context: Context): List<TraverseJob> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TRAVERSE_JOBS, "[]").orEmpty()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::decodeTraverse) }
            .sortedByDescending { it.updatedIso }
    }.getOrDefault(emptyList())

    fun levelJobs(context: Context): List<LevelJob> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(LEVEL_JOBS, "[]").orEmpty()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let(::decodeLevel) }
            .sortedByDescending { it.updatedIso }
    }.getOrDefault(emptyList())

    fun traverseJob(context: Context, id: String): TraverseJob? = traverseJobs(context).firstOrNull { it.id == id }
    fun levelJob(context: Context, id: String): LevelJob? = levelJobs(context).firstOrNull { it.id == id }

    fun saveTraverseJob(context: Context, job: TraverseJob): TraverseJob {
        val updated = job.copy(updatedIso = Instant.now().toString())
        val jobs = traverseJobs(context).filterNot { it.id == updated.id } + updated
        write(context, TRAVERSE_JOBS, JSONArray().apply { jobs.forEach { put(encodeTraverse(it)) } })
        return updated
    }

    fun saveLevelJob(context: Context, job: LevelJob): LevelJob {
        val updated = job.copy(updatedIso = Instant.now().toString())
        val jobs = levelJobs(context).filterNot { it.id == updated.id } + updated
        write(context, LEVEL_JOBS, JSONArray().apply { jobs.forEach { put(encodeLevel(it)) } })
        return updated
    }

    fun deleteTraverseJob(context: Context, id: String) {
        val jobs = traverseJobs(context).filterNot { it.id == id }
        write(context, TRAVERSE_JOBS, JSONArray().apply { jobs.forEach { put(encodeTraverse(it)) } })
    }

    fun deleteLevelJob(context: Context, id: String) {
        val jobs = levelJobs(context).filterNot { it.id == id }
        write(context, LEVEL_JOBS, JSONArray().apply { jobs.forEach { put(encodeLevel(it)) } })
    }

    fun traverseContext(job: TraverseJob): Map<String, String> = mapOf(
        "survey_job_id" to job.id,
        "survey_job_name" to job.name,
        "start_point_id" to job.startPointId,
        "start_easting" to job.startEasting.toString(),
        "start_northing" to job.startNorthing.toString(),
        "close_easting" to job.closeEasting?.toString().orEmpty(),
        "close_northing" to job.closeNorthing?.toString().orEmpty(),
        "adjustment_mode" to job.adjustmentMode,
        "minimum_relative_precision" to job.minimumRelativePrecision?.toString().orEmpty(),
        "traverse_legs" to job.legs.joinToString("\n") { "${it.toId},${it.bearingDeg},${it.distance}" }
    )

    fun levelContext(job: LevelJob): Map<String, String> = mapOf(
        "survey_job_id" to job.id,
        "survey_job_name" to job.name,
        "start_reduced_level_m" to job.startReducedLevel.toString(),
        "known_close_reduced_level_m" to job.knownCloseReducedLevel?.toString().orEmpty(),
        "distribute_closure" to job.distributeClosure.toString(),
        "level_observations" to job.observations.joinToString("\n") {
            "${it.station},${it.type},${it.reading},${it.distanceFromPrevious}"
        }
    )

    private fun write(context: Context, key: String, array: JSONArray) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }

    private fun encodeTraverse(job: TraverseJob): JSONObject = JSONObject().apply {
        put("id", job.id)
        put("name", job.name)
        put("startPointId", job.startPointId)
        put("startEasting", job.startEasting)
        put("startNorthing", job.startNorthing)
        putNullable("closeEasting", job.closeEasting)
        putNullable("closeNorthing", job.closeNorthing)
        put("adjustmentMode", job.adjustmentMode)
        putNullable("minimumRelativePrecision", job.minimumRelativePrecision)
        put("createdIso", job.createdIso)
        put("updatedIso", job.updatedIso)
        put("legs", JSONArray().apply {
            job.legs.forEach { leg ->
                put(JSONObject().apply {
                    put("toId", leg.toId)
                    put("bearingDeg", leg.bearingDeg)
                    put("distance", leg.distance)
                })
            }
        })
    }

    private fun decodeTraverse(obj: JSONObject): TraverseJob? = runCatching {
        val legsArray = obj.optJSONArray("legs") ?: JSONArray()
        val legs = (0 until legsArray.length()).mapNotNull { index ->
            legsArray.optJSONObject(index)?.let { leg ->
                SurveyCalculations.TraverseLeg(
                    toId = leg.optString("toId", "P${index + 1}"),
                    bearingDeg = leg.getDouble("bearingDeg"),
                    distance = leg.getDouble("distance")
                )
            }
        }
        TraverseJob(
            id = obj.getString("id"),
            name = obj.optString("name", "Traverse"),
            startPointId = obj.optString("startPointId", "START"),
            startEasting = obj.getDouble("startEasting"),
            startNorthing = obj.getDouble("startNorthing"),
            closeEasting = obj.optNullableDouble("closeEasting"),
            closeNorthing = obj.optNullableDouble("closeNorthing"),
            adjustmentMode = obj.optString("adjustmentMode", "none"),
            minimumRelativePrecision = obj.optNullableDouble("minimumRelativePrecision"),
            legs = legs,
            createdIso = obj.optString("createdIso", Instant.EPOCH.toString()),
            updatedIso = obj.optString("updatedIso", Instant.EPOCH.toString())
        )
    }.getOrNull()

    private fun encodeLevel(job: LevelJob): JSONObject = JSONObject().apply {
        put("id", job.id)
        put("name", job.name)
        put("startReducedLevel", job.startReducedLevel)
        putNullable("knownCloseReducedLevel", job.knownCloseReducedLevel)
        put("distributeClosure", job.distributeClosure)
        put("createdIso", job.createdIso)
        put("updatedIso", job.updatedIso)
        put("observations", JSONArray().apply {
            job.observations.forEach { obs ->
                put(JSONObject().apply {
                    put("station", obs.station)
                    put("type", obs.type)
                    put("reading", obs.reading)
                    put("distanceFromPrevious", obs.distanceFromPrevious)
                })
            }
        })
    }

    private fun decodeLevel(obj: JSONObject): LevelJob? = runCatching {
        val obsArray = obj.optJSONArray("observations") ?: JSONArray()
        val observations = (0 until obsArray.length()).mapNotNull { index ->
            obsArray.optJSONObject(index)?.let { obs ->
                SurveyCalculations.LevelObservation(
                    station = obs.optString("station", "P${index + 1}"),
                    type = obs.optString("type", "IS"),
                    reading = obs.getDouble("reading"),
                    distanceFromPrevious = obs.optDouble("distanceFromPrevious", 0.0)
                )
            }
        }
        LevelJob(
            id = obj.getString("id"),
            name = obj.optString("name", "Levelling"),
            startReducedLevel = obj.getDouble("startReducedLevel"),
            knownCloseReducedLevel = obj.optNullableDouble("knownCloseReducedLevel"),
            distributeClosure = obj.optBoolean("distributeClosure", true),
            observations = observations,
            createdIso = obj.optString("createdIso", Instant.EPOCH.toString()),
            updatedIso = obj.optString("updatedIso", Instant.EPOCH.toString())
        )
    }.getOrNull()

    private fun JSONObject.putNullable(key: String, value: Double?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { it.isFinite() }
}
