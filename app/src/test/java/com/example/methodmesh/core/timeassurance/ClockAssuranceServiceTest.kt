package com.example.methodmesh.core.timeassurance

import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class ClockAssuranceServiceTest {
    private val epoch = Instant.parse("2026-09-22T12:00:00Z")
    private inner class Fixture : ClockSource, ClockAnchorRepository {
        var elapsed = 2_000L
        var boot: String? = "boot-a"
        var wall = epoch.plusMillis(1_000)
        var stored: ClockAnchor? = null
        var error: String? = null
        val service = ClockAssuranceService(this, this)
        override fun wallTime() = wall
        override fun monotonicSnapshot() = MonotonicSnapshot(elapsed, boot)
        override fun load() = ClockAnchorLoadResult(stored, error)
        override fun save(anchor: ClockAnchor) { stored = anchor; error = null }
        override fun clear() { stored = null }
        fun anchor() = service.publishVerifiedAnchor(VerifiedTimeAnchorCandidate(
            epoch, "test TSA", "a".repeat(64), MonotonicSnapshot(0, "boot-a"),
            MonotonicSnapshot(2_000, "boot-a"), 100
        ))
    }
    @Test fun `unanchored and unreadable states never claim trusted time`() {
        val f = Fixture()
        assertEquals(ClockEvidenceState.WALL_CLOCK_ONLY, f.service.snapshot().evidenceState)
        f.error = "authentication failed"
        assertEquals(ClockEvidenceState.INDETERMINATE, f.service.snapshot().evidenceState)
        assertNull(f.service.snapshot().trustedEstimate)
    }
    @Test fun `trusted estimate follows monotonic time despite wall rollback`() {
        val f = Fixture(); assertTrue(f.anchor().accepted)
        f.elapsed = 12_000; f.wall = epoch.minusSeconds(500)
        val s = f.service.snapshot()
        assertEquals(epoch.plusSeconds(11), s.trustedEstimate)
        assertEquals(WallClockRelation.BEFORE_TRUSTED_INTERVAL, s.wallClockRelation)
        assertTrue(s.uncertaintyMillis!! >= 1_100)
    }
    @Test fun `reboot missing identity and monotonic regression invalidate continuity`() {
        val f = Fixture(); f.anchor()
        for (boot in listOf("boot-b", null)) {
            f.boot = boot
            assertEquals(ClockEvidenceState.REBOOT_UNANCHORED, f.service.snapshot().evidenceState)
            assertNull(f.service.snapshot().trustedEstimate)
        }
        f.boot = "boot-a"; f.elapsed = 500
        assertEquals(MonotonicContinuity.MONOTONIC_REGRESSION, f.service.snapshot().monotonicContinuity)
    }
    @Test fun `acquisition spanning boots cannot replace the anchor`() {
        val f = Fixture(); f.anchor(); val prior = f.stored
        assertFalse(f.service.publishVerifiedAnchor(VerifiedTimeAnchorCandidate(
            epoch, "test TSA", "b".repeat(64), MonotonicSnapshot(10, "a"), MonotonicSnapshot(20, "b")
        )).accepted)
        assertEquals(prior, f.stored)
    }
    @Test fun `rollback and forward tolerance boundaries remain independent`() {
        val f = Fixture(); f.anchor()
        val lower = f.service.snapshot().trustedLowerBound!!
        val upper = f.service.snapshot().trustedUpperBound!!
        val policy = ClockAssurancePolicy(100_000, 100_000, 1_000, 10_000)
        for (offset in listOf(5_000L, 10_000L)) {
            f.wall = lower.minusMillis(offset)
            assertEquals(WallClockStatus.CONSISTENT, f.service.observe(policy).wallClockStatus)
        }
        f.wall = lower.minusMillis(10_001)
        assertEquals(WallClockStatus.ROLLBACK_DETECTED, f.service.observe(policy).wallClockStatus)
        f.wall = upper.plusMillis(1_000)
        assertEquals(WallClockStatus.CONSISTENT, f.service.observe(policy).wallClockStatus)
        f.wall = upper.plusMillis(1_001)
        assertEquals(WallClockStatus.DIVERGED_FORWARD, f.service.observe(policy).wallClockStatus)
    }
    @Test fun `policy age limits retain the policy neutral evidence`() {
        val f = Fixture(); f.anchor(); f.elapsed = 50_000
        assertEquals(ClockEvidenceState.TRUSTED_INTERVAL_AVAILABLE, f.service.snapshot().evidenceState)
        assertEquals(ClockAssuranceStatus.TRUSTED_AGED,
            f.service.observe(ClockAssurancePolicy(100, 100_000, 1_000, 1_000)).assuranceStatus)
    }
}
