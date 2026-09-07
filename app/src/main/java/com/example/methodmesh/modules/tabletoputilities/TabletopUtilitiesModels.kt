package com.example.methodmesh.modules.tabletoputilities

import java.util.UUID

enum class TabletopFeature(val wire: String, val label: String) {
    HP("hp", "Hit points"),
    TEMP_HP("temp_hp", "Temporary HP"),
    EXP("exp", "Experience"),
    RESOURCES("resources", "Resources"),
    EFFECTS("effects", "Buffs / effects"),
    DEATH_SAVES("death_saves", "Death saves"),
    INITIATIVE("initiative", "Initiative"),
    COUNTERS("counters", "Generic counters"),
    CHARACTERS("characters", "Character cards"),
    SCORES("scores", "Player scores"),
    SESSIONS("sessions", "Campaign / sessions");

    companion object {
        fun fromWire(value: String): TabletopFeature? = entries.firstOrNull { it.wire == value }
    }
}

enum class CounterKind(val wire: String) {
    HP("hp"), TEMP_HP("temp_hp"), EXP("exp"), RESOURCE("resource"), GENERIC("generic"), SCORE("score");

    companion object {
        fun fromWire(value: String): CounterKind = entries.firstOrNull { it.wire == value } ?: GENERIC
    }
}

enum class CounterScope(val wire: String) {
    WORKSPACE("workspace"), PLAYER("player"), CHARACTER("character");

    companion object {
        fun fromWire(value: String): CounterScope = entries.firstOrNull { it.wire == value } ?: WORKSPACE
    }
}

data class GameCounter(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val kind: CounterKind = CounterKind.GENERIC,
    val scope: CounterScope = CounterScope.WORKSPACE,
    val ownerId: String? = null,
    val current: Int = 0,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val quickSteps: List<Int> = listOf(-1, 1)
)

data class GamePlayer(
    val id: String = UUID.randomUUID().toString(),
    val name: String
)

data class GameCharacter(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val playerId: String? = null,
    val notes: String = ""
)

data class GameEffect(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val characterId: String? = null,
    val active: Boolean = true,
    val remainingRounds: Int? = null,
    val autoTick: Boolean = true
)

data class DeathSaveState(
    val characterId: String,
    val successes: Int = 0,
    val failures: Int = 0
)

data class InitiativeEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val characterId: String? = null,
    val score: Int = 0
)

data class InitiativeState(
    val entries: List<InitiativeEntry> = emptyList(),
    val currentIndex: Int = 0,
    val round: Int = 1,
    val active: Boolean = false
) {
    val current: InitiativeEntry?
        get() = entries.getOrNull(currentIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0)))
}

data class SessionRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val startedAtIso: String,
    val endedAtIso: String? = null,
    val notes: List<String> = emptyList()
)

data class ScoreRecord(
    val id: String = UUID.randomUUID().toString(),
    val playerId: String,
    val playerName: String,
    val score: Int,
    val recordedAtIso: String,
    val sessionId: String? = null
)

data class GameWorkspace(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ruleset: String = "",
    val features: Set<TabletopFeature> = setOf(
        TabletopFeature.HP,
        TabletopFeature.TEMP_HP,
        TabletopFeature.EXP,
        TabletopFeature.EFFECTS,
        TabletopFeature.DEATH_SAVES,
        TabletopFeature.INITIATIVE,
        TabletopFeature.CHARACTERS,
        TabletopFeature.SESSIONS
    ),
    val players: List<GamePlayer> = emptyList(),
    val characters: List<GameCharacter> = emptyList(),
    val counters: List<GameCounter> = emptyList(),
    val effects: List<GameEffect> = emptyList(),
    val deathSaves: List<DeathSaveState> = emptyList(),
    val initiative: InitiativeState = InitiativeState(),
    val sessions: List<SessionRecord> = emptyList(),
    val scores: List<ScoreRecord> = emptyList(),
    val archived: Boolean = false,
    val createdAtIso: String,
    val updatedAtIso: String
) {
    val activeSession: SessionRecord?
        get() = sessions.lastOrNull { it.endedAtIso == null }

    fun countersForCharacter(characterId: String): List<GameCounter> =
        counters.filter { it.scope == CounterScope.CHARACTER && it.ownerId == characterId }

    fun countersForPlayer(playerId: String): List<GameCounter> =
        counters.filter { it.scope == CounterScope.PLAYER && it.ownerId == playerId }

    fun workspaceCounters(): List<GameCounter> =
        counters.filter { it.scope == CounterScope.WORKSPACE }
}

sealed interface TabletopMutation {
    data class AdjustCounter(val counterId: String, val delta: Int) : TabletopMutation
    data class SetCounter(val counterId: String, val value: Int) : TabletopMutation
    data class AddCounter(val counter: GameCounter) : TabletopMutation
    data class AddPlayer(val player: GamePlayer) : TabletopMutation
    data class AddCharacter(val character: GameCharacter, val initialHpMaximum: Int = 10) : TabletopMutation
    data class AddEffect(val effect: GameEffect) : TabletopMutation
    data class ToggleEffect(val effectId: String) : TabletopMutation
    data class SetEffectDuration(val effectId: String, val remainingRounds: Int?) : TabletopMutation
    data class SetDeathSaves(val characterId: String, val successes: Int, val failures: Int) : TabletopMutation
    data class AddInitiativeEntry(val entry: InitiativeEntry) : TabletopMutation
    data class AdjustInitiativeScore(val entryId: String, val delta: Int) : TabletopMutation
    data object StartInitiative : TabletopMutation
    data object NextTurn : TabletopMutation
    data object NextRound : TabletopMutation
    data class StartSession(val name: String, val atIso: String) : TabletopMutation
    data class AddSessionNote(val note: String) : TabletopMutation
    data class FinishSession(val atIso: String) : TabletopMutation
    data class RecordScores(val atIso: String) : TabletopMutation
}

data class MutationOutcome(
    val workspace: GameWorkspace,
    val eventType: String,
    val summary: String,
    val entityId: String? = null,
    val beforeValue: String? = null,
    val afterValue: String? = null,
    val undoMutation: TabletopMutation? = null
)

data class TabletopAuditEvent(
    val eventId: String,
    val workspaceId: String,
    val sessionId: String?,
    val timestampIso: String,
    val eventType: String,
    val summary: String,
    val entityId: String?,
    val source: String,
    val beforeValue: String?,
    val afterValue: String?,
    val undoMutationJson: String?,
    val reversesEventId: String?,
    val externalReference: String?,
    val workspaceSha256: String
)
