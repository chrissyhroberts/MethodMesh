package com.example.methodmesh.ui.odk

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import com.example.methodmesh.modules.MethodMeshModuleRegistry
import com.example.methodmesh.ui.components.MethodMeshDestructiveConfirmation
import com.example.methodmesh.ui.odkcentral.OdkCentralApiException
import com.example.methodmesh.ui.odkcentral.OdkCentralClient
import com.example.methodmesh.ui.odkcentral.OdkCentralConnectionRepository
import com.example.methodmesh.ui.odkcentral.OdkCentralDeploymentStore
import com.example.methodmesh.ui.odkcentral.OdkCentralProfile
import com.example.methodmesh.ui.odkcentral.OdkCentralSessionStore
import com.example.methodmesh.ui.kobo.KoboApiException
import com.example.methodmesh.ui.kobo.KoboClient
import com.example.methodmesh.ui.kobo.KoboConnectionRepository
import com.example.methodmesh.ui.kobo.KoboDeploymentStore
import com.example.methodmesh.ui.kobo.KoboProfile
import com.example.methodmesh.ui.kobo.KoboSessionStore
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
    val formVersion: String,
    val sourceRelativePath: String,
    val capabilityIds: List<String>,
    val tags: List<String>,
    val validationIssues: List<OdkValidationIssue>,
    val authoritativeValidated: Boolean,
    val validationEngine: String
)

data class OdkTemplateCatalogStatus(
    val templates: List<OdkTemplateDescriptor>,
    val indexPresent: Boolean,
    val declaredTemplateCount: Int,
    val validationSummary: OdkValidationSummary = OdkValidationSummary(
        engine = "MethodMesh XLSForm lint",
        authoritativeValidationAvailable = false,
        authoritativeValidatedForms = 0,
        cleanForms = 0,
        formsNeedingRevision = 0,
        errorCount = 0,
        warningCount = 0,
        styleCount = 0
    ),
    val error: String? = null
)

object OdkTemplateCatalog {
    fun load(context: Context): List<OdkTemplateDescriptor> = loadWithStatus(context).templates

