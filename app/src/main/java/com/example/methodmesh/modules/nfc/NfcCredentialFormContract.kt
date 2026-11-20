package com.example.methodmesh.modules.nfc

/**
 * The MethodMesh-facing contract used when an ODK/XLSForm is mapped to the NFC
 * credential methods.  It intentionally contains no study or provisioner
 * allow-list: Sentinel owns that legitimacy decision.
 */
object NfcCredentialFormContract {
    const val SCHEMA_VERSION = "methodmesh.nfc_form_contract.v1"
    const val CURRENT_VERSION = "v1"

    data class MethodMapping(
        val methodId: String,
        val methodVersion: String,
        val credentialFormatVersion: String,
        val requiredInputs: Map<String, String>,
        val requiredReturnFields: List<String>,
        val compatibilityStatement: String
    )

    private val v1Provisioning = MethodMapping(
        methodId = As100NfcCredentialProvisioningMethod.ID,
        // v1 is immutable. Do not replace these with the live method constants:
        // a future method revision must be added as a new contract version.
        methodVersion = "1.0.1",
        credentialFormatVersion = "ROSC2",
        requiredInputs = linkedMapOf(
            "credential_subject_id" to "credential_subject_id_input",
            "pin_length" to "pin_length_input",
            "valid_until_date" to "valid_until_date_iso",
            "overwrite_policy" to "overwrite_policy_input"
        ),
        requiredReturnFields = listOf(
            "credential_id",
            "credential_subject_id",
            NfcProvisionFields.VALID_UNTIL_ISO,
            NfcProvisionFields.PROVISION_SUCCESS,
            NfcProvisionFields.PROVISION_MESSAGE,
            NfcWriteFields.WRITE_VERIFIED,
            NfcProvisionFields.ISSUER_KEY_ID,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256,
            "methodmesh_full_json"
        ),
        compatibilityStatement = "Invokes nfc_credential_provisioning 1.0.1 for a staff member with the ROSC2 credential format, passes the inclusive ISO valid-until date, and captures the authenticated expiry, write verification, issuer evidence, and full JSON."
    )

    private val v1Verification = MethodMapping(
        methodId = As100NfcCredentialVerificationMethod.ID,
        methodVersion = "1.1.0",
        credentialFormatVersion = "ROSC2",
        requiredInputs = emptyMap(),
        requiredReturnFields = listOf(
            "methodmesh_execution_id",
            "methodmesh_status",
            NfcCredentialVerificationFields.CREDENTIAL_VERIFIED,
            NfcCredentialVerificationFields.VERIFICATION_MESSAGE,
            NfcProvisionFields.CREDENTIAL_ID,
            NfcProvisionFields.CREDENTIAL_SUBJECT_ID,
            NfcProvisionFields.VALID_UNTIL_ISO,
            NfcCredentialVerificationFields.PIN_VERIFIED,
            NfcCredentialVerificationFields.ISSUER_SIGNATURE_VALID,
            NfcCredentialVerificationFields.ISSUER_TRUST_STATUS,
            NfcProvisionFields.ISSUER_KEY_ID,
            NfcProvisionFields.ISSUER_PUBLIC_KEY_FINGERPRINT_SHA256,
            "methodmesh_full_json"
        ),
        compatibilityStatement = "Invokes nfc_credential_verification 1.1.0 to authenticate the staff operator's ROSC2 credential and captures authenticated expiry, PIN/signature outcomes, human-readable diagnostics, issuer evidence for Sentinel reconciliation, and full JSON. Participant or study subject identifiers are not used as NFC inputs."
    )

    private val v1: Map<String, MethodMapping> = mapOf(
        v1Provisioning.methodId to v1Provisioning,
        v1Verification.methodId to v1Verification
    )

    /** Immutable registry: add v2 here; never rewrite v1. */
    val versions: Map<String, Map<String, MethodMapping>> = mapOf("v1" to v1)

    val provisioning: MethodMapping
        get() = requireNotNull(forMethod(As100NfcCredentialProvisioningMethod.ID))

    val verification: MethodMapping
        get() = requireNotNull(forMethod(As100NfcCredentialVerificationMethod.ID))

    fun forMethod(methodId: String, contractVersion: String = CURRENT_VERSION): MethodMapping? =
        versions[contractVersion]?.get(methodId)

    fun require(methodId: String, contractVersion: String = CURRENT_VERSION): MethodMapping =
        requireNotNull(forMethod(methodId, contractVersion)) {
            "NFC form contract $contractVersion does not map method '$methodId'."
        }

    fun supportedVersions(): List<String> = versions.keys.sorted()
}
