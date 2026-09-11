package com.example.methodmesh.modules.emergency

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.BiometricAuthHelper
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.json.JSONObject
import java.time.Instant

object EmergencyStatusCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EmergencyStatusMethod.ID
    override val title = "Emergency"
    override val description = "Offline-first emergency control centre."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        EmergencyScreen(context, EmergencyPane.DASHBOARD, onBack, onConfirmed, onCancel)
}

object EmergencyLocationCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EmergencyLocationMethod.ID
    override val title = "Emergency location"
    override val description = "Capture GPS and a Plus Code for emergency sharing."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        EmergencyScreen(context, EmergencyPane.LOCATION, onBack, onConfirmed, onCancel)
}

object EmergencyExitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EmergencyExitFindMethod.ID
    override val title = "Exit strategy"
    override val description = "Rank sparse offline strategic exit locations."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        EmergencyScreen(context, EmergencyPane.EXIT, onBack, onConfirmed, onCancel)
}

object EmergencyReferenceCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EmergencyReferenceOpenMethod.ID
    override val title = "Emergency reference"
    override val description = "Open a versioned offline emergency guide."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        EmergencyScreen(context, EmergencyPane.REFERENCE, onBack, onConfirmed, onCancel)
}

object EmergencyPackCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100EmergencyPackPrepareMethod.ID
    override val title = "Prepare this location"
    override val description = "Inspect regional offline emergency pack readiness."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) =
        EmergencyScreen(context, EmergencyPane.PREPARE, onBack, onConfirmed, onCancel)
}

private enum class EmergencyPane { DASHBOARD, LOCATION, EXIT, REFERENCE, PREPARE, VAULT }

