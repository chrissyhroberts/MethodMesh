package com.example.methodmesh.modules.diving

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** Module-owned local persistence for diving records.
 *
 * Records are deliberately stored as portable JSON in SharedPreferences for this
 * Development prototype. They can later migrate to a shared MethodMesh repository
 * without changing the public method IDs or output fields.
 */
object DivingRepository {
    private const val PREFS = "methodmesh_diving"
    private const val DIVES = "dives_json"
    private const val ANALYSES = "gas_analyses_json"
    private const val CYLINDERS = "cylinders_json"

    data class DiveLogEntry(
        val id: String,
        val dateTimeIso: String,
        val site: String,
        val buddy: String,
        val maxDepthM: Double,
        val averageDepthM: Double?,
        val durationMin: Double,
        val cylinderVolumeL: Double?,
        val startPressureBar: Double?,
        val endPressureBar: Double?,
        val fo2Percent: Double?,
        val fhePercent: Double?,
        val waterTemperatureC: Double?,
        val visibilityM: Double?,
        val suit: String,
        val weightKg: Double?,
        val notes: String,
        val rmvLMin: Double?,
        val recordedTimeIso: String
    )

    data class GasAnalysisEntry(
        val id: String,
        val cylinderId: String,
        val fo2Percent: Double,
        val fhePercent: Double,
        val pressureBar: Double?,
        val analyser: String,
        val analysedBy: String,
        val analysedTimeIso: String,
        val note: String
    )

    data class CylinderEntry(
        val id: String,
        val label: String,
        val waterVolumeL: Double,
        val workingPressureBar: Double,
        val currentPressureBar: Double,
        val material: String,
        val testDueDate: String,
        val serviceNote: String,
        val oxygenClean: Boolean,
        val updatedTimeIso: String
    )

    data class LogSummary(
        val count: Int,
        val totalTimeMin: Double,
        val deepestM: Double,
        val meanRmvLMin: Double?,
        val recent: List<DiveLogEntry>
    )

    data class GasSummary(
        val cylinderCount: Int,
        val analysisCount: Int,
        val totalNominalGasL: Double,
        val cylinders: List<CylinderEntry>,
        val analyses: List<GasAnalysisEntry>
    )

    fun newId(prefix: String): String = "$prefix-${UUID.randomUUID()}"

    fun dives(context: Context): List<DiveLogEntry> =
        readArray(context, DIVES).mapNotNull(::diveFromJson).sortedByDescending { it.dateTimeIso }

    fun analyses(context: Context): List<GasAnalysisEntry> =
        readArray(context, ANALYSES).mapNotNull(::analysisFromJson).sortedByDescending { it.analysedTimeIso }

    fun cylinders(context: Context): List<CylinderEntry> =
        readArray(context, CYLINDERS).mapNotNull(::cylinderFromJson).sortedBy { it.label.lowercase() }

    fun saveDive(context: Context, entry: DiveLogEntry) = upsert(context, DIVES, entry.id, diveToJson(entry))
    fun saveAnalysis(context: Context, entry: GasAnalysisEntry) = upsert(context, ANALYSES, entry.id, analysisToJson(entry))
    fun saveCylinder(context: Context, entry: CylinderEntry) = upsert(context, CYLINDERS, entry.id, cylinderToJson(entry))

    fun saveDiveJson(context: Context, raw: String): DiveLogEntry? = runCatching { diveFromJson(JSONObject(raw)) }.getOrNull()?.also { saveDive(context, it) }
    fun saveAnalysisJson(context: Context, raw: String): GasAnalysisEntry? = runCatching { analysisFromJson(JSONObject(raw)) }.getOrNull()?.also { saveAnalysis(context, it) }
    fun saveCylinderJson(context: Context, raw: String): CylinderEntry? = runCatching { cylinderFromJson(JSONObject(raw)) }.getOrNull()?.also { saveCylinder(context, it) }

    fun logSummary(context: Context, recentLimit: Int = 10): LogSummary = logSummary(dives(context), recentLimit)

    fun logSummary(entries: List<DiveLogEntry>, recentLimit: Int = 10): LogSummary {
        val validRmvs = entries.mapNotNull { it.rmvLMin }.filter { it.isFinite() && it > 0.0 }
        return LogSummary(
            count = entries.size,
            totalTimeMin = entries.sumOf { it.durationMin },
            deepestM = entries.maxOfOrNull { it.maxDepthM } ?: 0.0,
            meanRmvLMin = validRmvs.takeIf { it.isNotEmpty() }?.average(),
            recent = entries.sortedByDescending { it.dateTimeIso }.take(recentLimit.coerceIn(1, 100))
        )
    }

    fun gasSummary(context: Context): GasSummary = gasSummary(cylinders(context), analyses(context))

    fun gasSummary(cylinders: List<CylinderEntry>, analyses: List<GasAnalysisEntry>): GasSummary = GasSummary(
        cylinderCount = cylinders.size,
        analysisCount = analyses.size,
        totalNominalGasL = cylinders.sumOf { it.waterVolumeL * it.currentPressureBar },
        cylinders = cylinders,
        analyses = analyses
    )

    fun divesJson(entries: List<DiveLogEntry>): String = JSONArray().apply { entries.forEach { put(diveToJson(it)) } }.toString()
    fun analysesJson(entries: List<GasAnalysisEntry>): String = JSONArray().apply { entries.forEach { put(analysisToJson(it)) } }.toString()
    fun cylindersJson(entries: List<CylinderEntry>): String = JSONArray().apply { entries.forEach { put(cylinderToJson(it)) } }.toString()

