package com.example.methodmesh.modules.signals

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import org.json.JSONObject

/** Shared visual language for the Signals instrument family. */
internal val SignalBlack = Color(0xFF07090B)
internal val SignalPanel = Color(0xFF101419)
internal val SignalPanelLift = Color(0xFF1A2027)
internal val SignalGrid = Color(0xFF303945)
internal val SignalText = Color(0xFFF4F7F9)
internal val SignalMuted = Color(0xFF98A5B2)
internal val SignalAmber = Color(0xFFF3B84B)
internal val SignalCyan = Color(0xFF66D9EF)
internal val SignalViolet = Color(0xFFB99CFF)
internal val SignalMagenta = Color(0xFFE58AD8)
internal val SignalGreen = Color(0xFF73DEA4)
internal val SignalRed = Color(0xFFFF7777)


internal fun Context.signalActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

internal fun Map<String, String>.signalSetting(id: String, default: String): String =
    this[id] ?: this["input_$id"] ?: default

/**
 * Hardware signal sessions are intentionally orientation-stable. Camera, microphone,
 * torch, speaker and accelerometer sessions cannot survive Activity recreation without
 * a discontinuity, so lock the current orientation only while a physical session is
 * active and restore the host policy immediately afterwards.
 */
@Composable
internal fun SignalActiveSessionOrientationGuard(active: Boolean) {
    val context = LocalContext.current
    val activity = remember(context) { context.signalActivity() }
    DisposableEffect(activity, active) {
        if (activity == null || !active) return@DisposableEffect onDispose { }
        val previous = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        onDispose {
            if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
                activity.requestedOrientation = previous
            }
        }
    }
}

@Composable
internal fun SignalDbfsHelp() {
    var expanded by remember { mutableStateOf(false) }
    Text(
        text = if (expanded)
            "dBFS is digital level: 0 dBFS is full scale. More-negative numbers are quieter; −78 dBFS is much more sensitive than −42 dBFS."
        else "ⓘ What does dBFS mean?",
        modifier = Modifier.clickable { expanded = !expanded },
        color = SignalMuted,
        style = MaterialTheme.typography.labelSmall
    )
}

internal fun signalResult(
    method: SignalsMethod,
    context: CapabilityScreenContext,
    values: Map<String, String>
): ExecutionResult {
    val request = method.request(
        action = context.action.canonicalId,
        context = context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings,
        signals = emptyList(),
        inputs = emptyList()
    )
    return method.result(request, values, context.request.invocationContext)
        .withInvocationContext(context.request.invocationContext)
}

internal fun fieldsJson(values: Map<String, String>): String = JSONObject(values).toString()

internal fun fieldsFromJson(raw: String?): Map<String, String> {
    if (raw.isNullOrBlank()) return emptyMap()
    return runCatching {
        val objectJson = JSONObject(raw)
        buildMap {
            objectJson.keys().forEach { key -> put(key, objectJson.optString(key, "")) }
        }
    }.getOrDefault(emptyMap())
}

internal fun copySignalValue(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
}

internal fun shareSignalText(context: Context, title: String, text: String): String? = runCatching {
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            title
        )
    )
    null
}.getOrElse { it.message ?: "No sharing app is available." }

internal fun saveSignalText(context: Context, label: String, text: String, jsonText: String = ""): String = runCatching {
    val result = OutputExportRepository.saveToDownloads(
        context = context,
        label = label,
        text = text,
        mediaUris = emptyList(),
        jsonText = jsonText
    )
    "Saved ${result.summary}"
}.getOrElse { "Save failed: ${it.message ?: "storage error"}" }

internal fun saveSignalMedia(
    context: Context,
    label: String,
    mediaUri: String,
    verificationText: String,
    jsonText: String = ""
): String = runCatching {
    val result = OutputExportRepository.saveToDownloads(
        context = context,
        label = label,
        text = verificationText,
        mediaUris = listOf(mediaUri),
        jsonText = jsonText
    )
    "Saved ${result.summary}"
}.getOrElse { "Save failed: ${it.message ?: "storage error"}" }

internal fun shareSignalMedia(context: Context, title: String, mediaUri: String, mimeType: String): String? = runCatching {
    val uri = Uri.parse(mediaUri)
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType.ifBlank { "application/octet-stream" }
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("MethodMesh received file", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            title
        )
    )
    null
}.getOrElse { it.message ?: "No sharing app is available." }

internal fun openSignalMedia(context: Context, mediaUri: String, mimeType: String): String? = runCatching {
    context.startActivity(
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(mediaUri), mimeType.ifBlank { "application/octet-stream" })
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    )
    null
}.getOrElse { it.message ?: "No app is available to open this file." }


