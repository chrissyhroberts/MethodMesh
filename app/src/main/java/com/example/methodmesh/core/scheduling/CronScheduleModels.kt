package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class CronTaskTarget { NOTIFICATION, PRESET, PROTOCOL, CAPABILITY, ODK_FORM, WEB_FORM, CLIPBOARD }

/** How a schedule obtains the time from which its cron tasks are evaluated. */
sealed interface CronTrigger {
    data object Manual : CronTrigger
    data class Absolute(val startAt: ZonedDateTime) : CronTrigger
    data class Event(val eventKey: String) : CronTrigger
    data class Preset(val presetId: String) : CronTrigger
}

/** A single row in a schedule bundle. */
data class CronTask(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timing: ScheduleTimingMode,
    val cronExpression: String,
    val relativeOffset: Duration = Duration.ZERO,
    val target: CronTaskTarget,
    val targetId: String = "",
    val notificationTitle: String = "MethodMesh reminder",
    val notificationMessage: String = "A scheduled task is due.",
    val retries: Int = 0,
    val retryInterval: Duration = Duration.ofMinutes(60),
    val maxOccurrences: Int? = null
) {
    init {
        require(name.isNotBlank())
        require(cronExpression.isNotBlank())
        require(!relativeOffset.isNegative)
        require(retries >= 0)
        require(!retryInterval.isNegative && !retryInterval.isZero)
        require(maxOccurrences == null || maxOccurrences > 0)
        require(target != CronTaskTarget.NOTIFICATION || notificationMessage.isNotBlank())
        require(target == CronTaskTarget.NOTIFICATION || targetId.isNotBlank())
    }
}

enum class ScheduleTimingMode { ABSOLUTE, RELATIVE }

/** The durable, user-visible definition of one schedule. */
data class CronScheduleBundle(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val timezone: ZoneId = ZoneId.systemDefault(),
    val trigger: CronTrigger = CronTrigger.Manual,
    val tasks: List<CronTask>,
    val enabled: Boolean = true,
    val jsonArtifactId: String? = null,
    val version: Int = 1,
    val createdAt: ZonedDateTime = ZonedDateTime.now(timezone),
    val updatedAt: ZonedDateTime = createdAt
) {
    init {
        require(name.isNotBlank())
        require(tasks.isNotEmpty())
        require(tasks.map { it.id }.distinct().size == tasks.size)
    }
}
