package com.example.methodmesh.modules.signals

import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.math.max

/**
 * MethodMesh Signal Protocol v1 (MMS/1).
 *
 * MMS/1 is transport-agnostic: QR, optical, acoustic and future hardware links
 * carry complete ASCII frames. Each frame has its own CRC32. Valid frames are
 * systematic Reed-Solomon erasure-code shards over GF(256), so a receiver can
 * lose whole frames, see them out of order, see duplicates, or join a loop in
 * progress and still reconstruct once it has k distinct valid coded shards for
 * a k-shard message. A whole-message CRC32 validates final reconstruction.
 *
 * This layer deliberately treats uncertain analogue symbols as erasures: a
 * physical demodulator either yields a frame that passes its CRC or that frame
 * is discarded. It is not an authentication or cryptographic integrity layer.
 */
object SignalPacketCodec {
    const val MAGIC = "MMS1"
    const val MAX_DATA_SHARDS = 60
    const val DEFAULT_SHARD_BYTES = 96
    private const val MAX_CODING_ROWS = 255

    enum class Robustness {
        FAST,
        ROBUST,
        EXTREME;

        companion object {
            fun from(raw: String?): Robustness = when (raw?.trim()?.lowercase()) {
                "fast" -> FAST
                "extreme" -> EXTREME
                else -> ROBUST
            }
        }
    }

    data class EncodedMessage(
        val messageId: String,
        val payloadBytes: Int,
        val dataShardCount: Int,
        val parityFrameCount: Int,
        val messageCrc32Hex: String,
        val frames: List<String>
    )

    data class Frame(
        val messageId: String,
        val dataShardCount: Int,
        val payloadLength: Int,
        val messageCrc32Hex: String,
        /** Generator-matrix row. 0 until dataShardCount are systematic rows. */
        val codingRow: Int,
        val shard: ByteArray,
        val raw: String
    ) {
        val isSystematic: Boolean get() = codingRow in 0 until dataShardCount
        val sourceIndex: Int? get() = codingRow.takeIf { isSystematic }
    }

    data class ParseResult(val frame: Frame? = null, val error: String? = null) {
        val valid: Boolean get() = frame != null && error == null
    }

    fun encodeText(
        payload: String,
        robustness: Robustness = Robustness.ROBUST,
        shardBytes: Int = DEFAULT_SHARD_BYTES,
        messageId: String = newMessageId()
    ): EncodedMessage = encode(payload.toByteArray(StandardCharsets.UTF_8), robustness, shardBytes, messageId)

    fun encode(
        payload: ByteArray,
        robustness: Robustness = Robustness.ROBUST,
        shardBytes: Int = DEFAULT_SHARD_BYTES,
        messageId: String = newMessageId()
    ): EncodedMessage {
        require(messageId.matches(Regex("[A-Za-z0-9_-]{4,32}"))) { "Invalid MMS/1 message ID." }
        val width = shardBytes.coerceIn(24, 512)
        val n = max(1, ceil(payload.size.toDouble() / width.toDouble()).toInt())
        require(n <= MAX_DATA_SHARDS) {
            "MMS/1 first-pass payload is too large: $n data shards exceeds $MAX_DATA_SHARDS."
        }
        val parityCount = when (robustness) {
            Robustness.FAST -> max(1, (n + 3) / 4)
            Robustness.ROBUST -> max(2, n)
            Robustness.EXTREME -> max(4, n * 2)
        }
        require(n + parityCount <= MAX_CODING_ROWS) { "Too many MMS/1 coding rows." }

        val data = Array(n) { ByteArray(width) }
        payload.forEachIndexed { index, byte -> data[index / width][index % width] = byte }
        val messageCrc = crc32Hex(payload)
        val frames = ArrayList<String>(n + parityCount)

        for (row in 0 until n + parityCount) {
            val shard = if (row < n) data[row].copyOf() else encodeRow(data, generatorRow(n, row))
            frames += renderFrame(messageId, n, payload.size, messageCrc, row, shard)
        }

        return EncodedMessage(
            messageId = messageId,
            payloadBytes = payload.size,
            dataShardCount = n,
            parityFrameCount = parityCount,
            messageCrc32Hex = messageCrc,
            frames = frames
        )
    }

