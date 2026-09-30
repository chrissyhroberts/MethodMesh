package com.example.methodmesh.modules.webactions

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.onlinedata.ApiDefinition
import com.example.methodmesh.core.onlinedata.ApiDefinitionRepository
import com.example.methodmesh.core.onlinedata.ApiExecutionResult
import com.example.methodmesh.core.onlinedata.ApiGetExecutor
import com.example.methodmesh.core.onlinedata.ApiGetRequest
import com.example.methodmesh.core.onlinedata.HttpUrlConnectionOnlineHttpClient
import com.example.methodmesh.core.onlinedata.ResultTree
import com.example.methodmesh.core.onlinedata.SharedApiResultCache
import com.example.methodmesh.core.onlinedata.toJsonString
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

internal object WebApiFields {
    const val STATUS = "api_status"
    const val VALUE = "api_value"
    const val VALUES_JSON = "api_values_json"
    const val LABEL = "api_label"
    const val DEFINITION_ID = "api_definition_id"
    const val DEFINITION_NAME = "api_definition_name"
    const val RESULT_PATH = "api_result_path"
    const val RESULT_PATHS = "api_result_paths"
    const val PROVIDER = "api_provider"
    const val HTTP_STATUS = "api_http_status"
    const val FROM_CACHE = "api_from_cache"
    const val STALE = "api_stale"
    const val SOURCE_URL = "api_source_url"
    const val RESPONSE_JSON = "api_response_json"
    const val ERROR = "api_error"
    const val RETRIEVED_TIME_ISO = "api_retrieved_time_iso"
    const val DATA_AGE_HOURS = "api_data_age_hours"

    val outputs = listOf(
        STATUS, VALUE, VALUES_JSON, LABEL, DEFINITION_ID, DEFINITION_NAME,
        RESULT_PATH, RESULT_PATHS, PROVIDER, HTTP_STATUS, FROM_CACHE, STALE,
        SOURCE_URL, RESPONSE_JSON, ERROR, RETRIEVED_TIME_ISO, DATA_AGE_HOURS
    )
}

internal object WebApiSupport {
    fun run(definition: ApiDefinition, settings: Map<String, String>): Map<String, String> {
        val normalized = settings.toMutableMap().apply {
            if (get("latitude").isNullOrBlank()) {
                get("gps_latitude")?.takeIf { it.isNotBlank() }?.let { put("latitude", it) }
                    ?: get("current_latitude")?.takeIf { it.isNotBlank() }?.let { put("latitude", it) }
            }
            if (get("longitude").isNullOrBlank()) {
                get("gps_longitude")?.takeIf { it.isNotBlank() }?.let { put("longitude", it) }
                    ?: get("current_longitude")?.takeIf { it.isNotBlank() }?.let { put("longitude", it) }
            }
        }
        val inputs = normalized
            .mapKeys { it.key.removePrefix("input_") }
            .filterKeys { key -> definition.inputs.any { it.id == key } }
        val result = ApiGetExecutor(
            registry = ApiDefinitionRepository,
            httpClient = HttpUrlConnectionOnlineHttpClient(),
            cache = SharedApiResultCache
        ).execute(ApiGetRequest(definitionId = definition.id, inputs = inputs))
        return valuesFrom(definition, result, settings)
    }

    fun resultFor(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?,
        methodId: String,
        methodRef: ArchitectureRef,
        methodVersion: String,
        phenomenon: String
    ): ExecutionResult {
        val ok = values[WebApiFields.STATUS] == "succeeded"
        val entity = Entity(ArchitectureId("web-api:${System.currentTimeMillis()}"), "OnlineApiResult", temporalContext = request.temporalContext)
        val provenance = ProvenanceContext("methodmesh.online_data", methodId, methodVersion)
        val observation = Observation(
            phenomenon = phenomenon,
            subject = ArchitectureRef(entity.id, entity.objectType, methodId),
            values = values + (WebApiFields.RETRIEVED_TIME_ISO to values[WebApiFields.RETRIEVED_TIME_ISO].orEmpty().ifBlank { Instant.now().toString() }),
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = methodId,
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(WebApiFields.ERROR to values[WebApiFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }

    private fun valuesFrom(definition: ApiDefinition, result: ApiExecutionResult, settings: Map<String, String>): Map<String, String> {
        val responseJson = result.data.toJsonString()
        val humanResponse = prettyJson(responseJson)
        val selectedPaths = settings["result_paths"].orEmpty().ifBlank { definition.response.expectedPaths.joinToString("|") }
        val valuesJson = ResultTree.ObjectNode(
            mapOf(
                "response" to ResultTree.string(responseJson),
                "selected_paths" to ResultTree.string(selectedPaths)
            )
        ).toJsonString()
        val retrieved = result.meta.retrievedAt ?: result.meta.requestedAt
        val ageHours = java.time.Duration.between(retrieved, Instant.now()).seconds.coerceAtLeast(0) / 3600.0
        val succeeded = result.status.name == "SUCCESS" || result.status.name == "STALE_CACHE"
        return linkedMapOf(
            WebApiFields.STATUS to if (succeeded) "succeeded" else "failed",
            WebApiFields.VALUE to humanResponse,
            WebApiFields.VALUES_JSON to valuesJson,
            WebApiFields.LABEL to definition.name,
            WebApiFields.DEFINITION_ID to definition.id,
            WebApiFields.DEFINITION_NAME to definition.name,
            WebApiFields.RESULT_PATH to selectedPaths.substringBefore('|'),
            WebApiFields.RESULT_PATHS to selectedPaths,
            WebApiFields.PROVIDER to definition.attribution.providerName,
            WebApiFields.HTTP_STATUS to result.meta.statusCode?.toString().orEmpty(),
            WebApiFields.FROM_CACHE to result.meta.fromCache.toString(),
            WebApiFields.STALE to result.meta.isStale.toString(),
            WebApiFields.SOURCE_URL to result.meta.sourceUrlRedacted,
            WebApiFields.RESPONSE_JSON to responseJson,
            WebApiFields.ERROR to result.error?.message.orEmpty(),
            WebApiFields.RETRIEVED_TIME_ISO to retrieved.toString(),
            WebApiFields.DATA_AGE_HOURS to "%.2f".format(java.util.Locale.US, ageHours)
        )
    }

    private fun prettyJson(raw: String): String = runCatching {
        when {
            raw.trimStart().startsWith("[") -> JSONArray(raw).toString(2)
            raw.trimStart().startsWith("{") -> JSONObject(raw).toString(2)
            else -> raw
        }
    }.getOrDefault(raw)
}
