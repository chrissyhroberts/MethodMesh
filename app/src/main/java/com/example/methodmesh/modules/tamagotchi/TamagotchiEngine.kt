package com.example.methodmesh.modules.tamagotchi

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlin.random.Random

object TamagotchiEngine {
    fun generateOptions(scenario: ScenarioDefinition, seed: Long): List<GeneratedOption> {
        val rng = Random(seed)
        return scenario.generators.flatMap { generator ->
            val labels = generator.labels.shuffled(rng).take(generator.count)
            labels.mapIndexed { index, label ->
                GeneratedOption(
                    id = "${generator.id}_${index + 1}_${seed.toString().takeLast(4)}",
                    label = label,
                    glyph = generator.glyph,
                    generatorId = generator.id,
                    visibleDescription = "A procedurally generated option. Its exact properties are deliberately hidden during care.",
                    effects = generator.effectRanges.mapValues { (_, range) -> randomIn(rng, range) },
                    hiddenEffects = generator.hiddenEffectRanges.mapValues { (_, range) -> randomIn(rng, range) }
                )
            }
        }
    }

    private fun randomIn(rng: Random, range: ClosedFloatingPointRange<Double>): Double {
        val raw = range.start + rng.nextDouble() * (range.endInclusive - range.start)
        return (raw * 100.0).roundToInt() / 100.0
    }

    fun advanceWallClock(session: TamagotchiSession, scenario: ScenarioDefinition, nowMs: Long = System.currentTimeMillis()): Pair<TamagotchiSession, List<TamagotchiEvent>> {
        if (session.ended || nowMs <= session.lastWallTimeMs) return session.copy(lastWallTimeMs = nowMs) to emptyList()
        val realMinutes = (nowMs - session.lastWallTimeMs) / 60000.0
        val simMinutes = when (session.simulationMode) {
            SimulationMode.RealTime -> realMinutes
            SimulationMode.Accelerated -> realMinutes * session.acceleration.coerceIn(0.1, 1440.0)
            SimulationMode.Classroom, SimulationMode.TurnBased -> 0.0
        }
        if (simMinutes <= 0.0) return session.copy(lastWallTimeMs = nowMs) to emptyList()

        val multiplier = offSessionMultiplier(session, nowMs)
        return advanceByMinutes(session.copy(lastWallTimeMs = nowMs), scenario, simMinutes * multiplier, "wall_clock")
    }

    fun advanceByMinutes(
        session: TamagotchiSession,
        scenario: ScenarioDefinition,
        minutes: Double,
        reason: String = "manual"
    ): Pair<TamagotchiSession, List<TamagotchiEvent>> {
        val safeMinutes = minutes.coerceIn(0.0, 10080.0)
        if (safeMinutes <= 0.0 || session.ended) return session to emptyList()
        val hours = safeMinutes / 60.0
        val before = session.state
        val state = before.toMutableMap()
        scenario.variables.forEach { variable ->
            val next = variable.clamp((state[variable.id] ?: variable.initial) + variable.driftPerSimulationHour * hours)
            state[variable.id] = next
        }
        applyScenarioDynamics(scenario.id, state, hours)
        scenario.variables.forEach { variable -> state[variable.id] = variable.clamp(state[variable.id] ?: variable.initial) }

        val updated = session.copy(
            simulationMinute = session.simulationMinute + safeMinutes,
            state = state
        )
        val event = TamagotchiEvent(
            simulationMinute = updated.simulationMinute,
            type = "state_transition",
            visibility = "latent",
            payload = JSONObject()
                .put("reason", reason)
                .put("delta_minutes", safeMinutes)
                .put("before", before.toJsonObject())
                .put("after", state.toJsonObject())
        )
        val publicEvent = TamagotchiEvent(
            simulationMinute = updated.simulationMinute,
            type = "care_state_transition",
            visibility = "care",
            payload = JSONObject()
                .put("reason", reason)
                .put("delta_minutes", safeMinutes)
                .put("visible_state", visibleState(updated, scenario).toJsonObject())
        )
        return updated to listOf(event, publicEvent)
    }

