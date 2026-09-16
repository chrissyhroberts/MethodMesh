package com.example.methodmesh.modules.adminfingerprint

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
import com.example.methodmesh.settings.SettingsState
import java.time.Instant

/**
 * Browser-callable biometric confirmation for web forms such as Enketo.
 *
 * This is intentionally a call-out capability. It proves only that Android
 * accepted an enrolled biometric on the local device. It does not identify
 * the person and it does not imply that an unmodified browser form can receive
 * an automatic callback value.
 */
object As100BrowserBiometricCalloutMethod : As100Method {
    const val ID = "admin_browser_biometric_callout"
    const val VERSION = "1.1.0"

    override val id: String = ID

    override val ref: ArchitectureRef = ArchitectureRef(
        id = ArchitectureId(ID),
        type = "Method",
        label = "Browser Biometric Call-out"
    )

    override val descriptor: MethodDescriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.SignalInterpreter,
        name = "Browser Biometric Call-out",
        version = VERSION,
        description = "Confirm local device access with an Android biometric when invoked from a browser or web form.",
        inputs = listOf(AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT),
        outputs = listOf(
            "confirmed",
            "verification_status",
            "auth_method",
            "assurance_scope",
            "identity_claimed",
            "caller",
            "request_ref",
            "timestamp_ms",
            "timestamp_iso",
            "reason",
            "message",
            "biometric_device_service",
            "biometric_signal_type",
            "biometric_execution_id",
            "verification_token",
            "verification_token_format",
            "verification_key_id",
            "verification_public_key_spki_base64url"
        ),
        parameters = mapOf(
            "category" to "Access control",
            "status" to "Experimental",
            "device_service" to AndroidBiometricDeviceService.SERVICE_ID,
            "authentication_method" to "biometric",
            "integration" to "browser_callout"
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
        val verified = signal?.payload?.get("verified")?.toBooleanStrictOrNull() ?: false
        val timestampMs = signal?.payload?.get("timestamp_ms")?.toLongOrNull()
            ?: signal?.temporalContext?.eventTimeEpochMs
            ?: System.currentTimeMillis()
        val authMethod = signal?.payload?.get("auth_method").orEmpty().ifBlank { "none" }
        val caller = request.context["caller"].orEmpty().ifBlank { "browser" }
        val requestRef = request.context["request_ref"].orEmpty()
        val reason = request.context["confirmation_reason"].orEmpty().ifBlank { "browser_biometric_callout" }
        val message = signal?.payload?.get("message").orEmpty()
        val verificationToken = request.context["verification_token"].orEmpty()
        val verificationTokenFormat = request.context["verification_token_format"].orEmpty()
        val verificationKeyId = request.context["verification_key_id"].orEmpty()
        val verificationPublicKey = request.context["verification_public_key_spki_base64url"].orEmpty()

        val output = linkedMapOf(
            "confirmed" to verified.toString(),
            "verification_status" to if (verified) "verified" else "not_verified",
            "auth_method" to authMethod,
            "assurance_scope" to "local_device_access",
            "identity_claimed" to "false",
            "caller" to caller,
            "request_ref" to requestRef,
            "timestamp_ms" to timestampMs.toString(),
            "timestamp_iso" to Instant.ofEpochMilli(timestampMs).toString(),
            "reason" to reason,
            "message" to message,
            "biometric_device_service" to (signal?.sourceService ?: AndroidBiometricDeviceService.SERVICE_ID),
            "biometric_signal_type" to (signal?.signalType ?: AndroidBiometricDeviceService.SIGNAL_TYPE_AUTHENTICATION_RESULT),
            "biometric_execution_id" to (signal?.id?.value ?: request.id.value),
            "verification_token" to verificationToken,
            "verification_token_format" to verificationTokenFormat,
            "verification_key_id" to verificationKeyId,
            "verification_public_key_spki_base64url" to verificationPublicKey
        )

        val provenance = ProvenanceContext(
            provider = signal?.provenance?.provider ?: AndroidBiometricDeviceService.SERVICE_ID,
            methodId = ID,
            methodVersion = VERSION
        )

        val observation = Observation(
            phenomenon = "authorization.browser_biometric_callout",
            subject = InvocationContext.from(request.context)?.subjectRef(),
            values = output,
            sourceSignal = signal?.let { ArchitectureRef(it.id, it.objectType, it.signalType) },
            temporalContext = signal?.temporalContext ?: request.temporalContext,
            provenance = provenance
        )

        val transformation = Transformation(
            action = "authorize.browser_biometric_callout",
            method = ref,
            inputs = signal?.let { listOf(ArchitectureRef(it.id, it.objectType, it.signalType)) } ?: emptyList(),
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (verified) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = observation.temporalContext,
            provenance = provenance,
            diagnostics = mapOf(
                "caller" to caller,
                "request_ref" to requestRef,
                "auth_method" to authMethod
            )
        )

        return As100ExecutionEngine.complete(
            request = request,
            status = transformation.status,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = mapOf(
                "method" to ID,
                "verified" to verified.toString(),
                "caller" to caller
            )
        )
    }
}
