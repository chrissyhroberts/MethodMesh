package com.example.methodmesh.modules.espmesh

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

enum class EspMeshOutboxState { QUEUED, LOCAL_STORED, REMOTE_STORED, DELIVERED_E2E, FAILED, EXPIRED }
enum class EspMeshInboxState { STORED_ENCRYPTED, DECRYPTED, DISPATCH_PENDING, DISPATCHED, ACK_QUEUED, FAILED, EXPIRED }

data class EspMeshOutboxRecord(
    val wire: EspMeshSecureWire,
    val state: EspMeshOutboxState,
    val attempts: Int = 0,
    val detail: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val terminalOnRemoteStore: Boolean = false
)

data class EspMeshInboxRecord(
    val wire: EspMeshSecureWire,
    val state: EspMeshInboxState,
    val detail: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class EspMeshQueueSnapshot(
    val outboundTotal: Int,
    val outboundPending: Int,
    val inboundTotal: Int,
    val inboundFailed: Int,
    val delivered: Int
)

/**
 * Encrypted-only persistent phone spool for ESP mesh transport.
 *
 * There is deliberately no plaintext MethodMesh envelope in these files. The
 * writer is fail-closed: undelivered records are never evicted merely to make
 * room. Snapshot replacement keeps a recoverable .bak file so a process/power
 * interruption between renames cannot turn an accepted queue into an empty one.
 */
class EspMeshDurableStore(context: Context) {
    private val root = File(context.filesDir, "methodmesh/espmesh").apply { mkdirs() }
    private val outbox = File(root, "encrypted_outbox.jsonl")
    private val inbox = File(root, "encrypted_inbox.jsonl")
    private val lock = Any()

    fun enqueue(wire: EspMeshSecureWire, terminalOnRemoteStore: Boolean = false): EspMeshOutboxRecord = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = pruneOutboxRecords(readOutbox(), now)
        current.firstOrNull { it.wire.messageId == wire.messageId }?.let { return@synchronized it }
        check(current.size < MAX_OUTBOX) { "Encrypted ESP mesh outbox is full; no message was accepted" }
        EspMeshOutboxRecord(wire, EspMeshOutboxState.QUEUED, terminalOnRemoteStore = terminalOnRemoteStore).also { record ->
            writeOutbox(current + record)
        }
    }

    fun updateOutbox(messageId: String, state: EspMeshOutboxState, detail: String = "", incrementAttempt: Boolean = false) = synchronized(lock) {
        writeOutbox(readOutbox().map { r ->
            if (r.wire.messageId == messageId) r.copy(
                state = state,
                attempts = r.attempts + if (incrementAttempt) 1 else 0,
                detail = detail,
                updatedAt = System.currentTimeMillis()
            ) else r
        })
    }

    fun markRemoteStored(messageId: String, detail: String = "Durably stored by remote ESP") = synchronized(lock) {
        writeOutbox(readOutbox().map { r ->
            if (r.wire.messageId == messageId) r.copy(
                state = if (r.terminalOnRemoteStore) EspMeshOutboxState.DELIVERED_E2E else EspMeshOutboxState.REMOTE_STORED,
                detail = detail,
                updatedAt = System.currentTimeMillis()
            ) else r
        })
    }

    fun pendingOutbox(now: Long = System.currentTimeMillis()): List<EspMeshOutboxRecord> = synchronized(lock) {
        val current = readOutbox()
        val withExpiry = current.map { record ->
            if (record.wire.isExpired(now) && record.state != EspMeshOutboxState.DELIVERED_E2E && record.state != EspMeshOutboxState.EXPIRED) {
                record.copy(state = EspMeshOutboxState.EXPIRED, detail = "Message expired before end-to-end delivery", updatedAt = now)
            } else record
        }
        if (withExpiry != current) writeOutbox(withExpiry)
        withExpiry.filter { record ->
            when (record.state) {
                EspMeshOutboxState.QUEUED, EspMeshOutboxState.FAILED -> true
                EspMeshOutboxState.LOCAL_STORED -> now - record.updatedAt >= LOCAL_STORED_RETRY_MS
                EspMeshOutboxState.REMOTE_STORED -> now - record.updatedAt >= REMOTE_STORED_RETRY_MS
                else -> false
            }
        }.filterNot { it.wire.isExpired(now) }
    }

    /** Returns true only for a newly accepted encrypted record; false means duplicate. */
    fun recordInbound(wire: EspMeshSecureWire, state: EspMeshInboxState, detail: String = ""): Boolean = synchronized(lock) {
        val now = System.currentTimeMillis()
        val current = pruneInboxRecords(readInbox(), now)
        if (current.any { it.wire.messageId == wire.messageId }) return@synchronized false
        check(current.size < MAX_INBOX) { "Encrypted ESP mesh inbox is full; remote record was not accepted" }
        writeInbox(current + EspMeshInboxRecord(wire, state, detail))
        true
    }


    /**
     * If a previously stored ciphertext failed authentication/decryption, a later
     * retransmission with the same stable message ID but different ciphertext may
     * replace it. This recovers from storage/radio corruption without weakening
     * dedupe for successfully authenticated records.
     */
    fun replaceFailedInboundIfDifferent(wire: EspMeshSecureWire): Boolean = synchronized(lock) {
        val current = readInbox()
        val index = current.indexOfFirst { it.wire.messageId == wire.messageId }
        if (index < 0) return@synchronized false
        val existing = current[index]
        if (existing.state != EspMeshInboxState.FAILED) return@synchronized false
        if (existing.wire.nonce == wire.nonce && existing.wire.ciphertext == wire.ciphertext) return@synchronized false
        val updated = current.toMutableList()
        updated[index] = EspMeshInboxRecord(wire, EspMeshInboxState.STORED_ENCRYPTED, "Replaced failed ciphertext from a later retransmission")
        writeInbox(updated)
        true
    }

    fun updateInbox(messageId: String, state: EspMeshInboxState, detail: String = "") = synchronized(lock) {
        writeInbox(readInbox().map { record ->
            if (record.wire.messageId == messageId) record.copy(state = state, detail = detail, updatedAt = System.currentTimeMillis()) else record
        })
    }

    fun inboxState(messageId: String): EspMeshInboxState? = synchronized(lock) {
        readInbox().firstOrNull { it.wire.messageId == messageId }?.state
    }

    /**
     * Records that may need decryption/dispatch again after process restart or
     * after a previously missing/wrong E2E key is corrected. At-least-once
     * dispatch is intentional; message IDs are stable for consumer dedupe.
     */
    fun pendingInboxForProcessing(now: Long = System.currentTimeMillis()): List<EspMeshInboxRecord> = synchronized(lock) {
        readInbox().filter { record ->
            when (record.state) {
                EspMeshInboxState.STORED_ENCRYPTED,
                EspMeshInboxState.DECRYPTED,
                EspMeshInboxState.DISPATCH_PENDING,
                EspMeshInboxState.ACK_QUEUED -> true
                EspMeshInboxState.FAILED -> now - record.updatedAt >= FAILED_RETRY_MS
                else -> false
            }
        }
    }

    fun snapshot(): EspMeshQueueSnapshot = synchronized(lock) {
        val o = readOutbox(); val i = readInbox()
        EspMeshQueueSnapshot(
            outboundTotal = o.size,
            outboundPending = o.count { it.state !in setOf(EspMeshOutboxState.DELIVERED_E2E, EspMeshOutboxState.EXPIRED) },
            inboundTotal = i.size,
            inboundFailed = i.count { it.state == EspMeshInboxState.FAILED },
            delivered = o.count { it.state == EspMeshOutboxState.DELIVERED_E2E && !it.terminalOnRemoteStore }
        )
    }

    fun prune(now: Long = System.currentTimeMillis()) = synchronized(lock) {
        writeOutbox(pruneOutboxRecords(readOutbox(), now))
        writeInbox(pruneInboxRecords(readInbox(), now))
    }

    private fun pruneOutboxRecords(records: List<EspMeshOutboxRecord>, now: Long): List<EspMeshOutboxRecord> = records.filterNot {
        (it.state == EspMeshOutboxState.DELIVERED_E2E || it.state == EspMeshOutboxState.EXPIRED) && now - it.updatedAt > RETAIN_TERMINAL_MS
    }

    private fun pruneInboxRecords(records: List<EspMeshInboxRecord>, now: Long): List<EspMeshInboxRecord> = records.filterNot {
        (it.state == EspMeshInboxState.DISPATCHED || it.state == EspMeshInboxState.EXPIRED) && now - it.updatedAt > RETAIN_TERMINAL_MS
    }

    private fun readOutbox(): List<EspMeshOutboxRecord> = parseDurable(outbox) { line ->
        val j = JSONObject(line)
        EspMeshOutboxRecord(
            wire = EspMeshSecureWire.fromJson(j.getJSONObject("wire")),
            state = EspMeshOutboxState.valueOf(j.getString("state")),
            attempts = j.optInt("attempts"),
            detail = j.optString("detail"),
            updatedAt = j.optLong("updated_at"),
            terminalOnRemoteStore = j.optBoolean("terminal_on_remote_store", false)
        )
    }

    private fun readInbox(): List<EspMeshInboxRecord> = parseDurable(inbox) { line ->
        val j = JSONObject(line)
        EspMeshInboxRecord(
            wire = EspMeshSecureWire.fromJson(j.getJSONObject("wire")),
            state = EspMeshInboxState.valueOf(j.getString("state")),
            detail = j.optString("detail"),
            updatedAt = j.optLong("updated_at")
        )
    }

    /** Parse all-or-nothing. A malformed record must never be silently dropped and then overwritten. */
    private fun <T> parseDurable(file: File, parser: (String) -> T): List<T> {
        fun parse(path: File): List<T> = path.readLines().filter(String::isNotBlank).map(parser)
        if (!file.exists()) {
            val backup = backupFor(file)
            return if (backup.exists()) parse(backup) else emptyList()
        }
        return runCatching { parse(file) }.getOrElse { primaryError ->
            val backup = backupFor(file)
            if (backup.exists()) runCatching { parse(backup) }.getOrElse { backupError ->
                throw IllegalStateException("Encrypted ESP mesh queue is unreadable (${file.name}); preserving both copies", backupError)
            } else throw IllegalStateException("Encrypted ESP mesh queue is unreadable (${file.name}); preserving the file", primaryError)
        }
    }

    private fun writeOutbox(records: List<EspMeshOutboxRecord>) = atomic(outbox, records.joinToString("\n") { r -> JSONObject()
        .put("wire", r.wire.toJson())
        .put("state", r.state.name)
        .put("attempts", r.attempts)
        .put("detail", r.detail)
        .put("updated_at", r.updatedAt)
        .put("terminal_on_remote_store", r.terminalOnRemoteStore)
        .toString() } + if (records.isEmpty()) "" else "\n")

    private fun writeInbox(records: List<EspMeshInboxRecord>) = atomic(inbox, records.joinToString("\n") { r -> JSONObject()
        .put("wire", r.wire.toJson())
        .put("state", r.state.name)
        .put("detail", r.detail)
        .put("updated_at", r.updatedAt)
        .toString() } + if (records.isEmpty()) "" else "\n")

    /**
     * Same-directory two-phase replace with fsync and recoverable backup.
     * Android/Linux rename within a directory is atomic; the .bak covers the
     * small application-level interval in which the canonical name is absent.
     */
    private fun atomic(file: File, text: String) {
        val tmp = File(file.parentFile, "${file.name}.partial")
        val bak = backupFor(file)
        FileOutputStream(tmp, false).use { out ->
            out.write(text.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        if (bak.exists() && !bak.delete()) error("Could not clear stale ${bak.name}")
        if (file.exists() && !file.renameTo(bak)) error("Could not preserve ${file.name} before replacement")
        if (!tmp.renameTo(file)) {
            if (!file.exists() && bak.exists()) bak.renameTo(file)
            error("Could not persist ${file.name}")
        }
        if (bak.exists()) bak.delete()
    }

    private fun backupFor(file: File) = File(file.parentFile, "${file.name}.bak")

    companion object {
        private const val MAX_OUTBOX = 2000
        private const val MAX_INBOX = 4000
        private const val RETAIN_TERMINAL_MS = 7L * 24L * 60L * 60L * 1000L
        private const val LOCAL_STORED_RETRY_MS = 30_000L
        private const val REMOTE_STORED_RETRY_MS = 5L * 60L * 1000L
        private const val FAILED_RETRY_MS = 60_000L
    }
}
