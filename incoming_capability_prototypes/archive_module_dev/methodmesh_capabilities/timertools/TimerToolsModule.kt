package com.example.methodmesh.modules.timertools

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TimerToolsModule : MethodMeshModule {
    override val moduleId = "timertools"
    override val displayName = "Timer tools"
    override val summary = "Stopwatch, laps, countdown, intervals and multiple simultaneous timers without background alarms."
    override val iconKey = "timer"
    override fun as100Methods() = listOf(As100TimerToolsMethod)
    override fun rilBindings() = listOf(RilBinding("open timer tools", As100TimerToolsMethod.ID, "Open the multi-timer workspace"))
    override fun capabilityScreens() = listOf(TimerToolsCapabilityScreen)
    override fun capabilitySettings() = mapOf(
        As100TimerToolsMethod.ID to listOf(
            MethodSetting.ChoiceSetting("default_mode", "Default timer mode", defaultValue = "stopwatch", choices = listOf("stopwatch", "countdown", "interval", "session")),
            MethodSetting.IntSetting("default_duration_seconds", "Default duration (seconds)", defaultValue = 60, minimum = 1, maximum = 86400),
            MethodSetting.IntSetting("default_interval_seconds", "Default interval (seconds)", defaultValue = 30, minimum = 1, maximum = 86400),
            MethodSetting.BooleanSetting("repeat_interval", "Repeat interval", defaultValue = false),
            MethodSetting.BooleanSetting("interactive_capture", "Interactive capture", defaultValue = true),
            MethodSetting.TextSetting("timers_json", "Timer snapshot JSON", defaultValue = "")
        )
    )
}
