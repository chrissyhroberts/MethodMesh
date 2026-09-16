package com.example.methodmesh.modules.adminfingerprint

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AdminFingerprintModule : MethodMeshModule {
    override val moduleId: String = "adminfingerprint"
    override val displayName: String = "Local device authentication"


    override fun as100Methods() = listOf(As100VerifyFingerprintMethod, As100BrowserBiometricCalloutMethod)

    override fun rilBindings() = listOf(
        RilBinding("authorize local access", As100VerifyFingerprintMethod.ID, "Authorise access using a device biometric or credential"),
        RilBinding("authenticate on device", As100VerifyFingerprintMethod.ID, "Authenticate locally without making a person-identity claim"),
        RilBinding("require biometric", As100VerifyFingerprintMethod.ID, "Require an enrolled Android biometric"),
        RilBinding("require device credential", As100VerifyFingerprintMethod.ID, "Require the configured PIN, pattern or password"),
        RilBinding("browser biometric callout", As100BrowserBiometricCalloutMethod.ID, "Request biometric confirmation from an Enketo or browser form"),
        RilBinding("enketo biometric confirmation", As100BrowserBiometricCalloutMethod.ID, "Launch biometric confirmation from a stock Enketo form")
    )

    override fun capabilityScreens() = listOf(AdminFingerprintCapabilityScreen, BrowserBiometricCalloutCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100VerifyFingerprintMethod.ID to listOf(
            MethodSetting.ChoiceSetting("authentication_method", "Authentication method", defaultValue = "biometric_or_device_credential", choices = listOf("biometric", "device_credential", "biometric_or_device_credential")),
            MethodSetting.TextSetting("confirmation_reason", "Confirmation reason", defaultValue = "local_access_authorisation")
        ),
        As100BrowserBiometricCalloutMethod.ID to listOf(
            MethodSetting.TextSetting("caller", "Caller", defaultValue = "browser"),
            MethodSetting.TextSetting("request_ref", "Request reference", defaultValue = ""),
            MethodSetting.TextSetting("confirmation_reason", "Confirmation reason", defaultValue = "browser_biometric_callout")
        )
    )
}
