package com.example.methodmesh.modules.adminfingerprint

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

object BrowserBiometricCalloutCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId: String = As100BrowserBiometricCalloutMethod.ID
    override val title: String = "Browser biometric call-out"
    override val description: String = "Allow a browser or web form to request local biometric confirmation without modifying the web-form host."

    private val settingsSpec = listOf(
        MethodSetting.TextSetting("prompt_title", "Prompt title", group = "Prompt", defaultValue = "Confirm with biometrics"),
        MethodSetting.TextSetting("prompt_subtitle", "Prompt subtitle", group = "Prompt", defaultValue = "Return to the web form when complete"),
        MethodSetting.TextSetting("prompt_description", "Prompt description", group = "Prompt", defaultValue = "This confirms an enrolled biometric on this device. It does not identify which enrolled person authenticated."),
        MethodSetting.TextSetting("cancel_text", "Cancel text", group = "Prompt", defaultValue = "Cancel"),
        MethodSetting.TextSetting("caller", "Caller", group = "Context", defaultValue = "browser"),
        MethodSetting.TextSetting("request_ref", "Request reference", group = "Context", defaultValue = ""),
        MethodSetting.TextSetting("confirmation_reason", "Confirmation reason", group = "Output", defaultValue = "browser_biometric_callout"),
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
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready for browser biometric confirmation.") }
        val availability = BiometricAuthHelper.biometricAvailability(androidContext)

        fun recordOutcome(verified: Boolean, authMethod: String, message: String) {
            val caller = settings.getString("caller").ifBlank { "browser" }
            val requestRef = settings.getString("request_ref")
            val reason = settings.getString("confirmation_reason").ifBlank { "browser_biometric_callout" }
            val issuedToken = if (verified) {
                runCatching {
                    BrowserBiometricVerificationToken.issue(
                        requestRef = requestRef,
                        caller = caller,
                        reason = reason,
                        authMethod = authMethod
                    )
                }.getOrNull()
            } else {
                null
            }
            val tokenContext = issuedToken?.let { issued ->
                mapOf(
                    "verification_token" to issued.token,
                    "verification_token_format" to BrowserBiometricVerificationToken.FORMAT,
                    "verification_key_id" to issued.keyId,
                    "verification_public_key_spki_base64url" to issued.publicKeySpkiBase64Url
                )
            }.orEmpty()

            val signal = AndroidBiometricDeviceService.authenticationSignal(
                verified = verified,
                authMethod = authMethod,
                message = message
            )
            result = As100BrowserBiometricCalloutMethod.execute(
                request = As100BrowserBiometricCalloutMethod.request(
                    action = action.canonicalId,
                    context = request.invocationContext.asMap(action.canonicalId) +
                        action.settings + mapOf(
                            "caller" to caller,
                            "request_ref" to requestRef,
                            "confirmation_reason" to reason
                        ) + tokenContext,
                    signals = listOf(signal.signal)
                ),
                settingsState = null,
                transport = request.source
            ).withInvocationContext(request.invocationContext)

            if (verified && issuedToken != null) {
                status = "Biometric confirmed. The verification token is available in the capability result."
            } else if (verified) {
                status = "Biometric confirmed, but the verification token could not be created."
            } else {
                status = message
            }
        }

        fun startVerification() {
            BiometricAuthHelper.authenticate(
                context = androidContext,
                title = settings.getString("prompt_title"),
                subtitle = settings.getString("prompt_subtitle"),
                description = settings.getString("prompt_description"),
                cancelText = settings.getString("cancel_text"),
                confirmationRequired = settings.getBoolean("confirmation_required"),
                allowDeviceCredential = false,
                onSuccess = { authMethod ->
                    recordOutcome(true, authMethod, "Browser biometric confirmation completed")
                },
                onFailure = { message ->
                    recordOutcome(false, "none", message)
                }
            )
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
            Spacer(Modifier.height(8.dp))
            Text("On success this capability returns a device-signed verification token as a normal MethodMesh output. Browser/Enketo launches use the shared browser bridge to copy the projected result to the clipboard; the capability itself does not own transport-specific clipboard behaviour.")
            if (settings.getString("request_ref").isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Request reference: ${settings.getString("request_ref")}")
            }
            Spacer(Modifier.height(10.dp))
            Button(enabled = availability.available, onClick = { startVerification() }) {
                Text(if (result == null) "Confirm with biometrics" else "Confirm again")
            }

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = As100BrowserBiometricCalloutMethod.ID,
                examples = listOf(
                    IntentExample(
                        label = "Browser / Enketo biometric call-out",
                        description = "Biometric-only call suitable for a browser launch",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${As100BrowserBiometricCalloutMethod.ID}',input_caller='enketo',input_confirmation_reason='enketo_browser_test')"
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
                is MethodSetting.MultiChoiceSetting -> settingsState.setString(setting.id, raw)
            }
        }
    }
}
