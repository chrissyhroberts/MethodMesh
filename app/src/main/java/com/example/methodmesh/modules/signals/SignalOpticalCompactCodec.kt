package com.example.methodmesh.modules.signals

import java.util.zip.CRC32
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Compact short-text frame for low-bitrate optical links. */
object OpticalCompactTextCodec {
    private const val MAGIC = 0x4d
    private const val VERSION_FLAGS = 0x11 // v1 + 5-bit alphabet
    private const val HEADER_BYTES = 5
    private const val CRC_BYTES = 4
    private const val MAX_CHARS = 255
    private const val ALPHABET = " abcdefghijklmnopqrstuvwxyz.,?/-"

    data class Decoded(val text: String, val messageId: String, val crcVerified: Boolean)

    fun canEncode(text: String): Boolean = text.isNotEmpty() && text.length <= MAX_CHARS && text.lowercase().all { it in ALPHABET }

    fun encode(text: String, messageId: String): ByteArray {
        val normalized = text.lowercase()
        require(canEncode(normalized)) { "Compact optical text supports up to 255 characters: letters, space and . , ? / -." }
        val payload = pack5(normalized)
        val id = messageId16(messageId)
        val body = ByteArray(HEADER_BYTES + payload.size)
        body[0] = MAGIC.toByte()
        body[1] = VERSION_FLAGS.toByte()
        body[2] = ((id ushr 8) and 0xff).toByte()
        body[3] = (id and 0xff).toByte()
        body[4] = normalized.length.toByte()
        payload.copyInto(body, HEADER_BYTES)
        val crc = crc32(body)
        return body + byteArrayOf(
            ((crc ushr 24) and 0xff).toByte(),
            ((crc ushr 16) and 0xff).toByte(),
            ((crc ushr 8) and 0xff).toByte(),
            (crc and 0xff).toByte()
        )
    }

    fun decode(packet: ByteArray): Decoded? {
        val total = expectedPacketBytes(packet.take(HEADER_BYTES).toByteArray()) ?: return null
        if (packet.size != total) return null
        val body = packet.copyOfRange(0, packet.size - CRC_BYTES)
        val expected = readCrc(packet, packet.size - CRC_BYTES)
        if (crc32(body) != expected) return null
        val chars = packet[4].toInt() and 0xff
        val payload = body.copyOfRange(HEADER_BYTES, body.size)
        val text = unpack5(payload, chars) ?: return null
        val id = ((packet[2].toInt() and 0xff) shl 8) or (packet[3].toInt() and 0xff)
        return Decoded(text, id.toString(16).padStart(4, '0').uppercase(), true)
    }

    fun expectedPacketBytes(header: ByteArray): Int? {
        if (header.size < HEADER_BYTES) return null
        if ((header[0].toInt() and 0xff) != MAGIC || (header[1].toInt() and 0xff) != VERSION_FLAGS) return null
        val chars = header[4].toInt() and 0xff
        if (chars !in 1..MAX_CHARS) return null
        return HEADER_BYTES + ceil(chars * 5.0 / 8.0).toInt() + CRC_BYTES
    }

    private fun pack5(text: String): ByteArray {
        val bitCount = text.length * 5
        val out = ByteArray((bitCount + 7) / 8)
        var bit = 0
        text.forEach { ch ->
            val value = ALPHABET.indexOf(ch)
            require(value >= 0)
            for (shift in 4 downTo 0) {
                if (((value ushr shift) and 1) != 0) {
                    val byteIndex = bit / 8
                    val bitIndex = 7 - (bit % 8)
                    out[byteIndex] = (out[byteIndex].toInt() or (1 shl bitIndex)).toByte()
                }
                bit++
            }
        }
        return out
    }

    private fun unpack5(bytes: ByteArray, chars: Int): String? {
        if (bytes.size * 8 < chars * 5) return null
        val out = StringBuilder(chars)
        var bit = 0
        repeat(chars) {
            var value = 0
            repeat(5) {
                val byteIndex = bit / 8
                val bitIndex = 7 - (bit % 8)
                value = (value shl 1) or ((bytes[byteIndex].toInt() ushr bitIndex) and 1)
                bit++
            }
            if (value !in ALPHABET.indices) return null
            out.append(ALPHABET[value])
        }
        return out.toString()
    }

