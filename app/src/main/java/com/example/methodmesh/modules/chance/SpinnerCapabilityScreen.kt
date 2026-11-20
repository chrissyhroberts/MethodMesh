package com.example.methodmesh.modules.chance

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.rememberRetainedExecutionResult
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.sin

private enum class SpinnerStage { Configure, Ready, Spinning, Landed, Committed }
private val SpinnerBackground = Color(0xFF101827)
private val SpinnerAccent = Color(0xFFFFD54F)

/** Presentation/session state only. Every draw still uses the canonical spinner method. */
object SpinnerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SpinnerMethod.ID
    override val title = "Spinner"
    override val description = "A full-screen choice wheel, with optional removal of each winner."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var items by rememberSaveable { mutableStateOf(context.chanceSetting("items", "Yes\nNo")) }
        var removeWinner by rememberSaveable { mutableStateOf(context.chanceBoolSetting("remove_after_spin", false)) }
        var rngMode by rememberSaveable { mutableStateOf(context.chanceSetting("rng_mode", "secure_random")) }
        var seed by rememberSaveable { mutableStateOf(context.chanceSetting("seed", "")) }
        var animation by rememberSaveable { mutableStateOf(context.chanceSetting("animation_mode", "full")) }
        var stage by rememberSaveable { mutableStateOf(SpinnerStage.Configure) }
        // Blank means not started; [] means exhausted. Never silently refill an empty pool.
        var remainingJson by rememberSaveable { mutableStateOf("") }
        var valuesJson by rememberSaveable { mutableStateOf("") }
        var frame by rememberSaveable { mutableStateOf(0) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var error by rememberSaveable { mutableStateOf("") }
        var execution by rememberRetainedExecutionResult("spinner:${context.action.canonicalId}")
        val values = remember(valuesJson) { spinnerValues(valuesJson) }
        val initialItems = remember(items) { ChanceSelectionEngine.parseItems(items) }
        val remaining = remember(remainingJson, items) {
            if (remainingJson.isBlank()) initialItems else spinnerItems(remainingJson)
        }
        val roundItems = remember(valuesJson) { spinnerItems(values[SpinnerFields.ITEMS_JSON].orEmpty()) }
        val selectedIndex = (values[SpinnerFields.SELECTED_INDEX]?.toIntOrNull() ?: 1) - 1
        val resultText = values[SpinnerFields.RESULT].orEmpty()

        fun settings(pool: List<String>) = mapOf(
            "items" to pool.joinToString("\n"), "remove_after_spin" to removeWinner.toString(),
            "rng_mode" to rngMode, "seed" to seed, "animation_mode" to animation
        )

        LaunchedEffect(items, removeWinner, rngMode, seed, animation) {
            // Preserve the authored roster, not the progressively depleted operational pool.
            context.onSettingsChanged(settings(initialItems))
        }

        fun clearResult() { valuesJson = ""; execution = null; error = ""; frame = 0 }
        fun restoreAll() { clearResult(); remainingJson = JSONArray(initialItems).toString(); stage = SpinnerStage.Ready }
        fun configure() { clearResult(); remainingJson = ""; stage = SpinnerStage.Configure }
        fun land(round: Map<String, String>) {
            remainingJson = round.getValue(SpinnerFields.REMAINING_ITEMS_JSON)
            frame = 100
            stage = SpinnerStage.Landed
        }
        fun spin() {
            if (stage == SpinnerStage.Spinning || stage == SpinnerStage.Committed) return
            // Also let the canonical method return a useful failure for invalid external inputs.
            if (remaining.isEmpty() && stage != SpinnerStage.Configure) return
            val roundSettings = settings(remaining)
            val request = As100SpinnerMethod.request(capabilityId,
                context.request.invocationContext.asMap(capabilityId) + context.action.settings + roundSettings,
                emptyList(), emptyList())
            val generated = As100SpinnerMethod.generate(roundSettings)
            val result = As100SpinnerMethod.result(request, generated, context.request.invocationContext)
            if (context.submitsImmediately) { onConfirmed(result); return }
            if (generated[SpinnerFields.STATUS] != "succeeded") {
                error = generated[SpinnerFields.ERROR].orEmpty()
                return
            }
            execution = result
            valuesJson = JSONObject(generated).toString()
            error = ""
            frame = 0
            if (animation == "off") land(generated) else stage = SpinnerStage.Spinning
        }

        val settingIds = listOf("items", "remove_after_spin", "rng_mode", "seed", "animation_mode")
        val autoRun = context.submitsImmediately || context.chanceCanAutoRunFixedPreset(settingIds)
        LaunchedEffect(autoRun, context.action.settings) {
            if (autoRun && !launched) { launched = true; spin() }
        }
        LaunchedEffect(stage, valuesJson, animation) {
            if (stage != SpinnerStage.Spinning || execution == null) return@LaunchedEffect
            while (frame < 100) {
                delay(if (animation == "fast") 16L else 24L)
                frame = (frame + if (animation == "fast") 4 else 1).coerceAtMost(100)
            }
            land(values)
        }

        if (stage == SpinnerStage.Configure) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
                ChanceConfigSurface(title, context, onBack, onCancel) {
                    Text("Bold colours, large labels and a full-screen wheel.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    if (context.settingShouldBeShown("items")) OutlinedTextField(
                        items, { items = it }, label = { Text("Spinner labels") },
                        supportingText = { Text("One label per line; 2–60 labels to start.") },
                        modifier = Modifier.fillMaxWidth(), minLines = 4, maxLines = 8
                    )
                    if (context.settingShouldBeShown("remove_after_spin")) ChanceSwitchRow(
                        label = "Remove winner after each spin", checked = removeWinner,
                        onCheckedChange = { removeWinner = it },
                        supportingText = "Each winning segment leaves the next draw. Restore all to start again."
                    )
                    if (context.settingShouldBeShown("animation_mode")) SpinnerSettingChoice(
                        "Animation", animation, listOf("full" to "Full", "fast" to "Fast", "off" to "Off")
                    ) { animation = it }
                    if (context.settingShouldBeShown("rng_mode")) SpinnerSettingChoice(
                        "Random source", rngMode, listOf("secure_random" to "Secure random", "fixed_seed" to "Fixed seed")
                    ) { rngMode = it }
                    if (rngMode == "fixed_seed" && context.settingShouldBeShown("seed")) OutlinedTextField(
                        seed, { seed = it }, label = { Text("Fixed seed") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        if (initialItems.size !in 2..60) error = "Start with 2 to 60 non-empty labels."
                        else restoreAll()
                    }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 56.dp)) { Text("Open spinner") }
                }
            }
            return
        }

        val view = LocalView.current
        DisposableEffect(view) {
            val previouslyKeptOn = view.keepScreenOn
            view.keepScreenOn = true
            onDispose { view.keepScreenOn = previouslyKeptOn }
        }
        val spinnerScheme = darkColorScheme(
            primary = SpinnerAccent, onPrimary = SpinnerBackground,
            background = SpinnerBackground, surface = SpinnerBackground,
            onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFD6DEEB),
            outline = Color(0xFFADBBD0)
        )
        MaterialTheme(colorScheme = spinnerScheme) {
            // The normal native host supplies a bounded, full-bleed viewport. A legacy
            // scroll host may be unbounded, so cap only that case at the device viewport.
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val heightLimit = if (constraints.hasBoundedHeight) maxHeight else LocalConfiguration.current.screenHeightDp.dp
                Surface(Modifier.fillMaxWidth().height(heightLimit), color = SpinnerBackground) {
                    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Spinner", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (context.stepNumber > 1) TextButton(onClick = onBack) { Text("Back") }
                            TextButton(onClick = onCancel) { Text("Close") }
                        }
                        if (valuesJson.isNotBlank() && execution == null) {
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                Text("Previous spin unavailable", style = MaterialTheme.typography.headlineMedium)
                                Text("The original execution could not be restored after restart. Restore all to begin a new draw; the previous result will not be recreated or returned.")
                                Button(onClick = { restoreAll() }) { Text("Restore all") }
                            }
                        } else if (stage == SpinnerStage.Committed) {
                            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                                SpinnerWinner(resultText)
                                ChancePostCommitActions("Spinner", resultText, chanceFullJson(execution),
                                    onDone = { execution?.let(onConfirmed) }, onNewRun = { configure() })
                                if (remaining.isNotEmpty()) OutlinedButton(onClick = {
                                    clearResult(); stage = SpinnerStage.Ready
                                }, modifier = Modifier.fillMaxWidth()) { Text("Continue spinning") }
                            }
                        } else {
                            val spinning = stage == SpinnerStage.Spinning
                            val landed = stage == SpinnerStage.Landed
                            val visibleLabels = if (spinning || landed) roundItems else remaining
                            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                                val landscape = maxWidth > maxHeight * 1.25f
                                val controlsHeightLimit = maxHeight * .55f
                                val controls: @Composable () -> Unit = {
                                    if (landed) SpinnerWinner(resultText)
                                    else Text(if (spinning) "Spinning…" else "Ready to spin", style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    Text(
                                        when {
                                            spinning -> if (removeWinner) "Winner leaves the next draw" else "Winners stay in the draw"
                                            remaining.isEmpty() -> "All labels drawn"
                                            landed && removeWinner -> "Removed from next spin · ${remaining.size} remaining"
                                            else -> "${remaining.size} labels · ${if (removeWinner) "remove winners on" else "winners stay in"}"
                                        }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge
                                    )
                                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                                    Button(onClick = { spin() }, enabled = !spinning && remaining.isNotEmpty(),
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)) {
                                        Text(if (remaining.isEmpty()) "All labels drawn" else if (landed && removeWinner) "Spin remaining (${remaining.size})" else "SPIN",
                                            fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (landed) OutlinedButton(onClick = {
                                            execution?.let { if (context.hostOwnsNativePresetCloseout) onConfirmed(it) else stage = SpinnerStage.Committed }
                                        }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Commit") }
                                        if (removeWinner && remaining.size < initialItems.size && !spinning) OutlinedButton(
                                            onClick = { restoreAll() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                        ) { Text("Restore all") }
                                        if (!spinning) TextButton(onClick = { configure() }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("Settings") }
                                    }
                                }
                                if (landscape) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    SpinnerWheel(visibleLabels, selectedIndex, frame / 100f, landed,
                                        Modifier.weight(1.25f).fillMaxHeight())
                                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { controls() }
                                } else Column(Modifier.fillMaxSize()) {
                                    SpinnerWheel(visibleLabels, selectedIndex, frame / 100f, landed, Modifier.fillMaxWidth().weight(1f))
                                    // Scroll only the controls when font scaling consumes the viewport.
                                    Column(Modifier.fillMaxWidth().heightIn(max = controlsHeightLimit).verticalScroll(rememberScrollState())) { controls() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpinnerSettingChoice(label: String, value: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${choices.firstOrNull { it.first == value }?.second ?: value}")
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelected(id); expanded = false }) }
        }
    }
}

