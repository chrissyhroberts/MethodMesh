package com.example.methodmesh.core.transport

/** Storage-independent durable message boundary. Implementations must reject a
 * full/corrupt journal instead of silently evicting accepted undelivered work. */
interface TransportJournal {
    fun hasConsumed(source: TransportEndpoint, messageId: String): Boolean
    fun recordInbox(envelope: MethodMeshTransportEnvelope, state: TransportInboxState, detail: String = "")
    fun enqueueOutbox(envelope: MethodMeshTransportEnvelope): TransportOutboxRecord
    fun updateOutbox(envelope: MethodMeshTransportEnvelope, state: TransportOutboxState, detail: String = "")
    fun pendingOutbox(now: Long = System.currentTimeMillis()): List<TransportOutboxRecord>
    fun inboxCount(): Int
    fun outboxCount(): Int
    fun prune(now: Long = System.currentTimeMillis())
}
data class TransportInboxRecord(val envelope: MethodMeshTransportEnvelope, val state: TransportInboxState, val detail: String = "")
data class TransportOutboxRecord(val envelope: MethodMeshTransportEnvelope, val state: TransportOutboxState, val attempts: Int = 0, val detail: String = "")
