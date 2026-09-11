package com.example.methodmesh.modules.webactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode

@Composable
internal fun WebActionHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    status: String,
    host: String,
    active: Boolean,
    success: Boolean,
    elapsedText: String = ""
) {
    val gradient = Brush.linearGradient(
        listOf(
            Color(0xFF06131E),
            Color(0xFF10394A),
            Color(0xFF156A6B)
        )
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradient)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        eyebrow.uppercase(),
                        color = Color(0xFFB7E4DF),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        subtitle,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.size(18.dp))
                StatusOrb(active = active, success = success)
            }
            Spacer(Modifier.height(20.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color.White.copy(alpha = 0.10f)
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (success) "✓" else if (active) "●" else "○",
                            color = if (success) Color(0xFFBFF4D0) else Color(0xFFB7E4DF),
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.size(9.dp))
                        Text(
                            status,
                            modifier = Modifier.weight(1f),
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (elapsedText.isNotBlank()) {
                            Text(
                                elapsedText,
                                color = Color.White.copy(alpha = 0.74f),
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (host.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "▣  $host",
                            color = Color.White.copy(alpha = 0.70f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusOrb(active: Boolean, success: Boolean) {
    Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(72.dp)) {
            val c = center
            val radius = size.minDimension * 0.39f
            drawCircle(
                color = Color.White.copy(alpha = 0.14f),
                radius = radius,
                center = c,
                style = Stroke(width = 7f)
            )
            if (success) {
                drawArc(
                    color = Color(0xFFBFF4D0),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
            } else if (active) {
                drawArc(
                    color = Color(0xFFB7E4DF),
                    startAngle = -90f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(c.x - radius, c.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
                    style = Stroke(width = 7f, cap = StrokeCap.Round)
                )
            }
        }
        Text(
            if (success) "✓" else if (active) "↗" else "WEB",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = if (active || success) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
internal fun SettingSection(
    title: String,
    subtitle: String = "",
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
internal fun SecurityToggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Allow HTTP", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                if (checked) "HTTP is enabled for this run. Use only with a trusted local/test service."
                else "HTTPS only — recommended.",
                style = MaterialTheme.typography.bodySmall,
                color = if (checked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
internal fun CopyableValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by rememberSaveable(label, value) { mutableStateOf(false) }
    if (value.isBlank()) return
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 7.dp)
            .clickable {
                copyPlainText(context, label, value)
                copied = true
            },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(
                if (copied) "Copied" else "Tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun CommittedWebResult(
    title: String,
    summary: String,
    result: ExecutionResult,
    displayValues: List<Pair<String, String>>,
    onDone: () -> Unit,
    onEditRetry: () -> Unit
) {
    val context = LocalContext.current
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    var actionStatus by rememberSaveable(result.request.id.value) { mutableStateOf("") }
    val fullJson = remember(result.request.id.value) {
        OutputFormatter.format(
            result = result,
            returnMode = ReturnMode.Json,
            includeProvenance = true,
            payloadMode = OutputFormatter.PayloadMode.FULL
        )
    }
    val shareText = remember(summary, displayValues) {
        buildString {
            append(summary)
            displayValues.forEach { (label, value) ->
                if (value.isNotBlank()) append("\n$label: $value")
            }
        }.trim()
    }

    WebActionHero(
        eyebrow = "Complete",
        title = title,
        subtitle = "The completed result is ready. Share, save, or finish.",
        status = "Done",
        host = displayValues.firstOrNull { it.first.equals("Host", ignoreCase = true) }?.second.orEmpty(),
        active = false,
        success = true
    )

    SettingSection("Result", "Tap any useful value to copy it.") {
        Text(summary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        displayValues.forEach { (label, value) -> CopyableValue(label, value) }
    }

    SettingSection("Done", "Choose what to do with this result.") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Include full JSON", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "Adds provenance and audit metadata to copy/share/save. Secrets are excluded by the capability contract.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = includeFullJson, onCheckedChange = { includeFullJson = it })
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                runCatching {
                    ResultShare.share(
                        context = context,
                        chooserTitle = "Share MethodMesh result",
                        text = shareText,
                        attachments = emptyList(),
                        jsonText = if (includeFullJson) fullJson else "",
                        fileLabel = title
                    )
                }.onFailure { actionStatus = "Share failed: ${it.message ?: "no sharing app available"}" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Share result") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                val text = shareText + if (includeFullJson) "\n\nmetadata.json\n$fullJson" else ""
                copyPlainText(context, "MethodMesh result", text)
                actionStatus = "Copied result."
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Copy result") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                runCatching {
                    OutputExportRepository.saveToDownloads(
                        context = context,
                        label = title,
                        text = shareText,
                        mediaUris = emptyList(),
                        jsonText = if (includeFullJson) fullJson else ""
                    )
                }.onSuccess { actionStatus = "Saved ${it.summary}" }
                    .onFailure { actionStatus = "Save failed: ${it.message ?: "storage error"}" }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save to Downloads") }
        if (actionStatus.isNotBlank()) {
            Text(
                actionStatus,
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEditRetry, modifier = Modifier.fillMaxWidth()) { Text("Run again") }
    }
}

internal fun copyPlainText(context: Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}

internal fun elapsedLabel(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