    fun parseDivesJson(raw: String): List<DiveLogEntry> = runCatching {
        val a = JSONArray(raw.ifBlank { "[]" })
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::diveFromJson) }
    }.getOrDefault(emptyList())

    fun parseAnalysesJson(raw: String): List<GasAnalysisEntry> = runCatching {
        val a = JSONArray(raw.ifBlank { "[]" })
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::analysisFromJson) }
    }.getOrDefault(emptyList())

    fun parseCylindersJson(raw: String): List<CylinderEntry> = runCatching {
        val a = JSONArray(raw.ifBlank { "[]" })
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let(::cylinderFromJson) }
    }.getOrDefault(emptyList())

    fun diveToJson(e: DiveLogEntry): JSONObject = JSONObject().apply {
        put("id", e.id); put("date_time_iso", e.dateTimeIso); put("site", e.site); put("buddy", e.buddy)
        put("max_depth_m", e.maxDepthM); putNullable("average_depth_m", e.averageDepthM); put("duration_min", e.durationMin)
        putNullable("cylinder_volume_l", e.cylinderVolumeL); putNullable("start_pressure_bar", e.startPressureBar); putNullable("end_pressure_bar", e.endPressureBar)
        putNullable("fo2_percent", e.fo2Percent); putNullable("fhe_percent", e.fhePercent); putNullable("water_temperature_c", e.waterTemperatureC)
        putNullable("visibility_m", e.visibilityM); put("suit", e.suit); putNullable("weight_kg", e.weightKg); put("notes", e.notes)
        putNullable("rmv_l_min", e.rmvLMin); put("recorded_time_iso", e.recordedTimeIso)
    }

    fun analysisToJson(e: GasAnalysisEntry): JSONObject = JSONObject().apply {
        put("id", e.id); put("cylinder_id", e.cylinderId); put("fo2_percent", e.fo2Percent); put("fhe_percent", e.fhePercent)
        putNullable("pressure_bar", e.pressureBar); put("analyser", e.analyser); put("analysed_by", e.analysedBy)
        put("analysed_time_iso", e.analysedTimeIso); put("note", e.note)
    }

    fun cylinderToJson(e: CylinderEntry): JSONObject = JSONObject().apply {
        put("id", e.id); put("label", e.label); put("water_volume_l", e.waterVolumeL); put("working_pressure_bar", e.workingPressureBar)
        put("current_pressure_bar", e.currentPressureBar); put("material", e.material); put("test_due_date", e.testDueDate)
        put("service_note", e.serviceNote); put("oxygen_clean", e.oxygenClean); put("updated_time_iso", e.updatedTimeIso)
    }

    private fun diveFromJson(o: JSONObject): DiveLogEntry? = runCatching {
        DiveLogEntry(
            id = o.optString("id").ifBlank { newId("dive") },
            dateTimeIso = o.optString("date_time_iso").ifBlank { Instant.now().toString() },
            site = o.optString("site"), buddy = o.optString("buddy"), maxDepthM = o.optDouble("max_depth_m", 0.0),
            averageDepthM = o.optNullableDouble("average_depth_m"), durationMin = o.optDouble("duration_min", 0.0),
            cylinderVolumeL = o.optNullableDouble("cylinder_volume_l"), startPressureBar = o.optNullableDouble("start_pressure_bar"),
            endPressureBar = o.optNullableDouble("end_pressure_bar"), fo2Percent = o.optNullableDouble("fo2_percent"),
            fhePercent = o.optNullableDouble("fhe_percent"), waterTemperatureC = o.optNullableDouble("water_temperature_c"),
            visibilityM = o.optNullableDouble("visibility_m"), suit = o.optString("suit"), weightKg = o.optNullableDouble("weight_kg"),
            notes = o.optString("notes"), rmvLMin = o.optNullableDouble("rmv_l_min"), recordedTimeIso = o.optString("recorded_time_iso")
        )
    }.getOrNull()

    private fun analysisFromJson(o: JSONObject): GasAnalysisEntry? = runCatching {
        GasAnalysisEntry(
            id = o.optString("id").ifBlank { newId("gas") }, cylinderId = o.optString("cylinder_id"),
            fo2Percent = o.optDouble("fo2_percent", 21.0), fhePercent = o.optDouble("fhe_percent", 0.0),
            pressureBar = o.optNullableDouble("pressure_bar"), analyser = o.optString("analyser"), analysedBy = o.optString("analysed_by"),
            analysedTimeIso = o.optString("analysed_time_iso").ifBlank { Instant.now().toString() }, note = o.optString("note")
        )
    }.getOrNull()

    private fun cylinderFromJson(o: JSONObject): CylinderEntry? = runCatching {
        CylinderEntry(
            id = o.optString("id").ifBlank { newId("cyl") }, label = o.optString("label"), waterVolumeL = o.optDouble("water_volume_l", 12.0),
            workingPressureBar = o.optDouble("working_pressure_bar", 232.0), currentPressureBar = o.optDouble("current_pressure_bar", 0.0),
            material = o.optString("material", "steel"), testDueDate = o.optString("test_due_date"), serviceNote = o.optString("service_note"),
            oxygenClean = o.optBoolean("oxygen_clean", false), updatedTimeIso = o.optString("updated_time_iso").ifBlank { Instant.now().toString() }
        )
    }.getOrNull()

    private fun readArray(context: Context, key: String): List<JSONObject> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]").orEmpty()
        val array = JSONArray(raw.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun upsert(context: Context, key: String, id: String, encoded: JSONObject) {
        val items = readArray(context, key).toMutableList()
        val existing = items.indexOfFirst { it.optString("id") == id }
        if (existing >= 0) items[existing] = encoded else items.add(encoded)
        val array = JSONArray().apply { items.forEach(::put) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
    }
}

private fun JSONObject.putNullable(key: String, value: Double?) {
    if (value == null || !value.isFinite()) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.optNullableDouble(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key).takeIf { it.isFinite() }
}