@Composable
private fun EmergencyScreen(
    screenContext: CapabilityScreenContext,
    initialPane: EmergencyPane,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit
) {
    val androidContext = LocalContext.current
    val dashboardHost = initialPane == EmergencyPane.DASHBOARD
    var pane by rememberSaveable { mutableStateOf(initialPane) }
    var statusText by rememberSaveable { mutableStateOf("") }
    var resultValuesJson by rememberSaveable(screenContext.action.canonicalId) { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<ExecutionResult?>(null) }
    var workingResult by remember { mutableStateOf<ExecutionResult?>(null) }
    var locationLat by rememberSaveable { mutableStateOf("") }
    var locationLon by rememberSaveable { mutableStateOf("") }
    var locationAccuracy by rememberSaveable { mutableStateOf("") }
    var selectedGuideId by rememberSaveable {
        mutableStateOf(screenContext.action.settings.setting("guide_id") ?: "adult_cpr_aed")
    }
    var region by rememberSaveable {
        mutableStateOf(EmergencyCountries.normalizeCode(screenContext.action.settings.setting("country_iso2") ?: screenContext.action.settings.setting("region")))
    }
    var nationality by rememberSaveable {
        mutableStateOf(EmergencyCountries.normalizeCode(screenContext.action.settings.setting("nationality_iso2")))
    }
    var pendingVaultSlot by rememberSaveable { mutableStateOf<EmergencyVaultRepository.Slot?>(null) }

    val currentSnapshot = remember(pane, resultValuesJson, statusText) { EmergencyRepository.currentStatus(androidContext) }
    val restored = remember(resultValuesJson, screenContext.action.canonicalId) {
        resultValuesJson?.let(::stringMapFromJson)?.let { values ->
            resultForValues(screenContext, values)
        }
    }
    val capturedResult = if (dashboardHost) result else result ?: restored

    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) statusText = "Location permission granted. Tap capture again."
        else statusText = "Location permission is required for GPS capture."
    }

    val vaultDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val slot = pendingVaultSlot
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        runCatching {
            val bytes = androidContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Could not read selected document.")
            require(bytes.size <= 20 * 1024 * 1024) { "Vault item is larger than 20 MB." }
            EmergencyVaultRepository.write(androidContext, slot, bytes)
        }.onSuccess {
            statusText = "Encrypted ${slot.wireName.replace('_', ' ')} stored on device."
        }.onFailure { error ->
            statusText = "Vault import failed: ${error.message ?: "storage error"}"
        }
        pendingVaultSlot = null
    }

    fun setExecution(execution: ExecutionResult) {
        if (dashboardHost) workingResult = execution else result = execution
        val values = execution.observations.lastOrNull()?.values.orEmpty().mapValues { it.value.toString() }
        resultValuesJson = JSONObject(values).toString()
    }

    fun captureStatus() {
        val snapshot = EmergencyRepository.currentStatus(androidContext)
        val request = As100EmergencyStatusMethod.request(
            action = As100EmergencyStatusMethod.ID,
            context = screenContext.request.invocationContext.asMap(As100EmergencyStatusMethod.ID) + screenContext.action.settings
        )
        setExecution(As100EmergencyStatusMethod.result(request, snapshot, screenContext.request.invocationContext))
    }

    fun captureLocation(after: ((Double, Double, Double?) -> Unit)? = null) {
        if (!hasLocationPermission(androidContext)) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        statusText = "Acquiring GPS…"
        val cancellation = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(androidContext)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    statusText = "No location fix available."
                    return@addOnSuccessListener
                }
                locationLat = location.latitude.toString()
                locationLon = location.longitude.toString()
                locationAccuracy = if (location.hasAccuracy()) location.accuracy.toString() else ""
                statusText = "Location captured."
                after?.invoke(location.latitude, location.longitude, location.takeIf { it.hasAccuracy() }?.accuracy?.toDouble())
            }
            .addOnFailureListener { error -> statusText = "Location failed: ${error.message ?: "GPS unavailable"}" }
    }

    fun captureLocationResult() {
        captureLocation { lat, lon, accuracy ->
            val request = As100EmergencyLocationMethod.request(
                action = As100EmergencyLocationMethod.ID,
                context = screenContext.request.invocationContext.asMap(As100EmergencyLocationMethod.ID) + screenContext.action.settings
            )
            val values = As100EmergencyLocationMethod.locationValues(lat, lon, accuracy)
            setExecution(As100EmergencyLocationMethod.result(request, values, screenContext.request.invocationContext))
        }
    }

    fun captureExitResult() {
        captureLocation { lat, lon, _ ->
            val options = EmergencyGeo.rankExitOptions(
                latitude = lat,
                longitude = lon,
                pois = EmergencyRepository.strategicPois(androidContext),
                nationalityIso2 = EmergencyCountries.normalizeCode(nationality).takeIf(String::isNotBlank)
            )
            val request = As100EmergencyExitFindMethod.request(
                action = As100EmergencyExitFindMethod.ID,
                context = screenContext.request.invocationContext.asMap(As100EmergencyExitFindMethod.ID) + screenContext.action.settings
            )
            setExecution(As100EmergencyExitFindMethod.result(request, options, screenContext.request.invocationContext))
        }
    }

    fun captureReferenceResult(item: EmergencyContentItem) {
        val request = As100EmergencyReferenceOpenMethod.request(
            action = As100EmergencyReferenceOpenMethod.ID,
            context = screenContext.request.invocationContext.asMap(As100EmergencyReferenceOpenMethod.ID) + screenContext.action.settings + mapOf("guide_id" to item.id)
        )
        setExecution(As100EmergencyReferenceOpenMethod.result(request, item, screenContext.request.invocationContext))
    }

    fun capturePackResult() {
        val request = As100EmergencyPackPrepareMethod.request(
            action = As100EmergencyPackPrepareMethod.ID,
            context = screenContext.request.invocationContext.asMap(As100EmergencyPackPrepareMethod.ID) + screenContext.action.settings + mapOf("region" to EmergencyCountries.normalizeCode(region), "country_iso2" to EmergencyCountries.normalizeCode(region))
        )
        setExecution(As100EmergencyPackPrepareMethod.result(request, EmergencyCountries.normalizeCode(region), EmergencyRepository.preparednessState(androidContext), screenContext.request.invocationContext))
    }

    LaunchedEffect(screenContext.action.settings["monitoring_mode"], screenContext.action.settings["input_monitoring_mode"]) {
        val requested = screenContext.action.settings.setting("monitoring_mode")
        requested?.let { value ->
            runCatching { EmergencyMonitoringMode.valueOf(value) }.getOrNull()?.let { mode ->
                EmergencyRepository.setMonitoringSession(androidContext, mode, region)
            }
        }
    }

    LaunchedEffect(screenContext.startsImmediately, initialPane) {
        if (!screenContext.startsImmediately || capturedResult != null) return@LaunchedEffect
        when (initialPane) {
            EmergencyPane.DASHBOARD -> captureStatus()
            EmergencyPane.LOCATION -> captureLocationResult()
            EmergencyPane.EXIT -> captureExitResult()
            EmergencyPane.REFERENCE -> EmergencyRepository.referenceItems().firstOrNull { it.id == selectedGuideId }?.let(::captureReferenceResult)
            EmergencyPane.PREPARE -> capturePackResult()
            EmergencyPane.VAULT -> Unit
        }
    }

    val preview = capturedResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
    CapabilityScreenScaffold(
        title = if (dashboardHost) {
            "Emergency control centre"
        } else {
            when (initialPane) {
                EmergencyPane.DASHBOARD -> "Emergency control centre"
                EmergencyPane.LOCATION -> "Emergency location"
                EmergencyPane.EXIT -> "Exit strategy"
                EmergencyPane.REFERENCE -> "Emergency reference"
                EmergencyPane.PREPARE -> "Prepare this location"
                EmergencyPane.VAULT -> "My emergency documents"
            }
        },
        capabilityId = screenContext.action.canonicalId,
        context = screenContext,
        canGoBack = screenContext.stepNumber > 1,
        capturedResult = capturedResult,
        resultPreview = preview,
        onBack = onBack,
        onRetry = {
            result = null
            workingResult = null
            resultValuesJson = null
            statusText = ""
        },
        onConfirm = { capturedResult?.let(onConfirmed) },
        onCancel = onCancel
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (dashboardHost && pane == EmergencyPane.DASHBOARD) {
                DashboardPane(
                    snapshot = currentSnapshot,
                    workingResult = workingResult,
                    onStatus = ::captureStatus,
                    onLocation = { pane = EmergencyPane.LOCATION },
                    onCpr = { selectedGuideId = "adult_cpr_aed"; pane = EmergencyPane.REFERENCE },
                    onFirstAid = { pane = EmergencyPane.REFERENCE },
                    onExit = { pane = EmergencyPane.EXIT },
                    onPrepare = { pane = EmergencyPane.PREPARE },
                    onVault = { pane = EmergencyPane.VAULT },
                    onCommit = { workingResult?.let { result = it } }
                )
            } else {
                EmergencyStatusStrip(currentSnapshot, compact = true)
                if (dashboardHost) {
                    EmergencyDashboardBreadcrumb(
                        current = pane,
                        onDashboard = { pane = EmergencyPane.DASHBOARD }
                    )
                }
                when (pane) {
                EmergencyPane.DASHBOARD -> DashboardPane(
                    snapshot = currentSnapshot,
                    workingResult = workingResult,
                    onStatus = ::captureStatus,
                    onLocation = { pane = EmergencyPane.LOCATION },
                    onCpr = { selectedGuideId = "adult_cpr_aed"; pane = EmergencyPane.REFERENCE },
                    onFirstAid = { pane = EmergencyPane.REFERENCE },
                    onExit = { pane = EmergencyPane.EXIT },
                    onPrepare = { pane = EmergencyPane.PREPARE },
                    onVault = { pane = EmergencyPane.VAULT },
                    onCommit = { workingResult?.let { result = it } }
                )
                EmergencyPane.LOCATION -> LocationPane(
                    latitude = locationLat,
                    longitude = locationLon,
                    accuracy = locationAccuracy,
                    onCapture = ::captureLocationResult,
                    onDashboard = if (dashboardHost) ({ pane = EmergencyPane.DASHBOARD }) else null
                )
                EmergencyPane.EXIT -> ExitPane(
                    androidContext = androidContext,
                    latitude = locationLat,
                    longitude = locationLon,
                    nationality = nationality,
                    onNationalityChange = { raw ->
                        val code = EmergencyCountries.normalizeCode(raw)
                        nationality = code
                        screenContext.onSettingsChanged(mapOf("nationality_iso2" to code))
                    },
                    onFind = ::captureExitResult,
                    onDashboard = if (dashboardHost) ({ pane = EmergencyPane.DASHBOARD }) else null
                )
                EmergencyPane.REFERENCE -> ReferencePane(
                    items = EmergencyRepository.referenceItems(),
                    selectedId = selectedGuideId,
                    onSelect = { selectedGuideId = it.id; captureReferenceResult(it) },
                    onDashboard = if (dashboardHost) ({ pane = EmergencyPane.DASHBOARD }) else null
                )
                EmergencyPane.PREPARE -> PreparePane(
                    region = region,
                    onRegionChange = { raw ->
                        val code = EmergencyCountries.normalizeCode(raw)
                        region = code
                        screenContext.onSettingsChanged(mapOf("country_iso2" to code, "region" to code))
                    },
                    preparedness = EmergencyRepository.preparednessState(androidContext),
                    preparedRegions = EmergencyRepository.preparedRegions(androidContext),
                    poiCount = EmergencyRepository.strategicPois(androidContext).size,
                    onInspect = ::capturePackResult,
                    onDashboard = if (dashboardHost) ({ pane = EmergencyPane.DASHBOARD }) else null
                )
                EmergencyPane.VAULT -> VaultPane(
                    androidContext = androidContext,
                    statusText = statusText,
                    onImport = { slot ->
                        BiometricAuthHelper.authenticate(
                            context = androidContext,
                            title = "Emergency vault",
                            subtitle = "Authenticate to store a sensitive document",
                            description = "The selected file will be encrypted on this device.",
                            cancelText = "Cancel",
                            confirmationRequired = false,
                            allowDeviceCredential = true,
                            onSuccess = {
                                pendingVaultSlot = slot
                                vaultDocumentLauncher.launch(arrayOf("image/*", "application/pdf"))
                            },
                            onFailure = { statusText = it }
                        )
                    },
                    onDelete = { slot ->
                        BiometricAuthHelper.authenticate(
                            context = androidContext,
                            title = "Delete emergency vault item",
                            subtitle = slot.wireName.replace('_', ' '),
                            description = "This permanently deletes the encrypted local copy.",
                            cancelText = "Cancel",
                            confirmationRequired = true,
                            allowDeviceCredential = true,
                            onSuccess = {
                                statusText = if (EmergencyVaultRepository.delete(androidContext, slot)) "Deleted." else "No stored item to delete."
                            },
                            onFailure = { statusText = it }
                        )
                    },
                    onDashboard = if (dashboardHost) ({ pane = EmergencyPane.DASHBOARD }) else null
                )
                }
            }
            if (statusText.isNotBlank() && pane != EmergencyPane.VAULT) {
                Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmergencyDashboardBreadcrumb(
    current: EmergencyPane,
    onDashboard: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("EMERGENCY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text(
                when (current) {
                    EmergencyPane.LOCATION -> "Location & SOS"
                    EmergencyPane.EXIT -> "Exit strategy"
                    EmergencyPane.REFERENCE -> "Emergency guides"
                    EmergencyPane.PREPARE -> "Offline pack"
                    EmergencyPane.VAULT -> "Documents"
                    EmergencyPane.DASHBOARD -> "Control centre"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        OutlinedButton(onClick = onDashboard) { Text("Dashboard") }
    }
}

@Composable
private fun CountryDropdown(
    selectedCode: String,
    onSelected: (String) -> Unit,
    placeholder: String,
    allowClear: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val normalized = EmergencyCountries.normalizeCode(selectedCode)
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (normalized.isBlank()) placeholder else EmergencyCountries.labelFor(normalized))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowClear) {
                DropdownMenuItem(
                    text = { Text("None / not specified") },
                    onClick = { onSelected(""); expanded = false }
                )
            }
            EmergencyCountries.all.forEach { country ->
                DropdownMenuItem(
                    text = { Text(country.label) },
                    onClick = { onSelected(country.code); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun EmergencyStatusStrip(snapshot: EmergencyStatusSnapshot, compact: Boolean = false) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (compact) 18.dp else 24.dp),
        color = when (snapshot.localRisk) {
            EmergencyRiskState.RED -> MaterialTheme.colorScheme.errorContainer
            EmergencyRiskState.AMBER -> MaterialTheme.colorScheme.tertiaryContainer
            EmergencyRiskState.GREEN -> MaterialTheme.colorScheme.secondaryContainer
            EmergencyRiskState.GREY -> MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Column(
            Modifier.padding(if (compact) 14.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)
        ) {
            Text(
                if (compact) "${snapshot.localRisk.name} LOCAL RISK" else "${snapshot.localRisk.name}",
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black
            )
            if (!compact) Text("LOCAL RISK", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            snapshot.primaryAlert?.let { Text(it.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            if (snapshot.localRisk == EmergencyRiskState.GREY) {
                Text(
                    "Local risk cannot currently be established. This is not a safety clearance.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Offline: ${snapshot.preparedness.name}", style = MaterialTheme.typography.labelLarge)
                Text("Checked ${snapshot.checkedAt.toString().substringAfter('T').take(5)}", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DashboardPane(
    snapshot: EmergencyStatusSnapshot,
    workingResult: ExecutionResult?,
    onStatus: () -> Unit,
    onLocation: () -> Unit,
    onCpr: () -> Unit,
    onFirstAid: () -> Unit,
    onExit: () -> Unit,
    onPrepare: () -> Unit,
    onVault: () -> Unit,
    onCommit: () -> Unit
) {
    Text("EMERGENCY CONTROL CENTRE", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    Text("What do you need right now?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    EmergencyStatusStrip(snapshot)

    Button(onClick = onLocation, modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SHARE MY LOCATION", fontWeight = FontWeight.Bold)
            Text("GPS • Plus Code • coordinates", style = MaterialTheme.typography.labelMedium)
        }
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmergencyActionCard("FIRST AID", "Immediate guides", onFirstAid, Modifier.weight(1f))
        EmergencyActionCard("CPR / AED", "30-second mode", onCpr, Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        EmergencyActionCard("EXIT", "Airports • borders • ports", onExit, Modifier.weight(1f))
        EmergencyActionCard("OFFLINE PACK", "Prepare a country", onPrepare, Modifier.weight(1f))
    }
    EmergencyActionCard("DOCUMENTS", "Passport • insurance • emergency card", onVault, Modifier.fillMaxWidth())

    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Preparedness", fontWeight = FontWeight.Bold)
                Text(snapshot.preparedness.name, fontWeight = FontWeight.Bold)
            }
            val prepared = EmergencyRepository.preparedRegions(LocalContext.current)
            Text(
                if (prepared.isEmpty()) "No regional emergency pack is installed." else "Prepared: ${prepared.sorted().joinToString()}",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedButton(onClick = onStatus, modifier = Modifier.fillMaxWidth()) { Text("Refresh local status") }
        }
    }

    snapshot.explanation.take(3).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    val session = EmergencyRepository.monitoringSession(LocalContext.current)
    Text("Monitoring: ${session.mode.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

    if (workingResult != null) {
        OutlinedButton(onClick = onCommit, modifier = Modifier.fillMaxWidth()) { Text("Commit current result") }
    }
}

@Composable
private fun EmergencyActionCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.clickable(onClick = onClick), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LocationPane(latitude: String, longitude: String, accuracy: String, onCapture: () -> Unit, onDashboard: (() -> Unit)?) {
    val context = LocalContext.current
    if (latitude.isNotBlank() && longitude.isNotBlank()) {
        val lat = latitude.toDoubleOrNull()
        val lon = longitude.toDoubleOrNull()
        if (lat != null && lon != null) {
            val values = As100EmergencyLocationMethod.locationValues(lat, lon, accuracy.toDoubleOrNull())
            CopyValueCard("Plus Code", values[EmergencyLocationFields.PLUS_CODE].orEmpty())
            CopyValueCard("Coordinates", "$latitude, $longitude")
            Button(onClick = { shareText(context, values[EmergencyLocationFields.SHARE_TEXT].orEmpty()) }, modifier = Modifier.fillMaxWidth()) { Text("Share location") }
        }
    }
    Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("Capture current location") }
    onDashboard?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Emergency dashboard") } }
}

@Composable
private fun ExitPane(
    androidContext: Context,
    latitude: String,
    longitude: String,
    nationality: String,
    onNationalityChange: (String) -> Unit,
    onFind: () -> Unit,
    onDashboard: (() -> Unit)?
) {
    Text("Nationality", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Optional. Used only to prioritise embassies/consulates associated with your country of nationality. It does not change general airport, port or border results.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CountryDropdown(
        selectedCode = nationality,
        onSelected = onNationalityChange,
        placeholder = "Select nationality (optional)",
        allowClear = true
    )
    Text("Installed strategic POIs: ${EmergencyRepository.strategicPois(androidContext).size}", style = MaterialTheme.typography.bodySmall)
    Text("Offline POIs are known locations, not proof that an airport, port or border crossing is currently operating.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onFind, modifier = Modifier.fillMaxWidth()) { Text("Use GPS and find exit options") }
    if (latitude.isNotBlank() && longitude.isNotBlank()) {
        Text("Current fix: $latitude, $longitude", style = MaterialTheme.typography.labelSmall)
    }
    onDashboard?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Emergency dashboard") } }
}

@Composable
private fun ReferencePane(items: List<EmergencyContentItem>, selectedId: String, onSelect: (EmergencyContentItem) -> Unit, onDashboard: (() -> Unit)?) {
    val selected = items.firstOrNull { it.id == selectedId } ?: items.firstOrNull()
    selected?.let { item ->
        Text(item.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("NOW", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(item.nowText)
        Text("30 SEC", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(item.thirtySecondText)
        Text("FULL GUIDE", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(item.fullGuideText)
        Text("Source: ${item.sourceOrganisation} • ${item.sourcePublicationVersion}", style = MaterialTheme.typography.labelSmall)
        if (item.validatedAt == null) Text("MethodMesh adaptation is not yet content-validated for Production.", style = MaterialTheme.typography.bodySmall)
    }
    HorizontalDivider()
    Text("Offline guides", fontWeight = FontWeight.SemiBold)
    items.forEach { item ->
        Text(
            text = if (item.id == selectedId) "• ${item.title}" else item.title,
            modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }.padding(vertical = 5.dp),
            fontWeight = if (item.id == selectedId) FontWeight.Bold else FontWeight.Normal
        )
    }
    onDashboard?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Emergency dashboard") } }
}

@Composable
private fun PreparePane(
    region: String,
    onRegionChange: (String) -> Unit,
    preparedness: EmergencyPreparednessState,
    preparedRegions: Set<String>,
    poiCount: Int,
    onInspect: () -> Unit,
    onDashboard: (() -> Unit)?
) {
    Text("Country to prepare", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        "Choose the country whose emergency pack should be inspected or prepared for offline use.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    CountryDropdown(
        selectedCode = region,
        onSelected = onRegionChange,
        placeholder = "Select country",
        allowClear = false
    )
    Text("Preparedness: ${preparedness.name}")
    Text("Strategic exit POIs installed: $poiCount", style = MaterialTheme.typography.bodySmall)
    Text("Prepared regions: ${preparedRegions.sorted().joinToString().ifBlank { "none" }}", style = MaterialTheme.typography.bodySmall)
    Text("A complete regional pack is intended to contain richer POIs, offline map/routing data and local authoritative guidance. This build supports manifests/install state but does not silently mark a region READY without real pack files.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = onInspect, modifier = Modifier.fillMaxWidth(), enabled = EmergencyCountries.normalizeCode(region).isNotBlank()) { Text("Inspect pack requirements") }
    onDashboard?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Emergency dashboard") } }
}

@Composable
private fun VaultPane(
    androidContext: Context,
    statusText: String,
    onImport: (EmergencyVaultRepository.Slot) -> Unit,
    onDelete: (EmergencyVaultRepository.Slot) -> Unit,
    onDashboard: (() -> Unit)?
) {
    val activity = remember(androidContext) { androidContext.findActivity() }
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    Text("Sensitive emergency documents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Stored files are encrypted on-device. Vault content is never returned through MethodMesh result JSON, widgets or the deferred notification shade.", style = MaterialTheme.typography.bodySmall)
    EmergencyVaultRepository.metadata(androidContext).forEach { meta ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(meta.slot.wireName.replace('_', ' ').replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.SemiBold)
                Text(if (meta.present) "Stored • ${meta.bytes} encrypted bytes" else "Not stored", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onImport(meta.slot) }) { Text(if (meta.present) "Replace" else "Import") }
                    if (meta.present) OutlinedButton(onClick = { onDelete(meta.slot) }) { Text("Delete") }
                }
            }
        }
    }
    if (statusText.isNotBlank()) Text(statusText, style = MaterialTheme.typography.bodySmall)
    onDashboard?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Emergency dashboard") } }
}

@Composable
private fun CopyValueCard(label: String, value: String) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().clickable { copyText(context, label, value) }) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Tap to copy", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun resultForValues(context: CapabilityScreenContext, values: Map<String, String>): ExecutionResult? = when (context.action.canonicalId) {
    As100EmergencyStatusMethod.ID -> {
        val snapshot = EmergencyStatusSnapshot(
            localRisk = runCatching { EmergencyRiskState.valueOf(values[EmergencyStatusFields.STATUS].orEmpty()) }.getOrDefault(EmergencyRiskState.GREY),
            preparedness = runCatching { EmergencyPreparednessState.valueOf(values[EmergencyStatusFields.PREPAREDNESS].orEmpty()) }.getOrDefault(EmergencyPreparednessState.MINIMAL),
            checkedAt = values[EmergencyStatusFields.CHECKED_AT]?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now(),
            incompleteCriticalCoverage = values[EmergencyStatusFields.INCOMPLETE_COVERAGE].toBoolean(),
            explanation = values[EmergencyStatusFields.EXPLANATION].orEmpty().split(" | ").filter(String::isNotBlank)
        )
        As100EmergencyStatusMethod.result(
            As100EmergencyStatusMethod.request(context = context.request.invocationContext.asMap(As100EmergencyStatusMethod.ID) + context.action.settings),
            snapshot,
            context.request.invocationContext
        )
    }
    As100EmergencyLocationMethod.ID -> As100EmergencyLocationMethod.result(
        As100EmergencyLocationMethod.request(context = context.request.invocationContext.asMap(As100EmergencyLocationMethod.ID) + context.action.settings),
        values,
        context.request.invocationContext
    )
    As100EmergencyExitFindMethod.ID -> null // Options are intentionally recomputed from current installed pack on retry.
    As100EmergencyReferenceOpenMethod.ID -> EmergencyCoreData.referenceItems.firstOrNull { it.id == values[EmergencyReferenceFields.GUIDE_ID] }?.let { item ->
        As100EmergencyReferenceOpenMethod.result(
            As100EmergencyReferenceOpenMethod.request(context = context.request.invocationContext.asMap(As100EmergencyReferenceOpenMethod.ID) + context.action.settings),
            item,
            context.request.invocationContext
        )
    }
    As100EmergencyPackPrepareMethod.ID -> As100EmergencyPackPrepareMethod.result(
        As100EmergencyPackPrepareMethod.request(context = context.request.invocationContext.asMap(As100EmergencyPackPrepareMethod.ID) + context.action.settings),
        values[EmergencyPackFields.REGION].orEmpty(),
        runCatching { EmergencyPreparednessState.valueOf(values[EmergencyPackFields.PREPAREDNESS].orEmpty()) }.getOrDefault(EmergencyPreparednessState.MINIMAL),
        context.request.invocationContext
    )
    else -> null
}

private fun stringMapFromJson(raw: String): Map<String, String> = runCatching {
    val root = JSONObject(raw)
    buildMap { root.keys().forEach { key -> put(key, root.optString(key)) } }
}.getOrDefault(emptyMap())

private fun Map<String, String>.setting(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf(String::isNotBlank)

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun copyText(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    ResultShare.share(
        context = context,
        chooserTitle = "Share emergency location",
        text = text,
        attachments = emptyList()
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
