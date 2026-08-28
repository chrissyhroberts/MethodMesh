package com.example.methodmesh.modules.nfc

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object NfcModule : MethodMeshModule {
    override val moduleId: String = "nfc"
    override val displayName: String = "NFC"


    override fun as100Methods() = listOf(
        As100NfcReadMethod,
        As100NfcWriteMethod,
        As100NfcWipeMethod,
        As100NfcCredentialProvisioningMethod,
        As100NfcCredentialVerificationMethod,
        As100ProtocolNfcCheckMethod,
        As100ProtocolNfcCompleteMethod,
        As100ProtocolNfcProvisionMethod,
        As100ProtocolNfcReconstructMethod,
        As100ProtocolNfcOverrideMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("scan nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("capture nfc", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("scan tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("read tag", As100NfcReadMethod.ID, "Read an NFC tag"),
        RilBinding("write nfc", As100NfcWriteMethod.ID, "Write an NFC tag"),
        RilBinding("write tag", As100NfcWriteMethod.ID, "Write an NFC tag"),
        RilBinding("wipe nfc", As100NfcWipeMethod.ID, "Remove NDEF user content from an NFC tag"),
        RilBinding("wipe tag", As100NfcWipeMethod.ID, "Remove NDEF user content from an NFC tag"),
        RilBinding(
            "provision nfc credential",
            As100NfcCredentialProvisioningMethod.ID,
            "Create a PIN-protected portable NFC credential"
        ),
        RilBinding(
            "verify nfc credential",
            As100NfcCredentialVerificationMethod.ID,
            "Verify a portable NFC credential and PIN"
        ),
        RilBinding("check protocol card", As100ProtocolNfcCheckMethod.id, "Check whether a protocol step is allowed on an NFC card"),
        RilBinding("complete protocol card", As100ProtocolNfcCompleteMethod.id, "Mark a completed protocol step on an NFC card"),
        RilBinding("provision protocol card", As100ProtocolNfcProvisionMethod.id, "Create the initial protocol receipt on an NFC card"),
        RilBinding("reconstruct protocol card", As100ProtocolNfcReconstructMethod.id, "Restore a protocol receipt to a replacement NFC card"),
        RilBinding("override protocol card", As100ProtocolNfcOverrideMethod.id, "Apply a justified manual protocol-card override")
    )

    override fun capabilityScreens() = listOf(
        NfcReadCapabilityScreen,
        NfcWriteCapabilityScreen,
        NfcWipeCapabilityScreen,
        NfcCredentialProvisioningCapabilityScreen,
        NfcCredentialVerificationCapabilityScreen,
        ProtocolNfcCheckCapabilityScreen,
        ProtocolNfcCompleteCapabilityScreen,
        ProtocolNfcProvisionCapabilityScreen,
        ProtocolNfcReconstructCapabilityScreen,
        ProtocolNfcOverrideCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100NfcWriteMethod.ID to listOf(
            MethodSetting.TextSetting("value", "Value to write", defaultValue = ""),
            MethodSetting.ChoiceSetting(
                "record_type",
                "Record type",
                defaultValue = "text/plain",
                choices = listOf("text/plain", "text/uri-list", "application/json")
            ),
            MethodSetting.ChoiceSetting("overwrite_policy", "Overwrite policy", defaultValue = "replace", choices = listOf("replace", "empty_only"))
        ),
        As100NfcCredentialProvisioningMethod.ID to listOf(
            MethodSetting.TextSetting("credential_subject_id", "Credential subject", defaultValue = ""),
            MethodSetting.IntSetting("pin_length", "PIN length", defaultValue = 6, minimum = 4, maximum = 6),
            MethodSetting.ChoiceSetting("overwrite_policy", "Overwrite policy", defaultValue = "empty_only", choices = listOf("empty_only", "replace"))
        ),
        As100NfcCredentialVerificationMethod.ID to listOf(
            MethodSetting.TextSetting("trusted_issuer_key_ids", "Trusted issuer key IDs", "Comma-separated; leave blank to report issuer trust as not checked.", defaultValue = "")
        ),
        As100ProtocolNfcCheckMethod.id to protocolSettings(),
        As100ProtocolNfcCompleteMethod.id to protocolSettings(),
        As100ProtocolNfcProvisionMethod.id to protocolProvisionSettings(),
        As100ProtocolNfcReconstructMethod.id to protocolReconstructSettings(),
        As100ProtocolNfcOverrideMethod.id to protocolOverrideSettings()
    )

    private fun protocolSettings() = listOf(
        MethodSetting.TextSetting("protocol_id", "Protocol ID", defaultValue = ""),
        MethodSetting.TextSetting("protocol_version", "Protocol version", defaultValue = "1"),
        MethodSetting.TextSetting("step_id", "Step ID", defaultValue = ""),
        MethodSetting.IntSetting("flag_bit_count", "Active flag bit count", defaultValue = 8, minimum = 1, maximum = 65535),
        MethodSetting.IntSetting("completion_bit_count", "Completion bit count", defaultValue = 8, minimum = 1, maximum = 65535),
        MethodSetting.TextSetting("flag_definitions", "Flag definitions", description = "bit=name;bit=name", defaultValue = ""),
        MethodSetting.TextSetting("step_definitions", "Step definitions", description = "bit=name;bit=name", defaultValue = ""),
        MethodSetting.TextSetting("required_bits", "Required bit mask (hex)", defaultValue = "00"),
        MethodSetting.TextSetting("required_value", "Required bit value (hex)", defaultValue = "00"),
        MethodSetting.TextSetting("required_expression", "Required condition", description = "ALL(0001,0002), ANY(0002,0004), or NONE(0004)", defaultValue = ""),
        MethodSetting.TextSetting("completion_bits", "Completion bit mask (hex)", defaultValue = "00"),
        MethodSetting.TextSetting("set_flag_bits", "Set active flag bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("clear_flag_bits", "Clear active flag bits (hex)", defaultValue = "")
    )

    private fun protocolProvisionSettings() = listOf(
        MethodSetting.TextSetting("protocol_id", "Protocol ID", defaultValue = ""),
        MethodSetting.TextSetting("protocol_version", "Protocol version", defaultValue = "1"),
        MethodSetting.IntSetting("flag_bit_count", "Active flag bit count", defaultValue = 8, minimum = 1, maximum = 65535),
        MethodSetting.IntSetting("completion_bit_count", "Completion bit count", defaultValue = 8, minimum = 1, maximum = 65535),
        MethodSetting.TextSetting("initial_flag_bits", "Initial active flag bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("initial_completion_bits", "Initial completion bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("protocol_definition_json", "Protocol definition (optional)", description = "Use the protocol builder or load a JSON definition file; the definition is hashed and returned as provenance.", defaultValue = ""),
        MethodSetting.ChoiceSetting("overwrite_policy", "Provisioning policy", defaultValue = "empty_only", choices = listOf("empty_only", "replace"))
    )

    private fun protocolReconstructSettings() = listOf(
        MethodSetting.TextSetting("protocol_state_payload", "State payload", description = "Encoded protocol receipt from an exported ODK/RIL result.", defaultValue = ""),
        MethodSetting.TextSetting("protocol_state_payload_hash", "State payload SHA-256", defaultValue = ""),
        MethodSetting.TextSetting("reconstruction_reason", "Replacement/reconstruction reason", defaultValue = "")
    )

    private fun protocolOverrideSettings() = listOf(
        MethodSetting.TextSetting("protocol_id", "Protocol ID", defaultValue = ""),
        MethodSetting.TextSetting("set_flag_bits", "Set active flag bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("clear_flag_bits", "Clear active flag bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("set_completion_bits", "Set completion bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("clear_completion_bits", "Clear completion bits (hex)", defaultValue = ""),
        MethodSetting.TextSetting("override_justification", "Override justification", defaultValue = "")
    )
}
