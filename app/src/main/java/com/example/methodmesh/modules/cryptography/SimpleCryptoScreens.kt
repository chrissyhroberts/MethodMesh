package com.example.methodmesh.modules.cryptography

import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

@Composable
private fun ResultScaffold(
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

object PasswordGenerateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100PasswordGenerateMethod.id
    override val title = "Create a secure password or code"
    override val description = "Generate a strong password, memorable passphrase, PIN or machine token without needing to understand cryptographic algorithms."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var mode by rememberSaveable { mutableStateOf(context.action.settings["mode"] ?: "random") }
        var length by rememberSaveable { mutableStateOf(context.action.settings["length"] ?: "20") }
        var words by rememberSaveable { mutableStateOf(context.action.settings["words"] ?: "6") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var value by remember { mutableStateOf("") }
        var entropy by rememberSaveable { mutableStateOf("") }

        fun generate() = runCatching {
            val generated = when (mode.lowercase()) {
                "pin" -> PasswordEngine.pin(length.toIntOrNull() ?: 8)
                "hex" -> PasswordEngine.hex(length.toIntOrNull() ?: 32)
                "token", "base64url" -> PasswordEngine.token(length.toIntOrNull() ?: 32)
                "phrase", "syllable_phrase" -> PasswordEngine.syllablePhrase(words.toIntOrNull() ?: 6)
                "alphanumeric" -> PasswordEngine.random(length.toIntOrNull() ?: 20, includeSymbols = false)
                else -> PasswordEngine.random(length.toIntOrNull() ?: 20, includeSymbols = true)
            }
            value = generated.value
            entropy = "%.1f".format(generated.entropyBits)
            val values = mapOf(
                CryptoFields.VALUE to generated.value,
                CryptoFields.FORMAT to generated.mode,
                CryptoFields.ENTROPY_BITS to entropy,
                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                    methodId = capabilityId,
                    status = "succeeded",
                    format = generated.mode,
                    extra = mapOf("entropy_bits" to generated.entropyBits)
                )
            )
            result = CryptoScreenSupport.succeeded(As100PasswordGenerateMethod, context, values)
        }.onFailure { result = CryptoScreenSupport.failed(As100PasswordGenerateMethod, context, it) }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && context.presentationMode == CapabilityPresentationMode.IntentLaunch) {
                generate()
            }
        }

        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; generate() }) {
            CryptoGuidanceCard(
                goal = "Choose what the secret is for",
                explanation = "MethodMesh uses the device cryptographic random source. Pick a human-friendly option rather than an algorithm name.",
                needs = "Only the kind of secret you want.",
                result = "A new secret that exists only in this result unless you save it.",
                caution = "Do not generate a password for something unless you have somewhere appropriate to store it."
            )
            CryptoChoiceRow("Password", mode == "random", { mode = "random" }, "Passphrase", mode in setOf("phrase", "syllable_phrase"), { mode = "phrase" })
            Spacer(Modifier.height(8.dp))
            CryptoChoiceRow("PIN", mode == "pin", { mode = "pin" }, "Machine token", mode in setOf("token", "base64url", "hex"), { mode = "token" })
            Spacer(Modifier.height(10.dp))
            if (mode.lowercase() in setOf("phrase", "syllable_phrase")) {
                OutlinedTextField(words, { words = it.filter(Char::isDigit) }, label = { Text("Number of words") }, supportingText = { Text("Six is a good general default.") }, modifier = Modifier.fillMaxWidth())
            } else {
                OutlinedTextField(length, { length = it.filter(Char::isDigit) }, label = { Text(if (mode == "token") "Random bytes" else if (mode == "pin") "PIN digits" else "Characters") }, modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::generate, modifier = Modifier.fillMaxWidth()) { Text("Create new ${if (mode == "pin") "PIN" else if (mode == "phrase") "passphrase" else if (mode == "token") "token" else "password"}") }
            if (value.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                CryptoStatusCard("New secret", value, "Estimated entropy: $entropy bits")
            }
        }
    }
}

object HashCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100HashMethod.id
    override val title = "Create a content fingerprint"
    override val description = "Create a reproducible fingerprint for exact text or file bytes. This checks identity/integrity; it does not hide the content."

    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val resolver = androidContext.contentResolver
        var text by rememberSaveable { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var algorithm by rememberSaveable { mutableStateOf(context.action.settings["algorithm"] ?: "SHA-256") }
        var uriText by rememberSaveable { mutableStateOf(context.action.settings["input_uri"] ?: "") }
        var sourceName by rememberSaveable { mutableStateOf("") }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uriText = uri?.toString().orEmpty(); sourceName = uri?.let { CryptoScreenSupport.sourceName(resolver, it) }.orEmpty()
        }

        fun runHash() = runCatching {
            val sourceSha: String
            val digest: String
            if (uriText.isNotBlank()) {
                val uri = Uri.parse(uriText)
                digest = resolver.openInputStream(uri).use { HashEngine.hex(requireNotNull(it), algorithm) }
                sourceSha = if (algorithm == "SHA-256") digest else resolver.openInputStream(uri).use { HashEngine.hex(requireNotNull(it), "SHA-256") }
            } else {
                val bytes = text.toByteArray(Charsets.UTF_8)
                require(bytes.isNotEmpty()) { "No content supplied." }
                digest = HashEngine.hex(bytes, algorithm)
                sourceSha = HashEngine.hex(bytes)
            }
            val values = mapOf(
                CryptoFields.VALUE to digest,
                CryptoFields.FORMAT to algorithm,
                CryptoFields.SOURCE_SHA256 to sourceSha,
                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(capabilityId, sourceSha256 = sourceSha, format = algorithm, status = "succeeded")
            )
            result = CryptoScreenSupport.succeeded(As100HashMethod, context, values)
        }.onFailure { result = CryptoScreenSupport.failed(As100HashMethod, context, it) }

        LaunchedEffect(context.startsImmediately, text, uriText) {
            if (context.startsImmediately && (text.isNotEmpty() || uriText.isNotBlank())) runHash()
        }

        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; runHash() }) {
            CryptoGuidanceCard(
                goal = "Check whether content is exactly the same",
                explanation = "A fingerprint changes when the underlying bytes change. It is useful for integrity checks and provenance, but anyone with the content can calculate the same fingerprint.",
                needs = "Text or one file.",
                result = "A SHA fingerprint you can compare elsewhere.",
                caution = "A fingerprint is not encryption and does not prove who created the content."
            )
            if (context.settingShouldBeShown("input_text")) OutlinedTextField(text, { text = it }, label = { Text("Text to fingerprint") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (sourceName.isBlank()) "Choose a file instead" else "File: $sourceName") }
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) {
                OutlinedTextField(algorithm, { algorithm = it }, label = { Text("Digest algorithm") }, supportingText = { Text("Default: SHA-256. Use SHA-512 only when another system requires it.") }, modifier = Modifier.fillMaxWidth())
            }
            Button(onClick = ::runHash, enabled = text.isNotBlank() || uriText.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create fingerprint") }
        }
    }
}