    fun parse(raw: String): ParseResult {
        val trimmed = raw.trim()
        val parts = trimmed.split('|')
        if (parts.size != 8 || parts[0] != MAGIC) return ParseResult(error = "Not an MMS/1 frame.")

        val body = parts.take(7).joinToString("|")
        val expectedFrameCrc = parts[7].uppercase()
        val actualFrameCrc = crc32Hex(body.toByteArray(StandardCharsets.US_ASCII))
        if (expectedFrameCrc != actualFrameCrc) return ParseResult(error = "Frame CRC failed.")

        val messageId = parts[1].takeIf { it.matches(Regex("[A-Za-z0-9_-]{4,32}")) }
            ?: return ParseResult(error = "Invalid message ID.")
        val dataShardCount = parts[2].toIntOrNull()?.takeIf { it in 1..MAX_DATA_SHARDS }
            ?: return ParseResult(error = "Invalid data shard count.")
        val payloadLength = parts[3].toIntOrNull()?.takeIf { it >= 0 }
            ?: return ParseResult(error = "Invalid payload length.")
        val messageCrc = parts[4].uppercase().takeIf { it.matches(Regex("[0-9A-F]{8}")) }
            ?: return ParseResult(error = "Invalid message CRC.")
        val codingRow = parts[5].toIntOrNull()?.takeIf { it in 0 until MAX_CODING_ROWS }
            ?: return ParseResult(error = "Invalid coding row.")
        val shard = runCatching { Base64.getUrlDecoder().decode(parts[6]) }
            .getOrElse { return ParseResult(error = "Invalid shard encoding.") }
        if (shard.size !in 24..512) return ParseResult(error = "Invalid shard size.")
        if (payloadLength > dataShardCount * shard.size) return ParseResult(error = "Payload length exceeds shard geometry.")

        return ParseResult(
            frame = Frame(messageId, dataShardCount, payloadLength, messageCrc, codingRow, shard, trimmed)
        )
    }

    private fun renderFrame(
        messageId: String,
        dataShardCount: Int,
        payloadLength: Int,
        messageCrc32Hex: String,
        codingRow: Int,
        shard: ByteArray
    ): String {
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(shard)
        val body = listOf(
            MAGIC,
            messageId,
            dataShardCount.toString(),
            payloadLength.toString(),
            messageCrc32Hex,
            codingRow.toString(),
            b64
        ).joinToString("|")
        return "$body|${crc32Hex(body.toByteArray(StandardCharsets.US_ASCII))}"
    }

    private fun encodeRow(data: Array<ByteArray>, coefficients: ByteArray): ByteArray {
        val out = ByteArray(data.first().size)
        for (source in data.indices) {
            val coefficient = coefficients[source].toInt() and 0xFF
            if (coefficient == 0) continue
            for (i in out.indices) {
                val value = Gf256.multiply(coefficient, data[source][i].toInt() and 0xFF)
                out[i] = ((out[i].toInt() and 0xFF) xor value).toByte()
            }
        }
        return out
    }

    internal fun generatorRow(dataShardCount: Int, rowIndex: Int): ByteArray {
        require(dataShardCount in 1..MAX_DATA_SHARDS)
        require(rowIndex in 0 until MAX_CODING_ROWS)
        if (rowIndex < dataShardCount) {
            return ByteArray(dataShardCount).also { it[rowIndex] = 1 }
        }
        val topInverse = TopInverseCache.get(dataShardCount)
        val vandermonde = vandermondeRow(dataShardCount, rowIndex)
        return multiplyRowByMatrix(vandermonde, topInverse)
    }

    private fun vandermondeRow(columns: Int, rowIndex: Int): ByteArray {
        // Distinct non-zero field elements x = rowIndex + 1, valid up to row 254.
        val x = rowIndex + 1
        val row = ByteArray(columns)
        var value = 1
        for (column in 0 until columns) {
            row[column] = value.toByte()
            value = Gf256.multiply(value, x)
        }
        return row
    }

    private fun multiplyRowByMatrix(row: ByteArray, matrix: Array<ByteArray>): ByteArray {
        val n = row.size
        val out = ByteArray(n)
        for (column in 0 until n) {
            var value = 0
            for (k in 0 until n) {
                value = value xor Gf256.multiply(row[k].toInt() and 0xFF, matrix[k][column].toInt() and 0xFF)
            }
            out[column] = value.toByte()
        }
        return out
    }

