package com.example.methodmesh.modules.star_spectrum

import android.content.Context
import org.json.JSONArray
import java.io.File

internal object SpectrumReferenceRepository {
    private const val DIRECTORY = "star_spectrum"
    private const val FILE_NAME = "references.json"

    @Synchronized
    fun list(context: Context): List<SpectrumReference> {
        val file = storageFile(context)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).map { SpectrumReference.fromJson(array.getJSONObject(it)) }
                .sortedByDescending { it.createdTimeIso }
        }.getOrDefault(emptyList())
    }

    fun get(context: Context, id: String): SpectrumReference? = list(context).firstOrNull { it.id == id }

    @Synchronized
    fun save(context: Context, reference: SpectrumReference) {
        val existing = list(context).filterNot { it.id == reference.id }.toMutableList()
        existing += reference
        val file = storageFile(context)
        file.parentFile?.mkdirs()
        val array = JSONArray().apply { existing.sortedBy { it.createdTimeIso }.forEach { put(it.toJson()) } }
        val temporary = File(file.parentFile, "$FILE_NAME.tmp")
        temporary.writeText(array.toString(2))
        if (!temporary.renameTo(file)) {
            file.writeText(temporary.readText())
            temporary.delete()
        }
    }

    private fun storageFile(context: Context): File = File(File(context.filesDir, DIRECTORY), FILE_NAME)
}
