package com.example.methodmesh.modules.choiceexperiment

import kotlin.math.abs
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

internal enum class DceMethod(val id: String, val title: String, val phenomenon: String) {
    Pairwise("dce.pairwise", "Pairwise comparison", "dce.pairwise"),
    MaxDiff("dce.maxdiff", "MaxDiff / Best-Worst", "dce.maxdiff"),
    Ranking("dce.ranking", "Ranking", "dce.ranking"),
    Points("dce.points", "Points allocation", "dce.points"),
    Conjoint("dce.conjoint", "Conjoint selection", "dce.conjoint")
}

internal data class DceConfig(
    val method: DceMethod,
    val options: List<String>,
    val rounds: Int,
    val optionsPerRound: Int,
    val itemsPerRound: Int,
    val itemsPerRoundMin: Int,
    val itemsPerRoundMax: Int,
    val itemsPerRoundSpec: String,
    val profilesPerRound: Int,
    val totalPoints: Int,
    val seed: String,
    val sessionId: String,
    val attributes: Map<String, List<String>> = emptyMap()
)

internal data class ChoiceRound(val roundNumber: Int, val shown: List<String>)
internal data class ProfileRound(val roundNumber: Int, val profiles: List<Map<String, String>>)

internal object DceConfigParser {
    fun from(settings: Map<String, String>, method: DceMethod): DceConfig {
        val seed = settings.value("seed") ?: System.currentTimeMillis().toString()
        val sessionId = settings.value("session_id") ?: settings.value("instance_id") ?: seed
        val rawOptions = settings.value("options") ?: settings.value("items") ?: "A|B|C|D"
        val options = parseItemList(rawOptions).ifEmpty { listOf("A", "B", "C", "D") }
        val rounds = settings.value("rounds")?.toIntOrNull()?.coerceIn(1, 100) ?: defaultRounds(method)
        val optionsPerRound = settings.value("options_per_round")?.toIntOrNull()
            ?.coerceIn(2, options.size.coerceAtLeast(2))
            ?: if (method == DceMethod.Ranking) options.size else 2
        val itemsPerRoundSpec = settings.value("items_per_round") ?: "4"
        val itemRange = parseCountRange(itemsPerRoundSpec, options.size.coerceAtLeast(2))
        val itemsPerRound = itemRange.first
        val profilesPerRound = settings.value("profiles_per_round")?.toIntOrNull()?.coerceIn(2, 6) ?: 2
        val totalPoints = settings.value("points")?.toIntOrNull()
            ?: settings.value("total_points")?.toIntOrNull()
            ?: 10
        val attributes = parseAttributes(
            settings.value("classes")
                ?: settings.value("attributes")
                ?: settings.value("profiles")
                ?: settings.value("conjoint_profiles")
        )
        return DceConfig(
            method = method,
            options = options,
            rounds = rounds,
            optionsPerRound = optionsPerRound,
            itemsPerRound = itemsPerRound,
            itemsPerRoundMin = itemRange.first,
            itemsPerRoundMax = itemRange.last,
            itemsPerRoundSpec = itemsPerRoundSpec,
            profilesPerRound = profilesPerRound,
            totalPoints = totalPoints.coerceAtLeast(1),
            seed = seed,
            sessionId = sessionId,
            attributes = attributes.ifEmpty { defaultAttributes() }
        )
    }

    private fun Map<String, String>.value(key: String): String? = this[key]?.takeIf { it.isNotBlank() }

    private fun defaultRounds(method: DceMethod): Int = when (method) {
        DceMethod.Pairwise -> 5
        DceMethod.MaxDiff -> 6
        DceMethod.Ranking -> 1
        DceMethod.Points -> 1
        DceMethod.Conjoint -> 5
    }

    internal fun parseItemList(raw: String?): List<String> = raw.orEmpty()
        .split('|', ',', ';', '\n')
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    internal fun parseAttributes(raw: String?): Map<String, List<String>> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('|', '\n')
            .mapNotNull { part ->
                val separator = if (':' in part) ':' else '='
                val name = part.substringBefore(separator, "").trim()
                val values = part.substringAfter(separator, "")
                    .split(',')
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                if (name.isBlank() || values.isEmpty()) null else name to values
            }
            .toMap()
    }

    internal fun parseCountRange(raw: String?, maxItems: Int): IntRange {
        val cleaned = raw.orEmpty().trim().ifBlank { "4" }
        val parts = cleaned.split("-", "–", "..").map { it.trim() }.filter { it.isNotBlank() }
        val lower = parts.firstOrNull()?.toIntOrNull() ?: 4
        val upper = parts.getOrNull(1)?.toIntOrNull() ?: lower
        val min = lower.coerceIn(2, maxItems.coerceAtLeast(2))
        val max = upper.coerceIn(2, maxItems.coerceAtLeast(2))
        return if (min <= max) min..max else max..min
    }

    private fun defaultAttributes(): Map<String, List<String>> = linkedMapOf(
        "BRAND" to listOf("A", "B", "C"),
        "FEATURE" to listOf("Basic", "Enhanced", "Premium"),
        "PRICE" to listOf("Low", "Medium", "High")
    )
}