@Composable
private fun SpinnerWinner(value: String) {
    Text("WINNER", modifier = Modifier.fillMaxWidth(), color = SpinnerAccent, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    ChanceCopyableText(value, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        style = MaterialTheme.typography.displayMedium.copy(fontSize = 52.sp), fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
}

@Composable
private fun SpinnerWheel(labels: List<String>, selectedIndex: Int, progress: Float, landed: Boolean, modifier: Modifier) {
    val safeLabels = labels.ifEmpty { listOf("?") }
    val rotation = if (progress == 0f && !landed) 0f else spinnerRotation(selectedIndex.coerceIn(safeLabels.indices), safeLabels.size, progress)
    val description = "Spinner wheel: ${safeLabels.joinToString(", ")}" // Full labels stay accessible, even on dense wheels.
    Canvas(modifier.semantics { contentDescription = description }.padding(4.dp)) {
        val diameter = (minOf(size.width, size.height) - 20.dp.toPx()).coerceAtLeast(1f)
        val radius = diameter / 2f
        val centre = Offset(size.width / 2f, size.height / 2f + 5.dp.toPx())
        val origin = Offset(centre.x - radius, centre.y - radius)
        val sweep = 360f / safeLabels.size
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = (diameter / if (safeLabels.size <= 6) 15f else 22f).coerceIn(14.sp.toPx(), 30.sp.toPx())
        }
        safeLabels.forEachIndexed { index, label ->
            val colours = spinnerSegmentColours(index, safeLabels.size)
            val start = -90f + rotation + index * sweep
            drawArc(Color(colours.background), start, sweep, useCenter = true, topLeft = origin, size = Size(diameter, diameter))
            drawArc(Color.White, start, sweep, useCenter = true, topLeft = origin, size = Size(diameter, diameter), style = Stroke(2.dp.toPx()))
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            val labelRadius = radius * .61f
            val x = centre.x + cos(mid).toFloat() * labelRadius
            val y = centre.y + sin(mid).toFloat() * labelRadius
            paint.color = colours.foreground.toInt()
            val availableWidth = radius * .86f
            val shown = if (safeLabels.size > 16) (index + 1).toString() else {
                val count = paint.breakText(label, true, availableWidth, null)
                if (count >= label.length) label else label.take((count - 1).coerceAtLeast(1)) + "…"
            }
            drawContext.canvas.nativeCanvas.drawText(shown, x, y - (paint.ascent() + paint.descent()) / 2, paint)
        }
        drawCircle(Color.White, radius, centre, style = Stroke(3.dp.toPx()))
        val pointer = Path().apply {
            val top = centre.y - radius
            moveTo(centre.x - 14.dp.toPx(), top - 6.dp.toPx())
            lineTo(centre.x + 14.dp.toPx(), top - 6.dp.toPx())
            lineTo(centre.x, top + 25.dp.toPx())
            close()
        }
        drawPath(pointer, SpinnerBackground, style = Stroke(6.dp.toPx()))
        drawPath(pointer, SpinnerAccent)
    }
}

private fun spinnerItems(json: String): List<String> = runCatching {
    val array = JSONArray(json); List(array.length()) { array.getString(it) }
}.getOrDefault(emptyList())

private fun spinnerValues(json: String): Map<String, String> = runCatching {
    val obj = JSONObject(json); obj.keys().asSequence().associateWith { obj.getString(it) }
}.getOrDefault(emptyMap())
