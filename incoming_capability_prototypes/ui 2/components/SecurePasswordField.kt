package com.example.methodmesh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Password entry that stays a password field at the IME level even while visible.
 * This keeps keyboards such as Gboard from treating the value as ordinary suggestion text.
 */
@Composable
fun SecurePasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrect = false
        ),
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                val colour = MaterialTheme.colorScheme.onSurfaceVariant
                Canvas(Modifier.size(22.dp)) {
                    val stroke = 1.7.dp.toPx()
                    val inset = 2.dp.toPx()
                    val eye = Path().apply {
                        moveTo(inset, size.height / 2f)
                        quadraticBezierTo(size.width / 2f, inset, size.width - inset, size.height / 2f)
                        quadraticBezierTo(size.width / 2f, size.height - inset, inset, size.height / 2f)
                        close()
                    }
                    drawPath(eye, colour, style = Stroke(width = stroke))
                    drawCircle(colour, radius = 2.6.dp.toPx(), center = center, style = Stroke(width = stroke))
                    if (visible) {
                        drawLine(
                            colour,
                            start = androidx.compose.ui.geometry.Offset(3.dp.toPx(), 3.dp.toPx()),
                            end = androidx.compose.ui.geometry.Offset(size.width - 3.dp.toPx(), size.height - 3.dp.toPx()),
                            strokeWidth = stroke
                        )
                    }
                }
            }
        }
    )
}
