package com.example.researchos.modules.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import com.example.researchos.core.crypto.Digests
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

data class NfcWriteResult(
    val success: Boolean,
    val message: String,
    val sizeBytes: Int,
    val tagValues: Map<String, String>,
    val overwritePolicy: String,
    val previousMessageHash: String,
    val writtenMessageHash: String,
    val verified: Boolean
)

private data class NdefWriteAttempt(
    val success: Boolean,
    val message: String,
    val previousMessageHash: String = "",
    val writtenMessageHash: String = "",
    val verified: Boolean = false
)

/**
 * NFC protocol codec.
 *
 * This object knows how to decode and write Android NFC tags. It deliberately
 * creates no research records: canonical Observation, Transformation and
 * ExecutionResult objects are owned by the NFC methods.
 */
object NfcTagRepository {
    fun readTag(tag: Tag): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        val ndef = Ndef.get(tag)
        val ndefMessage = readNdefMessage(ndef)
        val records = ndefMessage?.records?.asList().orEmpty()

        fields[NfcEvidenceFields.TAG_UID_HEX] = tag.id.toHexString()
        fields[NfcEvidenceFields.TAG_UID_DEC] = tag.id.toUnsignedLongString()
        fields[NfcEvidenceFields.TECH_LIST] = tag.techList.joinToString(",") { it.substringAfterLast('.') }
        fields[NfcEvidenceFields.NDEF_SUPPORTED] = (ndef != null).toString()
        fields[NfcEvidenceFields.NDEF_MESSAGE_SIZE_BYTES] = ndefMessage?.toByteArray()?.size?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_MAX_SIZE_BYTES] = ndef?.maxSize?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_IS_WRITABLE] = ndef?.isWritable?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_CAN_MAKE_READ_ONLY] = ndef?.canMakeReadOnly()?.toString().orEmpty()
        fields[NfcEvidenceFields.NDEF_RECORD_COUNT] = records.size.toString()
        fields[NfcEvidenceFields.NDEF_TEXT] = records.mapNotNull(::textFromRecord).joinToString(" | ")
        fields[NfcEvidenceFields.NDEF_URI] = records.mapNotNull(::uriFromRecord).joinToString(" | ")
        fields[NfcEvidenceFields.NDEF_MIME_TYPES] = records.mapNotNull(::mimeFromRecord).distinct().joinToString(",")
        fields[NfcEvidenceFields.NDEF_EXTERNAL_TYPES] = records.mapNotNull(::externalTypeFromRecord).distinct().joinToString(",")
        fields[NfcEvidenceFields.NDEF_PAYLOAD_HEX_ALL] = records.joinToString("|") { it.payload.toHexString() }
        fields[NfcEvidenceFields.NDEF_PAYLOAD_UTF8_ALL] = records.joinToString(" | ") { it.payload.decodeUtf8Guess() }
        fields[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_HEX] = records.firstOrNull()?.payload.toHexString()
        fields[NfcEvidenceFields.NDEF_FIRST_PAYLOAD_UTF8] = records.firstOrNull()?.payload.decodeUtf8Guess()
        fields[NfcEvidenceFields.NDEF_RECORDS_JSON] = recordsJson(records).toString()
        fields[NfcEvidenceFields.TAG_SUMMARY] = listOfNotNull(
            fields[NfcEvidenceFields.TAG_UID_HEX]?.takeIf(String::isNotBlank)?.let { "uid=$it" },
            fields[NfcEvidenceFields.NDEF_TEXT]?.takeIf(String::isNotBlank)?.let { "text=$it" },
            fields[NfcEvidenceFields.NDEF_URI]?.takeIf(String::isNotBlank)?.let { "uri=$it" }
        ).joinToString("; ")
        return fields
    }

    fun writeTag(tag: Tag, request: NfcWriteRequest): NfcWriteResult {
        val message = NdefMessage(arrayOf(buildRecord(request)))
        val sizeBytes = message.toByteArray().size
        val write = writeNdefMessage(tag, message, sizeBytes, request)
        return NfcWriteResult(
            success = write.success,
            message = write.message,
            sizeBytes = sizeBytes,
            tagValues = readTag(tag),
            overwritePolicy = request.overwritePolicy.wireValue,
            previousMessageHash = write.previousMessageHash,
            writtenMessageHash = write.writtenMessageHash,
            verified = write.verified
        )
    }

    private fun readNdefMessage(ndef: Ndef?): NdefMessage? {
        if (ndef == null) return null
        return try {
            if (!ndef.isConnected) ndef.connect()
            val message = ndef.ndefMessage ?: ndef.cachedNdefMessage
            closeQuietly(ndef)
            message
        } catch (_: Exception) {
            closeQuietly(ndef)
            ndef.cachedNdefMessage
        }
    }

    private fun writeNdefMessage(
        tag: Tag,
        message: NdefMessage,
        sizeBytes: Int,
        request: NfcWriteRequest
    ): NdefWriteAttempt {
        val ndef = Ndef.get(tag)
            ?: return NdefWriteAttempt(false, "Tag does not expose NDEF technology.")
        return try {
            ndef.connect()
            val existingMessage = ndef.ndefMessage ?: ndef.cachedNdefMessage
            val existingBytes = existingMessage?.toByteArray()
            val existingHash = existingBytes?.let(Digests::sha256Hex)
            val hasExistingContent = existingMessage?.records?.any { record ->
                record.tnf != NdefRecord.TNF_EMPTY ||
                    record.type.isNotEmpty() || record.id.isNotEmpty() || record.payload.isNotEmpty()
            } == true
            val overwrite = evaluateOverwritePolicy(
                policy = request.overwritePolicy,
                hasExistingContent = hasExistingContent,
                existingMessageHash = existingHash,
                expectedCurrentHash = request.expectedCurrentHash
            )
            when {
                !overwrite.allowed -> NdefWriteAttempt(
                    false, overwrite.reason, previousMessageHash = existingHash.orEmpty()
                )
                !ndef.isWritable -> NdefWriteAttempt(
                    false, "Tag is NDEF but not writable.", previousMessageHash = existingHash.orEmpty()
                )
                ndef.maxSize < sizeBytes -> NdefWriteAttempt(
                    false,
                    "Tag too small. Need $sizeBytes bytes; tag maximum is ${ndef.maxSize} bytes.",
                    previousMessageHash = existingHash.orEmpty()
                )
                else -> {
                    ndef.writeNdefMessage(message)
                    val requestedHash = Digests.sha256Hex(message.toByteArray())
                    val readBackHash = ndef.ndefMessage?.toByteArray()?.let(Digests::sha256Hex)
                    val verified = requestedHash == readBackHash
                    NdefWriteAttempt(
                        success = verified,
                        message = if (verified) {
                            "NDEF ${sizeBytes}-byte message written and verified."
                        } else {
                            "NDEF write completed, but read-back verification failed."
                        },
                        previousMessageHash = existingHash.orEmpty(),
                        writtenMessageHash = readBackHash.orEmpty(),
                        verified = verified
                    )
                }
            }
        } catch (error: IOException) {
            NdefWriteAttempt(
                false,
                "NDEF write lost communication with the tag. Keep it against the device until verification completes: ${error.message ?: "I/O error"}"
            )
        } catch (error: Exception) {
            NdefWriteAttempt(false, "NDEF write failed: ${error.message ?: error::class.java.simpleName}")
        } finally {
            closeQuietly(ndef)
        }
    }

    private fun buildRecord(request: NfcWriteRequest): NdefRecord =
        when (request.recordType.lowercase(Locale.ROOT)) {
            "uri" -> NdefRecord.createUri(request.value)
            "mime" -> NdefRecord.createMime(
                request.mimeType.ifBlank { "text/plain" },
                request.value.toByteArray(Charsets.UTF_8)
            )
            "external" -> {
                val parts = request.mimeType.split(":", limit = 2)
                val domain = parts.getOrNull(0)?.takeIf(String::isNotBlank) ?: "researchos"
                val type = parts.getOrNull(1)?.takeIf(String::isNotBlank) ?: "value"
                NdefRecord.createExternal(domain, type, request.value.toByteArray(Charsets.UTF_8))
            }
            else -> NdefRecord.createTextRecord(request.languageCode.ifBlank { "en" }, request.value)
        }

    private fun closeQuietly(technology: TagTechnology?) {
        runCatching { technology?.close() }
    }

    private fun textFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return ""
        val status = payload[0].toInt()
        val languageLength = status and 0x3F
        val charset = if ((status and 0x80) == 0) Charsets.UTF_8 else Charsets.UTF_16
        if (payload.size <= 1 + languageLength) return ""
        return String(payload, 1 + languageLength, payload.size - 1 - languageLength, charset)
    }

    private fun uriFromRecord(record: NdefRecord): String? =
        runCatching { record.toUri()?.toString() }.getOrNull()

    private fun mimeFromRecord(record: NdefRecord): String? =
        if (record.tnf == NdefRecord.TNF_MIME_MEDIA) String(record.getType() ?: ByteArray(0), Charsets.US_ASCII) else null

    private fun externalTypeFromRecord(record: NdefRecord): String? =
        if (record.tnf == NdefRecord.TNF_EXTERNAL_TYPE) String(record.getType() ?: ByteArray(0), Charsets.US_ASCII) else null

    private fun recordsJson(records: List<NdefRecord>): JSONArray = JSONArray().apply {
        records.forEachIndexed { index, record ->
            put(JSONObject(linkedMapOf<String, Any?>(
                "index" to index,
                "tnf" to record.tnf.toInt(),
                "type_hex" to record.type.toHexString(),
                "type_utf8" to record.type.decodeUtf8Guess(),
                "id_hex" to record.id.toHexString(),
                "payload_hex" to record.payload.toHexString(),
                "payload_utf8" to record.payload.decodeUtf8Guess(),
                "text" to textFromRecord(record).orEmpty(),
                "uri" to uriFromRecord(record).orEmpty(),
                "mime_type" to mimeFromRecord(record).orEmpty(),
                "external_type" to externalTypeFromRecord(record).orEmpty()
            )))
        }
    }
}

private fun ByteArray?.toHexString(): String =
    this?.joinToString(separator = "") { byte -> "%02X".format(byte) }.orEmpty()

private fun ByteArray?.toUnsignedLongString(): String {
    val bytes = this ?: return ""
    var value = 0L
    bytes.forEach { value = (value shl 8) + (it.toInt() and 0xff) }
    return value.toString()
}

private fun ByteArray?.decodeUtf8Guess(): String =
    runCatching { String(this ?: ByteArray(0), Charsets.UTF_8) }.getOrDefault("")
