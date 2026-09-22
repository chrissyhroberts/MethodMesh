package com.example.methodmesh.modules.trustedtimestamp

import com.example.methodmesh.platform.timestamp.TrustedTimestampEvidence

import com.example.methodmesh.core.timeassurance.ClockAnchorPublishResult
import com.example.methodmesh.core.timeassurance.ClockAssuranceRuntime
import com.example.methodmesh.core.timeassurance.MonotonicSnapshot
import com.example.methodmesh.core.timeassurance.VerifiedTimeAnchorCandidate
import java.time.Instant

/**
 * Bridges validated RFC 3161 evidence into the project-wide clock-assurance
 * service without making core depend on a capability module.
 */
internal object TrustedTimestampClockAnchor {
    fun publishIfTrusted(
        evidence: TrustedTimestampEvidence,
        acquisitionStarted: MonotonicSnapshot,
        acquisitionCompleted: MonotonicSnapshot
    ): ClockAnchorPublishResult? {
        if (evidence.trustStatus != TRUSTED_REGISTRY_STATUS) return null

        return runCatching {
            ClockAssuranceRuntime.publishVerifiedAnchor(
                VerifiedTimeAnchorCandidate(
                    trustedTime = Instant.parse(evidence.generationTimeIso),
                    source = "rfc3161:${evidence.authorityUrl}",
                    evidenceHash = evidence.tokenSha256,
                    acquisitionStarted = acquisitionStarted,
                    acquisitionCompleted = acquisitionCompleted,
                    sourcePrecisionMillis = RFC3161_SOURCE_PRECISION_MS
                )
            )
        }.getOrElse { error ->
            ClockAnchorPublishResult(
                accepted = false,
                reason = "Trusted timestamp proof succeeded, but shared clock anchoring failed: " +
                    (error.message ?: error::class.java.simpleName)
            )
        }
    }

    private const val TRUSTED_REGISTRY_STATUS = "trusted_registry_match"
    private const val RFC3161_SOURCE_PRECISION_MS = 1000L
}
