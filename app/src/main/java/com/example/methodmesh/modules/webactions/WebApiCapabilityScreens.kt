package com.example.methodmesh.modules.webactions

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.onlinedata.LocationDisclosureMode
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object WebApiCapabilityScreens {
    val all: List<CapabilityScreenSpec> = WebApiMethods.all.map(::WebApiCapabilityScreen)
}

private class WebApiCapabilityScreen(
    private val method: WebApiMethod
) : CapabilityScreenSpec {
    override val capabilityId: String = method.id
    override val title: String = method.definition.name
    override val description: String = "Fetch the complete ${method.definition.name} data stream and return it to ODK or export readable results on the phone."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = androidx.compose.ui.platform.LocalContext.current
        val scope = rememberCoroutineScope()
        val inputValues = remember { mutableStateMapOf<String, String>() }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Ready.") }
        var running by rememberSaveable { mutableStateOf(false) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        val hasLocationInputs = method.definition.inputs.any { it.id == "latitude" } && method.definition.inputs.any { it.id == "longitude" }
        var locationMode by rememberSaveable {
            mutableStateOf(context.action.settings["location_mode"] ?: context.action.settings["input_location_mode"] ?: "manual")
        }
        var locationStatus by rememberSaveable { mutableStateOf("") }

        var acquireGps: () -> Unit = {}
        val locationPermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
                acquireGps()
            } else {
                locationMode = "manual"
                locationStatus = "Location permission was not granted. You can enter coordinates manually."
            }
        }
        acquireGps = {
            if (hasLocationInputs) {
                locationStatus = "Getting a current GPS fix…"
                val token = CancellationTokenSource()
                LocationServices.getFusedLocationProviderClient(androidContext)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                    .addOnSuccessListener { location ->
                        if (location == null) {
                            locationMode = "manual"
                            locationStatus = "No current GPS fix was available. Enter coordinates manually."
                        } else {
                            inputValues["latitude"] = location.latitude.toString()
                            inputValues["longitude"] = location.longitude.toString()
                            locationMode = "gps"
                            locationStatus = "GPS fix ready: %.5f, %.5f".format(location.latitude, location.longitude)
                        }
                    }
                    .addOnFailureListener { error ->
                        locationMode = "manual"
                        locationStatus = error.message ?: "GPS lookup failed."
                    }
            }
        }

        LaunchedEffect(method.id) {
            method.definition.inputs.forEach { input ->
                inputValues.putIfAbsent(input.id, (context.action.settings[input.id] ?: context.action.settings["input_${input.id}"]).orEmpty().ifBlank { input.defaultValue })
            }
            context.onSettingsChanged(inputValues.toMap() + mapOf("result_paths" to method.definition.response.expectedPaths.joinToString("|")))
        }

        LaunchedEffect(locationMode, inputValues.toMap()) {
            context.onSettingsChanged(
                inputValues.toMap() +
                    mapOf(
                        "location_mode" to locationMode,
                        "result_paths" to method.definition.response.expectedPaths.joinToString("|")
                    )
            )
        }

        fun runApi() {
            running = true
            status = "Fetching complete data stream…"
            if (hasLocationInputs && (inputValues["latitude"].orEmpty().toDoubleOrNull() == null || inputValues["longitude"].orEmpty().toDoubleOrNull() == null)) {
                status = "Choose GPS or enter a valid latitude and longitude."
                running = false
                return
            }
            scope.launch {
                val execution = withContext(Dispatchers.IO) {
                    method.execute(
                        method.request(
                            context = context.request.invocationContext.asMap(method.id) + context.action.settings + inputValues.toMap()
                        )
                    )
                }
                result = execution
                val fields = OutputFormatter.fields(execution, includeProvenance = false)
                status = fields[WebApiFields.ERROR]?.toString()?.takeIf { it.isNotBlank() } ?: "Data ready."
                running = false
                if (context.submitsImmediately) onConfirmed(execution)
            }
        }

        LaunchedEffect(context.presentationMode, context.action.settings) {
            if ((context.startsImmediately || context.presentationMode == CapabilityPresentationMode.IntentLaunch) && !launched) {
                launched = true
                runApi()
            }
        }

        val preview = result?.let { execution ->
            val fields = OutputFormatter.fields(execution, includeProvenance = false)
            linkedMapOf<String, Any?>(
                "Data" to fields[WebApiFields.VALUE],
                "Provider" to fields[WebApiFields.PROVIDER],
                "Updated" to fields[WebApiFields.RETRIEVED_TIME_ISO],
                "Status" to fields[WebApiFields.STATUS]
            ).filterValues { it?.toString().orEmpty().isNotBlank() }
        }.orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = preview,
            onBack = onBack,
            onRetry = { runApi() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text("Fetch the complete response. ODK can request the full JSON sidecar; native use can copy, share, or save the readable result.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            if (method.definition.privacy.sendsLocation) {
                val precision = when (method.definition.privacy.locationMode) {
                    LocationDisclosureMode.ROUNDED -> "rounded to about ${method.definition.privacy.roundedLocationRadiusMeters / 1_000} km"
                    LocationDisclosureMode.EXACT -> "exact"
                    else -> "manually supplied"
                }
                Text("This request sends $precision location to ${method.definition.attribution.providerName}.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
            }
            if (hasLocationInputs) {
                Text("Location source", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            locationMode = "gps"
                            val fine = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            val coarse = ContextCompat.checkSelfPermission(androidContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            if (fine || coarse) acquireGps() else locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (locationMode == "gps") "✓ Use GPS" else "Use GPS") }
                    OutlinedButton(onClick = { locationMode = "manual" }, modifier = Modifier.weight(1f)) {
                        Text(if (locationMode == "manual") "✓ Enter manually" else "Enter manually")
                    }
                }
                if (locationMode == "manual") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("latitude", "longitude").forEach { id ->
                            val input = method.definition.inputs.first { it.id == id }
                            OutlinedTextField(
                                value = inputValues[id].orEmpty(),
                                onValueChange = { inputValues[id] = it },
                                label = { Text(input.name) },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }
                if (locationStatus.isNotBlank()) Text(locationStatus, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(6.dp))
            }
            method.definition.inputs.filterNot { hasLocationInputs && it.id in setOf("latitude", "longitude") }.forEach { input ->
                OutlinedTextField(
                    value = inputValues[input.id].orEmpty(),
                    onValueChange = { inputValues[input.id] = it },
                    label = { Text(input.name) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(6.dp))
            }
            Button(onClick = { runApi() }, modifier = Modifier.fillMaxWidth(), enabled = !running) {
                Text(if (running) "Fetching…" else "Fetch data")
            }
            Text(status, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
        }
    }
}
