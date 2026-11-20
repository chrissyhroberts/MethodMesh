package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.ReturnMode
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

/** One confirmed execution, one JSONL entry. Storage failures never change its evidence. */
internal class PresetLogRecorder(private val storage: Storage) {
    interface Storage {
        val lock: Any
        fun read(ref: String): String
        fun append(ref: String, text: String)
        fun copyMedia(source: String, name: String, collectionId: String, entryId: String, mediaId: String): JSONObject
    }

    data class Outcome(val warning: String? = null)

    fun record(ref: String, summaryRef: String, result: ExecutionResult): Outcome {
        if (ref.isBlank()) return Outcome()
        return synchronized(storage.lock) {
            try {
                require(summaryRef.isBlank() || summaryRef != ref) { "JSONL and summary must be different artifacts" }
                val entryId = result.request.id.value
                val contents = storage.read(ref)
                check(contents.isEmpty() || contents.endsWith("\n")) { "Incomplete log tail" }
                val entries = contents.lineSequence().filter { it.isNotBlank() }.map(::JSONObject).toList()
                var entry = entries.firstOrNull { it.optString("entry_id") == entryId }
                if (entry == null) {
                    val fields = OutputFormatter.fields(result, includeProvenance = true)
                    val compact = OutputFormatter.projectFields(fields, OutputFormatter.PayloadMode.CORE, result.status)
                    val summary = compact.entries.firstOrNull { (key, value) ->
                        !key.startsWith("methodmesh_") && !key.contains("status", true) &&
                            !key.endsWith("_count") && !key.endsWith("_time_iso") &&
                            !key.endsWith("_json") && !key.endsWith("_uri") &&
                            value?.toString().orEmpty().isNotBlank()
                    }
                    val media = JSONArray()
                    val mediaErrors = JSONArray()
                    ResultShare.shareableMediaAttachmentValues(fields).forEach { attachment ->
                        try {
                            media.put(storage.copyMedia(attachment.uri, attachment.name, ref, entryId, UUID.randomUUID().toString()))
                        } catch (failure: Exception) {
                            mediaErrors.put(JSONObject().put("uri", attachment.uri).put("error", "Media copy failed"))
                        }
                    }
                    entry = JSONObject()
                        .put("recorded_at", Instant.now().toString())
                        .put("entry_id", entryId).put("execution_id", entryId)
                        .put("capability", result.request.action).put("status", result.status)
                        .put("summary", JSONObject().put("field", summary?.key ?: "methodmesh_status").put("value", summary?.value?.toString() ?: result.status.name))
                        .put("fields", JSONObject(fields))
                        .put("result_json", OutputFormatter.format(result, ReturnMode.Json, true, payloadMode = OutputFormatter.PayloadMode.FULL))
                        .put("media", media).put("media_errors", mediaErrors)
                    // appendPersistent may throw AFTER writing the JSONL bytes (e.g. metadata
                    // failure). Never delete media here: the committed entry may reference it.
                    storage.append(ref, entry.toString() + "\n")
                } else storage.append(ref, "") // Refresh hash/metadata after an earlier post-write failure.
                // Repair a previously failed summary without copying media or appending a
                // second execution entry. Use the original entry's timestamp and summary.
                val summary = entry.optJSONObject("summary")
                if (summaryRef.isNotBlank() && summary?.optString("field").orEmpty().isNotBlank()) {
                    fun singleLine(value: String) = value.replace("\r", "\\r").replace("\n", "\\n")
                    val line = "${entry.getString("recorded_at")} — ${singleLine(summary!!.getString("field"))}: ${singleLine(summary.optString("value"))}"
                    val readable = storage.read(summaryRef)
                    check(readable.isEmpty() || readable.endsWith("\n")) { "Incomplete summary tail" }
                    if (readable.lineSequence().none { it == line }) storage.append(summaryRef, "$line\n")
                    else storage.append(summaryRef, "")
                }
                if (entry.optString("result_json").isBlank() ||
                    (entry.optJSONArray("media")?.length() ?: 0) < ResultShare.shareableMediaAttachmentValues(OutputFormatter.fields(result, true)).size &&
                    (entry.optJSONArray("media_errors")?.length() ?: 0) == 0) {
                    Outcome("Persistent log entry is incomplete. Captured result returned unchanged.")
                } else if ((entry.optJSONArray("media_errors")?.length() ?: 0) > 0) {
                    Outcome("Persistent log saved with missing media. Captured result returned unchanged.")
                } else Outcome()
            } catch (failure: Exception) {
                Outcome("Persistent log could not be saved completely. Captured result returned unchanged.")
            }
        }
    }
}
