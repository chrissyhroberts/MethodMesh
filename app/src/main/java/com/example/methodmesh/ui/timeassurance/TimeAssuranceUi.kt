package com.example.methodmesh.ui.timeassurance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.timeassurance.ClockAssuranceRuntime
import com.example.methodmesh.core.timeassurance.ClockEvidenceSnapshot
import com.example.methodmesh.core.timeassurance.ClockEvidenceState
import com.example.methodmesh.core.timeassurance.TrustedTimeRefreshRegistry
import com.example.methodmesh.core.timeassurance.WallClockRelation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Presentation-only warning threshold. This is not a Clock Assurance policy. */
private const val SYSTEM_CLOCK_OFFSET_WARNING_MILLIS = 60_000L

@Composable
fun HomeTimeRecencyChip(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var snapshot by remember { mutableStateOf(readSnapshot()) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readSnapshot()
            delay(30_000L)
        }
    }
    val label = snapshot?.let(::homeLabel) ?: "Time · check"
    val systemClockOffsetMillis = snapshot?.let(::systemClockOffsetMillis)
    val driftWarning = systemClockOffsetMillis?.let {
        kotlin.math.abs(it) > SYSTEM_CLOCK_OFFSET_WARNING_MILLIS
    } == true

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (driftWarning) MaterialTheme.colorScheme.errorContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (driftWarning) MaterialTheme.colorScheme.onErrorContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TimeAssuranceWorkbenchPanel(
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf(readSnapshot()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    val provider = TrustedTimeRefreshRegistry.currentProvider()

    LaunchedEffect(Unit) {
        while (true) {
            snapshot = readSnapshot()
            delay(5_000L)
        }
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Time Assurance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Shared temporal evidence · policy-neutral",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                EvidenceStateBadge(snapshot)
            }

            Spacer(Modifier.height(14.dp))
            snapshot?.let { evidence ->
                EvidenceRow("Observed wall time", evidence.observedWallTimeIso)
                EvidenceRow("Trusted estimate", evidence.trustedEstimateIso ?: "Not available")
                if (evidence.trustedLowerBoundIso != null || evidence.trustedUpperBoundIso != null) {
                    EvidenceRow(
                        "Trusted bounds",
                        "${evidence.trustedLowerBoundIso ?: "?"}  →  ${evidence.trustedUpperBoundIso ?: "?"}"
                    )
                }
                EvidenceRow("Anchor age", evidence.anchorAgeMillis?.let(::formatAge) ?: "Not comparable")
                EvidenceRow("Anchor source", evidence.anchorSource ?: "None")
                EvidenceRow(
                    "System clock offset",
                    systemClockOffsetMillis(evidence)?.let(::formatSignedOffset) ?: "Not comparable"
                )
                EvidenceRow("Monotonic continuity", evidence.monotonicContinuity.wireValue.replace('_', ' '))
                EvidenceRow(
                    "Wall-clock relation",
                    buildString {
                        append(evidence.wallClockRelation.wireValue.replace('_', ' '))
                        evidence.wallClockOffsetFromTrustedIntervalMillis?.takeIf { it != 0L }?.let { offset ->
                            append(" · ")
                            append(if (offset > 0) "+" else "")
                            append(offset)
                            append(" ms")
                        }
                    }
                )
                EvidenceRow("Clock schema", "${evidence.schemaVersion} · ${evidence.clockModelVersion}")
                Spacer(Modifier.height(8.dp))
                Text(
                    evidence.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: Text(
                "Clock Assurance is not initialised.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    syncing = true
                    syncMessage = "Acquiring and validating trusted time…"
                    scope.launch {
                        val result = TrustedTimeRefreshRegistry.refresh()
                        syncing = false
                        syncMessage = result.message
                        snapshot = readSnapshot()
                    }
                },
                enabled = !syncing && provider != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (syncing) "Synchronizing…" else "Sync trusted time")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (provider == null) {
                    "No trusted-time provider is installed."
                } else {
                    "Uses ${provider.displayName}. This refreshes MethodMesh evidence; it does not set Android system time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            syncMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { snapshot = readSnapshot() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh view")
            }
        }
    }
}

@Composable
private fun EvidenceStateBadge(snapshot: ClockEvidenceSnapshot?) {
    val label = snapshot?.evidenceState?.wireValue?.replace('_', ' ') ?: "unavailable"
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun EvidenceRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            modifier = Modifier.width(150.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (label.contains("time", ignoreCase = true) || label.contains("schema", ignoreCase = true)) {
                FontFamily.Monospace
            } else FontFamily.Default
        )
    }
}

private fun readSnapshot(): ClockEvidenceSnapshot? =
    runCatching { ClockAssuranceRuntime.snapshot() }.getOrNull()

private fun homeLabel(snapshot: ClockEvidenceSnapshot): String = when (snapshot.evidenceState) {
    ClockEvidenceState.TRUSTED_INTERVAL_AVAILABLE -> {
        val age = snapshot.anchorAgeMillis?.let(::formatAge) ?: "?"
        val offset = systemClockOffsetMillis(snapshot)
        if (offset != null && kotlin.math.abs(offset) > SYSTEM_CLOCK_OFFSET_WARNING_MILLIS) {
            "Time · drift ${formatSignedOffset(offset)}"
        } else {
            val offsetLabel = offset?.let(::formatSignedOffset)
            if (offsetLabel == null) "Time · $age" else "Time · $age · $offsetLabel"
        }
    }
    ClockEvidenceState.WALL_CLOCK_ONLY -> "Time · unanchored"
    ClockEvidenceState.REBOOT_UNANCHORED -> "Time · reboot"
    ClockEvidenceState.INDETERMINATE -> "Time · check"
}

/**
 * Signed Android wall-clock offset from the trusted estimate.
 * Positive means Android is ahead; negative means Android is behind.
 * This is display evidence only and does not alter Clock Assurance policy.
 */
private fun systemClockOffsetMillis(snapshot: ClockEvidenceSnapshot): Long? {
    val trustedEstimate = snapshot.trustedEstimate ?: return null
    return snapshot.observedWallTime.toEpochMilli() - trustedEstimate.toEpochMilli()
}

private fun formatSignedOffset(milliseconds: Long): String {
    val sign = if (milliseconds >= 0L) "+" else "−"
    val magnitude = kotlin.math.abs(milliseconds)
    return when {
        magnitude < 1_000L -> "$sign${magnitude} ms"
        magnitude < 60_000L -> {
            val tenths = (magnitude + 50L) / 100L
            "$sign${tenths / 10L}.${tenths % 10L} s"
        }
        magnitude < 3_600_000L -> {
            val totalSeconds = magnitude / 1_000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            if (seconds == 0L) "$sign${minutes}m" else "$sign${minutes}m ${seconds}s"
        }
        else -> {
            val totalMinutes = magnitude / 60_000L
            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            if (minutes == 0L) "$sign${hours}h" else "$sign${hours}h ${minutes}m"
        }
    }
}

private fun formatAge(milliseconds: Long): String {
    if (milliseconds < 0L) return "?"
    val seconds = milliseconds / 1000L
    return when {
        seconds < 60L -> "${seconds}s"
        seconds < 3600L -> "${seconds / 60L}m"
        seconds < 86_400L -> "${seconds / 3600L}h"
        else -> "${seconds / 86_400L}d"
    }
}
