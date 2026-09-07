package com.example.methodmesh.modules.music

import java.util.Locale

object As100PracticeDashboardMethod : MusicPureMethod(
    "music.practice_dashboard", "Practice dashboard", "Return the current practice-dashboard snapshot.",
    MusicFieldSet("music_practice_dashboard", listOf("bpm", "beats_per_bar", "subdivision", "reference_a4_hz", "exercise", "target_bpm", "beat_interval_ms", "elapsed_seconds", "elapsed_formatted")),
    listOf("bpm", "beats_per_bar", "subdivision", "reference_a4_hz", "exercise", "target_bpm", "elapsed_seconds"), "music.practice.dashboard"
) {
    const val ID = "music.practice_dashboard"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val bpm = MusicMethodSupport.setting(settings, "bpm", "100").toDouble(); require(bpm > 0)
        val beats = MusicMethodSupport.setting(settings, "beats_per_bar", "4").toInt(); val sub = MusicMethodSupport.setting(settings, "subdivision", "quarter")
        val a4 = MusicMethodSupport.setting(settings, "reference_a4_hz", "440").toDouble(); val exercise = MusicMethodSupport.setting(settings, "exercise")
        val target = MusicMethodSupport.setting(settings, "target_bpm", bpm.toString()).toDouble()
        val elapsed = MusicMethodSupport.setting(settings, "elapsed_seconds", "0").toLongOrNull()?.coerceAtLeast(0L) ?: 0L
        MusicMethodSupport.ok(fields, mapOf("bpm" to bpm, "beats_per_bar" to beats, "subdivision" to sub, "reference_a4_hz" to a4, "exercise" to exercise, "target_bpm" to target, "beat_interval_ms" to "%.3f".format(Locale.US, MusicAlgorithms.tempoIntervalMs(bpm)), "elapsed_seconds" to elapsed, "elapsed_formatted" to MusicAlgorithms.formatDuration(elapsed.toInt())), "Practice · ${"%.1f".format(Locale.US, bpm)} BPM · ${MusicAlgorithms.formatDuration(elapsed.toInt())}${if (exercise.isNotBlank()) " · $exercise" else ""}")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Invalid practice settings.") }
}

object As100PerformanceDashboardMethod : MusicPureMethod(
    "music.performance_dashboard", "Performance / set-list dashboard", "Return the current persistent performance-set snapshot.",
    MusicFieldSet("music_performance_dashboard", listOf("set_name", "planned_start", "curfew", "song_count", "planned_seconds", "planned_formatted", "completed_count", "current_index", "current_song", "running", "elapsed_seconds", "remaining_seconds", "variance_seconds", "songs_json")),
    listOf("set_name", "planned_start", "curfew", "song_count", "planned_seconds", "completed_count", "current_index", "current_song", "running", "elapsed_seconds", "remaining_seconds", "variance_seconds", "songs_json"), "music.performance.dashboard"
) {
    const val ID = "music.performance_dashboard"
    override fun calculate(settings: Map<String, String>) = runCatching {
        val setName = MusicMethodSupport.setting(settings, "set_name", "Set")
        val plannedStart = MusicMethodSupport.setting(settings, "planned_start")
        val curfew = MusicMethodSupport.setting(settings, "curfew")
        val count = MusicMethodSupport.setting(settings, "song_count", "0").toIntOrNull() ?: 0
        val planned = MusicMethodSupport.setting(settings, "planned_seconds", "0").toIntOrNull() ?: 0
        val completed = MusicMethodSupport.setting(settings, "completed_count", "0").toIntOrNull() ?: 0
        val currentIndex = MusicMethodSupport.setting(settings, "current_index", completed.toString()).toIntOrNull() ?: completed
        val currentSong = MusicMethodSupport.setting(settings, "current_song")
        val running = MusicMethodSupport.setting(settings, "running", "false").toBoolean()
        val elapsed = MusicMethodSupport.setting(settings, "elapsed_seconds", "0").toIntOrNull() ?: 0
        val remaining = MusicMethodSupport.setting(settings, "remaining_seconds", (planned - elapsed).coerceAtLeast(0).toString()).toIntOrNull() ?: 0
        val variance = MusicMethodSupport.setting(settings, "variance_seconds", "0").toIntOrNull() ?: 0
        val songsJson = MusicMethodSupport.setting(settings, "songs_json", "[]")
        MusicMethodSupport.ok(fields, mapOf("set_name" to setName, "planned_start" to plannedStart, "curfew" to curfew, "song_count" to count, "planned_seconds" to planned, "planned_formatted" to MusicAlgorithms.formatDuration(planned), "completed_count" to completed, "current_index" to currentIndex, "current_song" to currentSong, "running" to running, "elapsed_seconds" to elapsed, "remaining_seconds" to remaining, "variance_seconds" to variance, "songs_json" to songsJson), "$setName · $completed/$count · ${MusicAlgorithms.formatDuration(remaining)} remaining")
    }.getOrElse { MusicMethodSupport.fail(fields, it.message ?: "Unable to build performance snapshot.") }
}

object As100ReferenceDashboardMethod : MusicPureMethod(
    "music.reference_dashboard", "Music reference dashboard", "Return a key/scale/chord reference snapshot while remaining on the live native dashboard.",
    MusicFieldSet("music_reference_dashboard", listOf("root", "scale", "notes", "triads", "relative_note")),
    listOf("root", "scale", "prefer_flats"), "music.reference.dashboard"
) {
    const val ID = "music.reference_dashboard"
    override fun calculate(settings: Map<String, String>) = As100MusicReferenceMethod.calculate(settings).let { source ->
        if (source[As100MusicReferenceMethod.fields.status] != "succeeded") return@let MusicMethodSupport.fail(fields, source[As100MusicReferenceMethod.fields.error].orEmpty())
        val root = source[As100MusicReferenceMethod.fields.field("root")].orEmpty(); val scale = source[As100MusicReferenceMethod.fields.field("scale")].orEmpty()
        val notes = source[As100MusicReferenceMethod.fields.field("notes")].orEmpty(); val triads = source[As100MusicReferenceMethod.fields.field("triads")].orEmpty(); val relative = source[As100MusicReferenceMethod.fields.field("relative_note")].orEmpty()
        MusicMethodSupport.ok(fields, mapOf("root" to root, "scale" to scale, "notes" to notes, "triads" to triads, "relative_note" to relative), "$root ${scale.replace('_',' ')} · $notes")
    }
}
