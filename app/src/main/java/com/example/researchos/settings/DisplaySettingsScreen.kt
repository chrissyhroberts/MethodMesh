package com.example.researchos.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DisplaySettingsScreen() {
    val current = DisplaySettingsRepository.settings.value.textScale
    val presets = listOf(
        "Extra small" to 1.0f,
        "Small" to 1.15f,
        "Normal" to 1.3f,
        "Large" to 1.45f,
        "Extra large" to 1.6f,
        "High visibility" to 1.8f
    )

    Column {
        Text("Text size", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Applies across the app, including intent-launched capability screens.",
            modifier = Modifier.padding(top = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(10.dp))
        presets.forEach { (label, scale) ->
            val selected = kotlin.math.abs(current - scale) < 0.01f
            val buttonModifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            if (selected) {
                Button(
                    modifier = buttonModifier,
                    onClick = { DisplaySettingsRepository.updateTextScale(scale) }
                ) {
                    Text("$label · ${(scale * 100).toInt()}%")
                }
            } else {
                OutlinedButton(
                    modifier = buttonModifier,
                    onClick = { DisplaySettingsRepository.updateTextScale(scale) }
                ) {
                    Text("$label · ${(scale * 100).toInt()}%")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Preview", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("How easy is this to read?", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
                Text("Question hints and explanatory text scale with this setting.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
