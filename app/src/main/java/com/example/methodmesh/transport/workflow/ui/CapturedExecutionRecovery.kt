package com.example.methodmesh.transport.workflow.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** No result actions or automatic execution when the original evidence is unavailable. */
@Composable
internal fun CapturedExecutionRecovery(
    onRestart: () -> Unit,
    onClose: () -> Unit,
    restartLabel: String = "Start new run"
) {
    Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text("Captured result unavailable", style = MaterialTheme.typography.headlineSmall)
        Text("The original record could not be restored after restart. Start a new run; no replacement record will be created automatically.")
        Button(onClick = onRestart, modifier = Modifier.fillMaxWidth()) { Text(restartLabel) }
        OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Close") }
    }
}
