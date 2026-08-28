package com.example.methodmesh.modules.appinspector

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject
import java.util.Locale

private data class InstalledApp(val packageName: String, val label: String)

object AppInspectorCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100AppInspectorMethod.ID
    override val title = "Inspect Android app"
    override val description = "Inspect public app interfaces and test an authorised intent."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val packageManager = androidContext.packageManager
        val apps = remember { launchableApps(packageManager) }
        val supplied = context.action.settings
        var selectedPackage by rememberSaveable { mutableStateOf(supplied["package_name"].orEmpty()) }
        var selectedComponent by rememberSaveable { mutableStateOf("") }
        var action by rememberSaveable { mutableStateOf(supplied["test_action"].orEmpty().ifBlank { Intent.ACTION_MAIN }) }
        var uriText by rememberSaveable { mutableStateOf(supplied["test_uri"].orEmpty()) }
        var extrasText by rememberSaveable { mutableStateOf("") }
        var pickerOpen by remember { mutableStateOf(false) }
        var componentPickerOpen by remember { mutableStateOf(false) }
        var components by remember { mutableStateOf(emptyList<String>()) }
        var activityComponents by remember { mutableStateOf(emptyList<String>()) }
        var inspection by remember { mutableStateOf("") }
        var testResult by remember { mutableStateOf("") }
        var savedStatus by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        LaunchedEffect(selectedPackage, selectedComponent, action, uriText, extrasText) {
            context.onSettingsChanged(
                mapOf(
                    "package_name" to selectedPackage,
                    "component_name" to selectedComponent,
                    "test_action" to action,
                    "test_uri" to uriText,
                    "extras" to extrasText
                )
            )
        }

        fun integrationDefinition(): String = JSONObject().apply {
            put("label", selectedPackage.substringAfterLast('.'))
            put("package_name", selectedPackage)
            put("component_name", selectedComponent)
            put("action", action)
            put("uri", uriText)
            put("extras", extrasText)
        }.toString()

        fun inspect() {
            if (selectedPackage.isBlank()) return
            val info = runCatching { packageManager.getPackageInfo(selectedPackage, PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS) }.getOrNull() ?: return
            val foundComponents = buildList {
                info.activities.orEmpty().filter { it.exported }.forEach { add("activity: ${it.name}") }
                info.services.orEmpty().filter { it.exported }.forEach { add("service: ${it.name}") }
                info.receivers.orEmpty().filter { it.exported }.forEach { add("receiver: ${it.name}") }
                info.providers.orEmpty().filter { it.exported }.forEach { add("provider: ${it.name}") }
            }
            components = foundComponents
            activityComponents = info.activities.orEmpty().filter { it.exported }.map { it.name }.distinct()
            val probes = listOf(Intent.ACTION_MAIN, Intent.ACTION_VIEW, Intent.ACTION_EDIT, Intent.ACTION_SEND, Intent.ACTION_GET_CONTENT)
                .filter { probe -> packageManager.queryIntentActivities(Intent(probe).setPackage(selectedPackage), PackageManager.MATCH_ALL).isNotEmpty() }
            val filterProbes = listOf(
                IntentProbe(Intent.ACTION_MAIN, Intent.CATEGORY_LAUNCHER, null),
                IntentProbe(Intent.ACTION_VIEW, Intent.CATEGORY_DEFAULT, "http"),
                IntentProbe(Intent.ACTION_VIEW, Intent.CATEGORY_DEFAULT, "https"),
                IntentProbe(Intent.ACTION_VIEW, Intent.CATEGORY_BROWSABLE, "http"),
                IntentProbe(Intent.ACTION_VIEW, Intent.CATEGORY_BROWSABLE, "https"),
                IntentProbe(Intent.ACTION_EDIT, Intent.CATEGORY_DEFAULT, null),
                IntentProbe(Intent.ACTION_SEND, Intent.CATEGORY_DEFAULT, null),
                IntentProbe(Intent.ACTION_GET_CONTENT, Intent.CATEGORY_OPENABLE, null)
            )
            val matchedFilters = filterProbes.mapNotNull { probe ->
                val probeIntent = Intent(probe.action).setPackage(selectedPackage).addCategory(probe.category)
                if (probe.scheme != null) probeIntent.data = Uri.parse("${probe.scheme}://methodmesh.invalid")
                val matches = packageManager.queryIntentActivities(probeIntent, PackageManager.MATCH_ALL)
                    .map { it.activityInfo.name }.distinct()
                matches.takeIf { it.isNotEmpty() }?.let {
                    "action=${probe.action};category=${probe.category};scheme=${probe.scheme.orEmpty()};activities=${it.joinToString(",")}"
                }
            }
            inspection = buildString {
                appendLine("package=$selectedPackage")
                appendLine("label=${info.applicationInfo?.loadLabel(packageManager)?.toString() ?: ""}")
                appendLine("version=${info.versionName.orEmpty()}")
                appendLine("exported_components=${foundComponents.size}")
                foundComponents.forEach { appendLine(it) }
                appendLine("matched_common_actions=${probes.joinToString(",")}")
                appendLine("matched_intent_filters=${matchedFilters.size}")
                matchedFilters.forEach { appendLine(it) }
            }.trim()
            result = As100AppInspectorMethod.result(
                As100AppInspectorMethod.request(As100AppInspectorMethod.ID, mapOf("package_name" to selectedPackage)),
                AppInspectionOutcome(selectedPackage, info.applicationInfo?.loadLabel(packageManager)?.toString().orEmpty(), info.versionName.orEmpty(), foundComponents.joinToString("\n"), probes.joinToString(","), matchedFilters.joinToString("\n"), action, selectedComponent, uriText, testResult, integrationDefinition()),
                context.request.invocationContext
            )
        }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            testResult = buildString {
                appendLine("result_code=${returned.resultCode}")
                appendLine("data_uri=${returned.data?.dataString.orEmpty()}")
                returned.data?.extras?.keySet().orEmpty().sorted().forEach { key -> appendLine("$key=${returned.data?.extras?.get(key)}") }
            }.trim()
            inspect()
        }

        fun testIntent() {
            if (selectedPackage.isBlank()) return
            val intent = Intent(action).setPackage(selectedPackage)
            if (action == Intent.ACTION_MAIN) {
                // ACTION_MAIN is only launchable when paired with the launcher
                // category, matching the way the app picker discovers apps.
                intent.addCategory(Intent.CATEGORY_LAUNCHER)
                // Some apps expose a launcher activity but do not resolve the
                // package-scoped implicit query. Fall back to Android's own
                // launcher resolution and make that component explicit.
                if (packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).isEmpty()) {
                    packageManager.getLaunchIntentForPackage(selectedPackage)?.component?.let { intent.component = it }
                }
            }
            if (uriText.isNotBlank()) intent.data = Uri.parse(uriText)
            if (selectedComponent.isNotBlank()) intent.component = ComponentName(selectedPackage, selectedComponent)
            extrasText.lines().mapNotNull { line -> line.substringBefore('=').trim().takeIf(String::isNotBlank)?.let { it to line.substringAfter('=', "") } }.forEach { (key, value) -> intent.putExtra(key, value) }
            runCatching { launcher.launch(intent) }.onFailure { testResult = "launch_error=${it.message.orEmpty()}"; inspect() }
        }

        fun saveIntegration() {
            if (selectedPackage.isBlank() || testResult.isBlank()) return
            AppInspectorRepository.save(androidContext, AppIntegrationDefinition(
                id = java.util.UUID.randomUUID().toString(),
                label = selectedPackage.substringAfterLast('.'),
                packageName = selectedPackage,
                componentName = selectedComponent,
                action = action,
                uri = uriText,
                extras = extrasText
            ))
            savedStatus = "Integration definition saved on this device."
        }

        LaunchedEffect(context.startsImmediately) { if (context.startsImmediately && selectedPackage.isNotBlank()) inspect() }

        CapabilityScreenScaffold(
            title, capabilityId, context, context.stepNumber > 1, result,
            result?.let { OutputFormatter.fields(it, false) }.orEmpty(), onBack, { inspect() }, { result?.let(onConfirmed) }, onCancel
        ) {
            Text(
                "Workflow: choose an app, inspect what Android says is public, choose an exported activity if needed, then test and save a named command. This is a public-interface tester, not a private reverse-engineering tool.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Text("1. App", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { pickerOpen = true }, Modifier.fillMaxWidth()) { Text(if (selectedPackage.isBlank()) "Choose installed app" else "$selectedPackage") }
            if (activityComponents.isNotEmpty()) {
                Text("2. Public activity / endpoint", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                OutlinedButton(onClick = { componentPickerOpen = true }, Modifier.fillMaxWidth()) {
                    Text(if (selectedComponent.isBlank()) "Use app default, or choose exported activity" else selectedComponent.substringAfterLast('.'))
                }
            }
            Text("3. Command to test", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            IntentActionChooser(action = action, onActionSelected = { action = it })
            OutlinedTextField(action, { action = it }, label = { Text("Intent action, e.g. android.intent.action.VIEW") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(uriText, { uriText = it }, label = { Text("URI/data, if the command uses one") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(extrasText, { extrasText = it }, label = { Text("Extras, one key=value per line") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (components.isNotEmpty()) {
                Text("Exported components", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                components.take(20).forEach { component -> Text(component, style = MaterialTheme.typography.bodySmall) }
            }
            Button(onClick = ::inspect, Modifier.fillMaxWidth()) { Text("Inspect public interfaces") }
            Button(onClick = ::testIntent, Modifier.fillMaxWidth(), enabled = selectedPackage.isNotBlank()) { Text("Test this command") }
            if (testResult.isNotBlank()) Button(onClick = ::saveIntegration, Modifier.fillMaxWidth()) { Text("Save tested command") }
            if (savedStatus.isNotBlank()) Text(savedStatus, style = MaterialTheme.typography.bodySmall)
            if (inspection.isNotBlank()) Surface(Modifier.fillMaxWidth().padding(top = 8.dp), tonalElevation = 1.dp) { Text(inspection, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
            if (testResult.isNotBlank()) Surface(Modifier.fillMaxWidth().padding(top = 8.dp), tonalElevation = 1.dp) { Text(testResult, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
        }

        if (pickerOpen) Dialog(onDismissRequest = { pickerOpen = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    items(apps) { app ->
                        DropdownMenuItem(text = { Text("${app.label} (${app.packageName})") }, onClick = { selectedPackage = app.packageName; pickerOpen = false; inspect() })
                    }
                }
            }
        }
        if (componentPickerOpen) Dialog(onDismissRequest = { componentPickerOpen = false }) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                LazyColumn(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    item {
                        DropdownMenuItem(text = { Text("No explicit activity") }, onClick = { selectedComponent = ""; componentPickerOpen = false })
                    }
                    items(activityComponents) { component ->
                        DropdownMenuItem(text = { Text(component) }, onClick = { selectedComponent = component; componentPickerOpen = false })
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun launchableApps(packageManager: PackageManager): List<InstalledApp> = packageManager
        .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), PackageManager.MATCH_ALL)
        .map { InstalledApp(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase(Locale.ROOT) }
}

private data class IntentProbe(val action: String, val category: String, val scheme: String?)

@Composable
private fun IntentActionChooser(action: String, onActionSelected: (String) -> Unit) {
    val rows = listOf(
        listOf(Intent.ACTION_MAIN to "Open app", Intent.ACTION_VIEW to "View / open URI"),
        listOf(Intent.ACTION_SEND to "Send", Intent.ACTION_SENDTO to "Send to"),
        listOf(Intent.ACTION_GET_CONTENT to "Pick content", Intent.ACTION_EDIT to "Edit")
    )
    Text("Common Android actions", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEach { (value, label) ->
                val selected = action == value
                if (selected) {
                    Button(onClick = { onActionSelected(value) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text("✓ $label") }
                } else {
                    OutlinedButton(onClick = { onActionSelected(value) }, modifier = Modifier.weight(1f).padding(2.dp)) { Text(label) }
                }
            }
        }
    }
}
