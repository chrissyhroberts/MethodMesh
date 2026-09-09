package com.example.methodmesh.modules.tamagotchi

import android.content.Context
import com.example.methodmesh.core.scheduling.ScheduleAction
import com.example.methodmesh.core.scheduling.ScheduleActionType
import com.example.methodmesh.core.scheduling.ScheduleEndMode
import com.example.methodmesh.core.scheduling.ScheduleLane
import com.example.methodmesh.core.scheduling.SchedulePlan
import com.example.methodmesh.core.scheduling.SchedulePlanRuntime
import com.example.methodmesh.core.scheduling.SchedulePlanStore
import com.example.methodmesh.core.scheduling.ScheduleRule
import com.example.methodmesh.core.scheduling.ScheduleTermination
import com.example.methodmesh.core.scheduling.ScheduleTimingRule
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs

/**
 * Module adapter over MethodMesh's shared Plan/Instance scheduler.
 *
 * Tamagotchi deliberately owns no BroadcastReceiver, Service, notification host,
 * overlay permission or app-manifest component. The module only registers ordinary
 * capability schedules with the core service already owned by MethodMesh.
 */
object TamagotchiReminderScheduler {
    private const val PREFIX = "tamagotchi_care_"
    private const val STEP_MINUTES = 15

    fun schedule(context: Context, session: TamagotchiSession): Result<Unit> = runCatching {
        cancel(context, session.id).getOrThrow()
        if (!session.attentionPolicy.enabled || session.ended || session.attentionPolicy.maxPerDay <= 0) {
            return@runCatching
        }

        val zone = ZoneId.systemDefault()
        val lanes = plannedSlots(session).map { minuteOfDay ->
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            ScheduleLane(
                name = "${session.creatureName} care check",
                defaultTime = time,
                defaultActions = listOf(ScheduleAction(
                    type = ScheduleActionType.NOTIFIER,
                    title = "${session.creatureName} is ready for a check-in",
                    message = "Open MethodMesh when you have a moment.",
                    snoozeMinutes = 15
                ))
            ) to time
        }
        if (lanes.isEmpty()) return@runCatching
        val plan = SchedulePlan(
            id = scheduleId(session.id),
            name = "${session.creatureName} care reminders",
            activation = com.example.methodmesh.core.scheduling.ScheduleActivation.CALENDAR_RULE,
            timezone = zone,
            termination = ScheduleTermination(ScheduleEndMode.FOREVER),
            lanes = lanes.map { it.first },
            rules = lanes.map { (lane, time) -> ScheduleRule(lane.id, ScheduleTimingRule.Weekly((1..7).toSet(), time)) }
        )
        SchedulePlanStore.savePlan(context, plan)
        SchedulePlanRuntime.start(context, plan)
    }

    fun cancel(context: Context, sessionId: String): Result<Unit> = runCatching {
        val id = scheduleId(sessionId)
        SchedulePlanStore.allInstances(context).filter { it.planId == id }.forEach { SchedulePlanRuntime.cancel(context, it) }
        SchedulePlanStore.removeInstancesForPlan(context, id)
        SchedulePlanStore.removePlan(context, id)
    }

    /**
     * Conservative daily slots chosen only from times allowed by the session policy.
     * School suppression is applied every day rather than trying to special-case
     * weekends; this errs toward fewer interruptions for child/classroom profiles.
     */
    fun plannedSlots(session: TamagotchiSession): List<Int> {
        val policy = session.attentionPolicy
        if (!policy.enabled || policy.maxPerDay <= 0) return emptyList()

        val candidates = (0 until 24 * 60 step STEP_MINUTES)
            .filter { minute -> isAllowed(policy, minute) }
        if (candidates.isEmpty()) return emptyList()

        val requested = policy.maxPerDay.coerceAtMost(candidates.size)
        val spacing = policy.minimumSpacingMinutes.coerceAtLeast(0)
        val selected = mutableListOf<Int>()

        for (ordinal in 1..requested) {
            val targetIndex = ((ordinal.toDouble() / (requested + 1)) * candidates.lastIndex)
                .toInt()
                .coerceIn(0, candidates.lastIndex)
            val target = candidates[targetIndex]
            val available = candidates
                .asSequence()
                .filter { candidate -> selected.all { previous -> circularDistance(candidate, previous) >= spacing } }
                .minByOrNull { candidate -> abs(candidate - target) }
                ?: continue
            selected += available
        }
        return selected.sorted()
    }

    private fun isAllowed(policy: AttentionPolicy, minute: Int): Boolean {
        if (inWrappedRange(minute, policy.quietStartMinutes, policy.quietEndMinutes)) return false
        if (!policy.allowDuringSchool && inWrappedRange(minute, policy.schoolStartMinutes, policy.schoolEndMinutes)) return false
        return true
    }

    private fun inWrappedRange(minute: Int, start: Int, end: Int): Boolean =
        if (start <= end) minute in start until end else minute >= start || minute < end

    private fun circularDistance(a: Int, b: Int): Int {
        val direct = abs(a - b)
        return minOf(direct, 24 * 60 - direct)
    }

    private fun scheduleId(sessionId: String) = "$PREFIX$sessionId"
}
