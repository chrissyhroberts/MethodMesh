package com.example.researchos.calibration

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CalibrationScreen() {
    val calibration = CalibrationRepository.calibration.value
    val referenceLengthMm = 50f
    val referenceLengthDp = referenceLengthMm.dp * calibration.dpPerMm

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "Device calibration",
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text("Hold a ruler against the screen and adjust until each line measures 50 mm.")

        Spacer(Modifier.height(12.dp))

        HorizontalDivider()

        Spacer(Modifier.height(12.dp))

        Text(
            if (calibration.calibrated) {
                "Status: Calibrated"
            } else {
                "Status: Not calibrated"
            }
        )

        Text("Calibration: %.2f dp/mm".format(calibration.dpPerMm))

        Spacer(Modifier.height(16.dp))

        CalibrationGraticule(
            length = referenceLengthDp
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    CalibrationRepository.update(calibration.dpPerMm - 0.05f)
                }
            ) {
                Text("−")
            }

            Text("50 mm")

            Button(
                onClick = {
                    CalibrationRepository.update(calibration.dpPerMm + 0.05f)
                }
            ) {
                Text("+")
            }
        }
    }
}

@Composable
private fun CalibrationGraticule(
    length: androidx.compose.ui.unit.Dp
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .width(length)
                .height(48.dp)
        ) {
            val y = size.height / 2f
            val capHeight = 18.dp.toPx()

            drawLine(
                color = Color.Black,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )

            drawLine(
                color = Color.Black,
                start = Offset(0f, y - capHeight / 2f),
                end = Offset(0f, y + capHeight / 2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )

            drawLine(
                color = Color.Black,
                start = Offset(size.width, y - capHeight / 2f),
                end = Offset(size.width, y + capHeight / 2f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )
        }

        Spacer(Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .width(48.dp)
                .height(length)
        ) {
            val x = size.width / 2f
            val capWidth = 18.dp.toPx()

            drawLine(
                color = Color.Black,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )

            drawLine(
                color = Color.Black,
                start = Offset(x - capWidth / 2f, 0f),
                end = Offset(x + capWidth / 2f, 0f),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )

            drawLine(
                color = Color.Black,
                start = Offset(x - capWidth / 2f, size.height),
                end = Offset(x + capWidth / 2f, size.height),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Square
            )
        }
    }
}