package com.example.methodmesh.core.transport

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Durable application boundary; providers own their links and any secure stores. */
class MethodMeshTransportRuntime internal constructor(
    private val store: TransportJournal,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val providers = ConcurrentHashMap<String, MethodMeshTransportProvider>()
    private val consumers = ConcurrentHashMap<String, MethodMeshTransportConsumer>()
    private val bindings = ConcurrentHashMap<String, String>()
    private val providerErrors = ConcurrentHashMap<String, String>()
    private val lifecycle = Mutex()
    private val ingestion = Mutex()
    private val activeProviders = mutableSetOf<MethodMeshTransportProvider>()
    @Volatile private var started = false

    fun registerProvider(provider: MethodMeshTransportProvider) {
        require(provider.transportId.isNotBlank())
        val existing = providers.putIfAbsent(provider.transportId, provider)
        require(existing == null || existing === provider) { "Transport ID is already registered" }
        if (started) reconcile(provider)
    }
    fun unregisterProvider(transportId: String) { providers.remove(transportId)?.let(::reconcile) }
    fun bind(endpoint: TransportEndpoint, transportId: String) {
        bindings[endpoint.asKey()] = transportId
        if (started) providers[transportId]?.let(::reconcile)
    }
    fun registerConsumer(endpoint: TransportEndpoint, consumer: MethodMeshTransportConsumer) { consumers[endpoint.asKey()] = consumer }
    fun unregisterConsumer(endpoint: TransportEndpoint) { consumers.remove(endpoint.asKey()) }

    @Synchronized fun start() {
        started = true
        providers.values.forEach(::reconcile)
    }
    @Synchronized fun stop() {
        started = false
        providers.values.forEach(::reconcile)
    }

    /** Serialize provider lifecycle transitions, consulting the latest desired
     * state so rapid start/stop or late registration cannot leave an orphan link. */
    private fun reconcile(provider: MethodMeshTransportProvider) {
        scope.launch {
            lifecycle.withLock {
                try {
                    if (started && providers[provider.transportId] === provider) {
                        if (provider !in activeProviders) {
                            try { provider.start { ingestFromProvider(provider, it) } }
                            catch (e: Exception) {
                                try { provider.stop() } catch (_: Exception) { }
                                throw e
                            }
                            activeProviders += provider
                        }
                        flushPending(provider)
                    } else if (activeProviders.remove(provider)) {
                        provider.stop()
                    }
                    providerErrors.remove(provider.transportId)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { providerErrors[provider.transportId] = e.message ?: "Transport lifecycle failed" }
            }
        }
    }

    suspend fun send(envelope: MethodMeshTransportEnvelope, transportId: String? = null): TransportSendResult {
        envelope.validate()
        val selectedId = transportId ?: bindings[envelope.destination.asKey()]
        val provider = selectedId?.let { providers[it] }
        if (provider == null) {
            // An explicit/bound provider may own encrypted durability. Never
            // fall back to writing its plaintext into the generic journal.
            if (selectedId != null) return TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "Selected transport is not registered; message was not accepted")
            return try {
                store.enqueueOutbox(envelope)
                TransportSendResult(TransportOutboxState.QUEUED, "No transport is currently bound", TransportAcceptance.LOCAL_DURABLE)
            } catch (e: Exception) { TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, e.message.orEmpty()) }
        }
        if (!provider.ownsDurability) {
            try { store.enqueueOutbox(envelope) }
            catch (e: Exception) { return TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, e.message.orEmpty()) }
        }
        return deliver(provider, envelope)
    }

    suspend fun ingest(envelope: MethodMeshTransportEnvelope) = ingest(envelope, ownsDurability = false)
    private suspend fun ingestFromProvider(provider: MethodMeshTransportProvider, envelope: MethodMeshTransportEnvelope) =
        ingest(envelope, provider.ownsDurability)

    private suspend fun ingest(envelope: MethodMeshTransportEnvelope, ownsDurability: Boolean) = ingestion.withLock {
        // A rejected secure envelope must propagate failure so its provider does
        // not mark an unconsumed encrypted inbox record as dispatched.
        envelope.validate()
        if (!ownsDurability) {
            if (store.hasConsumed(envelope.source, envelope.messageId)) return@withLock
            store.recordInbox(envelope, TransportInboxState.DISPATCH_PENDING)
        }
        val consumer = consumers[envelope.destination.asKey()]
        try {
            checkNotNull(consumer) { "No consumer is currently registered for ${envelope.destination.asKey()}" }
            consumer.consume(envelope)
            if (!ownsDurability) store.recordInbox(envelope, TransportInboxState.CONSUMED)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (ownsDurability) throw e
            store.recordInbox(envelope, TransportInboxState.FAILED, e.message.orEmpty())
        }
    }

    fun pendingOutbox() = store.pendingOutbox()
    fun diagnostics(): TransportDiagnostics = TransportDiagnostics(
        registeredTransports = providers.keys().toList().sorted(),
        transportStatuses = providers.values.associate { it.transportId to it.status.value },
        inboxCount = store.inboxCount(), outboxCount = store.outboxCount(),
        pendingOutboxCount = store.pendingOutbox().size,
        transportCapabilities = providers.values.associate { it.transportId to it.capabilities },
        providerErrors = providerErrors.toMap()
    )
    fun prune() = store.prune()

    private suspend fun flushPending(provider: MethodMeshTransportProvider) {
        // No migration from a plaintext journal into a secure provider is implied.
        if (provider.ownsDurability) return
        store.pendingOutbox().filter { bindings[it.envelope.destination.asKey()] == provider.transportId }
            .forEach { deliver(provider, it.envelope) }
    }
    private suspend fun deliver(provider: MethodMeshTransportProvider, envelope: MethodMeshTransportEnvelope): TransportSendResult {
        val result = try { provider.send(envelope) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, e.message.orEmpty()) }
        if (!provider.ownsDurability) store.updateOutbox(envelope, result.state, result.detail)
        return result
    }
    companion object {
        @Volatile private var instance: MethodMeshTransportRuntime? = null
        fun initialise(context: Context): MethodMeshTransportRuntime = instance ?: synchronized(this) {
            instance ?: MethodMeshTransportRuntime(DurableTransportStore(context.applicationContext)).also { instance = it }
        }
        fun get(context: Context): MethodMeshTransportRuntime = initialise(context)
    }
}

data class TransportDiagnostics(
    val registeredTransports: List<String>,
    val transportStatuses: Map<String, TransportStatus>,
    val inboxCount: Int,
    val outboxCount: Int,
    val pendingOutboxCount: Int,
    val transportCapabilities: Map<String, TransportCapabilities> = emptyMap(),
    val providerErrors: Map<String, String> = emptyMap()
)
