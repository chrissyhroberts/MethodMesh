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

object TrustedTimestampFields {
    const val STATUS = "trusted_timestamp_status"
    const val ORIGINAL_NAME = "trusted_timestamp_original_name"
    const val SIZE_BYTES = "trusted_timestamp_size_bytes"
    const val SHA256 = "trusted_timestamp_sha256"
    const val TSA = "trusted_timestamp_authority"
    const val TIME_ISO = "trusted_timestamp_time_iso"
    const val SERIAL = "trusted_timestamp_serial"
    const val POLICY_OID = "trusted_timestamp_policy_oid"
    const val TOKEN_SHA256 = "trusted_timestamp_token_sha256"
    const val TRUST_STATUS = "trusted_timestamp_trust_status"

    /**
     * Conditional ODK roundtrip returns. These are populated only when MethodMesh
     * acquired the source on behalf of an external caller.
     */
    const val SOURCE_URI = "trusted_timestamp_source_uri"
    const val SOURCE_TEXT = "trusted_timestamp_source_text"

    const val PROOF_FILENAME = "trusted_timestamp_proof_filename"

    /**
     * Historical canonical name retained for compatibility. For ODK this value
     * is transported as a real attachment using ClipData/read grants; the user
     * should not be left with the raw content URI.
     */
    const val PROOF_URI = "trusted_timestamp_proof_uri"

    /** Optional capability-owned metadata JSON for native/runtime use. */
    const val FULL_JSON = "trusted_timestamp_full_json"
    const val ERROR = "trusted_timestamp_error"

    val outputs = listOf(
        STATUS,
        ORIGINAL_NAME,
        SIZE_BYTES,
        SHA256,
        TSA,
        TIME_ISO,
        SERIAL,
        POLICY_OID,
        TOKEN_SHA256,
        TRUST_STATUS,
        SOURCE_URI,
        SOURCE_TEXT,
        PROOF_FILENAME,
        PROOF_URI,
        FULL_JSON,
        ERROR
    )
}

object As100TrustedTimestampMethod : As100Method {
    const val ID = "integrity.trusted_timestamp"
    private const val VERSION = "0.2.0"

    override val id = ID
    override val ref = ArchitectureRef(
        ArchitectureId(ID),
        "Method",
        "Trusted RFC 3161 timestamp"
    )

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Trusted timestamp",
        version = VERSION,
        description = "Create a portable RFC 3161 proof that exact bytes existed no later than a trusted time.",
        inputs = listOf(
            TrustedTimestampContractMetadata.RUNTIME_INPUT_TEXT,
            TrustedTimestampContractMetadata.RUNTIME_INPUT_FILE,
            TrustedTimestampContractMetadata.RUNTIME_TSA_URL,
            TrustedTimestampContractMetadata.RUNTIME_TIMEOUT_MS
        ),
        outputs = TrustedTimestampFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Integrity",
            "status" to TrustedTimestampContractMetadata.MATURITY,
            "maturity" to TrustedTimestampContractMetadata.MATURITY,
            "connectivity" to TrustedTimestampContractMetadata.CONNECTIVITY,
            "interactive" to "true",
            "core_return" to TrustedTimestampFields.PROOF_URI,
            "conditional_external_return" to "${TrustedTimestampFields.SOURCE_URI}, ${TrustedTimestampFields.SOURCE_TEXT}",
            "runtime_projection" to "source first, proof ZIP second, metadata optional",
            "odk_metadata_return" to "methodmesh_full_json"
        )
    )

    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult =
        result(
            request,
            mapOf(
                TrustedTimestampFields.STATUS to "failed",
                TrustedTimestampFields.ERROR to
                    "Trusted timestamp creation requires the capability screen to supply text or a file."
            ),
            InvocationContext.from(request.context)
        )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val succeeded = values[TrustedTimestampFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.integrity", ID, VERSION)

        val observation = Observation(
            phenomenon = ID,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        val entity = Entity(
            ArchitectureId("timestamp-proof:${System.currentTimeMillis()}"),
            "TrustedTimestampProof",
            temporalContext = request.temporalContext
        )

        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(
                ArchitectureRef(
                    observation.id,
                    observation.objectType,
                    observation.phenomenon
                )
            ),
            status = if (succeeded) {
                TransformationStatus.Succeeded
            } else {
                TransformationStatus.Failed
            },
            temporalContext = request.temporalContext,
            provenance = provenance
        )

        return As100ExecutionEngine.complete(
            request,
            if (succeeded) {
                TransformationStatus.Succeeded
            } else {
                TransformationStatus.Failed
            },
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) {
                emptyMap()
            } else {
                mapOf(
                    TrustedTimestampFields.ERROR to
                        values[TrustedTimestampFields.ERROR].orEmpty()
                )
            }
        ).withInvocationContext(invocation)
    }
}
