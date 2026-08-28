package com.example.methodmesh.modules.nfc

object NfcEvidenceFields {
    const val TAG_UID_HEX = "tag_uid_hex"
    const val TAG_UID_DEC = "tag_uid_dec"
    const val TECH_LIST = "tech_list"
    const val NDEF_SUPPORTED = "ndef_supported"
    const val NDEF_MESSAGE_SIZE_BYTES = "ndef_message_size_bytes"
    const val NDEF_MESSAGE_SHA256 = "ndef_message_sha256"
    const val NDEF_HAS_MEANINGFUL_CONTENT = "ndef_has_meaningful_content"
    const val NDEF_MAX_SIZE_BYTES = "ndef_max_size_bytes"
    const val NDEF_IS_WRITABLE = "ndef_is_writable"
    const val NDEF_CAN_MAKE_READ_ONLY = "ndef_can_make_read_only"
    const val NDEF_RECORD_COUNT = "ndef_record_count"
    const val NDEF_TEXT = "ndef_text"
    const val NDEF_URI = "ndef_uri"
    const val NDEF_MIME_TYPES = "ndef_mime_types"
    const val NDEF_EXTERNAL_TYPES = "ndef_external_types"
    const val NDEF_PAYLOAD_HEX_ALL = "ndef_payload_hex_all"
    const val NDEF_PAYLOAD_UTF8_ALL = "ndef_payload_utf8_all"
    const val NDEF_FIRST_PAYLOAD_HEX = "ndef_first_payload_hex"
    const val NDEF_FIRST_PAYLOAD_UTF8 = "ndef_first_payload_utf8"
    const val NDEF_RECORDS_JSON = "ndef_records_json"
    const val TAG_SUMMARY = "tag_summary"

    val tagOutputFields: List<String> = listOf(
        TAG_UID_HEX,
        TAG_UID_DEC,
        TECH_LIST,
        NDEF_SUPPORTED,
        NDEF_MESSAGE_SIZE_BYTES,
        NDEF_MESSAGE_SHA256,
        NDEF_HAS_MEANINGFUL_CONTENT,
        NDEF_MAX_SIZE_BYTES,
        NDEF_IS_WRITABLE,
        NDEF_CAN_MAKE_READ_ONLY,
        NDEF_RECORD_COUNT,
        NDEF_TEXT,
        NDEF_URI,
        NDEF_MIME_TYPES,
        NDEF_EXTERNAL_TYPES,
        NDEF_PAYLOAD_HEX_ALL,
        NDEF_PAYLOAD_UTF8_ALL,
        NDEF_FIRST_PAYLOAD_HEX,
        NDEF_FIRST_PAYLOAD_UTF8,
        NDEF_RECORDS_JSON,
        TAG_SUMMARY,
        NfcCredentialEvidence.FORMAT_FIELD,
        NfcCredentialEvidence.HASH_FIELD
    )
}

object NfcWriteFields {
    const val WRITE_SUCCESS = "write_success"
    const val WRITE_MESSAGE = "write_message"
    const val WRITE_RECORD_TYPE = "write_record_type"
    const val WRITE_SIZE_BYTES = "write_size_bytes"
    const val OVERWRITE_POLICY = "overwrite_policy"
    const val PREVIOUS_MESSAGE_HASH = "previous_message_hash"
    const val WRITTEN_MESSAGE_HASH = "written_message_hash"
    const val WRITE_VERIFIED = "write_verified"
}

object NfcWipeFields {
    const val WIPE_SUCCESS = "wipe_success"
    const val WIPE_MESSAGE = "wipe_message"
    const val WIPED_TIME_ISO = "wiped_time_iso"

    val outputFields = listOf(
        WIPE_SUCCESS,
        WIPE_MESSAGE,
        WIPED_TIME_ISO,
        NfcWriteFields.PREVIOUS_MESSAGE_HASH,
        NfcWriteFields.WRITTEN_MESSAGE_HASH,
        NfcWriteFields.WRITE_VERIFIED,
        NfcEvidenceFields.TAG_UID_HEX,
        NfcEvidenceFields.NDEF_RECORD_COUNT,
        NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES,
        NfcEvidenceFields.NDEF_MESSAGE_SHA256
    )
}

