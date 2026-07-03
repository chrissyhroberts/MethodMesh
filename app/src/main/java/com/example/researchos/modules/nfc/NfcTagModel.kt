package com.example.researchos.modules.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.TagTechnology
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset
import java.util.Locale

object NfcStandardFields {
    const val CAPABILITY_ID = "method_id"
    const val CAPABILITY_VERSION = "method_version"
    const val OPERATION = "operation"
    const val OBSERVED_AT_EPOCH_MS = "observed_at_epoch_ms"
    const val DEVICE_MANUFACTURER = "device_manufacturer"
    const val DEVICE_MODEL = "device_model"
    const val ANDROID_SDK_INT = "android_sdk_int"
    const val TAG_UID_HEX = "tag_uid_hex"
    const val TAG_UID_DEC = "tag_uid_dec"
    const val TECH_LIST = "tech_list"
    const val NDEF_SUPPORTED = "ndef_supported"
    const val NDEF_MESSAGE_SIZE_BYTES = "ndef_message_size_bytes"
    const val NDEF_MAX_SIZE_BYTES = "ndef_max_size_bytes"
    const val NDEF_IS_WRITABLE = "ndef_is_writable"
    const val NDEF_CAN_MAKE_READ_ONLY = "ndef_can_make_read_only"
    const val NDEF_RECORD_COUNT = "ndef_record_count"
    const val NDEF_TEXT = "ndef_text"
    const val NDEF_URI = "ndef_uri"
    const val NDEF_MIME_TYPES = "ndef_mime_types"
    const val NDEF_EXTERNAL_TYPES = "ndef_external_types"
    const val NDEF_PAYLOAD_HEX_ALL = "ndef_payload_hex_all"
    const val NDEF_PAYLOAD_UTF8_ALL = "ndef_payload_utf8_all"
    const val NDEF_FIRST_PAYLOAD_HEX = "ndef_first_payload_hex"
    const val NDEF_FIRST_PAYLOAD_UTF8 = "ndef_first_payload_utf8"
    const val NDEF_RECORDS_JSON = "ndef_records_json"
    const val TAG_SUMMARY = "tag_summary"
    const val WRITE_SUCCESS = "write_success"
    const val WRITE_MESSAGE = "write_message"
    const val WRITE_RECORD_TYPE = "write_record_type"
    const val WRITE_SIZE_BYTES = "write_size_bytes"
}

data class NfcReadObservation(
    val fields: Map<String, String>,
    val json: String
)

data class NfcWriteRequest(
    val recordType: String,
    val value: String,
    val mimeType: String = "text/plain",
    val languageCode: String = "en"
)

data class NfcWriteObservation(
    val success: Boolean,
    val message: String,
    val fields: Map<String, String>,
    val json: String
)

object NfcTagCodec {