    private fun messageId16(messageId: String): Int {
        val crc = CRC32(); crc.update(messageId.toByteArray(Charsets.UTF_8)); return (crc.value and 0xffff).toInt()
    }

    private fun crc32(bytes: ByteArray): Long { val crc = CRC32(); crc.update(bytes); return crc.value }
    private fun readCrc(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xff) shl 24) or
            ((bytes[at + 1].toLong() and 0xff) shl 16) or
            ((bytes[at + 2].toLong() and 0xff) shl 8) or
            (bytes[at + 3].toLong() and 0xff)
}

/** Compact Screen Grid transport: state index is transmitted in existing grid control cells. */
object OpticalCompactGridCodec {
    fun encode(packet: ByteArray, profile: SignalOpticalProfiles.Profile): List<OpticalGridCodec.State> =
        OpticalGridCodec.encodeBytes(packet, profile)
}

/** Collects grid states across repeated cycles; a missed state can arrive on a later loop. */
class OpticalCompactGridCollector(private val profile: SignalOpticalProfiles.Profile) {
    private val stateVotes = linkedMapOf<Int, MutableMap<String, Int>>()
    private var expectedStates: Int? = null
    var rejectedStates: Int = 0
        private set

    data class Snapshot(val decoded: OpticalCompactTextCodec.Decoded?, val statesHeld: Int, val statesNeeded: Int?, val rejected: Int)

    fun reset() { stateVotes.clear(); expectedStates = null; rejectedStates = 0 }

    fun offer(state: OpticalGridCodec.State): Snapshot {
        if (state.size != profile.gridSize || state.index !in 0..63) { rejectedStates++; return snapshot() }
        val key = state.dataSymbols.joinToString(",")
        val votes = stateVotes.getOrPut(state.index) { linkedMapOf() }
        votes[key] = (votes[key] ?: 0) + 1
        maybeResolveLength()
        return snapshot()
    }

    fun snapshot(): Snapshot {
        val needed = expectedStates
        val decoded = if (needed != null && (0 until needed).all { stateVotes[it]?.isNotEmpty() == true }) {
            val symbols = ArrayList<Int>()
            for (index in 0 until needed) symbols += winner(index).toList()
            val headerBytes = OpticalPamCodec.symbolsToBytes(symbols.take(20))
            val packetBytes = headerBytes?.let(OpticalCompactTextCodec::expectedPacketBytes)
            val packet = if (packetBytes != null) OpticalPamCodec.symbolsToBytes(symbols.take(packetBytes * 4)) else null
            packet?.let(OpticalCompactTextCodec::decode)
        } else null
        return Snapshot(decoded, stateVotes.count { it.value.isNotEmpty() }, needed, rejectedStates)
    }

    private fun maybeResolveLength() {
        if (expectedStates != null) return
        val symbolsPerState = OpticalGridCodec.dataCellCount(profile.gridSize)
        val needForHeader = ceil(20.0 / symbolsPerState.toDouble()).toInt()
        if (!(0 until needForHeader).all { stateVotes[it]?.isNotEmpty() == true }) return
        val symbols = ArrayList<Int>()
        for (index in 0 until needForHeader) symbols += winner(index).toList()
        val header = OpticalPamCodec.symbolsToBytes(symbols.take(20)) ?: return
        val packetBytes = OpticalCompactTextCodec.expectedPacketBytes(header) ?: return
        expectedStates = ceil((packetBytes * 4).toDouble() / symbolsPerState.toDouble()).toInt().coerceIn(1, 64)
    }

    private fun winner(index: Int): IntArray {
        val key = stateVotes[index].orEmpty().entries.maxWithOrNull(compareBy<Map.Entry<String, Int>> { it.value }.thenBy { it.key })?.key.orEmpty()
        return if (key.isBlank()) IntArray(0) else key.split(',').map { it.toInt() }.toIntArray()
    }
}

/** 8-PPM: one flash per three-bit symbol, plus six XOR parity stripes and seven sync flashes. */
object Torch8PpmCodec {
    const val PREAMBLE_FLASHES = 7
    const val SYMBOL_SLOTS = 9
    const val SYNC_SLOT = 8
    const val PARITY_STRIPES = 6

    data class Transmission(val dataSymbols: IntArray, val paritySymbols: IntArray, val symbols: IntArray) {
        val flashCount: Int get() = PREAMBLE_FLASHES + symbols.size
    }

