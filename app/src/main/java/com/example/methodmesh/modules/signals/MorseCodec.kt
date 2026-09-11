package com.example.methodmesh.modules.signals

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sqrt

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

    fun update(isOn: Boolean, timestampMs: Long, markHint: Char? = null, markHintConfidence: Double = 0.0): Snapshot {
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
                else -> acceptDataMark(duration, markHint, markHintConfidence)
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

    private fun acceptDataMark(duration: Long, markHint: Char?, markHintConfidence: Double) {
        val symbol = fusedDataMark(duration, markHint, markHintConfidence) ?: return
        currentSymbols.append(symbol)
        pulseDurations += duration
        dotEstimates += if (symbol == '-') (duration / 3L).coerceAtLeast(1L) else duration
        pulses++
        if (autoTiming) updateDotEstimate()
    }

    /**
     * Fuse the ordinary duration evidence with an optional independent dot/dash hint.
     * Colour assist is deliberately soft: weak/ambiguous chroma collapses to ordinary
     * timing-only Morse, while strong colour can rescue a smeared mark near the timing boundary.
     */
    private fun fusedDataMark(duration: Long, markHint: Char?, markHintConfidence: Double): Char? {
        val timingOnly = MorseCodec.decodeMark(duration, dotMs)
        val hint = markHint?.takeIf { it == '.' || it == '-' } ?: return timingOnly
        val confidence = markHintConfidence.coerceIn(0.0, 1.0)
        if (confidence < 0.12) return timingOnly

        val units = duration.toDouble() / dotMs.coerceAtLeast(1L).toDouble()
        // Broad likelihoods acknowledge camera smear and rolling-shutter edge delay.
        val timingDot = exp(-0.5 * ((units - 1.0) / 0.72) * ((units - 1.0) / 0.72)).coerceAtLeast(1e-6)
        val timingDash = exp(-0.5 * ((units - 3.0) / 1.05) * ((units - 3.0) / 1.05)).coerceAtLeast(1e-6)
        val preferred = 0.5 + 0.49 * confidence
        val other = 1.0 - preferred
        val colourDot = if (hint == '.') preferred else other
        val colourDash = if (hint == '-') preferred else other
        val dotScore = timingDot * colourDot
        val dashScore = timingDash * colourDash

        // If timing strongly rejects both hypotheses this is probably a framing marker/noise.
        if (max(timingDot, timingDash) < 0.01 && confidence < 0.75) return timingOnly
        return if (dotScore >= dashScore) '.' else '-'
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

/**
 * Camera colour calibration for colour-assisted screen Morse. White dots and red dashes
 * provide evidence orthogonal to duration. The known acquisition dots calibrate white and
 * the long START marker calibrates red before payload decoding.
 */
class MorseColourAssistCalibrator {
    companion object {
        /**
         * Activity metric used by the camera edge detector when colour assist is enabled.
         * Saturated red has much lower Y than white, so positive V/Cr energy is added back
         * to prevent a red dash being mistaken for OFF simply because its luminance is lower.
         */
        fun activityLevel(luma: Double, chromaV: Double?): Double {
            val redChroma = ((chromaV ?: 128.0) - 128.0).coerceAtLeast(0.0)
            return luma + redChroma * 0.85
        }
    }

    data class Evidence(
        val hint: Char?,
        val confidence: Double,
        val whiteDistance: Double,
        val redDistance: Double
    )

    private var whiteU = 128.0
    private var whiteV = 128.0
    private var redU = 96.0
    private var redV = 208.0
    private var whiteSamples = 0
    private var redSamples = 0

    fun reset() {
        whiteU = 128.0; whiteV = 128.0
        redU = 96.0; redV = 208.0
        whiteSamples = 0; redSamples = 0
    }

    fun observeWhite(u: Double, v: Double) {
        if (!u.isFinite() || !v.isFinite()) return
        val alpha = if (whiteSamples == 0) 1.0 else 0.30
        whiteU += (u - whiteU) * alpha
        whiteV += (v - whiteV) * alpha
        whiteSamples++
    }

    fun observeRed(u: Double, v: Double) {
        if (!u.isFinite() || !v.isFinite()) return
        val alpha = if (redSamples == 0) 1.0 else 0.30
        redU += (u - redU) * alpha
        redV += (v - redV) * alpha
        redSamples++
    }

    fun classify(u: Double, v: Double): Evidence {
        if (!u.isFinite() || !v.isFinite()) return Evidence(null, 0.0, Double.NaN, Double.NaN)
        val dw = distance(u, v, whiteU, whiteV)
        val dr = distance(u, v, redU, redV)
        val calibrationSeparation = distance(whiteU, whiteV, redU, redV)
        if (calibrationSeparation < 14.0) return Evidence(null, 0.0, dw, dr)
        val total = (dw + dr).coerceAtLeast(1.0)
        val relative = (abs(dw - dr) / total).coerceIn(0.0, 1.0)
        val nearest = minOf(dw, dr)
        val locality = (1.0 - nearest / (calibrationSeparation * 1.35).coerceAtLeast(20.0)).coerceIn(0.0, 1.0)
        val confidence = (relative * 0.65 + locality * 0.35).coerceIn(0.0, 1.0)
        if (confidence < 0.16) return Evidence(null, 0.0, dw, dr)
        return Evidence(if (dw <= dr) '.' else '-', confidence, dw, dr)
    }

    fun calibrationLabel(): String = when {
        whiteSamples > 0 && redSamples > 0 -> "white+red calibrated"
        whiteSamples > 0 -> "white calibrated • waiting for START red"
        else -> "generic colour prior • waiting for acquisition"
    }

    private fun distance(u: Double, v: Double, cu: Double, cv: Double): Double {
        val du = u - cu
        val dv = v - cv
        return sqrt(du * du + dv * dv)
    }
}

class MorseColourMarkTracker(private val calibrator: MorseColourAssistCalibrator) {
    data class MarkResult(
        val hint: Char?,
        val confidence: Double,
        val averageU: Double,
        val averageV: Double,
        val calibrationEvent: String? = null
    )

    private var markStartedMs: Long? = null
    private var sumU = 0.0
    private var sumV = 0.0
    private var samples = 0

    fun reset() {
        markStartedMs = null
        sumU = 0.0; sumV = 0.0; samples = 0
        calibrator.reset()
    }

    fun start(timestampMs: Long) {
        markStartedMs = timestampMs
        sumU = 0.0; sumV = 0.0; samples = 0
    }

    fun observe(u: Double?, v: Double?) {
        if (markStartedMs == null || u == null || v == null || !u.isFinite() || !v.isFinite()) return
        sumU += u; sumV += v; samples++
    }

    fun finish(timestampMs: Long, dotMs: Long, frameState: MorseTimingDecoder.FrameState): MarkResult? {
        val started = markStartedMs ?: return null
        markStartedMs = null
        if (samples <= 0) return null
        val u = sumU / samples.toDouble()
        val v = sumV / samples.toDouble()
        val duration = (timestampMs - started).coerceAtLeast(1L)
        sumU = 0.0; sumV = 0.0; samples = 0

        val units = duration.toDouble() / dotMs.coerceAtLeast(1L).toDouble()
        if (frameState == MorseTimingDecoder.FrameState.SEEKING_START && units in 0.35..2.0) {
            calibrator.observeWhite(u, v)
            return MarkResult('.', 0.0, u, v, "white acquisition calibrated")
        }
        if (units in 8.0..16.0) {
            calibrator.observeRed(u, v)
            return MarkResult(null, 0.0, u, v, "START red calibrated")
        }
        if (units in 16.0..30.0) {
            calibrator.observeRed(u, v)
            return MarkResult(null, 0.0, u, v, "END red refreshed")
        }
        val evidence = calibrator.classify(u, v)
        return MarkResult(evidence.hint, evidence.confidence, u, v)
    }
}

/**
 * Human-observed Morse decoder. The operator classifies each visible/audible mark as dot or dash;
 * timing between taps is used only as probabilistic evidence for element/letter/word spacing.
 * START SIGNAL anchors cycle zero and each later START closes the previous observation and opens the next.
 */
class MorseManualTapDecoder(
    initialDotMs: Long = 240L,
    private val minimumDotMs: Long = 40L,
    private val maximumDotMs: Long = 2000L
) {
    data class Snapshot(
        val decodedText: String,
        val currentSymbols: String,
        val dotMs: Long,
        val marksSeen: Int,
        val completedCopies: Int,
        val consensusConfidence: Double,
        val anchored: Boolean,
        val unanimousCharacters: Int,
        val characterCount: Int,
        val quality: String
    )

    private data class Tap(val symbol: Char, val timeMs: Long)
    private enum class TapStyle { ONSET, END_OF_MARK }
    private data class DecodedObservation(val text: String, val estimatedDotMs: Long, val style: TapStyle)

    private val startingDotMs = initialDotMs.coerceIn(minimumDotMs, maximumDotMs)
    private var dotMs = startingDotMs
    private var anchored = false
    private val taps = mutableListOf<Tap>()
    private val consensus = MorseRepeatConsensus()
    private var marksSeen = 0

    fun reset() {
        dotMs = startingDotMs
        anchored = false
        taps.clear()
        consensus.reset()
        marksSeen = 0
    }

    /**
     * START is a hard cycle anchor. The first press opens an observation; each later press
     * closes the preceding observation, contributes its re-decoded text to consensus, and
     * immediately opens the next one. No synthetic END or manual letter/word buttons exist.
     */
    fun startSignal(timestampMs: Long): Snapshot {
        if (anchored && taps.isNotEmpty()) {
            val observation = decodeObservation(taps)
            if (observation.text.isNotBlank()) {
                consensus.add(observation.text)
                // Completed observations are strong cadence evidence, but do not jump instantly.
                dotMs = observation.estimatedDotMs.coerceIn(minimumDotMs, maximumDotMs)
            }
        }
        anchored = true
        taps.clear()
        // Timestamp is intentionally only the cycle anchor; operator reaction latency means it is
        // not treated as a Morse mark/gap measurement.
        @Suppress("UNUSED_VARIABLE") val anchor = timestampMs
        return snapshot()
    }

    fun tapDot(timestampMs: Long): Snapshot = tap('.', timestampMs)
    fun tapDash(timestampMs: Long): Snapshot = tap('-', timestampMs)

    private fun tap(symbol: Char, timestampMs: Long): Snapshot {
        if (!anchored) return snapshot("Tap START SIGNAL when you see the MethodMesh start marker; pre-start taps are ignored.")
        if (taps.lastOrNull()?.let { timestampMs <= it.timeMs } == true) {
            return snapshot("Tap timing was non-monotonic; the duplicate/late tap was ignored.")
        }
        taps += Tap(symbol, timestampMs)
        marksSeen++
        return snapshot()
    }

    /**
     * Re-decode the complete raw tap sequence every time. This is crucial: the configured WPM is
     * only a weak prior, so an early bad cadence estimate must never permanently merge/split letters.
     * We fit both plausible human behaviours: tapping near mark onset, or tapping after recognising
     * the completed dot/dash. The better global timing fit wins.
     */
    private fun decodeObservation(raw: List<Tap>): DecodedObservation {
        if (raw.isEmpty()) return DecodedObservation("", dotMs, TapStyle.ONSET)
        if (raw.size == 1) return DecodedObservation(MorseCodec.decode(raw.first().symbol.toString()), dotMs, TapStyle.ONSET)

        val fits = TapStyle.entries.map { style -> fitCadence(raw, style) }
        val best = fits.minByOrNull { it.first } ?: Triple(Double.POSITIVE_INFINITY, dotMs.toDouble(), TapStyle.ONSET)
        val fittedDot = best.second.roundToLong().coerceIn(minimumDotMs, maximumDotMs)
        val style = best.third

        val notation = StringBuilder().append(raw.first().symbol)
        for (i in 1 until raw.size) {
            val previous = raw[i - 1]
            val current = raw[i]
            val interval = (current.timeMs - previous.timeMs).coerceAtLeast(1L).toDouble()
            val markUnits = when (style) {
                TapStyle.ONSET -> if (previous.symbol == '-') 3.0 else 1.0
                TapStyle.END_OF_MARK -> if (current.symbol == '-') 3.0 else 1.0
            }
            val gap = classifyGap(interval, fittedDot.toDouble(), markUnits)
            when (gap) {
                MorseCodec.GapKind.LETTER -> notation.append(' ')
                MorseCodec.GapKind.WORD -> notation.append(" / ")
                else -> Unit
            }
            notation.append(current.symbol)
        }
        return DecodedObservation(MorseCodec.decode(notation.toString()), fittedDot, style)
    }

    /** score, dot-ms, style */
    private fun fitCadence(raw: List<Tap>, style: TapStyle): Triple<Double, Double, TapStyle> {
        val candidates = mutableListOf<Double>()
        candidates += dotMs.toDouble()
        for (i in 1 until raw.size) {
            val previous = raw[i - 1]
            val current = raw[i]
            val interval = (current.timeMs - previous.timeMs).coerceAtLeast(1L).toDouble()
            val markUnits = when (style) {
                TapStyle.ONSET -> if (previous.symbol == '-') 3.0 else 1.0
                TapStyle.END_OF_MARK -> if (current.symbol == '-') 3.0 else 1.0
            }
            for (gapUnits in doubleArrayOf(1.0, 3.0, 7.0)) {
                val estimate = interval / (markUnits + gapUnits)
                if (estimate in minimumDotMs.toDouble()..maximumDotMs.toDouble()) candidates += estimate
            }
        }

        var bestDot = dotMs.toDouble()
        var bestScore = Double.POSITIVE_INFINITY
        for (candidate in candidates.distinct()) {
            var error = 0.0
            var count = 0
            for (i in 1 until raw.size) {
                val previous = raw[i - 1]
                val current = raw[i]
                val interval = (current.timeMs - previous.timeMs).coerceAtLeast(1L).toDouble()
                val markUnits = when (style) {
                    TapStyle.ONSET -> if (previous.symbol == '-') 3.0 else 1.0
                    TapStyle.END_OF_MARK -> if (current.symbol == '-') 3.0 else 1.0
                }
                val residual = doubleArrayOf(1.0, 3.0, 7.0).minOf { gapUnits ->
                    val totalUnits = markUnits + gapUnits
                    kotlin.math.abs(interval - candidate * totalUnits) / (candidate * totalUnits)
                }
                // Huber-ish loss: outlier reaction delays should not dominate the cadence fit.
                error += if (residual <= 0.20) residual * residual else 0.04 + (residual - 0.20) * 0.20
                count++
            }
            val timingScore = error / count.coerceAtLeast(1)
            // WPM is deliberately a weak prior: enough to break exact aliases, never enough to
            // defeat a coherent observed cadence.
            val prior = 0.0025 * kotlin.math.abs(kotlin.math.ln(candidate / dotMs.coerceAtLeast(1L).toDouble()))
            val score = timingScore + prior
            if (score < bestScore) {
                bestScore = score
                bestDot = candidate
            }
        }
        return Triple(bestScore, bestDot, style)
    }

    private fun classifyGap(intervalMs: Double, fittedDotMs: Double, markUnits: Double): MorseCodec.GapKind {
        data class Candidate(val kind: MorseCodec.GapKind, val error: Double)
        val options = listOf(
            1.0 to MorseCodec.GapKind.ELEMENT,
            3.0 to MorseCodec.GapKind.LETTER,
            7.0 to MorseCodec.GapKind.WORD
        )
        return options.map { (gapUnits, kind) ->
            val expected = fittedDotMs * (markUnits + gapUnits)
            Candidate(kind, kotlin.math.abs(intervalMs - expected) / expected.coerceAtLeast(1.0))
        }.minByOrNull { it.error }?.kind ?: MorseCodec.GapKind.ELEMENT
    }

    fun snapshot(message: String? = null): Snapshot {
        val voted = consensus.result()
        val provisionalObservation = if (anchored && taps.isNotEmpty()) decodeObservation(taps) else null
        val provisional = if (voted.copies == 0) provisionalObservation?.text.orEmpty() else ""
        val observedDot = provisionalObservation?.estimatedDotMs ?: dotMs
        val quality = message ?: when {
            !anchored -> "Waiting for START SIGNAL; manual dots/dashes are quarantined until the cycle is anchored."
            voted.copies >= 2 -> "Best guess from ${voted.copies} START-bounded observations • ${(voted.confidence * 100).toInt()}% confidence"
            voted.copies == 1 -> "1 START-bounded observation • ${(voted.confidence * 100).toInt()}% confidence • tap START at the next cycle"
            else -> "START anchored • tap each observed dot/dash; the next START closes this observation"
        }
        return Snapshot(
            decodedText = voted.text.ifBlank { provisional },
            currentSymbols = taps.joinToString("") { it.symbol.toString() },
            dotMs = observedDot,
            marksSeen = marksSeen,
            completedCopies = voted.copies,
            consensusConfidence = voted.confidence,
            anchored = anchored,
            unanimousCharacters = voted.unanimousCharacters,
            characterCount = voted.characterCount,
            quality = quality
        )
    }
}