internal object DceDesignGenerator {
    fun stableSeed(seed: String): Int = abs(seed.fold(0) { acc, c -> acc * 31 + c.code })

    fun choiceRounds(config: DceConfig, perRound: Int): List<ChoiceRound> {
        val random = Random(stableSeed(config.seed))
        return List(config.rounds) { index ->
            ChoiceRound(index + 1, config.options.shuffled(random).take(perRound.coerceAtMost(config.options.size)))
        }
    }

    fun maxDiffRounds(config: DceConfig): List<ChoiceRound> {
        val random = Random(stableSeed(config.seed))
        return List(config.rounds) { index ->
            val count = if (config.itemsPerRoundMin == config.itemsPerRoundMax) {
                config.itemsPerRoundMin
            } else {
                random.nextInt(config.itemsPerRoundMin, config.itemsPerRoundMax + 1)
            }.coerceAtMost(config.options.size)
            ChoiceRound(index + 1, config.options.shuffled(random).take(count))
        }
    }

    fun conjointRounds(config: DceConfig): List<ProfileRound> {
        val random = Random(stableSeed(config.seed))
        val attrs = config.attributes
        return List(config.rounds) { roundIndex ->
            val profiles = List(config.profilesPerRound) { profileIndex ->
                val profile = linkedMapOf<String, String>()
                profile["profile_id"] = "${roundIndex + 1}${('A'.code + profileIndex).toChar()}"
                attrs.forEach { (attribute, levels) ->
                    profile[attribute] = levels[random.nextInt(levels.size)]
                }
                profile
            }
            ProfileRound(roundIndex + 1, profiles)
        }
    }
}

internal object DceJson {
    fun pairwise(config: DceConfig, responses: List<PairwiseResponse>): String = base(config).apply {
        put("options_per_round", config.optionsPerRound)
        put("options", JSONArray(config.options))
        put("rounds", JSONArray().apply {
            responses.forEach { response ->
                put(JSONObject().apply {
                    put("round", response.roundNumber)
                    put("shown", JSONArray(response.shown))
                    put("selected", response.selected)
                })
            }
        })
    }.toString()

    fun maxDiff(config: DceConfig, responses: List<MaxDiffResponse>): String = base(config).apply {
        put("items_per_round", config.itemsPerRound)
        put("items_per_round_min", config.itemsPerRoundMin)
        put("items_per_round_max", config.itemsPerRoundMax)
        put("items_per_round_spec", config.itemsPerRoundSpec)
        put("items", JSONArray(config.options))
        put("rounds", JSONArray().apply {
            responses.forEach { response ->
                put(JSONObject().apply {
                    put("round", response.roundNumber)
                    put("shown", JSONArray(response.shown))
                    put("best", response.best)
                    put("worst", response.worst)
                })
            }
        })
    }.toString()

    fun ranking(config: DceConfig, responses: List<RankingResponse>): String = base(config).apply {
        put("options_per_round", config.optionsPerRound)
        put("options", JSONArray(config.options))
        put("rounds", JSONArray().apply {
            responses.forEach { response ->
                put(JSONObject().apply {
                    put("round", response.roundNumber)
                    put("shown", JSONArray(response.shown))
                    put("ranking", JSONArray(response.ranking))
                })
            }
        })
    }.toString()

    fun points(config: DceConfig, allocations: Map<String, Int>): String = base(config).apply {
        put("total_points", config.totalPoints)
        put("options", JSONArray(config.options))
        put("rounds", JSONArray().apply {
            put(JSONObject().apply {
                put("round", 1)
                put("shown", JSONArray(config.options))
                put("total_points", config.totalPoints)
                put("allocations", JSONObject().apply {
                    allocations.forEach { (key, value) -> put(key, value) }
                })
            })
        })
    }.toString()

    fun conjoint(config: DceConfig, responses: List<ConjointResponse>): String = base(config).apply {
        put("profiles_per_round", config.profilesPerRound)
        put("attributes", JSONObject().apply {
            config.attributes.forEach { (key, values) -> put(key, JSONArray(values)) }
        })
        put("rounds", JSONArray().apply {
            responses.forEach { response ->
                put(JSONObject().apply {
                    put("round", response.roundNumber)
                    put("profiles", JSONArray().apply {
                        response.profiles.forEach { profile ->
                            put(JSONObject().apply { profile.forEach { (key, value) -> put(key, value) } })
                        }
                    })
                    put("selected", response.selectedProfileId)
                })
            }
        })
    }.toString()

    private fun base(config: DceConfig): JSONObject = JSONObject().apply {
        put("module", "choice")
        put("method", config.method.id.removePrefix("dce."))
        put("session_id", config.sessionId)
        put("seed", config.seed)
        put("rounds_requested", config.rounds)
        put("app_component", "MethodMesh")
    }
}

internal data class PairwiseResponse(val roundNumber: Int, val shown: List<String>, val selected: String)
internal data class MaxDiffResponse(val roundNumber: Int, val shown: List<String>, val best: String, val worst: String)
internal data class RankingResponse(val roundNumber: Int, val shown: List<String>, val ranking: List<String>)
internal data class ConjointResponse(val roundNumber: Int, val profiles: List<Map<String, String>>, val selectedProfileId: String)
