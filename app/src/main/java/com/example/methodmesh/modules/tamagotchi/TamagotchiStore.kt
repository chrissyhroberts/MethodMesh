package com.example.methodmesh.modules.tamagotchi

import android.content.Context
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant

class TamagotchiStore(private val context: Context) {
    private val root = File(context.filesDir, "tamagotchi/sessions").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("methodmesh_tamagotchi", Context.MODE_PRIVATE)

    fun create(
        creatureId: String,
        creatureName: String,
        scenarioId: String,
        seed: Long,
        simulationMode: SimulationMode,
        acceleration: Double,
        offSessionMode: OffSessionMode,
        attentionPolicy: AttentionPolicy
    ): TamagotchiSession {
        val scenario = TamagotchiCatalog.scenario(scenarioId)
        val generated = TamagotchiEngine.generateOptions(scenario, seed)
        val historyRef = AndroidArtifacts.service(context).createPersistent(
            name = "Tamagotchi ${creatureName.ifBlank { TamagotchiCatalog.creature(creatureId).displayName }} history.jsonl",
            mime = "application/jsonl",
            input = ByteArrayInputStream(ByteArray(0))
        )
        val session = TamagotchiSession.new(
            creatureId = creatureId,
            creatureName = creatureName.ifBlank { TamagotchiCatalog.creature(creatureId).displayName },
            scenario = scenario,
            seed = seed,
            simulationMode = simulationMode,
            acceleration = acceleration,
            offSessionMode = offSessionMode,
            attentionPolicy = attentionPolicy,
            generatedOptions = generated,
            historyArtifactRef = historyRef.id
        )
        save(session)
        prefs.edit().putString("active_session_id", session.id).apply()
        append(session, TamagotchiEvent(
            simulationMinute = 0.0,
            type = "session_started",
            visibility = "care",
            payload = JSONObject()
                .put("creature_id", session.creatureId)
                .put("creature_name", session.creatureName)
                .put("scenario_id", session.scenarioId)
                .put("seed", session.seed)
                .put("simulation_mode", session.simulationMode.wire)
                .put("off_session_mode", session.offSessionMode.wire)
                .put("attention_policy", session.attentionPolicy.toJson())
        ))
        generated.forEach { option ->
            append(session, TamagotchiEvent(
                simulationMinute = 0.0,
                type = "generated_option",
                visibility = "latent",
                payload = option.toJson()
            ))
        }
        append(session, TamagotchiEvent(
            simulationMinute = 0.0,
            type = "initial_state",
            visibility = "latent",
            payload = JSONObject().put("state", session.state.toJsonObject())
        ))
        append(session, TamagotchiEvent(
            simulationMinute = 0.0,
            type = "initial_visible_state",
            visibility = "care",
            payload = JSONObject().put("visible_state", TamagotchiEngine.visibleState(session, scenario).toJsonObject())
        ))
        TamagotchiReminderScheduler.schedule(context, session)
        return session
    }

    fun activeSessionId(): String? = prefs.getString("active_session_id", null)
    fun active(): TamagotchiSession? = activeSessionId()?.let(::load)

    fun load(id: String): TamagotchiSession? {
        val file = sessionFile(id)
        if (!file.isFile) return null
        return runCatching { TamagotchiSession.fromJson(JSONObject(file.readText())) }.getOrNull()
    }

    fun loadAndAdvance(id: String, nowMs: Long = System.currentTimeMillis()): TamagotchiSession? {
        val session = load(id) ?: return null
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val (advanced, events) = TamagotchiEngine.advanceWallClock(session, scenario, nowMs)
        if (advanced != session || events.isNotEmpty()) {
            save(advanced)
            events.forEach { append(advanced, it) }
        }
        return advanced
    }

    fun save(session: TamagotchiSession) {
        val dir = File(root, session.id).apply { mkdirs() }
        val tmp = File(dir, "session.json.partial")
        tmp.writeText(session.toJson().toString(2))
        val target = File(dir, "session.json")
        if (target.exists()) target.delete()
        check(tmp.renameTo(target)) { "Could not persist Tamagotchi session ${session.id}" }
    }

    fun append(session: TamagotchiSession, event: TamagotchiEvent) {
        val record = event.toJson(session.id).toString() + "\n"
        AndroidArtifacts.service(context).appendPersistent(
            ArtifactRef(session.historyArtifactRef),
            record.toByteArray(Charsets.UTF_8)
        )
    }

    fun historyLines(session: TamagotchiSession): List<JSONObject> = runCatching {
        AndroidArtifacts.service(context).open(ArtifactRef(session.historyArtifactRef))
            .bufferedReader()
            .useLines { lines ->
                lines.filter { it.isNotBlank() }
                    .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                    .toList()
            }
    }.getOrDefault(emptyList())

    fun applyAction(sessionId: String, actionId: String): Pair<TamagotchiSession, TamagotchiEvent> {
        var session = loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val result = TamagotchiEngine.applyAction(session, scenario, actionId)
        session = result.first
        append(session, result.second)
        result.third.forEach { append(session, it) }
        save(session)
        TamagotchiReminderScheduler.schedule(context, session)
        return session to result.second
    }

    fun observe(sessionId: String): Pair<TamagotchiSession, TamagotchiEvent> {
        val session = loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val creature = TamagotchiCatalog.creature(session.creatureId)
        val event = TamagotchiEngine.observe(session, scenario, creature)
        append(session, event)
        save(session)
        return session to event
    }

    fun measure(sessionId: String, variableId: String): Pair<TamagotchiSession, TamagotchiEvent> {
        val session = loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val event = TamagotchiEngine.measure(session, scenario, variableId)
        append(session, event)
        save(session)
        return session to event
    }

    fun advance(sessionId: String, minutes: Double): TamagotchiSession {
        var session = loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        val scenario = TamagotchiCatalog.scenario(session.scenarioId)
        val result = TamagotchiEngine.advanceByMinutes(session, scenario, minutes, "manual_advance")
        session = result.first
        result.second.forEach { append(session, it) }
        save(session)
        return session
    }

    fun end(sessionId: String): TamagotchiSession {
        var session = loadAndAdvance(sessionId) ?: error("Tamagotchi session not found: $sessionId")
        if (!session.ended) {
            session = session.copy(endedAtIso = Instant.now().toString())
            save(session)
            append(session, TamagotchiEvent(
                simulationMinute = session.simulationMinute,
                type = "session_ended",
                visibility = "care",
                payload = JSONObject().put("ended_at", session.endedAtIso)
            ))
            TamagotchiReminderScheduler.cancel(context, session.id)
        }
        return session
    }

    fun recordNotificationCandidate(session: TamagotchiSession, delivered: Boolean, reason: String): TamagotchiSession {
        val updated = if (delivered) TamagotchiEngine.markNotificationDelivered(session) else session
        append(updated, TamagotchiEvent(
            simulationMinute = updated.simulationMinute,
            type = if (delivered) "notification_delivered" else "notification_suppressed",
            visibility = "audit",
            payload = JSONObject().put("reason", reason)
        ))
        save(updated)
        return updated
    }

    fun sessionDirectory(id: String): File = File(root, id)
    private fun sessionFile(id: String) = File(sessionDirectory(id), "session.json")
}
