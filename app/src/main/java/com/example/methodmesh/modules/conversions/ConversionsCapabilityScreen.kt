package com.example.methodmesh.modules.conversions

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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.*
import kotlinx.coroutines.delay

object ConversionsCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ConversionsMethod.ID
    override val title = "Conversions"
    override val description = "Offline unit conversion and practical calculations."

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
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }

        val unitChoices = when (category) {
            "temperature" -> listOf("C", "F", "K")
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
            if (context.submitsImmediately) onConfirmed(calculated)
        }

        LaunchedEffect(category) {
            if (unitChoices.isNotEmpty()) {
                if (from !in unitChoices) from = unitChoices.first()
                if (to !in unitChoices) to = unitChoices.getOrElse(1) { unitChoices.first() }
            }
            val validOperations = operationsFor(category, shape)
            if (validOperations.isNotEmpty()) {
                if (operation !in validOperations) operation = validOperations.first()
            } else if (category in unitConversionCategories || category == "date_difference" || category == "age") {
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

            if (inputsReady(category, operation, shape, value, value2, value3, date1, date2)) {
                result = calculateCurrent()
            } else {
                result = null
                workingValues = emptyMap()
            }
        }

        LaunchedEffect(context.presentationMode) {
            if (context.presentationMode == CapabilityPresentationMode.IntentLaunch && !launched) {
                launched = true
                runAndMaybeSubmit()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = emptyMap(),
            onBack = onBack,
            onRetry = { runAndMaybeSubmit() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (context.settingShouldBeShown("category")) {
                    ModeDashboard(
                        selected = category,
                        onSelect = { category = it }
                    )
                } else {
                    FixedModeBanner(category)
                }

                when (category) {
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
                        onValueChange = { value = numericText(it) },
                        onFromChange = { from = it },
                        onToChange = { to = it },
                        onSwap = {
                            val previousFrom = from
                            from = to
                            to = previousFrom
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
                        onValueChange = { value = numericText(it) },
                        onValue2Change = { value2 = numericText(it) },
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
                        onValueChange = { value = numericText(it) },
                        onValue2Change = { value2 = numericText(it) },
                        onValue3Change = { value3 = numericText(it) },
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
                        onValueChange = { value = numericText(it) },
                        onValue2Change = { value2 = numericText(it) },
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
    ConversionMode("data_size", "Data size", "GB")
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
private val unitConversionCategories = unitModes.map { it.id }.toSet()
private val precisionCategories = unitConversionCategories + setOf("percentage", "ratio", "geometry")

@Composable
private fun FixedModeBanner(category: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = allModes.firstOrNull { it.id == category }?.label ?: humanLabel(category),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "offline",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ModeDashboard(selected: String, onSelect: (String) -> Unit) {
    ModeGrid(allModes, selected, onSelect)
}

@Composable
private fun ModeGrid(modes: List<ConversionMode>, selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        modes.chunked(5).forEach { rowModes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                rowModes.forEach { mode ->
                    CompactModeButton(
                        mode = mode,
                        selected = mode.id == selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(mode.id) }
                    )
                }
                repeat(5 - rowModes.size) { Spacer(Modifier.weight(1f)) }
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
            .height(27.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = compactModeLabel(mode),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

private fun compactModeLabel(mode: ConversionMode): String = when (mode.id) {
    "temperature" -> "Temp"
    "pressure" -> "Press"
    "data_size" -> "Data"
    "percentage" -> "%"
    "date_difference" -> "Gap"
    "date_arithmetic" -> "Date ±"
    "geometry" -> "Geo"
    else -> mode.label
}

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
            "Decimals",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        MiniStepButton("−", enabled = decimalPlaces > 0, onClick = onDecrease)
        Text(
            decimalPlaces.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(min = 14.dp)
        )
        MiniStepButton("+", enabled = decimalPlaces < 10, onClick = onIncrease)
    }
}

@Composable
private fun MiniStepButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
        }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
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
            CompactTextField(
                value = value,
                onValueChange = onValueChange,
                label = "Value",
                keyboardType = KeyboardType.Decimal
            )
        }

        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
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
            modifier = Modifier.width(54.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (trailingAction != null) {
                Text(
                    "⇅",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = trailingAction)
                )
            }
        }

        units.forEach { unit ->
            val isSelected = unit == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clickable { onSelect(unit) },
                shape = RoundedCornerShape(7.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        compactUnitLabel(unit),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
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
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
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
        if (showOperation) OperationSelector(operationsFor("ratio", ""), operation, onOperationChange)

        if (showValue) NumericField(value, onValueChange, "A")
        if (showValue2) NumericField(value2, onValue2Change, "B")
        if (operation == "solve_proportion" && showValue3) {
            NumericField(value3, onValue3Change, "C  •  solve A:B = C:X")
        }
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
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
        if (showDate1) DateField(date1, onDate1Change, "Start date")
        if (showDate2) DateField(date2, onDate2Change, "End date")
        ResultCard(resultValues)
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
        if (showOperation) OperationSelector(operationsFor("date_arithmetic", ""), operation, onOperationChange)
        if (showDate1) DateField(date1, onDate1Change, "Date")
        if (showValue) IntegerField(value, onValueChange, "Amount")
        ResultCard(resultValues)
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
        if (showDate1) DateField(date1, onDate1Change, "Birth date")
        if (showDate2) DateField(date2, onDate2Change, "At date (optional; blank = today)")
        ResultCard(resultValues)
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
        ResultCard(
            values = resultValues,
            decimalPlaces = decimalPlaces,
            showPrecision = showPrecision,
            onDecreasePrecision = onDecreasePrecision,
            onIncreasePrecision = onIncreasePrecision
        )
    }
}

@Composable
private fun CalculatorCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
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
        keyboardType = KeyboardType.Number
    )
}

@Composable
private fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(9.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 9.dp)
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

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "Result",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (showPrecision) {
                PrecisionStepper(
                    decimalPlaces = decimalPlaces,
                    onDecrease = onDecreasePrecision,
                    onIncrease = onIncreasePrecision
                )
            }
        }

        when (status) {
            "succeeded" -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = answer.isNotBlank()) {
                            clipboard.setText(AnnotatedString(answer))
                            copiedAnswer = true
                            copiedWorking = false
                        }
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            value,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (unit.isNotBlank() && unit != "date") {
                            Text(
                                unitLabel(unit),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                    Text(
                        if (copiedAnswer) "Copied" else "Tap answer to copy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
                }

                if (working.isNotBlank()) {
                    Text(
                        working,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(AnnotatedString(working))
                                copiedWorking = true
                                copiedAnswer = false
                            }
                            .padding(vertical = 2.dp)
                    )
                    Text(
                        if (copiedWorking) "Working + answer copied" else "Tap working to copy formula + answer",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.64f)
                    )
                }
            }

            "failed" -> Text(
                error.ifBlank { "Calculation could not be completed." },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )

            else -> Text(
                "Enter values to calculate",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                modifier = Modifier.padding(vertical = 8.dp)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showLabel && !label.isNullOrBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            choices.chunked(4).forEach { rowChoices ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    rowChoices.forEach { choice ->
                        val isSelected = selected == choice
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clickable { onSelect(choice) },
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = labelFor(choice),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    repeat(4 - rowChoices.size) { Spacer(Modifier.weight(1f)) }
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
    date2: String
): Boolean {
    fun numeric(text: String) = text.isNotBlank() && text.toDoubleOrNull() != null
    fun date(text: String) = Regex("\\d{4}-\\d{2}-\\d{2}").matches(text)

    return when (category) {
        in unitConversionCategories -> numeric(value)
        "percentage" -> numeric(value) && numeric(value2)
        "ratio" -> numeric(value) && numeric(value2) && (operation != "solve_proportion" || numeric(value3))
        "date_difference" -> date(date1) && date(date2)
        "date_arithmetic" -> date(date1) && value.isNotBlank() && value.toLongOrNull() != null
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
    else -> value.replace('_', ' ').replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun numericText(value: String): String {
    val filtered = value.filter { it.isDigit() || it == '-' || it == '.' }
    val sign = if (filtered.startsWith('-')) "-" else ""
    val unsigned = filtered.replace("-", "")
    val firstDot = unsigned.indexOf('.')
    val normalized = if (firstDot >= 0) {
        unsigned.substring(0, firstDot + 1) + unsigned.substring(firstDot + 1).replace(".", "")
    } else unsigned
    return sign + normalized
}

private fun signedIntegerText(value: String): String {
    val digits = value.filter { it.isDigit() }
    return if (value.trimStart().startsWith('-')) "-$digits" else digits
}

private fun dateText(value: String): String = value.filter { it.isDigit() || it == '-' }.take(10)
