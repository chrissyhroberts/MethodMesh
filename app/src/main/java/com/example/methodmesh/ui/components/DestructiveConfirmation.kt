package com.example.methodmesh.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Shared confirmation for genuinely destructive operations.
 *
 * Reversible actions such as pause, disable, hide or archive should not use this.
 */
@Composable
fun MethodMeshDestructiveConfirmation(
    title: String,
    objectName: String? = null,
    consequence: String? = null,
    confirmLabel: String = "Delete",
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            val message = buildString {
                objectName?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append("\n\n")
                }
                consequence?.takeIf { it.isNotBlank() }?.let { append(it) }
            }.ifBlank { "This action cannot be undone." }
            Text(message)
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        }
    )
}
