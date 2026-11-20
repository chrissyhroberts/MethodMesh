package com.example.methodmesh.modules.astronomy

import org.junit.Assert.*
import org.junit.Test

class AstronomyFrameSessionTest {
    private fun metric(width: Double, detected: Boolean = true) =
        StarFrameMetric(1L, 10.0, 10.0, width, 12.0, 150.0, 100.0, detected)

    @Test fun rollingMedianAndBestUseTheExistingOddAndEvenWindowRules() {
        val sample = AstronomyFrameSession(3)
        sample.accept(metric(4.0)); assertEquals(4.0, sample.readout().median!!, 0.0)
        sample.accept(metric(2.0)); assertEquals(3.0, sample.readout().median!!, 0.0)
        sample.accept(metric(9.0)); assertEquals(4.0, sample.readout().median!!, 0.0)
        sample.accept(metric(1.0)); assertEquals(2.0, sample.readout().median!!, 0.0)
        sample.accept(metric(10.0)); assertEquals(9.0, sample.readout().median!!, 0.0)
        assertEquals(2.0, sample.readout().best!!, 0.0)
    }

    @Test fun freezeCapturesOneCoherentSummaryAndRejectsLaterFrames() {
        val sample = AstronomyFrameSession(3)
        repeat(20) { sample.accept(metric(2.0)) }
        val capture = sample.freeze(10)
        sample.accept(metric(100.0))
        assertFalse(sample.isActive)
        assertEquals(20, capture.summary.frameCount)
        assertEquals(20, capture.summary.detectionCount)
        assertEquals(2.0, capture.summary.medianFwhmPx, 0.0)
        assertEquals(capture, sample.freeze(10))
    }

    @Test fun aRetiredCameraSessionCannotContaminateTheReplacement() {
        val old = AstronomyFrameSession(5)
        old.accept(metric(1.0)); old.close()
        val fresh = AstronomyFrameSession(5)
        old.accept(metric(0.1)); fresh.accept(metric(7.0))
        assertEquals(7.0, fresh.readout().best!!, 0.0)
        assertEquals(1, fresh.freeze(1).summary.frameCount)
        assertEquals(1, old.freeze(1).summary.frameCount)
    }

    @Test fun queuedUiWorkCanRejectItsRetiredOwnerAfterReset() {
        val posted = mutableListOf<() -> Unit>(); var applied = 0
        val sample = AstronomyFrameSession(5) { owner, _ -> posted += { if (owner.isActive) applied++ } }
        sample.accept(metric(2.0)); sample.close(); posted.forEach { it() }
        assertEquals(0, applied)
    }

    @Test fun invalidDetectionsDoNotPoisonTheRollingReadoutOrBest() {
        var updates = 0
        val sample = AstronomyFrameSession(5) { _, _ -> updates++ }
        sample.accept(metric(2.0)); sample.accept(metric(Double.NaN)); sample.accept(metric(1.0, false))
        assertEquals(2.0, sample.readout().median!!, 0.0)
        assertEquals(1, sample.readout().detections)
        assertEquals(1, updates)
        assertEquals(1, sample.freeze(1).summary.detectionCount)
    }

    @Test fun workingFramesRemainBoundedWithoutChangingTheSummaryFormula() {
        val sample = AstronomyFrameSession(5)
        repeat(700) { sample.accept(metric(3.0)) }
        val capture = sample.freeze(10)
        assertEquals(600, capture.summary.frameCount)
        assertEquals(3.0, capture.summary.medianFwhmPx, 0.0)
        assertEquals(0.0, capture.summary.scintillationCv, 0.0)
    }

    @Test fun emptyAndShortSamplesKeepTheScientificWarningBehavior() {
        assertEquals("No stable bright point source detected.", AstronomyFrameSession().freeze(10).summary.qualityWarning)
        val sample = AstronomyFrameSession()
        sample.accept(metric(3.0))
        assertEquals("Short sample: collect at least 10 frames.", sample.freeze(10).summary.qualityWarning)
    }

    @Test fun closingPendingCameraLeasePreventsALateBind() {
        val lease = AstronomyCameraLease(); var bound = 0
        lease.close(); lease.bind { bound++; {} }
        assertEquals(0, bound)
    }

    @Test fun cameraCleanupReleasesOnlyItsOwnedUseCasesAndIsIdempotent() {
        val lease = AstronomyCameraLease(); var owned = 1; var other = 1
        lease.bind { { owned-- } }
        lease.close(); lease.close()
        assertEquals(0, owned); assertEquals(1, other)
    }

    @Test fun failedBindingDoesNotRegisterCleanupForUnacquiredResources() {
        val lease = AstronomyCameraLease()
        assertThrows(IllegalStateException::class.java) { lease.bind { error("Unavailable camera") } }
        var cleaned = 0
        lease.bind { { cleaned++ } }; lease.close()
        assertEquals(1, cleaned)
    }
}
