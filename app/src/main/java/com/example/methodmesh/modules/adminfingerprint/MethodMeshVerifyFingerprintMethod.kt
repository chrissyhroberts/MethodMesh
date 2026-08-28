package com.example.methodmesh.modules.adminfingerprint

import com.example.methodmesh.core.ResearchRuntime
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
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.platform.biometric.AndroidBiometricDeviceService
import com.example.methodmesh.platform.biometric.BiometricAuthenticationSignal
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

/**
 * Native AS1.00 method for local access authorisation using Android biometric
 * or device-credential authentication. It makes no person-identity claim.
 *
 * Android BiometricPrompt remains in the device-service/presentation boundary.
 * This method owns the research operation: authentication-result signal ->
 * attestation observation, transformation record and transport output fields.
 */
object As100VerifyFingerprintMethod : As100Method {
    const val ID = "admin_fingerprint_confirmation"
    const val VERSION = "1.1.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Local Device Authentication"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Local Device Authentication",
        version = VERSION,
        description = "Interpret an Android biometric/device-credential result as local access authorisation without claiming which enrolled person authenticated.",
        inputs = listOf(AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT),
        outputs = listOf(
            "confirmed",
            "verification_status",
            "auth_method",
            "authentication_policy",
            "assurance_scope",
            "identity_claimed",
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
            "category" to "Access control",
            "status" to "Experimental",
            "device_service" to AndroidBiometricDeviceService.SERVICE_ID,
            "authentication_method" to "biometric|device_credential|biometric_or_device_credential"
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
                reason = request.context["confirmation_reason"].orEmpty().ifBlank { "local_access_authorisation" },
                executionId = request.id.value,
                authenticationPolicy = request.context["authentication_method"].orEmpty()
                    .ifBlank { LocalAuthenticationMode.Biometric.wireValue }
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
            phenomenon = "authorization.local_device_authentication",
            subject = InvocationContext.from(request.context)?.subjectRef(),
            values = output,
            sourceSignal = signal?.let { ArchitectureRef(it.id, it.objectType, it.signalType) },
            temporalContext = signal?.temporalContext ?: request.temporalContext,
            provenance = provenance
        )

        val transformation = Transformation(
            action = "authorize.local_access",
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

        val result = execute(
            request = request(
                action = "verify.biometric.authentication_result",
                context = mapOf("confirmation_reason" to reason),
                signals = listOf(signal)
            ),
            settingsState = null
        )
        ResearchRuntime.session.record(result)
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
            "authentication_policy" to settingsState.getString("authentication_method")
                .ifBlank { LocalAuthenticationMode.Biometric.wireValue },
            "assurance_scope" to "local_device_access",
            "identity_claimed" to false,
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
        executionId: String,
        authenticationPolicy: String
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
            "authentication_policy" to authenticationPolicy,
            "assurance_scope" to "local_device_access",
            "identity_claimed" to false,
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
