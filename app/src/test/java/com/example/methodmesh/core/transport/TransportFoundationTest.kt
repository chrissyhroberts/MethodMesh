package com.example.methodmesh.core.transport

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TransportFoundationTest {
    @get:Rule val temp = TemporaryFolder()
    private val source = TransportEndpoint("installation", "phone-a")
    private val destination = TransportEndpoint("service", "test-receiver")
    private fun message(id: String = "message-1", sender: TransportEndpoint = source) = MethodMeshTransportEnvelope(
        messageId = id, source = sender, destination = destination,
        messageType = "DATA", payloadType = "text/plain", payload = "private-test-payload"
    )
    private class Provider(override val transportId: String = "test", override val ownsDurability: Boolean = false) : MethodMeshTransportProvider {
        override val status = MutableStateFlow(TransportStatus(true, true))
        var callback: (suspend (MethodMeshTransportEnvelope) -> Unit)? = null
        var startGate: CompletableDeferred<Unit>? = null
        var failStart = false
        var starts = 0; var stops = 0
        val sent = mutableListOf<MethodMeshTransportEnvelope>()
        override suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit) {
            starts++; startGate?.await()
            if (failStart) error("simulated link failure")
            callback = onEnvelope
        }
        override suspend fun stop() { stops++; callback = null }
        override suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult {
            sent += envelope
            return TransportSendResult(TransportOutboxState.SENT)
        }
        suspend fun inject(envelope: MethodMeshTransportEnvelope) = checkNotNull(callback).invoke(envelope)
    }

    @Test fun unboundQueueDrainsWhenAProviderIsBoundLater() = runBlocking {
        val journal = DurableTransportStore(temp.newFolder())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(journal, scope)
            val envelope = message()
            assertEquals(TransportOutboxState.QUEUED, runtime.send(envelope).state)
            runtime.start()
            val provider = Provider()
            runtime.registerProvider(provider)
            runtime.bind(destination, provider.transportId)
            assertEquals(listOf(envelope), provider.sent)
            assertTrue(runtime.pendingOutbox().isEmpty())
            runtime.stop(); assertEquals(1, provider.stops)
        } finally { scope.cancel() }
    }

    @Test fun absentExplicitTransportDoesNotLeakIntoPlaintextFallback() = runBlocking {
        val folder = temp.newFolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(DurableTransportStore(folder), scope)
            assertEquals(TransportOutboxState.FAILED_RETRYABLE, runtime.send(message(), "secure-provider").state)
            assertFalse(File(folder, "outbox.jsonl").exists())
        } finally { scope.cancel() }
    }

    @Test fun secureConsumerFailureRemainsRetryableWithoutPlaintextJournals() = runBlocking {
        val folder = temp.newFolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(DurableTransportStore(folder), scope)
            val provider = Provider(ownsDurability = true)
            runtime.registerProvider(provider); runtime.start()
            runtime.registerConsumer(destination) { error("consumer temporarily unavailable") }
            assertTrue(runCatching { provider.inject(message()) }.isFailure)
            var received = 0
            runtime.registerConsumer(destination) { received++ }
            provider.inject(message())
            assertEquals(1, received)
            runtime.send(message(), provider.transportId)
            assertFalse(File(folder, "inbox.jsonl").exists())
            assertFalse(File(folder, "outbox.jsonl").exists())
        } finally { scope.cancel() }
    }

    @Test fun failedConsumerRetriesAndDedupeIncludesLogicalSource() = runBlocking {
        val folder = temp.newFolder()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(DurableTransportStore(folder), scope)
            var attempts = 0
            runtime.registerConsumer(destination) { attempts++; if (attempts == 1) error("not ready") }
            val envelope = message()
            runtime.ingest(envelope); runtime.ingest(envelope); runtime.ingest(envelope)
            assertEquals(2, attempts)
            runtime.ingest(envelope.copy(source = TransportEndpoint("installation", "phone-b")))
            assertEquals(3, attempts)
            val restarted = MethodMeshTransportRuntime(DurableTransportStore(folder), scope)
            restarted.registerConsumer(destination) { attempts++ }
            restarted.ingest(envelope)
            assertEquals(3, attempts)
        } finally { scope.cancel() }
    }

    @Test fun queueFullAndCorruptionNeverDiscardAcceptedWork() {
        val folder = temp.newFolder()
        val journal = DurableTransportStore(folder, maxOutboxRecords = 1)
        val envelope = message()
        journal.enqueueOutbox(envelope)
        val file = File(folder, "outbox.jsonl")
        val committed = file.readBytes()
        assertThrows(IllegalStateException::class.java) { journal.enqueueOutbox(message("message-2")) }
        assertArrayEquals(committed, file.readBytes())
        file.appendText("broken-json\n")
        val corrupt = file.readBytes()
        assertThrows(IllegalStateException::class.java) { journal.enqueueOutbox(envelope) }
        assertThrows(IllegalStateException::class.java) { journal.prune() }
        assertArrayEquals(corrupt, file.readBytes())
    }

    @Test fun interruptedWriteRetainsCommittedDataAndAttemptsSurviveReopen() {
        val folder = temp.newFolder()
        val envelope = message()
        val journal = DurableTransportStore(folder)
        journal.enqueueOutbox(envelope)
        journal.updateOutbox(envelope, TransportOutboxState.FAILED_RETRYABLE)
        journal.updateOutbox(envelope, TransportOutboxState.FAILED_RETRYABLE)
        File(folder, "outbox.jsonl.partial").writeText("interrupted-write")
        val restarted = DurableTransportStore(folder)
        assertEquals(2, restarted.pendingOutbox().single().attempts)
        assertEquals(envelope, restarted.pendingOutbox().single().envelope)
        restarted.enqueueOutbox(envelope)
        assertEquals(2, restarted.pendingOutbox().single().attempts)
        assertThrows(IllegalArgumentException::class.java) { restarted.enqueueOutbox(envelope.copy(payload = "different")) }
    }

    @Test fun stopDuringStartupAndLateProviderRegistrationAreSafe() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(DurableTransportStore(temp.newFolder()), scope)
            runtime.start()
            val provider = Provider().apply { startGate = CompletableDeferred() }
            runtime.registerProvider(provider)
            runtime.stop()
            provider.startGate!!.complete(Unit)
            yield()
            assertEquals(1, provider.starts); assertEquals(1, provider.stops)
            assertNull(provider.callback)
            runtime.start(); assertEquals(2, provider.starts)
            runtime.unregisterProvider(provider.transportId)
            assertEquals(2, provider.stops)
        } finally { scope.cancel() }
    }

    @Test fun aFailingProviderDoesNotPreventOtherProvidersStarting() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val runtime = MethodMeshTransportRuntime(DurableTransportStore(temp.newFolder()), scope)
            runtime.registerProvider(Provider("broken").apply { failStart = true })
            val good = Provider("working")
            runtime.registerProvider(good); runtime.start()
            assertEquals(1, good.starts)
            assertTrue(runtime.diagnostics().providerErrors.containsKey("broken"))
            assertEquals(good.capabilities, runtime.diagnostics().transportCapabilities[good.transportId])
        } finally { scope.cancel() }
    }
}
