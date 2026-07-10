package com.example.researchos.modules.adminfingerprint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.platform.BiometricAuthHelper
import com.example.researchos.platform.biometric.AndroidBiometricDeviceService
import com.example.researchos.settings.MethodSetting
import com.example.researchos.settings.SettingsState
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

object AdminFingerprintCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100VerifyFingerprintMethod.ID
    override val title: String = "Identity verification"
    override val description: String = "Run device verification, review the outcome, then confirm or retry."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val action = context.action
        val request = context.request
        val androidContext = LocalContext.current
        val settings = remember(action.settings) {
            SettingsState(AdminFingerprintMethod().settings).also { state ->
                applyParameters(state, AdminFingerprintMethod().settings, action.settings)
            }
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready for verification.") }
        val allowDeviceCredential = settings.getBoolean("allow_device_credential")
        val availability = AndroidBiometricDeviceService.availability(androidContext, allowDeviceCredential)

        fun startVerification() {
            BiometricAuthHelper.authenticate(
                context = androidContext,
                title = settings.getString("prompt_title"),
                subtitle = settings.getString("prompt_subtitle"),
                description = settings.getString("prompt_description"),
                cancelText = settings.getString("cancel_text"),
                confirmationRequired = settings.getBoolean("confirmation_required"),
                allowDeviceCredential = allowDeviceCredential,
                onSuccess = { authMethod ->
                    val signal = AndroidBiometricDeviceService.authenticationSignal(
                        verified = true,
                        authMethod = authMethod,
                        message = "Confirmed"
                    )
                    val execution = As100VerifyFingerprintMethod.execute(
                        request = As100VerifyFingerprintMethod.request(
                            action = action.canonicalId,
                            context = request.invocationContext.asMap(action.canonicalId) + action.settings,
                            signals = listOf(signal.signal)
                        ),
                        settingsState = null,
                        transport = request.source
                    ).withInvocationContext(request.invocationContext)
                    result = execution
                    status = "Verified using $authMethod."
                },
                onFailure = { message ->
                    val signal = AndroidBiometricDeviceService.authenticationSignal(
                        verified = false,
                        authMethod = "none",
                        message = message
                    )
                    val execution = As100VerifyFingerprintMethod.execute(
                        request = As100VerifyFingerprintMethod.request(
                            action = action.canonicalId,
                            context = request.invocationContext.asMap(action.canonicalId) + action.settings,
                            signals = listOf(signal.signal)
                        ),
                        settingsState = null,
                        transport = request.source
                    ).withInvocationContext(request.invocationContext)
                    result = execution
                    status = message
                }
            )
        }

        LaunchedEffect(Unit) { if (availability.available) startVerification() }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { if (availability.available) startVerification() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(availability.message)
            Text(status)
            Spacer(Modifier.height(10.dp))
            Button(enabled = availability.available, onClick = { startVerification() }) {
                Text(if (result == null) "Start verification" else "Verify again")
            }
        }
    }

    private fun applyParameters(settingsState: SettingsState, settings: List<MethodSetting>, parameters: Map<String, String>) {
        settings.forEach { setting ->
            val raw = parameters[setting.id] ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: raw == "1")
                is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
                is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
                is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }
}
