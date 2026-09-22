package com.example.methodmesh.core.timeassurance

import java.time.Instant
import kotlin.math.abs
import kotlin.math.ceil

interface ClockSource {
    fun wallTime(): Instant
    fun monotonicSnapshot(): MonotonicSnapshot
}

interface ClockAnchorRepository {
    fun load(): ClockAnchorLoadResult
    fun save(anchor: ClockAnchor)
    fun clear()
}

class ClockAssuranceService(
    private val clockSource: ClockSource,
    private val repository: ClockAnchorRepository
) {
    fun monotonicSnapshot(): MonotonicSnapshot = clockSource.monotonicSnapshot()

    @Synchronized
    fun publishVerifiedAnchor(candidate: VerifiedTimeAnchorCandidate): ClockAnchorPublishResult {
        val start = candidate.acquisitionStarted
        val end = candidate.acquisitionCompleted
        val startBoot = start.bootSessionId
        val endBoot = end.bootSessionId

        if (startBoot == null || endBoot == null) {
            return ClockAnchorPublishResult(
                false,
                "Trusted time was verified, but this device cannot establish a stable boot-session identity."
            )
        }
        if (startBoot != endBoot) {
            return ClockAnchorPublishResult(false, "Device rebooted while the trusted time anchor was being acquired.")
        }
        if (end.elapsedRealtimeMillis < start.elapsedRealtimeMillis) {
            return ClockAnchorPublishResult(false, "Monotonic time moved backwards during anchor acquisition.")
        }

        val windowMillis = end.elapsedRealtimeMillis - start.elapsedRealtimeMillis
        val midpoint = start.elapsedRealtimeMillis + windowMillis / 2L
        val existing = repository.load()
        val replacingUnauthenticatedAnchor = existing.error != null

        existing.anchor?.let { prior ->
            if (prior.bootSessionId == endBoot && end.elapsedRealtimeMillis >= prior.anchorElapsedRealtimeMillis) {
                val priorEstimate = prior.trustedTime.plusMillis(
                    end.elapsedRealtimeMillis - prior.anchorElapsedRealtimeMillis
                )
                val candidateLatestReasonable = candidate.trustedTime.plusMillis(
                    (windowMillis / 2L) + candidate.sourcePrecisionMillis
                )
                if (candidateLatestReasonable.isBefore(priorEstimate.minusMillis(MAX_ANCHOR_REGRESSION_TOLERANCE_MS))) {
                    return ClockAnchorPublishResult(
                        false,
                        "New trusted time would regress an existing same-boot trusted timeline."
                    )
                }
            }
        }

        val anchor = ClockAnchor(
            trustedTimeIso = candidate.trustedTime.toString(),
            anchorElapsedRealtimeMillis = midpoint,
            bootSessionId = endBoot,
            acquisitionWindowMillis = windowMillis,
            sourcePrecisionMillis = candidate.sourcePrecisionMillis,
            source = candidate.source.trim(),
            evidenceHash = candidate.evidenceHash.lowercase(),
            storedAtWallTimeIso = clockSource.wallTime().toString()
        )
        repository.save(anchor)
        return ClockAnchorPublishResult(
            true,
            if (replacingUnauthenticatedAnchor) {
                "Trusted time anchor accepted and replaced an unreadable or unauthenticated prior anchor."
            } else {
                "Trusted time anchor accepted."
            },
            anchor
        )
    }

    /** Policy-neutral evidence snapshot for UI and historical execution sidecars. */
    fun snapshot(): ClockEvidenceSnapshot {
        val wall = clockSource.wallTime()
        val now = clockSource.monotonicSnapshot()
        val loaded = repository.load()

        if (loaded.error != null) {
            return ClockEvidenceSnapshot(
                observedWallTimeIso = wall.toString(),
                elapsedRealtimeMillis = now.elapsedRealtimeMillis,
                bootSessionId = now.bootSessionId,
                evidenceState = ClockEvidenceState.INDETERMINATE,
                monotonicContinuity = MonotonicContinuity.ANCHOR_UNREADABLE,
                reason = "Stored clock anchor is unreadable or unauthenticated: ${loaded.error}"
            )
        }

        val anchor = loaded.anchor ?: return ClockEvidenceSnapshot(
            observedWallTimeIso = wall.toString(),
            elapsedRealtimeMillis = now.elapsedRealtimeMillis,
            bootSessionId = now.bootSessionId,
            evidenceState = ClockEvidenceState.WALL_CLOCK_ONLY,
            monotonicContinuity = if (now.bootSessionId == null) {
                MonotonicContinuity.BOOT_ID_UNAVAILABLE
            } else {
                MonotonicContinuity.NO_ANCHOR
            },
            reason = "No trusted time anchor has been established on this installation."
        )

        val continuity = when {
            now.bootSessionId == null -> MonotonicContinuity.BOOT_ID_UNAVAILABLE
            now.bootSessionId != anchor.bootSessionId -> MonotonicContinuity.BOOT_CHANGED
            now.elapsedRealtimeMillis < anchor.anchorElapsedRealtimeMillis -> MonotonicContinuity.MONOTONIC_REGRESSION
            else -> MonotonicContinuity.SAME_BOOT
        }

        if (continuity != MonotonicContinuity.SAME_BOOT) {
            return ClockEvidenceSnapshot(
                observedWallTimeIso = wall.toString(),
                elapsedRealtimeMillis = now.elapsedRealtimeMillis,
                bootSessionId = now.bootSessionId,
                evidenceState = ClockEvidenceState.REBOOT_UNANCHORED,
                monotonicContinuity = continuity,
                anchorTrustedTimeIso = anchor.trustedTimeIso,
                anchorSource = anchor.source,
                anchorEvidenceHash = anchor.evidenceHash,
                reason = "Trusted anchor exists, but same-boot monotonic continuity can no longer be established."
            )
        }

        val elapsedSinceAnchor = now.elapsedRealtimeMillis - anchor.anchorElapsedRealtimeMillis
        val estimate = anchor.trustedTime.plusMillis(elapsedSinceAnchor)
        val initialUncertainty = safeAdd(anchor.acquisitionWindowMillis / 2L, anchor.sourcePrecisionMillis)
        val driftUncertainty = ceil(
            elapsedSinceAnchor.toDouble() * CLOCK_MODEL_OSCILLATOR_DRIFT_PPM.toDouble() / 1_000_000.0
        ).toLong().coerceAtLeast(0L)
        val uncertainty = safeAdd(initialUncertainty, driftUncertainty)
        val lower = estimate.minusMillis(uncertainty)
        val upper = estimate.plusMillis(uncertainty)
        val offset = when {
            wall.isBefore(lower) -> wall.toEpochMilli() - lower.toEpochMilli()
            wall.isAfter(upper) -> wall.toEpochMilli() - upper.toEpochMilli()
            else -> 0L
        }
        val relation = when {
            offset < 0L -> WallClockRelation.BEFORE_TRUSTED_INTERVAL
            offset > 0L -> WallClockRelation.AFTER_TRUSTED_INTERVAL
            else -> WallClockRelation.WITHIN_TRUSTED_INTERVAL
        }

        return ClockEvidenceSnapshot(
            observedWallTimeIso = wall.toString(),
            elapsedRealtimeMillis = now.elapsedRealtimeMillis,
            bootSessionId = now.bootSessionId,
            evidenceState = ClockEvidenceState.TRUSTED_INTERVAL_AVAILABLE,
            monotonicContinuity = MonotonicContinuity.SAME_BOOT,
            trustedEstimateIso = estimate.toString(),
            trustedLowerBoundIso = lower.toString(),
            trustedUpperBoundIso = upper.toString(),
            uncertaintyMillis = uncertainty,
            anchorTrustedTimeIso = anchor.trustedTimeIso,
            anchorSource = anchor.source,
            anchorEvidenceHash = anchor.evidenceHash,
            anchorAgeMillis = elapsedSinceAnchor,
            wallClockRelation = relation,
            wallClockOffsetFromTrustedIntervalMillis = offset,
            reason = "Trusted same-boot time interval is available."
        )
    }

    fun observe(policy: ClockAssurancePolicy): ClockAssuranceObservation {
        val evidence = snapshot()
        val assurance = when (evidence.evidenceState) {
            ClockEvidenceState.TRUSTED_INTERVAL_AVAILABLE -> {
                if (
                    (evidence.anchorAgeMillis ?: Long.MAX_VALUE) <= policy.maxTrustedAnchorAgeMillis &&
                    (evidence.uncertaintyMillis ?: Long.MAX_VALUE) <= policy.maxTrustedUncertaintyMillis
                ) ClockAssuranceStatus.TRUSTED_FRESH else ClockAssuranceStatus.TRUSTED_AGED
            }
            ClockEvidenceState.WALL_CLOCK_ONLY -> ClockAssuranceStatus.WALL_CLOCK_ONLY
            ClockEvidenceState.REBOOT_UNANCHORED -> ClockAssuranceStatus.REBOOT_UNANCHORED
            ClockEvidenceState.INDETERMINATE -> ClockAssuranceStatus.INDETERMINATE
        }

        val offset = evidence.wallClockOffsetFromTrustedIntervalMillis
        val wallStatus = when {
            offset == null -> WallClockStatus.NOT_COMPARABLE
            offset < -policy.wallClockRollbackToleranceMillis -> WallClockStatus.ROLLBACK_DETECTED
            offset > policy.wallClockDivergenceToleranceMillis -> WallClockStatus.DIVERGED_FORWARD
            abs(offset) <= policy.wallClockDivergenceToleranceMillis -> WallClockStatus.CONSISTENT
            else -> WallClockStatus.ROLLBACK_DETECTED
        }

        val reason = buildString {
            append(evidence.reason)
            if (evidence.evidenceState == ClockEvidenceState.TRUSTED_INTERVAL_AVAILABLE) {
                append(
                    if (assurance == ClockAssuranceStatus.TRUSTED_FRESH) {
                        " Trusted evidence is within the caller's age and uncertainty limits."
                    } else {
                        " Trusted evidence exceeds the caller's preferred age or uncertainty limits."
                    }
                )
                append(
                    when (wallStatus) {
                        WallClockStatus.CONSISTENT -> " Android wall clock is consistent with the trusted interval."
                        WallClockStatus.DIVERGED_FORWARD -> " Android wall clock is materially ahead of the trusted interval."
                        WallClockStatus.ROLLBACK_DETECTED -> " Android wall clock is materially behind the trusted interval; rollback is suspected."
                        WallClockStatus.NOT_COMPARABLE -> ""
                    }
                )
            }
        }

        return ClockAssuranceObservation(
            observedWallTimeIso = evidence.observedWallTimeIso,
            elapsedRealtimeMillis = evidence.elapsedRealtimeMillis,
            bootSessionId = evidence.bootSessionId,
            assuranceStatus = assurance,
            wallClockStatus = wallStatus,
            trustedEstimateIso = evidence.trustedEstimateIso,
            trustedLowerBoundIso = evidence.trustedLowerBoundIso,
            trustedUpperBoundIso = evidence.trustedUpperBoundIso,
            anchorSource = evidence.anchorSource,
            anchorEvidenceHash = evidence.anchorEvidenceHash,
            anchorAgeMillis = evidence.anchorAgeMillis,
            uncertaintyMillis = evidence.uncertaintyMillis,
            wallClockDivergenceMillis = offset,
            reason = reason
        )
    }

    fun assessTemporalWindow(
        validFrom: Instant?,
        validUntil: Instant?,
        observation: ClockAssuranceObservation,
        boundaryPolicy: TemporalBoundaryPolicy = TemporalBoundaryPolicy()
    ): TemporalWindowAssessment {
        require(validFrom == null || validUntil == null || !validUntil.isBefore(validFrom)) {
            "validUntil must not be before validFrom."
        }
        val lower = observation.trustedLowerBound
        val upper = observation.trustedUpperBound
        val estimate = observation.trustedEstimate
        if (lower == null || upper == null || estimate == null) {
            return TemporalWindowAssessment(
                TemporalWindowStatus.NO_TRUSTED_TIME,
                null,
                observation.assuranceStatus,
                "No trusted current-time interval is available."
            )
        }

        val entirelyBeforeStart = validFrom?.let { start ->
            if (boundaryPolicy.validFromInclusive) upper.isBefore(start) else !upper.isAfter(start)
        } ?: false
        if (entirelyBeforeStart) {
            return TemporalWindowAssessment(
                TemporalWindowStatus.BEFORE_WINDOW,
                estimate.toString(),
                observation.assuranceStatus,
                "The entire assured current-time interval is before valid_from."
            )
        }

        val entirelyAfterEnd = validUntil?.let { end ->
            if (boundaryPolicy.validUntilInclusive) lower.isAfter(end) else !lower.isBefore(end)
        } ?: false
        if (entirelyAfterEnd) {
            return TemporalWindowAssessment(
                TemporalWindowStatus.AFTER_WINDOW,
                estimate.toString(),
                observation.assuranceStatus,
                "The entire assured current-time interval is after valid_until."
            )
        }

        val startsInside = validFrom?.let { start ->
            if (boundaryPolicy.validFromInclusive) !lower.isBefore(start) else lower.isAfter(start)
        } ?: true
        val endsInside = validUntil?.let { end ->
            if (boundaryPolicy.validUntilInclusive) !upper.isAfter(end) else upper.isBefore(end)
        } ?: true

        return if (startsInside && endsInside) {
            TemporalWindowAssessment(
                TemporalWindowStatus.WITHIN_WINDOW,
                estimate.toString(),
                observation.assuranceStatus,
                "The entire assured current-time interval is within the validity window."
            )
        } else {
            TemporalWindowAssessment(
                TemporalWindowStatus.OVERLAPS_BOUNDARY,
                estimate.toString(),
                observation.assuranceStatus,
                "Clock uncertainty overlaps a validity boundary, so the temporal relationship is not definitive."
            )
        }
    }

    @Synchronized
    fun clearAnchor() = repository.clear()

    private fun safeAdd(a: Long, b: Long): Long =
        if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b

    companion object {
        private const val MAX_ANCHOR_REGRESSION_TOLERANCE_MS = 5L * 60L * 1000L
        private const val CLOCK_MODEL_OSCILLATOR_DRIFT_PPM = 50L
    }
}
