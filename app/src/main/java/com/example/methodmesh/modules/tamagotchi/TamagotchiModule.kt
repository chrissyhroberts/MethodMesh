package com.example.methodmesh.modules.tamagotchi

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TamagotchiModule : MethodMeshModule {
    override val moduleId = "tamagotchi"
    override val displayName = "Tamagotchi Lab"
    override val summary = "A kawaii, configurable longitudinal care simulator whose complete intervention, observation, state and attention history becomes a teaching dataset."
    override val iconKey = "teaching"

    override fun as100Methods() = listOf(
        As100TamagotchiSessionMethod,
        As100TamagotchiInterveneMethod,
        As100TamagotchiObserveMethod,
        As100TamagotchiMeasureMethod,
        As100TamagotchiAdvanceMethod,
        As100TamagotchiHistoryMethod,
        As100TamagotchiAnalyseMethod,
        As100TamagotchiExportMethod,
        As100TamagotchiConfigureMethod
    )

    override fun capabilityScreens() = listOf(
        TamagotchiSessionCapabilityScreen,
        TamagotchiInterveneCapabilityScreen,
        TamagotchiObserveCapabilityScreen,
        TamagotchiMeasureCapabilityScreen,
        TamagotchiAdvanceCapabilityScreen,
        TamagotchiHistoryCapabilityScreen,
        TamagotchiAnalyseCapabilityScreen,
        TamagotchiExportCapabilityScreen,
        TamagotchiConfigureCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding("care for tamagotchi", As100TamagotchiSessionMethod.ID, "Open or resume the longitudinal creature care dashboard"),
        RilBinding("intervene with tamagotchi", As100TamagotchiInterveneMethod.ID, "Apply a configured care action"),
        RilBinding("observe tamagotchi", As100TamagotchiObserveMethod.ID, "Record visible creature phenotype"),
        RilBinding("measure tamagotchi", As100TamagotchiMeasureMethod.ID, "Record an authorised measurement"),
        RilBinding("advance tamagotchi time", As100TamagotchiAdvanceMethod.ID, "Advance classroom simulation time"),
        RilBinding("view tamagotchi history", As100TamagotchiHistoryMethod.ID, "View longitudinal care history"),
        RilBinding("analyse tamagotchi", As100TamagotchiAnalyseMethod.ID, "Analyse ended session with latent state revealed"),
        RilBinding("export tamagotchi session", As100TamagotchiExportMethod.ID, "Export the complete teaching dataset"),
        RilBinding("configure tamagotchi scenario", As100TamagotchiConfigureMethod.ID, "Inspect scenario configuration")
    )

    override fun capabilitySettings() = mapOf(
        As100TamagotchiSessionMethod.ID to listOf(
            MethodSetting.ChoiceSetting("creature", "Starter creature", "Choose the visual/personality phenotype. This does not change the scenario data model.", "Creature", "mossbit", TamagotchiCatalog.creatures.map { it.id }),
            MethodSetting.TextSetting("creature_name", "Creature name", "Optional student-facing name.", "Creature", ""),
            MethodSetting.ChoiceSetting("scenario_id", "Scenario", "The configurable care/simulation model.", "Scenario", "foundation_care", TamagotchiCatalog.scenarios.map { it.id }),
            MethodSetting.ChoiceSetting("simulation_mode", "Simulation clock", "Real time, accelerated, teacher-advanced classroom time, or action-driven turns.", "Time", "classroom", SimulationMode.entries.map { it.wire }),
            MethodSetting.FloatSetting("acceleration", "Acceleration", "Simulation minutes per real minute in accelerated mode.", "Time", 12f, .1f, 1440f, .1f, "×", 1),
            MethodSetting.ChoiceSetting("off_session_mode", "Between sessions", "How physiology progresses when the learner is not actively caring.", "Time", "sleep", OffSessionMode.entries.map { it.wire }),
            MethodSetting.BooleanSetting("notifications_enabled", "Care notifications", "Use a deliberately rate-limited attention policy.", "Attention", true),
            MethodSetting.ChoiceSetting("notification_profile", "Notification profile", "Preset intent; numeric caps below remain authoritative.", "Attention", "young_learner", listOf("young_learner", "classroom", "teen", "adult", "continuous_simulation")),
            MethodSetting.IntSetting("max_notifications_per_day", "Maximum notifications/day", "Ordinary notifications are suppressed after this cap.", "Attention", 2, 0, 12, 1, null),
            MethodSetting.IntSetting("minimum_notification_spacing_minutes", "Minimum spacing", "Minimum interval between delivered care notifications.", "Attention", 180, 0, 1440, 15, "min"),
            MethodSetting.TextSetting("school_start", "School starts", "HH:MM local time.", "Attention", "08:30"),
            MethodSetting.TextSetting("school_end", "School ends", "HH:MM local time.", "Attention", "15:30"),
            MethodSetting.TextSetting("quiet_start", "Quiet hours start", "HH:MM local time.", "Attention", "20:30"),
            MethodSetting.TextSetting("quiet_end", "Quiet hours end", "HH:MM local time.", "Attention", "07:00")
        ),
        As100TamagotchiInterveneMethod.ID to listOf(
            MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use the active Tamagotchi session.", "Run", ""),
            MethodSetting.TextSetting("action_id", "Action ID", "Configured action or generated option ID. Leave blank to choose at runtime.", "Run", "")
        ),
        As100TamagotchiObserveMethod.ID to listOf(
            MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use the active Tamagotchi session.", "Run", "")
        ),
        As100TamagotchiMeasureMethod.ID to listOf(
            MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use the active Tamagotchi session.", "Run", ""),
            MethodSetting.TextSetting("variable_id", "Measurement variable", "Scenario-authorised variable. Leave blank to choose at runtime.", "Run", "")
        ),
        As100TamagotchiAdvanceMethod.ID to listOf(
            MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use the active Tamagotchi session.", "Run", ""),
            MethodSetting.IntSetting("minutes", "Simulation minutes", "Manual/classroom time advance.", "Run", 60, 1, 10080, 1, "min")
        ),
        As100TamagotchiHistoryMethod.ID to listOf(MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use active session.", "Run", "")),
        As100TamagotchiAnalyseMethod.ID to listOf(MethodSetting.TextSetting("session_id", "Session ID", "Analysis unlocks only after the care period ends.", "Run", "")),
        As100TamagotchiExportMethod.ID to listOf(
            MethodSetting.TextSetting("session_id", "Session ID", "Leave blank to use active session.", "Run", ""),
            MethodSetting.BooleanSetting("include_analysis", "Include latent analysis", "Honoured only after the care period ends.", "Export", true)
        ),
        As100TamagotchiConfigureMethod.ID to listOf(
            MethodSetting.ChoiceSetting("scenario_id", "Scenario", "Inspect one built-in scenario configuration.", "Scenario", "foundation_care", TamagotchiCatalog.scenarios.map { it.id }),
            MethodSetting.BooleanSetting("reveal_latent", "Reveal latent configuration", "Teacher/analysis use only; false keeps hidden variables/effects out of the configuration payload.", "Scenario", false)
        )
    )
}
