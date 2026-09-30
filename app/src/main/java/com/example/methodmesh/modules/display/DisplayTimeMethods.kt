package com.example.methodmesh.modules.display

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

object DisplayTimerFields {
    const val STATUS = "display_timer_status"
    const val RESULT = "display_timer_result"
    const val DURATION_SECONDS = "display_timer_duration_seconds"
    const val REMAINING_SECONDS = "display_timer_remaining_seconds"
    const val BEEP = "display_timer_beep"
    const val THEME = "display_timer_theme"
    const val HIGH_BRIGHTNESS = "display_timer_high_brightness"
    const val STARTED_TIME_ISO = "display_timer_started_time_iso"
    const val STOPPED_TIME_ISO = "display_timer_stopped_time_iso"
    const val ELAPSED_MS = "display_timer_elapsed_ms"
    const val COMPLETION_REASON = "display_timer_completion_reason"
    const val AUDIT_JSON = "display_timer_audit_json"
    const val ERROR = "display_timer_error"

    val outputs = listOf(
        STATUS, RESULT, DURATION_SECONDS, REMAINING_SECONDS, BEEP, THEME,
        HIGH_BRIGHTNESS, STARTED_TIME_ISO, STOPPED_TIME_ISO, ELAPSED_MS,
        COMPLETION_REASON, AUDIT_JSON, ERROR
    )
}


object DisplayDebateTimerFields {
    const val STATUS = "display_debate_timer_status"
    const val RESULT = "display_debate_timer_result"
    const val DURATION_SECONDS = "display_debate_timer_duration_seconds"
    const val WARNING_SECONDS = "display_debate_timer_warning_seconds"
    const val REMAINING_SECONDS = "display_debate_timer_remaining_seconds"
    const val PHASE = "display_debate_timer_phase"
    const val WARNING_BEEP = "display_debate_timer_warning_beep"
    const val BEEP = "display_debate_timer_beep"
    const val HIGH_BRIGHTNESS = "display_debate_timer_high_brightness"
    const val STARTED_TIME_ISO = "display_debate_timer_started_time_iso"
    const val STOPPED_TIME_ISO = "display_debate_timer_stopped_time_iso"
    const val ELAPSED_MS = "display_debate_timer_elapsed_ms"
    const val COMPLETION_REASON = "display_debate_timer_completion_reason"
    const val AUDIT_JSON = "display_debate_timer_audit_json"
    const val ERROR = "display_debate_timer_error"

    val outputs = listOf(
        STATUS, RESULT, DURATION_SECONDS, WARNING_SECONDS, REMAINING_SECONDS, PHASE,
        WARNING_BEEP, BEEP, HIGH_BRIGHTNESS, STARTED_TIME_ISO, STOPPED_TIME_ISO,
        ELAPSED_MS, COMPLETION_REASON, AUDIT_JSON, ERROR
    )
}

object DisplayClockFields {
    const val STATUS = "display_clock_status"
    const val RESULT = "display_clock_result"
    const val TIME_VALUE = "display_clock_time"
    const val DATE_VALUE = "display_clock_date"
    const val FORMAT = "display_clock_format"
    const val SHOW_DATE = "display_clock_show_date"
    const val ZONE_ID = "display_clock_zone_id"
    const val THEME = "display_clock_theme"
    const val HIGH_BRIGHTNESS = "display_clock_high_brightness"
    const val STARTED_TIME_ISO = "display_clock_started_time_iso"
    const val STOPPED_TIME_ISO = "display_clock_stopped_time_iso"
    const val ELAPSED_MS = "display_clock_elapsed_ms"
    const val COMPLETION_REASON = "display_clock_completion_reason"
    const val AUDIT_JSON = "display_clock_audit_json"
    const val ERROR = "display_clock_error"

