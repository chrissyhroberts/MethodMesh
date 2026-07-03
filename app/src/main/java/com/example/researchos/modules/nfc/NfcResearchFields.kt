package com.example.researchos.modules.nfc

object NfcEvidenceFields {
    const val TAG_UID_HEX = "tag_uid_hex"
    const val TAG_UID_DEC = "tag_uid_dec"
    const val TECH_LIST = "tech_list"
    const val NDEF_SUPPORTED = "ndef_supported"
    const val NDEF_MESSAGE_SIZE_BYTES = "ndef_message_size_bytes"
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
        TAG_SUMMARY
    )
}

object NfcWriteFields {
    const val WRITE_SUCCESS = "write_success"
    const val WRITE_MESSAGE = "write_message"
    const val WRITE_RECORD_TYPE = "write_record_type"
    const val WRITE_SIZE_BYTES = "write_size_bytes"
    const val INTERVENTION_JSON = "intervention_json"
    const val POST_WRITE_EVIDENCE_JSON = "post_write_evidence_json"
}

object ResearchOutputFields {
    const val EVIDENCE_ID = "evidence_id"
    const val EVIDENCE_KIND = "evidence_kind"
    const val PHENOMENON = "phenomenon"
    const val METHOD = "method"
    const val TEMPORAL_SEMANTICS = "temporal_semantics"
    const val AGGREGATION_SEMANTICS = "aggregation_semantics"
    const val LINEAGE = "lineage"
    const val PROVENANCE_JSON = "provenance_json"
    const val CAPTURE_OUTCOME_JSON = "capture_outcome_json"
    const val QUALITY_JSON = "quality_json"
    const val VALIDATION_JSON = "validation_json"
    const val ARTIFACT_JSON = "artifact_json"
    const val EVIDENCE_JSON = "evidence_json"
    const val EXECUTION_JSON = "execution_json"
    const val AS_SIGNAL_TYPE = "as_signal_type"
    const val AS_SIGNAL_SOURCE_SERVICE = "as_signal_source_service"
    const val AS_TRANSFORMATION_ACTION = "as_transformation_action"
    const val AS_TRANSFORMATION_STATUS = "as_transformation_status"
}

