package com.example.methodmesh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun NumericSettingField(
    label: String,
    value: Float,
    minimum: Float,
    maximum: Float,
    step: Float = 1f,
    unit: String? = null,
    decimals: Int = 1,
    onValueChange: (Float) -> Unit
) {
    var text by remember(label) { mutableStateOf(formatNumber(value, decimals)) }

    LaunchedEffect(value) {
        val formatted = formatNumber(value, decimals)
        if (text.toFloatOrNull() != value) text = formatted
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)

        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { onValueChange((value - step).coerceIn(minimum, maximum)) },
                modifier = Modifier.width(48.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Text("−") }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { entered ->
                    text = entered
                    entered.toFloatOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(minimum, maximum))
                    }
                },
                suffix = { if (unit != null) Text(unit) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = { onValueChange((value + step).coerceIn(minimum, maximum)) },
                modifier = Modifier.width(48.dp),
                shape = MaterialTheme.shapes.small,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Text("+") }
        }

        Slider(
            value = value.coerceIn(minimum, maximum),
            onValueChange = { onValueChange(it.coerceIn(minimum, maximum)) },
            valueRange = minimum..maximum
        )
    }
}

private fun formatNumber(value: Float, decimals: Int): String = "%.${decimals}f".format(value)
