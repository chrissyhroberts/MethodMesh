package com.example.methodmesh.modules.aviation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Lightweight capability-owned chrome for aviation screens that opt into
 * CapabilityHostPresentation.Immersive.
 *
 * The generic MethodMesh immersive host deliberately supplies no Home button,
 * padding or scrolling. These screens therefore own an explicit exit route and
 * their own bounded layout without teaching the host anything about aviation.
 */
@Composable
internal fun AviationImmersiveTopBar(
    title: String,
    subtitle: String,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onExit: () -> Unit,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (canGoBack) {
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
            OutlinedButton(onClick = onExit) { Text("Exit") }
        }
    }
}
