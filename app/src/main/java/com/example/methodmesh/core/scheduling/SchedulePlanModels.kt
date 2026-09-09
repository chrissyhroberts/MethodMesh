package com.example.methodmesh.core.scheduling

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

enum class ScheduleActivation { MANUAL_DAY_ONE, ABSOLUTE_START, CALENDAR_RULE, EXTERNAL }

enum class ScheduleEndMode { OCCURRENCE_COUNT, DURATION, ABSOLUTE, PARENT_SEQUENCE, FOREVER }

data class ScheduleTermination(
    val mode: ScheduleEndMode,
    val occurrenceCount: Int? = null,
    val duration: Duration? = null,
    val absoluteEnd: ZonedDateTime? = null
) {
    init {
        require(mode != ScheduleEndMode.OCCURRENCE_COUNT || occurrenceCount != null && occurrenceCount > 0)
        require(mode != ScheduleEndMode.DURATION || duration != null && !duration.isNegative && !duration.isZero)
        require(mode != ScheduleEndMode.ABSOLUTE || absoluteEnd != null)
    }
}

enum class ScheduleActionType { NOTIFIER, PRESET }

enum class ScheduleMissedStartPolicy { SKIP_MISSED, RUN_MISSED }

data class ScheduleAction(
    val id: String = UUID.randomUUID().toString(),
    val type: ScheduleActionType,
    val presetId: String = "",
    val title: String = "",
    val message: String = "",
    val requireCompletion: Boolean = type == ScheduleActionType.NOTIFIER,
    val automatic: Boolean = false,
    val required: Boolean = true,
    val snoozeMinutes: Int = 0,
    val followUpCount: Int = 0,
    val followUpIntervalMinutes: Int = 0
) {
    init {
        require(type != ScheduleActionType.PRESET || presetId.isNotBlank())
        require(snoozeMinutes >= 0 && followUpCount >= 0 && followUpIntervalMinutes >= 0)
    }
}

sealed interface ScheduleTimingRule {
    data class Cron(
        val expression: String,
        val timing: ScheduleTimingMode = ScheduleTimingMode.ABSOLUTE,
        val offset: Duration = Duration.ZERO
    ) : ScheduleTimingRule {
        init {
            require(expression.isNotBlank())
            require(!offset.isNegative)
        }
    }

    data class RelativeDays(
        val days: Set<Int>,
        val time: LocalTime,
        val windowBefore: Duration? = null,
        val windowAfter: Duration? = null
    ) : ScheduleTimingRule {
        init { require(days.isNotEmpty() && days.all { it >= 1 }) }
    }

    data class Weekly(
        val weekdays: Set<Int>,
        val time: LocalTime,
        val windowBefore: Duration? = null,
        val windowAfter: Duration? = null
    ) : ScheduleTimingRule {
        init { require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) }
    }

    data class MonthlyNthWeekday(
        val weekday: Int,
        val ordinal: Int,
        val time: LocalTime
    ) : ScheduleTimingRule {
        init { require(weekday in 1..7 && ordinal in 1..5) }
    }

    data class IntradayInterval(
        val weekdays: Set<Int>,
        val firstTime: LocalTime,
        val lastTime: LocalTime,
        val interval: Duration
    ) : ScheduleTimingRule {
        init {
            require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 })
            require(!interval.isZero && !interval.isNegative && !lastTime.isBefore(firstTime))
        }
    }
}

data class ScheduleLane(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultTime: LocalTime,
    val defaultActions: List<ScheduleAction>,
    val missedStartPolicy: ScheduleMissedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED,
    val defaultWindowBefore: Duration? = null,
    val defaultWindowAfter: Duration? = null
)

data class ScheduleRule(
    val laneId: String,
    val timing: ScheduleTimingRule,
    val actions: List<ScheduleAction>? = null,
    val label: String = ""
)

data class SchedulePlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val activation: ScheduleActivation,
    val startAt: ZonedDateTime? = null,
    val timezone: ZoneId = ZoneId.systemDefault(),
    val termination: ScheduleTermination,
    val lanes: List<ScheduleLane> = emptyList(),
    val rules: List<ScheduleRule> = emptyList(),
    val version: Int = 1,
    val enabled: Boolean = true,
    val createdAt: ZonedDateTime = ZonedDateTime.now(timezone),
    val updatedAt: ZonedDateTime = createdAt
) {
    init {
        require(name.isNotBlank())
        require(lanes.map { it.id }.distinct().size == lanes.size)
        require(rules.all { rule -> lanes.any { it.id == rule.laneId } })
        require(activation != ScheduleActivation.ABSOLUTE_START || startAt != null)
    }
}

enum class ScheduleOccurrenceState { UPCOMING, WINDOW_OPEN, DUE, IN_PROGRESS, COMPLETED, SNOOZED, OVERDUE, MISSED, FAILED, SKIPPED, CANCELLED }

enum class ScheduleActionExecutionState { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }

data class ScheduleActionExecution(
    val actionId: String,
    val index: Int,
    val state: ScheduleActionExecutionState = ScheduleActionExecutionState.PENDING,
    val startedAt: ZonedDateTime? = null,
    val completedAt: ZonedDateTime? = null,
    val error: String = ""
)

data class ScheduleOccurrence(
    val id: String = UUID.randomUUID().toString(),
    val instanceId: String,
    val laneId: String,
    val laneName: String,
    val scheduledAt: ZonedDateTime,
    val windowOpen: ZonedDateTime? = null,
    val windowClose: ZonedDateTime? = null,
    val actions: List<ScheduleAction>,
    val actionExecutions: List<ScheduleActionExecution> = actions.mapIndexed { index, action -> ScheduleActionExecution(action.id, index) },
    val state: ScheduleOccurrenceState = ScheduleOccurrenceState.UPCOMING,
    val completedAt: ZonedDateTime? = null
)

data class ScheduleInstance(
    val id: String = UUID.randomUUID().toString(),
    val planId: String,
    val planVersion: Int,
    val anchoredAt: ZonedDateTime,
    val effectiveEnd: ZonedDateTime? = null,
    val occurrences: List<ScheduleOccurrence> = emptyList(),
    val stoppedAt: ZonedDateTime? = null
)

data class ScheduleGenerationSpec(
    val horizon: Duration = Duration.ofDays(366),
    val now: ZonedDateTime = ZonedDateTime.now()
)
