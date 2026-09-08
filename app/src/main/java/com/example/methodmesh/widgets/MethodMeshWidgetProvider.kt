package com.example.methodmesh.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.methodmesh.R
import com.example.methodmesh.core.scheduling.SchedulerDispatchActivity
import com.example.methodmesh.core.scheduling.SchedulerRepository

class MethodMeshWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { MethodMeshWidgetRepository.delete(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_TAP) return
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val config = MethodMeshWidgetRepository.get(context, appWidgetId) ?: return
        when (config.targetType) {
            MethodMeshWidgetTargetType.PRESET -> launchPreset(context, config.targetId)
            MethodMeshWidgetTargetType.PROTOCOL -> launchProtocol(context, config.targetId)
            MethodMeshWidgetTargetType.SCHEDULE -> toggleSchedule(context, config.targetId)
        }
        updateAll(context)
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.example.methodmesh.widgets.ACTION_WIDGET_TAP"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MethodMeshWidgetProvider::class.java))
            ids.forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_methodmesh_trigger)
            val config = MethodMeshWidgetRepository.get(context, appWidgetId)
            if (config == null) {
                views.setTextViewText(R.id.widgetTitle, "MethodMesh")
                views.setTextViewText(R.id.widgetSubtitle, "Tap to set up")
                views.setOnClickPendingIntent(R.id.widgetRoot, configureIntent(context, appWidgetId))
            } else {
                views.setTextViewText(R.id.widgetTitle, MethodMeshWidgetRepository.resolveTitle(context, config))
                views.setTextViewText(R.id.widgetSubtitle, MethodMeshWidgetRepository.resolveSubtitle(context, config))
                views.setTextViewText(R.id.widgetIcon, MethodMeshWidgetRepository.resolveIconKey(context, config).emoji)
                views.setInt(R.id.widgetRoot, "setBackgroundColor", MethodMeshWidgetRepository.resolveColour(config).argb)
                views.setOnClickPendingIntent(R.id.widgetRoot, tapIntent(context, appWidgetId))
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun tapIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MethodMeshWidgetProvider::class.java)
                .setAction(ACTION_WIDGET_TAP)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            return PendingIntent.getBroadcast(
                context,
                "methodmesh_widget_tap_$appWidgetId".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun configureIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MethodMeshWidgetConfigureActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            return PendingIntent.getActivity(
                context,
                "methodmesh_widget_configure_$appWidgetId".hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun launchPreset(context: Context, presetId: String) {
            context.startActivity(Intent(context, SchedulerDispatchActivity::class.java)
                .putExtra("preset_id", presetId)
                .putExtra("transient_preset_run", true)
                .putExtra("finish_to_launcher", true)
                .putExtra("notification_kind", "widget")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        private fun launchProtocol(context: Context, protocolId: String) {
            context.startActivity(Intent(context, SchedulerDispatchActivity::class.java)
                .putExtra("protocol_id", protocolId)
                .putExtra("transient_protocol_run", true)
                .putExtra("finish_to_launcher", true)
                .putExtra("notification_kind", "widget")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        }

        private fun toggleSchedule(context: Context, scheduleId: String) {
            val schedule = SchedulerRepository.get(context, scheduleId) ?: return
            SchedulerRepository.setChainEnabled(context, schedule, !schedule.enabled)
            SchedulerRepository.recordEvent(context, schedule.id, if (schedule.enabled) "widget_paused" else "widget_activated")
        }

    }
}
