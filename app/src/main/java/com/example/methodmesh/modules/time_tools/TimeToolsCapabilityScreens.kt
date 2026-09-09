package com.example.methodmesh.modules.time_tools

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.modules.time_tools.duration.DurationCalculatorCapability
import com.example.methodmesh.modules.time_tools.elapsed.ElapsedTimeCapability
import com.example.methodmesh.modules.time_tools.notifications.ActiveTimerKind
import com.example.methodmesh.modules.time_tools.notifications.TimeToolsTimerRuntime
import com.example.methodmesh.modules.time_tools.timing.AlarmRepeat
import com.example.methodmesh.modules.time_tools.timing.AlarmSchedule
import com.example.methodmesh.modules.time_tools.timing.AlertProfile
import com.example.methodmesh.modules.time_tools.timing.TimeFormatting
import com.example.methodmesh.modules.time_tools.until.UntilTimeCapability
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.android.IntentRouterActivity
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.time.*
import kotlinx.coroutines.delay

object CountdownMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CountdownMethod.ID
    override val title = "Countdown"
    override val description = "Count down a duration or to a chosen date and time."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val android = LocalContext.current
        val runtime = remember { TimeToolsTimerRuntime(android) }
        var hours by rememberSaveable { mutableStateOf(((context.action.settings.v("duration_ms")?.toLongOrNull() ?: 300000L) / 3600000).toString()) }
        var minutes by rememberSaveable { mutableStateOf((((context.action.settings.v("duration_ms")?.toLongOrNull() ?: 300000L) % 3600000) / 60000).toString()) }
        var seconds by rememberSaveable { mutableStateOf((((context.action.settings.v("duration_ms")?.toLongOrNull() ?: 300000L) % 60000) / 1000).toString()) }
        var inputMode by rememberSaveable { mutableStateOf(context.action.settings.v("input_mode") ?: "duration") }
        var targetMs by rememberSaveable { mutableStateOf(System.currentTimeMillis() + 3600000L) }
        var timerId by rememberSaveable { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var snap by remember { mutableStateOf<TimeToolsTimerRuntime.Snapshot?>(null) }
        var launched by rememberSaveable { mutableStateOf(false) }
        val reminder = remember(context.action.settings) { reminderConfig(context.action.settings, true) }

        fun durationMs() = ((hours.toLongOrNull() ?: 0) * 3600000L + (minutes.toLongOrNull() ?: 0) * 60000L + (seconds.toLongOrNull() ?: 0) * 1000L).coerceAtLeast(1000L)
        fun start() {
            val start = if (inputMode == "date_time") {
                runtime.startUntil(Instant.ofEpochMilli(targetMs), reminder.label.ifBlank { "Countdown" }, reminder.message, ZoneId.systemDefault().id, reminder.profile, reminder.ongoing, reminder.lock, reminder.privateMessage.not(), reminder.confirm, reminder.followups, reminder.followupMinutes, reminder.snooze)
            } else runtime.startCountdown(durationMs(), reminder.label.ifBlank { "Countdown" }, reminder.message, reminder.profile, reminder.ongoing, reminder.lock, reminder.privateMessage.not(), reminder.confirm, reminder.followups, reminder.followupMinutes, reminder.snooze)
            timerId = start.timerId
            if (context.submitsImmediately) {
                val core = linkedMapOf<String, Any?>(TimeToolsFields.FORMATTED_DURATION to if (inputMode == "date_time") TimeFormatting.longRange(Instant.ofEpochMilli(targetMs)) else TimeFormatting.duration(durationMs()), TimeToolsFields.DURATION_MS to durationMs(), TimeToolsFields.REQUESTED_DURATION_MS to durationMs(), TimeToolsFields.TIMER_ID to start.timerId, TimeToolsFields.EXACT_ALERT_SCHEDULED to start.exactAlertScheduled, TimeToolsFields.COMPLETION_STATUS to "scheduled", TimeToolsFields.MESSAGE to reminder.message)
                result = execution(As100CountdownMethod, context, core); result?.let(onConfirmed)
            }
        }
        LaunchedEffect(timerId) { while (timerId != null && result == null) { snap = timerId?.let(runtime::snapshot); if ((snap?.remainingMs == 0L || snap?.completed == true) && !context.submitsImmediately) { val s=snap!!; result = execution(As100CountdownMethod, context, mapOf(TimeToolsFields.FORMATTED_DURATION to s.formatted, TimeToolsFields.DURATION_MS to s.elapsedMs, TimeToolsFields.ACTUAL_ELAPSED_MS to s.elapsedMs, TimeToolsFields.TIMER_ID to s.id, TimeToolsFields.COMPLETION_STATUS to "completed", TimeToolsFields.MESSAGE to s.message)); }; delay(100) } }
        LaunchedEffect(context.startsImmediately) { if (context.startsImmediately && !launched) { launched=true; start() } }

        CapabilityScreenScaffold(title, capabilityId, context, context.stepNumber > 1, result, result?.let { OutputFormatter.fields(it,false) }.orEmpty(), onBack, { result=null; timerId=null }, { result?.let(onConfirmed) }, { timerId?.let(runtime::cancel); onCancel() }) {
            if (timerId == null) {
                SegmentedToggle(inputMode == "duration", "Duration", "Date & time") { inputMode = if (it) "duration" else "date_time" }
                Spacer(Modifier.height(14.dp))
                if (inputMode == "duration") HmsEditor(hours, minutes, seconds, {hours=it}, {minutes=it}, {seconds=it})
                else DateTimePickerRow(android, targetMs) { targetMs = it }
                Spacer(Modifier.height(12.dp)); ReminderSummary(reminder)
                Button(::start, Modifier.fillMaxWidth().height(56.dp)) { Text("Start", fontSize=18.sp) }
            } else {
                TimerHero(snap?.formatted ?: "…", snap?.remainingMs?.let { 1f - it.toFloat() / durationMs().coerceAtLeast(1).toFloat() } ?: 0f, snap?.label ?: "Countdown")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (snap?.kind == ActiveTimerKind.COUNTDOWN) OutlinedButton({ timerId?.let(runtime::addMinute) }, Modifier.weight(1f)) { Text("+1:00") }
                    Button({ timerId?.let { if (snap?.paused == true) runtime.resume(it) else runtime.pause(it) } }, Modifier.weight(1f)) { Text(if (snap?.paused == true) "Resume" else "Pause") }
                    OutlinedButton({ timerId?.let { val s=runtime.stop(it); if (s!=null) result=execution(As100CountdownMethod,context,mapOf(TimeToolsFields.FORMATTED_DURATION to s.formatted,TimeToolsFields.DURATION_MS to s.elapsedMs,TimeToolsFields.TIMER_ID to s.id,TimeToolsFields.COMPLETION_STATUS to "stopped")) } }, Modifier.weight(1f)) { Text("Stop") }
                }
            }
        }
    }
}

object StopwatchMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100StopwatchMethod.ID; override val title = "Stopwatch"; override val description = "Live stopwatch with laps and shade/lock-screen controls."
    @Composable override fun Render(context: CapabilityScreenContext, onBack:()->Unit, onConfirmed:(ExecutionResult)->Unit, onCancel:()->Unit) {
        val android = LocalContext.current
        val runtime = remember(android) { TimeToolsTimerRuntime(android) }; var id by rememberSaveable{mutableStateOf<String?>(null)}; var snap by remember{mutableStateOf<TimeToolsTimerRuntime.Snapshot?>(null)}; var result by remember{mutableStateOf<ExecutionResult?>(null)}; var launched by rememberSaveable{mutableStateOf(false)}
        fun start(){ val s=runtime.startStopwatch(context.action.settings.v("label") ?: "Stopwatch", context.action.settings.v("message").orEmpty(), context.action.settings.b("notify_ongoing",true), context.action.settings.b("lock_screen",true)); id=s.timerId }
        LaunchedEffect(id){ while(id!=null&&result==null){ snap=id?.let(runtime::snapshot); val s=snap; if(s?.completed==true){ result=execution(As100StopwatchMethod,context,mapOf(TimeToolsFields.FORMATTED_DURATION to s.formatted,TimeToolsFields.DURATION_MS to s.elapsedMs,TimeToolsFields.LAP_COUNT to s.laps.size,TimeToolsFields.LAPS_JSON to s.laps.joinToString(prefix="[",postfix="]"),TimeToolsFields.TIMER_ID to s.id,TimeToolsFields.COMPLETION_STATUS to "completed")) }; delay(50) } }
        LaunchedEffect(context.startsImmediately){if(context.startsImmediately&&!launched){launched=true;start()}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{OutputFormatter.fields(it,false)}.orEmpty(),onBack,{result=null;id=null},{result?.let(onConfirmed)},{id?.let(runtime::cancel);onCancel()}){
            TimerHero(snap?.formatted ?: "00:00.00",1f,snap?.label ?: "Stopwatch")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedButton({id?.let(runtime::lap)},Modifier.weight(1f),enabled=id!=null&&snap?.paused!=true){Text("Lap")}
                Button({if(id==null)start() else id?.let{if(snap?.paused==true)runtime.resume(it)else runtime.pause(it)}},Modifier.weight(1f)){Text(if(id==null)"Start" else if(snap?.paused==true)"Resume" else "Pause")}
                OutlinedButton({id?.let{runtime.stop(it)?.let{s->result=execution(As100StopwatchMethod,context,mapOf(TimeToolsFields.FORMATTED_DURATION to s.formatted,TimeToolsFields.DURATION_MS to s.elapsedMs,TimeToolsFields.LAP_COUNT to s.laps.size,TimeToolsFields.LAPS_JSON to s.laps.joinToString(prefix="[",postfix="]"),TimeToolsFields.TIMER_ID to s.id,TimeToolsFields.COMPLETION_STATUS to "completed"))}}},Modifier.weight(1f),enabled=id!=null){Text("Stop")}
            }
            if(!snap?.laps.isNullOrEmpty()){Spacer(Modifier.height(14.dp));snap!!.laps.asReversed().forEachIndexed{i,v->Text("Lap ${snap!!.laps.size-i}   ${TimeFormatting.duration(v-(snap!!.laps.getOrNull(snap!!.laps.size-i-2)?:0),true)}   ${TimeFormatting.duration(v,true)}",Modifier.fillMaxWidth().padding(vertical=6.dp),fontFamily=FontFamily.Monospace)}}
        }
    }
}

object IntervalMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100IntervalMethod.ID; override val title="Interval timer"; override val description="Repeated labelled phases with live phase/cycle status."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val android = LocalContext.current
        val runtime = remember(android) { TimeToolsTimerRuntime(android) };var aLabel by rememberSaveable{mutableStateOf(context.action.settings.v("phase_a_label")?:"Work")};var aSec by rememberSaveable{mutableStateOf(((context.action.settings.v("phase_a_duration_ms")?.toLongOrNull()?:60000)/1000).toString())};var bLabel by rememberSaveable{mutableStateOf(context.action.settings.v("phase_b_label")?:"Rest")};var bSec by rememberSaveable{mutableStateOf(((context.action.settings.v("phase_b_duration_ms")?.toLongOrNull()?:60000)/1000).toString())};var cycles by rememberSaveable{mutableStateOf(context.action.settings.v("cycles")?:"1")};var id by rememberSaveable{mutableStateOf<String?>(null)};var snap by remember{mutableStateOf<TimeToolsTimerRuntime.Snapshot?>(null)};var result by remember{mutableStateOf<ExecutionResult?>(null)};val reminder=remember(context.action.settings){reminderConfig(context.action.settings,true)}
        fun start(){val s=runtime.startInterval(listOf(aLabel,bLabel),listOf((aSec.toLongOrNull()?:60)*1000,(bSec.toLongOrNull()?:60)*1000),cycles.toIntOrNull()?:1,reminder.label.ifBlank{"Interval timer"},reminder.message,reminder.profile,reminder.ongoing,reminder.lock,!reminder.privateMessage,reminder.confirm,reminder.followups,reminder.followupMinutes,reminder.snooze);id=s.timerId;if(context.submitsImmediately){result=execution(As100IntervalMethod,context,mapOf(TimeToolsFields.TIMER_ID to s.timerId,TimeToolsFields.COMPLETION_STATUS to "scheduled"));result?.let(onConfirmed)}}
        LaunchedEffect(id){while(id!=null&&result==null){snap=id?.let(runtime::snapshot);if((snap?.remainingMs==0L||snap?.completed==true)&&!context.submitsImmediately){val s=snap!!;result=execution(As100IntervalMethod,context,mapOf(TimeToolsFields.FORMATTED_DURATION to s.formatted,TimeToolsFields.DURATION_MS to s.elapsedMs,TimeToolsFields.COMPLETED_CYCLES to s.cycles,TimeToolsFields.CONFIGURED_CYCLES to s.cycles,TimeToolsFields.CURRENT_PHASE to s.currentPhase,TimeToolsFields.TIMER_ID to s.id,TimeToolsFields.COMPLETION_STATUS to "completed"))};delay(100)}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{OutputFormatter.fields(it,false)}.orEmpty(),onBack,{result=null;id=null},{result?.let(onConfirmed)},{id?.let(runtime::cancel);onCancel()}){
            if(id==null){OutlinedTextField(aLabel,{aLabel=it},label={Text("First phase")},modifier=Modifier.fillMaxWidth());OutlinedTextField(aSec,{aSec=it.filter(Char::isDigit)},label={Text("Seconds")},modifier=Modifier.fillMaxWidth());OutlinedTextField(bLabel,{bLabel=it},label={Text("Second phase")},modifier=Modifier.fillMaxWidth());OutlinedTextField(bSec,{bSec=it.filter(Char::isDigit)},label={Text("Seconds")},modifier=Modifier.fillMaxWidth());OutlinedTextField(cycles,{cycles=it.filter(Char::isDigit)},label={Text("Cycles")},modifier=Modifier.fillMaxWidth());Button(::start,Modifier.fillMaxWidth().padding(top=10.dp)){Text("Start intervals")}}
            else{TimerHero(snap?.formatted?:"…",0.5f,"${snap?.currentPhase ?: "Interval"} · cycle ${snap?.cycle ?: 1}/${snap?.cycles ?: cycles}");Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){Button({id?.let{if(snap?.paused==true)runtime.resume(it)else runtime.pause(it)}},Modifier.weight(1f)){Text(if(snap?.paused==true)"Resume" else "Pause")};OutlinedButton({id?.let{runtime.stop(it)};id=null},Modifier.weight(1f)){Text("Stop")}}}
        }
    }
}

object UntilMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100UntilMethod.ID;override val title="Date & time countdown";override val description="Count down to a date/time or anchor + calendar-day offset."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val android=LocalContext.current;val runtime=remember{TimeToolsTimerRuntime(android)};var targetMode by rememberSaveable{mutableStateOf(context.action.settings.v("target_mode")?:"absolute")};var targetMs by rememberSaveable{mutableStateOf(context.action.settings.v("target_timestamp")?.let{runCatching{Instant.parse(it).toEpochMilli()}.getOrNull()}?:System.currentTimeMillis()+86400000)};var dayOffset by rememberSaveable{mutableStateOf(context.action.settings.v("day_offset")?:"14")};var localTime by rememberSaveable{mutableStateOf(context.action.settings.v("local_time")?:"21:00")};var id by rememberSaveable{mutableStateOf<String?>(null)};var snap by remember{mutableStateOf<TimeToolsTimerRuntime.Snapshot?>(null)};var result by remember{mutableStateOf<ExecutionResult?>(null)};val reminder=remember(context.action.settings){reminderConfig(context.action.settings,false)}
        fun resolved():Instant=if(targetMode=="anchor_offset_local_time"){Instant.parse(UntilTimeCapability.daysAfterAnchorAtLocalTime(context.action.settings.v("anchor_timestamp")?:Instant.now().toString(),dayOffset.toIntOrNull()?:0,localTime,context.action.settings.v("zone_id")?:ZoneId.systemDefault().id))}else Instant.ofEpochMilli(targetMs)
        fun start(){val t=resolved();val s=runtime.startUntil(t,reminder.label.ifBlank{"Date & time countdown"},reminder.message,context.action.settings.v("zone_id")?:ZoneId.systemDefault().id,reminder.profile,reminder.ongoing,reminder.lock,!reminder.privateMessage,reminder.confirm,reminder.followups,reminder.followupMinutes,reminder.snooze);id=s.timerId;result=execution(As100UntilMethod,context,mapOf(TimeToolsFields.TARGET_TIMESTAMP to t.toString(),TimeToolsFields.FORMATTED_DURATION to TimeFormatting.longRange(t),TimeToolsFields.REMAINING_MS to Duration.between(Instant.now(),t).toMillis().coerceAtLeast(0),TimeToolsFields.TIMER_ID to s.timerId,TimeToolsFields.EXACT_ALERT_SCHEDULED to s.exactAlertScheduled,TimeToolsFields.COMPLETION_STATUS to "scheduled",TimeToolsFields.MESSAGE to reminder.message));if(context.submitsImmediately)result?.let(onConfirmed)}
        LaunchedEffect(id){while(id!=null){snap=id?.let(runtime::snapshot);delay(1000)}}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{OutputFormatter.fields(it,false)}.orEmpty(),onBack,{result=null;id=null},{result?.let(onConfirmed)},{id?.let(runtime::cancel);onCancel()}){
            if(id==null){SegmentedToggle(targetMode=="absolute","Date & time","Day offset"){targetMode=if(it)"absolute" else "anchor_offset_local_time"};Spacer(Modifier.height(12.dp));if(targetMode=="absolute")DateTimePickerRow(android,targetMs){targetMs=it}else{OutlinedTextField(dayOffset,{dayOffset=it.filter(Char::isDigit)},label={Text("Calendar days after anchor")},modifier=Modifier.fillMaxWidth());OutlinedTextField(localTime,{localTime=it},label={Text("Local time (HH:MM)")},modifier=Modifier.fillMaxWidth())};ReminderSummary(reminder);Button(::start,Modifier.fillMaxWidth().padding(top=10.dp)){Text("Start countdown")}}
            else TimerHero(snap?.formatted?:TimeFormatting.longRange(resolved()),0.1f,snap?.label?:"Date & time countdown")
        }
    }
}

object AlarmMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100AlarmMethod.ID;override val title="Alarm";override val description="One-off or repeating alarms with Done, Snooze and follow-up reminders."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){
        val android=LocalContext.current;val runtime=remember{TimeToolsTimerRuntime(android)};var time by rememberSaveable{mutableStateOf(context.action.settings.v("alarm_time")?:"09:00")};var date by rememberSaveable{mutableStateOf(context.action.settings.v("alarm_date")?:LocalDate.now().toString())};var repeat by rememberSaveable{mutableStateOf(context.action.settings.v("alarm_repeat")?:"ONCE")};var days by rememberSaveable{mutableStateOf(context.action.settings.v("alarm_weekdays")?:"1|2|3|4|5")};var result by remember{mutableStateOf<ExecutionResult?>(null)};val reminder=remember(context.action.settings){reminderConfig(context.action.settings,false)}
        fun set(){val r=AlarmRepeat.valueOf(repeat);val zone=context.action.settings.v("zone_id")?.takeIf(String::isNotBlank)?:ZoneId.systemDefault().id;val s=runtime.startAlarm(time,date.takeIf{r==AlarmRepeat.ONCE},r,AlarmSchedule.parseWeekdays(days),zone,reminder.label.ifBlank{"Alarm"},reminder.message,reminder.profile,reminder.confirm,reminder.followups,reminder.followupMinutes,reminder.snooze);result=execution(As100AlarmMethod,context,mapOf(TimeToolsFields.NEXT_TRIGGER_TIMESTAMP to s.targetTimestamp,TimeToolsFields.ALARM_REPEAT to repeat,TimeToolsFields.ALARM_WEEKDAYS to days,TimeToolsFields.MESSAGE to reminder.message,TimeToolsFields.TIMER_ID to s.timerId,TimeToolsFields.EXACT_ALERT_SCHEDULED to s.exactAlertScheduled,TimeToolsFields.RESULT to "Alarm set"));if(context.submitsImmediately)result?.let(onConfirmed)}
        CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{OutputFormatter.fields(it,false)}.orEmpty(),onBack,{result=null},{result?.let(onConfirmed)},onCancel){
            Text("Alarm",style=MaterialTheme.typography.labelLarge);OutlinedTextField(time,{time=it},label={Text("Time (HH:MM)")},modifier=Modifier.fillMaxWidth());if(repeat=="ONCE")OutlinedTextField(date,{date=it},label={Text("Date (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth());Text("Repeat",Modifier.padding(top=10.dp));FlowChoices(listOf("ONCE","DAILY","WEEKDAYS","WEEKENDS","WEEKLY","CUSTOM"),repeat){repeat=it};if(repeat in listOf("WEEKLY","CUSTOM"))OutlinedTextField(days,{days=it},label={Text("Days: 1=Mon … 7=Sun")},modifier=Modifier.fillMaxWidth());ReminderSummary(reminder);Button(::set,Modifier.fillMaxWidth().padding(top=10.dp)){Text("Set alarm")}
        }
    }
}

object ElapsedMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId=As100ElapsedMethod.ID;override val title="Elapsed time";override val description="Calculate the interval between two date/times."
    @Composable override fun Render(context:CapabilityScreenContext,onBack:()->Unit,onConfirmed:(ExecutionResult)->Unit,onCancel:()->Unit){val android=LocalContext.current;var start by rememberSaveable{mutableStateOf(System.currentTimeMillis()-3600000)};var end by rememberSaveable{mutableStateOf(System.currentTimeMillis())};var result by remember{mutableStateOf<ExecutionResult?>(null)};fun calc(){val core=ElapsedTimeCapability.execute(Instant.ofEpochMilli(start).toString(),Instant.ofEpochMilli(end).toString());result=execution(As100ElapsedMethod,context,core)};CapabilityScreenScaffold(title,capabilityId,context,context.stepNumber>1,result,result?.let{OutputFormatter.fields(it,false)}.orEmpty(),onBack,{result=null},{result?.let(onConfirmed)},onCancel){DateTimePickerRow(android,start){start=it};Text("to",Modifier.padding(8.dp));DateTimePickerRow(android,end){end=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({start=System.currentTimeMillis()},Modifier.weight(1f)){Text("Start = now")};OutlinedButton({end=System.currentTimeMillis()},Modifier.weight(1f)){Text("End = now")}};Text(TimeFormatting.duration((end-start).coerceAtLeast(0)),fontSize=42.sp,fontFamily=FontFamily.Monospace,modifier=Modifier.padding(vertical=18.dp));Button(::calc,Modifier.fillMaxWidth()){Text("Use result")}}}
}

object DurationCalculatorMethodMeshCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100DurationCalculateMethod.ID
    override val title = "Duration calculator"
    override val description = "Add or subtract two durations."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var ah by rememberSaveable { mutableStateOf("0") }
        var am by rememberSaveable { mutableStateOf("0") }
        var asec by rememberSaveable { mutableStateOf("0") }
        var bh by rememberSaveable { mutableStateOf("0") }
        var bm by rememberSaveable { mutableStateOf("0") }
        var bsec by rememberSaveable { mutableStateOf("0") }
        var op by rememberSaveable { mutableStateOf("add") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun ms(h: String, m: String, sec: String): Long =
            (h.toLongOrNull() ?: 0L) * 3_600_000L +
                (m.toLongOrNull() ?: 0L) * 60_000L +
                (sec.toLongOrNull() ?: 0L) * 1_000L

        val first = ms(ah, am, asec)
        val second = ms(bh, bm, bsec)
        val preview = if (op == "add") first + second else (first - second).coerceAtLeast(0L)

        fun calculate() {
            val core = DurationCalculatorCapability.execute(
                first,
                second,
                if (op == "add") DurationCalculatorCapability.Operation.ADD else DurationCalculatorCapability.Operation.SUBTRACT
            )
            result = execution(As100DurationCalculateMethod, context, core)
        }

        CapabilityScreenScaffold(
            title, capabilityId, context, context.stepNumber > 1, result,
            result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack, { result = null }, { result?.let(onConfirmed) }, onCancel
        ) {
            Text("First duration")
            HmsEditor(ah, am, asec, { ah = it }, { am = it }, { asec = it })
            SegmentedToggle(op == "add", "Add", "Subtract") { op = if (it) "add" else "subtract" }
            Text("Second duration", Modifier.padding(top = 8.dp))
            HmsEditor(bh, bm, bsec, { bh = it }, { bm = it }, { bsec = it })
            Text(
                TimeFormatting.duration(preview),
                fontSize = 42.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 18.dp)
            )
            Button(::calculate, Modifier.fillMaxWidth()) { Text("Use result") }
        }
    }
}

