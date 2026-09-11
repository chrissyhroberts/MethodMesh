package com.example.methodmesh.modules.paperbridge

import android.content.Context
import android.net.Uri
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

internal object PaperTemplateImport {
    private data class Payload(val manifest: String, val templatePng: ByteArray? = null)

    fun readManifest(context: Context, uri: Uri): String = readPayload(context, uri).manifest

    /** Restore definition and, for a portable bundle, its canonical authoring image. */
    fun importIntoWorkspace(context: Context, uri: Uri): PaperTemplate {
        val payload = readPayload(context, uri)
        val template = PaperBridgeWorkspace.saveAndActivateTemplate(context, payload.manifest)
        payload.templatePng?.let { bytes ->
            val source = PaperDesignSourceStore.importBytes(
                context = context,
                bytes = bytes,
                mimeType = "image/png",
                displayName = "${template.templateId}-v${template.version}.template.png",
                alreadyRegistered = true
            )
            PaperDesignSourceStore.bind(context, template, source)
        }
        return template
    }

    private fun readPayload(context: Context, uri: Uri): Payload {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Could not read Paper Bridge template.")
        require(bytes.size <= 30 * 1024 * 1024) { "Template file is too large." }
        val name = uri.lastPathSegment.orEmpty().lowercase()
        return when {
            name.endsWith(".zip") || isZip(bytes) -> readZip(bytes)
            name.endsWith(".yaml") || name.endsWith(".yml") -> Payload(readYaml(bytes.toString(Charsets.UTF_8)))
            else -> Payload(bytes.toString(Charsets.UTF_8))
        }.let { it.copy(manifest = PaperBridgeWorkspace.normalizeManifest(it.manifest)) }
    }

    private fun readZip(bytes: ByteArray): Payload {
        var manifest: String? = null
        var templatePng: ByteArray? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var count = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                count++
                require(count <= 32) { "Paper Bridge bundle contains too many files." }
                if (!entry.isDirectory) {
                    when (entry.name.substringAfterLast('/').lowercase()) {
                        "template.paperbridge.json" -> manifest = zip.readBytes().toString(Charsets.UTF_8)
                        "template.png" -> templatePng = zip.readBytes()
                    }
                }
                zip.closeEntry()
            }
        }
        return Payload(
            manifest = manifest ?: error("Paper Bridge bundle does not contain template.paperbridge.json."),
            templatePng = templatePng
        )
    }

    /** Imports exactly the fixed YAML profile produced by PaperDesignExportStore. */
    private fun readYaml(text: String): String {
        val lines = text.lines()
        val marker = lines.indexOfFirst { it.trim() == "manifest_json: |" }
        require(marker >= 0) { "YAML is not a Paper Bridge fixed-profile backup." }
        val body = lines.drop(marker + 1).takeWhile { it.startsWith("  ") || it.isBlank() }
            .joinToString("\n") { if (it.startsWith("  ")) it.drop(2) else it }
            .trim()
        require(body.isNotBlank()) { "Paper Bridge YAML backup contains no manifest." }
        return body
    }

    private fun isZip(bytes: ByteArray): Boolean = bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
}
