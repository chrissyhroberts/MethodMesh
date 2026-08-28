package com.example.methodmesh.platform.biometric

import android.content.Context
import com.example.methodmesh.core.methodmesh.DeviceService
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.TemporalContext
import com.example.methodmesh.platform.BiometricAvailability
import com.example.methodmesh.platform.BiometricAuthHelper

/**
 * Platform biometric/device-credential authentication event captured by the
 * Android biometric device service.
 *
 * The AS1.00 Signal is the architectural object consumed by methods. Android's
 * BiometricPrompt remains inside platform code; no biometric template or raw
 * biometric material is ever exposed to MethodMesh.
 */
data class BiometricAuthenticationSignal(
    val signal: Signal,
    val verified: Boolean,
    val authMethod: String,
    val message: String
)

/**
 * AS1.00 Device Service for Android biometric/device-credential authentication.
 *
 * This service is source-only: it normalises Android authentication outcomes
 * into transient Signals. It does not decide how the authentication should be
 * used in a protocol and does not create persistent observations.
 */
object AndroidBiometricDeviceService : DeviceService {
    override val serviceId: String = SERVICE_ID

    override fun describe(): Map<String, String> = mapOf(
        "service_id" to SERVICE_ID,
        "service_type" to "device.biometric",
        "platform" to "android",
        "signal_type" to SIGNAL_TYPE_AUTHENTICATION_RESULT,
        "responsibility" to "Emit biometric/device-credential authentication-result signals without storing biometric material."
    )

    fun availability(
        context: Context,
        allowDeviceCredential: Boolean
    ): BiometricAvailability = BiometricAuthHelper.availability(
        context = context,
        allowDeviceCredential = allowDeviceCredential
    )

    fun signalFromResult(
        verified: Boolean,
        authMethod: String,
        message: String = if (verified) "Confirmed" else "Authentication failed"
    ): Signal {
        val now = System.currentTimeMillis()
        return Signal(
            signalType = SIGNAL_TYPE_AUTHENTICATION_RESULT,
            sourceService = SERVICE_ID,
            payload = linkedMapOf(
                "verified" to verified.toString(),
                "auth_method" to authMethod,
                "message" to message,
                "timestamp_ms" to now.toString()
            ),
            temporalContext = TemporalContext(
                eventTimeEpochMs = now,
                systemTimeEpochMs = now
            ),
            provenance = ProvenanceContext(
                provider = "android.biometric_prompt",
                methodId = SERVICE_ID
            )
        )
    }

    fun authenticationSignal(
        verified: Boolean,
        authMethod: String,
        message: String = if (verified) "Confirmed" else "Authentication failed"
    ): BiometricAuthenticationSignal = BiometricAuthenticationSignal(
        signal = signalFromResult(
            verified = verified,
            authMethod = authMethod,
            message = message
        ),
        verified = verified,
        authMethod = authMethod,
        message = message
    )

    const val SERVICE_ID = "device.biometric.android"
    const val SIGNAL_TYPE_AUTHENTICATION_RESULT = "biometric.authentication.result"
}
