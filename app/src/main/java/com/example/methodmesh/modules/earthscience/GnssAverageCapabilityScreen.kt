package com.example.methodmesh.modules.earthscience

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import org.json.JSONArray
import org.json.JSONObject

object GnssAverageCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100GnssAveragePositionMethod.ID
    override val title = "Average GNSS position"
    override val description = "Collect repeated high-accuracy Android location fixes and report a mean position plus spread diagnostics."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var hasPermission by rememberSaveable { mutableStateOf(hasLocationPermission(androidContext)) }
        var targetCountText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["target_fix_count"] ?: context.action.settings["input_target_fix_count"] ?: "20")
        }
        var maxAccuracyText by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["max_accuracy_m"] ?: context.action.settings["input_max_accuracy_m"] ?: "10")
        }
        var weighting by rememberSaveable(context.action.canonicalId) {
            mutableStateOf(context.action.settings["weighting"] ?: context.action.settings["input_weighting"] ?: "inverse_variance")
        }
        var collecting by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var permissionRequested by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var fixesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("[]") }
        var rejectedFixes by rememberSaveable(context.action.canonicalId) { mutableStateOf(0) }
        var resultValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Ready to collect fixes.") }
        var lastAcceptedAccuracyText by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            hasPermission = grants.values.any { it } || hasLocationPermission(androidContext)
            permissionRequested = true
            status = if (hasPermission) "Location permission granted." else "Location permission is required to average a position."
            if (hasPermission && context.startsImmediately) collecting = true
        }

        val targetCount = targetCountText.toIntOrNull()?.coerceIn(2, 500) ?: 20
        val maxAccuracy = maxAccuracyText.toDoubleOrNull()?.coerceIn(0.5, 500.0) ?: 10.0
        val acceptedCount = runCatching { JSONArray(fixesJson).length() }.getOrDefault(0)

        fun parseFixes(): List<GnssFix> {
            val array = JSONArray(fixesJson)
            return List(array.length()) { index ->
                val item = array.getJSONObject(index)
                GnssFix(
                    latitude = item.getDouble("latitude"),
                    longitude = item.getDouble("longitude"),
                    accuracyM = item.getDouble("accuracy_m"),
                    altitudeM = if (item.has("altitude_m") && !item.isNull("altitude_m")) item.getDouble("altitude_m") else null,
                    elapsedRealtimeNanos = if (item.has("elapsed_realtime_nanos")) item.optLong("elapsed_realtime_nanos") else null
                )
            }
        }

        val currentAverage = remember(fixesJson, weighting) {
            runCatching {
                val fixes = parseFixes()
                if (fixes.size >= 2) EarthScienceMath.averageGnss(fixes, weighting) else null
            }.getOrNull()
        }
        val currentUncertaintyM = currentAverage?.rmsSpreadM
            ?: lastAcceptedAccuracyText.toDoubleOrNull()
            ?: maxAccuracy

        fun finishCapture(autoSubmit: Boolean) {
            val values = As100GnssAveragePositionMethod.fromFixes(parseFixes(), rejectedFixes, weighting)
            resultValuesJson = JSONObject(values).toString()
            collecting = false
            status = if (values[GnssAverageFields.STATUS] == "succeeded") "Position averaged." else values[GnssAverageFields.ERROR].orEmpty()
            if (autoSubmit) onConfirmed(As100GnssAveragePositionMethod.resultFromValues(values, context.request.invocationContext))
        }

        val result = remember(resultValuesJson) {
            if (resultValuesJson.isBlank()) null else As100GnssAveragePositionMethod.resultFromValues(
                buildMap {
                    val json = JSONObject(resultValuesJson)
                    val keys = json.keys()
                    while (keys.hasNext()) { val key = keys.next(); put(key, json.optString(key, "")) }
                },
                context.request.invocationContext
            )
        }
        val preview = result?.let { execution ->
            OutputFormatter.fields(execution, includeProvenance = false)[GnssAverageFields.RESULT]?.let { mapOf(GnssAverageFields.RESULT to it) }.orEmpty()
        }.orEmpty()

        LaunchedEffect(targetCountText, maxAccuracyText, weighting) {
            context.onSettingsChanged(mapOf("target_fix_count" to targetCountText, "max_accuracy_m" to maxAccuracyText, "weighting" to weighting))
        }

        LaunchedEffect(context.startsImmediately, hasPermission) {
            if (context.startsImmediately) {
                if (hasPermission) {
                    if (resultValuesJson.isBlank()) collecting = true
                } else if (!permissionRequested) {
                    permissionRequested = true
                    permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            }
        }

        LiveLocationCollection(
            enabled = collecting && hasPermission && resultValuesJson.isBlank(),
            onLocation = { latitude, longitude, altitude, accuracy, elapsedRealtimeNanos ->
                if (!collecting || resultValuesJson.isNotBlank()) {
                    // A batched LocationResult may contain fixes after the target count was reached.
                } else if (accuracy <= maxAccuracy) {
                    val array = JSONArray(fixesJson)
                    array.put(JSONObject().apply {
                        put("latitude", latitude)
                        put("longitude", longitude)
                        put("accuracy_m", accuracy)
                        if (altitude != null) put("altitude_m", altitude)
                        put("elapsed_realtime_nanos", elapsedRealtimeNanos)
                    })
                    fixesJson = array.toString()
                    lastAcceptedAccuracyText = accuracy.toString()
                    val n = array.length()
                    status = "Accepted $n/$targetCount fixes · last reported accuracy ${formatEarth(accuracy, 1)} m"
                    if (n >= targetCount) finishCapture(autoSubmit = context.submitsImmediately)
                } else {
                    rejectedFixes += 1
                    status = "Rejected fix: reported accuracy ${formatEarth(accuracy, 1)} m > ${formatEarth(maxAccuracy, 1)} m. Accepted $acceptedCount/$targetCount."
                }
            },
            onError = { status = it; collecting = false }
        )

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = {
                fixesJson = "[]"; rejectedFixes = 0; resultValuesJson = ""; lastAcceptedAccuracyText = ""; status = "Starting a new capture."; collecting = hasPermission
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            if (context.settingShouldBeShown("target_fix_count")) {
                OutlinedTextField(targetCountText, { targetCountText = it.filter(Char::isDigit) }, label = { Text("Accepted fixes") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("max_accuracy_m")) {
                OutlinedTextField(maxAccuracyText, { maxAccuracyText = it.filter { c -> c.isDigit() || c == '.' } }, label = { Text("Maximum reported horizontal uncertainty (m)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            if (context.settingShouldBeShown("weighting")) {
                Text("Averaging", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("inverse_variance", "equal").forEach { option ->
                        val label = if (option == "inverse_variance") "Accuracy-weighted" else "Equal"
                        if (weighting == option) Button({ weighting = option }, Modifier.weight(1f).padding(2.dp)) { Text("✓ $label") }
                        else OutlinedButton({ weighting = option }, Modifier.weight(1f).padding(2.dp)) { Text(label) }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            GnssConvergenceGraphic(
                currentAverage = currentAverage,
                uncertaintyM = currentUncertaintyM,
                maxAccuracyM = maxAccuracy,
                acceptedCount = acceptedCount,
                targetCount = targetCount
            )
            currentAverage?.let { average ->
                Text(
                    "Current average: ${formatEarth(average.latitude, 6)}, ${formatEarth(average.longitude, 6)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Accepted-fix RMS spread: ${formatEarth(average.rmsSpreadM, 1)} m · max spread ${formatEarth(average.maxSpreadM, 1)} m",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text("Accepted: $acceptedCount/$targetCount · Rejected: $rejectedFixes", style = MaterialTheme.typography.bodySmall)
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            if (!hasPermission) {
                Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Grant location permission")
                }
            } else if (result == null) {
                Button(onClick = {
                    if (!collecting) { fixesJson = "[]"; rejectedFixes = 0; lastAcceptedAccuracyText = ""; collecting = true; status = "Collecting fixes…" } else collecting = false
                }, modifier = Modifier.fillMaxWidth()) { Text(if (collecting) "Pause collection" else "Start collection") }
                if (acceptedCount >= 2) {
                    OutlinedButton(onClick = { finishCapture(autoSubmit = context.submitsImmediately) }, modifier = Modifier.fillMaxWidth()) { Text("Finish with $acceptedCount fixes") }
                }
            }
        }
    }
}

@Composable
private fun GnssConvergenceGraphic(
    currentAverage: GnssAverage?,
    uncertaintyM: Double,
    maxAccuracyM: Double,
    acceptedCount: Int,
    targetCount: Int
) {
    val ringColor = MaterialTheme.colorScheme.primary
    val guideColor = MaterialTheme.colorScheme.outlineVariant
    val pointColor = MaterialTheme.colorScheme.tertiary
    val fraction = (uncertaintyM / maxAccuracyM.coerceAtLeast(0.5)).coerceIn(0.08, 1.0).toFloat()
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        val outerRadius = size.minDimension * 0.42f
        drawCircle(color = guideColor, radius = outerRadius, style = Stroke(width = 3f))
        drawCircle(color = guideColor, radius = outerRadius * 0.66f, style = Stroke(width = 2f))
        drawCircle(color = guideColor, radius = outerRadius * 0.33f, style = Stroke(width = 2f))
        drawCircle(color = ringColor, radius = outerRadius * fraction, style = Stroke(width = 8f))
        drawCircle(color = pointColor, radius = 8f)
    }
    Text(
        if (currentAverage == null) {
            "Convergence view · collecting enough fixes to estimate spread"
        } else {
            "Convergence radius: ${formatEarth(uncertaintyM, 1)} m · progress $acceptedCount/$targetCount"
        },
        style = MaterialTheme.typography.bodySmall
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun LiveLocationCollection(
    enabled: Boolean,
    onLocation: (latitude: Double, longitude: Double, altitude: Double?, accuracy: Double, elapsedRealtimeNanos: Long) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }
        val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(500L)
            .setMaxUpdateDelayMillis(1000L)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.forEach { location ->
                    val accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.POSITIVE_INFINITY
                    val altitude = if (location.hasAltitude()) location.altitude else null
                    onLocation(location.latitude, location.longitude, altitude, accuracy, location.elapsedRealtimeNanos)
                }
            }
        }
        runCatching { client.requestLocationUpdates(request, callback, Looper.getMainLooper()) }
            .onFailure { onError(it.message ?: "Unable to start Android location updates.") }
        onDispose { client.removeLocationUpdates(callback) }
    }
}

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
