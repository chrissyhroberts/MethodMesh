package com.example.methodmesh.modules.nfc

import com.example.methodmesh.core.ResearchRuntime
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
import com.example.methodmesh.core.methodmesh.QualityAssessment
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.ValidationFinding
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.platform.nfc.AndroidNfcDeviceService
import com.example.methodmesh.platform.nfc.NfcTagSignal
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

object As100NfcCredentialProvisioningMethod : As100Method {
    const val ID = "nfc_credential_provisioning"
    const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC credential provisioning")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC credential provisioning",
        version = VERSION,
        description = "Create a signed, PIN-encrypted portable credential, write it to NFC, and verify the read-back.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = NfcProvisionFields.outputFields,
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "device_service" to AndroidNfcDeviceService.SERVICE_ID
        )
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = descriptor.inputs,
        requiredContext = listOf(NfcProvisionFields.CREDENTIAL_SUBJECT_ID),
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
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "NFC credential provisioning requires live NFC interaction.")
    )

    internal fun provision(
        tagSignal: NfcTagSignal,
        credential: NfcPortableCredentialFormat.ProvisionedCredential,
        writeRequest: NfcWriteRequest,
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        val write = NfcTagRepository.writeTag(tagSignal.androidTag, writeRequest)
        val success = write.success && write.verified
        val now = Instant.now().toString()
        val values = linkedMapOf(
            NfcProvisionFields.CREDENTIAL_ID to credential.credentialId,
            NfcProvisionFields.CREDENTIAL_SUBJECT_ID to credential.credentialSubjectId,
            NfcProvisionFields.PIN_LENGTH to credential.pinLength.toString(),
            NfcProvisionFields.CREDENTIAL_FORMAT_VERSION to NfcPortableCredentialFormat.VERSION,
            NfcProvisionFields.KEY_DERIVATION to NfcPortableCredentialFormat.KEY_DERIVATION,
            NfcProvisionFields.CREDENTIAL_ISSUED_TIME_ISO to credential.issuedAtIso,
            NfcProvisionFields.CREDENTIAL_ENVELOPE_HASH to credential.envelopeHash,
            NfcProvisionFields.CREDENTIAL_SECRET_HASH to credential.credentialSecretHash,
            NfcProvisionFields.ISSUER_KEY_ID to credential.issuerKeyId,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_BASE64 to credential.issuerPublicKeyBase64,
            NfcProvisionFields.ISSUER_SIGNATURE_ALGORITHM to NfcCredentialSigner.SIGNATURE_ALGORITHM,
            NfcProvisionFields.PROVISION_SUCCESS to success.toString(),
            NfcProvisionFields.PROVISION_MESSAGE to if (success) {
                "Credential written and verified."
            } else write.message,
            NfcProvisionFields.PROVISIONED_TIME_ISO to now,
            NfcWriteFields.WRITE_RECORD_TYPE to writeRequest.recordType,
            NfcWriteFields.WRITE_SIZE_BYTES to write.sizeBytes.toString(),
            NfcWriteFields.OVERWRITE_POLICY to write.overwritePolicy,
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to write.previousMessageHash,
            NfcWriteFields.WRITTEN_MESSAGE_HASH to write.writtenMessageHash,
            NfcWriteFields.WRITE_VERIFIED to write.verified.toString(),
            NfcEvidenceFields.TAG_UID_HEX to write.tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
            NfcCredentialEvidence.HASH_FIELD to credential.envelopeHash
        )
        return credentialExecutionResult(
            method = this,
            methodVersion = VERSION,
            phenomenon = "nfc.credential.provisioned",
            tagSignal = tagSignal,
            values = values,
            success = success,
            message = values.getValue(NfcProvisionFields.PROVISION_MESSAGE),
            invocationContext = invocationContext
        )
    }

    internal fun confirmReadBack(
        tagSignal: NfcTagSignal,
        credential: NfcPortableCredentialFormat.ProvisionedCredential,
        overwritePolicy: NfcOverwritePolicy,
        previousValues: Map<String, String> = emptyMap(),
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        val tagValues = NfcTagRepository.readTag(tagSignal.androidTag)
        val envelope = NfcPortableCredentialFormat.extractEnvelope(
            listOf(
                tagValues[NfcEvidenceFields.NDEF_TEXT].orEmpty(),
                tagValues[NfcEvidenceFields.NDEF_URI].orEmpty(),
                tagValues[NfcEvidenceFields.NDEF_PAYLOAD_UTF8_ALL].orEmpty(),
                tagValues[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8].orEmpty()
            )
        )
        val verified = envelope == credential.envelope &&
            envelope?.let(com.example.methodmesh.core.crypto.Digests::sha256Hex) == credential.envelopeHash
        val message = if (verified) {
            "Credential written and verified on the confirmation tap."
        } else {
            "The card does not contain the credential that was just prepared."
        }
        val values = linkedMapOf(
            NfcProvisionFields.CREDENTIAL_ID to credential.credentialId,
            NfcProvisionFields.CREDENTIAL_SUBJECT_ID to credential.credentialSubjectId,
            NfcProvisionFields.PIN_LENGTH to credential.pinLength.toString(),
            NfcProvisionFields.CREDENTIAL_FORMAT_VERSION to NfcPortableCredentialFormat.VERSION,
            NfcProvisionFields.KEY_DERIVATION to NfcPortableCredentialFormat.KEY_DERIVATION,
            NfcProvisionFields.CREDENTIAL_ISSUED_TIME_ISO to credential.issuedAtIso,
            NfcProvisionFields.CREDENTIAL_ENVELOPE_HASH to credential.envelopeHash,
            NfcProvisionFields.CREDENTIAL_SECRET_HASH to credential.credentialSecretHash,
            NfcProvisionFields.ISSUER_KEY_ID to credential.issuerKeyId,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_BASE64 to credential.issuerPublicKeyBase64,
            NfcProvisionFields.ISSUER_SIGNATURE_ALGORITHM to NfcCredentialSigner.SIGNATURE_ALGORITHM,
            NfcProvisionFields.PROVISION_SUCCESS to verified.toString(),
            NfcProvisionFields.PROVISION_MESSAGE to message,
            NfcProvisionFields.PROVISIONED_TIME_ISO to Instant.now().toString(),
            NfcWriteFields.WRITE_RECORD_TYPE to "external",
            NfcWriteFields.WRITE_SIZE_BYTES to tagValues[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES].orEmpty(),
            NfcWriteFields.OVERWRITE_POLICY to overwritePolicy.wireValue,
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to previousValues[NfcWriteFields.PREVIOUS_MESSAGE_HASH].orEmpty(),
            NfcWriteFields.WRITTEN_MESSAGE_HASH to tagValues[NfcEvidenceFields.NDEF_MESSAGE_SHA256].orEmpty(),
            NfcWriteFields.WRITE_VERIFIED to verified.toString(),
            NfcEvidenceFields.TAG_UID_HEX to tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
            NfcCredentialEvidence.HASH_FIELD to credential.envelopeHash
        )
        return credentialExecutionResult(
            method = this,
            methodVersion = VERSION,
            phenomenon = "nfc.credential.provisioned",
            tagSignal = tagSignal,
            values = values,
            success = verified,
            message = message,
            invocationContext = invocationContext
        )
    }
}

