package com.example.methodmesh.modules.signals

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.ceil

/**
 * Multi-message QR transfer layer above MMS/1.
 *
 * MMS/1 deliberately caps one Reed-Solomon matrix at 60 source shards. QR file
 * transfer can therefore exceed one MMS/1 message by splitting the checksum-
 * bearing content envelope into independently recoverable MMS/1 segments. The
 * receiver reassembles segments and verifies a SHA-256 over the complete content
 * envelope before the inner content SHA-256 is checked.
 */
internal object SignalQrTransferCodec {
    private val MAGIC = byteArrayOf('M'.code.toByte(), 'Q'.code.toByte(), 'S'.code.toByte(), '1'.code.toByte())
    private const val ID_BYTES = 12
    private const val SHA_BYTES = 32
    private const val HEADER_BYTES = 4 + ID_BYTES + 2 + 2 + 4 + SHA_BYTES + 4
    private const val SEGMENT_DATA_SHARDS = 20

    data class EncodedTransfer(
        val transferId: String,
        val contentEnvelopeBytes: Int,
        val segmentCount: Int,
        val packets: List<SignalPacketCodec.EncodedMessage>
    ) {
        val frames: List<String> = packets.flatMap { it.frames }
        val dataShardCount: Int = packets.sumOf { it.dataShardCount }
        val parityFrameCount: Int = packets.sumOf { it.parityFrameCount }
        val messageCrc32Hex: String = if (packets.size == 1) packets.first().messageCrc32Hex else "MULTI"
    }

    data class State(
        val transferId: String? = null,
        val expectedSegments: Int = 0,
        val completeSegments: Int = 0,
        val packetGroupsSeen: Int = 0,
        val acceptedFrames: Int = 0,
        val rejectedFrames: Int = 0,
        val recoveredMissingSources: Int = 0,
        val contentEnvelope: ByteArray? = null,
        val transferSha256Expected: String = "",
        val transferSha256Reconstructed: String = "",
        val transferSha256Verified: Boolean = false
    ) {
        val complete: Boolean get() = contentEnvelope != null && transferSha256Verified
    }

    private data class Segment(
        val transferId: String,
        val index: Int,
        val count: Int,
        val totalBytes: Int,
        val envelopeSha256: ByteArray,
        val payload: ByteArray
    )

    fun encode(
        contentEnvelope: ByteArray,
        robustness: SignalPacketCodec.Robustness,
        shardBytes: Int,
        transferId: String = SignalPacketCodec.newMessageId().take(ID_BYTES)
    ): EncodedTransfer {
        require(contentEnvelope.isNotEmpty()) { "QR transfer content is empty." }
        require(transferId.matches(Regex("[A-Za-z0-9_-]{4,$ID_BYTES}"))) { "Invalid QR transfer ID." }
        val width = shardBytes.coerceIn(24, 512)
        val maxMmsPayload = width * SEGMENT_DATA_SHARDS
        val chunkBytes = (maxMmsPayload - HEADER_BYTES).coerceAtLeast(width)
        val count = ceil(contentEnvelope.size.toDouble() / chunkBytes.toDouble()).toInt().coerceAtLeast(1)
        require(count <= 4096) { "QR transfer requires too many segments." }
        val digest = sha256(contentEnvelope)
        val packets = ArrayList<SignalPacketCodec.EncodedMessage>(count)
        for (index in 0 until count) {
            val from = index * chunkBytes
            val to = minOf(contentEnvelope.size, from + chunkBytes)
            val chunk = contentEnvelope.copyOfRange(from, to)
            val segment = encodeSegment(transferId, index, count, contentEnvelope.size, digest, chunk)
            val messageId = "$transferId-${index.toString(36)}"
            packets += SignalPacketCodec.encode(segment, robustness, width, messageId)
        }
        return EncodedTransfer(transferId, contentEnvelope.size, count, packets)
    }

