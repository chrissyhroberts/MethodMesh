package com.example.methodmesh.transport.android

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import com.example.methodmesh.transport.workflow.ExternalWorkflowRequest
import java.security.MessageDigest
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * Browser/Enketo return adapter for stock web forms.
 *
 * A stock Enketo page cannot receive an Android Activity result. For every
 * methodmesh:// browser launch we therefore copy the structured MethodMesh
 * result to the clipboard. If the result contains content:// media, media mode
 * is inferred automatically and those outputs are also published through
 * Android MediaStore so a normal HTML/Enketo attachment question can select
 * them.
 *
 * Callers may still request input_browser_return=clipboard or
 * input_browser_return=clipboard_media explicitly. input_browser_return=none
 * disables the bridge. This remains a transport concern, not capability code.
 */
object BrowserReturnBridge {
    const val MODE_NONE = "none"
    const val MODE_CLIPBOARD = "clipboard"
    const val MODE_CLIPBOARD_MEDIA = "clipboard_media"
    const val PAYLOAD_VERSION = 1

    data class PublishedMedia(
        val sourceFields: List<String>,
        val sourceUri: String,
        val publishedUri: String,
        val displayName: String,
        val mimeType: String,
        val relativePath: String,
        val sha256: String
    )

    data class Outcome(
        val clipboardPayload: String,
        val publishedMedia: List<PublishedMedia>,
        val warnings: List<String>
    )

    fun handleIfRequested(
        context: Context,
        request: ExternalWorkflowRequest,
        fields: Map<String, Any?>,
        browserDeepLink: Boolean
    ): Outcome? {
        if (!browserDeepLink) return null

        val requestedMode = request.settings["browser_return"]
            ?.trim()
            ?.lowercase()
            .orEmpty()
        if (requestedMode == MODE_NONE) return null

        val containsMedia = collectContentUriFields(fields).isNotEmpty()
        val mode = when (requestedMode) {
            MODE_CLIPBOARD -> MODE_CLIPBOARD
            MODE_CLIPBOARD_MEDIA -> MODE_CLIPBOARD_MEDIA
            else -> if (containsMedia) MODE_CLIPBOARD_MEDIA else MODE_CLIPBOARD
        }

        val warnings = mutableListOf<String>()
        val published = if (mode == MODE_CLIPBOARD_MEDIA) {
            runCatching {
                publishContentUris(context.contentResolver, fields, warnings)
            }.onFailure { failure ->
                warnings += "Media publication failed: ${failure.message ?: "unknown MediaStore error"}"
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

        val payload = runCatching {
            buildPayload(request, fields, published, warnings)
        }.getOrElse { failure ->
            warnings += "Could not build full browser payload: ${failure.message ?: "JSON encoding failed"}"
            JSONObject()
                .put("methodmesh_browser_return_v", PAYLOAD_VERSION)
                .put("status", "completed_with_warning")
                .put("created_at", Instant.now().toString())
                .put("warnings", JSONArray(warnings))
                .toString()
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("MethodMesh browser result", payload))

        val mediaMessage = when (published.size) {
            0 -> if (containsMedia && mode == MODE_CLIPBOARD_MEDIA) {
                " Media was returned but could not be published; paste the result for details."
            } else ""
            1 -> " 1 media file saved to ${published.first().relativePath}."
            else -> " ${published.size} media files saved to MethodMesh media folders."
        }
        Toast.makeText(
            context,
            "MethodMesh result copied.$mediaMessage",
            Toast.LENGTH_LONG
        ).show()

        return Outcome(
            clipboardPayload = payload,
            publishedMedia = published,
            warnings = warnings
        )
    }

    private fun publishContentUris(
        resolver: ContentResolver,
        fields: Map<String, Any?>,
        warnings: MutableList<String>
    ): List<PublishedMedia> {
        val uriFields = collectContentUriFields(fields)
        if (uriFields.isEmpty()) return emptyList()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            warnings += "Browser media return currently requires Android 10 or later to publish app-owned output without broad storage permission."
            return emptyList()
        }

        return uriFields.mapNotNull { (source, sourceFields) ->
            runCatching {
                publishOne(resolver, Uri.parse(source), sourceFields)
            }.onFailure { failure ->
                warnings += "Could not publish ${sourceFields.joinToString()}: ${failure.message ?: "media export failed"}"
            }.getOrNull()
        }
    }

    /**
     * Collect exact content:// scalar fields and content:// values embedded in
     * JSON/list strings. The latter matters for capabilities that return a set
     * of page images rather than one image field.
     */
    private fun collectContentUriFields(fields: Map<String, Any?>): LinkedHashMap<String, MutableList<String>> {
        val found = linkedMapOf<String, MutableList<String>>()
        fields.forEach { (field, value) ->
            val text = value?.toString().orEmpty()
            if (text.isBlank()) return@forEach

            val uris = linkedSetOf<String>()
            if (text.startsWith("content://")) uris += text
            CONTENT_URI_REGEX.findAll(text).forEach { match ->
                match.value.trimEnd('"', '\'', ']', '}', ',').takeIf { it.startsWith("content://") }?.let(uris::add)
            }
            uris.forEach { uri -> found.getOrPut(uri) { mutableListOf() }.add(field) }
        }
        return found
    }

    private fun publishOne(
        resolver: ContentResolver,
        source: Uri,
        sourceFields: List<String>
    ): PublishedMedia {
        val mime = resolver.getType(source).orEmpty().ifBlank { "application/octet-stream" }
        val originalName = queryDisplayName(resolver, source)
        val displayName = safeDisplayName(originalName, sourceFields.firstOrNull().orEmpty(), mime)
        val destination = destinationFor(mime)

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, destination.relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val target = resolver.insert(destination.collection, values)
            ?: error("Android MediaStore did not create a destination item.")

        val digest = MessageDigest.getInstance("SHA-256")
        try {
            resolver.openInputStream(source).use { input ->
                requireNotNull(input) { "Could not open MethodMesh output for reading." }
                resolver.openOutputStream(target, "w").use { output ->
                    requireNotNull(output) { "Could not open MediaStore destination for writing." }
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                    output.flush()
                }
            }
            ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }.also { resolver.update(target, it, null, null) }
        } catch (failure: Throwable) {
            resolver.delete(target, null, null)
            throw failure
        }

        return PublishedMedia(
            sourceFields = sourceFields,
            sourceUri = source.toString(),
            publishedUri = target.toString(),
            displayName = displayName,
            mimeType = mime,
            relativePath = destination.relativePath,
            sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        )
    }