    private fun applyScenarioDynamics(scenarioId: String, state: MutableMap<String, Double>, hours: Double) {
        when (scenarioId) {
            "foundation_care" -> {
                val lowHydration = (55.0 - (state["hydration"] ?: 55.0)).coerceAtLeast(0.0)
                val lowSatiety = (50.0 - (state["satiety"] ?: 50.0)).coerceAtLeast(0.0)
                state["stress"] = (state["stress"] ?: 0.0) + (lowHydration * .035 + lowSatiety * .025) * hours
                state["mood"] = (state["mood"] ?: 50.0) - (lowHydration * .02 + lowSatiety * .015) * hours
            }
            "infection_lab" -> {
                val burden = state["infection_burden"] ?: 0.0
                state["temperature"] = (state["temperature"] ?: 37.0) + (burden / 100.0) * 0.11 * hours
                state["energy"] = (state["energy"] ?: 50.0) - (burden / 100.0) * 2.2 * hours
                state["stress"] = (state["stress"] ?: 20.0) + (burden / 100.0) * 1.4 * hours
            }
            "sprig_growth" -> {
                val moisture = state["moisture"] ?: 50.0
                val light = state["light"] ?: 50.0
                val nutrition = state["nutrition"] ?: 50.0
                val balance = (minOf(moisture, light, nutrition) / 100.0).coerceIn(0.0, 1.0)
                state["growth"] = (state["growth"] ?: 0.0) + 0.95 * balance * hours
                if (moisture > 92.0) state["root_stress"] = (state["root_stress"] ?: 0.0) + 2.5 * hours
                if (moisture < 25.0) state["leaf_vigor"] = (state["leaf_vigor"] ?: 50.0) - 2.2 * hours
            }
        }
    }

    fun applyAction(
        session: TamagotchiSession,
        scenario: ScenarioDefinition,
        actionId: String
    ): Triple<TamagotchiSession, TamagotchiEvent, List<TamagotchiEvent>> {
        val action = scenario.action(actionId)
            ?: session.generatedOptions.firstOrNull { it.id == actionId }?.toAction()
            ?: error("Unknown Tamagotchi action: $actionId")
        val before = session.state
        val state = before.toMutableMap()
        (action.immediateEffects + action.hiddenEffects).forEach { (id, delta) ->
            val variable = scenario.variable(id) ?: return@forEach
            state[id] = variable.clamp((state[id] ?: variable.initial) + delta)
        }
        var updated = session.copy(state = state)
        val actionEvent = TamagotchiEvent(
            simulationMinute = updated.simulationMinute,
            type = "action",
            visibility = "care",
            payload = JSONObject()
                .put("action_id", action.id)
                .put("label", action.label)
                .put("generated", action.generated)
                .put("generator_id", action.generatorId ?: JSONObject.NULL)
                .put("visible_effects", action.immediateEffects.toJsonObject())
        )
        val hiddenEvent = TamagotchiEvent(
            simulationMinute = updated.simulationMinute,
            type = "action_effects",
            visibility = "latent",
            payload = JSONObject()
                .put("action_id", action.id)
                .put("all_effects", (action.immediateEffects + action.hiddenEffects).toJsonObject())
                .put("before", before.toJsonObject())
                .put("after", state.toJsonObject())
        )
        val extraEvents = mutableListOf(hiddenEvent)
        if (action.advancesMinutes > 0.0) {
            val advanced = advanceByMinutes(updated, scenario, action.advancesMinutes, "action:${action.id}")
            updated = advanced.first
            extraEvents += advanced.second
        }
        if (session.simulationMode == SimulationMode.TurnBased) {
            val advanced = advanceByMinutes(updated, scenario, 15.0, "turn_based_step")
            updated = advanced.first
            extraEvents += advanced.second
        }
        return Triple(updated, actionEvent, extraEvents)
    }

    fun observe(session: TamagotchiSession, scenario: ScenarioDefinition, creature: CreatureDefinition): TamagotchiEvent {
        val phenotype = phenotype(session, scenario, creature)
        return TamagotchiEvent(
            simulationMinute = session.simulationMinute,
            type = "observation",
            visibility = "care",
            payload = JSONObject()
                .put("headline", phenotype.headline)
                .put("detail", phenotype.detail)
                .put("visible_state", visibleState(session, scenario).toJsonObject())
        )
    }

    fun measure(session: TamagotchiSession, scenario: ScenarioDefinition, variableId: String): TamagotchiEvent {
        val variable = scenario.variable(variableId) ?: error("Unknown variable: $variableId")
        require(variable.measurable) { "$variableId is not measurable in ${scenario.id}" }
        val value = session.state[variableId] ?: variable.initial
        return TamagotchiEvent(
            simulationMinute = session.simulationMinute,
            type = "measurement",
            visibility = "care",
            payload = JSONObject()
                .put("variable_id", variable.id)
                .put("label", variable.label)
                .put("value", value)
                .put("unit", variable.unit)
        )
    }

    fun visibleState(session: TamagotchiSession, scenario: ScenarioDefinition): Map<String, Double> =
        scenario.variables.filter { it.careVisible }.associate { it.id to (session.state[it.id] ?: it.initial) }

