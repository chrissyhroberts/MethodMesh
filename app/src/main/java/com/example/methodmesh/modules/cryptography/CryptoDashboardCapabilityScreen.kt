package com.example.methodmesh.modules.cryptography

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONObject

/**
 * Human-first cryptography landing surface.
 *
 * The dashboard helps the operator choose the right cryptographic class by real-world goal.
 * It deliberately contains no keys, seeds, passwords or operational payloads.
 */
object CryptoDashboardCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100CryptoDashboardMethod.id
    override val title = "Crypto guide & status"
    override val description = "Choose a cryptography task by what you are trying to achieve, then see non-secret device status." 

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var signingIdentityPresent by remember { mutableStateOf(false) }
        var vaultPresent by remember { mutableStateOf(false) }
        var loading by rememberSaveable { mutableStateOf(false) }
        var attempted by rememberSaveable { mutableStateOf(false) }

        fun refresh() {
            if (loading) return
            loading = true
            val biometricSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
            vaultPresent = if (biometricSupported) runCatching { AndroidBiometricVault(androidContext).hasVault() }.getOrDefault(false) else false
            signingIdentityPresent = runCatching { JwsIdentityEngine.hasIdentity() }.getOrDefault(false)
            val status = JSONObject(
                linkedMapOf(
                    "schema" to "methodmesh.crypto.dashboard.v2",
                    "portable_encryption" to "JWE Compact (PBES2-HS256+A128KW / A256GCM)",
                    "portable_signatures" to "JWS detached ES256 + public JWK",
                    "totp" to "RFC 6238 / otpauth provisioning",
                    "signing_identity_present" to signingIdentityPresent,
                    "biometric_vault_supported" to biometricSupported,
                    "totp_vault_present" to vaultPresent,
                    "secrets_in_dashboard" to false
                )
            ).toString()
            val values = mapOf(
                CryptoFields.VALUE to status,
                CryptoFields.FORMAT to "MethodMesh cryptography guide/status snapshot",
                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                    methodId = capabilityId,
                    status = "succeeded",
                    format = "methodmesh.crypto.dashboard.v2",
                    extra = mapOf(
                        "signing_identity_present" to signingIdentityPresent,
                        "biometric_vault_supported" to biometricSupported,
                        "totp_vault_present" to vaultPresent
                    )
                )
            )
            result = CryptoScreenSupport.succeeded(As100CryptoDashboardMethod, context, values)
            loading = false
        }

        LaunchedEffect(Unit) {
            if (!attempted) {
                attempted = true
                refresh()
            }
        }

        val keepLiveDashboard = context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun
        val scaffoldResult = if (keepLiveDashboard) null else result

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, false) }.orEmpty(),
            onBack = onBack,
            onRetry = { result = null; refresh() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column {
                Text("Start with the outcome", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("You do not need to choose an algorithm. Choose the real-world problem; MethodMesh keeps the technical profile behind the rail unless you open Expert options.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(14.dp))

                CryptoRouteCard(
                    "Privacy",
                    "I need other people or systems to receive data without seeing the plaintext.",
                    "Choose: Protect text with a password · Protect a file with a password",
                    "Encryption hides content. The password must travel separately and cannot be recovered by MethodMesh."
                )
                CryptoRouteCard(
                    "Authenticity & integrity",
                    "I need someone to check that content really matches what I signed and was not changed.",
                    "Choose: Set up my signing identity · Sign a file or message · Check a signature",
                    "Signing does not hide content. A trusted identity card/fingerprint connects a signing key to a person."
                )
                CryptoRouteCard(
                    "Live proof",
                    "I need someone to prove they control a signing key right now, not merely show an old signature.",
                    "Choose: Create a live identity challenge · Prove I control my signing key · Check a live identity proof",
                    "Challenges expire and protect against replay. They prove current key possession, not legal identity by themselves."
                )
                CryptoRouteCard(
                    "Authenticator",
                    "I need six/eight digit login codes without using Google or Microsoft Authenticator.",
                    "Choose: Add an authenticator account · Authenticator codes · Back up my authenticator",
                    "TOTP seeds stay in a biometric-gated local locker. Treat setup seeds and backups as highly sensitive."
                )
                CryptoRouteCard(
                    "Recovery & checking",
                    "I need several people/places to share recovery responsibility, or I need to fingerprint exact content.",
                    "Choose: Create recovery shares · Recover a shared secret · Create a content fingerprint",
                    "Recovery shares are sensitive; fingerprints check exact bytes but do not encrypt or prove authorship."
                )

                Text("This device", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                CryptoStatusCard(
                    "Signing identity",
                    if (signingIdentityPresent) "READY" else "NOT SET UP",
                    if (signingIdentityPresent) "This device has a local ES256 signing identity." else "Set one up before sharing an identity card or signing content."
                )
                CryptoStatusCard(
                    "Authenticator locker",
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) "UNAVAILABLE" else if (vaultPresent) "READY" else "EMPTY",
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) "Strong biometric vault support requires Android 11+." else if (vaultPresent) "One or more local TOTP accounts are stored." else "Add an authenticator account to initialise the locker."
                )

                Text("Under the hood", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Portable encryption: JWE. Signatures: detached JWS/ES256. Public keys: JWK. Authenticator: RFC 6238 TOTP. These standards details are mainly for interoperability and audit, not normal operation.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Text("No passwords, private keys, TOTP seeds, plaintext payloads or recovery shares are shown on this dashboard.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                Button(onClick = ::refresh, modifier = Modifier.fillMaxWidth(), enabled = !loading) { Text(if (loading) "Refreshing…" else "Refresh device status") }

                if (keepLiveDashboard && result != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { result?.let(onConfirmed) }, modifier = Modifier.fillMaxWidth()) { Text(if (context.isNativePresetRun) "Finish" else "Use status snapshot") }
                }
            }
        }
    }
}
