package com.example.methodmesh.modules.signals

/**
 * Capture-first QR burst physical-link helpers.
 *
 * The payload/recovery layer remains SignalQrTransferCodec + MMS/1. This class only
 * defines fast display profiles and keeps offline decoder accounting/deduplication
 * separate from the camera runtime.
 */
internal object SignalQrBurstProfiles {
    data class Profile(
        val id: String,
        val label: String,
        val qrPerSecond: Int,
        val shardBytes: Int,
        val robustness: SignalPacketCodec.Robustness
    ) {
        val dwellMs: Int get() = (1000.0 / qrPerSecond.toDouble()).toInt().coerceAtLeast(20)
    }

    val all = listOf(
        Profile("safe", "Safe · 10 QR/s", 10, 320, SignalPacketCodec.Robustness.ROBUST),
        Profile("balanced", "Balanced · 15 QR/s", 15, 448, SignalPacketCodec.Robustness.ROBUST),
        Profile("fast", "Fast · 20 QR/s", 20, 512, SignalPacketCodec.Robustness.FAST),
        Profile("max", "Experimental · 30 QR/s", 30, 512, SignalPacketCodec.Robustness.FAST)
    )

    fun byId(raw: String?): Profile = all.firstOrNull { it.id == raw?.trim()?.lowercase() } ?: all[1]
}

internal class SignalQrBurstOfflineCollector(
    private var messageIdFilter: String = ""
) {
    data class Snapshot(
        val qrDecodes: Int,
        val uniqueQrFrames: Int,
        val duplicates: Int,
        val rejectedQrPayloads: Int,
        val unrelatedQrPayloads: Int,
        val transfer: SignalQrTransferCodec.State
    )

    private val uniqueRaw = linkedSetOf<String>()
    private val accumulator = SignalQrTransferCodec.Accumulator()
    private var qrDecodes = 0
    private var duplicates = 0
    private var rejected = 0
    private var unrelated = 0

    fun reset(filter: String = messageIdFilter) {
        messageIdFilter = filter.trim()
        uniqueRaw.clear()
        accumulator.reset()
        qrDecodes = 0
        duplicates = 0
        rejected = 0
        unrelated = 0
    }

    fun updateFilter(filter: String) {
        val clean = filter.trim()
        if (clean != messageIdFilter) reset(clean)
    }

    fun offerDecoded(raw: String): Snapshot {
        qrDecodes++
        val clean = raw.trim()
        if (clean.isBlank()) {
            rejected++
            return snapshot()
        }
        if (!uniqueRaw.add(clean)) {
            duplicates++
            return snapshot()
        }
        val parsed = SignalPacketCodec.parse(clean)
        val frame = parsed.frame
        if (frame == null) {
            rejected++
            return snapshot()
        }
        if (messageIdFilter.isNotBlank() && !frame.messageId.startsWith(messageIdFilter)) {
            unrelated++
            return snapshot()
        }
        accumulator.offer(clean)
        return snapshot()
    }

    fun snapshot(): Snapshot = Snapshot(
        qrDecodes = qrDecodes,
        uniqueQrFrames = uniqueRaw.size,
        duplicates = duplicates,
        rejectedQrPayloads = rejected,
        unrelatedQrPayloads = unrelated,
        transfer = accumulator.state()
    )
}

internal fun qrBurstCycleDurationMs(frameCount: Int, qrPerSecond: Int): Long {
    if (frameCount <= 0 || qrPerSecond <= 0) return 0L
    return kotlin.math.ceil(frameCount * 1000.0 / qrPerSecond.toDouble()).toLong()
}
