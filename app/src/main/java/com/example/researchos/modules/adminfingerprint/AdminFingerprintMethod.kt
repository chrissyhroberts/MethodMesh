package com.example.researchos.modules.adminfingerprint

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.Method
import com.example.researchos.core.MethodCategory
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.MethodManifest
import com.example.researchos.core.MethodOutput
import com.example.researchos.core.MethodOutputSchema
import com.example.researchos.core.GraphField
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.MethodRequest
import com.example.researchos.core.MethodResult
import com.example.researchos.core.MethodStatus
import com.example.researchos.core.ResearchActivity
import com.example.researchos.core.ResearchActivityKind
import com.example.researchos.platform.BiometricAuthHelper
import com.example.researchos.platform.biometric.AndroidBiometricDeviceService
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState

/**
 * Compatibility UI shell for the native AS1.00 fingerprint/device-credential
 * verification method. The research operation is owned by
 * As100VerifyFingerprintMethod; this class keeps the current MethodCard demo,
 * settings and transport preview working while the app UI is still Method-based.
 */
class AdminFingerprintMethod : Method {

    override val manifest = MethodManifest(
        id = As100VerifyFingerprintMethod.ID,
        name = "Verify Fingerprint / Device Credential",
        description = "Request biometric, PIN, pattern or password confirmation from the registered device user and return an attestation record.",
        version = As100VerifyFingerprintMethod.VERSION,
        category = MethodCategory.Attestation,
        status = MethodStatus.Experimental,
        capabilities = listOf(
            ResearchActivity(
                id = "verify_fingerprint.attest",
                kind = ResearchActivityKind.Attest,
                label = "Verify participant or operator presence",
                producesEvidence = listOf("confirmed", "verification_status", "auth_method", "timestamp_iso")
            )
        ),
        requiredDeviceFeatures = listOf("biometric_or_device_credential"),
        contractSummary = "Requests local device authentication and returns an AS1.00 attestation observation with configurable provenance fields."
    )

    override val settings = listOf(
        MethodSetting.TextSetting(
            id = "prompt_title",
            label = "Prompt title",
            group = "Prompt",
            defaultValue = "Confirmation required"
        ),
        MethodSetting.TextSetting(
            id = "prompt_subtitle",
            label = "Prompt subtitle",
            group = "Prompt",
            defaultValue = "Use fingerprint, face unlock, PIN, pattern, or password to continue"
        ),
        MethodSetting.TextSetting(
            id = "prompt_description",
            label = "Prompt description",
            group = "Prompt",
            defaultValue = "Confirm that the expected participant or operator is present."
        ),
        MethodSetting.TextSetting(
            id = "cancel_text",
            label = "Cancel text",
            group = "Prompt",
            defaultValue = "Cancel"
        ),
        MethodSetting.TextSetting(
            id = "confirmation_reason",
            label = "Confirmation reason",
            group = "Output",
            defaultValue = "verify_fingerprint"
        ),
        MethodSetting.BooleanSetting(
            id = "confirmation_required",
            label = "Require explicit confirmation",
            group = "Security",
            defaultValue = true
        ),
        MethodSetting.BooleanSetting(
            id = "allow_device_credential",
            label = "Allow PIN/pattern/password fallback",
            group = "Security",
            defaultValue = true
        ),
    )

