package com.example.researchos.modules.attestation

import com.example.researchos.modules.ModuleDependency
import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding

object AttestationModule : ResearchOSModule {
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

    override fun dependencies() = listOf(
        ModuleDependency("nfc", "NFC tag evidence is captured by the existing NFC capability and consumed by attestation."),
        ModuleDependency("qrcode", "QR token evidence is captured by the QR capability and consumed by attestation."),
        ModuleDependency("android_device_credential", "PIN, pattern and phone password authorisation use Android device credential prompts rather than ResearchOS storing secrets.")
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