    fun read(tag: Tag, methodId: String, methodVersion: String, operation: String): NfcReadObservation {
        val fields = linkedMapOf<String, String>()
        val observedAt = System.currentTimeMillis()
        val idBytes = tag.id ?: ByteArray(0)
        val ndef = Ndef.get(tag)
        val ndefMessage = readNdefMessage(ndef)
        val records = ndefMessage?.records?.asList() ?: emptyList()

        fields[NfcStandardFields.CAPABILITY_ID] = methodId
        fields[NfcStandardFields.CAPABILITY_VERSION] = methodVersion
        fields[NfcStandardFields.OPERATION] = operation
        fields[NfcStandardFields.OBSERVED_AT_EPOCH_MS] = observedAt.toString()
        fields[NfcStandardFields.DEVICE_MANUFACTURER] = Build.MANUFACTURER.orEmpty()
        fields[NfcStandardFields.DEVICE_MODEL] = Build.MODEL.orEmpty()
        fields[NfcStandardFields.ANDROID_SDK_INT] = Build.VERSION.SDK_INT.toString()
        fields[NfcStandardFields.TAG_UID_HEX] = idBytes.toHexString()
        fields[NfcStandardFields.TAG_UID_DEC] = idBytes.toUnsignedLongString()
        fields[NfcStandardFields.TECH_LIST] = tag.techList.joinToString(",") { it.substringAfterLast('.') }
        fields[NfcStandardFields.NDEF_SUPPORTED] = (ndef != null).toString()
        fields[NfcStandardFields.NDEF_MESSAGE_SIZE_BYTES] = ndefMessage?.toByteArray()?.size?.toString().orEmpty()
        fields[NfcStandardFields.NDEF_MAX_SIZE_BYTES] = ndef?.maxSize?.toString().orEmpty()
        fields[NfcStandardFields.NDEF_IS_WRITABLE] = ndef?.isWritable?.toString().orEmpty()
        fields[NfcStandardFields.NDEF_CAN_MAKE_READ_ONLY] = ndef?.canMakeReadOnly()?.toString().orEmpty()
        fields[NfcStandardFields.NDEF_RECORD_COUNT] = records.size.toString()
        fields[NfcStandardFields.NDEF_TEXT] = records.mapNotNull { textFromRecord(it) }.joinToString(" | ")
        fields[NfcStandardFields.NDEF_URI] = records.mapNotNull { uriFromRecord(it) }.joinToString(" | ")
        fields[NfcStandardFields.NDEF_MIME_TYPES] = records.mapNotNull { mimeFromRecord(it) }.distinct().joinToString(",")
        fields[NfcStandardFields.NDEF_EXTERNAL_TYPES] = records.mapNotNull { externalTypeFromRecord(it) }.distinct().joinToString(",")
        fields[NfcStandardFields.NDEF_PAYLOAD_HEX_ALL] = records.joinToString("|") { it.payload.toHexString() }
        fields[NfcStandardFields.NDEF_PAYLOAD_UTF8_ALL] = records.joinToString(" | ") { it.payload.decodeUtf8Guess() }
        fields[NfcStandardFields.NDEF_FIRST_PAYLOAD_HEX] = records.firstOrNull()?.payload?.toHexString().orEmpty()
        fields[NfcStandardFields.NDEF_FIRST_PAYLOAD_UTF8] = records.firstOrNull()?.payload?.decodeUtf8Guess().orEmpty()
        fields[NfcStandardFields.NDEF_RECORDS_JSON] = recordsJson(records).toString()
        fields[NfcStandardFields.TAG_SUMMARY] = listOfNotNull(
            fields[NfcStandardFields.TAG_UID_HEX]?.takeIf { it.isNotBlank() }?.let { "uid=$it" },
            fields[NfcStandardFields.NDEF_TEXT]?.takeIf { it.isNotBlank() }?.let { "text=$it" },
            fields[NfcStandardFields.NDEF_URI]?.takeIf { it.isNotBlank() }?.let { "uri=$it" }
        ).joinToString("; ")

        return NfcReadObservation(fields = fields, json = JSONObject(fields).toString())
    }

    fun write(tag: Tag, request: NfcWriteRequest, methodId: String, methodVersion: String): NfcWriteObservation {
        val record = buildRecord(request)
        val message = NdefMessage(arrayOf(record))
        val sizeBytes = message.toByteArray().size
        val writeResult = writeNdefMessage(tag, message, sizeBytes)
        val readBack = read(tag, methodId, methodVersion, operation = "write")
        val fields = linkedMapOf<String, String>()
        fields.putAll(readBack.fields)
        fields[NfcStandardFields.WRITE_SUCCESS] = writeResult.first.toString()
        fields[NfcStandardFields.WRITE_MESSAGE] = writeResult.second
        fields[NfcStandardFields.WRITE_RECORD_TYPE] = request.recordType
        fields[NfcStandardFields.WRITE_SIZE_BYTES] = sizeBytes.toString()
        return NfcWriteObservation(
            success = writeResult.first,
            message = writeResult.second,
            fields = fields,
            json = JSONObject(fields).toString()
        )
    }

    fun supportedFields(): List<String> = listOf(
        NfcStandardFields.CAPABILITY_ID,
        NfcStandardFields.CAPABILITY_VERSION,
        NfcStandardFields.OPERATION,
        NfcStandardFields.OBSERVED_AT_EPOCH_MS,
        NfcStandardFields.DEVICE_MANUFACTURER,
        NfcStandardFields.DEVICE_MODEL,
        NfcStandardFields.ANDROID_SDK_INT,
        NfcStandardFields.TAG_UID_HEX,
        NfcStandardFields.TAG_UID_DEC,
        NfcStandardFields.TECH_LIST,
        NfcStandardFields.NDEF_SUPPORTED,
        NfcStandardFields.NDEF_MESSAGE_SIZE_BYTES,
        NfcStandardFields.NDEF_MAX_SIZE_BYTES,
        NfcStandardFields.NDEF_IS_WRITABLE,
        NfcStandardFields.NDEF_CAN_MAKE_READ_ONLY,
        NfcStandardFields.NDEF_RECORD_COUNT,
        NfcStandardFields.NDEF_TEXT,
        NfcStandardFields.NDEF_URI,
        NfcStandardFields.NDEF_MIME_TYPES,
        NfcStandardFields.NDEF_EXTERNAL_TYPES,
        NfcStandardFields.NDEF_PAYLOAD_HEX_ALL,
        NfcStandardFields.NDEF_PAYLOAD_UTF8_ALL,
        NfcStandardFields.NDEF_FIRST_PAYLOAD_HEX,
        NfcStandardFields.NDEF_FIRST_PAYLOAD_UTF8,
        NfcStandardFields.NDEF_RECORDS_JSON,
        NfcStandardFields.TAG_SUMMARY
    )