object As100NfcCredentialVerificationMethod : As100Method {
    const val ID = "nfc_credential_verification"
    const val VERSION = "1.0.0"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC credential verification")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC credential verification",
        version = VERSION,
        description = "Verify the issuer signature and a user-entered PIN for a portable NFC credential.",
        inputs = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        outputs = NfcCredentialVerificationFields.outputFields,
        parameters = mapOf(
            "category" to "NFC",
            "status" to "Experimental",
            "device_service" to AndroidNfcDeviceService.SERVICE_ID
        )
    )
    override val contract = MethodContract(
        method = ref,
        acceptedSignals = descriptor.inputs,
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
    ): ExecutionResult = As100ExecutionEngine.complete(
        request,
        TransformationStatus.Unsupported,
        diagnostics = mapOf("reason" to "NFC credential verification requires live NFC interaction.")
    )

    internal fun verified(
        tagSignal: NfcTagSignal,
        capturedTagValues: Map<String, String>,
        credential: NfcPortableCredentialFormat.VerifiedCredential,
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        require(credential.verified) { "Only a verified credential can produce a successful verification result." }
        val trustStatus = when (credential.issuerTrusted) {
            true -> "trusted"
            false -> "untrusted"
            null -> "not_checked"
        }
        val values = linkedMapOf(
            NfcCredentialVerificationFields.CREDENTIAL_VERIFIED to "true",
            NfcCredentialVerificationFields.VERIFICATION_MESSAGE to credential.message,
            NfcProvisionFields.CREDENTIAL_ID to credential.credentialId,
            NfcProvisionFields.CREDENTIAL_SUBJECT_ID to credential.credentialSubjectId,
            NfcProvisionFields.PIN_LENGTH to credential.pinLength.toString(),
            NfcProvisionFields.CREDENTIAL_FORMAT_VERSION to NfcPortableCredentialFormat.VERSION,
            NfcProvisionFields.KEY_DERIVATION to NfcPortableCredentialFormat.KEY_DERIVATION,
            NfcProvisionFields.CREDENTIAL_ISSUED_TIME_ISO to credential.issuedAtIso,
            NfcProvisionFields.CREDENTIAL_ENVELOPE_HASH to credential.envelopeHash,
            NfcProvisionFields.CREDENTIAL_SECRET_HASH to credential.credentialSecretHash,
            NfcProvisionFields.ISSUER_KEY_ID to credential.issuerKeyId,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_BASE64 to credential.issuerPublicKeyBase64,
            NfcProvisionFields.ISSUER_SIGNATURE_ALGORITHM to NfcCredentialSigner.SIGNATURE_ALGORITHM,
            NfcCredentialVerificationFields.PIN_VERIFIED to "true",
            NfcCredentialVerificationFields.ISSUER_SIGNATURE_VALID to credential.issuerSignatureValid.toString(),
            NfcCredentialVerificationFields.ISSUER_TRUST_STATUS to trustStatus,
            NfcCredentialVerificationFields.VERIFIED_TIME_ISO to Instant.now().toString(),
            NfcEvidenceFields.TAG_UID_HEX to capturedTagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty(),
            NfcCredentialEvidence.HASH_FIELD to credential.envelopeHash
        )
        return credentialExecutionResult(
            method = this,
            methodVersion = VERSION,
            phenomenon = "nfc.credential.verified",
            tagSignal = tagSignal,
            values = values,
            success = true,
            message = credential.message,
            invocationContext = invocationContext
        )
    }
}