    fun loadWithStatus(context: Context): OdkTemplateCatalogStatus {
        val moduleNames = runCatching {
            MethodMeshModuleRegistry.all().associate { it.moduleId to it.displayName }
        }.getOrDefault(emptyMap())

        val json = try {
            context.assets.open(INDEX_ASSET).bufferedReader().use { it.readText() }
        } catch (error: Exception) {
            return OdkTemplateCatalogStatus(
                templates = emptyList(),
                indexPresent = false,
                declaredTemplateCount = 0,
                error = error.message
            )
        }

        return runCatching {
            val root = JSONObject(json)
            val templates = root.optJSONArray("templates") ?: JSONArray()
            val parsed = buildList {
                for (i in 0 until templates.length()) {
                    val item = templates.optJSONObject(i) ?: continue
                    val assetPath = item.optString("assetPath")
                    if (assetPath.isBlank()) continue
                    val moduleId = item.optString("moduleId").ifBlank { "other" }
                    add(
                        OdkTemplateDescriptor(
                            id = item.optString("id").ifBlank { assetPath },
                            moduleId = moduleId,
                            moduleName = moduleNames[moduleId]
                                ?: item.optString("moduleName").ifBlank { humanizeModuleId(moduleId) },
                            displayName = item.optString("displayName").ifBlank {
                                displayNameFromFilename(item.optString("sourceFileName").ifBlank { assetPath.substringAfterLast('/') })
                            },
                            description = item.optString("description"),
                            assetPath = assetPath,
                            sourceFileName = item.optString("sourceFileName").ifBlank { assetPath.substringAfterLast('/') },
                            centralFormId = item.optString("centralFormId"),
                            formVersion = item.optString("formVersion"),
                            sourceRelativePath = item.optString("sourceRelativePath"),
                            capabilityIds = item.optJSONArray("capabilityIds").stringList(),
                            tags = item.optJSONArray("tags").stringList(),
                            validationIssues = item.optJSONObject("validation")?.optJSONArray("issues").validationIssueList(),
                            authoritativeValidated = item.optJSONObject("validation")?.optBoolean("authoritativeAvailable", false) == true,
                            validationEngine = item.optJSONObject("validation")?.optString("engine").orEmpty()
                        )
                    )
                }
            }.sortedWith(compareBy<OdkTemplateDescriptor> { it.moduleName.lowercase() }.thenBy { it.displayName.lowercase() })
            val validation = root.optJSONObject("validationSummary")
            val severity = validation?.optJSONObject("severityCounts")
            OdkTemplateCatalogStatus(
                templates = parsed,
                indexPresent = true,
                declaredTemplateCount = root.optInt("templateCount", parsed.size),
                validationSummary = OdkValidationSummary(
                    engine = validation?.optString("engine").orEmpty().ifBlank { "MethodMesh XLSForm lint" },
                    authoritativeValidationAvailable = validation?.optBoolean("authoritativeValidationAvailable", false) == true,
                    authoritativeValidatedForms = validation?.optInt("authoritativeValidatedForms", 0) ?: 0,
                    cleanForms = validation?.optInt("cleanForms", parsed.count { it.validationIssues.isEmpty() }) ?: parsed.count { it.validationIssues.isEmpty() },
                    formsNeedingRevision = validation?.optInt("formsNeedingRevision", parsed.count { it.validationIssues.isNotEmpty() }) ?: parsed.count { it.validationIssues.isNotEmpty() },
                    errorCount = severity?.optInt("error", parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Error } })
                        ?: parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Error } },
                    warningCount = severity?.optInt("warning", parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Warning } })
                        ?: parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Warning } },
                    styleCount = severity?.optInt("style", parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Style } })
                        ?: parsed.sumOf { t -> t.validationIssues.count { it.severity == OdkValidationSeverity.Style } }
                ),
                error = null
            )
        }.getOrElse { error ->
            OdkTemplateCatalogStatus(
                templates = emptyList(),
                indexPresent = true,
                declaredTemplateCount = 0,
                error = error.message
            )
        }
    }

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
                template.formVersion,
                template.sourceRelativePath,
                template.capabilityIds.joinToString(" "),
                template.tags.joinToString(" "),
                template.validationIssues.joinToString(" ") { "${it.code} ${it.message} ${it.location}" },
                "ODK Kobo KoboCollect XLSForm form design template collection validation errors naming"
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

    private fun humanizeModuleId(moduleId: String): String = moduleId
        .replace('_', ' ')
        .replace('-', ' ')
        .trim()
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { c -> c.uppercase() } }
        .ifBlank { "Other" }

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

    private fun JSONArray?.validationIssueList(): List<OdkValidationIssue> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val item = optJSONObject(i) ?: continue
                add(
                    OdkValidationIssue(
                        severity = OdkValidationSeverity.fromWire(item.optString("severity")),
                        code = item.optString("code").ifBlank { "XLSFORM_FINDING" },
                        message = item.optString("message"),
                        location = item.optString("location"),
                        suggestion = item.optString("suggestion"),
                        source = item.optString("source"),
                        details = item.optString("details")
                    )
                )
            }
        }
    }
}

private data class PendingCentralRemoval(
    val template: OdkTemplateDescriptor,
    val submissions: Int
)

private data class PendingKoboRemoval(
    val template: OdkTemplateDescriptor,
    val submissions: Int
)

private data class ProviderSyncDiagnostic(
    val provider: String,
    val summary: String,
    val httpStatus: Int? = null,
    val code: String? = null,
    val details: String? = null,
    val request: String? = null,
    val rawResponse: String? = null,
    val guidance: String? = null,
    val repairAvailable: Boolean = false
) {
    fun report(): String = buildString {
        append(provider).append(" sync diagnostic\n")
        httpStatus?.let { append("HTTP: ").append(it).append('\n') }
        code?.takeIf(String::isNotBlank)?.let { append("Code: ").append(it).append('\n') }
        request?.takeIf(String::isNotBlank)?.let { append("Request: ").append(it).append('\n') }
        append("Message: ").append(summary)
        guidance?.takeIf(String::isNotBlank)?.let { append("\n\nHow to resolve\n").append(it) }
        details?.takeIf(String::isNotBlank)?.let { append("\n\nDetails\n").append(it) }
        rawResponse?.takeIf(String::isNotBlank)?.let {
            if (details.isNullOrBlank() || !it.contains(details)) append("\n\nServer response\n").append(it)
        }
    }
}

