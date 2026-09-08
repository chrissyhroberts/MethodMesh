package com.example.methodmesh.modules.digitalsigning

import android.graphics.Bitmap
import java.io.File

enum class DigitalSigningMode { Navigate, Ink, Erase }

enum class PdfInputOrigin(val id: String) {
    Odk("odk"),
    FilePicker("file_picker"),
    AndroidIntent("android_intent"),
    RestoredDraft("restored_draft"),
    Unknown("unknown")
}

data class InkPoint(val x: Float, val y: Float)

data class InkStroke(
    val id: String,
    val pageIndex: Int,
    val points: List<InkPoint>,
    val widthPt: Float,
    val argb: Int = 0xFF141718.toInt()
)

data class WorkingPdf(
    val sourceFile: File,
    val sourceUriString: String,
    val displayName: String,
    val sourceOrigin: PdfInputOrigin,
    val sourceSha256: String,
    val pageCount: Int
)

data class RenderedPdfPage(
    val pageIndex: Int,
    val bitmap: Bitmap,
    val pageWidthPt: Float,
    val pageHeightPt: Float
)

data class PdfCommitResult(
    val outputFile: File,
    val signedSha256: String,
    val pageCount: Int,
    val inkFlattened: Boolean,
    val formFieldsFlattened: Boolean,
    val finalised: Boolean,
    val finalisationMode: String,
    val permissionRestrictionsApplied: Boolean,
    val newMarkupBlocked: Boolean,
    val committedAtUtc: String
)

data class TsaAttestation(
    val requested: Boolean,
    val status: String,
    val authority: String? = null,
    val timestampUtc: String? = null,
    val messageImprintAlgorithm: String = "SHA-256",
    val messageImprintHex: String? = null,
    val policyOid: String? = null,
    val serialNumber: String? = null,
    val tokenFormat: String = "RFC3161",
    val tokenBase64: String? = null,
    val signatureValid: Boolean? = null,
    val imprintMatchesSignedPdf: Boolean? = null,
    val certificateValidAtTimestamp: Boolean? = null,
    val error: String? = null
) {
    companion object {
        fun notRequested() = TsaAttestation(false, "not_requested")
        fun pending(imprint: String) = TsaAttestation(true, "pending", messageImprintHex = imprint)
    }
}

data class VerificationBundle(
    val status: String,
    val filename: String? = null,
    val uri: String? = null,
    val sha256: String? = null,
    val error: String? = null
) {
    companion object {
        fun pending() = VerificationBundle("pending")
        fun created(filename: String, uri: String, sha256: String) =
            VerificationBundle("created", filename = filename, uri = uri, sha256 = sha256)
        fun failed(error: String) = VerificationBundle("failed", error = error)
    }
}

data class DigitalSigningCommittedResult(
    val sourceOrigin: PdfInputOrigin,
    val sourceFilename: String,
    val sourceSha256: String,
    val signedFilename: String,
    val signedPdfUri: String,
    val signedSha256: String,
    val pageCount: Int,
    val inkPresent: Boolean,
    val inkStrokeCount: Int,
    val finalised: Boolean,
    val finalisationMode: String,
    val inkFlattened: Boolean,
    val formFieldsFlattened: Boolean,
    val permissionRestrictionsApplied: Boolean,
    val newMarkupBlocked: Boolean,
    val committedAtUtc: String,
    val tsa: TsaAttestation,
    val verificationBundle: VerificationBundle = VerificationBundle.pending()
)
