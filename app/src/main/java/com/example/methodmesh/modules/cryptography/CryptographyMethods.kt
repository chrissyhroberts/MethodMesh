package com.example.methodmesh.modules.cryptography

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState

object CryptoFields {
    const val STATUS = "crypto_status"
    const val VALUE = "crypto_value"
    const val FORMAT = "crypto_format"
    const val SOURCE_SHA256 = "crypto_source_sha256"
    const val OUTPUT_SHA256 = "crypto_output_sha256"
    const val OUTPUT_URI = "crypto_output_uri"
    const val OUTPUT_FILENAME = "crypto_output_filename"
    const val FINGERPRINT = "crypto_fingerprint"
    const val JWK = "crypto_jwk"
    const val KEY_ALIAS = "crypto_key_alias"
    const val IDENTITY_TOKEN = "crypto_identity_token"
    const val SIGNATURE_URI = "crypto_signature_uri"
    const val SIGNATURE = "crypto_signature"
    const val ENTROPY_BITS = "crypto_entropy_bits"
    const val TOTP_VALID_FOR_SECONDS = "crypto_totp_valid_for_seconds"
    const val THRESHOLD = "crypto_threshold"
    const val SHARE_COUNT = "crypto_share_count"
    const val PROVENANCE_JSON = "crypto_provenance_json"
    const val ERROR = "crypto_error"

    val common = listOf(
        STATUS, VALUE, FORMAT, SOURCE_SHA256, OUTPUT_SHA256,
        OUTPUT_URI, OUTPUT_FILENAME, FINGERPRINT, JWK, KEY_ALIAS, IDENTITY_TOKEN,
        SIGNATURE_URI, SIGNATURE, ENTROPY_BITS, TOTP_VALID_FOR_SECONDS,
        THRESHOLD, SHARE_COUNT, PROVENANCE_JSON, ERROR
    )
}

abstract class AbstractCryptoMethod(
    final override val id: String,
    private val methodName: String,
    private val methodDescription: String,
    private val version: String = "0.3.1"
) : As100Method {
    final override val ref = ArchitectureRef(ArchitectureId(id), "Method", methodName)

    final override val descriptor = MethodDescriptor(
        id = ArchitectureId(id),
        methodType = MethodObjectType.Calculation,
        name = methodName,
        version = version,
        description = methodDescription,
        outputs = CryptoFields.common,
        graphOutputs = listOf(id),
        parameters = mapOf(
            "category" to "Cryptography",
            "status" to "Development",
            "dependency_policy" to "standalone_no_new_host_dependencies"
        )
    )

    final override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    final override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ) = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult = result(
        request,
        mapOf(
            CryptoFields.STATUS to "failed",
            CryptoFields.ERROR to "$methodName requires its capability screen or a composed runtime adapter."
        ),
        InvocationContext.from(request.context)
    )

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val succeeded = values[CryptoFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.cryptography", id, version)
        val observation = Observation(
            phenomenon = id,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val entity = Entity(
            ArchitectureId("crypto-result:${System.currentTimeMillis()}"),
            "CryptographyResult",
            temporalContext = request.temporalContext
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (succeeded) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (succeeded) emptyMap() else mapOf(CryptoFields.ERROR to values[CryptoFields.ERROR].orEmpty())
        ).withInvocationContext(invocation)
    }
}

object As100PasswordGenerateMethod : AbstractCryptoMethod(
    "crypto.password.generate", "Create secure secret", "Generate a cryptographically random password, PIN, token or human-enterable syllable phrase."
)
object As100HashMethod : AbstractCryptoMethod(
    "crypto.hash", "Create content fingerprint", "Calculate a SHA-256 or SHA-512 digest of exact text or file bytes."
)
object As100TextEncryptMethod : AbstractCryptoMethod(
    "crypto.text.encrypt", "Protect text with password", "Encrypt UTF-8 text as password-protected JWE Compact Serialization (PBES2-HS256+A128KW / A256GCM)."
)
object As100TextDecryptMethod : AbstractCryptoMethod(
    "crypto.text.decrypt", "Open protected text", "Decrypt password-protected JWE Compact Serialization."
)
object As100FileEncryptMethod : AbstractCryptoMethod(
    "crypto.file.encrypt", "Protect file with password", "Encrypt a small/medium file as standard JWE Compact Serialization without external libraries."
)
object As100FileDecryptMethod : AbstractCryptoMethod(
    "crypto.file.decrypt", "Open protected file", "Decrypt a standard password-protected JWE file."
)
object As100KeyGenerateMethod : AbstractCryptoMethod(
    "crypto.key.generate", "Generate signing identity", "Generate a device-bound P-256 Android Keystore signing identity and export its public JWK."
)
object As100IdentityExportMethod : AbstractCryptoMethod(
    "crypto.identity.export", "Share public identity", "Create a QR/NFC-safe identity token binding a display name to a public JWK thumbprint."
)
object As100SignMethod : AbstractCryptoMethod(
    "crypto.sign", "Sign a file or message", "Create an ES256 detached JWS over exact text or file bytes using the device-bound signing identity."
)
object As100VerifySignatureMethod : AbstractCryptoMethod(
    "crypto.signature.verify", "Check a signature", "Verify an ES256 detached JWS using a public P-256 JWK."
)
object As100SecretSplitMethod : AbstractCryptoMethod(
    "crypto.secret.split", "Create recovery shares", "Split a secret into threshold Shamir shares."
)
object As100SecretCombineMethod : AbstractCryptoMethod(
    "crypto.secret.combine", "Recover shared secret", "Recover a Shamir-shared secret when the threshold is met."
)
object As100ChallengeCreateMethod : AbstractCryptoMethod(
    "crypto.challenge.create", "Create live identity challenge", "Create an expiring cryptographic challenge for proof-of-key-possession."
)
object As100ChallengeRespondMethod : AbstractCryptoMethod(
    "crypto.challenge.respond", "Prove key possession", "Sign an expiring challenge with the device-bound ES256 identity."
)
object As100ChallengeVerifyMethod : AbstractCryptoMethod(
    "crypto.challenge.verify", "Check live identity proof", "Verify an ES256-signed challenge and its validity window using a public JWK."
)
object As100TotpImportMethod : AbstractCryptoMethod(
    "otp.totp.import", "Import TOTP account", "Import an otpauth:// TOTP account into the biometric-protected local vault."
)
object As100TotpGenerateMethod : AbstractCryptoMethod(
    "otp.totp.generate", "Generate TOTP code", "Generate an RFC 6238 code from an explicit secret or unlocked local account."
)
object As100TotpDeleteMethod : AbstractCryptoMethod(
    "otp.totp.delete", "Delete TOTP account", "Delete an account from the biometric-protected local TOTP vault."
)
object As100TotpExportMethod : AbstractCryptoMethod(
    "otp.totp.export", "Export TOTP backup", "Export TOTP accounts only as password-protected standard JWE."
)
object As100CryptoDashboardMethod : AbstractCryptoMethod(
    "crypto.dashboard", "Cryptography dashboard", "Return a non-secret snapshot of standalone crypto and local identity/vault status."
)
