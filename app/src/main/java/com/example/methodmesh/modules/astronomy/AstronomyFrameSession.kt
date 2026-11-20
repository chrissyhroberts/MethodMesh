package com.example.methodmesh.modules.astronomy

/** One physical camera/sample generation. Old analyzers can never feed a new generation. */
internal class AstronomyFrameSession(
    private val averagingFrames: Int = 30,
    private val onUpdated: (AstronomyFrameSession, Readout) -> Unit = { _, _ -> }
) {
    data class Readout(val current: Double?, val median: Double?, val best: Double?, val detections: Int)
    data class Snapshot(val summary: StarTestSummary, val readout: Readout)
    private val lock = Any()
    private var active = true
    private val frames = ArrayDeque<StarFrameMetric>()
    private val rolling = ArrayDeque<Double>()
    private var readout = Readout(null, null, null, 0)
    val analyzer = StarFrameAnalyzer(onMetric = ::accept)
    val isActive: Boolean get() = synchronized(lock) { active }

    fun accept(metric: StarFrameMetric) {
        val updated = synchronized(lock) {
            if (!active) return
            frames.addLast(metric)
            if (frames.size > 600) frames.removeFirst()
            val changed = metric.detected && metric.fwhmPx.isFinite()
            if (changed) {
                rolling.addLast(metric.fwhmPx)
                while (rolling.size > averagingFrames.coerceAtLeast(1)) rolling.removeFirst()
                val sorted = rolling.sorted()
                val middle = sorted.size / 2
                val median = if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2.0
                readout = Readout(metric.fwhmPx, median, readout.best?.let { minOf(it, median) } ?: median,
                    readout.detections + 1)
            }
            if (changed) readout else null
        }
        if (updated != null) onUpdated(this, updated)
    }

    fun close() { synchronized(lock) { active = false } }
    fun readout(): Readout = synchronized(lock) { readout }
    fun freeze(minimumFrames: Int): Snapshot = synchronized(lock) {
        active = false
        Snapshot(StarFrameAnalyzer.summarize(frames.toList(), minimumFrames), readout)
    }
}

/** Main-thread camera ownership: delayed provider callbacks may bind only while this lease is alive. */
internal class AstronomyCameraLease {
    var active = true
        private set
    private var release: (() -> Unit)? = null
    fun bind(acquire: () -> (() -> Unit)) {
        if (!active) return
        check(release == null)
        release = acquire()
    }
    fun close() {
        if (!active) return
        active = false
        release?.invoke()
        release = null
    }
}
