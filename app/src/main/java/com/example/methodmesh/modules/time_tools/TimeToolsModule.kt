package com.example.methodmesh.modules.time_tools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TimeToolsModule : MethodMeshModule {
    override val moduleId = "time_tools"
    override val displayName = "Time & Alarms"
    override val summary = "Timers, stopwatch, intervals, alarms, long-range countdowns and time calculations."
    override val iconKey = "schedule"

    override fun as100Methods() = listOf(
        As100DashboardMethod, As100CountdownMethod, As100StopwatchMethod, As100IntervalMethod,
        As100UntilMethod, As100AlarmMethod, As100ElapsedMethod, As100DurationCalculateMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("manage time tools", As100DashboardMethod.ID, "Open Time & Alarms"),
        RilBinding("start countdown", As100CountdownMethod.ID, "Start a countdown timer"),
        RilBinding("start stopwatch", As100StopwatchMethod.ID, "Start a stopwatch"),
        RilBinding("start interval timer", As100IntervalMethod.ID, "Start an interval timer"),
        RilBinding("count down to date and time", As100UntilMethod.ID, "Count down to a target date/time"),
        RilBinding("set alarm", As100AlarmMethod.ID, "Set a one-off or recurring alarm"),
        RilBinding("calculate elapsed time", As100ElapsedMethod.ID, "Calculate elapsed time"),
        RilBinding("calculate duration", As100DurationCalculateMethod.ID, "Add or subtract durations")
    )

    override fun capabilityScreens() = listOf(
        TimeToolsDashboardCapabilityScreen, CountdownMethodMeshCapabilityScreen, StopwatchMethodMeshCapabilityScreen,
        IntervalMethodMeshCapabilityScreen, UntilMethodMeshCapabilityScreen, AlarmMethodMeshCapabilityScreen,
        ElapsedMethodMeshCapabilityScreen, DurationCalculatorMethodMeshCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100CountdownMethod.ID to listOf(
            MethodSetting.IntSetting("duration_ms", "Duration", "Countdown duration in milliseconds.", "Timer", 300000, 1000, 31_536_000_000L.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(), 1000, "ms"),
            MethodSetting.ChoiceSetting("input_mode", "Input mode", "Duration or target date/time.", "Timer", "duration", listOf("duration", "date_time"))
        ) + reminderSettings(defaultOngoing = true),
        As100StopwatchMethod.ID to listOf(
            MethodSetting.TextSetting("label", "Stopwatch name", null, "Display", "Stopwatch"),
            MethodSetting.TextSetting("message", "Message / note", null, "Display", ""),
            MethodSetting.BooleanSetting("notify_ongoing", "Show live notification", "Show the count-up and controls in the notification shade.", "Notifications", true),
            MethodSetting.BooleanSetting("lock_screen", "Show on lock screen", null, "Notifications", true)
        ),
        As100IntervalMethod.ID to listOf(
            MethodSetting.TextSetting("phase_a_label", "First phase", null, "Intervals", "Work"),
            MethodSetting.IntSetting("phase_a_duration_ms", "First phase duration", null, "Intervals", 60000, 1000, Int.MAX_VALUE, 1000, "ms"),
            MethodSetting.TextSetting("phase_b_label", "Second phase", null, "Intervals", "Rest"),
            MethodSetting.IntSetting("phase_b_duration_ms", "Second phase duration", null, "Intervals", 60000, 1000, Int.MAX_VALUE, 1000, "ms"),
            MethodSetting.IntSetting("cycles", "Cycles", null, "Intervals", 1, 1, 10000)
        ) + reminderSettings(defaultOngoing = true),
        As100UntilMethod.ID to listOf(
            MethodSetting.ChoiceSetting("target_mode", "Target", null, "Target", "absolute", listOf("absolute", "anchor_offset_local_time")),
            MethodSetting.TextSetting("target_timestamp", "Target timestamp", "ISO-8601 instant.", "Target", ""),
            MethodSetting.TextSetting("anchor_timestamp", "Anchor timestamp", "Blank may be supplied as launch time by the preset.", "Target", ""),
            MethodSetting.IntSetting("day_offset", "Day offset", null, "Target", 0, 0, 3650, 1, "days"),
            MethodSetting.TextSetting("local_time", "Local time", "24-hour HH:MM.", "Target", "21:00"),
            MethodSetting.TextSetting("zone_id", "Timezone", "IANA timezone; blank uses device timezone.", "Target", "")
        ) + reminderSettings(defaultOngoing = false),
        As100AlarmMethod.ID to listOf(
            MethodSetting.TextSetting("alarm_time", "Alarm time", "24-hour HH:MM.", "Alarm", "09:00"),
            MethodSetting.TextSetting("alarm_date", "Date", "YYYY-MM-DD for a one-off alarm.", "Alarm", ""),
            MethodSetting.ChoiceSetting("alarm_repeat", "Repeat", null, "Alarm", "ONCE", listOf("ONCE", "DAILY", "WEEKDAYS", "WEEKENDS", "WEEKLY", "CUSTOM")),
            MethodSetting.MultiChoiceSetting("alarm_weekdays", "Days", "For weekly/custom alarms.", "Alarm", "", listOf("1", "2", "3", "4", "5", "6", "7"), "|", false),
            MethodSetting.TextSetting("zone_id", "Timezone", "Blank uses device timezone.", "Alarm", "")
        ) + reminderSettings(defaultOngoing = false),
        As100ElapsedMethod.ID to listOf(
            MethodSetting.TextSetting("start_timestamp", "Start timestamp", "ISO-8601 instant.", "Times", ""),
            MethodSetting.TextSetting("end_timestamp", "End timestamp", "ISO-8601 instant.", "Times", "")
        ),
        As100DurationCalculateMethod.ID to listOf(
            MethodSetting.IntSetting("a_ms", "First duration", null, "Calculation", 0, 0, Int.MAX_VALUE, 1000, "ms"),
            MethodSetting.IntSetting("b_ms", "Second duration", null, "Calculation", 0, 0, Int.MAX_VALUE, 1000, "ms"),
            MethodSetting.ChoiceSetting("operation", "Operation", null, "Calculation", "add", listOf("add", "subtract"))
        )
    )

    private fun reminderSettings(defaultOngoing: Boolean): List<MethodSetting> = listOf(
        MethodSetting.TextSetting("label", "Name", null, "Display", ""),
        MethodSetting.TextSetting("message", "Reminder message", "Text shown when the timer/alarm is due.", "Reminder", ""),
        MethodSetting.BooleanSetting("notify_ongoing", "Show live notification", "Useful for short timers; normally off for long/private reminders.", "Notifications", defaultOngoing),
        MethodSetting.BooleanSetting("lock_screen", "Show live timer on lock screen", null, "Notifications", defaultOngoing),
        MethodSetting.BooleanSetting("show_message_on_lock_screen", "Show reminder text on lock screen", "Off by default for privacy.", "Notifications", false),
        MethodSetting.BooleanSetting("alert_sound", "Sound", null, "Due alert", true),
        MethodSetting.BooleanSetting("alert_vibration", "Vibrate", null, "Due alert", true),
        MethodSetting.BooleanSetting("alert_lights", "Notification light", "Device/OEM dependent.", "Due alert", true),
        MethodSetting.BooleanSetting("alert_high_priority", "High-priority alert", null, "Due alert", true),
        MethodSetting.BooleanSetting("require_confirmation", "Require Done confirmation", "Follow-ups continue until Done is pressed.", "Follow-up", false),
        MethodSetting.IntSetting("follow_up_count", "Follow-up reminders", null, "Follow-up", 0, 0, 100),
        MethodSetting.IntSetting("follow_up_interval_minutes", "Follow-up interval", null, "Follow-up", 10, 1, 1440, 1, "min"),
        MethodSetting.IntSetting("snooze_minutes", "Snooze", "0 disables Snooze.", "Follow-up", 10, 0, 1440, 1, "min")
    )
}
