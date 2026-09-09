package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

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

    @Test
    fun laneCanCatchUpSameDayOccurrencesWhenStartedLate() {
        val catchUpLane = ScheduleLane(
            name = "Patch",
            defaultTime = LocalTime.of(9, 0),
            defaultActions = listOf(ScheduleAction(type = ScheduleActionType.NOTIFIER, title = "Change patch")),
            missedStartPolicy = ScheduleMissedStartPolicy.RUN_MISSED
        )
        val skipLane = catchUpLane.copy(id = java.util.UUID.randomUUID().toString(), name = "AHT", missedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED)
        val plan = SchedulePlan(
            name = "Morning checks",
            activation = ScheduleActivation.MANUAL_DAY_ONE,
            timezone = zone,
            termination = ScheduleTermination(ScheduleEndMode.DURATION, duration = Duration.ofDays(2)),
            lanes = listOf(catchUpLane, skipLane),
            rules = listOf(
                ScheduleRule(catchUpLane.id, ScheduleTimingRule.RelativeDays(setOf(1), LocalTime.of(9, 0))),
                ScheduleRule(skipLane.id, ScheduleTimingRule.RelativeDays(setOf(1), LocalTime.of(9, 0)))
            )
        )

        val instance = SchedulePlanEngine.instantiate(plan, anchor)

        assertEquals(listOf("Patch"), instance.occurrences.map { it.laneName })
        assertEquals(LocalTime.of(9, 0), instance.occurrences.single().scheduledAt.toLocalTime())
    }

    @Test
    fun weeklyRuleGeneratesOnlySelectedWeekdays() {
        val lane = ScheduleLane(
            name = "Weekly check",
            defaultTime = LocalTime.of(9, 0),
            defaultActions = listOf(ScheduleAction(type = ScheduleActionType.NOTIFIER, title = "Weekly check"))
        )
        val plan = SchedulePlan(
            name = "Monday checks",
            activation = ScheduleActivation.CALENDAR_RULE,
            timezone = zone,
            termination = ScheduleTermination(ScheduleEndMode.DURATION, duration = Duration.ofDays(15)),
            lanes = listOf(lane),
            rules = listOf(ScheduleRule(lane.id, ScheduleTimingRule.Weekly(setOf(1), LocalTime.of(9, 0))))
        )

        val instance = SchedulePlanEngine.instantiate(plan, anchor)

        assertTrue(instance.occurrences.isNotEmpty())
        assertTrue(instance.occurrences.all { it.scheduledAt.dayOfWeek.value == 1 })
    }
}
