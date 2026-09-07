package com.example.methodmesh.modules.digitalsigning

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object DigitalSigningPdfEngine {
    fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }

    fun renderPage(file: File, pageIndex: Int, targetLongSidePx: Int = 1800): RenderedPdfPage =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "PDF page index out of range" }
                renderer.openPage(pageIndex).use { page ->
                    val bitmap = renderBitmap(page, targetLongSidePx)
                    RenderedPdfPage(
                        pageIndex = pageIndex,
                        bitmap = bitmap,
                        pageWidthPt = page.width.toFloat(),
                        pageHeightPt = page.height.toFloat()
                    )
                }
            }
        }

    fun commit(
        sourceFile: File,
        strokes: List<InkStroke>,
        outputFile: File,
        finalise: Boolean
    ): PdfCommitResult {
        outputFile.parentFile?.mkdirs()
        val committedAt = DigitalSigningTime.nowIso()
        val pageCount: Int

        if (finalise) {
            val intermediate = File(outputFile.parentFile, ".${outputFile.name}.intermediate-${System.nanoTime()}.pdf")
            try {
                pageCount = writePdfWithInk(sourceFile, strokes, intermediate)
                rasterFlatten(intermediate, outputFile)
            } finally {
                intermediate.delete()
            }
        } else {
            pageCount = writePdfWithInk(sourceFile, strokes, outputFile)
        }

        return PdfCommitResult(
            outputFile = outputFile,
            signedSha256 = DigitalSigningHash.sha256(outputFile),
            pageCount = pageCount,
            inkFlattened = finalise,
            formFieldsFlattened = true,
            finalised = finalise,
            finalisationMode = if (finalise) "raster_flattened" else "page_copy_with_vector_ink",
            permissionRestrictionsApplied = false,
            newMarkupBlocked = false,
            committedAtUtc = committedAt
        )
    }

    private fun writePdfWithInk(sourceFile: File, strokes: List<InkStroke>, outputFile: File): Int {
        val output = PdfDocument()
        try {
            return ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { sourcePage ->
                            val pageWidth = sourcePage.width.coerceAtLeast(1)
                            val pageHeight = sourcePage.height.coerceAtLeast(1)
                            val background = renderBitmap(sourcePage, targetLongSidePx = 2400)
                            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                            val page = output.startPage(pageInfo)
                            try {
                                page.canvas.drawColor(Color.WHITE)
                                page.canvas.drawBitmap(
                                    background,
                                    null,
                                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                )
                                drawInk(page.canvas, pageWidth.toFloat(), pageHeight.toFloat(), strokes.filter { it.pageIndex == index })
                            } finally {
                                output.finishPage(page)
                                background.recycle()
                            }
                        }
                    }
                    outputFile.outputStream().use { stream -> output.writeTo(stream) }
                    renderer.pageCount
                }
            }
        } finally {
            output.close()
        }
    }

    private fun rasterFlatten(sourceFile: File, outputFile: File) {
        val output = PdfDocument()
        try {
            ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { sourcePage ->
                            val pageWidth = sourcePage.width.coerceAtLeast(1)
                            val pageHeight = sourcePage.height.coerceAtLeast(1)
                            val bitmap = renderBitmap(sourcePage, targetLongSidePx = 2600)
                            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                            val page = output.startPage(pageInfo)
                            try {
                                page.canvas.drawColor(Color.WHITE)
                                page.canvas.drawBitmap(
                                    bitmap,
                                    null,
                                    RectF(0f, 0f, pageWidth.toFloat(), pageHeight.toFloat()),
                                    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                                )
                            } finally {
                                output.finishPage(page)
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
            outputFile.outputStream().use { stream -> output.writeTo(stream) }
        } finally {
            output.close()
        }
    }

    private fun drawInk(canvas: android.graphics.Canvas, width: Float, height: Float, strokes: List<InkStroke>) {
        strokes.forEach { stroke ->
            if (stroke.points.isEmpty()) return@forEach
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.widthPt.coerceIn(0.6f, 18f)
                color = stroke.argb
            }
            val first = stroke.points.first()
            val path = Path().apply {
                moveTo(first.x.coerceIn(0f, 1f) * width, first.y.coerceIn(0f, 1f) * height)
                stroke.points.drop(1).forEach { point ->
                    lineTo(point.x.coerceIn(0f, 1f) * width, point.y.coerceIn(0f, 1f) * height)
                }
            }
            if (stroke.points.size == 1) {
                val x = first.x.coerceIn(0f, 1f) * width
                val y = first.y.coerceIn(0f, 1f) * height
                canvas.drawCircle(x, y, paint.strokeWidth / 2f, Paint(paint).apply { style = Paint.Style.FILL })
            } else {
                canvas.drawPath(path, paint)
            }
        }
    }

    private fun renderBitmap(page: PdfRenderer.Page, targetLongSidePx: Int): Bitmap {
        val longSide = max(page.width, page.height).coerceAtLeast(1)
        val scale = (targetLongSidePx.toFloat() / longSide.toFloat()).coerceIn(1f, 4f)
        val width = (page.width * scale).roundToInt().coerceAtLeast(1)
        val height = (page.height * scale).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        val matrix = Matrix().apply { postScale(scale, scale) }
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }
}
