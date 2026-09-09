package com.example.methodmesh.modules.media

import android.content.Context
import android.media.MediaRecorder
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Module-local, no-user-sign-in audio discovery boundary.
 *
 * The default adapter is AudD standard recognition. It uploads only the short
 * temporary clip the operator intentionally records. The API token is stored in
 * this module's private SharedPreferences and is never projected into presets,
 * ODK, protocol settings, results, logs or exported definitions.
 */
object MediaAudioDiscovery {
    const val PROVIDER_AUDD = "audd"
    const val FREE_TEST_TOKEN = "test"
    private const val PREFS = "methodmesh_media_private"
    private const val KEY_AUDD_TOKEN = "audd_api_token"
    private const val AUDD_URL = "https://api.audd.io/"

    data class Match(
        val matched: Boolean,
        val artist: String = "",
        val title: String = "",
        val album: String = "",
        val releaseDate: String = "",
        val songLink: String = "",
        val rawResult: String = "",
        val provider: String = PROVIDER_AUDD
    )

    fun hasToken(context: Context): Boolean = true

    fun usingFreeTestService(context: Context): Boolean = token(context).isBlank()

    fun token(context: Context): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(KEY_AUDD_TOKEN, "").orEmpty().trim()

    fun saveToken(context: Context, value: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_AUDD_TOKEN, value.trim()).apply()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_AUDD_TOKEN).apply()
    }

    fun newClipFile(context: Context): File = File.createTempFile("media-discovery-", ".m4a", context.cacheDir)

    @Suppress("DEPRECATION")
    fun startRecorder(file: File): MediaRecorder = MediaRecorder().apply {
        setAudioSource(MediaRecorder.AudioSource.MIC)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        setAudioEncodingBitRate(128_000)
        setAudioSamplingRate(44_100)
        setOutputFile(file.absolutePath)
        prepare()
        start()
    }

    fun stopRecorder(recorder: MediaRecorder?) {
        if (recorder == null) return
        runCatching { recorder.stop() }
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
    }

    fun identify(context: Context, clip: File, tokenOverride: String = ""): Match {
        require(clip.exists() && clip.length() > 0L) { "Audio sample is empty." }
        val apiToken = tokenOverride.trim().ifBlank { token(context).ifBlank { FREE_TEST_TOKEN } }

        val boundary = "----MethodMeshMedia${System.currentTimeMillis()}"
        val connection = (URL(AUDD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            useCaches = false
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        try {
            DataOutputStream(connection.outputStream).use { out ->
                fun field(name: String, value: String) {
                    out.writeBytes("--$boundary\r\n")
                    out.writeBytes("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
                    out.write(value.toByteArray(Charsets.UTF_8))
                    out.writeBytes("\r\n")
                }
                field("api_token", apiToken)
                field("return", "musicbrainz")
                out.writeBytes("--$boundary\r\n")
                out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"sample.m4a\"\r\n")
                out.writeBytes("Content-Type: audio/mp4\r\n\r\n")
                BufferedInputStream(clip.inputStream()).use { input ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        out.write(buffer, 0, n)
                    }
                }
                out.writeBytes("\r\n--$boundary--\r\n")
                out.flush()
            }

            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Audio identification service returned HTTP $code.")

            val root = JSONObject(body)
            if (root.optString("status") != "success") {
                val err = root.optJSONObject("error")?.optString("error_message").orEmpty()
                error(err.ifBlank { "Audio identification service returned an error." })
            }
            val result = root.optJSONObject("result") ?: return Match(matched = false, rawResult = body)
            return Match(
                matched = true,
                artist = result.optString("artist"),
                title = result.optString("title"),
                album = result.optString("album"),
                releaseDate = result.optString("release_date"),
                songLink = result.optString("song_link"),
                rawResult = body
            )
        } finally {
            connection.disconnect()
        }
    }
}