    val outputs = listOf(
        STATUS, RESULT, TIME_VALUE, DATE_VALUE, FORMAT, SHOW_DATE, ZONE_ID, THEME,
        HIGH_BRIGHTNESS, STARTED_TIME_ISO, STOPPED_TIME_ISO, ELAPSED_MS,
        COMPLETION_REASON, AUDIT_JSON, ERROR
    )
}

object DisplayTimerMethod : As100Method {
    const val ID = "display.timer"
    private const val METHOD_VERSION = "0.1.1"
    const val DEFAULT_DURATION_SECONDS = 300

    override val id = ID
    val title = "Countdown timer"
    val help = "Show a large countdown timer and sound a short local alarm-beep pattern when it reaches zero."

    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", title)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = title,
        version = METHOD_VERSION,
        description = help,
        outputs = DisplayTimerFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development",
            "interaction" to "interactive"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val duration = request.context.setting("duration_seconds", DEFAULT_DURATION_SECONDS.toString())
            .toIntOrNull()?.coerceIn(1, 359999) ?: DEFAULT_DURATION_SECONDS
        val values = values(
            settings = request.context,
            startedIso = "",
            stoppedIso = Instant.now().toString(),
            elapsedMs = 0L,
            remainingSeconds = duration,
            completionReason = "headless_specification"
        )
        return buildDisplayTemporalResult(
            methodId = ID,
            methodVersion = METHOD_VERSION,
            methodRef = ref,
            entityType = "DisplayTimerPresentation",
            request = request,
            values = values,
            statusField = DisplayTimerFields.STATUS,
            errorField = DisplayTimerFields.ERROR,
            invocation = InvocationContext.from(request.context)
        )
    }

    fun values(
        settings: Map<String, String>,
        startedIso: String,
        stoppedIso: String,
        elapsedMs: Long,
        remainingSeconds: Int,
        completionReason: String
    ): Map<String, String> {
        val duration = settings.setting("duration_seconds", DEFAULT_DURATION_SECONDS.toString())
            .toIntOrNull()?.takeIf { it in 1..359999 }
            ?: return failure(settings, "Duration must be between 1 and 359999 seconds.")
        val beep = settings.setting("beep", "true").toBooleanStrictOrNull() ?: true
        val theme = settings.setting("theme", "white_on_black").normalizeTheme()
        val highBrightness = settings.setting("high_brightness", "true").toBooleanStrictOrNull() ?: true
        val remaining = remainingSeconds.coerceIn(0, duration)
        val resultText = formatCountdownSeconds(remaining)
        val audit = JSONObject()
            .put("method_id", ID)
            .put("method_version", METHOD_VERSION)
            .put("duration_seconds", duration)
            .put("remaining_seconds", remaining)
            .put("beep", beep)
            .put("theme", theme)
            .put("high_brightness", highBrightness)
            .put("started_time_iso", startedIso)
            .put("stopped_time_iso", stoppedIso)
            .put("elapsed_ms", elapsedMs)
            .put("completion_reason", completionReason)

        return linkedMapOf(
            DisplayTimerFields.STATUS to "succeeded",
            DisplayTimerFields.RESULT to resultText,
            DisplayTimerFields.DURATION_SECONDS to duration.toString(),
            DisplayTimerFields.REMAINING_SECONDS to remaining.toString(),
            DisplayTimerFields.BEEP to beep.toString(),
            DisplayTimerFields.THEME to theme,
            DisplayTimerFields.HIGH_BRIGHTNESS to highBrightness.toString(),
            DisplayTimerFields.STARTED_TIME_ISO to startedIso,
            DisplayTimerFields.STOPPED_TIME_ISO to stoppedIso,
            DisplayTimerFields.ELAPSED_MS to elapsedMs.toString(),
            DisplayTimerFields.COMPLETION_REASON to completionReason,
            DisplayTimerFields.AUDIT_JSON to audit.toString(),
            DisplayTimerFields.ERROR to ""
        )
    }

    private fun failure(settings: Map<String, String>, error: String) =
        DisplayTimerFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[DisplayTimerFields.STATUS] = "failed"
            this[DisplayTimerFields.DURATION_SECONDS] = settings.setting("duration_seconds", "")
            this[DisplayTimerFields.ERROR] = error
        }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ) = buildDisplayTemporalResult(
        methodId = ID,
        methodVersion = METHOD_VERSION,
        methodRef = ref,
        entityType = "DisplayTimerPresentation",
        request = request,
        values = values,
        statusField = DisplayTimerFields.STATUS,
        errorField = DisplayTimerFields.ERROR,
        invocation = invocation
    )
}


