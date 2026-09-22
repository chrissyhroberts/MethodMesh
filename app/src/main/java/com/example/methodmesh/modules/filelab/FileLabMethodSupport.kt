package com.example.methodmesh.modules.filelab

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine

internal object FileLabMethodSupport {
    fun complete(
        request: ExecutionRequest,
        methodRef: ArchitectureRef,
        methodId: String,
        version: String,
        phenomenon: String,
        values: Map<String, String>,
        success: Boolean,
        error: String? = null
    ): ExecutionResult {
        val status = if (success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val provenance = ProvenanceContext(
            provider = "methodmesh.filelab",
            methodId = methodId,
            methodVersion = version,
            operatorId = request.context["operator_id"]
        )
        val observation = Observation(
            id = ArchitectureId(),
            phenomenon = phenomenon,
            subject = InvocationContext.from(request.context)?.subjectRef(),
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            id = ArchitectureId(),
            action = methodId,
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = status,
            temporalContext = request.temporalContext,
            provenance = provenance,
            diagnostics = if (error.isNullOrBlank()) emptyMap() else mapOf("error" to error)
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = status,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = transformation.diagnostics
        ).withInvocationContext(InvocationContext.from(request.context))
    }
}