    internal fun invertMatrix(input: Array<ByteArray>): Array<ByteArray>? {
        val n = input.size
        if (n == 0 || input.any { it.size != n }) return null
        val a = Array(n) { r -> input[r].copyOf() }
        val inv = Array(n) { r -> ByteArray(n).also { it[r] = 1 } }

        for (column in 0 until n) {
            var pivot = column
            while (pivot < n && (a[pivot][column].toInt() and 0xFF) == 0) pivot++
            if (pivot == n) return null
            if (pivot != column) {
                val tmpA = a[column]; a[column] = a[pivot]; a[pivot] = tmpA
                val tmpI = inv[column]; inv[column] = inv[pivot]; inv[pivot] = tmpI
            }
            val pivotValue = a[column][column].toInt() and 0xFF
            val scale = Gf256.inverse(pivotValue)
            for (c in 0 until n) {
                a[column][c] = Gf256.multiply(a[column][c].toInt() and 0xFF, scale).toByte()
                inv[column][c] = Gf256.multiply(inv[column][c].toInt() and 0xFF, scale).toByte()
            }
            for (row in 0 until n) {
                if (row == column) continue
                val factor = a[row][column].toInt() and 0xFF
                if (factor == 0) continue
                for (c in 0 until n) {
                    a[row][c] = ((a[row][c].toInt() and 0xFF) xor Gf256.multiply(factor, a[column][c].toInt() and 0xFF)).toByte()
                    inv[row][c] = ((inv[row][c].toInt() and 0xFF) xor Gf256.multiply(factor, inv[column][c].toInt() and 0xFF)).toByte()
                }
            }
        }
        return inv
    }

    fun crc32Hex(bytes: ByteArray): String {
        val crc = CRC32()
        crc.update(bytes)
        return "%08X".format(crc.value)
    }

    fun newMessageId(): String = UUID.randomUUID().toString().replace("-", "").take(16)

    private object TopInverseCache {
        private val cache = ConcurrentHashMap<Int, Array<ByteArray>>()
        fun get(n: Int): Array<ByteArray> = cache.getOrPut(n) {
            val top = Array(n) { row -> vandermondeRow(n, row) }
            invertMatrix(top) ?: error("Unable to construct MMS/1 Reed-Solomon generator matrix for $n shards.")
        }
    }

    private object Gf256 {
        private const val PRIMITIVE = 0x11D
        private val exp = IntArray(512)
        private val log = IntArray(256)

        init {
            var x = 1
            for (i in 0 until 255) {
                exp[i] = x
                log[x] = i
                x = x shl 1
                if ((x and 0x100) != 0) x = x xor PRIMITIVE
            }
            for (i in 255 until exp.size) exp[i] = exp[i - 255]
        }

        fun multiply(a: Int, b: Int): Int {
            if (a == 0 || b == 0) return 0
            return exp[log[a and 0xFF] + log[b and 0xFF]]
        }

        fun inverse(a: Int): Int {
            require(a != 0) { "Zero has no multiplicative inverse in GF(256)." }
            return exp[255 - log[a and 0xFF]]
        }
    }
}

/**
 * Incremental MMS/1 Reed-Solomon erasure decoder. Complete valid frames can be
 * duplicated, reordered or lost. Once k distinct coded rows are present for a
 * k-source-shard message, reconstruction is attempted and verified by CRC.
 */
class SignalFrameCollector {
    data class State(
        val messageId: String? = null,
        val rank: Int = 0,
        val requiredRank: Int = 0,
        val acceptedFrames: Int = 0,
        val rejectedFrames: Int = 0,
        val redundantFrames: Int = 0,
        val systematicSourcesSeen: Int = 0,
        val recoveredMissingSources: Int = 0,
        val complete: Boolean = false,
        val payload: ByteArray? = null,
        val text: String? = null,
        val messageCrcVerified: Boolean = false,
        val lastError: String? = null
    )

    private var messageId: String? = null
    private var n: Int = 0
    private var payloadLength: Int = 0
    private var messageCrc: String = ""
    private var shardSize: Int = 0
    private val framesByRow = linkedMapOf<Int, SignalPacketCodec.Frame>()
    private val systematicSeen = linkedSetOf<Int>()
    private var accepted = 0
    private var rejected = 0
    private var redundant = 0
    private var completedPayload: ByteArray? = null
    private var lastError: String? = null