private fun credentialExecutionResult(
    method: As100Method,
    methodVersion: String,
    phenomenon: String,
    tagSignal: NfcTagSignal,
    values: Map<String, String>,
    success: Boolean,
    message: String,
    invocationContext: InvocationContext?
): ExecutionResult {
    val uid = values[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
    val request = method.request(
        action = method.id,
        context = invocationContext?.asMap(method.id).orEmpty() + values,
        signals = listOf(tagSignal.signal),
        inputs = emptyList()
    )
    val provenance = ProvenanceContext(
        provider = tagSignal.signal.provenance.provider,
        methodId = method.id,
        methodVersion = methodVersion,
        operatorId = invocationContext?.operatorId
    )
    val tagEntity = Entity(
        id = ArchitectureId("nfc-tag:$uid"),
        entityType = "NfcTag",
        attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid),
        temporalContext = tagSignal.signal.temporalContext
    )
    val observation = Observation(
        phenomenon = phenomenon,
        subject = ArchitectureRef(tagEntity.id, tagEntity.objectType, uid),
        values = values,
        sourceSignal = ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType),
        temporalContext = tagSignal.signal.temporalContext,
        provenance = provenance
    )
    val status = if (success) TransformationStatus.Succeeded else TransformationStatus.Failed
    val transformation = Transformation(
        action = method.id,
        method = method.ref,
        inputs = listOf(ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)),
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = status,
        diagnostics = mapOf("message" to message),
        temporalContext = observation.temporalContext,
        provenance = provenance
    )
    return ResearchRuntime.session.record(
        As100ExecutionEngine.complete(
            request = request,
            status = status,
            entities = listOf(tagEntity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            validation = listOf(
                ValidationFinding(
                    passed = success,
                    message = message,
                    field = values.keys.firstOrNull(),
                    code = if (success) "nfc_credential_valid" else "nfc_credential_invalid"
                )
            ),
            quality = QualityAssessment(usable = success, metrics = mapOf("message" to message)),
            diagnostics = mapOf("message" to message)
        ).withInvocationContext(invocationContext)
    )
}
