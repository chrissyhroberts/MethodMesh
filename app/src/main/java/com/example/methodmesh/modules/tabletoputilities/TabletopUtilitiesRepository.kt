package com.example.methodmesh.modules.tabletoputilities

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/**
 * Module-owned persistence.
 *
 * Each tabletop workspace is stored in its own directory:
 *   files/tabletoputilities/workspaces/<workspace-id>/workspace.json
 *   files/tabletoputilities/workspaces/<workspace-id>/audit.jsonl
 *
 * No database, manifest entry or shared-framework storage hook is required.
 */
object TabletopUtilitiesRepository {
    @Volatile private var root: File? = null

    fun initialise(context: Context) {
        if (root != null) return
        synchronized(this) {
            if (root == null) {
                root = File(context.applicationContext.filesDir, "tabletoputilities").apply { mkdirs() }
                workspacesRoot().mkdirs()
            }
        }
    }

    fun isInitialised(): Boolean = root != null

    @Synchronized
    fun listWorkspaces(includeArchived: Boolean = false): List<GameWorkspace> = workspacesRoot()
        .listFiles()
        .orEmpty()
        .filter { it.isDirectory }
        .mapNotNull { dir -> runCatching { readWorkspace(dir) }.getOrNull() }
        .filter { includeArchived || !it.archived }
        .sortedByDescending { it.updatedAtIso }

    @Synchronized
    fun getWorkspace(workspaceId: String): GameWorkspace? =
        workspaceDir(workspaceId).takeIf { it.isDirectory }?.let { runCatching { readWorkspace(it) }.getOrNull() }

    @Synchronized
    fun createWorkspace(
        name: String,
        ruleset: String,
        features: Set<TabletopFeature>,
        nowIso: String = Instant.now().toString()
    ): GameWorkspace {
        require(name.isNotBlank()) { "Game name is required." }
        val workspace = GameWorkspace(
            name = name.trim(),
            ruleset = ruleset.trim(),
            features = features,
            createdAtIso = nowIso,
            updatedAtIso = nowIso
        )
        val dir = workspaceDir(workspace.id).apply { mkdirs() }
        writeWorkspace(dir, workspace)
        appendAudit(
            dir,
            eventFor(
                workspace = workspace,
                eventType = "workspace_created",
                summary = "Created ${workspace.name}",
                entityId = workspace.id,
                source = "native",
                beforeValue = null,
                afterValue = workspace.name,
                undoMutationJson = null,
                reversesEventId = null,
                externalReference = null,
                timestampIso = nowIso
            )
        )
        return workspace
    }

    @Synchronized
    fun renameWorkspace(workspaceId: String, name: String, ruleset: String, nowIso: String = Instant.now().toString()): GameWorkspace {
        val before = requireWorkspace(workspaceId)
        val after = before.copy(name = name.trim().ifBlank { before.name }, ruleset = ruleset.trim(), updatedAtIso = nowIso)
        val dir = workspaceDir(workspaceId)
        writeWorkspace(dir, after)
        appendAudit(dir, eventFor(after, "workspace_config_changed", "Workspace details updated", workspaceId, "native", before.name, after.name, null, null, null, nowIso))
        return after
    }

    @Synchronized
    fun archiveWorkspace(workspaceId: String, nowIso: String = Instant.now().toString()): GameWorkspace {
        val before = requireWorkspace(workspaceId)
        val after = before.copy(archived = true, updatedAtIso = nowIso)
        val dir = workspaceDir(workspaceId)
        writeWorkspace(dir, after)
        appendAudit(dir, eventFor(after, "workspace_archived", "Archived ${before.name}", workspaceId, "native", "active", "archived", null, null, null, nowIso))
        return after
    }

    @Synchronized
    fun mutate(
        workspaceId: String,
        mutation: TabletopMutation,
        source: String = "native",
        externalReference: String? = null,
        reversesEventId: String? = null,
        nowIso: String = Instant.now().toString()
    ): MutationOutcome {
        val before = requireWorkspace(workspaceId)
        val outcome = TabletopUtilitiesStateEngine.apply(before, mutation, nowIso)
        val dir = workspaceDir(workspaceId)
        writeWorkspace(dir, outcome.workspace)
        appendAudit(
            dir,
            eventFor(
                workspace = outcome.workspace,
                eventType = outcome.eventType,
                summary = outcome.summary,
                entityId = outcome.entityId,
                source = source,
                beforeValue = outcome.beforeValue,
                afterValue = outcome.afterValue,
                undoMutationJson = outcome.undoMutation?.let(::mutationToJson)?.toString(),
                reversesEventId = reversesEventId,
                externalReference = externalReference,
                timestampIso = nowIso
            )
        )
        return outcome
    }

