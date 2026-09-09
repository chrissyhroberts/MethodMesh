package com.example.methodmesh.modules.signals

import kotlin.math.max

object MorseCodec {
    private val encodeMap: Map<Char, String> = mapOf(
        'A' to ".-", 'B' to "-...", 'C' to "-.-.", 'D' to "-..", 'E' to ".",
        'F' to "..-.", 'G' to "--.", 'H' to "....", 'I' to "..", 'J' to ".---",
        'K' to "-.-", 'L' to ".-..", 'M' to "--", 'N' to "-.", 'O' to "---",
        'P' to ".--.", 'Q' to "--.-", 'R' to ".-.", 'S' to "...", 'T' to "-",
        'U' to "..-", 'V' to "...-", 'W' to ".--", 'X' to "-..-", 'Y' to "-.--",
        'Z' to "--..",
        '0' to "-----", '1' to ".----", '2' to "..---", '3' to "...--", '4' to "....-",
        '5' to ".....", '6' to "-....", '7' to "--...", '8' to "---..", '9' to "----.",
        '.' to ".-.-.-", ',' to "--..--", '?' to "..--..", '/' to "-..-.",
        '=' to "-...-", '+' to ".-.-.", '-' to "-....-", '(' to "-.--.", ')' to "-.--.-",
        ':' to "---...", ';' to "-.-.-.", '@' to ".--.-."
    )
    private val decodeMap = encodeMap.entries.associate { (k, v) -> v to k }

    enum class MarkKind { DATA, PREAMBLE, START, END }

    sealed interface Element {
        data class Mark(
            val symbol: Char,
            val units: Int,
            val sourceChar: Char,
            val kind: MarkKind = MarkKind.DATA
        ) : Element
        data class Gap(val units: Int, val kind: GapKind) : Element
    }

    enum class GapKind { ELEMENT, LETTER, WORD, LOOP }

    fun encode(text: String): String = text
        .uppercase()
        .trim()
        .split(Regex("\\s+"))
        .joinToString(" / ") { word ->
            word.mapNotNull { encodeMap[it] }.joinToString(" ")
        }

    fun decode(notation: String): String = notation
        .trim()
        .split(Regex("\\s*/\\s*"))
        .joinToString(" ") { word ->
            word.trim().split(Regex("\\s+")).mapNotNull { decodeMap[it] }.joinToString("")
        }

    /**
     * Build one cyclic MethodMesh Morse frame.
     *
     * Every cycle is deliberately self-framing:
     *   acquisition dots -> START -> payload -> END -> loop silence.
     *
     * START and END are long, non-Morse marks with different durations. The short
     * acquisition dots give camera exposure/ROI and auto-timing a chance to settle
     * before START arrives. A receiver joining mid-cycle may decode an orphan suffix,
     * but it is quarantined until a later START/END pair establishes message geometry.
     */
    fun timeline(text: String, loopGapUnits: Int = 10, includeStartSignal: Boolean = true): List<Element> {
        val words = text.uppercase().trim().split(Regex("\\s+")).filter(String::isNotBlank)
        val out = mutableListOf<Element>()
        if (words.isEmpty()) return out

        if (includeStartSignal) {
            repeat(3) { index ->
                out += Element.Mark('~', 1, '~', MarkKind.PREAMBLE)
                out += Element.Gap(if (index == 2) 3 else 2, GapKind.LETTER)
            }
            out += Element.Mark('^', 12, '^', MarkKind.START)
            out += Element.Gap(4, GapKind.LETTER)
        }

        words.forEachIndexed { wordIndex, word ->
            val supported = word.filter { encodeMap.containsKey(it) }
            supported.forEachIndexed { charIndex, char ->
                val code = encodeMap.getValue(char)
                code.forEachIndexed { markIndex, mark ->
                    out += Element.Mark(mark, if (mark == '.') 1 else 3, char, MarkKind.DATA)
                    if (markIndex < code.lastIndex) out += Element.Gap(1, GapKind.ELEMENT)
                }
                if (charIndex < supported.lastIndex) out += Element.Gap(3, GapKind.LETTER)
            }
            if (wordIndex < words.lastIndex) out += Element.Gap(7, GapKind.WORD)
        }

        if (includeStartSignal) {
            // Guard silence flushes the last payload character before the END mark.
            out += Element.Gap(4, GapKind.LETTER)
            out += Element.Mark('$', 20, '$', MarkKind.END)
        }
        if (loopGapUnits > 0) out += Element.Gap(loopGapUnits, GapKind.LOOP)
        return out
    }

