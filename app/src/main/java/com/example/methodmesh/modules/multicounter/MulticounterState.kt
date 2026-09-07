package com.example.methodmesh.modules.multicounter

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.max

internal data class MulticounterEvent(
    val offsetMs: Long,
    val eventType: String,
    val entityIndex: Int? = null,
    val entityLabel: String? = null,
    val oldValue: String? = null,
    val newValue: String? = null,
    val wallTimeIso: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("offset_ms", offsetMs)
        put("event_type", eventType)
        entityIndex?.let { put("entity_index", it) }
        entityLabel?.let { put("entity_label", it) }
        oldValue?.let { put("old_value", it) }
        newValue?.let { put("new_value", it) }
        wallTimeIso?.let { put("wall_time_iso", it) }
    }

    companion object {
        fun fromJson(json: JSONObject) = MulticounterEvent(
            offsetMs = json.optLong("offset_ms", 0L),
            eventType = json.optString("event_type", "unknown"),
            entityIndex = json.nullableInt("entity_index"),
            entityLabel = json.nullableString("entity_label"),
            oldValue = json.nullableString("old_value"),
            newValue = json.nullableString("new_value"),
            wallTimeIso = json.nullableString("wall_time_iso")
        )
    }
}

internal data class MulticounterEntity(
    val label: String,
    val value: Int,
    val elapsedMs: Long = 0L,
    val runningSinceRealtimeMs: Long? = null,
    val pendingStartRealtimeMs: Long? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("value", value)
        put("elapsed_ms", elapsedMs)
        putNullable("running_since_realtime_ms", runningSinceRealtimeMs)
        putNullable("pending_start_realtime_ms", pendingStartRealtimeMs)
    }

    companion object {
        fun fromJson(json: JSONObject) = MulticounterEntity(
            label = json.optString("label", "Counter"),
            value = json.optInt("value", 0),
            elapsedMs = json.optLong("elapsed_ms", 0L).coerceAtLeast(0L),
            runningSinceRealtimeMs = json.nullableLong("running_since_realtime_ms"),
            pendingStartRealtimeMs = json.nullableLong("pending_start_realtime_ms")
        )
    }
}

