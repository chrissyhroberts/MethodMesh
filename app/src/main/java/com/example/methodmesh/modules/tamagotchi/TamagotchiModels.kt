package com.example.methodmesh.modules.tamagotchi

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal fun JSONObject.stringMap(name: String): Map<String, String> {
    val o = optJSONObject(name) ?: return emptyMap()
    return buildMap {
        o.keys().forEach { key -> put(key, o.optString(key)) }
    }
}

internal fun JSONObject.doubleMap(name: String): Map<String, Double> {
    val o = optJSONObject(name) ?: return emptyMap()
    return buildMap {
        o.keys().forEach { key -> put(key, o.optDouble(key, 0.0)) }
    }
}

internal fun Map<String, Double>.toJsonObject() = JSONObject().also { out ->
    forEach { (k, v) -> out.put(k, v) }
}


data class StateVariable(
    val id: String,
    val label: String,
    val minimum: Double = 0.0,
    val maximum: Double = 100.0,
    val initial: Double = 50.0,
    val driftPerSimulationHour: Double = 0.0,
    val careVisible: Boolean = true,
    val measurable: Boolean = false,
    val unit: String = ""
) {
    fun clamp(value: Double) = value.coerceIn(minimum, maximum)
}

data class TamagotchiAction(
    val id: String,
    val label: String,
    val glyph: CareGlyphKind,
    val description: String,
    val immediateEffects: Map<String, Double> = emptyMap(),
    val hiddenEffects: Map<String, Double> = emptyMap(),
    val advancesMinutes: Double = 0.0,
    val generated: Boolean = false,
    val generatorId: String? = null
)

data class ProceduralGenerator(
    val id: String,
    val labels: List<String>,
    val count: Int,
    val effectRanges: Map<String, ClosedFloatingPointRange<Double>>,
    val hiddenEffectRanges: Map<String, ClosedFloatingPointRange<Double>> = emptyMap(),
    val glyph: CareGlyphKind = CareGlyphKind.Feed
)

data class ScenarioDefinition(
    val id: String,
    val title: String,
    val subtitle: String,
    val variables: List<StateVariable>,
    val actions: List<TamagotchiAction>,
    val generators: List<ProceduralGenerator> = emptyList(),
    val observationVariableIds: List<String> = emptyList(),
    val measurementVariableIds: List<String> = emptyList(),
    val teachingNote: String = ""
) {
    fun variable(id: String): StateVariable? = variables.firstOrNull { it.id == id }
    fun action(id: String): TamagotchiAction? = actions.firstOrNull { it.id == id }
}

data class CreatureDefinition(
    val id: String,
    val displayName: String,
    val tagline: String,
    val personality: String,
    val bodyHue: Long,
    val accentHue: Long,
    val sociability: Double,
    val activity: Double,
    val expressiveness: Double,
    val sleepiness: Double
)

enum class SimulationMode(val wire: String) {
    RealTime("real_time"),
    Accelerated("accelerated"),
    Classroom("classroom"),
    TurnBased("turn_based");

    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.wire == raw } ?: Classroom
    }
}

enum class OffSessionMode(val wire: String) {
    Pause("pause"), Sleep("sleep"), Autonomous("autonomous"), Continuous("continuous");
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.wire == raw } ?: Sleep
    }
}

data class AttentionPolicy(
    val enabled: Boolean = true,
    val profile: String = "young_learner",
    val maxPerDay: Int = 2,
    val minimumSpacingMinutes: Int = 180,
    val schoolStartMinutes: Int = 8 * 60 + 30,
    val schoolEndMinutes: Int = 15 * 60 + 30,
    val quietStartMinutes: Int = 20 * 60 + 30,
    val quietEndMinutes: Int = 7 * 60,
    val allowDuringSchool: Boolean = false
) {
    fun toJson() = JSONObject()
        .put("enabled", enabled)
        .put("profile", profile)
        .put("max_per_day", maxPerDay)
        .put("minimum_spacing_minutes", minimumSpacingMinutes)
        .put("school_start_minutes", schoolStartMinutes)
        .put("school_end_minutes", schoolEndMinutes)
        .put("quiet_start_minutes", quietStartMinutes)
        .put("quiet_end_minutes", quietEndMinutes)
        .put("allow_during_school", allowDuringSchool)

    companion object {
        fun from(o: JSONObject?) = if (o == null) AttentionPolicy() else AttentionPolicy(
            enabled = o.optBoolean("enabled", true),
            profile = o.optString("profile", "young_learner"),
            maxPerDay = o.optInt("max_per_day", 2).coerceIn(0, 24),
            minimumSpacingMinutes = o.optInt("minimum_spacing_minutes", 180).coerceAtLeast(0),
            schoolStartMinutes = o.optInt("school_start_minutes", 8 * 60 + 30),
            schoolEndMinutes = o.optInt("school_end_minutes", 15 * 60 + 30),
            quietStartMinutes = o.optInt("quiet_start_minutes", 20 * 60 + 30),
            quietEndMinutes = o.optInt("quiet_end_minutes", 7 * 60),
            allowDuringSchool = o.optBoolean("allow_during_school", false)
        )
    }
}