    fun reset() {
        messageId = null
        n = 0
        payloadLength = 0
        messageCrc = ""
        shardSize = 0
        framesByRow.clear()
        systematicSeen.clear()
        accepted = 0
        rejected = 0
        redundant = 0
        completedPayload = null
        lastError = null
    }

    fun offer(raw: String): State {
        val parsed = SignalPacketCodec.parse(raw)
        val frame = parsed.frame
        if (frame == null) {
            rejected++
            lastError = parsed.error
            return state()
        }
        if (messageId == null) initialise(frame)
        if (!compatible(frame)) {
            rejected++
            lastError = "Frame belongs to a different MMS/1 message or geometry."
            return state()
        }
        if (framesByRow.containsKey(frame.codingRow)) {
            redundant++
            return state()
        }
        framesByRow[frame.codingRow] = frame
        accepted++
        if (frame.isSystematic) frame.sourceIndex?.let(systematicSeen::add)
        lastError = null

        if (completedPayload == null && framesByRow.size >= n) {
            val reconstructed = reconstruct()
            if (reconstructed != null) {
                if (SignalPacketCodec.crc32Hex(reconstructed) == messageCrc) completedPayload = reconstructed
                else lastError = "Message CRC failed after Reed-Solomon reconstruction."
            }
        }
        return state()
    }

    fun state(): State {
        val payload = completedPayload
        val text = payload?.let { bytes ->
            SignalContentEnvelope.decode(bytes)?.text
                ?: runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull()
        }
        return State(
            messageId = messageId,
            rank = minOf(framesByRow.size, n),
            requiredRank = n,
            acceptedFrames = accepted,
            rejectedFrames = rejected,
            redundantFrames = redundant,
            systematicSourcesSeen = systematicSeen.size,
            recoveredMissingSources = if (payload != null) (n - systematicSeen.size).coerceAtLeast(0) else 0,
            complete = payload != null,
            payload = payload,
            text = text,
            messageCrcVerified = payload != null,
            lastError = lastError
        )
    }

    private fun initialise(frame: SignalPacketCodec.Frame) {
        messageId = frame.messageId
        n = frame.dataShardCount
        payloadLength = frame.payloadLength
        messageCrc = frame.messageCrc32Hex
        shardSize = frame.shard.size
    }

    private fun compatible(frame: SignalPacketCodec.Frame): Boolean =
        frame.messageId == messageId &&
            frame.dataShardCount == n &&
            frame.payloadLength == payloadLength &&
            frame.messageCrc32Hex == messageCrc &&
            frame.shard.size == shardSize

    private fun reconstruct(): ByteArray? {
        val selected = framesByRow.values.take(n)
        if (selected.size < n) return null
        val matrix = Array(n) { row -> SignalPacketCodec.generatorRow(n, selected[row].codingRow) }
        val inverse = SignalPacketCodec.invertMatrix(matrix) ?: return null
        val sources = Array(n) { ByteArray(shardSize) }
        for (source in 0 until n) {
            for (received in 0 until n) {
                val coefficient = inverse[source][received].toInt() and 0xFF
                if (coefficient == 0) continue
                val shard = selected[received].shard
                for (i in 0 until shardSize) {
                    val product = gfMultiply(coefficient, shard[i].toInt() and 0xFF)
                    sources[source][i] = ((sources[source][i].toInt() and 0xFF) xor product).toByte()
                }
            }
        }
        val joined = ByteArray(n * shardSize)
        sources.forEachIndexed { index, shard -> shard.copyInto(joined, index * shardSize) }
        if (payloadLength > joined.size) return null
        return joined.copyOf(payloadLength)
    }

    // Decoder-local GF multiply keeps the protocol's internal field table private.
    private fun gfMultiply(a: Int, b: Int): Int {
        if (a == 0 || b == 0) return 0
        var aa = a and 0xFF
        var bb = b and 0xFF
        var result = 0
        while (bb != 0) {
            if ((bb and 1) != 0) result = result xor aa
            bb = bb ushr 1
            aa = aa shl 1
            if ((aa and 0x100) != 0) aa = aa xor 0x11D
        }
        return result and 0xFF
    }
}
