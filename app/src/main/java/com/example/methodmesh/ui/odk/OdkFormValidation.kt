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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class OdkValidationSeverity {
    Error,
    Warning,
    Style,
    Info;

    companion object {
        fun fromWire(value: String): OdkValidationSeverity = when (value.lowercase()) {
            "error" -> Error
            "warning" -> Warning
            "style" -> Style
            else -> Info
        }
    }
}

data class OdkValidationIssue(
    val severity: OdkValidationSeverity,
    val code: String,
    val message: String,
    val location: String,
    val suggestion: String,
    val source: String,
    val details: String
)

data class OdkValidationSummary(
    val engine: String,
    val authoritativeValidationAvailable: Boolean,
    val authoritativeValidatedForms: Int,
    val cleanForms: Int,
    val formsNeedingRevision: Int,
    val errorCount: Int,
    val warningCount: Int,
    val styleCount: Int
)

object OdkValidationReports {
    fun issueText(template: OdkTemplateDescriptor, issue: OdkValidationIssue): String = buildString {
        append(template.displayName).append('\n')
        append("Module: ").append(template.moduleName).append('\n')
        append("File: ").append(template.sourceRelativePath.ifBlank { template.sourceFileName }).append('\n')
        append("Severity: ").append(issue.severity.name.uppercase()).append('\n')
        append("Code: ").append(issue.code).append('\n')
        issue.location.takeIf(String::isNotBlank)?.let { append("Location: ").append(it).append('\n') }
        append("Message: ").append(issue.message)
        issue.suggestion.takeIf(String::isNotBlank)?.let { append("\nSuggestion: ").append(it) }
        issue.source.takeIf(String::isNotBlank)?.let { append("\nValidator: ").append(it) }
        issue.details.takeIf(String::isNotBlank)?.let { append("\n\nDetails\n").append(it) }
    }

    fun templateMarkdown(template: OdkTemplateDescriptor, issues: List<OdkValidationIssue>): String = buildString {
        append("# MethodMesh XLSForm validation finding\n\n")
        append("## ").append(template.displayName).append("\n\n")
        append("Module: ").append(template.moduleName).append('\n')
        append("File: ").append(template.sourceRelativePath.ifBlank { template.sourceFileName }).append("\n\n")
        if (issues.isEmpty()) {
            append("No findings match the selected filter.\n")
            return@buildString
        }
        issues.forEach { issue ->
            append("### ").append(issue.severity.name.uppercase()).append(" · ").append(issue.code).append("\n\n")
            append(issue.message).append('\n')
            issue.location.takeIf(String::isNotBlank)?.let { append("Location: ").append(it).append('\n') }
            issue.suggestion.takeIf(String::isNotBlank)?.let { append("Suggestion: ").append(it).append('\n') }
            issue.source.takeIf(String::isNotBlank)?.let { append("Validator: ").append(it).append('\n') }
            issue.details.takeIf(String::isNotBlank)?.let { append("\nDetails\n").append(it).append('\n') }
            append('\n')
        }
    }

    fun batchMarkdown(templates: List<OdkTemplateDescriptor>, summary: OdkValidationSummary): String = buildString {
        append("# MethodMesh XLSForm validation report\n\n")
        append("Validator: ").append(summary.engine).append("\n\n")
        append("- Forms: ").append(templates.size).append('\n')
        append("- Clean: ").append(summary.cleanForms).append('\n')
        append("- Forms with findings: ").append(summary.formsNeedingRevision).append('\n')
        append("- Errors: ").append(summary.errorCount).append('\n')
        append("- Warnings: ").append(summary.warningCount).append('\n')
        append("- Naming/style findings: ").append(summary.styleCount).append('\n')
        append("- Full pyxform + ODK Validate coverage: ")
            .append(if (summary.authoritativeValidationAvailable) "yes" else "no")
            .append("\n\n")
        if (!summary.authoritativeValidationAvailable) {
            append("> The packaged report includes MethodMesh structural and naming lint. Install pyxform on the build machine to add authoritative pyxform + ODK Validate results.\n\n")
        }
        val withIssues = templates.filter { it.validationIssues.isNotEmpty() }
            .sortedWith(compareBy<OdkTemplateDescriptor> { it.moduleName.lowercase() }.thenBy { it.displayName.lowercase() })
        if (withIssues.isEmpty()) {
            append("No issues were found.\n")
            return@buildString
        }
        withIssues.groupBy { it.moduleName }.toSortedMap().forEach { (module, forms) ->
            append("## ").append(module).append("\n\n")
            forms.forEach { template ->
                append("### ").append(template.displayName).append("\n\n")
                append("`").append(template.sourceRelativePath.ifBlank { template.sourceFileName }).append("`\n\n")
                template.validationIssues.forEach { issue ->
                    append("- **").append(issue.severity.name.uppercase()).append(" · ").append(issue.code).append("** — ").append(issue.message)
                    issue.location.takeIf(String::isNotBlank)?.let { append(" (`").append(it).append("`)") }
                    append('\n')
                    issue.suggestion.takeIf(String::isNotBlank)?.let { append("  - Suggested: ").append(it).append('\n') }
                    issue.details.takeIf(String::isNotBlank)?.let { append("  - Details: `").append(it.replace("\n", " ").take(800)).append("`\n") }
                }
                append('\n')
            }
        }
    }
}

