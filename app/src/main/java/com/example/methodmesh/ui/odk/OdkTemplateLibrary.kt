package com.example.methodmesh.ui.odk

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.core.content.FileProvider
import com.example.methodmesh.ui.components.MethodMeshDestructiveConfirmation
import com.example.methodmesh.ui.odkcentral.OdkCentralClient
import com.example.methodmesh.ui.odkcentral.OdkCentralConnectionRepository
import com.example.methodmesh.ui.odkcentral.OdkCentralDeploymentStore
import com.example.methodmesh.ui.odkcentral.OdkCentralProfile
import com.example.methodmesh.ui.odkcentral.OdkCentralSessionStore
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val INDEX_ASSET = "methodmesh/artifacts/index.json"

data class OdkTemplateDescriptor(
    val id: String,
    val moduleId: String,
    val moduleName: String,
    val displayName: String,
    val description: String,
    val assetPath: String,
    val sourceFileName: String,
    val centralFormId: String,
    val capabilityIds: List<String>,
    val tags: List<String>
)

object OdkTemplateCatalog {
    fun load(context: Context): List<OdkTemplateDescriptor> = runCatching {
        val json = context.assets.open(INDEX_ASSET).bufferedReader().use { it.readText() }
        val root = JSONObject(json)
        val templates = root.optJSONArray("artifacts") ?: JSONArray()
        buildList {
            for (i in 0 until templates.length()) {
                val item = templates.optJSONObject(i) ?: continue
                val assetPath = item.optString("assetPath")
                if (assetPath.isBlank()) continue
                add(
                    OdkTemplateDescriptor(
                        id = item.optString("id").ifBlank { assetPath },
                        moduleId = item.optString("moduleId").ifBlank { "other" },
                        moduleName = item.optString("moduleName").ifBlank { "Other" },
                        displayName = item.optString("displayName").ifBlank {
                            displayNameFromFilename(item.optString("sourceFileName").ifBlank { assetPath.substringAfterLast('/') })
                        },
                        description = item.optString("description"),
                        assetPath = assetPath,
                        sourceFileName = item.optString("sourceFileName").ifBlank { assetPath.substringAfterLast('/') },
                        centralFormId = item.optString("centralFormId"),
                        capabilityIds = item.optJSONArray("capabilityIds").stringList(),
                        tags = item.optJSONArray("tags").stringList()
                    )
                )
            }
        }.sortedWith(compareBy<OdkTemplateDescriptor> { it.moduleName.lowercase() }.thenBy { it.displayName.lowercase() })
    }.getOrDefault(emptyList())

    fun searchScore(template: OdkTemplateDescriptor, query: String): Int {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return 0
        val corpus = normalize(
            listOf(
                template.displayName,
                template.moduleName,
                template.moduleId,
                template.description,
                template.sourceFileName,
                template.centralFormId,
                template.capabilityIds.joinToString(" "),
                template.tags.joinToString(" "),
                "ODK XLSForm form design template"
            ).joinToString(" ")
        )
        var score = if (corpus.contains(normalizedQuery)) 100 else 0
        normalizedQuery.split(' ').filter { it.length > 1 }.forEach { token ->
            if (corpus.contains(token)) score += 25
        }
        return score
    }

    fun share(context: Context, template: OdkTemplateDescriptor): Result<Unit> = runCatching {
        val file = materialize(context, template)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(template.displayName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ODK design template"))
    }

    fun saveTo(context: Context, template: OdkTemplateDescriptor, destination: Uri): Result<Unit> = runCatching {
        com.example.methodmesh.core.artifacts.AndroidArtifacts.service(context).open(
            com.example.methodmesh.core.artifacts.ArtifactRef(template.id)
        ).use { input ->
            context.contentResolver.openOutputStream(destination)?.use { output ->
                input.copyTo(output)
            } ?: error("Could not open destination")
        }
    }

    private fun materialize(context: Context, template: OdkTemplateDescriptor): File {
        val dir = File(context.cacheDir, "odk_templates/${template.id}").apply { mkdirs() }
        val safeName = template.sourceFileName.ifBlank { "${template.id}.xlsx" }
        val file = File(dir, safeName)
        com.example.methodmesh.core.artifacts.AndroidArtifacts.service(context).open(
            com.example.methodmesh.core.artifacts.ArtifactRef(template.id)
        ).use { input -> file.outputStream().use { input.copyTo(it) } }
        return file
    }

    private fun normalize(value: String): String = value.lowercase().replace(Regex("[^a-z0-9+]+"), " ").trim()

    private fun displayNameFromFilename(filename: String): String = filename
        .substringAfterLast('/')
        .removeSuffix(".xlsx")
        .removePrefix("example_odk_")
        .replace('.', ' ')
        .replace('_', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercase() } }

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
    }
}

