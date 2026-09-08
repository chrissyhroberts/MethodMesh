package com.example.methodmesh.ui.kobo

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.ui.components.SecurePasswordField
import kotlinx.coroutines.launch

@Composable
fun KoboSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(KoboConnectionRepository.load(context)) }
    var serverUrl by rememberSaveable { mutableStateOf(profile.serverUrl) }
    var username by rememberSaveable { mutableStateOf(profile.username) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var connectedAs by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }

    fun token(): String? = KoboSessionStore.load(context)?.token

    fun refreshIdentity() {
        val sessionToken = token() ?: return
        val server = KoboConnectionRepository.normalizeServerUrl(serverUrl)
        scope.launch {
            busy = true
            runCatching { KoboClient.currentUser(server, sessionToken) }
                .onSuccess { current ->
                    connectedAs = current.optString("username").ifBlank { current.optString("email") }
                    status = "Connected to KoboToolbox."
                }
                .onFailure { error ->
                    if (error is KoboApiException && error.statusCode == 401) KoboSessionStore.clear(context)
                    status = error.message ?: "Could not connect to KoboToolbox."
                }
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (token() != null && profile.serverUrl.isNotBlank()) refreshIdentity()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Connect a KoboToolbox account for disposable KoboCollect example testing. MethodMesh-managed forms are never production/live forms. The password is used only to retrieve your API token and is never stored; the token is kept encrypted on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Kobo server") },
            placeholder = { Text("https://kf.kobotoolbox.org") },
            singleLine = true
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Kobo username") },
            singleLine = true
        )
        SecurePasswordField(
            value = password,
            onValueChange = { password = it },
            visible = showPassword,
            onVisibleChange = { showPassword = it },
            label = "Password — not saved",
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            KoboInlineAction(
                if (token() == null) "Connect" else "Reconnect",
                emphasized = true,
                onClick = {
                    val normalized = KoboConnectionRepository.normalizeServerUrl(serverUrl)
                    if (!normalized.startsWith("https://")) {
                        status = "KoboToolbox login requires HTTPS here so your password is not sent in clear text."
                        return@KoboInlineAction
                    }
                    KoboConnectionRepository.saveConnection(context, normalized, username)
                    profile = KoboConnectionRepository.load(context)
                    if (password.isBlank()) {
                        status = "Enter your KoboToolbox password to retrieve an API token. It will not be saved."
                    } else {
                        scope.launch {
                            busy = true
                            status = "Connecting…"
                            runCatching { KoboClient.login(normalized, username, password) }
                                .onSuccess { session ->
                                    KoboSessionStore.save(context, session)
                                    password = ""
                                    showPassword = false
                                    status = "Connected."
                                    refreshIdentity()
                                }
                                .onFailure { status = it.message ?: "KoboToolbox login failed." }
                            busy = false
                        }
                    }
                }
            )
            if (token() != null) {
                Text("  ·  ")
                KoboInlineAction("Disconnect") {
                    KoboSessionStore.clear(context)
                    password = ""
                    showPassword = false
                    connectedAs = null
                    status = "Disconnected from KoboToolbox."
                }
            }
        }
        connectedAs?.let {
            Text(
                "Connected as $it",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        status?.let {
            Text(
                it,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("fail", true) || it.contains("could not", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "In ODK Forms, ticking Kobo uploads or updates a disposable test copy and makes it available in KoboCollect. Advisory XLSForm warnings are non-blocking; validation errors still block. Unticking deactivates the remote test project without deleting it or its test submissions. Permanent removal is a separate confirmed action.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "If your Kobo account uses 2FA or password login is unavailable, obtain the API key from Kobo Account Settings and use a dedicated test account for this rapid-test workflow. Direct API-key entry can be added as a later refinement.",
            modifier = Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KoboInlineAction(label: String, emphasized: Boolean = false, onClick: () -> Unit) {
    Text(
        label,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
        color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    )
}
