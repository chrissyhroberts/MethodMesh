package com.example.methodmesh.modules.emergency

import java.time.Duration
import java.time.Instant

/**
 * Module-owned boundary for live hazard sources. Exact GPS should not be sent to
 * providers unless the source contract explicitly requires it and the user has
 * requested that behaviour.
 */
interface EmergencySourceAdapter {
    val sourceId: String
    val authority: EmergencySourceAuthority
    val domains: Set<EmergencyHazardDomain>
    val freshnessPolicy: Duration

    fun fetch(query: EmergencySourceQuery): EmergencySourceResult
}

data class EmergencySourceQuery(
    val regionCode: String,
    val coarseLatitude: Double? = null,
    val coarseLongitude: Double? = null,
    val boundingBox: EmergencyBoundingBox? = null,
    val now: Instant = Instant.now()
)

data class EmergencyBoundingBox(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double
)

data class EmergencySourceResult(
    val sourceId: String,
    val retrievedAt: Instant,
    val coverage: List<EmergencyDomainCoverage>,
    val alerts: List<EmergencyAlert>,
    val diagnostics: Map<String, String> = emptyMap()
)

/**
 * Deliberately empty in this first checked-in build. Live adapters are added
 * only after source/licence/freshness semantics are explicitly reviewed.
 */
object EmergencySourceRegistry {
    private val adapters = mutableListOf<EmergencySourceAdapter>()

    fun install(adapter: EmergencySourceAdapter) {
        require(adapters.none { it.sourceId == adapter.sourceId }) { "Duplicate emergency source ${adapter.sourceId}" }
        adapters += adapter
    }

    fun all(): List<EmergencySourceAdapter> = adapters.toList()
}
