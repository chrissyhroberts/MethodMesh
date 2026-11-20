package com.example.methodmesh.modules.imageredaction

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor

/** Normalised coordinates in the displayed, top-left-origin page. */
internal data class PdfRedactionRegion(val page: Int, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    init {
        require(page >= 0 && listOf(left, top, right, bottom).all { it.isFinite() && it in 0f..1f }) { "Invalid redaction region" }
        require(left < right && top < bottom) { "Redaction regions must have a non-zero area" }
    }

    fun pixels(width: Int, height: Int): IntArray {
        require(width > 0 && height > 0)
        // Round outwards, with a one-pixel guard. Never leave a fractional strip of source pixels.
        return intArrayOf((floor(left * width).toInt() - 1).coerceAtLeast(0),
            (floor(top * height).toInt() - 1).coerceAtLeast(0),
            (ceil(right * width).toInt() + 1).coerceAtMost(width),
            (ceil(bottom * height).toInt() + 1).coerceAtMost(height))
    }

    companion object {
        fun fromDrag(page: Int, startX: Float, startY: Float, endX: Float, endY: Float,
            viewWidth: Float, viewHeight: Float, imageWidth: Int, imageHeight: Int): PdfRedactionRegion? {
            return redactionViewportLayout(viewWidth, viewHeight, imageWidth, imageHeight)
                ?.region(page, startX, startY, endX, endY)
        }
    }
}

internal object PdfRedactionRegions {
    const val MAX_REGIONS = 500
    fun encode(regions: List<PdfRedactionRegion>): String {
        require(regions.size <= MAX_REGIONS) { "Too many redaction regions" }
        return JSONArray().apply { regions.forEach { r -> put(JSONObject().put("page", r.page)
            .put("left", r.left).put("top", r.top).put("right", r.right).put("bottom", r.bottom)) } }.toString()
    }
    fun decode(json: String): List<PdfRedactionRegion> {
        val array = JSONArray(json)
        require(array.length() <= MAX_REGIONS) { "Too many redaction regions" }
        return (0 until array.length()).map { index -> array.getJSONObject(index).let { r ->
            val page = r.getDouble("page")
            require(page.isFinite() && page >= 0 && page <= Int.MAX_VALUE && page % 1.0 == 0.0) { "Invalid page number" }
            PdfRedactionRegion(page.toInt(), r.getDouble("left").toFloat(), r.getDouble("top").toFloat(),
                r.getDouble("right").toFloat(), r.getDouble("bottom").toFloat())
        } }
    }
}
