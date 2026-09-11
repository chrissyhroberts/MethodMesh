package com.example.methodmesh.modules.signals

import java.util.zip.CRC32
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure optical physical-layer codecs used above MMS/1.
 *
 * The analogue front ends deliberately emit erasures rather than guessed symbols.
 * A physical frame is only returned when its local CRC passes and the compact MMS/1
 * payload reconstructs to an MMS frame that passes the existing MMS/1 frame CRC.
 */
object SignalOpticalProfiles {
    data class Profile(
        val id: String,
        val label: String,
        val pamSymbolMs: Int,
        val gridSize: Int,
        val gridDwellMs: Int,
        val torchSlotMs: Int,
        val torchPulseMs: Int,
        val shardBytes: Int,
        val defaultZoom: Float,
        val roiId: String
    )

    val all = listOf(
        Profile("long", "Long range", pamSymbolMs = 180, gridSize = 4, gridDwellMs = 180, torchSlotMs = 55, torchPulseMs = 45, shardBytes = 24, defaultZoom = 4f, roiId = "pinpoint"),
        Profile("balanced", "Balanced", pamSymbolMs = 110, gridSize = 6, gridDwellMs = 120, torchSlotMs = 40, torchPulseMs = 32, shardBytes = 48, defaultZoom = 3f, roiId = "focus"),
        Profile("fast", "Fast", pamSymbolMs = 75, gridSize = 8, gridDwellMs = 90, torchSlotMs = 32, torchPulseMs = 25, shardBytes = 64, defaultZoom = 2f, roiId = "focus")
    )

    fun byId(raw: String?): Profile = all.firstOrNull { it.id == raw?.lowercase() } ?: all.first()
}

internal object OpticalPhysicalPacket {
    private const val MAX_PAYLOAD = FskPhysicalCodec.MAX_FRAME_BYTES

    fun encode(frame: String): ByteArray {
        val payload = FskPhysicalCodec.encodeFramePayload(frame)
        require(payload.size in 1..MAX_PAYLOAD) { "Optical MMS physical payload is too large (${payload.size} bytes)." }
        val out = ByteArray(2 + payload.size + 4)
        out[0] = ((payload.size ushr 8) and 0xff).toByte()
        out[1] = (payload.size and 0xff).toByte()
        payload.copyInto(out, 2)
        writeCrc(out, 2 + payload.size, crc32(payload))
        return out
    }

    fun decode(packet: ByteArray): String? {
        if (packet.size < 7) return null
        val length = ((packet[0].toInt() and 0xff) shl 8) or (packet[1].toInt() and 0xff)
        if (length !in 1..MAX_PAYLOAD || packet.size != 2 + length + 4) return null
        val payload = packet.copyOfRange(2, 2 + length)
        val expected = readCrc(packet, 2 + length)
        if (crc32(payload) != expected) return null
        return FskPhysicalCodec.decodeFramePayloadResult(payload).frame
    }

    fun expectedPacketBytes(firstTwo: ByteArray): Int? {
        if (firstTwo.size < 2) return null
        val length = ((firstTwo[0].toInt() and 0xff) shl 8) or (firstTwo[1].toInt() and 0xff)
        if (length !in 1..MAX_PAYLOAD) return null
        return 2 + length + 4
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = CRC32(); crc.update(bytes); return crc.value
    }

    private fun writeCrc(out: ByteArray, at: Int, value: Long) {
        out[at] = ((value ushr 24) and 0xff).toByte()
        out[at + 1] = ((value ushr 16) and 0xff).toByte()
        out[at + 2] = ((value ushr 8) and 0xff).toByte()
        out[at + 3] = (value and 0xff).toByte()
    }

    private fun readCrc(bytes: ByteArray, at: Int): Long =
        ((bytes[at].toLong() and 0xff) shl 24) or
            ((bytes[at + 1].toLong() and 0xff) shl 16) or
            ((bytes[at + 2].toLong() and 0xff) shl 8) or
            (bytes[at + 3].toLong() and 0xff)
}

object OpticalPamCodec {
    private val PREAMBLE = intArrayOf(0, 3, 0, 3, 1, 2, 3, 1, 0, 2, 2, 1)

