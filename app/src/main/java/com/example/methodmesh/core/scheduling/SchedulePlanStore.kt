package com.example.methodmesh.core.scheduling

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Durable storage for replacement scheduler Plans and running Instances. */
object SchedulePlanStore {
    private const val PREFS = "methodmesh_schedule_plans"
    private const val PLANS = "plans"
    private const val INSTANCES = "instances"

    fun allPlans(context: Context): List<SchedulePlan> = readArray(context, PLANS).mapNotNull { decodePlan(it) }
    fun plan(context: Context, id: String): SchedulePlan? = allPlans(context).firstOrNull { it.id == id }

    fun savePlan(context: Context, plan: SchedulePlan) {
        val values = allPlans(context).filterNot { it.id == plan.id } + plan
        writeArray(context, PLANS, values.map(::encodePlan))
    }

    fun removePlan(context: Context, id: String) {
        writeArray(context, PLANS, allPlans(context).filterNot { it.id == id }.map(::encodePlan))
    }

    fun allInstances(context: Context): List<ScheduleInstance> = readArray(context, INSTANCES).mapNotNull { decodeInstance(it) }
    fun instance(context: Context, id: String): ScheduleInstance? = allInstances(context).firstOrNull { it.id == id }

    fun saveInstance(context: Context, instance: ScheduleInstance) {
        val values = allInstances(context).filterNot { it.id == instance.id } + instance
        writeArray(context, INSTANCES, values.map(::encodeInstance))
    }

    fun removeInstancesForPlan(context: Context, planId: String) {
        writeArray(context, INSTANCES, allInstances(context).filterNot { it.planId == planId }.map(::encodeInstance))
    }

    fun updateOccurrence(context: Context, instanceId: String, occurrenceId: String, state: ScheduleOccurrenceState, completedAt: ZonedDateTime? = null) {
        val instance = instance(context, instanceId) ?: return
        saveInstance(context, instance.copy(occurrences = instance.occurrences.map { occurrence ->
            if (occurrence.id == occurrenceId) occurrence.copy(state = state, completedAt = completedAt) else occurrence
        }))
    }

    fun updateActionExecution(context: Context, instanceId: String, occurrenceId: String, index: Int, state: ScheduleActionExecutionState, error: String = "") {
        val instance = instance(context, instanceId) ?: return
        saveInstance(context, instance.copy(occurrences = instance.occurrences.map { occurrence ->
            if (occurrence.id != occurrenceId) occurrence else occurrence.copy(actionExecutions = occurrence.actionExecutions.map { execution ->
                if (execution.index != index) execution else execution.copy(state = state, startedAt = if (state == ScheduleActionExecutionState.IN_PROGRESS) ZonedDateTime.now() else execution.startedAt, completedAt = if (state == ScheduleActionExecutionState.COMPLETED || state == ScheduleActionExecutionState.FAILED) ZonedDateTime.now() else execution.completedAt, error = error)
            })
        }))
    }