object DisplayDebateTimerMethod : As100Method {
    const val ID = "display.debate_timer"
    private const val METHOD_VERSION = "0.1.0"
    const val DEFAULT_DURATION_SECONDS = 300
    const val DEFAULT_WARNING_SECONDS = 60

    override val id = ID
    val title = "Debate timer"
    val help = "Show a large debate countdown: green during the main period, yellow at the warning threshold and red at zero."

    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", title)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = title,
        version = METHOD_VERSION,
        description = help,
        outputs = DisplayDebateTimerFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development",
            "interaction" to "interactive"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val duration = request.context.setting("duration_seconds", DEFAULT_DURATION_SECONDS.toString())
            .toIntOrNull()?.coerceIn(1, 359999) ?: DEFAULT_DURATION_SECONDS
        val warning = request.context.setting("warning_seconds", DEFAULT_WARNING_SECONDS.toString())
            .toIntOrNull()?.coerceIn(1, duration) ?: DEFAULT_WARNING_SECONDS.coerceAtMost(duration)
        val values = values(
            settings = request.context + mapOf("duration_seconds" to duration.toString(), "warning_seconds" to warning.toString()),
            startedIso = "",
            stoppedIso = Instant.now().toString(),
            elapsedMs = 0L,
            remainingSeconds = duration,
            completionReason = "headless_specification"
        )
        return buildDisplayTemporalResult(
            methodId = ID,
            methodVersion = METHOD_VERSION,
            methodRef = ref,
            entityType = "DisplayDebateTimerPresentation",
            request = request,
            values = values,
            statusField = DisplayDebateTimerFields.STATUS,
            errorField = DisplayDebateTimerFields.ERROR,
            invocation = InvocationContext.from(request.context)
        )
    }

    fun values(
        settings: Map<String, String>,
        startedIso: String,
        stoppedIso: String,
        elapsedMs: Long,
        remainingSeconds: Int,
        completionReason: String
    ): Map<String, String> {
        val duration = settings.setting("duration_seconds", DEFAULT_DURATION_SECONDS.toString())
            .toIntOrNull()?.takeIf { it in 1..359999 }
            ?: return failure(settings, "Duration must be between 1 and 359999 seconds.")
        val warning = settings.setting("warning_seconds", DEFAULT_WARNING_SECONDS.toString())
            .toIntOrNull()?.takeIf { it in 1..duration }
            ?: return failure(settings, "Warning threshold must be between 1 second and the total duration.")
        val warningBeep = settings.setting("warning_beep", "true").toBooleanStrictOrNull() ?: true
        val beep = settings.setting("beep", "true").toBooleanStrictOrNull() ?: true
        val highBrightness = settings.setting("high_brightness", "true").toBooleanStrictOrNull() ?: true
        val remaining = remainingSeconds.coerceIn(0, duration)
        val phase = debateTimerPhase(remaining, warning)
        val resultText = "${formatCountdownSeconds(remaining)} · ${phase.uppercase(Locale.US)}"
        val audit = JSONObject()
            .put("method_id", ID)
            .put("method_version", METHOD_VERSION)
            .put("duration_seconds", duration)
            .put("warning_seconds", warning)
            .put("remaining_seconds", remaining)
            .put("phase", phase)
            .put("warning_beep", warningBeep)
            .put("beep", beep)
            .put("high_brightness", highBrightness)
            .put("started_time_iso", startedIso)
            .put("stopped_time_iso", stoppedIso)
            .put("elapsed_ms", elapsedMs)
            .put("completion_reason", completionReason)

        return linkedMapOf(
            DisplayDebateTimerFields.STATUS to "succeeded",
            DisplayDebateTimerFields.RESULT to resultText,
            DisplayDebateTimerFields.DURATION_SECONDS to duration.toString(),
            DisplayDebateTimerFields.WARNING_SECONDS to warning.toString(),
            DisplayDebateTimerFields.REMAINING_SECONDS to remaining.toString(),
            DisplayDebateTimerFields.PHASE to phase,
            DisplayDebateTimerFields.WARNING_BEEP to warningBeep.toString(),
            DisplayDebateTimerFields.BEEP to beep.toString(),
            DisplayDebateTimerFields.HIGH_BRIGHTNESS to highBrightness.toString(),
            DisplayDebateTimerFields.STARTED_TIME_ISO to startedIso,
            DisplayDebateTimerFields.STOPPED_TIME_ISO to stoppedIso,
            DisplayDebateTimerFields.ELAPSED_MS to elapsedMs.toString(),
            DisplayDebateTimerFields.COMPLETION_REASON to completionReason,
            DisplayDebateTimerFields.AUDIT_JSON to audit.toString(),
            DisplayDebateTimerFields.ERROR to ""
        )
    }

    private fun failure(settings: Map<String, String>, error: String) =
        DisplayDebateTimerFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[DisplayDebateTimerFields.STATUS] = "failed"
            this[DisplayDebateTimerFields.DURATION_SECONDS] = settings.setting("duration_seconds", "")
            this[DisplayDebateTimerFields.WARNING_SECONDS] = settings.setting("warning_seconds", "")
            this[DisplayDebateTimerFields.ERROR] = error
        }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ) = buildDisplayTemporalResult(
        methodId = ID,
        methodVersion = METHOD_VERSION,
        methodRef = ref,
        entityType = "DisplayDebateTimerPresentation",
        request = request,
        values = values,
        statusField = DisplayDebateTimerFields.STATUS,
        errorField = DisplayDebateTimerFields.ERROR,
        invocation = invocation
    )
}

