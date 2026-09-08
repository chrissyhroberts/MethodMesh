package com.example.methodmesh.modules.digitalsigning

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DigitalSigningDraftStore {
    private const val DRAFT_FILE = "active_draft.json"

    data class RestoredDraft(
        val workingPdf: WorkingPdf,
        val strokes: List<InkStroke>,
        val currentPage: Int,
        val committedResult: DigitalSigningCommittedResult?
    )

    fun importPdf(
        context: Context,
        uri: Uri,
        origin: PdfInputOrigin,
        displayNameHint: String? = null
    ): WorkingPdf {
        val dir = workingDir(context)
        dir.mkdirs()
        val temp = File(dir, ".incoming-${System.nanoTime()}.bin")
        copyUri(context, uri, temp)
        require(temp.length() > 0L) { "The selected PDF is empty." }
        val inputName = displayNameHint
            ?.takeIf { it.isNotBlank() }
            ?: displayName(context, uri)
            ?: "document.pdf"
        val inputMime = context.contentResolver.getType(uri).orEmpty()
        val source = if (DigitalSigningDocumentConverter.isPdf(inputName, inputMime)) {
            temp
        } else {
            val converted = File(dir, ".converted-${System.nanoTime()}.pdf")
            runCatching {
                DigitalSigningDocumentConverter.convert(temp, converted, inputName, inputMime)
            }.getOrElse {
                temp.delete()
                converted.delete()
                throw it
            }
            temp.delete()
            converted
        }
        val sha = DigitalSigningHash.sha256(source)
        val target = File(dir, "source-${sha.take(16)}.pdf")
        if (!target.exists()) {
            if (!source.renameTo(target)) {
                source.copyTo(target, overwrite = true)
                source.delete()
            }
        } else {
            source.delete()
        }
        val pageCount = DigitalSigningPdfEngine.pageCount(target)
        require(pageCount > 0) { "The selected PDF has no pages." }
        val working = WorkingPdf(
            sourceFile = target,
            sourceUriString = uri.toString(),
            displayName = if (DigitalSigningDocumentConverter.isPdf(inputName, inputMime)) {
                normalisePdfName(inputName)
            } else {
                inputName.substringBeforeLast('.').ifBlank { "document" } + ".pdf"
            },
            sourceOrigin = origin,
            sourceSha256 = sha,
            pageCount = pageCount
        )
        saveDraft(context, working, emptyList(), 0, committedResult = null)
        return working
    }

    fun saveDraft(
        context: Context,
        workingPdf: WorkingPdf,
        strokes: List<InkStroke>,
        currentPage: Int,
        committedResult: DigitalSigningCommittedResult?
    ) {
        val root = JSONObject().apply {
            put("source_path", workingPdf.sourceFile.absolutePath)
            put("source_uri", workingPdf.sourceUriString)
            put("display_name", workingPdf.displayName)
            put("source_origin", workingPdf.sourceOrigin.id)
            put("source_sha256", workingPdf.sourceSha256)
            put("page_count", workingPdf.pageCount)
            put("current_page", currentPage.coerceIn(0, (workingPdf.pageCount - 1).coerceAtLeast(0)))
            put("strokes", JSONArray().apply {
                strokes.forEach { stroke ->
                    put(JSONObject().apply {
                        put("id", stroke.id)
                        put("page", stroke.pageIndex)
                        put("width_pt", stroke.widthPt.toDouble())
                        put("argb", stroke.argb)
                        put("points", JSONArray().apply {
                            stroke.points.forEach { point ->
                                put(JSONArray().apply {
                                    put(point.x.toDouble())
                                    put(point.y.toDouble())
                                })
                            }
                        })
                    })
                }
            })
            put("committed_result", committedResult?.let { JSONObject(DigitalSigningResultJson.fullJson(it)) } ?: JSONObject.NULL)
        }
        atomicWrite(File(workingDir(context), DRAFT_FILE), root.toString())
    }

    fun saveCommitted(context: Context, committedResult: DigitalSigningCommittedResult) {
        val restored = restore(context) ?: return
        saveDraft(
            context = context,
            workingPdf = restored.workingPdf,
            strokes = restored.strokes,
            currentPage = restored.currentPage,
            committedResult = committedResult
        )
    }

    fun restore(context: Context): RestoredDraft? = runCatching {
        val file = File(workingDir(context), DRAFT_FILE)
        if (!file.exists()) return null
        val root = JSONObject(file.readText())
        val source = File(root.getString("source_path"))
        if (!source.exists()) return null
        val pageCount = root.optInt("page_count", 0).takeIf { it > 0 }
            ?: DigitalSigningPdfEngine.pageCount(source)
        val originId = root.optString("source_origin")
        val working = WorkingPdf(
            sourceFile = source,
            sourceUriString = root.optString("source_uri"),
            displayName = root.optString("display_name", "document.pdf"),
            sourceOrigin = PdfInputOrigin.entries.firstOrNull { it.id == originId } ?: PdfInputOrigin.RestoredDraft,
            sourceSha256 = root.optString("source_sha256").ifBlank { DigitalSigningHash.sha256(source) },
            pageCount = pageCount
        )
        val strokes = root.optJSONArray("strokes").toStrokeList()
        val committed = root.optJSONObject("committed_result")
            ?.let { DigitalSigningResultJson.parse(it.toString()) }
            ?.takeIf { resultUriStillValid(context, it.signedPdfUri) }
            ?.let { result ->
                if (result.verificationBundle.status == "created" && !resultUriStillValid(context, result.verificationBundle.uri.orEmpty())) {
                    result.copy(verificationBundle = VerificationBundle.pending())
                } else {
                    result
                }
            }
        RestoredDraft(
            workingPdf = working.copy(sourceOrigin = if (working.sourceOrigin == PdfInputOrigin.Unknown) PdfInputOrigin.RestoredDraft else working.sourceOrigin),
            strokes = strokes,
            currentPage = root.optInt("current_page", 0).coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
            committedResult = committed
        )
    }.getOrNull()

    private fun resultsDir(context: Context): File =
        File(context.cacheDir, "methodmesh/digital_signing/results").apply { mkdirs() }

    fun nextResultFile(context: Context, sourceName: String): File {
        val results = resultsDir(context)
        val stem = sourceName.removeSuffix(".pdf").removeSuffix(".PDF")
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "document" }
        var candidate = File(results, "${stem}_signed_${DigitalSigningTime.fileStamp()}.pdf")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(results, "${stem}_signed_${DigitalSigningTime.fileStamp()}_$counter.pdf")
            counter += 1
        }
        return candidate
    }

    fun resultFile(context: Context, filename: String): File =
        File(resultsDir(context), File(filename).name)

    fun nextVerificationBundleFile(context: Context, sourceName: String): File {
        val results = resultsDir(context)
        val stem = sourceName.removeSuffix(".pdf").removeSuffix(".PDF")
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "document" }
        var candidate = File(results, "${stem}_provenance_${DigitalSigningTime.fileStamp()}.zip")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(results, "${stem}_provenance_${DigitalSigningTime.fileStamp()}_$counter.zip")
            counter += 1
        }
        return candidate
    }

    fun clear(context: Context) {
        File(workingDir(context), DRAFT_FILE).delete()
    }

    private fun workingDir(context: Context): File =
        File(context.filesDir, "methodmesh/digital_signing/work").apply { mkdirs() }

    private fun copyUri(context: Context, uri: Uri, target: File) {
        target.parentFile?.mkdirs()
        val scheme = uri.scheme.orEmpty().lowercase()
        if (scheme.isBlank() || scheme == "file") {
            val source = File(requireNotNull(uri.path) { "PDF file path is missing." })
            source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        } else {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Could not open the supplied PDF URI.")
            input.use { stream -> target.outputStream().use { output -> stream.copyTo(output) } }
        }
    }

    private fun displayName(context: Context, uri: Uri): String? {
        if (uri.scheme.equals("file", true)) return uri.lastPathSegment
        var cursor: Cursor? = null
        return try {
            cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor != null && cursor.moveToFirst()) cursor.getString(0) else uri.lastPathSegment
        } catch (_: Exception) {
            uri.lastPathSegment
        } finally {
            cursor?.close()
        }
    }

    private fun normalisePdfName(name: String): String =
        if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"

    private fun resultUriStillValid(context: Context, uriString: String): Boolean {
        if (uriString.isBlank()) return false
        return runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use { true } ?: false
        }.getOrDefault(false)
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, ".${target.name}.${System.nanoTime()}.tmp")
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }

    private fun JSONArray?.toStrokeList(): List<InkStroke> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                val pointsJson = item.optJSONArray("points") ?: JSONArray()
                val points = buildList {
                    for (pointIndex in 0 until pointsJson.length()) {
                        val pair = pointsJson.optJSONArray(pointIndex) ?: continue
                        add(InkPoint(pair.optDouble(0).toFloat(), pair.optDouble(1).toFloat()))
                    }
                }
                if (points.isNotEmpty()) {
                    add(
                        InkStroke(
                            id = item.optString("id", "stroke-$index"),
                            pageIndex = item.optInt("page", 0),
                            points = points,
                            widthPt = item.optDouble("width_pt", 2.4).toFloat(),
                            argb = item.optInt("argb", 0xFF141718.toInt())
                        )
                    )
                }
            }
        }
    }
}