    fun phenotype(session: TamagotchiSession, scenario: ScenarioDefinition, creature: CreatureDefinition, nowMs: Long = System.currentTimeMillis()): Phenotype {
        val s = session.state
        val hydration = s["hydration"] ?: s["moisture"] ?: 70.0
        val energy = s["energy"] ?: s["leaf_vigor"] ?: 70.0
        val mood = s["mood"] ?: s["leaf_vigor"] ?: 70.0
        val stress = s["stress"] ?: s["root_stress"] ?: 20.0
        val burden = s["infection_burden"] ?: 0.0
        val sleeping = isQuietTime(session.attentionPolicy, nowMs) || (energy < 22.0 && creature.sleepiness > .5)
        val unwell = burden > 45.0 || hydration < 28.0 || stress > 78.0
        val headline = when {
            sleeping -> "${session.creatureName} is tucked up asleep"
            unwell -> "${session.creatureName} seems a little off"
            energy > 78.0 && mood > 70.0 -> "${session.creatureName} is extremely pleased to be here"
            energy < 35.0 -> "${session.creatureName} is moving more slowly than usual"
            mood < 38.0 -> "${session.creatureName} is unusually quiet"
            hydration < 40.0 -> "${session.creatureName} keeps looking toward the water"
            else -> "${session.creatureName} seems content"
        }
        val detail = when {
            sleeping -> "Quiet-hours mode is active; ordinary notifications are suppressed."
            unwell -> "There is enough change to justify a deliberate observation or measurement."
            energy > 78.0 && creature.id == "nib" -> "Maximum frill wiggle."
            creature.id == "mossbit" && mood > 72.0 -> "Mossbit is carrying the button around again."
            creature.id == "pip" && energy < 45.0 -> "Pip is considering wrapping both ears around itself."
            creature.id == "tumble" && energy > 65.0 -> "Tumble has elected to travel by rolling."
            creature.id == "sprig" -> "Sprig is leaning very slightly toward the brighter side."
            else -> "Small behaviours are expressive, but they are not formal measurements."
        }
        return Phenotype(headline, detail, mood, energy, hydration, stress, sleeping, unwell)
    }

    fun notificationDecision(session: TamagotchiSession, nowMs: Long = System.currentTimeMillis()): Pair<Boolean, String> {
        val p = session.attentionPolicy
        if (!p.enabled || session.ended) return false to "notifications_disabled_or_session_ended"
        val dayKey = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault()).toLocalDate().toString()
        val countToday = if (dayKey == session.notificationDayKey) session.notificationsToday else 0
        if (countToday >= p.maxPerDay) return false to "daily_cap"
        if (session.lastNotificationWallTimeMs > 0 && nowMs - session.lastNotificationWallTimeMs < p.minimumSpacingMinutes * 60000L) {
            return false to "minimum_spacing"
        }
        if (isQuietTime(p, nowMs)) return false to "quiet_hours"
        if (!p.allowDuringSchool && isSchoolTime(p, nowMs)) return false to "school_hours"
        return true to "eligible"
    }

    fun markNotificationDelivered(session: TamagotchiSession, nowMs: Long = System.currentTimeMillis()): TamagotchiSession {
        val dayKey = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault()).toLocalDate().toString()
        val count = if (dayKey == session.notificationDayKey) session.notificationsToday + 1 else 1
        return session.copy(
            lastNotificationWallTimeMs = nowMs,
            notificationDayKey = dayKey,
            notificationsToday = count
        )
    }

    private fun offSessionMultiplier(session: TamagotchiSession, nowMs: Long): Double = when (session.offSessionMode) {
        OffSessionMode.Pause -> 0.0
        OffSessionMode.Continuous -> 1.0
        OffSessionMode.Autonomous -> if (isQuietTime(session.attentionPolicy, nowMs)) 0.35 else 0.8
        OffSessionMode.Sleep -> if (isQuietTime(session.attentionPolicy, nowMs) || isSchoolTime(session.attentionPolicy, nowMs)) 0.25 else 1.0
    }

    private fun isSchoolTime(policy: AttentionPolicy, nowMs: Long): Boolean {
        val dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
        if (dt.dayOfWeek.value >= 6) return false
        val minute = dt.hour * 60 + dt.minute
        return minute in policy.schoolStartMinutes until policy.schoolEndMinutes
    }

    private fun isQuietTime(policy: AttentionPolicy, nowMs: Long): Boolean {
        val time = LocalTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault())
        val minute = time.hour * 60 + time.minute
        return if (policy.quietStartMinutes <= policy.quietEndMinutes) {
            minute in policy.quietStartMinutes until policy.quietEndMinutes
        } else {
            minute >= policy.quietStartMinutes || minute < policy.quietEndMinutes
        }
    }
}