    override val outputSchema = MethodOutputSchema(
        graphOutputs = listOf(
            GraphOutput(
                id = "biometric_attestation_observation",
                objectType = KnowledgeObjectType.Observation,
                phenomenon = "attestation.biometric_verification",
                description = "Local Android biometric or device-credential authentication interpreted as an attestation observation. No biometric template or biometric material is retained.",
                fields = listOf(
                    GraphField("confirmed", "Observation.values.confirmed", MethodFieldType.Boolean, RequiredWhen.OnSuccessfulCapture),
                    GraphField("verification_status", "Observation.values.verification_status", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("auth_method", "Observation.values.auth_method", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("timestamp_ms", "Observation.temporalContext", MethodFieldType.Integer, RequiredWhen.OnSuccessfulCapture),
                    GraphField("timestamp_iso", "Observation.temporalContext", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("reason", "Observation.values.reason", MethodFieldType.Text, RequiredWhen.IfAvailable),
                    GraphField("message", "Observation.values.message", MethodFieldType.Text, RequiredWhen.IfAvailable),
                    GraphField("biometric_device_service", "Observation.provenance.provider", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("biometric_signal_type", "Observation.sourceSignal.signalType", MethodFieldType.Text, RequiredWhen.OnSuccessfulCapture),
                    GraphField("biometric_execution_id", "Transformation.id", MethodFieldType.Text, RequiredWhen.IfAvailable),
                    GraphField("biometric_provenance_json", "Observation.provenance", MethodFieldType.Json, RequiredWhen.IfAvailable)
                )
            )
        ),
        fields = listOf(
            MethodField("confirmed", "Confirmed", MethodFieldType.Boolean, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.confirmed"),
            MethodField("verification_status", "Verification status", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.verification_status"),
            MethodField("auth_method", "Authentication method", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.values.auth_method"),
            MethodField("timestamp_ms", "Timestamp milliseconds", MethodFieldType.Integer, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.temporalContext"),
            MethodField("timestamp_iso", "Timestamp ISO", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.temporalContext"),
            MethodField("reason", "Reason", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.reason"),
            MethodField("message", "Message", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.values.message"),
            MethodField("biometric_device_service", "Biometric device service", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.provenance.provider"),
            MethodField("biometric_signal_type", "Biometric signal type", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.OnSuccessfulCapture, graphPath = "Observation.sourceSignal.signalType"),
            MethodField("biometric_execution_id", "Biometric execution ID", MethodFieldType.Text, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Transformation.id"),
            MethodField("biometric_provenance_json", "Biometric provenance JSON", MethodFieldType.Json, required = false, requiredWhen = RequiredWhen.IfAvailable, graphPath = "Observation.provenance")
        )
    )

    @Composable
    override fun Demo(settingsState: SettingsState) {
        val context = LocalContext.current
        var statusText by remember { mutableStateOf("Not confirmed") }

        val allowDeviceCredential = settingsState.getBoolean("allow_device_credential")
        val availability = AndroidBiometricDeviceService.availability(
            context = context,
            allowDeviceCredential = allowDeviceCredential
        )

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(availability.message)

            Spacer(modifier = Modifier.height(8.dp))

            Text("Availability code: ${availability.code}")

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                enabled = availability.available,
                onClick = {
                    BiometricAuthHelper.authenticate(
                        context = context,
                        title = settingsState.getString("prompt_title"),
                        subtitle = settingsState.getString("prompt_subtitle"),
                        description = settingsState.getString("prompt_description"),
                        cancelText = settingsState.getString("cancel_text"),
                        confirmationRequired = settingsState.getBoolean("confirmation_required"),
                        allowDeviceCredential = settingsState.getBoolean("allow_device_credential"),
                        onSuccess = { authMethod ->
                            val signal = AndroidBiometricDeviceService.authenticationSignal(
                                verified = true,
                                authMethod = authMethod,
                                message = "Confirmed"
                            )
                            As100VerifyFingerprintMethod.recordAuthenticationResult(
                                settingsState = settingsState,
                                authenticationSignal = signal,
                                reason = settingsState.getString("confirmation_reason")
                            )
                            statusText = "Confirmed using $authMethod"
                        },
                        onFailure = { message ->
                            val signal = AndroidBiometricDeviceService.authenticationSignal(
                                verified = false,
                                authMethod = "none",
                                message = message
                            )
                            As100VerifyFingerprintMethod.recordAuthenticationResult(
                                settingsState = settingsState,
                                authenticationSignal = signal,
                                reason = settingsState.getString("confirmation_reason")
                            )
                            statusText = message
                        }
                    )
                }
            ) {
                Text("Request confirmation")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = statusText,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    override fun buildOutput(settingsState: SettingsState): MethodOutput =
        As100VerifyFingerprintMethod.buildOutput(settingsState)

    @Composable
    override fun Help() {
        Text(
            "Requests confirmation using Android BiometricPrompt. " +
                "Depending on device settings, this can use fingerprint, face unlock, PIN, pattern, or password. " +
                "No fingerprint data is accessed or stored by ResearchOS. The AS1.00 method records only the authentication outcome, timestamp, device-service signal and provenance."
        )
    }

    override fun execute(request: MethodRequest): MethodResult {
        return MethodResult(
            success = false,
            errorMessage = "Fingerprint/device-credential verification is an interactive device-service method and must be initiated from a UI or intent flow that can display Android BiometricPrompt."
        )
    }
}
