package com.example.methodmesh.core.transport

import android.content.Context
import org.json.JSONObject
import java.io.File

/** Small bounded JSON-line journal using the same file-first persistence style as core repositories. */
class DurableTransportStore(context: Context) {
    private val root = File(context.filesDir, "methodmesh/transport").apply { mkdirs() }
    private val inboxFile = File(root, "inbox.jsonl")
    private val outboxFile = File(root, "outbox.jsonl")
    private val lock = Any()

    data class InboxRecord(val envelope: MethodMeshTransportEnvelope, val state: TransportInboxState, val detail: String = "")
    data class OutboxRecord(val envelope: MethodMeshTransportEnvelope, val state: TransportOutboxState, val attempts: Int = 0, val detail: String = "")

    fun hasSeen(messageId: String): Boolean = synchronized(lock) {
        readInbox().any { it.envelope.messageId == messageId }
    }

    fun recordInbox(envelope: MethodMeshTransportEnvelope, state: TransportInboxState, detail: String = "") = synchronized(lock) {
        val records = readInbox().filterNot { it.envelope.messageId == envelope.messageId } + InboxRecord(envelope, state, detail)
        writeInbox(records.takeLast(MAX_INBOX_RECORDS))
    }

    fun enqueueOutbox(envelope: MethodMeshTransportEnvelope): OutboxRecord = synchronized(lock) {
        val record = OutboxRecord(envelope, TransportOutboxState.QUEUED)
        val records = readOutbox().filterNot { it.envelope.messageId == envelope.messageId } + record
        writeOutbox(records.takeLast(MAX_OUTBOX_RECORDS))
        record
    }

    fun updateOutbox(messageId: String, state: TransportOutboxState, attempts: Int, detail: String = "") = synchronized(lock) {
        writeOutbox(readOutbox().map { record ->
            if (record.envelope.messageId == messageId) record.copy(state = state, attempts = attempts, detail = detail) else record
        })
    }

    fun pendingOutbox(now: Long = System.currentTimeMillis()): List<OutboxRecord> = synchronized(lock) {
        readOutbox()
            .filter { it.state == TransportOutboxState.QUEUED || it.state == TransportOutboxState.FAILED_RETRYABLE }
            .filterNot { it.envelope.isExpired(now) }
    }

    fun inboxCount(): Int = synchronized(lock) { readInbox().size }
    fun outboxCount(): Int = synchronized(lock) { readOutbox().size }

    fun prune(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        writeInbox(readInbox().takeLast(MAX_INBOX_RECORDS))
        writeOutbox(readOutbox()
            .filterNot { it.envelope.isExpired(now) && it.state !in setOf(TransportOutboxState.SENT, TransportOutboxState.DELIVERED) }
            .takeLast(MAX_OUTBOX_RECORDS))
    }

    private fun readInbox(): List<InboxRecord> = readLines(inboxFile).mapNotNull { line ->
        runCatching {
            val root = JSONObject(line)
            InboxRecord(
                envelope = MethodMeshTransportEnvelope.fromJson(root.getJSONObject("envelope")),
                state = TransportInboxState.valueOf(root.getString("state")),
                detail = root.optString("detail")
            )
        }.getOrNull()
    }

    private fun readOutbox(): List<OutboxRecord> = readLines(outboxFile).mapNotNull { line ->
        runCatching {
            val root = JSONObject(line)
            OutboxRecord(
                envelope = MethodMeshTransportEnvelope.fromJson(root.getJSONObject("envelope")),
                state = TransportOutboxState.valueOf(root.getString("state")),
                attempts = root.optInt("attempts"),
                detail = root.optString("detail")
            )
        }.getOrNull()
    }

    private fun writeInbox(records: List<InboxRecord>) {
        atomicWrite(inboxFile, records.joinToString("\n") { record ->
            JSONObject().put("envelope", record.envelope.toJson()).put("state", record.state.name).put("detail", record.detail).toString()
        } + if (records.isEmpty()) "" else "\n")
    }

    private fun writeOutbox(records: List<OutboxRecord>) {
        atomicWrite(outboxFile, records.joinToString("\n") { record ->
            JSONObject().put("envelope", record.envelope.toJson()).put("state", record.state.name)
                .put("attempts", record.attempts).put("detail", record.detail).toString()
        } + if (records.isEmpty()) "" else "\n")
    }

    private fun readLines(file: File): List<String> = if (file.exists()) file.readLines().filter { it.isNotBlank() } else emptyList()

    private fun atomicWrite(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.partial")
        tmp.writeText(text)
        if (file.exists()) file.delete()
        check(tmp.renameTo(file)) { "Could not persist transport journal" }
    }

    companion object {
        private const val MAX_INBOX_RECORDS = 2000
        private const val MAX_OUTBOX_RECORDS = 1000
    }
}
