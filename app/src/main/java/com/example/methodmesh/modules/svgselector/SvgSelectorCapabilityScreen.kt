package com.example.methodmesh.modules.svgselector

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.net.Uri
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CanonicalCommittedResultActions
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.io.File
import java.time.Instant

object SvgSelectorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SvgSelectorMethod.ID
    override val title = "SVG polygon selector"
    override val description = "Select, sequence, or heat-map SVG polygons with an audit trail."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current
        val clipboard = LocalClipboardManager.current
        val definitions = SvgSelectorModule.capabilitySettings()[capabilityId].orEmpty().associateBy { it.id }
        val settings = remember(context.action.settings) {
            SettingsState(SvgSelectorModule.capabilitySettings()[capabilityId].orEmpty()) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { state ->
                context.action.settings.forEach { (key, value) ->
                    when (definitions[key]) {
                        is MethodSetting.BooleanSetting -> state.setBoolean(key, value.equals("true", true))
                        is MethodSetting.IntSetting -> value.toIntOrNull()?.let { state.setInt(key, it) }
                        is MethodSetting.FloatSetting -> value.toFloatOrNull()?.let { state.setFloat(key, it) }
                        else -> state.setString(key, value)
                    }
                }
            }
        }
        val svgDirectory = remember { File(appContext.filesDir, "svg").apply { mkdirs() } }
        var available by remember { mutableStateOf(svgFiles(svgDirectory)) }
        var selectedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var eventsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Select a polygon.") }
        var startedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf(Instant.now().toString()) }
        var resetNonce by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var committedSelectionJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedEventsJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedCompletedAt by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var committedExecution by remember(context.action.canonicalId) { mutableStateOf<ExecutionResult?>(null) }
        val requestedName = context.action.settings["input_svg_name"].orEmpty()
            .ifBlank { context.action.settings["svg_name"].orEmpty() }
            .ifBlank { settings.getString("svg_name") }
        val svgName = requestedName.substringAfterLast('/').ifBlank { "bodymap_black.svg" }
        val mode = context.action.settings["input_selection_mode"].orEmpty()
            .ifBlank { context.action.settings["selection_mode"].orEmpty() }
            .ifBlank { settings.getString("selection_mode") }
            .lowercase()
            .let { if (it in setOf("single", "multiple", "sequence", "heatmap")) it else "single" }
        val heatmapLevels = context.action.settings["input_heatmap_levels"].orEmpty()
            .ifBlank { context.action.settings["heatmap_levels"].orEmpty() }
            .toIntOrNull()
            ?: settings.getString("heatmap_levels").toIntOrNull()
            ?: 5
        val boundedHeatmapLevels = heatmapLevels.coerceIn(2, 9)
        val selectionSummary = remember(selectedJson, mode) { SvgSelectorCodec.selectionSummary(selectedJson, mode) }
        val selectedCount = remember(selectedJson) { SvgSelectorCodec.selectedCount(selectedJson) }
        val headlessRun = context.request.settings["methodmesh_headless"] == "true" ||
            context.request.settings["input_methodmesh_headless"] == "true"
        val protocolStepRun = context.request.settings["methodmesh_protocol_step_run"] == "true" ||
            context.request.settings["input_methodmesh_protocol_step_run"] == "true"
        val sequenceStepRun = context.request.settings["methodmesh_sequence_step_run"] == "true" ||
            context.request.settings["input_methodmesh_sequence_step_run"] == "true"
        // Native preset execution is an interactive instrument surface. Do not infer
        // closeout from completionMode/submitsImmediately: the same external workflow
        // host also carries schedules/protocols/ODK. Visible presets must remain alive
        // after Commit so their canonical post-Commit actions can render.
        val interactiveNativePreset = context.isNativePresetRun &&
            !headlessRun && !protocolStepRun && !sequenceStepRun
        val returnImmediatelyOnCommit = context.submitsImmediately && !interactiveNativePreset

        val chooseSvg = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                val name = displayName(appContext, uri).ifBlank { "svg_${System.currentTimeMillis()}.svg" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                    .let { if (it.endsWith(".svg", true)) it else "$it.svg" }
                appContext.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    File(svgDirectory, name).outputStream().use { output -> input.copyTo(output) }
                }
                settings.setString("svg_name", name)
                available = svgFiles(svgDirectory)
                resetNonce++
                selectedJson = "[]"
                eventsJson = "[]"
                startedAt = Instant.now().toString()
                committedSelectionJson = null
                committedEventsJson = null
                committedCompletedAt = null
                committedExecution = null
                status = "SVG loaded. Tap a polygon to begin."
            }
        }

        fun execute(
            selectionJson: String = selectedJson,
            auditEventsJson: String = eventsJson,
            completedAt: String = Instant.now().toString()
        ): ExecutionResult {
            val request = As100SvgSelectorMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf(
                    "svg_name" to svgName,
                    "selection_mode" to mode,
                    "selected_polygons" to selectionJson,
                    "polygon_levels" to SvgSelectorCodec.polygonLevelsJson(selectionJson),
                    "heatmap_max_level" to boundedHeatmapLevels.toString(),
                    "selection_events" to auditEventsJson,
                    "selection_started_at" to startedAt,
                    "selection_completed_at" to completedAt
                )
            )
            return As100SvgSelectorMethod.execute(request, transport = context.request.source).withInvocationContext(context.request.invocationContext)
        }

        val restoredCommittedResult = remember(committedSelectionJson, committedEventsJson, committedCompletedAt, startedAt, svgName, mode, boundedHeatmapLevels) {
            val selection = committedSelectionJson
            val events = committedEventsJson
            val completedAt = committedCompletedAt
            if (selection == null || events == null || completedAt == null) null
            else runCatching { execute(selection, events, completedAt) }.getOrNull()
        }
        val committedResult = committedExecution ?: restoredCommittedResult

        fun clearCommittedForEditing(message: String = "Editing working selection.") {
            committedSelectionJson = null
            committedEventsJson = null
            committedCompletedAt = null
            committedExecution = null
            status = message
        }

        fun commitWorkingResult() {
            if (selectedJson == "[]") return
            val completedAt = Instant.now().toString()
            runCatching { execute(selectedJson, eventsJson, completedAt) }
                .onFailure { error ->
                    // Commit must never be able to tear down the capability because a
                    // result projection failed. Keep the working UI alive and report it.
                    status = "Commit failed: ${error.message ?: error.javaClass.simpleName}. Selection is still editable."
                }
                .onSuccess { result ->
                    if (returnImmediatelyOnCommit) {
                        // ODK/external automatic and genuine headless/protocol/sequence
                        // runs return directly to their caller.
                        onConfirmed(result)
                    } else {
                        // Native/manual Commit freezes a snapshot and stays on this
                        // exact surface. Share/Save/Copy/JSON/Done operate on this
                        // immutable result; only Done returns to the preset runner.
                        committedSelectionJson = selectedJson
                        committedEventsJson = eventsJson
                        committedCompletedAt = completedAt
                        committedExecution = result
                        status = "Selection committed. Share, copy or save it, then tap Done when finished."
                    }
                }
        }

        fun updateWorkingResult(selectionJson: String, auditEventsJson: String, message: String) {
            // A polygon tap is interaction only. Never manufacture a captured/committed
            // ExecutionResult here: CapabilityScreenScaffold interprets capturedResult as
            // completion and would leave the selector after the first tap.
            if (committedSelectionJson != null) return
            selectedJson = selectionJson
            eventsJson = auditEventsJson
            status = message
        }

        val standaloneNativePreset = interactiveNativePreset
        if (standaloneNativePreset) {
            // A manually run preset is an instrument surface, not a card inside the
            // generic external-workflow scroller. Own a true full-screen dialog here
            // so the shared runtime remains capability-agnostic.
            Dialog(
                onDismissRequest = onCancel,
                properties = DialogProperties(
                    dismissOnBackPress = true,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                PresetFullscreenSelector(
                    title = title,
                    svgName = svgName,
                    mode = mode,
                    heatmapLevels = boundedHeatmapLevels,
                    svg = loadSvg(appContext, svgDirectory, svgName),
                    status = status,
                    selectionSummary = selectionSummary,
                    selectedCount = selectedCount,
                    resetNonce = resetNonce,
                    selectedJson = selectedJson,
                    eventsJson = eventsJson,
                    committedResult = committedResult,
                    onUpdate = ::updateWorkingResult,
                    onCopySummary = {
                        clipboard.setText(AnnotatedString(selectionSummary))
                        status = "Selection copied."
                    },
                    onReset = {
                        resetNonce++
                        selectedJson = "[]"
                        eventsJson = "[]"
                        startedAt = Instant.now().toString()
                        committedSelectionJson = null
                        committedEventsJson = null
                        committedCompletedAt = null
                        committedExecution = null
                        status = "Selection reset."
                    },
                    onCommit = ::commitWorkingResult,
                    commitEnabled = selectedJson != "[]" && committedResult == null,
                    onDone = { committedResult?.let(onConfirmed) },
                    onEdit = { clearCommittedForEditing() },
                    onCancel = onCancel
                )
            }
            return
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            // The selector owns its live working result. Passing a tap-time result to
            // capturedResult switches the generic scaffold into its conclude state,
            // which is specifically wrong for a direct-manipulation selector.
            capturedResult = null,
            resultPreview = emptyMap(),
            onBack = onBack,
            onRetry = {
                resetNonce++
                selectedJson = "[]"
                eventsJson = "[]"
                startedAt = Instant.now().toString()
                committedSelectionJson = null
                committedEventsJson = null
                committedCompletedAt = null
                committedExecution = null
                status = "Selection reset."
            },
            // Commit is capability-owned below so selection, preview and finalisation
            // remain one continuous UI instead of a second conclude/result surface.
            onConfirm = {},
            onCancel = onCancel
        ) {
            Text("Mode: $mode · SVG: $svgName")
            Text(status)

            // Tool first: the SVG remains visible and interactive for the whole run.
            key(svgName, mode, boundedHeatmapLevels, resetNonce) {
                SvgSelectorCanvas(
                    svg = loadSvg(appContext, svgDirectory, svgName),
                    mode = mode,
                    heatmapLevels = boundedHeatmapLevels,
                    initialSelectionJson = selectedJson,
                    initialEventsJson = eventsJson,
                    interactive = committedResult == null,
                    modifier = Modifier.fillMaxWidth().height(560.dp),
                    onUpdate = ::updateWorkingResult
                )
            }

            if (selectionSummary.isNotBlank()) {
                Text("Current selection ($selectedCount)")
                Text(
                    selectionSummary,
                    modifier = Modifier.fillMaxWidth().clickable {
                        clipboard.setText(AnnotatedString(selectionSummary))
                        status = "Selection copied."
                    }
                )
                Text(
                    if (committedResult == null) {
                        "Tap the selection summary to copy it. Continue tapping polygons to edit it."
                    } else {
                        "Committed snapshot. Use Edit / new run before changing the selection."
                    }
                )
            } else {
                Text("No polygons selected yet.")
            }

            if (committedResult == null) {
                Button(
                    onClick = {
                        resetNonce++
                        selectedJson = "[]"
                        eventsJson = "[]"
                        startedAt = Instant.now().toString()
                        committedSelectionJson = null
                        committedEventsJson = null
                        committedCompletedAt = null
                        committedExecution = null
                        status = "Selection reset."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Reset selections") }

            if (!context.startsImmediately) {
                Button(
                    onClick = { chooseSvg.launch(arrayOf("image/svg+xml", "text/xml", "image/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Select SVG from file picker") }
                Text("Stored SVGs: ${available.ifEmpty { listOf("none") }.joinToString()}")
                Button(
                    onClick = {
                        settings.setString("selection_mode", nextMode(settings.getString("selection_mode")))
                        resetNonce++
                        selectedJson = "[]"
                        eventsJson = "[]"
                        startedAt = Instant.now().toString()
                        status = "Mode changed. Tap a polygon to begin."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Selection mode: ${settings.getString("selection_mode")}") }
                if (mode == "heatmap") {
                    Text("Heat map: repeated taps cycle 0 → 1 → … → $boundedHeatmapLevels → 0.")
                    Button(
                        onClick = {
                            val next = if (boundedHeatmapLevels >= 9) 2 else boundedHeatmapLevels + 1
                            settings.setString("heatmap_levels", next.toString())
                            resetNonce++
                            selectedJson = "[]"
                            eventsJson = "[]"
                            startedAt = Instant.now().toString()
                            status = "Heat-map levels changed to $next."
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Heat-map levels: $boundedHeatmapLevels") }
                }
            }

                Button(
                    onClick = ::commitWorkingResult,
                    enabled = selectedJson != "[]",
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Commit selection") }
            } else {
                Text(
                    "Committed result",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                CanonicalCommittedResultActions(
                    result = committedResult,
                    label = title,
                    onDone = { committedResult?.let(onConfirmed) },
                    onEdit = { clearCommittedForEditing() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Composable
private fun PresetFullscreenSelector(
    title: String,
    svgName: String,
    mode: String,
    heatmapLevels: Int,
    svg: String,
    status: String,
    selectionSummary: String,
    selectedCount: Int,
    resetNonce: Int,
    selectedJson: String,
    eventsJson: String,
    committedResult: ExecutionResult?,
    onUpdate: (String, String, String) -> Unit,
    onCopySummary: () -> Unit,
    onReset: () -> Unit,
    onCommit: () -> Unit,
    commitEnabled: Boolean,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "$mode · $svgName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = if (committedResult == null) onCancel else onDone) {
                    Text(if (committedResult == null) "Cancel" else "Done")
                }
            }

            Text(
                status,
                modifier = Modifier.padding(top = 6.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (committedResult == null) 1f else 0.55f)
            ) {
                key(svgName, mode, heatmapLevels, resetNonce) {
                    SvgSelectorCanvas(
                        svg = svg,
                        mode = mode,
                        heatmapLevels = heatmapLevels,
                        initialSelectionJson = selectedJson,
                        initialEventsJson = eventsJson,
                        interactive = committedResult == null,
                        modifier = Modifier.fillMaxSize(),
                        onUpdate = onUpdate
                    )
                }
            }

            if (mode == "heatmap") {
                Text(
                    "Repeated taps cycle 0 → 1 → … → $heatmapLevels → 0.",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (selectionSummary.isNotBlank()) {
                Text(
                    "Current selection ($selectedCount): $selectionSummary",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clickable(onClick = onCopySummary),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    "No polygons selected yet.",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (committedResult == null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f)) { Text("Reset") }
                    Button(onClick = onCommit, enabled = commitEnabled, modifier = Modifier.weight(1f)) {
                        Text("Commit selection")
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                CanonicalCommittedResultActions(
                    result = committedResult,
                    label = title,
                    onDone = onDone,
                    onEdit = onEdit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

private fun nextMode(value: String): String = when (value.lowercase()) {
    "single" -> "multiple"
    "multiple" -> "sequence"
    "sequence" -> "heatmap"
    else -> "single"
}
private fun svgFiles(directory: File): List<String> = directory.listFiles().orEmpty().filter { it.isFile && it.extension.equals("svg", true) }.map { it.name }.sorted()
private fun displayName(context: android.content.Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
    }.orEmpty()
}.getOrDefault("")

private fun loadSvg(context: android.content.Context, directory: File, name: String): String {
    val file = File(directory, name.substringAfterLast('/'))
    if (file.isFile) return runCatching { normalizeSvg(file.readText()) }.getOrDefault(placeholderSvg())
    return runCatching { normalizeSvg(context.assets.open("svg/$name").bufferedReader().use { it.readText() }) }.getOrDefault(placeholderSvg())
}

private fun normalizeSvg(value: String): String = value
    .replace(Regex("<\\?xml[^>]*\\?>", RegexOption.IGNORE_CASE), "")
    .replace(Regex("<!DOCTYPE[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), "")
    .trim()

private fun placeholderSvg(): String = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><rect id='placeholder' x='5' y='5' width='90' height='90' fill='#eeeeee' stroke='#333'/></svg>"

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SvgSelectorWebView(svg: String, mode: String, heatmapLevels: Int, modifier: Modifier, onUpdate: (String, String, String) -> Unit) {
    val currentUpdate by rememberUpdatedState(onUpdate)
    AndroidView(modifier = modifier, factory = { context ->
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.allowFileAccess = true
            settings.domStorageEnabled = false
            settings.loadsImagesAutomatically = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            addJavascriptInterface(object {
                @JavascriptInterface fun update(selected: String, events: String, message: String) { post { currentUpdate(selected, events, message) } }
            }, "MethodMesh")
            val svgFile = File(context.cacheDir, "svg-selector-${System.nanoTime()}.svg")
            svgFile.writeText(selectorSvgDocument(svg, mode, heatmapLevels))
            loadUrl(svgFile.toURI().toString())
        }
    }, update = {})
}

private fun selectorSvgDocument(svg: String, mode: String, heatmapLevels: Int): String {
    // Some body-map exports carry a very large embedded raster image in defs and
    // reference it with <use>. The polygon paths are the selectable content; the
    // raster layer can make Android WebView fail to paint the whole SVG.
    val renderSource = if (svg.contains("data:image/", ignoreCase = true)) {
        svg.replace(Regex("<use\\b[^>]*/>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<defs[\\s\\S]*?</defs>", setOf(RegexOption.IGNORE_CASE)), "")
    } else svg
    val root = renderSource.replaceFirst(Regex("<svg\\b", RegexOption.IGNORE_CASE), "<svg id=\"methodmesh-root\"")
    val script = """
        const selected=[];const events=[];const mode='$mode';const heatmapLevels=$heatmapLevels;
        function now(){return new Date().toISOString()}
        function shape(e){let p=e.target;while(p&&!(p.matches&&p.matches('path,polygon,rect,circle,ellipse')))p=p.parentElement;return p}
        function idOf(p){if(!p)return null;if(!p.id){p.id='polygon_'+(document.querySelectorAll('path,polygon,rect,circle,ellipse').length)}return p.id}
        function point(p){const svg=document.getElementById('methodmesh-root');if(!svg||!svg.viewBox.baseVal)return{x:.5,y:.5};const r=p.getBoundingClientRect(),s=svg.getBoundingClientRect();return{x:Math.max(0,Math.min(1,(r.left+r.width/2-s.left)/s.width)),y:Math.max(0,Math.min(1,(r.top+r.height/2-s.top)/s.height))}}
        function emit(message){const payload=mode==='heatmap'?selected.map(x=>({polygon_id:x.id,level:x.level})):selected.map((x,i)=>({polygon_id:x.id,sequence_index:i+1}));MethodMesh.update(JSON.stringify(payload),JSON.stringify(events),message)}
        function redraw(){document.querySelectorAll('.picked').forEach(x=>{x.classList.remove('picked');x.style.removeProperty('fill');x.removeAttribute('data-methodmesh-level')});selected.forEach(x=>{const el=document.getElementById(x.id);if(!el)return;el.classList.add('picked');if(mode==='heatmap'){const f=(x.level-1)/Math.max(1,heatmapLevels-1);const hue=48*(1-f);el.style.setProperty('fill','hsl('+hue+',90%,55%)','important');el.setAttribute('data-methodmesh-level',String(x.level))}});document.getElementById('methodmesh-labels')?.remove();if(mode!=='sequence')return;const svg=document.getElementById('methodmesh-root');if(!svg)return;const g=document.createElementNS('http://www.w3.org/2000/svg','g');g.id='methodmesh-labels';const vb=svg.viewBox.baseVal;selected.forEach((x,i)=>{const t=document.createElementNS('http://www.w3.org/2000/svg','text');t.setAttribute('x',String(x.x*vb.width));t.setAttribute('y',String(x.y*vb.height));t.setAttribute('class','selection-label');t.textContent=String(i+1);g.appendChild(t)});svg.appendChild(g)}
        function pick(e){const p=shape(e);const id=idOf(p);if(!id)return;const t=now();const index=selected.findIndex(x=>x.id===id);const xy=point(p);
          if(mode==='single'){if(index===0)selected.splice(0,1);else{selected.splice(0,selected.length);selected.push({id:id,x:xy.x,y:xy.y});}events.push({type:index===0?'remove':'select',polygon_id:id,sequence_index:1,time_iso:t});emit(index===0?'Selection cleared.':'Selected '+id+'.');redraw();return}
          if(mode==='multiple'){if(index>=0){selected.splice(index,1);events.push({type:'remove',polygon_id:id,sequence_index:index+1,time_iso:t});emit('Removed '+id+'.')}else{selected.push({id:id,x:xy.x,y:xy.y});events.push({type:'select',polygon_id:id,sequence_index:selected.length,time_iso:t});emit('Selected '+id+'.')}redraw();return}
          if(mode==='heatmap'){const old=index>=0?selected[index].level:0;const next=(old+1)%(heatmapLevels+1);if(next===0){selected.splice(index,1);events.push({type:'heat_clear',polygon_id:id,level:0,previous_level:old,time_iso:t});emit('Cleared '+id+' after level '+old+'.')}else if(index>=0){selected[index].level=next;events.push({type:'heat_increment',polygon_id:id,level:next,previous_level:old,time_iso:t});emit(id+' level '+next+' of '+heatmapLevels+'.')}else{selected.push({id:id,x:xy.x,y:xy.y,level:1});events.push({type:'heat_increment',polygon_id:id,level:1,previous_level:0,time_iso:t});emit(id+' level 1 of '+heatmapLevels+'.')}redraw();return}
          if(index>=0){if(index!==selected.length-1){events.push({type:'backstep_rejected',polygon_id:id,sequence_index:index+1,time_iso:t});emit('Backstep only: remove '+selected[selected.length-1].id+' first.');return}selected.pop();events.push({type:'backstep_remove',polygon_id:id,sequence_index:index+1,time_iso:t});emit('Removed '+id+' by backstep.');redraw();return}
          selected.push({id:id,x:xy.x,y:xy.y});events.push({type:'select',polygon_id:id,sequence_index:selected.length,time_iso:t});emit('Selected '+id+' as '+selected.length+'.');redraw();
        }
        document.addEventListener('click',pick);redraw();
    """.trimIndent()
    val injection = "<style><![CDATA[html,body{margin:0;background:#fff;overflow:auto}#methodmesh-root{width:100%;height:100%;touch-action:auto}path,polygon,rect,circle,ellipse{cursor:pointer;transition:fill .12s,stroke .12s}.picked{fill:#ff8a65!important;stroke:#8d2f1f!important;stroke-width:4}.selection-label{font:700 24px sans-serif;fill:#0d47a1;stroke:#fff;stroke-width:5px;paint-order:stroke;pointer-events:none}]]></style><script type=\"text/javascript\"><![CDATA[$script]]></script>"
    return root.replaceFirst(Regex("</svg>\\s*$", RegexOption.IGNORE_CASE), "$injection</svg>")
}

private data class SvgShape(val id: String, val path: Path, val color: Int)

@Composable
private fun SvgSelectorCanvas(
    svg: String,
    mode: String,
    heatmapLevels: Int,
    initialSelectionJson: String,
    initialEventsJson: String,
    interactive: Boolean,
    modifier: Modifier,
    onUpdate: (String, String, String) -> Unit
) {
    val currentUpdate by rememberUpdatedState(onUpdate)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            SvgSelectorCanvasView(
                context = context,
                svg = svg,
                mode = mode,
                heatmapLevels = heatmapLevels,
                initialSelectionJson = initialSelectionJson,
                initialEventsJson = initialEventsJson
            ) { selected, events, message -> currentUpdate(selected, events, message) }
        },
        update = { view -> view.setInteractive(interactive) }
    )
}

private class SvgSelectorCanvasView(
    context: android.content.Context,
    svg: String,
    private val mode: String,
    private val heatmapLevels: Int,
    initialSelectionJson: String,
    initialEventsJson: String,
    private val onUpdate: (String, String, String) -> Unit
) : View(context) {
    private val shapes = parseSvgShapes(svg)
    private val backgroundBitmap = parseSvgRaster(svg)
    private val viewBox = parseSvgViewBox(svg)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3f }
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 6f)
            invalidate()
            return true
        }
    })
    private val selected = restoreSelections(initialSelectionJson, shapes)
    private val events = restoreEvents(initialEventsJson)
    private var interactive = true
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init { setBackgroundColor(Color.WHITE); isClickable = true; setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    fun setInteractive(value: Boolean) {
        interactive = value
        isClickable = value
    }

    private fun transform(): Pair<Float, Float> {
        val fit = minOf(width / viewBox.width(), height / viewBox.height()).coerceAtLeast(0.01f)
        return fit * zoom to fit
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val (scale, fit) = transform()
        val baseX = (width - viewBox.width() * fit) / 2f + panX
        val baseY = (height - viewBox.height() * fit) / 2f + panY
        canvas.save()
        canvas.translate(baseX, baseY)
        canvas.scale(scale, scale)
        backgroundBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, null, RectF(0f, 0f, viewBox.width(), viewBox.height()), paint)
        }
        shapes.forEach { shape ->
            val selectedShape = selected.firstOrNull { it.id == shape.id }
            paint.color = when {
                selectedShape == null -> shape.color
                mode == "heatmap" -> heatColor(selectedShape.level, heatmapLevels)
                else -> Color.argb(170, 48, 152, 217)
            }
            canvas.drawPath(shape.path, paint)
            if (selectedShape != null) {
                stroke.color = if (mode == "heatmap") Color.rgb(117, 24, 18) else Color.rgb(21, 101, 192)
                canvas.drawPath(shape.path, stroke)
            }
        }
        if (mode == "sequence") {
            paint.textSize = 24f / scale
            paint.color = Color.rgb(13, 71, 161)
            paint.style = Paint.Style.FILL
            selected.forEachIndexed { index, value ->
                canvas.drawText((index + 1).toString(), value.x, value.y, paint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y; lastX = event.x; lastY = event.y; moved = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX; val dy = event.y - lastY
                    if (kotlin.math.abs(event.x - downX) > 8f || kotlin.math.abs(event.y - downY) > 8f) moved = true
                    panX += dx; panY += dy
                    lastX = event.x; lastY = event.y; invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (interactive && !moved && !scaleDetector.isInProgress) selectAt(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return true
    }

    private fun selectAt(screenX: Float, screenY: Float) {
        val (scale, fit) = transform()
        val baseX = (width - viewBox.width() * fit) / 2f + panX
        val baseY = (height - viewBox.height() * fit) / 2f + panY
        val x = (screenX - baseX) / scale + viewBox.left
        val y = (screenY - baseY) / scale + viewBox.top
        val hit = shapes.asReversed().firstOrNull { shape ->
            val bounds = RectF(); shape.path.computeBounds(bounds, true)
            val region = Region(bounds.left.toInt(), bounds.top.toInt(), bounds.right.toInt() + 1, bounds.bottom.toInt() + 1)
            region.setPath(shape.path, region)
            region.contains(x.toInt(), y.toInt())
        } ?: return
        val now = SvgSelectorCodec.now()
        val index = selected.indexOfFirst { it.id == hit.id }
        when (mode) {
            "single" -> {
                if (index == 0) {
                    selected.clear(); events += SvgSelectorEvent("remove", hit.id, 1, now)
                    emit("Selection cleared.")
                } else {
                    selected.clear(); selected += selectedValue(hit, x, y); events += SvgSelectorEvent("select", hit.id, 1, now)
                    emit("Selected ${hit.id}.")
                }
            }
            "multiple" -> if (index >= 0) {
                selected.removeAt(index); events += SvgSelectorEvent("remove", hit.id, index + 1, now); emit("Removed ${hit.id}.")
            } else {
                selected += selectedValue(hit, x, y); events += SvgSelectorEvent("select", hit.id, selected.size, now); emit("Selected ${hit.id}.")
            }
            "heatmap" -> {
                val previousLevel = if (index >= 0) selected[index].level else 0
                val nextLevel = (previousLevel + 1) % (heatmapLevels + 1)
                if (nextLevel == 0) {
                    if (index >= 0) selected.removeAt(index)
                    events += SvgSelectorEvent(type = "heat_clear", polygonId = hit.id, sequenceIndex = null, timeIso = now, level = 0, previousLevel = previousLevel)
                    emit("Cleared ${hit.id} after level $previousLevel.")
                } else if (index >= 0) {
                    selected[index] = selected[index].copy(level = nextLevel)
                    events += SvgSelectorEvent(type = "heat_increment", polygonId = hit.id, sequenceIndex = null, timeIso = now, level = nextLevel, previousLevel = previousLevel)
                    emit("${hit.id} level $nextLevel of $heatmapLevels.")
                } else {
                    selected += selectedValue(hit, x, y, level = 1)
                    events += SvgSelectorEvent(type = "heat_increment", polygonId = hit.id, sequenceIndex = null, timeIso = now, level = 1, previousLevel = 0)
                    emit("${hit.id} level 1 of $heatmapLevels.")
                }
            }
            else -> if (index >= 0) {
                if (index != selected.lastIndex) {
                    events += SvgSelectorEvent("backstep_rejected", hit.id, index + 1, now)
                    emit("Backstep only: remove ${selected.last().id} first.")
                } else {
                    selected.removeAt(index); events += SvgSelectorEvent("backstep_remove", hit.id, index + 1, now); emit("Removed ${hit.id} by backstep.")
                }
            } else {
                selected += selectedValue(hit, x, y); events += SvgSelectorEvent("select", hit.id, selected.size, now); emit("Selected ${hit.id} as ${selected.size}.")
            }
        }
        invalidate()
    }

    private fun selectedValue(shape: SvgShape, x: Float, y: Float, level: Int = 1): SelectedShape {
        val bounds = RectF(); shape.path.computeBounds(bounds, true)
        return SelectedShape(shape.id, bounds.centerX(), bounds.centerY(), level)
    }

    private fun emit(message: String) {
        val selectionJson = if (mode == "heatmap") {
            SvgSelectorCodec.heatmapSelectionsJson(selected.map { it.id to it.level })
        } else {
            SvgSelectorCodec.selectionsJson(selected.mapIndexed { index, value -> value.id to (index + 1) })
        }
        onUpdate(selectionJson, SvgSelectorCodec.eventsJson(events), message)
    }
}

private fun restoreSelections(json: String, shapes: List<SvgShape>): MutableList<SelectedShape> = runCatching {
    val array = org.json.JSONArray(json)
    MutableList(array.length()) { index ->
        val item = array.getJSONObject(index)
        val id = item.optString("polygon_id")
        val shape = shapes.firstOrNull { it.id == id }
        val bounds = RectF().also { rect -> shape?.path?.computeBounds(rect, true) }
        SelectedShape(
            id = id,
            x = if (shape == null) 0f else bounds.centerX(),
            y = if (shape == null) 0f else bounds.centerY(),
            level = item.optInt("level", 1).coerceAtLeast(1)
        )
    }.filter { it.id.isNotBlank() }.toMutableList()
}.getOrDefault(mutableListOf())

private fun restoreEvents(json: String): MutableList<SvgSelectorEvent> = runCatching {
    val array = org.json.JSONArray(json)
    MutableList(array.length()) { index ->
        val item = array.getJSONObject(index)
        SvgSelectorEvent(
            type = item.optString("type"),
            polygonId = item.optString("polygon_id"),
            sequenceIndex = if (item.has("sequence_index")) item.optInt("sequence_index") else null,
            timeIso = item.optString("time_iso"),
            level = if (item.has("level")) item.optInt("level") else null,
            previousLevel = if (item.has("previous_level")) item.optInt("previous_level") else null
        )
    }.toMutableList()
}.getOrDefault(mutableListOf())

private data class SelectedShape(val id: String, val x: Float, val y: Float, val level: Int = 1)

private fun heatColor(level: Int, maxLevel: Int): Int {
    val fraction = if (maxLevel <= 1) 1f else ((level - 1).toFloat() / (maxLevel - 1).toFloat()).coerceIn(0f, 1f)
    val hue = 48f * (1f - fraction)
    return Color.HSVToColor(190, floatArrayOf(hue, 0.88f, 0.95f))
}

private fun parseSvgViewBox(svg: String): RectF {
    val value = Regex("viewBox\\s*=\\s*[\\\"']([^\\\"']+)", RegexOption.IGNORE_CASE).find(svg)?.groupValues?.getOrNull(1)
        ?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() }
    return if (value != null && value.size >= 4) RectF(value[0], value[1], value[0] + value[2], value[1] + value[3]) else RectF(0f, 0f, 100f, 100f)
}

private fun parseSvgShapes(svg: String): List<SvgShape> {
    val result = mutableListOf<SvgShape>()
    val parser = android.util.Xml.newPullParser()
    parser.setInput(StringReader(svg))
    var fallback = 1
    runCatching {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val tag = parser.name.lowercase()
            val id = parser.getAttributeValue(null, "id").orEmpty().ifBlank { "polygon_${fallback++}" }
            val path = when (tag) {
                "path" -> parser.getAttributeValue(null, "d")?.let { PathParser.createPathFromPathData(it) }
                "polygon" -> polygonPath(parser.getAttributeValue(null, "points"))
                "rect" -> rectPath(parser)
                else -> null
            } ?: continue
            val fill = parseSvgFill(parser.getAttributeValue(null, "fill"), parser.getAttributeValue(null, "style"))
            result += SvgShape(id, path, fill)
        }
    }
    return result
}

private fun parseSvgRaster(svg: String): Bitmap? = runCatching {
    val encoded = Regex("<image\\b[^>]*(?:xlink:href|href)\\s*=\\s*[\\\"']data:image/[^;]+;base64,([^\\\"']+)[\\\"']", RegexOption.IGNORE_CASE)
        .find(svg)?.groupValues?.getOrNull(1) ?: return@runCatching null
    BitmapFactory.decodeByteArray(Base64.decode(encoded, Base64.DEFAULT), 0, Base64.decode(encoded, Base64.DEFAULT).size)
}.getOrNull()

private fun polygonPath(points: String?): Path? {
    val values = points?.trim()?.split(Regex("[ ,]+"))?.mapNotNull { it.toFloatOrNull() } ?: return null
    if (values.size < 4) return null
    return Path().apply { moveTo(values[0], values[1]); var i = 2; while (i + 1 < values.size) { lineTo(values[i], values[i + 1]); i += 2 }; close() }
}

private fun rectPath(parser: XmlPullParser): Path? {
    val x = parser.getAttributeValue(null, "x")?.toFloatOrNull() ?: 0f
    val y = parser.getAttributeValue(null, "y")?.toFloatOrNull() ?: 0f
    val w = parser.getAttributeValue(null, "width")?.toFloatOrNull() ?: return null
    val h = parser.getAttributeValue(null, "height")?.toFloatOrNull() ?: return null
    return Path().apply { addRect(x, y, x + w, y + h, Path.Direction.CW) }
}

private fun parseSvgFill(fill: String?, style: String?): Int {
    val value = fill?.takeIf { it.isNotBlank() } ?: style?.substringAfter("fill:")?.substringBefore(';')
    return runCatching {
        val color = when {
            value.isNullOrBlank() || value == "none" -> Color.rgb(235, 235, 235)
            value.startsWith("rgb", true) -> {
                val n = Regex("\\d+").findAll(value).map { it.value.toInt() }.toList(); Color.rgb(n.getOrElse(0) { 235 }, n.getOrElse(1) { 235 }, n.getOrElse(2) { 235 })
            }
            else -> Color.parseColor(value.trim())
        }
        val opacity = Regex("fill-opacity\\s*:\\s*([0-9.]+)", RegexOption.IGNORE_CASE).find(style.orEmpty())?.groupValues?.getOrNull(1)?.toFloatOrNull() ?: 1f
        Color.argb((Color.alpha(color) * opacity).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }.getOrDefault(Color.rgb(235, 235, 235))
}
