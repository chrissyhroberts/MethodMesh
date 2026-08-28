package com.example.methodmesh.core.methodmesh

/**
 * Transient information emitted by a Device Service.
 *
 * A Signal is not persistent knowledge. It becomes architectural knowledge only
 * after a Method, usually a SignalInterpreter, produces one or more Observations.
 */
data class Signal(
    override val id: ArchitectureId = ArchitectureId(),
    val signalType: String,
    val sourceService: String,
    val payload: Map<String, String>,
    val temporalContext: TemporalContext = TemporalContext(),
    val spatialContext: SpatialContext? = null,
    val provenance: ProvenanceContext
) : ArchitectureObject {
    override val objectType: String = "Signal"
}

interface DeviceService {
    val serviceId: String
    fun describe(): Map<String, String>
}

fun interface SignalInterpreter {
    fun interpret(signal: Signal): List<Observation>
}
