package com.example.methodmesh.modules.gamedeck

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Tiny asset-free retro sound palette for GameDeck.
 *
 * Uses Android's ToneGenerator so the capability does not ship media files.
 * Calls are deliberately short and non-blocking; sound never drives game state.
 */
object GameDeckSound {
    private val tone by lazy { ToneGenerator(AudioManager.STREAM_MUSIC, 72) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }

    fun pickup(enabled: Boolean) = play(enabled, ToneGenerator.TONE_DTMF_0, 55)
    fun drop(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_BEEP, 80)
    fun move(enabled: Boolean) = play(enabled, ToneGenerator.TONE_DTMF_6, 55)
    fun roll(enabled: Boolean) {
        if (!enabled) return
        sequence(
            ToneGenerator.TONE_DTMF_2 to 45,
            ToneGenerator.TONE_DTMF_5 to 45,
            ToneGenerator.TONE_DTMF_8 to 55
        )
    }
    fun cpu(enabled: Boolean) = play(enabled, ToneGenerator.TONE_DTMF_4, 70)
    fun error(enabled: Boolean) = play(enabled, ToneGenerator.TONE_PROP_NACK, 110)
    fun win(enabled: Boolean) {
        if (!enabled) return
        sequence(
            ToneGenerator.TONE_DTMF_1 to 70,
            ToneGenerator.TONE_DTMF_3 to 70,
            ToneGenerator.TONE_DTMF_6 to 80,
            ToneGenerator.TONE_DTMF_9 to 120
        )
    }

    private fun play(enabled: Boolean, toneType: Int, durationMs: Int) {
        if (!enabled) return
        runCatching { tone.startTone(toneType, durationMs) }
    }

    private fun sequence(vararg notes: Pair<Int, Int>) {
        var delayMs = 0L
        notes.forEach { (toneType, durationMs) ->
            handler.postDelayed({ runCatching { tone.startTone(toneType, durationMs) } }, delayMs)
            delayMs += durationMs + 28L
        }
    }
}