internal fun finishSignalResult(
    context: CapabilityScreenContext,
    androidContext: Context,
    result: ExecutionResult,
    onConfirmed: (ExecutionResult) -> Unit,
    onSaveRequested: (() -> Unit)? = null
) {
    if (!context.isNativePresetRun || !context.isLastStep) {
        onConfirmed(result)
        return
    }
    val action = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher = context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
        context.request.settings["input_methodmesh_finish_to_launcher"] == "true"

    when {
        action == PresetResultAction.SAVE -> {
            onSaveRequested?.invoke()
            onConfirmed(result)
        }
        finishToLauncher -> onConfirmed(result)
        else -> androidContext.startActivity(
            Intent(androidContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }
}

internal fun fullJson(result: ExecutionResult?): String = result?.let {
    OutputFormatter.format(
        result = it,
        returnMode = ReturnMode.Json,
        includeProvenance = true,
        payloadMode = OutputFormatter.PayloadMode.FULL
    )
}.orEmpty()

@Composable
internal fun SignalStatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = accent.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.38f))
    ) {
        Text(
            text.uppercase(),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = accent,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp
        )
    }
}

@Composable
internal fun SignalInstrumentPanel(
    kicker: String,
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(30.dp)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, accent.copy(alpha = 0.24f), shape),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 9.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            SignalPanelLift.copy(alpha = 0.98f),
                            SignalPanel,
                            SignalBlack
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        kicker.uppercase(),
                        color = accent.copy(alpha = 0.92f),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        maxLines = 1
                    )
                    Text(
                        title,
                        color = SignalText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                badge?.takeIf(String::isNotBlank)?.let { SignalStatusPill(it, accent) }
            }
            content()
        }
    }
}

@Composable
internal fun SignalTelemetryTile(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = false,
    onClick: () -> Unit = {}
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .background(SignalBlack.copy(alpha = 0.54f), shape)
            .border(1.dp, SignalGrid.copy(alpha = 0.58f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            label.uppercase(),
            color = SignalMuted,
            style = MaterialTheme.typography.labelSmall,
            letterSpacing = 0.8.sp
        )
        Text(
            value.ifBlank { "—" },
            color = accent,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun SignalProgressTrack(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val p = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(RoundedCornerShape(50))
            .background(SignalGrid.copy(alpha = 0.48f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(p)
                .height(7.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.55f), accent)
                    )
                )
        )
    }
}