    private fun buildRecord(request: NfcWriteRequest): NdefRecord {
        return when (request.recordType.lowercase(Locale.ROOT)) {
            "uri" -> NdefRecord.createUri(request.value)
            "mime" -> NdefRecord.createMime(request.mimeType.ifBlank { "text/plain" }, request.value.toByteArray(Charsets.UTF_8))
            "external" -> {
                val parts = request.mimeType.split(":", limit = 2)
                val domain = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "researchos"
                val type = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "value"
                NdefRecord.createExternal(domain, type, request.value.toByteArray(Charsets.UTF_8))
            }
            else -> NdefRecord.createTextRecord(request.languageCode.ifBlank { "en" }, request.value)
        }
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

    private fun writeNdefMessage(tag: Tag, message: NdefMessage, sizeBytes: Int): Pair<Boolean, String> {
        val ndef = Ndef.get(tag)
        if (ndef == null) return false to "Tag does not expose NDEF technology. Formatting support can be added later as a separate platform operation."
        return try {
            ndef.connect()
            when {
                !ndef.isWritable -> false to "Tag is NDEF but not writable."
                ndef.maxSize < sizeBytes -> false to "Tag too small. Need $sizeBytes bytes; tag maximum is ${ndef.maxSize} bytes."
                else -> {
                    ndef.writeNdefMessage(message)
                    true to "NDEF ${sizeBytes}-byte message written."
                }
            }
        } catch (e: Exception) {
            false to "NDEF write failed: ${e.message ?: e::class.java.simpleName}"
        } finally {
            closeQuietly(ndef)
        }
    }

    private fun closeQuietly(tech: TagTechnology?) {
        try { tech?.close() } catch (_: Exception) { }
    }

    private fun textFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_WELL_KNOWN || !record.type.contentEquals(NdefRecord.RTD_TEXT)) return null
        val payload = record.payload ?: return null
        if (payload.isEmpty()) return null
        val languageLength = payload[0].toInt() and 0x3F
        val encoding = if ((payload[0].toInt() and 0x80) == 0) Charsets.UTF_8 else Charset.forName("UTF-16")
        val textStart = 1 + languageLength
        if (textStart > payload.size) return null
        return String(payload, textStart, payload.size - textStart, encoding)
    }

    private fun uriFromRecord(record: NdefRecord): String? = runCatching { record.toUri()?.toString() }.getOrNull()

    private fun mimeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_MIME_MEDIA) return null
        return String(record.type, Charsets.US_ASCII).lowercase(Locale.ROOT)
    }

    private fun externalTypeFromRecord(record: NdefRecord): String? {
        if (record.tnf != NdefRecord.TNF_EXTERNAL_TYPE) return null
        return String(record.type, Charsets.US_ASCII)
    }

    private fun recordsJson(records: List<NdefRecord>): JSONArray {
        val array = JSONArray()
        records.forEachIndexed { index, record ->
            array.put(JSONObject().apply {
                put("index", index)
                put("tnf", record.tnf)
                put("type_hex", record.type.toHexString())
                put("id_hex", record.id.toHexString())
                put("mime", mimeFromRecord(record).orEmpty())
                put("external_type", externalTypeFromRecord(record).orEmpty())
                put("text", textFromRecord(record).orEmpty())
                put("uri", uriFromRecord(record).orEmpty())
                put("payload_hex", record.payload.toHexString())
                put("payload_utf8_guess", record.payload.decodeUtf8Guess())
                put("payload_size_bytes", record.payload.size)
            })
        }
        return array
    }

    private fun ByteArray?.toHexString(): String = this?.joinToString("") { "%02X".format(it) }.orEmpty()

    private fun ByteArray?.toUnsignedLongString(): String {
        val bytes = this ?: return ""
        var value = 0L
        bytes.forEach { value = (value shl 8) + (it.toInt() and 0xff) }
        return value.toString()
    }

    private fun ByteArray?.decodeUtf8Guess(): String = runCatching { String(this ?: ByteArray(0), Charsets.UTF_8) }.getOrDefault("")
}