    fun dotDurationMs(wpm: Int): Long = (1200.0 / max(1, wpm)).toLong().coerceAtLeast(20L)

    fun decodeMark(durationMs: Long, dotMs: Long): Char? = when {
        durationMs < dotMs * 0.35 -> null
        durationMs < dotMs * 2.0 -> '.'
        durationMs < dotMs * 5.0 -> '-'
        else -> null
    }

    fun gapKind(durationMs: Long, dotMs: Long): GapKind = when {
        durationMs >= dotMs * 6 -> GapKind.WORD
        durationMs >= dotMs * 2 -> GapKind.LETTER
        else -> GapKind.ELEMENT
    }

    fun decodeSymbols(symbols: List<String>): String = symbols.mapNotNull { decodeMap[it] }.joinToString("")
}

/**
 * Consensus across independently framed repeats of the same message.
 *
 * Complete START->END copies carry full weight. A suffix heard before the first START
 * can only contribute after a complete frame establishes the right-hand boundary; it
 * is then right-aligned at reduced weight. This prevents an orphan from sliding freely
 * through the message and manufacturing confidence.
 */
class MorseRepeatConsensus {
    data class Result(
        val text: String,
        val copies: Int,
        val confidence: Double,
        val unanimousCharacters: Int = 0,
        val characterCount: Int = 0,
        val orphanObservations: Int = 0
    )

    private data class Partial(val text: String, val weight: Double)
    private val copies = mutableListOf<String>()
    private val rightAnchoredPartials = mutableListOf<Partial>()

    fun reset() {
        copies.clear()
        rightAnchoredPartials.clear()
    }

    fun add(text: String): Result {
        val normalized = normalize(text)
        if (normalized.isNotBlank()) copies += normalized
        return result()
    }

    fun addRightAnchored(text: String, weight: Double = 0.35): Result {
        val normalized = normalize(text)
        if (normalized.isNotBlank() && copies.isNotEmpty()) {
            rightAnchoredPartials += Partial(normalized, weight.coerceIn(0.05, 0.75))
        }
        return result()
    }

    fun preview(current: String): Result {
        val normalized = normalize(current)
        if (normalized.isBlank() || copies.isNotEmpty()) return result()
        // An anchored-but-incomplete first frame may be shown provisionally, but it
        // deliberately carries zero message confidence until END is observed.
        return Result(normalized, 0, 0.0, 0, normalized.length, rightAnchoredPartials.size)
    }

