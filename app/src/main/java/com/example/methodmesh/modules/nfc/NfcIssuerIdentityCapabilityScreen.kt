package com.example.methodmesh.modules.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object NfcIssuerIdentityCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcIssuerIdentityMethod.ID
    override val title = "NFC issuer identity"
    override val description =
        "Export this device's credential-issuer public identity for offline study trust configuration."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        val clipboard = remember(androidContext) {
            androidContext.getSystemService(ClipboardManager::class.java)
        }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Loading issuer identity…") }

        LaunchedEffect(context.action.canonicalId) {
            val execution = withContext(Dispatchers.Default) {
                As100NfcIssuerIdentityMethod.execute(
                    request = As100NfcIssuerIdentityMethod.request(
                        action = As100NfcIssuerIdentityMethod.ID,
                        context = context.request.invocationContext.asMap(As100NfcIssuerIdentityMethod.ID),
                        signals = emptyList(),
                        inputs = emptyList()
                    ),
                    settingsState = null,
                    transport = context.request.source
                )
            }
            result = execution
            status = "Issuer identity ready. Register the full SHA-256 fingerprint in the study's offline trust configuration."
            if (context.submitsImmediately) onConfirmed(execution)
        }

        val fields = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        val shortId = fields[NfcProvisionFields.ISSUER_KEY_ID]?.toString().orEmpty()
        val fingerprint = fields[NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256]?.toString().orEmpty()
        val identityJson = fields[NfcIssuerIdentityFields.IDENTITY_JSON]?.toString().orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = fields,
            onBack = onBack,
            onRetry = { },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "The private signing key remains in Android Keystore. Share only this public issuer identity.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(12.dp))
            Text("Short issuer ID", style = MaterialTheme.typography.labelLarge)
            Text(shortId.ifBlank { "…" }, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            Text("Full public-key SHA-256 fingerprint", style = MaterialTheme.typography.labelLarge)
            Text(fingerprint.ifBlank { "…" }, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (fingerprint.isNotBlank()) {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("MethodMesh NFC issuer fingerprint", fingerprint)
                            )
                            status = "Fingerprint copied."
                        }
                    },
                    enabled = fingerprint.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Copy fingerprint") }
                Spacer(Modifier.weight(0.05f))
                OutlinedButton(
                    onClick = {
                        if (identityJson.isNotBlank()) {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("MethodMesh NFC issuer identity", identityJson)
                            )
                            status = "Issuer identity JSON copied."
                        }
                    },
                    enabled = identityJson.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Copy JSON") }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (identityJson.isNotBlank()) {
                        androidContext.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, identityJson)
                                },
                                "Share NFC issuer identity"
                            )
                        )
                    }
                },
                enabled = identityJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share issuer identity") }
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Export issuer identity",
                        description = "Return the public issuer identity and full SHA-256 fingerprint; no network access is required.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_payload_mode='FULL',return_mode='flat')"
                    )
                )
            )
        }
    }
}