private data class ReminderUi(val label:String,val message:String,val ongoing:Boolean,val lock:Boolean,val privateMessage:Boolean,val confirm:Boolean,val followups:Int,val followupMinutes:Int,val snooze:Int,val profile:AlertProfile)
private fun reminderConfig(m:Map<String,String>,defaultOngoing:Boolean)=ReminderUi(m.v("label").orEmpty(),m.v("message").orEmpty(),m.b("notify_ongoing",defaultOngoing),m.b("lock_screen",defaultOngoing),!m.b("show_message_on_lock_screen",false),m.b("require_confirmation",false),m.v("follow_up_count")?.toIntOrNull()?:0,m.v("follow_up_interval_minutes")?.toIntOrNull()?:10,m.v("snooze_minutes")?.toIntOrNull()?:10,AlertProfile(m.b("alert_sound",true),m.b("alert_vibration",true),m.b("alert_lights",true),m.b("lock_screen",true),m.b("show_message_on_lock_screen",false),m.b("alert_high_priority",true)))
private fun Map<String,String>.v(k:String)= (this[k]?:this["input_$k"])?.takeIf{it.isNotBlank()}
private fun Map<String,String>.b(k:String,d:Boolean)=v(k)?.toBooleanStrictOrNull()?:d
private fun execution(method:BaseTimeToolsMethod,context:CapabilityScreenContext,core:Map<String,Any?>):ExecutionResult{val req=method.request(context.action.canonicalId,context.request.invocationContext.asMap(context.action.canonicalId)+context.action.settings,emptyList(),emptyList());return method.result(req,method.successfulValues(core),context.request.invocationContext)}

@Composable
private fun TimerHero(value: String, progress: Float, label: String) {
    // MaterialTheme is composable state. Read it here, before entering Canvas'
    // non-composable DrawScope lambda.
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(270.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                drawArc(
                    color = trackColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(13.dp.toPx(), cap = StrokeCap.Round)
                )
                drawArc(
                    color = progressColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    style = Stroke(13.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value,
                    fontSize = 44.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Light
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
@Composable private fun HmsEditor(h:String,m:String,s:String,onH:(String)->Unit,onM:(String)->Unit,onS:(String)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(h,{onH(it.filter(Char::isDigit).take(4))},label={Text("Hours")},modifier=Modifier.weight(1f));OutlinedTextField(m,{onM(it.filter(Char::isDigit).take(2))},label={Text("Min")},modifier=Modifier.weight(1f));OutlinedTextField(s,{onS(it.filter(Char::isDigit).take(2))},label={Text("Sec")},modifier=Modifier.weight(1f))}}
@Composable private fun SegmentedToggle(left:Boolean,leftLabel:String,rightLabel:String,on:(Boolean)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){if(left)Button({on(true)},Modifier.weight(1f)){Text(leftLabel)}else OutlinedButton({on(true)},Modifier.weight(1f)){Text(leftLabel)};if(!left)Button({on(false)},Modifier.weight(1f)){Text(rightLabel)}else OutlinedButton({on(false)},Modifier.weight(1f)){Text(rightLabel)}}}
@Composable private fun DateTimePickerRow(context:Context,value:Long,on:(Long)->Unit){val z=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault());Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedButton({DatePickerDialog(context,{_,y,m,d->val old=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault());on(ZonedDateTime.of(y,m+1,d,old.hour,old.minute,0,0,old.zone).toInstant().toEpochMilli())},z.year,z.monthValue-1,z.dayOfMonth).show()},Modifier.weight(1f)){Text(z.toLocalDate().toString())};OutlinedButton({TimePickerDialog(context,{_,h,m->val old=Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault());on(old.withHour(h).withMinute(m).withSecond(0).withNano(0).toInstant().toEpochMilli())},z.hour,z.minute,true).show()},Modifier.weight(1f)){Text("%02d:%02d".format(z.hour,z.minute))}}}
@Composable private fun ReminderSummary(r:ReminderUi){if(r.message.isNotBlank()||r.confirm||r.followups>0){Surface(Modifier.fillMaxWidth().padding(vertical=8.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)){Column(Modifier.padding(14.dp)){if(r.message.isNotBlank())Text(r.message,fontWeight=FontWeight.Medium);Text(buildString{append(if(r.ongoing)"Live notification on" else "No persistent notification");if(r.confirm)append(" · Done required");if(r.followups>0)append(" · ${r.followups} follow-ups")},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}}
@Composable private fun FlowChoices(values:List<String>,selected:String,on:(String)->Unit){Column{values.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){row.forEach{v->if(v==selected)Button({on(v)},Modifier.weight(1f)){Text(v.lowercase().replaceFirstChar{it.uppercase()},fontSize=11.sp)}else OutlinedButton({on(v)},Modifier.weight(1f)){Text(v.lowercase().replaceFirstChar{it.uppercase()},fontSize=11.sp)}};repeat(3-row.size){Spacer(Modifier.weight(1f))}}}}}
