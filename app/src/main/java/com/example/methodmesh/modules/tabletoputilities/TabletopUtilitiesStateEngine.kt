package com.example.methodmesh.modules.tabletoputilities

object TabletopUtilitiesStateEngine {

    fun apply(workspace: GameWorkspace, mutation: TabletopMutation, nowIso: String): MutationOutcome = when (mutation) {
        is TabletopMutation.AdjustCounter -> adjustCounter(workspace, mutation.counterId, mutation.delta, nowIso)
        is TabletopMutation.SetCounter -> setCounter(workspace, mutation.counterId, mutation.value, nowIso)
        is TabletopMutation.AddCounter -> addCounter(workspace, mutation.counter, nowIso)
        is TabletopMutation.AddPlayer -> addPlayer(workspace, mutation.player, nowIso)
        is TabletopMutation.AddCharacter -> addCharacter(workspace, mutation.character, mutation.initialHpMaximum, nowIso)
        is TabletopMutation.AddEffect -> addEffect(workspace, mutation.effect, nowIso)
        is TabletopMutation.ToggleEffect -> toggleEffect(workspace, mutation.effectId, nowIso)
        is TabletopMutation.SetEffectDuration -> setEffectDuration(workspace, mutation.effectId, mutation.remainingRounds, nowIso)
        is TabletopMutation.SetDeathSaves -> setDeathSaves(workspace, mutation.characterId, mutation.successes, mutation.failures, nowIso)
        is TabletopMutation.AddInitiativeEntry -> addInitiativeEntry(workspace, mutation.entry, nowIso)
        is TabletopMutation.AdjustInitiativeScore -> adjustInitiativeScore(workspace, mutation.entryId, mutation.delta, nowIso)
        TabletopMutation.StartInitiative -> startInitiative(workspace, nowIso)
        TabletopMutation.NextTurn -> nextTurn(workspace, nowIso)
        TabletopMutation.NextRound -> nextRound(workspace, nowIso)
        is TabletopMutation.StartSession -> startSession(workspace, mutation.name, mutation.atIso, nowIso)
        is TabletopMutation.AddSessionNote -> addSessionNote(workspace, mutation.note, nowIso)
        is TabletopMutation.FinishSession -> finishSession(workspace, mutation.atIso, nowIso)
        is TabletopMutation.RecordScores -> recordScores(workspace, mutation.atIso, nowIso)
    }