    private fun readArray(context: Context, key: String): List<JSONObject> = runCatching {
        val array = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key, "[]"))
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeArray(context: Context, key: String, values: List<JSONObject>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(key, JSONArray().apply { values.forEach(::put) }.toString()).apply()
    }

    private fun encodePlan(plan: SchedulePlan) = JSONObject().apply {
        put("id", plan.id); put("name", plan.name); put("description", plan.description); put("activation", plan.activation.name)
        put("start_at", plan.startAt?.toString()); put("timezone", plan.timezone.id); put("version", plan.version)
        put("created_at", plan.createdAt.toString()); put("updated_at", plan.updatedAt.toString())
        put("termination", encodeTermination(plan.termination))
        put("lanes", JSONArray().apply { plan.lanes.forEach { put(encodeLane(it)) } })
        put("rules", JSONArray().apply { plan.rules.forEach { put(encodeRule(it)) } })
    }

    private fun decodePlan(o: JSONObject): SchedulePlan? = runCatching {
        val timezone = ZoneId.of(o.optString("timezone", ZoneId.systemDefault().id))
        SchedulePlan(
            id = o.optString("id"), name = o.optString("name"), description = o.optString("description"),
            activation = ScheduleActivation.valueOf(o.optString("activation")), startAt = o.optString("start_at").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse),
            timezone = timezone, termination = decodeTermination(o.getJSONObject("termination")),
            lanes = array(o.optJSONArray("lanes")).mapNotNull(::decodeLane), rules = array(o.optJSONArray("rules")).mapNotNull(::decodeRule),
            version = o.optInt("version", 1), createdAt = ZonedDateTime.parse(o.getString("created_at")), updatedAt = ZonedDateTime.parse(o.getString("updated_at"))
        )
    }.getOrNull()?.takeIf { it.id.isNotBlank() && it.name.isNotBlank() }

    private fun encodeTermination(value: ScheduleTermination) = JSONObject().apply {
        put("mode", value.mode.name); put("occurrence_count", value.occurrenceCount); put("duration_seconds", value.duration?.seconds); put("absolute_end", value.absoluteEnd?.toString())
    }

    private fun decodeTermination(o: JSONObject) = ScheduleTermination(
        mode = ScheduleEndMode.valueOf(o.getString("mode")), occurrenceCount = o.optInt("occurrence_count").takeIf { it > 0 },
        duration = o.optLong("duration_seconds").takeIf { it > 0 }?.let(Duration::ofSeconds), absoluteEnd = o.optString("absolute_end").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse)
    )

    private fun encodeLane(lane: ScheduleLane) = JSONObject().apply {
        put("id", lane.id); put("name", lane.name); put("default_time", lane.defaultTime.toString()); put("missed_start_policy", lane.missedStartPolicy.name); put("window_before", lane.defaultWindowBefore?.seconds); put("window_after", lane.defaultWindowAfter?.seconds)
        put("actions", JSONArray().apply { lane.defaultActions.forEach { put(encodeAction(it)) } })
    }

    private fun decodeLane(o: JSONObject) = runCatching { ScheduleLane(
        id = o.getString("id"), name = o.getString("name"), defaultTime = LocalTime.parse(o.getString("default_time")),
        defaultActions = array(o.optJSONArray("actions")).mapNotNull(::decodeAction), missedStartPolicy = runCatching { ScheduleMissedStartPolicy.valueOf(o.optString("missed_start_policy", ScheduleMissedStartPolicy.SKIP_MISSED.name)) }.getOrDefault(ScheduleMissedStartPolicy.SKIP_MISSED), defaultWindowBefore = o.optLong("window_before").takeIf { it > 0 }?.let(Duration::ofSeconds), defaultWindowAfter = o.optLong("window_after").takeIf { it > 0 }?.let(Duration::ofSeconds)
    ) }.getOrNull()

    private fun encodeAction(action: ScheduleAction) = JSONObject().apply {
        put("id", action.id); put("type", action.type.name); put("preset_id", action.presetId); put("title", action.title); put("message", action.message)
        put("require_completion", action.requireCompletion); put("automatic", action.automatic); put("required", action.required); put("snooze_minutes", action.snoozeMinutes); put("follow_up_count", action.followUpCount); put("follow_up_interval_minutes", action.followUpIntervalMinutes)
    }

    private fun decodeAction(o: JSONObject) = runCatching { ScheduleAction(
        id = o.getString("id"), type = ScheduleActionType.valueOf(o.getString("type")), presetId = o.optString("preset_id"), title = o.optString("title"), message = o.optString("message"),
        requireCompletion = o.optBoolean("require_completion", true), automatic = o.optBoolean("automatic"), required = o.optBoolean("required", true), snoozeMinutes = o.optInt("snooze_minutes"), followUpCount = o.optInt("follow_up_count"), followUpIntervalMinutes = o.optInt("follow_up_interval_minutes")
    ) }.getOrNull()

    private fun encodeRule(rule: ScheduleRule) = JSONObject().apply { put("lane_id", rule.laneId); put("label", rule.label); put("timing", encodeTiming(rule.timing)); rule.actions?.let { put("actions", JSONArray().apply { it.forEach { action -> put(encodeAction(action)) } }) } }
    private fun decodeRule(o: JSONObject) = runCatching { ScheduleRule(o.getString("lane_id"), decodeTiming(o.getJSONObject("timing")), o.optJSONArray("actions")?.let { array(it).mapNotNull(::decodeAction) }, o.optString("label")) }.getOrNull()

    private fun encodeTiming(timing: ScheduleTimingRule) = JSONObject().apply {
        when (timing) {
            is ScheduleTimingRule.RelativeDays -> { put("type", "relative_days"); put("days", JSONArray(timing.days.toList())); put("time", timing.time.toString()); put("before", timing.windowBefore?.seconds); put("after", timing.windowAfter?.seconds) }
            is ScheduleTimingRule.Weekly -> { put("type", "weekly"); put("weekdays", JSONArray(timing.weekdays.toList())); put("time", timing.time.toString()); put("before", timing.windowBefore?.seconds); put("after", timing.windowAfter?.seconds) }
            is ScheduleTimingRule.MonthlyNthWeekday -> { put("type", "monthly_nth"); put("weekday", timing.weekday); put("ordinal", timing.ordinal); put("time", timing.time.toString()) }
            is ScheduleTimingRule.IntradayInterval -> { put("type", "intraday"); put("weekdays", JSONArray(timing.weekdays.toList())); put("first_time", timing.firstTime.toString()); put("last_time", timing.lastTime.toString()); put("interval_seconds", timing.interval.seconds) }
        }
    }

    private fun decodeTiming(o: JSONObject): ScheduleTimingRule = when (o.getString("type")) {
        "relative_days" -> ScheduleTimingRule.RelativeDays(ints(o.getJSONArray("days")), LocalTime.parse(o.getString("time")), seconds(o, "before"), seconds(o, "after"))
        "weekly" -> ScheduleTimingRule.Weekly(ints(o.getJSONArray("weekdays")), LocalTime.parse(o.getString("time")), seconds(o, "before"), seconds(o, "after"))
        "monthly_nth" -> ScheduleTimingRule.MonthlyNthWeekday(o.getInt("weekday"), o.getInt("ordinal"), LocalTime.parse(o.getString("time")))
        "intraday" -> ScheduleTimingRule.IntradayInterval(ints(o.getJSONArray("weekdays")), LocalTime.parse(o.getString("first_time")), LocalTime.parse(o.getString("last_time")), Duration.ofSeconds(o.getLong("interval_seconds")))
        else -> error("Unknown schedule timing rule")
    }

    private fun encodeInstance(instance: ScheduleInstance) = JSONObject().apply {
        put("id", instance.id); put("plan_id", instance.planId); put("plan_version", instance.planVersion); put("anchored_at", instance.anchoredAt.toString()); put("effective_end", instance.effectiveEnd?.toString()); put("stopped_at", instance.stoppedAt?.toString())
        put("occurrences", JSONArray().apply { instance.occurrences.forEach { put(encodeOccurrence(it)) } })
    }

    private fun decodeInstance(o: JSONObject) = runCatching { ScheduleInstance(
        id = o.getString("id"), planId = o.getString("plan_id"), planVersion = o.getInt("plan_version"), anchoredAt = ZonedDateTime.parse(o.getString("anchored_at")), effectiveEnd = o.optString("effective_end").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse), occurrences = array(o.optJSONArray("occurrences")).mapNotNull(::decodeOccurrence), stoppedAt = o.optString("stopped_at").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse)
    ) }.getOrNull()

    private fun encodeOccurrence(value: ScheduleOccurrence) = JSONObject().apply {
        put("id", value.id); put("instance_id", value.instanceId); put("lane_id", value.laneId); put("lane_name", value.laneName); put("scheduled_at", value.scheduledAt.toString()); put("window_open", value.windowOpen?.toString()); put("window_close", value.windowClose?.toString()); put("state", value.state.name); put("completed_at", value.completedAt?.toString()); put("actions", JSONArray().apply { value.actions.forEach { put(encodeAction(it)) } }); put("action_executions", JSONArray().apply { value.actionExecutions.forEach { put(encodeActionExecution(it)) } })
    }

    private fun decodeOccurrence(o: JSONObject) = runCatching {
        val actions = array(o.optJSONArray("actions")).mapNotNull(::decodeAction)
        val executions = array(o.optJSONArray("action_executions")).mapNotNull(::decodeActionExecution).ifEmpty { actions.mapIndexed { index, action -> ScheduleActionExecution(action.id, index) } }
        ScheduleOccurrence(
            id = o.getString("id"), instanceId = o.getString("instance_id"), laneId = o.getString("lane_id"), laneName = o.getString("lane_name"), scheduledAt = ZonedDateTime.parse(o.getString("scheduled_at")), windowOpen = o.optString("window_open").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse), windowClose = o.optString("window_close").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse), actions = actions, actionExecutions = executions, state = ScheduleOccurrenceState.valueOf(o.optString("state", ScheduleOccurrenceState.UPCOMING.name)), completedAt = o.optString("completed_at").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse)
        )
    }.getOrNull()

    private fun encodeActionExecution(value: ScheduleActionExecution) = JSONObject().apply {
        put("action_id", value.actionId); put("index", value.index); put("state", value.state.name); put("started_at", value.startedAt?.toString()); put("completed_at", value.completedAt?.toString()); put("error", value.error)
    }

    private fun decodeActionExecution(o: JSONObject) = runCatching { ScheduleActionExecution(
        actionId = o.getString("action_id"), index = o.getInt("index"), state = ScheduleActionExecutionState.valueOf(o.optString("state", ScheduleActionExecutionState.PENDING.name)), startedAt = o.optString("started_at").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse), completedAt = o.optString("completed_at").takeIf { it.isNotBlank() && it != "null" }?.let(ZonedDateTime::parse), error = o.optString("error")
    ) }.getOrNull()

    private fun array(value: JSONArray?): List<JSONObject> = value?.let { (0 until it.length()).mapNotNull(it::optJSONObject) } ?: emptyList()
    private fun ints(value: JSONArray): Set<Int> = (0 until value.length()).mapNotNull(value::optInt).toSet()
    private fun seconds(value: JSONObject, key: String): Duration? = value.optLong(key).takeIf { it > 0 }?.let(Duration::ofSeconds)
}
