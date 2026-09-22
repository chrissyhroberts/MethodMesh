package com.example.methodmesh.core.timeassurance

import java.time.Instant

enum class ClockAssuranceStatus(val wireValue: String) {
    TRUSTED_FRESH("trusted_fresh"),
    TRUSTED_AGED("trusted_aged"),
    WALL_CLOCK_ONLY("wall_clock_only"),
    REBOOT_UNANCHORED("reboot_unanchored"),
    INDETERMINATE("indeterminate")
}

enum class WallClockStatus(val wireValue: String) {
    CONSISTENT("consistent"),
    DIVERGED_FORWARD("diverged_forward"),
    ROLLBACK_DETECTED("rollback_detected"),
    NOT_COMPARABLE("not_comparable")
}

enum class ClockEvidenceState(val wireValue: String) {
    TRUSTED_INTERVAL_AVAILABLE("trusted_interval_available"),
    WALL_CLOCK_ONLY("wall_clock_only"),
    REBOOT_UNANCHORED("reboot_unanchored"),
    INDETERMINATE("indeterminate")
}

enum class MonotonicContinuity(val wireValue: String) {
    SAME_BOOT("same_boot"),
    NO_ANCHOR("no_anchor"),
    BOOT_ID_UNAVAILABLE("boot_id_unavailable"),
    BOOT_CHANGED("boot_changed"),
    MONOTONIC_REGRESSION("monotonic_regression"),
    ANCHOR_UNREADABLE("anchor_unreadable")
}

enum class WallClockRelation(val wireValue: String) {
    WITHIN_TRUSTED_INTERVAL("within_trusted_interval"),
    BEFORE_TRUSTED_INTERVAL("before_trusted_interval"),
    AFTER_TRUSTED_INTERVAL("after_trusted_interval"),
    NOT_COMPARABLE("not_comparable")
}

enum class TemporalWindowStatus(val wireValue: String) {
    BEFORE_WINDOW("before_window"),
    WITHIN_WINDOW("within_window"),
    AFTER_WINDOW("after_window"),
    OVERLAPS_BOUNDARY("overlaps_boundary"),
    NO_TRUSTED_TIME("no_trusted_time")
}

data class ClockAssurancePolicy(
    val maxTrustedAnchorAgeMillis: Long,
    val maxTrustedUncertaintyMillis: Long,
    val wallClockDivergenceToleranceMillis: Long,
    val wallClockRollbackToleranceMillis: Long,
    val oscillatorDriftPpm: Long = 50L
) {
    init {
        require(maxTrustedAnchorAgeMillis >= 0L)
        require(maxTrustedUncertaintyMillis >= 0L)
        require(wallClockDivergenceToleranceMillis >= 0L)
        require(wallClockRollbackToleranceMillis >= 0L)
        require(oscillatorDriftPpm in 0L..100_000L)
    }

    companion object {
        @Deprecated("Use an explicit capability/workflow policy. Universal time_assurance is policy-neutral.")
        val STANDARD = ClockAssurancePolicy(
            maxTrustedAnchorAgeMillis = 7L * 24L * 60L * 60L * 1000L,
            maxTrustedUncertaintyMillis = 2L * 60L * 1000L,
            wallClockDivergenceToleranceMillis = 2L * 60L * 1000L,
            wallClockRollbackToleranceMillis = 30L * 1000L,
            oscillatorDriftPpm = 50L
        )
    }
}

data class MonotonicSnapshot(
    val elapsedRealtimeMillis: Long,
    val bootSessionId: String?
) {
    init {
        require(elapsedRealtimeMillis >= 0L)
    }
}

data class VerifiedTimeAnchorCandidate(
    val trustedTime: Instant,
    val source: String,
    val evidenceHash: String,
    val acquisitionStarted: MonotonicSnapshot,
    val acquisitionCompleted: MonotonicSnapshot,
    val sourcePrecisionMillis: Long = 1000L
) {
    init {
        require(source.isNotBlank()) { "source is required." }
        require('\n' !in source && '\r' !in source) { "source must be a single line." }
        require(evidenceHash.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "evidenceHash must be a SHA-256 hexadecimal digest."
        }
        require(sourcePrecisionMillis >= 0L)
    }
}

