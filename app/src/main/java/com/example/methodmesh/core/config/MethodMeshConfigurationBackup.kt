package com.example.methodmesh.core.config

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object MethodMeshConfigurationBackup {
    const val MIME_TYPE = "application/vnd.methodmesh.configuration+zip"
    const val FILE_NAME = "MethodMesh-Configuration-Backup.zip"
    private const val FORMAT = 1

    fun write(context: Context, output: OutputStream) {
        val dataRoot = File(context.applicationInfo.dataDir)
        val preferences = File(dataRoot, "shared_prefs")
        val files = context.filesDir
        ZipOutputStream(output).use { zip ->
            val manifest = JSONObject()
                .put("format", FORMAT)
                .put("created_at", Instant.now().toString())
                .put("application", "MethodMesh")
                .toString()
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(StandardCharsets.UTF_8))
            zip.closeEntry()
            addTree(zip, preferences, "shared_prefs")
            addTree(zip, files, "files") { relative ->
                !relative.startsWith("artifacts/workspace/") &&
                    !relative.startsWith("artifacts/store/") &&
                    !relative.startsWith("methodmesh/digital_signing/results/") &&
                    !relative.startsWith("methodmesh/digital_signing/work/") &&
                    !relative.substringAfterLast('/').startsWith("MethodMesh-Configuration-Backup")
            }
        }
    }

    fun restore(context: Context, input: InputStream) {
        val staging = File.createTempFile("methodmesh-restore-", "", context.cacheDir).apply {
            delete()
            mkdirs()
        }
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Backup contains an unsupported directory entry." }
                    val relative = entry.name.replace('\\', '/')
                    require(relative.isNotBlank() && !relative.startsWith('/') && ".." !in relative.split('/')) {
                        "Backup contains an unsafe path."
                    }
                    val destination = File(staging, relative)
                    destination.parentFile?.mkdirs()
                    destination.outputStream().use { zip.copyTo(it) }
                    zip.closeEntry()
                }
            }
            val manifest = File(staging, "manifest.json")
            require(manifest.isFile) { "This is not a MethodMesh configuration backup." }
            require(JSONObject(manifest.readText()).optInt("format", -1) == FORMAT) {
                "This MethodMesh backup format is not supported."
            }
            replaceTree(File(staging, "shared_prefs"), File(context.applicationInfo.dataDir, "shared_prefs"))
            replaceTree(File(staging, "files"), context.filesDir)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun addTree(
        zip: ZipOutputStream,
        root: File,
        prefix: String,
        include: (String) -> Boolean = { true }
    ) {
        if (!root.isDirectory) return
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val relative = file.relativeTo(root).invariantSeparatorsPath
            val entryName = "$prefix/$relative"
            if (!include(entryName.removePrefix("files/"))) return@forEach
            zip.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    private fun replaceTree(source: File, destination: File) {
        if (!source.isDirectory) return
        source.walkTopDown().filter { it.isFile }.forEach { file ->
            val target = File(destination, file.relativeTo(source).invariantSeparatorsPath)
            target.parentFile?.mkdirs()
            file.copyTo(target, overwrite = true)
        }
    }
}
