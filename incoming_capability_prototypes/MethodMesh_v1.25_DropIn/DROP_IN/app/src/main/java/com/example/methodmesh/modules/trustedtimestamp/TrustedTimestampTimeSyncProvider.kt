package com.example.methodmesh.modules.trustedtimestamp

import com.example.methodmesh.core.timeassurance.ClockAssuranceRuntime
import com.example.methodmesh.core.timeassurance.TrustedTimeRefreshProvider
import com.example.methodmesh.core.timeassurance.TrustedTimeRefreshResult
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manual trusted-time refresh used by the Workbench. It timestamps an ephemeral
 * nonce payload, validates the RFC 3161 response through the normal Trusted
 * Timestamp trust path, then publishes the verified generation time into shared
 * Clock Assurance. It never changes Android system time.
 */
internal class TrustedTimestampTimeSyncProvider : TrustedTimeRefreshProvider {
    override val providerId: String = "trustedtimestamp.rfc3161"
    override val displayName: String = "RFC 3161 trusted timestamp"

    override suspend fun refresh(): TrustedTimeRefreshResult = withContext(Dispatchers.IO) {
        runCatching {
            val payload = buildString {
                appendLine("MethodMesh trusted-time refresh v1")
                appendLine("nonce=${UUID.randomUUID()}")
                append("observed_wall_time=${Instant.now()}")
            }
            val digest = TrustedTimestampEngine.hashTextUtf8(payload).second
            val authority = TrustedTimestampAuthorities.FREETSA
            val started = ClockAssuranceRuntime.monotonicSnapshot()
            val (request, responseDer) = TrustedTimestampEngine.requestTimestamp(
                digest = digest,
                authorityUrl = authority.endpoint,
                timeoutMs = DEFAULT_TIMEOUT_MS
            )
            val evidence = TrustedTimestampEngine.parseAndValidate(
                request = request,
                responseDer = responseDer,
                digest = digest,
                authorityUrl = authority.endpoint,
                timeoutMs = DEFAULT_TIMEOUT_MS
            )
            val completed = ClockAssuranceRuntime.monotonicSnapshot()
            val published = TrustedTimestampClockAnchor.publishIfTrusted(
                evidence = evidence,
                acquisitionStarted = started,
                acquisitionCompleted = completed
            )
            when {
                published == null -> TrustedTimeRefreshResult(
                    success = false,
                    message = "Timestamp was received but is not trusted for clock anchoring (${evidence.trustStatus}).",
                    source = authority.name,
                    trustedTimeIso = evidence.generationTimeIso,
                    evidenceHash = evidence.tokenSha256
                )
                !published.accepted -> TrustedTimeRefreshResult(
                    success = false,
                    message = published.reason,
                    source = authority.name,
                    trustedTimeIso = evidence.generationTimeIso,
                    evidenceHash = evidence.tokenSha256
                )
                else -> TrustedTimeRefreshResult(
                    success = true,
                    message = "Trusted time synchronized from ${authority.name}. Android system time was not changed.",
                    source = authority.name,
                    trustedTimeIso = evidence.generationTimeIso,
                    evidenceHash = evidence.tokenSha256
                )
            }
        }.getOrElse { error ->
            TrustedTimeRefreshResult(
                success = false,
                message = "Trusted-time refresh failed: ${error.message ?: error::class.java.simpleName}"
            )
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 10_000
    }
}
