package com.example.methodmesh.core.timeassurance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ClockAssuranceServiceTest {
    private val policy = ClockAssurancePolicy(
        maxTrustedAnchorAgeMillis = 60_000L,
        maxTrustedUncertaintyMillis = 5_000L,
        wallClockDivergenceToleranceMillis = 2_000L,
        wallClockRollbackToleranceMillis = 1_000L,
        oscillatorDriftPpm = 50L
    )

    @Test
    fun `without anchor service is wall clock only`() {
        val service = ClockAssuranceService(
            FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4"),
            MemoryRepo()
        )
        assertEquals(ClockAssuranceStatus.WALL_CLOCK_ONLY, service.observe(policy).assuranceStatus)
    }

    @Test
    fun `trusted anchor advances monotonically while wall rollback is separate evidence`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        assertTrue(service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 9_800L, 10_000L)).accepted)

        clock.elapsed = 20_000L
        clock.wall = Instant.parse("2026-09-22T06:59:00Z")
        val observation = service.observe(policy.copy(maxTrustedAnchorAgeMillis = 30_000L))

        assertEquals(ClockAssuranceStatus.TRUSTED_FRESH, observation.assuranceStatus)
        assertEquals(WallClockStatus.ROLLBACK_DETECTED, observation.wallClockStatus)
        assertEquals(Instant.parse("2026-09-22T07:00:10.100Z"), observation.trustedEstimate)
        assertNotNull(observation.trustedLowerBound)
        assertNotNull(observation.trustedUpperBound)
    }

    @Test
    fun `old same boot anchor becomes aged rather than disappearing`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 100L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 0L, 100L))
        clock.elapsed = 120_000L
        assertEquals(ClockAssuranceStatus.TRUSTED_AGED, service.observe(policy).assuranceStatus)
    }

    @Test
    fun `changed boot session breaks trusted continuity even if elapsed realtime is larger`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 9_900L, 10_000L))
        clock.boot = "boot:5"
        clock.elapsed = 99_000L
        val observation = service.observe(policy)
        assertEquals(ClockAssuranceStatus.REBOOT_UNANCHORED, observation.assuranceStatus)
        assertNull(observation.trustedEstimate)
    }

    @Test
    fun `anchor acquisition is rejected without boot identity`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, null)
        val service = ClockAssuranceService(clock, MemoryRepo())
        val result = service.publishVerifiedAnchor(
            VerifiedTimeAnchorCandidate(
                Instant.parse("2026-09-22T07:00:00Z"), "test", "a".repeat(64),
                MonotonicSnapshot(9_900L, null), MonotonicSnapshot(10_000L, null), 0L
            )
        )
        assertFalse(result.accepted)
    }

    @Test
    fun `exclusive expiry boundary is definitive only when whole interval is beyond it`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 8_000L, 10_000L, 1_000L))
        val observation = service.observe(policy.copy(maxTrustedUncertaintyMillis = 10_000L))
        val assessment = service.assessTemporalWindow(
            validFrom = null,
            validUntil = Instant.parse("2026-09-22T07:00:00.500Z"),
            observation = observation,
            boundaryPolicy = TemporalBoundaryPolicy(validUntilInclusive = false)
        )
        assertEquals(TemporalWindowStatus.OVERLAPS_BOUNDARY, assessment.status)
    }

    @Test
    fun `new anchor cannot regress established same boot trusted timeline`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 9_900L, 10_000L))
        clock.elapsed = 610_000L
        val result = service.publishVerifiedAnchor(
            candidate(Instant.parse("2026-09-22T06:40:00Z"), 609_000L, 610_000L)
        )
        assertFalse(result.accepted)
    }

    @Test
    fun `corrupt persisted anchor is not trusted but fresh verified evidence can recover it`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:00Z"), 10_000L, "boot:4")
        val repo = RecoverableErrorRepo("bad hmac")
        val service = ClockAssuranceService(clock, repo)
        assertEquals(ClockAssuranceStatus.INDETERMINATE, service.observe(policy).assuranceStatus)
        assertTrue(service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 9_900L, 10_000L)).accepted)
        assertNotNull(repo.value)
    }

    @Test
    fun `wall clock inside trusted uncertainty interval is not called divergent`() {
        val clock = FakeClock(Instant.parse("2026-09-22T07:00:01.500Z"), 10_000L, "boot:4")
        val service = ClockAssuranceService(clock, MemoryRepo())
        service.publishVerifiedAnchor(candidate(Instant.parse("2026-09-22T07:00:00Z"), 8_000L, 10_000L, 1_000L))
        val observation = service.observe(policy.copy(maxTrustedUncertaintyMillis = 10_000L))
        assertEquals(WallClockStatus.CONSISTENT, observation.wallClockStatus)
        assertEquals(0L, observation.wallClockDivergenceMillis)
    }

    private fun candidate(
        time: Instant,
        start: Long,
        end: Long,
        precision: Long = 0L
    ) = VerifiedTimeAnchorCandidate(
        trustedTime = time,
        source = "test",
        evidenceHash = "a".repeat(64),
        acquisitionStarted = MonotonicSnapshot(start, "boot:4"),
        acquisitionCompleted = MonotonicSnapshot(end, "boot:4"),
        sourcePrecisionMillis = precision
    )

    private class FakeClock(
        var wall: Instant,
        var elapsed: Long,
        var boot: String?
    ) : ClockSource {
        override fun wallTime(): Instant = wall
        override fun monotonicSnapshot() = MonotonicSnapshot(elapsed, boot)
    }

    private class MemoryRepo : ClockAnchorRepository {
        var value: ClockAnchor? = null
        override fun load() = ClockAnchorLoadResult(value)
        override fun save(anchor: ClockAnchor) { value = anchor }
        override fun clear() { value = null }
    }

    private class RecoverableErrorRepo(private val message: String) : ClockAnchorRepository {
        var value: ClockAnchor? = null
        override fun load() = value?.let { ClockAnchorLoadResult(it) } ?: ClockAnchorLoadResult(null, message)
        override fun save(anchor: ClockAnchor) { value = anchor }
        override fun clear() { value = null }
    }
}
