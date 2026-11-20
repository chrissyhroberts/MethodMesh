package com.example.methodmesh.modules.screentools

import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

data class ScreenRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    init {
        require(left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f)
        require(right > left && bottom > top)
    }

    fun toJson() = JSONArray()
        .put(left.toDouble())
        .put(top.toDouble())
        .put(right.toDouble())
        .put(bottom.toDouble())
}

data class ScreenSelection(
    val name: String,
    val region: ScreenRegion,
    val id: String = UUID.randomUUID().toString()
) {
    fun toJson() = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("roi", region.toJson())
        .put("coordinate_frame", "normalised_image")
}

fun SnapshotStateList<ScreenSelection>.toSelectionJson(): String = JSONArray()
    .apply { forEach { put(it.toJson()) } }
    .toString()

fun normalizedRegion(x1: Float, y1: Float, x2: Float, y2: Float): ScreenRegion? {
    val left = min(x1, x2).coerceIn(0f, 1f)
    val top = min(y1, y2).coerceIn(0f, 1f)
    val right = max(x1, x2).coerceIn(0f, 1f)
    val bottom = max(y1, y2).coerceIn(0f, 1f)
    return if (right - left >= 0.01f && bottom - top >= 0.01f) {
        ScreenRegion(left, top, right, bottom)
    } else null
}