@Composable
internal fun SignalScopeTrace(
    samples: List<Float>,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(150.dp)
            .background(SignalBlack.copy(alpha = 0.86f), shape)
            .border(1.dp, SignalGrid.copy(alpha = 0.7f), shape)
            .padding(10.dp)
    ) {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(SignalGrid.copy(alpha = 0.35f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (i in 0..6) {
                val x = size.width * i / 6f
                drawLine(SignalGrid.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            if (samples.size >= 2) {
                val min = samples.minOrNull() ?: 0f
                val max = samples.maxOrNull() ?: min
                val rawSpan = max - min
                val last = samples.lastIndex.coerceAtLeast(1)
                fun yFor(value: Float): Float = if (rawSpan <= 1e-6f) {
                    size.height * 0.5f
                } else {
                    val paddedMin = min - rawSpan * 0.08f
                    val paddedSpan = rawSpan * 1.16f
                    size.height * (1f - ((value - paddedMin) / paddedSpan).coerceIn(0f, 1f))
                }
                for (i in 1..samples.lastIndex) {
                    val x0 = size.width * (i - 1).toFloat() / last.toFloat()
                    val x1 = size.width * i.toFloat() / last.toFloat()
                    val y0 = yFor(samples[i - 1])
                    val y1 = yFor(samples[i])
                    drawLine(accent.copy(alpha = 0.92f), Offset(x0, y0), Offset(x1, y1), strokeWidth = 3f)
                }
                drawCircle(accent, radius = 5.5f, center = Offset(size.width, yFor(samples.last())))
            }
        }
    }
}

@Composable
internal fun SignalCarrierRail(
    markHz: Double,
    spaceHz: Double,
    minHz: Double,
    maxHz: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val span = (maxHz - minHz).coerceAtLeast(1.0)
    val markFraction = ((markHz - minHz) / span).coerceIn(0.0, 1.0).toFloat()
    val spaceFraction = ((spaceHz - minHz) / span).coerceIn(0.0, 1.0).toFloat()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(Modifier.fillMaxWidth().height(34.dp)) {
            val y = size.height * 0.52f
            drawLine(SignalGrid, Offset(0f, y), Offset(size.width, y), strokeWidth = 3f)
            for (i in 0..8) {
                val x = size.width * i / 8f
                drawLine(SignalGrid.copy(alpha = 0.58f), Offset(x, y - 7f), Offset(x, y + 7f), strokeWidth = 1.5f)
            }
            val mx = size.width * markFraction
            val sx = size.width * spaceFraction
            drawLine(accent.copy(alpha = 0.42f), Offset(mx, 0f), Offset(mx, size.height), strokeWidth = 2f)
            drawLine(SignalAmber.copy(alpha = 0.42f), Offset(sx, 0f), Offset(sx, size.height), strokeWidth = 2f)
            drawCircle(accent, radius = 6.5f, center = Offset(mx, y))
            drawCircle(SignalAmber, radius = 6.5f, center = Offset(sx, y))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${minHz.toInt()} Hz", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Text("MARK ${markHz.toInt()}  •  SPACE ${spaceHz.toInt()}", color = SignalText, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
            Text("${maxHz.toInt()} Hz", color = SignalMuted, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
internal fun SignalCommittedCard(
    title: String,
    primaryLabel: String,
    primaryValue: String,
    fields: List<Pair<String, String>>,
    status: String?,
    onCopy: (String, String) -> Unit,
    onShare: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    onOpen: (() -> Unit)? = null
) {
    SignalInstrumentPanel(
        kicker = "FROZEN RESULT",
        title = title,
        accent = SignalGreen,
        badge = "Committed"
    ) {
        Text(
            primaryValue.ifBlank { "—" },
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = primaryValue.isNotBlank()) { onCopy(primaryLabel, primaryValue) },
            color = SignalText,
            style = MaterialTheme.typography.headlineSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Tap any frozen value to copy it.",
            color = SignalMuted,
            style = MaterialTheme.typography.labelSmall
        )
        fields.filter { it.second.isNotBlank() }.chunked(2).forEach { rowFields ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowFields.forEach { (label, value) ->
                    SignalTelemetryTile(
                        label = label,
                        value = value,
                        accent = SignalGreen,
                        modifier = Modifier.weight(1f),
                        enabled = true,
                        onClick = { onCopy(label, value) }
                    )
                }
                if (rowFields.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        status?.takeIf(String::isNotBlank)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = SignalMuted)
        }
        Spacer(Modifier.height(1.dp))
        val buttonColors = ButtonDefaults.outlinedButtonColors(contentColor = SignalText)
        val border = BorderStroke(1.dp, SignalGreen.copy(alpha = 0.36f))
        if (onOpen != null) {
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = buttonColors, border = border) { Text("Open received file") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShare, modifier = Modifier.weight(1f), colors = buttonColors, border = border) { Text("Share") }
            OutlinedButton(onClick = onSave, modifier = Modifier.weight(1f), colors = buttonColors, border = border) { Text("Save") }
            OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f), colors = buttonColors, border = border) { Text("Done") }
        }
    }
}

@Composable
internal fun SignalSpectrumView(
    bins: List<Float>,
    minHz: Double,
    maxHz: Double,
    markHz: Double,
    spaceHz: Double,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(132.dp)
            .background(SignalBlack.copy(alpha = 0.88f), shape)
            .border(1.dp, SignalGrid.copy(alpha = 0.7f), shape)
            .padding(10.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(SignalGrid.copy(alpha = 0.28f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (bins.isNotEmpty()) {
                val barWidth = size.width / bins.size.toFloat()
                bins.forEachIndexed { index, raw ->
                    val value = raw.coerceIn(0f, 1f)
                    val left = index * barWidth
                    val top = size.height * (1f - value)
                    drawRect(
                        color = accent.copy(alpha = 0.32f + value * 0.62f),
                        topLeft = Offset(left + 1f, top),
                        size = Size((barWidth - 2f).coerceAtLeast(1f), size.height - top)
                    )
                }
            }
            val span = (maxHz - minHz).coerceAtLeast(1.0)
            fun xFor(hz: Double): Float = (size.width * ((hz - minHz) / span).coerceIn(0.0, 1.0)).toFloat()
            val markX = xFor(markHz)
            val spaceX = xFor(spaceHz)
            drawLine(SignalAmber, Offset(markX, 0f), Offset(markX, size.height), strokeWidth = 3f)
            drawLine(SignalCyan, Offset(spaceX, 0f), Offset(spaceX, size.height), strokeWidth = 3f)
        }
    }
}
