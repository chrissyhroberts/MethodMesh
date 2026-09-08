package com.example.methodmesh.modules.digitalsigning

import org.json.JSONObject

object DigitalSigningResultJson {
    fun tsaJson(tsa: TsaAttestation): JSONObject = JSONObject().apply {
        put("requested", tsa.requested)
        put("status", tsa.status)
        putNullable("authority", tsa.authority)
        putNullable("timestamp_utc", tsa.timestampUtc)
        put("message_imprint_algorithm", tsa.messageImprintAlgorithm)
        putNullable("message_imprint", tsa.messageImprintHex)
        putNullable("policy_oid", tsa.policyOid)
        putNullable("serial_number", tsa.serialNumber)
        put("token_format", tsa.tokenFormat)
        putNullable("token", tsa.tokenBase64)
        put("verification", JSONObject().apply {
            putNullable("signature_valid", tsa.signatureValid)
            putNullable("imprint_matches_signed_pdf", tsa.imprintMatchesSignedPdf)
            putNullable("certificate_valid_at_timestamp", tsa.certificateValidAtTimestamp)
        })
        putNullable("error", tsa.error)
    }

    fun fullJson(result: DigitalSigningCommittedResult): String = JSONObject().apply {
        put("method", As100DigitalSigningMethod.ID)
        put("status", "committed")
        put("source", JSONObject().apply {
            put("origin", result.sourceOrigin.id)
            put("filename", result.sourceFilename)
            put("sha256", result.sourceSha256)
        })
        put("document", JSONObject().apply {
            put("signed_filename", result.signedFilename)
            put("signed_pdf_uri", result.signedPdfUri)
            put("signed_sha256", result.signedSha256)
            put("signed_artifact_ref", "artifact://signing.${result.signedSha256.take(32)}")
            put("page_count", result.pageCount)
            put("ink_present", result.inkPresent)
            put("ink_stroke_count", result.inkStrokeCount)
            put("finalised", result.finalised)
            put("finalisation_mode", result.finalisationMode)
            put("ink_flattened", result.inkFlattened)
            put("form_fields_flattened", result.formFieldsFlattened)
            put("permission_restrictions_applied", result.permissionRestrictionsApplied)
            put("new_markup_blocked", result.newMarkupBlocked)
            put("committed_at", result.committedAtUtc)
        })
        put("deliverables", JSONObject().apply {
            put("A", JSONObject().apply {
                put("role", "signed_document")
                put("label", "Deliverable A — Signed PDF")
                put("filename", result.signedFilename)
                put("uri", result.signedPdfUri)
                put("sha256", result.signedSha256)
                put("artifact_ref", "artifact://signing.${result.signedSha256.take(32)}")
            })
            put("B", JSONObject().apply {
                put("role", "provenance_and_verification_bundle")
                put("label", "Deliverable B — Provenance ZIP")
                putNullable("filename", result.verificationBundle.filename)
                putNullable("uri", result.verificationBundle.uri)
                putNullable("sha256", result.verificationBundle.sha256)
                result.verificationBundle.sha256?.let { put("artifact_ref", "artifact://signing-bundle.${it.take(32)}") }
                put("status", result.verificationBundle.status)
                put("contains_signed_pdf", false)
                put("verifies_deliverable", "A")
            })
        })
        put("tsa", tsaJson(result.tsa))
        put("verification_bundle", JSONObject().apply {
            put("status", result.verificationBundle.status)
            putNullable("filename", result.verificationBundle.filename)
            putNullable("uri", result.verificationBundle.uri)
            putNullable("sha256", result.verificationBundle.sha256)
            putNullable("error", result.verificationBundle.error)
        })
    }.toString()

