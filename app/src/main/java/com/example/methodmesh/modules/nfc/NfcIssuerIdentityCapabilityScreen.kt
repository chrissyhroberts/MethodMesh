package com.example.methodmesh.modules.nfc

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * First-class Workbench presentation of the local public NFC issuer identity.
 * The certificate is a MethodMesh public-key identity bundle, not an X.509 CA
 * certificate and not an authorisation decision.
 */
@Composable
fun NfcIssuerCertificateWorkbenchPanel(
    modifier: Modifier = Modifier
) {
    val androidContext = LocalContext.current
    val clipboard = remember(androidContext) {
        androidContext.getSystemService(ClipboardManager::class.java)
    }
    val values = remember { NfcIssuerIdentity.currentValues() }
    val shortId = values[NfcProvisionFields.ISSUER_KEY_ID].orEmpty()
    val fingerprint = values[NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256].orEmpty()
    val certificate = values[NfcIssuerIdentityFields.CERTIFICATE_JSON].orEmpty()
    var expanded by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("NFC issuer certificate", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Public provisioning-device identity · safe to copy for registry and later validation",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("Issuer ID", style = MaterialTheme.typography.labelLarge)
            Text(shortId, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(8.dp))
            Text("SHA-256 public-key fingerprint", style = MaterialTheme.typography.labelLarge)
            SelectionContainer {
                Text(fingerprint, fontFamily = FontFamily.Monospace)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("MethodMesh NFC issuer certificate", certificate)
                        )
                        status = "Issuer certificate copied."
                    },
                    enabled = certificate.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Copy certificate") }
                Spacer(Modifier.weight(0.05f))
                OutlinedButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.weight(1f)
                ) { Text(if (expanded) "Hide certificate" else "Show certificate") }
            }
            if (expanded) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        certificate,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "This bundle contains only the public key and its canonical identity. The private signing key remains in Android Keystore. " +
                    "Registering the certificate is the separate administrative act that makes an issuer recognised by a study.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

object NfcIssuerIdentityCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100NfcIssuerIdentityMethod.ID
    override val title = "NFC issuer identity"
    override val description =
        "Show this installation's public credential-issuer identity for provisioning-device registration."

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
        var reloadNonce by remember { mutableStateOf(0) }

        LaunchedEffect(context.action.canonicalId, reloadNonce) {
            val execution = withContext(Dispatchers.IO) {
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
            status = if (execution.status == TransformationStatus.Succeeded) {
                "Issuer identity ready. Register the full fingerprint in the study provisioning-device registry only if this installation is authorised to issue study credentials."
            } else {
                execution.diagnostics["reason"] ?: "Issuer identity could not be loaded."
            }
        }

        val fields = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        val shortId = fields[NfcProvisionFields.ISSUER_KEY_ID]?.toString().orEmpty()
        val fingerprint = fields[NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256]?.toString().orEmpty()
        val certificateJson = fields[NfcIssuerIdentityFields.CERTIFICATE_JSON]?.toString().orEmpty()

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = fields,
            onBack = onBack,
            onRetry = {
                result = null
                status = "Reloading issuer identity…"
                reloadNonce += 1
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(
                "This is the public identity of the signing key this MethodMesh installation uses when it provisions NFC credentials.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "The private signing key remains in Android Keystore. The MethodMesh issuer certificate is a public identity bundle, not an X.509 CA certificate and not a study authorisation decision.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text("Short issuer ID", style = MaterialTheme.typography.labelLarge)
            Text(shortId.ifBlank { "…" }, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(10.dp))
            Text("Canonical public-key SHA-256 fingerprint", style = MaterialTheme.typography.labelLarge)
            Text(fingerprint.ifBlank { "…" }, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (certificateJson.isNotBlank()) {
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText("MethodMesh NFC issuer certificate", certificateJson)
                            )
                            status = "Issuer certificate copied."
                        }
                    },
                    enabled = certificateJson.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) { Text("Copy certificate") }
                Spacer(Modifier.weight(0.05f))
                OutlinedButton(
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
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    if (certificateJson.isNotBlank()) {
                        androidContext.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    type = "application/json"
                                    putExtra(Intent.EXTRA_TEXT, certificateJson)
                                },
                                "Share NFC issuer certificate"
                            )
                        )
                    }
                },
                enabled = certificateJson.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Share issuer certificate") }
            Spacer(Modifier.height(12.dp))
            Text(status, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = listOf(
                    IntentExample(
                        label = "Export issuer identity",
                        description = "Return the public provisioning-device identity, copyable certificate bundle and full SHA-256 fingerprint; no network access is required.",
                        intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='$capabilityId',input_payload_mode='FULL',return_mode='flat')"
                    )
                )
            )
        }
    }
}
