package com.example.methodmesh.modules.signals

import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Standalone JVM smoke test for the pure Signals codecs.
 *
 * Run from a MethodMesh checkout or beside these sources, for example:
 * kotlinc SignalPacketCodec.kt SignalContentEnvelope.kt SignalAcousticPayloadCodec.kt MorseCodec.kt FskPhysicalCodec.kt SignalQrTransferCodec.kt docs/SignalCodecSmoke.kt -include-runtime -d /tmp/signals-smoke.jar
 * java -jar /tmp/signals-smoke.jar
 */
fun main() {
    var assertions = 0
    fun requireSmoke(condition: Boolean, message: String) {
        assertions++
        if (!condition) error(message)
    }

    requireSmoke(MorseCodec.encode("SOS") == "... --- ...", "Morse encode")
    requireSmoke(MorseCodec.decode("... --- ...") == "SOS", "Morse decode")
    requireSmoke(MorseCodec.dotDurationMs(12) == 100L, "Morse PARIS dot")

    fun simulateMorse(text: String, actualDotMs: Long, initialDotMs: Long, jitter: Double = 0.0): MorseTimingDecoder.Snapshot {
        val random = Random(text.hashCode() xor actualDotMs.toInt() xor initialDotMs.toInt())
        val decoder = MorseTimingDecoder(initialDotMs, autoTiming = true)
        var now = 0L
        decoder.reset(now)
        fun duration(base: Long): Long {
            if (jitter <= 0.0) return base
            val scale = 1.0 + (random.nextDouble() * 2.0 - 1.0) * jitter
            return (base * scale).roundToLong().coerceAtLeast(1L)
        }
        MorseCodec.timeline(text, loopGapUnits = 0).forEach { element ->
            when (element) {
                is MorseCodec.Element.Mark -> {
                    now += 1
                    decoder.update(true, now)
                    now += duration(actualDotMs * element.units)
                    decoder.update(false, now)
                }
                is MorseCodec.Element.Gap -> now += duration(actualDotMs * element.units)
            }
        }
        return decoder.flush(now + actualDotMs * 7)
    }

    listOf(80L, 120L, 200L).forEach { actual ->
        listOf(0.7, 1.0, 1.4).forEach { factor ->
            val snapshot = simulateMorse("FIELD TEST 123", actual, (actual * factor).roundToLong(), jitter = 0.08)
            requireSmoke(snapshot.decodedText.trim() == "FIELD TEST 123", "Morse adaptive actual=$actual factor=$factor: $snapshot")
        }
    }
    listOf("OOO", "EEEE", "TTT", "SOS").forEach { text ->
        val snapshot = simulateMorse(text, 100, 100, jitter = 0.05)
        requireSmoke(snapshot.decodedText.trim() == text, "Morse edge traffic $text: $snapshot")
    }

    // Physical FSK frame parser: arbitrary leading bits and chunk boundaries.
    val physicalPayload = "MMS1|TEST|1|1|00000000|0|QQ|00000000"
    val physicalBits = FskPhysicalCodec.encodeFrame(physicalPayload)
    val parser = FskBitstreamParser()
    var parsedFrames = emptyList<String>()
    parsedFrames += parser.feed(listOf(true, false, true, true, false) + physicalBits.take(31))
    parsedFrames += parser.feed(physicalBits.drop(31).take(53).asIterable())
    parsedFrames += parser.feed(physicalBits.drop(84).asIterable())
    requireSmoke(parsedFrames == listOf(physicalPayload), "FSK chunked physical framing")

    // Tone-run decoder: simulate exact dominant-tone samples in 10 ms windows.
    listOf(20, 30, 40, 50, 60, 80, 100, 150).forEach { bitMs ->
        val sourceBits = listOf(true, true, false, true, false, false, false, true)
        val decoder = FskToneRunDecoder(bitMs)
        var now = 1_000L
        val emitted = mutableListOf<Boolean>()
        sourceBits.forEach { bit ->
            val samples = (bitMs / 10.0).roundToLong().coerceAtLeast(1L).toInt()
            repeat(samples) {
                emitted += decoder.feed(bit, now)
                now += 10L
            }
        }
        emitted += decoder.flush(now)
        requireSmoke(emitted == sourceBits, "FSK run decode bitMs=$bitMs emitted=$emitted")
    }

    val random = Random(0x51A1)
    val shardWidths = listOf(24, 32, 48, 64, 96, 128, 256)
    val profiles = SignalPacketCodec.Robustness.values()
    repeat(250) { trial ->
        val shardBytes = shardWidths[trial % shardWidths.size]
        val maxLength = SignalPacketCodec.MAX_DATA_SHARDS * shardBytes
        val length = when (trial % 9) {
            0 -> 0
            1 -> 1
            2 -> 23.coerceAtMost(maxLength)
            3 -> 24.coerceAtMost(maxLength)
            4 -> 95.coerceAtMost(maxLength)
            5 -> 96.coerceAtMost(maxLength)
            6 -> 511.coerceAtMost(maxLength)
            7 -> 1024.coerceAtMost(maxLength)
            else -> random.nextInt(2, minOf(1800, maxLength).coerceAtLeast(3))
        }
        val payload = ByteArray(length) { random.nextInt(0, 256).toByte() }
        val profile = profiles[trial % profiles.size]
        val encoded = SignalPacketCodec.encode(payload, profile, shardBytes, "T${trial.toString().padStart(5, '0')}")
        val k = encoded.dataShardCount
        val m = encoded.frames.size
        requireSmoke(m >= k, "MMS frame count")
        encoded.frames.forEach { requireSmoke(SignalPacketCodec.parse(it).valid, "Generated frame must parse") }

        // Systematic, parity-heavy, random, and every loop-aligned consecutive k-window.
        val subsets = mutableListOf<List<String>>()
        subsets += encoded.frames.take(k)
        subsets += encoded.frames.takeLast(k)
        repeat(6) { subsets += encoded.frames.shuffled(random).take(k) }
        for (start in encoded.frames.indices) {
            subsets += List(k) { offset -> encoded.frames[(start + offset) % m] }
        }
        subsets.forEachIndexed { subsetIndex, subset ->
            val collector = SignalFrameCollector()
            subset.shuffled(random).forEach(collector::offer)
            val state = collector.state()
            requireSmoke(state.complete, "RS recovery trial=$trial subset=$subsetIndex profile=$profile k=$k: $state")
            requireSmoke(state.payload!!.contentEquals(payload), "RS payload trial=$trial subset=$subsetIndex")
            requireSmoke(state.messageCrcVerified, "RS whole-message CRC trial=$trial subset=$subsetIndex")
        }

        val duplicateCollector = SignalFrameCollector()
        val first = encoded.frames.first()
        duplicateCollector.offer(first)
        duplicateCollector.offer(first)
        duplicateCollector.offer(first)
        encoded.frames.drop(1).take(k - 1).forEach(duplicateCollector::offer)
        requireSmoke(duplicateCollector.state().complete, "Duplicate frames must not block reconstruction")
        requireSmoke(duplicateCollector.state().redundantFrames >= 2, "Duplicate count")

        // Mutate frame body without updating frame CRC: parser must reject it before RS.
        val chars = first.toCharArray()
        val firstSeparator = first.indexOf('|')
        val secondSeparator = first.indexOf('|', firstSeparator + 1)
        val mutateAt = (firstSeparator + 1).coerceAtMost(secondSeparator - 1)
        chars[mutateAt] = if (chars[mutateAt] == 'X') 'Y' else 'X'
        requireSmoke(!SignalPacketCodec.parse(chars.concatToString()).valid, "Frame CRC rejection")
    }


    // v0.3 repeated-message consensus: noisy copies converge rather than last-copy wins.
    run {
        val consensus = MorseRepeatConsensus()
        requireSmoke(consensus.add("SELLO, WORLD").text == "SELLO, WORLD", "Morse consensus first copy")
        consensus.add("HELLO, WORLD")
        val voted = consensus.add("HELLO, WORLD")
        requireSmoke(voted.text == "HELLO, WORLD", "Morse consensus majority")
        requireSmoke(voted.copies == 3, "Morse consensus copy count")
        requireSmoke(voted.confidence > 0.85 && voted.confidence < 0.95, "Morse consensus confidence is agreement + evidence weighted")
    }

    // v0.3 end-to-end object integrity envelope: SHA-256 travels with text/files.
    run {
        val textEnvelope = SignalContentEnvelope.encodeText("checksum test")
        val decoded = SignalContentEnvelope.decode(textEnvelope)!!
        requireSmoke(decoded.type == "text", "Content envelope text type")
        requireSmoke(decoded.text == "checksum test", "Content envelope text payload")
        requireSmoke(decoded.checksumVerified, "Content envelope text SHA-256")

        val fileBytes = ByteArray(1024) { (it * 31).toByte() }
        val fileEnvelope = SignalContentEnvelope.encodeFile(fileBytes, "field.bin", "application/octet-stream")
        val fileDecoded = SignalContentEnvelope.decode(fileEnvelope)!!
        requireSmoke(fileDecoded.type == "file", "Content envelope file type")
        requireSmoke(fileDecoded.fileName == "field.bin", "Content envelope file name")
        requireSmoke(fileDecoded.payload.contentEquals(fileBytes), "Content envelope file payload")
        requireSmoke(fileDecoded.checksumVerified, "Content envelope file SHA-256")
        val corrupted = fileEnvelope.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 0x01).toByte() }
        requireSmoke(SignalContentEnvelope.decode(corrupted)?.checksumVerified == false, "Content envelope detects reconstructed corruption")
    }

    // v0.3 receiver clock acquisition: majority-vote 10 ms decision windows into 30 ms bits.
    run {
        val raw = "MMS1|TEST|1|1|00000000|0|QQ|00000000"
        val bits = FskPhysicalCodec.encodeFrame(raw)
        val decoder = FskClockRecoveryDecoder(30)
        val recovered = mutableListOf<String>()
        bits.forEachIndexed { bitIndex, bit ->
            repeat(3) { windowIndex ->
                val decision = if (bitIndex % 29 == 0 && windowIndex == 0) !bit else bit
                decoder.feed(decision).forEach(recovered::add)
            }
        }
        requireSmoke(raw in recovered, "FSK clock recovery with sparse edge errors")
        val telemetry = decoder.telemetry()
        requireSmoke(telemetry.syncDetections >= 1, "FSK sync telemetry")
        requireSmoke(telemetry.physicalFrames >= 1, "FSK physical-frame telemetry")
    }

    // v0.4.2 compact acoustic wire form: generated MMS/1 IDs pack binary on the
    // physical link and reconstruct the exact CRC-bearing ASCII frame at RX.
    run {
        val raw = SignalPacketCodec.encodeText(
            "HELLO",
            SignalPacketCodec.Robustness.FAST,
            shardBytes = 24,
            messageId = "0123456789abcdef"
        ).frames.first()
        val compactBits = FskPhysicalCodec.encodeFrame(raw)
        val taggedAsciiBits = (8 + 2 + 2 + 1 + raw.toByteArray().size) * 8
        requireSmoke(compactBits.size < taggedAsciiBits, "FSK compact physical form is smaller than tagged ASCII")
        val decoder = FskClockRecoveryDecoder(40)
        val recovered = mutableListOf<String>()
        compactBits.forEach { bit -> repeat(4) { decoder.feed(bit).forEach(recovered::add) } }
        requireSmoke(raw in recovered, "FSK compact physical form reconstructs exact MMS/1 frame")
    }


    // v0.4 explicit START framing: repeated cycles refine one message rather than concatenate.
    run {
        val decoder = MorseTimingDecoder(100, autoTiming = true)
        var now = 0L
        decoder.reset(now)
        var last = decoder.flush(now)
        repeat(3) {
            MorseCodec.timeline("HELLO", loopGapUnits = 10).forEach { element ->
                when (element) {
                    is MorseCodec.Element.Mark -> {
                        now += 1
                        decoder.update(true, now)
                        now += 100L * element.units
                        last = decoder.update(false, now)
                    }
                    is MorseCodec.Element.Gap -> now += 100L * element.units
                }
            }
        }
        last = decoder.flush(now + 1000L)
        requireSmoke(last.decodedText == "HELLO", "Morse START framing keeps one message: $last")
        requireSmoke(last.completedCopies >= 3, "Morse START framing counts repeated copies: $last")
        requireSmoke(last.consensusConfidence > 0.89 && last.consensusConfidence < 0.95, "Morse framed confidence shrinks finite evidence: $last")
    }


    // v0.4.3 cyclic START/END framing: a mid-cycle orphan is quarantined until a
    // later complete bounded cycle establishes its right-hand alignment.
    run {
        val decoder = MorseTimingDecoder(100, autoTiming = false)
        var now = 0L
        decoder.reset(now)
        fun feed(elements: List<MorseCodec.Element>) {
            elements.forEach { element ->
                when (element) {
                    is MorseCodec.Element.Mark -> {
                        now += 1
                        decoder.update(true, now)
                        now += 100L * element.units
                        decoder.update(false, now)
                    }
                    is MorseCodec.Element.Gap -> now += 100L * element.units
                }
            }
        }
        val full = MorseCodec.timeline("HELLO WORLD", loopGapUnits = 10)
        val firstData = full.indexOfFirst { it is MorseCodec.Element.Mark && it.kind == MorseCodec.MarkKind.DATA }
        val firstEnd = full.indexOfFirst { it is MorseCodec.Element.Mark && it.kind == MorseCodec.MarkKind.END }
        val mid = (firstData + (firstEnd - firstData) / 2).coerceIn(firstData, firstEnd)
        feed(full.subList(mid, full.size))
        var snap = decoder.flush(now + 50)
        requireSmoke(snap.completedCopies == 0, "Morse orphan must not vote before START: $snap")
        requireSmoke(snap.orphanObservations == 1, "Morse END-anchored orphan retained: $snap")
        feed(full)
        snap = decoder.flush(now + 50)
        requireSmoke(snap.completedCopies == 1, "First complete START-END cycle accepted: $snap")
        requireSmoke(snap.decodedText == "HELLO WORLD", "Complete frame establishes message geometry: $snap")
        requireSmoke(snap.consensusConfidence < 0.9, "One complete cycle must not claim near-certainty: $snap")
        feed(full)
        feed(full)
        snap = decoder.flush(now + 50)
        requireSmoke(snap.completedCopies == 3, "Three bounded repeats counted: $snap")
        requireSmoke(snap.consensusConfidence > 0.88, "Repeated bounded copies refine confidence: $snap")
    }

    // Camera front-end edge debounce: rolling-shutter / flare transition frames must
    // remain stable for multiple samples before becoming a timing edge.
    run {
        val gate = MorseStableSignalGate(requiredStableSamples = 2)
        requireSmoke(gate.feed(false) == null, "Stable gate first OFF waits")
        requireSmoke(gate.feed(false) == false, "Stable gate emits stable OFF")
        requireSmoke(gate.feed(true) == null, "Stable gate rejects one-frame ON transition")
        requireSmoke(gate.feed(false) == null, "Stable gate transition back is not immediate")
        requireSmoke(gate.feed(false) == null, "Already-emitted OFF is not duplicated")
        requireSmoke(gate.feed(true) == null, "Stable gate first ON waits")
        requireSmoke(gate.feed(true) == true, "Stable gate emits stable ON")
    }


    // v0.4.4 slow-link compression: repetitive text compresses and round-trips;
    // short text stays raw so compression headers never make tiny messages slower.
    run {
        val repetitive = "METHODMESH FIELD SIGNAL ".repeat(40)
        val compressed = SignalAcousticPayloadCodec.encodeText(repetitive)
        requireSmoke(compressed.compressed, "Acoustic repetitive text should compress")
        requireSmoke(compressed.wireBytes < compressed.originalBytes, "Acoustic compression reduces bytes")
        requireSmoke(SignalAcousticPayloadCodec.decodeText(compressed.bytes) == repetitive, "Acoustic compressed round-trip")
        val short = SignalAcousticPayloadCodec.encodeText("HELLO")
        requireSmoke(!short.compressed, "Short acoustic text stays raw")
        requireSmoke(SignalAcousticPayloadCodec.decodeText(short.bytes) == "HELLO", "Raw acoustic round-trip")
    }

    // v0.4.4 compact A2 framing can be clocked at 40 ms and reconstructs an exact
    // MMS/1 frame while counting no payload-decode failure.
    run {
        val wire = SignalAcousticPayloadCodec.encodeText("FAST FAST FAST FAST FAST FAST FAST FAST")
        val packet = SignalPacketCodec.encode(
            wire.bytes,
            SignalPacketCodec.Robustness.FAST,
            SignalAcousticPayloadCodec.recommendedShardBytes(wire.wireBytes),
            "0123456789abcdef"
        )
        val bits = FskPhysicalCodec.encodeFrame(packet.frames.first())
        val decoder = FskClockRecoveryDecoder(40)
        val recovered = mutableListOf<String>()
        bits.forEach { bit -> repeat(4) { decoder.feed(bit).forEach(recovered::add) } }
        requireSmoke(packet.frames.first() in recovered, "A2 FSK 40 ms frame round-trip")
        requireSmoke(decoder.telemetry().payloadDecodeFailures == 0, "A2 FSK payload decode telemetry")
    }

    // v0.4 segmented QR transport: objects beyond one MMS/1 matrix reconstruct and retain SHA-256.
    run {
        val fileBytes = ByteArray(128 * 1024) { ((it * 17 + 3) and 0xff).toByte() }
        val envelope = SignalContentEnvelope.encodeFile(fileBytes, "segmented.bin", "application/octet-stream")
        val transfer = SignalQrTransferCodec.encode(envelope, SignalPacketCodec.Robustness.FAST, 384, "QRSEGMENT001")
        requireSmoke(transfer.segmentCount > 1, "QR segmented transfer uses multiple MMS messages")
        val sufficientFrames = transfer.packets.flatMap { packet -> packet.frames.take(packet.dataShardCount) }
        val state = SignalQrTransferCodec.decode(sufficientFrames)
        requireSmoke(state.complete, "QR segmented transfer reconstructs")
        requireSmoke(state.transferSha256Verified, "QR transfer envelope SHA-256")
        val decoded = SignalContentEnvelope.decode(state.contentEnvelope!!)!!
        requireSmoke(decoded.checksumVerified, "QR segmented object SHA-256")
        requireSmoke(decoded.payload.contentEquals(fileBytes), "QR segmented file bytes")

        val maxBytes = ByteArray(SignalContentEnvelope.MAX_FILE_BYTES) { (it and 0xff).toByte() }
        val maxEnvelope = SignalContentEnvelope.encodeFile(maxBytes, "one-megabyte.bin", "application/octet-stream")
        val maxTransfer = SignalQrTransferCodec.encode(maxEnvelope, SignalPacketCodec.Robustness.FAST, 384, "QRMAXIMUM001")
        requireSmoke(maxTransfer.segmentCount > transfer.segmentCount, "QR 1 MiB transfer is segmented")
        requireSmoke(maxTransfer.frames.isNotEmpty(), "QR 1 MiB transfer produces frames")
    }

    // v0.4.5 shared discrete catalog invariants: TX/RX select the same named
    // physical geometry, and the Morse sound ceiling remains 30 WPM.
    run {
        requireSmoke(SignalFskProfiles.audible.map { it.id } == listOf("A", "B", "C", "D"), "Audible FSK profiles A-D")
        requireSmoke(SignalFskProfiles.highBand.map { it.id } == listOf("A", "B", "C", "D"), "High-band FSK profiles A-D")
        requireSmoke(SignalPresetCatalog.morseAudioWpm.maxOrNull() == 30, "Morse audio shared 30 WPM ceiling")
        requireSmoke(SignalPresetCatalog.surfaceProfiles.map { it.id }.distinct().size == 4, "Surface matched profiles A-D")
    }

    println("Signals codec smoke PASS: $assertions assertions")
}
