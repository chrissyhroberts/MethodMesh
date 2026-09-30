package com.example.methodmesh.modules.display

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

/** Display-oriented presentation capabilities: signs, countdowns and clocks. */
object DisplayModule : MethodMeshModule {
    override val moduleId = "display"
    override val displayName = "Display"
    override val version = "0.4.0"
    override val summary = "Turn the device into a large sign, countdown timer, debate timer or clock."
    override val iconKey = "display"

    override fun as100Methods() = listOf(
        DisplayShowMethod,
        DisplayTimerMethod,
        DisplayDebateTimerMethod,
        DisplayClockMethod
    )

    override fun capabilityScreens() = listOf(
        DisplayCapabilityScreen(),
        DisplayTimerCapabilityScreen(),
        DisplayDebateTimerCapabilityScreen(),
        DisplayClockCapabilityScreen()
    )

    override fun rilBindings() = listOf(
        RilBinding("show display", DisplayShowMethod.ID, "Show content prominently on a display"),
        RilBinding("show sign", DisplayShowMethod.ID, "Show content prominently on a display"),
        RilBinding("show message", DisplayShowMethod.ID, "Show a large message"),
        RilBinding("scroll message", DisplayShowMethod.ID, "Show a scrolling message"),
        RilBinding("flash message", DisplayShowMethod.ID, "Show a safely rate-limited flashing message"),
        RilBinding("show countdown", DisplayTimerMethod.ID, "Show a large countdown timer"),
        RilBinding("count down", DisplayTimerMethod.ID, "Show a large countdown timer"),
        RilBinding("show debate timer", DisplayDebateTimerMethod.ID, "Show a green/yellow/red debate countdown"),
        RilBinding("start debate timer", DisplayDebateTimerMethod.ID, "Start a green/yellow/red debate countdown"),
        RilBinding("show clock", DisplayClockMethod.ID, "Show a large local clock")
    )

    override fun capabilitySettings() = mapOf(
        DisplayShowMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "content_kind",
                label = "Content",
                defaultValue = "text",
                choices = listOf("text", "media")
            ),
            MethodSetting.TextSetting(
                id = "text",
                label = "Message / emoji",
                defaultValue = DisplayShowMethod.DEFAULT_TEXT
            ),
            MethodSetting.TextSetting(
                id = "media_uri",
                label = "Image / GIF URI",
                description = "Android content URI for an image, animated GIF or animated WebP.",
                defaultValue = ""
            ),
            MethodSetting.ChoiceSetting(
                id = "motion",
                label = "Motion",
                defaultValue = "still",
                choices = listOf("still", "scroll", "flash", "pulse")
            ),
            MethodSetting.ChoiceSetting(
                id = "theme",
                label = "Appearance",
                defaultValue = "white_on_black",
                choices = listOf(
                    "white_on_black",
                    "black_on_white",
                    "black_on_yellow",
                    "neon",
                    "rainbow",
                    "pride",
                    "fabulous"
                )
            ),
            MethodSetting.ChoiceSetting(
                id = "alignment",
                label = "Alignment",
                defaultValue = "center",
                choices = listOf("start", "center", "end")
            ),
            MethodSetting.ChoiceSetting(
                id = "content_rotation",
                label = "Content rotation",
                defaultValue = "0",
                choices = listOf("0", "90", "180", "270")
            ),
            MethodSetting.FloatSetting(
                id = "text_scale",
                label = "Text size",
                description = "Fraction of the largest non-clipping fitted text size.",
                defaultValue = 1f,
                minimum = 0.2f,
                maximum = 1f,
                step = 0.05f
            ),
            MethodSetting.FloatSetting(
                id = "speed",
                label = "Motion speed",
                description = "Scroll speed is linear and orientation-independent (1.0 = 180 dp/s); pulse uses the same multiplier scale. Flash is always capped at 2 Hz.",
                defaultValue = 1f,
                minimum = 0.25f,
                maximum = 4f,
                step = 0.25f
            ),
            MethodSetting.ChoiceSetting(
                id = "direction",
                label = "Scroll direction",
                defaultValue = "left",
                choices = listOf("left", "right")
            ),
            MethodSetting.BooleanSetting(
                id = "high_brightness",
                label = "Use high brightness while showing",
                defaultValue = true
            )
        ),
        DisplayTimerMethod.ID to listOf(
            MethodSetting.IntSetting(
                id = "duration_seconds",
                label = "Duration",
                description = "Countdown duration in seconds.",
                defaultValue = DisplayTimerMethod.DEFAULT_DURATION_SECONDS,
                minimum = 1,
                maximum = 359999,
                unit = "seconds"
            ),
            MethodSetting.BooleanSetting(
                id = "beep",
                label = "Beep at zero",
                defaultValue = true
            ),
            MethodSetting.ChoiceSetting(
                id = "theme",
                label = "Appearance",
                defaultValue = "white_on_black",
                choices = listOf("white_on_black", "black_on_white", "black_on_yellow")
            ),
            MethodSetting.BooleanSetting(
                id = "high_brightness",
                label = "Use high brightness while showing",
                defaultValue = true
            )
        ),
        DisplayDebateTimerMethod.ID to listOf(
            MethodSetting.IntSetting(
                id = "duration_seconds",
                label = "Total duration",
                description = "Total debate countdown duration in seconds.",
                defaultValue = DisplayDebateTimerMethod.DEFAULT_DURATION_SECONDS,
                minimum = 1,
                maximum = 359999,
                unit = "seconds"
            ),
            MethodSetting.IntSetting(
                id = "warning_seconds",
                label = "Yellow warning",
                description = "Switch from green to yellow when this many seconds remain.",
                defaultValue = DisplayDebateTimerMethod.DEFAULT_WARNING_SECONDS,
                minimum = 1,
                maximum = 359999,
                unit = "seconds"
            ),
            MethodSetting.BooleanSetting(
                id = "warning_beep",
                label = "Single beep at warning",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = "beep",
                label = "Alarm beeps at zero",
                defaultValue = true
            ),
            MethodSetting.BooleanSetting(
                id = "high_brightness",
                label = "Use high brightness while showing",
                defaultValue = true
            )
        ),
        DisplayClockMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "clock_format",
                label = "Time format",
                defaultValue = "HH:MM",
                choices = listOf("HH:MM", "HH:MM:SS")
            ),
            MethodSetting.BooleanSetting(
                id = "show_date",
                label = "Show date above clock",
                defaultValue = false
            ),
            MethodSetting.ChoiceSetting(
                id = "theme",
                label = "Appearance",
                defaultValue = "white_on_black",
                choices = listOf("white_on_black", "black_on_white", "black_on_yellow")
            ),
            MethodSetting.BooleanSetting(
                id = "high_brightness",
                label = "Use high brightness while showing",
                defaultValue = true
            )
        )
    )
}
