package com.example.methodmesh.modules.gpstargetnavigator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val NavigatorFace = Color(0xFF171613)
private val NavigatorFaceLift = Color(0xFF23211C)
private val NavigatorIvory = Color(0xFFF4EEDC)
private val NavigatorBrass = Color(0xFFC7A861)
private val NavigatorBrassMuted = Color(0xFF8C7746)
private val NavigatorNorth = Color(0xFFB8413B)
private val NavigatorSouth = Color(0xFFE6DFC9)
private val NavigatorTarget = Color(0xFF79A7D8)
private val NavigatorArrived = Color(0xFF68C08B)

@Composable
internal fun NavigationTargetCard(
    targetName: String,
    targetPlusCode: String,
    targetLatitude: Float,
    targetLongitude: Float,
    arrivalRadiusMeters: Float,
    onChangeLocation: (() -> Unit)? = null
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TARGET LOCATION", style = MaterialTheme.typography.labelMedium)
                    Text(
                        targetName.ifBlank { "Target location" },
                        modifier = Modifier.clickable {
                            copyNavigationValue(context, targetName.ifBlank { "Target location" })
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "±${arrivalRadiusMeters.roundToInt()} m",
                        modifier = Modifier
                            .clickable { copyNavigationValue(context, formatRawFloat(arrivalRadiusMeters)) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            CopyableNavigationValue(
                label = "Latitude",
                displayValue = formatCoordinateValue(targetLatitude),
                clipboardValue = formatCoordinateValue(targetLatitude),
                context = context,
                monospace = true
            )
            CopyableNavigationValue(
                label = "Longitude",
                displayValue = formatCoordinateValue(targetLongitude),
                clipboardValue = formatCoordinateValue(targetLongitude),
                context = context,
                monospace = true
            )
            if (targetPlusCode.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                CopyableNavigationValue(
                    label = "Plus Code",
                    displayValue = targetPlusCode,
                    clipboardValue = targetPlusCode,
                    context = context,
                    monospace = true
                )
            }
            if (onChangeLocation != null) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onChangeLocation
                ) {
                    Text("Change location")
                }
            }
        }
    }
}

