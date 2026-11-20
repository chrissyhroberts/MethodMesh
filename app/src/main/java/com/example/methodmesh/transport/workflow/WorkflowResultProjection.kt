package com.example.methodmesh.transport.workflow

import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.ReturnMode
import org.json.JSONArray
import org.json.JSONObject

/** Caller projection over this run's committed results, never unrelated session history. */
internal object WorkflowResultProjection {
    fun cancellationFields(
        request: ExternalWorkflowRequest,
        currentIndex: Int,
        completedStepCount: Int,
        message: String
    ): Map<String, String?> {
        val actionId = request.actions.getOrNull(currentIndex)?.canonicalId ?: "methodmesh.workflow"
        val result = As100ExecutionEngine.complete(
            request = As100ExecutionEngine.request(
                action = actionId,
                method = ArchitectureRef(ArchitectureId(actionId), "Method"),
                context = request.invocationContext.asMap(actionId)
            ),
            status = TransformationStatus.Cancelled,
            diagnostics = mapOf("error" to message)
        )
        return linkedMapOf(
            "methodmesh_closeout_status" to "cancelled",
            "methodmesh_closeout_step_count" to completedStepCount.toString(),
            "methodmesh_closeout_has_payload" to "false",
            "methodmesh_status" to TransformationStatus.Cancelled.name,
            "methodmesh_execution_id" to result.request.id.value,
            "methodmesh_full_json" to fullJson(listOf(result)),
            "error" to message
        )
    }

    fun combine(results: List<ExecutionResult>): ExecutionResult {
        require(results.isNotEmpty()) { "No confirmed results to return." }
        val first = results.first()
        // Preserve the exact committed evidence on the ordinary single-call path.
        if (results.size == 1) return first
        return first.copy(
            status = if (results.all { it.status == TransformationStatus.Succeeded }) {
                TransformationStatus.Succeeded
            } else {
                TransformationStatus.Failed
            },
            entities = results.flatMap { it.entities },
            attributes = results.flatMap { it.attributes },
            observations = results.flatMap { it.observations },
            relationships = results.flatMap { it.relationships },
            classifications = results.flatMap { it.classifications },
            transformations = results.flatMap { it.transformations },
            states = results.flatMap { it.states },
            validation = results.flatMap { it.validation },
            diagnostics = results.flatMap { it.diagnostics.entries }
                .associate { (key, value) -> key to value }
                .plus("workflow_step_count" to results.size.toString()),
            // A workflow spans its first request to its last committed completion.
            // Never replace historical completion evidence with closeout-time clock state.
            timeAssurance = results.last().timeAssurance,
            softwareProvenance = results.flatMap { it.softwareProvenance }.distinct()
        )
    }

    fun fullJson(results: List<ExecutionResult>): String {
        fun json(result: ExecutionResult) = OutputFormatter.format(
            result = result,
            returnMode = ReturnMode.Json,
            includeProvenance = true,
            payloadMode = OutputFormatter.PayloadMode.FULL
        )
        val combined = json(combine(results))
        if (results.size == 1) return combined
        // Keep individual status, timing and software identity auditable.
        return JSONObject(combined)
            .put("workflow_steps", JSONArray(results.map { JSONObject(json(it)) }))
            .toString()
    }
}
