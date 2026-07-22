package com.example.researchos.modules.nfc

data class NfcWriteRequest(
    val recordType: String,
    val value: String,
    val mimeType: String = "text/plain",
    val languageCode: String = "en"
)
