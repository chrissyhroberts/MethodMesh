package com.example.methodmesh.modules.signals

import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlin.math.roundToInt

/**
 * Slow, deliberately simple binary-FSK framing used on top of MMS/1.
 *
 * Physical framing is only for symbol synchronisation. MMS/1 remains the data
 * integrity/recovery layer and validates every decoded ASCII frame itself.
 */
object FskPhysicalCodec {
    private val PREAMBLE = ByteArray(4) { 0x55.toByte() }
    private val SYNC = byteArrayOf(0xD3.toByte(), 0x91.toByte())
    const val MAX_FRAME_BYTES = 4096

    fun encodeFrame(frame: String): BooleanArray {
        val payload = encodeFramePayload(frame)
        require(payload.size <= MAX_FRAME_BYTES) { "FSK frame is too large (${payload.size} bytes)." }
        val packet = ByteArray(PREAMBLE.size + SYNC.size + 2 + payload.size)
        var p = 0
        PREAMBLE.copyInto(packet, p); p += PREAMBLE.size
        SYNC.copyInto(packet, p); p += SYNC.size
        packet[p++] = ((payload.size ushr 8) and 0xFF).toByte()
        packet[p++] = (payload.size and 0xFF).toByte()
        payload.copyInto(packet, p)
        return bytesToBits(packet)
    }

    /** Compact binary MMS/1 payload shared by bit-at-a-time physical links. */
    internal fun encodeFramePayload(frame: String): ByteArray =
        compactPayload(frame) ?: byteArrayOf(0x00) + frame.toByteArray(StandardCharsets.UTF_8)

    /**
     * MMS/1 is human-readable ASCII, which is useful for QR/debugging but wasteful on
     * a bit-at-a-time acoustic link. Generated 16-hex message IDs can be packed with
     * the existing CRCs and reconstructed byte-for-byte at the receiver. Unknown frame
     * formats fall back to a tagged UTF-8 payload.
     */
    private fun compactPayload(frame: String): ByteArray? {
        val parsed = SignalPacketCodec.parse(frame).frame ?: return null
        val id = parsed.messageId
        if (!id.matches(Regex("[0-9a-f]{16}"))) return null
        val parts = frame.trim().split('|')
        if (parts.size != 8) return null
        val messageCrc = hexToBytes(parsed.messageCrc32Hex, 4) ?: return null
        val idBytes = hexToBytes(id, 8) ?: return null
        val shard = parsed.shard
        // A2 omits the MMS frame CRC because it is deterministic from the reconstructed
        // body. Acoustic links pay for every bit, so retransmitting those four bytes is
        // pure overhead. A1 remains decodable below for compatibility with older captures.
        val out = ByteArray(1 + 8 + 1 + 4 + 4 + 1 + 2 + shard.size)
        var at = 0
        out[at++] = 0xA2.toByte()
        idBytes.copyInto(out, at); at += idBytes.size
        out[at++] = parsed.dataShardCount.toByte()
        writeInt(out, at, parsed.payloadLength); at += 4
        messageCrc.copyInto(out, at); at += 4
        out[at++] = parsed.codingRow.toByte()
        out[at++] = ((shard.size ushr 8) and 0xFF).toByte()
        out[at++] = (shard.size and 0xFF).toByte()
        shard.copyInto(out, at)
        return out
    }

    data class PayloadDecodeResult(val frame: String? = null, val error: String? = null) {
        val valid: Boolean get() = frame != null && error == null
    }

    fun decodeFramePayload(payload: ByteArray): String? = decodeFramePayloadResult(payload).frame