    fun encodeFrame(frame: String): IntArray = PREAMBLE + bytesToSymbols(OpticalPhysicalPacket.encode(frame))
    internal fun preamble(): IntArray = PREAMBLE.copyOf()

    fun bytesToSymbols(bytes: ByteArray): IntArray {
        val out = IntArray(bytes.size * 4)
        var at = 0
        bytes.forEach { b ->
            val v = b.toInt() and 0xff
            out[at++] = (v ushr 6) and 3
            out[at++] = (v ushr 4) and 3
            out[at++] = (v ushr 2) and 3
            out[at++] = v and 3
        }
        return out
    }

    fun symbolsToBytes(symbols: List<Int>): ByteArray? {
        if (symbols.size % 4 != 0 || symbols.any { it !in 0..3 }) return null
        return ByteArray(symbols.size / 4) { index ->
            val at = index * 4
            ((symbols[at] shl 6) or (symbols[at + 1] shl 4) or (symbols[at + 2] shl 2) or symbols[at + 3]).toByte()
        }
    }
}

/** Incremental 4-PAM physical-frame decoder with join-in-progress preamble search. */
class OpticalPamStreamDecoder {
    data class Telemetry(val frames: Int, val physicalRejects: Int, val bufferedSymbols: Int, val preambleMismatch: Int)
    private val buffer = ArrayList<Int>(8192)
    private val preamble = OpticalPamCodec.preamble().toList()
    private var frames = 0
    private var rejects = 0
    private var lastMismatch = -1

    fun reset() { buffer.clear(); frames = 0; rejects = 0; lastMismatch = -1 }

    fun feed(level: Int?): List<String> {
        if (level == null || level !in 0..3) {
            // An uncertain analogue sample is an erasure. Do not insert a guessed symbol.
            return emptyList()
        }
        buffer += level
        val out = mutableListOf<String>()
        while (true) {
            val start = findPreamble()
            if (start < 0) {
                if (buffer.size > preamble.size * 3) buffer.subList(0, buffer.size - preamble.size * 2).clear()
                break
            }
            if (start > 0) buffer.subList(0, start).clear()
            val headerSymbols = preamble.size + 8 // 2-byte physical length
            if (buffer.size < headerSymbols) break
            val headerBytes = OpticalPamCodec.symbolsToBytes(buffer.subList(preamble.size, headerSymbols)) ?: break
            val totalBytes = OpticalPhysicalPacket.expectedPacketBytes(headerBytes)
            if (totalBytes == null) {
                rejects++; buffer.removeAt(0); continue
            }
            val need = preamble.size + totalBytes * 4
            if (buffer.size < need) break
            val packet = OpticalPamCodec.symbolsToBytes(buffer.subList(preamble.size, need))
            val frame = packet?.let(OpticalPhysicalPacket::decode)
            if (frame != null) { frames++; out += frame } else rejects++
            buffer.subList(0, need).clear()
        }
        return out
    }

    fun telemetry() = Telemetry(frames, rejects, buffer.size, lastMismatch)

    private fun findPreamble(): Int {
        if (buffer.size < preamble.size) return -1
        var best = -1
        var bestMismatch = Int.MAX_VALUE
        for (start in 0..buffer.size - preamble.size) {
            var mismatch = 0
            for (i in preamble.indices) {
                if (buffer[start + i] != preamble[i]) mismatch++
                if (mismatch > 1) break
            }
            if (mismatch < bestMismatch) { best = start; bestMismatch = mismatch }
            if (mismatch == 0) break
        }
        lastMismatch = if (best >= 0) bestMismatch else -1
        return if (bestMismatch <= 1) best else -1
    }
}

/** Adaptive four-level classifier for scalar screen luminance. */
class AdaptivePam4Detector(private val minimumSpan: Double = 20.0) {
    data class Detection(val level: Int?, val low: Double, val high: Double, val normalized: Double, val confidence: Double)
    private var low = Double.POSITIVE_INFINITY
    private var high = Double.NEGATIVE_INFINITY

