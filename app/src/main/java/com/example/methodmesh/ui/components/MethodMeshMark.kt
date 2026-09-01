package com.example.methodmesh.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.methodmesh.ui.theme.MethodMeshGreen
import com.example.methodmesh.ui.theme.MethodMeshInk
import com.example.methodmesh.ui.theme.MethodMeshWarmGrey

@Composable
fun MethodMeshMark(
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    ink: Color = MethodMeshInk,
    accent: Color = MethodMeshGreen,
    support: Color = MethodMeshWarmGrey
) {
    Canvas(
        modifier = modifier
            .size(size)
            .aspectRatio(1f)
    ) {
        val unit = this.size.minDimension
        val thickness = unit * 0.105f
        drawRoundedBar(
            color = ink,
            x = unit * 0.17f,
            y = unit * 0.25f,
            width = unit * 0.62f,
            height = thickness
        )
        drawRoundedBar(
            color = accent,
            x = unit * 0.31f,
            y = unit * 0.45f,
            width = unit * 0.48f,
            height = thickness
        )
        drawRoundedBar(
            color = support,
            x = unit * 0.48f,
            y = unit * 0.65f,
            width = unit * 0.30f,
            height = thickness
        )
    }
}

private fun DrawScope.drawRoundedBar(
    color: Color,
    x: Float,
    y: Float,
    width: Float,
    height: Float
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(x, y),
        size = Size(width, height),
        cornerRadius = CornerRadius(height / 2f, height / 2f)
    )
}
