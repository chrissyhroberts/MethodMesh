package com.example.methodmesh.modules.arcade

import kotlin.math.max

/** Shared normalized geometry for Breakout rules and rendering. */
internal object ArcadeBreakoutRules {
    const val BALL_RADIUS = 0.014
    const val PADDLE_Y = 0.915
    const val PADDLE_HALF = 0.15
    const val PADDLE_HEIGHT = 0.025

    const val BRICK_LEFT = 0.035
    const val BRICK_RIGHT = 0.965
    const val BRICK_TOP = 0.10
    const val BRICK_HEIGHT = 0.045
    const val BRICK_GAP_X = 0.010
    const val BRICK_GAP_Y = 0.012

    data class Rect(
        val left: Double,
        val top: Double,
        val right: Double,
        val bottom: Double
    )

    fun brickRect(index: Int, rows: Int, cols: Int): Rect {
        val safeCols = max(1, cols)
        val row = index / safeCols
        val col = index % safeCols
        val totalWidth = BRICK_RIGHT - BRICK_LEFT
        val width = (totalWidth - BRICK_GAP_X * (safeCols - 1)) / safeCols
        val left = BRICK_LEFT + col * (width + BRICK_GAP_X)
        val top = BRICK_TOP + row * (BRICK_HEIGHT + BRICK_GAP_Y)
        return Rect(left, top, left + width, top + BRICK_HEIGHT)
    }
}
