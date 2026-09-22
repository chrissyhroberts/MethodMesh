package com.example.methodmesh.modules.textdocuments

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Shares the current working buffer, never a stale on-disk version. */
object DocumentSharing {
    fun share(context: Context, title: String, format: DocumentFormat, text: String): Result<Unit> = runCatching {
        val cleaned = title
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim()
            .ifBlank { "document.${format.extension}" }
            .take(140)
        val safeTitle = DocumentIo.ensureExtension(cleaned, format)
        val shareDir = File(context.cacheDir, "textdocuments_share").apply { mkdirs() }
        shareDir.listFiles()
            ?.filter { it.isFile && System.currentTimeMillis() - it.lastModified() > 24L * 60L * 60L * 1000L }
            ?.forEach { it.delete() }

        val file = File(shareDir, safeTitle)
        file.writeText(text, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_SUBJECT, safeTitle)
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(safeTitle, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Share $safeTitle").apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
