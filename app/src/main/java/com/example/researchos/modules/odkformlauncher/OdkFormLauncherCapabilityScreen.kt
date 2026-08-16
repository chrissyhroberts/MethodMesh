package com.example.researchos.modules.odkformlauncher

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object OdkFormLauncherCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100OdkFormLauncherMethod.ID
    override val title = "Open ODK form"
    override val description = "Open a locally available ODK Collect form by its ID or display name."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val activity = appContext.findActivity()
        val scope = rememberCoroutineScope()
        val supplied = remember(context.action.settings, context.request.settings) {
            context.action.settings + context.request.settings.filterValues(String::isNotBlank)
        }
        var selector by remember {
            mutableStateOf(
                supplied[OdkFormLaunchFields.FORM_SELECTOR]
                    ?: supplied["input_form_selector"]
                    ?: supplied["odk_form_id"]
                    ?: supplied["form_id"]
                    ?: ""
            )
        }
        var projectId by remember {
            mutableStateOf(supplied["project_id"] ?: supplied["input_project_id"] ?: "")
        }
        var projectName by remember { mutableStateOf("") }
        var projectPackage by remember { mutableStateOf(supplied["input_project_package"] ?: supplied["odk_project_package"] ?: "") }
        var savedProjects by remember { mutableStateOf(OdkProjectRegistry.load(appContext)) }
        var availableForms by remember { mutableStateOf(emptyList<OdkFormDescriptor>()) }
        var selectedFormId by remember { mutableStateOf("") }
        var status by remember { mutableStateOf("Enter an ODK form ID or display name.") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var pending by remember { mutableStateOf<OdkFormDescriptor?>(null) }
        var launched by remember { mutableStateOf(false) }

        LaunchedEffect(selector, projectId, projectPackage) {
            context.onSettingsChanged(
                mapOf(
                    "form_selector" to selector,
                    "project_id" to projectId,
                    "project_package" to projectPackage
                )
            )
        }

        val projectPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            val selectedUri = returned.data?.data ?: return@rememberLauncherForActivityResult
            val selectedProject = selectedUri.getQueryParameter("projectId").orEmpty()
            if (selectedProject.isBlank()) {
                status = "ODK did not return a project ID for the selected form."
            } else {
                val selected = OdkFormLauncherRepository.describe(appContext, selectedUri, selectedProject)
                projectId = selectedProject
                projectName = selectedProject
                projectPackage = selected?.packageName.orEmpty()
                OdkProjectRegistry.save(appContext, OdkProject(selectedProject, selectedProject, projectPackage))
                savedProjects = OdkProjectRegistry.load(appContext)
                if (selected != null) {
                    selector = selected.id
                    selectedFormId = selected.id
                }
                status = "Registered ODK project ${selectedProject}."
            }
        }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { returned ->
            val descriptor = pending
            val instanceUri = returned.data?.data?.toString().orEmpty()
            val outcome = if (returned.resultCode == Activity.RESULT_OK) {
                OdkFormLaunchOutcome(
                    projectId = projectId,
                    selector = selector, formId = descriptor?.id.orEmpty(), formName = descriptor?.name.orEmpty(),
                    formUri = descriptor?.uri.toString(), instanceUri = instanceUri, status = "returned"
                )
            } else {
                OdkFormLaunchOutcome(
                    projectId = projectId,
                    selector = selector, formId = descriptor?.id.orEmpty(), formName = descriptor?.name.orEmpty(),
                    formUri = descriptor?.uri.toString(), status = "cancelled", error = "ODK Collect did not return a completed form."
                )
            }
            val execution = As100OdkFormLauncherMethod.result(
                As100OdkFormLauncherMethod.request(
                    action = capabilityId,
                    context = context.request.invocationContext.asMap(capabilityId) + context.action.settings
                ),
                outcome,
                context.request.invocationContext
            )
            result = execution
            pending = null
            status = outcome.status
            if (context.startsImmediately) onConfirmed(execution)
        }

        fun openForm() {
            if (selector.isBlank()) {
                status = "A form ID or display name is required."
                return
            }
            if (activity == null) {
                status = "An Android activity is required to open ODK Collect."
                return
            }
            scope.launch {
                status = "Looking for the form in ODK Collect…"
                val lookup = withContext(Dispatchers.IO) { OdkFormLauncherRepository.lookup(appContext, selector, projectId.trim(), projectPackage) }
                val found = (lookup as? OdkFormLookupResult.Found)?.form
                if (found == null) {
                    val statusCode = if (lookup is OdkFormLookupResult.ProviderUnavailable) "provider_unavailable" else "not_found"
                    val error = when (lookup) {
                        is OdkFormLookupResult.ProviderUnavailable ->
                            "ODK Collect's forms provider could not be queried: ${lookup.reason}"
                        is OdkFormLookupResult.NotFound -> {
                            val available = lookup.available.take(8).joinToString(", ")
                            if (available.isBlank()) {
                                "ODK Collect returned no locally available forms."
                            } else {
                                "No locally available ODK form matched '$selector'. Available forms: $available"
                            }
                        }
                        else -> "No locally available ODK form matched '$selector'."
                    }
                    val outcome = OdkFormLaunchOutcome(projectId = projectId, selector = selector, status = statusCode, error = error)
                    val execution = As100OdkFormLauncherMethod.result(
                        As100OdkFormLauncherMethod.request(
                            action = capabilityId,
                            context = context.request.invocationContext.asMap(capabilityId) + context.action.settings
                        ),
                        outcome,
                        context.request.invocationContext
                    )
                    result = execution
                    status = outcome.error
                    if (context.startsImmediately) onConfirmed(execution)
                } else {
                    pending = found
                    status = "Opening ${found.name} in ODK Collect…"
                    launched = true
                    launcher.launch(
                        Intent(Intent.ACTION_EDIT).setDataAndType(
                            found.uri,
                            "vnd.android.cursor.item/vnd.odk.form"
                        )
                            .setPackage(found.packageName)
                    )
                }
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) openForm()
        }
        LaunchedEffect(projectId) {
            if (!context.startsImmediately && projectId.isNotBlank()) {
                availableForms = withContext(Dispatchers.IO) { OdkFormLauncherRepository.list(appContext, projectId.trim(), projectPackage) }
            } else {
                availableForms = emptyList()
            }
        }

        CapabilityScreenScaffold(
            title = title, capabilityId = capabilityId, context = context,
            canGoBack = context.stepNumber > 1, capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack, onRetry = { result = null; launched = false; openForm() },
            onConfirm = { result?.let(onConfirmed) }, onCancel = onCancel
        ) {
            OutlinedTextField(
                value = selector, onValueChange = { selector = it }, enabled = !context.startsImmediately && result == null,
                label = { Text("ODK form ID or display name") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = projectId, onValueChange = { projectId = it }, enabled = !context.startsImmediately && result == null,
                label = { Text("ODK project ID (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
            )
            if (!context.startsImmediately) {
                Spacer(Modifier.height(8.dp))
                Text("Collect app", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { projectPackage = "org.odk.collect.android" },
                        colors = if (projectPackage == "org.odk.collect.android") ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Text("ODK Collect") }
                    Button(
                        onClick = { projectPackage = "org.koboc.collect.android" },
                        colors = if (projectPackage == "org.koboc.collect.android") ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) { Text("Kobo Collect") }
                }
            }
            if (!context.startsImmediately && savedProjects.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Saved ODK projects", style = MaterialTheme.typography.labelLarge)
                savedProjects.forEach { project ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { projectId = project.id; projectName = project.name; projectPackage = project.packageName },
                            colors = if (projectId == project.id) ButtonDefaults.buttonColors()
                            else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) { Text(project.name) }
                        TextButton(
                            onClick = {
                                OdkProjectRegistry.remove(appContext, project.id)
                                savedProjects = OdkProjectRegistry.load(appContext)
                                if (projectId == project.id) {
                                    projectId = ""
                                    projectName = ""
                                    projectPackage = ""
                                    availableForms = emptyList()
                                }
                            }
                        ) { Text("Remove") }
                    }
                }
            }
            if (!context.startsImmediately && result == null) {
                Button(
                    onClick = {
                        projectPicker.launch(
                            Intent(Intent.ACTION_PICK)
                                .setType("vnd.android.cursor.dir/vnd.odk.form")
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Choose a project/form from ODK or Kobo") }
            }
            if (!context.startsImmediately && projectId.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = projectName, onValueChange = { projectName = it },
                    label = { Text("Project name (for saved list)") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Button(
                    onClick = {
                        OdkProjectRegistry.save(appContext, OdkProject(projectId.trim(), projectName.trim(), projectPackage))
                        savedProjects = OdkProjectRegistry.load(appContext)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save project") }
            }
            if (!context.startsImmediately && availableForms.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Forms in selected project", style = MaterialTheme.typography.labelLarge)
                availableForms.forEach { form ->
                    Button(
                        onClick = { selector = form.id; selectedFormId = form.id },
                        colors = if (selectedFormId.equals(form.id, ignoreCase = true)) ButtonDefaults.buttonColors()
                        else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (form.name.equals(form.id, ignoreCase = true)) form.id else "${form.name}\n${form.id}")
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (!context.startsImmediately && result == null) {
                Button(onClick = { openForm() }, modifier = Modifier.fillMaxWidth()) { Text("Open form") }
            }
            Spacer(Modifier.height(10.dp))
            Text(status, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Open a named ODK form",
                        description = "Find a downloaded form in ODK Collect and open it for editing.",
                        intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='odk_form_launcher',input_project_id='my_project_id',input_form_selector='my_form_id',return_mode='flat')"
                    )
                )
            )
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
