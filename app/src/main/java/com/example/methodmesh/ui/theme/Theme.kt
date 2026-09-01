package com.example.methodmesh.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import com.example.methodmesh.settings.DisplaySettingsRepository

private val ColorCompatDarkSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF4A423D)

private val DarkColorScheme = darkColorScheme(
    primary = MethodMeshGreen,
    onPrimary = MethodMeshDarkInk,
    primaryContainer = MethodMeshSoftGreen,
    onPrimaryContainer = MethodMeshInk,
    secondary = MethodMeshDarkSecondaryInk,
    onSecondary = MethodMeshDarkSurface,
    secondaryContainer = MethodMeshDarkPanel,
    onSecondaryContainer = MethodMeshDarkInk,
    tertiary = MethodMeshGreen,
    onTertiary = MethodMeshDarkInk,
    tertiaryContainer = MethodMeshDarkPanel,
    onTertiaryContainer = MethodMeshDarkInk,
    background = MethodMeshDarkSurface,
    onBackground = MethodMeshDarkInk,
    surface = MethodMeshDarkPanel,
    onSurface = MethodMeshDarkInk,
    surfaceVariant = ColorCompatDarkSurfaceVariant,
    onSurfaceVariant = MethodMeshDarkSecondaryInk,
    outline = MethodMeshDarkOutline,
    outlineVariant = MethodMeshDarkOutline,
    error = androidx.compose.ui.graphics.Color(0xFFB86B5D),
    onError = MethodMeshDarkInk
)

private val LightColorScheme = lightColorScheme(
    primary = MethodMeshGreen,
    onPrimary = MethodMeshSurface,
    primaryContainer = MethodMeshSoftGreen,
    onPrimaryContainer = MethodMeshInk,
    secondary = MethodMeshSecondaryInk,
    onSecondary = MethodMeshSurface,
    secondaryContainer = MethodMeshSelectedSurface,
    onSecondaryContainer = MethodMeshInk,
    tertiary = MethodMeshGreen,
    onTertiary = MethodMeshSurface,
    tertiaryContainer = MethodMeshSoftGreen,
    onTertiaryContainer = MethodMeshInk,
    background = MethodMeshPaper,
    onBackground = MethodMeshInk,
    surface = MethodMeshSurface,
    onSurface = MethodMeshInk,
    surfaceVariant = MethodMeshSelectedSurface,
    onSurfaceVariant = MethodMeshSecondaryInk,
    outline = MethodMeshOutline,
    outlineVariant = MethodMeshOutline,
    error = androidx.compose.ui.graphics.Color(0xFF9B4E3F),
    onError = MethodMeshSurface
)

private val MethodMeshShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun MethodMeshTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val textScale = DisplaySettingsRepository.settings.value.textScale
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography.scaled(textScale),
        shapes = MethodMeshShapes,
        content = content
    )
}

private fun Typography.scaled(scale: Float): Typography = Typography(
    displayLarge = displayLarge.scaled(scale),
    displayMedium = displayMedium.scaled(scale),
    displaySmall = displaySmall.scaled(scale),
    headlineLarge = headlineLarge.scaled(scale),
    headlineMedium = headlineMedium.scaled(scale),
    headlineSmall = headlineSmall.scaled(scale),
    titleLarge = titleLarge.scaled(scale),
    titleMedium = titleMedium.scaled(scale),
    titleSmall = titleSmall.scaled(scale),
    bodyLarge = bodyLarge.scaled(scale),
    bodyMedium = bodyMedium.scaled(scale),
    bodySmall = bodySmall.scaled(scale),
    labelLarge = labelLarge.scaled(scale),
    labelMedium = labelMedium.scaled(scale),
    labelSmall = labelSmall.scaled(scale)
)

private fun TextStyle.scaled(scale: Float): TextStyle = copy(
    fontSize = if (fontSize.isSpecified) fontSize * scale else fontSize,
    lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight
)