object DisplayClockMethod : As100Method {
    const val ID = "display.clock"
    private const val METHOD_VERSION = "0.1.0"

    override val id = ID
    val title = "Clock"
    val help = "Show a large local clock as HH:MM or HH:MM:SS, optionally with the date above it."

    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", title)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = title,
        version = METHOD_VERSION,
        description = help,
        outputs = DisplayClockFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Development",
            "status" to "Development",
            "interaction" to "interactive"
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val values = values(
            settings = request.context,
            startedIso = "",
            stoppedIso = Instant.now().toString(),
            elapsedMs = 0L,
            completionReason = "headless_snapshot",
            now = ZonedDateTime.now()
        )
        return buildDisplayTemporalResult(
            methodId = ID,
            methodVersion = METHOD_VERSION,
            methodRef = ref,
            entityType = "DisplayClockPresentation",
            request = request,
            values = values,
            statusField = DisplayClockFields.STATUS,
            errorField = DisplayClockFields.ERROR,
            invocation = InvocationContext.from(request.context)
        )
    }

    fun values(
        settings: Map<String, String>,
        startedIso: String,
        stoppedIso: String,
        elapsedMs: Long,
        completionReason: String,
        now: ZonedDateTime = ZonedDateTime.now()
    ): Map<String, String> {
        val format = settings.setting("clock_format", "HH:MM").uppercase()
            .takeIf { it == "HH:MM" || it == "HH:MM:SS" } ?: "HH:MM"
        val showDate = settings.setting("show_date", "false").toBooleanStrictOrNull() ?: false
        val theme = settings.setting("theme", "white_on_black").normalizeTheme()
        val highBrightness = settings.setting("high_brightness", "true").toBooleanStrictOrNull() ?: true
        val locale = Locale.getDefault()
        val timeFormatter = DateTimeFormatter.ofPattern(if (format == "HH:MM:SS") "HH:mm:ss" else "HH:mm", locale)
        val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
        val timeText = now.format(timeFormatter)
        val dateText = if (showDate) now.format(dateFormatter) else ""
        val audit = JSONObject()
            .put("method_id", ID)
            .put("method_version", METHOD_VERSION)
            .put("clock_format", format)
            .put("show_date", showDate)
            .put("zone_id", now.zone.id)
            .put("theme", theme)
            .put("high_brightness", highBrightness)
            .put("started_time_iso", startedIso)
            .put("stopped_time_iso", stoppedIso)
            .put("elapsed_ms", elapsedMs)
            .put("completion_reason", completionReason)

        return linkedMapOf(
            DisplayClockFields.STATUS to "succeeded",
            DisplayClockFields.RESULT to listOf(dateText, timeText).filter { it.isNotBlank() }.joinToString(" "),
            DisplayClockFields.TIME_VALUE to timeText,
            DisplayClockFields.DATE_VALUE to dateText,
            DisplayClockFields.FORMAT to format,
            DisplayClockFields.SHOW_DATE to showDate.toString(),
            DisplayClockFields.ZONE_ID to now.zone.id,
            DisplayClockFields.THEME to theme,
            DisplayClockFields.HIGH_BRIGHTNESS to highBrightness.toString(),
            DisplayClockFields.STARTED_TIME_ISO to startedIso,
            DisplayClockFields.STOPPED_TIME_ISO to stoppedIso,
            DisplayClockFields.ELAPSED_MS to elapsedMs.toString(),
            DisplayClockFields.COMPLETION_REASON to completionReason,
            DisplayClockFields.AUDIT_JSON to audit.toString(),
            DisplayClockFields.ERROR to ""
        )
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ) = buildDisplayTemporalResult(
        methodId = ID,
        methodVersion = METHOD_VERSION,
        methodRef = ref,
        entityType = "DisplayClockPresentation",
        request = request,
        values = values,
        statusField = DisplayClockFields.STATUS,
        errorField = DisplayClockFields.ERROR,
        invocation = invocation
    )
}

