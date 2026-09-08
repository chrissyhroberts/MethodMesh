package com.example.methodmesh.modules.trustedtimestamp

object TrustedTimestampContractMetadata {
    const val MATURITY = "Development"
    const val CONNECTIVITY = "Online only"

    // ODK/external intent parameter names.
    const val ODK_INPUT_TEXT = "input_text"
    const val ODK_INPUT_FILE = "input_file"
    const val ODK_INPUT_TSA_URL = "input_tsa_url"
    const val ODK_INPUT_TIMEOUT_MS = "input_timeout_ms"

    // Runtime/preset/protocol setting/context names.
    const val RUNTIME_INPUT_TEXT = "input_text"
    const val RUNTIME_INPUT_FILE = "input_file"
    const val RUNTIME_TSA_URL = "tsa_url"
    const val RUNTIME_TIMEOUT_MS = "timeout_ms"

    val directTextIntent =
        "com.example.methodmesh.EXECUTE_METHOD(" +
            "method_id='integrity.trusted_timestamp'," +
            "input_text=\${source_text}," +
            "input_tsa_url=\${tsa_url}," +
            "input_timeout_ms=\${timeout_ms}," +
            "return_mode='flat',input_payload_mode='FULL')"

    val directFileIntent =
        "com.example.methodmesh.EXECUTE_METHOD(" +
            "method_id='integrity.trusted_timestamp'," +
            "input_file=\${source_file}," +
            "input_tsa_url=\${tsa_url}," +
            "input_timeout_ms=\${timeout_ms}," +
            "return_mode='flat',input_payload_mode='FULL')"

    val interactiveIntent =
        "com.example.methodmesh.EXECUTE_METHOD(" +
            "method_id='integrity.trusted_timestamp'," +
            "input_tsa_url=\${tsa_url}," +
            "input_timeout_ms=\${timeout_ms}," +
            "return_mode='flat',input_payload_mode='FULL')"
}
