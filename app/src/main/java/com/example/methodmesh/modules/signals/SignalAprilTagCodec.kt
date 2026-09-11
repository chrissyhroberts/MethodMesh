package com.example.methodmesh.modules.signals

import kotlin.math.ceil

/**
 * Minimal tag16h5 renderer/decoder used by the screen optical modem.
 *
 * This intentionally implements only the family/codeword layer MethodMesh needs:
 * a full-screen tag with a white quiet zone, black border and 4x4 payload cells.
 * It does not embed the native AprilTag detector; camera localisation is handled
 * by the constrained MethodMesh analyser in SignalOpticalVisualEngine.kt.
 */
object AprilTag16h5 {
    const val TAG_COUNT = 30
    const val TOTAL_WIDTH = 8
    const val BORDER_WIDTH = 6
    const val DATA_BITS = 16

    // AprilRobotics tag16h5 codewords (current family ordering).
    private val codes = intArrayOf(
        0x27c8, 0x31b6, 0x3859, 0x569c, 0x6c76, 0x7ddb, 0xaf09, 0xf5a1,
        0xfb8b, 0x1cb9, 0x28ca, 0xe8dc, 0x1426, 0x5770, 0x9253, 0xb702,
        0x063a, 0x8f34, 0xb4c0, 0x51ec, 0xe6f0, 0x5fa4, 0xdd43, 0x1aaa,
        0xe62f, 0x6dbc, 0xb6eb, 0xde10, 0x154d, 0xb57a
    )

    // Bit coordinates inside the 6x6 black-border coordinate system.
    private val bitX = intArrayOf(1, 2, 3, 2, 4, 4, 4, 3, 4, 3, 2, 3, 1, 1, 1, 2)
    private val bitY = intArrayOf(1, 1, 1, 2, 1, 2, 3, 2, 4, 4, 4, 3, 4, 3, 2, 3)

    data class Detection(val id: Int, val rotationQuarterTurns: Int, val hamming: Int)

    /** true = white module, false = black module. */
    fun modules(id: Int): Array<BooleanArray> {
        require(id in 0 until TAG_COUNT)
        val out = Array(TOTAL_WIDTH) { BooleanArray(TOTAL_WIDTH) { true } }
        // 6x6 black tag body inside one-module white quiet zone.
        for (y in 1..6) for (x in 1..6) out[y][x] = false
        val code = codes[id]
        for (i in 0 until DATA_BITS) {
            val white = ((code ushr (DATA_BITS - 1 - i)) and 1) != 0
            out[1 + bitY[i]][1 + bitX[i]] = white
        }
        return out
    }

    /** Decode a physical 4x4 white/black data matrix. Up to one wrong bit is accepted. */
    fun decodeData(data: Array<BooleanArray>, maxHamming: Int = 1): Detection? {
        if (data.size != 4 || data.any { it.size != 4 }) return null
        var best: Detection? = null
        var candidate = copy4(data)
        repeat(4) { rotation ->
            val observed = codeFromData(candidate)
            codes.forEachIndexed { id, code ->
                val hamming = Integer.bitCount(observed xor code)
                if (hamming <= maxHamming && (best == null || hamming < best!!.hamming)) {
                    best = Detection(id, rotation, hamming)
                }
            }
            candidate = rotateClockwise(candidate)
        }
        return best
    }

    fun dataForId(id: Int): Array<BooleanArray> {
        val modules = modules(id)
        return Array(4) { y -> BooleanArray(4) { x -> modules[y + 2][x + 2] } }
    }

    private fun codeFromData(data: Array<BooleanArray>): Int {
        var code = 0
        for (i in 0 until DATA_BITS) {
            code = code shl 1
            val x = bitX[i] - 1
            val y = bitY[i] - 1
            if (data[y][x]) code = code or 1
        }
        return code
    }

    private fun copy4(input: Array<BooleanArray>): Array<BooleanArray> =
        Array(4) { y -> input[y].clone() }

    private fun rotateClockwise(input: Array<BooleanArray>): Array<BooleanArray> =
        Array(4) { y -> BooleanArray(4) { x -> input[3 - x][y] } }
}

/** Full-screen AprilTag burst transport for compact optical text. */
object AprilTagBurstCodec {
    const val START_ID = 29
    const val END_ID = 28
    const val BLOCK_MARKER_BASE = 16
    const val MAX_BLOCKS = 12
    const val DATA_PER_BLOCK = 12
    const val DATA_BANK_A = 0
    const val DATA_BANK_B = 8

    enum class Kind { START, END, BLOCK, DATA, PARITY }
    data class State(val tagId: Int, val kind: Kind, val block: Int? = null, val position: Int? = null)

