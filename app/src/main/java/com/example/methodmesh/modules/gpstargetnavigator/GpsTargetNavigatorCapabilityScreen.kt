package com.example.methodmesh.modules.gpstargetnavigator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.modules.pluscodecapture.OpenLocationCode
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import kotlinx.coroutines.delay

object GpsTargetNavigatorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100LocateTargetMethod.ID
    override val title: String = "Target navigator"
    override val description: String = "Live GPS guidance to a coordinate or full Plus Code."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val action = context.action
        val request = context.request
        val androidContext = LocalContext.current
        val nativePresetRun = context.isNativePresetRun
        val interaction = remember { GpsTargetNavigatorInteraction() }
        val settings = remember(action.canonicalId) {
            SettingsState(interaction.settings) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { applyParameters(it, interaction.settings, action.settings) }
        }

        // A destination is normally execution-time data, not preset configuration.
        // Seed the generic preset editor with the target fields marked as runtime
        // inputs. The user can still explicitly tick any of them to create a fixed
        // destination preset. Existing presets and external/ODK calls are untouched.
        LaunchedEffect(action.canonicalId, nativePresetRun, context.submitsImmediately) {
            val runtimeFieldsAlreadyDeclared = action.settings["methodmesh_runtime_fields"].orEmpty().isNotBlank() ||
                action.settings["input_methodmesh_runtime_fields"].orEmpty().isNotBlank()
            if (!nativePresetRun && !context.submitsImmediately && !runtimeFieldsAlreadyDeclared) {
                context.onSettingsChanged(
                    mapOf(
                        "methodmesh_runtime_fields" to
                            "target_plus_code,target_name,target_latitude,target_longitude"
                    )
                )
            }
        }

        var resultFieldsJson by rememberSaveable(action.canonicalId) { mutableStateOf<String?>(null) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var startRequested by rememberSaveable(action.canonicalId) { mutableStateOf(false) }
        var navigationRevision by rememberSaveable(action.canonicalId) { mutableStateOf(0) }

        val coordinateKeysPresent = listOf("target_latitude", "input_target_latitude", "latitude", "lat").any(action.settings::containsKey) &&
            listOf("target_longitude", "input_target_longitude", "longitude", "lon", "lng").any(action.settings::containsKey)
        val hasConfiguredTarget = settings.getString("target_plus_code").isNotBlank() || coordinateKeysPresent ||
            settings.getFloat("target_latitude") != 0f || settings.getFloat("target_longitude") != 0f
        val destinationHasRuntimeSettings = listOf(
            "target_plus_code",
            "target_name",
            "target_latitude",
            "target_longitude",
            "arrival_radius_m"
        ).any(context::settingIsRuntimeInput)
        var destinationReady by rememberSaveable(action.canonicalId) {
            mutableStateOf(hasConfiguredTarget && (!nativePresetRun || !destinationHasRuntimeSettings))
        }
        // v1.05 unified navigator: destination editing expands inside the same working dashboard.
        var locationEditorVisible by rememberSaveable(action.canonicalId) {
            mutableStateOf(!destinationReady)
        }

        val plusCodeRuntime = context.settingIsRuntimeInput("target_plus_code")
        val coordinateRuntime = context.settingIsRuntimeInput("target_latitude") || context.settingIsRuntimeInput("target_longitude")
        var targetMode by rememberSaveable(action.canonicalId) {
            mutableStateOf(
                if (settings.getString("target_plus_code").isNotBlank() || (plusCodeRuntime && !coordinateRuntime)) "plus_code"
                else "coordinates"
            )
        }
        var plusCodeText by rememberSaveable(action.canonicalId) {
            mutableStateOf(settings.getString("target_plus_code"))
        }
        var targetLatitudeText by rememberSaveable(action.canonicalId) {
            mutableStateOf(settings.getFloat("target_latitude").takeIf { it != 0f }?.toString().orEmpty())
        }
        var targetLongitudeText by rememberSaveable(action.canonicalId) {
            mutableStateOf(settings.getFloat("target_longitude").takeIf { it != 0f }?.toString().orEmpty())
        }
        var targetNameText by rememberSaveable(action.canonicalId) {
            mutableStateOf(settings.getString("target_name").ifBlank { "Target location" })
        }
        var arrivalRadiusText by rememberSaveable(action.canonicalId) {
            mutableStateOf(settings.getFloat("arrival_radius_m").toString())
        }
        var targetStatus by rememberSaveable(action.canonicalId) { mutableStateOf("") }

        val restoredResult = remember(resultFieldsJson) {
            resultFieldsJson
                ?.let(::gpsNavigatorFieldsFromJson)
                ?.let { As100LocateTargetMethod.navigationOutcomeResult(it) }
                ?.withInvocationContext(request.invocationContext)
        }
        val capturedResult = result ?: restoredResult

        fun storeResult(execution: ExecutionResult): ExecutionResult {
            val withContext = execution.withInvocationContext(request.invocationContext)
            result = withContext
            resultFieldsJson = gpsNavigatorFieldsToJson(OutputFormatter.fields(withContext, includeProvenance = false))
            return withContext
        }

        fun submitDestination(coordinateOverride: Pair<Double, Double>? = null): Boolean {
            val targetName = targetNameText.trim().ifBlank { "Target location" }
            val radius = arrivalRadiusText.trim().toFloatOrNull()
            if (radius == null || radius !in 1f..500f) {
                targetStatus = "Arrival radius must be between 1 and 500 metres."
                return false
            }
            settings.setFloat("arrival_radius_m", radius)
            settings.setString("target_name", targetName)

            if (targetMode == "plus_code") {
                val plusCode = plusCodeText.trim().uppercase()
                if (plusCode.isBlank()) {
                    targetStatus = "Enter a full Plus Code."
                    return false
                }
                val area = runCatching { OpenLocationCode.decode(plusCode) }.getOrElse { error ->
                    targetStatus = error.message ?: "Plus Code is not valid."
                    return false
                }
                settings.setString("target_plus_code", plusCode)
                settings.setFloat("target_latitude", area.centerLatitude.toFloat())
                settings.setFloat("target_longitude", area.centerLongitude.toFloat())
                targetLatitudeText = "%.7f".format(area.centerLatitude)
                targetLongitudeText = "%.7f".format(area.centerLongitude)
                targetStatus = "Destination resolved locally from Plus Code."
            } else {
                val latitude = coordinateOverride?.first?.toFloat() ?: targetLatitudeText.trim().toFloatOrNull()
                val longitude = coordinateOverride?.second?.toFloat() ?: targetLongitudeText.trim().toFloatOrNull()
                if (latitude == null || latitude !in -90f..90f) {
                    targetStatus = "Latitude must be between −90 and 90."
                    return false
                }
                if (longitude == null || longitude !in -180f..180f) {
                    targetStatus = "Longitude must be between −180 and 180."
                    return false
                }
                targetLatitudeText = "%.7f".format(latitude)
                targetLongitudeText = "%.7f".format(longitude)
                settings.setString("target_plus_code", "")
                settings.setFloat("target_latitude", latitude)
                settings.setFloat("target_longitude", longitude)
                targetStatus = "Destination set."
            }

            destinationReady = true
            locationEditorVisible = false
            navigationRevision += 1
            startRequested = true
            return true
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = capturedResult,
            resultPreview = emptyMap(),
            onBack = onBack,
            onRetry = {
                result = null
                resultFieldsJson = null
                startRequested = false
                destinationReady = hasConfiguredTarget && nativePresetRun && !destinationHasRuntimeSettings
                locationEditorVisible = !destinationReady
                targetStatus = ""
            },
            onConfirm = { capturedResult?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            val destinationEditorContent: @Composable () -> Unit = {
                DestinationDashboardEditor(
                    context = context,
                    onTargetModeChanged = { targetMode = it },
                    plusCodeText = plusCodeText,
                    onPlusCodeChanged = { plusCodeText = it.uppercase() },
                    targetNameText = targetNameText,
                    onTargetNameChanged = { targetNameText = it },
                    targetLatitudeText = targetLatitudeText,
                    onTargetLatitudeChanged = { targetLatitudeText = it },
                    targetLongitudeText = targetLongitudeText,
                    onTargetLongitudeChanged = { targetLongitudeText = it },
                    arrivalRadiusText = arrivalRadiusText,
                    onArrivalRadiusChanged = { arrivalRadiusText = it },
                    targetStatus = targetStatus,
                    onMapLocationSelected = { latitude, longitude ->
                        targetMode = "coordinates"
                        targetLatitudeText = "%.7f".format(latitude)
                        targetLongitudeText = "%.7f".format(longitude)
                        plusCodeText = ""
                        GpsTargetNavigatorStateStore(androidContext).clear()
                        result = null
                        resultFieldsJson = null
                        startRequested = false
                        submitDestination(latitude to longitude)
                    },
                    onUseCurrentPosition = {
                        targetStatus = "Getting current position…"
                        useCurrentPosition(
                            context = androidContext,
                            onLocation = { latitude, longitude ->
                                targetMode = "coordinates"
                                targetLatitudeText = latitude.toString()
                                targetLongitudeText = longitude.toString()
                                plusCodeText = ""
                                settings.setString("target_plus_code", "")
                                settings.setFloat("target_latitude", latitude.toFloat())
                                settings.setFloat("target_longitude", longitude.toFloat())
                                settings.setString("target_name", "Current position")
                                targetNameText = "Current position"
                                targetStatus = "Current position loaded. Press Use this location to apply it."
                            },
                            onError = { targetStatus = it }
                        )
                    },
                    onStart = {
                        GpsTargetNavigatorStateStore(androidContext).clear()
                        result = null
                        resultFieldsJson = null
                        startRequested = false
                        submitDestination()
                    }
                )
            }

            if (!destinationReady) {
                // Even initial destination setup is part of the navigator dashboard.
                NavigatorAwaitingTargetDashboard(destinationEditorContent)
            } else {
                val navigationStartsImmediately = context.submitsImmediately ||
                    (nativePresetRun && destinationReady) || startRequested
                key(navigationRevision) {
                    interaction.Render(
                        settingsState = settings,
                        startsImmediately = navigationStartsImmediately,
                        onChangeLocation = if (context.submitsImmediately ||
                            (nativePresetRun && listOf("target_plus_code", "target_latitude", "target_longitude").all(context::settingIsFixedInNativePreset))) {
                            null
                        } else {
                            {
                                locationEditorVisible = !locationEditorVisible
                                targetStatus = if (locationEditorVisible) {
                                    "Adjust the target here, then use the new location."
                                } else {
                                    ""
                                }
                            }
                        },
                        targetEditor = if (locationEditorVisible && !context.submitsImmediately) destinationEditorContent else null,
                        onNavigationCommitted = { execution ->
                            val withContext = execution.withInvocationContext(request.invocationContext)
                            if (context.submitsImmediately) {
                                onConfirmed(withContext)
                            } else {
                                storeResult(withContext)
                            }
                        }
                    )
                }
            }

            if (capturedResult != null) {
                Spacer(Modifier.height(10.dp))
                CommittedNavigationResultCard(
                    fields = OutputFormatter.fields(capturedResult, includeProvenance = false)
                )
            }
        }
    }

    @Composable
    private fun NavigatorAwaitingTargetDashboard(destinationEditor: @Composable () -> Unit) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            NavigationCompassDashboard(
                bearingDegrees = 0f,
                headingDegrees = null,
                relativeBearingDegrees = 0f,
                hasLocationFix = false,
                arrived = false
            )
            NavigationHeroCard(
                distanceMeters = 0f,
                bearingDegrees = 0f,
                headingDegrees = null,
                relativeBearingDegrees = 0f,
                accuracyMeters = 0f,
                currentLatitude = 0f,
                currentLongitude = 0f,
                hasLocationFix = false,
                hasHeading = false,
                arrived = false,
                lifecycleLabel = "Set target"
            )
            destinationEditor()
        }
    }

    @Composable
    private fun DestinationDashboardEditor(
        context: CapabilityScreenContext,
        onTargetModeChanged: (String) -> Unit,
        plusCodeText: String,
        onPlusCodeChanged: (String) -> Unit,
        targetNameText: String,
        onTargetNameChanged: (String) -> Unit,
        targetLatitudeText: String,
        onTargetLatitudeChanged: (String) -> Unit,
        targetLongitudeText: String,
        onTargetLongitudeChanged: (String) -> Unit,
        arrivalRadiusText: String,
        onArrivalRadiusChanged: (String) -> Unit,
        targetStatus: String,
        onMapLocationSelected: (Double, Double) -> Unit,
        onUseCurrentPosition: () -> Unit,
        onStart: () -> Unit
    ) {
        var locationOptionsVisible by rememberSaveable { mutableStateOf(false) }
        var editorMode by rememberSaveable { mutableStateOf<String?>(null) }
        var mapPickerOpen by rememberSaveable { mutableStateOf(false) }
        val androidContext = LocalContext.current

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("TARGET LOCATION", style = MaterialTheme.typography.labelMedium)
                Text(
                    "Choose destination",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )

                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        locationOptionsVisible = !locationOptionsVisible
                        if (!locationOptionsVisible) editorMode = null
                    }
                ) {
                    Text(if (locationOptionsVisible) "Close location editor" else "Set location")
                }

                if (locationOptionsVisible) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onTargetModeChanged("coordinates")
                                editorMode = "coordinates"
                            }
                        ) { Text("Lat / lon") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onTargetModeChanged("plus_code")
                                editorMode = "plus_code"
                            }
                        ) { Text("Plus Code") }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            onTargetModeChanged("coordinates")
                            mapPickerOpen = true
                        }
                    ) {
                        Text("Pick from map")
                    }
                }

                if (!context.settingIsFixedInNativePreset("target_name") && editorMode != null) {
                    OutlinedTextField(
                        value = targetNameText,
                        onValueChange = onTargetNameChanged,
                        label = { Text("Destination name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                when (editorMode) {
                    "plus_code" -> if (!context.settingIsFixedInNativePreset("target_plus_code")) {
                        OutlinedTextField(
                            value = plusCodeText,
                            onValueChange = onPlusCodeChanged,
                            label = { Text("Full Plus Code") },
                            supportingText = { Text("Resolved locally on the device.") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    "coordinates" -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!context.settingIsFixedInNativePreset("target_latitude")) {
                                OutlinedTextField(
                                    value = targetLatitudeText,
                                    onValueChange = onTargetLatitudeChanged,
                                    label = { Text("Latitude") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                            if (!context.settingIsFixedInNativePreset("target_longitude")) {
                                OutlinedTextField(
                                    value = targetLongitudeText,
                                    onValueChange = onTargetLongitudeChanged,
                                    label = { Text("Longitude") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true
                                )
                            }
                        }
                        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onUseCurrentPosition) {
                            Text("Use my current position")
                        }
                    }
                }

                if (editorMode != null && !context.settingIsFixedInNativePreset("arrival_radius_m")) {
                    OutlinedTextField(
                        value = arrivalRadiusText,
                        onValueChange = onArrivalRadiusChanged,
                        label = { Text("Arrival radius (m)") },
                        supportingText = { Text("Arrival is flagged inside this radius.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }

                if (targetStatus.isNotBlank()) {
                    Text(targetStatus, style = MaterialTheme.typography.bodyMedium)
                }

                if (editorMode != null) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = onStart) {
                        Text("Use this location")
                    }
                }
            }
        }

        if (mapPickerOpen) {
            MapTargetPickerDialog(
                initialLatitude = targetLatitudeText.toDoubleOrNull() ?: 0.0,
                initialLongitude = targetLongitudeText.toDoubleOrNull() ?: 0.0,
                onDismiss = { mapPickerOpen = false },
                onUseCurrentPosition = { onCoordinate, onError ->
                    useCurrentPosition(androidContext, onCoordinate, onError)
                },
                onLocationSelected = { latitude, longitude ->
                    onTargetModeChanged("coordinates")
                    onTargetLatitudeChanged("%.7f".format(latitude))
                    onTargetLongitudeChanged("%.7f".format(longitude))
                    editorMode = "coordinates"
                    locationOptionsVisible = true
                    mapPickerOpen = false
                    onMapLocationSelected(latitude, longitude)
                }
            )
        }
    }

    @Composable
    private fun MapTargetPickerDialog(
        initialLatitude: Double,
        initialLongitude: Double,
        onDismiss: () -> Unit,
        onUseCurrentPosition: (((Double, Double) -> Unit), (String) -> Unit) -> Unit,
        onLocationSelected: (Double, Double) -> Unit
    ) {
        val androidContext = LocalContext.current
        var centreLatitude by rememberSaveable { mutableStateOf(initialLatitude.coerceIn(-85.0, 85.0)) }
        var centreLongitude by rememberSaveable { mutableStateOf(normalizeLongitude(initialLongitude)) }
        var status by rememberSaveable { mutableStateOf("Pan and zoom until the crosshair is on the target.") }
        var webView by remember { mutableStateOf<WebView?>(null) }
        val centrePlusCode = remember(centreLatitude, centreLongitude) {
            OpenLocationCode.encode(centreLatitude, centreLongitude, 10)
        }

        fun readCentre(andThen: ((Double, Double) -> Unit)? = null) {
            val view = webView ?: return
            view.evaluateJavascript("window.methodMeshGetCentre ? window.methodMeshGetCentre() : ''") { raw ->
                val value = raw.trim().trim('"').replace("\\\"", "\"")
                val parts = value.split(',')
                val latitude = parts.getOrNull(0)?.toDoubleOrNull()
                val longitude = parts.getOrNull(1)?.toDoubleOrNull()
                if (latitude != null && longitude != null) {
                    centreLatitude = latitude.coerceIn(-85.0, 85.0)
                    centreLongitude = normalizeLongitude(longitude)
                    andThen?.invoke(centreLatitude, centreLongitude)
                }
            }
        }

        LaunchedEffect(webView) {
            while (webView != null) {
                delay(350)
                readCentre()
            }
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("PICK TARGET", style = MaterialTheme.typography.labelMedium)
                            Text("Map picker", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        }
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { mapContext ->
                                WebView(mapContext).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    webViewClient = WebViewClient()
                                    loadDataWithBaseURL(
                                        "https://tiles.openfreemap.org/",
                                        mapPickerHtml(centreLatitude, centreLongitude),
                                        "text/html",
                                        "utf-8",
                                        null
                                    )
                                    webView = this
                                }
                            }
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 12.dp)
                                .clickable {
                                    copyNavigationValue(
                                        androidContext,
                                        "${"%.6f".format(centreLatitude)},${"%.6f".format(centreLongitude)}"
                                    )
                                },
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "${"%.6f".format(centreLatitude)}, ${"%.6f".format(centreLongitude)}",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    centrePlusCode,
                                    modifier = Modifier.clickable {
                                        copyNavigationValue(androidContext, centrePlusCode)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { webView?.evaluateJavascript("window.methodMeshZoomOut && window.methodMeshZoomOut()", null) }
                        ) { Text("− Zoom") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { webView?.evaluateJavascript("window.methodMeshZoomIn && window.methodMeshZoomIn()", null) }
                        ) { Text("+ Zoom") }
                    }
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            status = "Getting current position…"
                            onUseCurrentPosition(
                                { lat, lon ->
                                    centreLatitude = lat.coerceIn(-85.0, 85.0)
                                    centreLongitude = normalizeLongitude(lon)
                                    webView?.evaluateJavascript(
                                        "window.methodMeshFlyTo && window.methodMeshFlyTo(${centreLatitude},${centreLongitude},16)",
                                        null
                                    )
                                    status = "Map centred on your current position."
                                },
                                { status = it }
                            )
                        }
                    ) { Text("Centre on my position") }

                    Text(status, style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Online basemap: OpenFreeMap / MapLibre. Target coordinates remain local until map tiles are requested.",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            readCentre { latitude, longitude ->
                                onLocationSelected(latitude, longitude)
                            }
                        }
                    ) { Text("Use this location") }
                }
            }
        }
    }

    private fun mapPickerHtml(latitude: Double, longitude: Double): String = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
          <link href="https://unpkg.com/maplibre-gl@5.7.1/dist/maplibre-gl.css" rel="stylesheet" />
          <script src="https://unpkg.com/maplibre-gl@5.7.1/dist/maplibre-gl.js"></script>
          <style>
            html, body, #map { width:100%; height:100%; margin:0; padding:0; overflow:hidden; }
            .crosshair { position:absolute; left:50%; top:50%; width:34px; height:34px; margin-left:-17px; margin-top:-17px; z-index:10; pointer-events:none; }
            .crosshair:before, .crosshair:after { content:''; position:absolute; background:#111; box-shadow:0 0 2px #fff; }
            .crosshair:before { width:34px; height:2px; top:16px; left:0; }
            .crosshair:after { width:2px; height:34px; left:16px; top:0; }
            .dot { position:absolute; width:8px; height:8px; left:13px; top:13px; border:2px solid #111; border-radius:50%; background:#fff; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <div class="crosshair"><div class="dot"></div></div>
          <script>
            const map = new maplibregl.Map({
              container: 'map',
              style: 'https://tiles.openfreemap.org/styles/liberty',
              center: [${longitude}, ${latitude}],
              zoom: 14,
              attributionControl: true
            });
            map.addControl(new maplibregl.NavigationControl({showCompass:false}), 'bottom-right');
            window.methodMeshGetCentre = function() {
              const c = map.getCenter();
              return c.lat.toFixed(7) + ',' + c.lng.toFixed(7);
            };
            window.methodMeshZoomIn = function() { map.zoomIn(); };
            window.methodMeshZoomOut = function() { map.zoomOut(); };
            window.methodMeshFlyTo = function(lat, lon, zoom) { map.flyTo({center:[lon,lat], zoom:zoom}); };
          </script>
        </body>
        </html>
    """.trimIndent()

    private fun normalizeLongitude(value: Double): Double {
        var result = value
        while (result > 180.0) result -= 360.0
        while (result < -180.0) result += 360.0
        return result
    }

    @Composable
    private fun CommittedNavigationResultCard(fields: Map<String, Any?>) {
        val finalDistance = fields["final_distance_m"]?.toString()?.toFloatOrNull()
            ?: fields["distance_m"]?.toString()?.toFloatOrNull()
        val arrived = fields["navigation_completed"]?.toString()
            ?: fields["arrived"]?.toString().orEmpty()
        val latitude = fields["arrival_latitude"]?.toString()
            ?: fields["current_latitude"]?.toString().orEmpty()
        val longitude = fields["arrival_longitude"]?.toString()
            ?: fields["current_longitude"]?.toString().orEmpty()

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("COMMITTED", style = MaterialTheme.typography.labelMedium)
                Text("Navigation result frozen", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                if (finalDistance != null) {
                    NavigationMetricCard(
                        label = "Final distance",
                        value = formatNavigationDistance(finalDistance),
                        clipboardValue = formatRawFloat(finalDistance),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    NavigationMetricCard(
                        label = "Arrived",
                        value = arrived.ifBlank { "false" },
                        clipboardValue = arrived.ifBlank { "false" },
                        modifier = Modifier.weight(1f)
                    )
                    NavigationMetricCard(
                        label = "Samples",
                        value = fields["sample_count"]?.toString().orEmpty().ifBlank { "0" },
                        clipboardValue = fields["sample_count"]?.toString().orEmpty().ifBlank { "0" },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (latitude.isNotBlank() && longitude.isNotBlank()) {
                    CopyableNavigationValue(
                        label = "Final fix",
                        displayValue = "$latitude, $longitude",
                        clipboardValue = "$latitude,$longitude",
                        monospace = true
                    )
                }
                Text(
                    "Share/save/Done actions are supplied by the MethodMesh result shell; technical JSON remains optional.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    private fun gpsNavigatorFieldsToJson(values: Map<String, Any?>): String =
        JSONObject().apply {
            values.toSortedMap().forEach { (key, value) -> put(key, value?.toString().orEmpty()) }
        }.toString()

    private fun gpsNavigatorFieldsFromJson(json: String): Map<String, Any?> = runCatching {
        val root = JSONObject(json.ifBlank { "{}" })
        buildMap<String, Any?> { root.keys().forEach { key -> put(key, root.optString(key)) } }
    }.getOrDefault(emptyMap())

    private fun applyParameters(
        settingsState: SettingsState,
        settings: List<MethodSetting>,
        parameters: Map<String, String>
    ) {
        val targetPlusCode = parameters["target_plus_code"]
            ?: parameters["input_target_plus_code"]
            ?: parameters["plus_code"]
            ?: parameters["input_plus_code"]
        if (!targetPlusCode.isNullOrBlank()) {
            settingsState.setString("target_plus_code", targetPlusCode.trim().uppercase())
            runCatching { OpenLocationCode.decode(targetPlusCode) }.onSuccess { area ->
                settingsState.setFloat("target_latitude", area.centerLatitude.toFloat())
                settingsState.setFloat("target_longitude", area.centerLongitude.toFloat())
                if (settingsState.getString("target_name").isBlank()) {
                    settingsState.setString("target_name", targetPlusCode.trim().uppercase())
                }
            }
        }
        settings.forEach { setting ->
            val raw = parameters[setting.id]
                ?: parameters["input_${setting.id}"]
                ?: (if (setting.id == "arrival_radius_m") parameters["arrival_radius"] else null)
                ?: (if (setting.id == "arrival_radius_m") parameters["input_arrival_radius"] else null)
                ?: (if (setting.id == "target_latitude") parameters["latitude"] ?: parameters["lat"] else null)
                ?: (if (setting.id == "target_longitude") parameters["longitude"] ?: parameters["lon"] ?: parameters["lng"] else null)
                ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: raw == "1")
                is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
                is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
                is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.MultiChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun useCurrentPosition(
        context: Context,
        onLocation: (Double, Double) -> Unit,
        onError: (String) -> Unit
    ) {
        val fineGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            onError("Grant location permission in the navigator, then try again.")
            return
        }
        val cancellation = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location == null) onError("A current GPS position is not available yet.")
                else onLocation(location.latitude, location.longitude)
            }
            .addOnFailureListener { error ->
                onError("Could not get current position: ${error.message ?: "location error"}")
            }
    }
}
