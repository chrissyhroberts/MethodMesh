package com.example.methodmesh.modules.scoring

import org.json.JSONObject

data class ScoreAction(val id: String, val label: String, val delta: Int? = null)

data class SportsRule(
    val id: String,
    val displayName: String,
    val actions: List<ScoreAction>,
    val structured: Boolean = false
)

object SportsRules {
    val all = listOf(
        SportsRule("football", "Football", listOf(ScoreAction("goal", "Goal +1", 1))),
        SportsRule("basketball", "Basketball", listOf(ScoreAction("ft", "+1", 1), ScoreAction("two", "+2", 2), ScoreAction("three", "+3", 3))),
        SportsRule("rugby_union", "Rugby union", listOf(ScoreAction("try", "Try +5", 5), ScoreAction("conversion", "Conversion +2", 2), ScoreAction("penalty", "Penalty +3", 3), ScoreAction("drop_goal", "Drop goal +3", 3))),
        SportsRule("hockey", "Hockey", listOf(ScoreAction("goal", "Goal +1", 1))),
        SportsRule("netball", "Netball", listOf(ScoreAction("goal", "Goal +1", 1))),
        SportsRule("handball", "Handball", listOf(ScoreAction("goal", "Goal +1", 1))),
        SportsRule("tennis", "Tennis", listOf(ScoreAction("point", "Point")), structured = true),
        SportsRule("badminton", "Badminton", listOf(ScoreAction("point", "Point")), structured = true),
        SportsRule("table_tennis", "Table tennis", listOf(ScoreAction("point", "Point")), structured = true),
        SportsRule("volleyball", "Volleyball", listOf(ScoreAction("point", "Point")), structured = true),
        SportsRule("squash", "Squash", listOf(ScoreAction("point", "Point")), structured = true),
        SportsRule("padel", "Padel", listOf(ScoreAction("point", "Point")), structured = true)
    )

    fun byId(id: String): SportsRule = all.firstOrNull { it.id == id } ?: all.first()

    fun initialState(ruleset: String, config: JSONObject): JSONObject = when (ruleset) {
        "tennis", "padel" -> JSONObject().apply {
            put("points_a", 0); put("points_b", 0)
            put("games_a", 0); put("games_b", 0)
            put("sets_a", 0); put("sets_b", 0)
            put("tiebreak", false); put("tiebreak_a", 0); put("tiebreak_b", 0)
            put("best_of", config.optInt("best_of", 3))
        }
        "badminton" -> rallyState(config, defaultTarget = 21, defaultBestOf = 3)
        "table_tennis" -> rallyState(config, defaultTarget = 11, defaultBestOf = 5)
        "volleyball" -> rallyState(config, defaultTarget = 25, defaultBestOf = 5)
        "squash" -> rallyState(config, defaultTarget = 11, defaultBestOf = 5)
        else -> JSONObject()
    }

    private fun rallyState(config: JSONObject, defaultTarget: Int, defaultBestOf: Int) = JSONObject().apply {
        put("points_a", 0); put("points_b", 0)
        put("games_a", 0); put("games_b", 0)
        put("target", config.optInt("target", defaultTarget).takeIf { it > 0 } ?: defaultTarget)
        put("win_by", config.optInt("win_by", 2))
        put("best_of", config.optInt("best_of", defaultBestOf))
    }
}