    fun encode(packet: ByteArray): List<State> {
        val symbols = Torch8PpmCodec.bytesTo3BitSymbols(packet)
        val blocks = ceil(symbols.size / DATA_PER_BLOCK.toDouble()).toInt().coerceAtLeast(1)
        require(blocks <= MAX_BLOCKS) { "AprilTag Burst compact payload needs $blocks blocks; maximum is $MAX_BLOCKS." }
        val out = ArrayList<State>(2 + blocks * (DATA_PER_BLOCK + 2))
        out += State(START_ID, Kind.START)
        repeat(blocks) { block ->
            out += State(BLOCK_MARKER_BASE + block, Kind.BLOCK, block = block)
            var parity = 0
            repeat(DATA_PER_BLOCK) { position ->
                val absolute = block * DATA_PER_BLOCK + position
                val value = if (absolute < symbols.size) symbols[absolute] else 0
                parity = parity xor value
                val bank = if (position % 2 == 0) DATA_BANK_A else DATA_BANK_B
                out += State(bank + value, Kind.DATA, block, position)
            }
            // Position 12 continues alternation with bank A.
            out += State(DATA_BANK_A + parity, Kind.PARITY, block, DATA_PER_BLOCK)
        }
        out += State(END_ID, Kind.END)
        return out
    }
}

/**
 * Loop-accumulating AprilTag Burst collector. Block markers re-anchor position;
 * alternating data banks make a single dropped observation an explicit erasure.
 */
class AprilTagBurstCollector {
    private val votes = Array(AprilTagBurstCodec.MAX_BLOCKS) {
        Array(AprilTagBurstCodec.DATA_PER_BLOCK) { linkedMapOf<Int, Int>() }
    }
    private var currentBlock: Int? = null
    private var nextPosition = 0
    private var currentValues = arrayOfNulls<Int>(AprilTagBurstCodec.DATA_PER_BLOCK)
    private var currentParity: Int? = null
    private var expectedPacketBytes: Int? = null
    private var expectedDataSymbols: Int? = null
    private var expectedBlocks: Int? = null

    var cyclesSeen: Int = 0
        private set
    var rejectedTags: Int = 0
        private set
    var recoveredErasures: Int = 0
        private set

    data class Snapshot(
        val decoded: OpticalCompactTextCodec.Decoded?,
        val symbolsHeld: Int,
        val symbolsNeeded: Int?,
        val blocksHeld: Int,
        val blocksNeeded: Int?,
        val cycles: Int,
        val recovered: Int,
        val rejected: Int
    )

    fun reset() {
        votes.forEach { block -> block.forEach { it.clear() } }
        clearCurrent()
        expectedPacketBytes = null
        expectedDataSymbols = null
        expectedBlocks = null
        cyclesSeen = 0
        rejectedTags = 0
        recoveredErasures = 0
    }

    fun offer(tagId: Int): Snapshot {
        when {
            tagId == AprilTagBurstCodec.START_ID -> {
                finalizeCurrentBlock()
                cyclesSeen++
                clearCurrent()
            }
            tagId == AprilTagBurstCodec.END_ID -> {
                finalizeCurrentBlock()
                clearCurrent()
            }
            tagId in AprilTagBurstCodec.BLOCK_MARKER_BASE until (AprilTagBurstCodec.BLOCK_MARKER_BASE + AprilTagBurstCodec.MAX_BLOCKS) -> {
                finalizeCurrentBlock()
                currentBlock = tagId - AprilTagBurstCodec.BLOCK_MARKER_BASE
                nextPosition = 0
                currentValues = arrayOfNulls(AprilTagBurstCodec.DATA_PER_BLOCK)
                currentParity = null
            }
            tagId in 0..15 -> acceptDataTag(tagId)
            else -> rejectedTags++
        }
        resolveLengthIfPossible()
        return snapshot()
    }

    fun snapshot(): Snapshot {
        val decoded = decodeIfPossible()
        val needed = expectedDataSymbols
        val held = if (needed == null) countHeld() else countHeld(needed)
        val blockNeed = expectedBlocks
        val blockHeld = if (blockNeed == null) countBlocksWithEvidence() else (0 until blockNeed).count { blockHasEvidence(it) }
        return Snapshot(decoded, held, needed, blockHeld, blockNeed, cyclesSeen, recoveredErasures, rejectedTags)
    }