private data class PendingCentralRemoval(
    val template: OdkTemplateDescriptor,
    val submissions: Int
)

@Composable
fun OdkTemplateLibrary(initialQuery: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val templates = remember { OdkTemplateCatalog.load(context) }
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var pendingSave by remember { mutableStateOf<OdkTemplateDescriptor?>(null) }
    var pendingCentralRemoval by remember { mutableStateOf<PendingCentralRemoval?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var busyTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var deploymentRevision by remember { mutableIntStateOf(0) }
    val profile = remember(deploymentRevision) { OdkCentralConnectionRepository.load(context) }
    val session = remember(deploymentRevision) { OdkCentralSessionStore.load(context) }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        val template = pendingSave
        pendingSave = null
        if (uri != null && template != null) {
            status = OdkTemplateCatalog.saveTo(context, template, uri).fold(
                onSuccess = { "Saved ${template.displayName}." },
                onFailure = { "Save failed: ${it.message ?: "storage error"}" }
            )
        }
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) query = initialQuery
    }

    val trimmed = query.trim()
    val filtered = remember(templates, trimmed) {
        if (trimmed.isBlank()) templates else templates
            .map { it to OdkTemplateCatalog.searchScore(it, trimmed) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val grouped = filtered.groupBy { it.moduleName }.toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ODK forms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Module-owned XLSForm design templates.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (profile.isConfigured && session != null) {
                Text(
                    "Sync checked",
                    modifier = Modifier.clickable(enabled = busyTemplateId == null) {
                        scope.launch {
                            val checked = templates.filter { template ->
                                val deployment = profile.deployment(context, template)
                                deployment?.assignedAppUserId == profile.appUserId
                            }
                            if (checked.isEmpty()) {
                                status = "No forms are checked for ${profile.appUserName}."
                                return@launch
                            }
                            busyTemplateId = "__all__"
                            var successes = 0
                            var failure: String? = null
                            checked.forEach { template ->
                                runCatching { OdkCentralClient.deployAndAssign(context, profile, session.token, template) }
                                    .onSuccess { successes += 1 }
                                    .onFailure { if (failure == null) failure = "${template.displayName}: ${it.message}" }
                            }
                            busyTemplateId = null
                            deploymentRevision += 1
                            status = failure ?: "Synced $successes checked form${if (successes == 1) "" else "s"}."
                        }
                    }.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        if (profile.isConfigured && session != null) {
            Text(
                "Testing on ${profile.projectName} · ${profile.appUserName}. Tick a form to deploy/update, publish and grant access. Untick to revoke only that App User's access.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                "Central testing is off. Configure Settings → ODK Central to deploy forms directly to a test App User.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            singleLine = true,
            placeholder = { Text("Search forms") }
        )

        if (templates.isEmpty()) {
            Text(
                "No ODK design templates are packaged in this build.",
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "The UI expects the generated module template index at $INDEX_ASSET.",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        Text(
            if (filtered.size == 1) "1 form" else "${filtered.size} forms",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (filtered.isEmpty()) {
            Text(
                "No forms match this search.",
                modifier = Modifier.padding(top = 18.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            grouped.forEach { (moduleName, moduleTemplates) ->
                var expanded by rememberSaveable("odk-template-module:$moduleName") { mutableStateOf(false) }
                val show = expanded || trimmed.isNotBlank()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(moduleName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${moduleTemplates.size} form${if (moduleTemplates.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(if (show) "−" else "+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                if (show) {
                    moduleTemplates.forEach { template ->
                        val deployment = remember(deploymentRevision, profile, template.id) { profile.deployment(context, template) }
                        val checked = profile.isConfigured && deployment?.assignedAppUserId == profile.appUserId
                        val localDigest = remember(template.id) { runCatching { OdkCentralClient.localDigest(context, template) }.getOrDefault("") }
                        val updateAvailable = deployment != null && localDigest.isNotBlank() && deployment.localSha256 != localDigest
                        val rowBusy = busyTemplateId == template.id || busyTemplateId == "__all__"

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp, bottom = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                enabled = profile.isConfigured && session != null && !rowBusy,
                                onCheckedChange = { enable ->
                                    if (session == null || !profile.isConfigured) {
                                        status = "Configure Settings → ODK Central first."
                                    } else {
                                        scope.launch {
                                            busyTemplateId = template.id
                                            status = if (enable) "Deploying ${template.displayName}…" else "Revoking ${profile.appUserName} access…"
                                            runCatching {
                                                if (enable) {
                                                    OdkCentralClient.deployAndAssign(context, profile, session.token, template)
                                                } else {
                                                    OdkCentralClient.revokeTesterAccess(context, profile, session.token, template)
                                                }
                                            }.onSuccess {
                                                deploymentRevision += 1
                                                status = if (enable) {
                                                    "${template.displayName} is published and available to ${profile.appUserName}."
                                                } else {
                                                    "${profile.appUserName} no longer has access to ${template.displayName}."
                                                }
                                            }.onFailure {
                                                status = "Central sync failed: ${it.message ?: "request error"}"
                                            }
                                            busyTemplateId = null
                                        }
                                    }
                                }
                            )
                            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                                Text(template.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                val subtitle = template.description.ifBlank {
                                    template.capabilityIds.joinToString(", ").ifBlank { template.sourceFileName }
                                }
                                Text(
                                    subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    when {
                                        rowBusy -> "Syncing…"
                                        checked && updateAvailable -> "On Central · update available"
                                        checked -> "On Central · ${profile.appUserName} has access"
                                        deployment?.assignedAppUserId != null -> "On Central · assigned to another MethodMesh test user"
                                        deployment != null -> "On Central · tester access off"
                                        else -> "Local design template"
                                    },
                                    modifier = Modifier.padding(top = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when {
                                        checked && !updateAvailable -> MaterialTheme.colorScheme.primary
                                        updateAvailable -> MaterialTheme.colorScheme.tertiary
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                "Share",
                                modifier = Modifier.clickable {
                                    status = OdkTemplateCatalog.share(context, template).fold(
                                        onSuccess = { null },
                                        onFailure = { "Share failed: ${it.message ?: "provider error"}" }
                                    )
                                }.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(14.dp))
                            Text(
                                "Save",
                                modifier = Modifier.clickable {
                                    pendingSave = template
                                    saveLauncher.launch(template.sourceFileName)
                                }.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (deployment != null && session != null) {
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    "Remove",
                                    modifier = Modifier.clickable(enabled = !rowBusy) {
                                        scope.launch {
                                            busyTemplateId = template.id
                                            val count = runCatching {
                                                OdkCentralClient.remoteSubmissionCount(context, profile, session.token, template)
                                            }.getOrDefault(0)
                                            busyTemplateId = null
                                            pendingCentralRemoval = PendingCentralRemoval(template, count)
                                        }
                                    }.padding(vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    }
                }
            }
        }
        status?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.contains("failed", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }

    pendingCentralRemoval?.let { pending ->
        val currentSession = session
        MethodMeshDestructiveConfirmation(
            title = "Remove from ODK Central?",
            objectName = pending.template.displayName,
            consequence = buildString {
                append("The remote form will be moved to Central Trash. The local MethodMesh XLSForm template is kept.")
                if (pending.submissions > 0) append("\n\nThis form currently has ${pending.submissions} submission${if (pending.submissions == 1) "" else "s"}.")
            },
            confirmLabel = "Remove",
            onDismiss = { pendingCentralRemoval = null },
            onConfirm = {
                pendingCentralRemoval = null
                if (currentSession != null) {
                    scope.launch {
                        busyTemplateId = pending.template.id
                        runCatching { OdkCentralClient.removeRemoteForm(context, profile, currentSession.token, pending.template) }
                            .onSuccess {
                                deploymentRevision += 1
                                status = "Removed ${pending.template.displayName} from ODK Central."
                            }
                            .onFailure { status = "Central removal failed: ${it.message ?: "request error"}" }
                        busyTemplateId = null
                    }
                }
            }
        )
    }
}

private fun OdkCentralProfile.deployment(context: Context, template: OdkTemplateDescriptor) =
    projectId?.let { project ->
        OdkCentralDeploymentStore.get(
            context,
            OdkCentralConnectionRepository.normalizeServerUrl(serverUrl),
            project,
            template.id
        )
    }
