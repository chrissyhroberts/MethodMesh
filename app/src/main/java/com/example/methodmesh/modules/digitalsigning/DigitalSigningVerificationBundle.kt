package com.example.methodmesh.modules.digitalsigning

import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Portable evidence package for a committed PDF.
 *
 * The ZIP intentionally contains the exact signed PDF bytes, structured TSA data,
 * the raw RFC 3161 token when available, deterministic hashes and plain-language
 * verification instructions. It is a companion evidence package, not a second
 * signature format.
 */
object DigitalSigningVerificationBundle {
    data class Created(val file: File, val sha256: String)

    fun create(
        signedPdfFile: File,
        result: DigitalSigningCommittedResult,
        outputFile: File
    ): Created {
        require(signedPdfFile.isFile && signedPdfFile.length() > 0L) { "Committed signed PDF is unavailable." }
        outputFile.parentFile?.mkdirs()
        val tokenBytes = result.tsa.tokenBase64
            ?.takeIf { it.isNotBlank() }
            ?.let { Base64.getDecoder().decode(it) }

        ZipOutputStream(BufferedOutputStream(outputFile.outputStream())).use { zip ->
            addFile(zip, result.signedFilename, signedPdfFile)
            addText(zip, "manifest.json", manifest(result).toString(2))
            addText(zip, "tsa.json", DigitalSigningResultJson.tsaJson(result.tsa).toString(2))
            tokenBytes?.let { addBytes(zip, "timestamp-token.tst", it) }
            addText(zip, "SHA256SUMS.txt", sha256Sums(result, tokenBytes))
            addText(zip, "VERIFY.txt", verificationInstructions(result, tokenBytes != null))
        }
        return Created(outputFile, DigitalSigningHash.sha256(outputFile))
    }

    private fun manifest(result: DigitalSigningCommittedResult): JSONObject = JSONObject().apply {
        put("schema", "methodmesh.digital_signing.provenance_bundle.v2")
        put("deliverable", "B")
        put("role", "provenance_bundle")
        put("method", As100DigitalSigningMethod.ID)
        put("signed_pdf", JSONObject().apply {
            put("deliverable", "A")
            put("role", "signed_document")
            put("filename", result.signedFilename)
            put("sha256", result.signedSha256)
            put("page_count", result.pageCount)
            put("committed_at", result.committedAtUtc)
            put("finalised", result.finalised)
            put("finalisation_mode", result.finalisationMode)
        })
        put("source", JSONObject().apply {
            put("origin", result.sourceOrigin.id)
            put("filename", result.sourceFilename)
            put("sha256", result.sourceSha256)
        })
        put("tsa", DigitalSigningResultJson.tsaJson(result.tsa))
    }

    private fun sha256Sums(result: DigitalSigningCommittedResult, tokenBytes: ByteArray?): String = buildString {
        append(result.signedSha256.lowercase())
        append("  ")
        append(result.signedFilename)
        append('\n')
        if (tokenBytes != null) {
            append(DigitalSigningHash.sha256(tokenBytes).lowercase())
            append("  timestamp-token.tst\n")
        }
    }

    private fun verificationInstructions(result: DigitalSigningCommittedResult, hasToken: Boolean): String = buildString {
        appendLine("MethodMesh Digital Signing — Deliverable B: Provenance ZIP")
        appendLine("=============================================================")
        appendLine()
        appendLine("Deliverable A is the signed PDF returned separately by MethodMesh. Deliverable B is this provenance ZIP. This ZIP also contains an exact copy of Deliverable A so that the evidence can be verified independently even after the two deliverables are moved or archived separately.")
        appendLine()
        appendLine("1. Verify the PDF SHA-256")
        appendLine("-------------------------")
        appendLine("Expected SHA-256:")
        appendLine(result.signedSha256)
        appendLine()
        appendLine("Linux/macOS:")
        appendLine("  sha256sum '${result.signedFilename}'")
        appendLine("or:")
        appendLine("  openssl dgst -sha256 '${result.signedFilename}'")
        appendLine()
        appendLine("Windows:")
        appendLine("  certutil -hashfile \"${result.signedFilename}\" SHA256")
        appendLine()
        appendLine("The computed digest must exactly match the expected value above and the value in manifest.json / tsa.json.")
        appendLine()
        appendLine("2. Inspect the timestamp evidence")
        appendLine("---------------------------------")
        appendLine("TSA status: ${result.tsa.status}")
        appendLine("TSA authority: ${result.tsa.authority.orEmpty().ifBlank { "not available" }}")
        appendLine("Timestamp UTC: ${result.tsa.timestampUtc.orEmpty().ifBlank { "not available" }}")
        appendLine("Message imprint: ${result.tsa.messageImprintHex.orEmpty().ifBlank { "not available" }}")
        appendLine()
        if (hasToken) {
            appendLine("The raw RFC 3161 CMS timestamp token is timestamp-token.tst.")
            appendLine("Inspect it with OpenSSL:")
            appendLine("  openssl ts -reply -token_in -in timestamp-token.tst -text")
            appendLine()
            appendLine("Verify that the token binds this PDF using a trust chain you independently trust for the timestamp authority:")
            appendLine("  openssl ts -verify -token_in -data '${result.signedFilename}' -in timestamp-token.tst -CAfile <trusted-tsa-chain.pem>")
            appendLine()
            appendLine("Do not treat a certificate embedded inside the token as a trust anchor by itself. Obtain/validate the TSA trust chain independently according to your organisation's policy.")
        } else {
            appendLine("No raw timestamp token is present. See tsa.json for whether timestamping was not requested or the TSA request failed.")
        }
        appendLine()
        appendLine("3. What the timestamp proves")
        appendLine("----------------------------")
        appendLine("A valid RFC 3161 timestamp proves that the TSA attested the PDF's cryptographic digest at the stated time. It does not by itself prove the identity or intent of the person who drew the visible ink.")
        appendLine()
        appendLine("Any later byte-level change to the PDF changes its SHA-256 and causes the timestamp message-imprint check to fail.")
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    private fun addText(zip: ZipOutputStream, name: String, text: String) =
        addBytes(zip, name, text.toByteArray(Charsets.UTF_8))

    private fun addBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
