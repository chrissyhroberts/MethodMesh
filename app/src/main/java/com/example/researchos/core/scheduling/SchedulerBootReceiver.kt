package com.example.researchos.core.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms persisted schedules after boot and system-clock changes. */
class SchedulerBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.intent.action.QUICKBOOT_POWERON" -> SchedulerRepository.rescheduleAll(context)
        }
    }
}
