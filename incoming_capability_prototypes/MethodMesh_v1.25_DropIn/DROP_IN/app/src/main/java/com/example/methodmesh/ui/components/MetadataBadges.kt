package com.example.methodmesh.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.modules.MaturityStatus

@Composable
fun MaturityBadge(
    maturity: MaturityStatus,
    modifier: Modifier = Modifier
) {
    val container = when (maturity) {
        MaturityStatus.Production -> MaterialTheme.colorScheme.primaryContainer
        MaturityStatus.Development -> MaterialTheme.colorScheme.secondaryContainer
        MaturityStatus.Experimental -> MaterialTheme.colorScheme.tertiaryContainer
    }
    val content = when (maturity) {
        MaturityStatus.Production -> MaterialTheme.colorScheme.onPrimaryContainer
        MaturityStatus.Development -> MaterialTheme.colorScheme.onSecondaryContainer
        MaturityStatus.Experimental -> MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Text(
            maturity.label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VersionAndMaturityBadges(
    version: String,
    maturity: MaturityStatus,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "v$version",
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
        MaturityBadge(maturity)
    }
}
