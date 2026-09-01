package com.example.methodmesh.modules.pluscodecapture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.time.Instant
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import kotlin.math.ln
import kotlin.math.roundToInt

object PlusCodeCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PlusCodeCaptureMethod.ID
    override val title = "Plus Code capture"
    override val description = "Capture a full offline Open Location Code from GPS and a selectable grid."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var codeLength by rememberSaveable { mutableIntStateOf((context.action.settings["code_length"] ?: context.action.settings["input_code_length"])?.toIntOrNull()?.coerceIn(2, 10) ?: 10) }
        var gpsAverageSeconds by rememberSaveable { mutableIntStateOf((context.action.settings["gps_average_seconds"] ?: context.action.settings["input_gps_average_seconds"])?.toIntOrNull()?.coerceIn(1, 60) ?: 10) }
        var basemapMode by rememberSaveable {
            mutableStateOf(
                (context.action.settings["basemap_mode"] ?: context.action.settings["input_basemap_mode"] ?: "auto")
                    .takeIf { it in setOf("auto", "satellite", "blank") }
                    ?: "auto"
            )
        }
        var allowOnlineTiles by rememberSaveable { mutableStateOf((context.action.settings["allow_online_tiles"] ?: context.action.settings["input_allow_online_tiles"])?.toBooleanStrictOrNull() ?: true) }
        var hasLocationPermission by remember { mutableStateOf(hasLocationPermission(androidContext)) }
        var latestGpsLatitude by rememberSaveable { mutableStateOf((context.action.settings["gps_latitude"] ?: context.action.settings["input_gps_latitude"]).orEmpty()) }
        var latestGpsLongitude by rememberSaveable { mutableStateOf((context.action.settings["gps_longitude"] ?: context.action.settings["input_gps_longitude"]).orEmpty()) }
        var latestGpsAccuracy by rememberSaveable { mutableStateOf((context.action.settings["gps_accuracy_m"] ?: context.action.settings["input_gps_accuracy_m"]).orEmpty()) }
        val latestLocation = remember(latestGpsLatitude, latestGpsLongitude, latestGpsAccuracy) {
            val latitude = latestGpsLatitude.toDoubleOrNull()
            val longitude = latestGpsLongitude.toDoubleOrNull()
            if (latitude == null || longitude == null) {
                null
            } else {
                Location("methodmesh.plus_code").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                    latestGpsAccuracy.toFloatOrNull()?.let { accuracy = it }
                }
            }
        }
        var centerLatitude by rememberSaveable { mutableStateOf((context.action.settings["selected_latitude"] ?: context.action.settings["gps_latitude"])?.toDoubleOrNull() ?: 0.0) }
        var centerLongitude by rememberSaveable { mutableStateOf((context.action.settings["selected_longitude"] ?: context.action.settings["gps_longitude"])?.toDoubleOrNull() ?: 0.0) }
        var selectedLatitude by rememberSaveable { mutableStateOf(centerLatitude) }
        var selectedLongitude by rememberSaveable { mutableStateOf(centerLongitude) }
        var followGpsFixes by rememberSaveable { mutableStateOf(true) }
        var gridSpanCells by rememberSaveable { mutableIntStateOf((context.action.settings["grid_span_cells"] ?: context.action.settings["input_grid_span_cells"])?.toIntOrNull()?.coerceIn(MIN_GRID_SPAN_CELLS, MAX_GRID_SPAN_CELLS) ?: DEFAULT_GRID_SPAN_CELLS) }
        val selectedCell by remember(selectedLatitude, selectedLongitude, codeLength) {
            derivedStateOf { OpenLocationCode.cellFor(selectedLatitude, selectedLongitude, codeLength) }
        }
        var status by rememberSaveable { mutableStateOf("Ready. Acquire GPS, then tap the intended grid cell.") }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var fixCount by rememberSaveable { mutableIntStateOf(0) }
        var averaging by rememberSaveable { mutableStateOf(false) }
        var selectorOpen by rememberSaveable { mutableStateOf(false) }
        val effectiveAllowOnlineTiles = basemapMode == "auto" || basemapMode == "satellite" || allowOnlineTiles
        var immediateLaunchStarted by rememberSaveable { mutableStateOf(false) }
        val restoredResult = remember(resultValuesJson) {
            resultValuesJson
                ?.let(::plusCodeValuesFromJson)
                ?.let { values ->
                    As100PlusCodeCaptureMethod.result(
                        request = As100PlusCodeCaptureMethod.request(
                            action = As100PlusCodeCaptureMethod.ID,
                            context = context.request.invocationContext.asMap(As100PlusCodeCaptureMethod.ID) + context.action.settings + values
                        ),
                        values = values,
                        invocation = context.request.invocationContext
                    )
                }
        }
        val capturedResult = result ?: restoredResult

        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            hasLocationPermission = hasLocationPermission(androidContext)
            status = if (hasLocationPermission) "Location permission granted." else "Location permission is needed for GPS capture."
        }

        fun startGpsRefresh(openSelector: Boolean = false) {
            followGpsFixes = true
            averaging = true
            if (openSelector) selectorOpen = true
            status = "Refreshing GPS…"
        }

        LaunchedEffect(codeLength, gpsAverageSeconds, basemapMode, effectiveAllowOnlineTiles, gridSpanCells) {
            context.onSettingsChanged(
                mapOf(
                    "code_length" to codeLength.toString(),
                    "gps_average_seconds" to gpsAverageSeconds.toString(),
                    "basemap_mode" to basemapMode,
                    "allow_online_tiles" to effectiveAllowOnlineTiles.toString(),
                    "grid_span_cells" to gridSpanCells.toString()
                )
            )
        }

        LaunchedEffect(context.startsImmediately, hasLocationPermission) {
            if (context.startsImmediately && !immediateLaunchStarted) {
                if (!hasLocationPermission) {
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                } else {
                    immediateLaunchStarted = true
                    startGpsRefresh(openSelector = true)
                }
            }
        }

        fun recenterOnGps() {
            val location = latestLocation
            if (location == null) {
                status = "Acquire GPS first."
            } else {
                val gpsCell = OpenLocationCode.cellFor(location.latitude, location.longitude, codeLength)
                centerLatitude = gpsCell.centerLatitude
                centerLongitude = gpsCell.centerLongitude
                selectedLatitude = gpsCell.centerLatitude
                selectedLongitude = gpsCell.centerLongitude
                followGpsFixes = true
                status = "Centred on GPS. Tap the intended cell, or open fullscreen for step controls."
            }
        }

        fun captureResult(): ExecutionResult {
            val settings = mapOf(
                "code_length" to codeLength.toString(),
                "grid_span_cells" to gridSpanCells.toString(),
                "basemap_mode" to basemapMode,
                "allow_online_tiles" to effectiveAllowOnlineTiles.toString(),
                "basemap_actual_source" to actualBasemapSource(basemapMode, effectiveAllowOnlineTiles, gridSpanCells),
                "plus_code" to selectedCell.code,
                "selected_centroid_latitude" to selectedCell.centerLatitude.formatCoordinate(),
                "selected_centroid_longitude" to selectedCell.centerLongitude.formatCoordinate(),
                "gps_latitude" to latestLocation?.latitude?.formatCoordinate().orEmpty(),
                "gps_longitude" to latestLocation?.longitude?.formatCoordinate().orEmpty(),
                "gps_accuracy_m" to latestLocation?.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()?.formatNumber().orEmpty(),
                "gps_fix_count" to fixCount.toString(),
                "selected_time_iso" to Instant.now().toString()
            )
            val request = As100PlusCodeCaptureMethod.request(
                action = As100PlusCodeCaptureMethod.ID,
                context = context.request.invocationContext.asMap(As100PlusCodeCaptureMethod.ID) + context.action.settings + settings
            )
            val execution = As100PlusCodeCaptureMethod.result(
                request = request,
                values = As100PlusCodeCaptureMethod.captureValues(settings),
                invocation = context.request.invocationContext
            )
            result = execution
            resultValuesJson = plusCodeValuesToJson(OutputFormatter.fields(execution, includeProvenance = false)
                .filterKeys { it !in setOf("methodmesh_execution_id", "methodmesh_method_id", "methodmesh_status") })
            if (context.submitsImmediately) onConfirmed(execution)
            return execution
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                result = null
                resultValuesJson = null
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            if (context.startsImmediately) {
                Text("Acquiring GPS. Select the intended Plus Code cell on the map.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
            } else {
                Text("GPS starts the map. Open the fullscreen selector, tap the intended cell, or step the map one cell at a time. Codes are calculated offline.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))

                BasemapChooser(
                    value = basemapMode,
                    allowOnlineTiles = effectiveAllowOnlineTiles,
                    onChange = { basemapMode = it }
                )
                Spacer(Modifier.height(8.dp))
                CodeLengthChooser(codeLength, onChange = {
                    codeLength = it
                })
                Spacer(Modifier.height(8.dp))
                ZoomChooser(gridSpanCells, onChange = { gridSpanCells = it })
                Spacer(Modifier.height(8.dp))
                GpsDurationChooser(gpsAverageSeconds, onChange = { gpsAverageSeconds = it })
            }

            Spacer(Modifier.height(12.dp))
            if (!hasLocationPermission) {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                ) { Text("Grant location permission") }
            } else if (!context.startsImmediately) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = !averaging,
                        onClick = { startGpsRefresh() }
                    ) { Text(if (averaging) "Averaging…" else "Acquire GPS") }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = ::recenterOnGps
                    ) { Text("Centre on GPS") }
                }
            }

            GpsAverager(
                enabled = averaging && hasLocationPermission,
                durationSeconds = gpsAverageSeconds,
                onFix = { location, count ->
                    latestGpsLatitude = location.latitude.formatCoordinate()
                    latestGpsLongitude = location.longitude.formatCoordinate()
                    latestGpsAccuracy = location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble()?.formatNumber().orEmpty()
                    fixCount = count
                    if (followGpsFixes) {
                        val gpsCell = OpenLocationCode.cellFor(location.latitude, location.longitude, codeLength)
                        centerLatitude = gpsCell.centerLatitude
                        centerLongitude = gpsCell.centerLongitude
                        selectedLatitude = gpsCell.centerLatitude
                        selectedLongitude = gpsCell.centerLongitude
                    }
                    status = "GPS fix $count: ±${if (location.hasAccuracy()) location.accuracy.roundToInt() else 0} m"
                },
                onFinished = {
                    averaging = false
                    status = "GPS acquired. Tap or step to adjust the selected cell."
                },
                onError = {
                    averaging = false
                    status = it
                }
            )

            Spacer(Modifier.height(8.dp))
            Text(status)
            latestLocation?.let { location ->
                Text(
                    "GPS ${location.latitude.formatCoordinate()}, ${location.longitude.formatCoordinate()} · ±${if (location.hasAccuracy()) location.accuracy.roundToInt() else 0} m · $fixCount fix(es)",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (!context.startsImmediately) {
                Spacer(Modifier.height(12.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = latestLocation != null || centerLatitude != 0.0 || centerLongitude != 0.0,
                    onClick = { selectorOpen = true }
                ) {
                    Text("Open full-screen grid selector")
                }
                Text(
                    "Tap a cell to select it. The blue dot is the GPS reference.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(10.dp))
            Text("Selected Plus Code", fontWeight = FontWeight.Bold)
            Text(selectedCell.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
            Text("Centroid ${selectedCell.centerLatitude.formatCoordinate()}, ${selectedCell.centerLongitude.formatCoordinate()}", style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(12.dp))
            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = latestLocation != null || centerLatitude != 0.0 || centerLongitude != 0.0,
                onClick = { captureResult() }
            ) {
                Text("Use selected Plus Code")
            }
        }

        if (selectorOpen) {
            FullscreenPlusCodeSelector(
                centerLatitude = centerLatitude,
                centerLongitude = centerLongitude,
                codeLength = codeLength,
                gridSpanCells = gridSpanCells,
                gpsLocation = latestLocation,
                selectedCell = selectedCell,
                basemapMode = basemapMode,
                allowOnlineTiles = effectiveAllowOnlineTiles,
                onBasemapModeChange = { basemapMode = it },
                onZoomChange = { gridSpanCells = it },
                gpsRefreshRunning = averaging,
                onRefreshGps = { startGpsRefresh(openSelector = true) },
                onMapCenterChanged = { latitude, longitude ->
                    followGpsFixes = false
                    centerLatitude = latitude
                    centerLongitude = longitude
                    status = "Map centred on ${OpenLocationCode.cellFor(latitude, longitude, codeLength).code}"
                },
                onCellSelected = { cell ->
                    followGpsFixes = false
                    selectedLatitude = cell.centerLatitude
                    selectedLongitude = cell.centerLongitude
                    status = "Selected ${cell.code}"
                },
                onClose = {
                    selectorOpen = false
                    if (context.startsImmediately) captureResult()
                }
            )
        }
    }
}

private fun plusCodeValuesToJson(values: Map<String, Any?>): String =
    JSONObject().apply { values.toSortedMap().forEach { (key, value) -> put(key, value?.toString().orEmpty()) } }.toString()

private fun plusCodeValuesFromJson(json: String): Map<String, String> = runCatching {
    val root = JSONObject(json.ifBlank { "{}" })
    buildMap {
        root.keys().forEach { key -> put(key, root.optString(key)) }
    }
}.getOrDefault(emptyMap())

private const val OPENFREEMAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val MIN_GRID_SPAN_CELLS = 5
private const val DEFAULT_GRID_SPAN_CELLS = 129
private const val MAX_GRID_SPAN_CELLS = 129
private const val ESRI_WORLD_IMAGERY_STYLE_JSON = """
{
  "version": 8,
  "sources": {
    "esri-world-imagery": {
      "type": "raster",
      "tiles": [
        "https://services.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"
      ],
      "tileSize": 256,
      "attribution": "Tiles © Esri — Source: Esri, Maxar, Earthstar Geographics, and the GIS User Community"
    }
  },
  "layers": [
    {
      "id": "esri-world-imagery",
      "type": "raster",
      "source": "esri-world-imagery",
      "minzoom": 0,
      "maxzoom": 22
    }
  ]
}
"""

private data class ProjectedPlusCodeCell(
    val area: PlusCodeArea,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centre: Offset
        get() = Offset((left + right) / 2f, (top + bottom) / 2f)
}

private data class PlusCodeProjectionSnapshot(
    val codeLength: Int,
    val gridSpanCells: Int,
    val centreCode: String,
    val cells: List<ProjectedPlusCodeCell>,
    val mapCentre: Offset,
    val gps: Offset?
)

@Composable
private fun BasemapChooser(
    value: String,
    allowOnlineTiles: Boolean,
    onChange: (String) -> Unit
) {
    Text("Map mode", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("auto" to "Street", "satellite" to "Satellite", "blank" to "Grid").forEach { (mode, label) ->
            if (value == mode) {
                Button(onClick = { onChange(mode) }, modifier = Modifier.weight(1f)) { Text("✓ $label") }
            } else {
                OutlinedButton(onClick = { onChange(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
    Text(
        if ((value == "auto" || value == "satellite") && allowOnlineTiles) {
            "Online basemap enabled. Plus Codes are calculated locally."
        } else {
            "Grid hides the basemap. Plus Codes are calculated locally."
        },
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun CodeLengthChooser(value: Int, onChange: (Int) -> Unit) {
    Text("Grid precision", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(8, 10).forEach { length ->
            if (value == length) {
                Button(onClick = { onChange(length) }, modifier = Modifier.weight(1f)) { Text("✓ $length-digit") }
            } else {
                OutlinedButton(onClick = { onChange(length) }, modifier = Modifier.weight(1f)) { Text("$length-digit") }
            }
        }
    }
}

@Composable
private fun GpsDurationChooser(value: Int, onChange: (Int) -> Unit) {
    Text("GPS averaging", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5, 10, 20).forEach { seconds ->
            if (value == seconds) {
                Button(onClick = { onChange(seconds) }, modifier = Modifier.weight(1f)) { Text("✓ ${seconds}s") }
            } else {
                OutlinedButton(onClick = { onChange(seconds) }, modifier = Modifier.weight(1f)) { Text("${seconds}s") }
            }
        }
    }
}

@Composable
private fun ZoomChooser(value: Int, onChange: (Int) -> Unit) {
    Text("Grid zoom", fontWeight = FontWeight.SemiBold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(5 to "Near", 33 to "Field", 129 to "Area").forEach { (span, label) ->
            if (value == span) {
                Button(onClick = { onChange(span) }, modifier = Modifier.weight(1f)) { Text("✓ $label") }
            } else {
                OutlinedButton(onClick = { onChange(span) }, modifier = Modifier.weight(1f)) { Text(label) }
            }
        }
    }
}

@Composable
private fun FullscreenPlusCodeSelector(
    centerLatitude: Double,
    centerLongitude: Double,
    codeLength: Int,
    gridSpanCells: Int,
    gpsLocation: Location?,
    selectedCell: PlusCodeArea,
    basemapMode: String,
    allowOnlineTiles: Boolean,
    onBasemapModeChange: (String) -> Unit,
    onZoomChange: (Int) -> Unit,
    gpsRefreshRunning: Boolean,
    onRefreshGps: () -> Unit,
    onMapCenterChanged: (Double, Double) -> Unit,
    onCellSelected: (PlusCodeArea) -> Unit,
    onClose: () -> Unit
) {
    var projectionSnapshot by remember { mutableStateOf<PlusCodeProjectionSnapshot?>(null) }

    fun stepMap(latitudeSteps: Int, longitudeSteps: Int) {
        val cell = selectedCell
        val nextCell = OpenLocationCode.cellFor(
            (cell.centerLatitude + latitudeSteps * cell.latitudeHeight).coerceIn(-89.999999, 89.999999),
            normalizeLongitude(cell.centerLongitude + longitudeSteps * cell.longitudeWidth),
            codeLength
        )
        onMapCenterChanged(nextCell.centerLatitude, nextCell.centerLongitude)
        onCellSelected(nextCell)
    }

    fun centreOnGps() {
        gpsLocation?.let { location ->
            val cell = OpenLocationCode.cellFor(location.latitude, location.longitude, codeLength)
            onMapCenterChanged(cell.centerLatitude, cell.centerLongitude)
            onCellSelected(cell)
        }
    }

    fun zoomIn() {
        onZoomChange(
            when {
                gridSpanCells > 65 -> 65
                gridSpanCells > 33 -> 33
                gridSpanCells > 25 -> 25
                gridSpanCells > 17 -> 17
                gridSpanCells > 9 -> 9
                gridSpanCells > 5 -> 5
                else -> 5
            }
        )
    }

    fun zoomOut() {
        onZoomChange(
            when {
                gridSpanCells < 9 -> 9
                gridSpanCells < 17 -> 17
                gridSpanCells < 25 -> 25
                gridSpanCells < 33 -> 33
                gridSpanCells < 65 -> 65
                gridSpanCells < MAX_GRID_SPAN_CELLS -> MAX_GRID_SPAN_CELLS
                else -> MAX_GRID_SPAN_CELLS
            }
        )
    }

    val coarseStepCells = normalizedGridSpan(gridSpanCells)

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Box(Modifier.fillMaxSize().background(Color(0xFFEAE3DE))) {
            Box(modifier = Modifier.fillMaxSize()) {
                PlusCodeBasemap(
                    modifier = Modifier.fillMaxSize(),
                    centerLatitude = centerLatitude,
                    centerLongitude = centerLongitude,
                    codeLength = codeLength,
                    gridSpanCells = gridSpanCells,
                    basemapMode = basemapMode,
                    allowOnlineTiles = allowOnlineTiles,
                    gpsLocation = gpsLocation,
                    onProjectionChanged = { projectionSnapshot = it }
                )
                PlusCodeGridCanvas(
                    modifier = Modifier.fillMaxSize(),
                    centerLatitude = centerLatitude,
                    centerLongitude = centerLongitude,
                    codeLength = codeLength,
                    gridSpanCells = gridSpanCells,
                    gpsLocation = gpsLocation,
                    selectedCell = selectedCell,
                    projectionSnapshot = projectionSnapshot.takeUnless { basemapMode == "blank" },
                    onCellSelected = onCellSelected
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xEFFFFFFF))
                    .padding(top = 34.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Text("Plus Code", fontWeight = FontWeight.Bold)
                Text(selectedCell.code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace)
                Text("Tap a cell to select it. Single arrows nudge; double arrows jump a screen.", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("auto" to "Street", "satellite" to "Satellite", "blank" to "Grid").forEach { (mode, label) ->
                        if (basemapMode == mode) {
                            Button(onClick = { onBasemapModeChange(mode) }, modifier = Modifier.weight(1f)) { Text("✓ $label") }
                        } else {
                            OutlinedButton(onClick = { onBasemapModeChange(mode) }, modifier = Modifier.weight(1f)) { Text(label) }
                        }
                    }
                }
                if ((basemapMode == "auto" || basemapMode == "satellite") && allowOnlineTiles) {
                    Text(onlineTileStatusLabel(basemapMode, gridSpanCells), style = MaterialTheme.typography.bodySmall)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .background(Color(0xEFFFFFFF))
                    .navigationBarsPadding()
                    .padding(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = ::zoomOut, modifier = Modifier.weight(1f).height(38.dp)) { Text("−") }
                    Text(
                        "${gridSpanCells}×${gridSpanCells} cells",
                        modifier = Modifier.weight(2f).padding(top = 8.dp),
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = ::zoomIn, modifier = Modifier.weight(1f).height(38.dp)) { Text("+") }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = ::centreOnGps,
                        enabled = gpsLocation != null,
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text("Centre on GPS") }
                    OutlinedButton(
                        onClick = onRefreshGps,
                        enabled = !gpsRefreshRunning,
                        modifier = Modifier.weight(1f).height(38.dp)
                    ) { Text(if (gpsRefreshRunning) "Refreshing…" else "Refresh GPS") }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MovementPad(
                        label = "Fine",
                        upLabel = "↑",
                        leftLabel = "←",
                        rightLabel = "→",
                        downLabel = "↓",
                        onUp = { stepMap(latitudeSteps = 1, longitudeSteps = 0) },
                        onLeft = { stepMap(latitudeSteps = 0, longitudeSteps = -1) },
                        onRight = { stepMap(latitudeSteps = 0, longitudeSteps = 1) },
                        onDown = { stepMap(latitudeSteps = -1, longitudeSteps = 0) },
                        modifier = Modifier.weight(1f)
                    )
                    MovementPad(
                        label = "Jump",
                        upLabel = "⇈",
                        leftLabel = "⇐",
                        rightLabel = "⇒",
                        downLabel = "⇊",
                        onUp = { stepMap(latitudeSteps = coarseStepCells, longitudeSteps = 0) },
                        onLeft = { stepMap(latitudeSteps = 0, longitudeSteps = -coarseStepCells) },
                        onRight = { stepMap(latitudeSteps = 0, longitudeSteps = coarseStepCells) },
                        onDown = { stepMap(latitudeSteps = -coarseStepCells, longitudeSteps = 0) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(modifier = Modifier.fillMaxWidth().height(44.dp), onClick = onClose) {
                    Text("Use this cell")
                }
            }
        }
    }
}

@Composable
private fun MovementPad(
    label: String,
    upLabel: String,
    leftLabel: String,
    rightLabel: String,
    downLabel: String,
    onUp: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(onClick = onUp, modifier = Modifier.size(width = 56.dp, height = 34.dp)) { Text(upLabel) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onLeft, modifier = Modifier.size(width = 56.dp, height = 34.dp)) { Text(leftLabel) }
            OutlinedButton(onClick = onRight, modifier = Modifier.size(width = 56.dp, height = 34.dp)) { Text(rightLabel) }
        }
        OutlinedButton(onClick = onDown, modifier = Modifier.size(width = 56.dp, height = 34.dp)) { Text(downLabel) }
    }
}

@Composable
private fun PlusCodeBasemap(
    modifier: Modifier,
    centerLatitude: Double,
    centerLongitude: Double,
    codeLength: Int,
    gridSpanCells: Int,
    basemapMode: String,
    allowOnlineTiles: Boolean,
    gpsLocation: Location?,
    onProjectionChanged: (PlusCodeProjectionSnapshot?) -> Unit
) {
    if (basemapMode == "blank") {
        onProjectionChanged(null)
        return
    }
    val renderBasemapMode = renderBasemapMode(basemapMode, gridSpanCells)
    val mayUseDataConnection = (renderBasemapMode == "auto" || renderBasemapMode == "satellite") && allowOnlineTiles
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            MapView(viewContext).apply {
                onCreate(null)
                getMapAsync { map ->
                    map.uiSettings.setAllGesturesEnabled(false)
                    map.uiSettings.isCompassEnabled = false
                    map.uiSettings.isLogoEnabled = false
                    map.uiSettings.isAttributionEnabled = true
                    if (mayUseDataConnection) {
                        map.setPlusCodeBasemapStyle(renderBasemapMode)
                    }
                    map.cameraPosition = CameraPosition.Builder()
                        .target(LatLng(centerLatitude, centerLongitude))
                        .zoom(plusCodeMapZoom(codeLength, gridSpanCells, renderBasemapMode))
                        .build()
                    post {
                        onProjectionChanged(projectPlusCodeGrid(map, codeLength, gridSpanCells, centerLatitude, centerLongitude, gpsLocation))
                    }
                }
                onResume()
            }
        },
        update = { mapView ->
            mapView.getMapAsync { map ->
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(centerLatitude, centerLongitude))
                    .zoom(plusCodeMapZoom(codeLength, gridSpanCells, renderBasemapMode))
                    .build()
                if (mayUseDataConnection && mapView.tag != renderBasemapMode) {
                    mapView.tag = renderBasemapMode
                    map.setPlusCodeBasemapStyle(renderBasemapMode)
                }
                mapView.post {
                    onProjectionChanged(projectPlusCodeGrid(map, codeLength, gridSpanCells, centerLatitude, centerLongitude, gpsLocation))
                }
            }
        }
    )
}

private fun org.maplibre.android.maps.MapLibreMap.setPlusCodeBasemapStyle(mode: String) {
    if (mode == "satellite") {
        setStyle(Style.Builder().fromJson(ESRI_WORLD_IMAGERY_STYLE_JSON))
    } else {
        setStyle(OPENFREEMAP_STYLE_URL)
    }
}

private fun projectPlusCodeGrid(
    map: MapLibreMap,
    codeLength: Int,
    gridSpanCells: Int,
    centerLatitude: Double,
    centerLongitude: Double,
    gpsLocation: Location?
): PlusCodeProjectionSnapshot {
    val centerCell = OpenLocationCode.cellFor(centerLatitude, centerLongitude, codeLength)
    val span = normalizedGridSpan(gridSpanCells)
    val halfSpan = span / 2
    val cells = (-(halfSpan + 1)..(halfSpan + 1)).flatMap { row ->
        (-(halfSpan + 1)..(halfSpan + 1)).map { column ->
            val cell = OpenLocationCode.cellFor(
                centerCell.centerLatitude + row * centerCell.latitudeHeight,
                normalizeLongitude(centerCell.centerLongitude + column * centerCell.longitudeWidth),
                centerCell.codeLength
            )
            val northWest = map.projection.toScreenLocation(LatLng(cell.north, cell.west))
            val southEast = map.projection.toScreenLocation(LatLng(cell.south, cell.east))
            ProjectedPlusCodeCell(
                area = cell,
                left = minOf(northWest.x, southEast.x),
                top = minOf(northWest.y, southEast.y),
                right = maxOf(northWest.x, southEast.x),
                bottom = maxOf(northWest.y, southEast.y)
            )
        }
    }
    val mapCenterPoint = map.projection.toScreenLocation(LatLng(centerLatitude, centerLongitude))
    val gpsPoint = gpsLocation?.let { location ->
        val point = map.projection.toScreenLocation(LatLng(location.latitude, location.longitude))
        Offset(point.x, point.y)
    }
    return PlusCodeProjectionSnapshot(
        codeLength = codeLength,
        gridSpanCells = span,
        centreCode = centerCell.code,
        cells = cells,
        mapCentre = Offset(mapCenterPoint.x, mapCenterPoint.y),
        gps = gpsPoint
    )
}

@Composable
private fun PlusCodeGridCanvas(
    modifier: Modifier,
    centerLatitude: Double,
    centerLongitude: Double,
    codeLength: Int,
    gridSpanCells: Int,
    gpsLocation: Location?,
    selectedCell: PlusCodeArea,
    projectionSnapshot: PlusCodeProjectionSnapshot?,
    onCellSelected: (PlusCodeArea) -> Unit
) {
    val centerCell = remember(centerLatitude, centerLongitude, codeLength) {
        OpenLocationCode.cellFor(centerLatitude, centerLongitude, codeLength)
    }
    val span = normalizedGridSpan(gridSpanCells)
    val halfSpan = span / 2
    val cells = remember(centerCell, span) {
        (-(halfSpan + 1)..(halfSpan + 1)).flatMap { row ->
            (-(halfSpan + 1)..(halfSpan + 1)).map { column ->
                OpenLocationCode.cellFor(
                    centerCell.centerLatitude + row * centerCell.latitudeHeight,
                    centerCell.centerLongitude + column * centerCell.longitudeWidth,
                    centerCell.codeLength
                )
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(centerCell, span, codeLength, projectionSnapshot) {
                detectTapGestures { tapOffset ->
                    projectionSnapshot
                        ?.cells
                        ?.firstOrNull { cell ->
                            tapOffset.x in cell.left..cell.right && tapOffset.y in cell.top..cell.bottom
                        }
                        ?.area
                        ?.let { tappedCell ->
                            onCellSelected(tappedCell)
                            return@detectTapGestures
                        }
                    val gridSidePx = minOf(size.width, size.height).toFloat()
                    val gridLeft = (size.width - gridSidePx) / 2f
                    val gridTop = (size.height - gridSidePx) / 2f
                    if (tapOffset.x !in gridLeft..(gridLeft + gridSidePx) || tapOffset.y !in gridTop..(gridTop + gridSidePx)) return@detectTapGestures
                    val cellSizePx = gridSidePx / span
                    val centreX = gridLeft + gridSidePx / 2f
                    val centreY = gridTop + gridSidePx / 2f
                    val latitudeDelta = ((centreY - tapOffset.y) / cellSizePx) * centerCell.latitudeHeight
                    val longitudeDelta = ((tapOffset.x - centreX) / cellSizePx) * centerCell.longitudeWidth
                    val tappedCell = OpenLocationCode.cellFor(
                        (centerLatitude + latitudeDelta).coerceIn(-89.999999, 89.999999),
                        normalizeLongitude(centerLongitude + longitudeDelta),
                        codeLength
                    )
                    onCellSelected(tappedCell)
                }
            }
            .padding(8.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val normalGridStroke = when {
                span >= 129 -> 0.45.dp.toPx()
                span >= 65 -> 0.65.dp.toPx()
                span >= 33 -> 0.9.dp.toPx()
                span >= 17 -> 1.15.dp.toPx()
                else -> 1.5.dp.toPx()
            }
            val normalGridColor = Color.White.copy(
                alpha = when {
                    span >= 129 -> 0.45f
                    span >= 65 -> 0.55f
                    span >= 33 -> 0.68f
                    else -> 0.9f
                }
            )
            if (projectionSnapshot != null) {
                projectionSnapshot.cells.forEach { projectedCell ->
                    val isSelected = projectedCell.area.code == selectedCell.code
                    val topLeft = Offset(projectedCell.left, projectedCell.top)
                    val cellSize = Size(
                        width = (projectedCell.right - projectedCell.left).coerceAtLeast(1f),
                        height = (projectedCell.bottom - projectedCell.top).coerceAtLeast(1f)
                    )
                    drawRect(
                        color = if (isSelected) Color(0x443A7D44) else Color.Transparent,
                        topLeft = topLeft,
                        size = cellSize
                    )
                    drawRect(
                        color = if (isSelected) Color(0xFF2E7D32) else normalGridColor,
                        topLeft = topLeft,
                        size = cellSize,
                        style = Stroke(if (isSelected) 4.dp.toPx() else normalGridStroke)
                    )
                    if (isSelected) {
                        drawPlusCodeCrosshair(projectedCell.centre)
                    }
                }
                projectionSnapshot.gps?.let { gps ->
                    if (gps.x in 0f..size.width && gps.y in 0f..size.height) {
                        drawCircle(
                            color = Color.White,
                            radius = 12.dp.toPx(),
                            center = gps
                        )
                        drawCircle(
                            color = Color(0xFF004DCC),
                            radius = 8.dp.toPx(),
                            center = gps
                        )
                    }
                }
                drawCircle(
                    color = Color(0x88222222),
                    radius = 5.dp.toPx(),
                    center = projectionSnapshot.mapCentre,
                )
                return@Canvas
            }

            val gridSide = minOf(size.width, size.height)
            val gridLeft = (size.width - gridSide) / 2f
            val gridTop = (size.height - gridSide) / 2f
            val cellSize = gridSide / span
            val gridCentre = Offset(gridLeft + gridSide / 2f, gridTop + gridSide / 2f)
            cells.forEach { cell ->
                val deltaLonCells = normalizeLongitude(cell.centerLongitude - centerLongitude) / centerCell.longitudeWidth
                val deltaLatCells = (cell.centerLatitude - centerLatitude) / centerCell.latitudeHeight
                val left = gridCentre.x + ((deltaLonCells - 0.5) * cellSize).toFloat()
                val top = gridCentre.y - ((deltaLatCells + 0.5) * cellSize).toFloat()
                val isSelected = cell.code == selectedCell.code
                drawRect(
                    color = when {
                        isSelected -> Color(0x443A7D44)
                        else -> Color.Transparent
                    },
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize)
                )
                drawRect(
                    color = if (isSelected) Color(0xFF2E7D32) else normalGridColor,
                    topLeft = Offset(left, top),
                    size = Size(cellSize, cellSize),
                    style = Stroke(if (isSelected) 4.dp.toPx() else normalGridStroke)
                )
                if (isSelected) {
                    val selectedCentre = Offset(left + cellSize / 2f, top + cellSize / 2f)
                    drawPlusCodeCrosshair(selectedCentre)
                }
            }

            gpsLocation?.let { location ->
                val latOffsetCells = ((location.latitude - centerLatitude) / centerCell.latitudeHeight).toFloat()
                val lonOffsetCells = (normalizeLongitude(location.longitude - centerLongitude) / centerCell.longitudeWidth).toFloat()
                val gpsX = gridCentre.x + lonOffsetCells * cellSize
                val gpsY = gridCentre.y - latOffsetCells * cellSize
                if (gpsX in gridLeft..(gridLeft + gridSide) && gpsY in gridTop..(gridTop + gridSide)) {
                    drawCircle(
                        color = Color.White,
                        radius = 12.dp.toPx(),
                        center = Offset(gpsX, gpsY)
                    )
                    drawCircle(
                        color = Color(0xFF004DCC),
                        radius = 8.dp.toPx(),
                        center = Offset(gpsX, gpsY)
                    )
                }
            }

            drawCircle(
                color = Color(0x88222222),
                radius = 5.dp.toPx(),
                center = gridCentre,
            )
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun GpsAverager(
    enabled: Boolean,
    durationSeconds: Int,
    onFix: (Location, Int) -> Unit,
    onFinished: () -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current

    androidx.compose.runtime.DisposableEffect(enabled, durationSeconds) {
        if (!enabled) {
            onDispose { }
        } else {
            val fusedLocationClient: FusedLocationProviderClient =
                LocationServices.getFusedLocationProviderClient(context)
            val fixes = mutableListOf<Location>()
            val started = System.currentTimeMillis()
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
                .setMinUpdateIntervalMillis(500L)
                .setMaxUpdateDelayMillis(1000L)
                .setWaitForAccurateLocation(false)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    fixes += location
                    onFix(averageLocation(fixes), fixes.size)
                    if (System.currentTimeMillis() - started >= durationSeconds * 1000L) {
                        fusedLocationClient.removeLocationUpdates(this)
                        onFinished()
                    }
                }
            }
            fusedLocationClient.requestLocationUpdates(locationRequest, callback, context.mainLooper)
                .addOnFailureListener { onError("Could not start GPS: ${it.message ?: "location error"}") }
            onDispose { fusedLocationClient.removeLocationUpdates(callback) }
        }
    }
}

private fun averageLocation(locations: List<Location>): Location {
    val best = locations.minByOrNull { if (it.hasAccuracy()) it.accuracy else Float.MAX_VALUE } ?: locations.last()
    return Location(best).apply {
        latitude = locations.map { it.latitude }.average()
        longitude = locations.map { it.longitude }.average()
        if (locations.any { it.hasAccuracy() }) {
            accuracy = locations.mapNotNull { if (it.hasAccuracy()) it.accuracy else null }.average().toFloat()
        }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun renderBasemapMode(mode: String, gridSpanCells: Int): String =
    if (mode == "satellite" && normalizedGridSpan(gridSpanCells) <= 9) "auto" else mode

private fun onlineTileStatusLabel(mode: String, gridSpanCells: Int): String =
    if (mode == "satellite" && renderBasemapMode(mode, gridSpanCells) == "auto") {
        "Street tiles shown at 9×9; satellite resumes when zoomed out."
    } else {
        "Online ${if (mode == "satellite") "satellite" else "street"} tiles enabled"
    }

private fun actualBasemapSource(mode: String, allowOnlineTiles: Boolean, gridSpanCells: Int): String =
    when (renderBasemapMode(mode, gridSpanCells)) {
        "auto" -> if (allowOnlineTiles) "openfreemap_online_or_cache" else "cached_map_tiles"
        "satellite" -> if (allowOnlineTiles) "esri_world_imagery_online_or_cache" else "cached_satellite_tiles"
        "offline" -> "cached_map_tiles"
        else -> "blank_grid"
    }

private fun DrawScope.drawPlusCodeCrosshair(selectedCentre: Offset) {
    drawLine(
        color = Color.White,
        start = Offset(selectedCentre.x - 34.dp.toPx(), selectedCentre.y),
        end = Offset(selectedCentre.x + 34.dp.toPx(), selectedCentre.y),
        strokeWidth = 8.dp.toPx()
    )
    drawLine(
        color = Color.White,
        start = Offset(selectedCentre.x, selectedCentre.y - 34.dp.toPx()),
        end = Offset(selectedCentre.x, selectedCentre.y + 34.dp.toPx()),
        strokeWidth = 8.dp.toPx()
    )
    drawCircle(
        color = Color.White,
        radius = 28.dp.toPx(),
        center = selectedCentre,
        style = Stroke(width = 8.dp.toPx())
    )
    drawLine(
        color = Color(0xFF2E7D32),
        start = Offset(selectedCentre.x - 34.dp.toPx(), selectedCentre.y),
        end = Offset(selectedCentre.x + 34.dp.toPx(), selectedCentre.y),
        strokeWidth = 4.dp.toPx()
    )
    drawLine(
        color = Color(0xFF2E7D32),
        start = Offset(selectedCentre.x, selectedCentre.y - 34.dp.toPx()),
        end = Offset(selectedCentre.x, selectedCentre.y + 34.dp.toPx()),
        strokeWidth = 4.dp.toPx()
    )
    drawCircle(
        color = Color(0xFF2E7D32),
        radius = 28.dp.toPx(),
        center = selectedCentre,
        style = Stroke(width = 4.dp.toPx())
    )
}

private fun normalizedGridSpan(gridSpanCells: Int): Int =
    if (gridSpanCells % 2 == 0) gridSpanCells + 1 else gridSpanCells

private fun plusCodeMapZoom(codeLength: Int, gridSpanCells: Int, basemapMode: String): Double {
    val cell = OpenLocationCode.cellFor(0.0, 0.0, codeLength)
    val visibleLongitudeDegrees = (cell.longitudeWidth * gridSpanCells).coerceAtLeast(0.00001)
    val nominalViewportPixels = 520.0
    val rawZoom = ln(360.0 * nominalViewportPixels / (256.0 * visibleLongitudeDegrees)) / ln(2.0)
    val maxZoom = if (basemapMode == "satellite") 18.6 else 22.0
    return rawZoom.coerceIn(1.0, maxZoom)
}

private fun normalizeLongitude(longitude: Double): Double {
    var lon = longitude
    while (lon < -180.0) lon += 360.0
    while (lon >= 180.0) lon -= 360.0
    return lon
}

private fun Double.formatCoordinate(): String = "%.8f".format(this).trimEnd('0').trimEnd('.')
private fun Double.formatNumber(): String = "%.3f".format(this).trimEnd('0').trimEnd('.')
