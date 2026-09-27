package com.example.methodmesh.modules.conversions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.methodmesh.MainActivity
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.protocols.PresetResultAction
import com.example.methodmesh.transport.OutputExportRepository
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.LocalDate

object ConversionsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ConversionsMethod.ID
    override val title = "Conversions"
    override val description = "Offline unit conversion and practical calculations."
    override val hostPresentation = CapabilityHostPresentation.Immersive

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        var category by rememberSaveable {
            mutableStateOf(context.action.settings["category"] ?: context.action.settings["input_category"] ?: "length")
        }
        var value by rememberSaveable {
            mutableStateOf(context.action.settings["value"] ?: context.action.settings["input_value"] ?: "")
        }
        var value2 by rememberSaveable {
            mutableStateOf(context.action.settings["value2"] ?: context.action.settings["input_value2"] ?: "")
        }
        var value3 by rememberSaveable {
            mutableStateOf(context.action.settings["value3"] ?: context.action.settings["input_value3"] ?: "")
        }
        var from by rememberSaveable {
            mutableStateOf(context.action.settings["from_unit"] ?: context.action.settings["input_from_unit"] ?: "")
        }
        var to by rememberSaveable {
            mutableStateOf(context.action.settings["to_unit"] ?: context.action.settings["input_to_unit"] ?: "")
        }
        var operation by rememberSaveable {
            mutableStateOf(context.action.settings["operation"] ?: context.action.settings["input_operation"] ?: "")
        }
        var date1 by rememberSaveable {
            mutableStateOf(context.action.settings["date1"] ?: context.action.settings["input_date1"] ?: "")
        }
        var date2 by rememberSaveable {
            mutableStateOf(context.action.settings["date2"] ?: context.action.settings["input_date2"] ?: "")
        }
        var shape by rememberSaveable {
            mutableStateOf(context.action.settings["shape"] ?: context.action.settings["input_shape"] ?: "rectangle")
        }
        var decimalPlaces by rememberSaveable {
            mutableStateOf((context.action.settings["decimal_places"] ?: context.action.settings["input_decimal_places"])?.toIntOrNull()?.coerceIn(0, 10) ?: 4)
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var workingValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var committedValuesJson by rememberSaveable(context.action.canonicalId) { mutableStateOf<String?>(null) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val unitChoices = when (category) {
            "temperature" -> listOf("C", "F", "K")
            "number" -> NumberRepresentation.ids
            else -> As100ConversionsMethod.unitsByCategory[category]?.keys?.toList().orEmpty()
        }

        fun settings() = mapOf(
            "category" to category,
            "value" to value,
            "value2" to value2,
            "value3" to value3,
            "from_unit" to from,
            "to_unit" to to,
            "operation" to operation,
            "date1" to date1,
            "date2" to date2,
            "shape" to shape,
            "decimal_places" to decimalPlaces.toString()
        )

        fun calculateCurrent(): ExecutionResult {
            val currentSettings = settings()
            val values = As100ConversionsMethod.calculate(currentSettings)
            workingValues = values
            val request = As100ConversionsMethod.request(
                action = capabilityId,
                context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + currentSettings,
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100ConversionsMethod.result(request, values, context.request.invocationContext)
        }

        fun runAndMaybeSubmit() {
            val calculated = calculateCurrent()
            result = calculated
            if (context.submitsImmediately && workingValues[ConversionFields.STATUS] == "succeeded") {
                onConfirmed(calculated)
            }
        }

        fun commitCurrent() {
            val current = result ?: return
            if (workingValues[ConversionFields.STATUS] != "succeeded") return
            if (context.submitsImmediately) {
                onConfirmed(current)
            } else {
                // Freeze both the canonical result and its human readout. The working
                // calculator remains separate until Edit / new run is chosen.
                committedResult = current
                committedValuesJson = JSONObject(workingValues).toString()
            }
        }

        LaunchedEffect(category) {
            if (unitChoices.isNotEmpty()) {
                if (from !in unitChoices) from = unitChoices.first()
                if (to !in unitChoices) to = unitChoices.getOrElse(1) { unitChoices.first() }
            }
            val validOperations = operationsFor(category, shape)
            if (validOperations.isNotEmpty()) {
                if (operation !in validOperations) operation = validOperations.first()
            } else if (category in unitConversionCategories || category == "number" || category == "date_difference" || category == "age") {
                operation = "convert"
            }
        }

        LaunchedEffect(shape) {
            if (category == "geometry") {
                val valid = operationsFor(category, shape)
                if (operation !in valid) operation = valid.first()
            }
        }

        LaunchedEffect(category, value, value2, value3, from, to, operation, date1, date2, shape, decimalPlaces) {
            val currentSettings = settings()
            context.onSettingsChanged(currentSettings)

            if (inputsReady(category, operation, shape, value, value2, value3, date1, date2, from)) {
                result = calculateCurrent()
            } else {
                result = null
                workingValues = emptyMap()
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && !launched) {
                launched = true
                if (inputsReady(category, operation, shape, value, value2, value3, date1, date2, from)) {
                    runAndMaybeSubmit()
                }
            }
        }

        val restoredCommittedValues = remember(committedValuesJson) {
            committedValuesJson?.let { json ->
                runCatching {
                    val obj = JSONObject(json)
                    obj.keys().asSequence().associateWith { key -> obj.optString(key) }
                }.getOrNull()
            }.orEmpty()
        }
        val restoredCommittedResult = remember(committedValuesJson, category, value, value2, value3, from, to, operation, date1, date2, shape, decimalPlaces) {
            if (committedValuesJson.isNullOrBlank() || restoredCommittedValues.isEmpty()) {
                null
            } else {
                val restoredSettings = settings()
                val request = As100ConversionsMethod.request(
                    action = capabilityId,
                    context = context.request.invocationContext.asMap(capabilityId) + context.action.settings + restoredSettings,
                    signals = emptyList(),
                    inputs = emptyList()
                )
                As100ConversionsMethod.result(request, restoredCommittedValues, context.request.invocationContext)
            }
        }

        // Immersive capability surface: the host gives this screen the real viewport.
        // System-bar padding remains capability-owned so header actions and the keypad
        // never sit under Android chrome.
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = InstrumentBackground,
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val frozenResult = committedResult ?: restoredCommittedResult
                InstrumentHeader(
                    category = category,
                    canGoBack = context.stepNumber > 1 && frozenResult == null,
                    hasResult = frozenResult != null || (result != null && workingValues[ConversionFields.STATUS] == "succeeded"),
                    committed = frozenResult != null,
                    onBack = onBack,
                    onCancel = onCancel,
                    onUse = {
                        if (frozenResult != null) onConfirmed(frozenResult) else commitCurrent()
                    }
                )

                if (frozenResult != null && !context.submitsImmediately) {
                    InstrumentDivider()
                    CommittedInstrumentPanel(
                        context = context,
                        result = frozenResult,
                        values = restoredCommittedValues.ifEmpty { workingValues },
                        category = category,
                        onDone = { onConfirmed(frozenResult) },
                        onEdit = {
                            committedResult = null
                            committedValuesJson = null
                        }
                    )
                } else {
                    if (context.settingShouldBeShown("category")) {
                        ModeDashboard(
                            selected = category,
                            onSelect = { category = it }
                        )
                    }

                    InstrumentDivider()

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (category) {
                            "number" -> NumberConversionPanel(
                                value = value,
                                from = from,
                                to = to,
                                representations = unitChoices,
                                resultValues = workingValues,
                                showValue = context.settingShouldBeShown("value"),
                                showFrom = context.settingShouldBeShown("from_unit"),
                                showTo = context.settingShouldBeShown("to_unit"),
                                onValueChange = { value = it.take(48) },
                                onFromChange = {
                                    from = it
                                    value = sanitiseNumberRepresentationInput(value, it)
                                },
                                onToChange = { to = it },
                                onSwap = {
                                    val previousFrom = from
                                    from = to
                                    to = previousFrom
                                    value = workingValues[ConversionFields.VALUE].orEmpty().ifBlank { value }
                                },
                                decimalPlaces = decimalPlaces,
                                showPrecision = context.settingShouldBeShown("decimal_places"),
                                onDecreasePrecision = { if (decimalPlaces > 0) decimalPlaces -= 1 },
                                onIncreasePrecision = { if (decimalPlaces < 10) decimalPlaces += 1 }
                            )

                            in unitConversionCategories -> UnitConversionPanel(
                                category = category,
                                value = value,
                                from = from,
                                to = to,
                                units = unitChoices,
                                resultValues = workingValues,
                                showValue = context.settingShouldBeShown("value"),
                                showFrom = context.settingShouldBeShown("from_unit"),
                                showTo = context.settingShouldBeShown("to_unit"),
                                onValueChange = { value = expressionText(it) },
                                onFromChange = { from = it },
                                onToChange = { to = it },
                                onSwap = {
                                    // A swap reverses the current conversion, not merely the
                                    // labels. Carry the successful displayed result forward as
                                    // the new source value so 1000 m → 1 km becomes
                                    // 1 km → 1000 m.
                                    val convertedValue = workingValues[ConversionFields.VALUE].orEmpty()
                                    val canCarryResult = workingValues[ConversionFields.STATUS] == "succeeded" &&
                                        convertedValue.isNotBlank()
                                    val previousFrom = from
                                    from = to
                                    to = previousFrom
                                    if (canCarryResult) value = convertedValue
                                },
                                decimalPlaces = decimalPlaces,
                                showPrecision = context.settingShouldBeShown("decimal_places"),
                                onDecreasePrecision = { if (decimalPlaces > 0) decimalPlaces -= 1 },
                                onIncreasePrecision = { if (decimalPlaces < 10) decimalPlaces += 1 }
                            )

                            "percentage" -> PercentagePanel(
                                value = value,
                                value2 = value2,
                                operation = operation,
                                resultValues = workingValues,
                                showValue = context.settingShouldBeShown("value"),
                                showValue2 = context.settingShouldBeShown("value2"),
                                showOperation = context.settingShouldBeShown("operation"),
                                onValueChange = { value = expressionText(it) },
                                onValue2Change = { value2 = expressionText(it) },
                                onOperationChange = { operation = it },
                                decimalPlaces = decimalPlaces,
                                showPrecision = context.settingShouldBeShown("decimal_places"),
                                onDecreasePrecision = { if (decimalPlaces > 0) decimalPlaces -= 1 },
                                onIncreasePrecision = { if (decimalPlaces < 10) decimalPlaces += 1 }
                            )

                            "ratio" -> RatioPanel(
                                value = value,
                                value2 = value2,
                                value3 = value3,
                                operation = operation,
                                resultValues = workingValues,
                                showValue = context.settingShouldBeShown("value"),
                                showValue2 = context.settingShouldBeShown("value2"),
                                showValue3 = context.settingShouldBeShown("value3"),
                                showOperation = context.settingShouldBeShown("operation"),
                                onValueChange = { value = expressionText(it) },
                                onValue2Change = { value2 = expressionText(it) },
                                onValue3Change = { value3 = expressionText(it) },
                                onOperationChange = { operation = it },
                                decimalPlaces = decimalPlaces,
                                showPrecision = context.settingShouldBeShown("decimal_places"),
                                onDecreasePrecision = { if (decimalPlaces > 0) decimalPlaces -= 1 },
                                onIncreasePrecision = { if (decimalPlaces < 10) decimalPlaces += 1 }
                            )

                            "date_difference" -> DateDifferencePanel(
                                date1 = date1,
                                date2 = date2,
                                resultValues = workingValues,
                                showDate1 = context.settingShouldBeShown("date1"),
                                showDate2 = context.settingShouldBeShown("date2"),
                                onDate1Change = { date1 = dateText(it) },
                                onDate2Change = { date2 = dateText(it) }
                            )

                            "date_arithmetic" -> DateArithmeticPanel(
                                date1 = date1,
                                value = value,
                                operation = operation,
                                resultValues = workingValues,
                                showDate1 = context.settingShouldBeShown("date1"),
                                showValue = context.settingShouldBeShown("value"),
                                showOperation = context.settingShouldBeShown("operation"),
                                onDate1Change = { date1 = dateText(it) },
                                onValueChange = { value = signedIntegerText(it) },
                                onOperationChange = { operation = it }
                            )

                            "age" -> AgePanel(
                                date1 = date1,
                                date2 = date2,
                                resultValues = workingValues,
                                showDate1 = context.settingShouldBeShown("date1"),
                                showDate2 = context.settingShouldBeShown("date2"),
                                onDate1Change = { date1 = dateText(it) },
                                onDate2Change = { date2 = dateText(it) }
                            )

                            "geometry" -> GeometryPanel(
                                value = value,
                                value2 = value2,
                                shape = shape,
                                operation = operation,
                                resultValues = workingValues,
                                showValue = context.settingShouldBeShown("value"),
                                showValue2 = context.settingShouldBeShown("value2"),
                                showShape = context.settingShouldBeShown("shape"),
                                showOperation = context.settingShouldBeShown("operation"),
                                onValueChange = { value = expressionText(it) },
                                onValue2Change = { value2 = expressionText(it) },
                                onShapeChange = { shape = it },
                                onOperationChange = { operation = it },
                                decimalPlaces = decimalPlaces,
                                showPrecision = context.settingShouldBeShown("decimal_places"),
                                onDecreasePrecision = { if (decimalPlaces > 0) decimalPlaces -= 1 },
                                onIncreasePrecision = { if (decimalPlaces < 10) decimalPlaces += 1 }
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class ConversionMode(
    val id: String,
    val label: String,
    val mark: String
)

private val unitModes = listOf(
    ConversionMode("length", "Length", "m"),
    ConversionMode("area", "Area", "m²"),
    ConversionMode("volume", "Volume", "L"),
    ConversionMode("mass", "Mass", "kg"),
    ConversionMode("temperature", "Temperature", "°"),
    ConversionMode("speed", "Speed", "km/h"),
    ConversionMode("pressure", "Pressure", "Pa"),
    ConversionMode("energy", "Energy", "J"),
    ConversionMode("power", "Power", "W"),
    ConversionMode("angle", "Angle", "∠"),
    ConversionMode("data_size", "Data size", "GB"),
    ConversionMode("number", "Number", "123")
)

private val calculatorModes = listOf(
    ConversionMode("percentage", "Percentage", "%"),
    ConversionMode("ratio", "Ratio", ":"),
    ConversionMode("date_difference", "Date gap", "Δd"),
    ConversionMode("date_arithmetic", "Date +/−", "+d"),
    ConversionMode("age", "Age", "yr"),
    ConversionMode("geometry", "Geometry", "△")
)

private val allModes = unitModes + calculatorModes
private val unitConversionCategories = unitModes.map { it.id }.filter { it != "number" }.toSet()
private val precisionCategories = unitConversionCategories + setOf("number", "percentage", "ratio", "geometry")

private val InstrumentBackground = Color(0xFF151512)
private val InstrumentSurface = Color(0xFF1D1D19)
private val InstrumentSurfaceRaised = Color(0xFF23231E)
private val InstrumentLine = Color(0xFF3A382F)
private val InstrumentGold = Color(0xFFC9AA5A)
private val InstrumentTeal = Color(0xFF62B6B0)
private val InstrumentText = Color(0xFFF3EFE4)
private val InstrumentMuted = Color(0xFFA7A197)
private val InstrumentError = Color(0xFFFF7474)

@Composable
private fun InstrumentHeader(
    category: String,
    canGoBack: Boolean,
    hasResult: Boolean,
    committed: Boolean,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onUse: () -> Unit
) {
    val label = allModes.firstOrNull { it.id == category }?.label ?: humanLabel(category)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "CONVERSIONS",
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Bold,
                color = InstrumentGold
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.labelMedium,
                color = InstrumentMuted.copy(alpha = 0.65f)
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = InstrumentText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (canGoBack) {
                InstrumentActionButton("‹", enabled = true, onClick = onBack)
            }
            // Before Commit this is Cancel. Once a payload is frozen, cancellation is
            // no longer a valid closeout path; use the committed Done/Share/Save rail.
            if (!committed) {
                InstrumentActionButton("×", enabled = true, onClick = onCancel)
            }
            if (committed) {
                InstrumentActionButton("FROZEN", enabled = false, primary = true, onClick = {})
            } else {
                InstrumentActionButton("COMMIT", enabled = hasResult, primary = true, onClick = onUse)
            }
        }
    }
}

@Composable
private fun InstrumentActionButton(
    label: String,
    enabled: Boolean,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .height(40.dp)
            .widthIn(min = if (label.length > 1) 78.dp else 40.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = when {
            primary && enabled -> InstrumentTeal.copy(alpha = 0.22f)
            else -> InstrumentSurfaceRaised
        },
        border = BorderStroke(
            1.dp,
            when {
                primary && enabled -> InstrumentTeal.copy(alpha = 0.72f)
                else -> InstrumentLine.copy(alpha = 0.55f)
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Bold,
                color = when {
                    !enabled -> InstrumentMuted.copy(alpha = 0.28f)
                    primary -> InstrumentTeal
                    else -> InstrumentMuted
                }
            )
        }
    }
}

@Composable
private fun InstrumentDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = InstrumentLine.copy(alpha = 0.75f)
    )
}

@Composable
private fun ModeDashboard(selected: String, onSelect: (String) -> Unit) {
    ModeGrid(allModes, selected, onSelect)
}

@Composable
private fun ModeGrid(modes: List<ConversionMode>, selected: String, onSelect: (String) -> Unit) {
    val columns = 4
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        modes.chunked(columns).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                rowModes.forEach { mode ->
                    CompactModeButton(
                        mode = mode,
                        selected = mode.id == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(mode.id) }
                    )
                }
                repeat(columns - rowModes.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CompactModeButton(
    mode: ConversionMode,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) InstrumentSurfaceRaised else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) InstrumentTeal.copy(alpha = 0.78f) else InstrumentLine.copy(alpha = 0.28f)
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = compactModeLabel(mode),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 16.5.sp),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (selected) InstrumentText else InstrumentMuted.copy(alpha = 0.9f),
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun compactModeLabel(mode: ConversionMode): String = mode.label


@Composable
private fun PrecisionStepper(
    decimalPlaces: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            "DP",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontFamily = FontFamily.Monospace,
            color = InstrumentMuted
        )
        MiniStepButton("−", enabled = decimalPlaces > 0, onClick = onDecrease)
        Text(
            decimalPlaces.toString(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = InstrumentText,
            modifier = Modifier.widthIn(min = 14.dp)
        )
        MiniStepButton("+", enabled = decimalPlaces < 10, onClick = onIncrease)
    }
}