    fun decodeFramePayloadResult(payload: ByteArray): PayloadDecodeResult {
        if (payload.isEmpty()) return PayloadDecodeResult(error = "empty physical payload")
        if ((payload[0].toInt() and 0xFF) == 0x00) {
            return PayloadDecodeResult(frame = payload.copyOfRange(1, payload.size).toString(StandardCharsets.UTF_8))
        }
        val format = payload[0].toInt() and 0xFF
        if (format != 0xA1 && format != 0xA2) return PayloadDecodeResult(error = "unknown physical payload tag 0x%02X".format(format))
        val trailingCrcBytes = if (format == 0xA1) 4 else 0
        if (payload.size < 21 + trailingCrcBytes) return PayloadDecodeResult(error = "truncated compact MMS header")
        var at = 1
        val id = bytesToHex(payload, at, 8).lowercase(); at += 8
        val dataShardCount = payload[at++].toInt() and 0xFF
        val payloadLength = readInt(payload, at); at += 4
        val messageCrc = bytesToHex(payload, at, 4); at += 4
        val codingRow = payload[at++].toInt() and 0xFF
        val shardLength = ((payload[at++].toInt() and 0xFF) shl 8) or (payload[at++].toInt() and 0xFF)
        if (shardLength !in 1..512) return PayloadDecodeResult(error = "invalid shard length $shardLength")
        if (at + shardLength + trailingCrcBytes != payload.size) return PayloadDecodeResult(error = "physical length/shard geometry mismatch")
        val shard = payload.copyOfRange(at, at + shardLength); at += shardLength
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(shard)
        val body = listOf("MMS1", id, dataShardCount.toString(), payloadLength.toString(), messageCrc, codingRow.toString(), b64).joinToString("|")
        val frameCrc = if (format == 0xA1) bytesToHex(payload, at, 4)
            else SignalPacketCodec.crc32Hex(body.toByteArray(StandardCharsets.US_ASCII))
        val frame = "$body|$frameCrc"
        val parsed = SignalPacketCodec.parse(frame)
        return if (parsed.valid) PayloadDecodeResult(frame = frame) else PayloadDecodeResult(error = parsed.error ?: "reconstructed MMS/1 frame failed validation")
    }

    private fun writeInt(out: ByteArray, at: Int, value: Int) {
        out[at] = ((value ushr 24) and 0xFF).toByte()
        out[at + 1] = ((value ushr 16) and 0xFF).toByte()
        out[at + 2] = ((value ushr 8) and 0xFF).toByte()
        out[at + 3] = (value and 0xFF).toByte()
    }

    private fun readInt(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun hexToBytes(hex: String, expectedBytes: Int): ByteArray? {
        if (hex.length != expectedBytes * 2 || !hex.matches(Regex("[0-9A-Fa-f]+"))) return null
        return ByteArray(expectedBytes) { index -> hex.substring(index * 2, index * 2 + 2).toInt(16).toByte() }
    }

    private fun bytesToHex(bytes: ByteArray, offset: Int, length: Int): String = buildString(length * 2) {
        for (i in offset until offset + length) append("%02X".format(bytes[i].toInt() and 0xFF))
    }

    fun bytesToBits(bytes: ByteArray): BooleanArray {
        val bits = BooleanArray(bytes.size * 8)
        var j = 0
        bytes.forEach { raw ->
            val value = raw.toInt() and 0xFF
            for (shift in 7 downTo 0) bits[j++] = ((value ushr shift) and 1) != 0
        }
        return bits
    }

    fun bitsToByte(bits: List<Boolean>, offset: Int): Int {
        var value = 0
        for (i in 0 until 8) value = (value shl 1) or if (bits[offset + i]) 1 else 0
        return value
    }

    fun syncBits(): BooleanArray = bytesToBits(SYNC)
}

/** Scans an arbitrary bit stream for FSK sync words and extracts complete MMS/1 frames. */
class FskBitstreamParser {
    private val bits = ArrayList<Boolean>(16_384)
    private val sync = FskPhysicalCodec.syncBits()

    var framesExtracted: Int = 0
        private set
    var invalidLengths: Int = 0
        private set

    fun reset() {
        bits.clear()
        framesExtracted = 0
        invalidLengths = 0
    }

