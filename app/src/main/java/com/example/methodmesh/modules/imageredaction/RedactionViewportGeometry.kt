package com.example.methodmesh.modules.imageredaction

import kotlin.math.abs

/** View-only state. Masks always remain in original, normalised document coordinates. */
internal data class RedactionView(val zoom: Float = 1f, val centerX: Float = .5f, val centerY: Float = .5f)
internal data class RedactionPoint(val x: Float, val y: Float)

/** Fit/clamped navigation follows the signing viewer, without a sibling-module dependency. */
internal data class RedactionViewportLayout(
    val viewWidth: Float, val viewHeight: Float, val width: Float, val height: Float,
    val originX: Float, val originY: Float, val view: RedactionView
) {
    fun point(x: Float, y: Float, clamp: Boolean = false): RedactionPoint? {
        if (!x.isFinite() || !y.isFinite()) return null
        val nx = (x - originX) / width; val ny = (y - originY) / height
        if (!clamp && (nx !in 0f..1f || ny !in 0f..1f)) return null
        return RedactionPoint(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
    }

    fun region(page: Int, startX: Float, startY: Float, endX: Float, endY: Float): PdfRedactionRegion? {
        val start = point(startX, startY) ?: return null
        val end = point(endX, endY, clamp = true) ?: return null
        if (abs(start.x - end.x) * width < 3 || abs(start.y - end.y) * height < 3) return null
        return PdfRedactionRegion(page, minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y))
    }

    /** Keep the document point under the fingers stationary while zooming, then apply the pan. */
    fun transform(anchorX: Float, anchorY: Float, panX: Float, panY: Float, factor: Float): RedactionView {
        if (listOf(anchorX, anchorY, panX, panY, factor).any { !it.isFinite() } || factor <= 0) return view
        val zoom = (view.zoom * factor).coerceIn(1f, 16f)
        val newWidth = width * (zoom / view.zoom); val newHeight = height * (zoom / view.zoom)
        if (!newWidth.isFinite() || !newHeight.isFinite()) return view
        val originX = anchorX + panX - (anchorX - this.originX) / width * newWidth
        val originY = anchorY + panY - (anchorY - this.originY) / height * newHeight
        return RedactionView(zoom, clampCenter((viewWidth / 2 - originX) / newWidth, newWidth, viewWidth),
            clampCenter((viewHeight / 2 - originY) / newHeight, newHeight, viewHeight))
    }
}

private fun clampCenter(center: Float, image: Float, viewport: Float): Float =
    if (image <= viewport) .5f else center.coerceIn(viewport / (2 * image), 1 - viewport / (2 * image))

internal fun redactionViewportLayout(viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int,
    view: RedactionView = RedactionView()): RedactionViewportLayout? {
    if (viewWidth <= 0 || viewHeight <= 0 || !viewWidth.isFinite() || !viewHeight.isFinite() || imageWidth <= 0 || imageHeight <= 0 ||
        listOf(view.zoom, view.centerX, view.centerY).any { !it.isFinite() }) return null
    val zoom = view.zoom.coerceIn(1f, 16f)
    val scale = minOf(viewWidth / imageWidth, viewHeight / imageHeight) * zoom
    val width = imageWidth * scale; val height = imageHeight * scale
    if (!width.isFinite() || !height.isFinite() || width <= 0 || height <= 0) return null
    val cx = clampCenter(view.centerX, width, viewWidth); val cy = clampCenter(view.centerY, height, viewHeight)
    return RedactionViewportLayout(viewWidth, viewHeight, width, height, viewWidth / 2 - cx * width,
        viewHeight / 2 - cy * height, RedactionView(zoom, cx, cy))
}

internal fun RedactionPoint.gridCell(rows: Int, columns: Int): String {
    require(rows > 0 && columns > 0)
    val row = (y * rows).toInt().coerceIn(0, rows - 1)
    val column = (x * columns).toInt().coerceIn(0, columns - 1)
    return "r${row + 1}c${column + 1}"
}
