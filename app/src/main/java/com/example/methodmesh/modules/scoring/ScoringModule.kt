package com.example.methodmesh.modules.scoring

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ScoringModule : MethodMeshModule {
    override val moduleId = "scoring"
    override val displayName = "Scoring & counters"
    override val summary = "Persistent counters, tallies, sports scoring, rounds, targets and high-score records."
    override val iconKey = "tool"

    override fun as100Methods() = ScoringMethods.all

    override fun rilBindings() = listOf(
        RilBinding("count score", ScoringMethods.Counter.id, "Run a persistent multi-entity counter"),
        RilBinding("tally observations", ScoringMethods.Tally.id, "Run a fast categorical tally"),
        RilBinding("score match", ScoringMethods.Match.id, "Run a generic match scoreboard"),
        RilBinding("score rounds", ScoringMethods.Rounds.id, "Accumulate round scores"),
        RilBinding("race to score", ScoringMethods.RaceTo.id, "Run a race-to-target score"),
        RilBinding("score set match", ScoringMethods.SetMatch.id, "Run hierarchical set scoring"),
        RilBinding("score sport", ScoringMethods.Sports.id, "Run a ruleset-aware sports scoreboard"),
        RilBinding("record high score", ScoringMethods.HighScore.id, "Explicitly save a score record"),
        RilBinding("read score session", ScoringMethods.SessionRead.id, "Read persistent score state"),
        RilBinding("resume score session", ScoringMethods.SessionResume.id, "Resume a persistent score session"),
        RilBinding("finish score session", ScoringMethods.SessionFinish.id, "Finish and return a persistent score session")
    )

    override fun capabilityScreens() = ScoringCapabilityScreens.all

    override fun capabilitySettings(): Map<String, List<MethodSetting>> {
        val common = listOf(
            MethodSetting.TextSetting("title", "Session title", defaultValue = ""),
            MethodSetting.TextSetting("participant_names", "Participants", defaultValue = "Player 1|Player 2"),
            MethodSetting.IntSetting("participant_count", "Number of participants", defaultValue = 2, minimum = 1, maximum = 12),
            MethodSetting.IntSetting("starting_value", "Starting value", defaultValue = 0),
            MethodSetting.IntSetting("increment", "Standard increment", defaultValue = 1, minimum = 1),
            MethodSetting.BooleanSetting("allow_negative", "Allow negative scores", defaultValue = true),
            MethodSetting.BooleanSetting("highest_wins", "Highest score wins", defaultValue = true),
            MethodSetting.BooleanSetting("keep_screen_awake", "Keep screen awake while scoring", defaultValue = false)
        )
        val sports = common + listOf(
            MethodSetting.ChoiceSetting("ruleset", "Sport", defaultValue = "football", choices = SportsRules.all.map { it.id }),
            MethodSetting.IntSetting("target", "Points per game / target", defaultValue = 0, minimum = 0),
            MethodSetting.IntSetting("win_by", "Win by", defaultValue = 2, minimum = 1),
            MethodSetting.IntSetting("best_of", "Best of", defaultValue = 3, minimum = 1, maximum = 15)
        )
        val target = common + listOf(MethodSetting.IntSetting("target", "Target", defaultValue = 10, minimum = 1))
        val sessionId = listOf(MethodSetting.TextSetting("score_session_id", "Score session ID", defaultValue = ""))
        return mapOf(
            ScoringMethods.Counter.id to common,
            ScoringMethods.Tally.id to common,
            ScoringMethods.Match.id to common,
            ScoringMethods.Rounds.id to common,
            ScoringMethods.RaceTo.id to target,
            ScoringMethods.SetMatch.id to sports,
            ScoringMethods.Sports.id to sports,
            ScoringMethods.HighScore.id to listOf(
                MethodSetting.TextSetting("activity", "Activity", defaultValue = ""),
                MethodSetting.TextSetting("participant", "Participant", defaultValue = ""),
                MethodSetting.IntSetting("score", "Score", defaultValue = 0),
                MethodSetting.TextSetting("note", "Note", defaultValue = "")
            ),
            ScoringMethods.SessionRead.id to sessionId,
            ScoringMethods.SessionResume.id to sessionId,
            ScoringMethods.SessionFinish.id to sessionId
        )
    }
}
