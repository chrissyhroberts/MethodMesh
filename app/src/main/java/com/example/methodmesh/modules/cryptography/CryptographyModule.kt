package com.example.methodmesh.modules.cryptography

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.settings.MethodSetting

/**
 * Standalone MethodMesh cryptography module.
 *
 * Golden rule: adding/removing this folder must not require changes outside the folder.
 * It therefore introduces no Maven/Gradle dependency and uses only MethodMesh's existing
 * platform/UI contract plus Android/JDK cryptographic APIs.
 */
object CryptographyModule : MethodMeshModule {
    override val moduleId = "cryptography"
    override val displayName = "Cryptography"
    override val summary = "Guided privacy, signing, verification, authenticator and recovery tools with expert details kept out of the normal path."

    override fun as100Methods() = listOf(
        As100PasswordGenerateMethod,
        As100HashMethod,
        As100TextEncryptMethod,
        As100TextDecryptMethod,
        As100FileEncryptMethod,
        As100FileDecryptMethod,
        As100KeyGenerateMethod,
        As100IdentityExportMethod,
        As100SignMethod,
        As100VerifySignatureMethod,
        As100SecretSplitMethod,
        As100SecretCombineMethod,
        As100ChallengeCreateMethod,
        As100ChallengeRespondMethod,
        As100ChallengeVerifyMethod,
        As100TotpImportMethod,
        As100TotpGenerateMethod,
        As100TotpDeleteMethod,
        As100TotpExportMethod,
        As100CryptoDashboardMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("create password", As100PasswordGenerateMethod.id, "Create a cryptographically random password or passphrase"),
        RilBinding("hash content", As100HashMethod.id, "Hash exact content"),
        RilBinding("encrypt text", As100TextEncryptMethod.id, "Encrypt text as password-protected JWE"),
        RilBinding("decrypt text", As100TextDecryptMethod.id, "Decrypt password-protected JWE text"),
        RilBinding("encrypt file", As100FileEncryptMethod.id, "Encrypt a file as JWE"),
        RilBinding("decrypt file", As100FileDecryptMethod.id, "Decrypt a JWE file"),
        RilBinding("create signing key", As100KeyGenerateMethod.id, "Create a device-bound ES256 identity"),
        RilBinding("export cryptographic identity", As100IdentityExportMethod.id, "Create a public NFC/QR identity token"),
        RilBinding("sign content", As100SignMethod.id, "Create a detached ES256 JWS"),
        RilBinding("verify signature", As100VerifySignatureMethod.id, "Verify a detached ES256 JWS"),
        RilBinding("split secret", As100SecretSplitMethod.id, "Split a secret into Shamir shares"),
        RilBinding("merge secret shares", As100SecretCombineMethod.id, "Recover a Shamir-shared secret"),
        RilBinding("create challenge", As100ChallengeCreateMethod.id, "Create an expiring proof-of-possession challenge"),
        RilBinding("sign challenge", As100ChallengeRespondMethod.id, "Respond to a challenge with the local identity"),
        RilBinding("verify challenge", As100ChallengeVerifyMethod.id, "Verify a signed challenge response"),
        RilBinding("import totp account", As100TotpImportMethod.id, "Import a TOTP account"),
        RilBinding("create totp code", As100TotpGenerateMethod.id, "Generate a TOTP code"),
        RilBinding("delete totp account", As100TotpDeleteMethod.id, "Delete a TOTP account"),
        RilBinding("export totp backup", As100TotpExportMethod.id, "Export a password-protected JWE TOTP backup"),
        RilBinding("describe cryptography status", As100CryptoDashboardMethod.id, "Show non-secret cryptography status")
    )