private class TextJweScreen(
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
        var input by remember { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var password by remember { mutableStateOf(context.action.settings["password"] ?: "") }
        var passwordConfirm by remember { mutableStateOf("") }
        var p2c by rememberSaveable { mutableStateOf(context.action.settings["p2c"] ?: JweEngine.DEFAULT_P2C.toString()) }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var output by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun runCrypto() = runCatching {
            require(password.isNotEmpty()) { "Password is required." }
            if (!decrypting) require(password == passwordConfirm) { "The two password entries do not match." }
            if (decrypting) {
                val clear = JweEngine.decrypt(input, password.toCharArray())
                output = clear.toString(Charsets.UTF_8)
                result = CryptoScreenSupport.succeeded(
                    As100TextDecryptMethod,
                    context,
                    mapOf(
                        CryptoFields.VALUE to output,
                        CryptoFields.FORMAT to "UTF-8 plaintext from JWE",
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
                    input.toByteArray(Charsets.UTF_8),
                    password.toCharArray(),
                    p2c.toIntOrNull() ?: JweEngine.DEFAULT_P2C
                )
                output = compact
                val outputSha = HashEngine.hex(compact.toByteArray(Charsets.US_ASCII))
                result = CryptoScreenSupport.succeeded(
                    As100TextEncryptMethod,
                    context,
                    mapOf(
                        CryptoFields.VALUE to compact,
                        CryptoFields.OUTPUT_SHA256 to outputSha,
                        CryptoFields.FORMAT to "JWE Compact Serialization",
                        CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                            capabilityId,
                            "succeeded",
                            outputSha256 = outputSha,
                            format = "JWE Compact Serialization",
                            protection = "PBES2-HS256+A128KW / A256GCM",
                            extra = mapOf(
                                "p2c" to (p2c.toIntOrNull() ?: JweEngine.DEFAULT_P2C),
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
                if (decrypting) As100TextDecryptMethod else As100TextEncryptMethod,
                context,
                it
            )
        }

        LaunchedEffect(context.startsImmediately) {
            if (context.startsImmediately && input.isNotBlank() && password.isNotEmpty()) runCrypto()
        }

        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; runCrypto() }) {
            CryptoGuidanceCard(
                goal = if (decrypting) "Open protected text" else "Keep text private with a password",
                explanation = if (decrypting) "Use this when someone has given you MethodMesh/JWE protected text and the password separately." else "Use this for short sensitive values such as identifiers, names, notes or small form fields that need to leave the device encrypted.",
                needs = if (decrypting) "The protected text and the exact password used to protect it." else "The text and a password you can recover later.",
                result = if (decrypting) "The original plaintext." else "Portable protected text that can be decrypted by standards-compatible JOSE software.",
                caution = if (decrypting) "Only decrypt in a place where displaying the plaintext is appropriate." else "There is no password recovery. Store the password separately from the encrypted data."
            )
            OutlinedTextField(
                input,
                { input = it },
                label = { Text(if (decrypting) "Protected text" else "Text to protect") },
                supportingText = { Text(if (decrypting) "Usually a long value with five dot-separated parts." else "The plaintext is not included in MethodMesh provenance.") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(password, { password = it }, label = { Text(if (decrypting) "Password" else "Choose a password") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            if (!decrypting) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(passwordConfirm, { passwordConfirm = it }, label = { Text("Enter password again") }, visualTransformation = PasswordVisualTransformation(), isError = passwordConfirm.isNotEmpty() && password != passwordConfirm, supportingText = { if (passwordConfirm.isNotEmpty() && password != passwordConfirm) Text("Passwords do not match") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                CryptoExpertToggle(showExpert, { showExpert = it }) {
                    OutlinedTextField(p2c, { p2c = it.filter(Char::isDigit) }, label = { Text("Password hardening iterations") }, supportingText = { Text("Leave this at the default unless another system requires a specific value.") }, modifier = Modifier.fillMaxWidth())
                }
            }
            Button(
                onClick = ::runCrypto,
                enabled = input.isNotBlank() && password.isNotEmpty() && (decrypting || (passwordConfirm.isNotEmpty() && password == passwordConfirm)),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (decrypting) "Open protected text" else "Protect this text") }
            if (output.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                CryptoStatusCard(if (decrypting) "Decrypted content" else "Protected text ready", output, if (decrypting) "Treat this as sensitive plaintext." else "Share/store this ciphertext; keep the password elsewhere.")
            }
        }
    }
}

val TextEncryptCapabilityScreen: CapabilityScreenSpec = TextJweScreen(
    As100TextEncryptMethod.id,
    "Protect text with a password",
    "Guided password encryption for short text, with portable JWE output and expert details hidden by default.",
    false
)
val TextDecryptCapabilityScreen: CapabilityScreenSpec = TextJweScreen(
    As100TextDecryptMethod.id,
    "Open protected text",
    "Open password-protected JWE text and return the original plaintext.",
    true
)

object SecretSplitCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SecretSplitMethod.id
    override val title = "Create recovery shares"
    override val description = "Split one important secret into several recovery pieces so no single holder has the whole secret."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var secret by remember { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var threshold by rememberSaveable { mutableStateOf(context.action.settings["threshold"] ?: "3") }
        var shareCount by rememberSaveable { mutableStateOf(context.action.settings["share_count"] ?: "5") }
        var sharesText by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        fun split() = runCatching {
            val shares = ShamirEngine.split(secret.toByteArray(Charsets.UTF_8), threshold.toInt(), shareCount.toInt()).map(ShamirShare::encode)
            sharesText = shares.joinToString("\n")
            result = CryptoScreenSupport.succeeded(As100SecretSplitMethod, context, mapOf(
                CryptoFields.VALUE to sharesText,
                CryptoFields.FORMAT to "Shamir GF(256); MethodMesh share transport mms1",
                CryptoFields.THRESHOLD to threshold,
                CryptoFields.SHARE_COUNT to shareCount,
                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(capabilityId, "succeeded", format = "Shamir GF(256)", extra = mapOf("threshold" to threshold.toInt(), "share_count" to shareCount.toInt()))
            ))
        }.onFailure { result = CryptoScreenSupport.failed(As100SecretSplitMethod, context, it) }
        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; split() }) {
            CryptoGuidanceCard(
                goal = "Make recovery require several people or places",
                explanation = "The original secret is divided into independent-looking pieces. A chosen minimum number of pieces can reconstruct it.",
                needs = "One secret, how many recovery pieces to create, and how many should be required.",
                result = "Several mms1 recovery shares to distribute separately.",
                caution = "Each share is sensitive. Do not store all shares together, and test your recovery plan before relying on it."
            )
            OutlinedTextField(secret, { secret = it }, label = { Text("Secret to protect") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit) }, label = { Text("Pieces required to recover") }, supportingText = { Text("Example: 3 means any three valid pieces can recover the secret.") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(shareCount, { shareCount = it.filter(Char::isDigit) }, label = { Text("Total pieces to create") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::split, enabled = secret.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Create recovery pieces") }
            if (sharesText.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Recovery pieces ready", sharesText, "Distribute these separately. The original secret is not returned in provenance.") }
        }
    }
}

object SecretCombineCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100SecretCombineMethod.id
    override val title = "Recover a shared secret"
    override val description = "Reconstruct a secret from enough valid recovery pieces."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var sharesText by remember { mutableStateOf(context.action.settings["input_text"] ?: "") }
        var recovered by remember { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        fun combine() = runCatching {
            val shares = sharesText.lines().map(String::trim).filter(String::isNotBlank).map(ShamirShare::decode)
            recovered = ShamirEngine.combine(shares).toString(Charsets.UTF_8)
            result = CryptoScreenSupport.succeeded(As100SecretCombineMethod, context, mapOf(
                CryptoFields.VALUE to recovered,
                CryptoFields.FORMAT to "UTF-8 recovered secret",
                CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(capabilityId, "succeeded", format = "Shamir GF(256)", extra = mapOf("shares_supplied" to shares.size))
            ))
        }.onFailure { result = CryptoScreenSupport.failed(As100SecretCombineMethod, context, it) }
        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; combine() }) {
            CryptoGuidanceCard(
                goal = "Recover the original secret",
                explanation = "Paste the recovery pieces you have. MethodMesh will reconstruct the secret only when the threshold encoded in the shares is satisfied.",
                needs = "Enough valid mms1 recovery pieces from the same set.",
                result = "The original secret in memory and in the main result.",
                caution = "Recover secrets only on a trusted device and clear/copy them carefully afterwards."
            )
            val supplied = sharesText.lines().count { it.trim().isNotBlank() }
            OutlinedTextField(sharesText, { sharesText = it }, label = { Text("Recovery pieces — one per line") }, supportingText = { Text("$supplied piece${if (supplied == 1) "" else "s"} entered") }, minLines = 5, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::combine, enabled = supplied >= 2, modifier = Modifier.fillMaxWidth()) { Text("Recover the secret") }
            if (recovered.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Secret recovered", recovered, "This is sensitive plaintext.") }
        }
    }
}

object ChallengeCreateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ChallengeCreateMethod.id
    override val title = "Create a live identity challenge"
    override val description = "Ask another device/key holder to prove they control a signing key right now, rather than merely showing an old signature."
    @Composable override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        var audience by rememberSaveable { mutableStateOf(context.action.settings["audience"] ?: "") }
        var ttl by rememberSaveable { mutableStateOf(context.action.settings["ttl_seconds"] ?: "300") }
        var showExpert by rememberSaveable { mutableStateOf(false) }
        var challenge by rememberSaveable { mutableStateOf("") }; var result by remember { mutableStateOf<ExecutionResult?>(null) }
        fun create() = runCatching {
            challenge = ChallengeEngine.create(audience, ttl.toLong())
            result = CryptoScreenSupport.succeeded(As100ChallengeCreateMethod, context, mapOf(CryptoFields.VALUE to challenge, CryptoFields.FORMAT to "methodmesh.challenge.v1", CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(capabilityId, "succeeded", format = "JSON challenge")))
        }.onFailure { result = CryptoScreenSupport.failed(As100ChallengeCreateMethod, context, it) }
        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null; create() }) {
            CryptoGuidanceCard(
                goal = "Prove someone controls a key now",
                explanation = "A short-lived random challenge prevents someone from satisfying the check by replaying an old signed message.",
                needs = "Optionally, the name of the person/system that will verify the response.",
                result = "A short-lived challenge to send to the person proving possession of their key.",
                caution = "This proves control of a key, not the person's legal identity unless you already trust the key-to-person link."
            )
            OutlinedTextField(audience, { audience = it }, label = { Text("Who is checking this? (optional)") }, supportingText = { Text("For example: 'Field supervisor tablet' or an organisation name.") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text("How long should the challenge work?", style = MaterialTheme.typography.labelLarge)
            CryptoChoiceRow("2 minutes", ttl == "120", { ttl = "120" }, "5 minutes", ttl == "300", { ttl = "300" })
            Spacer(Modifier.height(8.dp))
            CryptoChoiceRow("15 minutes", ttl == "900", { ttl = "900" }, "30 minutes", ttl == "1800", { ttl = "1800" })
            Spacer(Modifier.height(8.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) { OutlinedTextField(ttl, { ttl = it.filter(Char::isDigit) }, label = { Text("Validity seconds") }, modifier = Modifier.fillMaxWidth()) }
            Button(onClick = ::create, modifier = Modifier.fillMaxWidth()) { Text("Create live challenge") }
            if (challenge.isNotBlank()) { Spacer(Modifier.height(12.dp)); CryptoStatusCard("Challenge ready", challenge, "Send this to the person/device that will respond.") }
        }
    }
}

object TotpGenerateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TotpGenerateMethod.id
    override val title = "Authenticator codes"
    override val description = "Unlock saved authenticator accounts and generate current TOTP login codes. Manual secrets remain available as an expert path."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val androidContext = LocalContext.current
        var secret by remember { mutableStateOf(context.action.settings["secret"] ?: "") }
        var digits by rememberSaveable { mutableStateOf(context.action.settings["digits"] ?: "6") }
        var period by rememberSaveable { mutableStateOf(context.action.settings["period"] ?: "30") }
        var algorithm by rememberSaveable { mutableStateOf(context.action.settings["algorithm"] ?: "SHA1") }
        var showExpert by rememberSaveable { mutableStateOf(secret.isNotBlank()) }
        var code by remember { mutableStateOf("") }
        var remaining by rememberSaveable { mutableStateOf("") }
        var selectedLabel by rememberSaveable { mutableStateOf("") }
        var lockerAccounts by remember { mutableStateOf<List<TotpAccount>>(emptyList()) }
        var lockerMessage by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun makeResult(account: TotpAccount, label: String) = runCatching {
            val generated = TotpEngine.generate(account)
            code = generated.code
            remaining = generated.validForSeconds.toString()
            selectedLabel = label
            result = CryptoScreenSupport.succeeded(
                As100TotpGenerateMethod,
                context,
                mapOf(
                    CryptoFields.VALUE to code,
                    CryptoFields.FORMAT to "RFC 6238 TOTP",
                    CryptoFields.TOTP_VALID_FOR_SECONDS to remaining,
                    CryptoFields.PROVENANCE_JSON to CryptoJson.provenance(
                        capabilityId,
                        "succeeded",
                        format = "RFC6238",
                        extra = mapOf(
                            "account_label" to label,
                            "digits" to account.digits,
                            "period_seconds" to account.periodSeconds,
                            "algorithm" to account.algorithm,
                            "secret_returned" to false
                        )
                    )
                )
            )
        }.onFailure { result = CryptoScreenSupport.failed(As100TotpGenerateMethod, context, it) }

        fun generateExplicit() {
            val account = TotpAccount("", "explicit", secret, digits.toInt(), period.toInt(), algorithm)
            makeResult(account, "explicit secret")
        }

        fun unlockLocker() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                lockerMessage = "Biometric TOTP locker requires Android 11 or later."
                return
            }
            val vault = AndroidBiometricVault(androidContext)
            if (!vault.hasVault()) {
                lockerMessage = "TOTP locker is empty. Import an otpauth:// account first."
                return
            }
            BiometricAuth.authenticate(
                androidContext,
                "Unlock TOTP locker",
                "Show one-time codes",
                onSuccess = {
                    runCatching {
                        lockerAccounts = vault.loadWithAuthenticatedCipher(vault.newDecryptCipher())
                        lockerMessage = "Unlocked ${lockerAccounts.size} account${if (lockerAccounts.size == 1) "" else "s"}."
                    }.onFailure { lockerMessage = it.message ?: "Unable to unlock TOTP locker." }
                },
                onError = { lockerMessage = it }
            )
        }

        LaunchedEffect(context.startsImmediately, secret) {
            if (context.startsImmediately && secret.isNotBlank()) generateExplicit()
        }

        ResultScaffold(title, capabilityId, context, result, onBack, onConfirmed, onCancel, { result = null }) {
            CryptoGuidanceCard(
                goal = "Get the code a website is asking for",
                explanation = "Unlock your local authenticator locker, then choose the account. MethodMesh generates the current time-based code without returning the stored seed.",
                needs = "A previously saved authenticator account and your device biometric.",
                result = "A short-lived login code and its remaining validity time.",
                caution = "Never send an authenticator code to someone who contacted you unexpectedly. These codes are for the service you are actively logging into."
            )
            Button(onClick = ::unlockLocker, modifier = Modifier.fillMaxWidth()) { Text("Unlock my authenticator") }
            if (lockerMessage.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(lockerMessage, style = MaterialTheme.typography.bodySmall) }
            lockerAccounts.forEach { account ->
                val generated = runCatching { TotpEngine.generate(account) }.getOrNull()
                if (generated != null) {
                    Spacer(Modifier.height(10.dp))
                    CryptoStatusCard(account.label, generated.code, "Valid for ${generated.validForSeconds} seconds")
                    OutlinedButton(onClick = { makeResult(account, account.label) }, modifier = Modifier.fillMaxWidth()) { Text("Use this code") }
                }
            }
            Spacer(Modifier.height(10.dp))
            CryptoExpertToggle(showExpert, { showExpert = it }) {
                Text("Generate from a one-off secret", style = MaterialTheme.typography.titleSmall)
                Text("Use this only when a workflow explicitly supplies a TOTP secret instead of storing an account in the locker.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                if (context.settingShouldBeShown("secret")) {
                    OutlinedTextField(secret, { secret = it }, label = { Text("Base32 secret") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(digits, { digits = it.filter(Char::isDigit) }, label = { Text("Code digits") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(period, { period = it.filter(Char::isDigit) }, label = { Text("Period seconds") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(algorithm, { algorithm = it }, label = { Text("HMAC algorithm") }, supportingText = { Text("Usually SHA1; use another value only if the service specifies it.") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Button(onClick = ::generateExplicit, enabled = secret.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Generate one-off code") }
            }
            if (code.isNotBlank()) { Spacer(Modifier.height(10.dp)); CryptoStatusCard("Current code", code, "$selectedLabel · valid for $remaining seconds") }
        }
    }
}