data class GeneratedOption(
    val id: String,
    val label: String,
    val glyph: CareGlyphKind,
    val generatorId: String,
    val visibleDescription: String,
    val effects: Map<String, Double>,
    val hiddenEffects: Map<String, Double>
) {
    fun toAction() = TamagotchiAction(
        id = id,
        label = label,
        glyph = glyph,
        description = visibleDescription,
        immediateEffects = effects,
        hiddenEffects = hiddenEffects,
        generated = true,
        generatorId = generatorId
    )

    fun toJson() = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("glyph", glyph.name)
        .put("generator_id", generatorId)
        .put("visible_description", visibleDescription)
        .put("effects", effects.toJsonObject())
        .put("hidden_effects", hiddenEffects.toJsonObject())

    companion object {
        fun from(o: JSONObject) = GeneratedOption(
            id = o.getString("id"),
            label = o.getString("label"),
            glyph = runCatching { CareGlyphKind.valueOf(o.optString("glyph")) }.getOrDefault(CareGlyphKind.Feed),
            generatorId = o.optString("generator_id"),
            visibleDescription = o.optString("visible_description"),
            effects = o.doubleMap("effects"),
            hiddenEffects = o.doubleMap("hidden_effects")
        )
    }
}

data class TamagotchiSession(
    val id: String,
    val creatureId: String,
    val creatureName: String,
    val scenarioId: String,
    val seed: Long,
    val simulationMode: SimulationMode,
    val acceleration: Double,
    val offSessionMode: OffSessionMode,
    val attentionPolicy: AttentionPolicy,
    val startedAtIso: String,
    val lastWallTimeMs: Long,
    val simulationMinute: Double,
    val state: Map<String, Double>,
    val generatedOptions: List<GeneratedOption>,
    val historyArtifactRef: String,
    val endedAtIso: String? = null,
    val lastNotificationWallTimeMs: Long = 0L,
    val notificationDayKey: String = "",
    val notificationsToday: Int = 0
) {
    val ended: Boolean get() = !endedAtIso.isNullOrBlank()

    fun toJson() = JSONObject()
        .put("id", id)
        .put("creature_id", creatureId)
        .put("creature_name", creatureName)
        .put("scenario_id", scenarioId)
        .put("seed", seed)
        .put("simulation_mode", simulationMode.wire)
        .put("acceleration", acceleration)
        .put("off_session_mode", offSessionMode.wire)
        .put("attention_policy", attentionPolicy.toJson())
        .put("started_at", startedAtIso)
        .put("last_wall_time_ms", lastWallTimeMs)
        .put("simulation_minute", simulationMinute)
        .put("state", state.toJsonObject())
        .put("generated_options", JSONArray().also { a -> generatedOptions.forEach { a.put(it.toJson()) } })
        .put("history_artifact_ref", historyArtifactRef)
        .put("ended_at", endedAtIso ?: JSONObject.NULL)
        .put("last_notification_wall_time_ms", lastNotificationWallTimeMs)
        .put("notification_day_key", notificationDayKey)
        .put("notifications_today", notificationsToday)

    companion object {
        fun new(
            creatureId: String,
            creatureName: String,
            scenario: ScenarioDefinition,
            seed: Long,
            simulationMode: SimulationMode,
            acceleration: Double,
            offSessionMode: OffSessionMode,
            attentionPolicy: AttentionPolicy,
            generatedOptions: List<GeneratedOption>,
            historyArtifactRef: String,
            nowMs: Long = System.currentTimeMillis()
        ) = TamagotchiSession(
            id = UUID.randomUUID().toString(),
            creatureId = creatureId,
            creatureName = creatureName,
            scenarioId = scenario.id,
            seed = seed,
            simulationMode = simulationMode,
            acceleration = acceleration,
            offSessionMode = offSessionMode,
            attentionPolicy = attentionPolicy,
            startedAtIso = Instant.ofEpochMilli(nowMs).toString(),
            lastWallTimeMs = nowMs,
            simulationMinute = 0.0,
            state = scenario.variables.associate { it.id to it.initial },
            generatedOptions = generatedOptions,
            historyArtifactRef = historyArtifactRef
        )

        fun fromJson(o: JSONObject) = TamagotchiSession(
            id = o.getString("id"),
            creatureId = o.getString("creature_id"),
            creatureName = o.optString("creature_name", o.optString("creature_id")),
            scenarioId = o.getString("scenario_id"),
            seed = o.optLong("seed", 1L),
            simulationMode = SimulationMode.from(o.optString("simulation_mode")),
            acceleration = o.optDouble("acceleration", 12.0),
            offSessionMode = OffSessionMode.from(o.optString("off_session_mode")),
            attentionPolicy = AttentionPolicy.from(o.optJSONObject("attention_policy")),
            startedAtIso = o.optString("started_at"),
            lastWallTimeMs = o.optLong("last_wall_time_ms"),
            simulationMinute = o.optDouble("simulation_minute"),
            state = o.doubleMap("state"),
            generatedOptions = o.optJSONArray("generated_options")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let(GeneratedOption::from) }
            }.orEmpty(),
            historyArtifactRef = o.optString("history_artifact_ref"),
            endedAtIso = o.optString("ended_at").takeIf { it.isNotBlank() && it != "null" },
            lastNotificationWallTimeMs = o.optLong("last_notification_wall_time_ms", 0L),
            notificationDayKey = o.optString("notification_day_key"),
            notificationsToday = o.optInt("notifications_today", 0)
        )
    }
}

data class TamagotchiEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val recordedAtIso: String = Instant.now().toString(),
    val simulationMinute: Double,
    val type: String,
    val visibility: String,
    val payload: JSONObject
) {
    fun toJson(sessionId: String) = JSONObject()
        .put("entry_id", eventId)
        .put("recorded_at", recordedAtIso)
        .put("simulation_minute", simulationMinute)
        .put("session_id", sessionId)
        .put("type", type)
        .put("visibility", visibility)
        .put("payload", payload)
}

enum class CareGlyphKind {
    Feed, Drink, Play, Rest, Clean, Medicine, Observe, Measure, Time, History, Analyse, Export, Sparkle
}

data class Phenotype(
    val headline: String,
    val detail: String,
    val mood: Double,
    val energy: Double,
    val hydration: Double,
    val stress: Double,
    val sleeping: Boolean,
    val unwell: Boolean
)