    fun feed(newBits: Iterable<Boolean>): List<String> {
        bits.addAll(newBits)
        val out = mutableListOf<String>()
        var cursor = 0
        while (true) {
            val syncAt = findSync(cursor)
            if (syncAt < 0) break
            val lengthAt = syncAt + sync.size
            if (bits.size < lengthAt + 16) {
                trimBefore(syncAt)
                break
            }
            val length = (readByte(lengthAt) shl 8) or readByte(lengthAt + 8)
            if (length <= 0 || length > FskPhysicalCodec.MAX_FRAME_BYTES) {
                invalidLengths++
                cursor = syncAt + 1
                continue
            }
            val payloadAt = lengthAt + 16
            val need = payloadAt + length * 8
            if (bits.size < need) {
                trimBefore(syncAt)
                break
            }
            val bytes = ByteArray(length)
            var bitAt = payloadAt
            for (i in bytes.indices) {
                bytes[i] = readByte(bitAt).toByte()
                bitAt += 8
            }
            FskPhysicalCodec.decodeFramePayload(bytes)?.let(out::add)
            framesExtracted++
            bits.subList(0, need).clear()
            cursor = 0
        }
        // Avoid unbounded growth if we are listening to unrelated sound.
        if (bits.size > 65_536) bits.subList(0, bits.size - 4096).clear()
        return out
    }

    private fun findSync(from: Int): Int {
        if (bits.size < sync.size) return -1
        outer@ for (i in from..bits.size - sync.size) {
            for (j in sync.indices) if (bits[i + j] != sync[j]) continue@outer
            return i
        }
        return -1
    }

    private fun readByte(offset: Int): Int = FskPhysicalCodec.bitsToByte(bits, offset)

    private fun trimBefore(index: Int) {
        if (index > 0) bits.subList(0, index).clear()
    }
}

/**
 * Converts a noisy sequence of dominant FSK tones into runs of bits. The parser
 * does not need a global byte boundary because it scans for the sync word.
 */
class FskToneRunDecoder(private var bitMs: Int) {
    private var current: Boolean? = null
    private var runStartMs: Long = 0
    private var lastTimestampMs: Long = 0

    fun setBitMs(value: Int) { bitMs = value.coerceAtLeast(5) }

    fun reset() {
        current = null
        runStartMs = 0
        lastTimestampMs = 0
    }

    fun feed(tone: Boolean?, timestampMs: Long): List<Boolean> {
        if (lastTimestampMs == 0L) lastTimestampMs = timestampMs
        if (current == null) {
            current = tone
            runStartMs = timestampMs
            lastTimestampMs = timestampMs
            return emptyList()
        }
        lastTimestampMs = timestampMs
        if (tone == current) return emptyList()
        val emitted = emitRun(timestampMs)
        current = tone
        runStartMs = timestampMs
        return emitted
    }

    fun flush(timestampMs: Long = lastTimestampMs): List<Boolean> {
        val out = emitRun(timestampMs)
        current = null
        runStartMs = timestampMs
        return out
    }

    private fun emitRun(endMs: Long): List<Boolean> {
        val value = current ?: return emptyList()
        val duration = (endMs - runStartMs).coerceAtLeast(0L)
        if (duration < bitMs * 0.45) return emptyList()
        val count = (duration.toDouble() / bitMs).roundToInt().coerceIn(1, 1024)
        return List(count) { value }
    }
}

/**
 * Recovers complete physical FSK frames from fixed-duration tone-decision windows.
 *
 * The receiver window is 10 ms. Configured bit periods are therefore 2..25 windows.
 * Instead of rounding MARK/SPACE run lengths, this decoder searches the 0x55 preamble
 * at every possible window phase, majority-votes each bit cell, verifies the sync word,
 * then reads the length/payload on that acquired clock. This is deliberately tolerant
 * of a small number of uncertain or wrong tone windows at symbol edges.
 */
class FskClockRecoveryDecoder(bitMs: Int, private val windowMs: Int = 10) {
    data class Telemetry(
        val windowsSeen: Int = 0,
        val clockLocked: Boolean = false,
        val preambleCandidates: Int = 0,
        val syncDetections: Int = 0,
        val physicalFrames: Int = 0,
        val payloadDecodeFailures: Int = 0,
        val invalidLengths: Int = 0,
        val lastClockPhase: Int = -1,
        val lastPreambleMismatch: Int = -1,
        val lastSyncMismatch: Int = -1,
        val pendingPayloadBytes: Int = 0,
        val pendingFrameProgress: Double = 0.0,
        val lastPayloadRejectReason: String? = null
    )