    fun encode(packet: ByteArray, parityStripes: Int = PARITY_STRIPES): Transmission {
        val data = bytesTo3BitSymbols(packet)
        val stripes = minOf(parityStripes.coerceIn(1, PARITY_STRIPES), maxOf(1, ceil(data.size / 12.0).toInt()))
        val parity = IntArray(stripes)
        data.forEachIndexed { i, value -> parity[i % stripes] = parity[i % stripes] xor value }
        return Transmission(data, parity, data + parity)
    }

    fun expectedSymbolCounts(headerBytes: ByteArray, parityStripes: Int = PARITY_STRIPES): Pair<Int, Int>? {
        val packetBytes = OpticalCompactTextCodec.expectedPacketBytes(headerBytes) ?: return null
        val data = ceil(packetBytes * 8.0 / 3.0).toInt()
        val parity = minOf(parityStripes.coerceIn(1, PARITY_STRIPES), maxOf(1, ceil(data / 12.0).toInt()))
        return data to parity
    }

    fun bytesTo3BitSymbols(bytes: ByteArray): IntArray {
        val count = ceil(bytes.size * 8.0 / 3.0).toInt()
        val out = IntArray(count)
        repeat(count) { symbolIndex ->
            var value = 0
            repeat(3) { bitInSymbol ->
                val bit = symbolIndex * 3 + bitInSymbol
                value = value shl 1
                if (bit < bytes.size * 8) {
                    val byte = bytes[bit / 8].toInt() and 0xff
                    value = value or ((byte ushr (7 - bit % 8)) and 1)
                }
            }
            out[symbolIndex] = value
        }
        return out
    }

    fun symbolsToBytes(symbols: List<Int>, byteCount: Int): ByteArray? {
        if (symbols.any { it !in 0..7 } || symbols.size * 3 < byteCount * 8) return null
        val out = ByteArray(byteCount)
        repeat(byteCount * 8) { bit ->
            val symbol = symbols[bit / 3]
            val bitInSymbol = bit % 3
            val value = (symbol ushr (2 - bitInSymbol)) and 1
            if (value != 0) {
                val byteIndex = bit / 8
                val bitIndex = 7 - bit % 8
                out[byteIndex] = (out[byteIndex].toInt() or (1 shl bitIndex)).toByte()
            }
        }
        return out
    }
}

/** Maps camera-observed rising edges to 8-PPM symbol indexes after a seven-flash cadence preamble. */
class Torch8PpmPulseDecoder(slotMs: Int, private val toleranceFraction: Double = 0.72) {
    data class Event(val cycleStarted: Boolean = false, val symbolIndex: Int? = null, val slot: Int? = null)
    private var slotMs = slotMs.coerceAtLeast(20).toDouble()
    private val recent = ArrayList<Long>(8)
    private var dataEpochMs: Double? = null

    fun reset() { recent.clear(); dataEpochMs = null }
    fun setSlotMs(value: Int) { slotMs = value.coerceAtLeast(20).toDouble(); reset() }

    fun feedPulse(timestampMs: Long): List<Event> {
        if (recent.lastOrNull()?.let { timestampMs <= it } == true) return emptyList()
        recent += timestampMs
        if (recent.size > Torch8PpmCodec.PREAMBLE_FLASHES) recent.removeAt(0)
        val out = mutableListOf<Event>()
        if (recent.size == Torch8PpmCodec.PREAMBLE_FLASHES && isPreamble(recent)) {
            dataEpochMs = recent.last().toDouble() + slotMs // sync is in reserved slot 8; next data window starts one slot later
            out += Event(cycleStarted = true)
            return out
        }
        val epoch = dataEpochMs ?: return out
        val relative = timestampMs - epoch
        if (relative < -slotMs) return out
        val period = slotMs * Torch8PpmCodec.SYMBOL_SLOTS
        val around = (relative / period).roundToInt()
        var bestWindow = -1
        var bestSlot = -1
        var bestError = Double.POSITIVE_INFINITY
        for (window in (around - 1)..(around + 1)) {
            if (window < 0) continue
            for (slot in 0..7) {
                val expected = epoch + window * period + slot * slotMs
                val error = kotlin.math.abs(timestampMs - expected)
                if (error < bestError) { bestError = error; bestWindow = window; bestSlot = slot }
            }
        }
        if (bestWindow >= 0 && bestError <= slotMs * toleranceFraction) out += Event(symbolIndex = bestWindow, slot = bestSlot)
        return out
    }

