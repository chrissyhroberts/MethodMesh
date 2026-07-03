package com.example.researchos.modules.adminfingerprint

import com.example.researchos.core.MethodOutput
import com.example.researchos.core.researchos.ArchitectureId
import com.example.researchos.core.researchos.ArchitectureRef
import com.example.researchos.core.researchos.ExecutionRequest
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.MethodContract
import com.example.researchos.core.researchos.MethodDescriptor
import com.example.researchos.core.researchos.MethodObjectType
import com.example.researchos.core.researchos.Observation
import com.example.researchos.core.researchos.ProvenanceContext
import com.example.researchos.core.researchos.Signal
import com.example.researchos.core.researchos.Transformation
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100ExecutionEngine
import com.example.researchos.core.researchos.runtime.As100Method
import com.example.researchos.platform.biometric.AndroidBiometricDeviceService
import com.example.researchos.platform.biometric.BiometricAuthenticationSignal
import com.example.researchos.settings.SettingsState
import java.time.Instant

/**
 * Native AS1.00 method for local participant/operator verification using
 * Android biometric or device-credential authentication.
 *
 * Android BiometricPrompt remains in the device-service/presentation boundary.
 * This method owns the research operation: authentication-result signal ->
 * attestation observation, transformation record and transport output fields.
 */
