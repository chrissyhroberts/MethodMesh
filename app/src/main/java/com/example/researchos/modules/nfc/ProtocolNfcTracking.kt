package com.example.researchos.modules.nfc

import android.nfc.NdefRecord
import com.example.researchos.core.crypto.Digests
import java.time.Instant
import java.math.BigInteger
import java.util.Base64

/**
 * Offline protocol receipt. The first bit region contains active flags; the
 * second contains monotonic completed-step bits. Widths are encoded so the
 * card remains self-describing across phones.
 */
data class ProtocolNfcState(
    val protocolId: String,
    val protocolVersion: String,
    val flagBitsHex: String,
    val completionBitsHex: String,
    val flagBitCount: Int,
    val completionBitCount: Int,
    val stateVersion: Long,
    val lastEventHash: String,
    val updatedAtIso: String
) {
    val bitsHex: String get() = flagBitsHex + completionBitsHex
}

/** Study-owned meaning of each bit. Names and severities never need to be stored on the card. */
data class ProtocolNfcFlagDefinition(
    val bitIndex: Int,
    val code: String,
    val label: String,
    val severity: String = "WARNING"
)

data class ProtocolNfcStepDefinition(
    val stepId: String,
    val bitMaskHex: String,
    val label: String,
    val requiredExpression: String = ""
)

data class ProtocolNfcDefinition(
    val protocolId: String,
    val protocolVersion: String,
    val flagBitCount: Int,
    val completionBitCount: Int,
    val flags: List<ProtocolNfcFlagDefinition> = emptyList(),
    val steps: List<ProtocolNfcStepDefinition> = emptyList()
)

object ProtocolNfcStateCodec {
    const val RECORD_TYPE = "application/researchos.protocol-state"
    private const val PREFIX = "ROSP2"

    fun empty(protocolId: String, protocolVersion: String, flagBitCount: Int = 8, completionBitCount: Int = 8): ProtocolNfcState =
        ProtocolNfcState(protocolId, protocolVersion, zeroHex(flagBitCount), zeroHex(completionBitCount), flagBitCount, completionBitCount, 0L, "GENESIS", "")

    fun encode(state: ProtocolNfcState): String = listOf(
        PREFIX, b64(state.protocolId), b64(state.protocolVersion),
        state.flagBitCount.toString(), state.completionBitCount.toString(),
        state.flagBitsHex.uppercase(), state.completionBitsHex.uppercase(),
        state.stateVersion.toString(), state.lastEventHash.ifBlank { "GENESIS" }, b64(state.updatedAtIso)
    ).joinToString(".")

    fun decode(value: String): ProtocolNfcState? {
        val parts = value.trim().split('.')
        if (parts.size != 10 || parts[0] != PREFIX) return null
        val flags = parts[5].let(::normaliseHex) ?: return null
        val completion = parts[6].let(::normaliseHex) ?: return null
        val flagCount = parts[3].toIntOrNull() ?: return null
        val completionCount = parts[4].toIntOrNull() ?: return null
        if (!validWidth(flagCount, flags) || !validWidth(completionCount, completion)) return null
        return ProtocolNfcState(
            protocolId = unb64(parts[1]) ?: return null,
            protocolVersion = unb64(parts[2]) ?: return null,
            flagBitsHex = flags,
            completionBitsHex = completion,
            flagBitCount = flagCount,
            completionBitCount = completionCount,
            stateVersion = parts[7].toLongOrNull() ?: return null,
            lastEventHash = parts[8].ifBlank { "GENESIS" },
            updatedAtIso = unb64(parts[9]).orEmpty()
        )
    }

    fun stateFrom(tag: android.nfc.Tag): ProtocolNfcState? =
        NfcTagRepository.readNdefRecords(tag).firstOrNull { isProtocolRecord(it) }
            ?.payload?.toString(Charsets.UTF_8)?.let(::decode)

    fun isProtocolRecord(record: NdefRecord): Boolean =
        record.tnf == NdefRecord.TNF_MIME_MEDIA && String(record.type, Charsets.US_ASCII).equals(RECORD_TYPE, ignoreCase = true)

    fun requiredBitsMatch(state: ProtocolNfcState, maskHex: String, valueHex: String): Boolean =
        maskedMatch(state.completionBitsHex, maskHex, valueHex)

    fun expressionMatches(state: ProtocolNfcState, expression: String, fallbackMask: String, fallbackValue: String): Boolean {
        val raw = expression.trim()
        if (raw.isBlank()) return requiredBitsMatch(state, fallbackMask, fallbackValue)
        val open = raw.indexOf('(')
        val close = raw.lastIndexOf(')')
        if (open <= 0 || close != raw.length - 1) return false
        val terms = raw.substring(open + 1, close).split(',').map(String::trim).filter(String::isNotBlank)
        if (terms.isEmpty()) return false
        return when (raw.substring(0, open).uppercase()) {
            "ALL" -> terms.all { maskedMatch(state.completionBitsHex, it, it) }
            "ANY" -> terms.any { maskedMatch(state.completionBitsHex, it, it) }
            "NONE" -> terms.none { maskedMatch(state.completionBitsHex, it, it) }
            else -> false
        }
    }

    fun complete(state: ProtocolNfcState, completionMaskHex: String, eventHash: String, setFlagsHex: String = "", clearFlagsHex: String = ""): ProtocolNfcState {
        val nextCompletion = orHex(state.completionBitsHex, completionMaskHex) ?: state.completionBitsHex
        val withFlags = orHex(state.flagBitsHex, setFlagsHex.ifBlank { zeroHex(state.flagBitCount) }) ?: state.flagBitsHex
        val nextFlags = andNotHex(withFlags, clearFlagsHex.ifBlank { zeroHex(state.flagBitCount) }) ?: withFlags
        return state.copy(
            flagBitsHex = nextFlags,
            completionBitsHex = nextCompletion,
            stateVersion = state.stateVersion + 1,
            lastEventHash = eventHash.ifBlank { Digests.sha256Hex(encode(state)) },
            updatedAtIso = Instant.now().toString()
        )
    }

