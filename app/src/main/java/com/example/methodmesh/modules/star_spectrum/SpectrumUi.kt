package com.example.methodmesh.modules.star_spectrum

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class TraceSelection(val startX: Float, val startY: Float, val endX: Float, val endY: Float) {
    val length: Float get() = hypot(endX - startX, endY - startY)
}

internal data class ColorTransitionLandmark(
    val id: String,
    val label: String,
    val x: Float,
    val y: Float,
    val priorWavelengthNm: Double,
    val priorSigmaNm: Double
)

internal object ColorTransitionLandmarks {
    data class Definition(
        val id: String,
        val label: String,
        val priorWavelengthNm: Double,
        val priorSigmaNm: Double,
        val accent: Color
    )

    val definitions = listOf(
        Definition(
            id = "red_yellow",
            label = "RED / YELLOW",
            priorWavelengthNm = 600.0,
            priorSigmaNm = 22.0,
            accent = Color(0xFFFF9B55)
        ),
        Definition(
            id = "yellow_green",
            label = "YELLOW / GREEN",
            priorWavelengthNm = 565.0,
            priorSigmaNm = 18.0,
            accent = Color(0xFFD7E95B)
        ),
        Definition(
            id = "green_blue",
            label = "GREEN / BLUE",
            priorWavelengthNm = 500.0,
            priorSigmaNm = 20.0,
            accent = Color(0xFF58D7C5)
        ),
        Definition(
            id = "blue_violet",
            label = "BLUE / VIOLET",
            priorWavelengthNm = 445.0,
            priorSigmaNm = 20.0,
            accent = Color(0xFF8D8CFF)
        )
    )

    fun definition(id: String): Definition? =
        definitions.firstOrNull { it.id == id }
}


internal data class AutoColorBoundaryDetection(
    val landmarks: List<ColorTransitionLandmark>,
    val fitQuality: Double,
    val reliableColourFraction: Double,
    val meanMatchCost: Double
)



@Composable
internal fun SpectrumToolHeader(
    title: String,
    subtitle: String,
    committed: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(50),
            color = if (committed) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        ) {
            Text(
                if (committed) "Committed" else "Working",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (canGoBack) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
        OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
    }
}

@Composable
internal fun SpectrumSection(
    title: String,
    hint: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            hint?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
internal fun MetricTile(label: String, value: String, copyValue: String = value, modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        modifier = modifier.clickable(enabled = copyValue.isNotBlank()) { copyToClipboard(context, label, copyValue) },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun SpectrumImageSurface(
    bitmap: Bitmap,
    trace: TraceSelection?,
    onSelectEndpoints: () -> Unit,
    ribbonHalfWidthPx: Int,
    extraction: SpectrumExtraction? = null,
    colorLandmarks: List<ColorTransitionLandmark> = emptyList(),
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var viewScale by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF05070B))
                .border(2.dp, Color(0xFF303741), RoundedCornerShape(18.dp))
                .onSizeChanged { boxSize = it }
                .pointerInput(boxSize, bitmap) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        viewScale = (viewScale * zoom).coerceIn(1f, 8f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Stellar spectrum image",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = viewScale
                        scaleY = viewScale
                        translationX = panX
                        translationY = panY
                    },
                contentScale = ContentScale.Fit
            )
            Canvas(Modifier.fillMaxSize()) {
                if (boxSize.width <= 0 || boxSize.height <= 0) return@Canvas
                trace?.let { user ->
                    val a = imageToScreen(Offset(user.startX, user.startY), boxSize, bitmap, viewScale, Offset(panX, panY))
                    val b = imageToScreen(Offset(user.endX, user.endY), boxSize, bitmap, viewScale, Offset(panX, panY))
                    if (a != null && b != null) {
                        val d = b - a
                        val len = hypot(d.x, d.y).coerceAtLeast(1f)
                        val nx = -d.y / len
                        val ny = d.x / len
                        val fitScale = fittedRect(boxSize, bitmap).width / bitmap.width.toFloat()
                        val ribbon = ribbonHalfWidthPx * fitScale * viewScale
                        drawLine(Color.White.copy(alpha = 0.9f), a, b, strokeWidth = 3f)
                        drawLine(primaryColor.copy(alpha = 0.65f), a + Offset(nx * ribbon, ny * ribbon), b + Offset(nx * ribbon, ny * ribbon), strokeWidth = 1.5f)
                        drawLine(primaryColor.copy(alpha = 0.65f), a - Offset(nx * ribbon, ny * ribbon), b - Offset(nx * ribbon, ny * ribbon), strokeWidth = 1.5f)
                        drawCircle(primaryColor, radius = 7f, center = a)
                        drawCircle(Color.White, radius = 7f, center = b, style = Stroke(3f))
                    }
                }
                extraction?.let { result ->
                    val stride = max(1, result.trace.sampleX.size / 800)
                    var previous: Offset? = null
                    for (i in result.trace.sampleX.indices step stride) {
                        val p = imageToScreen(
                            Offset(result.trace.sampleX[i].toFloat(), result.trace.sampleY[i].toFloat()),
                            boxSize, bitmap, viewScale, Offset(panX, panY)
                        ) ?: continue
                        previous?.let { drawLine(Color(0xFF55E0C2), it, p, strokeWidth = 3f) }
                        previous = p
                        if (result.contaminationProbability[i] >= 0.5) {
                            drawCircle(Color(0xFFFF765E).copy(alpha = 0.85f), 3f + 4f * result.contaminationProbability[i].toFloat(), p)
                        }
                    }
                    result.features.filter { it.confidence >= 0.35 }.take(30).forEach { feature ->
                        val i = feature.index.coerceIn(0, result.trace.sampleX.lastIndex)
                        imageToScreen(
                            Offset(result.trace.sampleX[i].toFloat(), result.trace.sampleY[i].toFloat()),
                            boxSize, bitmap, viewScale, Offset(panX, panY)
                        )?.let { drawCircle(Color(0xFFFFD95A), 6f, it, style = Stroke(2f)) }
                    }
                }

                colorLandmarks.forEach { landmark ->
                    val definition =
                        ColorTransitionLandmarks
                            .definition(landmark.id)
                            ?: return@forEach

                    imageToScreen(
                        Offset(
                            landmark.x,
                            landmark.y
                        ),
                        boxSize,
                        bitmap,
                        viewScale,
                        Offset(
                            panX,
                            panY
                        )
                    )?.let { point ->
                        drawCircle(
                            Color.Black.copy(alpha = 0.75f),
                            10f,
                            point
                        )
                        drawCircle(
                            definition.accent,
                            6.5f,
                            point
                        )
                        drawCircle(
                            Color.White,
                            2.5f,
                            point
                        )
                    }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.68f)
            ) {
                Text(
                    if (trace == null) "No endpoints selected" else "RED → VIOLET selected",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSelectEndpoints, modifier = Modifier.weight(1f)) {
                Text(if (trace == null) "Select spectrum landmarks" else "Edit spectrum landmarks")
            }
            OutlinedButton(
                onClick = { viewScale = 1f; panX = 0f; panY = 0f },
                modifier = Modifier.weight(1f)
            ) { Text("Reset view") }
        }
    }
}

