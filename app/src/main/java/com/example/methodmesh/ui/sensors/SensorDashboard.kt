package com.example.methodmesh.ui.sensors

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.platform.sensors.SensorReading

@Composable
fun SensorDashboard() {
    val context = LocalContext.current
    var running by remember { mutableStateOf(true) }

    DisposableEffect(running, context) {
        if (running) {
            PhoneSensorRepository.start(context)
        } else {
            PhoneSensorRepository.stop()
        }

        onDispose {
            PhoneSensorRepository.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "Sensor dashboard",
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text("Status: ${PhoneSensorRepository.status}")
        Text("Heading: ${PhoneSensorRepository.formattedHeading()}")

        Spacer(Modifier.height(8.dp))

        Row {
            Button(
                onClick = { running = true },
                enabled = !running
            ) {
                Text("Start")
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { running = false },
                enabled = running
            ) {
                Text("Stop")
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        val readings = PhoneSensorRepository.readings.values
            .sortedBy { it.label }

        readings.forEach { reading ->
            SensorReadingRow(reading)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SensorReadingRow(
    reading: SensorReading
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = if (reading.available) "✓ ${reading.label}" else "✕ ${reading.label}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )

        if (reading.available) {
            Text(
                text = if (reading.values.isEmpty()) {
                    "waiting"
                } else {
                    reading.values.joinToString(
                        separator = ", ",
                        postfix = reading.unit?.let { " $it" } ?: ""
                    ) { "%.2f".format(it) }
                },
                fontFamily = FontFamily.Monospace
            )

            Text(
                text = "accuracy=${reading.accuracy ?: "unknown"}; updated=${reading.timestampMs}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
