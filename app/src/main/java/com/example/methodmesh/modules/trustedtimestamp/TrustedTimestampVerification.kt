package com.example.methodmesh.modules.trustedtimestamp

import com.example.methodmesh.platform.timestamp.TrustedTimestampEngine
import com.example.methodmesh.platform.timestamp.sha256Hex
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.bouncycastle.tsp.TimeStampRequest
import org.json.JSONObject

object TrustedTimestampVerificationFields {
    const val STATUS = "trusted_timestamp_verification_status"
    const val PROOF_FILENAME = "trusted_timestamp_verification_proof_filename"
    const val SOURCE_NAME = "trusted_timestamp_verification_source_name"
    const val SOURCE_SIZE_BYTES = "trusted_timestamp_verification_source_size_bytes"
    const val EXPECTED_SHA256 = "trusted_timestamp_verification_expected_sha256"
    const val ACTUAL_SHA256 = "trusted_timestamp_verification_actual_sha256"
    const val HASH_MATCH = "trusted_timestamp_verification_hash_match"
    const val TSA = "trusted_timestamp_verification_authority"
    const val TSA_URL = "trusted_timestamp_verification_authority_url"
    const val TIME_ISO = "trusted_timestamp_verification_time_iso"
    const val SERIAL = "trusted_timestamp_verification_serial"
    const val TRUST_STATUS = "trusted_timestamp_verification_trust_status"
    const val PROOF_FILES_STATUS = "trusted_timestamp_verification_proof_files_status"
    const val FULL_JSON = "trusted_timestamp_verification_full_json"
    const val ERROR = "trusted_timestamp_verification_error"
    val outputs = listOf(STATUS, PROOF_FILENAME, SOURCE_NAME, SOURCE_SIZE_BYTES, EXPECTED_SHA256, ACTUAL_SHA256, HASH_MATCH, TSA, TSA_URL, TIME_ISO, SERIAL, TRUST_STATUS, PROOF_FILES_STATUS, FULL_JSON, ERROR)
}

data class TrustedTimestampVerification(
    val values: Map<String, String>
)

object TrustedTimestampVerifier {
    fun verify(
        proofBytes: ByteArray,
        sourceBytes: ByteArray,
        fallbackSourceName: String,
        timeoutMs: Int,
        proofName: String = "trusted_timestamp_proof.zip"
    ): TrustedTimestampVerification {
        val entries = readZip(proofBytes)
        val manifest = JSONObject(String(requireEntry(entries, "proof.json"), Charsets.UTF_8))
        val subject = manifest.optJSONObject("subject") ?: error("Proof manifest has no subject.")
        val timestamp = manifest.optJSONObject("timestamp") ?: error("Proof manifest has no timestamp.")
        val expectedHash = subject.optString("sha256").lowercase()
        val actualHash = sourceBytes.sha256Hex().lowercase()
        val sourceName = subject.optString("filename").ifBlank { fallbackSourceName.ifBlank { "source" } }
        val proofFilesStatus = verifyComponentHashes(entries, manifest.optJSONObject("proof_files"))
        val base = linkedMapOf(
            TrustedTimestampVerificationFields.PROOF_FILENAME to proofName,
            TrustedTimestampVerificationFields.SOURCE_NAME to sourceName,
            TrustedTimestampVerificationFields.SOURCE_SIZE_BYTES to sourceBytes.size.toString(),
            TrustedTimestampVerificationFields.EXPECTED_SHA256 to expectedHash,
            TrustedTimestampVerificationFields.ACTUAL_SHA256 to actualHash,
            TrustedTimestampVerificationFields.HASH_MATCH to (expectedHash == actualHash).toString(),
            TrustedTimestampVerificationFields.TSA to timestamp.optString("authority"),
            TrustedTimestampVerificationFields.TSA_URL to timestamp.optString("authority_url"),
            TrustedTimestampVerificationFields.TIME_ISO to timestamp.optString("generation_time"),
            TrustedTimestampVerificationFields.SERIAL to timestamp.optString("serial_number"),
            TrustedTimestampVerificationFields.TRUST_STATUS to "not_checked",
            TrustedTimestampVerificationFields.PROOF_FILES_STATUS to proofFilesStatus,
            TrustedTimestampVerificationFields.FULL_JSON to "",
            TrustedTimestampVerificationFields.ERROR to ""
        )
        if (expectedHash.isBlank() || expectedHash != actualHash) {
            return finish(base, "failed_source_hash_mismatch", "The supplied source bytes do not match the proof's SHA-256 subject hash.")
        }
        if (proofFilesStatus.startsWith("mismatch:")) {
            return finish(base, "failed_proof_bundle_integrity", "One or more files in the proof bundle do not match proof.json.")
        }
        val authorityUrl = timestamp.optString("authority_url").ifBlank { error("Proof manifest has no timestamp authority URL.") }
        val request = TimeStampRequest(requireEntry(entries, "timestamp.tsq"))
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes)
        val evidence = TrustedTimestampEngine.parseAndValidate(request, requireEntry(entries, "timestamp.tsr"), digest, authorityUrl, timeoutMs)
        base[TrustedTimestampVerificationFields.TSA] = evidence.authorityName
        base[TrustedTimestampVerificationFields.TRUST_STATUS] = evidence.trustStatus
        base[TrustedTimestampVerificationFields.TIME_ISO] = evidence.generationTimeIso
        base[TrustedTimestampVerificationFields.SERIAL] = evidence.serialNumber
        val status = if (evidence.trustStatus == "trusted_registry_match") "verified_trusted" else "verified_cryptographically_unconfigured"
        return finish(base, status, "")
    }

    private fun finish(base: LinkedHashMap<String, String>, status: String, error: String): TrustedTimestampVerification {
        base[TrustedTimestampVerificationFields.STATUS] = status
        base[TrustedTimestampVerificationFields.ERROR] = error
        val json = JSONObject().apply {
            base.forEach { (key, value) -> put(key, value) }
        }.toString()
        base[TrustedTimestampVerificationFields.FULL_JSON] = json
        return TrustedTimestampVerification(base.toMap())
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
            }
        }
        return entries
    }

    private fun requireEntry(entries: Map<String, ByteArray>, name: String): ByteArray = entries[name] ?: error("Proof bundle is missing $name.")

    private fun verifyComponentHashes(entries: Map<String, ByteArray>, manifest: JSONObject?): String {
        if (manifest == null) return "not_declared"
        val names = manifest.keys().asSequence().toList()
        val mismatches = names.filter { name ->
            val expected = manifest.optJSONObject(name)?.optString("sha256").orEmpty()
            val actual = entries[name]?.sha256Hex().orEmpty()
            expected.isBlank() || actual.isBlank() || !expected.equals(actual, ignoreCase = true)
        }
        return if (mismatches.isEmpty()) "match" else "mismatch:${mismatches.joinToString(",")}" 
    }
}
