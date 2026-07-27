package com.example.researchos.modules.nfc

import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.Entity
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.QualityAssessment
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.ValidationFinding
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.nfc.AndroidNfcDeviceService
import com.example.researchos.platform.nfc.NfcTagSignal
import com.example.researchos.settings.SettingsState
import java.time.Instant

/**
 * Provisions one NFC credential by writing an NDEF record and verifying the
 * bytes read back from the same physical tag.
 */
object As100NfcProvisionMethod : As100Method {
    const val ID = "nfc.provision"
    const val VERSION = "1.0.0"

    override val id: String = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "NFC credential provision")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Method,
        name = "NFC credential provision",
        version = VERSION,
        description = "Write an NFC credential, read it back, and return registry evidence compatible with signed attestation.",
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
        acceptedSignals = listOf(AndroidNfcDeviceService.SIGNAL_TYPE_TAG_DISCOVERED),
        requiredContext = listOf(NfcProvisionFields.CREDENTIAL_ID, "value"),
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
        diagnostics = mapOf(
            "reason" to "NFC provisioning requires a live NfcTagSignal from the Android NFC Device Service."
        )
    )

    fun provision(
        tagSignal: NfcTagSignal,
        credentialId: String,
        writeRequest: NfcWriteRequest,
        invocationContext: InvocationContext? = null
    ): ExecutionResult {
        val normalizedCredentialId = credentialId.trim()
        require(normalizedCredentialId.isNotBlank()) { "credential_id is required" }

        val write = NfcTagRepository.writeTag(tagSignal.androidTag, writeRequest)
        val evidence = runCatching { NfcCredentialEvidence.fields(write.tagValues) }.getOrDefault(emptyMap())
        val evidenceReady = evidence[NfcCredentialEvidence.HASH_FIELD].orEmpty().isNotBlank()
        val success = write.success && write.verified && evidenceReady
        val provisionedTime = Instant.ofEpochMilli(System.currentTimeMillis()).toString()
        val message = when {
            success -> "Credential written, read back, and verified."
            !write.success -> write.message
            !write.verified -> "Credential write was not confirmed by read-back verification."
            else -> "Credential was written but canonical NFC evidence could not be created."
        }
        val request = request(
            action = ID,
            context = invocationContext?.asMap(ID).orEmpty() + mapOf(
                NfcProvisionFields.CREDENTIAL_ID to normalizedCredentialId,
                "record_type" to writeRequest.recordType,
                "value" to writeRequest.value,
                "mime_type" to writeRequest.mimeType,
                "language_code" to writeRequest.languageCode,
                "overwrite_policy" to writeRequest.overwritePolicy.wireValue,
                "expected_current_hash" to writeRequest.expectedCurrentHash.orEmpty()
            ),
            signals = listOf(tagSignal.signal)
        )
        val provenance = ProvenanceContext(
            provider = tagSignal.signal.provenance.provider,
            methodId = ID,
            methodVersion = VERSION,
            operatorId = invocationContext?.operatorId
        )
        val uid = write.tagValues[NfcEvidenceFields.TAG_UID_HEX].orEmpty()
        val tagEntity = Entity(
            id = ArchitectureId("nfc-tag:$uid"),
            entityType = "NfcTag",
            attributes = mapOf(NfcEvidenceFields.TAG_UID_HEX to uid),
            temporalContext = tagSignal.signal.temporalContext
        )
        val values = linkedMapOf(
            NfcProvisionFields.CREDENTIAL_ID to normalizedCredentialId,
            NfcProvisionFields.PROVISION_SUCCESS to success.toString(),
            NfcProvisionFields.PROVISION_MESSAGE to message,
            NfcProvisionFields.PROVISIONED_TIME_ISO to provisionedTime,
            NfcWriteFields.WRITE_RECORD_TYPE to writeRequest.recordType,
            NfcWriteFields.WRITE_SIZE_BYTES to write.sizeBytes.toString(),
            NfcWriteFields.OVERWRITE_POLICY to write.overwritePolicy,
            NfcWriteFields.PREVIOUS_MESSAGE_HASH to write.previousMessageHash,
            NfcWriteFields.WRITTEN_MESSAGE_HASH to write.writtenMessageHash,
            NfcWriteFields.WRITE_VERIFIED to write.verified.toString()
        ) + write.tagValues + evidence
        val observation = Observation(
            phenomenon = "nfc.credential.provisioned",
            subject = ArchitectureRef(tagEntity.id, tagEntity.objectType, uid),
            values = values,
            sourceSignal = ArchitectureRef(
                tagSignal.signal.id,
                tagSignal.signal.objectType,
                tagSignal.signal.signalType
            ),
            temporalContext = tagSignal.signal.temporalContext,
            provenance = provenance
        )
        val status = if (success) TransformationStatus.Succeeded else TransformationStatus.Failed
        val diagnostics = mapOf(
            NfcProvisionFields.PROVISION_SUCCESS to success.toString(),
            NfcProvisionFields.PROVISION_MESSAGE to message
        )
        val transformation = Transformation(
            action = "intervene.nfc.provision",
            method = ref,
            inputs = listOf(
                ArchitectureRef(tagSignal.signal.id, tagSignal.signal.objectType, tagSignal.signal.signalType)
            ),
            outputs = listOf(
                ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)
            ),
            status = status,
            diagnostics = diagnostics,
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
                        field = NfcProvisionFields.PROVISION_SUCCESS,
                        code = if (success) "nfc_credential_provisioned" else "nfc_credential_provision_failed"
                    )
                ),
                quality = QualityAssessment(usable = success, metrics = diagnostics),
                diagnostics = diagnostics
            ).withInvocationContext(invocationContext)
        )
    }
}
