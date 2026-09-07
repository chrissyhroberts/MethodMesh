package com.example.methodmesh.modules.scoring

import android.content.Context
import org.json.JSONArray
import java.io.File
import java.time.Instant

class ScoringSessionRepository private constructor(context: Context) {
    private val root = File(context.filesDir, "methodmesh/scoring/sessions").apply { mkdirs() }
    private val recordsFile = File(context.filesDir, "methodmesh/scoring/high_scores.json").apply { parentFile?.mkdirs() }

    @Synchronized
    fun save(session: ScoreSession): ScoreSession {
        val target = File(root, "${session.id}.json")
        val temp = File(root, "${session.id}.json.tmp")
        temp.writeText(session.toJson().toString())
        if (target.exists()) target.delete()
        check(temp.renameTo(target)) { "Could not persist scoring session ${session.id}." }
        return session
    }

    @Synchronized
    fun load(id: String): ScoreSession? {
        val file = File(root, "$id.json")
        if (!file.exists()) return null
        return runCatching { ScoreSession.fromJson(org.json.JSONObject(file.readText())) }.getOrNull()
    }

    @Synchronized
    fun listActive(): List<ScoreSession> = root.listFiles { f -> f.extension == "json" }?.mapNotNull { file ->
        runCatching { ScoreSession.fromJson(org.json.JSONObject(file.readText())) }.getOrNull()
    }?.filter { it.status == ScoreSessionStatus.ACTIVE || it.status == ScoreSessionStatus.PAUSED }
        ?.sortedByDescending { it.updatedAtIso } ?: emptyList()

    @Synchronized
    fun deleteTemporary(id: String) { File(root, "$id.json").delete() }

    @Synchronized
    fun saveHighScore(record: HighScoreRecord) {
        val records = listHighScores().toMutableList().apply { add(record) }
        val tmp = File(recordsFile.parentFile, "${recordsFile.name}.tmp")
        tmp.writeText(JSONArray().apply { records.forEach { put(it.toJson()) } }.toString())
        if (recordsFile.exists()) recordsFile.delete()
        check(tmp.renameTo(recordsFile)) { "Could not persist high score record." }
    }

    @Synchronized
    fun listHighScores(activity: String? = null): List<HighScoreRecord> {
        if (!recordsFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(recordsFile.readText())
            List(arr.length()) { HighScoreRecord.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
            .filter { activity.isNullOrBlank() || it.activity == activity }
            .sortedWith(compareByDescending<HighScoreRecord> { it.score }.thenBy { it.recordedAtIso })
    }

    companion object {
        @Volatile private var instance: ScoringSessionRepository? = null
        fun get(context: Context): ScoringSessionRepository = instance ?: synchronized(this) {
            instance ?: ScoringSessionRepository(context.applicationContext).also { instance = it }
        }
    }
}
