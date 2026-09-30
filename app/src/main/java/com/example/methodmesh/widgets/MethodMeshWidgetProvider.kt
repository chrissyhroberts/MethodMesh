package com.example.methodmesh.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.example.methodmesh.R
import com.example.methodmesh.core.scheduling.SchedulePlanRuntime
import com.example.methodmesh.core.scheduling.SchedulePlanStore
import com.example.methodmesh.core.scheduling.SchedulerDispatchActivity

class MethodMeshWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { MethodMeshWidgetRepository.delete(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val config = MethodMeshWidgetRepository.get(context, appWidgetId) ?: return

        when (intent.action) {
            ACTION_WIDGET_TAP -> {
                if (config.targetType != MethodMeshWidgetTargetType.BUNDLE && config.targetId.isNotBlank()) {
                    execute(
                        context,
                        MethodMeshWidgetTarget(config.targetType, config.targetId)
                    )
                }
            }

            ACTION_BUNDLE_TARGET -> {
                if (config.targetType != MethodMeshWidgetTargetType.BUNDLE) return

                val type = runCatching {
                    MethodMeshWidgetTargetType.valueOf(
                        intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty()
                    )
                }.getOrNull() ?: return
                if (type == MethodMeshWidgetTargetType.BUNDLE) return

                val targetId = intent.getStringExtra(EXTRA_TARGET_ID).orEmpty()
                val target = config.bundleTargets.firstOrNull {
                    it.type == type && it.id == targetId
                } ?: return

                if (MethodMeshWidgetRepository.targetExists(context, target)) {
                    execute(context, target)
                }
            }

            else -> return
        }

        updateAll(context)
    }

    companion object {
        const val ACTION_WIDGET_TAP = "com.example.methodmesh.widgets.ACTION_WIDGET_TAP"
        const val ACTION_BUNDLE_TARGET = "com.example.methodmesh.widgets.ACTION_BUNDLE_TARGET"
        const val EXTRA_TARGET_TYPE = "methodmesh_widget_target_type"
        const val EXTRA_TARGET_ID = "methodmesh_widget_target_id"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(
                ComponentName(context, MethodMeshWidgetProvider::class.java)
            ).forEach { updateWidget(context, manager, it) }
        }

        fun updateWidget(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val config = MethodMeshWidgetRepository.get(context, appWidgetId)
            val views = when {
                config == null -> unconfiguredViews(context, appWidgetId)
                config.targetType == MethodMeshWidgetTargetType.BUNDLE -> {
                    bundleCoverViews(context, appWidgetId, config)
                }
                else -> singleViews(context, appWidgetId, config)
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        private fun unconfiguredViews(context: Context, appWidgetId: Int): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_methodmesh_trigger).apply {
                setTextViewText(R.id.widgetIcon, "🕸️")
                setTextViewText(R.id.widgetTitle, "MethodMesh")
                setTextViewText(R.id.widgetSubtitle, "Tap to set up")
                setViewVisibility(R.id.widgetSubtitle, View.VISIBLE)
                setInt(
                    R.id.widgetBackground,
                    "setBackgroundResource",
                    R.drawable.widget_bg_slate_solid
                )
                setInt(
                    R.id.widgetContentPlate,
                    "setBackgroundResource",
                    R.drawable.widget_content_plate_clear
                )
                setOnClickPendingIntent(
                    R.id.widgetRoot,
                    configureIntent(context, appWidgetId)
                )
            }
        }

