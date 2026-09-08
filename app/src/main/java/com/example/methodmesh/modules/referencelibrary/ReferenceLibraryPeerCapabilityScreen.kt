package com.example.methodmesh.modules.referencelibrary

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.delay
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID

/**
 * One-screen nearby library manager. It intentionally does not use the generic
 * result scaffold: live connection details, transfer counters and stop/finish
 * controls stay together on the same operational surface.
 */
object ReferenceLibraryPeerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ReferenceLibraryPeerMethod.ID
    override val title = "Nearby library manager"
    override val description = "Create a temporary local library website for batch upload and shelf maintenance."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val hostActivity = remember(appContext) { appContext.findActivity() }
        val repository = remember { ReferenceLibraryRepository(appContext) }
        val existingSession = remember { ReferenceLibraryPeerRuntime.current() }
        val hotspot = remember { existingSession?.hotspot ?: ReferenceLibraryHotspotController(appContext) }
        val mainHandler = remember { Handler(Looper.getMainLooper()) }
        fun initial(key: String): String = context.action.settings[key] ?: context.action.settings["input_$key"] ?: ""

        var networkMode by rememberSaveable { mutableStateOf(existingSession?.networkMode ?: initial("network_mode").ifBlank { "local_hotspot" }) }
        var sessionMinutes by rememberSaveable { mutableStateOf(existingSession?.sessionMinutes ?: (initial("session_minutes").toIntOrNull()?.coerceIn(5, 240) ?: 30)) }
        var maxFileMb by rememberSaveable { mutableStateOf(existingSession?.maxFileMb ?: (initial("max_file_mb").toIntOrNull()?.coerceIn(10, 2048) ?: 250)) }
        var defaultShelf by rememberSaveable { mutableStateOf(existingSession?.defaultShelf ?: initial("default_shelf").ifBlank { "personal" }) }
        var allowEdits by rememberSaveable { mutableStateOf(existingSession?.allowEdits ?: initial("allow_edits").ifBlank { "true" }.equals("true", true)) }
        var durationMenu by remember { mutableStateOf(false) }
        var sizeMenu by remember { mutableStateOf(false) }
        var shelfMenu by remember { mutableStateOf(false) }
        var modeMenu by remember { mutableStateOf(false) }

        var server by remember { mutableStateOf(existingSession?.server) }
        // Credentials remain process-memory only. The runtime holder keeps the
        // live socket/hotspot reachable across ordinary Activity recreation;
        // process death still ends the temporary session.
        var active by remember { mutableStateOf(existingSession != null) }
        var starting by remember { mutableStateOf(false) }
        var sessionId by remember { mutableStateOf(existingSession?.sessionId.orEmpty()) }
        var sessionToken by remember { mutableStateOf(existingSession?.sessionToken.orEmpty()) }
        var startedEpochMs by remember { mutableStateOf(existingSession?.startedEpochMs ?: 0L) }
        var startedTimeIso by remember { mutableStateOf(existingSession?.startedTimeIso.orEmpty()) }
        var addresses by remember { mutableStateOf(if (existingSession != null) referenceLibraryPrivateIpv4Addresses() else emptyList()) }
        var port by remember { mutableStateOf(existingSession?.port ?: 0) }
        var ssid by remember { mutableStateOf(existingSession?.ssid.orEmpty()) }
        var passphrase by remember { mutableStateOf(existingSession?.passphrase.orEmpty()) }
        var stats by remember { mutableStateOf(existingSession?.server?.snapshot() ?: ReferenceLibraryPeerStats()) }
        var status by rememberSaveable { mutableStateOf(if (existingSession != null) "Nearby library is ready." else "Ready to create a temporary local library connection.") }
        var pendingHotspotPermission by remember { mutableStateOf(false) }
        val shelves = remember(active, stats.shelfChangesCount) { REFERENCE_LIBRARY_BUILT_IN_SHELVES + repository.customShelves() }
        LaunchedEffect(shelves, defaultShelf) {
            if (defaultShelf !in shelves.map { it.id }) defaultShelf = "personal"
        }

        fun settingsMap() = mapOf(
            "network_mode" to networkMode,
            "session_minutes" to sessionMinutes.toString(),
            "max_file_mb" to maxFileMb.toString(),
            "default_shelf" to defaultShelf,
            "allow_edits" to allowEdits.toString()
        )

        LaunchedEffect(networkMode, sessionMinutes, maxFileMb, defaultShelf, allowEdits) {
            context.onSettingsChanged(settingsMap())
        }

        fun copy(label: String, value: String) {
            if (value.isBlank()) return
            appContext.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText(label, value))
            status = "$label copied."
        }

        fun buildResult(statusValue: String, error: String = ""): ExecutionResult {
            val finished = Instant.now().toString()
            val duration = if (startedEpochMs > 0L) System.currentTimeMillis() - startedEpochMs else 0L
            val request = As100ReferenceLibraryPeerMethod.request(
                action = As100ReferenceLibraryPeerMethod.ID,
                context = context.request.invocationContext.asMap(As100ReferenceLibraryPeerMethod.ID) + context.action.settings + settingsMap(),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100ReferenceLibraryPeerMethod.result(
                request,
                As100ReferenceLibraryPeerMethod.values(
                    status = statusValue,
                    networkMode = networkMode,
                    sessionId = sessionId,
                    stats = stats,
                    startedTimeIso = startedTimeIso,
                    finishedTimeIso = finished,
                    durationMs = duration,
                    error = error
                ),
                context.request.invocationContext
            )
        }

        fun clearConnectionState() {
            active = false
            starting = false
            addresses = emptyList()
            port = 0
            ssid = ""
            passphrase = ""
            sessionToken = ""
        }

        fun stopInfrastructure() {
            stats = server?.snapshot() ?: stats
            ReferenceLibraryPeerRuntime.clearIf(server)
            server?.stop()
            server = null
            hotspot.stop()
            clearConnectionState()
        }

        fun finishSession(statusValue: String = "completed", error: String = "") {
            stats = server?.snapshot() ?: stats
            val result = buildResult(statusValue, error)
            stopInfrastructure()
            onConfirmed(result)
        }

        fun startServer(mode: String, hotspotInfo: ReferenceLibraryHotspotInfo? = null) {
            val token = randomSessionToken()
            val id = UUID.randomUUID().toString()
            val peerServer = ReferenceLibraryPeerServer(
                context = appContext,
                repository = repository,
                sessionToken = token,
                defaultShelf = defaultShelf,
                maxFileBytes = maxFileMb.toLong() * 1024L * 1024L,
                allowEdits = allowEdits,
                onStatsChanged = { newStats -> mainHandler.post { stats = newStats } }
            )
            val startedPort = runCatching { peerServer.start() }
                .getOrElse { error ->
                    hotspot.stop()
                    starting = false
                    status = "Could not start the local library website: ${error.message ?: "socket error"}"
                    return
                }
            server = peerServer
            sessionId = id
            sessionToken = token
            startedEpochMs = System.currentTimeMillis()
            startedTimeIso = Instant.now().toString()
            port = startedPort
            ssid = hotspotInfo?.ssid.orEmpty()
            passphrase = hotspotInfo?.passphrase.orEmpty()
            networkMode = mode
            stats = ReferenceLibraryPeerStats()
            active = true
            starting = false
            ReferenceLibraryPeerRuntime.adopt(
                ReferenceLibraryPeerRuntimeSession(
                    server = peerServer,
                    hotspot = hotspot,
                    sessionId = id,
                    sessionToken = token,
                    networkMode = mode,
                    startedEpochMs = startedEpochMs,
                    startedTimeIso = startedTimeIso,
                    sessionMinutes = sessionMinutes,
                    maxFileMb = maxFileMb,
                    defaultShelf = defaultShelf,
                    allowEdits = allowEdits,
                    port = startedPort,
                    ssid = ssid,
                    passphrase = passphrase
                )
            )
            status = "Nearby library started. Waiting for the local address…"
            mainHandler.postDelayed({
                if (server === peerServer && active) {
                    val found = referenceLibraryPrivateIpv4Addresses()
                    if (found.isNotEmpty()) addresses = found
                    status = if (found.isEmpty()) {
                        "Session is live, but Android has not exposed a private IPv4 address yet."
                    } else {
                        "Nearby library is ready."
                    }
                }
            }, if (mode == "local_hotspot") 900L else 150L)
        }

        fun startHotspot() {
            starting = true
            status = "Starting a local-only Wi-Fi network…"
            hotspot.start(
                onStarted = { info -> startServer("local_hotspot", info) },
                onStopped = {
                    if (active || starting) {
                        ReferenceLibraryPeerRuntime.clearIf(server)
                        server?.stop()
                        server = null
                        clearConnectionState()
                        status = "Local hotspot stopped."
                    }
                },
                onFailed = { error ->
                    starting = false
                    status = error
                }
            )
        }

        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (pendingHotspotPermission) {
                pendingHotspotPermission = false
                if (granted) startHotspot() else status = "Nearby Wi-Fi permission is required to create the local hotspot."
            }
        }

        fun requestHotspotStart() {
            if (ContextCompat.checkSelfPermission(appContext, requiredPermission) == PackageManager.PERMISSION_GRANTED) {
                startHotspot()
            } else {
                pendingHotspotPermission = true
                permissionLauncher.launch(requiredPermission)
            }
        }

        fun startCurrentWifi() {
            starting = true
            status = "Starting the local library website on the current Wi-Fi network…"
            startServer("current_wifi", null)
        }

        DisposableEffect(hostActivity) {
            onDispose {
                // Rotation/configuration recreation keeps the process-memory session
                // alive; leaving the capability for any other reason closes it.
                if (hostActivity?.isChangingConfigurations != true) {
                    ReferenceLibraryPeerRuntime.clearIf(server)
                    server?.stop()
                    hotspot.stop()
                } else if (!active) {
                    // A half-started reservation cannot be safely reattached.
                    server?.stop()
                    hotspot.stop()
                }
            }
        }

        LaunchedEffect(active, sessionId, sessionMinutes, startedEpochMs) {
            if (active && sessionId.isNotBlank() && startedEpochMs > 0L) {
                val totalMs = sessionMinutes.toLong() * 60_000L
                val remainingMs = (totalMs - (System.currentTimeMillis() - startedEpochMs)).coerceAtLeast(0L)
                if (remainingMs > 0L) delay(remainingMs)
                if (active) finishSession("completed")
            }
        }

        LaunchedEffect(active, port, server) {
            while (active && port > 0) {
                stats = server?.snapshot() ?: stats
                val found = referenceLibraryPrivateIpv4Addresses()
                if (found.isNotEmpty()) addresses = found
                delay(900L)
            }
        }

        val primaryAddress = addresses.firstOrNull().orEmpty()
        val managerUrl = if (primaryAddress.isNotBlank() && port > 0 && sessionToken.isNotBlank()) {
            "http://$primaryAddress:$port/manage?token=$sessionToken"
        } else ""

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Nearby library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (active) "Temporary local manager is live" else "Batch-load and organise from another device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = {
                        if (active || starting) stopInfrastructure()
                        onBack()
                    }) { Text("Back") }
                }

                Spacer(Modifier.height(18.dp))
                if (!active && !starting) {
                    Text(
                        "MethodMesh can create an isolated local-only hotspot with no internet access. Connect a laptop or tablet, open the address shown here, then drag whole batches into a shelf.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(18.dp))

                    if (!context.settingIsFixedInNativePreset("network_mode")) {
                        PeerSettingRow("Network") {
                            Box {
                                OutlinedButton(onClick = { modeMenu = true }) {
                                    Text(if (networkMode == "current_wifi") "Current Wi-Fi" else "Local hotspot")
                                }
                                DropdownMenu(expanded = modeMenu, onDismissRequest = { modeMenu = false }) {
                                    DropdownMenuItem(text = { Text("Local hotspot · recommended") }, onClick = { networkMode = "local_hotspot"; modeMenu = false })
                                    DropdownMenuItem(text = { Text("Current Wi-Fi · less private") }, onClick = { networkMode = "current_wifi"; modeMenu = false })
                                }
                            }
                        }
                    }
                    if (!context.settingIsFixedInNativePreset("session_minutes")) {
                        PeerSettingRow("Session") {
                            Box {
                                OutlinedButton(onClick = { durationMenu = true }) { Text("$sessionMinutes min") }
                                DropdownMenu(expanded = durationMenu, onDismissRequest = { durationMenu = false }) {
                                    listOf(10, 30, 60, 120).forEach { value ->
                                        DropdownMenuItem(text = { Text("$value minutes") }, onClick = { sessionMinutes = value; durationMenu = false })
                                    }
                                }
                            }
                        }
                    }
                    if (!context.settingIsFixedInNativePreset("max_file_mb")) {
                        PeerSettingRow("Max file") {
                            Box {
                                OutlinedButton(onClick = { sizeMenu = true }) { Text("$maxFileMb MB") }
                                DropdownMenu(expanded = sizeMenu, onDismissRequest = { sizeMenu = false }) {
                                    listOf(25, 100, 250, 500, 1024).forEach { value ->
                                        DropdownMenuItem(text = { Text("$value MB") }, onClick = { maxFileMb = value; sizeMenu = false })
                                    }
                                }
                            }
                        }
                    }
                    if (!context.settingIsFixedInNativePreset("default_shelf")) {
                        PeerSettingRow("Default shelf") {
                            Box {
                                OutlinedButton(onClick = { shelfMenu = true }) {
                                    Text(shelves.firstOrNull { it.id == defaultShelf }?.label ?: "Personal", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                DropdownMenu(expanded = shelfMenu, onDismissRequest = { shelfMenu = false }) {
                                    shelves.forEach { item ->
                                        DropdownMenuItem(text = { Text(item.label) }, onClick = { defaultShelf = item.id; shelfMenu = false })
                                    }
                                }
                            }
                        }
                    }
                    if (!context.settingIsFixedInNativePreset("allow_edits")) {
                        PeerSettingRow("Web edits") {
                            Switch(checked = allowEdits, onCheckedChange = { allowEdits = it })
                        }
                    }

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { if (networkMode == "current_wifi") startCurrentWifi() else requestHotspotStart() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) { Text(if (networkMode == "current_wifi") "Start on current Wi-Fi" else "Start local library network") }
                    if (networkMode == "current_wifi") {
                        Text(
                            "Current Wi-Fi is a fallback for trusted LANs. Upload traffic is local HTTP and is not encrypted end-to-end; use the isolated hotspot for sensitive documents.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 9.dp)
                        )
                    }
                } else if (starting) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.inverseSurface
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Starting nearby library…", color = MaterialTheme.colorScheme.inverseOnSurface, fontWeight = FontWeight.SemiBold)
                            Text(status, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f), modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { stopInfrastructure() }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        color = MaterialTheme.colorScheme.inverseSurface
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Text("LOCAL LIBRARY LIVE", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            if (networkMode == "local_hotspot") {
                                PeerCopyValue("Wi-Fi name", ssid, onCopy = { copy("Wi-Fi name", ssid) })
                                PeerCopyValue("Password", passphrase, onCopy = { copy("Wi-Fi password", passphrase) })
                                Spacer(Modifier.height(8.dp))
                            }
                            PeerCopyValue(
                                "Website",
                                managerUrl.ifBlank { "Waiting for local address…" },
                                onCopy = { copy("Library website", managerUrl) }
                            )
                            if (addresses.size > 1) {
                                Text(
                                    "Other local addresses: ${addresses.drop(1).joinToString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f),
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Connect the other device${if (networkMode == "local_hotspot") " to the Wi-Fi above" else " to the same trusted Wi-Fi"}, then open the website. The link contains a temporary session key.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.72f)
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeerStat("Uploaded", stats.uploadedCount.toString(), Modifier.weight(1f))
                        PeerStat("Edited", stats.updatedCount.toString(), Modifier.weight(1f))
                        PeerStat("Removed", stats.removedCount.toString(), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PeerStat("Shelves", stats.shelfChangesCount.toString(), Modifier.weight(1f))
                        PeerStat("Received", humanBytes(stats.bytesReceived), Modifier.weight(2f))
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { finishSession("completed") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("Stop session and return")
                    }
                }

                if (status.isNotBlank()) {
                    Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun PeerSettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.width(12.dp))
        control()
    }
}

@Composable
private fun PeerCopyValue(label: String, value: String, onCopy: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(enabled = value.isNotBlank(), onClick = onCopy).padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.58f))
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PeerStat(label: String, value: String, modifier: Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun randomSessionToken(): String {
    val bytes = ByteArray(18)
    SecureRandom().nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

private fun humanBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}
