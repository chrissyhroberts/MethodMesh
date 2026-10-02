package com.example.methodmesh.modules.externaldisplay

import android.app.Activity
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.example.methodmesh.R

object ExternalDisplayManager {
    const val ACTION_BLANK = "com.example.methodmesh.externaldisplay.BLANK"
    const val ACTION_STOP = "com.example.methodmesh.externaldisplay.STOP"
    private const val PREFS = "external_display"
    private const val KEY_DISPLAY_ID = "display_id"
    private const val KEY_BLANKED = "blanked"
    private const val KEY_ACTIVE = "active"
    private const val CHANNEL_ID = "external_display"
    private const val NOTIFICATION_ID = 24071

    fun displays(context: Context): List<ExternalDisplayInfo> {
        val manager = context.getSystemService(DisplayManager::class.java)
        return manager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.isValid }
            .map { it.toExternalDisplayInfo() }
    }

    fun currentState(context: Context): ExternalDisplayState {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val active = prefs.getBoolean(KEY_ACTIVE, false)
        val display = displays(context).firstOrNull { it.displayId == prefs.getInt(KEY_DISPLAY_ID, -1) }
            ?: displays(context).firstOrNull()
        return ExternalDisplayState(
            connected = display != null,
            display = display,
            mode = if (active) ExternalDisplayMode.PRESENTATION else null,
            blanked = prefs.getBoolean(KEY_BLANKED, false),
            message = when {
                display == null -> "No suitable external display detected. USB video also requires device hardware and an adapter that supports wired video output."
                active -> if (prefs.getBoolean(KEY_BLANKED, false)) "Presentation is active but blanked." else "Presenting MethodMesh content."
                else -> "External display available."
            }
        )
    }

    fun startPresentation(context: Context, displayId: Int): Boolean {
        val display = displays(context).firstOrNull { it.displayId == displayId } ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putInt(KEY_DISPLAY_ID, display.displayId)
            .putBoolean(KEY_BLANKED, false)
            .apply()
        val intent = Intent(context, ExternalDisplayActivity::class.java)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, options.toBundle())
        showNotification(context)
        return true
    }

    fun setBlanked(context: Context, blanked: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_BLANKED, blanked).apply()
        if (context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)) showNotification(context)
    }

    fun stop(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun systemDisplaySettingsIntent(): Intent = Intent(Settings.ACTION_CAST_SETTINGS)

    fun systemDisplaySettingsFallbackIntent(): Intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)

    private fun showNotification(context: Context) {
        if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED && android.os.Build.VERSION.SDK_INT >= 33) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "External display", NotificationManager.IMPORTANCE_LOW))
        val state = currentState(context)
        val open = Intent(context, ExternalDisplayActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val openPending = android.app.PendingIntent.getActivity(context, 1, open, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val blankIntent = Intent(context, ExternalDisplayControlReceiver::class.java).setAction(ACTION_BLANK)
        val stopIntent = Intent(context, ExternalDisplayControlReceiver::class.java).setAction(ACTION_STOP)
        val blankPending = android.app.PendingIntent.getBroadcast(context, 2, blankIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val stopPending = android.app.PendingIntent.getBroadcast(context, 3, stopIntent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MethodMesh presentation")
            .setContentText(state.display?.name ?: "External display")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, if (state.blanked) "Show" else "Blank", blankPending)
            .addAction(0, "Stop", stopPending)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}

class ExternalDisplayControlReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ExternalDisplayManager.ACTION_BLANK -> ExternalDisplayManager.setBlanked(context, !ExternalDisplayManager.currentState(context).blanked)
            ExternalDisplayManager.ACTION_STOP -> ExternalDisplayManager.stop(context)
        }
    }
}
