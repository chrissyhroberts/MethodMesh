package com.example.methodmesh.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
    var text by remember(label) {
        mutableStateOf(formatNumber(value, decimals))
    }

    LaunchedEffect(value) {
        val formatted = formatNumber(value, decimals)
        if (text.toFloatOrNull() != value) {
            text = formatted
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(label)

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    onValueChange(
                        (value - step).coerceIn(minimum, maximum)
                    )
                }
            ) {
                Text("−")
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = text,
                onValueChange = { entered ->
                    text = entered

                    entered.toFloatOrNull()?.let { parsed ->
                        onValueChange(parsed.coerceIn(minimum, maximum))
                    }
                },
                suffix = {
                    if (unit != null) {
                        Text(unit)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    onValueChange(
                        (value + step).coerceIn(minimum, maximum)
                    )
                }
            ) {
                Text("+")
            }
        }

        Slider(
            value = value.coerceIn(minimum, maximum),
            onValueChange = {
                onValueChange(it.coerceIn(minimum, maximum))
            },
            valueRange = minimum..maximum
        )
    }
}

private fun formatNumber(
    value: Float,
    decimals: Int
): String {
    return "%.${decimals}f".format(value)
}