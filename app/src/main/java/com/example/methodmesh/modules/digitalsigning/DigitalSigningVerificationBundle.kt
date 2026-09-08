package com.example.methodmesh.modules.digitalsigning

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.tsp.TimeStampToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Portable evidence package for Deliverable A (the committed PDF).
 *
 * Deliverable B intentionally does NOT duplicate Deliverable A. Instead it is a
 * verification kit that can travel beside the signed PDF: hashes, RFC 3161 token,
 * the certificates embedded by the TSA, machine-readable metadata, a self-contained
 * shell verifier and plain-English instructions.
 */
object DigitalSigningVerificationBundle {
    data class Created(val file: File, val sha256: String)

    private data class CertificateMaterial(
        val signerPem: String,
        val allIncludedPem: String,
        val individualFiles: LinkedHashMap<String, ByteArray>,
        val manifestEntries: JSONArray,
        val summary: String
    )

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
        val certificates = tokenBytes?.let(::extractCertificateMaterial)

        val files = linkedMapOf<String, ByteArray>()
        files["manifest.json"] = manifest(result, certificates).toString(2).toByteArray(Charsets.UTF_8)
        files["tsa.json"] = DigitalSigningResultJson.tsaJson(result.tsa).toString(2).toByteArray(Charsets.UTF_8)
        files["SIGNED_PDF_SHA256.txt"] = "${result.signedSha256.lowercase()}  ${result.signedFilename}\n".toByteArray(Charsets.UTF_8)

        if (tokenBytes != null) {
            files["timestamp-token.tst"] = tokenBytes
        }
        certificates?.let { material: CertificateMaterial ->
            files["certificates/tsa-signer.pem"] = material.signerPem.toByteArray(Charsets.UTF_8)
            files["certificates/tsa-included-certificates.pem"] = material.allIncludedPem.toByteArray(Charsets.UTF_8)
            files.putAll(material.individualFiles)
            files["certificates/CERTIFICATES.txt"] = material.summary.toByteArray(Charsets.UTF_8)
        }

        files["verify.sh"] = verificationScript(result, tokenBytes != null, certificates != null).toByteArray(Charsets.UTF_8)
        files["verify.py"] = verificationPythonScript(result, tokenBytes != null, certificates != null).toByteArray(Charsets.UTF_8)
        files["VERIFY.txt"] = verificationInstructions(result, tokenBytes != null, certificates != null).toByteArray(Charsets.UTF_8)
        files["SHA256SUMS.txt"] = sha256Sums(result, files).toByteArray(Charsets.UTF_8)

