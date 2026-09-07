package com.example.methodmesh.modules.cryptography

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

@Composable
private fun PortableScaffold(
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

private class FileJweScreen(
    override val capabilityId: String,
    override val title: String,
    override val description: String,
    private val decrypting: Boolean
) : CapabilityScreenSpec {
    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var uriText by rememberSaveable { mutableStateOf(context.action.settings["input_uri"] ?: "") }
        var password by remember { mutableStateOf(context.action.settings["password"] ?: "") }
        var passwordConfirm by remember { mutableStateOf("") }
        var p2c by rememberSaveable { mutableStateOf(context.action.settings["p2c"] ?: JweEngine.DEFAULT_P2C.toString()) }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("") }

        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) uriText = uri.toString()
        }

        fun runCrypto() = runCatching {
            require(uriText.isNotBlank()) { "Input file URI is required." }
            require(password.isNotEmpty()) { "Password is required." }
            if (!decrypting) require(password == passwordConfirm) { "The two password entries do not match." }
            val uri = Uri.parse(uriText)
            val resolver = androidContext.contentResolver
            val sourceName = CryptoScreenSupport.sourceName(resolver, uri)
            val inputBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read input URI.")

            if (decrypting) {
                val compact = inputBytes.toString(Charsets.US_ASCII)
                inputBytes.fill(0)
                val clear = JweEngine.decrypt(compact, password.toCharArray())
                val outName = sourceName.removeSuffix(".jwe").ifBlank { "decrypted-file" }
                val file = CryptoScreenSupport.cacheFile(androidContext, outName).apply { writeBytes(clear) }
                clear.fill(0)
                status = "Decrypted ${sourceName}."
                result = CryptoScreenSupport.succeeded(
                    As100FileDecryptMethod,
                    context,
                    mapOf(
                        CryptoFields.OUTPUT_URI to CryptoScreenSupport.shareableUri(androidContext, file),
                        CryptoFields.OUTPUT_FILENAME to file.name,
                        CryptoFields.FORMAT to "decrypted file from JWE",
                        CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                            capabilityId,
                            "succeeded",
                            format = "JWE Compact Serialization",
                            protection = "PBES2-HS256+A128KW / A256GCM",
                            extra = mapOf("plaintext_hash_returned" to false)
                        )
                    )
                )
            } else {
                val compact = JweEngine.encrypt(
                    inputBytes,
                    password.toCharArray(),
                    p2c.toIntOrNull() ?: JweEngine.DEFAULT_P2C
                )
                inputBytes.fill(0)
                val outBytes = compact.toByteArray(Charsets.US_ASCII)
                val file = CryptoScreenSupport.cacheFile(androidContext, "$sourceName.jwe").apply { writeBytes(outBytes) }
                val outputSha = HashEngine.hex(outBytes)
                status = "Encrypted ${sourceName} as JWE."
                result = CryptoScreenSupport.succeeded(
                    As100FileEncryptMethod,
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
                            extra = mapOf(
                                "p2c" to (p2c.toIntOrNull() ?: JweEngine.DEFAULT_P2C),
                                "standalone_max_input_bytes" to JweEngine.MAX_INPUT_BYTES,
                                "plaintext_hash_returned" to false
                            )
                        )
                    )
                )
            }
        }.onSuccess {
            password = ""
        }.onFailure {
            result = CryptoScreenSupport.failed(
                if (decrypting) As100FileDecryptMethod else As100FileEncryptMethod,
                context,
                it
            )
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && uriText.isNotBlank() && password.isNotEmpty()) runCrypto()
        }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; runCrypto() }) {
            CryptoGuidanceCard(
                goal = if (decrypting) "Open a password-protected file" else "Keep a file private with a password",
                explanation = if (decrypting) "Choose the .jwe file and enter the password that was used to protect it." else "MethodMesh creates a portable encrypted .jwe file. The original file is not modified.",
                needs = if (decrypting) "The protected .jwe file and its password." else "One file up to ${JweEngine.MAX_INPUT_BYTES / (1024 * 1024)} MiB and a password you can recover later.",
                result = if (decrypting) "A decrypted copy you can save or share." else "A password-protected .jwe copy that can be decrypted with standards-compatible JOSE software.",
                caution = if (decrypting) "The decrypted copy is sensitive plaintext." else "There is no password recovery. Keep the password separate from the encrypted file."
            )
            Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (uriText.isBlank()) "Choose file" else "Choose a different file") }
            if (uriText.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("File selected", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text(if (decrypting) "Password" else "Choose a password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (!decrypting) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(passwordConfirm, { passwordConfirm = it }, label = { Text("Enter password again") }, visualTransformation = PasswordVisualTransformation(), isError = passwordConfirm.isNotEmpty() && password != passwordConfirm, supportingText = { if (passwordConfirm.isNotEmpty() && password != passwordConfirm) Text("Passwords do not match") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                CryptoExpertToggle(showExpert, { showExpert = it }) {
                    OutlinedTextField(p2c, { p2c = it.filter(Char::isDigit) }, label = { Text("Password hardening iterations") }, supportingText = { Text("Leave at the default unless interoperability requires otherwise.") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    Text("Format: compact JWE using PBES2-HS256+A128KW and A256GCM.", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = ::runCrypto, enabled = uriText.isNotBlank() && password.isNotEmpty() && (decrypting || (passwordConfirm.isNotEmpty() && password == passwordConfirm)), modifier = Modifier.fillMaxWidth()) {
                Text(if (decrypting) "Open protected file" else "Protect this file")
            }
            if (status.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(status, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

val FileEncryptCapabilityScreen: CapabilityScreenSpec = FileJweScreen(
    As100FileEncryptMethod.id,
    "Protect a file with a password",
    "Guided password protection for files up to the standalone compact-JWE size limit.",
    false
)
val FileDecryptCapabilityScreen: CapabilityScreenSpec = FileJweScreen(
    As100FileDecryptMethod.id,
    "Open a protected file",
    "Open a password-protected JWE file and produce a decrypted copy.",
    true
)

object KeyGenerateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100KeyGenerateMethod.id
    override val title = "Set up my signing identity"
    override val description = "Create or load one device-bound signing identity. The private key stays inside Android Keystore."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var alias by rememberSaveable { mutableStateOf(context.action.settings["key_alias"] ?: JwsIdentityEngine.DEFAULT_ALIAS) }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var jwk by rememberSaveable { mutableStateOf("") }
        var fingerprint by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun generate() = runCatching {
            jwk = JwsIdentityEngine.ensureIdentity(alias)
            fingerprint = JwsIdentityEngine.thumbprint(jwk)
            result = CryptoScreenSupport.succeeded(
                As100KeyGenerateMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to jwk,
                    CryptoFields.JWK to jwk,
                    CryptoFields.KEY_ALIAS to alias,
                    CryptoFields.FINGERPRINT to fingerprint,
                    CryptoFields.FORMAT to "RFC 7517 EC P-256 JWK",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId,
                        "succeeded",
                        format = "JWK",
                        protection = "Android Keystore device-bound private key",
                        keyFingerprints = listOf(fingerprint),
                        extra = mapOf("private_key_exported" to false, "jws_alg" to "ES256")
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100KeyGenerateMethod, context, it) }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; generate() }) {
            CryptoGuidanceCard(
                goal = "Create the key you use to sign things",
                explanation = "Signing lets another person check that exact content was signed by this device identity and has not changed since.",
                needs = "Nothing. MethodMesh creates the private key inside Android Keystore.",
                result = "A device-bound private signing key plus a public identity key/fingerprint you may share.",
                caution = "A signing identity proves control of this key. Connecting the key to a real person still requires a trusted handoff, identity card or institutional certification."
            )
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(alias, { alias = it }, label = { Text("Local key alias") }, modifier = Modifier.fillMaxWidth()) }
            val identityExists = runCatching { JwsIdentityEngine.hasIdentity(alias) }.getOrDefault(false)
            Button(onClick = ::generate, enabled = alias.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (identityExists) "Show my signing identity" else "Set up signing identity") }
            if (fingerprint.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Public fingerprint", fingerprint, "Safe to share. The private key has not left the device.") }
        }
    }
}

object IdentityExportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100IdentityExportMethod.id
    override val title = "Share my public identity"
    override val description = "Create a public identity card that binds a display name to this device signing key for QR/NFC or other transfer."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var displayName by rememberSaveable { mutableStateOf(context.action.settings["display_name"] ?: "") }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var jwk by rememberSaveable { mutableStateOf(context.action.settings["jwk"] ?: "") }
        var token by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun export() = runCatching {
            require(displayName.isNotBlank()) { "Display name is required." }
            if (jwk.isBlank()) jwk = JwsIdentityEngine.ensureIdentity()
            val fp = JwsIdentityEngine.thumbprint(jwk)
            token = CryptoJson.identityToken(displayName, jwk)
            result = CryptoScreenSupport.succeeded(
                As100IdentityExportMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to token,
                    CryptoFields.IDENTITY_TOKEN to token,
                    CryptoFields.JWK to jwk,
                    CryptoFields.FINGERPRINT to fp,
                    CryptoFields.FORMAT to "methodmesh.identity.jwk.v1 JSON",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId, "succeeded", format = "JWK identity token", keyFingerprints = listOf(fp),
                        extra = mapOf("private_material" to false)
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100IdentityExportMethod, context, it) }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; export() }) {
            CryptoGuidanceCard(
                goal = "Let someone recognise my signing key",
                explanation = "This creates a public identity card containing your display name, public signing key and fingerprint. It contains no private key.",
                needs = "The name you want shown on the identity card.",
                result = "A public token suitable for QR, NFC, messaging or a printed fingerprint.",
                caution = "The display name is a label, not independently verified identity. Trust comes from how the card/fingerprint is handed over or certified."
            )
            OutlinedTextField(displayName, { displayName = it }, label = { Text("Name shown on public identity") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(jwk, { jwk = it }, label = { Text("Use a different public JWK") }, supportingText = { Text("Leave blank to use this device's signing identity.") }, minLines = 3, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::export, enabled = displayName.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create public identity card") }
            if (token.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Public identity card ready", displayName, "The result contains the transferable identity token and fingerprint.") }
        }
    }
}

private fun readContentBytes(androidContext: android.content.Context, uriText: String, text: String): ByteArray {
    if (uriText.isNotBlank()) {
        return androidContext.contentResolver.openInputStream(Uri.parse(uriText))?.use { it.readBytes() }
            ?: error("Unable to read input URI.")
    }
    return text.toByteArray(Charsets.UTF_8)
}

object SignCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SignMethod.id
    override val title = "Sign a file or message"
    override val description = "Create a portable detached signature proving this device identity signed exact content."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        var text by remember { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var uriText by rememberSaveable { mutableStateOf(context.action.settings["input_uri"] ?: "") }
        var alias by rememberSaveable { mutableStateOf(context.action.settings["key_alias"] ?: JwsIdentityEngine.DEFAULT_ALIAS) }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var signature by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) uriText = uri.toString() }

        fun sign() = runCatching {
            val data = readContentBytes(androidContext, uriText, text)
            signature = JwsIdentityEngine.signDetached(data, alias)
            val jwk = JwsIdentityEngine.publicJwk(alias)
            val fp = JwsIdentityEngine.thumbprint(jwk)
            data.fill(0)
            result = CryptoScreenSupport.succeeded(
                As100SignMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to signature,
                    CryptoFields.SIGNATURE to signature,
                    CryptoFields.JWK to jwk,
                    CryptoFields.FINGERPRINT to fp,
                    CryptoFields.FORMAT to "RFC 7515 detached JWS / ES256",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId, "succeeded", format = "detached JWS", protection = "Android Keystore", keyFingerprints = listOf(fp)
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100SignMethod, context, it) }

        LaunchedEffect(context.startsImmediately) { if (context.startsImmediately && (text.isNotBlank() || uriText.isNotBlank())) sign() }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; sign() }) {
            CryptoGuidanceCard(
                goal = "Prove I signed exact content",
                explanation = "The content stays readable. MethodMesh creates a separate signature that fails verification if the content changes.",
                needs = "Text or a file. Your signing identity is created automatically on this device if needed.",
                result = "A detached signature plus your public key fingerprint.",
                caution = "Signing does not encrypt or hide the content."
            )
            OutlinedTextField(text, { text = it }, label = { Text("Message to sign (or choose a file)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (uriText.isBlank()) "Choose a file instead" else "File selected — choose another") }
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(alias, { alias = it }, label = { Text("Signing key alias") }, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::sign, enabled = text.isNotBlank() || uriText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Sign this content") }
            if (signature.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text("Signature created. The signature and public fingerprint are in the result.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

object VerifySignatureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100VerifySignatureMethod.id
    override val title = "Check a signature"
    override val description = "Check whether exact content matches a detached signature and, when supplied, a public identity card."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        var text by remember { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var uriText by rememberSaveable { mutableStateOf(context.action.settings["input_uri"] ?: "") }
        var signature by rememberSaveable { mutableStateOf(context.action.settings["signature"] ?: "") }
        var jwk by rememberSaveable { mutableStateOf(context.action.settings["jwk"] ?: "") }
        var expected by rememberSaveable { mutableStateOf(context.action.settings["expected_fingerprint"] ?: "") }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var verified by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) uriText = uri.toString() }

        fun verify() = runCatching {
            require(signature.isNotBlank()) { "Detached JWS is required." }
            require(jwk.isNotBlank()) { "A public identity card or public JWK is required." }
            val identity = CryptoJson.parsePublicIdentity(jwk)
            val data = readContentBytes(androidContext, uriText, text)
            val fp = JwsIdentityEngine.verifyDetached(data, signature, identity.jwk)
            data.fill(0)
            if (expected.isNotBlank()) require(fp == expected.trim()) { "Signature is valid but the signer fingerprint does not match the expected identity." }
            verified = if (identity.displayName != null) "Valid signature · identity card matches ${identity.displayName}" else "Valid signature · public key matched (identity label not supplied)"
            result = CryptoScreenSupport.succeeded(
                As100VerifySignatureMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to "valid",
                    CryptoFields.FINGERPRINT to fp,
                    CryptoFields.JWK to identity.jwk,
                    CryptoFields.FORMAT to "RFC 7515 detached JWS / ES256",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId, "succeeded", format = "detached JWS verification", keyFingerprints = listOf(fp),
                        extra = mapOf("expected_thumbprint_checked" to expected.isNotBlank(), "identity_token_used" to identity.fromIdentityToken, "identity_display_name" to identity.displayName)
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100VerifySignatureMethod, context, it) }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; verify() }) {
            CryptoGuidanceCard(
                goal = "Check content has not changed and which key signed it",
                explanation = "Verification checks the exact content against a separate signature. A MethodMesh public identity card can also provide the human-readable name associated with that key.",
                needs = "The original text/file, its detached signature, and the signer's public identity card or public key.",
                result = "A cryptographic valid/invalid result and the signing-key fingerprint.",
                caution = "A valid signature proves the key signed the content. A human name is trustworthy only to the extent that you trust how that identity card/key was obtained."
            )
            OutlinedTextField(text, { text = it }, label = { Text("Original message (or choose a file)") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = { picker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (uriText.isBlank()) "Choose original file instead" else "Original file selected") }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(signature, { signature = it }, label = { Text("Signature") }, supportingText = { Text("Paste the detached signature supplied with the content.") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(jwk, { jwk = it }, label = { Text("Public identity card or public key") }, supportingText = { Text("Paste/hand in the public identity token. Raw JWK is also accepted.") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(expected, { expected = it }, label = { Text("Expected fingerprint") }, supportingText = { Text("Optional pinning check when you already know the trusted fingerprint.") }, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::verify, enabled = signature.isNotBlank() && jwk.isNotBlank() && (text.isNotBlank() || uriText.isNotBlank()), modifier = Modifier.fillMaxWidth()) { Text("Check signature") }
            if (verified.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Signature check", "VALID", verified) }
        }
    }
}

object ChallengeRespondCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ChallengeRespondMethod.id
    override val title = "Prove I control my signing key"
    override val description = "Respond to a short-lived challenge using this device signing identity."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var challenge by rememberSaveable { mutableStateOf(context.action.settings["challenge"] ?: "") }
        var alias by rememberSaveable { mutableStateOf(context.action.settings["key_alias"] ?: JwsIdentityEngine.DEFAULT_ALIAS) }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var signature by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun respond() = runCatching {
            ChallengeEngine.validate(challenge)
            signature = JwsIdentityEngine.signDetached(challenge.toByteArray(Charsets.UTF_8), alias)
            val jwk = JwsIdentityEngine.publicJwk(alias)
            val fp = JwsIdentityEngine.thumbprint(jwk)
            result = CryptoScreenSupport.succeeded(
                As100ChallengeRespondMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to signature,
                    CryptoFields.SIGNATURE to signature,
                    CryptoFields.JWK to jwk,
                    CryptoFields.FINGERPRINT to fp,
                    CryptoFields.FORMAT to "ES256 detached JWS challenge response",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(capabilityId, "succeeded", format = "JWS challenge response", keyFingerprints = listOf(fp))
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100ChallengeRespondMethod, context, it) }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; respond() }) {
            CryptoGuidanceCard(
                goal = "Show that I control my key right now",
                explanation = "The verifier sends you a short-lived challenge. This device signs that exact challenge so it cannot be satisfied by replaying an old signature.",
                needs = "A current challenge from the verifier.",
                result = "A signed response plus this device's public key/fingerprint.",
                caution = "Read who the challenge is for before signing it. Expired challenges are rejected."
            )
            OutlinedTextField(challenge, { challenge = it }, label = { Text("Challenge from verifier") }, supportingText = { Text("Paste or hand in the challenge exactly as received.") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(alias, { alias = it }, label = { Text("Signing key alias") }, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::respond, enabled = challenge.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create signed response") }
            if (signature.isNotBlank()) { Spacer(Modifier.height(12.dp)); Text("Response ready to return to the verifier.", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

object ChallengeVerifyCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ChallengeVerifyMethod.id
    override val title = "Check a live identity proof"
    override val description = "Verify that a current challenge was signed by the expected key and has not expired."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var challenge by rememberSaveable { mutableStateOf(context.action.settings["challenge"] ?: "") }
        var signature by rememberSaveable { mutableStateOf(context.action.settings["signature"] ?: "") }
        var jwk by rememberSaveable { mutableStateOf(context.action.settings["jwk"] ?: "") }
        var audience by rememberSaveable { mutableStateOf(context.action.settings["audience"] ?: "") }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun verify() = runCatching {
            val identity = CryptoJson.parsePublicIdentity(jwk)
            val fp = JwsIdentityEngine.verifyDetached(challenge.toByteArray(Charsets.UTF_8), signature, identity.jwk)
            ChallengeEngine.validate(challenge, audience.ifBlank { null })
            status = if (identity.displayName != null) "Valid current response · ${identity.displayName} · $fp" else "Valid current response · $fp"
            result = CryptoScreenSupport.succeeded(
                As100ChallengeVerifyMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to "valid",
                    CryptoFields.FINGERPRINT to fp,
                    CryptoFields.FORMAT to "ES256 detached JWS challenge verification",
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId, "succeeded", format = "JWS challenge verification", keyFingerprints = listOf(fp),
                        extra = mapOf("audience_checked" to audience.isNotBlank())
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100ChallengeVerifyMethod, context, it) }

        PortableScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; verify() }) {
            CryptoGuidanceCard(
                goal = "Check someone controls the expected key now",
                explanation = "MethodMesh checks the challenge is still current and that the supplied key produced the response.",
                needs = "Your original challenge, the signed response, and the responder's public identity card/key.",
                result = "A current valid/invalid proof plus the responder key fingerprint.",
                caution = "This proves current key possession, not legal identity unless the public key was already trusted as belonging to that person."
            )
            OutlinedTextField(challenge, { challenge = it }, label = { Text("Original challenge") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(signature, { signature = it }, label = { Text("Signed response") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(jwk, { jwk = it }, label = { Text("Responder public identity card or key") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(audience, { audience = it }, label = { Text("Expected audience") }, supportingText = { Text("Optional extra check that this challenge was intended for a particular verifier.") }, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::verify, enabled = challenge.isNotBlank() && signature.isNotBlank() && jwk.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Check live proof") }
            if (status.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Live proof", "VALID", status) }
        }
    }
}