internal data class MulticounterSessionState(
    val schema: String = SCHEMA,
    val mode: String,
    val timerMode: String,
    val timerPrecision: String,
    val counterStart: Int,
    val step: Int,
    val allowNegative: Boolean,
    val countdownMs: Long,
    val staggerMs: Long,
    val showTotal: Boolean,
    val showLeader: Boolean,
    val startedAtIso: String,
    val startedAtRealtimeMs: Long,
    val entities: List<MulticounterEntity>,
    val events: List<MulticounterEvent> = emptyList(),
    val droppedEventCount: Int = 0
) {
    val showsCounter: Boolean get() = mode == MODE_COUNT || mode == MODE_COUNT_AND_TIME
    val showsTimer: Boolean get() = mode == MODE_TIME || mode == MODE_COUNT_AND_TIME
    val hasActiveTimers: Boolean get() = showsTimer && entities.any {
        it.runningSinceRealtimeMs != null || it.pendingStartRealtimeMs != null
    }

    fun currentElapsedMs(entity: MulticounterEntity, nowRealtimeMs: Long): Long {
        val runningExtra = entity.runningSinceRealtimeMs
            ?.let { max(0L, nowRealtimeMs - it) }
            ?: 0L
        return (entity.elapsedMs + runningExtra).coerceAtLeast(0L)
    }

    fun displayTimerMs(entity: MulticounterEntity, nowRealtimeMs: Long): Long {
        val elapsed = currentElapsedMs(entity, nowRealtimeMs)
        return if (timerMode == TIMER_COUNTDOWN) {
            (countdownMs - elapsed).coerceAtLeast(0L)
        } else {
            elapsed
        }
    }

    fun normalize(nowRealtimeMs: Long, wallTimeIso: String = Instant.now().toString()): MulticounterSessionState {
        if (!showsTimer) return this
        var state = this
        entities.indices.forEach { index ->
            var entity = state.entities[index]
            val pending = entity.pendingStartRealtimeMs
            if (pending != null && nowRealtimeMs >= pending) {
                entity = entity.copy(
                    runningSinceRealtimeMs = pending,
                    pendingStartRealtimeMs = null
                )
                state = state.replaceEntity(index, entity).withEvent(
                    eventType = "timer_started",
                    entityIndex = index,
                    oldValue = "scheduled",
                    newValue = "running",
                    nowRealtimeMs = nowRealtimeMs,
                    wallTimeIso = wallTimeIso
                )
            }

            entity = state.entities[index]
            if (timerMode == TIMER_COUNTDOWN && entity.runningSinceRealtimeMs != null) {
                val elapsed = state.currentElapsedMs(entity, nowRealtimeMs)
                if (elapsed >= countdownMs) {
                    entity = entity.copy(
                        elapsedMs = countdownMs,
                        runningSinceRealtimeMs = null,
                        pendingStartRealtimeMs = null
                    )
                    state = state.replaceEntity(index, entity).withEvent(
                        eventType = "timer_expired",
                        entityIndex = index,
                        oldValue = "running",
                        newValue = "0",
                        nowRealtimeMs = nowRealtimeMs,
                        wallTimeIso = wallTimeIso
                    )
                }
            }
        }
        return state
    }

    fun changeValue(
        entityIndex: Int,
        direction: Int,
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsCounter || entityIndex !in entities.indices || direction == 0) return this
        val entity = entities[entityIndex]
        val candidate = entity.value.toLong() + step.toLong() * direction.toLong()
        val bounded = candidate.coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val next = if (allowNegative) bounded else bounded.coerceAtLeast(0)
        if (next == entity.value) return this
        return replaceEntity(entityIndex, entity.copy(value = next)).withEvent(
            eventType = if (direction > 0) "counter_incremented" else "counter_decremented",
            entityIndex = entityIndex,
            oldValue = entity.value.toString(),
            newValue = next.toString(),
            nowRealtimeMs = nowRealtimeMs,
            wallTimeIso = wallTimeIso
        )
    }

    fun toggleTimer(
        entityIndex: Int,
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsTimer || entityIndex !in entities.indices) return this
        var state = normalize(nowRealtimeMs, wallTimeIso)
        var entity = state.entities[entityIndex]

        if (entity.pendingStartRealtimeMs != null) {
            entity = entity.copy(pendingStartRealtimeMs = null)
            return state.replaceEntity(entityIndex, entity).withEvent(
                eventType = "timer_schedule_cancelled",
                entityIndex = entityIndex,
                oldValue = "scheduled",
                newValue = "paused",
                nowRealtimeMs = nowRealtimeMs,
                wallTimeIso = wallTimeIso
            )
        }

        val runningSince = entity.runningSinceRealtimeMs
        if (runningSince != null) {
            val elapsed = state.currentElapsedMs(entity, nowRealtimeMs)
                .let { if (timerMode == TIMER_COUNTDOWN) it.coerceAtMost(countdownMs) else it }
            entity = entity.copy(elapsedMs = elapsed, runningSinceRealtimeMs = null)
            return state.replaceEntity(entityIndex, entity).withEvent(
                eventType = "timer_paused",
                entityIndex = entityIndex,
                oldValue = "running",
                newValue = elapsed.toString(),
                nowRealtimeMs = nowRealtimeMs,
                wallTimeIso = wallTimeIso
            )
        }

        val restartExpiredCountdown = timerMode == TIMER_COUNTDOWN && entity.elapsedMs >= countdownMs
        if (restartExpiredCountdown) entity = entity.copy(elapsedMs = 0L)
        entity = entity.copy(runningSinceRealtimeMs = nowRealtimeMs)
        return state.replaceEntity(entityIndex, entity).withEvent(
            eventType = if (restartExpiredCountdown) "timer_restarted" else "timer_started",
            entityIndex = entityIndex,
            oldValue = if (restartExpiredCountdown) "expired" else "paused",
            newValue = "running",
            nowRealtimeMs = nowRealtimeMs,
            wallTimeIso = wallTimeIso
        )
    }

    fun startAll(
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsTimer) return this
        var state = normalize(nowRealtimeMs, wallTimeIso)
        state.entities.indices.forEach { index ->
            var entity = state.entities[index]
            if (entity.runningSinceRealtimeMs != null || entity.pendingStartRealtimeMs != null) return@forEach
            if (timerMode == TIMER_COUNTDOWN && entity.elapsedMs >= countdownMs) {
                entity = entity.copy(elapsedMs = 0L)
            }
            val scheduled = nowRealtimeMs + index * staggerMs
            entity = if (scheduled <= nowRealtimeMs) {
                entity.copy(runningSinceRealtimeMs = nowRealtimeMs, pendingStartRealtimeMs = null)
            } else {
                entity.copy(runningSinceRealtimeMs = null, pendingStartRealtimeMs = scheduled)
            }
            state = state.replaceEntity(index, entity).withEvent(
                eventType = if (scheduled <= nowRealtimeMs) "timer_started" else "timer_scheduled",
                entityIndex = index,
                oldValue = "paused",
                newValue = if (scheduled <= nowRealtimeMs) "running" else (scheduled - nowRealtimeMs).toString(),
                nowRealtimeMs = nowRealtimeMs,
                wallTimeIso = wallTimeIso
            )
        }
        return state
    }

    fun pauseAll(
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsTimer) return this
        var state = normalize(nowRealtimeMs, wallTimeIso)
        state.entities.indices.forEach { index ->
            val entity = state.entities[index]
            when {
                entity.runningSinceRealtimeMs != null -> {
                    val elapsed = state.currentElapsedMs(entity, nowRealtimeMs)
                        .let { if (timerMode == TIMER_COUNTDOWN) it.coerceAtMost(countdownMs) else it }
                    state = state.replaceEntity(
                        index,
                        entity.copy(elapsedMs = elapsed, runningSinceRealtimeMs = null, pendingStartRealtimeMs = null)
                    ).withEvent(
                        eventType = "timer_paused",
                        entityIndex = index,
                        oldValue = "running",
                        newValue = elapsed.toString(),
                        nowRealtimeMs = nowRealtimeMs,
                        wallTimeIso = wallTimeIso
                    )
                }
                entity.pendingStartRealtimeMs != null -> {
                    state = state.replaceEntity(index, entity.copy(pendingStartRealtimeMs = null)).withEvent(
                        eventType = "timer_schedule_cancelled",
                        entityIndex = index,
                        oldValue = "scheduled",
                        newValue = "paused",
                        nowRealtimeMs = nowRealtimeMs,
                        wallTimeIso = wallTimeIso
                    )
                }
            }
        }
        return state
    }

    fun resetTimers(
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsTimer) return this
        var state = this
        state.entities.indices.forEach { index ->
            val entity = state.entities[index]
            if (entity.elapsedMs != 0L || entity.runningSinceRealtimeMs != null || entity.pendingStartRealtimeMs != null) {
                state = state.replaceEntity(
                    index,
                    entity.copy(elapsedMs = 0L, runningSinceRealtimeMs = null, pendingStartRealtimeMs = null)
                ).withEvent(
                    eventType = "timer_reset",
                    entityIndex = index,
                    oldValue = state.displayTimerMs(entity, nowRealtimeMs).toString(),
                    newValue = if (timerMode == TIMER_COUNTDOWN) countdownMs.toString() else "0",
                    nowRealtimeMs = nowRealtimeMs,
                    wallTimeIso = wallTimeIso
                )
            }
        }
        return state
    }

    fun resetValues(
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState {
        if (!showsCounter) return this
        var state = this
        state.entities.indices.forEach { index ->
            val entity = state.entities[index]
            if (entity.value != counterStart) {
                state = state.replaceEntity(index, entity.copy(value = counterStart)).withEvent(
                    eventType = "counter_reset",
                    entityIndex = index,
                    oldValue = entity.value.toString(),
                    newValue = counterStart.toString(),
                    nowRealtimeMs = nowRealtimeMs,
                    wallTimeIso = wallTimeIso
                )
            }
        }
        return state
    }

    fun finish(
        nowRealtimeMs: Long,
        wallTimeIso: String = Instant.now().toString()
    ): MulticounterSessionState = pauseAll(nowRealtimeMs, wallTimeIso).withEvent(
        eventType = "session_finished",
        entityIndex = null,
        oldValue = null,
        newValue = null,
        nowRealtimeMs = nowRealtimeMs,
        wallTimeIso = wallTimeIso
    )

    fun resultText(nowRealtimeMs: Long): String {
        val normalized = normalize(nowRealtimeMs)
        val rows = normalized.entities.map { entity ->
            when (mode) {
                MODE_TIME -> "${entity.label} ${formatDuration(normalized.displayTimerMs(entity, nowRealtimeMs), timerPrecision)}"
                MODE_COUNT_AND_TIME -> "${entity.label} ${entity.value} · ${formatDuration(normalized.displayTimerMs(entity, nowRealtimeMs), timerPrecision)}"
                else -> "${entity.label} ${entity.value}"
            }
        }.toMutableList()

        if (showsCounter && showTotal) {
            rows += "Total ${normalized.entities.sumOf { it.value }}"
        }
        if (showsCounter && showLeader && normalized.entities.isNotEmpty()) {
            val best = normalized.entities.maxOf { it.value }
            val leaders = normalized.entities.filter { it.value == best }.joinToString(" / ") { it.label }
            rows += if (leaders.contains(" / ")) "Leaders $leaders ($best)" else "Leader $leaders ($best)"
        }
        return rows.joinToString("\n")
    }

    fun entitiesJson(nowRealtimeMs: Long): String {
        val normalized = normalize(nowRealtimeMs)
        return JSONArray().apply {
            normalized.entities.forEachIndexed { index, entity ->
                put(JSONObject().apply {
                    put("index", index)
                    put("label", entity.label)
                    put("value", entity.value)
                    put("timer_text", if (showsTimer) formatDuration(normalized.displayTimerMs(entity, nowRealtimeMs), timerPrecision) else "")
                    put("elapsed_ms", normalized.currentElapsedMs(entity, nowRealtimeMs))
                    if (timerMode == TIMER_COUNTDOWN) put("remaining_ms", normalized.displayTimerMs(entity, nowRealtimeMs))
                    put("running", entity.runningSinceRealtimeMs != null)
                    put("scheduled", entity.pendingStartRealtimeMs != null)
                })
            }
        }.toString()
    }

    fun auditJson(nowRealtimeMs: Long, finishedTimeIso: String): String {
        val normalized = normalize(nowRealtimeMs)
        return JSONObject().apply {
            put("schema", "methodmesh.multicounter.audit.v1")
            put("method_id", As100MulticounterMethod.ID)
            put("session_schema", normalized.schema)
            put("started_time_iso", normalized.startedAtIso)
            put("finished_time_iso", finishedTimeIso)
            put("session_elapsed_ms", (nowRealtimeMs - normalized.startedAtRealtimeMs).coerceAtLeast(0L))
            put("mode", normalized.mode)
            put("timer_mode", normalized.timerMode)
            put("timer_precision", normalized.timerPrecision)
            put("counter_start", normalized.counterStart)
            put("step", normalized.step)
            put("allow_negative", normalized.allowNegative)
            put("countdown_ms", normalized.countdownMs)
            put("stagger_ms", normalized.staggerMs)
            put("show_total", normalized.showTotal)
            put("show_leader", normalized.showLeader)
            put("entities", JSONArray(normalized.entitiesJson(nowRealtimeMs)))
            put("events", JSONArray().apply { normalized.events.forEach { put(it.toJson()) } })
            put("retained_event_count", normalized.events.size)
            put("dropped_event_count", normalized.droppedEventCount)
            put("event_log_truncated", normalized.droppedEventCount > 0)
        }.toString()
    }

    fun toJson(): String = JSONObject().apply {
        put("schema", schema)
        put("mode", mode)
        put("timer_mode", timerMode)
        put("timer_precision", timerPrecision)
        put("counter_start", counterStart)
        put("step", step)
        put("allow_negative", allowNegative)
        put("countdown_ms", countdownMs)
        put("stagger_ms", staggerMs)
        put("show_total", showTotal)
        put("show_leader", showLeader)
        put("started_at_iso", startedAtIso)
        put("started_at_realtime_ms", startedAtRealtimeMs)
        put("entities", JSONArray().apply { entities.forEach { put(it.toJson()) } })
        put("events", JSONArray().apply { events.forEach { put(it.toJson()) } })
        put("dropped_event_count", droppedEventCount)
    }.toString()

    private fun replaceEntity(index: Int, entity: MulticounterEntity): MulticounterSessionState =
        copy(entities = entities.toMutableList().apply { this[index] = entity })

    private fun withEvent(
        eventType: String,
        entityIndex: Int?,
        oldValue: String?,
        newValue: String?,
        nowRealtimeMs: Long,
        wallTimeIso: String
    ): MulticounterSessionState {
        val event = MulticounterEvent(
            offsetMs = (nowRealtimeMs - startedAtRealtimeMs).coerceAtLeast(0L),
            eventType = eventType,
            entityIndex = entityIndex,
            entityLabel = entityIndex?.let { entities.getOrNull(it)?.label },
            oldValue = oldValue,
            newValue = newValue,
            wallTimeIso = wallTimeIso
        )
        val combined = events + event
        return if (combined.size <= MAX_EVENTS) {
            copy(events = combined)
        } else {
            copy(events = combined.takeLast(MAX_EVENTS), droppedEventCount = droppedEventCount + (combined.size - MAX_EVENTS))
        }
    }

    companion object {
        const val SCHEMA = "methodmesh.multicounter.session.v1"
        const val MODE_COUNT = "count"
        const val MODE_TIME = "time"
        const val MODE_COUNT_AND_TIME = "count_and_time"
        const val TIMER_STOPWATCH = "stopwatch"
        const val TIMER_COUNTDOWN = "countdown"
        const val PRECISION_SECONDS = "seconds"
        const val PRECISION_TENTHS = "tenths"
        private const val MAX_EVENTS = 5000

        fun create(settings: Map<String, String>, nowRealtimeMs: Long, startedTimeIso: String): MulticounterSessionState {
            val mode = settings.value("mode").orEmpty().takeIf { it in setOf(MODE_COUNT, MODE_TIME, MODE_COUNT_AND_TIME) } ?: MODE_COUNT
            val timerMode = settings.value("timer_mode").orEmpty().takeIf { it in setOf(TIMER_STOPWATCH, TIMER_COUNTDOWN) } ?: TIMER_STOPWATCH
            val timerPrecision = settings.value("timer_precision").orEmpty().takeIf { it in setOf(PRECISION_SECONDS, PRECISION_TENTHS) } ?: PRECISION_TENTHS
            val entityCount = settings.value("entity_count")?.toIntOrNull()?.coerceIn(1, 24) ?: 2
            val names = parseNames(settings.value("entity_names").orEmpty(), entityCount)
            val allowNegative = settings.value("allow_negative")?.toBooleanStrictOrNull() ?: false
            val requestedCounterStart = settings.value("counter_start")?.toIntOrNull()?.coerceIn(-1_000_000, 1_000_000) ?: 0
            val counterStart = if (allowNegative) requestedCounterStart else requestedCounterStart.coerceAtLeast(0)
            val step = settings.value("step")?.toIntOrNull()?.coerceIn(1, 1_000_000) ?: 1
            val countdownMs = (settings.value("countdown_seconds")?.toLongOrNull()?.coerceIn(1L, 604_800L) ?: 60L) * 1000L
            val staggerMs = (settings.value("stagger_seconds")?.toLongOrNull()?.coerceIn(0L, 3_600L) ?: 0L) * 1000L
            val showTotal = settings.value("show_total")?.toBooleanStrictOrNull() ?: false
            val showLeader = settings.value("show_leader")?.toBooleanStrictOrNull() ?: false
            val base = MulticounterSessionState(
                mode = mode,
                timerMode = timerMode,
                timerPrecision = timerPrecision,
                counterStart = counterStart,
                step = step,
                allowNegative = allowNegative,
                countdownMs = countdownMs,
                staggerMs = staggerMs,
                showTotal = showTotal,
                showLeader = showLeader,
                startedAtIso = startedTimeIso,
                startedAtRealtimeMs = nowRealtimeMs,
                entities = names.map { MulticounterEntity(label = it, value = counterStart) }
            )
            return base.withEvent(
                eventType = "session_started",
                entityIndex = null,
                oldValue = null,
                newValue = mode,
                nowRealtimeMs = nowRealtimeMs,
                wallTimeIso = startedTimeIso
            )
        }

        fun fromJson(text: String): MulticounterSessionState {
            val json = JSONObject(text)
            val entitiesJson = json.optJSONArray("entities") ?: JSONArray()
            val eventsJson = json.optJSONArray("events") ?: JSONArray()
            return MulticounterSessionState(
                schema = json.optString("schema", SCHEMA),
                mode = json.optString("mode", MODE_COUNT),
                timerMode = json.optString("timer_mode", TIMER_STOPWATCH),
                timerPrecision = json.optString("timer_precision", PRECISION_TENTHS),
                counterStart = json.optInt("counter_start", 0),
                step = json.optInt("step", 1).coerceAtLeast(1),
                allowNegative = json.optBoolean("allow_negative", false),
                countdownMs = json.optLong("countdown_ms", 60_000L).coerceAtLeast(1L),
                staggerMs = json.optLong("stagger_ms", 0L).coerceAtLeast(0L),
                showTotal = json.optBoolean("show_total", false),
                showLeader = json.optBoolean("show_leader", false),
                startedAtIso = json.optString("started_at_iso", Instant.now().toString()),
                startedAtRealtimeMs = json.optLong("started_at_realtime_ms", 0L),
                entities = buildList {
                    for (i in 0 until entitiesJson.length()) add(MulticounterEntity.fromJson(entitiesJson.getJSONObject(i)))
                },
                events = buildList {
                    for (i in 0 until eventsJson.length()) add(MulticounterEvent.fromJson(eventsJson.getJSONObject(i)))
                },
                droppedEventCount = json.optInt("dropped_event_count", 0).coerceAtLeast(0)
            )
        }

        fun formatDuration(valueMs: Long, precision: String): String {
            val clamped = valueMs.coerceAtLeast(0L)
            val totalSeconds = clamped / 1000L
            val hours = totalSeconds / 3600L
            val minutes = (totalSeconds % 3600L) / 60L
            val seconds = totalSeconds % 60L
            val base = if (hours > 0L) {
                "%d:%02d:%02d".format(hours, minutes, seconds)
            } else {
                "%02d:%02d".format(minutes, seconds)
            }
            return if (precision == PRECISION_TENTHS) "$base.${(clamped % 1000L) / 100L}" else base
        }

        private fun parseNames(text: String, count: Int): List<String> {
            val supplied = text.split('|', '\n', ';')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            return List(count) { index -> supplied.getOrNull(index) ?: "Counter ${index + 1}" }
        }
    }
}

internal fun Map<String, String>.value(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

private fun JSONObject.putNullable(key: String, value: Long?) {
    if (value == null) put(key, JSONObject.NULL) else put(key, value)
}

private fun JSONObject.nullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun JSONObject.nullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) getInt(key) else null

private fun JSONObject.nullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