    @Synchronized
    fun recordDiceRoll(
        workspaceId: String,
        result: String,
        diceAuditJson: String?,
        nowIso: String = Instant.now().toString()
    ): GameWorkspace {
        val workspace = requireWorkspace(workspaceId).copy(updatedAtIso = nowIso)
        val dir = workspaceDir(workspaceId)
        writeWorkspace(dir, workspace)
        val detail = JSONObject()
            .put("dice_result", result)
            .put("dice_audit_json", diceAuditJson.orEmpty())
            .toString()
        appendAudit(
            dir,
            eventFor(
                workspace,
                "dice_roll_linked",
                "Dice: ${result.ifBlank { "result returned" }}",
                null,
                "dependency:dice.simulate",
                null,
                detail,
                null,
                null,
                null,
                nowIso
            )
        )
        return workspace
    }

    @Synchronized
    fun undoLast(workspaceId: String, nowIso: String = Instant.now().toString()): MutationOutcome? {
        val events = readAudit(workspaceId, limit = 500)
        val reversed = events.mapNotNull { it.reversesEventId }.toSet()
        val target = events.asReversed().firstOrNull { event ->
            event.undoMutationJson != null && event.eventId !in reversed && event.source != "undo"
        } ?: return null
        val mutation = mutationFromJson(JSONObject(target.undoMutationJson!!)) ?: return null
        return mutate(
            workspaceId = workspaceId,
            mutation = mutation,
            source = "undo",
            reversesEventId = target.eventId,
            nowIso = nowIso
        )
    }

    @Synchronized
    fun readAudit(workspaceId: String, limit: Int = 100): List<TabletopAuditEvent> {
        val file = auditFile(workspaceId)
        if (!file.exists()) return emptyList()
        val lines = file.useLines { it.toList() }
        return lines.takeLast(limit.coerceAtLeast(1)).mapNotNull { line ->
            runCatching { auditFromJson(JSONObject(line)) }.getOrNull()
        }
    }

    fun summary(workspace: GameWorkspace): String {
        val session = workspace.activeSession?.name ?: "No active session"
        val round = workspace.initiative.round
        val characterCount = workspace.characters.size
        val playerCount = workspace.players.size
        return buildString {
            append(workspace.name)
            if (workspace.ruleset.isNotBlank()) append(" · ${workspace.ruleset}")
            append(" · $session")
            if (TabletopFeature.INITIATIVE in workspace.features || TabletopFeature.EFFECTS in workspace.features) append(" · Round $round")
            if (characterCount > 0) append(" · $characterCount character${if (characterCount == 1) "" else "s"}")
            if (playerCount > 0) append(" · $playerCount player${if (playerCount == 1) "" else "s"}")
        }
    }

    fun workspaceJson(workspace: GameWorkspace): String = workspaceToJson(workspace).toString()

    fun auditJson(workspaceId: String, limit: Int = 500): String = JSONArray().apply {
        readAudit(workspaceId, limit).forEach { put(auditToJson(it)) }
    }.toString()

    private fun requireWorkspace(workspaceId: String): GameWorkspace =
        getWorkspace(workspaceId) ?: error("Workspace not found: $workspaceId")

    private fun workspacesRoot(): File = File(requireRoot(), "workspaces")
    private fun workspaceDir(workspaceId: String): File = File(workspacesRoot(), safeId(workspaceId))
    private fun stateFile(workspaceId: String): File = File(workspaceDir(workspaceId), "workspace.json")
    private fun auditFile(workspaceId: String): File = File(workspaceDir(workspaceId), "audit.jsonl")

    private fun requireRoot(): File = root ?: error("TabletopUtilitiesRepository is not initialised. Call initialise(context) first.")

    private fun safeId(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid workspace ID." }
        return value
    }

    private fun readWorkspace(dir: File): GameWorkspace = workspaceFromJson(JSONObject(File(dir, "workspace.json").readText()))

    private fun writeWorkspace(dir: File, workspace: GameWorkspace) {
        dir.mkdirs()
        val finalFile = File(dir, "workspace.json")
        val temp = File(dir, "workspace.json.tmp")
        temp.writeText(workspaceToJson(workspace).toString(2))
        if (finalFile.exists() && !finalFile.delete()) error("Could not replace workspace snapshot.")
        if (!temp.renameTo(finalFile)) error("Could not commit workspace snapshot.")
    }

    private fun appendAudit(dir: File, event: TabletopAuditEvent) {
        val file = File(dir, "audit.jsonl")
        file.appendText(auditToJson(event).toString() + "\n")
    }