data class ClockAnchor(
    val schemaVersion: Int = 1,
    val trustedTimeIso: String,
    val anchorElapsedRealtimeMillis: Long,
    val bootSessionId: String,
    val acquisitionWindowMillis: Long,
    val sourcePrecisionMillis: Long,
    val source: String,
    val evidenceHash: String,
    val storedAtWallTimeIso: String
) {
    val trustedTime: Instant get() = Instant.parse(trustedTimeIso)
}

data class ClockAnchorLoadResult(
    val anchor: ClockAnchor?,
    val error: String? = null
)

data class ClockAnchorPublishResult(
    val accepted: Boolean,
    val reason: String,
    val anchor: ClockAnchor? = null
)

/**
 * Policy-neutral historical clock evidence. This is the object captured into
 * every ExecutionResult and serialized into methodmesh_full_json.
 */
data class ClockEvidenceSnapshot(
    val schemaVersion: Int = 1,
    val clockModelVersion: String = "monotonic_anchor_v1",
    val observedWallTimeIso: String,
    val elapsedRealtimeMillis: Long,
    val bootSessionId: String?,
    val evidenceState: ClockEvidenceState,
    val monotonicContinuity: MonotonicContinuity,
    val trustedEstimateIso: String? = null,
    val trustedLowerBoundIso: String? = null,
    val trustedUpperBoundIso: String? = null,
    val uncertaintyMillis: Long? = null,
    val anchorTrustedTimeIso: String? = null,
    val anchorSource: String? = null,
    val anchorEvidenceHash: String? = null,
    val anchorAgeMillis: Long? = null,
    val wallClockRelation: WallClockRelation = WallClockRelation.NOT_COMPARABLE,
    /** Signed distance from the trusted interval: negative=behind, positive=ahead. */
    val wallClockOffsetFromTrustedIntervalMillis: Long? = null,
    val reason: String
) {
    val observedWallTime: Instant get() = Instant.parse(observedWallTimeIso)
    val trustedEstimate: Instant? get() = trustedEstimateIso?.let(Instant::parse)
    val trustedLowerBound: Instant? get() = trustedLowerBoundIso?.let(Instant::parse)
    val trustedUpperBound: Instant? get() = trustedUpperBoundIso?.let(Instant::parse)
}

data class ClockAssuranceObservation(
    val observedWallTimeIso: String,
    val elapsedRealtimeMillis: Long,
    val bootSessionId: String?,
    val assuranceStatus: ClockAssuranceStatus,
    val wallClockStatus: WallClockStatus,
    val trustedEstimateIso: String?,
    val trustedLowerBoundIso: String?,
    val trustedUpperBoundIso: String?,
    val anchorSource: String?,
    val anchorEvidenceHash: String?,
    val anchorAgeMillis: Long?,
    val uncertaintyMillis: Long?,
    val wallClockDivergenceMillis: Long?,
    val reason: String
) {
    val observedWallTime: Instant get() = Instant.parse(observedWallTimeIso)
    val trustedEstimate: Instant? get() = trustedEstimateIso?.let(Instant::parse)
    val trustedLowerBound: Instant? get() = trustedLowerBoundIso?.let(Instant::parse)
    val trustedUpperBound: Instant? get() = trustedUpperBoundIso?.let(Instant::parse)
}

data class TemporalBoundaryPolicy(
    val validFromInclusive: Boolean = true,
    val validUntilInclusive: Boolean = false
)

data class TemporalWindowAssessment(
    val status: TemporalWindowStatus,
    val timeUsedIso: String?,
    val assuranceStatus: ClockAssuranceStatus,
    val reason: String
)

data class TrustedTimeRefreshResult(
    val success: Boolean,
    val message: String,
    val source: String? = null,
    val trustedTimeIso: String? = null,
    val evidenceHash: String? = null
)