        private fun singleViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            return if (config.appearance == MethodMeshWidgetAppearance.SOLID) {
                solidSingleViews(context, appWidgetId, config)
            } else {
                shortcutSingleViews(context, appWidgetId, config)
            }
        }

        private fun solidSingleViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            val subtitle = MethodMeshWidgetRepository.resolveSubtitle(context, config)
            return RemoteViews(context.packageName, R.layout.widget_methodmesh_trigger).apply {
                setInt(
                    R.id.widgetBackground,
                    "setBackgroundResource",
                    solidBackgroundResource(config.colour)
                )
                setInt(
                    R.id.widgetContentPlate,
                    "setBackgroundResource",
                    R.drawable.widget_content_plate_clear
                )
                setTextViewText(
                    R.id.widgetIcon,
                    MethodMeshWidgetRepository.resolveIconKey(context, config).emoji
                )
                setTextViewText(
                    R.id.widgetTitle,
                    MethodMeshWidgetRepository.resolveTitle(context, config)
                )
                setTextViewText(R.id.widgetSubtitle, subtitle)
                setViewVisibility(
                    R.id.widgetSubtitle,
                    if (subtitle.isBlank()) View.GONE else View.VISIBLE
                )

                val foreground = foregroundColour(config.colour)
                setTextColor(R.id.widgetTitle, foreground)
                setTextColor(R.id.widgetSubtitle, foreground)
                setOnClickPendingIntent(R.id.widgetRoot, tapIntent(context, appWidgetId))
            }
        }

        private fun shortcutSingleViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            val subtitle = MethodMeshWidgetRepository.resolveSubtitle(context, config)
            return RemoteViews(context.packageName, R.layout.widget_methodmesh_shortcut).apply {
                setInt(
                    R.id.widgetShortcutCircle,
                    "setBackgroundResource",
                    shortcutCircleResource(config)
                )
                setTextViewText(
                    R.id.widgetShortcutCircle,
                    MethodMeshWidgetRepository.resolveIconKey(context, config).emoji
                )
                setTextViewText(
                    R.id.widgetTitle,
                    MethodMeshWidgetRepository.resolveTitle(context, config)
                )
                setTextViewText(R.id.widgetSubtitle, subtitle)
                setViewVisibility(
                    R.id.widgetSubtitle,
                    if (subtitle.isBlank()) View.GONE else View.VISIBLE
                )
                setOnClickPendingIntent(R.id.widgetRoot, tapIntent(context, appWidgetId))
            }
        }

        private fun bundleCoverViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            return if (config.appearance == MethodMeshWidgetAppearance.SOLID) {
                solidBundleCoverViews(context, appWidgetId, config)
            } else {
                shortcutBundleCoverViews(context, appWidgetId, config)
            }
        }

        private fun solidBundleCoverViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_methodmesh_bundle_cover).apply {
                setInt(
                    R.id.widgetBackground,
                    "setBackgroundResource",
                    solidBackgroundResource(config.colour)
                )
                setInt(
                    R.id.widgetContentPlate,
                    "setBackgroundResource",
                    R.drawable.widget_content_plate_clear
                )
                setTextViewText(
                    R.id.widgetIcon,
                    MethodMeshWidgetRepository.resolveIconKey(context, config).emoji
                )
                setTextViewText(
                    R.id.widgetTitle,
                    MethodMeshWidgetRepository.resolveTitle(context, config)
                )
                setTextViewText(R.id.bundleCountBadge, config.bundleTargets.size.toString())
                setTextColor(R.id.widgetTitle, foregroundColour(config.colour))
                setOnClickPendingIntent(
                    R.id.widgetRoot,
                    bundlePopupIntent(context, appWidgetId)
                )
            }
        }

        private fun shortcutBundleCoverViews(
            context: Context,
            appWidgetId: Int,
            config: MethodMeshWidgetConfig
        ): RemoteViews {
            return RemoteViews(
                context.packageName,
                R.layout.widget_methodmesh_bundle_shortcut
            ).apply {
                setInt(
                    R.id.widgetShortcutCircle,
                    "setBackgroundResource",
                    shortcutCircleResource(config)
                )
                setTextViewText(
                    R.id.widgetShortcutCircle,
                    MethodMeshWidgetRepository.resolveIconKey(context, config).emoji
                )
                setTextViewText(
                    R.id.widgetTitle,
                    MethodMeshWidgetRepository.resolveTitle(context, config)
                )
                setTextViewText(R.id.bundleCountBadge, config.bundleTargets.size.toString())
                setOnClickPendingIntent(
                    R.id.widgetRoot,
                    bundlePopupIntent(context, appWidgetId)
                )
            }
        }

        private fun solidBackgroundResource(colour: MethodMeshWidgetColour): Int = when (colour) {
            MethodMeshWidgetColour.TEAL -> R.drawable.widget_bg_teal_solid
            MethodMeshWidgetColour.BLUE -> R.drawable.widget_bg_blue_solid
            MethodMeshWidgetColour.PURPLE -> R.drawable.widget_bg_purple_solid
            MethodMeshWidgetColour.AMBER -> R.drawable.widget_bg_amber_solid
            MethodMeshWidgetColour.CORAL -> R.drawable.widget_bg_coral_solid
            MethodMeshWidgetColour.SLATE -> R.drawable.widget_bg_slate_solid
            MethodMeshWidgetColour.DARK -> R.drawable.widget_bg_dark_solid
        }

        private fun shortcutCircleResource(config: MethodMeshWidgetConfig): Int {
            val frosted = config.appearance == MethodMeshWidgetAppearance.FROSTED
            return when (config.colour) {
                MethodMeshWidgetColour.TEAL -> if (frosted) {
                    R.drawable.widget_shortcut_teal_frosted
                } else {
                    R.drawable.widget_shortcut_teal_compact
                }
                MethodMeshWidgetColour.BLUE -> if (frosted) {
                    R.drawable.widget_shortcut_blue_frosted
                } else {
                    R.drawable.widget_shortcut_blue_compact
                }
                MethodMeshWidgetColour.PURPLE -> if (frosted) {
                    R.drawable.widget_shortcut_purple_frosted
                } else {
                    R.drawable.widget_shortcut_purple_compact
                }
                MethodMeshWidgetColour.AMBER -> if (frosted) {
                    R.drawable.widget_shortcut_amber_frosted
                } else {
                    R.drawable.widget_shortcut_amber_compact
                }
                MethodMeshWidgetColour.CORAL -> if (frosted) {
                    R.drawable.widget_shortcut_coral_frosted
                } else {
                    R.drawable.widget_shortcut_coral_compact
                }
                MethodMeshWidgetColour.SLATE -> if (frosted) {
                    R.drawable.widget_shortcut_slate_frosted
                } else {
                    R.drawable.widget_shortcut_slate_compact
                }
                MethodMeshWidgetColour.DARK -> if (frosted) {
                    R.drawable.widget_shortcut_dark_frosted
                } else {
                    R.drawable.widget_shortcut_dark_compact
                }
            }
        }

        private fun foregroundColour(colour: MethodMeshWidgetColour): Int =
            if (colour == MethodMeshWidgetColour.DARK) {
                0xFFFFFFFF.toInt()
            } else {
                0xFF302A28.toInt()
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

        private fun bundlePopupIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, MethodMeshWidgetBundleActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            return PendingIntent.getActivity(
                context,
                "methodmesh_widget_bundle_$appWidgetId".hashCode(),
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

        private fun execute(context: Context, target: MethodMeshWidgetTarget) {
            when (target.type) {
                MethodMeshWidgetTargetType.PRESET -> {
                    context.startActivity(
                        Intent(context, SchedulerDispatchActivity::class.java)
                            .putExtra("preset_id", target.id)
                            .putExtra("transient_preset_run", true)
                            .putExtra("finish_to_launcher", true)
                            .putExtra("notification_kind", "widget")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }

                MethodMeshWidgetTargetType.PROTOCOL -> {
                    context.startActivity(
                        Intent(context, SchedulerDispatchActivity::class.java)
                            .putExtra("protocol_id", target.id)
                            .putExtra("transient_protocol_run", true)
                            .putExtra("finish_to_launcher", true)
                            .putExtra("notification_kind", "widget")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }

                MethodMeshWidgetTargetType.SCHEDULE -> toggleSchedule(context, target.id)
                MethodMeshWidgetTargetType.BUNDLE -> Unit
            }
        }

        private fun toggleSchedule(context: Context, scheduleId: String) {
            val plan = SchedulePlanStore.plan(context, scheduleId) ?: return
            val running = SchedulePlanStore.allInstances(context)
                .filter { it.planId == plan.id && it.stoppedAt == null }

            if (running.isEmpty()) {
                SchedulePlanRuntime.start(context, plan)
            } else {
                running.forEach { SchedulePlanRuntime.cancel(context, it) }
                SchedulePlanStore.removeInstancesForPlan(context, plan.id)
            }
        }
    }
}
