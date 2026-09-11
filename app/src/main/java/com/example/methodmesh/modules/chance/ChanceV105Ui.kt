package com.example.methodmesh.modules.chance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext

/**
 * Module-local v1.05 presentation helpers.
 *
 * They deliberately contain no Chance-specific execution logic.  The capability
 * screens own their working state and canonical result; these helpers only provide
 * the common Chance presentation for configuration, tap-to-copy and post-Commit
 * actions.  Closeout is handed back through the screen's supplied callback so the
 * shared MethodMesh host remains responsible for app/widget/ODK/protocol/schedule
 * routing.
 */
@Composable
internal fun ChanceConfigSurface(
    title: String,
    context: CapabilityScreenContext,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        content()
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (context.stepNumber > 1) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
            }
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
        }
    }
}


@Composable
internal fun ChanceSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    supportingText: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (supportingText.isNotBlank()) {
                Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun ChanceCountStepper(
    label: String,
    value: Int,
    minimum: Int,
    maximum: Int,
    onValueChange: (Int) -> Unit,
    supportingText: String = ""
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            OutlinedButton(
                onClick = { onValueChange((value - 1).coerceAtLeast(minimum)) },
                enabled = value > minimum,
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("−") }
            Text(
                text = value.toString(),
                modifier = Modifier.width(64.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(
                onClick = { onValueChange((value + 1).coerceAtMost(maximum)) },
                enabled = value < maximum,
                modifier = Modifier.size(44.dp),
                contentPadding = PaddingValues(0.dp)
            ) { Text("+") }
        }
        if (supportingText.isNotBlank()) {
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ChanceCopyableText(
    value: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    color: Color = Color.Unspecified
) {
    val appContext = LocalContext.current
    Text(
        text = value,
        modifier = modifier.clickable(enabled = value.isNotBlank()) {
            copyChanceValue(appContext, value)
        },
        style = style,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        color = color
    )
}

internal fun chanceFullJson(result: ExecutionResult?): String = result?.let {
    OutputFormatter.format(
        result = it,
        returnMode = ReturnMode.Json,
        includeProvenance = true,
        payloadMode = OutputFormatter.PayloadMode.FULL
    )
}.orEmpty()

internal fun copyChanceValue(context: Context, value: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText("MethodMesh Chance result", value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ChancePostCommitActions(
    title: String,
    primaryText: String,
    auditJson: String,
    onDone: () -> Unit,
    onNewRun: () -> Unit
) {
    val appContext = LocalContext.current
    var includeAudit by rememberSaveable { mutableStateOf(false) }
    var showTechnicalDetails by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf("") }

    Spacer(Modifier.height(14.dp))
    Text("Committed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                runCatching {
                    ResultShare.share(
                        context = appContext,
                        chooserTitle = "Share $title",
                        text = primaryText,
                        attachments = emptyList(),
                        jsonText = if (includeAudit) auditJson else ""
                    )
                }.onFailure { status = "Share failed: ${it.message ?: "no sharing app"}" }
            }
        ) { Text("Share") }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                copyChanceValue(appContext, chanceShareText(primaryText, auditJson, includeAudit))
                status = "Copied"
            }
        ) { Text("Copy") }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = {
                runCatching {
                    OutputExportRepository.saveToDownloads(
                        context = appContext,
                        label = title,
                        text = primaryText,
                        mediaUris = emptyList(),
                        jsonText = if (includeAudit) auditJson else ""
                    )
                }.onSuccess { status = "Saved ${it.summary}" }
                    .onFailure { status = "Save failed: ${it.message ?: "storage error"}" }
            }
        ) { Text("Save") }
    }

    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Include JSON / audit", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Switch(checked = includeAudit, onCheckedChange = { includeAudit = it })
    }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = { showTechnicalDetails = !showTechnicalDetails }
    ) {
        Text(if (showTechnicalDetails) "Hide technical details" else "Technical details")
    }

    if (showTechnicalDetails && auditJson.isNotBlank()) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.medium
        ) {
            ChanceCopyableText(
                value = auditJson,
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    if (status.isNotBlank()) {
        Text(status, modifier = Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }

    Spacer(Modifier.height(10.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    OutlinedButton(onClick = onNewRun, modifier = Modifier.fillMaxWidth()) { Text("New run") }
}

private fun chanceShareText(primaryText: String, auditJson: String, includeAudit: Boolean): String =
    ResultShare.buildShareText(primaryText, if (includeAudit) auditJson else "")

internal fun CapabilityScreenContext.chanceCanAutoRunFixedPreset(settingIds: Collection<String>): Boolean =
    isNativePresetRun && settingIds.none { settingShouldBeShown(it) }