object NfcProvisionFields {
    const val CREDENTIAL_ID = "credential_id"
    const val CREDENTIAL_SUBJECT_ID = "credential_subject_id"
    const val PIN_LENGTH = "pin_length"
    const val CREDENTIAL_FORMAT_VERSION = "credential_format_version"
    const val KEY_DERIVATION = "key_derivation"
    const val CREDENTIAL_ISSUED_TIME_ISO = "credential_issued_time_iso"
    const val CREDENTIAL_ENVELOPE_HASH = "credential_envelope_hash"
    const val CREDENTIAL_SECRET_HASH = "credential_secret_hash"
    const val ISSUER_KEY_ID = "issuer_key_id"
    const val ISSUER_PUBLIC_KEY_BASE64 = "issuer_public_key_base64"
    const val ISSUER_SIGNATURE_ALGORITHM = "issuer_signature_algorithm"
    const val PROVISION_SUCCESS = "provision_success"
    const val PROVISION_MESSAGE = "provision_message"
    const val PROVISIONED_TIME_ISO = "provisioned_time_iso"

    val outputFields: List<String> = listOf(
        CREDENTIAL_ID,
        CREDENTIAL_SUBJECT_ID,
        PIN_LENGTH,
        CREDENTIAL_FORMAT_VERSION,
        KEY_DERIVATION,
        CREDENTIAL_ISSUED_TIME_ISO,
        CREDENTIAL_ENVELOPE_HASH,
        CREDENTIAL_SECRET_HASH,
        ISSUER_KEY_ID,
        ISSUER_PUBLIC_KEY_BASE64,
        ISSUER_SIGNATURE_ALGORITHM,
        PROVISION_SUCCESS,
        PROVISION_MESSAGE,
        PROVISIONED_TIME_ISO,
        NfcWriteFields.WRITE_RECORD_TYPE,
        NfcWriteFields.WRITE_SIZE_BYTES,
        NfcWriteFields.OVERWRITE_POLICY,
        NfcWriteFields.PREVIOUS_MESSAGE_HASH,
        NfcWriteFields.WRITTEN_MESSAGE_HASH,
        NfcWriteFields.WRITE_VERIFIED,
        NfcEvidenceFields.TAG_UID_HEX,
        NfcCredentialEvidence.HASH_FIELD
    )
}

object NfcCredentialVerificationFields {
    const val CREDENTIAL_VERIFIED = "credential_verified"
    const val VERIFICATION_MESSAGE = "credential_verification_message"
    const val VERIFIED_TIME_ISO = "credential_verified_time_iso"
    const val PIN_VERIFIED = "pin_verified"
    const val ISSUER_SIGNATURE_VALID = "issuer_signature_valid"
    const val ISSUER_TRUST_STATUS = "issuer_trust_status"

    val outputFields: List<String> = listOf(
        CREDENTIAL_VERIFIED,
        VERIFICATION_MESSAGE,
        NfcProvisionFields.CREDENTIAL_ID,
        NfcProvisionFields.CREDENTIAL_SUBJECT_ID,
        NfcProvisionFields.PIN_LENGTH,
        NfcProvisionFields.CREDENTIAL_FORMAT_VERSION,
        NfcProvisionFields.KEY_DERIVATION,
        NfcProvisionFields.CREDENTIAL_ISSUED_TIME_ISO,
        NfcProvisionFields.CREDENTIAL_ENVELOPE_HASH,
        NfcProvisionFields.CREDENTIAL_SECRET_HASH,
        NfcProvisionFields.ISSUER_KEY_ID,
        NfcProvisionFields.ISSUER_PUBLIC_KEY_BASE64,
        NfcProvisionFields.ISSUER_SIGNATURE_ALGORITHM,
        PIN_VERIFIED,
        ISSUER_SIGNATURE_VALID,
        ISSUER_TRUST_STATUS,
        VERIFIED_TIME_ISO,
        NfcEvidenceFields.TAG_UID_HEX,
        NfcCredentialEvidence.HASH_FIELD
    )
}
