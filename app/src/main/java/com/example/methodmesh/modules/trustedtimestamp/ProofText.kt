package com.example.methodmesh.modules.trustedtimestamp

import org.json.JSONArray
import org.json.JSONObject

object ProofText {

    fun callerJson(
        source: TimestampSource,
        evidence: TrustedTimestampEvidence,
        proofFileName: String,
        sourceOrigin: String,
        sourceReturnedToCaller: Boolean
    ): String = JSONObject()
        .put("format", "MethodMesh Trusted Timestamp Result")
        .put("format_version", "1.1")
        .put("standard", "RFC 3161")
        .put("source_name", source.displayName)
        .put("source_origin", sourceOrigin)
        .put("source_returned_to_caller", sourceReturnedToCaller)
        .put("size_bytes", source.sizeBytes)
        .put("sha256", source.sha256)
        .put("tsa", evidence.authorityName)
        .put("tsa_url", evidence.authorityUrl)
        .put("generation_time", evidence.generationTimeIso)
        .put("serial_number", evidence.serialNumber)
        .put("policy_oid", evidence.policyOid)
        .put("token_sha256", evidence.tokenSha256)
        .put("trust_status", evidence.trustStatus)
        .put("proof_attachment", proofFileName)
        .toString()

    fun proofJson(
        source: TimestampSource,
        evidence: TrustedTimestampEvidence,
        componentHashes: Map<String, String>
    ): String {
        fun certificateJson(cert: TimestampCertificateInfo?): Any =
            cert?.let {
                JSONObject()
                    .put("subject", it.subject)
                    .put("issuer", it.issuer)
                    .put("serial_number", it.serialNumber)
                    .put("not_before", it.notBeforeIso)
                    .put("not_after", it.notAfterIso)
                    .put("sha256_fingerprint", it.sha256Fingerprint)
                    .put("extended_key_usage", JSONArray(it.extendedKeyUsage))
            } ?: JSONObject.NULL

        val proofFiles = JSONObject()
        componentHashes.forEach { (name, sha256) ->
            proofFiles.put(name, JSONObject().put("sha256", sha256))
        }

        return JSONObject()
            .put("format", "MethodMesh Trusted Timestamp Proof")
            .put("format_version", "1.0")
            .put("standard", "RFC 3161")
            .put("proof_type", "proof_of_existence")
            .put("claim", "The exact source bytes existed no later than the trusted timestamp.")
            .put(
                "subject",
                JSONObject()
                    .put("filename", source.displayName)
                    .put("size_bytes", source.sizeBytes)
                    .put("hash_algorithm", "SHA-256")
                    .put("sha256", source.sha256)
            )
            .put(
                "timestamp",
                JSONObject()
                    .put("authority", evidence.authorityName)
                    .put("authority_url", evidence.authorityUrl)
                    .put("generation_time", evidence.generationTimeIso)
                    .put("serial_number", evidence.serialNumber)
                    .put("policy_oid", evidence.policyOid)
                    .put("message_imprint_algorithm_oid", evidence.messageImprintAlgorithmOid)
                    .put("message_imprint", evidence.messageImprintSha256)
                    .put("nonce", evidence.nonce ?: JSONObject.NULL)
                    .put("token_sha256", evidence.tokenSha256)
                    .put("trust_status", evidence.trustStatus)
            )
            .put("tsa_signing_certificate", certificateJson(evidence.signerCertificate))
            .put("tsa_root_certificate", certificateJson(evidence.rootCertificate))
            .put("proof_files", proofFiles)
            .put(
                "verification",
                JSONObject()
                    .put("openssl", opensslCommand(source))
                    .put(
                        "note",
                        "Independently confirm the TSA trust anchor; a root certificate is not trusted merely because it is bundled here."
                    )
            )
            .toString(2)
    }