    fun normaliseHex(value: String): String? {
        val cleaned = value.trim().removePrefix("0x").removePrefix("0X")
        if (cleaned.isEmpty() || cleaned.length % 2 != 0 || !cleaned.matches(Regex("[0-9a-fA-F]+"))) return null
        return cleaned.uppercase()
    }

    /** Resolves study-defined labels such as `0=consent;1=form_2` for set bits. */
    fun labelsForBits(bitsHex: String, definitions: String): String = runCatching {
        val bits = BigInteger(bitsHex.ifBlank { "0" }, 16)
        definitions.split(';').mapNotNull { item ->
            val parts = item.split('=', limit = 2)
            val index = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: return@mapNotNull null
            val label = parts.getOrNull(1)?.trim().orEmpty().ifBlank { return@mapNotNull null }
            label.takeIf { bits.testBit(index) }
        }.joinToString(" | ")
    }.getOrDefault("")

    private fun maskedMatch(currentHex: String, maskHex: String, valueHex: String): Boolean {
        val current = bytes(currentHex) ?: return false
        val mask = bytes(maskHex) ?: return false
        val value = bytes(valueHex) ?: return false
        val width = maxOf(current.size, mask.size, value.size)
        val c = current.padStart(width); val m = mask.padStart(width); val v = value.padStart(width)
        return c.indices.all { i -> (c[i].toInt() and m[i].toInt()) == (v[i].toInt() and m[i].toInt()) }
    }

    private fun orHex(a: String, b: String): String? = combine(a, b) { x, y -> x or y }
    private fun andNotHex(a: String, b: String): String? = combine(a, b) { x, y -> x and y.inv() }
    private fun combine(a: String, b: String, op: (Int, Int) -> Int): String? {
        val left = bytes(a) ?: return null; val right = bytes(b) ?: return null; val width = maxOf(left.size, right.size)
        return ByteArray(width) { i -> op(left.getOrElse(left.size - width + i) { 0 }.toInt(), right.getOrElse(right.size - width + i) { 0 }.toInt()).toByte() }.toHex()
    }
    private fun bytes(value: String): ByteArray? = normaliseHex(value)?.chunked(2)?.map { it.toInt(16).toByte() }?.toByteArray()
    private fun zeroHex(bits: Int): String = ByteArray((bits.coerceAtLeast(1) + 7) / 8).toHex()
    private fun validWidth(bits: Int, hex: String): Boolean = bits in 1..65535 && hex.length == ((bits + 7) / 8) * 2
    private fun b64(value: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unb64(value: String): String? = runCatching { String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8) }.getOrNull()
    private fun ByteArray.padStart(width: Int): ByteArray = ByteArray(width - size) + this
    private fun ByteArray.toHex(): String = joinToString("") { "%02X".format(it) }
}

object ProtocolNfcTrackingFields {
    const val PROTOCOL_ID = "protocol_id"; const val PROTOCOL_VERSION = "protocol_version"; const val STEP_ID = "step_id"
    const val FLAG_BIT_COUNT = "flag_bit_count"; const val COMPLETION_BIT_COUNT = "completion_bit_count"
    const val FLAG_BITS = "active_flag_bits"; const val COMPLETION_BITS_STATE = "completion_bits_state"
    const val FLAG_DEFINITIONS = "flag_definitions"; const val STEP_DEFINITIONS = "step_definitions"
    const val ACTIVE_FLAG_LABELS = "active_flag_labels"; const val COMPLETED_STEP_LABELS = "completed_step_labels"
    const val REQUIRED_BITS = "required_bits"; const val REQUIRED_VALUE = "required_value"; const val REQUIRED_EXPRESSION = "required_expression"
    const val COMPLETION_BITS = "completion_bits"; const val SET_FLAGS = "set_flag_bits"; const val CLEAR_FLAGS = "clear_flag_bits"
    const val PROTOCOL_ALLOWED = "protocol_allowed"; const val PROTOCOL_REASON = "protocol_reason"; const val PROTOCOL_STATE_BITS = "protocol_state_bits"
    const val PROTOCOL_STATE_VERSION = "protocol_state_version"; const val PROTOCOL_STATE_HASH = "protocol_state_hash"; const val PROTOCOL_UPDATED_TIME_ISO = "protocol_updated_time_iso"
    const val PROTOCOL_WRITE_VERIFIED = "protocol_write_verified"; const val PROTOCOL_OPERATION = "protocol_operation"
    val outputFields = listOf(PROTOCOL_ID, PROTOCOL_VERSION, STEP_ID, FLAG_BIT_COUNT, COMPLETION_BIT_COUNT, FLAG_BITS, COMPLETION_BITS_STATE, FLAG_DEFINITIONS, STEP_DEFINITIONS, ACTIVE_FLAG_LABELS, COMPLETED_STEP_LABELS, REQUIRED_BITS, REQUIRED_VALUE, REQUIRED_EXPRESSION, COMPLETION_BITS, PROTOCOL_ALLOWED, PROTOCOL_REASON, PROTOCOL_STATE_BITS, PROTOCOL_STATE_VERSION, PROTOCOL_STATE_HASH, PROTOCOL_UPDATED_TIME_ISO, PROTOCOL_WRITE_VERIFIED, PROTOCOL_OPERATION, NfcEvidenceFields.TAG_UID_HEX, NfcEvidenceFields.NDEF_MESSAGE_SHA256)
}
