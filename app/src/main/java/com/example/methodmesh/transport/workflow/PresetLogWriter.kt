package com.example.methodmesh.transport.workflow

import android.content.Context
import android.net.Uri
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import com.example.methodmesh.core.methodmesh.ExecutionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** Single logging path for all confirmed capability screens, including specialised ones. */
internal object PresetLogWriter {
    suspend fun record(context: Context, request: ExternalWorkflowRequest, result: ExecutionResult): PresetLogRecorder.Outcome {
        val ref = (request.settings["methodmesh_log_ref"] ?: request.settings["input_methodmesh_log_ref"]).orEmpty().trim()
        val summaryRef = (request.settings["methodmesh_log_summary_ref"] ?: request.settings["input_methodmesh_log_summary_ref"]).orEmpty().trim()
        if (ref.isBlank()) return PresetLogRecorder.Outcome()
        return withContext(Dispatchers.IO) {
            val service = AndroidArtifacts.service(context)
            PresetLogRecorder(object : PresetLogRecorder.Storage {
                override val lock: Any = service
                override fun read(ref: String) = service.open(ArtifactRef(ref)).bufferedReader().use { it.readText() }
                override fun append(ref: String, text: String) = service.appendPersistent(ArtifactRef(ref), text.toByteArray(Charsets.UTF_8))
                override fun copyMedia(source: String, name: String, collectionId: String, entryId: String, mediaId: String): JSONObject {
                    val uri = Uri.parse(source)
                    val mime = if (source.startsWith("content://")) context.contentResolver.getType(uri) else null
                    val input = if (source.startsWith("content://")) context.contentResolver.openInputStream(uri)
                        else File(source.removePrefix("file://")).inputStream()
                    val mediaRef = requireNotNull(input) { "Media URI could not be opened" }.use {
                        service.createPersistent("${collectionId.take(12)}/media/$entryId-$name", mime ?: "application/octet-stream", it,
                            collectionId = collectionId, entryId = entryId, mediaId = mediaId)
                    }
                    return JSONObject().put("media_id", mediaId).put("artifact_ref", mediaRef.id)
                        .put("filename", name).put("mime_type", mime ?: "application/octet-stream")
                }
            }).record(ref, summaryRef, result)
        }
    }
}