@Composable
internal fun NavigationHeroCard(
    distanceMeters: Float,
    bearingDegrees: Float,
    headingDegrees: Float?,
    relativeBearingDegrees: Float,
    accuracyMeters: Float,
    currentLatitude: Float,
    currentLongitude: Float,
    hasLocationFix: Boolean,
    hasHeading: Boolean,
    arrived: Boolean,
    lifecycleLabel: String
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (arrived) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "DISTANCE TO TARGET",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                ) {
                    Text(
                        lifecycleLabel.uppercase(),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                if (hasLocationFix) formatNavigationDistance(distanceMeters) else "Waiting for GPS",
                modifier = if (hasLocationFix) Modifier.clickable {
                    copyNavigationValue(context, formatRawFloat(distanceMeters))
                } else Modifier,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                when {
                    arrived -> "Inside arrival radius"
                    hasHeading -> arTurnInstruction(relativeBearingDegrees, false)
                    else -> "Waiting for compass heading"
                },
                modifier = if (hasHeading) Modifier.clickable {
                    copyNavigationValue(context, formatRawFloat(relativeBearingDegrees))
                } else Modifier,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
            ) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("CURRENT LOCATION", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        if (hasLocationFix && accuracyMeters > 0f) {
                            Text(
                                "GPS ±${accuracyMeters.roundToInt()} m",
                                modifier = Modifier.clickable {
                                    copyNavigationValue(context, formatRawFloat(accuracyMeters))
                                },
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    CopyableNavigationValue(
                        label = "Latitude",
                        displayValue = if (hasLocationFix) formatCoordinateValue(currentLatitude) else "Waiting",
                        clipboardValue = if (hasLocationFix) formatCoordinateValue(currentLatitude) else "",
                        context = context,
                        monospace = true,
                        enabled = hasLocationFix
                    )
                    CopyableNavigationValue(
                        label = "Longitude",
                        displayValue = if (hasLocationFix) formatCoordinateValue(currentLongitude) else "Waiting",
                        clipboardValue = if (hasLocationFix) formatCoordinateValue(currentLongitude) else "",
                        context = context,
                        monospace = true,
                        enabled = hasLocationFix
                    )
                }
            }

            if (hasLocationFix) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Target bearing ${bearingDegrees.roundToInt()}°",
                    modifier = Modifier.clickable {
                        copyNavigationValue(context, formatRawFloat(bearingDegrees))
                    },
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
internal fun NavigationCompassDashboard(
    bearingDegrees: Float,
    headingDegrees: Float?,
    relativeBearingDegrees: Float,
    hasLocationFix: Boolean,
    arrived: Boolean
) {
    val context = LocalContext.current
    val targetAccent = if (arrived) NavigatorArrived else NavigatorTarget
    val heading = headingDegrees?.let(::normaliseNavigationDegrees)
    val target = normaliseNavigationDegrees(bearingDegrees)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = NavigatorFace)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "TARGET COMPASS",
                        color = NavigatorBrass,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                    Text(
                        if (hasLocationFix) "Target bearing ${target.roundToInt()}°" else "Waiting for GPS target vector",
                        color = NavigatorIvory.copy(alpha = 0.68f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = when {
                        arrived -> NavigatorArrived.copy(alpha = 0.18f)
                        hasLocationFix && heading != null -> NavigatorTarget.copy(alpha = 0.18f)
                        else -> NavigatorIvory.copy(alpha = 0.08f)
                    }
                ) {
                    Text(
                        when {
                            arrived -> "ARRIVED"
                            hasLocationFix && heading != null -> "LIVE"
                            !hasLocationFix -> "GPS"
                            else -> "HEADING"
                        },
                        color = NavigatorIvory.copy(alpha = 0.90f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                heading?.let { "${navigationCardinal(it)} ${it.roundToInt()}°" } ?: "—°",
                modifier = Modifier.clickable(enabled = heading != null) {
                    heading?.let { copyNavigationValue(context, formatRawFloat(it)) }
                },
                color = NavigatorIvory,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
            Text(
                when {
                    arrived -> "Target reached"
                    heading == null -> "Waiting for orientation sensor"
                    hasLocationFix -> arTurnInstruction(relativeBearingDegrees, false)
                    else -> "Set a target and acquire GPS"
                },
                modifier = Modifier.clickable(enabled = heading != null && hasLocationFix) {
                    copyNavigationValue(context, formatRawFloat(relativeBearingDegrees))
                },
                color = if (arrived) NavigatorArrived else NavigatorIvory.copy(alpha = 0.70f),
                fontWeight = if (arrived) FontWeight.Bold else FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(282.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val centre = center
                    val radius = size.minDimension * 0.455f
                    val innerRadius = radius * 0.81f

                    drawCircle(NavigatorFaceLift, radius + 10f, centre)
                    drawCircle(NavigatorBrassMuted, radius + 2f, centre, style = Stroke(width = 4f))
                    drawCircle(NavigatorBrass.copy(alpha = 0.52f), innerRadius, centre, style = Stroke(width = 2f))

                    for (degree in 0 until 360 step 5) {
                        val radians = Math.toRadians((degree - 90).toDouble())
                        val major = degree % 30 == 0
                        val medium = !major && degree % 10 == 0
                        val tick = when {
                            major -> 23f
                            medium -> 15f
                            else -> 8f
                        }
                        val outer = Offset(
                            centre.x + cos(radians).toFloat() * radius,
                            centre.y + sin(radians).toFloat() * radius
                        )
                        val inner = Offset(
                            centre.x + cos(radians).toFloat() * (radius - tick),
                            centre.y + sin(radians).toFloat() * (radius - tick)
                        )
                        drawLine(
                            color = if (major) NavigatorBrass else NavigatorIvory.copy(alpha = if (medium) 0.58f else 0.30f),
                            start = inner,
                            end = outer,
                            strokeWidth = if (major) 3.2f else if (medium) 2.2f else 1.25f,
                            cap = StrokeCap.Round
                        )
                    }

                    heading?.let { liveHeading ->
                        // Magnetic north needle, visually subordinate to the target vector.
                        rotate(-liveHeading, pivot = centre) {
                            val northTip = Offset(centre.x, centre.y - innerRadius + 20f)
                            val southTip = Offset(centre.x, centre.y + innerRadius - 20f)
                            val northNeedle = Path().apply {
                                moveTo(centre.x - 10f, centre.y + 9f)
                                lineTo(northTip.x, northTip.y)
                                lineTo(centre.x + 10f, centre.y + 9f)
                                close()
                            }
                            val southNeedle = Path().apply {
                                moveTo(centre.x - 8f, centre.y - 7f)
                                lineTo(southTip.x, southTip.y)
                                lineTo(centre.x + 8f, centre.y - 7f)
                                close()
                            }
                            drawPath(northNeedle, NavigatorNorth)
                            drawPath(southNeedle, NavigatorSouth.copy(alpha = 0.72f))
                        }

                        if (hasLocationFix) {
                            // The primary arrow is the signed target direction relative to the phone heading.
                            rotate(relativeBearingDegrees, pivot = centre) {
                                drawLine(
                                    color = targetAccent,
                                    start = Offset(centre.x, centre.y + innerRadius * 0.22f),
                                    end = Offset(centre.x, centre.y - innerRadius + 11f),
                                    strokeWidth = 11f,
                                    cap = StrokeCap.Round
                                )
                                val arrowHead = Path().apply {
                                    moveTo(centre.x, centre.y - innerRadius + 4f)
                                    lineTo(centre.x - 18f, centre.y - innerRadius + 34f)
                                    lineTo(centre.x + 18f, centre.y - innerRadius + 34f)
                                    close()
                                }
                                drawPath(arrowHead, targetAccent)
                            }
                        }
                    }

                    drawCircle(NavigatorBrass, 14f, centre)
                    drawCircle(NavigatorFace, 7f, centre)

                    val marker = Path().apply {
                        moveTo(centre.x, centre.y - radius - 3f)
                        lineTo(centre.x - 10f, centre.y - radius + 15f)
                        lineTo(centre.x + 10f, centre.y - radius + 15f)
                        close()
                    }
                    drawPath(marker, NavigatorBrass)
                }

                Text("N", color = NavigatorNorth, modifier = Modifier.align(Alignment.TopCenter).padding(top = 25.dp), fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("E", color = NavigatorIvory, modifier = Modifier.align(Alignment.CenterEnd).padding(end = 28.dp), fontWeight = FontWeight.Bold)
                Text("S", color = NavigatorIvory, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 25.dp), fontWeight = FontWeight.Bold)
                Text("W", color = NavigatorIvory, modifier = Modifier.align(Alignment.CenterStart).padding(start = 27.dp), fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DarkNavigationValueTile(
                    label = "HEADING",
                    value = heading?.let { "${it.roundToInt()}°" } ?: "—",
                    modifier = Modifier.weight(1f),
                    enabled = heading != null,
                    onClick = { heading?.let { copyNavigationValue(context, formatRawFloat(it)) } }
                )
                DarkNavigationValueTile(
                    label = "TARGET",
                    value = if (hasLocationFix) "${target.roundToInt()}°" else "—",
                    modifier = Modifier.weight(1f),
                    enabled = hasLocationFix,
                    onClick = { if (hasLocationFix) copyNavigationValue(context, formatRawFloat(target)) }
                )
                DarkNavigationValueTile(
                    label = "TURN",
                    value = if (heading != null && hasLocationFix) "${signedNavigationDegrees(relativeBearingDegrees).roundToInt()}°" else "—",
                    modifier = Modifier.weight(1f),
                    enabled = heading != null && hasLocationFix,
                    onClick = { if (heading != null && hasLocationFix) copyNavigationValue(context, formatRawFloat(relativeBearingDegrees)) },
                    accent = if (arrived) NavigatorArrived else NavigatorIvory
                )
            }
            Text(
                "Blue arrow points to the target • red needle points north • tap values to copy",
                color = NavigatorIvory.copy(alpha = 0.46f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun DarkNavigationValueTile(
    label: String,
    value: String,
    modifier: Modifier,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Color = NavigatorIvory
) {
    Surface(
        modifier = modifier
            .border(1.dp, NavigatorBrassMuted.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color.Black.copy(alpha = 0.16f)
    ) {
        Column(
            Modifier.padding(horizontal = 9.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, color = NavigatorBrass, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = accent, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun NavigationTelemetryGrid(
    currentLatitude: Float,
    currentLongitude: Float,
    accuracyMeters: Float,
    bearingDegrees: Float,
    headingDegrees: Float?,
    relativeBearingDegrees: Float,
    updateCount: Int,
    hasLocationFix: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationMetricCard(
                label = "Latitude",
                value = if (hasLocationFix) formatCoordinateValue(currentLatitude) else "Waiting",
                clipboardValue = if (hasLocationFix) formatCoordinateValue(currentLatitude) else null,
                modifier = Modifier.weight(1f),
                monospace = true
            )
            NavigationMetricCard(
                label = "Longitude",
                value = if (hasLocationFix) formatCoordinateValue(currentLongitude) else "Waiting",
                clipboardValue = if (hasLocationFix) formatCoordinateValue(currentLongitude) else null,
                modifier = Modifier.weight(1f),
                monospace = true
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationMetricCard(
                label = "Accuracy",
                value = if (accuracyMeters > 0f) "±${accuracyMeters.roundToInt()} m" else "Waiting",
                clipboardValue = if (accuracyMeters > 0f) formatRawFloat(accuracyMeters) else null,
                modifier = Modifier.weight(1f)
            )
            NavigationMetricCard(
                label = "Updates",
                value = updateCount.toString(),
                clipboardValue = updateCount.toString(),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavigationMetricCard(
                label = "Target bearing",
                value = "${bearingDegrees.roundToInt()}°",
                clipboardValue = if (hasLocationFix) formatRawFloat(bearingDegrees) else null,
                modifier = Modifier.weight(1f)
            )
            NavigationMetricCard(
                label = "Turn",
                value = headingDegrees?.let { "${relativeBearingDegrees.roundToInt()}°" } ?: "Waiting",
                clipboardValue = headingDegrees?.let { formatRawFloat(relativeBearingDegrees) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun NavigationMetricCard(
    label: String,
    value: String,
    clipboardValue: String?,
    modifier: Modifier = Modifier,
    monospace: Boolean = false
) {
    val context = LocalContext.current
    Card(
        modifier = modifier.clickable(enabled = clipboardValue != null) {
            clipboardValue?.let { copyNavigationValue(context, it) }
        }
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
            )
        }
    }
}

@Composable
internal fun CopyableNavigationValue(
    label: String,
    displayValue: String,
    clipboardValue: String,
    context: Context = LocalContext.current,
    monospace: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { if (enabled) copyNavigationValue(context, clipboardValue) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(104.dp), style = MaterialTheme.typography.labelMedium)
        Text(
            displayValue,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            textAlign = TextAlign.End
        )
    }
}

internal fun copyNavigationValue(context: Context, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh navigation value", value))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

internal fun formatNavigationDistance(distanceMeters: Float): String =
    if (distanceMeters >= 1000f) "%.2f km".format(distanceMeters / 1000f) else "${distanceMeters.roundToInt()} m"

internal fun formatCoordinateValue(value: Float): String = "%.6f".format(value)

internal fun formatRawFloat(value: Float): String = when {
    value.isNaN() || value.isInfinite() -> ""
    kotlin.math.abs(value - value.roundToInt()) < 0.0001f -> value.roundToInt().toString()
    else -> "%.6f".format(value).trimEnd('0').trimEnd('.')
}

private fun normaliseNavigationDegrees(value: Float): Float {
    val normalised = value % 360f
    return if (normalised < 0f) normalised + 360f else normalised
}

private fun signedNavigationDegrees(value: Float): Float {
    var signed = value % 360f
    if (signed > 180f) signed -= 360f
    if (signed <= -180f) signed += 360f
    return signed
}

private fun navigationCardinal(headingDegrees: Float): String {
    val names = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
    val index = ((normaliseNavigationDegrees(headingDegrees) + 22.5f) / 45f).toInt() % names.size
    return names[index]
}
