package com.example.researchos.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

data class BiometricAvailability(
    val available: Boolean,
    val code: Int,
    val message: String,
    val authenticators: Int
)

object BiometricAuthHelper {
    fun biometricAvailability(context: Context): BiometricAvailability =
        availabilityFor(
            context = context,
            authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK,
            successMessage = "Biometric authentication is available.",
            noneEnrolledMessage = "No biometric credential is enrolled. Add a fingerprint or face unlock in Android settings."
        )

    fun deviceCredentialAvailability(context: Context): BiometricAvailability =
        availabilityFor(
            context = context,
            authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            successMessage = "PIN, pattern or password authentication is available.",
            noneEnrolledMessage = "No PIN, pattern or password is configured in Android settings."
        )

    fun biometricOrCredentialAvailability(context: Context): BiometricAvailability =
        availabilityFor(
            context = context,
            authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            successMessage = "Biometric or device credential authentication is available.",
            noneEnrolledMessage = "No biometric, PIN, pattern or password is enrolled in Android settings."
        )

    fun authenticators(
        allowDeviceCredential: Boolean
    ): Int {
        return if (allowDeviceCredential) {
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
        } else {
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        }
    }

    fun availability(
        context: Context,
        allowDeviceCredential: Boolean
    ): BiometricAvailability {
        return if (allowDeviceCredential) {
            biometricOrCredentialAvailability(context)
        } else {
            biometricAvailability(context)
        }
    }

    private fun availabilityFor(
        context: Context,
        authenticators: Int,
        successMessage: String,
        noneEnrolledMessage: String
    ): BiometricAvailability {
        val code = BiometricManager.from(context).canAuthenticate(authenticators)
        val message = when (code) {
            BiometricManager.BIOMETRIC_SUCCESS -> successMessage
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> noneEnrolledMessage
            else -> availabilityMessage(
                code,
                allowDeviceCredential =
                    (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
            )
        }
        return BiometricAvailability(
            available = code == BiometricManager.BIOMETRIC_SUCCESS,
            code = code,
            message = message,
            authenticators = authenticators
        )
    }

    fun canAuthenticate(
        context: Context,
        allowDeviceCredential: Boolean
    ): Boolean {
        return availability(
            context = context,
            allowDeviceCredential = allowDeviceCredential
        ).available
    }

    fun authenticate(
        context: Context,
        title: String,
        subtitle: String,
        description: String,
        cancelText: String,
        confirmationRequired: Boolean,
        allowDeviceCredential: Boolean,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()

        if (activity == null) {
            onFailure("No FragmentActivity available for biometric prompt.")
            return
        }

        val availability = availability(
            context = context,
            allowDeviceCredential = allowDeviceCredential
        )

        if (!availability.available) {
            onFailure(availability.message)
            return
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title.ifBlank { "Authentication required" })
            .setConfirmationRequired(confirmationRequired)
            .setAllowedAuthenticators(availability.authenticators)

        if (subtitle.isNotBlank()) {
            promptInfoBuilder.setSubtitle(subtitle)
        }

        if (description.isNotBlank()) {
            promptInfoBuilder.setDescription(description)
        }

        if (!allowDeviceCredential) {
            promptInfoBuilder.setNegativeButtonText(
                cancelText.ifBlank { "Cancel" }
            )
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    val method = when (result.authenticationType) {
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> "biometric"
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> "device_credential"
                        else -> "unknown"
                    }

                    onSuccess(method)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    onFailure("$errString ($errorCode)")
                }

                override fun onAuthenticationFailed() {
                    onFailure("Authentication failed.")
                }
            }
        )

        prompt.authenticate(promptInfoBuilder.build())
    }

    fun authenticateDeviceCredential(
        context: Context,
        title: String,
        subtitle: String,
        description: String,
        confirmationRequired: Boolean,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        authenticateWithAuthenticators(
            context = context,
            authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            title = title,
            subtitle = subtitle,
            description = description,
            cancelText = "Cancel",
            confirmationRequired = confirmationRequired,
            onSuccess = { onSuccess("device_credential") },
            onFailure = onFailure
        )
    }

    private fun authenticateWithAuthenticators(
        context: Context,
        authenticators: Int,
        title: String,
        subtitle: String,
        description: String,
        cancelText: String,
        confirmationRequired: Boolean,
        onSuccess: (String) -> Unit,
        onFailure: (String) -> Unit
    ) {
        val activity = context.findFragmentActivity()

        if (activity == null) {
            onFailure("No FragmentActivity available for authentication prompt.")
            return
        }

        val code = BiometricManager.from(context).canAuthenticate(authenticators)
        if (code != BiometricManager.BIOMETRIC_SUCCESS) {
            onFailure(availabilityMessage(code, allowDeviceCredential = (authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0))
            return
        }

        val promptInfoBuilder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title.ifBlank { "Authentication required" })
            .setConfirmationRequired(confirmationRequired)
            .setAllowedAuthenticators(authenticators)

        if (subtitle.isNotBlank()) promptInfoBuilder.setSubtitle(subtitle)
        if (description.isNotBlank()) promptInfoBuilder.setDescription(description)
        if ((authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL) == 0) {
            promptInfoBuilder.setNegativeButtonText(cancelText.ifBlank { "Cancel" })
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val method = when (result.authenticationType) {
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_BIOMETRIC -> "biometric"
                        BiometricPrompt.AUTHENTICATION_RESULT_TYPE_DEVICE_CREDENTIAL -> "device_credential"
                        else -> "unknown"
                    }
                    onSuccess(method)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure("$errString ($errorCode)")
                }

                override fun onAuthenticationFailed() {
                    onFailure("Authentication failed.")
                }
            }
        )

        prompt.authenticate(promptInfoBuilder.build())
    }

    private fun availabilityMessage(
        code: Int,
        allowDeviceCredential: Boolean
    ): String {
        return when (code) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                if (allowDeviceCredential) {
                    "Biometric or device credential authentication is available."
                } else {
                    "Biometric authentication is available."
                }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No biometric hardware is available on this device."

            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware is currently unavailable."

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                if (allowDeviceCredential) {
                    "No biometric or device credential is enrolled. Add a fingerprint, face unlock, PIN, pattern, or password in Android settings."
                } else {
                    "No biometric credential is enrolled. Add a fingerprint or face unlock in Android settings."
                }

            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "A security update is required before biometric authentication can be used."

            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                "This authentication combination is not supported on this device."

            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                "Authentication availability is unknown."

            else ->
                "Authentication is not available. BiometricManager code: $code."
        }
    }
}

private fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this

    while (current is ContextWrapper) {
        if (current is FragmentActivity) {
            return current
        }
        current = current.baseContext
    }

    return null
}
