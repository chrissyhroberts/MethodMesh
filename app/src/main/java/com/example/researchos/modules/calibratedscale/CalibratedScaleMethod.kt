package com.example.researchos.modules.calibratedscale

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.researchos.calibration.CalibrationRepository
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodManifest
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.core.ResearchActivity
import com.example.researchos.core.ResearchActivityKind
import com.example.researchos.core.MethodOutput
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.MethodOutputSchema
import com.example.researchos.core.GraphField
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState

class CalibratedScaleMethod : Method {

    
    override val manifest = MethodManifest(
        id = "calibrated_scale",
        name = "Calibrated Scale",
        description = "A configurable visual analogue and numeric scale.",
        version = "1.0.0",
        category = MethodCategory.Measurement,
        status = MethodStatus.Experimental,
        capabilities = listOf(
            ResearchActivity(
                id = "calibrated_scale.measure",
                kind = ResearchActivityKind.Measure,
                label = "Measure a value on a calibrated scale",
                producesEvidence = listOf("value", "minimum", "maximum")
            )
        ),
        contractSummary = "Collects a calibrated scalar or range value and returns declared numeric outputs."
    )


    override val outputSchema = MethodOutputSchema(
        graphOutputs = listOf(
            GraphOutput(
                id = "calibrated_scale_observation",
                objectType = KnowledgeObjectType.Observation,
                phenomenon = "measurement.calibrated_scale",
                description = "A scalar or selected range measured on a calibrated visual scale.",
                fields = listOf(
                    GraphField("value", "Observation.values.value", MethodFieldType.Float, RequiredWhen.Always),
                    GraphField("minimum", "Observation.values.minimum", MethodFieldType.Float, RequiredWhen.Always),
                    GraphField("maximum", "Observation.values.maximum", MethodFieldType.Float, RequiredWhen.Always),
                    GraphField("use_range", "Observation.values.use_range", MethodFieldType.Boolean, RequiredWhen.Always),
                    GraphField("lower_value", "Observation.values.lower_value", MethodFieldType.Float, RequiredWhen.IfAvailable),
                    GraphField("upper_value", "Observation.values.upper_value", MethodFieldType.Float, RequiredWhen.IfAvailable)
                )
            )
        ),
        fields = listOf(
            MethodField(
                id = "value",
                label = "Current value",
                type = MethodFieldType.Float,
                required = true,
                graphPath = "Observation.values.value"
            ),
            MethodField(
                id = "minimum",
                label = "Minimum scale value",
                type = MethodFieldType.Float,
                required = true,
                graphPath = "Observation.values.minimum"
            ),
            MethodField(
                id = "maximum",
                label = "Maximum scale value",
                type = MethodFieldType.Float,
                required = true,
                graphPath = "Observation.values.maximum"
            ),
            MethodField(
                id = "use_range",
                label = "Range mode enabled",
                type = MethodFieldType.Boolean,
                required = true,
                graphPath = "Observation.values.use_range"
            ),
            MethodField(
                id = "lower_value",
                label = "Lower selected value",
                type = MethodFieldType.Float,
                required = false,
                requiredWhen = RequiredWhen.IfAvailable,
                graphPath = "Observation.values.lower_value"
            ),
            MethodField(
                id = "upper_value",
                label = "Upper selected value",
                type = MethodFieldType.Float,
                required = false,
                requiredWhen = RequiredWhen.IfAvailable,
                graphPath = "Observation.values.upper_value"
            )
        )
    )
    override val settings = listOf(
        MethodSetting.FloatSetting(
            id = "vas_length_mm",
            label = "VAS length",
            group = "Appearance",
            defaultValue = 100f,
            minimum = 40f,
            maximum = 200f,
            step = 0.5f,
            unit = "mm",
            decimals = 1
        ),
        MethodSetting.BooleanSetting(
            id = "vertical_mode",
            label = "Vertical mode",
            group = "Appearance",
            defaultValue = false
        ),
        MethodSetting.TextSetting(
            id = "prompt",
            label = "Prompt",
            group = "Scale",
            defaultValue = "Rate your pain"
        ),
        MethodSetting.FloatSetting(
            id = "minimum",
            label = "Minimum value",
            group = "Scale",
            defaultValue = 0f,
            minimum = 0f,
            maximum = 100f,
            step = 1f,
            decimals = 0
        ),
        MethodSetting.FloatSetting(
            id = "maximum",
            label = "Maximum value",
            group = "Scale",
            defaultValue = 100f,
            minimum = 0f,
            maximum = 100f,
            step = 1f,
            decimals = 0
        ),
        MethodSetting.BooleanSetting(
            id = "use_range",
            label = "Use two scales",
            group = "Range",
            defaultValue = false
        ),
        MethodSetting.TextSetting(
            id = "lower_label",
            label = "Lower scale label",
            group = "Range",
            defaultValue = "Minimum selected value"
        ),
        MethodSetting.TextSetting(
            id = "upper_label",
            label = "Upper scale label",
            group = "Range",
            defaultValue = "Maximum selected value"
        ),
        MethodSetting.FloatSetting(
            id = "value",
            label = "Current value",
            group = "Display",
            defaultValue = 50f,
            minimum = 0f,
            maximum = 100f,
            step = 1f,
            decimals = 0
        ),
        MethodSetting.FloatSetting(
            id = "lower_value",
            label = "Lower selected value",
            group = "Display",
            defaultValue = 25f,
            minimum = 0f,
            maximum = 100f,
            step = 1f,
            decimals = 0
        ),
        MethodSetting.FloatSetting(
            id = "upper_value",
            label = "Upper selected value",
            group = "Display",
            defaultValue = 75f,
            minimum = 0f,
            maximum = 100f,
            step = 1f,
            decimals = 0
        ),
        MethodSetting.BooleanSetting(
            id = "show_endpoint_labels",
            label = "Show endpoint labels",
            group = "Display",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "show_current_score",
            label = "Show current value",
            group = "Display",
            defaultValue = true
        )
    )

@Composable
    override fun Demo(settingsState: SettingsState) {
        val minimum = settingsState.getFloat("minimum")
        val maximum = settingsState.getFloat("maximum").let {
            if (it > minimum) it else minimum + 1f
        }
        val useRange = settingsState.getBoolean("use_range")
        val vasLengthMm = settingsState.getFloat("vas_length_mm").coerceIn(40f, 200f)
        val desiredLength = vasLengthMm.dp * CalibrationRepository.current().dpPerMm
        val showEndpointLabels = settingsState.getBoolean("show_endpoint_labels")
        val showCurrentValue = settingsState.getBoolean("show_current_score")
        val verticalMode = settingsState.getBoolean("vertical_mode")

        normaliseRangeValues(settingsState, minimum, maximum)

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(settingsState.getString("prompt"))

            Spacer(modifier = Modifier.height(12.dp))

            if (useRange) {
                if (verticalMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = desiredLength + 170.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        ScaleInput(
                            label = settingsState.getString("lower_label"),
                            valueId = "lower_value",
                            settingsState = settingsState,
                            scaleMinimum = minimum,
                            scaleMaximum = maximum,
                            desiredLength = desiredLength,
                            showEndpointLabels = showEndpointLabels,
                            showCurrentValue = showCurrentValue,
                            vertical = true,
                            onValueChange = { proposed ->
                                val upper = settingsState.getFloat("upper_value")
                                settingsState.setFloat("lower_value", proposed.coerceIn(minimum, upper))
                            }
                        )

                        ScaleInput(
                            label = settingsState.getString("upper_label"),
                            valueId = "upper_value",
                            settingsState = settingsState,
                            scaleMinimum = minimum,
                            scaleMaximum = maximum,
                            desiredLength = desiredLength,
                            showEndpointLabels = showEndpointLabels,
                            showCurrentValue = showCurrentValue,
                            vertical = true,
                            onValueChange = { proposed ->
                                val lower = settingsState.getFloat("lower_value")
                                settingsState.setFloat("upper_value", proposed.coerceIn(lower, maximum))
                            }
                        )
                    }
                } else {
                    Column {
                        ScaleInput(
                            label = settingsState.getString("lower_label"),
                            valueId = "lower_value",
                            settingsState = settingsState,
                            scaleMinimum = minimum,
                            scaleMaximum = maximum,
                            desiredLength = desiredLength,
                            showEndpointLabels = showEndpointLabels,
                            showCurrentValue = showCurrentValue,
                            vertical = false,
                            onValueChange = { proposed ->
                                val upper = settingsState.getFloat("upper_value")
                                settingsState.setFloat("lower_value", proposed.coerceIn(minimum, upper))
                            }
                        )

                        ScaleInput(
                            label = settingsState.getString("upper_label"),
                            valueId = "upper_value",
                            settingsState = settingsState,
                            scaleMinimum = minimum,
                            scaleMaximum = maximum,
                            desiredLength = desiredLength,
                            showEndpointLabels = showEndpointLabels,
                            showCurrentValue = showCurrentValue,
                            vertical = false,
                            onValueChange = { proposed ->
                                val lower = settingsState.getFloat("lower_value")
                                settingsState.setFloat("upper_value", proposed.coerceIn(lower, maximum))
                            }
                        )
                    }
                }
            } else {
                ScaleInput(
                    label = "Current value",
                    valueId = "value",
                    settingsState = settingsState,
                    scaleMinimum = minimum,
                    scaleMaximum = maximum,
                    desiredLength = desiredLength,
                    showEndpointLabels = showEndpointLabels,
                    showCurrentValue = showCurrentValue,
                    vertical = verticalMode,
                    onValueChange = { proposed ->
                        settingsState.setFloat("value", proposed.coerceIn(minimum, maximum))
                    }
                )
            }
        }
    }

    private fun normaliseRangeValues(
        settingsState: SettingsState,
        minimum: Float,
        maximum: Float
    ) {
        val lower = settingsState.getFloat("lower_value").coerceIn(minimum, maximum)
        val upper = settingsState.getFloat("upper_value").coerceIn(minimum, maximum)

        if (lower > upper) {
            settingsState.setFloat("lower_value", upper)
            settingsState.setFloat("upper_value", lower)
        } else {
            settingsState.setFloat("lower_value", lower)
            settingsState.setFloat("upper_value", upper)
        }
    }

    @Composable
    private fun ScaleInput(
        label: String,
        valueId: String,
        settingsState: SettingsState,
        scaleMinimum: Float,
        scaleMaximum: Float,
        desiredLength: Dp,
        showEndpointLabels: Boolean,
        showCurrentValue: Boolean,
        vertical: Boolean,
        onValueChange: (Float) -> Unit
    ) {
        val current = settingsState.getFloat(valueId).coerceIn(scaleMinimum, scaleMaximum)

        BoxWithConstraints(
            modifier = if (vertical) {
                Modifier
                    .width(150.dp)
                    .height(desiredLength + 170.dp)
                    .padding(8.dp)
            } else {
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            }
        ) {
            val availableLength = if (vertical) {
                desiredLength
            } else {
                minOf(desiredLength, maxWidth - 16.dp)
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = if (vertical) {
                    Modifier.width(150.dp)
                } else {
                    Modifier.fillMaxWidth()
                }
            ) {
                Column(
                    modifier = Modifier
                        .height(84.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        maxLines = 3
                    )

                    if (showCurrentValue) {
                        Text(
                            text = current.toInt().toString(),
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (vertical) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (showEndpointLabels) {
                            Column(
                                modifier = Modifier
                                    .height(availableLength)
                                    .width(36.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(scaleMaximum.toInt().toString())
                                Text(scaleMinimum.toInt().toString())
                            }

                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        CanvasScale(
                            value = current,
                            minimum = scaleMinimum,
                            maximum = scaleMaximum,
                            length = availableLength,
                            vertical = true,
                            onValueChange = onValueChange
                        )
                    }
                } else {
                    CanvasScale(
                        value = current,
                        minimum = scaleMinimum,
                        maximum = scaleMaximum,
                        length = availableLength,
                        vertical = false,
                        onValueChange = onValueChange
                    )

                    if (showEndpointLabels) {
                        Row(
                            modifier = Modifier
                                .width(availableLength)
                                .height(24.dp)
                        ) {
                            Text(scaleMinimum.toInt().toString())
                            Spacer(modifier = Modifier.weight(1f))
                            Text(scaleMaximum.toInt().toString())
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CanvasScale(
        value: Float,
        minimum: Float,
        maximum: Float,
        length: Dp,
        vertical: Boolean,
        onValueChange: (Float) -> Unit
    ) {
        val modifier = if (vertical) {
            Modifier
                .width(56.dp)
                .height(length)
        } else {
            Modifier
                .width(length)
                .height(56.dp)
        }

        Box(
            modifier = modifier.pointerInput(minimum, maximum, vertical) {
                detectDragGestures(
                    onDragStart = { offset ->
                        onValueChange(
                            if (vertical) {
                                valueFromY(offset.y, size.height.toFloat(), minimum, maximum)
                            } else {
                                valueFromX(offset.x, size.width.toFloat(), minimum, maximum)
                            }
                        )
                    },
                    onDrag = { change, _ ->
                        onValueChange(
                            if (vertical) {
                                valueFromY(change.position.y, size.height.toFloat(), minimum, maximum)
                            } else {
                                valueFromX(change.position.x, size.width.toFloat(), minimum, maximum)
                            }
                        )
                    }
                )
            }
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val proportion = if (maximum > minimum) {
                    (value - minimum) / (maximum - minimum)
                } else {
                    0f
                }.coerceIn(0f, 1f)

                if (vertical) {
                    val x = size.width / 2f
                    val top = 0f
                    val bottom = size.height
                    val y = bottom - (bottom - top) * proportion

                    drawLine(
                        color = Color.Black,
                        start = Offset(x, top),
                        end = Offset(x, bottom),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Square
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = 10.dp.toPx(),
                        center = Offset(x, y)
                    )
                } else {
                    val y = size.height / 2f
                    val start = 0f
                    val end = size.width
                    val x = start + (end - start) * proportion

                    drawLine(
                        color = Color.Black,
                        start = Offset(start, y),
                        end = Offset(end, y),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Square
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = 10.dp.toPx(),
                        center = Offset(x, y)
                    )
                }
            }
        }
    }

    private fun valueFromX(
        x: Float,
        width: Float,
        minimum: Float,
        maximum: Float
    ): Float {
        val proportion = (x / width).coerceIn(0f, 1f)
        return minimum + proportion * (maximum - minimum)
    }

    private fun valueFromY(
        y: Float,
        height: Float,
        minimum: Float,
        maximum: Float
    ): Float {
        val proportion = (1f - (y / height)).coerceIn(0f, 1f)
        return minimum + proportion * (maximum - minimum)
    }


    override fun buildOutput(
        settingsState: SettingsState
    ): MethodOutput = As100CalibratedScaleMethod.buildOutput(settingsState)

    @Composable
    override fun Help() {
        Text("Help coming soon")
    }

    override fun execute(request: MethodRequest): MethodResult {
        return MethodResult(success = true)
    }
}