    /**
     * Incremental receiver for long QR transfers. A 1 MiB blast may contain thousands
     * of QR frames; rebuilding every Reed-Solomon collector after each scan is therefore
     * deliberately avoided. Complete MMS/1 segments are decoded once and retained.
     */
    class Accumulator {
        private val rawSeen = linkedSetOf<String>()
        private val collectors = linkedMapOf<String, SignalFrameCollector>()
        private val completedSegments = linkedMapOf<String, Segment>()
        private var invalidFrames = 0
        private var legacyMessageId: String? = null
        private var legacyPayload: ByteArray? = null

        fun reset() {
            rawSeen.clear()
            collectors.clear()
            completedSegments.clear()
            invalidFrames = 0
            legacyMessageId = null
            legacyPayload = null
        }

        fun restore(rawFrames: List<String>): State {
            reset()
            rawFrames.forEach { offer(it) }
            return state()
        }

        fun offer(raw: String): State {
            if (!rawSeen.add(raw)) return state()
            val parsed = SignalPacketCodec.parse(raw)
            val frame = parsed.frame
            if (frame == null) {
                invalidFrames++
                return state()
            }
            val collector = collectors.getOrPut(frame.messageId) { SignalFrameCollector() }
            val before = collector.state().complete
            val after = collector.offer(raw)
            if (!before && after.complete) {
                after.payload?.let { payload ->
                    val segment = decodeSegment(payload)
                    if (segment != null) {
                        completedSegments[frame.messageId] = segment
                    } else if (SignalContentEnvelope.decode(payload) != null) {
                        // Backward compatibility with v0.3 single-message QR payloads.
                        legacyMessageId = after.messageId
                        legacyPayload = payload
                    }
                }
            }
            return state()
        }

        fun state(): State {
            val groupStates = collectors.values.map { it.state() }
            val accepted = groupStates.sumOf { it.acceptedFrames }
            val rejected = invalidFrames + groupStates.sumOf { it.rejectedFrames }
            val recovered = groupStates.sumOf { it.recoveredMissingSources }

            val direct = legacyPayload
            if (direct != null && completedSegments.isEmpty()) {
                val digest = sha256(direct).toHex()
                return State(
                    transferId = legacyMessageId,
                    expectedSegments = 1,
                    completeSegments = 1,
                    packetGroupsSeen = collectors.size,
                    acceptedFrames = accepted,
                    rejectedFrames = rejected,
                    recoveredMissingSources = recovered,
                    contentEnvelope = direct,
                    transferSha256Expected = digest,
                    transferSha256Reconstructed = digest,
                    transferSha256Verified = true
                )
            }

            val segments = completedSegments.values.toList()
            if (segments.isEmpty()) {
                return State(
                    packetGroupsSeen = collectors.size,
                    acceptedFrames = accepted,
                    rejectedFrames = rejected,
                    recoveredMissingSources = recovered
                )
            }

            val chosenId = segments.groupingBy { it.transferId }.eachCount().maxByOrNull { it.value }?.key
                ?: return State(packetGroupsSeen = collectors.size, acceptedFrames = accepted, rejectedFrames = rejected, recoveredMissingSources = recovered)
            val matching = segments.filter { it.transferId == chosenId }
            val reference = matching.first()
            val expectedCount = reference.count
            val compatible = matching.filter {
                it.count == expectedCount && it.totalBytes == reference.totalBytes &&
                    MessageDigest.isEqual(it.envelopeSha256, reference.envelopeSha256)
            }.distinctBy { it.index }
            if (compatible.size < expectedCount) {
                return State(
                    transferId = chosenId,
                    expectedSegments = expectedCount,
                    completeSegments = compatible.size,
                    packetGroupsSeen = collectors.size,
                    acceptedFrames = accepted,
                    rejectedFrames = rejected,
                    recoveredMissingSources = recovered,
                    transferSha256Expected = reference.envelopeSha256.toHex()
                )
            }
            val ordered = compatible.sortedBy { it.index }
            if (ordered.indices.any { ordered[it].index != it }) {
                return State(
                    transferId = chosenId,
                    expectedSegments = expectedCount,
                    completeSegments = compatible.size,
                    packetGroupsSeen = collectors.size,
                    acceptedFrames = accepted,
                    rejectedFrames = rejected,
                    recoveredMissingSources = recovered,
                    transferSha256Expected = reference.envelopeSha256.toHex()
                )
            }
            val assembled = ByteArray(reference.totalBytes)
            var offset = 0
            ordered.forEach { segment ->
                if (offset + segment.payload.size > assembled.size) {
                    return State(
                        transferId = chosenId,
                        expectedSegments = expectedCount,
                        completeSegments = compatible.size,
                        packetGroupsSeen = collectors.size,
                        acceptedFrames = accepted,
                        rejectedFrames = rejected,
                        recoveredMissingSources = recovered,
                        transferSha256Expected = reference.envelopeSha256.toHex()
                    )
                }
                segment.payload.copyInto(assembled, offset)
                offset += segment.payload.size
            }
            if (offset != assembled.size) {
                return State(
                    transferId = chosenId,
                    expectedSegments = expectedCount,
                    completeSegments = compatible.size,
                    packetGroupsSeen = collectors.size,
                    acceptedFrames = accepted,
                    rejectedFrames = rejected,
                    recoveredMissingSources = recovered,
                    transferSha256Expected = reference.envelopeSha256.toHex()
                )
            }
            val rebuilt = sha256(assembled)
            val verified = MessageDigest.isEqual(reference.envelopeSha256, rebuilt)
            return State(
                transferId = chosenId,
                expectedSegments = expectedCount,
                completeSegments = compatible.size,
                packetGroupsSeen = collectors.size,
                acceptedFrames = accepted,
                rejectedFrames = rejected,
                recoveredMissingSources = recovered,
                contentEnvelope = assembled.takeIf { verified },
                transferSha256Expected = reference.envelopeSha256.toHex(),
                transferSha256Reconstructed = rebuilt.toHex(),
                transferSha256Verified = verified
            )
        }
    }

