package com.example.researchos.transport.workflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Reusable component for displaying example intent calls to capability actions.
 *
 * Shows typical ODK intent formats that external callers can use to invoke
 * a capability. Users can select between different example variations to see
 * how to pass different parameters.
 */
@Composable
fun IntentExampleDropdown(
    capabilityId: String,
    examples: List<IntentExample> = listOf(
        IntentExample(
            label = "Basic invocation",
            description = "Minimal intent with just the action and basic parameters",
            intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='$capabilityId')"
        )
    )
) {
    var selectedIndex by remember { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(false) }

    if (examples.isEmpty()) return

    val selected = examples[selectedIndex]

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Example ODK Intent Call",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Dropdown for selecting example
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp)
                )
                .clickable { expanded = true }
                .padding(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = selected.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("▼", modifier = Modifier.padding(start = 8.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            examples.forEachIndexed { index, example ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = example.label,
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = example.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        selectedIndex = index
                        expanded = false
                    }
                )
            }
        }

        // Display the selected example
        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
        ) {
            Column {
                Text(
                    text = "Intent action string:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = selected.intentUri,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )

                if (selected.extras.isNotEmpty()) {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = "Intent extras:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    selected.extras.forEach { (key, value) ->
                        Text(
                            text = "$key = \"$value\"",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                if (selected.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        text = selected.notes,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
/**
 * Represents a single example intent call for a capability.
 */
data class IntentExample(
    val label: String,
    val description: String,
    val intentUri: String,
    val extras: Map<String, String> = emptyMap(),
    val notes: String = ""
)
