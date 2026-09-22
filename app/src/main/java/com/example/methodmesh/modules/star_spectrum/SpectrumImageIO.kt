package com.example.methodmesh.modules.star_spectrum

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import kotlin.math.max

internal object SpectrumImageIO {
    fun decode(context: Context, rawUri: String, maxDimension: Int = 4096): Bitmap {
        require(rawUri.isNotBlank()) { "Choose a spectrum image first." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(context.contentResolver, rawUri).use { input ->
            requireNotNull(input) { "Could not open the selected spectrum image." }
            BitmapFactory.decodeStream(input, null, bounds)
        }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a readable raster image." }
        var sample = 1
        while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxDimension) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = open(context.contentResolver, rawUri).use { input ->
            requireNotNull(input) { "Could not reopen the selected spectrum image." }
            BitmapFactory.decodeStream(input, null, options)
        } ?: error("The selected file could not be decoded as an image.")

        val orientation = runCatching {
            open(context.contentResolver, rawUri).use { input ->
                input?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                    ?: ExifInterface.ORIENTATION_NORMAL
            }
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return orient(decoded, orientation)
    }


    fun copyOriginalToCache(context: Context, rawUri: String, stem: String): File {
        val resolver = context.contentResolver
        val uri = rawUri.takeIf { it.startsWith("content://") || it.startsWith("file://") }?.let(Uri::parse)
        val displayName = if (uri != null) runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull() else File(rawUri).name
        val mime = uri?.let(resolver::getType)
        val extension = displayName?.substringAfterLast('.', "")?.takeIf { it.length in 2..8 }
            ?: mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
            ?: "img"
        val safeStem = stem.replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        val out = File(context.cacheDir, "${safeStem}_${System.currentTimeMillis()}.$extension")
        open(resolver, rawUri).use { input ->
            requireNotNull(input) { "Could not reopen the selected source image for return." }
            out.outputStream().use { output -> input.copyTo(output) }
        }
        return out
    }

    fun takePersistableReadPermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun open(resolver: ContentResolver, rawUri: String): InputStream? {
        return when {
            rawUri.startsWith("content://") || rawUri.startsWith("file://") -> resolver.openInputStream(Uri.parse(rawUri))
            else -> File(rawUri).takeIf(File::isFile)?.inputStream()
        }
    }

    private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { matrix.setRotate(180f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.setRotate(90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.setRotate(-90f); matrix.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }
}
