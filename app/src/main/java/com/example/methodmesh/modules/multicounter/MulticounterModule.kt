package com.example.methodmesh.modules.multicounter

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object MulticounterModule : MethodMeshModule {
    override val moduleId = "multicounter"
    override val displayName = "Multi-counter"
    override val summary = "Track several named counts or scores with optional independent and staggered timers in one compact board."
    override val iconKey = "tool"

    override fun as100Methods() = listOf(As100MulticounterMethod)

    override fun rilBindings() = listOf(
        RilBinding("track multiple counters", As100MulticounterMethod.ID, "Open a compact board for several counters"),
        RilBinding("tally scores", As100MulticounterMethod.ID, "Track simple scores without sport-specific rules"),
        RilBinding("time multiple entities", As100MulticounterMethod.ID, "Run independent or staggered entity timers")
    )

    override fun capabilityScreens() = listOf(MulticounterCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100MulticounterMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "mode",
                label = "Board mode",
                defaultValue = "count",
                choices = listOf("count", "time", "count_and_time"),
                group = "Session"
            ),
            MethodSetting.IntSetting(
                id = "entity_count",
                label = "Number of entities",
                defaultValue = 2,
                minimum = 1,
                maximum = 24,
                group = "Session"
            ),
            MethodSetting.TextSetting(
                id = "entity_names",
                label = "Entity names",
                description = "Pipe-separated names, for example Alice|Bob|Charlie.",
                defaultValue = "",
                group = "Session"
            ),
            MethodSetting.IntSetting(
                id = "counter_start",
                label = "Starting value",
                defaultValue = 0,
                minimum = -1_000_000,
                maximum = 1_000_000,
                group = "Counter"
            ),
            MethodSetting.IntSetting(
                id = "step",
                label = "Counter step",
                defaultValue = 1,
                minimum = 1,
                maximum = 1_000_000,
                group = "Counter"
            ),
            MethodSetting.BooleanSetting(
                id = "allow_negative",
                label = "Allow negative values",
                defaultValue = false,
                group = "Counter"
            ),
            MethodSetting.BooleanSetting(
                id = "show_total",
                label = "Show total",
                defaultValue = false,
                group = "Counter"
            ),
            MethodSetting.BooleanSetting(
                id = "show_leader",
                label = "Show leader",
                defaultValue = false,
                group = "Counter"
            ),
            MethodSetting.ChoiceSetting(
                id = "timer_mode",
                label = "Timer mode",
                defaultValue = "stopwatch",
                choices = listOf("stopwatch", "countdown"),
                group = "Timer"
            ),
            MethodSetting.IntSetting(
                id = "countdown_seconds",
                label = "Countdown length",
                defaultValue = 60,
                minimum = 1,
                maximum = 604_800,
                unit = "s",
                group = "Timer"
            ),
            MethodSetting.IntSetting(
                id = "stagger_seconds",
                label = "Start stagger",
                description = "Start-all delay between consecutive entities. Zero starts simultaneously.",
                defaultValue = 0,
                minimum = 0,
                maximum = 3_600,
                unit = "s",
                group = "Timer"
            ),
            MethodSetting.ChoiceSetting(
                id = "timer_precision",
                label = "Timer precision",
                defaultValue = "tenths",
                choices = listOf("seconds", "tenths"),
                group = "Timer"
            )
        )
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Three-person tally",
            ril = "WHAT; track multiple counters; RESULT; return counter_result, counter_audit_json; format json",
            notes = "Use mode=count and entity_names=Alice|Bob|Charlie."
        ),
        ModuleExample(
            title = "Simple scoreboard",
            ril = "WHAT; tally scores; RESULT; return counter_result; format json",
            notes = "Generic tallying only; sport-specific scoring rules belong in the scoring module."
        ),
        ModuleExample(
            title = "Staggered stopwatch board",
            ril = "WHAT; time multiple entities; RESULT; return counter_result, counter_entities_json; format json",
            notes = "Use mode=time and a non-zero stagger_seconds value."
        )
    )
}
