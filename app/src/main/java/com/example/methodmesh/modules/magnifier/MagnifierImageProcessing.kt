package com.example.methodmesh.modules.magnifier

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import java.io.File

internal enum class MagnifierFilter(val wireValue: String) {
    NORMAL("normal"),
    HIGH_CONTRAST("high_contrast"),
    MONOCHROME("monochrome"),
    NEGATIVE("negative");

    companion object {
        fun fromWireValue(value: String): MagnifierFilter =
            values().firstOrNull { it.wireValue == value.trim().lowercase() } ?: NORMAL
    }
}

internal fun decodeOrientedBitmap(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val rotation = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotation == 0f) return bitmap

    val matrix = Matrix().apply { postRotate(rotation) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}

internal fun magnifierColorMatrix(filter: MagnifierFilter): ColorMatrix? = when (filter) {
    MagnifierFilter.NORMAL -> null
    MagnifierFilter.MONOCHROME -> ColorMatrix().apply { setSaturation(0f) }
    MagnifierFilter.HIGH_CONTRAST -> {
        val contrast = 1.65f
        val translate = 128f * (1f - contrast)
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
    }
    MagnifierFilter.NEGATIVE -> ColorMatrix(
        floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f
        )
    )
}

internal fun applyMagnifierFilter(source: Bitmap, filter: MagnifierFilter): Bitmap {
    val matrix = magnifierColorMatrix(filter)
        ?: return source.copy(Bitmap.Config.ARGB_8888, false)

    val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    Canvas(output).drawBitmap(
        source,
        0f,
        0f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) }
    )
    return output
}

internal fun writeFilteredJpeg(source: File, target: File, filter: MagnifierFilter): Boolean {
    val bitmap = decodeOrientedBitmap(source) ?: return false
    val filtered = applyMagnifierFilter(bitmap, filter)
    return try {
        target.outputStream().use { filtered.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    } finally {
        filtered.recycle()
        bitmap.recycle()
    }
}
