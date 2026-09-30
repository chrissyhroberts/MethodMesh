package com.example.methodmesh.modules.trustedtimestamp

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState

object As100TrustedTimestampVerificationMethod : As100Method {
    const val ID = "integrity.trusted_timestamp.verify"
    private const val VERSION = "0.1.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Verify trusted RFC 3161 timestamp")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Verify trusted timestamp",
        version = VERSION,
        description = "Verify a portable RFC 3161 proof bundle against the exact source bytes.",
        inputs = emptyList(),
        outputs = TrustedTimestampVerificationFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Integrity",
            "status" to TrustedTimestampContractMetadata.MATURITY,
            "maturity" to TrustedTimestampContractMetadata.MATURITY,
            "connectivity" to TrustedTimestampContractMetadata.CONNECTIVITY,
            "interactive" to "true",
            "core_return" to TrustedTimestampVerificationFields.STATUS,
            "odk_metadata_return" to TrustedTimestampVerificationFields.FULL_JSON
        )
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, mapOf(
            TrustedTimestampVerificationFields.STATUS to "failed",
            TrustedTimestampVerificationFields.ERROR to "Timestamp verification requires the capability screen to supply a proof bundle and source."
        ), InvocationContext.from(request.context))

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val succeeded = values[TrustedTimestampVerificationFields.STATUS]?.startsWith("verified_") == true
        val provenance = ProvenanceContext("methodmesh.integrity", ID, VERSION)
        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val entity = Entity(
            id = ArchitectureId("timestamp-verification:${System.currentTimeMillis()}"),
            entityType = "TrustedTimestampVerification",
            temporalContext = request.temporalContext
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity), observations = listOf(observation), transformations = listOf(transformation),
            diagnostics = if (succeeded) emptyMap() else mapOf(TrustedTimestampVerificationFields.ERROR to values[TrustedTimestampVerificationFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}