    private fun eventFor(
        workspace: GameWorkspace,
        eventType: String,
        summary: String,
        entityId: String?,
        source: String,
        beforeValue: String?,
        afterValue: String?,
        undoMutationJson: String?,
        reversesEventId: String?,
        externalReference: String?,
        timestampIso: String
    ): TabletopAuditEvent = TabletopAuditEvent(
        eventId = UUID.randomUUID().toString(),
        workspaceId = workspace.id,
        sessionId = workspace.activeSession?.id,
        timestampIso = timestampIso,
        eventType = eventType,
        summary = summary,
        entityId = entityId,
        source = source,
        beforeValue = beforeValue,
        afterValue = afterValue,
        undoMutationJson = undoMutationJson,
        reversesEventId = reversesEventId,
        externalReference = externalReference,
        workspaceSha256 = sha256(workspaceToJson(workspace).toString())
    )

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun workspaceToJson(w: GameWorkspace): JSONObject = JSONObject()
        .put("schema_version", 1)
        .put("id", w.id)
        .put("name", w.name)
        .put("ruleset", w.ruleset)
        .put("features", JSONArray(w.features.map { it.wire }))
        .put("players", JSONArray().apply { w.players.forEach { put(JSONObject().put("id", it.id).put("name", it.name)) } })
        .put("characters", JSONArray().apply {
            w.characters.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("player_id", it.playerId).put("notes", it.notes)) }
        })
        .put("counters", JSONArray().apply {
            w.counters.forEach { c ->
                put(JSONObject()
                    .put("id", c.id).put("label", c.label).put("kind", c.kind.wire).put("scope", c.scope.wire)
                    .put("owner_id", c.ownerId).put("current", c.current).put("minimum", c.minimum).put("maximum", c.maximum)
                    .put("quick_steps", JSONArray(c.quickSteps)))
            }
        })
        .put("effects", JSONArray().apply {
            w.effects.forEach { e ->
                put(JSONObject().put("id", e.id).put("name", e.name).put("character_id", e.characterId)
                    .put("active", e.active).put("remaining_rounds", e.remainingRounds).put("auto_tick", e.autoTick))
            }
        })
        .put("death_saves", JSONArray().apply {
            w.deathSaves.forEach { d -> put(JSONObject().put("character_id", d.characterId).put("successes", d.successes).put("failures", d.failures)) }
        })
        .put("initiative", JSONObject()
            .put("active", w.initiative.active).put("current_index", w.initiative.currentIndex).put("round", w.initiative.round)
            .put("entries", JSONArray().apply {
                w.initiative.entries.forEach { e -> put(JSONObject().put("id", e.id).put("name", e.name).put("character_id", e.characterId).put("score", e.score)) }
            }))
        .put("sessions", JSONArray().apply {
            w.sessions.forEach { s -> put(JSONObject().put("id", s.id).put("name", s.name).put("started_at_iso", s.startedAtIso)
                .put("ended_at_iso", s.endedAtIso).put("notes", JSONArray(s.notes))) }
        })
        .put("scores", JSONArray().apply {
            w.scores.forEach { s -> put(JSONObject().put("id", s.id).put("player_id", s.playerId).put("player_name", s.playerName)
                .put("score", s.score).put("recorded_at_iso", s.recordedAtIso).put("session_id", s.sessionId)) }
        })
        .put("archived", w.archived)
        .put("created_at_iso", w.createdAtIso)
        .put("updated_at_iso", w.updatedAtIso)

    private fun workspaceFromJson(o: JSONObject): GameWorkspace = GameWorkspace(
        id = o.getString("id"),
        name = o.getString("name"),
        ruleset = o.optString("ruleset"),
        features = o.optJSONArray("features").strings().mapNotNull(TabletopFeature::fromWire).toSet(),
        players = o.optJSONArray("players").objects().map { GamePlayer(it.getString("id"), it.getString("name")) },
        characters = o.optJSONArray("characters").objects().map { GameCharacter(it.getString("id"), it.getString("name"), it.optNullableString("player_id"), it.optString("notes")) },
        counters = o.optJSONArray("counters").objects().map { c ->
            GameCounter(
                id = c.getString("id"), label = c.getString("label"), kind = CounterKind.fromWire(c.optString("kind")), scope = CounterScope.fromWire(c.optString("scope")),
                ownerId = c.optNullableString("owner_id"), current = c.optInt("current"), minimum = c.optNullableInt("minimum"), maximum = c.optNullableInt("maximum"),
                quickSteps = c.optJSONArray("quick_steps").ints().ifEmpty { listOf(-1, 1) }
            )
        },
        effects = o.optJSONArray("effects").objects().map { e -> GameEffect(e.getString("id"), e.getString("name"), e.optNullableString("character_id"), e.optBoolean("active", true), e.optNullableInt("remaining_rounds"), e.optBoolean("auto_tick", true)) },
        deathSaves = o.optJSONArray("death_saves").objects().map { d -> DeathSaveState(d.getString("character_id"), d.optInt("successes"), d.optInt("failures")) },
        initiative = o.optJSONObject("initiative")?.let { i ->
            InitiativeState(
                entries = i.optJSONArray("entries").objects().map { e -> InitiativeEntry(e.getString("id"), e.getString("name"), e.optNullableString("character_id"), e.optInt("score")) },
                currentIndex = i.optInt("current_index"), round = i.optInt("round", 1), active = i.optBoolean("active")
            )
        } ?: InitiativeState(),
        sessions = o.optJSONArray("sessions").objects().map { s -> SessionRecord(s.getString("id"), s.getString("name"), s.getString("started_at_iso"), s.optNullableString("ended_at_iso"), s.optJSONArray("notes").strings()) },
        scores = o.optJSONArray("scores").objects().map { s -> ScoreRecord(s.getString("id"), s.getString("player_id"), s.getString("player_name"), s.optInt("score"), s.getString("recorded_at_iso"), s.optNullableString("session_id")) },
        archived = o.optBoolean("archived"),
        createdAtIso = o.optString("created_at_iso"),
        updatedAtIso = o.optString("updated_at_iso")
    )

    private fun auditToJson(e: TabletopAuditEvent): JSONObject = JSONObject()
        .put("event_id", e.eventId).put("workspace_id", e.workspaceId).put("session_id", e.sessionId).put("timestamp_iso", e.timestampIso)
        .put("event_type", e.eventType).put("summary", e.summary).put("entity_id", e.entityId).put("source", e.source)
        .put("before", e.beforeValue).put("after", e.afterValue).put("undo_mutation_json", e.undoMutationJson)
        .put("reverses_event_id", e.reversesEventId).put("external_reference", e.externalReference).put("workspace_sha256", e.workspaceSha256)

    private fun auditFromJson(o: JSONObject): TabletopAuditEvent = TabletopAuditEvent(
        eventId = o.getString("event_id"), workspaceId = o.getString("workspace_id"), sessionId = o.optNullableString("session_id"), timestampIso = o.getString("timestamp_iso"),
        eventType = o.getString("event_type"), summary = o.optString("summary"), entityId = o.optNullableString("entity_id"), source = o.optString("source"),
        beforeValue = o.optNullableString("before"), afterValue = o.optNullableString("after"), undoMutationJson = o.optNullableString("undo_mutation_json"),
        reversesEventId = o.optNullableString("reverses_event_id"), externalReference = o.optNullableString("external_reference"), workspaceSha256 = o.optString("workspace_sha256")
    )

    private fun mutationToJson(m: TabletopMutation): JSONObject = when (m) {
        is TabletopMutation.AdjustCounter -> JSONObject().put("type", "adjust_counter").put("counter_id", m.counterId).put("delta", m.delta)
        is TabletopMutation.SetCounter -> JSONObject().put("type", "set_counter").put("counter_id", m.counterId).put("value", m.value)
        is TabletopMutation.ToggleEffect -> JSONObject().put("type", "toggle_effect").put("effect_id", m.effectId)
        is TabletopMutation.SetEffectDuration -> JSONObject().put("type", "set_effect_duration").put("effect_id", m.effectId).put("remaining", m.remainingRounds)
        is TabletopMutation.SetDeathSaves -> JSONObject().put("type", "set_death_saves").put("character_id", m.characterId).put("successes", m.successes).put("failures", m.failures)
        is TabletopMutation.AdjustInitiativeScore -> JSONObject().put("type", "adjust_initiative_score").put("entry_id", m.entryId).put("delta", m.delta)
        else -> JSONObject().put("type", "unsupported")
    }

    private fun mutationFromJson(o: JSONObject): TabletopMutation? = when (o.optString("type")) {
        "adjust_counter" -> TabletopMutation.AdjustCounter(o.getString("counter_id"), o.getInt("delta"))
        "set_counter" -> TabletopMutation.SetCounter(o.getString("counter_id"), o.getInt("value"))
        "toggle_effect" -> TabletopMutation.ToggleEffect(o.getString("effect_id"))
        "set_effect_duration" -> TabletopMutation.SetEffectDuration(o.getString("effect_id"), o.optNullableInt("remaining"))
        "set_death_saves" -> TabletopMutation.SetDeathSaves(o.getString("character_id"), o.getInt("successes"), o.getInt("failures"))
        "adjust_initiative_score" -> TabletopMutation.AdjustInitiativeScore(o.getString("entry_id"), o.getInt("delta"))
        else -> null
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    private fun JSONArray?.ints(): List<Int> = if (this == null) emptyList() else (0 until length()).map { optInt(it) }
    private fun JSONObject.optNullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf(String::isNotBlank)
    private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
}