    private data class Destination(val collection: Uri, val relativePath: String)

    private fun destinationFor(mime: String): Destination = when {
        mime.startsWith("image/") -> Destination(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${Environment.DIRECTORY_PICTURES}/MethodMesh"
        )
        mime.startsWith("video/") -> Destination(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            "${Environment.DIRECTORY_MOVIES}/MethodMesh"
        )
        mime.startsWith("audio/") -> Destination(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            "${Environment.DIRECTORY_MUSIC}/MethodMesh"
        )
        else -> Destination(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            "${Environment.DIRECTORY_DOWNLOADS}/MethodMesh"
        )
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) cursor.getString(index) else null
        }
    }.getOrNull()

    private fun safeDisplayName(original: String?, field: String, mime: String): String {
        val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
            ?.takeIf { it.isNotBlank() }
            .orEmpty()
        val fallbackBase = field
            .ifBlank { "methodmesh_output" }
            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
            .trim('_')
            .ifBlank { "methodmesh_output" }
        val candidate = original
            ?.substringAfterLast('/')
            ?.replace(Regex("[^A-Za-z0-9._-]+"), "_")
            ?.takeIf { it.isNotBlank() }
            ?: "${fallbackBase}_${System.currentTimeMillis()}${if (extension.isBlank()) "" else ".$extension"}"
        return if (extension.isNotBlank() && !candidate.contains('.')) "$candidate.$extension" else candidate
    }

    private fun buildPayload(
        request: ExternalWorkflowRequest,
        fields: Map<String, Any?>,
        published: List<PublishedMedia>,
        warnings: List<String>
    ): String {
        val mediaBySource = published.associateBy { it.sourceUri }
        val fieldJson = JSONObject()
        fields.forEach { (key, value) ->
            val text = value?.toString()
            val media = text?.let(mediaBySource::get)
            when {
                media != null -> fieldJson.put(key, media.displayName)
                value == null -> fieldJson.put(key, JSONObject.NULL)
                else -> fieldJson.put(key, text)
            }
        }

        val mediaJson = JSONArray()
        published.forEach { item ->
            mediaJson.put(
                JSONObject()
                    .put("source_fields", JSONArray(item.sourceFields))
                    .put("display_name", item.displayName)
                    .put("mime_type", item.mimeType)
                    .put("relative_path", item.relativePath)
                    .put("published_uri", item.publishedUri)
                    .put("sha256", item.sha256)
            )
        }

        return JSONObject()
            .put("methodmesh_browser_return_v", PAYLOAD_VERSION)
            .put("status", "completed")
            .put("created_at", Instant.now().toString())
            .put("methods", JSONArray(request.actions.map { it.canonicalId }))
            .put("request_ref", request.settings["request_ref"].orEmpty())
            .put("fields", fieldJson)
            .put("media", mediaJson)
            .put("warnings", JSONArray(warnings))
            .toString()
    }

    private val CONTENT_URI_REGEX = Regex("content://[^\\s\\\"',}\\]]+")
}