private fun providerDiagnostic(provider: String, error: Throwable): ProviderSyncDiagnostic = when (error) {
    is OdkCentralApiException -> ProviderSyncDiagnostic(
        provider = provider,
        summary = error.message ?: "ODK Central request failed.",
        httpStatus = error.statusCode,
        code = error.apiCode,
        details = error.details,
        request = listOfNotNull(error.requestMethod, error.requestPath).joinToString(" ").takeIf(String::isNotBlank),
        rawResponse = error.rawResponse
    )
    is KoboApiException -> {
        val duplicateName = error.errorType.equals("DuplicateNameException", ignoreCase = true)
        val plan = error.compatibilityPlan
        ProviderSyncDiagnostic(
            provider = provider,
            summary = if (duplicateName) {
                "Kobo requires globally unique field names."
            } else {
                error.message ?: "KoboToolbox request failed."
            },
            httpStatus = error.statusCode.takeIf { !error.requestPath.isNullOrBlank() },
            code = error.errorType,
            details = error.details,
            request = listOfNotNull(error.requestMethod, error.requestPath).joinToString(" ").takeIf(String::isNotBlank),
            rawResponse = error.rawResponse,
            guidance = when {
                duplicateName && plan != null -> plan.guidance()
                error.errorType == "KoboCompatibilityRepairUnsafe" && plan != null -> plan.guidance()
                else -> null
            },
            repairAvailable = duplicateName && plan?.canAutoRepair == true
        )
    }
    else -> ProviderSyncDiagnostic(provider = provider, summary = error.message ?: "Unknown request error.")
}

