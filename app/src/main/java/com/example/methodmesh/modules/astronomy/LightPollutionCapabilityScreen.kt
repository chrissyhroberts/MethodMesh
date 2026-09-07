package com.example.methodmesh.modules.astronomy

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object LightPollutionCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LightPollutionMethod.id
    override val title = "Light pollution heatmap"
    override val description = "Download NASA VIIRS nighttime radiance around a site, cache it locally, and display it as a transparent light-pollution proxy over a map."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val app = LocalContext.current
        val scope = rememberCoroutineScope()
        val repo = remember { LightPollutionRepository(app) }
        val initial = remember(context.action.settings) { context.action.settings.mapKeys { it.key.removePrefix("input_") } }

        var mode by rememberSaveable { mutableStateOf(initial["location_source"].orEmpty().ifBlank { "auto" }) }
        var plusCode by rememberSaveable { mutableStateOf(initial["plus_code"].orEmpty()) }
        var latitude by rememberSaveable { mutableStateOf(initial["latitude"].orEmpty()) }
        var longitude by rememberSaveable { mutableStateOf(initial["longitude"].orEmpty()) }
        var radiusKm by rememberSaveable { mutableStateOf(initial["radius_km"]?.toDoubleOrNull()?.coerceIn(5.0, 200.0) ?: 50.0) }
        var basemapMode by rememberSaveable { mutableStateOf(initial["basemap_mode"].orEmpty().ifBlank { "auto" }) }
        var opacity by rememberSaveable { mutableStateOf(initial["heatmap_opacity"]?.toFloatOrNull()?.coerceIn(0.15f, 0.9f) ?: 0.58f) }
        val fetchIfMissing = initial["fetch_if_missing"]?.toBooleanStrictOrNull() ?: true

        var rasterRegions by remember { mutableStateOf(repo.listRasterRegions()) }
        var legacyRegions by remember { mutableStateOf(repo.listRegions()) }
        var activeRaster by remember { mutableStateOf<LightPollutionRepository.RasterRegion?>(null) }
        var heatmap by remember { mutableStateOf<Bitmap?>(null) }
        var status by rememberSaveable { mutableStateOf("Choose a site, then load a NASA heatmap.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var values by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var lastLocation by remember { mutableStateOf<AstroLocation?>(null) }
        var busy by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var pendingGpsAction by rememberSaveable { mutableStateOf("check") }
        var pendingSubmitImmediately by rememberSaveable { mutableStateOf(false) }
        var pendingDownloadMissing by rememberSaveable { mutableStateOf(false) }

        val interactiveNative =
            context.request.source.equals("intent_test", ignoreCase = true) ||
                context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun

        fun settings(): Map<String, String> = mapOf(
            "location_source" to mode,
            "plus_code" to plusCode,
            "latitude" to latitude,
            "longitude" to longitude,
            "radius_km" to radiusKm.toString(),
            "basemap_mode" to basemapMode,
            "heatmap_opacity" to opacity.toString(),
            "fetch_if_missing" to fetchIfMissing.toString()
        )

        LaunchedEffect(mode, plusCode, latitude, longitude, radiusKm, basemapMode, opacity) {
            context.onSettingsChanged(context.action.settings + settings())
        }

        fun selected(): AstroLocation? = when (mode) {
            "plus_code" -> explicitLocation(mapOf("plus_code" to plusCode, "location_source" to "plus_code"))
            "manual" -> explicitLocation(mapOf("plus_code" to "", "latitude" to latitude, "longitude" to longitude, "location_source" to "manual"))
            else -> null
        }

        fun updateMapFor(location: AstroLocation) {
            lastLocation = location
            val region = repo.rasterCovering(location.latitude, location.longitude)
            activeRaster = region
            heatmap = region?.let(repo::heatmapBitmap)
        }

        fun applyLookup(location: AstroLocation, submitImmediately: Boolean) {
            lastLocation = location
            val lookup = repo.query(location.latitude, location.longitude)
            val displayRegion = lookup
                ?.takeIf { it.cacheKind == "nasa_gibs_raster" }
                ?.let { repo.rasterById(it.regionId) }
                ?: repo.rasterCovering(location.latitude, location.longitude)
            activeRaster = displayRegion
            heatmap = displayRegion?.let(repo::heatmapBitmap)
            val calculated = lookup?.let(As100LightPollutionMethod::fromLookup) ?: As100LightPollutionMethod.noCache()
            values = calculated
            status = when {
                lookup == null -> "No cached radiance covers this site yet. Load a heatmap to fetch NASA data."
                lookup.cacheKind == "nasa_gibs_raster" -> "NASA ${lookup.datasetDate} · cached raster · ${"%.2f".format(lookup.value)} ${lookup.unit}."
                else -> "Using legacy imported region ${lookup.regionName}."
            }
            val effective = settings() + mapOf(
                "latitude" to location.latitude.toString(),
                "longitude" to location.longitude.toString(),
                "resolved_location_source" to location.source
            )
            val execution = methodExecution(As100LightPollutionMethod, context, calculated, effective)
            result = if (lookup != null || submitImmediately) execution else null
            if (submitImmediately) onConfirmed(execution)
        }

        fun downloadAt(location: AstroLocation, submitImmediately: Boolean) {
            lastLocation = location
            busy = true
            status = "Downloading ${radiusKm.toInt()} km NASA nighttime-radiance region…"
            scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        LightPollutionNetwork.downloadAndCache(repo, location.latitude, location.longitude, radiusKm)
                    }
                }.onSuccess { region ->
                    rasterRegions = repo.listRasterRegions()
                    activeRaster = region
                    heatmap = repo.heatmapBitmap(region)
                    busy = false
                    applyLookup(location, submitImmediately)
                }.onFailure { error ->
                    busy = false
                    status = "NASA radiance download failed: ${error.message.orEmpty()}"
                    val calculated = As100LightPollutionMethod.noCache().toMutableMap().apply {
                        put(LightPollutionFields.ERROR, status)
                    }
                    values = calculated
                    if (submitImmediately) {
                        val execution = methodExecution(As100LightPollutionMethod, context, calculated, settings())
                        result = execution
                        onConfirmed(execution)
                    }
                }
            }
        }

        fun resolveLocation(onResolved: (AstroLocation) -> Unit, submitImmediately: Boolean) {
            if (mode == "auto") {
                currentLocation(app, onResolved) { message ->
                    status = message
                    busy = false
                    if (submitImmediately) {
                        val calculated = As100LightPollutionMethod.locationFailure(message)
                        val execution = methodExecution(As100LightPollutionMethod, context, calculated, settings())
                        result = execution
                        onConfirmed(execution)
                    }
                }
                return
            }
            val location = selected()
            if (location == null) {
                val message = if (mode == "plus_code") "Enter a valid Plus Code." else "Enter valid coordinates."
                status = message
                if (submitImmediately) {
                    val calculated = As100LightPollutionMethod.locationFailure(message)
                    val execution = methodExecution(As100LightPollutionMethod, context, calculated, settings())
                    result = execution
                    onConfirmed(execution)
                }
            } else onResolved(location)
        }

        fun checkSite(submitImmediately: Boolean = false, downloadMissing: Boolean = false) {
            resolveLocation({ location ->
                val lookup = repo.query(location.latitude, location.longitude)
                if (lookup == null && downloadMissing) downloadAt(location, submitImmediately)
                else applyLookup(location, submitImmediately)
            }, submitImmediately)
        }

        fun loadHeatmap(submitImmediately: Boolean = false) {
            resolveLocation({ location -> downloadAt(location, submitImmediately) }, submitImmediately)
        }

        val gpsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _: Map<String, Boolean> ->
            if (hasLocationPermission(app)) {
                if (pendingGpsAction == "download") {
                    loadHeatmap(pendingSubmitImmediately)
                } else {
                    checkSite(pendingSubmitImmediately, pendingDownloadMissing)
                }
            } else {
                status = "Location denied. Use Plus Code or coordinates."
                busy = false
            }
        }

        fun requestGpsFor(action: String, submitImmediately: Boolean, downloadMissing: Boolean = false) {
            pendingGpsAction = action
            pendingSubmitImmediately = submitImmediately
            pendingDownloadMissing = downloadMissing
            gpsLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }

        fun startCheck(submitImmediately: Boolean = false, downloadMissing: Boolean = false) {
            if (mode != "auto" || hasLocationPermission(app)) {
                checkSite(submitImmediately, downloadMissing)
            } else {
                requestGpsFor("check", submitImmediately, downloadMissing)
            }
        }

        fun startLoadHeatmap(submitImmediately: Boolean = false) {
            if (mode != "auto" || hasLocationPermission(app)) {
                loadHeatmap(submitImmediately)
            } else {
                requestGpsFor("download", submitImmediately)
            }
        }

        LaunchedEffect(Unit) {
            if (!launched) {
                launched = true
                if (!interactiveNative) startCheck(submitImmediately = true, downloadMissing = fetchIfMissing)
            }
        }

        val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("Could not read selected file.")
                    }
                }.mapCatching(repo::importRegion)
                    .onSuccess { region ->
                        legacyRegions = repo.listRegions()
                        status = "Imported legacy region ${region.name}."
                        lastLocation?.let { applyLookup(it, submitImmediately = false) }
                    }
                    .onFailure { error -> status = "Import failed: ${error.message.orEmpty()}" }
            }
        }

        val scaffoldResult = if (interactiveNative) null else result
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = emptyMap(),
            onBack = onBack,
            onRetry = { result = null; values = emptyMap() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodySmall)
            Text("NASA GIBS supplies the map imagery. The satellite quantity is upward nighttime radiance, used here as a light-pollution proxy; it is not a Bortle class or a direct SQM measurement.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.padding(4.dp))

            Text("Site", fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("auto" to "GPS", "plus_code" to "Plus Code", "manual" to "Lat/lon").forEach { (key, label) ->
                    OutlinedButton(onClick = { mode = key; values = emptyMap(); result = null }, modifier = Modifier.weight(1f)) {
                        Text(if (mode == key) "✓ $label" else label)
                    }
                }
            }
            when (mode) {
                "plus_code" -> OutlinedTextField(plusCode, { plusCode = it }, label = { Text("Plus Code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                "manual" -> Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(latitude, { latitude = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f))
                    Spacer(Modifier.padding(3.dp))
                    OutlinedTextField(longitude, { longitude = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f))
                }
            }

            Text("Offline radius", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf(10.0, 25.0, 50.0, 100.0).forEach { radius ->
                    OutlinedButton(onClick = { radiusKm = radius }, modifier = Modifier.weight(1f)) {
                        Text(if (radiusKm == radius) "✓ ${radius.toInt()} km" else "${radius.toInt()} km")
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Button(onClick = { startLoadHeatmap(false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    if (busy) CircularProgressIndicator(strokeWidth = 2.dp) else Text("Load / refresh heatmap")
                }
                OutlinedButton(onClick = { startCheck(false, false) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                    Text("Use cached")
                }
            }

            lastLocation?.let { location ->
                Text("Map", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf("auto" to "Street", "satellite" to "Satellite", "blank" to "Dark").forEach { (key, label) ->
                        OutlinedButton(onClick = { basemapMode = key }, modifier = Modifier.weight(1f)) {
                            Text(if (basemapMode == key) "✓ $label" else label)
                        }
                    }
                }
                LightPollutionMap(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    radiusKm = activeRaster?.radiusKm ?: radiusKm,
                    region = activeRaster,
                    heatmap = heatmap,
                    basemapMode = basemapMode,
                    opacity = opacity
                )
                Text("Heatmap opacity ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.15f..0.9f)
                Text("Legend: blue = low radiance · green/yellow = moderate · orange/red/purple = high", style = MaterialTheme.typography.bodySmall)
            }

            val resultText = values[LightPollutionFields.RESULT].orEmpty()
            val label = values[LightPollutionFields.LABEL].orEmpty()
            if (label.isNotBlank()) {
                Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(resultText)
                        Text(
                            listOf(values[LightPollutionFields.DATASET_DATE], values[LightPollutionFields.DATASET], values[LightPollutionFields.CACHE_KIND])
                                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (interactiveNative && result != null) {
                    Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text(if (context.isNativePresetRun) "Finish with this result" else "Use this result")
                    }
                }
            }

            Text("Cached NASA heatmaps", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
            Text("${rasterRegions.size} raster region${if (rasterRegions.size == 1) "" else "s"} stored inside astronomy.", style = MaterialTheme.typography.bodySmall)
            rasterRegions.take(8).forEach { region ->
                Card(Modifier.fillMaxWidth().padding(top = 5.dp)) {
                    Column(Modifier.padding(9.dp)) {
                        Text(region.name, fontWeight = FontWeight.SemiBold)
                        Text("${region.datasetDate} · ${region.radiusKm.toInt()} km · ${region.unit}", style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = {
                            repo.deleteRasterRegion(region.id)
                            rasterRegions = repo.listRasterRegions()
                            if (activeRaster?.id == region.id) { activeRaster = null; heatmap = null }
                            lastLocation?.let { applyLookup(it, false) }
                        }) { Text("Remove") }
                    }
                }
            }

            if (legacyRegions.isNotEmpty()) {
                Text("Legacy imported point grids: ${legacyRegions.size}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) { Text("Import legacy regional JSON") }

            Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 7.dp))
        }
    }
}