    fun result(): Result {
        if (copies.isEmpty()) return Result("", 0, 0.0, 0, 0, rightAnchoredPartials.size)
        val reference = copies.maxWithOrNull(compareBy<String> { it.length }.thenBy { it }) ?: return Result("", 0, 0.0)
        val votes = Array(reference.length) { linkedMapOf<Char, Double>() }
        val totals = DoubleArray(reference.length)
        val fullCoverage = IntArray(reference.length)

        copies.forEach { text ->
            val aligned = alignToReference(reference, text)
            aligned.forEachIndexed { index, char ->
                if (char != null) {
                    votes[index][char] = (votes[index][char] ?: 0.0) + 1.0
                    totals[index] += 1.0
                    fullCoverage[index] += 1
                }
            }
        }

        rightAnchoredPartials.forEach { partial ->
            val chars = partial.text.toCharArray()
            val start = (reference.length - chars.size).coerceAtLeast(0)
            val offsetInPartial = (chars.size - reference.length).coerceAtLeast(0)
            for (i in offsetInPartial until chars.size) {
                val index = start + (i - offsetInPartial)
                if (index in votes.indices) {
                    val char = chars[i]
                    votes[index][char] = (votes[index][char] ?: 0.0) + partial.weight
                    totals[index] += partial.weight
                }
            }
        }

        val out = StringBuilder()
        var agreementSum = 0.0
        var agreementPositions = 0
        var unanimous = 0
        for (index in votes.indices) {
            val positionVotes = votes[index]
            if (positionVotes.isEmpty()) continue
            val winner = positionVotes.entries.maxWithOrNull(compareBy<Map.Entry<Char, Double>> { it.value }.thenBy { it.key }) ?: continue
            out.append(winner.key)
            val total = totals[index].coerceAtLeast(0.0001)
            val agreement = winner.value / total
            agreementSum += agreement
            agreementPositions++
            if (fullCoverage[index] == copies.size && positionVotes.size == 1) unanimous++
        }

        val agreement = if (agreementPositions == 0) 0.0 else agreementSum / agreementPositions
        // Evidence shrinkage prevents a single apparently perfect copy from claiming
        // 100%. One full cycle ~=75%, three agreeing cycles ~=90%, six ~=95%.
        val evidence = copies.size.toDouble() / (copies.size.toDouble() + 0.33)
        val confidence = (agreement * evidence).coerceIn(0.0, 0.999)
        val text = out.toString().trim()
        return Result(text, copies.size, confidence, unanimous, text.length, rightAnchoredPartials.size)
    }

    private fun normalize(text: String): String = text.trim().replace(Regex("\\s+"), " ")

    /** Global edit alignment; insertions in an observation are ignored rather than shifting the tail. */
    private fun alignToReference(reference: String, observed: String): Array<Char?> {
        val n = reference.length
        val m = observed.length
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val substitution = dp[i - 1][j - 1] + if (reference[i - 1] == observed[j - 1]) 0 else 1
                val deletion = dp[i - 1][j] + 1
                val insertion = dp[i][j - 1] + 1
                dp[i][j] = minOf(substitution, deletion, insertion)
            }
        }
        val aligned = arrayOfNulls<Char>(n)
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && dp[i][j] == dp[i - 1][j - 1] + if (reference[i - 1] == observed[j - 1]) 0 else 1 -> {
                    aligned[i - 1] = observed[j - 1]
                    i--; j--
                }
                i > 0 && dp[i][j] == dp[i - 1][j] + 1 -> i--
                j > 0 -> j--
                else -> break
            }
        }
        return aligned
    }
}

/** Debounce a boolean physical detector so camera transition frames do not become Morse edges. */
class MorseStableSignalGate(private val requiredStableSamples: Int = 2) {
    private var candidate: Boolean? = null
    private var candidateCount = 0
    private var emitted: Boolean? = null

    fun reset() {
        candidate = null
        candidateCount = 0
        emitted = null
    }

    /** Returns a new stable state only when it should be emitted to the timing decoder. */
    fun feed(value: Boolean): Boolean? {
        if (candidate == value) candidateCount++ else {
            candidate = value
            candidateCount = 1
        }
        if (candidateCount < requiredStableSamples.coerceAtLeast(1)) return null
        if (emitted == value) return null
        emitted = value
        return value
    }
}

