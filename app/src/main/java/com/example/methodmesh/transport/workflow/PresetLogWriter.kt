package com.example.methodmesh.transport.workflow

import android.content.Context
import com.example.methodmesh.core.artifacts.AndroidArtifacts
import com.example.methodmesh.core.artifacts.ArtifactRef
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Shared fallback for specialised capability screens that do not use the standard scaffold. */
object PresetLogWriter {
    suspend fun record(context: Context, request: ExternalWorkflowRequest, result: ExecutionResult) {
        val refId = (request.settings["methodmesh_log_ref"]
            ?: request.settings["input_methodmesh_log_ref"]).orEmpty().trim()
        if (refId.isBlank()) return
        withContext(Dispatchers.IO) {
            val service = AndroidArtifacts.service(context)
            val ref = ArtifactRef(refId)
            val entryId = result.request.id.value
            val alreadyRecorded = service.open(ref).bufferedReader().useLines { lines ->
                lines.any { line -> runCatching { JSONObject(line).optString("entry_id") == entryId }.getOrDefault(false) }
            }
            if (alreadyRecorded) return@withContext
            val fullJson = OutputFormatter.format(
                result = result,
                returnMode = ReturnMode.Json,
                includeProvenance = true,
                payloadMode = OutputFormatter.PayloadMode.FULL
            )
            val record = JSONObject()
                .put("recorded_at", Instant.now().toString())
                .put("entry_id", entryId)
                .put("capability", result.request.action)
                .put("execution_id", entryId)
                .put("status", result.status)
                .put("result_json", fullJson)
                .put("media", JSONArray())
                .put("media_errors", JSONArray())
                .toString() + "\n"
            service.appendPersistent(ref, record.toByteArray())
        }
    }
}
