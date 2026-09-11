package com.example.methodmesh.modules.signals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.platform.sensors.PhoneSensorRepository
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONArray
import java.util.Locale
import kotlin.math.sqrt

object SignalSensorScopeCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignalSensorScopeMethod.id
    override val title = "Signal sensor scope"
    override val description = "Inspect live light, magnetic, motion, pressure and proximity sensor values, then freeze a diagnostic snapshot."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        var sensorId by rememberSaveable(context.action.canonicalId) { mutableStateOf(context.action.settings.signalSetting("sensor", "light")) }
        var committedJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var status by rememberSaveable(context.action.canonicalId) { mutableStateOf("Live sensor values are not stored until Commit.") }
        var exportStatus by rememberSaveable(context.action.canonicalId) { mutableStateOf("") }
        val trace = remember(sensorId) { mutableStateListOf<Float>() }

        SignalActiveSessionOrientationGuard(true)
        DisposableEffect(androidContext) {
            PhoneSensorRepository.start(androidContext)
            onDispose { PhoneSensorRepository.stop() }
        }
        LaunchedEffect(sensorId) { context.onSettingsChanged(mapOf("sensor" to sensorId)) }

        val reading = PhoneSensorRepository.readings[sensorId]
        val values = reading?.values.orEmpty()
        val available = reading?.available == true && values.isNotEmpty()
        val unit = reading?.unit.orEmpty()
        val magnitude = if (values.size >= 2) sqrt(values.take(3).sumOf { (it * it).toDouble() }) else values.firstOrNull()?.toDouble()
        val accent = sensorAccent(sensorId)
        LaunchedEffect(sensorId, values) {
            magnitude?.takeIf { it.isFinite() }?.toFloat()?.let { next ->
                if (trace.size >= 120) trace.removeAt(0)
                trace.add(next)
            }
        }

        fun format(value: Double?): String = value?.takeIf { it.isFinite() }?.let { "%.4f".format(Locale.US, it) }.orEmpty()
        fun liveResultText(): String {
            if (!available) return "${sensorLabel(sensorId)} unavailable"
            val body = if (values.size == 1) format(values[0].toDouble()) else values.take(3).joinToString(", ") { format(it.toDouble()) }
            return "${sensorLabel(sensorId)}: $body${if (unit.isBlank()) "" else " $unit"}"
        }

        fun commit() {
            if (!available) {
                status = "This sensor is not available or has not produced a reading yet."
                return
            }
            val jsonValues = JSONArray().also { array -> values.forEach { array.put(it.toDouble()) } }.toString()
            val committedValues = mapOf(
                SignalSensorScopeFields.RESULT to liveResultText(),
                SignalSensorScopeFields.SENSOR_ID to sensorId,
                SignalSensorScopeFields.VALUE_0 to format(values.getOrNull(0)?.toDouble()),
                SignalSensorScopeFields.VALUE_1 to format(values.getOrNull(1)?.toDouble()),
                SignalSensorScopeFields.VALUE_2 to format(values.getOrNull(2)?.toDouble()),
                SignalSensorScopeFields.MAGNITUDE to format(magnitude),
                SignalSensorScopeFields.UNIT to unit,
                SignalSensorScopeFields.ACCURACY to reading?.accuracy?.toString().orEmpty(),
                SignalSensorScopeFields.VALUES_JSON to jsonValues,
                SignalSensorScopeFields.STATUS to "captured",
                SignalSensorScopeFields.ERROR to ""
            )
            committedJson = fieldsJson(committedValues)
            status = "Committed. Live values continue to update; recommit to replace the frozen snapshot."
            val result = signalResult(As100SignalSensorScopeMethod, context, committedValues)
            if (context.submitsImmediately) onConfirmed(result)
        }

        val committedFields = fieldsFromJson(committedJson)
        val committedResult = remember(committedJson) { committedFields.takeIf { it.isNotEmpty() }?.let { signalResult(As100SignalSensorScopeMethod, context, it) } }
        val committedFullJson = remember(committedJson) { fullJson(committedResult) }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SignalInstrumentPanel(
                kicker = "LIVE SENSOR SCOPE",
                title = sensorLabel(sensorId),
                accent = accent,
                badge = when { !available -> "Unavailable"; else -> "Live" }
            ) {
                Text(
                    format(magnitude).ifBlank { "—" },
                    color = if (available) SignalText else SignalMuted,
                    style = MaterialTheme.typography.displaySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(enabled = available) {
                        copySignalValue(androidContext, if (values.size >= 2) "magnitude" else sensorLabel(sensorId), format(magnitude))
                    }
                )
                Text(
                    if (unit.isBlank()) "CURRENT MAGNITUDE / VALUE" else "$unit  •  CURRENT MAGNITUDE / VALUE",
                    color = SignalMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                SignalScopeTrace(trace, accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (values.size >= 2) {
                        listOf("X", "Y", "Z").forEachIndexed { index, label ->
                            val text = format(values.getOrNull(index)?.toDouble())
                            SignalTelemetryTile(label, text, accent, Modifier.weight(1f), enabled = text.isNotBlank()) {
                                copySignalValue(androidContext, label, text)
                            }
                        }
                    } else {
                        SignalTelemetryTile("VALUE", format(values.firstOrNull()?.toDouble()), accent, Modifier.weight(1f), enabled = available) {
                            copySignalValue(androidContext, sensorLabel(sensorId), format(values.firstOrNull()?.toDouble()))
                        }
                        SignalTelemetryTile("UNIT", unit.ifBlank { "—" }, accent, Modifier.weight(1f))
                        SignalTelemetryTile("ACCURACY", reading?.accuracy?.toString().orEmpty(), accent, Modifier.weight(1f))
                    }
                }
                Text(
                    when {
                        reading == null -> "Waiting for sensor repository…"
                        !reading.available -> "Sensor unavailable on this phone"
                        else -> "Android accuracy ${reading.accuracy ?: "not reported"} • tap live values to copy"
                    },
                    color = SignalMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(onClick = ::commit, enabled = available, modifier = Modifier.fillMaxWidth()) { Text(if (committedResult == null) "Commit snapshot" else "Recommit snapshot") }

            if (committedResult != null && !context.submitsImmediately) {
                SignalCommittedCard(
                    title = "Committed sensor snapshot",
                    primaryLabel = "sensor result",
                    primaryValue = committedFields[SignalSensorScopeFields.RESULT].orEmpty(),
                    fields = listOf(
                        "Sensor" to committedFields[SignalSensorScopeFields.SENSOR_ID].orEmpty(),
                        "Magnitude" to committedFields[SignalSensorScopeFields.MAGNITUDE].orEmpty(),
                        "Unit" to committedFields[SignalSensorScopeFields.UNIT].orEmpty(),
                        "Accuracy" to committedFields[SignalSensorScopeFields.ACCURACY].orEmpty()
                    ),
                    status = exportStatus,
                    onCopy = { label, value -> copySignalValue(androidContext, label, value) },
                    onShare = { includeFullJson -> exportStatus = shareSignalText(androidContext, "Share sensor snapshot", committedFields[SignalSensorScopeFields.RESULT].orEmpty(), if (includeFullJson) committedFullJson else "") ?: "" },
                    onSave = { includeFullJson -> exportStatus = saveSignalText(androidContext, "signal_sensor_snapshot", committedFields[SignalSensorScopeFields.RESULT].orEmpty(), if (includeFullJson) committedFullJson else "") },
                    onDone = { includeFullJson -> finishSignalResult(context, androidContext, committedResult, onConfirmed) { saveSignalText(androidContext, "signal_sensor_snapshot", committedFields[SignalSensorScopeFields.RESULT].orEmpty(), if (includeFullJson) committedFullJson else "") } }
                )
            }

            if (context.settingShouldBeShown("sensor")) {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Sensor", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("light", "magnetometer", "accelerometer").forEach { id -> FilterChip(selected = sensorId == id, onClick = { sensorId = id }, label = { Text(shortSensorLabel(id)) }) }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("gyroscope", "pressure", "proximity").forEach { id -> FilterChip(selected = sensorId == id, onClick = { sensorId = id }, label = { Text(shortSensorLabel(id)) }) }
                        }
                    }
                }
            }

            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (committedResult == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (context.stepNumber > 1) OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Back") }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
                }
            }
        }
    }
}

private fun sensorLabel(id: String): String = when (id) {
    "light" -> "Ambient light"
    "magnetometer" -> "Magnetic field"
    "accelerometer" -> "Acceleration"
    "gyroscope" -> "Angular velocity"
    "pressure" -> "Pressure"
    "proximity" -> "Proximity"
    else -> id
}

private fun shortSensorLabel(id: String): String = when (id) {
    "magnetometer" -> "Magnetic"
    "accelerometer" -> "Accel"
    "gyroscope" -> "Gyro"
    else -> id.replaceFirstChar { it.uppercase() }
}


private fun sensorAccent(id: String) = when (id) {
    "light" -> SignalAmber
    "magnetometer" -> SignalMagenta
    "accelerometer" -> SignalCyan
    "gyroscope" -> SignalViolet
    "pressure" -> SignalGreen
    "proximity" -> SignalAmber
    else -> SignalCyan
}
