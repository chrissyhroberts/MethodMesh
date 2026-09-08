package com.example.methodmesh.ui.odkcentral

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.methodmesh.ui.components.SecurePasswordField
import kotlinx.coroutines.launch

@Composable
fun OdkCentralSettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf(OdkCentralConnectionRepository.load(context)) }
    var serverUrl by rememberSaveable { mutableStateOf(profile.serverUrl) }
    var email by rememberSaveable { mutableStateOf(profile.email) }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var projects by remember { mutableStateOf(emptyList<OdkCentralProject>()) }
    var appUsers by remember { mutableStateOf(emptyList<OdkCentralAppUser>()) }
    var busy by remember { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var connectedAs by rememberSaveable { mutableStateOf<String?>(null) }

    fun token(): String? = OdkCentralSessionStore.load(context)?.token

    fun refreshProjectData() {
        val sessionToken = token() ?: return
        val server = OdkCentralConnectionRepository.normalizeServerUrl(serverUrl)
        scope.launch {
            busy = true
            runCatching {
                OdkCentralClient.currentUser(server, sessionToken).also { current ->
                    connectedAs = current.optString("displayName").ifBlank { current.optString("email") }
                }
                projects = OdkCentralClient.listProjects(server, sessionToken)
                val selectedProjectId = OdkCentralConnectionRepository.load(context).projectId
                appUsers = if (selectedProjectId != null) {
                    OdkCentralClient.listAppUsers(server, sessionToken, selectedProjectId)
                } else emptyList()
            }.onFailure { error ->
                if (error is OdkCentralApiException && error.statusCode == 401) OdkCentralSessionStore.clear(context)
                status = error.message ?: "Could not load ODK Central."
            }
            profile = OdkCentralConnectionRepository.load(context)
            busy = false
        }
    }

    LaunchedEffect(Unit) {
        if (token() != null && profile.serverUrl.isNotBlank()) refreshProjectData()
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            "Connect one ODK Central project for rapid module-form testing. Your password is used only to create a short-lived Central session and is never stored.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = serverUrl,
            onValueChange = { serverUrl = it },
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            label = { Text("Central server") },
            placeholder = { Text("https://central.example.org") },
            singleLine = true
        )
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Web User email") },
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
            CentralInlineAction(
                if (token() == null) "Connect" else "Reconnect",
                emphasized = true,
                onClick = {
                    val normalized = OdkCentralConnectionRepository.normalizeServerUrl(serverUrl)
                    if (!normalized.startsWith("https://")) {
                        status = "ODK Central login requires HTTPS here so your password is not sent in clear text."
                        return@CentralInlineAction
                    }
                    OdkCentralConnectionRepository.saveConnection(context, normalized, email)
                    profile = OdkCentralConnectionRepository.load(context)
                    if (password.isBlank()) {
                        status = "Enter your ODK Central password to create a session. It will not be saved."
                    } else {
                        scope.launch {
                            busy = true
                            status = "Connecting…"
                            runCatching { OdkCentralClient.login(normalized, email, password) }
                                .onSuccess { session ->
                                    OdkCentralSessionStore.save(context, session)
                                    password = ""
                                    showPassword = false
                                    status = "Connected."
                                    refreshProjectData()
                                }
                                .onFailure { status = it.message ?: "Could not sign in to ODK Central." }
                            busy = false
                        }
                    }
                }
            )
            Spacer(Modifier.weight(1f))
            if (token() != null) {
                CentralInlineAction("Disconnect", onClick = {
                    OdkCentralSessionStore.clear(context)
                    showPassword = false
                    connectedAs = null
                    projects = emptyList()
                    appUsers = emptyList()
                    status = "Disconnected."
                })
            }
        }

        connectedAs?.let {
            Text(
                "Connected as $it",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
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

        if (projects.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("Test project", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            projects.forEach { project ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                        OdkCentralConnectionRepository.selectProject(context, project)
                        profile = OdkCentralConnectionRepository.load(context)
                        val sessionToken = token() ?: return@clickable
                        scope.launch {
                            busy = true
                            appUsers = runCatching {
                                OdkCentralClient.listAppUsers(profile.serverUrl, sessionToken, project.id)
                            }.getOrElse {
                                status = it.message ?: "Could not load App Users."
                                emptyList()
                            }
                            busy = false
                        }
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = profile.projectId == project.id,
                        onClick = null,
                        enabled = !busy
                    )
                    Column(Modifier.padding(start = 6.dp)) {
                        Text(project.name, style = MaterialTheme.typography.bodyMedium)
                        Text("Project ${project.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (profile.projectId != null) {
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Text("Test App User", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Ticking a form creates or updates a disposable MethodMesh test copy, publishes it, and grants this App User access. XLSForm warnings are ignored; genuine validation/conversion errors still block. Unticking revokes only this user's access; it does not delete the remote test copy or test submissions.",
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (appUsers.isEmpty()) {
                Text("No App Users available for this project, or they have not been loaded yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                appUsers.forEach { user ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = !busy && user.tokenActive) {
                            OdkCentralConnectionRepository.selectAppUser(context, user)
                            profile = OdkCentralConnectionRepository.load(context)
                        }.padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = profile.appUserId == user.id, onClick = null, enabled = !busy && user.tokenActive)
                        Column(Modifier.padding(start = 6.dp)) {
                            Text(user.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (user.tokenActive) "App User ${user.id}" else "App User ${user.id} · access revoked",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CentralInlineAction(
    label: String,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
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
