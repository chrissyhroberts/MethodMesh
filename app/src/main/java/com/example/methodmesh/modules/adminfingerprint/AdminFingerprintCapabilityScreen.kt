package com.example.methodmesh.modules.adminfingerprint

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.platform.BiometricAuthHelper
import com.example.methodmesh.platform.biometric.AndroidBiometricDeviceService
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.settings.SettingsState
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown

internal enum class LocalAuthenticationMode(
    val wireValue: String,
    val label: String,
    val explanation: String
) {
    Biometric(
        "biometric",
        "Biometric",
        "Require an enrolled fingerprint, face, or other Android biometric."
    ),
    DeviceCredential(
        "device_credential",
        "PIN, pattern or password",
        "Require the device credential configured in Android settings."
    ),
    BiometricOrDeviceCredential(
        "biometric_or_device_credential",
        "Biometric or device credential",
        "Allow either local authentication route and report which Android accepted."
    );

    companion object {
        fun parse(raw: String?): LocalAuthenticationMode? = entries.firstOrNull {
            it.wireValue.equals(raw?.trim(), ignoreCase = true)
        }
    }
}

object AdminFingerprintCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100VerifyFingerprintMethod.ID
    override val title: String = "Local device authentication"
    override val description: String = "Authorise local access using an enrolled biometric or device credential."

    private val settingsSpec = listOf(
        MethodSetting.TextSetting("prompt_title", "Prompt title", group = "Prompt", defaultValue = "Access authorisation required"),
        MethodSetting.TextSetting("prompt_subtitle", "Prompt subtitle", group = "Prompt", defaultValue = "Authenticate on this device to continue"),
        MethodSetting.TextSetting("prompt_description", "Prompt description", group = "Prompt", defaultValue = "This confirms local device access; it does not identify which enrolled person authenticated."),
        MethodSetting.TextSetting("cancel_text", "Cancel text", group = "Prompt", defaultValue = "Cancel"),
        MethodSetting.TextSetting("confirmation_reason", "Confirmation reason", group = "Output", defaultValue = "local_access_authorisation"),
        MethodSetting.BooleanSetting("confirmation_required", "Require explicit confirmation", group = "Security", defaultValue = true)
    )

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
            SettingsState(settingsSpec) { key, value ->
                context.onSettingsChanged(mapOf(key to value.toString()))
            }.also { state ->
                applyParameters(state, settingsSpec, action.settings)
            }
        }
        val requestedMode = action.settings["authentication_method"] ?: action.settings["input_authentication_method"]
        var mode by remember(action.settings) {
            mutableStateOf(LocalAuthenticationMode.parse(requestedMode) ?: LocalAuthenticationMode.Biometric)
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready for local authentication.") }
        val modeIsValid = requestedMode == null || LocalAuthenticationMode.parse(requestedMode) != null
        val availability = when (mode) {
            LocalAuthenticationMode.Biometric -> BiometricAuthHelper.biometricAvailability(androidContext)
            LocalAuthenticationMode.DeviceCredential -> BiometricAuthHelper.deviceCredentialAvailability(androidContext)
            LocalAuthenticationMode.BiometricOrDeviceCredential ->
                BiometricAuthHelper.biometricOrCredentialAvailability(androidContext)
        }

        fun recordOutcome(verified: Boolean, authMethod: String, message: String) {
            val signal = AndroidBiometricDeviceService.authenticationSignal(
                verified = verified,
                authMethod = authMethod,
                message = message
            )
            result = As100VerifyFingerprintMethod.execute(
                request = As100VerifyFingerprintMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) +
                        action.settings + mapOf(
                            "authentication_method" to mode.wireValue,
                            "confirmation_reason" to settings.getString("confirmation_reason")
                        ),
                    signals = listOf(signal.signal)
                ),
                settingsState = null,
                transport = request.source
            ).withInvocationContext(request.invocationContext)
            status = if (verified) "Authorised using $authMethod." else message
        }

        fun startVerification() {
            if (!modeIsValid) {
                status = "Unknown authentication_method '$requestedMode'."
                return
            }
            val success: (String) -> Unit = { authMethod ->
                recordOutcome(true, authMethod, "Local access authorised")
            }
            val failure: (String) -> Unit = { message ->
                recordOutcome(false, "none", message)
            }
            when (mode) {
                LocalAuthenticationMode.Biometric -> BiometricAuthHelper.authenticate(
                    context = androidContext,
                    title = settings.getString("prompt_title"),
                    subtitle = settings.getString("prompt_subtitle"),
                    description = settings.getString("prompt_description"),
                    cancelText = settings.getString("cancel_text"),
                    confirmationRequired = settings.getBoolean("confirmation_required"),
                    allowDeviceCredential = false,
                    onSuccess = success,
                    onFailure = failure
                )
                LocalAuthenticationMode.DeviceCredential ->
                    BiometricAuthHelper.authenticateDeviceCredential(
                        context = androidContext,
                        title = settings.getString("prompt_title"),
                        subtitle = settings.getString("prompt_subtitle"),
                        description = settings.getString("prompt_description"),
                        confirmationRequired = settings.getBoolean("confirmation_required"),
                        onSuccess = success,
                        onFailure = failure
                    )
                LocalAuthenticationMode.BiometricOrDeviceCredential -> BiometricAuthHelper.authenticate(
                    context = androidContext,
                    title = settings.getString("prompt_title"),
                    subtitle = settings.getString("prompt_subtitle"),
                    description = settings.getString("prompt_description"),
                    cancelText = settings.getString("cancel_text"),
                    confirmationRequired = settings.getBoolean("confirmation_required"),
                    allowDeviceCredential = true,
                    onSuccess = success,
                    onFailure = failure
                )
            }
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && availability.available) startVerification()
        }

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
            Text("Authentication method", fontWeight = FontWeight.SemiBold)
            LocalAuthenticationMode.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            mode = option
                            result = null
                            status = option.explanation
                        }
                        .padding(vertical = 6.dp)
                ) {
                    Text(if (mode == option) "●" else "○", modifier = Modifier.padding(end = 8.dp))
                    Text(option.label)
                }
            }
            Text(mode.explanation)
            Spacer(Modifier.height(10.dp))
            Button(enabled = availability.available, onClick = { startVerification() }) {
                Text(if (result == null) "Authorise access" else "Authorise again")
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100VerifyFingerprintMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Biometric access",
                        description = "Require an enrolled Android biometric",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100VerifyFingerprintMethod.ID}',input_authentication_method='biometric')"
                    ),
                    IntentExample(
                        label = "PIN, pattern or password",
                        description = "Require the configured Android device credential",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100VerifyFingerprintMethod.ID}',input_authentication_method='device_credential')"
                    ),
                    IntentExample(
                        label = "Either local method",
                        description = "Allow biometric or device credential",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100VerifyFingerprintMethod.ID}',input_authentication_method='biometric_or_device_credential')"
                    )
                )
            )
        }
    }

    private fun applyParameters(settingsState: SettingsState, settings: List<MethodSetting>, parameters: Map<String, String>) {
        settings.forEach { setting ->
            val raw = parameters[setting.id] ?: parameters["input_${setting.id}"] ?: return@forEach
            when (setting) {
                is MethodSetting.BooleanSetting -> settingsState.setBoolean(setting.id, raw.toBooleanStrictOrNull() ?: (raw == "1"))
                is MethodSetting.IntSetting -> raw.toIntOrNull()?.let { settingsState.setInt(setting.id, it) }
                is MethodSetting.FloatSetting -> raw.toFloatOrNull()?.let { settingsState.setFloat(setting.id, it) }
                is MethodSetting.TextSetting -> settingsState.setString(setting.id, raw)
                is MethodSetting.ChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }
}
