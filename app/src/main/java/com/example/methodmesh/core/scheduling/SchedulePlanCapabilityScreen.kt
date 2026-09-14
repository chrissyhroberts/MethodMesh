package com.example.methodmesh.core.scheduling

import android.app.Activity
import android.provider.Settings
import android.net.Uri
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.app.AlarmManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.NumberPicker
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.ProtocolLibraryRepository
import com.example.methodmesh.core.protocols.PresetLaunchMode
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.Duration
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class BuilderMode { ONCE, INTERVAL, DAILY, WEEKLY, MONTHLY, SEQUENCE, CRON }
private enum class BuilderStartMode { MANUAL_DAY_ONE, CALENDAR_NOW, ABSOLUTE }
private enum class IntervalUnit { MINUTES, HOURS, DAYS }
private enum class DailyTimingMode { FROM_START, FIXED_TIMES }
private val ALL_WEEKDAYS = (1..7).toSet()

private data class BuilderLane(val name: String, val hour: Int = 9, val minute: Int = 0, val actionType: ScheduleActionType = ScheduleActionType.NOTIFIER, val message: String = "", val presetId: String = "", val snoozeMinutes: Int = 10, val followUpCount: Int = 0, val followUpIntervalMinutes: Int = 30, val missedStartPolicy: ScheduleMissedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED, val days: Set<Int> = setOf(1), val dayTimes: Map<Int, Pair<Int, Int>> = emptyMap(), val cronExpression: String = "0 9 * * *", val presetLaunchMode: SchedulePresetLaunchMode = SchedulePresetLaunchMode.FOLLOW_PRESET) {
    val time: String get() = "%02d:%02d".format(hour, minute)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

object SchedulePlanCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SchedulerMethod.ID
    override val title = "Schedule"
    override val description = "Build one-off, interval, calendar, sequence, or advanced cron schedules."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val localContext = LocalContext.current
        val hostActivity = localContext.findActivity()
        val app = localContext.applicationContext
        val planId = context.action.settings["schedule_plan_id"] ?: context.request.settings["schedule_plan_id"]
        val existingPlan = remember(planId) { planId?.let { SchedulePlanStore.plan(app, it) } }
        var name by remember(existingPlan?.id) { mutableStateOf(existingPlan?.name.orEmpty()) }
        var description by remember(existingPlan?.id) { mutableStateOf(existingPlan?.description.orEmpty()) }
        var sequenceDays by remember(existingPlan?.id) { mutableStateOf(existingPlan?.rules?.flatMap { (it.timing as? ScheduleTimingRule.RelativeDays)?.days.orEmpty() }?.maxOrNull()?.coerceAtLeast(1)?.toString() ?: "22") }
        var durationDays by remember(existingPlan?.id) { mutableStateOf(existingPlan?.termination?.duration?.toDays()?.toString() ?: "22") }
        var endMode by remember(existingPlan?.id) { mutableStateOf(existingPlan?.termination?.mode ?: ScheduleEndMode.DURATION) }
        var mode by remember(existingPlan?.id) {
            mutableStateOf(
                when (existingPlan?.rules?.firstOrNull()?.timing) {
                    is ScheduleTimingRule.Once -> BuilderMode.ONCE
                    is ScheduleTimingRule.ElapsedInterval -> BuilderMode.INTERVAL
                    is ScheduleTimingRule.AnchoredCalendarDays -> BuilderMode.DAILY
                    is ScheduleTimingRule.MonthlyDayOfMonth, is ScheduleTimingRule.MonthlyNthWeekday -> BuilderMode.MONTHLY
                    is ScheduleTimingRule.Cron -> BuilderMode.CRON
                    is ScheduleTimingRule.Weekly -> if (existingPlan?.rules?.all { rule -> (rule.timing as? ScheduleTimingRule.Weekly)?.weekdays == ALL_WEEKDAYS } == true) BuilderMode.DAILY else BuilderMode.WEEKLY
                    is ScheduleTimingRule.RelativeDays -> BuilderMode.SEQUENCE
                    else -> BuilderMode.DAILY
                }
            )
        }
        var startMode by remember(existingPlan?.id) { mutableStateOf(when (existingPlan?.activation) { ScheduleActivation.ABSOLUTE_START -> BuilderStartMode.ABSOLUTE; ScheduleActivation.CALENDAR_RULE -> BuilderStartMode.CALENDAR_NOW; else -> BuilderStartMode.MANUAL_DAY_ONE }) }
        var startDate by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.toLocalDate()?.toString() ?: LocalDate.now().toString()) }
        var startHour by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.hour ?: 9) }
        var startMinute by remember(existingPlan?.id) { mutableStateOf(existingPlan?.startAt?.withZoneSameInstant(ZoneId.systemDefault())?.minute ?: 0) }
        val existingInterval = existingPlan?.rules?.firstOrNull()?.timing as? ScheduleTimingRule.ElapsedInterval
        var intervalUnit by remember(existingPlan?.id) {
            mutableStateOf(
                when {
                    existingInterval == null -> IntervalUnit.HOURS
                    existingInterval.interval.toDays() > 0 && existingInterval.interval.seconds % Duration.ofDays(1).seconds == 0L -> IntervalUnit.DAYS
                    existingInterval.interval.toHours() > 0 && existingInterval.interval.seconds % Duration.ofHours(1).seconds == 0L -> IntervalUnit.HOURS
                    else -> IntervalUnit.MINUTES
                }
            )
        }
        var intervalValue by remember(existingPlan?.id, intervalUnit) {
            mutableStateOf(
                when (intervalUnit) {
                    IntervalUnit.DAYS -> existingInterval?.interval?.toDays() ?: 1L
                    IntervalUnit.HOURS -> existingInterval?.interval?.toHours() ?: 1L
                    IntervalUnit.MINUTES -> existingInterval?.interval?.toMinutes() ?: 1L
                }.coerceAtLeast(1).toString()
            )
        }
        var intervalImmediate by remember(existingPlan?.id) {
            mutableStateOf((existingPlan?.rules?.firstOrNull()?.timing as? ScheduleTimingRule.ElapsedInterval)?.runImmediately ?: true)
        }
        var dailyTimingMode by remember(existingPlan?.id) {
            mutableStateOf(if (existingPlan?.rules?.firstOrNull()?.timing is ScheduleTimingRule.AnchoredCalendarDays) DailyTimingMode.FROM_START else DailyTimingMode.FIXED_TIMES)
        }
        var dailyImmediate by remember(existingPlan?.id) {
            mutableStateOf((existingPlan?.rules?.firstOrNull()?.timing as? ScheduleTimingRule.AnchoredCalendarDays)?.runImmediately ?: true)
        }
        var monthlyDay by remember(existingPlan?.id) {
            mutableStateOf((existingPlan?.rules?.firstOrNull()?.timing as? ScheduleTimingRule.MonthlyDayOfMonth)?.dayOfMonth?.toString() ?: "1")
        }
        var occurrenceCount by remember(existingPlan?.id) {
            mutableStateOf(existingPlan?.termination?.occurrenceCount?.toString() ?: "10")
        }
        val lanes = remember(existingPlan?.id) {
            mutableStateListOf<BuilderLane>().apply {
                if (existingPlan == null) add(BuilderLane("Activity"))
                else existingPlan.lanes.forEach { lane ->
                    val rules = existingPlan.rules.filter { it.laneId == lane.id }
                    val action = lane.defaultActions.firstOrNull()
                    val selectedDays = rules.flatMap { rule -> when (val timing = rule.timing) { is ScheduleTimingRule.RelativeDays -> timing.days.toList(); is ScheduleTimingRule.Weekly -> timing.weekdays.toList(); else -> emptyList() } }.toSet().ifEmpty { setOf(1) }
                    val overrides = rules.mapNotNull { rule ->
                        val day = Regex("^Day (\\d+) override$").matchEntire(rule.label)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val time = when (val timing = rule.timing) {
                            is ScheduleTimingRule.RelativeDays -> timing.time
                            is ScheduleTimingRule.Weekly -> timing.time
                            else -> null
                        }
                        if (day != null && time != null) day to (time.hour to time.minute) else null
                    }.toMap()
                    val cronExpression = rules.firstNotNullOfOrNull { (it.timing as? ScheduleTimingRule.Cron)?.expression } ?: "0 9 * * *"
                    add(BuilderLane(lane.name, lane.defaultTime.hour, lane.defaultTime.minute, action?.type ?: ScheduleActionType.NOTIFIER, action?.message.orEmpty(), action?.presetId.orEmpty(), action?.snoozeMinutes ?: 10, action?.followUpCount ?: 0, action?.followUpIntervalMinutes ?: 30, lane.missedStartPolicy, selectedDays, overrides, cronExpression, action?.presetLaunchMode ?: SchedulePresetLaunchMode.FOLLOW_PRESET))
                }
            }
        }
        var status by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var timeDialogLane by remember { mutableStateOf<Int?>(null) }
        var dialogHour by remember { mutableStateOf(9) }
        var dialogMinute by remember { mutableStateOf(0) }
        var timeDialogDay by remember { mutableStateOf<Int?>(null) }
        var ruleDialogLane by remember { mutableStateOf<Int?>(null) }
        var ruleEvery by remember { mutableStateOf("5") }
        var ruleStart by remember { mutableStateOf("1") }
        var ruleEnd by remember { mutableStateOf("22") }
        var expandedLanes by remember { mutableStateOf(emptySet<Int>()) }
        var presetMenuLane by remember { mutableStateOf<Int?>(null) }
        val presets = remember { ProtocolLibraryRepository.presets(app) }
        val days = sequenceDays.toIntOrNull()?.coerceIn(1, 366) ?: 22
        val gridScroll = rememberScrollState()
        val bodyScroll = rememberScrollState()

        fun save() {
            val totalDays = sequenceDays.toIntOrNull()?.takeIf { it in 1..366 }
            val duration = durationDays.toLongOrNull()?.takeIf { it > 0 }?.let(Duration::ofDays)
            val runs = occurrenceCount.toIntOrNull()?.takeIf { it > 0 }
            val parsedStart = startDate.trim()
                .let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.atTime(startHour, startMinute)
                ?.atZone(ZoneId.systemDefault())
            val intervalNumber = intervalValue.toLongOrNull()?.takeIf { it > 0 }
            val intervalDuration = intervalNumber?.let { value ->
                when (intervalUnit) {
                    IntervalUnit.MINUTES -> Duration.ofMinutes(value)
                    IntervalUnit.HOURS -> Duration.ofHours(value)
                    IntervalUnit.DAYS -> Duration.ofDays(value)
                }
            }
            val monthDay = monthlyDay.toIntOrNull()?.takeIf { it in 1..31 }

            if (name.isBlank()) {
                status = "Give the schedule a name."
                return
            }
            if (mode == BuilderMode.SEQUENCE && totalDays == null) {
                status = "Enter a sequence length between 1 and 366 days."
                return
            }
            if (mode == BuilderMode.INTERVAL && intervalDuration == null) {
                status = "Enter an interval greater than zero."
                return
            }
            if (mode == BuilderMode.MONTHLY && monthDay == null) {
                status = "Choose a day of month from 1 to 31."
                return
            }
            if (endMode == ScheduleEndMode.DURATION && duration == null && mode != BuilderMode.ONCE) {
                status = "Enter a duration greater than zero days."
                return
            }
            if (endMode == ScheduleEndMode.OCCURRENCE_COUNT && runs == null && mode != BuilderMode.ONCE) {
                status = "Enter the number of occurrences."
                return
            }
            if (startMode == BuilderStartMode.ABSOLUTE && parsedStart == null) {
                status = "Use YYYY-MM-DD and a valid start time."
                return
            }

            val laneData = lanes.mapNotNull { lane ->
                val time = LocalTime.of(lane.hour, lane.minute)
                val action = when {
                    lane.actionType == ScheduleActionType.NOTIFIER -> ScheduleAction(
                        type = ScheduleActionType.NOTIFIER,
                        title = lane.name,
                        message = lane.message.ifBlank { lane.name },
                        snoozeMinutes = lane.snoozeMinutes,
                        followUpCount = lane.followUpCount,
                        followUpIntervalMinutes = lane.followUpIntervalMinutes
                    )
                    lane.presetId.isNotBlank() -> ScheduleAction(
                        type = ScheduleActionType.PRESET,
                        presetId = lane.presetId,
                        presetLaunchMode = lane.presetLaunchMode,
                        title = presets.firstOrNull { it.id == lane.presetId }?.name.orEmpty(),
                        message = lane.message.ifBlank { "Scheduled activity due" },
                        snoozeMinutes = lane.snoozeMinutes,
                        followUpCount = lane.followUpCount,
                        followUpIntervalMinutes = lane.followUpIntervalMinutes
                    )
                    else -> null
                } ?: return@mapNotNull null
                ScheduleLane(
                    name = lane.name.ifBlank { "Activity" },
                    defaultTime = time,
                    defaultActions = listOf(action),
                    missedStartPolicy = lane.missedStartPolicy
                ) to lane
            }
            if (laneData.size != lanes.size) {
                status = "Every activity needs an action. Preset activities also need a selected preset."
                return
            }

            val validationAnchor = parsedStart ?: java.time.ZonedDateTime.now(ZoneId.systemDefault())
            if (mode == BuilderMode.CRON) {
                val bad = laneData.firstOrNull { (_, lane) ->
                    CronSchedule.nextOrNull(lane.cronExpression.trim(), validationAnchor) == null
                }
                if (bad != null) {
                    status = "Invalid cron pattern for ${bad.second.name.ifBlank { "an activity" }}. Use five fields, e.g. 0 9 * * 1-5."
                    return
                }
            }

            val planLanes = laneData.map { it.first }
            val built = laneData.flatMap { (scheduleLane, lane) ->
                when (mode) {
                    BuilderMode.ONCE -> listOf(
                        ScheduleRule(scheduleLane.id, ScheduleTimingRule.Once(), label = "Once")
                    )
                    BuilderMode.INTERVAL -> listOf(
                        ScheduleRule(
                            scheduleLane.id,
                            ScheduleTimingRule.ElapsedInterval(intervalDuration!!, intervalImmediate),
                            label = "Every ${intervalValue.trim()} ${intervalUnit.name.lowercase()}"
                        )
                    )
                    BuilderMode.DAILY -> when (dailyTimingMode) {
                        DailyTimingMode.FROM_START -> listOf(
                            ScheduleRule(
                                scheduleLane.id,
                                ScheduleTimingRule.AnchoredCalendarDays(1, dailyImmediate),
                                label = "Daily from start"
                            )
                        )
                        DailyTimingMode.FIXED_TIMES -> listOf(
                            ScheduleRule(
                                scheduleLane.id,
                                ScheduleTimingRule.Weekly(ALL_WEEKDAYS, scheduleLane.defaultTime),
                                label = "Daily at ${lane.time}"
                            )
                        )
                    }
                    BuilderMode.WEEKLY -> {
                        val overrideDays = lane.dayTimes.keys
                        val defaultDays = (lane.days - overrideDays).filter { it in 1..7 }.toSet()
                        val defaultRule = if (defaultDays.isEmpty()) emptyList() else listOf(
                            ScheduleRule(
                                scheduleLane.id,
                                ScheduleTimingRule.Weekly(defaultDays, scheduleLane.defaultTime),
                                label = if (overrideDays.isEmpty()) "Weekly" else "Default"
                            )
                        )
                        val overrideRules = lane.dayTimes
                            .filterKeys { it in 1..7 }
                            .map { (day, hm) ->
                                ScheduleRule(
                                    scheduleLane.id,
                                    ScheduleTimingRule.Weekly(setOf(day), LocalTime.of(hm.first, hm.second)),
                                    label = "Day $day override"
                                )
                            }
                        defaultRule + overrideRules
                    }
                    BuilderMode.MONTHLY -> listOf(
                        ScheduleRule(
                            scheduleLane.id,
                            ScheduleTimingRule.MonthlyDayOfMonth(monthDay!!, scheduleLane.defaultTime),
                            label = "Monthly day $monthDay"
                        )
                    )
                    BuilderMode.SEQUENCE -> {
                        val overrideDays = lane.dayTimes.keys
                        val defaultDays = (lane.days - overrideDays)
                            .filter { it <= (totalDays ?: 1) }
                            .toSet()
                        val defaultRule = if (defaultDays.isEmpty()) emptyList() else listOf(
                            ScheduleRule(
                                scheduleLane.id,
                                ScheduleTimingRule.RelativeDays(defaultDays, scheduleLane.defaultTime),
                                label = if (overrideDays.isEmpty()) "Day sequence" else "Default"
                            )
                        )
                        val overrideRules = lane.dayTimes
                            .filterKeys { it <= (totalDays ?: 1) }
                            .map { (day, hm) ->
                                ScheduleRule(
                                    scheduleLane.id,
                                    ScheduleTimingRule.RelativeDays(setOf(day), LocalTime.of(hm.first, hm.second)),
                                    label = "Day $day override"
                                )
                            }
                        defaultRule + overrideRules
                    }
                    BuilderMode.CRON -> listOf(
                        ScheduleRule(
                            scheduleLane.id,
                            ScheduleTimingRule.Cron(lane.cronExpression.trim()),
                            label = "Cron ${lane.cronExpression.trim()}"
                        )
                    )
                }
            }
            if (built.isEmpty()) {
                status = "Select at least one day or timing rule."
                return
            }

            val termination = when {
                mode == BuilderMode.ONCE -> ScheduleTermination(
                    ScheduleEndMode.OCCURRENCE_COUNT,
                    occurrenceCount = planLanes.size.coerceAtLeast(1)
                )
                endMode == ScheduleEndMode.FOREVER -> ScheduleTermination(ScheduleEndMode.FOREVER)
                endMode == ScheduleEndMode.OCCURRENCE_COUNT -> ScheduleTermination(
                    ScheduleEndMode.OCCURRENCE_COUNT,
                    occurrenceCount = runs
                )
                else -> ScheduleTermination(ScheduleEndMode.DURATION, duration = duration)
            }
            val activation = when (startMode) {
                BuilderStartMode.MANUAL_DAY_ONE -> ScheduleActivation.MANUAL_DAY_ONE
                BuilderStartMode.CALENDAR_NOW -> ScheduleActivation.CALENDAR_RULE
                BuilderStartMode.ABSOLUTE -> ScheduleActivation.ABSOLUTE_START
            }
            val plan = SchedulePlan(
                id = existingPlan?.id ?: java.util.UUID.randomUUID().toString(),
                name = name.trim(),
                description = description.trim(),
                activation = activation,
                startAt = parsedStart.takeIf { startMode == BuilderStartMode.ABSOLUTE },
                termination = termination,
                lanes = planLanes,
                rules = built,
                version = (existingPlan?.version ?: 0) + 1,
                enabled = existingPlan?.enabled ?: true,
                createdAt = existingPlan?.createdAt ?: java.time.ZonedDateTime.now(),
                updatedAt = java.time.ZonedDateTime.now()
            )

            // Editing a live plan must replace its generated occurrence set.
            // Otherwise the UI changes but the old alarms keep running.
            SchedulePlanStore.allInstances(app)
                .filter { it.planId == plan.id && it.stoppedAt == null }
                .forEach { SchedulePlanRuntime.cancel(app, it) }
            SchedulePlanStore.removeInstancesForPlan(app, plan.id)
            SchedulePlanStore.savePlan(app, plan)

            if (Build.VERSION.SDK_INT >= 33) {
                hostActivity?.requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 7402)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmManager = app.getSystemService(AlarmManager::class.java)
                if (!alarmManager.canScheduleExactAlarms()) {
                    val exactAlarmIntent = Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:${app.packageName}")
                    )
                    if (hostActivity != null) {
                        hostActivity.startActivity(exactAlarmIntent)
                    } else {
                        exactAlarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        app.startActivity(exactAlarmIntent)
                    }
                    status = "Saved ${plan.name}. Allow Alarms & reminders for exact timing; MethodMesh will arm it automatically when permission is granted."
                    return
                }
            }

            if (plan.activation != ScheduleActivation.MANUAL_DAY_ONE) {
                val anchor = when (plan.activation) {
                    ScheduleActivation.ABSOLUTE_START -> plan.startAt ?: java.time.ZonedDateTime.now(plan.timezone)
                    else -> java.time.ZonedDateTime.now(plan.timezone).withNano(0)
                }
                SchedulePlanRuntime.start(app, plan, anchor)
            }

            status = if (plan.activation == ScheduleActivation.MANUAL_DAY_ONE) {
                "Saved ${plan.name}. Start it when you are ready."
            } else {
                "Saved ${plan.name}. Schedule is armed."
            }
            val execution = As100SchedulerMethod.result(
                As100SchedulerMethod.request(capabilityId, emptyMap(), emptyList(), emptyList()),
                SchedulerOutcome(null, "created"),
                context.request.invocationContext
            )
            result = execution
            onConfirmed(execution)
        }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { result = null }, { result?.let(onConfirmed) }, onCancel) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(bodyScroll)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (existingPlan == null) "New schedule" else "Edit schedule",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        when (mode) {
                            BuilderMode.ONCE -> "Run the activity once when the schedule starts."
                            BuilderMode.INTERVAL -> "Run immediately when started, then repeat after an elapsed interval."
                            BuilderMode.DAILY -> "Repeat by local calendar day, either from the start time or at fixed times."
                            BuilderMode.WEEKLY -> "Choose the weekdays and times for each activity."
                            BuilderMode.MONTHLY -> "Run on a chosen day of each month."
                            BuilderMode.SEQUENCE -> "Build a finite day-by-day sequence with one or more activities."
                            BuilderMode.CRON -> "Use a five-field cron rule for advanced calendar scheduling."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Plan", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            name,
                            { name = it },
                            label = { Text("Name") },
                            placeholder = { Text("e.g. Daily Kobo follow-up") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            description,
                            { description = it },
                            label = { Text("Description · optional") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pattern", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mode == BuilderMode.ONCE,
                            onClick = { mode = BuilderMode.ONCE },
                            label = { Text("Once") }
                        )
                        FilterChip(
                            selected = mode == BuilderMode.INTERVAL,
                            onClick = {
                                mode = BuilderMode.INTERVAL
                                if (existingPlan == null && endMode == ScheduleEndMode.DURATION) {
                                    endMode = ScheduleEndMode.OCCURRENCE_COUNT
                                    occurrenceCount = "24"
                                }
                            },
                            label = { Text("Interval") }
                        )
                        FilterChip(
                            selected = mode == BuilderMode.DAILY,
                            onClick = { mode = BuilderMode.DAILY },
                            label = { Text("Daily") }
                        )
                        FilterChip(
                            selected = mode == BuilderMode.WEEKLY,
                            onClick = { mode = BuilderMode.WEEKLY },
                            label = { Text("Weekly") }
                        )
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = mode == BuilderMode.MONTHLY,
                            onClick = { mode = BuilderMode.MONTHLY },
                            label = { Text("Monthly") }
                        )
                        FilterChip(
                            selected = mode == BuilderMode.SEQUENCE,
                            onClick = { mode = BuilderMode.SEQUENCE },
                            label = { Text("Day sequence") }
                        )
                        FilterChip(
                            selected = mode == BuilderMode.CRON,
                            onClick = { mode = BuilderMode.CRON },
                            label = { Text("Cron") }
                        )
                    }

                    Text(
                        when (mode) {
                            BuilderMode.ONCE -> "One occurrence at the moment the schedule is started."
                            BuilderMode.INTERVAL -> "Elapsed time from the trigger: e.g. every 1 hour from whenever Start is pressed."
                            BuilderMode.DAILY -> "Calendar-day recurrence. This preserves local wall-clock time across daylight-saving changes."
                            BuilderMode.WEEKLY -> "Paint the weekdays on which each activity should run."
                            BuilderMode.MONTHLY -> "Choose the day of month; shorter months use their final day."
                            BuilderMode.SEQUENCE -> "Paint the numbered days on which each activity should run."
                            BuilderMode.CRON -> "Advanced calendar rule: minute hour day month weekday. Example: 0 9 * * 1-5."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    when (mode) {
                        BuilderMode.INTERVAL -> {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    intervalValue,
                                    { intervalValue = it.filter(Char::isDigit) },
                                    label = { Text("Every") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                                FilterChip(
                                    selected = intervalUnit == IntervalUnit.MINUTES,
                                    onClick = { intervalUnit = IntervalUnit.MINUTES },
                                    label = { Text("min") }
                                )
                                FilterChip(
                                    selected = intervalUnit == IntervalUnit.HOURS,
                                    onClick = { intervalUnit = IntervalUnit.HOURS },
                                    label = { Text("hours") }
                                )
                                FilterChip(
                                    selected = intervalUnit == IntervalUnit.DAYS,
                                    onClick = { intervalUnit = IntervalUnit.DAYS },
                                    label = { Text("days") }
                                )
                            }
                            FilterChip(
                                selected = intervalImmediate,
                                onClick = { intervalImmediate = !intervalImmediate },
                                label = { Text(if (intervalImmediate) "✓ Run immediately when started" else "First run after one interval") }
                            )
                        }
                        BuilderMode.DAILY -> {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = dailyTimingMode == DailyTimingMode.FROM_START,
                                    onClick = { dailyTimingMode = DailyTimingMode.FROM_START },
                                    label = { Text("From start time") }
                                )
                                FilterChip(
                                    selected = dailyTimingMode == DailyTimingMode.FIXED_TIMES,
                                    onClick = { dailyTimingMode = DailyTimingMode.FIXED_TIMES },
                                    label = { Text("Fixed times") }
                                )
                            }
                            if (dailyTimingMode == DailyTimingMode.FROM_START) {
                                FilterChip(
                                    selected = dailyImmediate,
                                    onClick = { dailyImmediate = !dailyImmediate },
                                    label = { Text(if (dailyImmediate) "✓ Run immediately when started" else "First run tomorrow") }
                                )
                            }
                        }
                        BuilderMode.MONTHLY -> {
                            OutlinedTextField(
                                monthlyDay,
                                { monthlyDay = it.filter(Char::isDigit) },
                                label = { Text("Day of month") },
                                supportingText = { Text("1–31 · shorter months use their last day") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        BuilderMode.SEQUENCE -> {
                            OutlinedTextField(
                                sequenceDays,
                                { sequenceDays = it.filter(Char::isDigit) },
                                label = { Text("Sequence length · days") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        else -> Unit
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Starts", style = MaterialTheme.typography.titleMedium)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = startMode == BuilderStartMode.MANUAL_DAY_ONE,
                            onClick = { startMode = BuilderStartMode.MANUAL_DAY_ONE },
                            label = { Text(if (mode == BuilderMode.SEQUENCE) "Start Day 1" else "Manual") }
                        )
                        FilterChip(
                            selected = startMode == BuilderStartMode.CALENDAR_NOW,
                            onClick = { startMode = BuilderStartMode.CALENDAR_NOW },
                            label = { Text(if (mode == BuilderMode.SEQUENCE) "Calendar now" else "Start now") }
                        )
                        FilterChip(
                            selected = startMode == BuilderStartMode.ABSOLUTE,
                            onClick = { startMode = BuilderStartMode.ABSOLUTE },
                            label = { Text("On date") }
                        )
                    }
                    if (startMode == BuilderStartMode.ABSOLUTE) {
                        OutlinedTextField(
                            startDate,
                            { startDate = it },
                            label = { Text("Start date") },
                            supportingText = { Text("YYYY-MM-DD") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedButton(
                            onClick = {
                                dialogHour = startHour
                                dialogMinute = startMinute
                                timeDialogLane = -1
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Start time  ·  %02d:%02d".format(startHour, startMinute))
                        }
                    }
                }

                if (mode != BuilderMode.ONCE) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Ends", style = MaterialTheme.typography.titleMedium)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = endMode == ScheduleEndMode.DURATION,
                                onClick = { endMode = ScheduleEndMode.DURATION },
                                label = { Text("After duration") }
                            )
                            FilterChip(
                                selected = endMode == ScheduleEndMode.OCCURRENCE_COUNT,
                                onClick = { endMode = ScheduleEndMode.OCCURRENCE_COUNT },
                                label = { Text("After runs") }
                            )
                            FilterChip(
                                selected = endMode == ScheduleEndMode.FOREVER,
                                onClick = { endMode = ScheduleEndMode.FOREVER },
                                label = { Text("Forever") }
                            )
                        }
                        when (endMode) {
                            ScheduleEndMode.DURATION -> OutlinedTextField(
                                durationDays,
                                { durationDays = it.filter(Char::isDigit) },
                                label = { Text("Duration · days") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            ScheduleEndMode.OCCURRENCE_COUNT -> OutlinedTextField(
                                occurrenceCount,
                                { occurrenceCount = it.filter(Char::isDigit) },
                                label = { Text("Stop after occurrences") },
                                supportingText = { Text("Counts scheduled occurrences across all activities.") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            ScheduleEndMode.FOREVER -> Text(
                                "The schedule stays active until you stop it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            else -> Unit
                        }
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        if (mode == BuilderMode.DAILY && dailyTimingMode == DailyTimingMode.FIXED_TIMES) "Activities & times" else "Activities",
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (mode == BuilderMode.WEEKLY || mode == BuilderMode.SEQUENCE) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(gridScroll)) {
                            Text(
                                if (mode == BuilderMode.WEEKLY) "ACTIVITY" else "ACTIVITY / DAY",
                                modifier = Modifier.width(152.dp).padding(horizontal = 4.dp, vertical = 6.dp),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            (1..if (mode == BuilderMode.WEEKLY) 7 else days).forEach { day ->
                                Text(
                                    if (mode == BuilderMode.WEEKLY) DayOfWeek.of(day).name.take(3) else day.toString(),
                                    modifier = Modifier.width(38.dp).padding(vertical = 6.dp),
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    lanes.forEachIndexed { index, lane ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            tonalElevation = 1.dp
                        ) {
                            Column(
                                Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    lane.name,
                                    { lanes[index] = lane.copy(name = it) },
                                    label = { Text("Activity") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )

                                val needsClockTime = mode == BuilderMode.WEEKLY ||
                                    mode == BuilderMode.MONTHLY ||
                                    mode == BuilderMode.SEQUENCE ||
                                    (mode == BuilderMode.DAILY && dailyTimingMode == DailyTimingMode.FIXED_TIMES)

                                if (needsClockTime) {
                                    Button(
                                        onClick = {
                                            dialogHour = lane.hour
                                            dialogMinute = lane.minute
                                            timeDialogLane = index
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(lane.time, style = MaterialTheme.typography.titleMedium)
                                    }
                                }

                                if (mode == BuilderMode.CRON) {
                                    OutlinedTextField(
                                        lane.cronExpression,
                                        { lanes[index] = lane.copy(cronExpression = it) },
                                        label = { Text("Cron pattern") },
                                        supportingText = { Text("minute hour day month weekday · e.g. 0 9 * * 1-5") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                if (mode == BuilderMode.WEEKLY || mode == BuilderMode.SEQUENCE) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(gridScroll),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        (1..if (mode == BuilderMode.WEEKLY) 7 else days).forEach { day ->
                                            val selected = day in lane.days
                                            val override = lane.dayTimes[day]
                                            Box(
                                                Modifier
                                                    .width(38.dp)
                                                    .height(48.dp)
                                                    .padding(2.dp)
                                                    .border(
                                                        1.dp,
                                                        if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                    .background(
                                                        if (selected) MaterialTheme.colorScheme.primaryContainer
                                                        else Color.Transparent
                                                    )
                                                    .combinedClickable(
                                                        onClick = {
                                                            val next = lane.days.toMutableSet().apply {
                                                                if (!remove(day)) add(day)
                                                            }
                                                            lanes[index] = lane.copy(
                                                                days = next,
                                                                dayTimes = if (day in next) lane.dayTimes else lane.dayTimes - day
                                                            )
                                                        },
                                                        onLongClick = {
                                                            if (selected) {
                                                                dialogHour = override?.first ?: lane.hour
                                                                dialogMinute = override?.second ?: lane.minute
                                                                timeDialogLane = index
                                                                timeDialogDay = day
                                                            }
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(
                                                        if (mode == BuilderMode.WEEKLY) DayOfWeek.of(day).name.take(1) else day.toString(),
                                                        fontSize = 10.sp,
                                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        if (selected) (if (override == null) "●" else "◆") else "○",
                                                        fontSize = 11.sp,
                                                        color = if (selected) MaterialTheme.colorScheme.primary
                                                        else MaterialTheme.colorScheme.outline
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Text(
                                        "Tap to include a day · long-press a selected day to give it a different time.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Text("Action", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = lane.actionType == ScheduleActionType.NOTIFIER,
                                        onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.NOTIFIER) },
                                        label = { Text("Notification") }
                                    )
                                    FilterChip(
                                        selected = lane.actionType == ScheduleActionType.PRESET,
                                        onClick = { lanes[index] = lane.copy(actionType = ScheduleActionType.PRESET) },
                                        label = { Text("Preset") }
                                    )
                                }

                                if (lane.actionType == ScheduleActionType.NOTIFIER) {
                                    OutlinedTextField(
                                        lane.message,
                                        { lanes[index] = lane.copy(message = it) },
                                        label = { Text("Message") },
                                        placeholder = { Text("What should the notification say?") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                } else {
                                    val selectedPresetName = presets
                                        .firstOrNull { it.id == lane.presetId }
                                        ?.name
                                        .orEmpty()
                                    Box(Modifier.fillMaxWidth()) {
                                        OutlinedButton(
                                            onClick = { presetMenuLane = index },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                selectedPresetName.ifBlank { "Choose preset" },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = presetMenuLane == index,
                                            onDismissRequest = { presetMenuLane = null }
                                        ) {
                                            presets.forEach { preset ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            if (lane.presetId == preset.id) "✓ ${preset.name}"
                                                            else preset.name
                                                        )
                                                    },
                                                    onClick = {
                                                        lanes[index] = lane.copy(presetId = preset.id)
                                                        presetMenuLane = null
                                                    }
                                                )
                                            }
                                            if (presets.isEmpty()) {
                                                DropdownMenuItem(
                                                    text = { Text("No saved presets") },
                                                    onClick = { presetMenuLane = null }
                                                )
                                            }
                                        }
                                    }
                                    val selectedPreset = presets.firstOrNull { it.id == lane.presetId }
                                    Text("Run behaviour", style = MaterialTheme.typography.labelLarge)
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = lane.presetLaunchMode == SchedulePresetLaunchMode.FOLLOW_PRESET,
                                            onClick = { lanes[index] = lane.copy(presetLaunchMode = SchedulePresetLaunchMode.FOLLOW_PRESET) },
                                            label = { Text("Follow preset") }
                                        )
                                        FilterChip(
                                            selected = lane.presetLaunchMode == SchedulePresetLaunchMode.INTERACTIVE,
                                            onClick = { lanes[index] = lane.copy(presetLaunchMode = SchedulePresetLaunchMode.INTERACTIVE) },
                                            label = { Text("Show UI") }
                                        )
                                        FilterChip(
                                            selected = lane.presetLaunchMode == SchedulePresetLaunchMode.BACKGROUND,
                                            onClick = { lanes[index] = lane.copy(presetLaunchMode = SchedulePresetLaunchMode.BACKGROUND) },
                                            label = { Text("Background") }
                                        )
                                    }
                                    Text(
                                        when (lane.presetLaunchMode) {
                                            SchedulePresetLaunchMode.INTERACTIVE -> "This schedule always opens the preset UI."
                                            SchedulePresetLaunchMode.BACKGROUND -> "This schedule runs the preset without opening its UI."
                                            SchedulePresetLaunchMode.FOLLOW_PRESET -> when (PresetLaunchMode.normalize(selectedPreset?.launchMode.orEmpty())) {
                                                PresetLaunchMode.BACKGROUND -> "Following preset: Background."
                                                PresetLaunchMode.INTERACTIVE -> "Following preset: Show UI."
                                                else -> "Following preset: Auto."
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        lane.message,
                                        { lanes[index] = lane.copy(message = it) },
                                        label = { Text("Notification message") },
                                        placeholder = { Text("It's time to run this form") },
                                        supportingText = {
                                            Text("Shown when this scheduled action is due.")
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )
                                }

                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (mode == BuilderMode.DAILY && dailyTimingMode == DailyTimingMode.FIXED_TIMES) {
                                        OutlinedButton(
                                            onClick = {
                                                lanes.add(
                                                    index + 1,
                                                    lane.copy(hour = (lane.hour + 4) % 24)
                                                )
                                            }
                                        ) {
                                            Text("+ Add time")
                                        }
                                    }
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            expandedLanes =
                                                if (index in expandedLanes) expandedLanes - index
                                                else expandedLanes + index
                                        }
                                    ) {
                                        Text(if (index in expandedLanes) "Hide options" else "More options")
                                    }
                                    if (lanes.size > 1) {
                                        androidx.compose.material3.TextButton(
                                            onClick = {
                                                lanes.removeAt(index)
                                                expandedLanes = expandedLanes
                                                    .filter { it != index }
                                                    .map { if (it > index) it - 1 else it }
                                                    .toSet()
                                            }
                                        ) {
                                            Text("Remove")
                                        }
                                    }
                                }

                                if (index in expandedLanes) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(1.dp)
                                            .background(MaterialTheme.colorScheme.outlineVariant)
                                    )
                                    Text("Reminder behaviour", style = MaterialTheme.typography.labelLarge)

                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            lane.snoozeMinutes.toString(),
                                            {
                                                lanes[index] = lane.copy(
                                                    snoozeMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0
                                                )
                                            },
                                            label = { Text("Snooze") },
                                            suffix = { Text("min") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            lane.followUpCount.toString(),
                                            {
                                                lanes[index] = lane.copy(
                                                    followUpCount = it.filter(Char::isDigit).toIntOrNull() ?: 0
                                                )
                                            },
                                            label = { Text("Follow-ups") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            lane.followUpIntervalMinutes.toString(),
                                            {
                                                lanes[index] = lane.copy(
                                                    followUpIntervalMinutes = it.filter(Char::isDigit).toIntOrNull() ?: 0
                                                )
                                            },
                                            label = { Text("Repeat") },
                                            suffix = { Text("min") },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true
                                        )
                                    }

                                    Text(
                                        "If MethodMesh starts after this run was due",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        FilterChip(
                                            selected = lane.missedStartPolicy == ScheduleMissedStartPolicy.RUN_MISSED,
                                            onClick = {
                                                lanes[index] = lane.copy(
                                                    missedStartPolicy = ScheduleMissedStartPolicy.RUN_MISSED
                                                )
                                            },
                                            label = { Text("Run it") }
                                        )
                                        FilterChip(
                                            selected = lane.missedStartPolicy == ScheduleMissedStartPolicy.SKIP_MISSED,
                                            onClick = {
                                                lanes[index] = lane.copy(
                                                    missedStartPolicy = ScheduleMissedStartPolicy.SKIP_MISSED
                                                )
                                            },
                                            label = { Text("Skip it") }
                                        )
                                    }

                                    if (mode == BuilderMode.SEQUENCE) {
                                        OutlinedButton(
                                            onClick = {
                                                ruleDialogLane = index
                                                ruleEvery = "5"
                                                ruleStart = "1"
                                                ruleEnd = sequenceDays
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Generate repeating day pattern")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = { lanes += BuilderLane("Activity ${lanes.size + 1}") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("+ Add activity")
                    }
                }

                Button(
                    onClick = ::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(if (existingPlan == null) "Save schedule" else "Save changes")
                }

                if (status.isNotBlank()) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            status,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
        timeDialogLane?.let { laneIndex ->
            Dialog(onDismissRequest = { timeDialogLane = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                androidx.compose.material3.Surface(Modifier.fillMaxWidth().padding(24.dp)) {
                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Choose time", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AndroidView(factory = { NumberPicker(it).apply { minValue = 0; maxValue = 23; value = dialogHour; setOnValueChangedListener { _, _, new -> dialogHour = new } } }, update = { it.value = dialogHour }, modifier = Modifier.width(90.dp).height(150.dp))
                            AndroidView(factory = { NumberPicker(it).apply { minValue = 0; maxValue = 59; value = dialogMinute; setOnValueChangedListener { _, _, new -> dialogMinute = new } } }, update = { it.value = dialogMinute }, modifier = Modifier.width(90.dp).height(150.dp))
                        }
                        Button(onClick = {
                            if (laneIndex == -1) { startHour = dialogHour; startMinute = dialogMinute } else {
                                val lane = lanes[laneIndex]
                                lanes[laneIndex] = if (timeDialogDay == null) lane.copy(hour = dialogHour, minute = dialogMinute) else lane.copy(dayTimes = lane.dayTimes + (timeDialogDay!! to (dialogHour to dialogMinute)))
                            }
                            timeDialogDay = null
                            timeDialogLane = null
                        }) { Text("Use time") }
                    }
                }
            }
        }
        ruleDialogLane?.let { laneIndex ->
            AlertDialog(
                onDismissRequest = { ruleDialogLane = null },
                title = { Text("Generate repeating days") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Create a pattern such as every 5 days from day 1 to day 22.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(ruleEvery, { ruleEvery = it.filter(Char::isDigit) }, label = { Text("Every") }, modifier = Modifier.width(100.dp), singleLine = true)
                            OutlinedTextField(ruleStart, { ruleStart = it.filter(Char::isDigit) }, label = { Text("Start") }, modifier = Modifier.width(100.dp), singleLine = true)
                            OutlinedTextField(ruleEnd, { ruleEnd = it.filter(Char::isDigit) }, label = { Text("End") }, modifier = Modifier.width(100.dp), singleLine = true)
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        val step = ruleEvery.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val start = ruleStart.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        val end = ruleEnd.toIntOrNull()?.coerceAtLeast(start) ?: start
                        lanes[laneIndex] = lanes[laneIndex].copy(days = generateSequence(start) { previous -> (previous + step).takeIf { it <= end } }.toSet())
                        ruleDialogLane = null
                    }) { Text("Apply") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { ruleDialogLane = null }) { Text("Cancel") } }
            )
        }
    }
}
