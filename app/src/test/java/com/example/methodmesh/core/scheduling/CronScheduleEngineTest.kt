package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CronScheduleEngineTest {
    private val zone = ZoneId.of("Europe/London")
    private val anchor = ZonedDateTime.parse("2026-09-09T12:00:00+01:00[Europe/London]")

    @Test
    fun absoluteCronUsesTheInitiationTimepoint() {
        val task = CronTask(name = "Morning check", timing = ScheduleTimingMode.ABSOLUTE, cronExpression = "0 9 * * *", target = CronTaskTarget.NOTIFICATION)
        val bundle = CronScheduleBundle(name = "Daily checks", timezone = zone, trigger = CronTrigger.Absolute(anchor), tasks = listOf(task))

        val values = CronScheduleEngine.occurrences(bundle, anchor, Duration.ofDays(3))

        assertEquals(3, values.size)
        assertEquals("09:00", values.first().scheduledAt.toLocalTime().toString())
    }

    @Test
    fun relativeCronStartsAfterItsOffset() {
        val task = CronTask(name = "Follow-up", timing = ScheduleTimingMode.RELATIVE, relativeOffset = Duration.ofDays(15), cronExpression = "0 10 * * *", target = CronTaskTarget.PRESET, targetId = "preset-1")
        val bundle = CronScheduleBundle(name = "Follow-up schedule", timezone = zone, trigger = CronTrigger.Event("preset.completed"), tasks = listOf(task))

        val next = CronScheduleEngine.next(task, anchor)

        assertEquals(anchor.plusDays(16).withHour(10).withMinute(0), next)
    }

    @Test
    fun taskOccurrenceLimitAppliesPerTask() {
        val limited = CronTask(name = "Hourly", timing = ScheduleTimingMode.ABSOLUTE, cronExpression = "0 * * * *", target = CronTaskTarget.NOTIFICATION, maxOccurrences = 2)
        val unlimited = limited.copy(id = "two", name = "Unlimited", maxOccurrences = null)
        val bundle = CronScheduleBundle(name = "Mixed", timezone = zone, tasks = listOf(limited, unlimited))

        val values = CronScheduleEngine.occurrences(bundle, anchor, Duration.ofHours(5))

        assertEquals(2, values.count { it.taskId == limited.id })
        assertTrue(values.count { it.taskId == unlimited.id } > 2)
    }
}
