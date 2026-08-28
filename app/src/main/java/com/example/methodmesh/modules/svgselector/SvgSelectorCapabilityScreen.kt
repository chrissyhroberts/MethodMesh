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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.PathParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.io.File
import java.time.Instant

object SvgSelectorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SvgSelectorMethod.ID
    override val title = "SVG polygon selector"
    override val description = "Select one, multiple, or ordered SVG polygons with an audit trail."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val appContext = LocalContext.current
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
        var selectedJson by remember { mutableStateOf("[]") }
        var eventsJson by remember { mutableStateOf("[]") }
        var status by remember { mutableStateOf("Select a polygon.") }
        var startedAt by remember { mutableStateOf(Instant.now().toString()) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var fullScreen by remember { mutableStateOf(false) }
        var resetNonce by remember { mutableIntStateOf(0) }
        val requestedName = context.action.settings["input_svg_name"].orEmpty()
            .ifBlank { context.action.settings["svg_name"].orEmpty() }
            .ifBlank { settings.getString("svg_name") }
        val svgName = requestedName.substringAfterLast('/').ifBlank { "bodymap_black.svg" }
        val mode = context.action.settings["input_selection_mode"].orEmpty()
            .ifBlank { context.action.settings["selection_mode"].orEmpty() }
            .ifBlank { settings.getString("selection_mode") }
            .lowercase()
            .let { if (it in setOf("single", "multiple", "sequence")) it else "single" }

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
            }
        }

        fun execute(): ExecutionResult {
            val request = As100SvgSelectorMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + mapOf(
                    "svg_name" to svgName,
                    "selection_mode" to mode,
                    "selected_polygons" to selectedJson,
                    "selection_events" to eventsJson,
                    "selection_started_at" to startedAt,
                    "selection_completed_at" to Instant.now().toString()
                )
            )
            return As100SvgSelectorMethod.execute(request, transport = context.request.source).withInvocationContext(context.request.invocationContext)
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context,
            canGoBack = context.stepNumber > 1, capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { selectedJson = "[]"; eventsJson = "[]"; startedAt = Instant.now().toString(); result = null; status = "Select a polygon." },
            onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            Text("Mode: $mode · SVG: $svgName")
            Text(status)
            if (!context.startsImmediately) {
                Button(onClick = { chooseSvg.launch(arrayOf("image/svg+xml", "text/xml", "image/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Select SVG from file picker") }
                Text("Stored SVGs: ${available.ifEmpty { listOf("none") }.joinToString()}")
                Button(onClick = { settings.setString("selection_mode", nextMode(settings.getString("selection_mode"))) }, modifier = Modifier.fillMaxWidth()) { Text("Selection mode: ${settings.getString("selection_mode")}") }
            }
            Button(onClick = { fullScreen = true }, modifier = Modifier.fillMaxWidth()) { Text("Open SVG full screen") }
            Button(onClick = { resetNonce++; selectedJson = "[]"; eventsJson = "[]"; startedAt = Instant.now().toString(); status = "Selection reset." }, modifier = Modifier.fillMaxWidth()) { Text("Reset selections") }
            if (!fullScreen) {
                key(svgName, mode, resetNonce) { SvgSelectorCanvas(
                    svg = loadSvg(appContext, svgDirectory, svgName), mode = mode,
                    modifier = Modifier.fillMaxWidth().height(520.dp),
                    onUpdate = { selected, events, message -> selectedJson = selected; eventsJson = events; status = message }
                ) }
            }
            Button(onClick = { val execution = execute(); result = execution; onConfirmed(execution) }, modifier = Modifier.fillMaxWidth()) { Text("Use SVG result") }
        }

        if (fullScreen) {
            Dialog(onDismissRequest = { fullScreen = false }) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.fillMaxSize()) {
                        Text("$svgName · $mode", modifier = Modifier.fillMaxWidth())
                        Button(onClick = { fullScreen = false }, modifier = Modifier.fillMaxWidth()) { Text("Close full screen") }
                        Button(onClick = { resetNonce++; selectedJson = "[]"; eventsJson = "[]"; startedAt = Instant.now().toString(); status = "Selection reset." }, modifier = Modifier.fillMaxWidth()) { Text("Reset selections") }
                        key(svgName, mode, resetNonce) { SvgSelectorCanvas(
                            svg = loadSvg(appContext, svgDirectory, svgName), mode = mode,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            onUpdate = { selected, events, message -> selectedJson = selected; eventsJson = events; status = message }
                        ) }
                        Button(onClick = { val execution = execute(); result = execution; fullScreen = false; onConfirmed(execution) }, modifier = Modifier.fillMaxWidth()) { Text("Use SVG result") }
                    }
                }
            }
        }
    }
}

private fun nextMode(value: String): String = when (value.lowercase()) { "single" -> "multiple"; "multiple" -> "sequence"; else -> "single" }
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
private fun SvgSelectorWebView(svg: String, mode: String, modifier: Modifier, onUpdate: (String, String, String) -> Unit) {
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
            svgFile.writeText(selectorSvgDocument(svg, mode))
            loadUrl(svgFile.toURI().toString())
        }
    }, update = {})
}

