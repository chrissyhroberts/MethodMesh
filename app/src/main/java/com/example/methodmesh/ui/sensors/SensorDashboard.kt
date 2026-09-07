package com.example.methodmesh.ui.sensors

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.platform.sensors.SensorReading

@Composable
fun SensorDashboard() {
    val context = LocalContext.current
    var running by remember { mutableStateOf(true) }

    DisposableEffect(running, context) {
        if (running) PhoneSensorRepository.start(context) else PhoneSensorRepository.stop()
        onDispose { PhoneSensorRepository.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sensors", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    PhoneSensorRepository.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                if (running) "Stop" else "Start",
                modifier = Modifier
                    .clickable { running = !running }
                    .padding(horizontal = 4.dp, vertical = 10.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(Modifier.height(12.dp))
        SensorValueRow(
            label = "Heading",
            value = PhoneSensorRepository.formattedHeading(),
            available = true,
            onCopy = { copyValue(context, "Heading", PhoneSensorRepository.formattedHeading()) }
        )

        val readings = PhoneSensorRepository.readings.values.sortedBy { it.label }
        readings.forEach { reading -> SensorReadingRow(reading) }
    }
}

@Composable
private fun SensorReadingRow(reading: SensorReading) {
    val context = LocalContext.current
    val value = if (reading.values.isEmpty()) {
        "Waiting"
    } else {
        reading.values.joinToString(", ") { "%.2f".format(it) } + (reading.unit?.let { " $it" } ?: "")
    }

    SensorValueRow(
        label = reading.label,
        value = value,
        available = reading.available,
        detail = if (reading.available) "Accuracy ${reading.accuracy ?: "unknown"}" else "Unavailable",
        onCopy = {
            if (reading.available && reading.values.isNotEmpty()) copyValue(context, reading.label, value)
        }
    )
}

@Composable
private fun SensorValueRow(
    label: String,
    value: String,
    available: Boolean,
    detail: String? = null,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = available, onClick = onCopy)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            detail?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            if (available) value else "—",
            style = MaterialTheme.typography.bodyLarge,
            color = if (available) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

private fun copyValue(context: android.content.Context, label: String, value: String) {
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
}