    fun reset() { low = Double.POSITIVE_INFINITY; high = Double.NEGATIVE_INFINITY }

    fun feed(value: Double): Detection {
        if (!value.isFinite()) return Detection(null, low, high, Double.NaN, 0.0)
        if (!low.isFinite()) low = value
        if (!high.isFinite()) high = value
        if (value < low) low = value else low += (value - low) * 0.0015
        if (value > high) high = value else high += (value - high) * 0.0015
        val span = high - low
        if (span < minimumSpan) return Detection(null, low, high, 0.0, 0.0)
        val normalized = ((value - low) / span).coerceIn(0.0, 1.0)
        val raw = normalized * 3.0
        val level = raw.roundToInt().coerceIn(0, 3)
        val distance = abs(raw - level)
        val confidence = (1.0 - distance / 0.5).coerceIn(0.0, 1.0)
        return Detection(level.takeIf { confidence >= 0.34 }, low, high, normalized, confidence)
    }
}

object OpticalGridCodec {
    private val pilotLevels = intArrayOf(0, 3, 1, 2) // TL, TR, BR, BL. Rotation can be recovered uniquely.

    data class State(val index: Int, val size: Int, val levels: IntArray, val dataSymbols: IntArray)

    fun encodeFrame(frame: String, profile: SignalOpticalProfiles.Profile): List<State> =
        encodeBytes(OpticalPhysicalPacket.encode(frame), profile)

    fun encodeBytes(packet: ByteArray, profile: SignalOpticalProfiles.Profile): List<State> {
        val size = profile.gridSize
        require(size >= 4)
        val packetSymbols = OpticalPamCodec.bytesToSymbols(packet)
        val dataCells = dataIndices(size)
        require(dataCells.isNotEmpty())
        val stateCount = ceil(packetSymbols.size.toDouble() / dataCells.size.toDouble()).toInt().coerceAtLeast(1)
        require(stateCount <= 64) { "Optical grid frame needs $stateCount states; use a smaller MMS shard or faster grid profile." }
        return List(stateCount) { stateIndex ->
            val levels = IntArray(size * size)
            writePilots(levels, size)
            val controls = controlIndices(size)
            val indexDigits = intArrayOf((stateIndex ushr 4) and 3, (stateIndex ushr 2) and 3, stateIndex and 3)
            controls.forEachIndexed { i, cell -> levels[cell] = indexDigits[i] }
            val start = stateIndex * dataCells.size
            val data = IntArray(dataCells.size) { offset -> packetSymbols.getOrElse(start + offset) { 0 } }
            dataCells.forEachIndexed { i, cell -> levels[cell] = data[i] }
            State(stateIndex, size, levels, data)
        }
    }

    fun decodeQuantized(levels: IntArray, size: Int): State? {
        if (levels.size != size * size || levels.any { it !in 0..3 }) return null
        val canonical = orientQuantized(levels, size) ?: return null
        val controls = controlIndices(size)
        val index = (canonical[controls[0]] shl 4) or (canonical[controls[1]] shl 2) or canonical[controls[2]]
        val data = dataIndices(size).map { canonical[it] }.toIntArray()
        return State(index, size, canonical, data)
    }

    /** Decode raw camera-cell luma, simultaneously resolving rotation and amplitude scale. */
    fun decodeLuma(values: DoubleArray, size: Int, minimumSpan: Double = 14.0): State? {
        if (values.size != size * size || values.any { !it.isFinite() }) return null
        var bestLevels: IntArray? = null
        var bestScore = Double.POSITIVE_INFINITY
        repeat(4) { rotations ->
            val canonical = rotate(values, size, rotations)
            val corners = cornerIndices(size)
            val low = canonical[corners[0]]
            val high = canonical[corners[1]]
            val span = high - low
            if (span < minimumSpan) return@repeat
            val quantized = IntArray(canonical.size)
            var score = 0.0
            canonical.forEachIndexed { i, v ->
                val raw = ((v - low) / span * 3.0).coerceIn(-0.75, 3.75)
                val q = raw.roundToInt().coerceIn(0, 3)
                quantized[i] = q
                score += abs(raw - q).coerceAtMost(1.0) * 0.02
            }
            val expected = pilotLevels
            corners.forEachIndexed { i, cell ->
                if (quantized[cell] != expected[i]) score += 3.0
                val target = expected[i].toDouble()
                val raw = ((canonical[cell] - low) / span * 3.0)
                score += abs(raw - target)
            }
            if (score < bestScore) { bestScore = score; bestLevels = quantized }
        }
        val levels = bestLevels ?: return null
        if (bestScore > 3.2) return null
        return decodeQuantized(levels, size)
    }