@Composable
internal fun SpectrumEndpointPickerDialog(
    bitmap: Bitmap,
    initialTrace: TraceSelection?,
    initialLandmarks: List<ColorTransitionLandmark> = emptyList(),
    allowColorBoundaries: Boolean = false,
    onDismiss: () -> Unit,
    onSelected: (TraceSelection, List<ColorTransitionLandmark>) -> Unit
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var viewScale by rememberSaveable { mutableFloatStateOf(1f) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }

    var redPoint by remember(initialTrace) {
        mutableStateOf(initialTrace?.let { Offset(it.startX, it.startY) })
    }
    var violetPoint by remember(initialTrace) {
        mutableStateOf(initialTrace?.let { Offset(it.endX, it.endY) })
    }
    var landmarks by remember(initialLandmarks) {
        mutableStateOf(initialLandmarks.associateBy { it.id })
    }
    var activeTarget by rememberSaveable {
        mutableStateOf(
            when {
                initialTrace == null -> "red"
                allowColorBoundaries &&
                    initialLandmarks.size <
                    ColorTransitionLandmarks.definitions.size ->
                    ColorTransitionLandmarks.definitions
                        .firstOrNull { landmarks[it.id] == null }
                        ?.id
                        ?: "red"
                else -> "red"
            }
        )
    }

    var magnifierPoint by remember { mutableStateOf<Offset?>(null) }
    var stableHoldPoint by remember { mutableStateOf<Offset?>(null) }
    var magnifying by remember { mutableStateOf(false) }
    var holdConfirmed by remember { mutableStateOf(false) }
    var stabilityToken by remember { mutableStateOf(0) }
    var lockProgress by remember { mutableFloatStateOf(0f) }
    var lockFeedback by remember { mutableStateOf<String?>(null) }
    var autoBoundaryFeedback by remember { mutableStateOf<String?>(null) }
    var autoBoundaryAttemptKey by rememberSaveable { mutableStateOf("") }
    var navigationMode by rememberSaveable { mutableStateOf(initialTrace != null && (!allowColorBoundaries || initialLandmarks.isNotEmpty())) }

    var redXText by remember(initialTrace) {
        mutableStateOf(initialTrace?.startX?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var redYText by remember(initialTrace) {
        mutableStateOf(initialTrace?.startY?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var violetXText by remember(initialTrace) {
        mutableStateOf(initialTrace?.endX?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var violetYText by remember(initialTrace) {
        mutableStateOf(initialTrace?.endY?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var boundaryXText by remember(activeTarget, landmarks) {
        mutableStateOf(
            landmarks[activeTarget]
                ?.x
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: ""
        )
    }
    var boundaryYText by remember(activeTarget, landmarks) {
        mutableStateOf(
            landmarks[activeTarget]
                ?.y
                ?.let { String.format(Locale.US, "%.1f", it) }
                ?: ""
        )
    }

    val redColor = Color(0xFFFF5E62)
    val violetColor = Color(0xFFB78CFF)

    fun clampToImage(point: Offset): Offset = Offset(
        point.x.coerceIn(0f, (bitmap.width - 1).coerceAtLeast(0).toFloat()),
        point.y.coerceIn(0f, (bitmap.height - 1).coerceAtLeast(0).toFloat())
    )

    fun targetLabel(target: String): String =
        when (target) {
            "red" -> "RED"
            "violet" -> "VIOLET"
            else ->
                ColorTransitionLandmarks
                    .definition(target)
                    ?.label
                    ?: target
        }

    fun targetColor(target: String): Color =
        when (target) {
            "red" -> redColor
            "violet" -> violetColor
            else ->
                ColorTransitionLandmarks
                    .definition(target)
                    ?.accent
                    ?: Color.White
        }

    fun updateTarget(target: String, point: Offset) {
        val p = clampToImage(point)

        when (target) {
            "red" -> {
                redPoint = p
                redXText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.x
                    )
                redYText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.y
                    )
            }

            "violet" -> {
                violetPoint = p
                violetXText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.x
                    )
                violetYText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.y
                    )
            }

            else -> {
                val definition =
                    ColorTransitionLandmarks
                        .definition(target)
                        ?: return

                landmarks =
                    landmarks +
                        (
                            target to
                                ColorTransitionLandmark(
                                    id = target,
                                    label = definition.label,
                                    x = p.x,
                                    y = p.y,
                                    priorWavelengthNm =
                                        definition
                                            .priorWavelengthNm,
                                    priorSigmaNm =
                                        definition
                                            .priorSigmaNm
                                )
                            )

                boundaryXText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.x
                    )
                boundaryYText =
                    String.format(
                        Locale.US,
                        "%.1f",
                        p.y
                    )
            }
        }
    }

    fun applyManual(target: String) {
        val xRaw =
            when (target) {
                "red" -> redXText
                "violet" -> violetXText
                else -> boundaryXText
            }
        val yRaw =
            when (target) {
                "red" -> redYText
                "violet" -> violetYText
                else -> boundaryYText
            }

        val x = xRaw.toFloatOrNull() ?: return
        val y = yRaw.toFloatOrNull() ?: return

        updateTarget(
            target,
            Offset(x, y)
        )
    }

    fun chooseNextAfter(target: String) {
        when (target) {
            "red" -> {
                if (violetPoint == null) {
                    activeTarget = "violet"
                    navigationMode = false
                } else if (allowColorBoundaries) {
                    activeTarget =
                        ColorTransitionLandmarks
                            .definitions
                            .firstOrNull {
                                landmarks[it.id] == null
                            }
                            ?.id
                            ?: "violet"
                    navigationMode =
                        ColorTransitionLandmarks
                            .definitions
                            .all {
                                landmarks[it.id] != null
                            }
                } else {
                    navigationMode = true
                }
            }

            "violet" -> {
                if (!allowColorBoundaries) {
                    navigationMode = true
                } else {
                    val next =
                        ColorTransitionLandmarks
                            .definitions
                            .firstOrNull {
                                landmarks[it.id] == null
                            }

                    if (next != null) {
                        activeTarget = next.id
                        navigationMode = false
                    } else {
                        navigationMode = true
                    }
                }
            }

            else -> {
                if (!allowColorBoundaries) {
                    navigationMode = true
                    return
                }

                val definitions =
                    ColorTransitionLandmarks
                        .definitions
                val current =
                    definitions.indexOfFirst {
                        it.id == target
                    }
                val next =
                    definitions
                        .drop(
                            (current + 1)
                                .coerceAtLeast(0)
                        )
                        .firstOrNull {
                            landmarks[it.id] == null
                        }
                        ?: definitions
                            .firstOrNull {
                                landmarks[it.id] == null
                            }

                if (next != null) {
                    activeTarget = next.id
                    navigationMode = false
                } else {
                    navigationMode = true
                }
            }
        }
    }

    val canFinish = redPoint != null &&
        violetPoint != null &&
        hypot(
            (violetPoint?.x ?: 0f) -
                (redPoint?.x ?: 0f),
            (violetPoint?.y ?: 0f) -
                (redPoint?.y ?: 0f)
        ) >= 12f

    fun proposeColorBoundaries(
        automatic: Boolean
    ): Boolean {
        val red = redPoint
        val violet = violetPoint

        if (
            !allowColorBoundaries ||
            red == null ||
            violet == null
        ) {
            return false
        }

        val detected =
            autoDetectColorTransitionLandmarks(
                bitmap = bitmap,
                trace =
                    TraceSelection(
                        startX = red.x,
                        startY = red.y,
                        endX = violet.x,
                        endY = violet.y
                    )
            )

        if (detected == null) {
            autoBoundaryFeedback =
                if (automatic) {
                    "Automatic colour-boundary proposal was not stable enough. Place the four transition markers manually or try Re-detect."
                } else {
                    "Auto-detect could not find a stable colour progression. Keep the boundaries manual for this image."
                }
            return false
        }

        landmarks =
            detected.landmarks
                .associateBy {
                    it.id
                }
        navigationMode = true
        autoBoundaryFeedback =
            (
                if (automatic) {
                    "Auto-proposed"
                } else {
                    "Suggested"
                }
                ) +
                " ${detected.landmarks.size}/4 boundaries · chromatic fit ${
                    "%.0f".format(
                        Locale.US,
                        detected.fitQuality * 100.0
                    )
                }% · useful colour ${
                    "%.0f".format(
                        Locale.US,
                        detected.reliableColourFraction * 100.0
                    )
                }%. Review the markers and adjust any boundary that is visibly wrong."

        return true
    }

    LaunchedEffect(
        allowColorBoundaries,
        redPoint,
        violetPoint
    ) {
        val red = redPoint
        val violet = violetPoint

        if (
            allowColorBoundaries &&
            red != null &&
            violet != null &&
            landmarks.isEmpty()
        ) {
            val key =
                "${"%.1f".format(Locale.US, red.x)},${"%.1f".format(Locale.US, red.y)}|" +
                    "${"%.1f".format(Locale.US, violet.x)},${"%.1f".format(Locale.US, violet.y)}"

            if (key != autoBoundaryAttemptKey) {
                autoBoundaryAttemptKey = key
                proposeColorBoundaries(
                    automatic = true
                )
            }
        }
    }

    LaunchedEffect(
        magnifying,
        stabilityToken,
        activeTarget,
        navigationMode
    ) {
        val candidate = stableHoldPoint
        val target = activeTarget
        val token = stabilityToken

        if (
            magnifying &&
            !navigationMode &&
            candidate != null
        ) {
            lockProgress = 0f
            lockFeedback = null

            for (step in 1..30) {
                delay(100)

                if (
                    !magnifying ||
                    navigationMode ||
                    stabilityToken != token ||
                    activeTarget != target
                ) {
                    return@LaunchedEffect
                }

                lockProgress =
                    step / 30f
            }

            if (
                magnifying &&
                !navigationMode &&
                stabilityToken == token &&
                activeTarget == target
            ) {
                updateTarget(
                    target,
                    candidate
                )
                holdConfirmed = true
                lockProgress = 1f
                lockFeedback =
                    "${targetLabel(target)} LOCKED ✓"
            }
        }
    }

    val endpointGestureModifier =
        if (navigationMode) {
            Modifier.pointerInput(boxSize, bitmap, navigationMode) {
                detectTransformGestures { _, pan, zoom, _ ->
                    if (boxSize.width <= 0 || boxSize.height <= 0) return@detectTransformGestures

                    val newScale = (viewScale * zoom).coerceIn(1f, 12f)
                    val fitted = fittedRect(boxSize, bitmap)
                    val scaledWidth = fitted.width * newScale
                    val scaledHeight = fitted.height * newScale
                    val maxPanX = max(0f, (scaledWidth - boxSize.width) / 2f + 24f)
                    val maxPanY = max(0f, (scaledHeight - boxSize.height) / 2f + 24f)

                    viewScale = newScale
                    panX = (panX + pan.x).coerceIn(-maxPanX, maxPanX)
                    panY = (panY + pan.y).coerceIn(-maxPanY, maxPanY)
                }
            }
        } else {
            Modifier.pointerInput(
                boxSize,
                bitmap,
                activeTarget,
                navigationMode
            ) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { point ->
                        val imagePoint = screenToImage(
                            point,
                            boxSize,
                            bitmap,
                            viewScale,
                            Offset(panX, panY)
                        ) ?: return@detectDragGesturesAfterLongPress

                        magnifying = true
                        holdConfirmed = false
                        stableHoldPoint = imagePoint
                        magnifierPoint = imagePoint
                        stabilityToken += 1
                        lockProgress = 0f
                        lockFeedback = null
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        if (holdConfirmed) return@detectDragGesturesAfterLongPress

                        val imagePoint = screenToImage(
                            change.position,
                            boxSize,
                            bitmap,
                            viewScale,
                            Offset(panX, panY)
                        ) ?: return@detectDragGesturesAfterLongPress

                        val stable = stableHoldPoint
                        if (
                            stable == null ||
                            hypot(imagePoint.x - stable.x, imagePoint.y - stable.y) > 3f
                        ) {
                            // Ignore normal finger tremor within a 3-image-pixel radius.
                            stableHoldPoint = imagePoint
                            magnifierPoint = imagePoint
                            stabilityToken += 1
                            lockProgress = 0f
                            lockFeedback = null
                        }
                    },
                    onDragEnd = {
                        magnifying = false
                        stableHoldPoint = null
                        magnifierPoint = null
                        lockProgress = if (holdConfirmed) 1f else 0f

                        if (holdConfirmed) {
                            chooseNextAfter(activeTarget)
                        }
                        holdConfirmed = false
                    },
                    onDragCancel = {
                        magnifying = false
                        stableHoldPoint = null
                        magnifierPoint = null
                        lockProgress = 0f
                        holdConfirmed = false
                    }
                )
            }
        }

    Dialog(
        onDismissRequest = { /* full-screen precision picker: explicit Cancel/Done only */ },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF020305)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { boxSize = it }
                    .then(endpointGestureModifier)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Full-screen stellar spectrum",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = viewScale
                            scaleY = viewScale
                            translationX = panX
                            translationY = panY
                        },
                    contentScale = ContentScale.Fit
                )

                Canvas(Modifier.fillMaxSize()) {
                    val red = redPoint?.let {
                        imageToScreen(it, boxSize, bitmap, viewScale, Offset(panX, panY))
                    }
                    val violet = violetPoint?.let {
                        imageToScreen(it, boxSize, bitmap, viewScale, Offset(panX, panY))
                    }

                    if (red != null && violet != null) {
                        drawLine(Color.White.copy(alpha = 0.88f), red, violet, strokeWidth = 3f)
                    }

                    red?.let {
                        drawCircle(Color.Black.copy(alpha = 0.68f), radius = 17f, center = it)
                        drawCircle(redColor, radius = 11f, center = it)
                        drawCircle(Color.White, radius = 4f, center = it)
                    }

                    violet?.let {
                        drawCircle(Color.Black.copy(alpha = 0.68f), radius = 17f, center = it)
                        drawCircle(violetColor, radius = 11f, center = it)
                        drawCircle(Color.White, radius = 4f, center = it)
                    }

                    landmarks.values.forEach { landmark ->
                        val definition =
                            ColorTransitionLandmarks
                                .definition(landmark.id)
                                ?: return@forEach
                        val screen =
                            imageToScreen(
                                Offset(
                                    landmark.x,
                                    landmark.y
                                ),
                                boxSize,
                                bitmap,
                                viewScale,
                                Offset(
                                    panX,
                                    panY
                                )
                            )
                                ?: return@forEach

                        drawCircle(
                            Color.Black.copy(alpha = 0.72f),
                            radius = 14f,
                            center = screen
                        )
                        drawCircle(
                            definition.accent,
                            radius = 8.5f,
                            center = screen
                        )
                        drawCircle(
                            Color.White,
                            radius = 3f,
                            center = screen
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    shape = RoundedCornerShape(18.dp),
                    color = Color.Black.copy(alpha = 0.78f)
                ) {
                    Column(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val instruction =
                            when {
                                navigationMode ->
                                    "PAN / ZOOM"

                                activeTarget == "red" &&
                                    redPoint == null ->
                                    "Place RED end · hold, position, keep still"

                                activeTarget == "violet" &&
                                    violetPoint == null ->
                                    "Place VIOLET end · hold, position, keep still"

                                activeTarget == "red" ->
                                    "Edit RED end · hold, position, keep still"

                                activeTarget == "violet" ->
                                    "Edit VIOLET end · hold, position, keep still"

                                landmarks[activeTarget] == null ->
                                    "Optional · place ${targetLabel(activeTarget)} boundary"

                                else ->
                                    "Edit ${targetLabel(activeTarget)} boundary"
                            }
                        Text(
                            instruction,
                            color = targetColor(activeTarget),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (navigationMode) {
                                if (allowColorBoundaries) {
                                    "Drag to pan · pinch to zoom · choose any landmark below to place or revise"
                                } else {
                                    "Drag to pan · pinch to zoom · choose RED or VIOLET to place or revise"
                                }
                            } else if (
                                activeTarget == "red" ||
                                activeTarget == "violet"
                            ) {
                                "RED and VIOLET are required · 3 px tremor deadband · stationary for 3 seconds locks"
                            } else {
                                "Colour boundaries are optional soft priors · 3 px tremor deadband · stationary for 3 seconds locks"
                            },
                            color = Color.White.copy(alpha = 0.74f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (magnifying) {
                    magnifierPoint?.let { point ->
                        SpectrumMagnifier(
                            bitmap = bitmap,
                            point = point,
                            accent = targetColor(activeTarget),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 92.dp, end = 16.dp)
                        )
                    }
                }

                if (!navigationMode && (magnifying || lockFeedback != null)) {
                    val remaining = (3.0 * (1.0 - lockProgress.coerceIn(0f, 1f))).coerceAtLeast(0.0)
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 274.dp, end = 16.dp)
                            .width(172.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Black.copy(alpha = 0.86f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (lockFeedback != null) Color(0xFF66D19E)
                            else targetColor(activeTarget)
                        )
                    ) {
                        Column(
                            Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                lockFeedback ?: "Hold steady  ${"%.1f".format(Locale.US, remaining)} s",
                                color = if (lockFeedback != null) Color(0xFF8EE6B8) else Color.White,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(2.dp))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(lockProgress.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .background(
                                            if (lockFeedback != null) Color(0xFF66D19E)
                                            else targetColor(activeTarget),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.84f)
                ) {
                    Column(
                        Modifier
                            .navigationBarsPadding()
                            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                ),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    7.dp
                                )
                        ) {
                            if (navigationMode) {
                                Button(
                                    onClick = {
                                        navigationMode =
                                            true
                                    }
                                ) {
                                    Text("PAN / ZOOM")
                                }
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        navigationMode =
                                            true
                                        magnifying = false
                                        stableHoldPoint =
                                            null
                                        magnifierPoint =
                                            null
                                        lockProgress =
                                            0f
                                    }
                                ) {
                                    Text("PAN / ZOOM")
                                }
                            }

                            fun selectTarget(
                                target: String
                            ) {
                                navigationMode =
                                    false
                                activeTarget =
                                    target
                                lockFeedback =
                                    null
                                lockProgress =
                                    0f

                                if (
                                    target != "red" &&
                                    target != "violet"
                                ) {
                                    boundaryXText =
                                        landmarks[target]
                                            ?.x
                                            ?.let {
                                                String.format(
                                                    Locale.US,
                                                    "%.1f",
                                                    it
                                                )
                                            }
                                            ?: ""
                                    boundaryYText =
                                        landmarks[target]
                                            ?.y
                                            ?.let {
                                                String.format(
                                                    Locale.US,
                                                    "%.1f",
                                                    it
                                                )
                                            }
                                            ?: ""
                                }
                            }

                            listOf(
                                "red" to "RED",
                                "violet" to "VIOLET"
                            ).forEach {
                                    pair ->
                                val target =
                                    pair.first
                                val label =
                                    pair.second
                                val placed =
                                    if (
                                        target ==
                                        "red"
                                    ) {
                                        redPoint != null
                                    } else {
                                        violetPoint != null
                                    }

                                if (
                                    !navigationMode &&
                                    activeTarget ==
                                    target
                                ) {
                                    Button(
                                        onClick = {
                                            selectTarget(
                                                target
                                            )
                                        }
                                    ) {
                                        Text(
                                            label +
                                                if (
                                                    placed
                                                ) {
                                                    " ✓"
                                                } else {
                                                    ""
                                                }
                                        )
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            selectTarget(
                                                target
                                            )
                                        }
                                    ) {
                                        Text(
                                            label +
                                                if (
                                                    placed
                                                ) {
                                                    " ✓"
                                                } else {
                                                    ""
                                                }
                                        )
                                    }
                                }
                            }

                            if (allowColorBoundaries) {
                                ColorTransitionLandmarks
                                    .definitions
                                    .forEach {
                                            definition ->

                                    val label =
                                        when (
                                            definition.id
                                        ) {
                                            "red_yellow" ->
                                                "R/Y"
                                            "yellow_green" ->
                                                "Y/G"
                                            "green_blue" ->
                                                "G/B"
                                            else ->
                                                "B/V"
                                        }

                                    val placed =
                                        landmarks[
                                            definition.id
                                        ] != null

                                    if (
                                        !navigationMode &&
                                        activeTarget ==
                                        definition.id
                                    ) {
                                        Button(
                                            onClick = {
                                                selectTarget(
                                                    definition.id
                                                )
                                            }
                                        ) {
                                            Text(
                                                label +
                                                    if (
                                                        placed
                                                    ) {
                                                        " ✓"
                                                    } else {
                                                        ""
                                                    }
                                            )
                                        }
                                    } else {
                                        OutlinedButton(
                                            onClick = {
                                                selectTarget(
                                                    definition.id
                                                )
                                            }
                                        ) {
                                            Text(
                                                label +
                                                    if (
                                                        placed
                                                    ) {
                                                        " ✓"
                                                    } else {
                                                        ""
                                                    }
                                            )
                                        }
                                    }
                                }
                            }

                        }

                        if (
                            allowColorBoundaries &&
                            redPoint != null &&
                            violetPoint != null
                        ) {
                            OutlinedButton(
                                onClick = {
                                    proposeColorBoundaries(
                                        automatic = false
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    if (landmarks.isEmpty()) {
                                        "Propose colour boundaries"
                                    } else {
                                        "Re-detect colour boundaries"
                                    }
                                )
                            }

                            autoBoundaryFeedback?.let { feedback ->
                                Text(
                                    feedback,
                                    color =
                                        Color.White.copy(
                                            alpha = 0.72f
                                        ),
                                    style =
                                        MaterialTheme.typography
                                            .labelSmall
                                )
                            }
                        }

                        Text(
                            if (allowColorBoundaries) {
                                "Required: RED + VIOLET · optional soft-prior boundaries: R/Y, Y/G, G/B, B/V · placed ${landmarks.size}/4"
                            } else {
                                "Required: RED + VIOLET"
                            },
                            color =
                                Color.White.copy(
                                    alpha = 0.62f
                                ),
                            style =
                                MaterialTheme.typography
                                    .labelSmall
                        )

                        Text(
                            "Image coordinates — x 0–${bitmap.width - 1}, y 0–${bitmap.height - 1}",
                            color =
                                Color.White.copy(
                                    alpha = 0.62f
                                ),
                            style =
                                MaterialTheme.typography
                                    .labelSmall
                        )

                        when (activeTarget) {
                            "red" -> {
                                CoordinateEditorRow(
                                    label = "RED",
                                    labelColor =
                                        redColor,
                                    xText =
                                        redXText,
                                    yText =
                                        redYText,
                                    onXChange = {
                                        redXText = it
                                    },
                                    onYChange = {
                                        redYText = it
                                    },
                                    onApply = {
                                        applyManual(
                                            "red"
                                        )
                                    }
                                )
                            }

                            "violet" -> {
                                CoordinateEditorRow(
                                    label =
                                        "VIOLET",
                                    labelColor =
                                        violetColor,
                                    xText =
                                        violetXText,
                                    yText =
                                        violetYText,
                                    onXChange = {
                                        violetXText = it
                                    },
                                    onYChange = {
                                        violetYText = it
                                    },
                                    onApply = {
                                        applyManual(
                                            "violet"
                                        )
                                    }
                                )
                            }

                            else -> {
                                val definition =
                                    ColorTransitionLandmarks
                                        .definition(
                                            activeTarget
                                        )

                                if (
                                    definition != null
                                ) {
                                    CoordinateEditorRow(
                                        label =
                                            definition.label,
                                        labelColor =
                                            definition.accent,
                                        xText =
                                            boundaryXText,
                                        yText =
                                            boundaryYText,
                                        onXChange = {
                                            boundaryXText =
                                                it
                                        },
                                        onYChange = {
                                            boundaryYText =
                                                it
                                        },
                                        onApply = {
                                            applyManual(
                                                activeTarget
                                            )
                                        }
                                    )

                                    Text(
                                        "Soft prior ≈ ${
                                            "%.0f".format(
                                                Locale.US,
                                                definition
                                                    .priorWavelengthNm
                                            )
                                        } ± ${
                                            "%.0f".format(
                                                Locale.US,
                                                definition
                                                    .priorSigmaNm
                                            )
                                        } nm",
                                        color =
                                            Color.White
                                                .copy(
                                                    alpha =
                                                        0.58f
                                                ),
                                        style =
                                            MaterialTheme
                                                .typography
                                                .labelSmall
                                    )
                                }
                            }
                        }

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement =
                                Arrangement.spacedBy(
                                    8.dp
                                )
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text("Cancel")
                            }

                            OutlinedButton(
                                onClick = {
                                    redPoint = null
                                    violetPoint = null
                                    landmarks =
                                        emptyMap()
                                    redXText = ""
                                    redYText = ""
                                    violetXText = ""
                                    violetYText = ""
                                    boundaryXText = ""
                                    boundaryYText = ""
                                    activeTarget =
                                        "red"
                                    navigationMode =
                                        false
                                    viewScale = 1f
                                    panX = 0f
                                    panY = 0f
                                    lockFeedback = null
                                    autoBoundaryFeedback = null
                                    lockProgress = 0f
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text("Reset")
                            }

                            Button(
                                enabled = canFinish,
                                onClick = {
                                    val red =
                                        redPoint
                                            ?: return@Button
                                    val violet =
                                        violetPoint
                                            ?: return@Button

                                    val selectedTrace =
                                        TraceSelection(
                                            startX = red.x,
                                            startY = red.y,
                                            endX = violet.x,
                                            endY = violet.y
                                        )
                                    val selectedLandmarks =
                                        ColorTransitionLandmarks
                                            .definitions
                                            .mapNotNull {
                                                landmarks[
                                                    it.id
                                                ]
                                            }
                                    val finalLandmarks =
                                        if (
                                            allowColorBoundaries &&
                                            selectedLandmarks.isEmpty()
                                        ) {
                                            // The normal path proposes boundaries as soon as both
                                            // endpoints are locked. Repeat the same shared detector
                                            // synchronously at Done so a fast user cannot outrun the
                                            // LaunchedEffect and accidentally return endpoints only.
                                            autoDetectColorTransitionLandmarks(
                                                bitmap = bitmap,
                                                trace = selectedTrace
                                            )?.landmarks.orEmpty()
                                        } else {
                                            selectedLandmarks
                                        }

                                    onSelected(
                                        selectedTrace,
                                        finalLandmarks
                                    )
                                },
                                modifier =
                                    Modifier.weight(
                                        1f
                                    )
                            ) {
                                Text(
                                    if (
                                        landmarks.isEmpty()
                                    ) {
                                        "Done · no boundaries"
                                    } else {
                                        "Done"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
private fun CoordinateEditorRow(
    label: String,
    labelColor: Color,
    xText: String,
    yText: String,
    onXChange: (String) -> Unit,
    onYChange: (String) -> Unit,
    onApply: () -> Unit
) {
    val valid = xText.toFloatOrNull() != null && yText.toFloatOrNull() != null
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.width(54.dp),
            color = labelColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        OutlinedTextField(
            value = xText,
            onValueChange = onXChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("x") }
        )
        OutlinedTextField(
            value = yText,
            onValueChange = onYChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            label = { Text("y") }
        )
        OutlinedButton(
            onClick = onApply,
            enabled = valid
        ) { Text("Apply") }
    }
}

@Composable
private fun SpectrumMagnifier(
    bitmap: Bitmap,
    point: Offset,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    Surface(
        modifier = modifier
            .width(172.dp)
            .height(172.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color.Black.copy(alpha = 0.92f),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cropSize = 44
            val half = cropSize / 2
            val maxX = (bitmap.width - cropSize).coerceAtLeast(0)
            val maxY = (bitmap.height - cropSize).coerceAtLeast(0)
            val left = (point.x.toInt() - half).coerceIn(0, maxX)
            val top = (point.y.toInt() - half).coerceIn(0, maxY)
            val srcW = cropSize.coerceAtMost(bitmap.width)
            val srcH = cropSize.coerceAtMost(bitmap.height)

            drawImage(
                image = image,
                srcOffset = IntOffset(left, top),
                srcSize = IntSize(srcW, srcH),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )

            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color.Black.copy(alpha = 0.75f), Offset(cx - 24f, cy), Offset(cx + 24f, cy), 5f)
            drawLine(Color.Black.copy(alpha = 0.75f), Offset(cx, cy - 24f), Offset(cx, cy + 24f), 5f)
            drawLine(accent, Offset(cx - 24f, cy), Offset(cx + 24f, cy), 2f)
            drawLine(accent, Offset(cx, cy - 24f), Offset(cx, cy + 24f), 2f)
            drawCircle(Color.White, radius = 4f, center = Offset(cx, cy), style = Stroke(2f))
        }
    }
}

private fun buildExtractedSpectrumStrip(
    bitmap: Bitmap,
    extraction: SpectrumExtraction,
    apertureHalfWidthPx: Int,
    reverseForDisplay: Boolean = true
): Bitmap {
    val sampleCount = extraction.trace.sampleX.size.coerceAtLeast(1)
    val outHeight = 28
    val strip = Bitmap.createBitmap(sampleCount, outHeight, Bitmap.Config.ARGB_8888)

    fun sampleColour(i: Int): Int {
        val x0 = extraction.trace.sampleX[i].toFloat()
        val y0 = extraction.trace.sampleY[i].toFloat()

        val i0 = (i - 1).coerceAtLeast(0)
        val i1 = (i + 1).coerceAtMost(extraction.trace.sampleX.lastIndex)
        val tx = extraction.trace.sampleX[i1] - extraction.trace.sampleX[i0]
        val ty = extraction.trace.sampleY[i1] - extraction.trace.sampleY[i0]
        val len = hypot(tx.toFloat(), ty.toFloat()).coerceAtLeast(1f)
        val nx = -ty.toFloat() / len
        val ny = tx.toFloat() / len

        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0

        for (offset in -apertureHalfWidthPx..apertureHalfWidthPx) {
            val x = (x0 + nx * offset).toInt().coerceIn(0, bitmap.width - 1)
            val y = (y0 + ny * offset).toInt().coerceIn(0, bitmap.height - 1)
            val c = bitmap.getPixel(x, y)
            r += AndroidColor.red(c)
            g += AndroidColor.green(c)
            b += AndroidColor.blue(c)
            n++
        }

        val rr = (r / n.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val gg = (g / n.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        val bb = (b / n.coerceAtLeast(1)).toInt().coerceIn(0, 255)
        return AndroidColor.rgb(rr, gg, bb)
    }

    for (displayX in 0 until sampleCount) {
        val sourceIndex = if (reverseForDisplay) sampleCount - 1 - displayX else displayX
        val colour = sampleColour(sourceIndex.coerceIn(0, extraction.trace.sampleX.lastIndex))
        for (y in 0 until outHeight) strip.setPixel(displayX, y, colour)
    }
    return strip
}

@Composable
internal fun ExtractedSpectrumStrip(
    bitmap: Bitmap,
    extraction: SpectrumExtraction,
    apertureHalfWidthPx: Int,
    modifier: Modifier = Modifier
) {
    val stripBitmap = remember(bitmap, extraction, apertureHalfWidthPx) {
        buildExtractedSpectrumStrip(
            bitmap = bitmap,
            extraction = extraction,
            apertureHalfWidthPx = apertureHalfWidthPx,
            reverseForDisplay = true
        )
    }

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VIOLET",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFB78CFF)
            )
            Text(
                "Extracted spectrum",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "RED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF5E62)
            )
        }

        Spacer(Modifier.height(6.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Image(
                bitmap = stripBitmap.asImageBitmap(),
                contentDescription = "Extracted stellar spectrum from violet to red",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            "Photographic appearance sampled across the extracted aperture. Displayed VIOLET → RED; colour is from the source image and is not radiometrically calibrated.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


internal data class ChromaticBootstrap(
    val wavelengthByIndex: DoubleArray,
    val violetNm: Double,
    val redNm: Double,
    val fitQuality: Double,
    val reliableColourFraction: Double,
    val meanMatchCost: Double,
    val landmarkCount: Int,
    val landmarkAgreement: Double,
    val landmarkWavelengthsNm: DoubleArray
) {
    fun wavelengthAt(index: Int): Double? {
        if (index !in wavelengthByIndex.indices) return null
        val value = wavelengthByIndex[index]
        return value.takeIf { it.isFinite() }
    }

    fun nearestIndex(wavelengthNm: Double): Int? {
        if (wavelengthByIndex.isEmpty()) return null

        var best = -1
        var bestDiff = Double.POSITIVE_INFINITY

        wavelengthByIndex.forEachIndexed { index, value ->
            if (!value.isFinite()) return@forEachIndexed
            val diff = kotlin.math.abs(value - wavelengthNm)
            if (diff < bestDiff) {
                best = index
                bestDiff = diff
            }
        }

        return best.takeIf { it >= 0 }
    }
}

private data class ChromaticPoint(
    val r: Double,
    val g: Double,
    val b: Double,
    val reliability: Double
)

private data class ChromaticDtwResult(
    val wavelengthByObservedPoint: DoubleArray,
    val meanCost: Double
)

private data class ChromaticLandmarkConstraint(
    val observedIndex: Int,
    val wavelengthNm: Double,
    val sigmaNm: Double
)

private fun idealSpectralRgb(wavelengthNm: Double): Triple<Double, Double, Double> {
    val wavelength = wavelengthNm.coerceIn(380.0, 780.0)

    var r = 0.0
    var g = 0.0
    var b = 0.0

    when {
        wavelength < 440.0 -> {
            r = -(wavelength - 440.0) / (440.0 - 380.0)
            b = 1.0
        }

        wavelength < 490.0 -> {
            g = (wavelength - 440.0) / (490.0 - 440.0)
            b = 1.0
        }

        wavelength < 510.0 -> {
            g = 1.0
            b = -(wavelength - 510.0) / (510.0 - 490.0)
        }

        wavelength < 580.0 -> {
            r = (wavelength - 510.0) / (580.0 - 510.0)
            g = 1.0
        }

        wavelength < 645.0 -> {
            r = 1.0
            g = -(wavelength - 645.0) / (645.0 - 580.0)
        }

        else -> {
            r = 1.0
        }
    }

    val attenuation = when {
        wavelength < 420.0 ->
            0.30 + 0.70 * (wavelength - 380.0) / (420.0 - 380.0)

        wavelength <= 700.0 ->
            1.0

        else ->
            0.30 + 0.70 * (780.0 - wavelength) / (780.0 - 700.0)
    }.coerceIn(0.0, 1.0)

    fun gamma(channel: Double): Double {
        if (channel <= 0.0) return 0.0
        return Math.pow(
            (channel * attenuation).coerceIn(0.0, 1.0),
            0.80
        )
    }

    return Triple(
        gamma(r),
        gamma(g),
        gamma(b)
    )
}

private fun normalizedChromaticPoint(
    r: Double,
    g: Double,
    b: Double
): ChromaticPoint {
    val rr = r.coerceAtLeast(0.0)
    val gg = g.coerceAtLeast(0.0)
    val bb = b.coerceAtLeast(0.0)

    val sum = rr + gg + bb
    if (sum <= 1e-9) {
        return ChromaticPoint(
            r = 1.0 / 3.0,
            g = 1.0 / 3.0,
            b = 1.0 / 3.0,
            reliability = 0.0
        )
    }

    val maxChannel = max(rr, max(gg, bb))
    val minChannel = min(rr, min(gg, bb))
    val brightness = (sum / 3.0).coerceIn(0.0, 1.0)
    val saturation = (maxChannel - minChannel).coerceIn(0.0, 1.0)

    val brightnessScore =
        ((brightness - 0.025) / 0.20).coerceIn(0.0, 1.0)
    val saturationScore =
        (saturation / 0.18).coerceIn(0.0, 1.0)

    return ChromaticPoint(
        r = rr / sum,
        g = gg / sum,
        b = bb / sum,
        reliability = (
            0.35 * brightnessScore +
                0.65 * saturationScore
            ).coerceIn(0.0, 1.0)
    )
}

private fun chromaticDistance(
    observed: ChromaticPoint,
    ideal: ChromaticPoint
): Double {
    val dr = observed.r - ideal.r
    val dg = observed.g - ideal.g
    val db = observed.b - ideal.b
    val colourDistance =
        kotlin.math.sqrt(dr * dr + dg * dg + db * db)

    // Dark/desaturated sections are deliberately down-weighted. They still
    // maintain ordering in the DTW path, but cannot dominate the wavelength fit.
    return observed.reliability * colourDistance +
        (1.0 - observed.reliability) * 0.10
}

private fun alignChromaticSequence(
    observed: List<ChromaticPoint>,
    startNm: Double,
    endNm: Double,
    constraints: List<ChromaticLandmarkConstraint> = emptyList()
): ChromaticDtwResult? {
    if (
        observed.size < 4 ||
        !startNm.isFinite() ||
        !endNm.isFinite() ||
        endNm <= startNm
    ) {
        return null
    }

    val idealWavelengths = mutableListOf<Double>()
    var wavelength = startNm
    while (wavelength <= endNm + 1e-9) {
        idealWavelengths += wavelength
        wavelength += 2.0
    }

    if (idealWavelengths.size < 4) return null

    val ideal = idealWavelengths.map { nm ->
        val rgb = idealSpectralRgb(nm)
        normalizedChromaticPoint(
            rgb.first,
            rgb.second,
            rgb.third
        ).copy(reliability = 1.0)
    }

    val constraintsByObservedIndex =
        constraints.groupBy {
            it.observedIndex.coerceIn(
                0,
                observed.lastIndex
            )
        }

    val rows = observed.size + 1
    val cols = ideal.size + 1
    val infinity = Double.POSITIVE_INFINITY

    val dp = DoubleArray(rows * cols) { infinity }
    val parent = ByteArray(rows * cols) { -1 }

    fun index(i: Int, j: Int): Int = i * cols + j

    dp[index(0, 0)] = 0.0

    for (i in 1 until rows) {
        for (j in 1 until cols) {
            val colourCost =
                chromaticDistance(
                    observed[i - 1],
                    ideal[j - 1]
                )

            val wavelengthNm =
                idealWavelengths[j - 1]

            val landmarkPenalty =
                constraintsByObservedIndex[
                    i - 1
                ]
                    .orEmpty()
                    .sumOf { constraint ->
                        val z =
                            (
                                wavelengthNm -
                                    constraint.wavelengthNm
                                ) /
                                constraint.sigmaNm
                                    .coerceAtLeast(
                                        1.0
                                    )

                        // Soft prior, not a hard calibration point. One
                        // user-selected colour boundary can steer the monotonic
                        // warp but cannot overwhelm the complete RGB trajectory.
                        (
                            0.80 *
                                z *
                                z
                            )
                            .coerceAtMost(
                                3.0
                            )
                    }

            val cost =
                colourCost +
                    landmarkPenalty

            val diagonal =
                dp[index(i - 1, j - 1)]

            val sameWavelengthForMorePixels =
                dp[index(i - 1, j)] + 0.035

            val skippedIdealWavelength =
                dp[index(i, j - 1)] + 0.035

            val best = min(
                diagonal,
                min(
                    sameWavelengthForMorePixels,
                    skippedIdealWavelength
                )
            )

            if (!best.isFinite()) continue

            dp[index(i, j)] = best + cost
            parent[index(i, j)] = when (best) {
                diagonal -> 0
                sameWavelengthForMorePixels -> 1
                else -> 2
            }
        }
    }

    if (!dp[index(observed.size, ideal.size)].isFinite()) {
        return null
    }

    val sums = DoubleArray(observed.size)
    val counts = IntArray(observed.size)

    var i = observed.size
    var j = ideal.size
    var pathLength = 0

    while (i > 0 && j > 0) {
        val obsIndex = i - 1
        val idealIndex = j - 1

        sums[obsIndex] += idealWavelengths[idealIndex]
        counts[obsIndex]++
        pathLength++

        when (parent[index(i, j)].toInt()) {
            0 -> {
                i--
                j--
            }

            1 -> {
                i--
            }

            2 -> {
                j--
            }

            else -> break
        }
    }

    val mapping = DoubleArray(observed.size) { idx ->
        if (counts[idx] > 0) {
            sums[idx] / counts[idx]
        } else {
            Double.NaN
        }
    }

    // Fill any unmapped observed samples from neighbouring mapped points.
    var last = Double.NaN
    for (idx in mapping.indices) {
        if (mapping[idx].isFinite()) {
            last = mapping[idx]
        } else if (last.isFinite()) {
            mapping[idx] = last
        }
    }

    last = Double.NaN
    for (idx in mapping.lastIndex downTo 0) {
        if (mapping[idx].isFinite()) {
            last = mapping[idx]
        } else if (last.isFinite()) {
            mapping[idx] = last
        }
    }

    if (mapping.any { !it.isFinite() }) return null

    val smoothed = DoubleArray(mapping.size) { idx ->
        val from = (idx - 2).coerceAtLeast(0)
        val to = (idx + 2).coerceAtMost(mapping.lastIndex)
        var sum = 0.0
        var count = 0
        for (k in from..to) {
            sum += mapping[k]
            count++
        }
        sum / count.coerceAtLeast(1)
    }

    // Preserve monotonic VIOLET -> RED order after smoothing.
    for (idx in 1 until smoothed.size) {
        if (smoothed[idx] < smoothed[idx - 1]) {
            smoothed[idx] = smoothed[idx - 1]
        }
    }

    val meanCost =
        dp[index(observed.size, ideal.size)] /
            pathLength.coerceAtLeast(1)

    return ChromaticDtwResult(
        wavelengthByObservedPoint = smoothed,
        meanCost = meanCost
    )
}

private fun chromaticHueDegrees(point: ChromaticPoint): Double? {
    val maxChannel = max(point.r, max(point.g, point.b))
    val minChannel = min(point.r, min(point.g, point.b))
    val delta = maxChannel - minChannel
    if (!delta.isFinite() || delta <= 1e-6) return null

    val raw = when (maxChannel) {
        point.r -> 60.0 * (((point.g - point.b) / delta) % 6.0)
        point.g -> 60.0 * (((point.b - point.r) / delta) + 2.0)
        else -> 60.0 * (((point.r - point.g) / delta) + 4.0)
    }

    return if (raw < 0.0) raw + 360.0 else raw
}

private fun hueDistanceDegrees(a: Double, b: Double): Double {
    val d = kotlin.math.abs(a - b) % 360.0
    return min(d, 360.0 - d)
}

internal fun autoDetectColorTransitionLandmarks(
    bitmap: Bitmap,
    trace: TraceSelection
): AutoColorBoundaryDetection? {
    if (
        trace.length < 24f ||
        bitmap.width <= 1 ||
        bitmap.height <= 1
    ) {
        return null
    }

    val sampleCount =
        trace.length
            .toInt()
            .coerceIn(
                96,
                220
            )

    val vx = trace.endX
    val vy = trace.endY
    val rx = trace.startX
    val ry = trace.startY
    val dx = rx - vx
    val dy = ry - vy
    val lineLength =
        hypot(
            dx,
            dy
        ).coerceAtLeast(1f)
    val nx = -dy / lineLength
    val ny = dx / lineLength
    val halfWidth = 3

    fun observedAt(index: Int): ChromaticPoint {
        val fraction =
            if (sampleCount <= 1) {
                0f
            } else {
                index.toFloat() /
                    (sampleCount - 1).toFloat()
            }
        val cx = vx + dx * fraction
        val cy = vy + dy * fraction

        var red = 0.0
        var green = 0.0
        var blue = 0.0
        var count = 0

        for (offset in -halfWidth..halfWidth) {
            val x =
                (cx + nx * offset)
                    .toInt()
                    .coerceIn(
                        0,
                        bitmap.width - 1
                    )
            val y =
                (cy + ny * offset)
                    .toInt()
                    .coerceIn(
                        0,
                        bitmap.height - 1
                    )
            val colour =
                bitmap.getPixel(
                    x,
                    y
                )

            red +=
                AndroidColor.red(colour) /
                    255.0
            green +=
                AndroidColor.green(colour) /
                    255.0
            blue +=
                AndroidColor.blue(colour) /
                    255.0
            count++
        }

        val denominator =
            count.coerceAtLeast(1)
                .toDouble()

        return normalizedChromaticPoint(
            red / denominator,
            green / denominator,
            blue / denominator
        )
    }

    val raw =
        List(sampleCount) {
            observedAt(it)
        }

    val observed =
        List(sampleCount) { index ->
            val from =
                (index - 3)
                    .coerceAtLeast(0)
            val to =
                (index + 3)
                    .coerceAtMost(
                        sampleCount - 1
                    )

            var red = 0.0
            var green = 0.0
            var blue = 0.0
            var weight = 0.0
            var reliability = 0.0

            for (j in from..to) {
                val point = raw[j]
                val w =
                    0.20 +
                        0.80 *
                        point.reliability
                red += point.r * w
                green += point.g * w
                blue += point.b * w
                reliability +=
                    point.reliability
                weight += w
            }

            val safeWeight =
                weight.coerceAtLeast(
                    1e-9
                )

            ChromaticPoint(
                r = red / safeWeight,
                g = green / safeWeight,
                b = blue / safeWeight,
                reliability =
                    (
                        reliability /
                            (to - from + 1)
                                .coerceAtLeast(1)
                        ).coerceIn(
                        0.0,
                        1.0
                    )
            )
        }

    val violetCandidates =
        doubleArrayOf(
            390.0,
            400.0,
            410.0,
            420.0
        )
    val redCandidates =
        doubleArrayOf(
            650.0,
            670.0,
            690.0,
            710.0
        )

    var best: ChromaticDtwResult? = null

    for (startNm in violetCandidates) {
        for (endNm in redCandidates) {
            if (
                endNm <=
                startNm + 180.0
            ) {
                continue
            }

            val candidate =
                alignChromaticSequence(
                    observed = observed,
                    startNm = startNm,
                    endNm = endNm
                ) ?: continue

            if (
                best == null ||
                candidate.meanCost <
                best!!.meanCost
            ) {
                best = candidate
            }
        }
    }

    val chosen =
        best ?: return null

    val reliableFraction =
        observed.count {
            it.reliability >= 0.35
        }.toDouble() /
            observed.size
                .coerceAtLeast(1)

    val fitQuality =
        (
            Math.exp(
                -3.0 *
                    chosen.meanCost
            ) *
                kotlin.math.sqrt(
                    reliableFraction
                        .coerceIn(
                            0.0,
                            1.0
                        )
                )
            ).coerceIn(
            0.0,
            1.0
        )

    val suggestions =
        ColorTransitionLandmarks
            .definitions
            .mapNotNull { definition ->
                var bestIndex = -1
                var bestScore =
                    Double.POSITIVE_INFINITY

                for (
                    index in
                    chosen.wavelengthByObservedPoint.indices
                ) {
                    val wavelength =
                        chosen.wavelengthByObservedPoint[
                            index
                        ]
                    if (!wavelength.isFinite()) {
                        continue
                    }

                    val reliability =
                        observed[index]
                            .reliability
                    val wavelengthDistance =
                        kotlin.math.abs(
                            wavelength -
                                definition
                                    .priorWavelengthNm
                        )

                    val score =
                        if (definition.id == "yellow_green") {
                            // Global RGB -> wavelength alignment can stretch the
                            // red/yellow camera response and put Y/G visibly too
                            // far into yellow. Use the observed hue transition as
                            // the dominant image-space cue. 90 degrees is halfway
                            // between canonical yellow (60) and green (120).
                            val hue =
                                chromaticHueDegrees(
                                    observed[index]
                                )
                            if (hue == null) {
                                Double.POSITIVE_INFINITY
                            } else {
                                0.24 * wavelengthDistance +
                                    0.76 * hueDistanceDegrees(hue, 90.0) +
                                    (1.0 - reliability) * 10.0
                            }
                        } else {
                            wavelengthDistance +
                                (1.0 - reliability) * 8.0
                        }

                    if (score < bestScore) {
                        bestScore = score
                        bestIndex = index
                    }
                }

                if (bestIndex < 0) {
                    return@mapNotNull null
                }

                val fraction =
                    if (sampleCount <= 1) {
                        0f
                    } else {
                        bestIndex.toFloat() /
                            (sampleCount - 1)
                                .toFloat()
                    }

                ColorTransitionLandmark(
                    id = definition.id,
                    label = definition.label,
                    x = vx + dx * fraction,
                    y = vy + dy * fraction,
                    priorWavelengthNm =
                        definition.priorWavelengthNm,
                    priorSigmaNm =
                        definition.priorSigmaNm
                )
            }

    if (suggestions.isEmpty()) {
        return null
    }

    return AutoColorBoundaryDetection(
        landmarks = suggestions,
        fitQuality = fitQuality,
        reliableColourFraction =
            reliableFraction,
        meanMatchCost =
            chosen.meanCost
    )
}

internal fun buildChromaticBootstrap(
    bitmap: Bitmap,
    extraction: SpectrumExtraction,
    apertureHalfWidthPx: Int,
    landmarks: List<ColorTransitionLandmark> = emptyList()
): ChromaticBootstrap? {
    val sampleCount = extraction.trace.sampleX.size
    if (
        sampleCount < 24 ||
        extraction.trace.sampleY.size != sampleCount
    ) {
        return null
    }

    fun observedAtDisplayIndex(displayIndex: Int): ChromaticPoint {
        val sourceIndex =
            (sampleCount - 1 - displayIndex)
                .coerceIn(0, sampleCount - 1)

        val x0 =
            extraction.trace.sampleX[sourceIndex]
                .toFloat()
        val y0 =
            extraction.trace.sampleY[sourceIndex]
                .toFloat()

        val i0 = (sourceIndex - 1).coerceAtLeast(0)
        val i1 = (sourceIndex + 1).coerceAtMost(sampleCount - 1)
        val tx =
            extraction.trace.sampleX[i1] -
                extraction.trace.sampleX[i0]
        val ty =
            extraction.trace.sampleY[i1] -
                extraction.trace.sampleY[i0]
        val len =
            hypot(tx.toFloat(), ty.toFloat())
                .coerceAtLeast(1f)
        val nx = -ty.toFloat() / len
        val ny = tx.toFloat() / len

        var r = 0.0
        var g = 0.0
        var b = 0.0
        var count = 0

        val width = apertureHalfWidthPx.coerceAtLeast(1)

        for (offset in -width..width) {
            val x =
                (x0 + nx * offset)
                    .toInt()
                    .coerceIn(0, bitmap.width - 1)
            val y =
                (y0 + ny * offset)
                    .toInt()
                    .coerceIn(0, bitmap.height - 1)
            val colour = bitmap.getPixel(x, y)

            r += AndroidColor.red(colour) / 255.0
            g += AndroidColor.green(colour) / 255.0
            b += AndroidColor.blue(colour) / 255.0
            count++
        }

        val n = count.coerceAtLeast(1).toDouble()

        return normalizedChromaticPoint(
            r / n,
            g / n,
            b / n
        )
    }

    val fullObserved =
        List(sampleCount) { displayIndex ->
            observedAtDisplayIndex(displayIndex)
        }

    // Smooth chromaticity very lightly along the dispersion axis. This is
    // deliberately broader than a single JPEG pixel but much narrower than
    // the Balmer features we later refine spectroscopically.
    val smoothedObserved =
        List(sampleCount) { index ->
            val from = (index - 3).coerceAtLeast(0)
            val to = (index + 3).coerceAtMost(sampleCount - 1)

            var r = 0.0
            var g = 0.0
            var b = 0.0
            var weight = 0.0
            var reliability = 0.0

            for (j in from..to) {
                val point = fullObserved[j]
                val w = 0.20 + 0.80 * point.reliability
                r += point.r * w
                g += point.g * w
                b += point.b * w
                reliability += point.reliability
                weight += w
            }

            val safeWeight = weight.coerceAtLeast(1e-9)
            ChromaticPoint(
                r = r / safeWeight,
                g = g / safeWeight,
                b = b / safeWeight,
                reliability =
                    (reliability / (to - from + 1).coerceAtLeast(1))
                        .coerceIn(0.0, 1.0)
            )
        }

    val observedCount =
        min(140, sampleCount)
            .coerceAtLeast(24)

    val sampledDisplayIndices =
        IntArray(observedCount) { i ->
            if (observedCount <= 1) {
                0
            } else {
                (
                    i.toDouble() *
                        (sampleCount - 1).toDouble() /
                        (observedCount - 1).toDouble()
                    ).toInt().coerceIn(0, sampleCount - 1)
            }
        }

    val observed =
        sampledDisplayIndices.map {
            smoothedObserved[it]
        }

    fun nearestSourceIndex(
        landmark: ColorTransitionLandmark
    ): Int {
        var best = 0
        var bestDistanceSquared =
            Double.POSITIVE_INFINITY

        for (index in 0 until sampleCount) {
            val dx =
                extraction.trace.sampleX[index] -
                    landmark.x
            val dy =
                extraction.trace.sampleY[index] -
                    landmark.y
            val distanceSquared =
                dx * dx + dy * dy

            if (
                distanceSquared <
                bestDistanceSquared
            ) {
                best = index
                bestDistanceSquared =
                    distanceSquared
            }
        }

        return best
    }

    val landmarkConstraints =
        landmarks.mapNotNull { landmark ->
            if (
                !landmark.x.isFinite() ||
                !landmark.y.isFinite()
            ) {
                return@mapNotNull null
            }

            val sourceIndex =
                nearestSourceIndex(
                    landmark
                )

            val displayIndex =
                sampleCount -
                    1 -
                    sourceIndex

            val observedIndex =
                if (
                    sampleCount <= 1 ||
                    observedCount <= 1
                ) {
                    0
                } else {
                    kotlin.math.round(
                        displayIndex.toDouble() /
                            (sampleCount - 1)
                                .toDouble() *
                            (observedCount - 1)
                                .toDouble()
                    )
                        .toInt()
                        .coerceIn(
                            0,
                            observedCount - 1
                        )
                }

            ChromaticLandmarkConstraint(
                observedIndex =
                    observedIndex,
                wavelengthNm =
                    landmark.priorWavelengthNm,
                sigmaNm =
                    landmark.priorSigmaNm
            )
        }

    val violetCandidates =
        doubleArrayOf(390.0, 400.0, 410.0, 420.0)
    val redCandidates =
        doubleArrayOf(650.0, 670.0, 690.0, 710.0)

    var bestStart = Double.NaN
    var bestEnd = Double.NaN
    var best: ChromaticDtwResult? = null

    for (startNm in violetCandidates) {
        for (endNm in redCandidates) {
            if (endNm <= startNm + 180.0) continue

            val result =
                alignChromaticSequence(
                    observed = observed,
                    startNm = startNm,
                    endNm = endNm,
                    constraints =
                        landmarkConstraints
                ) ?: continue

            val currentBest = best
            if (
                currentBest == null ||
                result.meanCost < currentBest.meanCost
            ) {
                best = result
                bestStart = startNm
                bestEnd = endNm
            }
        }
    }

    val chosen = best ?: return null

    val displayWavelengths =
        DoubleArray(sampleCount)

    for (displayIndex in 0 until sampleCount) {
        val position =
            if (sampleCount <= 1) {
                0.0
            } else {
                displayIndex.toDouble() /
                    (sampleCount - 1).toDouble() *
                    (observedCount - 1).toDouble()
            }

        val lo =
            kotlin.math.floor(position)
                .toInt()
                .coerceIn(0, observedCount - 1)
        val hi =
            kotlin.math.ceil(position)
                .toInt()
                .coerceIn(0, observedCount - 1)
        val fraction = position - lo

        val lowValue =
            chosen.wavelengthByObservedPoint[lo]
        val highValue =
            chosen.wavelengthByObservedPoint[hi]

        displayWavelengths[displayIndex] =
            lowValue * (1.0 - fraction) +
                highValue * fraction
    }

    for (index in 1 until displayWavelengths.size) {
        if (displayWavelengths[index] < displayWavelengths[index - 1]) {
            displayWavelengths[index] =
                displayWavelengths[index - 1]
        }
    }

    val wavelengthByIndex =
        DoubleArray(sampleCount)

    for (displayIndex in 0 until sampleCount) {
        val sourceIndex =
            sampleCount - 1 - displayIndex

        wavelengthByIndex[sourceIndex] =
            displayWavelengths[displayIndex]
    }

    val reliableColourFraction =
        smoothedObserved.count {
            it.reliability >= 0.35
        }.toDouble() / sampleCount

    val landmarkAgreement =
        if (landmarks.isEmpty()) {
            1.0
        } else {
            val zSquared =
                landmarks.mapNotNull { landmark ->
                    val sourceIndex =
                        nearestSourceIndex(
                            landmark
                        )

                    val fittedNm =
                        wavelengthByIndex
                            .getOrNull(
                                sourceIndex
                            )
                            ?: return@mapNotNull null

                    if (!fittedNm.isFinite()) {
                        return@mapNotNull null
                    }

                    val z =
                        (
                            fittedNm -
                                landmark
                                    .priorWavelengthNm
                            ) /
                            landmark
                                .priorSigmaNm
                                .coerceAtLeast(
                                    1.0
                                )

                    z * z
                }

            if (zSquared.isEmpty()) {
                0.0
            } else {
                kotlin.math.exp(
                    -0.5 *
                        zSquared.average()
                )
                    .coerceIn(
                        0.0,
                        1.0
                    )
            }
        }

    val colourFit =
        Math.exp(-3.0 * chosen.meanCost)
            .coerceIn(0.0, 1.0)

    val fitQuality =
        (
            colourFit *
                kotlin.math.sqrt(
                    reliableColourFraction
                        .coerceIn(
                            0.0,
                            1.0
                        )
                ) *
                if (landmarks.isEmpty()) {
                    1.0
                } else {
                    0.65 +
                        0.35 *
                            landmarkAgreement
                }
            ).coerceIn(
                0.0,
                1.0
            )

    return ChromaticBootstrap(
        wavelengthByIndex = wavelengthByIndex,
        violetNm =
            displayWavelengths.firstOrNull()
                ?: bestStart,
        redNm =
            displayWavelengths.lastOrNull()
                ?: bestEnd,
        fitQuality = fitQuality,
        reliableColourFraction =
            reliableColourFraction,
        meanMatchCost = chosen.meanCost,
        landmarkCount =
            landmarks.size,
        landmarkAgreement =
            landmarkAgreement,
        landmarkWavelengthsNm =
            landmarks
                .map {
                    it.priorWavelengthNm
                }
                .toDoubleArray()
    )
}

private fun buildIdealChromaticStrip(
    bootstrap: ChromaticBootstrap,
    width: Int
): Bitmap {
    val safeWidth = width.coerceAtLeast(2)
    val height = 22
    val bitmap =
        Bitmap.createBitmap(
            safeWidth,
            height,
            Bitmap.Config.ARGB_8888
        )

    for (displayX in 0 until safeWidth) {
        val sourceIndex =
            bootstrap.wavelengthByIndex.lastIndex -
                (
                    displayX.toDouble() /
                        (safeWidth - 1).toDouble() *
                        bootstrap.wavelengthByIndex.lastIndex
                    ).toInt()

        val wavelength =
            bootstrap.wavelengthByIndex[
                sourceIndex.coerceIn(
                    0,
                    bootstrap.wavelengthByIndex.lastIndex
                )
            ]

        val rgb = idealSpectralRgb(wavelength)

        val colour =
            AndroidColor.rgb(
                (rgb.first * 255.0)
                    .toInt()
                    .coerceIn(0, 255),
                (rgb.second * 255.0)
                    .toInt()
                    .coerceIn(0, 255),
                (rgb.third * 255.0)
                    .toInt()
                    .coerceIn(0, 255)
            )

        for (y in 0 until height) {
            bitmap.setPixel(
                displayX,
                y,
                colour
            )
        }
    }

    return bitmap
}

@Composable
internal fun ChromaticBootstrapPanel(
    bitmap: Bitmap,
    extraction: SpectrumExtraction,
    apertureHalfWidthPx: Int,
    bootstrap: ChromaticBootstrap,
    landmarks: List<ColorTransitionLandmark> = emptyList(),
    modifier: Modifier = Modifier
) {
    val observed =
        remember(
            bitmap,
            extraction,
            apertureHalfWidthPx
        ) {
            buildExtractedSpectrumStrip(
                bitmap = bitmap,
                extraction = extraction,
                apertureHalfWidthPx = apertureHalfWidthPx,
                reverseForDisplay = true
            )
        }

    val ideal =
        remember(bootstrap, extraction) {
            buildIdealChromaticStrip(
                bootstrap = bootstrap,
                width =
                    extraction.trace.sampleX.size
                        .coerceAtLeast(2)
            )
        }

    val balmerSearchWindows =
        remember(
            bootstrap,
            extraction
        ) {
            chromaticBalmerSearchWindows(
                bootstrap = bootstrap,
                extraction = extraction,
                anchors = emptyList()
            )
        }

    val landmarkDisplayFractions =
        remember(
            landmarks,
            extraction
        ) {
            landmarks.mapNotNull { landmark ->
                if (
                    extraction.trace.sampleX.isEmpty()
                ) {
                    return@mapNotNull null
                }

                var bestIndex = 0
                var bestDistanceSquared =
                    Double.POSITIVE_INFINITY

                extraction.trace.sampleX
                    .indices
                    .forEach { index ->
                        val dx =
                            extraction.trace
                                .sampleX[index] -
                                landmark.x
                        val dy =
                            extraction.trace
                                .sampleY[index] -
                                landmark.y
                        val distanceSquared =
                            dx * dx + dy * dy

                        if (
                            distanceSquared <
                            bestDistanceSquared
                        ) {
                            bestIndex = index
                            bestDistanceSquared =
                                distanceSquared
                        }
                    }

                val displayFraction =
                    if (
                        extraction.trace.sampleX
                            .size <= 1
                    ) {
                        0.0
                    } else {
                        1.0 -
                            bestIndex.toDouble() /
                                extraction.trace
                                    .sampleX
                                    .lastIndex
                                    .toDouble()
                    }

                landmark to
                    displayFraction
                        .coerceIn(
                            0.0,
                            1.0
                        )
            }
        }

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                label = "Chromatic fit",
                value = "%.0f%%".format(
                    Locale.US,
                    bootstrap.fitQuality * 100.0
                ),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                label = "Useful colour",
                value = "%.0f%%".format(
                    Locale.US,
                    bootstrap.reliableColourFraction * 100.0
                ),
                modifier = Modifier.weight(1f)
            )
        }

        if (bootstrap.landmarkCount > 0) {
            MetricTile(
                label = "Boundary agreement",
                value = "%.0f%% · %d landmark%s".format(
                    Locale.US,
                    bootstrap.landmarkAgreement * 100.0,
                    bootstrap.landmarkCount,
                    if (bootstrap.landmarkCount == 1) "" else "s"
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Text(
            "Observed photographic colour",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(
                Modifier.fillMaxSize()
            ) {
                Image(
                    bitmap =
                        observed.asImageBitmap(),
                    contentDescription =
                        "Observed extracted spectrum colour",
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.FillBounds
                )

                Canvas(
                    Modifier.fillMaxSize()
                ) {
                    if (
                        extraction.trace.sampleX.size >
                        1
                    ) {
                        balmerSearchWindows
                            .forEach { window ->
                                val lastIndex =
                                    extraction.trace
                                        .sampleX
                                        .lastIndex
                                        .toFloat()

                                val f1 =
                                    1f -
                                        window
                                            .lowIndex
                                            .toFloat() /
                                            lastIndex
                                val f2 =
                                    1f -
                                        window
                                            .highIndex
                                            .toFloat() /
                                            lastIndex

                                val x1 =
                                    size.width *
                                        f1.coerceIn(
                                            0f,
                                            1f
                                        )
                                val x2 =
                                    size.width *
                                        f2.coerceIn(
                                            0f,
                                            1f
                                        )

                                drawRect(
                                    color =
                                        balmerAccentColor(
                                            window.label
                                        )
                                            .copy(
                                                alpha =
                                                    0.18f
                                            ),
                                    topLeft =
                                        Offset(
                                            min(
                                                x1,
                                                x2
                                            ),
                                            0f
                                        ),
                                    size =
                                        androidx.compose.ui.geometry.Size(
                                            kotlin.math.abs(
                                                x2 -
                                                    x1
                                            )
                                                .coerceAtLeast(
                                                    2f
                                                ),
                                            size.height
                                        )
                                )

                                val predictedFraction =
                                    1f -
                                        window
                                            .predictedIndex
                                            .toFloat() /
                                            lastIndex
                                val predictedX =
                                    size.width *
                                        predictedFraction
                                            .coerceIn(
                                                0f,
                                                1f
                                            )

                                drawLine(
                                    balmerAccentColor(
                                        window.label
                                    ),
                                    Offset(
                                        predictedX,
                                        0f
                                    ),
                                    Offset(
                                        predictedX,
                                        size.height
                                    ),
                                    2f
                                )
                            }
                    }

                    landmarkDisplayFractions
                        .forEach {
                                pair ->
                            val landmark =
                                pair.first
                            val fraction =
                                pair.second
                                    .toFloat()
                            val definition =
                                ColorTransitionLandmarks
                                    .definition(
                                        landmark.id
                                    )
                                    ?: return@forEach
                            val x =
                                size.width *
                                    fraction

                            drawLine(
                                Color.Black
                                    .copy(
                                        alpha = 0.78f
                                    ),
                                Offset(
                                    x,
                                    0f
                                ),
                                Offset(
                                    x,
                                    size.height
                                ),
                                5f
                            )
                            drawLine(
                                definition
                                    .accent,
                                Offset(
                                    x,
                                    0f
                                ),
                                Offset(
                                    x,
                                    size.height
                                ),
                                2.5f
                            )
                        }
                }
            }
        }

        if (balmerSearchWindows.isNotEmpty()) {
            Text(
                "Colour-derived Balmer search windows",
                style =
                    MaterialTheme.typography
                        .labelMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState()
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(
                        8.dp
                    )
            ) {
                balmerSearchWindows
                    .sortedBy {
                        when (it.label) {
                            "Hδ" -> 0
                            "Hγ" -> 1
                            "Hβ" -> 2
                            else -> 3
                        }
                    }
                    .forEach { window ->
                        Surface(
                            shape =
                                RoundedCornerShape(
                                    10.dp
                                ),
                            color =
                                balmerAccentColor(
                                    window.label
                                )
                                    .copy(
                                        alpha =
                                            0.12f
                                    ),
                            border =
                                androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    balmerAccentColor(
                                        window.label
                                    )
                                        .copy(
                                            alpha =
                                                0.55f
                                        )
                                )
                        ) {
                            Text(
                                "${window.label} ${
                                    "%.0f".format(
                                        Locale.US,
                                        window.wavelengthNm
                                    )
                                } ±${
                                    "%.0f".format(
                                        Locale.US,
                                        window.uncertaintyNm
                                    )
                                } nm · ${window.widthClass}",
                                modifier =
                                    Modifier.padding(
                                        horizontal =
                                            10.dp,
                                        vertical =
                                            6.dp
                                    ),
                                style =
                                    MaterialTheme
                                        .typography
                                        .labelSmall,
                                fontWeight =
                                    FontWeight.SemiBold
                            )
                        }
                    }
            }
        }

        Text(
            "Ideal colour locus after monotonic alignment",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Box(
                Modifier.fillMaxSize()
            ) {
                Image(
                    bitmap =
                        ideal.asImageBitmap(),
                    contentDescription =
                        "Ideal visible spectrum aligned to observed colour",
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.FillBounds
                )

                Canvas(
                    Modifier.fillMaxSize()
                ) {
                    landmarks.forEach {
                            landmark ->
                        val sourceIndex =
                            bootstrap.nearestIndex(
                                landmark
                                    .priorWavelengthNm
                            )
                                ?: return@forEach

                        val displayFraction =
                            if (
                                bootstrap
                                    .wavelengthByIndex
                                    .size <= 1
                            ) {
                                0f
                            } else {
                                1f -
                                    sourceIndex
                                        .toFloat() /
                                        bootstrap
                                            .wavelengthByIndex
                                            .lastIndex
                                            .toFloat()
                            }

                        val definition =
                            ColorTransitionLandmarks
                                .definition(
                                    landmark.id
                                )
                                ?: return@forEach

                        val x =
                            size.width *
                                displayFraction
                                    .coerceIn(
                                        0f,
                                        1f
                                    )

                        drawLine(
                            Color.Black
                                .copy(
                                    alpha = 0.78f
                                ),
                            Offset(
                                x,
                                0f
                            ),
                            Offset(
                                x,
                                size.height
                            ),
                            5f
                        )
                        drawLine(
                            definition.accent,
                            Offset(
                                x,
                                0f
                            ),
                            Offset(
                                x,
                                size.height
                            ),
                            2.5f
                        )
                    }
                }
            }
        }

        val wavelengthTicks =
            listOf(0.0, 0.25, 0.50, 0.75, 1.0)
                .map { fraction ->
                    val displayIndex =
                        (
                            fraction *
                                bootstrap.wavelengthByIndex.lastIndex
                            ).toInt()
                            .coerceIn(
                                0,
                                bootstrap.wavelengthByIndex.lastIndex
                            )

                    val sourceIndex =
                        bootstrap.wavelengthByIndex.lastIndex -
                            displayIndex

                    bootstrap.wavelengthByIndex[sourceIndex]
                }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            wavelengthTicks.forEach { wavelengthNm ->
                Text(
                    "%.0f".format(
                        Locale.US,
                        wavelengthNm
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Text(
            "nm · coarse colour-derived prior only",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (landmarks.isNotEmpty()) {
            Text(
                landmarks.joinToString("   ") {
                    "${it.label} ≈ ${
                        "%.0f".format(
                            Locale.US,
                            it.priorWavelengthNm
                        )
                    }±${
                        "%.0f".format(
                            Locale.US,
                            it.priorSigmaNm
                        )
                    } nm"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val guideStrong =
            (
                bootstrap.fitQuality >= 0.25 &&
                    bootstrap.reliableColourFraction >= 0.15
                ) ||
                (
                    bootstrap.landmarkCount >= 2 &&
                        bootstrap.landmarkAgreement >= 0.50 &&
                        bootstrap.reliableColourFraction >= 0.10
                    )

        if (!guideStrong) {
            Text(
                "Chromatic fit is too weak to drive Balmer guide positions automatically; it is shown for QA only.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            if (landmarks.isEmpty()) {
                "The colour trajectory is matched monotonically to an approximate visible spectral-colour locus. Brightness is removed by RGB chromaticity and dark/desaturated samples are down-weighted. This constrains where Balmer lines should be searched; confirmed absorption lines remain the final wavelength calibration."
            } else {
                "The continuous RGB trajectory is combined with your colour-transition boundaries as broad soft wavelength priors. The fitter can still move each boundary within its stated uncertainty. Confirmed Balmer absorption lines remain the final wavelength calibration."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


private fun balmerAccentColor(
    label: String
): Color =
    when (label) {
        "Hα" -> Color(0xFFFF6B6B)
        "Hβ" -> Color(0xFF6FD7FF)
        "Hγ" -> Color(0xFFD29BFF)
        "Hδ" -> Color(0xFFFFC56B)
        else -> Color.White
    }

private data class BalmerSearchWindow(
    val label: String,
    val wavelengthNm: Double,
    val predictedIndex: Int,
    val predictedDistancePx: Double,
    val lowIndex: Int,
    val highIndex: Int,
    val uncertaintyNm: Double,
    val widthClass: String
)

private fun chromaticBalmerSearchWindows(
    bootstrap: ChromaticBootstrap,
    extraction: SpectrumExtraction,
    anchors: List<CalibrationAnchor>
): List<BalmerSearchWindow> {
    if (
        bootstrap.wavelengthByIndex.size !=
        extraction.distancesPx.size ||
        bootstrap.wavelengthByIndex.isEmpty()
    ) {
        return emptyList()
    }

    val landmarkWavelengths =
        bootstrap.landmarkWavelengthsNm
            .filter { it.isFinite() }
            .sorted()

    val minLandmark =
        landmarkWavelengths.minOrNull()
    val maxLandmark =
        landmarkWavelengths.maxOrNull()

    val oneAnchorOffsetNm =
        if (
            anchors.size == 1 &&
            extraction.distancesPx
                .isNotEmpty()
        ) {
            val anchor =
                anchors.first()

            var anchorIndex = 0
            var anchorDistanceError =
                Double.POSITIVE_INFINITY

            extraction.distancesPx
                .forEachIndexed {
                        index,
                        distancePx ->

                    val error =
                        kotlin.math.abs(
                            distancePx -
                                anchor.distancePx
                        )

                    if (
                        error <
                        anchorDistanceError
                    ) {
                        anchorIndex = index
                        anchorDistanceError =
                            error
                    }
                }

            val chromaticAtAnchor =
                bootstrap.wavelengthAt(
                    anchorIndex
                )

            if (
                chromaticAtAnchor != null &&
                chromaticAtAnchor.isFinite()
            ) {
                anchor.wavelengthNm -
                    chromaticAtAnchor
            } else {
                0.0
            }
        } else {
            0.0
        }

    fun uncertaintyFor(
        wavelengthNm: Double
    ): Double {
        if (
            minLandmark == null ||
            maxLandmark == null
        ) {
            val low =
                min(
                    bootstrap.violetNm,
                    bootstrap.redNm
                )
            val high =
                max(
                    bootstrap.violetNm,
                    bootstrap.redNm
                )
            val span =
                (high - low)
                    .coerceAtLeast(1.0)
            val centre =
                (low + high) / 2.0
            val edgeFraction =
                (
                    kotlin.math.abs(
                        wavelengthNm - centre
                    ) /
                        (span / 2.0)
                    )
                    .coerceIn(
                        0.0,
                        1.5
                    )

            return (
                13.0 +
                    13.0 *
                        edgeFraction
                )
                .coerceIn(
                    12.0,
                    32.0
                )
        }

        return when {
            wavelengthNm < minLandmark -> {
                val extrapolation =
                    minLandmark -
                        wavelengthNm
                (
                    14.0 +
                        0.35 *
                            extrapolation
                    )
                    .coerceIn(
                        15.0,
                        30.0
                    )
            }

            wavelengthNm > maxLandmark -> {
                val extrapolation =
                    wavelengthNm -
                        maxLandmark
                (
                    16.0 +
                        0.35 *
                            extrapolation
                    )
                    .coerceIn(
                        17.0,
                        38.0
                    )
            }

            else -> {
                val nearest =
                    landmarkWavelengths
                        .minOf {
                            kotlin.math.abs(
                                it -
                                    wavelengthNm
                            )
                        }

                (
                    10.0 +
                        0.15 *
                            nearest
                    )
                    .coerceIn(
                        10.0,
                        18.0
                    )
            }
        }
    }

    fun adjustedUncertainty(
        wavelengthNm: Double
    ): Double {
        val base =
            uncertaintyFor(
                wavelengthNm
            )

        return if (anchors.size == 1) {
            (
                base *
                    0.85
                )
                .coerceAtLeast(
                    8.0
                )
        } else {
            base
        }
    }

    fun nearestIndex(
        wavelengthNm: Double
    ): Int? =
        bootstrap.nearestIndex(
            wavelengthNm -
                oneAnchorOffsetNm
        )

    return HydrogenBalmerLines.standard
        .filter { line ->
            anchors.none {
                it.label ==
                    line.label
            }
        }
        .mapNotNull { line ->
            val predictedIndex =
                nearestIndex(
                    line.wavelengthNm
                )
                    ?: return@mapNotNull null

            val uncertaintyNm =
                adjustedUncertainty(
                    line.wavelengthNm
                )

            val lowWavelength =
                line.wavelengthNm -
                    uncertaintyNm
            val highWavelength =
                line.wavelengthNm +
                    uncertaintyNm

            val lowCandidate =
                nearestIndex(
                    highWavelength
                )
                    ?: predictedIndex
            val highCandidate =
                nearestIndex(
                    lowWavelength
                )
                    ?: predictedIndex

            val lowIndex =
                min(
                    lowCandidate,
                    highCandidate
                )
                    .coerceIn(
                        0,
                        extraction.distancesPx
                            .lastIndex
                    )
            val highIndex =
                max(
                    lowCandidate,
                    highCandidate
                )
                    .coerceIn(
                        0,
                        extraction.distancesPx
                            .lastIndex
                    )

            val minimumHalfWidth =
                when (line.label) {
                    "Hβ",
                    "Hγ" -> 12

                    else -> 20
                }

            val adjustedLow =
                min(
                    lowIndex,
                    (
                        predictedIndex -
                            minimumHalfWidth
                        )
                        .coerceAtLeast(
                            0
                        )
                )
            val adjustedHigh =
                max(
                    highIndex,
                    (
                        predictedIndex +
                            minimumHalfWidth
                        )
                        .coerceAtMost(
                            extraction.distancesPx
                                .lastIndex
                        )
                )

            val widthClass =
                when {
                    uncertaintyNm <= 14.0 ->
                        "tight"

                    uncertaintyNm <= 22.0 ->
                        "moderate"

                    else ->
                        "broad"
                }

            BalmerSearchWindow(
                label =
                    line.label,
                wavelengthNm =
                    line.wavelengthNm,
                predictedIndex =
                    predictedIndex,
                predictedDistancePx =
                    extraction.distancesPx[
                        predictedIndex
                    ],
                lowIndex =
                    adjustedLow,
                highIndex =
                    adjustedHigh,
                uncertaintyNm =
                    uncertaintyNm,
                widthClass =
                    widthClass
            )
        }
}

private fun chromaticBalmerPredictions(
    bootstrap: ChromaticBootstrap,
    extraction: SpectrumExtraction,
    anchors: List<CalibrationAnchor>
): List<BalmerPrediction> {
    if (
        bootstrap.wavelengthByIndex.size !=
        extraction.distancesPx.size
    ) {
        return emptyList()
    }

    return HydrogenBalmerLines.standard
        .filter { line ->
            anchors.none { it.label == line.label }
        }
        .mapNotNull { line ->
            val index =
                bootstrap.nearestIndex(
                    line.wavelengthNm
                ) ?: return@mapNotNull null

            val estimatedWavelength =
                bootstrap.wavelengthAt(index)
                    ?: return@mapNotNull null

            // If the estimated chromatic range does not actually include the
            // requested line, do not pretend that an endpoint is a prediction.
            if (
                line.wavelengthNm <
                min(
                    bootstrap.violetNm,
                    bootstrap.redNm
                ) - 18.0 ||
                line.wavelengthNm >
                max(
                    bootstrap.violetNm,
                    bootstrap.redNm
                ) + 18.0
            ) {
                return@mapNotNull null
            }

            BalmerPrediction(
                label = line.label,
                wavelengthNm = line.wavelengthNm,
                distancePx =
                    extraction.distancesPx[index],
                index = index
            )
        }
}



private fun buildSpectrumDiagnosticBitmap(
    values: DoubleArray,
    width: Int,
    height: Int,
    rejectedMask: BooleanArray? = null,
    reverseForDisplay: Boolean = true,
    positiveOnly: Boolean = false,
    centerZero: Boolean = false,
    fixedLow: Double? = null,
    fixedHigh: Double? = null,
    thresholdAbs: Double? = null,
    strongThresholdAbs: Double? = null
): Bitmap {
    val safeWidth = width.coerceAtLeast(1)
    val safeHeight = height.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(
        safeWidth,
        safeHeight,
        Bitmap.Config.ARGB_8888
    )

    if (values.isEmpty()) {
        bitmap.eraseColor(AndroidColor.BLACK)
        return bitmap
    }

    val stride = max(1, values.size / 8000)
    val sampled = mutableListOf<Double>()
    var i = 0
    while (i < values.size) {
        val value = values[i]
        if (value.isFinite()) {
            sampled += if (positiveOnly) max(0.0, value) else value
        }
        i += stride
    }

    val sorted = sampled.sorted()

    fun percentile(p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val position = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lo = position.toInt().coerceIn(0, sorted.lastIndex)
        val hi = kotlin.math.ceil(position).toInt().coerceIn(0, sorted.lastIndex)
        val f = position - lo
        return sorted[lo] * (1.0 - f) + sorted[hi] * f
    }

    val low: Double
    val high: Double

    if (fixedLow != null && fixedHigh != null && fixedHigh > fixedLow) {
        low = fixedLow
        high = fixedHigh
    } else if (centerZero) {
        val absSorted = sampled.map { kotlin.math.abs(it) }.sorted()
        val maxAbs =
            if (absSorted.isEmpty()) 1.0
            else {
                val position = 0.985 * (absSorted.size - 1)
                val lo = position.toInt().coerceIn(0, absSorted.lastIndex)
                val hi = kotlin.math.ceil(position).toInt().coerceIn(0, absSorted.lastIndex)
                val f = position - lo
                (absSorted[lo] * (1.0 - f) + absSorted[hi] * f)
                    .coerceAtLeast(1e-12)
            }
        low = -maxAbs
        high = maxAbs
    } else {
        low = if (positiveOnly) 0.0 else percentile(0.02)
        high = percentile(0.985)
    }

    val range = (high - low).coerceAtLeast(1e-12)

    for (displayX in 0 until safeWidth) {
        val sourceX =
            if (reverseForDisplay) safeWidth - 1 - displayX
            else displayX

        for (y in 0 until safeHeight) {
            val sourceIndex = y * safeWidth + sourceX
            val rejected =
                rejectedMask?.getOrNull(sourceIndex) == true

            val raw = values.getOrElse(sourceIndex) { Double.NaN }
            val absRaw = if (raw.isFinite()) kotlin.math.abs(raw) else 0.0

            val colour =
                when {
                    rejected -> {
                        // Rejected by the extractor.
                        AndroidColor.rgb(255, 106, 82)
                    }

                    strongThresholdAbs != null &&
                        raw.isFinite() &&
                        absRaw >= strongThresholdAbs -> {
                        // Statistically extreme but not necessarily rejected on the
                        // final pass: bright magenta distinguishes it from rejection.
                        AndroidColor.rgb(255, 90, 138)
                    }

                    thresholdAbs != null &&
                        raw.isFinite() &&
                        absRaw >= thresholdAbs -> {
                        // Moderate standardized residual.
                        AndroidColor.rgb(255, 217, 90)
                    }

                    else -> {
                        val scaled =
                            if (!raw.isFinite()) 0.5
                            else ((if (positiveOnly) max(0.0, raw) else raw) - low) / range

                        val level =
                            (scaled.coerceIn(0.0, 1.0) * 255.0)
                                .toInt()
                                .coerceIn(0, 255)

                        AndroidColor.rgb(level, level, level)
                    }
                }

            bitmap.setPixel(displayX, y, colour)
        }
    }

    return bitmap
}

@Composable
private fun SpectrumProcessingStage(
    number: String,
    title: String,
    description: String,
    values: DoubleArray,
    diagnostics: SpectrumExtractionDiagnostics,
    rejectedMask: BooleanArray? = null,
    positiveOnly: Boolean = false,
    centerZero: Boolean = false,
    fixedLow: Double? = null,
    fixedHigh: Double? = null,
    thresholdAbs: Double? = null,
    strongThresholdAbs: Double? = null,
    sigmaLegend: Boolean = false
) {
    val image = remember(
        values,
        diagnostics.width,
        diagnostics.height,
        rejectedMask,
        positiveOnly,
        centerZero,
        fixedLow,
        fixedHigh,
        thresholdAbs,
        strongThresholdAbs
    ) {
        buildSpectrumDiagnosticBitmap(
            values = values,
            width = diagnostics.width,
            height = diagnostics.height,
            rejectedMask = rejectedMask,
            reverseForDisplay = true,
            positiveOnly = positiveOnly,
            centerZero = centerZero,
            fixedLow = fixedLow,
            fixedHigh = fixedHigh,
            thresholdAbs = thresholdAbs,
            strongThresholdAbs = strongThresholdAbs
        )
    }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$number · $title",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "VIOLET → RED",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(4.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            shape = RoundedCornerShape(10.dp),
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = "$title extraction diagnostic",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds
            )
        }

        Spacer(Modifier.height(4.dp))

        if (sigmaLegend) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "−5σ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "0σ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "+5σ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "● |z| ≥ 3σ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFFD95A)
                )
                Text(
                    "● |z| ≥ 5σ",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF5A8A)
                )
                Text(
                    "● rejected",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6A52)
                )
            }

            Spacer(Modifier.height(2.dp))
        }

        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OptimalVsBoxcarChart(
    diagnostics: SpectrumExtractionDiagnostics,
    modifier: Modifier = Modifier
) {
    val boxcar = diagnostics.boxcarSignal
    val optimal = diagnostics.optimalSignal

    val finite = (boxcar.asList() + optimal.asList())
        .filter { it.isFinite() }

    val minValue = min(0.0, finite.minOrNull() ?: 0.0)
    val maxValue = (finite.maxOrNull() ?: 1.0).coerceAtLeast(minValue + 1e-9)
    val range = (maxValue - minValue).coerceAtLeast(1e-9)

    Box(
        modifier
            .fillMaxWidth()
            .height(210.dp)
            .background(Color(0xFF090D13), RoundedCornerShape(14.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(14.dp)
            )
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 20.dp)) {
            if (optimal.isEmpty()) return@Canvas

            fun drawSeries(
                values: DoubleArray,
                colour: Color,
                strokeWidth: Float
            ) {
                var previous: Offset? = null
                for (displayIndex in values.indices) {
                    val sourceIndex = values.lastIndex - displayIndex
                    val value = values[sourceIndex]
                    if (!value.isFinite()) {
                        previous = null
                        continue
                    }

                    val x =
                        size.width *
                            displayIndex /
                            max(1, values.lastIndex).toFloat()

                    val y =
                        size.height *
                            (1f -
                                ((value - minValue) / range)
                                    .toFloat()
                                    .coerceIn(0f, 1f))

                    val point = Offset(x, y)
                    previous?.let {
                        drawLine(
                            colour,
                            it,
                            point,
                            strokeWidth
                        )
                    }
                    previous = point
                }
            }

            drawSeries(
                boxcar,
                Color.White.copy(alpha = 0.35f),
                1.5f
            )
            drawSeries(
                optimal,
                Color(0xFF67E4CB),
                2.5f
            )
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "VIOLET",
                color = Color(0xFFB78CFF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "boxcar  ·  optimal",
                color = Color.White.copy(alpha = 0.68f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                "RED",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "white = simple aperture sum · cyan = Horne-style optimal extraction",
                color = Color.White.copy(alpha = 0.52f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
internal fun SpectrumProcessingAudit(
    extraction: SpectrumExtraction,
    modifier: Modifier = Modifier
) {
    val diagnostics = extraction.diagnostics

    Column(
        modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            diagnostics.extractionMethod,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                "Trace fit RMS",
                "%.2f px".format(Locale.US, diagnostics.traceFitRmsPx),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "Rejected pixels",
                "%.2f%%".format(
                    Locale.US,
                    diagnostics.rejectedPixelFraction * 100.0
                ),
                modifier = Modifier.weight(1f)
            )
        }

        MetricTile(
            "Outlier rejection",
            "> %.1fσ residual".format(Locale.US, diagnostics.rejectionSigma),
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                "|z| > 3σ",
                "%.2f%%".format(
                    Locale.US,
                    diagnostics.residualAbove3SigmaFraction * 100.0
                ),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "|z| > 5σ",
                "%.2f%%".format(
                    Locale.US,
                    diagnostics.residualAbove5SigmaFraction * 100.0
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricTile(
                "Max |z|",
                "%.1fσ".format(
                    Locale.US,
                    diagnostics.maxAbsStandardizedResidual
                ),
                modifier = Modifier.weight(1f)
            )
            MetricTile(
                "Profile updates",
                diagnostics.profileUpdateIterations.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        SpectrumProcessingStage(
            number = "1",
            title = "Rectified trace",
            description =
                "The fitted RED → VIOLET trace has been rotated into detector coordinates with dispersion horizontal. The stellar spectrum should form a narrow horizontal band.",
            values = diagnostics.rawRectified,
            diagnostics = diagnostics,
            positiveOnly = true
        )

        SpectrumProcessingStage(
            number = "2",
            title = "Two-sided background",
            description =
                "Sigma-clipped side windows are fitted independently at each spectral position. Field stars in the sidebands should be rejected rather than copied into the target spectrum.",
            values = diagnostics.backgroundModel,
            diagnostics = diagnostics,
            positiveOnly = true
        )

        SpectrumProcessingStage(
            number = "3",
            title = "Background subtracted",
            description =
                "The local sky/background model is removed. The target stripe should remain while most of the surrounding star field disappears.",
            values = diagnostics.backgroundSubtracted,
            diagnostics = diagnostics,
            positiveOnly = true
        )

        SpectrumProcessingStage(
            number = "4",
            title = "Spatial profile weights",
            description =
                "This is the actual empirical cross-dispersion profile used by the Horne weights. Every dispersion column is normalized to unit integral, so brightness changes here reflect profile shape only, not stellar flux.",
            values = diagnostics.spatialProfileWeights,
            diagnostics = diagnostics,
            positiveOnly = true
        )

        SpectrumProcessingStage(
            number = "5",
            title = "Fitted target model",
            description =
                "Optimal 1-D flux multiplied by the unit-normalized spatial profile. This is the fitted 2-D target spectrum that the residual test compares against the background-subtracted data.",
            values = diagnostics.fittedTargetModel,
            diagnostics = diagnostics,
            positiveOnly = true
        )

        SpectrumProcessingStage(
            number = "6",
            title = "Data − fitted model residual",
            description =
                "Signed residual after subtracting the fitted target model. Mid-grey is approximately zero; bright and dark structure indicates positive and negative mismatch. Orange marks pixels that exceeded the rejection threshold.",
            values = diagnostics.residualImage,
            diagnostics = diagnostics,
            rejectedMask = diagnostics.rejectedMask,
            positiveOnly = false,
            centerZero = true
        )

        SpectrumProcessingStage(
            number = "7",
            title = "Standardized residual z-map",
            description =
                "Fixed physical scale from −5σ to +5σ: mid-grey is 0σ, black is −5σ and white is +5σ. Yellow marks |z| ≥ 3σ, magenta marks |z| ≥ 5σ, and orange marks pixels actually rejected by the extractor. Unlike the raw residual image above, this panel is not auto-contrasted.",
            values = diagnostics.standardizedResidualImage,
            diagnostics = diagnostics,
            rejectedMask = diagnostics.rejectedMask,
            positiveOnly = false,
            centerZero = true,
            fixedLow = -5.0,
            fixedHigh = 5.0,
            thresholdAbs = 3.0,
            strongThresholdAbs = 5.0,
            sigmaLegend = true
        )

        SpectrumProcessingStage(
            number = "8",
            title = "Cleaned optimal aperture",
            description =
                "Pixels inconsistent with the fitted spatial profile are iteratively rejected. Orange pixels were rejected and replaced only in this QA view by the fitted target model; the rejection mask remains preserved separately.",
            values = diagnostics.cleanedRectified,
            diagnostics = diagnostics,
            rejectedMask = diagnostics.rejectedMask,
            positiveOnly = true
        )

        Text(
            "9 · 1-D extraction comparison",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )

        OptimalVsBoxcarChart(diagnostics)

        Text(
            diagnostics.varianceSemantics,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun SpectrumChart(
    extraction: SpectrumExtraction,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onTapIndex: ((Int) -> Unit)? = null,
    displaySmoothingWindow: Int = 1
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    val signal = spectrumDisplayLowPass(extraction.corrected, displaySmoothingWindow)
    val validValues = signal.indices.filter { extraction.valid[it] }.map { signal[it] }
    val minValue = validValues.minOrNull() ?: signal.minOrNull() ?: 0.0
    val maxValue = validValues.maxOrNull() ?: signal.maxOrNull() ?: 1.0
    val range = (maxValue - minValue).coerceAtLeast(1e-6)
    val paddingX = 18f
    val paddingTop = 18f
    val paddingBottom = 34f
    val endpointGuardSamples =
        SpectrumFeatureSearchQa.endpointGuardSamples(
            signal.size
        )
    val anchorBracketedIndices =
        signal.indices.filter { index ->
            SpectrumFeatureSearchQa.zoneFor(
                extraction,
                index
            ) == FeatureSearchZone.AnchorBracketed
        }

    Box(
        modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF090D13), RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
            .onSizeChanged { size = it }
            .pointerInput(extraction, onTapIndex, size) {
                if (onTapIndex != null) {
                    detectTapGestures { point ->
                        if (size.width > paddingX * 2 && extraction.distancesPx.isNotEmpty()) {
                            val f = ((point.x - paddingX) / (size.width - paddingX * 2)).coerceIn(0f, 1f)
                            val displayIndex = (f * extraction.distancesPx.lastIndex)
                                .toInt()
                                .coerceIn(0, extraction.distancesPx.lastIndex)
                            onTapIndex(extraction.distancesPx.lastIndex - displayIndex)
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val plotW = (this.size.width - paddingX * 2).coerceAtLeast(1f)
            val plotH = (this.size.height - paddingTop - paddingBottom).coerceAtLeast(1f)

            fun xForSourceIndex(index: Int): Float {
                if (signal.lastIndex <= 0) return paddingX
                val displayIndex =
                    signal.lastIndex -
                        index.coerceIn(0, signal.lastIndex)
                return paddingX +
                    plotW *
                    displayIndex /
                    signal.lastIndex.toFloat()
            }

            if (endpointGuardSamples > 0 && signal.lastIndex > 0) {
                val leftGuardBoundary =
                    xForSourceIndex(
                        (signal.lastIndex - endpointGuardSamples)
                            .coerceAtLeast(0)
                    )
                val rightGuardBoundary =
                    xForSourceIndex(
                        endpointGuardSamples
                            .coerceAtMost(signal.lastIndex)
                    )

                drawRect(
                    Color(0xFFB78CFF).copy(alpha = 0.09f),
                    topLeft = Offset(paddingX, paddingTop),
                    size = Size(
                        (leftGuardBoundary - paddingX)
                            .coerceAtLeast(0f),
                        plotH
                    )
                )
                drawRect(
                    Color(0xFFFF6B6B).copy(alpha = 0.09f),
                    topLeft = Offset(rightGuardBoundary, paddingTop),
                    size = Size(
                        (paddingX + plotW - rightGuardBoundary)
                            .coerceAtLeast(0f),
                        plotH
                    )
                )
            }

            if (
                extraction.calibration != null &&
                anchorBracketedIndices.isNotEmpty()
            ) {
                val sourceLow = anchorBracketedIndices.minOrNull() ?: 0
                val sourceHigh = anchorBracketedIndices.maxOrNull() ?: signal.lastIndex
                val bracketLeft = xForSourceIndex(sourceHigh)
                val bracketRight = xForSourceIndex(sourceLow)
                val interiorLeft =
                    if (endpointGuardSamples > 0) {
                        xForSourceIndex(
                            (signal.lastIndex - endpointGuardSamples)
                                .coerceAtLeast(0)
                        )
                    } else {
                        paddingX
                    }
                val interiorRight =
                    if (endpointGuardSamples > 0) {
                        xForSourceIndex(
                            endpointGuardSamples
                                .coerceAtMost(signal.lastIndex)
                        )
                    } else {
                        paddingX + plotW
                    }

                if (bracketLeft > interiorLeft) {
                    drawRect(
                        Color(0xFFFFC857).copy(alpha = 0.055f),
                        topLeft = Offset(interiorLeft, paddingTop),
                        size = Size(
                            bracketLeft - interiorLeft,
                            plotH
                        )
                    )
                }
                if (interiorRight > bracketRight) {
                    drawRect(
                        Color(0xFFFFC857).copy(alpha = 0.055f),
                        topLeft = Offset(bracketRight, paddingTop),
                        size = Size(
                            interiorRight - bracketRight,
                            plotH
                        )
                    )
                }
            }

            for (g in 0..4) {
                val y = paddingTop + plotH * g / 4f
                drawLine(Color.White.copy(alpha = 0.09f), Offset(paddingX, y), Offset(paddingX + plotW, y), 1f)
            }
            var previous: Offset? = null
            for (displayIndex in signal.indices) {
                val i = signal.lastIndex - displayIndex
                val x = paddingX + plotW * displayIndex / max(1, signal.lastIndex).toFloat()
                val y = paddingTop + plotH * (1f - ((signal[i] - minValue) / range).toFloat().coerceIn(0f, 1f))
                val point = Offset(x, y)
                previous?.let { drawLine(Color(0xFF67E4CB), it, point, 2.2f) }
                previous = point
                if (extraction.contaminationProbability[i] >= 0.6) {
                    drawLine(Color(0xFFFF765E).copy(alpha = 0.35f), Offset(x, paddingTop), Offset(x, paddingTop + plotH), 2f)
                }
            }
            extraction.features.forEach { feature ->
                val i = feature.index
                val displayIndex = signal.lastIndex - i
                val x = paddingX + plotW * displayIndex / max(1, signal.lastIndex).toFloat()
                val y = paddingTop + plotH * (1f - ((signal[i] - minValue) / range).toFloat().coerceIn(0f, 1f))
                drawCircle(
                    if (feature.type == FeatureType.Emission) Color(0xFFFFD95A) else Color(0xFFCAB7FF),
                    radius = 4.8f,
                    center = Offset(x, y),
                    style = Stroke(2f)
                )
            }
            selectedIndex?.coerceIn(0, signal.lastIndex)?.let { i ->
                val displayIndex = signal.lastIndex - i
                val x = paddingX + plotW * displayIndex / max(1, signal.lastIndex).toFloat()
                drawLine(Color.White.copy(alpha = 0.8f), Offset(x, paddingTop), Offset(x, paddingTop + plotH), 2f)
            }
            if (extraction.calibration != null) {
                val gradient = Brush.horizontalGradient(
                    listOf(
                        Color(0xFF5E4BFF), Color(0xFF2678FF), Color(0xFF29C98A),
                        Color(0xFFE2D64D), Color(0xFFFF9B42), Color(0xFFFF4D55)
                    ),
                    startX = paddingX,
                    endX = paddingX + plotW
                )
                drawRect(gradient, topLeft = Offset(paddingX, this.size.height - 17f), size = Size(plotW, 5f))
            }
        }
        val leftLabel = extraction.wavelengthAt(extraction.distancesPx.lastIndex)
            ?.let { "%.0f nm".format(Locale.US, it) }
            ?: "%.0f px".format(Locale.US, extraction.distancesPx.lastOrNull() ?: 0.0)
        val rightLabel = extraction.wavelengthAt(0)
            ?.let { "%.0f nm".format(Locale.US, it) }
            ?: "0 px"
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(leftLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
            Text(if (extraction.calibration != null) "wavelength · VIOLET → RED" else "trace · VIOLET → RED", color = Color.White.copy(alpha = 0.55f), style = MaterialTheme.typography.labelSmall)
            Text(rightLabel, color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
        }
    }
}


private fun spectrumDisplayLowPass(values: DoubleArray, requestedWindow: Int): DoubleArray {
    if (values.isEmpty()) return values
    val window = requestedWindow.coerceAtLeast(1).let { if (it % 2 == 0) it + 1 else it }
    if (window <= 1) return values.copyOf()
    val half = window / 2
    return DoubleArray(values.size) { index ->
        val from = (index - half).coerceAtLeast(0)
        val to = (index + half).coerceAtMost(values.lastIndex)
        var sum = 0.0
        var count = 0
        for (i in from..to) {
            val value = values[i]
            if (value.isFinite()) {
                sum += value
                count++
            }
        }
        if (count > 0) sum / count else Double.NaN
    }
}


private fun spectrumMovingAverage(values: DoubleArray, radius: Int): DoubleArray {
    if (values.isEmpty()) return values
    val r = radius.coerceAtLeast(0)
    if (r == 0) return values.copyOf()

    val result = DoubleArray(values.size)
    var sum = 0.0
    var count = 0

    for (i in values.indices) {
        val add = i + r
        if (add < values.size && values[add].isFinite()) {
            sum += values[add]
            count++
        }
        val remove = i - r - 1
        if (remove >= 0 && values[remove].isFinite()) {
            sum -= values[remove]
            count--
        }
        result[i] = if (count > 0) sum / count else Double.NaN
    }

    return result
}

private fun spectrumRollingMedian(values: DoubleArray, window: Int): DoubleArray {
    if (values.isEmpty()) return values
    val half = (window.coerceAtLeast(3) / 2).coerceAtLeast(1)
    return DoubleArray(values.size) { i ->
        val start = (i - half).coerceAtLeast(0)
        val end = (i + half).coerceAtMost(values.lastIndex)
        val local = values.sliceArray(start..end)
            .filter { it.isFinite() }
            .sorted()
        if (local.isEmpty()) {
            Double.NaN
        } else if (local.size % 2 == 1) {
            local[local.size / 2]
        } else {
            (local[local.size / 2 - 1] + local[local.size / 2]) / 2.0
        }
    }
}

private fun calibrationBaseSignal(extraction: SpectrumExtraction): DoubleArray {
    // Preserve the signed, background-subtracted extraction for wavelength
    // calibration. A weak spectrum can legitimately cross below the fitted
    // local background; clipping those samples to zero destroys exactly the
    // local shape needed to identify an absorption trough.
    return DoubleArray(extraction.corrected.size) { i ->
        val optimal = extraction.corrected[i]
        val boxcar = extraction.diagnostics.boxcarSignal.getOrElse(i) {
            Double.NaN
        }

        when {
            extraction.valid[i] && optimal.isFinite() ->
                optimal

            boxcar.isFinite() ->
                boxcar

            else ->
                Double.NaN
        }
    }
}


private data class EndpointGuard(
    val firstUsableIndex: Int,
    val lastUsableIndex: Int,
    val guardSamples: Int
) {
    fun contains(index: Int): Boolean =
        index < firstUsableIndex ||
            index > lastUsableIndex
}

private fun endpointGuardFor(
    extraction: SpectrumExtraction
): EndpointGuard {
    val count = extraction.distancesPx.size
    if (count <= 1) {
        return EndpointGuard(
            firstUsableIndex = 0,
            lastUsableIndex = 0,
            guardSamples = 0
        )
    }

    // About 4% of the trace at each end, bounded so short spectra do not
    // lose most of their useful region and long JPEG traces do not get
    // excessively large dead zones. These are QA/detection guards only:
    // the RED/VIOLET endpoints still define trace geometry and chromatic limits.
    val guard =
        max(
            12,
            min(
                40,
                count / 25
            )
        )
            .coerceAtMost(
                max(
                    1,
                    (count - 2) / 3
                )
            )

    return EndpointGuard(
        firstUsableIndex = guard,
        lastUsableIndex =
            (count - 1 - guard)
                .coerceAtLeast(
                    guard
                ),
        guardSamples = guard
    )
}

private data class CalibrationFluxDisplay(
    val signal: DoubleArray,
    val continuum: DoubleArray
)

private fun calibrationRelativeFluxDisplay(
    extraction: SpectrumExtraction
): CalibrationFluxDisplay {
    if (extraction.raw.isEmpty()) {
        return CalibrationFluxDisplay(DoubleArray(0), DoubleArray(0))
    }

    val base = calibrationBaseSignal(extraction)
    val smoothed = spectrumMovingAverage(base, radius = 3)

    val wanted = max(101, extraction.raw.size / 8)
    val continuumWindow =
        if (wanted % 2 == 0) wanted + 1 else wanted
    val broadContinuum =
        spectrumRollingMedian(
            smoothed,
            continuumWindow.coerceAtMost(301)
        )

    val usableAbsolute = smoothed.indices
        .filter {
            extraction.valid[it] &&
                smoothed[it].isFinite()
        }
        .map {
            kotlin.math.abs(smoothed[it])
        }
        .sorted()

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 1.0
        val position =
            p.coerceIn(0.0, 1.0) *
                (values.size - 1)
        val lo =
            position.toInt()
                .coerceIn(0, values.lastIndex)
        val hi =
            kotlin.math.ceil(position)
                .toInt()
                .coerceIn(0, values.lastIndex)
        val f = position - lo
        return values[lo] * (1.0 - f) +
            values[hi] * f
    }

    // Scale by robust absolute amplitude, not positive amplitude. This preserves
    // genuine negative background-subtracted samples while preventing a single
    // JPEG/demosaic spike from setting the complete display range.
    val scale =
        percentile(
            usableAbsolute,
            0.98
        )
            .coerceAtLeast(1e-12)

    val signal =
        DoubleArray(smoothed.size) { i ->
            val value = smoothed[i]
            if (
                !extraction.valid[i] ||
                !value.isFinite()
            ) {
                Double.NaN
            } else {
                (value / scale)
                    .coerceIn(
                        -1.60,
                        1.60
                    )
            }
        }

    val continuum =
        DoubleArray(broadContinuum.size) { i ->
            val value = broadContinuum[i]
            if (
                !extraction.valid[i] ||
                !value.isFinite()
            ) {
                Double.NaN
            } else {
                (value / scale)
                    .coerceIn(
                        -1.60,
                        1.60
                    )
            }
        }

    return CalibrationFluxDisplay(
        signal,
        continuum
    )
}

private fun spectrumRollingPercentile(
    values: DoubleArray,
    window: Int,
    percentile: Double
): DoubleArray {
    if (values.isEmpty()) return values

    val half = (window.coerceAtLeast(3) / 2).coerceAtLeast(1)
    val p = percentile.coerceIn(0.0, 1.0)

    return DoubleArray(values.size) { i ->
        val start = (i - half).coerceAtLeast(0)
        val end = (i + half).coerceAtMost(values.lastIndex)
        val local = values
            .sliceArray(start..end)
            .filter { it.isFinite() }
            .sorted()

        if (local.isEmpty()) {
            Double.NaN
        } else {
            val position = p * (local.size - 1)
            val lo = position.toInt().coerceIn(0, local.lastIndex)
            val hi = kotlin.math.ceil(position).toInt().coerceIn(0, local.lastIndex)
            val fraction = position - lo
            local[lo] * (1.0 - fraction) + local[hi] * fraction
        }
    }
}

private data class CalibrationLineFinderDisplay(
    val signal: DoubleArray,
    val continuum: DoubleArray,
    val reliable: BooleanArray,
    val reliableFraction: Double
)

private fun calibrationLineFinderDisplay(
    extraction: SpectrumExtraction
): CalibrationLineFinderDisplay {
    if (extraction.corrected.isEmpty()) {
        return CalibrationLineFinderDisplay(
            DoubleArray(0),
            DoubleArray(0),
            BooleanArray(0),
            0.0
        )
    }

    val base = calibrationBaseSignal(extraction)
    val smoothed = spectrumMovingAverage(base, radius = 4)
    val endpointGuard = endpointGuardFor(extraction)

    val usableValues = smoothed.indices
        .filter {
            !endpointGuard.contains(it) &&
                extraction.valid[it] &&
                smoothed[it].isFinite() &&
                smoothed[it] > 0.0
        }
        .map { smoothed[it] }
        .sorted()

    fun percentile(values: List<Double>, p: Double): Double {
        if (values.isEmpty()) return 0.0
        val position = p.coerceIn(0.0, 1.0) * (values.size - 1)
        val lo = position.toInt().coerceIn(0, values.lastIndex)
        val hi = kotlin.math.ceil(position).toInt().coerceIn(0, values.lastIndex)
        val fraction = position - lo
        return values[lo] * (1.0 - fraction) + values[hi] * fraction
    }

    val nearPeak = percentile(usableValues, 0.95).coerceAtLeast(1e-12)
    val signalFloor = nearPeak * 0.055

    val firstPassReliable = BooleanArray(smoothed.size) { i ->
        val value = smoothed[i]
        val variance = extraction.diagnostics.optimalVariance.getOrNull(i)
            ?: Double.POSITIVE_INFINITY
        val noise =
            if (variance.isFinite() && variance > 0.0) kotlin.math.sqrt(variance)
            else extraction.localNoise.getOrNull(i) ?: Double.POSITIVE_INFINITY
        val signalSnr =
            if (noise.isFinite() && noise > 1e-12) value / noise
            else Double.POSITIVE_INFINITY

        !endpointGuard.contains(i) &&
            extraction.valid[i] &&
            value.isFinite() &&
            value >= signalFloor &&
            signalSnr >= 2.5
    }

    val masked = DoubleArray(smoothed.size) { i ->
        if (firstPassReliable[i]) smoothed[i] else Double.NaN
    }

    // A broad high-percentile envelope follows the camera/spectral response
    // without following individual absorption troughs downwards.
    val wanted = max(121, smoothed.size / 5)
    val envelopeWindow = (
        if (wanted % 2 == 0) wanted + 1 else wanted
    ).coerceAtMost(301)

    val upperEnvelope = spectrumRollingPercentile(
        values = masked,
        window = envelopeWindow,
        percentile = 0.82
    )

    val continuum = spectrumMovingAverage(
        spectrumMovingAverage(upperEnvelope, radius = 10),
        radius = 10
    )

    val continuumFloor = nearPeak * 0.075

    val reliable = BooleanArray(smoothed.size) { i ->
        firstPassReliable[i] &&
            continuum[i].isFinite() &&
            continuum[i] >= continuumFloor
    }

    val signal = DoubleArray(smoothed.size) { i ->
        if (!reliable[i]) {
            Double.NaN
        } else {
            val ratio = smoothed[i] / continuum[i].coerceAtLeast(1e-12)

            // This is a calibration aid, not a science flux product. Values
            // outside a physically useful local range are suppressed rather
            // than allowed to create giant ratio spikes at channel handovers.
            if (!ratio.isFinite() || ratio < 0.40 || ratio > 1.35) {
                Double.NaN
            } else {
                ratio
            }
        }
    }

    val finalReliable =
        BooleanArray(
            signal.size
        ) {
            signal[it].isFinite()
        }
    val usableCount =
        (
            endpointGuard.lastUsableIndex -
                endpointGuard.firstUsableIndex +
                1
            )
            .coerceAtLeast(
                1
            )
    val reliableCount =
        (
            endpointGuard.firstUsableIndex..
                endpointGuard.lastUsableIndex
            )
            .count {
                finalReliable
                    .getOrElse(it) {
                        false
                    }
            }
    val reliableFraction =
        if (signal.isEmpty()) {
            0.0
        } else {
            reliableCount.toDouble() /
                usableCount.toDouble()
        }

    return CalibrationLineFinderDisplay(
        signal = signal,
        continuum = continuum,
        reliable = finalReliable,
        reliableFraction = reliableFraction
    )
}

private data class BalmerPrediction(
    val label: String,
    val wavelengthNm: Double,
    val distancePx: Double,
    val index: Int
)

private fun predictedDistanceForWavelength(
    fit: CalibrationFit,
    wavelengthNm: Double,
    minDistancePx: Double,
    maxDistancePx: Double
): Double? {
    if (fit.coefficients.size < 2) return null

    val c0 = fit.coefficients[0]
    val c1 = fit.coefficients[1]
    val minD = min(minDistancePx, maxDistancePx)
    val maxD = max(minDistancePx, maxDistancePx)

    if (fit.order <= 1 || fit.coefficients.size < 3 || kotlin.math.abs(fit.coefficients[2]) < 1e-12) {
        if (kotlin.math.abs(c1) < 1e-12) return null
        val d = (wavelengthNm - c0) / c1
        return d.takeIf { it.isFinite() && it in minD..maxD }
    }

    val c2 = fit.coefficients[2]
    val discriminant = c1 * c1 - 4.0 * c2 * (c0 - wavelengthNm)
    if (discriminant < 0.0) return null

    val root = kotlin.math.sqrt(discriminant)
    val candidates = listOf(
        (-c1 + root) / (2.0 * c2),
        (-c1 - root) / (2.0 * c2)
    ).filter { it.isFinite() && it in minD..maxD }

    if (candidates.isEmpty()) return null

    val centre = (minD + maxD) / 2.0
    return candidates.minByOrNull { kotlin.math.abs(it - centre) }
}

private fun provisionalBalmerPredictions(
    extraction: SpectrumExtraction,
    anchors: List<CalibrationAnchor>,
    requestedOrder: Int,
    excludedLabels: Set<String> = anchors.map { it.label }.toSet()
): List<BalmerPrediction> {
    if (anchors.size < 2 || extraction.distancesPx.isEmpty()) return emptyList()

    val provisionalOrder = requestedOrder
        .coerceIn(1, 2)
        .coerceAtMost(anchors.size - 1)

    val fit = runCatching {
        SpectrumEngine.fitCalibration(anchors, provisionalOrder)
    }.getOrNull() ?: return emptyList()

    val minDistance = extraction.distancesPx.first()
    val maxDistance = extraction.distancesPx.last()

    fun nearestIndex(distancePx: Double): Int {
        var best = 0
        var bestDiff = Double.POSITIVE_INFINITY
        extraction.distancesPx.forEachIndexed { index, value ->
            val diff = kotlin.math.abs(value - distancePx)
            if (diff < bestDiff) {
                best = index
                bestDiff = diff
            }
        }
        return best
    }

    return HydrogenBalmerLines.standard
        .filter { line -> line.label !in excludedLabels }
        .mapNotNull { line ->
            val distance = predictedDistanceForWavelength(
                fit = fit,
                wavelengthNm = line.wavelengthNm,
                minDistancePx = minDistance,
                maxDistancePx = maxDistance
            ) ?: return@mapNotNull null

            BalmerPrediction(
                label = line.label,
                wavelengthNm = line.wavelengthNm,
                distancePx = distance,
                index = nearestIndex(distance)
            )
        }
}



@Composable
internal fun SpectrumTracePreview(
    extraction: SpectrumExtraction,
    modifier: Modifier = Modifier
) {
    // Compute directly from the current extraction rather than tying this preview
    // to calibration/anchor state. It should appear immediately after Extract.
    val flux = calibrationRelativeFluxDisplay(extraction)
    val signal = flux.signal
    val continuum = flux.continuum

    val paddingX = 18f
    val paddingTop = 24f
    val paddingBottom = 32f

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "VIOLET",
                color = Color(0xFFB78CFF),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Signed relative image signal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "RED",
                color = Color(0xFFFF6B6B),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(6.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF090D13))
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(16.dp)
                )
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (signal.isEmpty()) return@Canvas

                val plotW = (size.width - paddingX * 2).coerceAtLeast(1f)
                val plotH = (size.height - paddingTop - paddingBottom).coerceAtLeast(1f)

                val finite =
                    signal.filter { it.isFinite() }.sorted()

                fun percentile(p: Double): Double {
                    if (finite.isEmpty()) return 0.0
                    val position =
                        p.coerceIn(0.0, 1.0) *
                            (finite.size - 1)
                    val lo =
                        position.toInt()
                            .coerceIn(0, finite.lastIndex)
                    val hi =
                        kotlin.math.ceil(position)
                            .toInt()
                            .coerceIn(0, finite.lastIndex)
                    val f = position - lo
                    return finite[lo] * (1.0 - f) +
                        finite[hi] * f
                }

                val q03 = percentile(0.03)
                val q97 = percentile(0.97)
                val robustSpan =
                    (q97 - q03)
                        .coerceAtLeast(0.08)

                val localMin =
                    min(
                        q03 - robustSpan * 0.12,
                        0.0
                    )
                val localMax =
                    max(
                        q97 + robustSpan * 0.12,
                        0.0
                    )
                val localRange =
                    (localMax - localMin)
                        .coerceAtLeast(0.08)

                for (g in 0..4) {
                    val y = paddingTop + plotH * g / 4f
                    drawLine(
                        Color.White.copy(alpha = 0.09f),
                        Offset(paddingX, y),
                        Offset(paddingX + plotW, y),
                        1f
                    )
                }

                if (0.0 in localMin..localMax) {
                    val zeroY =
                        paddingTop +
                            plotH *
                                (
                                    1f -
                                        (
                                            (0.0 - localMin) /
                                                localRange
                                            )
                                            .toFloat()
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                    )
                    drawLine(
                        Color.White.copy(alpha = 0.28f),
                        Offset(paddingX, zeroY),
                        Offset(paddingX + plotW, zeroY),
                        1.4f
                    )
                }

                var previous: Offset? = null
                for (i in signal.lastIndex downTo 0) {
                    val value = signal[i]
                    if (!value.isFinite()) {
                        previous = null
                        continue
                    }

                    val displayFraction =
                        if (signal.size <= 1) 0f
                        else 1f - i.toFloat() / signal.lastIndex.toFloat()

                    val x = paddingX + plotW * displayFraction
                    val y = paddingTop + plotH *
                        (1f - ((value - localMin) / localRange)
                            .toFloat()
                            .coerceIn(0f, 1f))
                    val p = Offset(x, y)

                    previous?.let {
                        drawLine(Color(0xFF67E4CB), it, p, 2.4f)
                    }
                    previous = p
                }

                var previousContinuum: Offset? = null
                for (i in continuum.lastIndex downTo 0) {
                    val value = continuum[i]
                    if (!value.isFinite()) {
                        previousContinuum = null
                        continue
                    }

                    val displayFraction =
                        if (continuum.size <= 1) 0f
                        else 1f - i.toFloat() / continuum.lastIndex.toFloat()

                    val x = paddingX + plotW * displayFraction
                    val y = paddingTop + plotH *
                        (1f - ((value - localMin) / localRange)
                            .toFloat()
                            .coerceIn(0f, 1f))
                    val p = Offset(x, y)

                    previousContinuum?.let {
                        drawLine(
                            Color.White.copy(alpha = 0.38f),
                            it,
                            p,
                            3f
                        )
                    }
                    previousContinuum = p
                }
            }

            Text(
                "signed background-subtracted signal",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 6.dp),
                color = Color.White.copy(alpha = 0.60f),
                style = MaterialTheme.typography.labelSmall
            )

            Text(
                "broad continuum shown in white",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 6.dp),
                color = Color.White.copy(alpha = 0.48f),
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            "This trace is generated immediately from the extraction and does not depend on wavelength anchors. It preserves the signed background-subtracted spectral envelope, including negative excursions below the fitted local background; calibration controls below only add wavelength positions.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}



@Composable
internal fun ReferenceCalibrationQaChart(
    extraction: SpectrumExtraction,
    reference: SpectrumReference,
    modifier: Modifier = Modifier
) {
    val display =
        remember(extraction) {
            calibrationRelativeFluxDisplay(
                extraction
            )
        }
    val endpointGuard =
        remember(extraction) {
            endpointGuardFor(extraction)
        }

    val signal = display.signal
    if (
        signal.isEmpty() ||
        extraction.distancesPx.size !=
        signal.size
    ) {
        return
    }

    val wavelengths =
        remember(
            extraction,
            reference
        ) {
            DoubleArray(
                extraction.distancesPx.size
            ) { index ->
                reference.wavelength(
                    extraction
                        .distancesPx[index]
                )
            }
        }

    val finiteSignal =
        signal.filter {
            it.isFinite()
        }.sorted()

    fun percentile(
        p: Double
    ): Double {
        if (finiteSignal.isEmpty()) {
            return 0.0
        }

        val position =
            p.coerceIn(
                0.0,
                1.0
            ) *
                (
                    finiteSignal.size -
                        1
                    )
        val lo =
            position.toInt()
                .coerceIn(
                    0,
                    finiteSignal.lastIndex
                )
        val hi =
            kotlin.math.ceil(
                position
            )
                .toInt()
                .coerceIn(
                    0,
                    finiteSignal.lastIndex
                )
        val f =
            position -
                lo

        return finiteSignal[lo] *
            (1.0 - f) +
            finiteSignal[hi] *
                f
    }

    val q03 =
        percentile(
            0.03
        )
    val q97 =
        percentile(
            0.97
        )
    val robustSpan =
        (q97 - q03)
            .coerceAtLeast(
                0.08
            )
    val yMin =
        min(
            q03 -
                robustSpan *
                    0.12,
            0.0
        )
    val yMax =
        max(
            q97 +
                robustSpan *
                    0.12,
            0.0
        )
    val yRange =
        (yMax - yMin)
            .coerceAtLeast(
                0.08
            )

    val finiteWavelengths =
        wavelengths.filter {
            it.isFinite()
        }

    if (finiteWavelengths.isEmpty()) {
        return
    }

    val wavelengthMin =
        finiteWavelengths
            .minOrNull()
            ?: return
    val wavelengthMax =
        finiteWavelengths
            .maxOrNull()
            ?: return
    val wavelengthRange =
        (wavelengthMax -
            wavelengthMin)
            .coerceAtLeast(
                1e-6
            )

    fun wavelengthFraction(
        wavelengthNm: Double
    ): Float =
        (
            (wavelengthNm -
                wavelengthMin) /
                wavelengthRange
            )
            .toFloat()
            .coerceIn(
                0f,
                1f
            )

    Column(
        modifier,
        verticalArrangement =
            Arrangement.spacedBy(
                8.dp
            )
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text(
                "VIOLET",
                color =
                    Color(
                        0xFFB78CFF
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.Bold
            )
            Text(
                "Committed calibrated spectrum",
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.SemiBold
            )
            Text(
                "RED",
                color =
                    Color(
                        0xFFFF6B6B
                    ),
                style =
                    MaterialTheme
                        .typography
                        .labelMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    280.dp
                )
                .clip(
                    RoundedCornerShape(
                        14.dp
                    )
                )
                .background(
                    Color(
                        0xFF090D13
                    )
                )
                .border(
                    1.dp,
                    MaterialTheme
                        .colorScheme
                        .outlineVariant,
                    RoundedCornerShape(
                        14.dp
                    )
                )
        ) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal =
                            12.dp,
                        vertical =
                            14.dp
                    )
            ) {
                val w =
                    size.width
                        .coerceAtLeast(
                            1f
                        )
                val h =
                    size.height
                        .coerceAtLeast(
                            1f
                        )

                fun xForWavelength(
                    wavelengthNm: Double
                ): Float =
                    w *
                        wavelengthFraction(
                            wavelengthNm
                        )

                val violetUsableNm =
                    wavelengths
                        .getOrNull(
                            endpointGuard
                                .lastUsableIndex
                        )
                val redUsableNm =
                    wavelengths
                        .getOrNull(
                            endpointGuard
                                .firstUsableIndex
                        )

                if (
                    violetUsableNm != null &&
                    violetUsableNm
                        .isFinite()
                ) {
                    val x =
                        xForWavelength(
                            violetUsableNm
                        )
                    drawRect(
                        Color(
                            0xFFB78CFF
                        )
                            .copy(
                                alpha =
                                    0.08f
                            ),
                        topLeft =
                            Offset(
                                0f,
                                0f
                            ),
                        size =
                            Size(
                                x.coerceIn(
                                    0f,
                                    w
                                ),
                                h
                            )
                    )
                }

                if (
                    redUsableNm != null &&
                    redUsableNm
                        .isFinite()
                ) {
                    val x =
                        xForWavelength(
                            redUsableNm
                        )
                            .coerceIn(
                                0f,
                                w
                            )
                    drawRect(
                        Color(
                            0xFFFF6B6B
                        )
                            .copy(
                                alpha =
                                    0.08f
                            ),
                        topLeft =
                            Offset(
                                x,
                                0f
                            ),
                        size =
                            Size(
                                (w - x)
                                    .coerceAtLeast(
                                        0f
                                    ),
                                h
                            )
                    )
                }

                for (grid in 0..4) {
                    val y =
                        h *
                            grid /
                            4f
                    drawLine(
                        Color.White
                            .copy(
                                alpha =
                                    0.08f
                            ),
                        Offset(
                            0f,
                            y
                        ),
                        Offset(
                            w,
                            y
                        ),
                        1f
                    )
                }

                if (
                    0.0 in
                    yMin..
                        yMax
                ) {
                    val zeroY =
                        h *
                            (
                                1f -
                                    (
                                        (0.0 -
                                            yMin) /
                                            yRange
                                        )
                                        .toFloat()
                                        .coerceIn(
                                            0f,
                                            1f
                                        )
                                )
                    drawLine(
                        Color.White
                            .copy(
                                alpha =
                                    0.28f
                            ),
                        Offset(
                            0f,
                            zeroY
                        ),
                        Offset(
                            w,
                            zeroY
                        ),
                        1.4f
                    )
                }

                var previous:
                    Offset? =
                    null

                for (
                    index in
                    signal.indices
                        .reversed()
                ) {
                    val value =
                        signal[index]
                    val wavelength =
                        wavelengths[index]

                    if (
                        !value.isFinite() ||
                        !wavelength
                            .isFinite()
                    ) {
                        previous =
                            null
                        continue
                    }

                    val x =
                        xForWavelength(
                            wavelength
                        )
                    val y =
                        h *
                            (
                                1f -
                                    (
                                        (value -
                                            yMin) /
                                            yRange
                                        )
                                        .toFloat()
                                        .coerceIn(
                                            0f,
                                            1f
                                        )
                                )
                    val point =
                        Offset(
                            x,
                            y
                        )

                    previous?.let {
                        drawLine(
                            Color(
                                0xFF67E4CB
                            ),
                            it,
                            point,
                            2.4f
                        )
                    }

                    previous =
                        point
                }

                reference.anchors
                    .forEach {
                            anchor ->
                        val x =
                            xForWavelength(
                                anchor
                                    .wavelengthNm
                            )
                        val color =
                            balmerAccentColor(
                                anchor.label
                            )

                        drawLine(
                            color
                                .copy(
                                    alpha =
                                        0.90f
                                ),
                            Offset(
                                x,
                                0f
                            ),
                            Offset(
                                x,
                                h
                            ),
                            2.2f
                        )
                        drawCircle(
                            color,
                            radius =
                                5f,
                            center =
                                Offset(
                                    x,
                                    5f
                                )
                        )
                    }
            }

            Text(
                "%.0f nm".format(
                    Locale.US,
                    wavelengthMin
                ),
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomStart
                        )
                        .padding(
                            start =
                                12.dp,
                            bottom =
                                4.dp
                        ),
                color =
                    Color.White
                        .copy(
                            alpha =
                                0.62f
                        ),
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )
            Text(
                "%.0f nm".format(
                    Locale.US,
                    wavelengthMax
                ),
                modifier =
                    Modifier
                        .align(
                            Alignment
                                .BottomEnd
                        )
                        .padding(
                            end =
                                12.dp,
                            bottom =
                                4.dp
                        ),
                color =
                    Color.White
                        .copy(
                            alpha =
                                0.62f
                        ),
                style =
                    MaterialTheme
                        .typography
                        .labelSmall
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                ),
            horizontalArrangement =
                Arrangement.spacedBy(
                    8.dp
                )
        ) {
            reference.anchors
                .sortedBy {
                    it.wavelengthNm
                }
                .forEach {
                        anchor ->
                    Surface(
                        shape =
                            RoundedCornerShape(
                                10.dp
                            ),
                        color =
                            balmerAccentColor(
                                anchor.label
                            )
                                .copy(
                                    alpha =
                                        0.10f
                                ),
                        border =
                            androidx.compose
                                .foundation
                                .BorderStroke(
                                    1.dp,
                                    balmerAccentColor(
                                        anchor.label
                                    )
                                        .copy(
                                            alpha =
                                                0.55f
                                        )
                                )
                    ) {
                        Text(
                            "${anchor.label} ${
                                "%.2f"
                                    .format(
                                        Locale.US,
                                        anchor
                                            .wavelengthNm
                                    )
                            } nm",
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        9.dp,
                                    vertical =
                                        6.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight
                                    .SemiBold
                        )
                    }
                }
        }

        Text(
            "Cyan = signed Horne-extracted signal · coloured verticals = confirmed Balmer anchors · shaded ends = endpoint guard zones excluded from line finding.",
            style =
                MaterialTheme
                    .typography
                    .bodySmall,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}



private data class BalmerPatternTrough(
    val index: Int,
    val distancePx: Double,
    val depth: Double,
    val localBaseline: Double
)

private data class BalmerPatternHypothesis(
    val id: String,
    val predictions: List<BalmerPrediction>,
    val supportCount: Int,
    val patternScore: Double,
    val geometryScore: Double,
    val balmerSpanFraction: Double,
    val impliedSpectrumSpanNm: Double,
    val dispersionNmPerPx: Double
)

private fun detectBalmerPatternTroughs(
    extraction: SpectrumExtraction,
    lineFinder: CalibrationLineFinderDisplay
): List<BalmerPatternTrough> {
    val signal = lineFinder.signal
    if (signal.size < 40 || extraction.distancesPx.size != signal.size) return emptyList()

    val candidates = mutableListOf<BalmerPatternTrough>()
    val endpointGuard = endpointGuardFor(extraction)
    val searchStart =
        max(
            10,
            endpointGuard.firstUsableIndex
        )
    val searchEndExclusive =
        min(
            signal.lastIndex - 10,
            endpointGuard.lastUsableIndex + 1
        )

    if (searchEndExclusive <= searchStart) {
        return emptyList()
    }

    for (i in searchStart until searchEndExclusive) {
        val centre = signal[i]
        if (!centre.isFinite()) continue

        var isMinimum = true
        for (j in (i - 3)..(i + 3)) {
            if (j == i) continue
            val value = signal[j]
            if (value.isFinite() && value < centre) {
                isMinimum = false
                break
            }
        }
        if (!isMinimum) continue

        val shoulders = mutableListOf<Double>()
        for (j in (i - 18)..(i - 6)) {
            val value = signal.getOrNull(j)
            if (value != null && value.isFinite()) shoulders += value
        }
        for (j in (i + 6)..(i + 18)) {
            val value = signal.getOrNull(j)
            if (value != null && value.isFinite()) shoulders += value
        }
        if (shoulders.size < 8) continue

        val sorted = shoulders.sorted()
        val baseline =
            if (sorted.size % 2 == 1) sorted[sorted.size / 2]
            else (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2.0

        val depth = baseline - centre

        if (
            baseline in 0.65..1.30 &&
            centre < 1.02 &&
            depth >= 0.025
        ) {
            candidates += BalmerPatternTrough(
                index = i,
                distancePx = extraction.distancesPx[i],
                depth = depth,
                localBaseline = baseline
            )
        }
    }

    val selected = mutableListOf<BalmerPatternTrough>()
    for (candidate in candidates.sortedByDescending { it.depth }) {
        if (selected.none { kotlin.math.abs(it.index - candidate.index) <= 8 }) {
            selected += candidate
        }
        if (selected.size >= 18) break
    }

    return selected.sortedBy { it.distancePx }
}

private fun localPatternSupport(
    troughs: List<BalmerPatternTrough>,
    predictedDistancePx: Double,
    tolerancePx: Double
): Pair<BalmerPatternTrough?, Double> {
    val nearest = troughs.minByOrNull {
        kotlin.math.abs(it.distancePx - predictedDistancePx)
    } ?: return null to 0.0

    val delta = kotlin.math.abs(nearest.distancePx - predictedDistancePx)
    if (delta > tolerancePx) return null to 0.0

    val distanceScore =
        kotlin.math.exp(
            -0.5 * (delta / tolerancePx.coerceAtLeast(1e-6)) *
                (delta / tolerancePx.coerceAtLeast(1e-6))
        )
    val depthScore = (nearest.depth / 0.12).coerceIn(0.0, 1.0)

    return nearest to (0.60 * distanceScore + 0.40 * depthScore)
}

private fun buildBalmerPatternHypotheses(
    extraction: SpectrumExtraction,
    lineFinder: CalibrationLineFinderDisplay
): Pair<List<BalmerPatternTrough>, List<BalmerPatternHypothesis>> {
    val troughs = detectBalmerPatternTroughs(extraction, lineFinder)
    if (troughs.size < 2 || extraction.distancesPx.isEmpty()) {
        return troughs to emptyList()
    }

    val lines = HydrogenBalmerLines.standard
        .sortedByDescending { it.wavelengthNm }

    val endpointGuard =
        endpointGuardFor(
            extraction
        )
    val minDistance =
        extraction.distancesPx[
            endpointGuard.firstUsableIndex
        ]
    val maxDistance =
        extraction.distancesPx[
            endpointGuard.lastUsableIndex
        ]
    val totalDistance =
        kotlin.math.abs(
            maxDistance -
                minDistance
        )
            .coerceAtLeast(
                1.0
            )
    val tolerancePx = max(8.0, totalDistance * 0.022)

    val all = mutableListOf<BalmerPatternHypothesis>()

    for (a in 0 until troughs.lastIndex) {
        for (b in a + 1 until troughs.size) {
            val firstTrough = troughs[a]
            val secondTrough = troughs[b]

            val deltaDistance = secondTrough.distancePx - firstTrough.distancePx
            if (deltaDistance <= max(6.0, totalDistance * 0.02)) continue

            for (lineA in 0 until lines.lastIndex) {
                for (lineB in lineA + 1 until lines.size) {
                    val firstLine = lines[lineA]
                    val secondLine = lines[lineB]

                    val slope =
                        (secondLine.wavelengthNm - firstLine.wavelengthNm) /
                            deltaDistance

                    // Distance increases RED -> VIOLET internally, so wavelength
                    // must decrease with distance.
                    if (!slope.isFinite() || slope >= -1e-9) continue

                    val intercept =
                        firstLine.wavelengthNm -
                            slope * firstTrough.distancePx

                    val predictions = mutableListOf<BalmerPrediction>()
                    var supportCount = 0
                    var supportScore = 0.0
                    var predictionFailure = false

                    for (line in lines) {
                        val predictedDistance =
                            (line.wavelengthNm - intercept) / slope

                        if (
                            !predictedDistance.isFinite() ||
                            predictedDistance < min(minDistance, maxDistance) ||
                            predictedDistance > max(minDistance, maxDistance)
                        ) {
                            predictionFailure = true
                            break
                        }

                        var nearestIndex = 0
                        var nearestDiff = Double.POSITIVE_INFINITY
                        extraction.distancesPx.forEachIndexed { index, value ->
                            val diff = kotlin.math.abs(value - predictedDistance)
                            if (diff < nearestDiff) {
                                nearestDiff = diff
                                nearestIndex = index
                            }
                        }

                        predictions += BalmerPrediction(
                            label = line.label,
                            wavelengthNm = line.wavelengthNm,
                            distancePx = predictedDistance,
                            index = nearestIndex
                        )

                        val (_, lineSupport) =
                            localPatternSupport(
                                troughs = troughs,
                                predictedDistancePx = predictedDistance,
                                tolerancePx = tolerancePx
                            )

                        if (lineSupport >= 0.20) supportCount++
                        supportScore += lineSupport
                    }

                    if (predictionFailure) continue

                    val alpha =
                        predictions.firstOrNull { it.label == "Hα" }
                            ?: continue
                    val delta =
                        predictions.firstOrNull { it.label == "Hδ" }
                            ?: continue

                    // The selected trace is already a RED -> VIOLET visible
                    // spectrum. A physically plausible Balmer solution should
                    // therefore occupy a substantial fraction of that trace,
                    // with Hα toward the RED side and Hδ toward the VIOLET side.
                    val traceStart = min(minDistance, maxDistance)
                    val alphaFraction =
                        (alpha.distancePx - traceStart) / totalDistance
                    val deltaFraction =
                        (delta.distancePx - traceStart) / totalDistance
                    val balmerSpanFraction =
                        (delta.distancePx - alpha.distancePx) / totalDistance

                    // Convert the proposed dispersion back into the wavelength
                    // span represented by the entire selected RED -> VIOLET
                    // trace. This is deliberately broad: endpoint taps are
                    // approximate colour boundaries, not calibrated 400/700 nm
                    // limits.
                    val impliedSpectrumSpanNm =
                        kotlin.math.abs(slope) * totalDistance

                    // Hard plausibility gates. These reject mathematically valid
                    // pairings that cram all four Balmer lines into one small
                    // patch of an otherwise full visible spectrum.
                    if (balmerSpanFraction !in 0.32..0.98) continue
                    if (alphaFraction !in 0.00..0.42) continue
                    if (deltaFraction !in 0.58..1.00) continue
                    if (impliedSpectrumSpanNm !in 260.0..800.0) continue

                    fun gaussianScore(
                        value: Double,
                        centre: Double,
                        width: Double
                    ): Double {
                        val z = (value - centre) / width.coerceAtLeast(1e-9)
                        return kotlin.math.exp(-0.5 * z * z)
                            .coerceIn(0.0, 1.0)
                    }

                    // Soft priors are intentionally broad. They favour a Balmer
                    // series spread across most of a visible trace, but they do
                    // not assume the endpoint taps correspond to exact physical
                    // wavelengths.
                    val spanScore =
                        gaussianScore(
                            value = balmerSpanFraction,
                            centre = 0.72,
                            width = 0.24
                        )
                    val alphaPositionScore =
                        gaussianScore(
                            value = alphaFraction,
                            centre = 0.14,
                            width = 0.18
                        )
                    val deltaPositionScore =
                        gaussianScore(
                            value = deltaFraction,
                            centre = 0.88,
                            width = 0.20
                        )
                    val totalSpanScore =
                        gaussianScore(
                            value = impliedSpectrumSpanNm,
                            centre = 360.0,
                            width = 170.0
                        )

                    val geometryScore =
                        (
                            0.38 * spanScore +
                                0.20 * alphaPositionScore +
                                0.20 * deltaPositionScore +
                                0.22 * totalSpanScore
                            ).coerceIn(0.0, 1.0)

                    if (geometryScore < 0.28) continue

                    val pairDepthScore =
                        (
                            (firstTrough.depth / 0.12).coerceIn(0.0, 1.0) +
                                (secondTrough.depth / 0.12).coerceIn(0.0, 1.0)
                            ) / 2.0

                    val meanSupport = supportScore / lines.size
                    val supportFraction = supportCount / lines.size.toDouble()

                    // Local trough matching remains important, but it can no
                    // longer dominate physically implausible global geometry.
                    val patternScore =
                        (
                            0.38 * meanSupport +
                                0.17 * supportFraction +
                                0.10 * pairDepthScore +
                                0.35 * geometryScore
                            ).coerceIn(0.0, 1.0)

                    if (supportCount < 2) continue

                    val id =
                        predictions.joinToString("|") {
                            "${it.label}:${"%.1f".format(Locale.US, it.distancePx)}"
                        }

                    all += BalmerPatternHypothesis(
                        id = id,
                        predictions = predictions,
                        supportCount = supportCount,
                        patternScore = patternScore,
                        geometryScore = geometryScore,
                        balmerSpanFraction = balmerSpanFraction,
                        impliedSpectrumSpanNm = impliedSpectrumSpanNm,
                        dispersionNmPerPx = slope
                    )
                }
            }
        }
    }

    val deduplicated = mutableListOf<BalmerPatternHypothesis>()

    for (hypothesis in all.sortedByDescending { it.patternScore }) {
        val alpha = hypothesis.predictions.firstOrNull { it.label == "Hα" }
        val beta = hypothesis.predictions.firstOrNull { it.label == "Hβ" }

        val duplicate = deduplicated.any { existing ->
            val existingAlpha =
                existing.predictions.firstOrNull { it.label == "Hα" }
            val existingBeta =
                existing.predictions.firstOrNull { it.label == "Hβ" }

            alpha != null &&
                beta != null &&
                existingAlpha != null &&
                existingBeta != null &&
                kotlin.math.abs(alpha.distancePx - existingAlpha.distancePx) <= tolerancePx &&
                kotlin.math.abs(beta.distancePx - existingBeta.distancePx) <= tolerancePx
        }

        if (!duplicate) {
            deduplicated += hypothesis
        }

        if (deduplicated.size >= 3) break
    }

    return troughs to deduplicated
}

@Composable
private fun BalmerPatternHypothesisPicker(
    hypotheses: List<BalmerPatternHypothesis>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    if (hypotheses.isEmpty()) {
        Text(
            "No sufficiently supported Balmer pattern hypothesis was found in the reliable line-finder regions. You can still place anchors manually.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Pattern-assisted starting points",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "These are ranked hypotheses from detected absorption troughs, known Balmer spacing, and the global RED → VIOLET trace geometry. None is selected automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        hypotheses.forEachIndexed { index, hypothesis ->
            val selected = selectedId == hypothesis.id

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color =
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Hypothesis ${('A'.code + index).toChar()}",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "support ${hypothesis.supportCount}/4 · pattern %.0f%% · geometry %.0f%%".format(
                                Locale.US,
                                hypothesis.patternScore * 100.0,
                                hypothesis.geometryScore * 100.0
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        hypothesis.predictions
                            .sortedByDescending { it.wavelengthNm }
                            .joinToString("   ") {
                                "${it.label} %.0fpx".format(
                                    Locale.US,
                                    it.distancePx
                                )
                            },
                        style = MaterialTheme.typography.bodySmall
                    )

                    Text(
                        "Hα–Hδ spans %.0f%% of trace · implied selected span %.0f nm".format(
                            Locale.US,
                            hypothesis.balmerSpanFraction * 100.0,
                            hypothesis.impliedSpectrumSpanNm
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        "provisional dispersion %.4f nm/px".format(
                            Locale.US,
                            hypothesis.dispersionNmPerPx
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (selected) {
                        Button(
                            onClick = { onSelected("") },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Clear hypothesis")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onSelected(hypothesis.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use as provisional guide")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun BalmerAnchorChart(
    extraction: SpectrumExtraction,
    anchors: List<CalibrationAnchor>,
    activeLabel: String,
    requestedOrder: Int = 2,
    chromaticBootstrap: ChromaticBootstrap? = null,
    consensusGuideAnchors: List<CalibrationAnchor> = emptyList(),
    consensusGuideDirectCount: Int = consensusGuideAnchors.size,
    onPlaceIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoomX by rememberSaveable { mutableFloatStateOf(1f) }
    var centreFraction by rememberSaveable { mutableFloatStateOf(0.5f) }
    var fineIndex by rememberSaveable { mutableStateOf(-1) }
    var magnifying by remember { mutableStateOf(false) }
    var displayMode by rememberSaveable { mutableStateOf("flux") }
    var chartNavigationMode by rememberSaveable { mutableStateOf(true) }
    var selectedPatternId by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(activeLabel) {
        chartNavigationMode = false
    }

    val rawSignal = remember(extraction) { calibrationBaseSignal(extraction) }
    val relativeFlux = remember(extraction) { calibrationRelativeFluxDisplay(extraction) }
    val lineFinder = remember(extraction) { calibrationLineFinderDisplay(extraction) }
    val endpointGuard =
        remember(extraction) {
            endpointGuardFor(extraction)
        }

    val patternBundle = remember(extraction, lineFinder) {
        buildBalmerPatternHypotheses(
            extraction = extraction,
            lineFinder = lineFinder
        )
    }
    val patternTroughs = patternBundle.first
    val patternHypotheses = patternBundle.second

    val selectedPattern = patternHypotheses
        .firstOrNull { it.id == selectedPatternId }

    LaunchedEffect(patternHypotheses, selectedPatternId) {
        if (
            selectedPatternId.isNotBlank() &&
            patternHypotheses.none { it.id == selectedPatternId }
        ) {
            selectedPatternId = ""
        }
    }

    val anchorPredictions = remember(extraction, anchors, requestedOrder) {
        provisionalBalmerPredictions(
            extraction = extraction,
            anchors = anchors,
            requestedOrder = requestedOrder
        )
    }

    val consensusGuidePredictions =
        remember(extraction, consensusGuideAnchors, anchors) {
            if (consensusGuideAnchors.size >= 2) {
                provisionalBalmerPredictions(
                    extraction = extraction,
                    anchors = consensusGuideAnchors,
                    requestedOrder = 1,
                    excludedLabels = anchors.map { it.label }.toSet()
                )
            } else {
                emptyList()
            }
        }
    val consensusGuideActive =
        anchors.size < 2 && consensusGuideAnchors.size >= 2 && consensusGuidePredictions.isNotEmpty()

    val chromaticGuideActive =
        chromaticBootstrap?.let {
            val colourOnlyStrong =
                it.fitQuality >= 0.25 &&
                    it.reliableColourFraction >= 0.15

            val landmarkAssistedStrong =
                it.landmarkCount >= 2 &&
                    it.landmarkAgreement >= 0.50 &&
                    it.reliableColourFraction >= 0.10

            colourOnlyStrong ||
                landmarkAssistedStrong
        } == true

    val chromaticSearchWindows =
        remember(
            chromaticBootstrap,
            extraction,
            anchors
        ) {
            chromaticBootstrap
                ?.let {
                    chromaticBalmerSearchWindows(
                        bootstrap = it,
                        extraction = extraction,
                        anchors = anchors
                    )
                }
                .orEmpty()
        }

    val chromaticPredictions =
        remember(chromaticSearchWindows) {
            chromaticSearchWindows.map { window ->
                BalmerPrediction(
                    label = window.label,
                    wavelengthNm = window.wavelengthNm,
                    distancePx = window.predictedDistancePx,
                    index = window.predictedIndex
                )
            }
        }

    val predictions =
        when {
            anchors.size >= 2 ->
                anchorPredictions

            consensusGuideActive ->
                consensusGuidePredictions

            chromaticGuideActive &&
                chromaticPredictions.isNotEmpty() ->
                chromaticPredictions

            else ->
                selectedPattern
                    ?.predictions
                    ?.filter { prediction ->
                        anchors.none { it.label == prediction.label }
                    }
                    .orEmpty()
        }

    val activePrediction =
        predictions.firstOrNull {
            it.label == activeLabel
        }

    val activeChromaticWindow =
        if (
            anchors.size < 2 &&
            !consensusGuideActive &&
            chromaticGuideActive
        ) {
            chromaticSearchWindows
                .firstOrNull {
                    it.label == activeLabel
                }
        } else {
            null
        }

    val signal = when (displayMode) {
        "direct" -> rawSignal
        "normalised" -> lineFinder.signal
        else -> relativeFlux.signal
    }
    val continuumOverlay = if (displayMode == "flux") relativeFlux.continuum else null

    val paddingX = 18f
    val paddingTop = 26f
    val paddingBottom = 38f

    fun nearestIndex(distancePx: Double): Int {
        if (extraction.distancesPx.isEmpty()) return 0
        var best = 0
        var bestDiff = Double.POSITIVE_INFINITY
        extraction.distancesPx.forEachIndexed { i, value ->
            val diff = kotlin.math.abs(value - distancePx)
            if (diff < bestDiff) {
                best = i
                bestDiff = diff
            }
        }
        return best
    }

    fun visibleBounds(): Pair<Float, Float> {
        val width = (1f / zoomX).coerceIn(0.05f, 1f)
        val minCentre = width / 2f
        val maxCentre = 1f - width / 2f
        val centre = centreFraction.coerceIn(minCentre, maxCentre)
        return (centre - width / 2f) to (centre + width / 2f)
    }

    // Display fraction is conventional spectroscopy order:
    // 0 = VIOLET, 1 = RED.
    // The extraction engine internally stores 0 = RED, last = VIOLET.
    fun sourceIndexFromDisplayFraction(displayFraction: Float): Int {
        if (signal.isEmpty()) return 0
        return ((1f - displayFraction.coerceIn(0f, 1f)) * signal.lastIndex)
            .toInt()
            .coerceIn(0, signal.lastIndex)
    }

    fun displayFractionFromSourceIndex(index: Int): Float {
        if (signal.size <= 1) return 0f
        return 1f - index.coerceIn(0, signal.lastIndex).toFloat() / signal.lastIndex.toFloat()
    }

    fun indexFromScreenX(x: Float): Int {
        if (signal.isEmpty() || size.width <= paddingX * 2) return 0
        val (startF, endF) = visibleBounds()
        val local = ((x - paddingX) / (size.width - paddingX * 2)).coerceIn(0f, 1f)
        val displayFraction = startF + local * (endF - startF)
        return sourceIndexFromDisplayFraction(displayFraction)
            .coerceIn(
                endpointGuard.firstUsableIndex,
                endpointGuard.lastUsableIndex
            )
    }

    fun lineColor(label: String): Color = when (label) {
        "Hα" -> Color(0xFFFF6B6B)
        "Hβ" -> Color(0xFF6FD7FF)
        "Hγ" -> Color(0xFFD29BFF)
        "Hδ" -> Color(0xFFFFC56B)
        else -> Color.White
    }

    val activeIndex = anchors.firstOrNull { it.label == activeLabel }
        ?.let { nearestIndex(it.distancePx) }
        ?: -1

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = displayMode == "flux",
                onClick = { displayMode = "flux" },
                modifier = Modifier.weight(1f),
                label = { Text("Signed signal") }
            )
            FilterChip(
                selected = displayMode == "normalised",
                onClick = { displayMode = "normalised" },
                modifier = Modifier.weight(1f),
                label = { Text("Line finder") }
            )
            FilterChip(
                selected = displayMode == "direct",
                onClick = { displayMode = "direct" },
                modifier = Modifier.weight(1f),
                label = { Text("Direct") }
            )
        }

        Spacer(Modifier.height(8.dp))

        if (consensusGuideActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Automatic consensus wavelength guide active",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "$consensusGuideDirectCount independently detected Balmer line${if (consensusGuideDirectCount == 1) "" else "s"} define the provisional linear wavelength map; ${consensusGuideAnchors.size} total proposal anchor${if (consensusGuideAnchors.size == 1) "" else "s"} are currently available.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Gold guides and missing-line review windows now come from the fitted consensus model, not from the earlier colour bootstrap. Manual placement remains review/fallback evidence only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (!consensusGuideActive && anchors.size < 2 && chromaticBootstrap != null && chromaticGuideActive) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            ) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        "Chromatic wavelength prior active",
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Estimated %.0f–%.0f nm · chromatic fit %.0f%%".format(
                            Locale.US,
                            min(chromaticBootstrap.violetNm, chromaticBootstrap.redNm),
                            max(chromaticBootstrap.violetNm, chromaticBootstrap.redNm),
                            chromaticBootstrap.fitQuality * 100.0
                        ),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (anchors.size == 1) {
                            "One confirmed Balmer line has offset-corrected the chromatic map; the remaining colour-derived search windows are tightened modestly. A second confirmed line will replace the colour prior with the actual two-anchor wavelength solution."
                        } else {
                            "Gold Balmer guides come from the colour-derived wavelength map until stronger wavelength evidence is available. The shaded bands are uncertainty-aware search windows, not exact wavelength claims."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (patternHypotheses.isNotEmpty()) {
                        Text(
                            "Independent trough-pattern search found ${patternHypotheses.size} secondary cross-check hypothesis/hypotheses.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        } else if (anchors.size < 2) {
            BalmerPatternHypothesisPicker(
                hypotheses = patternHypotheses,
                selectedId = selectedPatternId,
                onSelected = { id ->
                    selectedPatternId = id
                    if (id.isNotBlank()) {
                        displayMode = "normalised"
                        chartNavigationMode = false
                    }
                }
            )

            if (patternTroughs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${patternTroughs.size} plausible broad trough candidates were found in reliable regions.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(10.dp))
        } else if (selectedPatternId.isNotBlank()) {
            Text(
                "Two confirmed anchors now define the provisional wavelength solution; manual anchors supersede the pattern hypothesis.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chartNavigationMode) {
                Button(
                    onClick = { chartNavigationMode = true },
                    modifier = Modifier.weight(1f)
                ) { Text("PAN / ZOOM") }
            } else {
                OutlinedButton(
                    onClick = { chartNavigationMode = true },
                    modifier = Modifier.weight(1f)
                ) { Text("PAN / ZOOM") }
            }

            if (!chartNavigationMode) {
                Button(
                    onClick = { chartNavigationMode = false },
                    modifier = Modifier.weight(1f)
                ) { Text("PLACE $activeLabel") }
            } else {
                OutlinedButton(
                    onClick = { chartNavigationMode = false },
                    modifier = Modifier.weight(1f)
                ) { Text("PLACE $activeLabel") }
            }
        }

        Spacer(Modifier.height(8.dp))

        val chartGestureModifier =
            if (chartNavigationMode) {
                Modifier.pointerInput(extraction, displayMode, chartNavigationMode, size) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        if (size.width <= paddingX * 2) return@detectTransformGestures

                        val plotWidth = (size.width - paddingX * 2).coerceAtLeast(1f)
                        val oldZoom = zoomX
                        val newZoom = (oldZoom * zoom).coerceIn(1f, 16f)
                        val oldWidth = 1f / oldZoom
                        val newWidth = 1f / newZoom
                        val oldStart = (centreFraction - oldWidth / 2f)
                            .coerceIn(0f, 1f - oldWidth)
                        val touchLocal = ((centroid.x - paddingX) / plotWidth).coerceIn(0f, 1f)
                        val touchedGlobal = oldStart + touchLocal * oldWidth

                        var newStart = touchedGlobal - touchLocal * newWidth
                        newStart -= (pan.x / plotWidth) * newWidth
                        newStart = newStart.coerceIn(0f, 1f - newWidth)

                        zoomX = newZoom
                        centreFraction = newStart + newWidth / 2f
                    }
                }
            } else {
                Modifier
                    .pointerInput(extraction, activeLabel, displayMode, chartNavigationMode, size) {
                        detectTapGestures { point ->
                            val index = indexFromScreenX(point.x)
                            fineIndex = index
                            onPlaceIndex(index)
                        }
                    }
                    .pointerInput(extraction, activeLabel, displayMode, chartNavigationMode, size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { point ->
                                magnifying = true
                                val index = indexFromScreenX(point.x)
                                fineIndex = index
                                onPlaceIndex(index)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                val index = indexFromScreenX(change.position.x)
                                fineIndex = index
                                onPlaceIndex(index)
                            },
                            onDragEnd = { magnifying = false },
                            onDragCancel = { magnifying = false }
                        )
                    }
            }

        Box(
            Modifier
                .fillMaxWidth()
                .height(330.dp)
                .background(Color(0xFF090D13), RoundedCornerShape(16.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .onSizeChanged { size = it }
                .then(chartGestureModifier)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (signal.isEmpty()) return@Canvas

                val plotW = (this.size.width - paddingX * 2).coerceAtLeast(1f)
                val plotH = (this.size.height - paddingTop - paddingBottom).coerceAtLeast(1f)
                val (startF, endF) = visibleBounds()

                val firstSource = sourceIndexFromDisplayFraction(startF)
                val lastSource = sourceIndexFromDisplayFraction(endF)
                val sourceHigh = max(firstSource, lastSource)
                val sourceLow = min(firstSource, lastSource)

                val visibleValues = (sourceLow..sourceHigh)
                    .filter { extraction.valid[it] && signal[it].isFinite() }
                    .map { signal[it] }

                var localMin: Double
                var localMax: Double

                val sortedVisible =
                    visibleValues
                        .filter { it.isFinite() }
                        .sorted()

                fun visiblePercentile(
                    p: Double,
                    fallback: Double
                ): Double {
                    if (sortedVisible.isEmpty()) {
                        return fallback
                    }

                    val position =
                        p.coerceIn(0.0, 1.0) *
                            (sortedVisible.size - 1)
                    val lo =
                        position.toInt()
                            .coerceIn(
                                0,
                                sortedVisible.lastIndex
                            )
                    val hi =
                        kotlin.math.ceil(position)
                            .toInt()
                            .coerceIn(
                                0,
                                sortedVisible.lastIndex
                            )
                    val f = position - lo

                    return sortedVisible[lo] *
                        (1.0 - f) +
                        sortedVisible[hi] * f
                }

                when (displayMode) {
                    "normalised" -> {
                        val q08 =
                            visiblePercentile(
                                0.08,
                                0.94
                            )
                        val q92 =
                            visiblePercentile(
                                0.92,
                                1.06
                            )
                        val robustSpan =
                            (q92 - q08)
                                .coerceAtLeast(
                                    0.06
                                )

                        localMin =
                            min(
                                q08 -
                                    robustSpan *
                                        0.18,
                                0.94
                            )
                                .coerceAtLeast(
                                    0.45
                                )
                        localMax =
                            max(
                                q92 +
                                    robustSpan *
                                        0.18,
                                1.06
                            )
                                .coerceAtMost(
                                    1.55
                                )
                    }

                    else -> {
                        // Direct and relative-flux calibration views are signed.
                        // Robust quantiles keep isolated JPEG/demosaic spikes
                        // from hiding weak structure while always retaining the
                        // physical zero-background reference line.
                        val q03 =
                            visiblePercentile(
                                0.03,
                                -0.05
                            )
                        val q97 =
                            visiblePercentile(
                                0.97,
                                0.05
                            )
                        val robustSpan =
                            (q97 - q03)
                                .coerceAtLeast(
                                    if (
                                        displayMode ==
                                        "flux"
                                    ) {
                                        0.08
                                    } else {
                                        1e-6
                                    }
                                )

                        localMin =
                            min(
                                q03 -
                                    robustSpan *
                                        0.12,
                                0.0
                            )
                        localMax =
                            max(
                                q97 +
                                    robustSpan *
                                        0.12,
                                0.0
                            )
                    }
                }

                val initialRange =
                    (localMax - localMin)
                        .coerceAtLeast(
                            if (
                                displayMode ==
                                "normalised" ||
                                displayMode ==
                                "flux"
                            ) {
                                0.08
                            } else {
                                1e-6
                            }
                        )

                val verticalPad =
                    initialRange * 0.04
                localMin -= verticalPad
                localMax += verticalPad
                val localRange = (localMax - localMin).coerceAtLeast(1e-6)

                fun xForDisplayFraction(
                    fraction: Float
                ): Float {
                    val local =
                        (
                            (fraction - startF) /
                                (endF - startF)
                            )
                            .coerceIn(
                                0f,
                                1f
                            )
                    return paddingX +
                        plotW * local
                }

                val violetGuardBoundary =
                    displayFractionFromSourceIndex(
                        endpointGuard.lastUsableIndex
                    )
                val redGuardBoundary =
                    displayFractionFromSourceIndex(
                        endpointGuard.firstUsableIndex
                    )

                val violetVisibleEnd =
                    min(
                        endF,
                        violetGuardBoundary
                    )
                if (violetVisibleEnd > startF) {
                    val x1 =
                        xForDisplayFraction(
                            startF
                        )
                    val x2 =
                        xForDisplayFraction(
                            violetVisibleEnd
                        )
                    drawRect(
                        Color(0xFFB78CFF)
                            .copy(
                                alpha = 0.08f
                            ),
                        topLeft =
                            Offset(
                                min(x1, x2),
                                paddingTop
                            ),
                        size =
                            androidx.compose.ui.geometry.Size(
                                kotlin.math.abs(
                                    x2 - x1
                                ),
                                plotH
                            )
                    )
                }

                val redVisibleStart =
                    max(
                        startF,
                        redGuardBoundary
                    )
                if (endF > redVisibleStart) {
                    val x1 =
                        xForDisplayFraction(
                            redVisibleStart
                        )
                    val x2 =
                        xForDisplayFraction(
                            endF
                        )
                    drawRect(
                        Color(0xFFFF6B6B)
                            .copy(
                                alpha = 0.08f
                            ),
                        topLeft =
                            Offset(
                                min(x1, x2),
                                paddingTop
                            ),
                        size =
                            androidx.compose.ui.geometry.Size(
                                kotlin.math.abs(
                                    x2 - x1
                                ),
                                plotH
                            )
                    )
                }

                for (g in 0..4) {
                    val y = paddingTop + plotH * g / 4f
                    drawLine(
                        Color.White.copy(alpha = 0.09f),
                        Offset(paddingX, y),
                        Offset(paddingX + plotW, y),
                        1f
                    )
                }

                if (displayMode == "normalised" && 1.0 in localMin..localMax) {
                    val baselineY =
                        paddingTop + plotH * (1f - ((1.0 - localMin) / localRange).toFloat())
                    drawLine(
                        Color.White.copy(alpha = 0.28f),
                        Offset(paddingX, baselineY),
                        Offset(paddingX + plotW, baselineY),
                        1.5f
                    )
                } else if (0.0 in localMin..localMax) {
                    val zeroY =
                        paddingTop +
                            plotH *
                                (
                                    1f -
                                        (
                                            (0.0 - localMin) /
                                                localRange
                                            )
                                            .toFloat()
                                            .coerceIn(
                                                0f,
                                                1f
                                            )
                                    )
                    drawLine(
                        Color.White.copy(alpha = 0.28f),
                        Offset(paddingX, zeroY),
                        Offset(paddingX + plotW, zeroY),
                        1.5f
                    )
                }

                var previous: Offset? = null
                for (i in sourceHigh downTo sourceLow) {
                    val value = signal[i]
                    if (!value.isFinite()) {
                        previous = null
                        continue
                    }

                    val displayFraction = displayFractionFromSourceIndex(i)
                    if (displayFraction < startF || displayFraction > endF) continue
                    val localF = ((displayFraction - startF) / (endF - startF)).coerceIn(0f, 1f)
                    val x = paddingX + plotW * localF
                    val y = paddingTop + plotH *
                        (1f - ((value - localMin) / localRange).toFloat().coerceIn(0f, 1f))
                    val point = Offset(x, y)
                    previous?.let { drawLine(Color(0xFF67E4CB), it, point, 2.2f) }
                    previous = point
                }

                continuumOverlay?.let { continuum ->
                    var previousContinuum: Offset? = null
                    for (i in sourceHigh downTo sourceLow) {
                        val value = continuum[i]
                        if (!value.isFinite()) {
                            previousContinuum = null
                            continue
                        }

                        val displayFraction = displayFractionFromSourceIndex(i)
                        if (displayFraction < startF || displayFraction > endF) continue
                        val localF =
                            ((displayFraction - startF) / (endF - startF)).coerceIn(0f, 1f)
                        val x = paddingX + plotW * localF
                        val y = paddingTop + plotH *
                            (1f - ((value - localMin) / localRange)
                                .toFloat()
                                .coerceIn(0f, 1f))
                        val point = Offset(x, y)
                        previousContinuum?.let {
                            drawLine(
                                Color.White.copy(alpha = 0.35f),
                                it,
                                point,
                                3.2f
                            )
                        }
                        previousContinuum = point
                    }
                }

                if (
                    anchors.size < 2 &&
                    chromaticGuideActive
                ) {
                    chromaticSearchWindows
                        .forEach { window ->
                            val lowFraction =
                                displayFractionFromSourceIndex(
                                    window.highIndex
                                )
                            val highFraction =
                                displayFractionFromSourceIndex(
                                    window.lowIndex
                                )

                            val visibleLow =
                                max(
                                    min(
                                        lowFraction,
                                        highFraction
                                    ),
                                    startF
                                )
                            val visibleHigh =
                                min(
                                    max(
                                        lowFraction,
                                        highFraction
                                    ),
                                    endF
                                )

                            if (
                                visibleHigh >
                                visibleLow
                            ) {
                                val x1 =
                                    paddingX +
                                        plotW *
                                            (
                                                (visibleLow - startF) /
                                                    (endF - startF)
                                                )
                                                .coerceIn(
                                                    0f,
                                                    1f
                                                )
                                val x2 =
                                    paddingX +
                                        plotW *
                                            (
                                                (visibleHigh - startF) /
                                                    (endF - startF)
                                                )
                                                .coerceIn(
                                                    0f,
                                                    1f
                                                )

                                val active =
                                    window.label ==
                                        activeLabel

                                drawRect(
                                    color =
                                        lineColor(
                                            window.label
                                        )
                                            .copy(
                                                alpha =
                                                    if (active) {
                                                        0.18f
                                                    } else {
                                                        0.08f
                                                    }
                                            ),
                                    topLeft =
                                        Offset(
                                            min(
                                                x1,
                                                x2
                                            ),
                                            paddingTop
                                        ),
                                    size =
                                        androidx.compose.ui.geometry.Size(
                                            kotlin.math.abs(
                                                x2 -
                                                    x1
                                            )
                                                .coerceAtLeast(
                                                    1f
                                                ),
                                            plotH
                                        )
                                )
                            }
                        }
                }

                if (displayMode == "normalised") {
                    patternTroughs.forEach { trough ->
                        val i = trough.index.coerceIn(0, signal.lastIndex)
                        val value = signal[i]
                        if (!value.isFinite()) return@forEach

                        val displayFraction = displayFractionFromSourceIndex(i)
                        if (displayFraction !in startF..endF) return@forEach

                        val localF =
                            ((displayFraction - startF) / (endF - startF))
                                .coerceIn(0f, 1f)
                        val x = paddingX + plotW * localF
                        val y = paddingTop + plotH *
                            (
                                1f -
                                    ((value - localMin) / localRange)
                                        .toFloat()
                                        .coerceIn(0f, 1f)
                                )

                        drawCircle(
                            Color.White.copy(alpha = 0.40f),
                            radius = 3.5f,
                            center = Offset(x, y),
                            style = Stroke(1.2f)
                        )
                    }
                }

                predictions.forEach { prediction ->
                    val i = prediction.index.coerceIn(0, signal.lastIndex)
                    val displayFraction = displayFractionFromSourceIndex(i)
                    if (displayFraction in startF..endF) {
                        val localF =
                            ((displayFraction - startF) / (endF - startF))
                                .coerceIn(0f, 1f)
                        val x = paddingX + plotW * localF
                        val active = prediction.label == activeLabel

                        drawLine(
                            Color(0xFFFFD95A).copy(
                                alpha = if (active) 0.92f else 0.48f
                            ),
                            Offset(x, paddingTop),
                            Offset(x, paddingTop + plotH),
                            if (active) 2.4f else 1.3f
                        )
                        drawCircle(
                            Color(0xFFFFD95A),
                            radius = if (active) 6f else 4f,
                            center = Offset(x, paddingTop + 5f),
                            style = Stroke(if (active) 2.2f else 1.4f)
                        )
                    }
                }

                anchors.forEach { anchor ->
                    val i = nearestIndex(anchor.distancePx)
                    val displayFraction = displayFractionFromSourceIndex(i)
                    if (displayFraction in startF..endF) {
                        val localF = ((displayFraction - startF) / (endF - startF)).coerceIn(0f, 1f)
                        val x = paddingX + plotW * localF
                        val color = lineColor(anchor.label)
                        drawLine(
                            color.copy(alpha = if (anchor.label == activeLabel) 0.98f else 0.78f),
                            Offset(x, paddingTop),
                            Offset(x, paddingTop + plotH),
                            if (anchor.label == activeLabel) 4f else 2.5f
                        )
                        drawCircle(color, 5f, Offset(x, paddingTop + 4f))
                    }
                }

                if (magnifying && fineIndex >= 0) {
                    val displayFraction = displayFractionFromSourceIndex(fineIndex)
                    if (displayFraction in startF..endF) {
                        val localF = ((displayFraction - startF) / (endF - startF)).coerceIn(0f, 1f)
                        val x = paddingX + plotW * localF
                        drawLine(
                            Color.White,
                            Offset(x, paddingTop),
                            Offset(x, paddingTop + plotH),
                            2f
                        )
                    }
                }
            }

            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "VIOLET",
                    color = Color(0xFFB78CFF),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    when (displayMode) {
                        "direct" -> "raw signed extraction"
                        "normalised" ->
                            "line finder · reliable %.0f%%".format(
                                Locale.US,
                                lineFinder.reliableFraction * 100.0
                            )
                        else -> "signed relative signal · zero preserved"
                    },
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "RED",
                    color = Color(0xFFFF6B6B),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (magnifying && fineIndex >= 0) {
                SpectrumLineMagnifier(
                    signal = signal,
                    centreIndex = fineIndex.coerceIn(0, signal.lastIndex),
                    accent = lineColor(activeLabel),
                    normalised = displayMode == "normalised",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 40.dp, end = 14.dp)
                )
            }

            val (startF, endF) = visibleBounds()
            val totalDistance = extraction.distancesPx.lastOrNull()?.coerceAtLeast(0.0) ?: 0.0
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "%.0f px".format(Locale.US, totalDistance * startF),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    when {
                        zoomX > 1.01f && displayMode == "normalised" ->
                            "×%.1f · local line-finder scale".format(Locale.US, zoomX)
                        zoomX > 1.01f ->
                            "×%.1f · broad envelope preserved".format(Locale.US, zoomX)
                        displayMode == "normalised" ->
                            "pinch to zoom · line-finder scale"
                        else ->
                            "pinch to zoom · broad envelope preserved"
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    "%.0f px".format(Locale.US, totalDistance * endF),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "Endpoint guard: the first and last ${endpointGuard.guardSamples} trace samples are shaded and excluded from automatic Balmer finding and local continuum fitting. RED/VIOLET still define the trace and chromatic limits.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(6.dp))

        if (predictions.isNotEmpty()) {
            Text(
                when {
                    anchors.size >= 2 ->
                        "Gold markers are provisional positions predicted from the current confirmed-anchor wavelength solution. They are guides only until you place the corresponding Balmer anchor."

                    consensusGuideActive ->
                        "Gold markers are predicted from the current automatic-consensus linear wavelength solution. Review them against the observed local troughs; the earlier colour prior no longer controls their positions."

                    chromaticGuideActive ->
                        "Gold markers are coarse positions predicted from the photographic colour-to-wavelength bootstrap. Refine each line on the observed local absorption trough before confirming it."

                    else ->
                        "Gold markers come from the selected Balmer-pattern hypothesis. They are provisional guides only; each line must still be placed explicitly."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }

        if (activeIndex < 0 && activePrediction != null) {
            PredictedBalmerLocalView(
                extraction = extraction,
                predictedIndex = activePrediction.index,
                predictedDistancePx = activePrediction.distancePx,
                activeLabel = activePrediction.label,
                wavelengthNm = activePrediction.wavelengthNm,
                searchLowIndex =
                    activeChromaticWindow
                        ?.lowIndex,
                searchHighIndex =
                    activeChromaticWindow
                        ?.highIndex,
                uncertaintyNm =
                    activeChromaticWindow
                        ?.uncertaintyNm,
                uncertaintyClass =
                    activeChromaticWindow
                        ?.widthClass,
                onPlaceIndex = onPlaceIndex
            )
            Spacer(Modifier.height(10.dp))
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Nudge controls follow the displayed VIOLET -> RED direction.
            OutlinedButton(
                onClick = {
                    val base = if (activeIndex >= 0) activeIndex else activePrediction?.index ?: signal.lastIndex / 2
                    onPlaceIndex(
                        (base + 5).coerceIn(
                            endpointGuard.firstUsableIndex,
                            endpointGuard.lastUsableIndex
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("−5") }
            OutlinedButton(
                onClick = {
                    val base = if (activeIndex >= 0) activeIndex else activePrediction?.index ?: signal.lastIndex / 2
                    onPlaceIndex(
                        (base + 1).coerceIn(
                            endpointGuard.firstUsableIndex,
                            endpointGuard.lastUsableIndex
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("−1") }
            OutlinedButton(
                onClick = {
                    val base = if (activeIndex >= 0) activeIndex else activePrediction?.index ?: signal.lastIndex / 2
                    onPlaceIndex(
                        (base - 1).coerceIn(
                            endpointGuard.firstUsableIndex,
                            endpointGuard.lastUsableIndex
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("+1") }
            OutlinedButton(
                onClick = {
                    val base = if (activeIndex >= 0) activeIndex else activePrediction?.index ?: signal.lastIndex / 2
                    onPlaceIndex(
                        (base - 5).coerceIn(
                            endpointGuard.firstUsableIndex,
                            endpointGuard.lastUsableIndex
                        )
                    )
                },
                modifier = Modifier.weight(1f)
            ) { Text("+5") }
        }

        Spacer(Modifier.height(6.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                enabled = activeIndex >= 0,
                onClick = {
                    if (activeIndex >= 0) {
                        val displayFraction = displayFractionFromSourceIndex(activeIndex)
                        zoomX = 6f
                        val width = 1f / zoomX
                        centreFraction = displayFraction.coerceIn(width / 2f, 1f - width / 2f)
                    }
                },
                modifier = Modifier.weight(1f)
            ) { Text("Zoom to active line") }

            OutlinedButton(
                onClick = {
                    zoomX = 1f
                    centreFraction = 0.5f
                },
                modifier = Modifier.weight(1f)
            ) { Text("Fit whole spectrum") }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            when (displayMode) {
                "direct" ->
                    "Direct background-subtracted aperture signal with no display normalisation."
                "normalised" ->
                    "Line finder uses only reliable samples and divides by a broad upper-envelope continuum. Low-signal/channel-handover regions are left blank rather than exploded into ratio spikes."
                else ->
                    "Signed relative signal preserves negative as well as positive background-subtracted structure and overlays its broad continuum in white. The horizontal zero line is the fitted local-background reference; this is still image-derived relative signal, not radiometrically calibrated stellar flux."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


@Composable
private fun PredictedBalmerLocalView(
    extraction: SpectrumExtraction,
    predictedIndex: Int,
    predictedDistancePx: Double,
    activeLabel: String,
    wavelengthNm: Double,
    searchLowIndex: Int? = null,
    searchHighIndex: Int? = null,
    uncertaintyNm: Double? = null,
    uncertaintyClass: String? = null,
    onPlaceIndex: (Int) -> Unit
) {
    if (extraction.corrected.isEmpty()) return

    val fullBase = remember(extraction) {
        spectrumMovingAverage(
            calibrationBaseSignal(extraction),
            radius = 3
        )
    }
    val endpointGuard =
        remember(extraction) {
            endpointGuardFor(extraction)
        }
    val predictionInEndpointGuard =
        endpointGuard.contains(
            predictedIndex.coerceIn(
                0,
                fullBase.lastIndex
            )
        )

    val safePrediction =
        predictedIndex
            .coerceIn(
                endpointGuard.firstUsableIndex
                    .coerceAtMost(
                        fullBase.lastIndex
                    ),
                endpointGuard.lastUsableIndex
                    .coerceIn(
                        0,
                        fullBase.lastIndex
                    )
            )

    val fallbackHalfWindow =
        max(
            36,
            fullBase.size / 16
        )
            .coerceAtMost(
                92
            )

    val requestedLow =
        searchLowIndex
            ?.coerceIn(
                endpointGuard.firstUsableIndex,
                endpointGuard.lastUsableIndex
            )
    val requestedHigh =
        searchHighIndex
            ?.coerceIn(
                endpointGuard.firstUsableIndex,
                endpointGuard.lastUsableIndex
            )

    val requestedWindowLow =
        if (
            requestedLow != null &&
            requestedHigh != null
        ) {
            min(
                requestedLow,
                requestedHigh
            )
        } else {
            null
        }

    val requestedWindowHigh =
        if (
            requestedLow != null &&
            requestedHigh != null
        ) {
            max(
                requestedLow,
                requestedHigh
            )
        } else {
            null
        }

    val requestedWindowWidth =
        if (
            requestedWindowLow != null &&
            requestedWindowHigh != null
        ) {
            (
                requestedWindowHigh -
                    requestedWindowLow
                )
                .coerceAtLeast(
                    1
                )
        } else {
            0
        }

    val contextMargin =
        if (requestedWindowWidth > 0) {
            max(
                12,
                requestedWindowWidth / 3
            )
        } else {
            0
        }

    val lowIndex =
        requestedWindowLow
            ?.let {
                (
                    it -
                        contextMargin
                    )
                    .coerceAtLeast(
                        endpointGuard.firstUsableIndex
                    )
            }
            ?: (
                safePrediction -
                    fallbackHalfWindow
                )
                .coerceAtLeast(
                    endpointGuard.firstUsableIndex
                )

    val highIndex =
        requestedWindowHigh
            ?.let {
                (
                    it +
                        contextMargin
                    )
                    .coerceAtMost(
                        endpointGuard.lastUsableIndex
                    )
            }
            ?: (
                safePrediction +
                    fallbackHalfWindow
                )
                .coerceAtMost(
                    endpointGuard.lastUsableIndex
                )

    val hasPredictedSearchWindow =
        requestedWindowLow != null &&
            requestedWindowHigh != null

    var searchExpanded by
        rememberSaveable(
            activeLabel,
            safePrediction,
            requestedWindowLow,
            requestedWindowHigh
        ) {
            mutableStateOf(false)
        }

    val lockedLowIndex =
        if (
            hasPredictedSearchWindow &&
            !searchExpanded
        ) {
            requestedWindowLow!!
                .coerceIn(
                    lowIndex,
                    highIndex
                )
        } else {
            lowIndex
        }

    val lockedHighIndex =
        if (
            hasPredictedSearchWindow &&
            !searchExpanded
        ) {
            requestedWindowHigh!!
                .coerceIn(
                    lowIndex,
                    highIndex
                )
        } else {
            highIndex
        }

    val activeAllowedLow =
        min(
            lockedLowIndex,
            lockedHighIndex
        )
    val activeAllowedHigh =
        max(
            lockedLowIndex,
            lockedHighIndex
        )

    val localSignal =
        remember(
            extraction,
            safePrediction,
            lowIndex,
            highIndex
        ) {
            val out =
                DoubleArray(
                    fullBase.size
                ) {
                    Double.NaN
                }

            val span =
                max(
                    1,
                    highIndex -
                        lowIndex
                )
            val shoulderWidth =
                max(
                    8,
                    span / 5
                )

            val leftValues =
                (
                    lowIndex..
                        min(
                            highIndex,
                            lowIndex +
                                shoulderWidth
                        )
                    )
                    .map {
                        fullBase[it]
                    }
                    .filter {
                        it.isFinite()
                    }
                    .sorted()

            val rightValues =
                (
                    max(
                        lowIndex,
                        highIndex -
                            shoulderWidth
                    )..
                        highIndex
                    )
                    .map {
                        fullBase[it]
                    }
                    .filter {
                        it.isFinite()
                    }
                    .sorted()

            fun median(
                values: List<Double>
            ): Double {
                if (values.isEmpty()) {
                    return Double.NaN
                }

                return if (
                    values.size % 2 == 1
                ) {
                    values[
                        values.size / 2
                    ]
                } else {
                    (
                        values[
                            values.size / 2 -
                                1
                        ] +
                            values[
                                values.size / 2
                            ]
                        ) / 2.0
                }
            }

            val leftBaseline =
                median(
                    leftValues
                )
            val rightBaseline =
                median(
                    rightValues
                )

            if (
                leftBaseline.isFinite() &&
                rightBaseline.isFinite()
            ) {
                for (
                    i in
                    lowIndex..
                        highIndex
                ) {
                    val f =
                        (
                            i -
                                lowIndex
                            ).toDouble() /
                            span.toDouble()

                    val localBaseline =
                        leftBaseline *
                            (1.0 - f) +
                            rightBaseline *
                                f

                    val value =
                        fullBase[i]

                    if (
                        value.isFinite() &&
                        localBaseline
                            .isFinite()
                    ) {
                        // Signed local residual. A real absorption trough should
                        // move downward relative to the shoulder-fitted baseline.
                        // No positivity constraint or ratio division is applied.
                        out[i] =
                            value -
                                localBaseline
                    }
                }
            }

            out
        }

    var cursorIndex by
        remember(
            activeLabel,
            safePrediction
        ) {
            mutableStateOf(
                safePrediction.coerceIn(
                    activeAllowedLow,
                    activeAllowedHigh
                )
            )
        }

    LaunchedEffect(
        activeAllowedLow,
        activeAllowedHigh,
        searchExpanded,
        activeLabel
    ) {
        cursorIndex =
            cursorIndex.coerceIn(
                activeAllowedLow,
                activeAllowedHigh
            )
    }

    var chartSize by
        remember {
            mutableStateOf(
                IntSize.Zero
            )
        }

    val signal = localSignal
    val finiteValues =
        (lowIndex..highIndex)
            .map {
                signal[it]
            }
            .filter {
                it.isFinite()
            }
            .sorted()

    fun localPercentile(
        p: Double
    ): Double {
        if (finiteValues.isEmpty()) {
            return 0.0
        }

        val position =
            p.coerceIn(
                0.0,
                1.0
            ) *
                (
                    finiteValues.size -
                        1
                    )
        val lo =
            position.toInt()
                .coerceIn(
                    0,
                    finiteValues.lastIndex
                )
        val hi =
            kotlin.math.ceil(
                position
            )
                .toInt()
                .coerceIn(
                    0,
                    finiteValues.lastIndex
                )
        val f =
            position -
                lo

        return finiteValues[lo] *
            (1.0 - f) +
            finiteValues[hi] *
                f
    }

    val q05 =
        localPercentile(
            0.05
        )
    val q95 =
        localPercentile(
            0.95
        )
    val robustSpan =
        (q95 - q05)
            .coerceAtLeast(
                1e-6
            )

    var localMin =
        min(
            q05 -
                robustSpan *
                    0.18,
            0.0
        )
    var localMax =
        max(
            q95 +
                robustSpan *
                    0.18,
            0.0
        )

    if (
        kotlin.math.abs(
            localMax -
                localMin
        ) <
        1e-6
    ) {
        localMin = -1e-3
        localMax = 1e-3
    }

    val range =
        (localMax - localMin)
            .coerceAtLeast(
                1e-6
            )

    fun indexFromX(x: Float): Int {
        if (chartSize.width <= 0) return safePrediction
        val f = (x / chartSize.width).coerceIn(0f, 1f)

        // Display order is VIOLET -> RED, so larger source indices are on the left.
        return (
            highIndex -
                f *
                    (
                        highIndex -
                            lowIndex
                        )
                        .toFloat()
            )
            .toInt()
            .coerceIn(
                activeAllowedLow,
                activeAllowedHigh
            )
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "$activeLabel predicted neighbourhood",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        buildString {
                            append(
                                "%.2f nm · provisional %.1f px"
                                    .format(
                                        Locale.US,
                                        wavelengthNm,
                                        predictedDistancePx
                                    )
                            )
                            if (
                                uncertaintyNm != null
                            ) {
                                append(
                                    " · ±%.0f nm %s"
                                        .format(
                                            Locale.US,
                                            uncertaintyNm,
                                            uncertaintyClass
                                                ?: "window"
                                        )
                                )
                            }
                        },
                        style =
                            MaterialTheme.typography.bodySmall,
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }
                Text(
                    "VIOLET → RED",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (predictionInEndpointGuard) {
                Text(
                    "The provisional position falls inside the endpoint guard zone. MethodMesh has moved the local refinement view to the nearest usable interior sample; edge falloff is not eligible as a Balmer anchor.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF090D13))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    )
                    .onSizeChanged { chartSize = it }
                    .pointerInput(
                        signal,
                        safePrediction,
                        lowIndex,
                        highIndex,
                        activeAllowedLow,
                        activeAllowedHigh,
                        searchExpanded
                    ) {
                        detectTapGestures { point ->
                            cursorIndex = indexFromX(point.x)
                        }
                    }
                    .pointerInput(
                        signal,
                        safePrediction,
                        lowIndex,
                        highIndex,
                        activeAllowedLow,
                        activeAllowedHigh,
                        searchExpanded
                    ) {
                        detectDragGestures(
                            onDragStart = { point ->
                                cursorIndex = indexFromX(point.x)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                cursorIndex = indexFromX(change.position.x)
                            }
                        )
                    }
            ) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val w = size.width.coerceAtLeast(1f)
                    val h = size.height.coerceAtLeast(1f)

                    if (
                        searchLowIndex != null &&
                        searchHighIndex != null
                    ) {
                        val span =
                            max(
                                1,
                                highIndex -
                                    lowIndex
                            )

                        fun localXFor(
                            index: Int
                        ): Float =
                            w *
                                (
                                    highIndex -
                                        index
                                    ) /
                                span.toFloat()

                        val x1 =
                            localXFor(
                                searchLowIndex
                                    .coerceIn(
                                        lowIndex,
                                        highIndex
                                    )
                            )
                        val x2 =
                            localXFor(
                                searchHighIndex
                                    .coerceIn(
                                        lowIndex,
                                        highIndex
                                    )
                            )

                        drawRect(
                            color =
                                Color(0xFFFFD95A)
                                    .copy(
                                        alpha = 0.12f
                                    ),
                            topLeft =
                                Offset(
                                    min(
                                        x1,
                                        x2
                                    ),
                                    0f
                                ),
                            size =
                                androidx.compose.ui.geometry.Size(
                                    kotlin.math.abs(
                                        x2 -
                                            x1
                                    )
                                        .coerceAtLeast(
                                            1f
                                        ),
                                    h
                                )
                        )
                    }

                    if (0.0 in localMin..localMax) {
                        val y =
                            h * (
                                1f -
                                    ((0.0 - localMin) / range)
                                        .toFloat()
                                        .coerceIn(0f, 1f)
                                )
                        drawLine(
                            Color.White.copy(alpha = 0.26f),
                            Offset(0f, y),
                            Offset(w, y),
                            1.4f
                        )
                    }

                    var previous: Offset? = null
                    for (sourceIndex in highIndex downTo lowIndex) {
                        val value = signal[sourceIndex]
                        if (!value.isFinite()) {
                            previous = null
                            continue
                        }

                        val displayIndex = highIndex - sourceIndex
                        val span = max(1, highIndex - lowIndex)
                        val x = w * displayIndex / span.toFloat()
                        val y =
                            h * (
                                1f -
                                    ((value - localMin) / range)
                                        .toFloat()
                                        .coerceIn(0f, 1f)
                                )
                        val point = Offset(x, y)

                        previous?.let {
                            drawLine(
                                Color(0xFF67E4CB),
                                it,
                                point,
                                2.6f
                            )
                        }
                        previous = point
                    }

                    fun xFor(index: Int): Float {
                        val span = max(1, highIndex - lowIndex)
                        return w * (highIndex - index) / span.toFloat()
                    }

                    val predictionX = xFor(safePrediction)
                    drawLine(
                        Color(0xFFFFD95A).copy(alpha = 0.80f),
                        Offset(predictionX, 0f),
                        Offset(predictionX, h),
                        2f
                    )

                    val cursorX = xFor(cursorIndex)
                    drawLine(
                        Color.White,
                        Offset(cursorX, 0f),
                        Offset(cursorX, h),
                        2.4f
                    )
                }

                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "VIOLET",
                        color = Color(0xFFB78CFF),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (uncertaintyNm != null) {
                            "gold band = search window · zero line = local baseline · white = cursor"
                        } else {
                            "gold = predicted · white = cursor"
                        },
                        color = Color.White.copy(alpha = 0.60f),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        "RED",
                        color = Color(0xFFFF6B6B),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (hasPredictedSearchWindow) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(
                            8.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {
                    Surface(
                        shape =
                            RoundedCornerShape(
                                10.dp
                            ),
                        color =
                            if (searchExpanded) {
                                MaterialTheme
                                    .colorScheme
                                    .errorContainer
                            } else {
                                MaterialTheme
                                    .colorScheme
                                    .primaryContainer
                            }
                    ) {
                        Text(
                            if (searchExpanded) {
                                "SEARCH EXPANDED"
                            } else {
                                "LOCKED TO GOLD WINDOW"
                            },
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        10.dp,
                                    vertical =
                                        7.dp
                                ),
                            style =
                                MaterialTheme
                                    .typography
                                    .labelSmall,
                            fontWeight =
                                FontWeight.Bold,
                            color =
                                if (searchExpanded) {
                                    MaterialTheme
                                        .colorScheme
                                        .onErrorContainer
                                } else {
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimaryContainer
                                }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (searchExpanded) {
                                searchExpanded =
                                    false
                                cursorIndex =
                                    cursorIndex
                                        .coerceIn(
                                            requestedWindowLow!!,
                                            requestedWindowHigh!!
                                        )
                            } else {
                                searchExpanded =
                                    true
                            }
                        },
                        modifier =
                            Modifier.weight(
                                1f
                            )
                    ) {
                        Text(
                            if (searchExpanded) {
                                "Relock to prediction"
                            } else {
                                "Expand search"
                            }
                        )
                    }
                }

                Text(
                    if (searchExpanded) {
                        "Expanded mode allows manual inspection across the wider endpoint-safe local context. Relock before returning to the colour-derived search window."
                    } else {
                        "Tap, drag and ±1/±5 controls are constrained to the gold predicted search window."
                    },
                    style =
                        MaterialTheme
                            .typography
                            .labelSmall,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            val cursorValue =
                signal.getOrNull(
                    cursorIndex
                )
            val cursorDistance =
                extraction
                    .distancesPx
                    .getOrNull(
                        cursorIndex
                    )

            Text(
                if (cursorValue != null && cursorValue.isFinite()) {
                    "Cursor %.1f px from RED origin · signed residual %.4f".format(
                        Locale.US,
                        cursorDistance ?: Double.NaN,
                        cursorValue
                    )
                } else {
                    "Cursor %.1f px from RED origin · no finite extracted sample".format(
                        Locale.US,
                        cursorDistance ?: Double.NaN
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        cursorIndex =
                            (cursorIndex + 5)
                                .coerceAtMost(
                                    activeAllowedHigh
                                )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("←5")
                }
                OutlinedButton(
                    onClick = {
                        cursorIndex =
                            (cursorIndex + 1)
                                .coerceAtMost(
                                    activeAllowedHigh
                                )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("←1")
                }
                OutlinedButton(
                    onClick = {
                        cursorIndex =
                            (cursorIndex - 1)
                                .coerceAtLeast(
                                    activeAllowedLow
                                )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("1→")
                }
                OutlinedButton(
                    onClick = {
                        cursorIndex =
                            (cursorIndex - 5)
                                .coerceAtLeast(
                                    activeAllowedLow
                                )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("5→")
                }
            }

            Button(
                enabled =
                    signal
                        .getOrNull(
                            cursorIndex
                        )
                        ?.isFinite() ==
                        true &&
                        (
                            searchExpanded ||
                                !hasPredictedSearchWindow ||
                                cursorIndex in
                                    activeAllowedLow..
                                        activeAllowedHigh
                            ),
                onClick = {
                    onPlaceIndex(
                        cursorIndex
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {
                Text(
                    if (
                        searchExpanded &&
                        hasPredictedSearchWindow
                    ) {
                        "Set $activeLabel here · expanded"
                    } else {
                        "Set $activeLabel here"
                    }
                )
            }

            Text(
                if (uncertaintyNm != null) {
                    if (searchExpanded) {
                        "Expanded search is an explicit override. The original gold band remains the colour-derived prior; inspect outside it only when the observed spectrum gives a defensible reason."
                    } else {
                        "This local view subtracts a straight baseline estimated from the shoulders of the window and preserves the signed extracted signal. Placement is locked to the gold colour-derived search window; a Balmer absorption trough should appear as a downward local excursion."
                    }
                } else {
                    "This local view subtracts a straight shoulder-fitted baseline and preserves signed extracted structure. Place the white cursor on the observed downward absorption minimum."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SpectrumLineMagnifier(
    signal: DoubleArray,
    centreIndex: Int,
    accent: Color,
    normalised: Boolean,
    modifier: Modifier = Modifier
) {
    if (signal.isEmpty()) return

    val start = (centreIndex - 24).coerceAtLeast(0)
    val end = (centreIndex + 24).coerceAtMost(signal.lastIndex)
    val values = if (start <= end) signal.slice(start..end) else emptyList()
    val finiteValues = values.filter { it.isFinite() }

    var localMin = finiteValues.minOrNull() ?: if (normalised) 0.90 else 0.0
    var localMax = finiteValues.maxOrNull() ?: if (normalised) 1.10 else 1.0
    if (normalised) {
        localMin = min(localMin, 0.98)
        localMax = max(localMax, 1.02)
    }
    val initialRange = (localMax - localMin).coerceAtLeast(if (normalised) 0.02 else 1e-6)
    localMin -= initialRange * 0.10
    localMax += initialRange * 0.10
    val localRange = (localMax - localMin).coerceAtLeast(1e-6)

    Surface(
        modifier = modifier
            .width(210.dp)
            .height(165.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF05080D).copy(alpha = 0.97f),
        border = androidx.compose.foundation.BorderStroke(2.dp, accent)
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            if (values.isEmpty()) return@Canvas
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)

            if (normalised && 1.0 in localMin..localMax) {
                val y =
                    h *
                        (
                            1f -
                                (
                                    (1.0 - localMin) /
                                        localRange
                                    )
                                    .toFloat()
                            )
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(0f, y),
                    Offset(w, y),
                    1f
                )
            } else if (0.0 in localMin..localMax) {
                val y =
                    h *
                        (
                            1f -
                                (
                                    (0.0 - localMin) /
                                        localRange
                                    )
                                    .toFloat()
                                    .coerceIn(
                                        0f,
                                        1f
                                    )
                            )
                drawLine(
                    Color.White.copy(alpha = 0.25f),
                    Offset(0f, y),
                    Offset(w, y),
                    1f
                )
            }

            var previous: Offset? = null
            values.forEachIndexed { local, value ->
                if (!value.isFinite()) {
                    previous = null
                    return@forEachIndexed
                }
                val x = w * local / max(1, values.lastIndex).toFloat()
                val y = h *
                    (1f - ((value - localMin) / localRange).toFloat().coerceIn(0f, 1f))
                val p = Offset(x, y)
                previous?.let { drawLine(Color(0xFF67E4CB), it, p, 2.4f) }
                previous = p
            }

            val centreLocal = (centreIndex - start).coerceIn(0, values.lastIndex)
            val cx = w * centreLocal / max(1, values.lastIndex).toFloat()
            drawLine(Color.Black.copy(alpha = 0.8f), Offset(cx, 0f), Offset(cx, h), 5f)
            drawLine(accent, Offset(cx, 0f), Offset(cx, h), 2f)
        }
    }
}

@Composable
internal fun FeatureSearchQaLegend(
    extraction: SpectrumExtraction
) {
    val guard =
        SpectrumFeatureSearchQa.endpointGuardSamples(
            extraction.distancesPx.size
        )
    val calibration = extraction.calibration
    val anchors = calibration?.reference?.anchors.orEmpty()

    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Feature-search QA",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Endpoint guard: first and last $guard samples remain visible/exported but are excluded from automatic feature detection.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (calibration != null && anchors.isNotEmpty()) {
            val minAnchor = anchors.minOf { it.wavelengthNm }
            val maxAnchor = anchors.maxOf { it.wavelengthNm }
            Text(
                "Anchor-bracketed calibration: %.2f–%.2f nm. Automatic detections outside that interval are marked extrapolated and their confidence is down-weighted."
                    .format(
                        Locale.US,
                        minAnchor,
                        maxAnchor
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (calibration == null) {
            Text(
                "No wavelength reference applied: detections are in uncalibrated pixel space.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun FeatureList(features: List<SpectralFeature>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    if (features.isEmpty()) {
        Text("No features reached the current robust significance threshold.", style = MaterialTheme.typography.bodyMedium)
        return
    }
    features.take(40).forEach { feature ->
        val location = feature.wavelengthNm?.let { "%.2f nm".format(Locale.US, it) }
            ?: "%.1f px".format(Locale.US, feature.distancePx)
        val methodLabel =
            when (feature.detectionMethod) {
                FeatureDetectionMethod.Narrow ->
                    "narrow"
                FeatureDetectionMethod.BroadMultiScale ->
                    "broad multi-scale"
                FeatureDetectionMethod.NarrowAndBroad ->
                    "narrow + broad"
            }
        val line = "$location · ${feature.type.wireValue} · $methodLabel · scale %.1f px · FWHM %.1f px · S/N %.1f · confidence %.2f · ${feature.searchZone.wireValue}".format(
            Locale.US,
            feature.detectionScalePx,
            feature.fwhmPx,
            feature.snr,
            feature.confidence
        )
        Surface(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { copyToClipboard(context, "Spectral feature", line) },
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(location, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    val zoneLabel =
                        when (feature.searchZone) {
                            FeatureSearchZone.AnchorBracketed -> "anchor-bracketed"
                            FeatureSearchZone.Extrapolated -> "extrapolated"
                            FeatureSearchZone.EndpointGuard -> "endpoint guard"
                            FeatureSearchZone.Uncalibrated -> "uncalibrated"
                        }
                    Text(
                        "${feature.type.wireValue} · $methodLabel · scale %.1f px · FWHM %.1f px".format(
                            Locale.US,
                            feature.detectionScalePx,
                            feature.fwhmPx
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "S/N %.1f · stability %.2f · $zoneLabel".format(
                            Locale.US,
                            feature.snr,
                            feature.stability
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color =
                            if (feature.searchZone == FeatureSearchZone.Extrapolated) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                    )
                }
                Text("%.0f%%".format(Locale.US, feature.confidence * 100.0), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
internal fun ReferenceChips(
    references: List<SpectrumReference>,
    selectedId: String,
    onSelected: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = selectedId.isBlank(), onClick = { onSelected("") }, label = { Text("Pixels only") })
        references.forEach { reference ->
            FilterChip(
                selected = selectedId == reference.id,
                onClick = { onSelected(reference.id) },
                label = { Text(reference.name.ifBlank { reference.starName.ifBlank { reference.id.take(8) } }) }
            )
        }
    }
}


@Composable
internal fun SpectrumReferenceSelectorDialog(
    references: List<SpectrumReference>,
    selectedId: String,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Reference calibration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Choose the saved calibration profile to apply to this spectrum. Profiles are never selected automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    val pixelsSelected = selectedId.isBlank()

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelected("")
                            },
                        shape = RoundedCornerShape(14.dp),
                        color =
                            if (pixelsSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (pixelsSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Column(
                            Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Uncalibrated",
                                    fontWeight = FontWeight.Bold
                                )
                                if (pixelsSelected) {
                                    Text(
                                        "SELECTED",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                "Pixel / distance axis only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    references.forEach { reference ->
                        val selected = reference.id == selectedId
                        val title =
                            reference.name.ifBlank {
                                reference.starName.ifBlank {
                                    "Reference ${reference.id.take(8)}"
                                }
                            }
                        val star =
                            reference.starName.ifBlank {
                                "Unnamed reference star"
                            }
                        val model =
                            if (reference.polynomialOrder == 1) {
                                "Linear"
                            } else {
                                "Quadratic"
                            }
                        val saved =
                            reference.createdTimeIso
                                .replace("T", " ")
                                .replace("Z", "")
                                .take(16)

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(reference.id)
                                },
                            shape = RoundedCornerShape(14.dp),
                            color =
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant
                                }
                            )
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        title,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (selected) {
                                        Text(
                                            "SELECTED",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Text(
                                    "$star · ${reference.spectralType.ifBlank { "unknown type" }}",
                                    style = MaterialTheme.typography.bodySmall
                                )

                                Text(
                                    "$model · ${reference.anchors.size} Balmer anchors · RMS %.3f nm"
                                        .format(
                                            Locale.US,
                                            reference.rmsNm
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    buildString {
                                        if (saved.isNotBlank()) {
                                            append("Saved $saved")
                                            append(" · ")
                                        }
                                        append("ID …${reference.id.takeLast(8)}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

internal fun fittedRect(box: IntSize, bitmap: Bitmap): Rect {
    if (box.width <= 0 || box.height <= 0 || bitmap.width <= 0 || bitmap.height <= 0) return Rect.Zero
    val scale = min(box.width.toFloat() / bitmap.width, box.height.toFloat() / bitmap.height)
    val width = bitmap.width * scale
    val height = bitmap.height * scale
    val left = (box.width - width) / 2f
    val top = (box.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun imageToScreen(point: Offset, box: IntSize, bitmap: Bitmap, zoom: Float, pan: Offset): Offset? {
    val fit = fittedRect(box, bitmap)
    if (fit == Rect.Zero) return null
    val base = Offset(
        fit.left + point.x * fit.width / bitmap.width,
        fit.top + point.y * fit.height / bitmap.height
    )
    val centre = Offset(box.width / 2f, box.height / 2f)
    return centre + (base - centre) * zoom + pan
}

private fun screenToImage(point: Offset, box: IntSize, bitmap: Bitmap, zoom: Float, pan: Offset): Offset? {
    val fit = fittedRect(box, bitmap)
    if (fit == Rect.Zero) return null
    val centre = Offset(box.width / 2f, box.height / 2f)
    val base = centre + (point - pan - centre) / zoom
    if (base.x < fit.left || base.x > fit.right || base.y < fit.top || base.y > fit.bottom) return null
    return Offset(
        ((base.x - fit.left) / fit.width * bitmap.width).coerceIn(0f, bitmap.width - 1f),
        ((base.y - fit.top) / fit.height * bitmap.height).coerceIn(0f, bitmap.height - 1f)
    )
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    if (text.isBlank()) return
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    android.widget.Toast.makeText(context, "Copied", android.widget.Toast.LENGTH_SHORT).show()
}

internal fun shareSpectrumArtifacts(
    context: Context,
    label: String,
    attachments: List<Pair<String, String>>,
    text: String = "",
    fullJson: String = ""
) {
    val shareAttachments = attachments
        .filter { (_, uri) -> uri.isNotBlank() }
        .map { (name, uri) ->
            com.example.methodmesh.transport.ResultShare.Attachment(
                name = name,
                uri = android.net.Uri.parse(uri)
            )
        }
    com.example.methodmesh.transport.ResultShare.share(
        context = context,
        chooserTitle = "Share $label",
        text = text,
        attachments = shareAttachments,
        jsonText = fullJson,
        fileLabel = label
    )
}

internal fun saveSpectrumArtifactsToDownloads(context: Context, label: String, uris: List<String>, summary: String, fullJson: String = ""): String {
    val saved = com.example.methodmesh.transport.OutputExportRepository.saveToDownloads(
        context = context,
        label = label,
        text = summary,
        mediaUris = uris.filter { it.isNotBlank() },
        jsonText = fullJson
    )
    return saved.summary
}

internal fun contextInput(context: com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext, key: String): String {
    return context.action.settings[key]
        ?: context.action.settings["input_$key"]
        ?: context.request.settings[key]
        ?: context.request.settings["input_$key"]
        ?: ""
}

internal fun initialiseSpectrumSettings(
    state: com.example.methodmesh.settings.SettingsState,
    definitions: List<com.example.methodmesh.settings.MethodSetting>,
    context: com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
) {
    val byId = definitions.associateBy { it.id }
    byId.forEach { (id, definition) ->
        val raw = contextInput(context, id)
        if (raw.isBlank()) return@forEach
        when (definition) {
            is com.example.methodmesh.settings.MethodSetting.BooleanSetting -> state.setBoolean(id, raw.equals("true", true))
            is com.example.methodmesh.settings.MethodSetting.IntSetting -> raw.toIntOrNull()?.let { state.setInt(id, it) }
            is com.example.methodmesh.settings.MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { state.setFloat(id, it) }
            else -> state.setString(id, raw)
        }
    }
}