    fun readme(source: TimestampSource, evidence: TrustedTimestampEvidence): String = """
METHODMESH PROOF OF EXISTENCE
=============================

Original file: ${source.displayName}
SHA-256: ${source.sha256}
Trusted timestamp: ${evidence.generationTimeIso}
Timestamp authority: ${evidence.authorityName}
Authority endpoint: ${evidence.authorityUrl}
RFC 3161 serial: ${evidence.serialNumber}

WHAT THIS PROVES
----------------
This archive contains an RFC 3161 trusted timestamp binding the SHA-256 digest
above to the trusted time shown above.

Successful verification demonstrates that the exact bytes of "${source.displayName}"
existed NO LATER THAN that timestamp.

It does not by itself prove authorship, who possessed the content, when it was
originally created, whether a photograph is authentic, whether statements inside
a document are true, or whether a legal instrument is valid.

PRIVACY
-------
The original content was not sent to the Timestamp Authority. MethodMesh computed
its SHA-256 digest locally and transmitted the RFC 3161 timestamp request containing
that digest and protocol metadata.

VERIFY WITH OPENSSL
-------------------
1. Extract this ZIP.
2. Put the original file "${source.displayName}" beside the extracted proof files.
3. Independently confirm the TSA trust anchor/certificate fingerprint.
4. Run:

${opensslCommand(source)}

A successful result should report:

Verification: OK

The included verify.sh and verify.ps1 files perform the same basic RFC 3161 check.

IMPORTANT TRUST NOTE
--------------------
The certificates in this ZIP are included for portability and reproducibility.
A certificate is NOT trustworthy merely because it is packaged beside a proof.
Independently verify the trust anchor before relying on the timestamp authority.

ARCHIVE CONTENTS
----------------
proof.json            Machine-readable proof description and hashes.
timestamp.tsq         Exact DER RFC 3161 request, including nonce.
timestamp.tsr         Exact DER RFC 3161 response/token.
tsa-signing-cert.pem  TSA signing certificate, when available.
tsa-root.pem          TSA root/trust certificate, when available.
verify.sh             Shell verification helper.
verify.ps1            PowerShell verification helper.

The source itself is deliberately not duplicated inside this ZIP. Keep the original
file or exact text separately; it is Part 1 of the MethodMesh result, while this
proof ZIP is Part 2.

This is intentionally an ordinary ZIP file. MethodMesh is not required to inspect
or independently verify the proof later.
""".trimIndent()

    private fun opensslCommand(source: TimestampSource): String =
        """openssl ts -verify -data "${source.displayName}" -in timestamp.tsr -CAfile tsa-root.pem -untrusted tsa-signing-cert.pem"""

    fun verifySh(source: TimestampSource, evidence: TrustedTimestampEvidence): String {
        val dollar = '$'
        return """#!/bin/sh
set -eu

SOURCE="${source.displayName}"
EXPECTED_SOURCE_SHA256="${source.sha256}"
EXPECTED_ROOT_SHA256="${evidence.rootCertificate?.sha256Fingerprint.orEmpty()}"

if [ ! -f "${dollar}SOURCE" ]; then
  echo "Original file not found: ${dollar}SOURCE" >&2
  exit 2
fi

ACTUAL_SOURCE_SHA256="${dollar}(openssl dgst -sha256 "${dollar}SOURCE" | awk '{print ${dollar}NF}')"
if [ "${dollar}ACTUAL_SOURCE_SHA256" != "${dollar}EXPECTED_SOURCE_SHA256" ]; then
  echo "FAILED: source SHA-256 does not match the timestamped content." >&2
  exit 3
fi

if [ -n "${dollar}EXPECTED_ROOT_SHA256" ] && [ -f tsa-root.pem ]; then
  ACTUAL_ROOT_SHA256="${dollar}(openssl x509 -in tsa-root.pem -outform DER | openssl dgst -sha256 | awk '{print ${dollar}NF}')"
  if [ "${dollar}ACTUAL_ROOT_SHA256" != "${dollar}EXPECTED_ROOT_SHA256" ]; then
    echo "FAILED: bundled TSA root fingerprint differs from proof.json." >&2
    exit 4
  fi
fi

echo "Source SHA-256 matches proof."
echo "NOTE: independently confirm the TSA root fingerprint before treating it as trusted."
openssl ts -verify -data "${dollar}SOURCE" -in timestamp.tsr -CAfile tsa-root.pem -untrusted tsa-signing-cert.pem
""".trimIndent()
    }

    fun verifyPowerShell(source: TimestampSource): String {
        val dollar = '$'
        return """
${dollar}ErrorActionPreference = "Stop"
${dollar}Source = "${source.displayName}"
${dollar}ExpectedSourceSha256 = "${source.sha256}"

if (-not (Test-Path ${dollar}Source)) {
    throw "Original file not found: ${dollar}Source"
}

${dollar}ActualSourceSha256 = (Get-FileHash -Algorithm SHA256 ${dollar}Source).Hash.ToLowerInvariant()
if (${dollar}ActualSourceSha256 -ne ${dollar}ExpectedSourceSha256.ToLowerInvariant()) {
    throw "FAILED: source SHA-256 does not match the timestamped content."
}

Write-Host "Source SHA-256 matches proof."
Write-Host "NOTE: independently confirm the TSA root fingerprint before treating it as trusted."

& openssl ts -verify -data ${dollar}Source -in timestamp.tsr -CAfile tsa-root.pem -untrusted tsa-signing-cert.pem
if (${dollar}LASTEXITCODE -ne 0) {
    throw "RFC 3161 verification failed."
}
""".trimIndent()
    }
}
