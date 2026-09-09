package com.example.methodmesh.modules.time_tools.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TimerControlReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_TIMER_ID) ?: return
        val runtime = TimeToolsTimerRuntime(context)
        when (intent.action) {
            ACTION_PAUSE -> runtime.pause(id)
            ACTION_RESUME -> runtime.resume(id)
            ACTION_LAP -> runtime.lap(id)
            ACTION_STOP -> runtime.stop(id)
            ACTION_ADD_MINUTE -> runtime.addMinute(id)
            ACTION_DONE -> runtime.done(id)
            ACTION_SNOOZE -> runtime.snooze(id)
        }
    }

    companion object {
        const val EXTRA_TIMER_ID = "timer_id"
        const val ACTION_PAUSE = "com.example.methodmesh.time.PAUSE"
        const val ACTION_RESUME = "com.example.methodmesh.time.RESUME"
        const val ACTION_LAP = "com.example.methodmesh.time.LAP"
        const val ACTION_STOP = "com.example.methodmesh.time.STOP"
        const val ACTION_ADD_MINUTE = "com.example.methodmesh.time.ADD_MINUTE"
        const val ACTION_DONE = "com.example.methodmesh.time.DONE"
        const val ACTION_SNOOZE = "com.example.methodmesh.time.SNOOZE"
    }
}