        ZipOutputStream(BufferedOutputStream(outputFile.outputStream())).use { zip ->
            files.forEach { (name, bytes) -> addBytes(zip, name, bytes) }
        }
        return Created(outputFile, DigitalSigningHash.sha256(outputFile))
    }

    private fun manifest(
        result: DigitalSigningCommittedResult,
        certificates: CertificateMaterial?
    ): JSONObject = JSONObject().apply {
        put("schema", "methodmesh.digital_signing.provenance_bundle.v4")
        put("deliverable", "B")
        put("role", "provenance_and_verification_bundle")
        put("contains_signed_pdf", false)
        put("method", As100DigitalSigningMethod.ID)
        put("verification_command", "sh verify.sh '${result.signedFilename}'")
        put("verification_command_python", "python3 verify.py ${JSONObject.quote(result.signedFilename)}")
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
        put("tsa_certificates", certificates?.manifestEntries ?: JSONArray())
    }

    /**
     * Extract every X.509 certificate embedded in the RFC 3161 CMS token. The TSA
     * signer is written first and separately; the remaining certificates are
     * evidence for chain construction, not automatically trusted roots.
     */
    private fun extractCertificateMaterial(tokenBytes: ByteArray): CertificateMaterial? = runCatching {
        val token = TimeStampToken(CMSSignedData(tokenBytes))
        val included: List<X509CertificateHolder> = token.certificates.getMatches(null).toList()
        val signer = included.firstOrNull { holder: X509CertificateHolder -> token.sid.match(holder) }
            ?: return@runCatching null
        val ordered: List<X509CertificateHolder> = buildList {
            add(signer)
            included.filterNotTo(this) { it == signer }
        }
        val converter = JcaX509CertificateConverter().setProvider(BouncyCastleProvider())
        val individual = linkedMapOf<String, ByteArray>()
        val entries = JSONArray()
        val summary = buildString {
            appendLine("MethodMesh Digital Signing — TSA certificates embedded in timestamp-token.tst")
            appendLine("============================================================================")
            appendLine()
            appendLine("These certificates came from the RFC 3161 token returned by the timestamp authority.")
            appendLine("They are supplied to make verification reproducible. Their presence in this ZIP does not, by itself, make them trusted roots.")
            appendLine()
            ordered.forEachIndexed { index: Int, holder: X509CertificateHolder ->
                val certificate = converter.getCertificate(holder)
                val role = if (index == 0) "signer" else "included-chain"
                val filename = "certificates/cert-${(index + 1).toString().padStart(2, '0')}-$role.pem"
                val pem = pemCertificate(holder.encoded)
                individual[filename] = pem.toByteArray(Charsets.UTF_8)
                val fingerprint = DigitalSigningHash.sha256(holder.encoded)
                entries.put(JSONObject().apply {
                    put("role", role)
                    put("filename", filename)
                    put("subject", certificate.subjectX500Principal.name)
                    put("issuer", certificate.issuerX500Principal.name)
                    put("serial_number_hex", certificate.serialNumber.toString(16))
                    put("sha256_fingerprint", fingerprint)
                    put("not_before", java.time.Instant.ofEpochMilli(certificate.notBefore.time).toString())
                    put("not_after", java.time.Instant.ofEpochMilli(certificate.notAfter.time).toString())
                })
                appendLine("Certificate ${index + 1} — $role")
                appendLine("  File: ${filename.substringAfterLast('/')}")
                appendLine("  Subject: ${certificate.subjectX500Principal.name}")
                appendLine("  Issuer: ${certificate.issuerX500Principal.name}")
                appendLine("  Serial: ${certificate.serialNumber.toString(16)}")
                appendLine("  SHA-256: $fingerprint")
                appendLine("  Valid: ${java.time.Instant.ofEpochMilli(certificate.notBefore.time)} to ${java.time.Instant.ofEpochMilli(certificate.notAfter.time)}")
                appendLine()
            }
        }
        CertificateMaterial(
            signerPem = pemCertificate(signer.encoded),
            allIncludedPem = ordered.joinToString(separator = "") { pemCertificate(it.encoded) },
            individualFiles = individual,
            manifestEntries = entries,
            summary = summary
        )
    }.getOrNull()

    private fun pemCertificate(der: ByteArray): String {
        val body = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII)).encodeToString(der)
        return "-----BEGIN CERTIFICATE-----\n$body\n-----END CERTIFICATE-----\n"
    }

    private fun sha256Sums(result: DigitalSigningCommittedResult, files: Map<String, ByteArray>): String = buildString {
        // This first line deliberately names Deliverable A even though it is external
        // to the ZIP. After extraction, placing A in the folder makes this a normal
        // checksum manifest for the signed document plus all verification evidence.
        append(result.signedSha256.lowercase())
        append("  ")
        append(result.signedFilename)
        append('\n')
        files.forEach { (name, bytes) ->
            if (name == "SHA256SUMS.txt") return@forEach
            append(DigitalSigningHash.sha256(bytes).lowercase())
            append("  ")
            append(name)
            append('\n')
        }
    }

    private fun verificationScript(
        result: DigitalSigningCommittedResult,
        hasToken: Boolean,
        hasCertificates: Boolean
    ): String = buildString {
        appendLine("#!/bin/sh")
        appendLine("set -eu")
        appendLine()
        appendLine("EXPECTED_NAME='${shellSingleQuote(result.signedFilename)}'")
        appendLine("EXPECTED_SHA='${result.signedSha256.lowercase()}'")
        appendLine("PDF=\"${'$'}{1:-${'$'}EXPECTED_NAME}\"")
        appendLine("TRUST_CA=\"${'$'}{2:-}\"")
        appendLine()
        appendLine("if [ ! -f \"${'$'}PDF\" ]; then")
        appendLine("  echo \"FAIL: signed PDF not found: ${'$'}PDF\"")
        appendLine("  echo \"Put Deliverable A in this folder, or pass its path: sh verify.sh /path/to/file.pdf\"")
        appendLine("  exit 2")
        appendLine("fi")
        appendLine()
        appendLine("if command -v sha256sum >/dev/null 2>&1; then")
        appendLine("  ACTUAL_SHA=${'$'}(sha256sum \"${'$'}PDF\" | awk '{print ${'$'}1}')")
        appendLine("elif command -v shasum >/dev/null 2>&1; then")
        appendLine("  ACTUAL_SHA=${'$'}(shasum -a 256 \"${'$'}PDF\" | awk '{print ${'$'}1}')")
        appendLine("elif command -v openssl >/dev/null 2>&1; then")
        appendLine("  ACTUAL_SHA=${'$'}(openssl dgst -sha256 \"${'$'}PDF\" | sed 's/^.*= //')")
        appendLine("else")
        appendLine("  echo 'FAIL: need sha256sum, shasum, or openssl.'")
        appendLine("  exit 3")
        appendLine("fi")
        appendLine("ACTUAL_SHA=${'$'}(printf '%s' \"${'$'}ACTUAL_SHA\" | tr 'A-F' 'a-f')")
        appendLine("if [ \"${'$'}ACTUAL_SHA\" != \"${'$'}EXPECTED_SHA\" ]; then")
        appendLine("  echo 'FAIL: Deliverable A SHA-256 does not match the committed document.'")
        appendLine("  echo \"Expected: ${'$'}EXPECTED_SHA\"")
        appendLine("  echo \"Actual:   ${'$'}ACTUAL_SHA\"")
        appendLine("  exit 4")
        appendLine("fi")
        appendLine("echo 'PASS: Deliverable A SHA-256 matches the committed document.'")
        appendLine()
        if (hasToken && hasCertificates) {
            appendLine("if ! command -v openssl >/dev/null 2>&1; then")
            appendLine("  echo 'FAIL: OpenSSL is required for RFC 3161 verification.'")
            appendLine("  exit 5")
            appendLine("fi")
            appendLine()
            appendLine("if [ -n \"${'$'}TRUST_CA\" ]; then")
            appendLine("  echo 'Verifying RFC 3161 token using independently supplied trusted CA file…'")
            appendLine("  openssl ts -verify -token_in -data \"${'$'}PDF\" -in timestamp-token.tst -untrusted certificates/tsa-included-certificates.pem -CAfile \"${'$'}TRUST_CA\"")
            appendLine("  echo 'PASS: RFC 3161 token, document imprint and supplied TSA trust chain verified.'")
            appendLine("else")
            appendLine("  echo 'Verifying RFC 3161 token and document imprint against the signer certificate packaged with the token…'")
            appendLine("  openssl ts -verify -token_in -data \"${'$'}PDF\" -in timestamp-token.tst -CAfile certificates/tsa-included-certificates.pem -partial_chain")
            appendLine("  echo 'PASS: RFC 3161 token signature and document imprint match the certificates embedded by the TSA.'")
            appendLine("  echo 'NOTE: this self-contained check does not independently establish trust in the TSA certificate chain.'")
            appendLine("  echo 'For independent trust validation, run: sh verify.sh \"${'$'}PDF\" /path/to/trusted-tsa-root-or-ca-bundle.pem'")
            appendLine("fi")
        } else if (hasToken) {
            appendLine("echo 'WARNING: an RFC 3161 token is present but no embedded TSA signer certificate could be extracted.'")
            appendLine("echo 'The document hash is verified, but this script cannot perform the self-contained token-signature check.'")
        } else {
            appendLine("echo 'NOTE: no RFC 3161 token is present; only the committed PDF hash can be verified from this bundle.'")
        }
    }

    private fun verificationPythonScript(
        result: DigitalSigningCommittedResult,
        hasToken: Boolean,
        hasCertificates: Boolean
    ): String = buildString {
        appendLine("#!/usr/bin/env python3")
        appendLine("import hashlib")
        appendLine("from pathlib import Path")
        appendLine("import shutil")
        appendLine("import subprocess")
        appendLine("import sys")
        appendLine()
        appendLine("BASE = Path(__file__).resolve().parent")
        appendLine("EXPECTED_NAME = ${JSONObject.quote(result.signedFilename)}")
        appendLine("EXPECTED_SHA = ${JSONObject.quote(result.signedSha256.lowercase())}")
        appendLine("arg = sys.argv[1] if len(sys.argv) > 1 else EXPECTED_NAME")
        appendLine("pdf = Path(arg)")
        appendLine("if not pdf.is_absolute() and not pdf.exists():")
        appendLine("    candidate = BASE / pdf")
        appendLine("    if candidate.exists():")
        appendLine("        pdf = candidate")
        appendLine("trust_ca = sys.argv[2] if len(sys.argv) > 2 else None")
        appendLine()
        appendLine("if not pdf.is_file():")
        appendLine("    print(f'FAIL: signed PDF not found: {pdf}')")
        appendLine("    print(f'Put Deliverable A beside this script, or run: python3 verify.py \\\"/path/to/{EXPECTED_NAME}\\\"')")
        appendLine("    raise SystemExit(2)")
        appendLine()
        appendLine("actual_sha = hashlib.sha256(pdf.read_bytes()).hexdigest().lower()")
        appendLine("if actual_sha != EXPECTED_SHA:")
        appendLine("    print('FAIL: Deliverable A SHA-256 does not match the committed document.')")
        appendLine("    print(f'Expected: {EXPECTED_SHA}')")
        appendLine("    print(f'Actual:   {actual_sha}')")
        appendLine("    raise SystemExit(4)")
        appendLine("print('PASS: Deliverable A SHA-256 matches the committed document.')")
        appendLine()
        if (hasToken && hasCertificates) {
            appendLine("openssl = shutil.which('openssl')")
            appendLine("if not openssl:")
            appendLine("    print('FAIL: OpenSSL is required for RFC 3161 verification.')")
            appendLine("    raise SystemExit(5)")
            appendLine("token = BASE / 'timestamp-token.tst'")
            appendLine("included = BASE / 'certificates' / 'tsa-included-certificates.pem'")
            appendLine("if trust_ca:")
            appendLine("    print('Verifying RFC 3161 token using independently supplied trusted CA file…')")
            appendLine("    cmd = [openssl, 'ts', '-verify', '-token_in', '-data', str(pdf), '-in', str(token), '-untrusted', str(included), '-CAfile', trust_ca]")
            appendLine("else:")
            appendLine("    print('Verifying RFC 3161 token and document imprint against the signer certificate packaged with the token…')")
            appendLine("    cmd = [openssl, 'ts', '-verify', '-token_in', '-data', str(pdf), '-in', str(token), '-CAfile', str(included), '-partial_chain']")
            appendLine("completed = subprocess.run(cmd)")
            appendLine("if completed.returncode != 0:")
            appendLine("    print('FAIL: RFC 3161 verification failed.')")
            appendLine("    raise SystemExit(completed.returncode)")
            appendLine("if trust_ca:")
            appendLine("    print('PASS: RFC 3161 token, document imprint and supplied TSA trust chain verified.')")
            appendLine("else:")
            appendLine("    print('PASS: RFC 3161 token signature and document imprint match the certificates embedded by the TSA.')")
            appendLine("    print('NOTE: this self-contained check does not independently establish trust in the TSA certificate chain.')")
            appendLine("    print(f'For independent trust validation, run: python3 verify.py \\\"{pdf}\\\" /path/to/trusted-tsa-root-or-ca-bundle.pem')")
        } else if (hasToken) {
            appendLine("print('WARNING: an RFC 3161 token is present but no embedded TSA signer certificate could be extracted.')")
            appendLine("print('The document hash is verified, but this script cannot perform the self-contained token-signature check.')")
        } else {
            appendLine("print('NOTE: no RFC 3161 token is present; only the committed PDF hash can be verified from this bundle.')")
        }
    }

    private fun verificationInstructions(
        result: DigitalSigningCommittedResult,
        hasToken: Boolean,
        hasCertificates: Boolean
    ): String = buildString {
        appendLine("VERIFY DELIVERABLE A — MethodMesh Digital Signing")
        appendLine("=================================================")
        appendLine()
        appendLine("Deliverable A is the signed PDF. Deliverable B is this provenance/verification ZIP.")
        appendLine("This ZIP deliberately does not contain another copy of the PDF.")
        appendLine()
        appendLine("TO VERIFY THE SIGNED PDF")
        appendLine("------------------------")
        appendLine("1. Extract this ZIP into a folder.")
        appendLine("2. Put Deliverable A in that folder. Its expected filename is:")
        appendLine("   ${result.signedFilename}")
        appendLine("3. Open Terminal in that folder.")
        appendLine("4. Run either of these commands:")
        appendLine()
        appendLine("   sh verify.sh '${result.signedFilename}'")
        appendLine("   python3 verify.py ${JSONObject.quote(result.signedFilename)}")
        appendLine()
        appendLine("IMPORTANT: verify.sh is a shell script. Do NOT run `python verify.sh` or `python3 verify.sh`.")
        appendLine("If you prefer Python, use verify.py instead.")
        appendLine()
        appendLine("The script first proves that the PDF bytes exactly match the SHA-256 committed by MethodMesh.")
        if (hasToken && hasCertificates) {
            appendLine("It then verifies that timestamp-token.tst is a valid RFC 3161 token whose message imprint is the hash of that exact PDF, using certificates embedded by the TSA.")
            appendLine()
            appendLine("For an INDEPENDENT TSA trust-chain check, provide a trusted CA/root bundle as a second argument:")
            appendLine()
            appendLine("   sh verify.sh '${result.signedFilename}' /path/to/trusted-tsa-ca.pem")
            appendLine("   python3 verify.py ${JSONObject.quote(result.signedFilename)} /path/to/trusted-tsa-ca.pem")
            appendLine()
            appendLine("The PEMs under certificates/ are certificates returned inside the timestamp token. They are evidence for chain construction; merely bundling a certificate does not make it a trusted root.")
        } else if (hasToken) {
            appendLine("A timestamp token is present, but the signer certificate was not extractable from it. The PDF SHA-256 check still works; inspect tsa.json and timestamp-token.tst for the timestamp evidence.")
        } else {
            appendLine("No timestamp token is present. The script therefore verifies the committed PDF SHA-256 only. See tsa.json for whether timestamping was not requested or failed.")
        }
        appendLine()
        appendLine("EXPECTED PDF SHA-256")
        appendLine("-------------------")
        appendLine(result.signedSha256.lowercase())
        appendLine()
        appendLine("BUNDLE CONTENTS")
        appendLine("---------------")
        appendLine("manifest.json                         machine-readable provenance manifest")
        appendLine("tsa.json                              RFC 3161 result/metadata")
        appendLine("SIGNED_PDF_SHA256.txt                 expected hash and filename for Deliverable A")
        appendLine("SHA256SUMS.txt                        checksums for A plus the verification evidence")
        appendLine("verify.sh                             shell one-command verifier")
        appendLine("verify.py                             Python 3 one-command verifier")
        if (hasToken) appendLine("timestamp-token.tst                    raw DER RFC 3161/CMS token")
        if (hasCertificates) {
            appendLine("certificates/tsa-signer.pem            timestamp signer certificate")
            appendLine("certificates/tsa-included-certificates.pem all certificates embedded in the token")
            appendLine("certificates/cert-*.pem                each embedded certificate separately")
            appendLine("certificates/CERTIFICATES.txt          subjects, issuers, validity and fingerprints")
        }
        appendLine()
        appendLine("WHAT THIS PROVES")
        appendLine("----------------")
        appendLine("A matching SHA-256 proves that Deliverable A is byte-for-byte the PDF committed by MethodMesh.")
        appendLine("A valid RFC 3161 token additionally proves that the TSA attested that PDF digest at the token's stated time.")
        appendLine("The visible handwritten mark remains an electronic ink/markup mark; RFC 3161 does not by itself establish the human signer's identity or intent.")
    }

    private fun shellSingleQuote(value: String): String = value.replace("'", "'\\''")

    private fun addBytes(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }
}
