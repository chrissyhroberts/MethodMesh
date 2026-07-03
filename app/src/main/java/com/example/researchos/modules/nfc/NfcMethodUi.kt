package com.example.researchos.modules.nfc

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun KeyValueSection(
    title: String,
    values: Map<String, String>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Divider(Modifier.padding(top = 10.dp, bottom = 10.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        if (values.isEmpty()) {
            Text("No values.")
        } else {
            values.forEach { (key, value) ->
                Text(
                    text = "$key = ${value.ifBlank { "" }}",
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

internal fun Map<String, String>.filterForDisplay(keys: List<String>): Map<String, String> =
    keys.associateWith { this[it].orEmpty() }
