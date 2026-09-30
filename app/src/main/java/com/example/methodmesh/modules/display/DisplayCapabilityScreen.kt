@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.example.methodmesh.modules.display

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.ImageDecoder
import android.graphics.drawable.Animatable as AndroidAnimatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.view.WindowManager
import android.widget.ImageView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.security.MessageDigest
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

private const val BASE_SCROLL_DP_PER_SECOND = 180f

class DisplayCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = DisplayShowMethod.ID
    override val title = DisplayShowMethod.title
    override val description = DisplayShowMethod.help

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        DisplayUi(context, onBack, onConfirmed, onCancel)
    }
}

@Composable
private fun DisplayUi(
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    fun initial(key: String, fallback: String) =
        context.action.settings[key] ?: context.action.settings["input_$key"] ?: fallback

    var contentKind by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("content_kind", "text")) }
    var text by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("text", DisplayShowMethod.DEFAULT_TEXT)) }
    var mediaUri by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("media_uri", "")) }
    var mediaMime by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("media_mime", "")) }
    var mediaSha256 by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("media_sha256", "")) }
    var motion by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("motion", "still")) }
    var theme by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("theme", "white_on_black")) }
    var alignment by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("alignment", "center")) }
    var rotation by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("content_rotation", "0").toIntOrNull() ?: 0) }
    var textScale by rememberSaveable(context.action.canonicalId) { mutableStateOf((initial("text_scale", "1.0").toFloatOrNull() ?: 1f).coerceIn(.2f, 1f)) }
    var speed by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("speed", "1.0").toFloatOrNull() ?: 1f) }
    var direction by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("direction", "left")) }
    var highBrightness by rememberSaveable(context.action.canonicalId) { mutableStateOf(initial("high_brightness", "true").toBoolean()) }
    var showing by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var startedIso by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
    var startedElapsed by rememberSaveable(context.action.canonicalId) { mutableStateOf(0L) }
    var autoStarted by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
    var committed by remember { mutableStateOf<ExecutionResult?>(null) }
    var committedPreview by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                androidContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            mediaUri = uri.toString()
            mediaMime = androidContext.contentResolver.getType(uri).orEmpty()
            contentKind = "media"
        }
    }

    LaunchedEffect(mediaUri) {
        mediaSha256 = if (mediaUri.isBlank()) {
            ""
        } else {
            withContext(Dispatchers.IO) { sha256ForUri(androidContext, mediaUri) }
        }
    }

    fun settings() = context.action.settings + mapOf(
        "content_kind" to contentKind,
        "text" to text,
        "media_uri" to mediaUri,
        "media_mime" to mediaMime,
        "media_sha256" to mediaSha256,
        "motion" to motion,
        "theme" to theme,
        "alignment" to alignment,
        "content_rotation" to rotation.toString(),
        "text_scale" to textScale.toString(),
        "speed" to speed.toString(),
        "direction" to direction,
        "high_brightness" to highBrightness.toString()
    )

    fun contentReady() = when (contentKind) {
        "media" -> mediaUri.isNotBlank()
        else -> text.isNotBlank()
    }

    fun startDisplay() {
        if (!contentReady()) return
        startedIso = Instant.now().toString()
        startedElapsed = android.os.SystemClock.elapsedRealtime()
        showing = true
    }

    fun finishDisplay(commit: Boolean) {
        showing = false
        if (!commit || startedIso.isBlank()) return
        val stopped = Instant.now().toString()
        val elapsed = (android.os.SystemClock.elapsedRealtime() - startedElapsed).coerceAtLeast(0L)
        val values = DisplayShowMethod.values(
            settings = settings(),
            startedIso = startedIso,
            stoppedIso = stopped,
            elapsedMs = elapsed,
            completionReason = "stopped_by_operator"
        )
        val request = DisplayShowMethod.request(
            DisplayShowMethod.ID,
            context.request.invocationContext.asMap(DisplayShowMethod.ID) + settings() + values,
            emptyList(),
            emptyList()
        )
        committedPreview = values
        committed = DisplayShowMethod.result(request, values, context.request.invocationContext)
    }

    LaunchedEffect(context.startsImmediately, autoStarted, committed, contentKind, text, mediaUri) {
        if (context.startsImmediately && !autoStarted && committed == null && contentReady()) {
            autoStarted = true
            startDisplay()
        }
    }

    LaunchedEffect(contentKind) {
        if (contentKind == "media" && motion == "scroll") motion = "still"
    }

    LaunchedEffect(motion) {
        if (motion == "flash" && speed > 2f) speed = 2f
    }

    CapabilityScreenScaffold(
        title = DisplayShowMethod.title,
        capabilityId = DisplayShowMethod.ID,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = committed,
        resultPreview = committedPreview,
        onBack = onBack,
        onRetry = {
            committed = null
            committedPreview = emptyMap()
            autoStarted = false
        },
        onConfirm = { committed?.let(onConfirmed) },
        onCancel = {
            if (showing) finishDisplay(false)
            onCancel()
        }
    ) {
        if (committed == null) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (context.settingShouldBeShown("content_kind")) {
                    CompactChoiceRow("Content", contentKind, listOf("text", "media")) { contentKind = it }
                }

                if (contentKind == "text") {
                    if (context.settingShouldBeShown("text")) {
                        OutlinedTextField(
                            value = text,
                            onValueChange = { text = it },
                            label = { Text("Message / emoji") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }
                } else {
                    if (context.settingShouldBeShown("media_uri")) {
                        CompactWideAction(
                            label = if (mediaUri.isBlank()) "Choose image / GIF" else "Choose different image / GIF",
                            onClick = { mediaPicker.launch(arrayOf("image/*")) }
                        )
                    }
                    if (mediaUri.isNotBlank()) {
                        Text(
                            text = mediaUri.substringAfterLast('/'),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                DisplayPreview(
                    contentKind = contentKind,
                    text = text,
                    mediaUri = mediaUri,
                    alignment = alignment,
                    rotation = rotation,
                    textScale = textScale,
                    motion = motion,
                    speed = speed,
                    direction = direction,
                    theme = theme
                )

                if (context.settingShouldBeShown("motion")) {
                    val motions = if (contentKind == "media") listOf("still", "flash", "pulse") else listOf("still", "scroll", "flash", "pulse")
                    CompactChoiceRow("Motion", if (motion in motions) motion else "still", motions) { motion = it }
                }
                if (context.settingShouldBeShown("theme")) {
                    CompactChoiceRow(
                        "Appearance",
                        theme,
                        listOf("white_on_black", "black_on_white", "black_on_yellow", "neon", "rainbow", "pride", "fabulous")
                    ) { theme = it }
                }
                if (contentKind == "text" && context.settingShouldBeShown("alignment")) {
                    CompactChoiceRow("Alignment", alignment, listOf("start", "center", "end")) { alignment = it }
                }
                if (context.settingShouldBeShown("content_rotation")) {
                    CompactChoiceRow("Rotation", rotation.toString(), listOf("0", "90", "180", "270")) {
                        rotation = it.toIntOrNull() ?: 0
                    }
                }
                if (contentKind == "text" && context.settingShouldBeShown("text_scale")) {
                    CompactSliderRow(
                        label = "Text size",
                        value = textScale,
                        valueRange = .2f..1f,
                        valueLabel = "${(textScale * 100f).roundToInt()}%",
                        onValueChange = { textScale = it.coerceIn(.2f, 1f) }
                    )
                }
                if (motion != "still" && context.settingShouldBeShown("speed")) {
                    val maxSpeed = if (motion == "flash") 2f else 4f
                    CompactSliderRow(
                        label = if (motion == "flash") "Flash rate" else "Motion speed",
                        value = speed.coerceIn(.25f, maxSpeed),
                        valueRange = .25f..maxSpeed,
                        valueLabel = when (motion) {
                            "flash" -> String.format("%.2f Hz", speed.coerceAtMost(2f))
                            "scroll" -> "${(BASE_SCROLL_DP_PER_SECOND * speed.coerceIn(.25f, 4f)).roundToInt()} dp/s"
                            else -> String.format("%.2f×", speed)
                        },
                        onValueChange = { speed = it.coerceIn(.25f, maxSpeed) }
                    )
                }
                if (motion == "scroll" && context.settingShouldBeShown("direction")) {
                    CompactChoiceRow("Scroll direction", direction, listOf("left", "right")) { direction = it }
                }
                if (context.settingShouldBeShown("high_brightness")) {
                    CompactChoiceRow("Brightness", if (highBrightness) "high" else "normal", listOf("high", "normal")) {
                        highBrightness = it == "high"
                    }
                }

                Button(
                    enabled = contentReady(),
                    onClick = { startDisplay() },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("SHOW") }
            }
        }
    }

    if (showing) {
        FullScreenDisplay(
            contentKind = contentKind,
            text = text,
            mediaUri = mediaUri,
            alignment = alignment,
            rotation = rotation,
            textScale = textScale,
            motion = motion,
            speed = speed,
            direction = direction,
            theme = theme,
            highBrightness = highBrightness,
            onScale = { textScale = it.coerceIn(.2f, 1f) },
            onFit = { textScale = 1f },
            onRotation = { rotation = it },
            onSpeed = { speed = it },
            onStop = { finishDisplay(true) },
            onCancel = { finishDisplay(false) }
        )
    }
}

@Composable
private fun DisplayPreview(
    contentKind: String,
    text: String,
    mediaUri: String,
    alignment: String,
    rotation: Int,
    textScale: Float,
    motion: String,
    speed: Float,
    direction: String,
    theme: String
) {
    Card(Modifier.fillMaxWidth()) {
        DisplaySurface(
            contentKind = contentKind,
            text = text.ifBlank { "Your message" },
            mediaUri = mediaUri,
            alignment = alignment,
            rotation = rotation,
            textScale = textScale,
            motion = motion,
            speed = speed,
            direction = direction,
            paused = false,
            theme = theme,
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
        )
    }
}

@Composable
private fun FullScreenDisplay(
    contentKind: String,
    text: String,
    mediaUri: String,
    alignment: String,
    rotation: Int,
    textScale: Float,
    motion: String,
    speed: Float,
    direction: String,
    theme: String,
    highBrightness: Boolean,
    onScale: (Float) -> Unit,
    onFit: () -> Unit,
    onRotation: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onStop: () -> Unit,
    onCancel: () -> Unit
) {
    val view = LocalView.current
    val activity = LocalContext.current.findActivity()
    var controls by rememberSaveable { mutableStateOf(true) }
    var paused by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(activity, view, highBrightness) {
        val window = activity?.window
        val oldBrightness = window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        view.keepScreenOn = true
        if (highBrightness) {
            window?.attributes = window?.attributes?.also { it.screenBrightness = 1f }
        }
        window?.decorView?.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        onDispose {
            view.keepScreenOn = false
            window?.attributes = window?.attributes?.also { it.screenBrightness = oldBrightness }
            window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    LaunchedEffect(controls) {
        if (controls) {
            delay(3000)
            controls = false
        }
    }

    BackHandler { onCancel() }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) { detectTapGestures { controls = !controls } }
                .pointerInput(contentKind, textScale) {
                    if (contentKind == "text") {
                        detectTransformGestures { _, _, zoom, _ ->
                            onScale((textScale * zoom).coerceIn(.2f, 1f))
                        }
                    }
                }
        ) {
            DisplaySurface(
                contentKind = contentKind,
                text = text,
                mediaUri = mediaUri,
                alignment = alignment,
                rotation = rotation,
                textScale = textScale,
                motion = motion,
                speed = speed,
                direction = direction,
                paused = paused,
                theme = theme,
                modifier = Modifier.fillMaxSize()
            )

            if (controls) {
                LiveControls(
                    contentKind = contentKind,
                    motion = motion,
                    scale = textScale,
                    rotation = rotation,
                    speed = speed,
                    paused = paused,
                    onScale = onScale,
                    onFit = onFit,
                    onRotation = onRotation,
                    onSpeed = onSpeed,
                    onPause = { paused = !paused },
                    onStop = onStop,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun DisplaySurface(
    contentKind: String,
    text: String,
    mediaUri: String,
    alignment: String,
    rotation: Int,
    textScale: Float,
    motion: String,
    speed: Float,
    direction: String,
    paused: Boolean,
    theme: String,
    modifier: Modifier = Modifier
) {
    val palette = paletteFor(theme)
    Box(
        modifier
            .displayBackground(palette)
            .clipToBounds(),
        contentAlignment = Alignment.Center
    ) {
        DisplayContent(
            contentKind = contentKind,
            text = text,
            mediaUri = mediaUri,
            alignment = alignment,
            rotation = rotation,
            textScale = textScale,
            motion = motion,
            speed = speed,
            direction = direction,
            paused = paused,
            palette = palette
        )
    }
}

@Composable
private fun DisplayContent(
    contentKind: String,
    text: String,
    mediaUri: String,
    alignment: String,
    rotation: Int,
    textScale: Float,
    motion: String,
    speed: Float,
    direction: String,
    paused: Boolean,
    palette: DisplayPalette
) {
    val transition = rememberInfiniteTransition(label = "display-effect")
    val flashHz = speed.coerceIn(.25f, 2f)
    val alpha = when {
        paused -> 1f
        motion == "flash" -> {
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween((500f / flashHz).toInt().coerceAtLeast(250), easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "flash-alpha"
            )
            value
        }
        motion == "pulse" -> {
            val value by transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.5f,
                animationSpec = infiniteRepeatable(
                    animation = tween((900f / speed.coerceIn(.25f, 4f)).toInt().coerceAtLeast(225)),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse-alpha"
            )
            value
        }
        else -> 1f
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val quarterTurn = rotation % 180 != 0
        val viewport = Modifier
            .width(if (quarterTurn) maxHeight else maxWidth)
            .height(if (quarterTurn) maxWidth else maxHeight)
            .graphicsLayer(rotationZ = rotation.toFloat(), alpha = alpha)

        if (contentKind == "media") {
            Box(viewport.padding(12.dp), contentAlignment = Alignment.Center) {
                if (mediaUri.isBlank()) {
                    Text(
                        "Choose an image, GIF or animated WebP",
                        color = palette.foreground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    MediaContent(mediaUri, Modifier.fillMaxSize())
                }
            }
        } else if (motion == "scroll") {
            ScrollingText(
                text = text,
                direction = direction,
                speed = speed,
                paused = paused,
                palette = palette,
                textScale = textScale,
                modifier = viewport.padding(vertical = 8.dp)
            )
        } else {
            FittedText(
                text = text,
                alignment = alignment,
                textScale = textScale,
                palette = palette,
                modifier = viewport.padding(14.dp)
            )
        }
    }
}

private data class FittedTextLayout(
    val renderedText: String,
    val fontSizeSp: Float
)

@Composable
private fun FittedText(
    text: String,
    alignment: String,
    textScale: Float,
    palette: DisplayPalette,
    modifier: Modifier = Modifier
) {
    val align = when (alignment) {
        "start" -> TextAlign.Start
        "end" -> TextAlign.End
        else -> TextAlign.Center
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val maxHeightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val maximumFit = remember(text, maxWidthPx, maxHeightPx) {
            fitTextWithoutBreakingWords(
                text = text,
                measurer = measurer,
                maxWidthPx = maxWidthPx,
                maxHeightPx = maxHeightPx,
                maximumFontSp = 720f
            )
        }
        val scaledFontSp = (maximumFit.fontSizeSp * textScale.coerceIn(.2f, 1f)).coerceAtLeast(4f)
        val scaledStyle = remember(scaledFontSp) {
            TextStyle(fontSize = scaledFontSp.sp, fontWeight = FontWeight.Bold)
        }
        val scaledText = remember(text, scaledFontSp, maxWidthPx) {
            wrapAtWordBoundaries(text.ifBlank { " " }.replace("\r\n", "\n"), scaledStyle, maxWidthPx, measurer)
                ?: maximumFit.renderedText
        }
        val style = displayTextStyle(
            palette = palette,
            fontSizeSp = scaledFontSp,
            textAlign = align
        )
        Text(
            text = scaledText,
            style = style,
            softWrap = false,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun fitTextWithoutBreakingWords(
    text: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxWidthPx: Int,
    maxHeightPx: Int,
    maximumFontSp: Float
): FittedTextLayout {
    val source = text.ifBlank { " " }.replace("\r\n", "\n")
    var low = 4f
    var high = maximumFontSp.coerceAtLeast(low)
    var best = FittedTextLayout(source, low)

    repeat(13) {
        val candidateSp = (low + high) / 2f
        val metricStyle = TextStyle(fontSize = candidateSp.sp, fontWeight = FontWeight.Bold)
        val wrapped = wrapAtWordBoundaries(source, metricStyle, maxWidthPx, measurer)
        val fits = if (wrapped == null) {
            false
        } else {
            val measured = measurer.measure(
                text = wrapped,
                style = metricStyle,
                softWrap = false
            )
            measured.size.height <= maxHeightPx
        }
        if (fits && wrapped != null) {
            best = FittedTextLayout(wrapped, candidateSp)
            low = candidateSp
        } else {
            high = candidateSp
        }
    }
    return best
}

private fun wrapAtWordBoundaries(
    text: String,
    style: TextStyle,
    maxWidthPx: Int,
    measurer: androidx.compose.ui.text.TextMeasurer
): String? {
    val output = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
        if (paragraph.isBlank()) {
            output += ""
            return@forEach
        }
        val words = Regex("\\S+").findAll(paragraph).map { it.value }.toList()
        var line = ""
        for (word in words) {
            val wordWidth = measurer.measure(word, style = style, softWrap = false).size.width
            if (wordWidth > maxWidthPx) return null
            val candidate = if (line.isBlank()) word else "$line $word"
            val candidateWidth = measurer.measure(candidate, style = style, softWrap = false).size.width
            if (candidateWidth <= maxWidthPx) {
                line = candidate
            } else {
                if (line.isNotBlank()) output += line
                line = word
            }
        }
        output += line
    }
    return output.joinToString("\n")
}

@Composable
private fun ScrollingText(
    text: String,
    direction: String,
    speed: Float,
    paused: Boolean,
    palette: DisplayPalette,
    textScale: Float,
    modifier: Modifier = Modifier
) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val scrollText = remember(text) { text.replace(Regex("\\s+"), " ").trim().ifBlank { " " } }
    val offset = remember(scrollText, direction) { Animatable(0f) }
    var initialized by remember(scrollText, direction) { mutableStateOf(false) }
    val left = direction != "right"

    BoxWithConstraints(
        modifier.clipToBounds(),
        contentAlignment = Alignment.CenterStart
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val maximumFitSp = remember(scrollText, containerHeightPx) {
            largestSingleLineFontSizeByHeight(
                text = scrollText,
                measurer = measurer,
                maxHeightPx = (containerHeightPx * .88f).toInt(),
                maximumFontSp = 1000f
            )
        }
        val fontSizeSp = (maximumFitSp * textScale.coerceIn(.2f, 1f)).coerceAtLeast(8f)
        val style = displayTextStyle(palette, fontSizeSp, TextAlign.Start)
        val measured = remember(scrollText, fontSizeSp, palette) {
            measurer.measure(
                text = scrollText,
                style = style,
                softWrap = false,
                maxLines = 1
            )
        }
        val textWidthPx = measured.size.width.coerceAtLeast(1).toFloat()
        val textHeightPx = measured.size.height.coerceAtLeast(1).toFloat()
        val edgePaddingPx = with(density) { 12.dp.toPx() }
        val travelWidthPx = textWidthPx + (edgePaddingPx * 2f)

        LaunchedEffect(containerWidthPx, travelWidthPx, direction, speed, paused, scrollText, fontSizeSp) {
            if (paused) {
                offset.stop()
                return@LaunchedEffect
            }
            val start = if (left) containerWidthPx else -travelWidthPx
            val end = if (left) -travelWidthPx else containerWidthPx
            val lowBound = minOf(start, end)
            val highBound = maxOf(start, end)
            if (!initialized || offset.value !in lowBound..highBound) {
                offset.snapTo(start)
                initialized = true
            }

            val pixelsPerSecond = with(density) { (BASE_SCROLL_DP_PER_SECOND * speed.coerceIn(.25f, 4f)).dp.toPx() }
            while (true) {
                val remaining = abs(end - offset.value)
                val durationMs = ((remaining / pixelsPerSecond) * 1000f).toInt().coerceAtLeast(80)
                offset.animateTo(end, animationSpec = tween(durationMs, easing = LinearEasing))
                offset.snapTo(start)
            }
        }

        // The viewport clips. The message itself is measured at its complete,
        // unconstrained single-line width and is drawn directly onto the viewport
        // canvas. This avoids Compose child-width constraints clipping the line
        // before translation (the previous cause of only "HEL" being visible).
        Canvas(Modifier.fillMaxSize()) {
            val x = offset.value + edgePaddingPx
            val y = ((size.height - textHeightPx) / 2f).coerceAtLeast(0f)
            drawText(measured, topLeft = Offset(x, y))
        }
    }
}

private fun largestSingleLineFontSizeByHeight(
    text: String,
    measurer: androidx.compose.ui.text.TextMeasurer,
    maxHeightPx: Int,
    maximumFontSp: Float
): Float {
    var low = 8f
    var high = maximumFontSp.coerceAtLeast(low)
    var best = low
    repeat(13) {
        val candidate = (low + high) / 2f
        val measured = measurer.measure(
            text = text,
            style = TextStyle(fontSize = candidate.sp, fontWeight = FontWeight.Bold),
            softWrap = false,
            maxLines = 1
        )
        if (measured.size.height <= maxHeightPx) {
            best = candidate
            low = candidate
        } else {
            high = candidate
        }
    }
    return best
}

private fun displayTextStyle(
    palette: DisplayPalette,
    fontSizeSp: Float,
    textAlign: TextAlign
): TextStyle = if (palette.textBrush != null) {
    TextStyle(
        brush = palette.textBrush,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        shadow = palette.shadow
    )
} else {
    TextStyle(
        color = palette.foreground,
        fontSize = fontSizeSp.sp,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        shadow = palette.shadow
    )
}

@Composable
private fun MediaContent(uriText: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val drawable = remember(uriText) { loadMediaDrawable(context, uriText) }

    DisposableEffect(drawable) {
        (drawable as? AndroidAnimatable)?.start()
        onDispose { (drawable as? AndroidAnimatable)?.stop() }
    }

    if (drawable == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Unable to open media", color = Color.White)
        }
        return
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
        },
        update = { imageView -> imageView.setImageDrawable(drawable) }
    )
}

@Composable
private fun LiveControls(
    contentKind: String,
    motion: String,
    scale: Float,
    rotation: Int,
    speed: Float,
    paused: Boolean,
    onScale: (Float) -> Unit,
    onFit: () -> Unit,
    onRotation: (Int) -> Unit,
    onSpeed: (Float) -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 18.dp),
        color = Color.Black.copy(alpha = .82f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (contentKind == "text") {
                OverlaySlider(
                    label = "Text size",
                    value = scale.coerceIn(.2f, 1f),
                    valueRange = .2f..1f,
                    valueLabel = "${(scale.coerceIn(.2f, 1f) * 100f).roundToInt()}%",
                    onValueChange = onScale
                )
            }
            if (motion != "still") {
                val maxSpeed = if (motion == "flash") 2f else 4f
                OverlaySlider(
                    label = if (motion == "flash") "Flash rate" else "Speed",
                    value = speed.coerceIn(.25f, maxSpeed),
                    valueRange = .25f..maxSpeed,
                    valueLabel = when (motion) {
                        "flash" -> String.format("%.2f Hz", speed.coerceAtMost(2f))
                        "scroll" -> "${(BASE_SCROLL_DP_PER_SECOND * speed.coerceIn(.25f, 4f)).roundToInt()} dp/s"
                        else -> String.format("%.2f×", speed)
                    },
                    onValueChange = { onSpeed(it.coerceIn(.25f, maxSpeed)) }
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (contentKind == "text") OverlayAction("Fit", onFit)
                OverlayAction("Rotate") { onRotation((rotation + 90) % 360) }
                if (motion != "still") OverlayAction(if (paused) "Resume" else "Pause", onPause)
                Button(onClick = onStop, shape = RoundedCornerShape(8.dp)) { Text("STOP") }
            }
        }
    }
}

@Composable
private fun OverlaySlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OverlayAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.White.copy(alpha = .08f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color.White.copy(alpha = .35f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun CompactWideAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CompactChoiceRow(
    label: String,
    selected: String,
    choices: List<String>,
    onSelect: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            choices.forEach { choice ->
                val isSelected = choice == selected
                Surface(
                    onClick = { onSelect(choice) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text(
                        text = pretty(choice),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactSliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueLabel: String,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun CompactAction(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private data class DisplayPalette(
    val background: Color,
    val foreground: Color,
    val backgroundBrush: Brush? = null,
    val textBrush: Brush? = null,
    val shadow: Shadow? = null
)

private fun paletteFor(theme: String): DisplayPalette = when (theme) {
    "black_on_white" -> DisplayPalette(Color.White, Color.Black)
    "black_on_yellow" -> DisplayPalette(Color(0xFFFFE600), Color.Black)
    "neon" -> DisplayPalette(
        background = Color.Black,
        foreground = Color(0xFF00FFFF),
        shadow = Shadow(Color(0xFFFF00FF), blurRadius = 18f)
    )
    "rainbow" -> DisplayPalette(
        background = Color.Black,
        foreground = Color.White,
        textBrush = Brush.linearGradient(
            listOf(
                Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFEB3B),
                Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA)
            )
        ),
        shadow = Shadow(Color.Black, blurRadius = 10f)
    )
    "pride" -> DisplayPalette(
        background = Color.Black,
        foreground = Color.White,
        textBrush = Brush.verticalGradient(
            listOf(
                Color(0xFFE40303), Color(0xFFFF8C00), Color(0xFFFFED00),
                Color(0xFF008026), Color(0xFF24408E), Color(0xFF732982)
            )
        ),
        shadow = Shadow(Color.Black, blurRadius = 12f)
    )
    "fabulous" -> DisplayPalette(
        background = Color(0xFF17001F),
        foreground = Color.White,
        backgroundBrush = Brush.linearGradient(
            listOf(Color(0xFF17001F), Color(0xFF3A073C), Color(0xFF001D2E))
        ),
        textBrush = Brush.linearGradient(
            listOf(Color(0xFFFF1493), Color(0xFF7B2CFF), Color(0xFF00E5FF), Color(0xFFFFD700))
        ),
        shadow = Shadow(Color(0xFFFF00FF), blurRadius = 20f)
    )
    else -> DisplayPalette(Color.Black, Color.White)
}

private fun Modifier.displayBackground(palette: DisplayPalette): Modifier {
    val base = background(palette.background)
    return palette.backgroundBrush?.let { base.background(it) } ?: base
}

private fun loadMediaDrawable(context: Context, uriText: String): Drawable? {
    if (uriText.isBlank()) return null
    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return null
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri))
        } else {
            @Suppress("DEPRECATION")
            context.contentResolver.openInputStream(uri)?.use { Drawable.createFromStream(it, uri.toString()) }
        }
    }.getOrNull()
}

private fun sha256ForUri(context: Context, uriText: String): String {
    val uri = runCatching { Uri.parse(uriText) }.getOrNull() ?: return ""
    return runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return@runCatching ""
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrDefault("")
}

private fun pretty(value: String): String = when (value) {
    "white_on_black" -> "White / black"
    "black_on_white" -> "Black / white"
    "black_on_yellow" -> "Black / yellow"
    else -> value
        .replace('_', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