    /** Rebuild a transfer from all unique raw MMS/1 QR frames seen so far. */
    fun decode(rawFrames: List<String>): State = Accumulator().restore(rawFrames)

    private fun encodeSegment(transferId: String, index: Int, count: Int, totalBytes: Int, sha: ByteArray, payload: ByteArray): ByteArray {
        val id = transferId.padEnd(ID_BYTES, '_').take(ID_BYTES).toByteArray(Charsets.US_ASCII)
        return ByteBuffer.allocate(HEADER_BYTES + payload.size).order(ByteOrder.BIG_ENDIAN).apply {
            put(MAGIC)
            put(id)
            putShort(index.toShort())
            putShort(count.toShort())
            putInt(totalBytes)
            put(sha)
            putInt(payload.size)
            put(payload)
        }.array()
    }

    private fun decodeSegment(bytes: ByteArray): Segment? = runCatching {
        if (bytes.size < HEADER_BYTES) return@runCatching null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val magic = ByteArray(4); buffer.get(magic)
        if (!magic.contentEquals(MAGIC)) return@runCatching null
        val idBytes = ByteArray(ID_BYTES); buffer.get(idBytes)
        val id = String(idBytes, Charsets.US_ASCII).trimEnd('_')
        val index = buffer.short.toInt() and 0xffff
        val count = buffer.short.toInt() and 0xffff
        val totalBytes = buffer.int
        val sha = ByteArray(SHA_BYTES); buffer.get(sha)
        val length = buffer.int
        if (id.isBlank() || count <= 0 || index !in 0 until count || totalBytes <= 0 || length < 0 || buffer.remaining() != length) return@runCatching null
        val payload = ByteArray(length); buffer.get(payload)
        Segment(id, index, count, totalBytes, sha, payload)
    }.getOrNull()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
