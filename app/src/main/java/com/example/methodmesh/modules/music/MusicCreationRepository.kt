package com.example.methodmesh.modules.music

import android.content.Context
import org.json.JSONObject

internal class MusicCreationRepository(context: Context) {
    private val prefs = context.getSharedPreferences("methodmesh_music_creation", Context.MODE_PRIVATE)

    fun loadSketch(): Map<String,String> = runCatching {
        val o=JSONObject(prefs.getString("song_sketch", "{}") ?: "{}")
        listOf("title","bpm","key","progression","beat","bassline","melody","structure").associateWith { o.optString(it,"") }
    }.getOrDefault(emptyMap())

    fun saveSketch(values: Map<String,String>) {
        prefs.edit().putString("song_sketch", JSONObject(values).toString()).apply()
    }

    fun loadJam(): Map<String,String> = runCatching {
        val o=JSONObject(prefs.getString("jam", "{}") ?: "{}")
        listOf("bpm","kick","snare","hat","clap","captured_pattern").associateWith { o.optString(it,"") }
    }.getOrDefault(emptyMap())

    fun saveJam(values: Map<String,String>) { prefs.edit().putString("jam", JSONObject(values).toString()).apply() }
    fun clear() { prefs.edit().clear().apply() }
}
