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
import org.json.JSONObject
import java.time.Instant

object DisplayFields {
    const val STATUS = "display_status"
    const val RESULT = "display_result"
    const val CONTENT_KIND = "display_content_kind"
    const val TEXT = "display_text"
    const val MEDIA_URI = "display_media_uri"
    const val MEDIA_MIME = "display_media_mime"
    const val MEDIA_SHA256 = "display_media_sha256"
    const val TARGET = "display_target"
    const val MOTION = "display_motion"
    const val THEME = "display_theme"
    const val ALIGNMENT = "display_alignment"
    const val ROTATION = "display_content_rotation"
    const val TEXT_SCALE = "display_text_scale"
    const val SPEED = "display_speed"
    const val DIRECTION = "display_direction"
    const val HIGH_BRIGHTNESS = "display_high_brightness"
    const val STARTED_TIME_ISO = "display_started_time_iso"
    const val STOPPED_TIME_ISO = "display_stopped_time_iso"
    const val ELAPSED_MS = "display_elapsed_ms"
    const val COMPLETION_REASON = "display_completion_reason"
    const val AUDIT_JSON = "display_audit_json"
    const val ERROR = "display_error"

    val outputs = listOf(
        STATUS,
        RESULT,
        CONTENT_KIND,
        TEXT,
        MEDIA_URI,
        MEDIA_MIME,
        MEDIA_SHA256,
        TARGET,
        MOTION,
        THEME,
        ALIGNMENT,
        ROTATION,
        TEXT_SCALE,
        SPEED,
        DIRECTION,
        HIGH_BRIGHTNESS,
        STARTED_TIME_ISO,
        STOPPED_TIME_ISO,
        ELAPSED_MS,
        COMPLETION_REASON,
        AUDIT_JSON,
        ERROR
    )
}

object DisplayShowMethod : As100Method {
    const val ID = "display.show"
    const val DEFAULT_TEXT = "HELLO 👋"
    private const val METHOD_VERSION = "0.2.3"

    override val id = ID
    val title = "Display"
    val help = "Show text, emoji, an image, GIF or animated WebP prominently on the screen."

    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", title)
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Workflow,
        name = title,
        version = METHOD_VERSION,
        description = help,
        outputs = DisplayFields.outputs,
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

    /** Headless execution records the requested display specification; local-screen presentation itself is interactive. */
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
            completionReason = "headless_specification"
        )
        return result(request, values, InvocationContext.from(request.context))
    }

    fun values(
        settings: Map<String, String>,
        startedIso: String,
        stoppedIso: String,
        elapsedMs: Long,
        completionReason: String
    ): Map<String, String> {
        val contentKind = settings.value("content_kind", "text").normalize(setOf("text", "media"), "text")
        val text = settings.value("text", DEFAULT_TEXT).trim()
        val mediaUri = settings.value("media_uri", "").trim()
        val mediaMime = settings.value("media_mime", "").trim()
        val mediaSha256 = settings.value("media_sha256", "").trim()
        if (contentKind == "text" && text.isBlank()) return failure(settings, "Message must not be blank.")
        if (contentKind == "media" && mediaUri.isBlank()) return failure(settings, "Choose an image, GIF or animated WebP.")

        val motion = settings.value("motion", "still").normalize(setOf("still", "scroll", "flash", "pulse"), "still")
        val theme = settings.value("theme", "white_on_black").normalize(
            setOf("white_on_black", "black_on_white", "black_on_yellow", "neon", "rainbow", "pride", "fabulous"),
            "white_on_black"
        )
        val alignment = settings.value("alignment", "center").normalize(setOf("start", "center", "end"), "center")
        val rotation = settings.value("content_rotation", "0").toIntOrNull()?.let { ((it % 360) + 360) % 360 } ?: 0
        val textScale = settings.value("text_scale", "1.0").toFloatOrNull()?.coerceIn(0.2f, 1f) ?: 1f
        val rawSpeed = settings.value("speed", "1.0").toFloatOrNull()?.coerceIn(0.25f, 4f) ?: 1f
        val speed = if (motion == "flash") rawSpeed.coerceAtMost(2f) else rawSpeed
        val direction = settings.value("direction", "left").normalize(setOf("left", "right"), "left")
        val highBrightness = settings.value("high_brightness", "true").toBooleanStrictOrNull() ?: true
        val resultText = if (contentKind == "text") text else mediaUri.substringAfterLast('/').ifBlank { "Media displayed" }

        val audit = JSONObject()
            .put("method_id", ID)
            .put("method_version", METHOD_VERSION)
            .put("target", "local_screen")
            .put("content_kind", contentKind)
            .put("text", text)
            .put("media_uri", mediaUri)
            .put("media_mime", mediaMime)
            .put("media_sha256", mediaSha256)
            .put("motion", motion)
            .put("theme", theme)
            .put("alignment", alignment)
            .put("content_rotation", rotation)
            .put("text_scale", textScale)
            .put("speed", speed)
            .put("direction", direction)
            .put("high_brightness", highBrightness)
            .put("started_time_iso", startedIso)
            .put("stopped_time_iso", stoppedIso)
            .put("elapsed_ms", elapsedMs)
            .put("completion_reason", completionReason)

        return linkedMapOf(
            DisplayFields.STATUS to "succeeded",
            DisplayFields.RESULT to resultText,
            DisplayFields.CONTENT_KIND to contentKind,
            DisplayFields.TEXT to if (contentKind == "text") text else "",
            DisplayFields.MEDIA_URI to if (contentKind == "media") mediaUri else "",
            DisplayFields.MEDIA_MIME to if (contentKind == "media") mediaMime else "",
            DisplayFields.MEDIA_SHA256 to if (contentKind == "media") mediaSha256 else "",
            DisplayFields.TARGET to "local_screen",
            DisplayFields.MOTION to motion,
            DisplayFields.THEME to theme,
            DisplayFields.ALIGNMENT to alignment,
            DisplayFields.ROTATION to rotation.toString(),
            DisplayFields.TEXT_SCALE to textScale.toString(),
            DisplayFields.SPEED to speed.toString(),
            DisplayFields.DIRECTION to direction,
            DisplayFields.HIGH_BRIGHTNESS to highBrightness.toString(),
            DisplayFields.STARTED_TIME_ISO to startedIso,
            DisplayFields.STOPPED_TIME_ISO to stoppedIso,
            DisplayFields.ELAPSED_MS to elapsedMs.toString(),
            DisplayFields.COMPLETION_REASON to completionReason,
            DisplayFields.AUDIT_JSON to audit.toString(),
            DisplayFields.ERROR to ""
        )
    }

    private fun failure(settings: Map<String, String>, error: String) =
        DisplayFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[DisplayFields.STATUS] = "failed"
            this[DisplayFields.CONTENT_KIND] = settings.value("content_kind", "text")
            this[DisplayFields.TEXT] = settings.value("text", "")
            this[DisplayFields.MEDIA_URI] = settings.value("media_uri", "")
            this[DisplayFields.TARGET] = "local_screen"
            this[DisplayFields.ERROR] = error
        }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[DisplayFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.display", ID, METHOD_VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val entity = Entity(
            ArchitectureId("DisplayPresentation:${System.currentTimeMillis()}"),
            "DisplayPresentation",
            temporalContext = request.temporalContext
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
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
            diagnostics = if (ok) emptyMap() else mapOf(DisplayFields.ERROR to values[DisplayFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun Map<String, String>.value(key: String, default: String) =
        this[key] ?: this["input_$key"] ?: default

    private fun String.normalize(allowed: Set<String>, default: String) =
        lowercase().takeIf { it in allowed } ?: default
}