    fun rotate(levels: IntArray, size: Int, clockwiseQuarterTurns: Int): IntArray {
        var out = levels.copyOf()
        repeat(((clockwiseQuarterTurns % 4) + 4) % 4) {
            val next = IntArray(out.size)
            for (r in 0 until size) for (c in 0 until size) next[c * size + (size - 1 - r)] = out[r * size + c]
            out = next
        }
        return out
    }

    private fun rotate(values: DoubleArray, size: Int, clockwiseQuarterTurns: Int): DoubleArray {
        var out = values.copyOf()
        repeat(((clockwiseQuarterTurns % 4) + 4) % 4) {
            val next = DoubleArray(out.size)
            for (r in 0 until size) for (c in 0 until size) next[c * size + (size - 1 - r)] = out[r * size + c]
            out = next
        }
        return out
    }

    private fun orientQuantized(levels: IntArray, size: Int): IntArray? {
        val corners = cornerIndices(size)
        repeat(4) { turns ->
            val candidate = rotate(levels, size, turns)
            if (corners.indices.all { candidate[corners[it]] == pilotLevels[it] }) return candidate
        }
        return null
    }

    private fun writePilots(levels: IntArray, size: Int) {
        cornerIndices(size).forEachIndexed { i, cell -> levels[cell] = pilotLevels[i] }
    }

    private fun cornerIndices(size: Int) = intArrayOf(0, size - 1, size * size - 1, (size - 1) * size)

    private fun controlIndices(size: Int): IntArray = nonCornerIndices(size).take(3).toIntArray()
    private fun dataIndices(size: Int): IntArray = nonCornerIndices(size).drop(3).toIntArray()
    fun dataCellCount(size: Int): Int = dataIndices(size).size

    private fun nonCornerIndices(size: Int): List<Int> {
        val corners = cornerIndices(size).toSet()
        return (0 until size * size).filterNot { it in corners }
    }
}

/** Requires repeated identical decoded grid states before accepting a camera observation. */
class OpticalGridStableGate(private val requiredCopies: Int = 2) {
    private var last: OpticalGridCodec.State? = null
    private var copies = 0
    private var emittedIndex: Int? = null

    fun reset() { last = null; copies = 0; emittedIndex = null }

    fun feed(state: OpticalGridCodec.State?): OpticalGridCodec.State? {
        if (state == null) { last = null; copies = 0; return null }
        val same = last?.let { it.index == state.index && it.size == state.size && it.dataSymbols.contentEquals(state.dataSymbols) } == true
        if (same) copies++ else { last = state; copies = 1 }
        if (copies >= requiredCopies && emittedIndex != state.index) {
            emittedIndex = state.index
            return state
        }
        return null
    }
}

/** Sequential grid-state collector. Any missing state erases the physical MMS frame. */
class OpticalGridFrameCollector(private val profile: SignalOpticalProfiles.Profile) {
    private val symbols = ArrayList<Int>()
    private var expectedIndex = 0
    private var expectedPacketSymbols: Int? = null
    var rejectedSequences: Int = 0
        private set

    fun reset() { symbols.clear(); expectedIndex = 0; expectedPacketSymbols = null }

