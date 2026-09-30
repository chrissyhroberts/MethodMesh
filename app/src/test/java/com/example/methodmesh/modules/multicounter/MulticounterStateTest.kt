package com.example.methodmesh.modules.multicounter

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MulticounterStateTest {
    @Test
    fun `count changes honour step and negative policy`() {
        val state = MulticounterSessionState.create(
            mapOf("mode" to "count", "entity_count" to "2", "entity_names" to "Alice|Bob", "step" to "2"),
            nowRealtimeMs = 100L,
            startedTimeIso = "2026-01-01T00:00:00Z"
        )

        val incremented = state.changeValue(0, 1, 200L, "2026-01-01T00:00:01Z")
        assertEquals(2, incremented.entities[0].value)
        val decremented = incremented.changeValue(0, -1, 300L, "2026-01-01T00:00:02Z")
        assertEquals(0, decremented.entities[0].value)
        assertEquals(3, decremented.events.size)
    }

    @Test
    fun `staggered start becomes running at its scheduled boundary`() {
        val state = MulticounterSessionState.create(
            mapOf("mode" to "time", "entity_count" to "2", "stagger_seconds" to "1"),
            nowRealtimeMs = 1_000L,
            startedTimeIso = "2026-01-01T00:00:00Z"
        )
        val scheduled = state.startAll(1_000L, "2026-01-01T00:00:01Z")
        assertNotNull(scheduled.entities[1].pendingStartRealtimeMs)
        assertNull(scheduled.entities[1].runningSinceRealtimeMs)

        val normalized = scheduled.normalize(2_000L, "2026-01-01T00:00:02Z")
        assertNull(normalized.entities[1].pendingStartRealtimeMs)
        assertEquals(2_000L, normalized.entities[1].runningSinceRealtimeMs)
        assertTrue(normalized.events.any { it.eventType == "timer_started" })
    }

    @Test
    fun `finish pauses timers and survives json round trip`() {
        val state = MulticounterSessionState.create(
            mapOf("mode" to "count_and_time", "entity_count" to "1", "entity_names" to "Sample"),
            nowRealtimeMs = 0L,
            startedTimeIso = "2026-01-01T00:00:00Z"
        ).changeValue(0, 1, 10L, "2026-01-01T00:00:01Z").startAll(20L, "2026-01-01T00:00:02Z")

        val finished = state.finish(1_020L, "2026-01-01T00:00:03Z")
        assertTrue(finished.events.any { it.eventType == "session_finished" })
        assertTrue(finished.entities[0].elapsedMs >= 1_000L)
        assertFalse(finished.hasActiveTimers)

        val restored = MulticounterSessionState.fromJson(finished.toJson())
        assertEquals(finished.entities, restored.entities)
        assertEquals(finished.events, restored.events)
        assertEquals("Sample 1 · 00:01.0", restored.resultText(1_020L))
    }

    @Test
    fun `event log declares truncation after retention limit`() {
        var state = MulticounterSessionState.create(
            mapOf("mode" to "count", "entity_count" to "1", "allow_negative" to "true"),
            nowRealtimeMs = 0L,
            startedTimeIso = "2026-01-01T00:00:00Z"
        )
        repeat(5_001) { index ->
            state = state.changeValue(0, 1, index.toLong(), "2026-01-01T00:00:00Z")
        }
        val audit = JSONObject(state.auditJson(5_001L, "2026-01-01T00:01:00Z"))
        assertEquals(5_000, audit.getInt("retained_event_count"))
        assertEquals(2, audit.getInt("dropped_event_count"))
        assertTrue(audit.getBoolean("event_log_truncated"))
    }
}
