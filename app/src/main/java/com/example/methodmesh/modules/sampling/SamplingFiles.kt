package com.example.methodmesh.modules.sampling

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import java.io.File
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter

data class SamplingCsvInput(
    val text: String,
    val bytes: ByteArray,
    val displayName: String,
    val sha256: String
)

data class SamplingFileArtifacts(
    val resultFile: File,
    val manifestFile: File,
    val resultUri: String,
    val manifestUri: String,
    val outputFileSha256: String,
    val manifestJson: String
)

object SamplingFiles {
    const val TEMPLATE_CSV = "item_id,item_label,weight,stratum,eligible\r\n001,Alice,1,A,true\r\n002,Bob,1,A,true\r\n003,Carol,2,B,true\r\n"

    fun readCsv(context: Context, uri: Uri): SamplingCsvInput {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Could not open the selected CSV file.")
        val text = bytes.toString(StandardCharsets.UTF_8)
        // Parse now so malformed files fail at selection rather than much later.
        SamplingCsv.parse(text)
        return SamplingCsvInput(
            text = text,
            bytes = bytes,
            displayName = displayName(context, uri).ifBlank { "sampling_input.csv" },
            sha256 = SamplingProvenance.sha256(bytes)
        )
    }

    fun writeTemplate(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.write(TEMPLATE_CSV.toByteArray(StandardCharsets.UTF_8))
        } ?: throw IllegalStateException("Could not create the CSV template.")
    }

    fun writeRun(context: Context, run: SamplingRun): SamplingFileArtifacts {
        val directory = File(context.cacheDir, "sampling").apply { mkdirs() }
        val sourceBase = run.sourceFileName
            ?.substringBeforeLast('.')
            ?.sanitizeFileName()
            ?.takeIf { it.isNotBlank() }
            ?: "sampling_result"
        val stamp = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now())
        val base = "${sourceBase}_sampled_$stamp"

        val resultText = when (run.config.outputFormat) {
            SamplingOutputFormat.CSV -> SamplingCsv.write(run.outputHeaders, run.outputRows)
            SamplingOutputFormat.JSON -> run.resultJson()
        }
        val extension = run.config.outputFormat.wireValue
        val resultFile = uniqueFile(directory, "$base.$extension")
        val resultBytes = resultText.toByteArray(StandardCharsets.UTF_8)
        resultFile.writeBytes(resultBytes)
        val outputSha = SamplingProvenance.sha256(resultBytes)

        val populationSha = SamplingProvenance.sha256(
            SamplingProvenance.canonicalJson(SamplingProvenance.populationPayload(run.population))
        )
        val resultSha = SamplingProvenance.sha256(
            SamplingProvenance.canonicalJson(SamplingProvenance.resultPayload(run))
        )
        val manifest = SamplingProvenance.buildManifest(
            run = run,
            inputFileSha256 = run.inputFileSha256,
            outputFileSha256 = outputSha,
            resultSha256 = resultSha,
            populationSha256 = populationSha
        )
        val manifestFile = File(resultFile.parentFile, resultFile.nameWithoutExtension + ".manifest.json")
        manifestFile.writeText(manifest.manifestJson, StandardCharsets.UTF_8)

        val resultUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", resultFile).toString()
        val manifestUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", manifestFile).toString()
        return SamplingFileArtifacts(resultFile, manifestFile, resultUri, manifestUri, outputSha, manifest.manifestJson)
    }

    private fun displayName(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
        }.orEmpty()
    }.getOrDefault("")

    private fun uniqueFile(directory: File, requestedName: String): File {
        var candidate = File(directory, requestedName)
        var index = 2
        while (candidate.exists()) {
            val stem = requestedName.substringBeforeLast('.')
            val extension = requestedName.substringAfterLast('.', "")
            candidate = File(directory, if (extension.isBlank()) "$stem-$index" else "$stem-$index.$extension")
            index += 1
        }
        return candidate
    }

    private fun String.sanitizeFileName(): String =
        replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').take(80)
}
