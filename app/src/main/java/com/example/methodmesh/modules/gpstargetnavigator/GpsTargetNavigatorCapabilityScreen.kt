package com.example.methodmesh.modules.gpstargetnavigator

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object GpsTargetNavigatorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100LocateTargetMethod.ID
    override val title: String = "GPS target navigation"
    override val description: String = "Navigate to a configured target and confirm the navigation result."

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
        val interaction = remember { GpsTargetNavigatorInteraction() }
        val settings = remember(action.settings) {
            SettingsState(interaction.settings) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { applyParameters(it, interaction.settings, action.settings) }
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var targetLatitudeText by remember(settings) {
            mutableStateOf(settings.getFloat("target_latitude").toString())
        }
        var targetLongitudeText by remember(settings) {
            mutableStateOf(settings.getFloat("target_longitude").toString())
        }
        var targetStatus by remember { mutableStateOf("") }

        fun captureResult(): ExecutionResult {
            val execution = As100LocateTargetMethod.execute(
                request = As100LocateTargetMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) + action.settings
                ),
                settingsState = settings,
                transport = request.source
            ).withInvocationContext(request.invocationContext)
            if (context.startsImmediately) {
                onConfirmed(execution)
            } else {
                result = execution
            }
            return execution
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                if (context.startsImmediately) {
                    "Navigation starts as soon as location permission is available. Save the result after reaching the target."
                } else {
                    "Navigate to the configured target, then review and confirm the saved navigation result."
                }
            )
            Spacer(Modifier.height(10.dp))

            if (!context.startsImmediately) {
                OutlinedTextField(
                    value = targetLatitudeText,
                    onValueChange = { value ->
                        targetLatitudeText = value
                        value.toFloatOrNull()?.takeIf { it in -90f..90f }
                            ?.let { settings.setFloat("target_latitude", it) }
                    },
                    label = { Text("Target latitude") },
                    supportingText = { Text("−90 to 90") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = targetLongitudeText,
                    onValueChange = { value ->
                        targetLongitudeText = value
                        value.toFloatOrNull()?.takeIf { it in -180f..180f }
                            ?.let { settings.setFloat("target_longitude", it) }
                    },
                    label = { Text("Target longitude") },
                    supportingText = { Text("−180 to 180") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        targetStatus = "Getting current position…"
                        useCurrentPosition(
                            context = androidContext,
                            onLocation = { latitude, longitude ->
                                targetLatitudeText = latitude.toString()
                                targetLongitudeText = longitude.toString()
                                settings.setFloat("target_latitude", latitude.toFloat())
                                settings.setFloat("target_longitude", longitude.toFloat())
                                settings.setString("target_name", "Current position")
                                targetStatus = "Current position set as target."
                            },
                            onError = { targetStatus = it }
                        )
                    }
                ) { Text("Quick target: use current position") }
                if (targetStatus.isNotBlank()) Text(targetStatus)
                Spacer(Modifier.height(10.dp))
            }

            interaction.Render(
                settingsState = settings,
                startsImmediately = context.startsImmediately,
                onNavigationSaved = { captureResult() }
            )

            if (!context.startsImmediately) {
                Spacer(Modifier.height(10.dp))
                if (result != null) {
                    Text("Saved navigation result is ready. Use result to confirm it.")
                }
            }

            if (!context.startsImmediately) {
                Spacer(Modifier.height(16.dp))
                IntentExampleDropdown(
                    capabilityId = As100LocateTargetMethod.ID,
                    examples = listOf(
                        IntentExample(
                            label = "Basic GPS navigation",
                            description = "Navigate to default target",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}')"
                        ),
                        IntentExample(
                            label = "With target coordinates",
                            description = "Specify target latitude and longitude",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}',input_target_latitude='-1.28',input_target_longitude='36.81')"
                        ),
                        IntentExample(
                            label = "With arrival radius",
                            description = "Specify distance threshold for arrival detection",
                            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100LocateTargetMethod.ID}',input_target_latitude='-1.28',input_target_longitude='36.81',input_arrival_radius='50')"
                        )
                    )
                )
            }
        }
    }

    private fun applyParameters(settingsState: SettingsState, settings: List<MethodSetting>, parameters: Map<String, String>) {
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
            onError("Grant location permission below, then try again.")
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
