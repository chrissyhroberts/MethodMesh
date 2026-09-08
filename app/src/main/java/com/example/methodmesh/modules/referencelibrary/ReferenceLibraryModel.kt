package com.example.methodmesh.modules.referencelibrary

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.Artifact
import com.example.methodmesh.core.artifacts.ArtifactOrigin
import com.example.methodmesh.core.artifacts.ArtifactRef
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

val REFERENCE_LIBRARY_BUILT_IN_SHELVES = listOf(
    LibraryShelf("first_aid", "First aid"),
    LibraryShelf("medical", "Medical"),
    LibraryShelf("safety", "Safety"),
    LibraryShelf("fieldwork", "Fieldwork"),
    LibraryShelf("equipment", "Equipment"),
    LibraryShelf("travel", "Travel"),
    LibraryShelf("personal", "Personal")
)

val REFERENCE_LIBRARY_BUILT_IN_SHELF_IDS: Set<String> =
    REFERENCE_LIBRARY_BUILT_IN_SHELVES.map { it.id }.toSet() + "all"

data class LibraryShelf(
    val id: String,
    val label: String
)

data class LibraryDocument(
    val id: String,
    val title: String,
    val shelf: String,
    val uri: String,
    val mimeType: String,
    val source: String = "User supplied",
    val version: String = "",
    val favourite: Boolean = false,
    val lastOpenedEpochMs: Long = 0L,
    val addedEpochMs: Long = System.currentTimeMillis()
)

class ReferenceLibraryRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("methodmesh.reference.library", Context.MODE_PRIVATE)

    fun documents(): List<LibraryDocument> = decode(prefs.getString(KEY_DOCUMENTS, "[]").orEmpty()).also { documents ->
        documents.forEach(::registerArtifact)
    }

    fun document(id: String): LibraryDocument? = documents().firstOrNull { it.id == id }

    fun upsert(document: LibraryDocument) {
        val current = documents().toMutableList()
        val index = current.indexOfFirst { it.id == document.id }
        if (index >= 0) current[index] = document else current.add(document)
        save(current)
        registerArtifact(document)
    }

    private fun registerArtifact(document: LibraryDocument) {
        runCatching {
            AndroidArtifacts.service(appContext).registerExternal(
                ref = ArtifactRef("library.${document.id}"),
                uri = document.uri,
                name = document.title,
                mime = document.mimeType
            )
        }
    }

    fun importDocument(
        title: String,
        shelf: String,
        uri: Uri,
        mimeType: String,
        source: String = "User supplied",
        version: String = ""
    ): LibraryDocument {
        val existing = documents().firstOrNull { it.uri == uri.toString() }
        val document = if (existing != null) {
            existing.copy(
                title = title.ifBlank { existing.title },
                shelf = shelf.ifBlank { existing.shelf },
                mimeType = mimeType.ifBlank { existing.mimeType },
                source = source.ifBlank { existing.source },
                version = version.ifBlank { existing.version }
            )
        } else {
            LibraryDocument(
                id = UUID.randomUUID().toString(),
                title = title.ifBlank { "Untitled document" },
                shelf = shelf.ifBlank { "personal" },
                uri = uri.toString(),
                mimeType = mimeType.ifBlank { "application/octet-stream" },
                source = source.ifBlank { "User supplied" },
                version = version
            )
        }
        upsert(document)
        return document
    }

    /** Add a persistent Files artifact to a shelf, retaining external URIs where possible. */
    fun importArtifact(artifact: Artifact, shelf: String): LibraryDocument {
        if (artifact.origin == ArtifactOrigin.EXTERNAL && artifact.location.startsWith("content://")) {
            return importDocument(
                title = artifact.displayName,
                shelf = shelf,
                uri = Uri.parse(artifact.location),
                mimeType = artifact.mimeType,
                source = "Files"
            )
        }

        val target = createManagedTarget(artifact.displayName, artifact.mimeType, "artifacts")
        AndroidArtifacts.service(appContext).open(artifact.ref).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return importManagedFileRecord(target, artifact.displayName, shelf, artifact.mimeType, "Files")
    }

    /**
     * Persist a dependency-produced or explicitly imported document into
     * MethodMesh-owned storage. This is used for scanner outputs and nearby
     * uploads where retaining a provider/cache URI would not be reliable.
     */
    fun importManagedCopy(
        sourceUri: Uri,
        title: String,
        shelf: String,
        mimeType: String = "application/pdf",
        source: String = "MethodMesh document scanner",
        storageFolder: String = "scans"
    ): LibraryDocument {
        val target = createManagedTarget(title, mimeType, storageFolder)
        appContext.contentResolver.openInputStream(sourceUri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: error("The source document could not be read.")
        return importManagedFileRecord(target, title, shelf, mimeType, source)
    }

    fun importManagedFile(
        sourceFile: File,
        title: String,
        shelf: String,
        mimeType: String = "application/octet-stream",
        source: String = "Nearby upload",
        storageFolder: String = "nearby"
    ): LibraryDocument {
        require(sourceFile.isFile) { "The uploaded document is unavailable." }
        val target = createManagedTarget(title, mimeType, storageFolder)
        sourceFile.inputStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return importManagedFileRecord(target, title, shelf, mimeType, source)
    }

    private fun importManagedFileRecord(
        file: File,
        title: String,
        shelf: String,
        mimeType: String,
        source: String
    ): LibraryDocument {
        val managedUri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", file)
        return importDocument(
            title = title.trim().ifBlank { file.name },
            shelf = shelf.ifBlank { "personal" },
            uri = managedUri,
            mimeType = mimeType.ifBlank { "application/octet-stream" },
            source = source.ifBlank { "User supplied" }
        )
    }

    private fun createManagedTarget(title: String, mimeType: String, storageFolder: String): File {
        val safeFolder = storageFolder.lowercase().replace(Regex("[^a-z0-9_-]"), "_").ifBlank { "imports" }
        val directory = File(appContext.filesDir, "reference_library/$safeFolder").apply { mkdirs() }
        val trimmedTitle = title.trim().ifBlank { "Document" }
        val extension = managedExtension(trimmedTitle, mimeType)
        val stemSource = if (extension.isNotBlank() && trimmedTitle.lowercase().endsWith(extension.lowercase())) {
            trimmedTitle.dropLast(extension.length)
        } else {
            trimmedTitle
        }
        val base = safeFileStem(stemSource)
        var file = File(directory, base + extension)
        var suffix = 2
        while (file.exists()) {
            file = File(directory, "$base ($suffix)$extension")
            suffix += 1
        }
        return file
    }

    private fun managedExtension(title: String, mimeType: String): String {
        val fromName = title.substringAfterLast('.', "")
            .takeIf { it.length in 1..12 && it.all { char -> char.isLetterOrDigit() } }
            ?.lowercase()
        if (fromName != null) return ".$fromName"
        return when (mimeType.lowercase()) {
            "application/pdf" -> ".pdf"
            "image/jpeg", "image/jpg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "text/plain" -> ".txt"
            "text/csv" -> ".csv"
            "application/json" -> ".json"
            "application/geo+json" -> ".geojson"
            "application/vnd.google-earth.kml+xml" -> ".kml"
            "application/zip" -> ".zip"
            else -> ""
        }
    }

    fun markOpened(id: String) {
        document(id)?.let { upsert(it.copy(lastOpenedEpochMs = System.currentTimeMillis())) }
    }

    fun toggleFavourite(id: String) {
        document(id)?.let { upsert(it.copy(favourite = !it.favourite)) }
    }

    fun updateDocument(id: String, title: String, shelf: String): LibraryDocument? {
        val existing = document(id) ?: return null
        val updated = existing.copy(
            title = title.trim().ifBlank { existing.title },
            shelf = shelf.ifBlank { existing.shelf }
        )
        upsert(updated)
        return updated
    }

    fun customShelves(): List<LibraryShelf> = decodeShelves(prefs.getString(KEY_CUSTOM_SHELVES, "[]").orEmpty())

    fun addCustomShelf(label: String): LibraryShelf {
        val cleanLabel = label.trim().replace(Regex("\\s+"), " ").take(40)
        require(cleanLabel.isNotBlank()) { "Shelf name cannot be empty." }
        val existing = customShelves()
        val reservedIds = BUILT_IN_SHELF_IDS + existing.map { it.id }
        var base = cleanLabel.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "shelf" }
            .take(32)
        var id = base
        var suffix = 2
        while (id in reservedIds) {
            id = "${base.take(28)}_$suffix"
            suffix += 1
        }
        if (existing.any { it.label.equals(cleanLabel, ignoreCase = true) }) {
            error("A shelf with that name already exists.")
        }
        val shelf = LibraryShelf(id, cleanLabel)
        saveShelves(existing + shelf)
        return shelf
    }

    fun deleteCustomShelf(id: String, fallbackShelf: String = "personal"): Int {
        val current = customShelves()
        require(current.any { it.id == id }) { "Only custom shelves can be deleted." }
        val affected = documents().count { it.shelf == id }
        save(documents().map { if (it.shelf == id) it.copy(shelf = fallbackShelf) else it })
        saveShelves(current.filterNot { it.id == id })
        return affected
    }

    fun remove(id: String) = save(documents().filterNot { it.id == id })

    fun isReadable(document: LibraryDocument): Boolean = runCatching {
        appContext.contentResolver.openFileDescriptor(Uri.parse(document.uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    private fun save(documents: List<LibraryDocument>) {
        prefs.edit().putString(KEY_DOCUMENTS, encode(documents)).apply()
    }

    private fun encode(documents: List<LibraryDocument>): String = JSONArray().apply {
        documents.forEach { document ->
            put(JSONObject().apply {
                put("id", document.id)
                put("title", document.title)
                put("shelf", document.shelf)
                put("uri", document.uri)
                put("mime_type", document.mimeType)
                put("source", document.source)
                put("version", document.version)
                put("favourite", document.favourite)
                put("last_opened", document.lastOpenedEpochMs)
                put("added", document.addedEpochMs)
            })
        }
    }.toString()

    private fun saveShelves(shelves: List<LibraryShelf>) {
        prefs.edit().putString(KEY_CUSTOM_SHELVES, JSONArray().apply {
            shelves.forEach { shelf ->
                put(JSONObject().apply {
                    put("id", shelf.id)
                    put("label", shelf.label)
                })
            }
        }.toString()).apply()
    }

    private fun decodeShelves(raw: String): List<LibraryShelf> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val id = item.optString("id").trim()
                val label = item.optString("label").trim()
                if (id.isNotBlank() && label.isNotBlank() && id !in BUILT_IN_SHELF_IDS) add(LibraryShelf(id, label))
            }
        }
    }.getOrDefault(emptyList())

    private fun decode(raw: String): List<LibraryDocument> = runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    LibraryDocument(
                        id = item.getString("id"),
                        title = item.optString("title", "Untitled document"),
                        shelf = item.optString("shelf", "personal"),
                        uri = item.getString("uri"),
                        mimeType = item.optString("mime_type", "application/octet-stream"),
                        source = item.optString("source", "User supplied"),
                        version = item.optString("version", ""),
                        favourite = item.optBoolean("favourite", false),
                        lastOpenedEpochMs = item.optLong("last_opened", 0L),
                        addedEpochMs = item.optLong("added", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun safeFileStem(raw: String): String =
        raw.trim()
            .replace(Regex("[\\/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .take(80)
            .ifBlank { "Scanned document" }

    companion object {
        private const val KEY_DOCUMENTS = "documents"
        private const val KEY_CUSTOM_SHELVES = "custom_shelves"
        private val BUILT_IN_SHELF_IDS = REFERENCE_LIBRARY_BUILT_IN_SHELF_IDS
    }
}
