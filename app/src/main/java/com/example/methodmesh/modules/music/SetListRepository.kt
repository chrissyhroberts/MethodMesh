package com.example.methodmesh.modules.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Active-session persistence for the music dashboards. Deliberately not a permanent results archive. */
class SetListRepository(context: Context) {
    data class Song(
        val id: String,
        val title: String,
        val durationSeconds: Int,
        val bpm: Double?,
        val key: String,
        val notes: String,
        val gapAfterSeconds: Int,
        val actualDurationSeconds: Int? = null,
    )

    data class RunState(
        val setName: String = "Tonight's set",
        val plannedStart: String = "",
        val curfew: String = "",
        val currentIndex: Int = 0,
        val running: Boolean = false,
        val startedAtEpochMs: Long = 0L,
        val accumulatedElapsedSeconds: Long = 0L,
        val songStartedAtEpochMs: Long = 0L,
    )

    data class PracticeState(
        val startedAtEpochMs: Long = 0L,
        val accumulatedElapsedSeconds: Long = 0L,
        val active: Boolean = true,
    )

    private val prefs = context.applicationContext.getSharedPreferences("methodmesh_music_setlist", Context.MODE_PRIVATE)

    fun load(): List<Song> {
        val raw = prefs.getString("songs", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val o = array.getJSONObject(i)
                    add(
                        Song(
                            id = o.optString("id", i.toString()),
                            title = o.optString("title", "Song ${i + 1}"),
                            durationSeconds = o.optInt("durationSeconds", 0),
                            bpm = if (o.has("bpm") && !o.isNull("bpm")) o.optDouble("bpm") else null,
                            key = o.optString("key", ""),
                            notes = o.optString("notes", ""),
                            gapAfterSeconds = o.optInt("gapAfterSeconds", 0),
                            actualDurationSeconds = if (o.has("actualDurationSeconds") && !o.isNull("actualDurationSeconds")) o.optInt("actualDurationSeconds") else null,
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun save(songs: List<Song>) {
        val array = JSONArray()
        songs.forEach { song ->
            array.put(
                JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("durationSeconds", song.durationSeconds)
                    if (song.bpm != null) put("bpm", song.bpm) else put("bpm", JSONObject.NULL)
                    put("key", song.key)
                    put("notes", song.notes)
                    put("gapAfterSeconds", song.gapAfterSeconds)
                    if (song.actualDurationSeconds != null) put("actualDurationSeconds", song.actualDurationSeconds) else put("actualDurationSeconds", JSONObject.NULL)
                }
            )
        }
        prefs.edit().putString("songs", array.toString()).apply()
    }

    fun loadRunState(): RunState = runCatching {
        val o = JSONObject(prefs.getString("run_state", "{}") ?: "{}")
        RunState(
            setName = o.optString("set_name", "Tonight's set"),
            plannedStart = o.optString("planned_start", ""),
            curfew = o.optString("curfew", ""),
            currentIndex = o.optInt("current_index", 0),
            running = o.optBoolean("running", false),
            startedAtEpochMs = o.optLong("started_at_epoch_ms", 0L),
            accumulatedElapsedSeconds = o.optLong("accumulated_elapsed_seconds", 0L),
            songStartedAtEpochMs = o.optLong("song_started_at_epoch_ms", 0L),
        )
    }.getOrDefault(RunState())

    fun saveRunState(state: RunState) {
        prefs.edit().putString(
            "run_state",
            JSONObject()
                .put("set_name", state.setName)
                .put("planned_start", state.plannedStart)
                .put("curfew", state.curfew)
                .put("current_index", state.currentIndex)
                .put("running", state.running)
                .put("started_at_epoch_ms", state.startedAtEpochMs)
                .put("accumulated_elapsed_seconds", state.accumulatedElapsedSeconds)
                .put("song_started_at_epoch_ms", state.songStartedAtEpochMs)
                .toString(),
        ).apply()
    }

    fun loadPracticeState(): PracticeState = runCatching {
        val o = JSONObject(prefs.getString("practice_state", "{}") ?: "{}")
        PracticeState(
            startedAtEpochMs = o.optLong("started_at_epoch_ms", 0L),
            accumulatedElapsedSeconds = o.optLong("accumulated_elapsed_seconds", 0L),
            active = o.optBoolean("active", true),
        )
    }.getOrDefault(PracticeState())

    fun savePracticeState(state: PracticeState) {
        prefs.edit().putString(
            "practice_state",
            JSONObject()
                .put("started_at_epoch_ms", state.startedAtEpochMs)
                .put("accumulated_elapsed_seconds", state.accumulatedElapsedSeconds)
                .put("active", state.active)
                .toString(),
        ).apply()
    }

    fun clearRunState() = prefs.edit().remove("run_state").apply()
    fun clearPracticeState() = prefs.edit().remove("practice_state").apply()

    fun clear() {
        prefs.edit().remove("songs").remove("run_state").apply()
    }
}
