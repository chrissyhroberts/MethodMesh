package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
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
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.Instant
import java.util.Base64

object As100NfcIssuerIdentityMethod : As100Method {
    const val ID = "nfc_issuer_identity"
    const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC issuer identity")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC issuer identity",
        version = VERSION,
        description = "Export the local NFC credential issuer public identity for offline study trust configuration.",
        inputs = emptyList(),
        outputs = NfcIssuerIdentityFields.outputFields,
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "network" to "none"
        )
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = emptyList(),
        requiredContext = emptyList(),
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
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
    ): ExecutionResult {
        val values = NfcIssuerIdentity.currentValues()
        val provenance = ProvenanceContext(
            provider = "methodmesh.nfc",
            methodId = ID,
            methodVersion = VERSION,
            operatorId = request.context["operator_id"]
        )
        val observation = Observation(
            phenomenon = "nfc.issuer.identity",
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = ID,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = TransformationStatus.Succeeded,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = TransformationStatus.Succeeded,
            observations = listOf(observation),
            transformations = listOf(transformation)
        ).withInvocationContext(InvocationContext.from(request.context))
    }
}

internal object NfcIssuerIdentity {
    const val SCHEMA_VERSION = "1"

    fun currentValues(exportedAt: Instant = Instant.now()): LinkedHashMap<String, String> {
        val publicKey = AndroidNfcCredentialSigner.publicKey
        val fingerprint = AndroidNfcCredentialSigner.fingerprintSha256
        val shortId = AndroidNfcCredentialSigner.keyId
        val publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.encoded)
        val identityJson = JSONObject(
            linkedMapOf<String, Any>(
                "schema_version" to SCHEMA_VERSION,
                "issuer_key_id" to shortId,
                "issuer_public_key_fingerprint_sha256" to fingerprint,
                "issuer_public_key_base64" to publicKeyBase64,
                "signature_algorithm" to NfcCredentialSigner.SIGNATURE_ALGORITHM
            )
        ).toString()
        return linkedMapOf(
            NfcIssuerIdentityFields.SCHEMA_VERSION to SCHEMA_VERSION,
            NfcProvisionFields.ISSUER_KEY_ID to shortId,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256 to fingerprint,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_BASE64 to publicKeyBase64,
            NfcProvisionFields.ISSUER_SIGNATURE_ALGORITHM to NfcCredentialSigner.SIGNATURE_ALGORITHM,
            NfcIssuerIdentityFields.EXPORTED_TIME_ISO to exportedAt.toString(),
            NfcIssuerIdentityFields.IDENTITY_JSON to identityJson
        )
    }
}