    fun fields(result: DigitalSigningCommittedResult): Map<String, String> {
        val tsaJsonText = tsaJson(result.tsa).toString()
        val error = listOfNotNull(
            result.tsa.error?.takeIf { result.tsa.status == "failed" },
            result.verificationBundle.error?.takeIf { result.verificationBundle.status == "failed" }
        ).joinToString("; ")
        return linkedMapOf(
            DigitalSigningFields.STATUS to "succeeded",
            DigitalSigningFields.SIGNED_PDF_URI to result.signedPdfUri,
            DigitalSigningFields.SIGNED_PDF_NAME to result.signedFilename,
            DigitalSigningFields.SIGNED_SHA256 to result.signedSha256,
            DigitalSigningFields.SIGNED_ARTIFACT_REF to "artifact://signing.${result.signedSha256.take(32)}",
            DigitalSigningFields.VERIFICATION_BUNDLE_URI to result.verificationBundle.uri.orEmpty(),
            DigitalSigningFields.VERIFICATION_BUNDLE_NAME to result.verificationBundle.filename.orEmpty(),
            DigitalSigningFields.VERIFICATION_BUNDLE_SHA256 to result.verificationBundle.sha256.orEmpty(),
            DigitalSigningFields.VERIFICATION_BUNDLE_ARTIFACT_REF to result.verificationBundle.sha256
                ?.let { "artifact://signing-bundle.${it.take(32)}" }.orEmpty(),
            DigitalSigningFields.SOURCE_SHA256 to result.sourceSha256,
            DigitalSigningFields.SOURCE_ORIGIN to result.sourceOrigin.id,
            DigitalSigningFields.PAGE_COUNT to result.pageCount.toString(),
            DigitalSigningFields.INK_PRESENT to result.inkPresent.toString(),
            DigitalSigningFields.INK_STROKE_COUNT to result.inkStrokeCount.toString(),
            DigitalSigningFields.FINALISED to result.finalised.toString(),
            DigitalSigningFields.FINALISATION_MODE to result.finalisationMode,
            DigitalSigningFields.COMMITTED_TIME_ISO to result.committedAtUtc,
            DigitalSigningFields.TSA_STATUS to result.tsa.status,
            DigitalSigningFields.TSA_TIME_ISO to result.tsa.timestampUtc.orEmpty(),
            DigitalSigningFields.TSA_AUTHORITY to result.tsa.authority.orEmpty(),
            DigitalSigningFields.TSA_JSON to tsaJsonText,
            DigitalSigningFields.RESULT_JSON to fullJson(result),
            DigitalSigningFields.ERROR to error
        )
    }

    fun parse(json: String): DigitalSigningCommittedResult? = runCatching {
        val root = JSONObject(json)
        val source = root.getJSONObject("source")
        val document = root.getJSONObject("document")
        val tsa = root.getJSONObject("tsa")
        val bundle = root.optJSONObject("verification_bundle")
        DigitalSigningCommittedResult(
            sourceOrigin = PdfInputOrigin.entries.firstOrNull { it.id == source.optString("origin") } ?: PdfInputOrigin.Unknown,
            sourceFilename = source.optString("filename"),
            sourceSha256 = source.optString("sha256"),
            signedFilename = document.optString("signed_filename"),
            signedPdfUri = document.optString("signed_pdf_uri"),
            signedSha256 = document.optString("signed_sha256"),
            pageCount = document.optInt("page_count", 0),
            inkPresent = document.optBoolean("ink_present", false),
            inkStrokeCount = document.optInt("ink_stroke_count", 0),
            finalised = document.optBoolean("finalised", false),
            finalisationMode = document.optString("finalisation_mode"),
            inkFlattened = document.optBoolean("ink_flattened", false),
            formFieldsFlattened = document.optBoolean("form_fields_flattened", true),
            permissionRestrictionsApplied = document.optBoolean("permission_restrictions_applied", false),
            newMarkupBlocked = document.optBoolean("new_markup_blocked", false),
            committedAtUtc = document.optString("committed_at"),
            tsa = parseTsa(tsa),
            verificationBundle = if (bundle == null) VerificationBundle.pending() else VerificationBundle(
                status = bundle.optString("status", "pending"),
                filename = bundle.optNullableString("filename"),
                uri = bundle.optNullableString("uri"),
                sha256 = bundle.optNullableString("sha256"),
                error = bundle.optNullableString("error")
            )
        )
    }.getOrNull()

    private fun parseTsa(obj: JSONObject): TsaAttestation {
        val verification = obj.optJSONObject("verification")
        return TsaAttestation(
            requested = obj.optBoolean("requested", false),
            status = obj.optString("status", "not_requested"),
            authority = obj.optNullableString("authority"),
            timestampUtc = obj.optNullableString("timestamp_utc"),
            messageImprintAlgorithm = obj.optString("message_imprint_algorithm", "SHA-256"),
            messageImprintHex = obj.optNullableString("message_imprint"),
            policyOid = obj.optNullableString("policy_oid"),
            serialNumber = obj.optNullableString("serial_number"),
            tokenFormat = obj.optString("token_format", "RFC3161"),
            tokenBase64 = obj.optNullableString("token"),
            signatureValid = verification?.optNullableBoolean("signature_valid"),
            imprintMatchesSignedPdf = verification?.optNullableBoolean("imprint_matches_signed_pdf"),
            certificateValidAtTimestamp = verification?.optNullableBoolean("certificate_valid_at_timestamp"),
            error = obj.optNullableString("error")
        )
    }

    private fun JSONObject.putNullable(key: String, value: Any?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)
}