@Composable
fun OdkFormValidationPanel(
    templates: List<OdkTemplateDescriptor>,
    summary: OdkValidationSummary,
    focusTemplateId: String? = null,
    initialFilter: String? = null,
    onClearFocus: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var filter by rememberSaveable(focusTemplateId, initialFilter) { mutableStateOf(initialFilter ?: "all") }
    val focusedTemplate = remember(templates, focusTemplateId) {
        focusTemplateId?.let { id -> templates.firstOrNull { it.id == id } }
    }
    fun matchesFilter(issue: OdkValidationIssue): Boolean = when (filter) {
        "errors" -> issue.severity == OdkValidationSeverity.Error
        "warnings" -> issue.severity == OdkValidationSeverity.Warning
        "naming" -> issue.severity == OdkValidationSeverity.Style
        else -> true
    }
    val report = remember(templates, summary, focusedTemplate, filter) {
        if (focusedTemplate != null) {
            OdkValidationReports.templateMarkdown(focusedTemplate, focusedTemplate.validationIssues.filter(::matchesFilter))
        } else {
            OdkValidationReports.batchMarkdown(templates, summary)
        }
    }
    var pendingReport by remember { mutableStateOf(report) }
    val saveReport = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingReport) }
                    ?: error("Could not open destination")
            }.onSuccess {
                Toast.makeText(context, "Validation report saved", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "Could not save report", Toast.LENGTH_SHORT).show()
            }
        }
    }
    val forms = remember(templates, focusTemplateId, filter) {
        templates.filter { template ->
            (focusTemplateId == null || template.id == focusTemplateId) &&
                template.validationIssues.any(::matchesFilter)
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    focusedTemplate?.let { "XLSForm validation · ${it.displayName}" } ?: "XLSForm validation",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (focusedTemplate != null) {
                        val visible = focusedTemplate.validationIssues.count(::matchesFilter)
                        "$visible relevant finding${if (visible == 1) "" else "s"} · showing this form only"
                    } else {
                        "${summary.cleanForms} clean · ${summary.formsNeedingRevision} with findings · ${summary.errorCount} blocking errors · ${summary.warningCount} advisory warnings"
                    },
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((focusedTemplate?.validationIssues?.any { it.severity == OdkValidationSeverity.Error && matchesFilter(it) } == true) || (focusedTemplate == null && summary.errorCount > 0)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    summary.engine,
                    modifier = Modifier.padding(top = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (focusedTemplate != null) {
                Text(
                    "All forms",
                    modifier = Modifier.clickable(onClick = onClearFocus).padding(vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                "Close",
                modifier = Modifier.clickable(onClick = onClose).padding(vertical = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("All", modifier = Modifier.clickable { filter = "all" }.padding(vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (filter == "all") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text("Errors", modifier = Modifier.clickable { filter = "errors" }.padding(vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (filter == "errors") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text("Warnings", modifier = Modifier.clickable { filter = "warnings" }.padding(vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (filter == "warnings") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text("Naming", modifier = Modifier.clickable { filter = "naming" }.padding(vertical = 6.dp), style = MaterialTheme.typography.labelMedium, color = if (filter == "naming") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (focusedTemplate != null) "Copy form report" else "Copy batch report",
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh XLSForm validation", report))
                    Toast.makeText(context, "Validation report copied", Toast.LENGTH_SHORT).show()
                }.padding(vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(18.dp))
            Text(
                "Export report",
                modifier = Modifier.clickable {
                    pendingReport = report
                    saveReport.launch(if (focusedTemplate != null) "methodmesh_xlsform_${focusedTemplate.id}_validation.md" else "methodmesh_xlsform_validation.md")
                }.padding(vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(18.dp))
            Text(
                "Share report",
                modifier = Modifier.clickable {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, report)
                    }, "Share XLSForm validation report"))
                }.padding(vertical = 7.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (!summary.authoritativeValidationAvailable) {
            Text(
                "Full ODK validation is not packaged for every form. The current report still checks workbook structure, XLSForm references, common unsupported XPath functions, duplicate IDs, and MethodMesh naming conventions. Install pyxform on the build machine for pyxform + ODK Validate coverage.",
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (forms.isEmpty()) {
            Text("No forms match this validation filter.", modifier = Modifier.padding(vertical = 14.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            forms.groupBy { it.moduleName }.toSortedMap().forEach { (module, moduleForms) ->
                Text(module, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                moduleForms.forEach { template ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(template.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(template.sourceRelativePath.ifBlank { template.sourceFileName }, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        template.validationIssues.filter(::matchesFilter).forEach { issue ->
                            ValidationIssueRow(template, issue)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
                }
            }
        }
    }
}

@Composable
private fun ValidationIssueRow(template: OdkTemplateDescriptor, issue: OdkValidationIssue) {
    val context = LocalContext.current
    var expanded by rememberSaveable(template.id, issue.code, issue.location) { mutableStateOf(false) }
    val color = when (issue.severity) {
        OdkValidationSeverity.Error -> MaterialTheme.colorScheme.error
        OdkValidationSeverity.Warning -> MaterialTheme.colorScheme.tertiary
        OdkValidationSeverity.Style -> MaterialTheme.colorScheme.primary
        OdkValidationSeverity.Info -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                val text = OdkValidationReports.issueText(template, issue)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh XLSForm issue", text))
                Toast.makeText(context, "Issue copied", Toast.LENGTH_SHORT).show()
                expanded = !expanded
            }
            .padding(top = 7.dp, bottom = 4.dp, start = 10.dp)
    ) {
        Text("${issue.severity.name.uppercase()} · ${issue.code}", style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.SemiBold)
        Text(issue.message, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall)
        issue.location.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (expanded) {
            issue.suggestion.takeIf(String::isNotBlank)?.let { Text("Suggested: $it", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            issue.details.takeIf(String::isNotBlank)?.let { Text(it, modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("Tap again to copy and collapse", modifier = Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Tap to copy", modifier = Modifier.padding(top = 3.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}