private fun selectorSvgDocument(svg: String, mode: String): String {
    // Some body-map exports carry a very large embedded raster image in defs and
    // reference it with <use>. The polygon paths are the selectable content; the
    // raster layer can make Android WebView fail to paint the whole SVG.
    val renderSource = if (svg.contains("data:image/", ignoreCase = true)) {
        svg.replace(Regex("<use\\b[^>]*/>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<defs[\\s\\S]*?</defs>", setOf(RegexOption.IGNORE_CASE)), "")
    } else svg
    val root = renderSource.replaceFirst(Regex("<svg\\b", RegexOption.IGNORE_CASE), "<svg id=\"methodmesh-root\"")
    val script = """
        const selected=[];const events=[];const mode='$mode';
        function now(){return new Date().toISOString()}
        function shape(e){let p=e.target;while(p&&!(p.matches&&p.matches('path,polygon,rect,circle,ellipse')))p=p.parentElement;return p}
        function idOf(p){if(!p)return null;if(!p.id){p.id='polygon_'+(document.querySelectorAll('path,polygon,rect,circle,ellipse').length)}return p.id}
        function point(p){const svg=document.getElementById('methodmesh-root');if(!svg||!svg.viewBox.baseVal)return{x:.5,y:.5};const r=p.getBoundingClientRect(),s=svg.getBoundingClientRect();return{x:Math.max(0,Math.min(1,(r.left+r.width/2-s.left)/s.width)),y:Math.max(0,Math.min(1,(r.top+r.height/2-s.top)/s.height))}}
        function emit(message){MethodMesh.update(JSON.stringify(selected.map((x,i)=>({polygon_id:x.id,sequence_index:i+1}))),JSON.stringify(events),message)}
        function redraw(){document.querySelectorAll('.picked').forEach(x=>x.classList.remove('picked'));selected.forEach(x=>document.getElementById(x.id)?.classList.add('picked'));document.getElementById('methodmesh-labels')?.remove();if(mode!=='sequence')return;const svg=document.getElementById('methodmesh-root');if(!svg)return;const g=document.createElementNS('http://www.w3.org/2000/svg','g');g.id='methodmesh-labels';const vb=svg.viewBox.baseVal;selected.forEach((x,i)=>{const t=document.createElementNS('http://www.w3.org/2000/svg','text');t.setAttribute('x',String(x.x*vb.width));t.setAttribute('y',String(x.y*vb.height));t.setAttribute('class','selection-label');t.textContent=String(i+1);g.appendChild(t)});svg.appendChild(g)}
        function pick(e){const p=shape(e);const id=idOf(p);if(!id)return;const t=now();const index=selected.findIndex(x=>x.id===id);const xy=point(p);
          if(mode==='single'){if(index===0)selected.splice(0,1);else{selected.splice(0,selected.length);selected.push({id:id,x:xy.x,y:xy.y});}events.push({type:index===0?'remove':'select',polygon_id:id,sequence_index:1,time_iso:t});emit(index===0?'Selection cleared.':'Selected '+id+'.');redraw();return}
          if(mode==='multiple'){if(index>=0){selected.splice(index,1);events.push({type:'remove',polygon_id:id,sequence_index:index+1,time_iso:t});emit('Removed '+id+'.')}else{selected.push({id:id,x:xy.x,y:xy.y});events.push({type:'select',polygon_id:id,sequence_index:selected.length,time_iso:t});emit('Selected '+id+'.')}redraw();return}
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
private fun SvgSelectorCanvas(svg: String, mode: String, modifier: Modifier, onUpdate: (String, String, String) -> Unit) {
    val currentUpdate by rememberUpdatedState(onUpdate)
    AndroidView(modifier = modifier, factory = { context ->
        SvgSelectorCanvasView(context, svg, mode) { selected, events, message -> currentUpdate(selected, events, message) }
    }, update = {})
}

private class SvgSelectorCanvasView(
    context: android.content.Context,
    svg: String,
    private val mode: String,
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
    private val selected = mutableListOf<SelectedShape>()
    private val events = mutableListOf<SvgSelectorEvent>()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init { setBackgroundColor(Color.WHITE); isClickable = true; setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

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
            paint.color = if (selected.any { it.id == shape.id }) Color.argb(170, 48, 152, 217) else shape.color
            canvas.drawPath(shape.path, paint)
            if (selected.any { it.id == shape.id }) {
                stroke.color = Color.rgb(21, 101, 192)
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
                if (!moved && !scaleDetector.isInProgress) selectAt(event.x, event.y)
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

    private fun selectedValue(shape: SvgShape, x: Float, y: Float): SelectedShape {
        val bounds = RectF(); shape.path.computeBounds(bounds, true)
        return SelectedShape(shape.id, bounds.centerX(), bounds.centerY())
    }

    private fun emit(message: String) {
        onUpdate(SvgSelectorCodec.selectionsJson(selected.mapIndexed { index, value -> value.id to (index + 1) }), SvgSelectorCodec.eventsJson(events), message)
    }
}

private data class SelectedShape(val id: String, val x: Float, val y: Float)

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