    fun feed(state: OpticalGridCodec.State): String? {
        if (state.size != profile.gridSize) return null
        if (state.index == 0) reset()
        if (state.index != expectedIndex) {
            rejectedSequences++
            reset()
            if (state.index != 0) return null
        }
        symbols += state.dataSymbols.toList()
        expectedIndex++
        if (expectedIndex >= 64) { rejectedSequences++; reset(); return null }

        if (expectedPacketSymbols == null && symbols.size >= 8) {
            val firstTwo = OpticalPamCodec.symbolsToBytes(symbols.take(8)) ?: return null
            val bytes = OpticalPhysicalPacket.expectedPacketBytes(firstTwo)
            if (bytes == null) { rejectedSequences++; reset(); return null }
            expectedPacketSymbols = bytes * 4
        }
        val needed = expectedPacketSymbols ?: return null
        if (symbols.size < needed) return null
        val packet = OpticalPamCodec.symbolsToBytes(symbols.take(needed))
        val frame = packet?.let(OpticalPhysicalPacket::decode)
        if (frame == null) rejectedSequences++
        reset()
        return frame
    }
}

object TorchPpmCodec {
    // Quaternary preamble chosen to have no long repeated run and strong transition diversity.
    private val PREAMBLE = intArrayOf(0, 3, 1, 2, 0, 2, 3, 1, 3, 0)
    fun encodeFrame(frame: String): IntArray = PREAMBLE + OpticalPamCodec.bytesToSymbols(OpticalPhysicalPacket.encode(frame))
    internal fun preamble() = PREAMBLE.copyOf()
}

/**
 * Converts leading-edge pulse timestamps into 4-PPM symbols.
 * Each data symbol is bracketed by two sync pulses exactly five slots apart;
 * the data pulse occurs one, two, three or four slots after the first sync.
 */
class TorchPpmPulseDecoder(slotMs: Int, private val toleranceFraction: Double = 0.32) {
    private var slotMs = slotMs.coerceAtLeast(20).toDouble()
    private val pulses = ArrayList<Long>(8)

    fun setSlotMs(value: Int) { slotMs = value.coerceAtLeast(20).toDouble(); reset() }
    fun reset() = pulses.clear()

    fun feedPulse(timestampMs: Long): List<Int> {
        if (pulses.lastOrNull()?.let { timestampMs <= it } == true) return emptyList()
        pulses += timestampMs
        val out = mutableListOf<Int>()
        while (pulses.size >= 3) {
            val a = pulses[0]; val b = pulses[1]; val c = pulses[2]
            val totalSlots = (c - a) / slotMs
            val dataSlots = (b - a) / slotMs
            val slot = dataSlots.roundToInt() - 1
            val validClock = abs(totalSlots - 5.0) <= toleranceFraction * 5.0
            val validData = slot in 0..3 && abs(dataSlots - (slot + 1).toDouble()) <= toleranceFraction
            if (validClock && validData) {
                out += slot
                // c is the next sync pulse.
                pulses.removeAt(0); pulses.removeAt(0)
            } else {
                pulses.removeAt(0)
            }
        }
        if (pulses.size > 6) pulses.subList(0, pulses.size - 3).clear()
        return out
    }
}

class TorchPpmFrameDecoder {
    private val buffer = ArrayList<Int>(8192)
    private val preamble = TorchPpmCodec.preamble().toList()
    var physicalRejects = 0
        private set

    fun reset() { buffer.clear(); physicalRejects = 0 }

    fun feed(slot: Int): List<String> {
        if (slot !in 0..3) return emptyList()
        buffer += slot
        val out = mutableListOf<String>()
        while (true) {
            val start = findPreamble()
            if (start < 0) {
                if (buffer.size > preamble.size * 3) buffer.subList(0, buffer.size - preamble.size * 2).clear()
                break
            }
            if (start > 0) buffer.subList(0, start).clear()
            val headerSymbols = preamble.size + 8
            if (buffer.size < headerSymbols) break
            val firstTwo = OpticalPamCodec.symbolsToBytes(buffer.subList(preamble.size, headerSymbols)) ?: break
            val totalBytes = OpticalPhysicalPacket.expectedPacketBytes(firstTwo)
            if (totalBytes == null) { physicalRejects++; buffer.removeAt(0); continue }
            val need = preamble.size + totalBytes * 4
            if (buffer.size < need) break
            val packet = OpticalPamCodec.symbolsToBytes(buffer.subList(preamble.size, need))
            val frame = packet?.let(OpticalPhysicalPacket::decode)
            if (frame != null) out += frame else physicalRejects++
            buffer.subList(0, need).clear()
        }
        return out
    }

