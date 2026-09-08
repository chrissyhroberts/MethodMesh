package com.example.methodmesh.modules.arcade

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/** Tiny generated retro sound layer; no bundled audio assets. */
object ArcadeSound {
    private val handler = Handler(Looper.getMainLooper())
    private fun tone(type:Int, duration:Int=70){ runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 72).also { tg -> tg.startTone(type,duration);handler.postDelayed({ runCatching{tg.release()} },(duration+30).toLong()) } } }
    fun score(enabled:Boolean){if(enabled)tone(ToneGenerator.TONE_DTMF_6,65)}
    fun turn(enabled:Boolean){if(enabled)tone(ToneGenerator.TONE_DTMF_2,35)}
    fun gameOver(enabled:Boolean){if(enabled)tone(ToneGenerator.TONE_PROP_NACK,160)}
}