    private fun isPreamble(times: List<Long>): Boolean {
        val target = slotMs * Torch8PpmCodec.SYMBOL_SLOTS
        return times.zipWithNext().all { (a, b) -> kotlin.math.abs((b - a) - target) <= slotMs * toleranceFraction }
    }
}

/** Vote/erasure collector across looping 8-PPM cycles. */
class Torch8PpmCompactCollector(private val parityStripes: Int = Torch8PpmCodec.PARITY_STRIPES) {
    private val votes = linkedMapOf<Int, MutableMap<Int, Int>>()
    private var expectedDataSymbols: Int? = null
    private var expectedParitySymbols: Int? = null
    var cyclesSeen: Int = 0
        private set
    var recoveredErasures: Int = 0
        private set

    data class Snapshot(val decoded: OpticalCompactTextCodec.Decoded?, val symbolsHeld: Int, val symbolsNeeded: Int?, val cycles: Int, val recovered: Int)

    fun reset() { votes.clear(); expectedDataSymbols = null; expectedParitySymbols = null; cyclesSeen = 0; recoveredErasures = 0 }
    fun startCycle() { cyclesSeen++ }

    fun offer(index: Int, slot: Int): Snapshot {
        if (index < 0 || slot !in 0..7) return snapshot()
        val total = expectedDataSymbols?.let { it + (expectedParitySymbols ?: 0) }
        if (total != null && index >= total) return snapshot()
        val bucket = votes.getOrPut(index) { linkedMapOf() }
        bucket[slot] = (bucket[slot] ?: 0) + 1
        maybeResolveLength()
        return snapshot()
    }

    fun snapshot(): Snapshot {
        val dataCount = expectedDataSymbols
        val parityCount = expectedParitySymbols
        val total = if (dataCount != null && parityCount != null) dataCount + parityCount else null
        val decoded = if (dataCount != null && parityCount != null) decode(dataCount, parityCount) else null
        return Snapshot(decoded, votes.count { it.value.isNotEmpty() }, total, cyclesSeen, recoveredErasures)
    }

    private fun maybeResolveLength() {
        if (expectedDataSymbols != null) return
        val headerSymbolCount = ceil(5 * 8.0 / 3.0).toInt()
        if (!(0 until headerSymbolCount).all { votes[it]?.isNotEmpty() == true }) return
        val headerSymbols = (0 until headerSymbolCount).map(::winner)
        val header = Torch8PpmCodec.symbolsToBytes(headerSymbols, 5) ?: return
        Torch8PpmCodec.expectedSymbolCounts(header, parityStripes)?.let { (data, parity) -> expectedDataSymbols = data; expectedParitySymbols = parity }
    }

    private fun decode(dataCount: Int, parityCount: Int): OpticalCompactTextCodec.Decoded? {
        val data = Array<Int?>(dataCount) { index -> votes[index]?.takeIf { it.isNotEmpty() }?.let { winner(index) } }
        val parity = Array<Int?>(parityCount) { p -> votes[dataCount + p]?.takeIf { it.isNotEmpty() }?.let { winner(dataCount + p) } }
        var recovered = 0
        for (stripe in 0 until parityCount) {
            var xor = 0
            val missing = mutableListOf<Int>()
            var index = stripe
            while (index < dataCount) {
                val value = data[index]
                if (value == null) missing += index else xor = xor xor value
                index += parityCount
            }
            if (missing.size == 1 && parity[stripe] != null) {
                data[missing.single()] = xor xor parity[stripe]!!
                recovered++
            } else if (missing.isNotEmpty()) return null
        }
        if (data.any { it == null }) return null
        val header = Torch8PpmCodec.symbolsToBytes(data.take(ceil(5 * 8.0 / 3.0).toInt()).map { it!! }, 5) ?: return null
        val packetBytes = OpticalCompactTextCodec.expectedPacketBytes(header) ?: return null
        val packet = Torch8PpmCodec.symbolsToBytes(data.map { it!! }, packetBytes) ?: return null
        val decoded = OpticalCompactTextCodec.decode(packet) ?: return null
        recoveredErasures = maxOf(recoveredErasures, recovered)
        return decoded
    }

    private fun winner(index: Int): Int = votes[index].orEmpty().entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key ?: 0
}
