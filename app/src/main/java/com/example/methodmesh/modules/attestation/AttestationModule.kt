package com.example.methodmesh.modules.attestation

import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AttestationModule : MethodMeshModule {
    override val moduleId: String = "attestation"
    override val displayName: String = "Traceable attestation"
    override val summary: String = "Signed, hash-chained field attestations with nightly ODK chain anchoring."

    override fun as100Methods() = listOf(
        As100CreateAttestationMethod,
        As100CreateAttestationAnchorMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("create attestation", As100CreateAttestationMethod.ID, "Sign a field event with the device attestation key"),
        RilBinding("attest event", As100CreateAttestationMethod.ID, "Sign a field event with the device attestation key"),
        RilBinding("verify event", As100CreateAttestationMethod.ID, "Create a signed event attestation"),
        RilBinding("create nightly anchor", As100CreateAttestationAnchorMethod.ID, "Export ODK fields for a nightly chain anchor"),
        RilBinding("anchor attestations", As100CreateAttestationAnchorMethod.ID, "Export ODK fields for a nightly chain anchor"),
        RilBinding("odk attestation anchor", As100CreateAttestationAnchorMethod.ID, "Export ODK fields for a nightly chain anchor")
    )

    override fun capabilityScreens() = listOf(
        AttestationCreateCapabilityScreen,
        AttestationAnchorCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100CreateAttestationMethod.ID to listOf(
            MethodSetting.TextSetting("event_payload_hash", "Event payload hash", defaultValue = ""),
            MethodSetting.TextSetting("commitment_recipe", "Commitment recipe", defaultValue = DEFAULT_ATTESTATION_COMMITMENT_RECIPE),
            MethodSetting.ChoiceSetting("verification_method", "Verification method", defaultValue = "Fingerprint", choices = listOf("Fingerprint", "Pin", "Qr", "Nfc", "Password")),
            MethodSetting.ChoiceSetting("trusted_timestamp", "Trusted timestamp", defaultValue = "preferred", choices = listOf("disabled", "preferred", "required")),
            MethodSetting.TextSetting("trusted_timestamp_authority", "Trusted timestamp authority URL", defaultValue = DEFAULT_TRUSTED_TIMESTAMP_AUTHORITY_URL),
            MethodSetting.IntSetting("trusted_timestamp_timeout_ms", "Timestamp timeout (ms)", defaultValue = 3500, minimum = 1000, maximum = 30000),
            MethodSetting.TextSetting("study_id", "Study ID", defaultValue = ""),
            MethodSetting.TextSetting("event_type", "Event type", defaultValue = "field_event")
        ),
        As100CreateAttestationAnchorMethod.ID to listOf(
            MethodSetting.TextSetting("study_id", "Study ID", defaultValue = ""),
            MethodSetting.TextSetting("operator_id", "Operator ID", defaultValue = "")
        )
    )

    override fun dependencies() = listOf(
        ModuleDependency("nfc", "NFC tag evidence is captured by the existing NFC capability and consumed by attestation."),
        ModuleDependency("qrcode", "QR token evidence is captured by the QR capability and consumed by attestation."),
        ModuleDependency("android_device_credential", "PIN, pattern and phone password authorisation use Android device credential prompts rather than MethodMesh storing secrets.")
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Attest a field event using PIN/biometric/QR/NFC/password",
            ril = "WHAT; create attestation; WHERE; participant/P001; RESULT; return attestation_hash, public_key_id, signature; format json",
            notes = "The event payload is hashed, chained to the previous attestation and signed by the phone key."
        ),
        ModuleExample(
            title = "Create nightly ODK anchor bundle",
            ril = "WHAT; create nightly anchor; WHERE; study/DEMO; RESULT; return current_chain_head_hash, public_key_base64, bundle_signature; format json",
            notes = "Submit these fields through ODK Central to obtain an independent server receipt timestamp."
        )
    )
}
