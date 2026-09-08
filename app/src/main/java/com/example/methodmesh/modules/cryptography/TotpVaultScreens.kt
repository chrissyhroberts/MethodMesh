package com.example.methodmesh.modules.cryptography

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import org.json.JSONArray
import org.json.JSONObject

@Composable
private fun VaultScaffold(
    title: String,
    capabilityId: String,
    context: CapabilityScreenContext,
    result: ExecutionResult?,
    onBack: () -> Unit,
    onConfirmed: (ExecutionResult) -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    body: @Composable () -> Unit
) {
    CapabilityScreenScaffold(
        title = title,
        capabilityId = capabilityId,
        context = context,
        canGoBack = context.stepNumber > 1,
        capturedResult = result,
        resultPreview = result?.let { OutputFormatter.fields(it, false) }.orEmpty(),
        onBack = onBack,
        onRetry = onRetry,
        onConfirm = { result?.let(onConfirmed) },
        onCancel = onCancel
    ) { body() }
}

private fun vaultUnavailableResult(
    method: AbstractCryptoMethod,
    context: CapabilityScreenContext
): ExecutionResult = CryptoScreenSupport.failed(
    method,
    context,
    IllegalStateException("Biometric TOTP vault requires Android 11 or later with a strong enrolled biometric.")
)

object TotpImportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TotpImportMethod.id
    override val title = "Add an authenticator account"
    override val description = "Store a TOTP account in the biometric-protected local locker using either a setup link or the secret shown by the service."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var otpAuth by remember { mutableStateOf(context.action.settings["otpauth_uri"] ?: "") }
        var entryMode by rememberSaveable { mutableStateOf(if (otpAuth.isNotBlank()) "link" else "manual") }
        var issuer by remember { mutableStateOf("") }
        var accountName by remember { mutableStateOf("") }
        var manualSecret by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("") }

        fun importNow() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                result = vaultUnavailableResult(As100TotpImportMethod, context)
                return
            }
            val account = runCatching {
                if (entryMode == "manual") {
                    require(accountName.isNotBlank()) { "Account name is required." }
                    require(manualSecret.isNotBlank()) { "Authenticator secret is required." }
                    TotpAccount(issuer = issuer.trim(), accountName = accountName.trim(), secretBase32 = manualSecret.replace(" ", "").uppercase())
                } else {
                    TotpEngine.parseOtpAuth(otpAuth)
                }
            }.getOrElse {
                result = CryptoScreenSupport.failed(As100TotpImportMethod, context, it); return
            }
            runCatching { TotpEngine.generate(account) }.onFailure {
                result = CryptoScreenSupport.failed(As100TotpImportMethod, context, IllegalArgumentException("Authenticator setup is invalid: ${it.message}")); return
            }
            val vault = AndroidBiometricVault(androidContext)
            // Make sure a key exists before prompting so the authentication policy is registered.
            runCatching { vault.ensureKey() }.onFailure {
                result = CryptoScreenSupport.failed(As100TotpImportMethod, context, it); return
            }
            BiometricAuth.authenticate(
                context = androidContext,
                title = "Unlock TOTP locker",
                subtitle = "Import ${account.label}",
                onSuccess = {
                    runCatching {
                        val existing = if (vault.hasVault()) vault.loadWithAuthenticatedCipher(vault.newDecryptCipher()) else emptyList()
                        val replacement = existing.filterNot { it.issuer == account.issuer && it.accountName == account.accountName } + account
                        vault.saveWithAuthenticatedCipher(vault.newEncryptCipher(), replacement)
                        status = "Imported ${account.label} (${replacement.size} account${if (replacement.size == 1) "" else "s"} in locker)."
                        result = CryptoScreenSupport.succeeded(
                            As100TotpImportMethod,
                            context,
                            mapOf(
                                CryptoFields.VALUE to account.label,
                                CryptoFields.FORMAT to "RFC 6238 / otpauth TOTP account",
                                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                                    methodId = capabilityId,
                                    status = "succeeded",
                                    format = "RFC6238/otpauth",
                                    protection = "Android Keystore key gated by BIOMETRIC_STRONG",
                                    extra = mapOf("account_label" to account.label, "account_count" to replacement.size)
                                )
                            )
                        )
                    }.onFailure { result = CryptoScreenSupport.failed(As100TotpImportMethod, context, it) }
                },
                onError = { result = CryptoScreenSupport.failed(As100TotpImportMethod, context, IllegalStateException(it)) }
            )
        }

        VaultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; importNow() }) {
            CryptoGuidanceCard(
                goal = "Use MethodMesh as an authenticator app",
                explanation = "Add the TOTP secret supplied when a website or service asks you to set up an authenticator. The secret is encrypted locally and access is biometric-gated.",
                needs = "Either the authenticator setup link, or the account name and Base32 secret shown by the service.",
                result = "An account stored in the local biometric locker. The seed is never returned in MethodMesh provenance.",
                caution = "Treat the setup secret like a password. Anyone who copies it can generate the same login codes."
            )
            CryptoChoiceRow("Manual setup", entryMode == "manual", { entryMode = "manual" }, "Setup link", entryMode == "link", { entryMode = "link" })
            Spacer(Modifier.height(10.dp))
            if (entryMode == "manual") {
                OutlinedTextField(issuer, { issuer = it }, label = { Text("Service (optional)") }, supportingText = { Text("For example: GitHub, university VPN, bank.") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(accountName, { accountName = it }, label = { Text("Account name") }, supportingText = { Text("Usually your username or email address.") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(manualSecret, { manualSecret = it }, label = { Text("Authenticator secret") }, visualTransformation = PasswordVisualTransformation(), supportingText = { Text("Enter the Base32 secret exactly as provided; spaces are ignored.") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(value = otpAuth, onValueChange = { otpAuth = it }, label = { Text("Authenticator setup link") }, visualTransformation = PasswordVisualTransformation(), supportingText = { Text("Usually starts with otpauth://totp/.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = ::importNow, enabled = if (entryMode == "manual") accountName.isNotBlank() && manualSecret.isNotBlank() else otpAuth.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Unlock and save account") }
            if (status.isNotBlank()) { Spacer(Modifier.height(10.dp)); CryptoStatusCard("Authenticator account", "SAVED", status) }
        }
    }
}

object TotpDeleteCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TotpDeleteMethod.id
    override val title = "Remove an authenticator account"
    override val description = "Remove one saved TOTP account from the biometric-protected locker."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var label by rememberSaveable { mutableStateOf(context.action.settings["account_label"] ?: "") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun deleteNow() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                result = vaultUnavailableResult(As100TotpDeleteMethod, context); return
            }
            val vault = AndroidBiometricVault(androidContext)
            if (!vault.hasVault()) {
                result = CryptoScreenSupport.failed(As100TotpDeleteMethod, context, IllegalStateException("TOTP locker is empty.")); return
            }
            BiometricAuth.authenticate(
                androidContext,
                "Unlock TOTP locker",
                "Delete $label",
                onSuccess = {
                    runCatching {
                        val current = vault.loadWithAuthenticatedCipher(vault.newDecryptCipher())
                        val next = current.filterNot { it.label == label }
                        require(next.size < current.size) { "No TOTP account matched '$label'." }
                        vault.saveWithAuthenticatedCipher(vault.newEncryptCipher(), next)
                        result = CryptoScreenSupport.succeeded(
                            As100TotpDeleteMethod,
                            context,
                            mapOf(
                                CryptoFields.VALUE to label,
                                CryptoFields.FORMAT to "TOTP locker change",
                                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                                    capabilityId,
                                    "succeeded",
                                    format = "RFC6238 vault metadata",
                                    protection = "Android Keystore key gated by BIOMETRIC_STRONG",
                                    extra = mapOf("deleted_account_label" to label, "remaining_account_count" to next.size)
                                )
                            )
                        )
                    }.onFailure { result = CryptoScreenSupport.failed(As100TotpDeleteMethod, context, it) }
                },
                onError = { result = CryptoScreenSupport.failed(As100TotpDeleteMethod, context, IllegalStateException(it)) }
            )
        }

        VaultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; deleteNow() }) {
            CryptoGuidanceCard(
                goal = "Stop MethodMesh generating codes for an account",
                explanation = "Removing an account deletes its stored TOTP seed from the MethodMesh locker.",
                needs = "The exact account label shown in the Authenticator screen.",
                result = "That account removed from the local locker.",
                caution = "Make sure you have disabled two-factor authentication or have another recovery route before deleting your only authenticator copy."
            )
            OutlinedTextField(label, { label = it }, label = { Text("Account to remove") }, supportingText = { Text("Enter the label exactly as shown in the authenticator list.") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::deleteNow, enabled = label.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Unlock and remove account") }
        }
    }
}

object TotpExportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TotpExportMethod.id
    override val title = "Back up my authenticator"
    override val description = "Create a password-protected backup of all saved authenticator accounts."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var password by remember { mutableStateOf(context.action.settings["password"] ?: "") }
        var passwordConfirm by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun exportNow() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                result = vaultUnavailableResult(As100TotpExportMethod, context); return
            }
            if (password.isEmpty()) {
                result = CryptoScreenSupport.failed(As100TotpExportMethod, context, IllegalArgumentException("Backup password is required.")); return
            }
            if (password != passwordConfirm) {
                result = CryptoScreenSupport.failed(As100TotpExportMethod, context, IllegalArgumentException("The two backup password entries do not match.")); return
            }
            val vault = AndroidBiometricVault(androidContext)
            if (!vault.hasVault()) {
                result = CryptoScreenSupport.failed(As100TotpExportMethod, context, IllegalStateException("TOTP locker is empty.")); return
            }
            BiometricAuth.authenticate(
                androidContext,
                "Unlock TOTP locker",
                "Create encrypted backup",
                onSuccess = {
                    runCatching {
                        val accounts = vault.loadWithAuthenticatedCipher(vault.newDecryptCipher())
                        val clearJson = JSONObject().apply {
                            put("schema", "methodmesh.totp.backup.v2")
                            put("accounts", JSONArray().apply {
                                accounts.forEach { put(TotpEngine.toOtpAuth(it)) }
                            })
                        }.toString().toByteArray(Charsets.UTF_8)

                        val compact = JweEngine.encrypt(clearJson, password.toCharArray())
                        clearJson.fill(0)
                        val outBytes = compact.toByteArray(Charsets.US_ASCII)
                        val file = CryptoScreenSupport.cacheFile(androidContext, "methodmesh-totp-backup.jwe")
                        file.writeBytes(outBytes)
                        val outputSha = HashEngine.hex(outBytes)
                        result = CryptoScreenSupport.succeeded(
                            As100TotpExportMethod,
                            context,
                            mapOf(
                                CryptoFields.OUTPUT_URI to CryptoScreenSupport.shareableUri(androidContext, file),
                                CryptoFields.OUTPUT_FILENAME to file.name,
                                CryptoFields.OUTPUT_SHA256 to outputSha,
                                CryptoFields.FORMAT to "JWE Compact Serialization (.jwe)",
                                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                                    capabilityId,
                                    "succeeded",
                                    outputSha256 = outputSha,
                                    format = "JWE Compact Serialization",
                                    protection = "PBES2-HS256+A128KW / A256GCM",
                                    extra = mapOf("account_count" to accounts.size, "cleartext_export_returned" to false)
                                )
                            )
                        )
                        password = ""
                    }.onFailure { result = CryptoScreenSupport.failed(As100TotpExportMethod, context, it) }
                },
                onError = { result = CryptoScreenSupport.failed(As100TotpExportMethod, context, IllegalStateException(it)) }
            )
        }

        VaultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; exportNow() }) {
            CryptoGuidanceCard(
                goal = "Recover my authenticator after device loss",
                explanation = "MethodMesh unlocks the local vault in memory and immediately re-encrypts all account seeds into one portable password-protected backup.",
                needs = "A strong backup password that you can recover independently of this phone.",
                result = "One encrypted .jwe backup file. No plaintext seed export is produced.",
                caution = "Anyone with both this backup and its password can generate your authentication codes. Store them separately."
            )
            OutlinedTextField(password, { password = it }, label = { Text("Choose backup password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(passwordConfirm, { passwordConfirm = it }, label = { Text("Enter backup password again") }, visualTransformation = PasswordVisualTransformation(), isError = passwordConfirm.isNotEmpty() && password != passwordConfirm, supportingText = { if (passwordConfirm.isNotEmpty() && password != passwordConfirm) Text("Passwords do not match") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Button(onClick = ::exportNow, enabled = password.isNotEmpty() && password == passwordConfirm, modifier = Modifier.fillMaxWidth()) { Text("Unlock and create encrypted backup") }
        }
    }
}

