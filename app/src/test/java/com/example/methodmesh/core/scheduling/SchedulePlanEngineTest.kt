package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchedulePlanEngineTest {
    private val zone = ZoneId.of("Europe/London")
    private val anchor = ZonedDateTime.parse("2026-09-09T14:22:00+01:00[Europe/London]")

    @Test
    fun relativePlanCreatesDayOneAnchoredOccurrences() {
        val lane = ScheduleLane(name = "Patch", defaultTime = LocalTime.of(7, 0), defaultActions = listOf(ScheduleAction(type = ScheduleActionType.NOTIFIER, title = "Change patch")))
        val plan = SchedulePlan(
            name = "Progesterone + Patch",
            activation = ScheduleActivation.MANUAL_DAY_ONE,
            timezone = zone,
            termination = ScheduleTermination(ScheduleEndMode.DURATION, duration = Duration.ofDays(22)),
            lanes = listOf(lane),
            rules = listOf(ScheduleRule(lane.id, ScheduleTimingRule.RelativeDays(setOf(5, 10, 15, 20), LocalTime.of(7, 0))))
        )

        val instance = SchedulePlanEngine.instantiate(plan, anchor)

        assertEquals(listOf(5L, 10L, 15L, 20L), instance.occurrences.map { Duration.between(anchor.toLocalDate().atStartOfDay(zone), it.scheduledAt).toDays() + 1 })
        assertEquals(anchor, instance.anchoredAt)
        assertEquals(4, instance.occurrences.size)
    }

    @Test
    fun foreverPlanUsesBoundedHorizon() {
        val lane = ScheduleLane(name = "Check", defaultTime = LocalTime.of(9, 0), defaultActions = listOf(ScheduleAction(type = ScheduleActionType.PRESET, presetId = "weekly-check", automatic = true)))
        val plan = SchedulePlan(
            name = "Equipment checks",
            activation = ScheduleActivation.CALENDAR_RULE,
            timezone = zone,
            termination = ScheduleTermination(ScheduleEndMode.FOREVER),
            lanes = listOf(lane),
            rules = listOf(ScheduleRule(lane.id, ScheduleTimingRule.Weekly(setOf(1), LocalTime.of(9, 0))))
        )

        val instance = SchedulePlanEngine.instantiate(plan, anchor, ScheduleGenerationSpec(Duration.ofDays(30), anchor))

        assertTrue(instance.occurrences.isNotEmpty())
        assertTrue(instance.occurrences.size < 10)
        assertTrue(instance.occurrences.all { it.scheduledAt <= anchor.plusDays(30) })
    }
}
