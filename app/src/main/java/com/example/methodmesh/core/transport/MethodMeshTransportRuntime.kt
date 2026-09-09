package com.example.methodmesh.core.transport

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Central durable boundary. Transport implementations remain outside core. */
class MethodMeshTransportRuntime private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = DurableTransportStore(appContext)
    private val providers = ConcurrentHashMap<String, MethodMeshTransportProvider>()
    private val consumers = ConcurrentHashMap<String, MethodMeshTransportConsumer>()
    private val bindings = ConcurrentHashMap<String, String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun registerProvider(provider: MethodMeshTransportProvider) { providers[provider.transportId] = provider }
    fun unregisterProvider(transportId: String) { providers.remove(transportId) }
    fun bind(endpoint: TransportEndpoint, transportId: String) { bindings[endpoint.asKey()] = transportId }
    fun registerConsumer(endpoint: TransportEndpoint, consumer: MethodMeshTransportConsumer) { consumers[endpoint.asKey()] = consumer }
    fun unregisterConsumer(endpoint: TransportEndpoint) { consumers.remove(endpoint.asKey()) }

    fun start() {
        providers.values.forEach { provider ->
            scope.launch {
                provider.start { envelope -> ingest(envelope) }
                flushPending(provider)
            }
        }
    }
    fun stop() { providers.values.forEach { provider -> scope.launch { provider.stop() } } }

    suspend fun send(envelope: MethodMeshTransportEnvelope, transportId: String? = null): TransportSendResult {
        envelope.validate()
        store.enqueueOutbox(envelope)
        val provider = providers[transportId ?: bindings[envelope.destination.asKey()]]
        if (provider == null) return TransportSendResult(TransportOutboxState.QUEUED, "No transport is currently bound")
        return deliver(provider, envelope)
    }

    suspend fun ingest(envelope: MethodMeshTransportEnvelope) {
        runCatching { envelope.validate() }.onFailure { error ->
            store.recordInbox(envelope, TransportInboxState.FAILED, error.message.orEmpty()); return
        }
        if (store.hasSeen(envelope.messageId)) return
        store.recordInbox(envelope, TransportInboxState.DISPATCH_PENDING)
        val consumer = consumers[envelope.destination.asKey()]
        if (consumer == null) {
            store.recordInbox(envelope, TransportInboxState.FAILED, "Unknown destination")
            return
        }
        runCatching { consumer.consume(envelope) }
            .onSuccess { store.recordInbox(envelope, TransportInboxState.CONSUMED) }
            .onFailure { store.recordInbox(envelope, TransportInboxState.FAILED, it.message.orEmpty()) }
    }

    fun pendingOutbox() = store.pendingOutbox()
    fun prune() = store.prune()

    private suspend fun flushPending(provider: MethodMeshTransportProvider) {
        store.pendingOutbox().filter { bindings[it.envelope.destination.asKey()] == provider.transportId }
            .forEach { deliver(provider, it.envelope) }
    }

    private suspend fun deliver(provider: MethodMeshTransportProvider, envelope: MethodMeshTransportEnvelope): TransportSendResult {
        val result = runCatching { provider.send(envelope) }.getOrElse { TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, it.message.orEmpty()) }
        store.updateOutbox(envelope.messageId, result.state, attempts = 1, detail = result.detail)
        return result
    }

    companion object {
        @Volatile private var instance: MethodMeshTransportRuntime? = null
        fun initialise(context: Context): MethodMeshTransportRuntime = instance ?: synchronized(this) { instance ?: MethodMeshTransportRuntime(context).also { instance = it } }
        fun get(context: Context): MethodMeshTransportRuntime = initialise(context)
    }
}