    private fun adjustCounter(workspace: GameWorkspace, counterId: String, delta: Int, nowIso: String): MutationOutcome {
        val existing = workspace.counters.firstOrNull { it.id == counterId } ?: error("Counter not found: $counterId")
        val target = clamp(existing.current + delta, existing.minimum, existing.maximum)
        val updated = existing.copy(current = target)
        return MutationOutcome(
            workspace = workspace.copy(counters = workspace.counters.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = "counter_adjusted",
            summary = "${existing.label}: ${existing.current} → $target",
            entityId = existing.id,
            beforeValue = existing.current.toString(),
            afterValue = target.toString(),
            undoMutation = TabletopMutation.SetCounter(existing.id, existing.current)
        )
    }

    private fun setCounter(workspace: GameWorkspace, counterId: String, value: Int, nowIso: String): MutationOutcome {
        val existing = workspace.counters.firstOrNull { it.id == counterId } ?: error("Counter not found: $counterId")
        val target = clamp(value, existing.minimum, existing.maximum)
        val updated = existing.copy(current = target)
        return MutationOutcome(
            workspace = workspace.copy(counters = workspace.counters.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = "counter_set",
            summary = "${existing.label}: ${existing.current} → $target",
            entityId = existing.id,
            beforeValue = existing.current.toString(),
            afterValue = target.toString(),
            undoMutation = TabletopMutation.SetCounter(existing.id, existing.current)
        )
    }

    private fun addCounter(workspace: GameWorkspace, counter: GameCounter, nowIso: String): MutationOutcome {
        require(counter.label.isNotBlank()) { "Counter name is required." }
        require(workspace.counters.none { it.id == counter.id }) { "Counter ID already exists." }
        return MutationOutcome(
            workspace = workspace.copy(counters = workspace.counters + counter, updatedAtIso = nowIso),
            eventType = "counter_created",
            summary = "Created ${counter.label}",
            entityId = counter.id,
            afterValue = counter.current.toString()
        )
    }

    private fun addPlayer(workspace: GameWorkspace, player: GamePlayer, nowIso: String): MutationOutcome {
        require(player.name.isNotBlank()) { "Player name is required." }
        val scoreCounter = if (TabletopFeature.SCORES in workspace.features) {
            GameCounter(label = "Score", kind = CounterKind.SCORE, scope = CounterScope.PLAYER, ownerId = player.id, current = 0, quickSteps = listOf(-10, -1, 1, 10))
        } else null
        return MutationOutcome(
            workspace = workspace.copy(
                players = workspace.players + player,
                counters = workspace.counters + listOfNotNull(scoreCounter),
                updatedAtIso = nowIso
            ),
            eventType = "player_created",
            summary = "Added player ${player.name}",
            entityId = player.id
        )
    }

    private fun addCharacter(workspace: GameWorkspace, character: GameCharacter, hpMaximum: Int, nowIso: String): MutationOutcome {
        require(character.name.isNotBlank()) { "Character name is required." }
        val createdCounters = buildList {
            if (TabletopFeature.HP in workspace.features) add(
                GameCounter(label = "HP", kind = CounterKind.HP, scope = CounterScope.CHARACTER, ownerId = character.id, current = hpMaximum.coerceAtLeast(1), minimum = 0, maximum = hpMaximum.coerceAtLeast(1), quickSteps = listOf(-1, 1))
            )
            if (TabletopFeature.TEMP_HP in workspace.features) add(
                GameCounter(label = "Temp HP", kind = CounterKind.TEMP_HP, scope = CounterScope.CHARACTER, ownerId = character.id, current = 0, minimum = 0, quickSteps = listOf(-1, 1))
            )
            if (TabletopFeature.EXP in workspace.features) add(
                GameCounter(label = "EXP", kind = CounterKind.EXP, scope = CounterScope.CHARACTER, ownerId = character.id, current = 0, minimum = 0, quickSteps = listOf(-100, -10, 10, 100))
            )
        }
        val saves = if (TabletopFeature.DEATH_SAVES in workspace.features) {
            workspace.deathSaves + DeathSaveState(character.id)
        } else workspace.deathSaves
        return MutationOutcome(
            workspace = workspace.copy(
                characters = workspace.characters + character,
                counters = workspace.counters + createdCounters,
                deathSaves = saves,
                updatedAtIso = nowIso
            ),
            eventType = "character_created",
            summary = "Added character ${character.name}",
            entityId = character.id
        )
    }

    private fun addEffect(workspace: GameWorkspace, effect: GameEffect, nowIso: String): MutationOutcome {
        require(effect.name.isNotBlank()) { "Effect name is required." }
        return MutationOutcome(
            workspace = workspace.copy(effects = workspace.effects + effect, updatedAtIso = nowIso),
            eventType = "effect_created",
            summary = "Added effect ${effect.name}",
            entityId = effect.id,
            afterValue = effect.remainingRounds?.toString() ?: "active"
        )
    }

    private fun toggleEffect(workspace: GameWorkspace, effectId: String, nowIso: String): MutationOutcome {
        val existing = workspace.effects.firstOrNull { it.id == effectId } ?: error("Effect not found: $effectId")
        val updated = existing.copy(active = !existing.active)
        return MutationOutcome(
            workspace = workspace.copy(effects = workspace.effects.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = if (updated.active) "effect_enabled" else "effect_disabled",
            summary = "${existing.name}: ${if (updated.active) "on" else "off"}",
            entityId = existing.id,
            beforeValue = existing.active.toString(),
            afterValue = updated.active.toString(),
            undoMutation = TabletopMutation.ToggleEffect(existing.id)
        )
    }

    private fun setEffectDuration(workspace: GameWorkspace, effectId: String, remaining: Int?, nowIso: String): MutationOutcome {
        val existing = workspace.effects.firstOrNull { it.id == effectId } ?: error("Effect not found: $effectId")
        val target = remaining?.coerceAtLeast(0)
        val updated = existing.copy(remainingRounds = target, active = target == null || target > 0)
        return MutationOutcome(
            workspace = workspace.copy(effects = workspace.effects.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = "effect_duration_changed",
            summary = "${existing.name}: ${existing.remainingRounds ?: "∞"} → ${target ?: "∞"}",
            entityId = existing.id,
            beforeValue = existing.remainingRounds?.toString() ?: "infinite",
            afterValue = target?.toString() ?: "infinite",
            undoMutation = TabletopMutation.SetEffectDuration(existing.id, existing.remainingRounds)
        )
    }

    private fun setDeathSaves(workspace: GameWorkspace, characterId: String, successes: Int, failures: Int, nowIso: String): MutationOutcome {
        val old = workspace.deathSaves.firstOrNull { it.characterId == characterId } ?: DeathSaveState(characterId)
        val updated = DeathSaveState(characterId, successes.coerceIn(0, 3), failures.coerceIn(0, 3))
        val list = workspace.deathSaves.filterNot { it.characterId == characterId } + updated
        return MutationOutcome(
            workspace = workspace.copy(deathSaves = list, updatedAtIso = nowIso),
            eventType = "death_save_changed",
            summary = "Death saves: ${old.successes}/${old.failures} → ${updated.successes}/${updated.failures}",
            entityId = characterId,
            beforeValue = "${old.successes},${old.failures}",
            afterValue = "${updated.successes},${updated.failures}",
            undoMutation = TabletopMutation.SetDeathSaves(characterId, old.successes, old.failures)
        )
    }

    private fun addInitiativeEntry(workspace: GameWorkspace, entry: InitiativeEntry, nowIso: String): MutationOutcome {
        val oldCurrentId = workspace.initiative.current?.id
        val entries = (workspace.initiative.entries + entry).sortedByDescending { it.score }
        val currentIndex = oldCurrentId?.let { id -> entries.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        return MutationOutcome(
            workspace = workspace.copy(initiative = workspace.initiative.copy(entries = entries, currentIndex = currentIndex), updatedAtIso = nowIso),
            eventType = "initiative_entry_added",
            summary = "Initiative: added ${entry.name} (${entry.score})",
            entityId = entry.id
        )
    }

    private fun adjustInitiativeScore(workspace: GameWorkspace, entryId: String, delta: Int, nowIso: String): MutationOutcome {
        val existing = workspace.initiative.entries.firstOrNull { it.id == entryId } ?: error("Initiative entry not found: $entryId")
        val oldCurrentId = workspace.initiative.current?.id
        val changed = existing.copy(score = existing.score + delta)
        val entries = workspace.initiative.entries.replaceById(changed) { it.id }.sortedByDescending { it.score }
        val currentIndex = oldCurrentId?.let { id -> entries.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: 0
        return MutationOutcome(
            workspace = workspace.copy(initiative = workspace.initiative.copy(entries = entries, currentIndex = currentIndex), updatedAtIso = nowIso),
            eventType = "initiative_score_changed",
            summary = "${existing.name} initiative: ${existing.score} → ${changed.score}",
            entityId = existing.id,
            beforeValue = existing.score.toString(),
            afterValue = changed.score.toString(),
            undoMutation = TabletopMutation.AdjustInitiativeScore(existing.id, -delta)
        )
    }

    private fun startInitiative(workspace: GameWorkspace, nowIso: String): MutationOutcome {
        require(workspace.initiative.entries.isNotEmpty()) { "Add at least one initiative entry first." }
        val updated = workspace.initiative.copy(active = true, currentIndex = 0, round = 1)
        return MutationOutcome(
            workspace = workspace.copy(initiative = updated, updatedAtIso = nowIso),
            eventType = "initiative_started",
            summary = "Initiative started · round 1 · ${updated.current?.name.orEmpty()}",
            afterValue = updated.current?.name
        )
    }

    private fun nextTurn(workspace: GameWorkspace, nowIso: String): MutationOutcome {
        val initiative = workspace.initiative
        require(initiative.active && initiative.entries.isNotEmpty()) { "Initiative is not active." }
        val old = initiative.current
        val wrapped = initiative.currentIndex >= initiative.entries.lastIndex
        val nextIndex = if (wrapped) 0 else initiative.currentIndex + 1
        val nextRound = if (wrapped) initiative.round + 1 else initiative.round
        var nextWorkspace = workspace.copy(initiative = initiative.copy(currentIndex = nextIndex, round = nextRound), updatedAtIso = nowIso)
        if (wrapped) nextWorkspace = tickRoundEffects(nextWorkspace, nowIso)
        return MutationOutcome(
            workspace = nextWorkspace,
            eventType = if (wrapped) "round_advanced" else "turn_advanced",
            summary = if (wrapped) "Round $nextRound · ${nextWorkspace.initiative.current?.name.orEmpty()}" else "${old?.name.orEmpty()} → ${nextWorkspace.initiative.current?.name.orEmpty()}",
            beforeValue = old?.name,
            afterValue = nextWorkspace.initiative.current?.name
        )
    }

    private fun nextRound(workspace: GameWorkspace, nowIso: String): MutationOutcome {
        val old = workspace.initiative.round
        val next = workspace.copy(initiative = workspace.initiative.copy(round = old + 1), updatedAtIso = nowIso)
        val ticked = tickRoundEffects(next, nowIso)
        return MutationOutcome(
            workspace = ticked,
            eventType = "round_advanced",
            summary = "Round $old → ${old + 1}",
            beforeValue = old.toString(),
            afterValue = (old + 1).toString()
        )
    }

    private fun tickRoundEffects(workspace: GameWorkspace, nowIso: String): GameWorkspace {
        val effects = workspace.effects.map { effect ->
            if (!effect.active || !effect.autoTick || effect.remainingRounds == null) effect
            else {
                val next = (effect.remainingRounds - 1).coerceAtLeast(0)
                effect.copy(remainingRounds = next, active = next > 0)
            }
        }
        return workspace.copy(effects = effects, updatedAtIso = nowIso)
    }

    private fun startSession(workspace: GameWorkspace, name: String, atIso: String, nowIso: String): MutationOutcome {
        require(workspace.activeSession == null) { "A session is already active." }
        val session = SessionRecord(name = name.ifBlank { "Session ${workspace.sessions.size + 1}" }, startedAtIso = atIso)
        return MutationOutcome(
            workspace = workspace.copy(sessions = workspace.sessions + session, updatedAtIso = nowIso),
            eventType = "session_started",
            summary = "Started ${session.name}",
            entityId = session.id
        )
    }

    private fun addSessionNote(workspace: GameWorkspace, note: String, nowIso: String): MutationOutcome {
        require(note.isNotBlank()) { "Session note is blank." }
        val active = workspace.activeSession ?: error("No session is active.")
        val updated = active.copy(notes = active.notes + note.trim())
        return MutationOutcome(
            workspace = workspace.copy(sessions = workspace.sessions.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = "session_note",
            summary = note.trim(),
            entityId = active.id
        )
    }

    private fun finishSession(workspace: GameWorkspace, atIso: String, nowIso: String): MutationOutcome {
        val active = workspace.activeSession ?: error("No session is active.")
        val updated = active.copy(endedAtIso = atIso)
        return MutationOutcome(
            workspace = workspace.copy(sessions = workspace.sessions.replaceById(updated) { it.id }, updatedAtIso = nowIso),
            eventType = "session_finished",
            summary = "Finished ${active.name}",
            entityId = active.id
        )
    }

    private fun recordScores(workspace: GameWorkspace, atIso: String, nowIso: String): MutationOutcome {
        val scoreCounters = workspace.counters.filter { it.kind == CounterKind.SCORE && it.scope == CounterScope.PLAYER }
        require(scoreCounters.isNotEmpty()) { "No player scores are configured." }
        val sessionId = workspace.activeSession?.id
        val records = scoreCounters.mapNotNull { counter ->
            val player = workspace.players.firstOrNull { it.id == counter.ownerId } ?: return@mapNotNull null
            ScoreRecord(playerId = player.id, playerName = player.name, score = counter.current, recordedAtIso = atIso, sessionId = sessionId)
        }
        val winner = records.maxByOrNull { it.score }
        return MutationOutcome(
            workspace = workspace.copy(scores = workspace.scores + records, updatedAtIso = nowIso),
            eventType = "scores_recorded",
            summary = winner?.let { "Scores recorded · ${it.playerName} ${it.score}" } ?: "Scores recorded",
            afterValue = records.joinToString(",") { "${it.playerName}:${it.score}" }
        )
    }

    private fun clamp(value: Int, minimum: Int?, maximum: Int?): Int {
        var result = value
        if (minimum != null) result = result.coerceAtLeast(minimum)
        if (maximum != null) result = result.coerceAtMost(maximum)
        return result
    }

    private fun <T> List<T>.replaceById(newValue: T, id: (T) -> String): List<T> = map { if (id(it) == id(newValue)) newValue else it }
}