object As100VerifyFingerprintMethod : As100Method {
    const val ID = "admin_fingerprint_confirmation"
    const val VERSION = "1.0.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Verify Fingerprint / Device Credential"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Verify Fingerprint / Device Credential",
        version = VERSION,
        description = "Interpret an Android biometric/device-credential authentication-result signal as an attestation observation.",
        inputs = listOf(AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT),
        outputs = listOf(
            "confirmed",
            "verification_status",
            "auth_method",
            "timestamp_ms",
            "timestamp_iso",
            "reason",
            "message",
            "biometric_device_service",
            "biometric_signal_type",
            "biometric_execution_id",
            "biometric_provenance_json"
        ),
        parameters = mapOf(
            "category" to "Attestation",
            "status" to "Experimental",
            "device_service" to AndroidBiometricDeviceService.SERVICE_ID,
            "intent_alias" to "verify_fingerprint"
        )
    )

    override val contract: MethodContract = MethodContract(
        method = ref,
        acceptedSignals = listOf(AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT),
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
        val signal = request.signals.firstOrNull()
        val output: Map<String, String> = (if (settingsState != null) {
            outputValues(settingsState)
        } else if (signal != null) {
            outputValues(
                signal = signal,
                reason = request.context["confirmation_reason"].orEmpty().ifBlank { "verify_fingerprint" },
                executionId = request.id.value
            )
        } else {
            emptyMap()
        }).mapValues { it.value.toString() }

        val provenance = ProvenanceContext(
            provider = signal?.provenance?.provider ?: AndroidBiometricDeviceService.SERVICE_ID,
            methodId = ID,
            methodVersion = VERSION
        )

        val observation = Observation(
            phenomenon = "attestation.biometric_verification",
            values = output,
            sourceSignal = signal?.let { ArchitectureRef(it.id, it.objectType, it.signalType) },
            temporalContext = signal?.temporalContext ?: request.temporalContext,
            provenance = provenance
        )

        val transformation = Transformation(
            action = "interpret.biometric.authentication_result",
            method = ref,
            inputs = signal?.let { listOf(ArchitectureRef(it.id, it.objectType, it.signalType)) } ?: emptyList(),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (output["confirmed"] == "true") TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf("auth_method" to output["auth_method"].orEmpty())
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = transformation.status,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf(
                "method" to ID,
                "verified" to output["confirmed"].orEmpty(),
                "auth_method" to output["auth_method"].orEmpty()
            )
        )
    }

    fun buildOutput(settingsState: SettingsState): MethodOutput = MethodOutput(
        fields = outputValues(settingsState)
    )

    fun buildObservation(settingsState: SettingsState): com.example.researchos.core.Observation =
        AdminFingerprintObservationMapper.fromOutput(buildOutput(settingsState))

    fun recordAuthenticationResult(
        settingsState: SettingsState,
        authenticationSignal: BiometricAuthenticationSignal,
        reason: String
    ) {
        val signal = authenticationSignal.signal
        val timestampMs = signal.payload["timestamp_ms"]?.toLongOrNull() ?: System.currentTimeMillis()
        settingsState.setBoolean("confirmed", authenticationSignal.verified)
        settingsState.setString(
            "verification_status",
            if (authenticationSignal.verified) "verified" else "not_verified"
        )
        settingsState.setInt("timestamp_ms", timestampMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        settingsState.setString("timestamp_iso", Instant.ofEpochMilli(timestampMs).toString())
        settingsState.setString("auth_method", authenticationSignal.authMethod.ifBlank { "none" })
        settingsState.setString("reason", reason)
        settingsState.setString("message", authenticationSignal.message)
        settingsState.setString("biometric_device_service", signal.sourceService)
        settingsState.setString("biometric_signal_type", signal.signalType)
        settingsState.setString("biometric_execution_id", signal.id.value)
        settingsState.setString(
            "biometric_provenance_json",
            provenanceJson(
                provider = signal.provenance.provider,
                methodId = ID,
                methodVersion = VERSION,
                sourceService = signal.sourceService,
                signalType = signal.signalType,
                signalId = signal.id.value,
                timestampMs = timestampMs
            )
        )
    }

    fun outputValues(settingsState: SettingsState): Map<String, Any?> {
        val timestampMs = settingsState.getInt("timestamp_ms")
        val timestampIso = settingsState.getString("timestamp_iso").ifBlank {
            if (timestampMs > 0) Instant.ofEpochMilli(timestampMs.toLong()).toString() else ""
        }
        val confirmed = settingsState.getBoolean("confirmed")
        val verificationStatus = settingsState.getString("verification_status").ifBlank {
            if (confirmed) "verified" else "not_verified"
        }
        return linkedMapOf(
            "confirmed" to confirmed,
            "verification_status" to verificationStatus,
            "auth_method" to settingsState.getString("auth_method").ifBlank { "none" },
            "timestamp_ms" to timestampMs,
            "timestamp_iso" to timestampIso,
            "reason" to settingsState.getString("reason").ifBlank { settingsState.getString("confirmation_reason") },
            "message" to settingsState.getString("message"),
            "biometric_device_service" to settingsState.getString("biometric_device_service").ifBlank { AndroidBiometricDeviceService.SERVICE_ID },
            "biometric_signal_type" to settingsState.getString("biometric_signal_type").ifBlank { AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT },
            "biometric_execution_id" to settingsState.getString("biometric_execution_id"),
            "biometric_provenance_json" to settingsState.getString("biometric_provenance_json")
        )
    }

    private fun outputValues(
        signal: Signal,
        reason: String,
        executionId: String
    ): Map<String, Any?> {
        val verified = signal.payload["verified"]?.toBooleanStrictOrNull() ?: false
        val timestampMs = signal.payload["timestamp_ms"]?.toLongOrNull()
            ?: signal.temporalContext.eventTimeEpochMs
            ?: System.currentTimeMillis()
        val authMethod = signal.payload["auth_method"].orEmpty().ifBlank { "none" }
        return linkedMapOf(
            "confirmed" to verified,
            "verification_status" to if (verified) "verified" else "not_verified",
            "auth_method" to authMethod,
            "timestamp_ms" to timestampMs,
            "timestamp_iso" to Instant.ofEpochMilli(timestampMs).toString(),
            "reason" to reason,
            "message" to signal.payload["message"].orEmpty(),
            "biometric_device_service" to signal.sourceService,
            "biometric_signal_type" to signal.signalType,
            "biometric_execution_id" to executionId,
            "biometric_provenance_json" to provenanceJson(
                provider = signal.provenance.provider,
                methodId = ID,
                methodVersion = VERSION,
                sourceService = signal.sourceService,
                signalType = signal.signalType,
                signalId = signal.id.value,
                timestampMs = timestampMs
            )
        )
    }

    private fun provenanceJson(
        provider: String,
        methodId: String,
        methodVersion: String,
        sourceService: String,
        signalType: String,
        signalId: String,
        timestampMs: Long
    ): String = "{" + listOf(
        jsonPair("provider", provider),
        jsonPair("method_id", methodId),
        jsonPair("method_version", methodVersion),
        jsonPair("source_service", sourceService),
        jsonPair("signal_type", signalType),
        jsonPair("signal_id", signalId),
        jsonPair("timestamp_ms", timestampMs.toString(), quoteValue = false)
    ).joinToString(",") + "}"

    private fun jsonPair(key: String, value: String, quoteValue: Boolean = true): String {
        val escapedKey = key.replace("\\", "\\\\").replace("\"", "\\\"")
        val escapedValue = value.replace("\\", "\\\\").replace("\"", "\\\"")
        return if (quoteValue) {
            "\"$escapedKey\":\"$escapedValue\""
        } else {
            "\"$escapedKey\":$escapedValue"
        }
    }
}
