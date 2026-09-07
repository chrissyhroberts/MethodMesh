package com.example.methodmesh.modules.cryptography

import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal

object BiometricAuth {
        fun authenticate(
        context: Context,
        title: String,
        subtitle: String = "",
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val prompt = BiometricPrompt.Builder(context)
            .setTitle(title)
            .apply { if (subtitle.isNotBlank()) setSubtitle(subtitle) }
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButton("Cancel", context.mainExecutor) { _, _ -> onError("Cancelled") }
            .build()

        prompt.authenticate(
            CancellationSignal(),
            context.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) =
                    onError(errString?.toString() ?: "Biometric authentication failed")
            }
        )
    }
}