    private fun findPreamble(): Int {
        if (buffer.size < preamble.size) return -1
        for (start in 0..buffer.size - preamble.size) {
            var mismatch = 0
            for (i in preamble.indices) {
                if (buffer[start + i] != preamble[i]) mismatch++
                if (mismatch > 1) break
            }
            if (mismatch <= 1) return start
        }
        return -1
    }
}

/**
 * Camera-clock recovery for whole-screen 4-PAM. The transmitter and receiver share
 * a discrete symbol period. Acquisition searches camera observations for the known
 * quaternary preamble at that period, then samples one camera observation per symbol
 * at the recovered phase. A missed symbol drops lock rather than shifting the frame.
 */
class OpticalPamTimedDecoder(symbolMs: Int) {
    data class Telemetry(val locked: Boolean, val samples: Int, val lockLosses: Int, val emittedSymbols: Int)
    private data class Timed(val t: Long, val level: Int)

    private var symbolMs = symbolMs.coerceAtLeast(40).toLong()
    private val history = ArrayList<Timed>(512)
    private val stream = OpticalPamStreamDecoder()
    private var epoch: Long? = null
    private var nextIndex = 0
    private var lockLosses = 0
    private var emittedSymbols = 0

    fun setSymbolMs(value: Int) { symbolMs = value.coerceAtLeast(40).toLong(); reset() }
    fun reset() { history.clear(); stream.reset(); epoch = null; nextIndex = 0; lockLosses = 0; emittedSymbols = 0 }

    fun feed(timestampMs: Long, level: Int?): List<String> {
        if (level != null && level in 0..3) history += Timed(timestampMs, level)
        if (history.size > 900) history.subList(0, history.size - 600).clear()
        if (epoch == null) tryAcquire()
        val out = mutableListOf<String>()
        val e = epoch ?: return out
        val margin = max(12L, (symbolMs * 0.32).toLong())
        while (timestampMs >= e + nextIndex * symbolMs + margin) {
            val target = e + nextIndex * symbolMs
            val sample = nearest(target, margin)
            if (sample == null) {
                lockLosses++
                epoch = null
                nextIndex = 0
                stream.reset()
                tryAcquire()
                break
            }
            stream.feed(sample.level).forEach(out::add)
            emittedSymbols++
            nextIndex++
        }
        return out
    }

    fun telemetry() = Telemetry(epoch != null, history.size, lockLosses, emittedSymbols)

    private fun tryAcquire() {
        val preamble = OpticalPamCodec.preamble()
        if (history.size < preamble.size) return
        val latest = history.last().t
        val margin = max(12L, (symbolMs * 0.32).toLong())
        val earliestCandidate = (history.size - 260).coerceAtLeast(0)
        var bestEpoch: Long? = null
        var bestMismatch = Int.MAX_VALUE
        for (i in earliestCandidate until history.size) {
            if (history[i].level != preamble[0]) continue
            val candidate = history[i].t
            if (candidate + (preamble.size - 1) * symbolMs + margin > latest) continue
            var mismatch = 0
            for (j in preamble.indices) {
                val observed = nearest(candidate + j * symbolMs, margin)?.level
                if (observed == null || observed != preamble[j]) mismatch++
                if (mismatch > 1) break
            }
            if (mismatch < bestMismatch) { bestMismatch = mismatch; bestEpoch = candidate }
            if (mismatch == 0) break
        }
        if (bestEpoch != null && bestMismatch <= 1) {
            epoch = bestEpoch
            nextIndex = 0
            stream.reset()
        }
    }

    private fun nearest(target: Long, tolerance: Long): Timed? {
        var best: Timed? = null
        var distance = Long.MAX_VALUE
        for (i in history.indices.reversed()) {
            val sample = history[i]
            val d = abs(sample.t - target)
            if (d < distance) { best = sample; distance = d }
            if (sample.t < target - tolerance && best != null) break
        }
        return best?.takeIf { distance <= tolerance }
    }
}
