package com.example.methodmesh.modules.time_tools.timing

import java.time.Duration

object TimeFormatting {
    fun duration(milliseconds: Long, centiseconds: Boolean = false): String {
        val safe = milliseconds.coerceAtLeast(0L)
        val hours = safe / 3_600_000L
        val minutes = (safe % 3_600_000L) / 60_000L
        val seconds = (safe % 60_000L) / 1_000L
        val cs = (safe % 1_000L) / 10L
        return when {
            hours > 0 && centiseconds -> "%02d:%02d:%02d.%02d".format(hours, minutes, seconds, cs)
            hours > 0 -> "%02d:%02d:%02d".format(hours, minutes, seconds)
            centiseconds -> "%02d:%02d.%02d".format(minutes, seconds, cs)
            else -> "%02d:%02d".format(minutes, seconds)
        }
    }

    fun duration(d: Duration): String = duration(d.toMillis())
}