    private fun acceptDataTag(tagId: Int) {
        if (currentBlock == null) { rejectedTags++; return }
        val bank = if (tagId >= AprilTagBurstCodec.DATA_BANK_B) 1 else 0
        val value = tagId and 0x7

        if (nextPosition < AprilTagBurstCodec.DATA_PER_BLOCK) {
            var expectedBank = nextPosition and 1
            if (bank != expectedBank) {
                // One missed observation flips the expected bank. Leave an explicit erasure.
                nextPosition++
                expectedBank = nextPosition and 1
            }
            if (nextPosition >= AprilTagBurstCodec.DATA_PER_BLOCK || bank != expectedBank) {
                rejectedTags++
                return
            }
            currentValues[nextPosition] = value
            nextPosition++
            return
        }

        // Parity follows 12 data symbols and therefore uses bank A.
        if (nextPosition == AprilTagBurstCodec.DATA_PER_BLOCK && bank == 0) {
            currentParity = value
            nextPosition++
        } else {
            rejectedTags++
        }
    }

    private fun finalizeCurrentBlock() {
        val block = currentBlock ?: return
        val parity = currentParity ?: run { rejectedTags++; return }
        val missing = currentValues.indices.filter { currentValues[it] == null }
        if (missing.size > 1) {
            // Discard the whole ambiguous block. A later loop can supply it cleanly.
            rejectedTags += missing.size
            return
        }
        var xor = 0
        currentValues.forEach { if (it != null) xor = xor xor it }
        if (missing.size == 1) {
            currentValues[missing.single()] = xor xor parity
            recoveredErasures++
        } else if (xor != parity) {
            // Sequence looked complete but parity says a shift/corruption occurred.
            rejectedTags++
            return
        }
        currentValues.forEachIndexed { position, value ->
            val v = value ?: return@forEachIndexed
            val bucket = votes[block][position]
            bucket[v] = (bucket[v] ?: 0) + 1
        }
    }

    private fun clearCurrent() {
        currentBlock = null
        nextPosition = 0
        currentValues = arrayOfNulls(AprilTagBurstCodec.DATA_PER_BLOCK)
        currentParity = null
    }

    private fun winner(bucket: Map<Int, Int>): Int? =
        bucket.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })?.key

    private fun blockValues(block: Int): Array<Int?> =
        Array(AprilTagBurstCodec.DATA_PER_BLOCK) { pos -> winner(votes[block][pos]) }

    private fun resolveLengthIfPossible() {
        if (expectedPacketBytes != null) return
        val headerSymbols = ceil(5 * 8.0 / 3.0).toInt()
        val first = ArrayList<Int>(headerSymbols)
        var block = 0
        while (first.size < headerSymbols && block < AprilTagBurstCodec.MAX_BLOCKS) {
            val values = blockValues(block)
            for (value in values) {
                if (first.size >= headerSymbols) break
                if (value == null) return
                first += value
            }
            block++
        }
        val header = Torch8PpmCodec.symbolsToBytes(first, 5) ?: return
        val packetBytes = OpticalCompactTextCodec.expectedPacketBytes(header) ?: return
        expectedPacketBytes = packetBytes
        expectedDataSymbols = ceil(packetBytes * 8.0 / 3.0).toInt()
        expectedBlocks = ceil(expectedDataSymbols!! / AprilTagBurstCodec.DATA_PER_BLOCK.toDouble()).toInt()
    }

    private fun decodeIfPossible(): OpticalCompactTextCodec.Decoded? {
        resolveLengthIfPossible()
        val byteCount = expectedPacketBytes ?: return null
        val symbolCount = expectedDataSymbols ?: return null
        val blocks = expectedBlocks ?: return null
        val symbols = ArrayList<Int>(symbolCount)
        repeat(blocks) { block ->
            val values = blockValues(block)
            for (value in values) {
                if (symbols.size >= symbolCount) break
                if (value == null) return null
                symbols += value
            }
        }
        val packet = Torch8PpmCodec.symbolsToBytes(symbols, byteCount) ?: return null
        return OpticalCompactTextCodec.decode(packet)
    }

    private fun countHeld(limit: Int = Int.MAX_VALUE): Int {
        var count = 0
        outer@ for (block in 0 until AprilTagBurstCodec.MAX_BLOCKS) {
            val values = blockValues(block)
            for (value in values) {
                if (count >= limit) break@outer
                if (value != null) count++
            }
        }
        return count
    }

    private fun blockHasEvidence(block: Int): Boolean = votes[block].any { it.isNotEmpty() }
    private fun countBlocksWithEvidence(): Int = (0 until AprilTagBurstCodec.MAX_BLOCKS).count(::blockHasEvidence)
}


/** Require two consecutive detections of a new tag, then emit only once until it changes. */
class AprilTagBurstStableGate(private val required: Int = 2) {
    private var candidate: Int? = null
    private var count = 0
    private var emitted: Int? = null

    fun reset() { candidate = null; count = 0; emitted = null }

    fun feed(tagId: Int?): Int? {
        if (tagId == null) { candidate = null; count = 0; return null }
        if (tagId != candidate) { candidate = tagId; count = 1 } else count++
        if (count < required || emitted == tagId) return null
        emitted = tagId
        return tagId
    }
}
