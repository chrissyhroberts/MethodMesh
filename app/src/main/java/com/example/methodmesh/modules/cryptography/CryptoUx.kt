package com.example.methodmesh.modules.cryptography

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Shared native UX rails for cryptography.
 *
 * These components deliberately describe user goals before algorithms. Standards and
 * implementation details remain available in provenance, docs and Expert controls.
 */
@Composable
fun CryptoGuidanceCard(
    goal: String,
    explanation: String,
    needs: String,
    result: String,
    caution: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(goal, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(explanation, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("You need", style = MaterialTheme.typography.labelLarge)
            Text(needs, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(6.dp))
            Text("You get", style = MaterialTheme.typography.labelLarge)
            Text(result, style = MaterialTheme.typography.bodySmall)
            caution?.let {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Important", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    Spacer(Modifier.height(14.dp))
}

@Composable
fun CryptoExpertToggle(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    OutlinedButton(onClick = { onExpandedChange(!expanded) }, modifier = Modifier.fillMaxWidth()) {
        Text(if (expanded) "Hide expert options" else "Expert options")
    }
    if (expanded) {
        Spacer(Modifier.height(8.dp))
        content()
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun CryptoChoiceRow(
    leftLabel: String,
    leftSelected: Boolean,
    onLeft: () -> Unit,
    rightLabel: String,
    rightSelected: Boolean,
    onRight: () -> Unit
) {
    Row(Modifier.fillMaxWidth()) {
        if (leftSelected) {
            Button(onClick = onLeft, modifier = Modifier.weight(1f)) { Text(leftLabel) }
        } else {
            OutlinedButton(onClick = onLeft, modifier = Modifier.weight(1f)) { Text(leftLabel) }
        }
        Spacer(Modifier.width(8.dp))
        if (rightSelected) {
            Button(onClick = onRight, modifier = Modifier.weight(1f)) { Text(rightLabel) }
        } else {
            OutlinedButton(onClick = onRight, modifier = Modifier.weight(1f)) { Text(rightLabel) }
        }
    }
}

@Composable
fun CryptoStatusCard(title: String, status: String, detail: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(status, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
fun CryptoRouteCard(
    title: String,
    question: String,
    actions: String,
    note: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(question, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Text(actions, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(note, style = MaterialTheme.typography.bodySmall)
        }
    }
    Spacer(Modifier.height(10.dp))
}
