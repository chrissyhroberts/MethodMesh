package com.example.methodmesh.core.transport

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Legacy plaintext journal. Secure providers own their ciphertext stores.
 * Existing JSONL format is retained. Corruption or queue pressure rejects writes. */
class DurableTransportStore internal constructor(
    private val root: File,
    private val maxInboxRecords: Int = 2000,
    private val maxOutboxRecords: Int = 1000
) : TransportJournal {
    constructor(context: Context) : this(File(context.filesDir, "methodmesh/transport"))
    init { require(maxInboxRecords > 0 && maxOutboxRecords > 0); check(root.isDirectory || root.mkdirs()) }
    private val inboxFile = File(root, "inbox.jsonl")
    private val outboxFile = File(root, "outbox.jsonl")
    private val lock = Any()
    private fun same(a: MethodMeshTransportEnvelope, b: MethodMeshTransportEnvelope) = a.source == b.source && a.messageId == b.messageId

    override fun hasConsumed(source: TransportEndpoint, messageId: String): Boolean = synchronized(lock) {
        readInbox().any { it.envelope.source == source && it.envelope.messageId == messageId && it.state == TransportInboxState.CONSUMED }
    }
    override fun recordInbox(envelope: MethodMeshTransportEnvelope, state: TransportInboxState, detail: String): Unit = synchronized(lock) {
        val records = readInbox().filterNot { same(it.envelope, envelope) } + TransportInboxRecord(envelope, state, detail)
        check(records.size <= maxInboxRecords) { "Transport inbox is full; prune consumed records before accepting more" }
        writeInbox(records)
    }
    override fun enqueueOutbox(envelope: MethodMeshTransportEnvelope): TransportOutboxRecord = synchronized(lock) {
        val records = readOutbox()
        val existing = records.firstOrNull { same(it.envelope, envelope) }
        if (existing != null) {
            require(existing.envelope == envelope) { "Message identity was reused with different content" }
            return@synchronized existing
        }
        check(records.size < maxOutboxRecords) { "Transport outbox is full; no message was evicted" }
        TransportOutboxRecord(envelope, TransportOutboxState.QUEUED).also { writeOutbox(records + it) }
    }
    override fun updateOutbox(envelope: MethodMeshTransportEnvelope, state: TransportOutboxState, detail: String): Unit = synchronized(lock) {
        writeOutbox(readOutbox().map { if (same(it.envelope, envelope)) it.copy(state = state, attempts = it.attempts + 1, detail = detail) else it })
    }
    override fun pendingOutbox(now: Long): List<TransportOutboxRecord> = synchronized(lock) {
        readOutbox().filter { it.state in setOf(TransportOutboxState.QUEUED, TransportOutboxState.IN_PROGRESS, TransportOutboxState.FAILED_RETRYABLE) && !it.envelope.isExpired(now) }
    }
    override fun inboxCount(): Int = synchronized(lock) { readInbox().size }
    override fun outboxCount(): Int = synchronized(lock) { readOutbox().size }
    override fun prune(now: Long): Unit = synchronized(lock) {
        // Read both before changing either so a corrupt journal is never rewritten.
        val inbox = readInbox(); val outbox = readOutbox()
        writeInbox(inbox.filterNot { it.state == TransportInboxState.CONSUMED || it.envelope.isExpired(now) })
        // SENT is only link-level evidence; retain it until destination delivery or expiry.
        writeOutbox(outbox.filterNot { it.state == TransportOutboxState.DELIVERED || it.envelope.isExpired(now) })
    }
    private fun readInbox(): List<TransportInboxRecord> = readRecords(inboxFile).map { root ->
        try { TransportInboxRecord(MethodMeshTransportEnvelope.fromJson(root.getJSONObject("envelope")), TransportInboxState.valueOf(root.getString("state")), root.optString("detail")) }
        catch (e: Exception) { throw IllegalStateException("Corrupt transport inbox; existing records preserved", e) }
    }
    private fun readOutbox(): List<TransportOutboxRecord> = readRecords(outboxFile).map { root ->
        try { TransportOutboxRecord(MethodMeshTransportEnvelope.fromJson(root.getJSONObject("envelope")), TransportOutboxState.valueOf(root.getString("state")), root.optInt("attempts"), root.optString("detail")) }
        catch (e: Exception) { throw IllegalStateException("Corrupt transport outbox; existing records preserved", e) }
    }
    private fun readRecords(file: File): List<JSONObject> {
        if (!file.exists()) return emptyList()
        return file.readLines().filter { it.isNotBlank() }.mapIndexed { index, line ->
            try { JSONObject(line) }
            catch (e: Exception) { throw IllegalStateException("Corrupt ${file.name} at record ${index + 1}; existing records preserved", e) }
        }
    }
    private fun writeInbox(records: List<TransportInboxRecord>) = atomicWrite(inboxFile, records.map {
        JSONObject().put("envelope", it.envelope.toJson()).put("state", it.state.name).put("detail", it.detail).toString()
    })
    private fun writeOutbox(records: List<TransportOutboxRecord>) = atomicWrite(outboxFile, records.map {
        JSONObject().put("envelope", it.envelope.toJson()).put("state", it.state.name).put("attempts", it.attempts).put("detail", it.detail).toString()
    })
    private fun atomicWrite(file: File, records: List<String>) {
        val tmp = File(root, "${file.name}.partial")
        val bytes = (records.joinToString("\n") + if (records.isEmpty()) "" else "\n").toByteArray(Charsets.UTF_8)
        FileOutputStream(tmp).use { it.write(bytes); it.fd.sync() }
        // Never delete the previous committed journal before its replacement.
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