/** Stateful transition-based decoder shared by camera-luma, microphone and legacy lux receivers. */
class MorseTimingDecoder(
    initialDotMs: Long = 120L,
    private val autoTiming: Boolean = true,
    private val minimumDotMs: Long = 20L,
    private val maximumDotMs: Long = 2000L,
    private val elementGapUnits: Double = 1.0,
    private val letterGapUnits: Double = 3.0,
    private val wordGapUnits: Double = 7.0
) {
    enum class FrameState { SEEKING_START, IN_FRAME, HAVE_COMPLETE_FRAME }

    data class Snapshot(
        val decodedText: String,
        val currentSymbols: String,
        val dotMs: Long,
        val pulsesSeen: Int,
        val quality: String,
        val completedCopies: Int = 0,
        val consensusConfidence: Double = 0.0,
        val framed: Boolean = false,
        val frameState: FrameState = FrameState.SEEKING_START,
        val orphanObservations: Int = 0,
        val unanimousCharacters: Int = 0,
        val characterCount: Int = 0
    )

    private val minDot = minimumDotMs.coerceAtLeast(20L)
    private val maxDot = maximumDotMs.coerceAtLeast(minDot)
    private var dotMs = initialDotMs.coerceIn(minDot, maxDot)
    private var currentState = false
    private var lastTransitionMs: Long? = null
    private val pulseDurations = mutableListOf<Long>()
    private val dotEstimates = mutableListOf<Long>()
    private val currentSymbols = StringBuilder()
    private val currentMessage = StringBuilder()
    private var pulses = 0
    private val consensus = MorseRepeatConsensus()
    private var frameState = FrameState.SEEKING_START
    private var orphanEndingAtEnd: String? = null
    private var orphanConsumed = false

    fun reset(nowMs: Long? = null) {
        currentState = false
        lastTransitionMs = nowMs
        pulseDurations.clear()
        dotEstimates.clear()
        currentSymbols.clear()
        currentMessage.clear()
        consensus.reset()
        pulses = 0
        frameState = FrameState.SEEKING_START
        orphanEndingAtEnd = null
        orphanConsumed = false
    }

    fun update(isOn: Boolean, timestampMs: Long): Snapshot {
        val last = lastTransitionMs
        if (last == null) {
            currentState = isOn
            lastTransitionMs = timestampMs
            return snapshot()
        }
        if (isOn == currentState) return snapshot()

        val duration = (timestampMs - last).coerceAtLeast(1L)
        if (currentState) {
            when {
                isEndMarker(duration) -> endMarker(duration)
                isStartMarker(duration) -> startMarker(duration)
                else -> acceptDataMark(duration)
            }
        } else {
            acceptGap(duration)
        }
        currentState = isOn
        lastTransitionMs = timestampMs
        return snapshot()
    }

    fun flush(timestampMs: Long): Snapshot {
        val last = lastTransitionMs
        if (last != null && !currentState) {
            val duration = (timestampMs - last).coerceAtLeast(1L)
            if (duration >= dotMs * 2) flushLetter()
        }
        // No END means no complete cycle. Never manufacture a vote at Stop/flush.
        return snapshot()
    }

    private fun isStartMarker(duration: Long): Boolean = duration >= dotMs * 8 && duration < dotMs * 16
    private fun isEndMarker(duration: Long): Boolean = duration >= dotMs * 16 && duration <= dotMs * 30

    private fun startMarker(duration: Long) {
        // Anything since an unanchored END or during acquisition is not allowed to
        // become a copy. START establishes position zero and clears that residue.
        currentSymbols.clear()
        currentMessage.clear()
        frameState = FrameState.IN_FRAME
        if (autoTiming) {
            val estimate = (duration / 12L).coerceIn(minDot, maxDot)
            dotMs = ((dotMs * 2 + estimate) / 3).coerceIn(minDot, maxDot)
            dotEstimates += estimate
        }
    }

    private fun endMarker(duration: Long) {
        flushLetter()
        val text = currentMessage.toString().trim()
        if (frameState == FrameState.IN_FRAME) {
            if (text.isNotBlank()) {
                consensus.add(text)
                if (!orphanConsumed) {
                    orphanEndingAtEnd?.takeIf { it.isNotBlank() }?.let { consensus.addRightAnchored(it) }
                    orphanConsumed = true
                }
            }
            currentMessage.clear()
            currentSymbols.clear()
            frameState = FrameState.HAVE_COMPLETE_FRAME
            if (autoTiming) {
                val estimate = (duration / 20L).coerceIn(minDot, maxDot)
                dotMs = ((dotMs * 3 + estimate) / 4).coerceIn(minDot, maxDot)
                dotEstimates += estimate
            }
        } else {
            // Joined midway through a previous cycle: retain only an END-anchored
            // suffix. It is not voted until a later complete START->END frame exists.
            if (text.isNotBlank()) orphanEndingAtEnd = text
            currentMessage.clear()
            currentSymbols.clear()
        }
    }

    private fun acceptDataMark(duration: Long) {
        MorseCodec.decodeMark(duration, dotMs)?.let { symbol ->
            currentSymbols.append(symbol)
            pulseDurations += duration
            dotEstimates += if (symbol == '-') (duration / 3L).coerceAtLeast(1L) else duration
            pulses++
            if (autoTiming) updateDotEstimate()
        }
    }

    private fun acceptGap(duration: Long) {
        val units = duration.toDouble() / dotMs.coerceAtLeast(1L).toDouble()
        val letterThreshold = (elementGapUnits + letterGapUnits) / 2.0
        val wordThreshold = (letterGapUnits + wordGapUnits) / 2.0
        when {
            units >= wordThreshold -> {
                flushLetter()
                if (currentMessage.isNotEmpty() && currentMessage.last() != ' ') currentMessage.append(' ')
            }
            units >= letterThreshold -> flushLetter()
            else -> Unit
        }
    }

    private fun flushLetter() {
        if (currentSymbols.isEmpty()) return
        val char = MorseCodec.decode(currentSymbols.toString())
        if (char.isNotBlank()) currentMessage.append(char)
        currentSymbols.clear()
    }

    private fun updateDotEstimate() {
        if (dotEstimates.size < 3) return
        val recent = dotEstimates.takeLast(24).sorted()
        val median = recent[recent.size / 2]
        if (median in minDot..maxDot) {
            dotMs = if (frameState == FrameState.SEEKING_START) median.coerceIn(minDot, maxDot)
            else ((dotMs * 3 + median) / 4).coerceIn(minDot, maxDot)
        }
    }

    private fun snapshot(): Snapshot {
        val completed = consensus.result()
        val provisional = if (completed.copies == 0 && frameState == FrameState.IN_FRAME) currentMessage.toString().trim() else ""
        val decoded = completed.text.ifBlank { provisional }
        val quality = when {
            frameState == FrameState.IN_FRAME && completed.copies == 0 -> "START locked • first bounded copy in progress"
            frameState == FrameState.IN_FRAME -> "START locked • cycle ${completed.copies + 1} in progress"
            completed.copies >= 2 -> "Best guess from ${completed.copies} complete cycles • ${(completed.confidence * 100).toInt()}% message confidence"
            completed.copies == 1 -> "1 complete START→END cycle • ${(completed.confidence * 100).toInt()}% message confidence • waiting for repeat"
            orphanEndingAtEnd != null -> "Orphan suffix buffered at END • waiting for START before it can contribute"
            pulses < 3 -> "Waiting for START • partial signal is quarantined"
            else -> "Signal seen • waiting for START boundary"
        }
        return Snapshot(
            decodedText = decoded,
            currentSymbols = currentSymbols.toString(),
            dotMs = dotMs,
            pulsesSeen = pulses,
            quality = quality,
            completedCopies = completed.copies,
            consensusConfidence = completed.confidence,
            framed = frameState != FrameState.SEEKING_START,
            frameState = frameState,
            orphanObservations = if (orphanEndingAtEnd != null) 1 else 0,
            unanimousCharacters = completed.unanimousCharacters,
            characterCount = completed.characterCount
        )
    }
}
