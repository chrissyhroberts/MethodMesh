package com.example.methodmesh.modules.imageredaction

/** Preserve the image setting ID and its established flat input_source transport spelling. */
internal fun imageRedactionInputSettings(settings: Map<String, String>): Map<String, String> {
    val normalised = settings.mapKeys { (key, _) -> key.removePrefix("input_") }.toMutableMap()
    val source = listOf("input_source", "source", "input_input_source").firstNotNullOfOrNull { settings[it]?.takeIf(String::isNotBlank) }
    if (source != null) { normalised.remove("source"); normalised["input_source"] = source }
    return normalised
}

/** Consume the scanner's public flat contract, never its concrete implementation or OCR sidecars. */
internal object RedactionScanContract {
    const val METHOD_ID = "document.scan"
    val settings = mapOf("page_limit" to "10", "scanner_mode" to "full", "allow_gallery_import" to "true",
        "run_ocr" to "false", "return_searchable_pdf" to "false", "return_text_file" to "false", "timestamp_policy" to "disabled")

    fun pdfUri(fields: Map<String, String>): String {
        require(fields["document_scan_status"].equals("succeeded", true)) { "The scan did not complete successfully." }
        fields["methodmesh_status"]?.takeIf(String::isNotBlank)?.let {
            require(it.equals("succeeded", true)) { "The scan reported an unsuccessful execution." }
        }
        val uri = fields["document_scan_pdf_uri"].orEmpty()
        require(uri.startsWith("content://") && uri.length > "content://".length) { "The scanner did not return a readable PDF." }
        return uri
    }
}
