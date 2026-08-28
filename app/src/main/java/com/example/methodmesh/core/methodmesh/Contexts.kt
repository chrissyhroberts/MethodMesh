package com.example.methodmesh.core.methodmesh

/**
 * AS1.00 separates event time, observation time, execution time and system time.
 * The distinction matters because field data may be captured, interpreted and
 * transported at different moments.
 */
data class TemporalContext(
    val eventTimeEpochMs: Long? = null,
    val observationTimeEpochMs: Long? = null,
    val executionTimeEpochMs: Long? = null,
    val effectiveTimeEpochMs: Long? = null,
    val systemTimeEpochMs: Long = System.currentTimeMillis()
)

/** Spatial context remains transport-neutral and can later hold GPS, image-map,
 * body-map, household-map or protocol-specific coordinate systems. */
data class SpatialContext(
    val referenceSystem: String? = null,
    val location: Map<String, String> = emptyMap(),
    val region: Map<String, String> = emptyMap()
)

/** Provenance for signals, methods and knowledge objects. */
data class ProvenanceContext(
    val provider: String,
    val methodId: String? = null,
    val methodVersion: String? = null,
    val deviceId: String? = null,
    val operatorId: String? = null,
    val softwareVersion: String? = null,
    val protocolVersion: String? = null,
    val extra: Map<String, String> = emptyMap()
)