@Composable
fun OdkTemplateLibrary(initialQuery: String = "") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val catalog = remember { OdkTemplateCatalog.loadWithStatus(context) }
    val templates = catalog.templates
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    var pendingSave by remember { mutableStateOf<OdkTemplateDescriptor?>(null) }
    var pendingCentralRemoval by remember { mutableStateOf<PendingCentralRemoval?>(null) }
    var pendingKoboRemoval by remember { mutableStateOf<PendingKoboRemoval?>(null) }
    var status by rememberSaveable { mutableStateOf<String?>(null) }
    var diagnostics by remember { mutableStateOf<Map<String, ProviderSyncDiagnostic>>(emptyMap()) }
    var busyKey by rememberSaveable { mutableStateOf<String?>(null) }
    var deploymentRevision by remember { mutableIntStateOf(0) }
    var showValidation by rememberSaveable { mutableStateOf(false) }
    var validationFocusTemplateId by rememberSaveable { mutableStateOf<String?>(null) }
    var validationFocusFilter by rememberSaveable { mutableStateOf<String?>(null) }

    val centralProfile = remember(deploymentRevision) { OdkCentralConnectionRepository.load(context) }
    val centralSession = remember(deploymentRevision) { OdkCentralSessionStore.load(context) }
    val koboProfile = remember(deploymentRevision) { KoboConnectionRepository.load(context) }
    val koboSession = remember(deploymentRevision) { KoboSessionStore.load(context) }

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
                    "Module-owned XLSForm example/test templates. Remote copies are disposable test deployments, never production MethodMesh forms.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (centralProfile.isConfigured && centralSession != null) {
                Text(
                    "Sync ODK",
                    modifier = Modifier.clickable(enabled = busyKey == null) {
                        scope.launch {
                            val checked = templates.filter { template ->
                                centralProfile.deployment(context, template)?.assignedAppUserId == centralProfile.appUserId
                            }
                            if (checked.isEmpty()) {
                                status = "No forms are enabled for ${centralProfile.appUserName}."
                                return@launch
                            }
                            busyKey = "central:__all__"
                            var successes = 0
                            var failures = 0
                            var blocked = 0
                            checked.forEach { template ->
                                val key = "central:${template.id}"
                                if (template.validationIssues.any { it.severity == OdkValidationSeverity.Error }) {
                                    blocked += 1
                                    diagnostics = diagnostics - key
                                } else {
                                    runCatching { OdkCentralClient.deployAndAssign(context, centralProfile, centralSession.token, template) }
                                        .onSuccess {
                                            successes += 1
                                            diagnostics = diagnostics - key
                                        }
                                        .onFailure { error ->
                                            failures += 1
                                            diagnostics = diagnostics + (key to providerDiagnostic("ODK Central", error))
                                        }
                                }
                            }
                            busyKey = null
                            deploymentRevision += 1
                            status = when {
                                failures > 0 -> "ODK sync: $successes succeeded, $failures provider failure${if (failures == 1) "" else "s"}, $blocked blocked by validation."
                                blocked > 0 -> "Synced $successes ODK form${if (successes == 1) "" else "s"}; $blocked blocked by validation errors. Advisory warnings were ignored."
                                else -> "Synced $successes ODK form${if (successes == 1) "" else "s"}; advisory warnings were ignored."
                            }
                        }
                    }.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (koboProfile.isConfigured && koboSession != null) {
                if (centralProfile.isConfigured && centralSession != null) Spacer(Modifier.width(14.dp))
                Text(
                    "Sync Kobo",
                    modifier = Modifier.clickable(enabled = busyKey == null) {
                        scope.launch {
                            val checked = templates.filter { template -> koboProfile.deployment(context, template)?.active == true }
                            if (checked.isEmpty()) {
                                status = "No forms are enabled for KoboCollect."
                                return@launch
                            }
                            busyKey = "kobo:__all__"
                            var successes = 0
                            var failures = 0
                            var blocked = 0
                            checked.forEach { template ->
                                val key = "kobo:${template.id}"
                                if (template.validationIssues.any { it.severity == OdkValidationSeverity.Error }) {
                                    blocked += 1
                                    diagnostics = diagnostics - key
                                } else {
                                    runCatching { KoboClient.deploy(context, koboProfile, koboSession.token, template) }
                                        .onSuccess {
                                            successes += 1
                                            diagnostics = diagnostics - key
                                        }
                                        .onFailure { error ->
                                            failures += 1
                                            diagnostics = diagnostics + (key to providerDiagnostic("KoboToolbox", error))
                                        }
                                }
                            }
                            busyKey = null
                            deploymentRevision += 1
                            status = when {
                                failures > 0 -> "Kobo sync: $successes succeeded, $failures provider failure${if (failures == 1) "" else "s"}, $blocked blocked by validation."
                                blocked > 0 -> "Synced $successes Kobo form${if (successes == 1) "" else "s"}; $blocked blocked by validation errors. Advisory warnings were ignored."
                                else -> "Synced $successes Kobo form${if (successes == 1) "" else "s"}; advisory warnings were ignored."
                            }
                        }
                    }.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        when {
            centralProfile.isConfigured && centralSession != null && koboProfile.isConfigured && koboSession != null -> Text(
                "Rapid testing is connected to ODK Central and KoboToolbox. Warnings are advisory; only validation errors block example deployment.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            centralProfile.isConfigured && centralSession != null -> Text(
                "ODK Central is connected. Configure Settings → KoboToolbox to test the same forms in KoboCollect too.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            koboProfile.isConfigured && koboSession != null -> Text(
                "KoboToolbox is connected. Configure Settings → ODK Central to test the same forms against Central too.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> Text(
                "Configure ODK Central and/or KoboToolbox in Settings to deploy these local templates for rapid testing.",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (templates.isNotEmpty()) {
            val validation = catalog.validationSummary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (showValidation && validationFocusTemplateId == null) {
                            showValidation = false
                        } else {
                            validationFocusTemplateId = null
                            validationFocusFilter = null
                            showValidation = true
                        }
                    }
                    .padding(top = 12.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Validate XLSForms", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (validation.formsNeedingRevision == 0) {
                            "${templates.size} forms · no packaged validation issues"
                        } else {
                            "${validation.formsNeedingRevision} findings · ${validation.errorCount} blocking errors · ${validation.warningCount} advisory warnings · ${validation.styleCount} naming/style"
                        },
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (validation.errorCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    if (showValidation) "Hide" else "Review",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (showValidation) {
                OdkFormValidationPanel(
                    templates = templates,
                    summary = validation,
                    focusTemplateId = validationFocusTemplateId,
                    initialFilter = validationFocusFilter,
                    onClearFocus = {
                        validationFocusTemplateId = null
                        validationFocusFilter = null
                    },
                    onClose = { showValidation = false }
                )
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            singleLine = true,
            placeholder = { Text("Search forms") }
        )

        if (templates.isEmpty()) {
            if (!catalog.indexPresent) {
                Text(
                    "ODK form catalogue is not packaged in this build.",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "The module forms are source files under modules/<module>/docs and Android does not package them automatically. Enable the MethodMesh XLSForm build hook, then rebuild the app. Expected asset: $INDEX_ASSET.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    if (catalog.error != null) "ODK form catalogue could not be read." else "No module XLSForms were found when this build was made.",
                    modifier = Modifier.padding(top = 18.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    catalog.error ?: "The catalogue exists but declares ${catalog.declaredTemplateCount} form(s). The build generator scans module docs recursively for canonical example_odk*.xlsx files and other workbooks that structurally look like XLSForms.",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                val moduleErrorCount = moduleTemplates.count { template ->
                    diagnostics.containsKey("central:${template.id}") || diagnostics.containsKey("kobo:${template.id}")
                }
                val moduleRevisionCount = moduleTemplates.count { it.validationIssues.isNotEmpty() }
                val show = expanded || trimmed.isNotBlank() || moduleErrorCount > 0
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
                            buildString {
                                append(moduleTemplates.size).append(" form").append(if (moduleTemplates.size == 1) "" else "s")
                                if (moduleErrorCount > 0) append(" · ").append(moduleErrorCount).append(" sync error").append(if (moduleErrorCount == 1) "" else "s")
                                if (moduleRevisionCount > 0) append(" · ").append(moduleRevisionCount).append(" with validation findings")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (moduleErrorCount > 0 || moduleTemplates.any { t -> t.validationIssues.any { it.severity == OdkValidationSeverity.Error } }) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(if (show) "−" else "+", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                if (show) {
                    moduleTemplates.forEach { template ->
                        val centralDeployment = remember(deploymentRevision, centralProfile, template.id) {
                            centralProfile.deployment(context, template)
                        }
                        val koboDeployment = remember(deploymentRevision, koboProfile, template.id) {
                            koboProfile.deployment(context, template)
                        }
                        val centralChecked = centralProfile.isConfigured && centralDeployment?.assignedAppUserId == centralProfile.appUserId
                        val koboChecked = koboProfile.isConfigured && koboDeployment?.active == true
                        val localDigest = remember(template.id) {
                            runCatching { OdkCentralClient.localDigest(context, template) }.getOrDefault("")
                        }
                        val centralUpdate = centralDeployment != null && localDigest.isNotBlank() && centralDeployment.localSha256 != localDigest
                        val koboUpdate = koboDeployment != null && localDigest.isNotBlank() && koboDeployment.localSha256 != localDigest
                        val centralBusy = busyKey == "central:${template.id}" || busyKey == "central:__all__"
                        val koboBusy = busyKey == "kobo:${template.id}" || busyKey == "kobo:__all__"

                        Column(Modifier.fillMaxWidth().padding(start = 4.dp, top = 11.dp, bottom = 11.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(template.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    val subtitle = template.description.ifBlank {
                                        template.capabilityIds.joinToString(", ").ifBlank { template.sourceFileName }
                                    }
                                    Text(
                                        subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (template.validationIssues.isNotEmpty()) {
                                        val errors = template.validationIssues.count { it.severity == OdkValidationSeverity.Error }
                                        val warnings = template.validationIssues.count { it.severity == OdkValidationSeverity.Warning }
                                        Text(
                                            buildString {
                                                if (errors > 0) append(errors).append(" validation error").append(if (errors == 1) "" else "s")
                                                if (warnings > 0) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append(warnings).append(" warning").append(if (warnings == 1) "" else "s")
                                                }
                                                val styleFindings = template.validationIssues.count { it.severity == OdkValidationSeverity.Style }
                                                if (styleFindings > 0) {
                                                    if (isNotEmpty()) append(" · ")
                                                    append(styleFindings).append(" naming/style")
                                                }
                                            },
                                            modifier = Modifier.padding(top = 2.dp).clickable {
                                                validationFocusTemplateId = template.id
                                                validationFocusFilter = when {
                                                    errors > 0 -> "errors"
                                                    warnings > 0 -> "warnings"
                                                    else -> "naming"
                                                }
                                                showValidation = true
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (errors > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                        )
                                    }
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
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ProviderToggle(
                                    label = "ODK",
                                    checked = centralChecked,
                                    enabled = centralProfile.isConfigured && centralSession != null && !centralBusy && busyKey == null,
                                    statusText = when {
                                        centralBusy -> "syncing"
                                        centralChecked && centralUpdate -> "update"
                                        centralChecked -> centralProfile.appUserName.ifBlank { "on" }
                                        centralDeployment != null -> "off"
                                        else -> "local"
                                    },
                                    attention = centralUpdate,
                                    onCheckedChange = { enable ->
                                        val blockingErrors = template.validationIssues.count { it.severity == OdkValidationSeverity.Error }
                                        if (enable && blockingErrors > 0) {
                                            validationFocusTemplateId = template.id
                                            validationFocusFilter = "errors"
                                            showValidation = true
                                            status = "${template.displayName} has $blockingErrors blocking validation error${if (blockingErrors == 1) "" else "s"}. Advisory warnings do not block deployment."
                                        } else if (centralSession == null || !centralProfile.isConfigured) {
                                            status = "Configure Settings → ODK Central first."
                                        } else {
                                            scope.launch {
                                                busyKey = "central:${template.id}"
                                                status = if (enable) "Deploying ${template.displayName} to ODK Central…" else "Revoking ODK test access…"
                                                runCatching {
                                                    if (enable) OdkCentralClient.deployAndAssign(context, centralProfile, centralSession.token, template)
                                                    else OdkCentralClient.revokeTesterAccess(context, centralProfile, centralSession.token, template)
                                                }.onSuccess {
                                                    diagnostics = diagnostics - "central:${template.id}"
                                                    deploymentRevision += 1
                                                    status = if (enable) {
                                                        "Test copy of ${template.displayName} is available to ${centralProfile.appUserName} in ODK Collect."
                                                    } else {
                                                        "${template.displayName} is no longer available to ${centralProfile.appUserName}."
                                                    }
                                                }.onFailure { error ->
                                                    diagnostics = diagnostics + ("central:${template.id}" to providerDiagnostic("ODK Central", error))
                                                    status = "ODK Central sync failed — see the server report under ${template.displayName}."
                                                }
                                                busyKey = null
                                            }
                                        }
                                    }
                                )
                                Spacer(Modifier.width(20.dp))
                                ProviderToggle(
                                    label = "Kobo",
                                    checked = koboChecked,
                                    enabled = koboProfile.isConfigured && koboSession != null && !koboBusy && busyKey == null,
                                    statusText = when {
                                        koboBusy -> "syncing"
                                        koboChecked && koboUpdate -> "update"
                                        koboChecked && koboDeployment?.namespacedCompatibilityCopy == true -> "KoboCollect · compatible copy"
                                        koboChecked -> "KoboCollect"
                                        koboDeployment != null -> "inactive"
                                        else -> "local"
                                    },
                                    attention = koboUpdate,
                                    onCheckedChange = { enable ->
                                        val blockingErrors = template.validationIssues.count { it.severity == OdkValidationSeverity.Error }
                                        if (enable && blockingErrors > 0) {
                                            validationFocusTemplateId = template.id
                                            validationFocusFilter = "errors"
                                            showValidation = true
                                            status = "${template.displayName} has $blockingErrors blocking validation error${if (blockingErrors == 1) "" else "s"}. Advisory warnings do not block deployment."
                                        } else if (koboSession == null || !koboProfile.isConfigured) {
                                            status = "Configure Settings → KoboToolbox first."
                                        } else {
                                            scope.launch {
                                                busyKey = "kobo:${template.id}"
                                                status = if (enable) "Deploying ${template.displayName} to KoboToolbox…" else "Removing ${template.displayName} from KoboCollect…"
                                                runCatching {
                                                    if (enable) KoboClient.deploy(context, koboProfile, koboSession.token, template)
                                                    else KoboClient.deactivate(context, koboProfile, koboSession.token, template)
                                                }.onSuccess {
                                                    diagnostics = diagnostics - "kobo:${template.id}"
                                                    deploymentRevision += 1
                                                    status = if (enable) {
                                                        "Test copy of ${template.displayName} is available in KoboCollect."
                                                    } else {
                                                        "${template.displayName} is inactive in KoboCollect; the remote project and submissions are preserved."
                                                    }
                                                }.onFailure { error ->
                                                    diagnostics = diagnostics + ("kobo:${template.id}" to providerDiagnostic("KoboToolbox", error))
                                                    status = "Kobo sync failed — see the server report under ${template.displayName}."
                                                }
                                                busyKey = null
                                            }
                                        }
                                    }
                                )
                            }

                            diagnostics["central:${template.id}"]?.let { diagnostic ->
                                ProviderDiagnosticBlock(
                                    diagnostic = diagnostic,
                                    onDismiss = { diagnostics = diagnostics - "central:${template.id}" }
                                )
                            }
                            diagnostics["kobo:${template.id}"]?.let { diagnostic ->
                                ProviderDiagnosticBlock(
                                    diagnostic = diagnostic,
                                    onDismiss = { diagnostics = diagnostics - "kobo:${template.id}" },
                                    onRepair = if (diagnostic.repairAvailable && koboSession != null && busyKey == null) {
                                        {
                                            scope.launch {
                                                val key = "kobo:${template.id}"
                                                busyKey = key
                                                runCatching {
                                                    KoboClient.deployCompatibleCopy(context, koboProfile, koboSession.token, template)
                                                }.onSuccess {
                                                    diagnostics = diagnostics - key
                                                    deploymentRevision += 1
                                                    status = "Created and deployed a Kobo-compatible test copy of ${template.displayName}. The canonical MethodMesh XLSForm was not changed; future Kobo updates will keep using the compatible copy."
                                                }.onFailure { error ->
                                                    diagnostics = diagnostics + (key to providerDiagnostic("KoboToolbox", error))
                                                    status = "Kobo compatibility retry failed — see the focused report under ${template.displayName}."
                                                }
                                                busyKey = null
                                            }
                                        }
                                    } else null
                                )
                            }

                            if ((centralDeployment != null && centralSession != null) || (koboDeployment != null && koboSession != null)) {
                                Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (centralDeployment != null && centralSession != null) {
                                        Text(
                                            "Remove from Central",
                                            modifier = Modifier.clickable(enabled = busyKey == null) {
                                                scope.launch {
                                                    busyKey = "central:${template.id}"
                                                    val count = runCatching {
                                                        OdkCentralClient.remoteSubmissionCount(context, centralProfile, centralSession.token, template)
                                                    }.getOrDefault(0)
                                                    busyKey = null
                                                    pendingCentralRemoval = PendingCentralRemoval(template, count)
                                                }
                                            }.padding(vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    if (centralDeployment != null && centralSession != null && koboDeployment != null && koboSession != null) {
                                        Spacer(Modifier.width(18.dp))
                                    }
                                    if (koboDeployment != null && koboSession != null) {
                                        Text(
                                            "Remove from Kobo",
                                            modifier = Modifier.clickable(enabled = busyKey == null) {
                                                scope.launch {
                                                    busyKey = "kobo:${template.id}"
                                                    val count = runCatching {
                                                        KoboClient.remoteSubmissionCount(context, koboProfile, koboSession.token, template)
                                                    }.getOrDefault(0)
                                                    busyKey = null
                                                    pendingKoboRemoval = PendingKoboRemoval(template, count)
                                                }
                                            }.padding(vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
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
        val currentSession = centralSession
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
                        busyKey = "central:${pending.template.id}"
                        runCatching { OdkCentralClient.removeRemoteForm(context, centralProfile, currentSession.token, pending.template) }
                            .onSuccess {
                                deploymentRevision += 1
                                status = "Removed ${pending.template.displayName} from ODK Central."
                            }
                            .onFailure { status = "Central removal failed: ${it.message ?: "request error"}" }
                        busyKey = null
                    }
                }
            }
        )
    }

    pendingKoboRemoval?.let { pending ->
        val currentSession = koboSession
        MethodMeshDestructiveConfirmation(
            title = "Remove from KoboToolbox?",
            objectName = pending.template.displayName,
            consequence = buildString {
                append("The remote KoboToolbox project will be deleted. The local MethodMesh XLSForm template is kept. Untick Kobo instead if you only want to remove it from KoboCollect while preserving the remote project.")
                if (pending.submissions > 0) append("\n\nThis project currently reports ${pending.submissions} submission${if (pending.submissions == 1) "" else "s"}.")
            },
            confirmLabel = "Remove",
            onDismiss = { pendingKoboRemoval = null },
            onConfirm = {
                pendingKoboRemoval = null
                if (currentSession != null) {
                    scope.launch {
                        busyKey = "kobo:${pending.template.id}"
                        runCatching { KoboClient.removeRemote(context, koboProfile, currentSession.token, pending.template) }
                            .onSuccess {
                                deploymentRevision += 1
                                status = "Removed ${pending.template.displayName} from KoboToolbox."
                            }
                            .onFailure { status = "Kobo removal failed: ${it.message ?: "request error"}" }
                        busyKey = null
                    }
                }
            }
        )
    }
}

@Composable
private fun ProviderDiagnosticBlock(
    diagnostic: ProviderSyncDiagnostic,
    onDismiss: () -> Unit,
    onRepair: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var expanded by rememberSaveable(diagnostic.provider, diagnostic.summary, diagnostic.httpStatus) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, start = 12.dp, end = 4.dp, bottom = 4.dp)) {
        Text(
            buildString {
                append(diagnostic.provider).append(if (diagnostic.repairAvailable) " · compatibility issue" else " · upload failed")
                diagnostic.httpStatus?.let { append(" · HTTP ").append(it) }
                diagnostic.code?.takeIf(String::isNotBlank)?.let { append(" · ").append(it) }
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            diagnostic.summary,
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        diagnostic.guidance?.takeIf(String::isNotBlank)?.let { guidance ->
            Text(
                guidance,
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (onRepair != null) {
            Text(
                "Create compatible copy & retry",
                modifier = Modifier.clickable(onClick = onRepair).padding(top = 7.dp, bottom = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Creates a temporary Kobo-only XLSForm with namespaced result fields. The module-owned XLSForm stays unchanged.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (expanded) "Hide details" else "Why did this fail?",
                modifier = Modifier.clickable {
                    if (expanded) {
                        expanded = false
                    } else {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText(
                                "MethodMesh ${diagnostic.provider} diagnostic",
                                diagnostic.report()
                            )
                        )
                        Toast.makeText(context, "Failure report copied", Toast.LENGTH_SHORT).show()
                        expanded = true
                    }
                }.padding(vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Copy report",
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh ${diagnostic.provider} diagnostic", diagnostic.report()))
                    Toast.makeText(context, "Failure report copied", Toast.LENGTH_SHORT).show()
                }.padding(vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(16.dp))
            Text(
                "Dismiss",
                modifier = Modifier.clickable(onClick = onDismiss).padding(vertical = 5.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            diagnostic.request?.takeIf(String::isNotBlank)?.let { request ->
                Text(
                    request,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            diagnostic.details?.takeIf(String::isNotBlank)?.let { details ->
                Text(
                    details,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: diagnostic.rawResponse?.takeIf(String::isNotBlank)?.let { raw ->
                Text(
                    raw,
                    modifier = Modifier.padding(top = 5.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ProviderToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    statusText: String,
    attention: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
        Column(Modifier.padding(start = 2.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (attention) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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

private fun KoboProfile.deployment(context: Context, template: OdkTemplateDescriptor) =
    KoboDeploymentStore.get(
        context,
        KoboConnectionRepository.normalizeServerUrl(serverUrl),
        template.id
    )
