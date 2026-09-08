package com.example.methodmesh.modules.sensorread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.modules.sensorprovisioner.extractJsonObject
import org.json.JSONArray
import org.json.JSONObject

@Composable
internal fun LiveSensorPanel(
    samples: List<String>,
    frozen: Boolean,
    refreshMs: Long,
    onFreeze: () -> Unit,
    onResume: () -> Unit,
    onUseCurrent: () -> Unit,
    onUseSummary: () -> Unit,
    onUseTrace: () -> Unit
) {
    val parsed = samples.mapNotNull(::extractJsonObject)
    val latest = parsed.lastOrNull() ?: return
    val previous = parsed.dropLast(1).lastOrNull()

    val latestSourceTime = latest.optLongOrNull("sample_time_ms")
    val previousSourceTime = previous?.optLongOrNull("sample_time_ms")

    val sourceChanged = when {
        latestSourceTime == null || previousSourceTime == null -> null
        else -> latestSourceTime != previousSourceTime
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (frozen) "FROZEN" else "LIVE ●",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "${samples.size} reading${if (samples.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Text(
                buildList {
                    add("refresh ${formatRefresh(refreshMs)}")
                    latestSourceTime?.let { add("source t=$it ms") }
                    sourceChanged?.let {
                        add(if (it) "source updated" else "source unchanged")
                    }
                }.joinToString(" · "),
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(10.dp))

            visibleFields(latest).forEach { (key, rawValue) ->
                LiveSensorValueRow(
                    label = humanLabel(key),
                    value = displayValue(key, rawValue)
                )
            }

            Spacer(Modifier.height(12.dp))

            if (!frozen) {
                Button(
                    onClick = onFreeze,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Freeze")
                }
            } else {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Resume live")
                }

                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onUseCurrent,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use current reading")
                    }
                    OutlinedButton(
                        onClick = onUseSummary,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use window summary")
                    }
                    OutlinedButton(
                        onClick = onUseTrace,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use trace")
                    }
                }
            }
        }
    }
}

@Composable
private fun LiveSensorValueRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

private val hiddenLiveKeys = setOf(
    "methodmesh_sensor_reading_version",
    "device_id",
    "device_name",
    "firmware_version",
    "sensor_profile",
    "sensor_type",
    "sensor_id",
    "status",
    "sample_time_ms",
    "payload_sha256",
    "installed_sensor_profile",
    "active_sensor_profile",
    "image_profile"
)

private fun visibleFields(json: JSONObject): List<Pair<String, Any?>> {
    val keys = buildList {
        val iterator = json.keys()
        while (iterator.hasNext()) add(iterator.next())
    }

    return keys
        .filterNot { it in hiddenLiveKeys }
        .mapNotNull { key ->
            val value = json.opt(key)
            if (
                value == null ||
                value == JSONObject.NULL ||
                value is JSONObject ||
                value is JSONArray
            ) {
                null
            } else {
                key to value
            }
        }
}

private fun humanLabel(key: String): String =
    key
        .removeSuffix("_cm")
        .removeSuffix("_pct")
        .removeSuffix("_ms")
        .replace('_', ' ')
        .trim()
        .replaceFirstChar { it.titlecase() }

private fun displayValue(key: String, value: Any?): String {
    val base = when (value) {
        is Boolean -> if (value) "TRUE" else "FALSE"
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
        is Float -> if (value % 1f == 0f) value.toLong().toString() else "%.2f".format(value)
        else -> value?.toString().orEmpty()
    }

    val unit = when {
        key.endsWith("_cm") -> " cm"
        key.endsWith("_pct") -> " %"
        key.endsWith("_ms") -> " ms"
        key.endsWith("_c") && key.contains("temperature") -> " °C"
        else -> ""
    }

    return base + unit
}

private fun formatRefresh(refreshMs: Long): String =
    if (refreshMs % 1000L == 0L) "${refreshMs / 1000L}s" else "${refreshMs}ms"

private fun JSONObject.optLongOrNull(key: String): Long? {
    if (!has(key) || isNull(key)) return null
    return when (val value = opt(key)) {
        is Number -> value.toLong()
        is String -> value.toLongOrNull()
        else -> null
    }
}