private fun buildDisplayTemporalResult(
    methodId: String,
    methodVersion: String,
    methodRef: ArchitectureRef,
    entityType: String,
    request: ExecutionRequest,
    values: Map<String, String>,
    statusField: String,
    errorField: String,
    invocation: InvocationContext?
): ExecutionResult {
    val ok = values[statusField] == "succeeded"
    val provenance = ProvenanceContext("methodmesh.display", methodId, methodVersion)
    val observation = Observation(
        phenomenon = methodId,
        subject = null,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val entity = Entity(
        ArchitectureId("$entityType:${System.currentTimeMillis()}"),
        entityType,
        temporalContext = request.temporalContext
    )
    val transformation = Transformation(
        action = methodId,
        method = methodRef,
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    return As100ExecutionEngine.complete(
        request,
        if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        entities = listOf(entity),
        observations = listOf(observation),
        transformations = listOf(transformation),
        diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
    ).withInvocationContext(invocation)
}

internal fun formatCountdownSeconds(totalSeconds: Int): String {
    val total = totalSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}


internal fun debateTimerPhase(remainingSeconds: Int, warningSeconds: Int): String = when {
    remainingSeconds <= 0 -> "red"
    remainingSeconds <= warningSeconds -> "yellow"
    else -> "green"
}

private fun Map<String, String>.setting(key: String, default: String): String =
    this[key] ?: this["input_$key"] ?: default

private fun String.normalizeTheme(): String = lowercase().takeIf {
    it == "white_on_black" || it == "black_on_white" || it == "black_on_yellow"
} ?: "white_on_black"