    override fun capabilityScreens() = listOf(
        PasswordGenerateCapabilityScreen,
        HashCapabilityScreen,
        TextEncryptCapabilityScreen,
        TextDecryptCapabilityScreen,
        FileEncryptCapabilityScreen,
        FileDecryptCapabilityScreen,
        KeyGenerateCapabilityScreen,
        IdentityExportCapabilityScreen,
        SignCapabilityScreen,
        VerifySignatureCapabilityScreen,
        SecretSplitCapabilityScreen,
        SecretCombineCapabilityScreen,
        ChallengeCreateCapabilityScreen,
        ChallengeRespondCapabilityScreen,
        ChallengeVerifyCapabilityScreen,
        TotpImportCapabilityScreen,
        TotpGenerateCapabilityScreen,
        TotpDeleteCapabilityScreen,
        TotpExportCapabilityScreen,
        CryptoDashboardCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100PasswordGenerateMethod.id to listOf(
            MethodSetting.TextSetting("mode", "Secret type", defaultValue = "random"),
            MethodSetting.IntSetting("length", "Length / digits / bytes", defaultValue = 20, minimum = 4, maximum = 512),
            MethodSetting.IntSetting("words", "Passphrase words", defaultValue = 6, minimum = 3, maximum = 20)
        ),
        As100HashMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "Text", defaultValue = ""),
            MethodSetting.TextSetting("algorithm", "Digest algorithm (expert)", defaultValue = "SHA-256"),
            MethodSetting.BooleanSetting("include_full_json", "Return provenance JSON", defaultValue = true)
        ),
        As100TextEncryptMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "Text", defaultValue = ""),
            MethodSetting.TextSetting("password", "Password", defaultValue = ""),
            MethodSetting.IntSetting("p2c", "Password hardening iterations (expert)", defaultValue = JweEngine.DEFAULT_P2C, minimum = 10000, maximum = 1000000),
            MethodSetting.BooleanSetting("include_full_json", "Return provenance JSON", defaultValue = true)
        ),
        As100TextDecryptMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "JWE ciphertext", defaultValue = ""),
            MethodSetting.TextSetting("password", "Password", defaultValue = ""),
            MethodSetting.BooleanSetting("include_full_json", "Return provenance JSON", defaultValue = true)
        ),
        As100FileEncryptMethod.id to listOf(
            MethodSetting.TextSetting("input_uri", "Input file URI", defaultValue = ""),
            MethodSetting.TextSetting("password", "Password", defaultValue = ""),
            MethodSetting.IntSetting("p2c", "Password hardening iterations (expert)", defaultValue = JweEngine.DEFAULT_P2C, minimum = 10000, maximum = 1000000),
            MethodSetting.BooleanSetting("include_full_json", "Return provenance JSON", defaultValue = true)
        ),
        As100FileDecryptMethod.id to listOf(
            MethodSetting.TextSetting("input_uri", "Encrypted JWE file URI", defaultValue = ""),
            MethodSetting.TextSetting("password", "Password", defaultValue = ""),
            MethodSetting.BooleanSetting("include_full_json", "Return provenance JSON", defaultValue = true)
        ),
        As100KeyGenerateMethod.id to listOf(
            MethodSetting.TextSetting("key_alias", "Local signing key alias (expert)", defaultValue = JwsIdentityEngine.DEFAULT_ALIAS)
        ),
        As100IdentityExportMethod.id to listOf(
            MethodSetting.TextSetting("display_name", "Display name", defaultValue = ""),
            MethodSetting.TextSetting("jwk", "Public identity key override (expert)", defaultValue = "")
        ),
        As100SignMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "Text", defaultValue = ""),
            MethodSetting.TextSetting("input_uri", "Input file URI", defaultValue = ""),
            MethodSetting.TextSetting("key_alias", "Local signing key alias (expert)", defaultValue = JwsIdentityEngine.DEFAULT_ALIAS)
        ),
        As100VerifySignatureMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "Text", defaultValue = ""),
            MethodSetting.TextSetting("input_uri", "Input file URI", defaultValue = ""),
            MethodSetting.TextSetting("signature", "Detached signature", defaultValue = ""),
            MethodSetting.TextSetting("jwk", "Responder public identity card or JWK", defaultValue = ""),
            MethodSetting.TextSetting("expected_fingerprint", "Expected signer fingerprint (expert)", defaultValue = "")
        ),
        As100SecretSplitMethod.id to listOf(
            MethodSetting.TextSetting("input_text", "Secret", defaultValue = ""),
            MethodSetting.IntSetting("threshold", "Threshold", defaultValue = 3, minimum = 2, maximum = 20),
            MethodSetting.IntSetting("share_count", "Number of shares", defaultValue = 5, minimum = 2, maximum = 20)
        ),
        As100ChallengeCreateMethod.id to listOf(
            MethodSetting.TextSetting("audience", "Verifier / audience", defaultValue = ""),
            MethodSetting.IntSetting("ttl_seconds", "Challenge validity seconds (expert)", defaultValue = 300, minimum = 30, maximum = 3600)
        ),
        As100ChallengeRespondMethod.id to listOf(
            MethodSetting.TextSetting("challenge", "Live challenge", defaultValue = ""),
            MethodSetting.TextSetting("key_alias", "Local signing key alias (expert)", defaultValue = JwsIdentityEngine.DEFAULT_ALIAS)
        ),
        As100ChallengeVerifyMethod.id to listOf(
            MethodSetting.TextSetting("challenge", "Live challenge", defaultValue = ""),
            MethodSetting.TextSetting("signature", "Detached signature", defaultValue = ""),
            MethodSetting.TextSetting("jwk", "Responder public identity card or JWK", defaultValue = ""),
            MethodSetting.TextSetting("audience", "Expected audience", defaultValue = "")
        ),
        As100TotpImportMethod.id to listOf(
            MethodSetting.TextSetting("otpauth_uri", "Authenticator setup link", defaultValue = "")
        ),
        As100TotpGenerateMethod.id to listOf(
            MethodSetting.TextSetting("secret", "TOTP secret (expert)", defaultValue = ""),
            MethodSetting.IntSetting("digits", "TOTP digits (expert)", defaultValue = 6, minimum = 6, maximum = 8),
            MethodSetting.IntSetting("period", "TOTP period seconds (expert)", defaultValue = 30, minimum = 5, maximum = 300),
            MethodSetting.TextSetting("algorithm", "TOTP algorithm (expert)", defaultValue = "SHA1")
        ),
        As100TotpExportMethod.id to listOf(
            MethodSetting.TextSetting("password", "Backup password", defaultValue = "")
        )
    )

    override fun dependencies(): List<ModuleDependency> = emptyList()

    override fun examples() = listOf(
        ModuleExample(
            title = "Encrypt a text field",
            ril = "WHAT; encrypt text; RESULT; return crypto_value as ciphertext, crypto_provenance_json as provenance; format json",
            notes = "Password-protected JWE Compact Serialization; see docs/example_odk_crypto.text.encrypt.xlsx"
        ),
        ModuleExample(
            title = "Encrypt a file attachment",
            ril = "WHAT; encrypt file; RESULT; return crypto_output_uri as encrypted_file, crypto_provenance_json as provenance; format json",
            notes = "Standalone compact-JWE file mode is capped at 24 MiB; see docs/example_odk_crypto.file.encrypt.xlsx"
        )
    )
}