    private val preambleBits = FskPhysicalCodec.bytesToBits(ByteArray(4) { 0x55.toByte() }).toList()
    private val syncBits = FskPhysicalCodec.syncBits().toList()
    private val windows = ArrayList<Boolean?>(32_768)
    private var windowsPerBit = 1
    private var totalWindows = 0
    private var candidateCount = 0
    private var syncCount = 0
    private var frameCount = 0
    private var payloadDecodeFailureCount = 0
    private var lastPayloadRejectReason: String? = null
    private var invalidLengthCount = 0
    private var lastPhase = -1
    private var lastPreambleMismatch = -1
    private var lastSyncMismatch = -1
    private var locked = false
    private var pendingCandidate: Candidate? = null

    init { setBitMs(bitMs) }

    fun setBitMs(value: Int) {
        windowsPerBit = (value.coerceIn(windowMs, 2000) / windowMs).coerceAtLeast(1)
        reset()
    }

    fun reset() {
        windows.clear()
        totalWindows = 0
        candidateCount = 0
        syncCount = 0
        frameCount = 0
        payloadDecodeFailureCount = 0
        lastPayloadRejectReason = null
        invalidLengthCount = 0
        lastPhase = -1
        lastPreambleMismatch = -1
        lastSyncMismatch = -1
        locked = false
        pendingCandidate = null
    }

    fun feed(tone: Boolean?): List<String> {
        windows += tone
        totalWindows++
        val out = extractAvailable()
        if (windows.size > 65_536) {
            windows.subList(0, windows.size - 16_384).clear()
            locked = false
        }
        return out
    }

    fun telemetry(): Telemetry {
        var pendingLength = 0
        var pendingProgress = 0.0
        pendingCandidate?.let { candidate ->
            val lengthBitStart = candidate.start + (preambleBits.size + syncBits.size) * windowsPerBit
            if (windows.size >= lengthBitStart + 16 * windowsPerBit) {
                val lengthBits = readCells(lengthBitStart, 16)
                if (lengthBits != null) {
                    val length = (bitsToInt(lengthBits.take(8)) shl 8) or bitsToInt(lengthBits.drop(8))
                    if (length in 1..FskPhysicalCodec.MAX_FRAME_BYTES) {
                        pendingLength = length
                        val payloadBitStart = lengthBitStart + 16 * windowsPerBit
                        val needed = length * 8 * windowsPerBit
                        val have = (windows.size - payloadBitStart).coerceIn(0, needed)
                        pendingProgress = if (needed > 0) have.toDouble() / needed.toDouble() else 0.0
                    }
                }
            }
        }
        return Telemetry(
            windowsSeen = totalWindows,
            clockLocked = locked,
            preambleCandidates = candidateCount,
            syncDetections = syncCount,
            physicalFrames = frameCount,
            payloadDecodeFailures = payloadDecodeFailureCount,
            invalidLengths = invalidLengthCount,
            lastClockPhase = lastPhase,
            lastPreambleMismatch = lastPreambleMismatch,
            lastSyncMismatch = lastSyncMismatch,
            pendingPayloadBytes = pendingLength,
            pendingFrameProgress = pendingProgress,
            lastPayloadRejectReason = lastPayloadRejectReason
        )
    }

