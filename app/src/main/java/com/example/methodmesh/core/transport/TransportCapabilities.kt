package com.example.methodmesh.core.transport

/** Link metadata, not a claim that a generic router implements every class. */
enum class TransportTrafficClass { CONTROL, LIVE, ALERT, DURABLE, TELEMETRY, BULK }
enum class TransportAddressing { UNICAST, GROUP, BROADCAST }
enum class TransportCost { UNKNOWN, UNMETERED, METERED }
enum class TransportPowerPolicy { UNKNOWN, INFRASTRUCTURE, DUTY_CYCLED, ENDPOINT_LOW_POWER, CRITICAL_BATTERY }
enum class TransportNodeRole { ENDPOINT, RELAY, HANDSET_GATEWAY, STORE, ANCHOR, EXTERNAL_GATEWAY }

/** Limits apply to provider wire bytes, including framing/crypto, not plaintext.
 * Null means unknown, never unlimited. Providers retain exact admission checks.
 * LIVE describes bearer ability; durable send() is not a live-frame API.
 */
data class TransportCapabilities(
    val protocol: String? = null,
    val trafficClasses: Set<TransportTrafficClass> = setOf(TransportTrafficClass.DURABLE),
    val addressing: Set<TransportAddressing> = setOf(TransportAddressing.UNICAST),
    val maxFrameBytes: Int? = null,
    val maxObjectBytes: Int? = null,
    val supportsFragmentation: Boolean = false,
    val supportsStoreAndForward: Boolean = false,
    val endToEndProtected: Boolean = false,
    val cost: TransportCost = TransportCost.UNKNOWN,
    val powerPolicy: TransportPowerPolicy = TransportPowerPolicy.UNKNOWN
) {
    init {
        require(protocol == null || protocol.length in 1..128)
        require(maxFrameBytes == null || maxFrameBytes > 0)
        require(maxObjectBytes == null || maxObjectBytes > 0)
        require(maxFrameBytes == null || maxObjectBytes == null || maxObjectBytes >= maxFrameBytes)
        require(trafficClasses.isNotEmpty())
        require(addressing.isNotEmpty())
    }
}

/** Future topology seam: a logical node can expose multiple provider interfaces.
 * Binding a node to a MAC/IP/USB address stays inside the owning provider.
 */
data class TransportNodeDescriptor(
    val endpoint: TransportEndpoint,
    val roles: Set<TransportNodeRole>,
    val interfaces: List<TransportInterfaceDescriptor>
) {
    init {
        require(interfaces.size <= 32)
        require(interfaces.map { it.interfaceId }.distinct().size == interfaces.size)
    }
}
data class TransportInterfaceDescriptor(
    val interfaceId: String,
    val transportId: String,
    val capabilities: TransportCapabilities
) {
    init { require(interfaceId.length in 1..128); require(transportId.length in 1..128) }
}

/** Typed progress evidence alongside legacy outbox states. Local acceptance is
 * not an end-to-end delivery receipt; providers must not infer later stages.
 */
enum class TransportAcceptance { LOCAL_DURABLE, LINK_SENT, REMOTE_DURABLE, DESTINATION_DELIVERED }