@Composable
private fun MiniStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(36.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = InstrumentSurfaceRaised,
        border = BorderStroke(1.dp, InstrumentLine.copy(alpha = 0.7f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = if (enabled) InstrumentTeal else InstrumentMuted.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun CommittedInstrumentPanel(
    context: CapabilityScreenContext,
    result: ExecutionResult,
    values: Map<String, String>,
    category: String,
    onDone: () -> Unit,
    onEdit: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ResultCard(values = values)

        Text(
            "COMMITTED · RESULT FROZEN",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = InstrumentTeal
        )

        InstrumentCommittedActions(
            context = context,
            result = result,
            label = "conversions ${humanLabel(category)}",
            onDone = onDone,
            onEdit = onEdit,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Compact instrument projection of the shared MethodMesh post-Commit contract.
 * Transport work is delegated to the shared ResultShare / OutputExportRepository
 * infrastructure; this panel only owns the capability-specific visual treatment.
 */
@Composable
private fun InstrumentCommittedActions(
    context: CapabilityScreenContext,
    result: ExecutionResult,
    label: String,
    onDone: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appContext = LocalContext.current
    val committedFields = remember(result.request.id.value) {
        OutputFormatter.fields(result, includeProvenance = false)
    }
    val committedAnswer = remember(result.request.id.value) {
        buildAnswer(
            committedFields[ConversionFields.VALUE]?.toString().orEmpty(),
            committedFields[ConversionFields.UNIT]?.toString().orEmpty()
        )
    }
    val primaryText = remember(result.request.id.value) {
        committedFields[ConversionFields.SUMMARY]?.toString().orEmpty().ifBlank { committedAnswer }
    }
    val fullJson = remember(result.request.id.value) {
        OutputFormatter.format(
            result = result,
            returnMode = ReturnMode.Json,
            includeProvenance = true,
            payloadMode = OutputFormatter.PayloadMode.FULL
        )
    }
    var includeFullJson by rememberSaveable(result.request.id.value) { mutableStateOf(false) }
    var status by rememberSaveable(result.request.id.value) { mutableStateOf("") }
    val copiedText = remember(primaryText, fullJson, includeFullJson) {
        ResultShare.buildShareText(primaryText, if (includeFullJson) fullJson else "")
    }
    val hasShareableResult = primaryText.isNotBlank() || (includeFullJson && fullJson.isNotBlank())
    val presetAction = PresetResultAction.normalize(
        context.request.settings["methodmesh_preset_result_action"]
            ?: context.request.settings["input_methodmesh_preset_result_action"]
            ?: PresetResultAction.HOME
    )
    val finishToLauncher =
        context.request.settings["methodmesh_finish_to_launcher"] == "true" ||
            context.request.settings["input_methodmesh_finish_to_launcher"] == "true"

    fun copyResult() {
        runCatching {
            if (copiedText.isBlank()) error("No text result to copy.")
            appContext.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("MethodMesh result", copiedText))
        }.onSuccess { status = "Copied committed result" }
            .onFailure { status = "Copy failed: ${it.message ?: "no text result"}" }
    }

    fun shareResult() {
        runCatching {
            ResultShare.share(
                context = appContext,
                chooserTitle = "Share Conversions result",
                text = primaryText,
                attachments = emptyList(),
                jsonText = if (includeFullJson) fullJson else "",
                fileLabel = label
            )
        }.onSuccess { status = "Sharing committed result…" }
            .onFailure { status = "Share failed: ${it.message ?: "no sharing app available"}" }
    }

    fun saveResult() {
        runCatching {
            OutputExportRepository.saveToDownloads(
                context = appContext,
                label = label,
                text = primaryText,
                mediaUris = emptyList(),
                jsonText = if (includeFullJson) fullJson else ""
            )
        }.onSuccess { status = "Saved ${it.summary}" }
            .onFailure { status = "Save failed: ${it.message ?: "storage error"}" }
    }

    fun finishNativePresetHome() {
        if (finishToLauncher) {
            onDone()
            return
        }
        appContext.startActivity(
            Intent(appContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun finishCommitted() {
        if (!context.isNativePresetRun || !context.isLastStep) {
            onDone()
            return
        }
        when (presetAction) {
            PresetResultAction.SAVE -> {
                saveResult()
                onDone()
            }
            PresetResultAction.SHARE -> {
                shareResult()
                finishNativePresetHome()
            }
            else -> finishNativePresetHome()
        }
    }

    val finishLabel = when {
        context.isNativePresetRun && context.isLastStep && presetAction == PresetResultAction.SAVE -> "SAVE + FINISH"
        context.isNativePresetRun && context.isLastStep && presetAction == PresetResultAction.SHARE -> "SHARE + FINISH"
        else -> "DONE"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            InstrumentPostCommitButton(
                label = "COPY",
                enabled = copiedText.isNotBlank(),
                modifier = Modifier.weight(1f),
                onClick = ::copyResult
            )

            InstrumentPostCommitButton(
                label = "SHARE",
                enabled = hasShareableResult,
                modifier = Modifier.weight(1f),
                onClick = ::shareResult
            )

            InstrumentPostCommitButton(
                label = "SAVE",
                enabled = hasShareableResult,
                modifier = Modifier.weight(1f),
                onClick = ::saveResult
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            InstrumentToggleButton(
                label = "FULL JSON",
                checked = includeFullJson,
                modifier = Modifier.weight(1.25f),
                onCheckedChange = { includeFullJson = it }
            )
            InstrumentPostCommitButton(
                label = "EDIT",
                enabled = true,
                modifier = Modifier.weight(0.8f),
                onClick = onEdit
            )
            InstrumentPostCommitButton(
                label = finishLabel,
                enabled = true,
                primary = true,
                modifier = Modifier.weight(if (finishLabel == "DONE") 0.8f else 1.35f),
                onClick = ::finishCommitted
            )
        }

        Text(
            text = if (status.isNotBlank()) status else if (includeFullJson) {
                "FULL JSON / AUDIT INCLUDED IN SHARE, COPY AND SAVE"
            } else {
                "SHARE/COPY = RESULT · SAVE = DOWNLOADS · FULL JSON OFF"
            },
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (status.startsWith("Share failed") || status.startsWith("Copy failed") || status.startsWith("Save failed")) {
                InstrumentError
            } else {
                InstrumentMuted.copy(alpha = 0.72f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InstrumentPostCommitButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = when {
            primary && enabled -> InstrumentTeal.copy(alpha = 0.20f)
            else -> InstrumentSurfaceRaised
        },
        border = BorderStroke(
            1.dp,
            when {
                primary && enabled -> InstrumentTeal.copy(alpha = 0.75f)
                else -> InstrumentLine.copy(alpha = 0.65f)
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = when {
                    !enabled -> InstrumentMuted.copy(alpha = 0.30f)
                    primary -> InstrumentTeal
                    else -> InstrumentText
                }
            )
        }
    }
}

@Composable
private fun InstrumentToggleButton(
    label: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = modifier
            .height(46.dp)
            .clickable { onCheckedChange(!checked) },
        shape = RoundedCornerShape(8.dp),
        color = if (checked) InstrumentGold.copy(alpha = 0.12f) else InstrumentSurfaceRaised,
        border = BorderStroke(
            1.dp,
            if (checked) InstrumentGold.copy(alpha = 0.65f) else InstrumentLine.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (checked) InstrumentGold else InstrumentMuted
            )
            Text(
                if (checked) "ON" else "OFF",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (checked) InstrumentTeal else InstrumentMuted.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun NumberConversionPanel(
    value: String,
    from: String,
    to: String,
    representations: List<String>,
    resultValues: Map<String, String>,
    showValue: Boolean,
    showFrom: Boolean,
    showTo: Boolean,
    onValueChange: (String) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit,
    decimalPlaces: Int,
    showPrecision: Boolean,
    onDecreasePrecision: () -> Unit,
    onIncreasePrecision: () -> Unit
) {
    CalculatorCard {
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision && to !in baseNumberRepresentations,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )

        if (representations.isNotEmpty() && (showFrom || showTo)) {
            UnitPairRows(
                units = representations,
                from = from,
                to = to,
                showFrom = showFrom,
                showTo = showTo,
                onFromChange = onFromChange,
                onToChange = onToChange,
                onSwap = onSwap
            )
        }

        if (showValue) {
            ExpressionReadout(value = value, label = if (from in baseNumberRepresentations) "VALUE" else "EXPR")
            when (from) {
                "binary" -> RepresentationKeypad(
                    expression = value,
                    keys = listOf("1", "0", "±", "CLR", "", "", "", "⌫"),
                    allowedCharacters = "01",
                    onExpressionChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                "octal" -> RepresentationKeypad(
                    expression = value,
                    keys = listOf("7", "6", "5", "4", "3", "2", "1", "0", "±", "CLR", "", "⌫"),
                    allowedCharacters = "01234567",
                    onExpressionChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                "hex" -> RepresentationKeypad(
                    expression = value,
                    keys = listOf(
                        "7", "8", "9", "A",
                        "4", "5", "6", "B",
                        "1", "2", "3", "C",
                        "0", "D", "E", "F",
                        "±", "CLR", "", "⌫"
                    ),
                    allowedCharacters = "0123456789ABCDEF",
                    onExpressionChange = onValueChange,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                "si" -> {
                    SiPrefixStrip(value = value, onExpressionChange = onValueChange)
                    RepresentationKeypad(
                        expression = value,
                        keys = listOf("7", "8", "9", "4", "5", "6", "1", "2", "3", "0", ".", "±", "CLR", "", "", "⌫"),
                        allowedCharacters = "0123456789.",
                        onExpressionChange = onValueChange,
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                }
                else -> CalculatorKeypad(
                    expression = value,
                    onExpressionChange = onValueChange,
                    includeExponent = true,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

private val baseNumberRepresentations = setOf("binary", "octal", "hex")

@Composable
private fun SiPrefixStrip(value: String, onExpressionChange: (String) -> Unit) {
    val prefixes = listOf("p", "n", "µ", "m", "k", "M", "G", "T")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        prefixes.forEach { prefix ->
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable {
                        val base = value.trim().replace(Regex("[pnumkMGTµu]$"), "")
                        if (base.isNotBlank()) onExpressionChange((base + prefix).take(48))
                    },
                shape = RoundedCornerShape(8.dp),
                color = InstrumentSurfaceRaised,
                border = BorderStroke(1.dp, InstrumentLine.copy(alpha = 0.65f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        prefix,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = InstrumentGold
                    )
                }
            }
        }
    }
}

@Composable
private fun RepresentationKeypad(
    expression: String,
    keys: List<String>,
    allowedCharacters: String,
    onExpressionChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rows = keys.chunked(4)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                row.forEach { key ->
                    if (key.isBlank()) {
                        Spacer(Modifier.weight(1f).fillMaxHeight())
                    } else {
                        CalculatorKey(
                            key = key,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = {
                                val current = expression.take(48)
                                val next = when (key) {
                                    "CLR" -> ""
                                    "⌫" -> current.dropLast(1)
                                    "±" -> if (current.startsWith("−") || current.startsWith("-")) current.drop(1) else if (current.isBlank()) "−" else "−$current"
                                    else -> if (key.length == 1 && key[0] in allowedCharacters) (current + key).take(48) else current
                                }
                                onExpressionChange(next)
                            }
                        )
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private fun sanitiseNumberRepresentationInput(value: String, from: String): String {
    if (value.isBlank()) return value
    return if (NumberRepresentation.canParse(value, from)) value else ""
}

@Composable
private fun UnitConversionPanel(
    category: String,
    value: String,
    from: String,
    to: String,
    units: List<String>,
    resultValues: Map<String, String>,
    showValue: Boolean,
    showFrom: Boolean,
    showTo: Boolean,
    onValueChange: (String) -> Unit,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit,
    decimalPlaces: Int,
    showPrecision: Boolean,
    onDecreasePrecision: () -> Unit,
    onIncreasePrecision: () -> Unit
) {
    CalculatorCard {
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )

        if (units.isNotEmpty() && (showFrom || showTo)) {
            UnitPairRows(
                units = units,
                from = from,
                to = to,
                showFrom = showFrom,
                showTo = showTo,
                onFromChange = onFromChange,
                onToChange = onToChange,
                onSwap = onSwap
            )
        }

        if (showValue) {
            ExpressionReadout(value = value)
            CalculatorKeypad(
                expression = value,
                onExpressionChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun UnitPairRows(
    units: List<String>,
    from: String,
    to: String,
    showFrom: Boolean,
    showTo: Boolean,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (showFrom) {
            CompactUnitRow(
                label = "From",
                units = units,
                selected = from,
                onSelect = onFromChange
            )
        }
        if (showTo) {
            CompactUnitRow(
                label = "To",
                units = units,
                selected = to,
                onSelect = onToChange,
                trailingAction = if (showFrom) onSwap else null
            )
        }
    }
}

@Composable
private fun CompactUnitRow(
    label: String,
    units: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    trailingAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.width(60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = InstrumentGold
            )
            if (trailingAction != null) {
                Text(
                    "⇅",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    color = InstrumentTeal,
                    modifier = Modifier.clickable(onClick = trailingAction)
                )
            }
        }

        units.forEach { unit ->
            val isSelected = unit == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clickable { onSelect(unit) },
                shape = RoundedCornerShape(6.dp),
                color = if (isSelected) InstrumentSurfaceRaised else Color.Transparent,
                border = BorderStroke(
                    1.dp,
                    if (isSelected) InstrumentTeal.copy(alpha = 0.65f) else InstrumentLine.copy(alpha = 0.38f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        compactUnitLabel(unit),
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.5.sp),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) InstrumentText else InstrumentMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Clip
                    )
                }
            }
        }
    }
}

private fun compactUnitLabel(value: String): String = when (value) {
    "hectare" -> "ha"
    "US_gal" -> "USg"
    "UK_gal" -> "UKg"
    "US_fl_oz" -> "fl oz"
    "knot" -> "kt"
    "decimal" -> "Dec"
    "scientific" -> "Sci"
    "engineering" -> "Eng"
    "si" -> "SI"
    "binary" -> "Bin"
    "octal" -> "Oct"
    "hex" -> "Hex"
    else -> unitLabel(value)
}

@Composable
private fun PercentagePanel(
    value: String,
    value2: String,
    operation: String,
    resultValues: Map<String, String>,
    showValue: Boolean,
    showValue2: Boolean,
    showOperation: Boolean,
    onValueChange: (String) -> Unit,
    onValue2Change: (String) -> Unit,
    onOperationChange: (String) -> Unit,
    decimalPlaces: Int,
    showPrecision: Boolean,
    onDecreasePrecision: () -> Unit,
    onIncreasePrecision: () -> Unit
) {
    CalculatorCard {
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
        if (showOperation) OperationSelector(operationsFor("percentage", ""), operation, onOperationChange)

        val labels = when (operation) {
            "percent_of" -> "Percent" to "Of value"
            "what_percent" -> "Part" to "Whole"
            "percent_change" -> "Starting value" to "New value"
            "increase_by_percent", "decrease_by_percent" -> "Value" to "Percent"
            else -> "A" to "B"
        }

        if (showValue) NumericField(value, onValueChange, labels.first)
        if (showValue2) NumericField(value2, onValue2Change, labels.second)
    }
}

@Composable
private fun RatioPanel(
    value: String,
    value2: String,
    value3: String,
    operation: String,
    resultValues: Map<String, String>,
    showValue: Boolean,
    showValue2: Boolean,
    showValue3: Boolean,
    showOperation: Boolean,
    onValueChange: (String) -> Unit,
    onValue2Change: (String) -> Unit,
    onValue3Change: (String) -> Unit,
    onOperationChange: (String) -> Unit,
    decimalPlaces: Int,
    showPrecision: Boolean,
    onDecreasePrecision: () -> Unit,
    onIncreasePrecision: () -> Unit
) {
    CalculatorCard {
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
        if (showOperation) OperationSelector(operationsFor("ratio", ""), operation, onOperationChange)

        if (showValue) NumericField(value, onValueChange, "A")
        if (showValue2) NumericField(value2, onValue2Change, "B")
        if (operation == "solve_proportion" && showValue3) {
            NumericField(value3, onValue3Change, "C  •  solve A:B = C:X")
        }
    }
}

@Composable
private fun DateDifferencePanel(
    date1: String,
    date2: String,
    resultValues: Map<String, String>,
    showDate1: Boolean,
    showDate2: Boolean,
    onDate1Change: (String) -> Unit,
    onDate2Change: (String) -> Unit
) {
    CalculatorCard {
        ResultCard(resultValues)
        if (showDate1) DateField(date1, onDate1Change, "Start date")
        if (showDate2) DateField(date2, onDate2Change, "End date")
    }
}

@Composable
private fun DateArithmeticPanel(
    date1: String,
    value: String,
    operation: String,
    resultValues: Map<String, String>,
    showDate1: Boolean,
    showValue: Boolean,
    showOperation: Boolean,
    onDate1Change: (String) -> Unit,
    onValueChange: (String) -> Unit,
    onOperationChange: (String) -> Unit
) {
    CalculatorCard {
        ResultCard(resultValues)
        if (showOperation) OperationSelector(operationsFor("date_arithmetic", ""), operation, onOperationChange)
        if (showDate1) DateField(date1, onDate1Change, "Date")
        if (showValue) IntegerField(value, onValueChange, "Amount")
    }
}

@Composable
private fun AgePanel(
    date1: String,
    date2: String,
    resultValues: Map<String, String>,
    showDate1: Boolean,
    showDate2: Boolean,
    onDate1Change: (String) -> Unit,
    onDate2Change: (String) -> Unit
) {
    CalculatorCard {
        ResultCard(resultValues)
        if (showDate1) DateField(date1, onDate1Change, "Birth date")
        if (showDate2) DateField(date2, onDate2Change, "At date (optional; blank = today)")
    }
}

@Composable
private fun GeometryPanel(
    value: String,
    value2: String,
    shape: String,
    operation: String,
    resultValues: Map<String, String>,
    showValue: Boolean,
    showValue2: Boolean,
    showShape: Boolean,
    showOperation: Boolean,
    onValueChange: (String) -> Unit,
    onValue2Change: (String) -> Unit,
    onShapeChange: (String) -> Unit,
    onOperationChange: (String) -> Unit,
    decimalPlaces: Int,
    showPrecision: Boolean,
    onDecreasePrecision: () -> Unit,
    onIncreasePrecision: () -> Unit
) {
    CalculatorCard {
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
        if (showShape) ChoiceChips("Shape", listOf("rectangle", "triangle", "circle"), shape, onShapeChange)
        if (showOperation) OperationSelector(operationsFor("geometry", shape), operation, onOperationChange)

        when (shape) {
            "circle" -> if (showValue) NumericField(value, onValueChange, "Radius")
            "triangle" -> {
                if (showValue) NumericField(value, onValueChange, "Base")
                if (showValue2) NumericField(value2, onValue2Change, "Height")
            }
            else -> {
                if (showValue) NumericField(value, onValueChange, "Length")
                if (showValue2) NumericField(value2, onValue2Change, "Width")
            }
        }
    }
}

@Composable
private fun CalculatorCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(5.dp),
        content = content
    )
}

@Composable
private fun ExpressionReadout(value: String, label: String = "EXPR") {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = InstrumentGold,
            modifier = Modifier.width(60.dp)
        )
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(64.dp),
            shape = RoundedCornerShape(9.dp),
            color = InstrumentSurfaceRaised,
            border = BorderStroke(1.dp, InstrumentLine.copy(alpha = 0.78f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 13.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = ConversionExpression.display(value).ifBlank { "0" },
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp),
                    color = if (value.isBlank()) InstrumentMuted.copy(alpha = 0.45f) else InstrumentText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CalculatorKeypad(
    expression: String,
    onExpressionChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    includeExponent: Boolean = false
) {
    val rows = if (includeExponent) {
        listOf(
            listOf("7", "8", "9", "÷", "EXP"),
            listOf("4", "5", "6", "×", "C"),
            listOf("1", "2", "3", "−", "("),
            listOf("0", ".", "+", ")", "⌫")
        )
    } else {
        listOf(
            listOf("7", "8", "9", "÷"),
            listOf("4", "5", "6", "×"),
            listOf("1", "2", "3", "−"),
            listOf(".", "0", "+", "("),
            listOf("C", "±", ")", "⌫")
        )
    }

    val columnCount = rows.maxOf { it.size }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { key ->
                    CalculatorKey(
                        key = key,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        onClick = {
                            onExpressionChange(applyCalculatorKey(expression, key))
                        }
                    )
                }
                repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CalculatorKey(
    key: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isOperator = key in setOf("÷", "×", "−", "+", "(", ")", "EXP")
    val isBackspace = key == "⌫"
    val isUtility = key == "C" || key == "CLR" || key == "±"
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(9.dp),
        color = when {
            isBackspace -> InstrumentTeal.copy(alpha = 0.22f)
            isOperator -> InstrumentGold.copy(alpha = 0.10f)
            else -> InstrumentSurfaceRaised
        },
        border = BorderStroke(
            1.dp,
            when {
                isBackspace -> InstrumentTeal.copy(alpha = 0.78f)
                isOperator -> InstrumentGold.copy(alpha = 0.35f)
                else -> InstrumentLine.copy(alpha = 0.65f)
            }
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = key,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 24.sp),
                fontFamily = FontFamily.Monospace,
                fontWeight = if (isBackspace || isUtility) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    isBackspace -> InstrumentTeal
                    isOperator -> InstrumentGold
                    isUtility -> InstrumentMuted
                    else -> InstrumentText
                }
            )
        }
    }
}

private fun applyCalculatorKey(expression: String, key: String): String {
    val current = expressionText(expression).take(48)
    return when (key) {
        "C" -> ""
        "⌫" -> current.dropLast(1)
        "EXP" -> appendExponent(current)
        "±" -> toggleExpressionSign(current)
        "(" -> {
            when {
                current.isBlank() -> "("
                current.last().isDigit() || current.last() == ')' -> (current + "×(").take(48)
                else -> (current + "(").take(48)
            }
        }
        ")" -> {
            val opens = current.count { it == '(' }
            val closes = current.count { it == ')' }
            if (opens > closes && current.isNotBlank() && current.last() !in setOf('+', '−', '×', '÷', '(')) {
                (current + ")").take(48)
            } else current
        }
        "+", "×", "÷" -> appendBinaryOperator(current, key)
        "−" -> {
            if (current.isBlank() || current.last() == '(') (current + "−").take(48)
            else appendBinaryOperator(current, key)
        }
        "." -> appendDecimalPoint(current)
        else -> {
            if (key.length == 1 && key[0].isDigit()) (current + key).take(48) else current
        }
    }
}

private fun toggleExpressionSign(expression: String): String {
    if (expression.isBlank()) return "−"
    return if (expression.startsWith("−(") && expression.endsWith(")") && expression.length > 3) {
        expression.substring(2, expression.length - 1)
    } else {
        "−($expression)".take(48)
    }
}

private fun appendExponent(expression: String): String {
    if (expression.isBlank() || expression.last() == ')') return expression
    val tokenStart = expression.indexOfLast { it in setOf('+', '−', '×', '÷', '(', ')') } + 1
    val token = expression.substring(tokenStart)
    if (token.isBlank() || token.none { it.isDigit() } || token.contains('e', ignoreCase = true)) return expression
    return (expression + "e").take(48)
}

private fun appendBinaryOperator(expression: String, operator: String): String {
    if (expression.isBlank()) return expression
    val operators = setOf('+', '−', '×', '÷')
    return if (expression.last() in operators) {
        expression.dropLast(1) + operator
    } else if (expression.last() == '(') {
        expression
    } else {
        (expression + operator).take(48)
    }
}

private fun appendDecimalPoint(expression: String): String {
    if (expression.isBlank()) return "0."
    val last = expression.last()
    if (last == ')') return expression
    if (last in setOf('+', '−', '×', '÷', '(')) return (expression + "0.").take(48)

    val tokenStart = expression.indexOfLast { it in setOf('+', '−', '×', '÷', '(', ')') } + 1
    val token = expression.substring(tokenStart)
    return if ('.' in token) expression else (expression + ".").take(48)
}

@Composable
private fun NumericField(value: String, onValueChange: (String) -> Unit, label: String) {
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardType = KeyboardType.Decimal
    )
}

@Composable
private fun IntegerField(value: String, onValueChange: (String) -> Unit, label: String) {
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        keyboardType = KeyboardType.Number
    )
}

@Composable
private fun DateField(value: String, onValueChange: (String) -> Unit, label: String) {
    CompactTextField(
        value = value,
        onValueChange = onValueChange,
        label = "$label · YYYY-MM-DD",
        keyboardType = KeyboardType.Text
    )
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Bold,
            color = InstrumentGold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            color = InstrumentSurfaceRaised,
            border = BorderStroke(1.dp, InstrumentLine.copy(alpha = 0.8f))
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(InstrumentTeal),
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontSize = 22.sp,
                    color = InstrumentText,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun UnitSelector(label: String, units: List<String>, selected: String, onSelect: (String) -> Unit) {
    CompactChoiceGrid(
        label = label,
        choices = units,
        selected = selected,
        onSelect = onSelect,
        labelFor = ::unitLabel
    )
}

@Composable
private fun OperationSelector(operations: List<String>, selected: String, onSelect: (String) -> Unit) {
    ChoiceChips("Operation", operations, selected, onSelect, ::operationLabel)
}

@Composable
private fun ResultCard(
    values: Map<String, String>,
    decimalPlaces: Int = 4,
    showPrecision: Boolean = false,
    onDecreasePrecision: () -> Unit = {},
    onIncreasePrecision: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    var copiedAnswer by remember { mutableStateOf(false) }
    var copiedWorking by remember { mutableStateOf(false) }

    LaunchedEffect(copiedAnswer) {
        if (copiedAnswer) {
            delay(1200)
            copiedAnswer = false
        }
    }
    LaunchedEffect(copiedWorking) {
        if (copiedWorking) {
            delay(1200)
            copiedWorking = false
        }
    }

    val status = values[ConversionFields.STATUS]
    val value = values[ConversionFields.VALUE].orEmpty()
    val unit = values[ConversionFields.UNIT].orEmpty()
    val working = values[ConversionFields.SUMMARY].orEmpty()
    val error = values[ConversionFields.ERROR].orEmpty()
    val answer = buildAnswer(value, unit)
    val succeeded = status == "succeeded"
    val failed = status == "failed"

    // The readout keeps identical geometry in empty, failed and populated states.
    // This prevents live calculation from pushing the unit/value controls around.
    val displayValue = if (succeeded && value.isNotBlank()) value else "—"
    val displayUnit = if (succeeded && unit.isNotBlank() && unit != "date") unitLabel(unit) else ""
    val answerHint = when {
        succeeded && copiedAnswer -> "COPIED"
        succeeded -> "TAP ANSWER TO COPY"
        failed -> "CALCULATION ERROR"
        else -> "ENTER VALUE TO CALCULATE"
    }
    val workingPreview = when {
        succeeded && working.isNotBlank() -> working
        failed -> error.ifBlank { "Calculation could not be completed." }
        else -> "Working appears here"
    }
    val workingHint = when {
        succeeded && copiedWorking -> "WORKING + ANSWER COPIED"
        succeeded -> "TAP WORKING FOR FORMULA + ANSWER"
        failed -> "CHECK INPUTS"
        else -> "FORMULA + ANSWER APPEAR HERE"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F100E),
        border = BorderStroke(1.dp, InstrumentLine.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "RESULT",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                    fontWeight = FontWeight.Bold,
                    color = InstrumentGold
                )
                if (showPrecision) {
                    PrecisionStepper(
                        decimalPlaces = decimalPlaces,
                        onDecrease = onDecreasePrecision,
                        onIncrease = onIncreasePrecision
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = succeeded && answer.isNotBlank()) {
                        clipboard.setText(AnnotatedString(answer))
                        copiedAnswer = true
                        copiedWorking = false
                    }
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Text(
                        displayValue,
                        style = MaterialTheme.typography.displaySmall.copy(fontSize = 38.sp),
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (succeeded) InstrumentText else InstrumentMuted.copy(alpha = 0.60f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        displayUnit.ifBlank { " " },
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = InstrumentTeal,
                        maxLines = 1,
                        softWrap = false
                    )
                }
                Text(
                    answerHint,
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontFamily = FontFamily.Monospace,
                    color = when {
                        copiedAnswer -> InstrumentTeal
                        failed -> InstrumentError
                        else -> InstrumentMuted.copy(alpha = 0.78f)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            HorizontalDivider(color = InstrumentLine.copy(alpha = 0.65f))
            Text(
                workingPreview,
                style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                fontFamily = FontFamily.Monospace,
                color = if (failed) InstrumentError else InstrumentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = succeeded && working.isNotBlank()) {
                        clipboard.setText(AnnotatedString(working))
                        copiedWorking = true
                        copiedAnswer = false
                    }
                    .padding(vertical = 1.dp)
            )
            Text(
                workingHint,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                fontFamily = FontFamily.Monospace,
                color = when {
                    copiedWorking -> InstrumentTeal
                    failed -> InstrumentError.copy(alpha = 0.85f)
                    else -> InstrumentMuted.copy(alpha = 0.58f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildAnswer(value: String, unit: String): String = when {
    value.isBlank() -> ""
    unit.isBlank() || unit == "date" -> value
    else -> "$value ${unitLabel(unit)}"
}

private fun operationsFor(category: String, shape: String): List<String> = when (category) {
    "percentage" -> listOf("percent_of", "what_percent", "percent_change", "increase_by_percent", "decrease_by_percent")
    "ratio" -> listOf("a_to_b", "solve_proportion")
    "date_arithmetic" -> listOf("add_days", "add_weeks", "add_months", "add_years", "subtract_days")
    "geometry" -> when (shape) {
        "rectangle" -> listOf("area", "perimeter")
        "triangle" -> listOf("area")
        "circle" -> listOf("area", "circumference")
        else -> listOf("area")
    }
    else -> emptyList()
}

@Composable
private fun CompactChoiceGrid(
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelFor: (String) -> String = ::humanLabel,
    label: String? = null,
    showLabel: Boolean = true
) {
    val columns = minOf(5, choices.size.coerceAtLeast(1))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel && !label.isNullOrBlank()) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Bold,
                color = InstrumentGold
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            choices.chunked(columns).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rowChoices.forEach { choice ->
                        val isSelected = selected == choice
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clickable { onSelect(choice) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) InstrumentSurfaceRaised else InstrumentSurface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) InstrumentTeal.copy(alpha = 0.65f) else InstrumentLine.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = labelFor(choice),
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) InstrumentText else InstrumentMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    repeat(columns - rowChoices.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChips(
    label: String,
    choices: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    labelFor: (String) -> String = ::humanLabel
) {
    CompactChoiceGrid(
        label = label,
        choices = choices,
        selected = selected,
        onSelect = onSelect,
        labelFor = labelFor
    )
}

private fun inputsReady(
    category: String,
    operation: String,
    shape: String,
    value: String,
    value2: String,
    value3: String,
    date1: String,
    date2: String,
    from: String
): Boolean {
    fun numeric(text: String) = text.isNotBlank() && ConversionExpression.evaluateOrNull(text) != null
    fun date(text: String) = runCatching { LocalDate.parse(text) }.isSuccess

    return when (category) {
        "number" -> value.isNotBlank() && NumberRepresentation.canParse(value, from)
        in unitConversionCategories -> numeric(value)
        "percentage" -> numeric(value) && numeric(value2)
        "ratio" -> numeric(value) && numeric(value2) && (operation != "solve_proportion" || numeric(value3))
        "date_difference" -> date(date1) && date(date2)
        "date_arithmetic" -> date(date1) && numeric(value)
        "age" -> date(date1) && (date2.isBlank() || date(date2))
        "geometry" -> numeric(value) && (shape == "circle" || numeric(value2))
        else -> false
    }
}

private fun operationLabel(operation: String): String = when (operation) {
    "percent_of" -> "% of"
    "what_percent" -> "What %?"
    "percent_change" -> "% change"
    "increase_by_percent" -> "Increase by %"
    "decrease_by_percent" -> "Decrease by %"
    "a_to_b" -> "A ÷ B"
    "solve_proportion" -> "Solve proportion"
    "add_days" -> "Add days"
    "add_weeks" -> "Add weeks"
    "add_months" -> "Add months"
    "add_years" -> "Add years"
    "subtract_days" -> "Subtract days"
    "area" -> "Area"
    "perimeter" -> "Perimeter"
    "circumference" -> "Circumference"
    else -> humanLabel(operation)
}

private fun unitLabel(value: String): String = when (value) {
    "C" -> "°C"
    "F" -> "°F"
    "m2" -> "m²"
    "km2" -> "km²"
    "cm2" -> "cm²"
    "ft2" -> "ft²"
    "m3" -> "m³"
    "cm3" -> "cm³"
    "US_gal" -> "US gal"
    "UK_gal" -> "UK gal"
    "US_fl_oz" -> "US fl oz"
    "decimal" -> "dec"
    "scientific" -> "sci"
    "engineering" -> "eng"
    "si" -> "SI"
    "binary" -> "bin"
    "octal" -> "oct"
    "hex" -> "hex"
    else -> value
}

private fun humanLabel(value: String): String = when (value) {
    "C" -> "°C"
    "F" -> "°F"
    "K" -> "K"
    "m2" -> "m²"
    "km2" -> "km²"
    "cm2" -> "cm²"
    "ft2" -> "ft²"
    "m3" -> "m³"
    "cm3" -> "cm³"
    "US_gal" -> "US gal"
    "UK_gal" -> "UK gal"
    "US_fl_oz" -> "US fl oz"
    "data_size" -> "Data size"
    "date_difference" -> "Date gap"
    "date_arithmetic" -> "Date arithmetic"
    "decimal" -> "Decimal"
    "scientific" -> "Scientific"
    "engineering" -> "Engineering"
    "si" -> "SI notation"
    "binary" -> "Binary"
    "octal" -> "Octal"
    "hex" -> "Hexadecimal"
    else -> value.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun expressionText(value: String): String = value
    .filter { it.isDigit() || it == 'e' || it == 'E' || it in setOf('.', '+', '-', '−', '*', '×', '/', '÷', '(', ')') }
    .replace('*', '×')
    .replace('/', '÷')
    .replace('-', '−')
    .take(48)

private fun signedIntegerText(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (value.trimStart().startsWith('-')) "-$digits" else digits
}

private fun dateText(value: String): String = value.filter { it.isDigit() || it == '-' }.take(10)