    private fun extractAvailable(): List<String> {
        val out = mutableListOf<String>()
        while (true) {
            val candidate = pendingCandidate ?: findCandidate()?.also { found ->
                pendingCandidate = found
                candidateCount++
                lastPhase = found.start % windowsPerBit
                lastPreambleMismatch = found.preambleMismatch
                lastSyncMismatch = found.syncMismatch
                locked = true
                syncCount++
            } ?: break

            val lengthBitStart = candidate.start + (preambleBits.size + syncBits.size) * windowsPerBit
            if (windows.size < lengthBitStart + 16 * windowsPerBit) break
            val lengthBits = readCells(lengthBitStart, 16)
            if (lengthBits == null) {
                pendingCandidate = null
                dropBefore(candidate.start + windowsPerBit)
                locked = false
                continue
            }
            val length = (bitsToInt(lengthBits.take(8)) shl 8) or bitsToInt(lengthBits.drop(8))
            if (length <= 0 || length > FskPhysicalCodec.MAX_FRAME_BYTES) {
                invalidLengthCount++
                pendingCandidate = null
                dropBefore(candidate.start + windowsPerBit)
                locked = false
                continue
            }
            val payloadBitStart = lengthBitStart + 16 * windowsPerBit
            val payloadBitsNeeded = length * 8
            val end = payloadBitStart + payloadBitsNeeded * windowsPerBit
            if (windows.size < end) break
            val payloadBits = readCells(payloadBitStart, payloadBitsNeeded)
            if (payloadBits == null) {
                pendingCandidate = null
                dropBefore(candidate.start + windowsPerBit)
                locked = false
                continue
            }
            val bytes = ByteArray(length)
            for (i in 0 until length) bytes[i] = bitsToInt(payloadBits.subList(i * 8, i * 8 + 8)).toByte()
            val decoded = FskPhysicalCodec.decodeFramePayloadResult(bytes)
            if (decoded.valid) {
                out += decoded.frame!!
                lastPayloadRejectReason = null
            } else {
                payloadDecodeFailureCount++
                lastPayloadRejectReason = decoded.error
            }
            frameCount++
            pendingCandidate = null
            dropBefore(end)
            locked = false
        }
        return out
    }

    private data class Candidate(val start: Int, val preambleMismatch: Int, val syncMismatch: Int)

    private fun findCandidate(): Candidate? {
        val headerCells = preambleBits.size + syncBits.size
        val need = headerCells * windowsPerBit
        if (windows.size < need) return null
        // Search a bounded recent region. Silence between frames means the true preamble
        // normally appears close to the newest data, while the bound avoids quadratic growth.
        val earliest = (windows.size - need - windowsPerBit * 24).coerceAtLeast(0)
        val latest = windows.size - need
        var best: Candidate? = null
        var bestScore = Int.MAX_VALUE
        for (start in earliest..latest) {
            val pre = readCells(start, preambleBits.size) ?: continue
            val preMismatch = pre.indices.count { pre[it] != preambleBits[it] }
            if (preMismatch > 10) continue
            val sync = readCells(start + preambleBits.size * windowsPerBit, syncBits.size) ?: continue
            val syncMismatch = sync.indices.count { sync[it] != syncBits[it] }
            if (syncMismatch > 2) continue
            val score = preMismatch * 3 + syncMismatch * 8
            if (score < bestScore) {
                bestScore = score
                best = Candidate(start, preMismatch, syncMismatch)
                if (score == 0) break
            }
        }
        return best
    }

    private fun readCells(startWindow: Int, cellCount: Int): List<Boolean>? {
        if (startWindow < 0 || startWindow + cellCount * windowsPerBit > windows.size) return null
        val result = ArrayList<Boolean>(cellCount)
        for (cell in 0 until cellCount) {
            val start = startWindow + cell * windowsPerBit
            var mark = 0
            var space = 0
            for (i in 0 until windowsPerBit) {
                when (windows[start + i]) {
                    true -> mark++
                    false -> space++
                    null -> Unit
                }
            }
            val known = mark + space
            if (known < (windowsPerBit + 1) / 2) return null
            result += mark >= space
        }
        return result
    }

    private fun bitsToInt(bits: List<Boolean>): Int {
        var value = 0
        bits.forEach { value = (value shl 1) or if (it) 1 else 0 }
        return value
    }

    private fun dropBefore(index: Int) {
        if (index <= 0) return
        windows.subList(0, index.coerceAtMost(windows.size)).clear()
    }
}
