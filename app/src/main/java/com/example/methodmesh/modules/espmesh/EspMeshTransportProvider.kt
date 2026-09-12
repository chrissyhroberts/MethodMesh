package com.example.methodmesh.modules.espmesh

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.core.transport.TransportEndpoint
import com.example.methodmesh.core.transport.TransportOutboxState
import com.example.methodmesh.core.transport.TransportSendResult
import com.example.methodmesh.core.transport.TransportStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class EspMeshGatewayCandidate(val address: String, val name: String, val rssi: Int)
data class EspMeshGatewayInfo(
    val nodeId: String = "",
    val firmware: String = "",
    val provisioned: Boolean = false,
    val networkId: String = "",
    val pendingForRadio: Int = 0,
    val pendingForPhone: Int = 0,
    val spoolError: String = "",
    val lastRadioAtMs: Long = 0L
)

data class EspMeshGatewayFrameDiagnostic(
    val characteristicUuid: String = "",
    val byteLength: Int = 0,
    val utf8: String = "",
    val hex: String = "",
    val parserError: String = "",
    val receivedAtMs: Long = 0L
)

data class EspMeshTransportSnapshot(
    val enabled: Boolean = false,
    val phoneId: String = "",
    val e2eKeyId: String = "",
    val gatewayAddress: String = "",
    val gatewayName: String = "",
    val connected: Boolean = false,
    val outboxPending: Int = 0,
    val inboxTotal: Int = 0,
    val delivered: Int = 0,
    val voiceListening: Boolean = true,
    val bleMtu: Int = 0,
    val liveVoiceReady: Boolean = false,
    val lastBleAtMs: Long = 0L,
    val lastRadioAtMs: Long = 0L
)

/**
 * Persistent BLE edge for the ESP-NOW store-and-forward network.
 *
 * Security invariant: plaintext MethodMesh envelopes are encrypted before they
 * enter this provider's durable queue. ESP nodes receive only EspMeshSecureWire.
 */
class EspMeshTransportProvider private constructor(private val context: Context) : MethodMeshTransportProvider {
    override val transportId: String = TRANSPORT_ID
    override val ownsDurability: Boolean = true
    override val capabilities = com.example.methodmesh.core.transport.TransportCapabilities(
        protocol = "methodmesh.gateway/1",
        trafficClasses = setOf(
            com.example.methodmesh.core.transport.TransportTrafficClass.CONTROL,
            com.example.methodmesh.core.transport.TransportTrafficClass.LIVE,
            com.example.methodmesh.core.transport.TransportTrafficClass.DURABLE
        ),
        addressing = com.example.methodmesh.core.transport.TransportAddressing.entries.toSet(),
        maxFrameBytes = 250,
        maxObjectBytes = MAX_SECURE_WIRE_BYTES,
        supportsFragmentation = true,
        supportsStoreAndForward = true,
        endToEndProtected = true,
        cost = com.example.methodmesh.core.transport.TransportCost.UNMETERED
    )

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val crypto = EspMeshCryptoManager(context)
    private val store = EspMeshDurableStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val mutableStatus = MutableStateFlow(TransportStatus(false, false, "ESP mesh transport is paused"))
    override val status: StateFlow<TransportStatus> = mutableStatus
    private val mutableGatewayInfo = MutableStateFlow(EspMeshGatewayInfo())
    val gatewayInfo: StateFlow<EspMeshGatewayInfo> = mutableGatewayInfo
    private val mutableRecentInbound = MutableStateFlow<List<MethodMeshTransportEnvelope>>(emptyList())
    val recentInbound: StateFlow<List<MethodMeshTransportEnvelope>> = mutableRecentInbound
    private val mutableLastInvalidFrame = MutableStateFlow<EspMeshGatewayFrameDiagnostic?>(null)
    val lastInvalidFrame: StateFlow<EspMeshGatewayFrameDiagnostic?> = mutableLastInvalidFrame
    private val mutableSnapshot = MutableStateFlow(EspMeshTransportSnapshot())
    val snapshot: StateFlow<EspMeshTransportSnapshot> = mutableSnapshot
    private val mutableVoiceListening = MutableStateFlow(prefs.getBoolean(KEY_VOICE_LISTENING, true))
    val voiceListening: StateFlow<Boolean> = mutableVoiceListening
    private val mutableLiveVoicePackets = MutableSharedFlow<ByteArray>(
        extraBufferCapacity = 96,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val liveVoicePackets: SharedFlow<ByteArray> = mutableLiveVoicePackets

    private var inbound: (suspend (MethodMeshTransportEnvelope) -> Unit)? = null
    private var gatt: BluetoothGatt? = null
    private var uplink: BluetoothGattCharacteristic? = null
    private var downlink: BluetoothGattCharacteristic? = null
    private var scanCallback: ScanCallback? = null
    private var reconnectAttempt = 0
    private var nextReconnectAtMs = 0L
    private var maintenanceJob: Job? = null
    private var negotiatedMtu = DEFAULT_MTU
    private var lastBleAtMs = 0L
    private var lastRadioAtMs = 0L
    private var lastSyncSentAtMs = 0L
    private var lastQueueSnapshot = EspMeshQueueSnapshot(0, 0, 0, 0, 0)

    private val bleReassembler = EspMeshBlePacketCodec.Reassembler()
    private val writeLock = Any()
    private val highPriorityWriteQueue = ArrayDeque<ByteArray>()
    private val liveVoiceWriteQueue = ArrayDeque<ByteArray>()
    private val normalWriteQueue = ArrayDeque<ByteArray>()
    private var writeInFlight = false

    override suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit) {
        inbound = onEnvelope
        refreshSnapshot()
        if (!isPersistentEnabled()) {
            mutableStatus.value = TransportStatus(false, false, "ESP mesh transport is paused")
            return
        }
        ensureServiceRunning(context)
        ensureMaintenanceLoop()
        if (gatewayAddress().isBlank()) mutableStatus.value = TransportStatus(false, false, "No ESP mesh gateway selected")
        else connectConfigured()
    }

    override suspend fun stop() {
        inbound = null
        maintenanceJob?.cancel(); maintenanceJob = null
        scanCallback?.let { runCatching { bluetoothAdapter()?.bluetoothLeScanner?.stopScan(it) } }
        scanCallback = null
        mainHandler.removeCallbacksAndMessages(null)
        closeGatt()
        mutableStatus.value = TransportStatus(isPersistentEnabled(), false, if (isPersistentEnabled()) "Transport service stopped" else "ESP mesh transport is paused")
        refreshSnapshot()
    }

    /** Durable acceptance: success means ciphertext is safely in the phone outbox, not end-to-end delivery. */
    override suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult {
        val wire = runCatching { crypto.encrypt(envelope, phoneId()) }.getOrElse {
            return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, it.message ?: "E2E encryption failed")
        }
        val wireBytes = wire.toJson().toString().toByteArray(Charsets.UTF_8).size
        if (wireBytes > MAX_SECURE_WIRE_BYTES) {
            return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, "Encrypted ESP mesh wire is $wireBytes bytes; limit is $MAX_SECURE_WIRE_BYTES bytes")
        }
        val queued = runCatching { store.enqueue(wire, terminalOnRemoteStore = envelope.messageType == E2E_ACK_TYPE) }
        if (queued.isFailure) {
            refreshSnapshot()
            return TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, queued.exceptionOrNull()?.message ?: "Encrypted mesh outbox rejected the message")
        }
        refreshSnapshot()
        if (status.value.connected) sendWire(wire)
        return TransportSendResult(TransportOutboxState.QUEUED, "Encrypted and durably queued for mesh delivery", com.example.methodmesh.core.transport.TransportAcceptance.LOCAL_DURABLE)
    }

    @SuppressLint("MissingPermission")
    fun scan(onCandidate: (EspMeshGatewayCandidate) -> Unit, onFinished: () -> Unit = {}) {
        val scanner = bluetoothAdapter()?.bluetoothLeScanner ?: run { onFinished(); return }
        scanCallback?.let { runCatching { scanner.stopScan(it) } }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName.orEmpty().ifBlank { runCatching { device.name }.getOrNull().orEmpty() }
                if (name.contains("MethodMesh", true) || result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true) {
                    onCandidate(EspMeshGatewayCandidate(device.address, name.ifBlank { "ESP mesh gateway" }, result.rssi))
                }
            }
            override fun onScanFailed(errorCode: Int) {
                mutableStatus.value = TransportStatus(true, false, "BLE scan failed ($errorCode)")
                onFinished()
            }
        }
        scanCallback = callback
        scanner.startScan(callback)
        mainHandler.postDelayed({
            runCatching { scanner.stopScan(callback) }
            if (scanCallback === callback) scanCallback = null
            onFinished()
        }, SCAN_DURATION_MS)
    }

    fun provision(candidate: EspMeshGatewayCandidate) {
        prefs.edit().putString(KEY_ADDRESS, candidate.address).putString(KEY_NAME, candidate.name).putBoolean(KEY_ENABLED, true).apply()
        refreshSnapshot()
        ensureServiceRunning(context)
        ensureMaintenanceLoop()
        connect(candidate.address)
    }

    /** E2E key is stored only on Android; network ID/key/token are the only secrets sent to the ESP. */
    fun configureNetwork(
        networkId: String,
        networkKey: String,
        e2eGroupKey: String,
        provisioningToken: String,
        peers: List<String> = emptyList()
    ): TransportSendResult {
        if (networkId.isBlank() || networkKey.isBlank()) return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, "Network ID and network key are required")
        val keyResult = if (e2eGroupKey.isBlank() && crypto.hasGroupKey()) Result.success(crypto.keyId())
        else runCatching { crypto.importAndStore(e2eGroupKey) }
        if (keyResult.isFailure) return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, keyResult.exceptionOrNull()?.message ?: "Invalid E2E group key")
        scope.launch { processPendingEncryptedInbox() }
        refreshSnapshot()
        val frame = EspMeshBridgeFrame("CONFIG", body = JSONObject()
            .put("network_id", networkId.trim())
            .put("network_key", networkKey.trim())
            .put("provisioning_token", provisioningToken.trim())
            .put("phone_id", phoneId())
            .put("peers", org.json.JSONArray(peers)))
        return if (enqueueBridgeFrame(frame)) TransportSendResult(TransportOutboxState.SENT, "Network settings sent; E2E key retained only on this phone")
        else TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "Gateway is not connected")
    }

    fun generateE2eGroupKey(): String = crypto.generateAndStore().also { refreshSnapshot() }
    fun exportE2eGroupKey(): String = crypto.exportGroupKey()
    fun e2eKeyId(): String = crypto.keyId()
    fun hasE2eKey(): Boolean = crypto.hasGroupKey()

    fun isVoiceListening(): Boolean = mutableVoiceListening.value

    fun setVoiceListening(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_VOICE_LISTENING, enabled).apply()
        mutableVoiceListening.value = enabled
        if (status.value.connected) sendVoiceListenState()
        refreshSnapshot()
    }

    /**
     * Ephemeral live-voice path. Voice packets are already phone-to-phone E2E
     * ciphertext and are never written to either durable phone queue.
     */
    fun sendLiveVoicePacket(packet: ByteArray, ttl: Int = DEFAULT_LIVE_VOICE_TTL): Boolean {
        if (!isPersistentEnabled() || !status.value.connected) return false
        if (!crypto.hasGroupKey()) return false
        if (packet.size !in EspMeshLiveVoicePacket.HEADER_BYTES + EspMeshLiveVoicePacket.GCM_TAG_BYTES..EspMeshLiveVoicePacket.MAX_PACKET_BYTES) return false
        val body = JSONObject()
            .put("packet", Base64.encodeToString(packet, Base64.NO_WRAP))
            .put("ttl", ttl.coerceIn(1, MAX_LIVE_VOICE_TTL))
        return enqueueBridgeFrame(
            EspMeshBridgeFrame("LIVE_VOICE", body = body),
            highPriority = true,
            requireSinglePacket = true
        )
    }

    fun phoneId(): String {
        prefs.getString(KEY_PHONE_ID, null)?.let { if (it.isNotBlank()) return it }
        return UUID.randomUUID().toString().also { prefs.edit().putString(KEY_PHONE_ID, it).apply() }
    }

    fun gatewayAddress(): String = prefs.getString(KEY_ADDRESS, "").orEmpty()
    fun gatewayName(): String = prefs.getString(KEY_NAME, "").orEmpty()
    fun isPersistentEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    fun setPersistentEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            ensureServiceRunning(context); ensureMaintenanceLoop(); if (gatewayAddress().isNotBlank() && !status.value.connected) connectConfigured()
        } else {
            closeGatt(); mutableStatus.value = TransportStatus(false, false, "ESP mesh transport is paused")
            context.stopService(android.content.Intent(context, EspMeshGatewayService::class.java))
        }
        refreshSnapshot()
    }

    fun queueSnapshot(): EspMeshQueueSnapshot = runCatching { store.snapshot() }
        .onSuccess { lastQueueSnapshot = it }
        .getOrElse { error ->
            mutableStatus.value = status.value.copy(detail = "Encrypted mesh queue requires recovery: ${error.message.orEmpty()}")
            lastQueueSnapshot
        }

    private fun ensureMaintenanceLoop() {
        if (maintenanceJob?.isActive == true) return
        maintenanceJob = scope.launch {
            while (isActive) {
                runCatching {
                    if (isPersistentEnabled()) {
                        if (!status.value.connected && gatewayAddress().isNotBlank() && gatt == null && System.currentTimeMillis() >= nextReconnectAtMs) connectConfigured()
                        processPendingEncryptedInbox()
                        if (status.value.connected) {
                            flushEncryptedOutbox()
                            val now = System.currentTimeMillis()
                            if (now - lastSyncSentAtMs >= STEADY_SYNC_INTERVAL_MS) sendSyncRequest()
                        }
                        store.prune()
                        refreshSnapshot()
                    }
                }.onFailure { Log.w(TAG, "Mesh maintenance iteration failed", it) }
                delay(MAINTENANCE_INTERVAL_MS)
            }
        }
    }

    private fun flushEncryptedOutbox() {
        if (!status.value.connected) return
        store.pendingOutbox().take(MAX_FLUSH_PER_TICK).forEach { sendWire(it.wire) }
    }

    private fun sendWire(wire: EspMeshSecureWire) {
        if (!enqueueBridgeFrame(EspMeshBridgeFrame("DATA", requestId = wire.messageId, wire = wire), highPriority = false)) return
        store.updateOutbox(wire.messageId, EspMeshOutboxState.QUEUED, "Submitted to BLE gateway", incrementAttempt = true)
        refreshSnapshot()
    }

    private fun sendDeliveryAck(original: MethodMeshTransportEnvelope, originPhoneId: String) {
        if (originPhoneId.isBlank()) return
        val ack = MethodMeshTransportEnvelope(
            source = TransportEndpoint("mesh-phone", phoneId()),
            destination = TransportEndpoint("mesh-phone", originPhoneId),
            messageType = E2E_ACK_TYPE,
            moduleId = "espmesh",
            capabilityId = null,
            correlationId = original.messageId,
            payloadType = "text/plain",
            payload = original.messageId,
            metadata = mapOf("ack_for" to original.messageId)
        )
        scope.launch { send(ack) }
    }

    private fun handleInboundWire(wire: EspMeshSecureWire) {
        lastRadioAtMs = System.currentTimeMillis()
        if (wire.destination.kind == "mesh-phone" && wire.destination.id != phoneId()) {
            // Keep the remote ESP copy: this ciphertext belongs to another phone.
            // Do not persist/decrypt/ack it on this handset even if both phones
            // intentionally share the same field-group E2E key.
            setProtocolError("Gateway offered a direct message addressed to another phone")
            refreshSnapshot()
            return
        }
        val stateBefore = store.inboxState(wire.messageId)
        var firstSeen = runCatching { store.recordInbound(wire, EspMeshInboxState.STORED_ENCRYPTED) }.getOrElse { error ->
            // Do NOT tell the ESP to release its persistent copy if the phone could
            // not durably accept the ciphertext.
            setProtocolError(error.message ?: "Encrypted phone inbox rejected the message")
            refreshSnapshot()
            return
        }
        if (!firstSeen && stateBefore == EspMeshInboxState.FAILED) {
            firstSeen = runCatching { store.replaceFailedInboundIfDifferent(wire) }.getOrDefault(false)
        }
        // Once ciphertext is durable on the phone, the ESP may release its local
        // phone-bound spool copy. Decryption/dispatch can safely happen later.
        enqueueBridgeFrame(EspMeshBridgeFrame("PHONE_STORED", requestId = wire.messageId, body = JSONObject().put("message_id", wire.messageId)))

        val shouldProcess = firstSeen || stateBefore in setOf(
            EspMeshInboxState.STORED_ENCRYPTED,
            EspMeshInboxState.DECRYPTED,
            EspMeshInboxState.DISPATCH_PENDING,
            EspMeshInboxState.ACK_QUEUED,
            EspMeshInboxState.FAILED
        )
        if (shouldProcess) processEncryptedInboxRecord(wire)
        else {
            // A duplicate after successful dispatch still gets a fresh authenticated
            // E2E ACK: the prior ACK may have been lost on the return path.
            runCatching { crypto.decrypt(wire) }.getOrNull()?.takeUnless { it.messageType == E2E_ACK_TYPE }?.let { sendDeliveryAck(it, wire.originPhoneId) }
        }
        refreshSnapshot()
    }

    private fun processPendingEncryptedInbox() {
        store.pendingInboxForProcessing().take(MAX_INBOX_REPLAY_PER_TICK).forEach { record ->
            processEncryptedInboxRecord(record.wire)
        }
    }

    private fun processEncryptedInboxRecord(wire: EspMeshSecureWire) {
        val envelope = runCatching { crypto.decrypt(wire) }.getOrElse { error ->
            val state = if (wire.isExpired()) EspMeshInboxState.EXPIRED else EspMeshInboxState.FAILED
            store.updateInbox(wire.messageId, state, error.message.orEmpty())
            mutableStatus.value = status.value.copy(detail = if (state == EspMeshInboxState.EXPIRED)
                "Expired encrypted mesh message retained for audit/pruning"
            else "Encrypted message retained but not decrypted: ${error.message.orEmpty()}")
            return
        }
        if (envelope.messageType == E2E_ACK_TYPE) {
            val ackFor = envelope.metadata["ack_for"].orEmpty().ifBlank { envelope.payload }
            if (ackFor.isNotBlank()) store.updateOutbox(ackFor, EspMeshOutboxState.DELIVERED_E2E, "Authenticated delivery ACK from ${envelope.source.id}")
            store.updateInbox(wire.messageId, EspMeshInboxState.DISPATCHED, "Authenticated E2E delivery ACK processed")
            return
        }

        store.updateInbox(wire.messageId, EspMeshInboxState.DECRYPTED, "Authenticated and ready for in-memory dispatch")
        if (mutableRecentInbound.value.none { it.messageId == envelope.messageId }) {
            mutableRecentInbound.value = (listOf(envelope) + mutableRecentInbound.value).take(MAX_RECENT_INBOUND)
        }

        // The ACK proves that the destination phone possesses the E2E key and
        // authenticated the ciphertext. Application consumers remain at-least-once
        // and may complete independently after the durable receipt boundary.
        sendDeliveryAck(envelope, wire.originPhoneId)
        store.updateInbox(wire.messageId, EspMeshInboxState.ACK_QUEUED, "Authenticated delivery ACK queued")

        // The backwards-compatible mesh message method is a Workbench transport
        // test harness, not an application endpoint. Recording it in recentInbound
        // is its complete local consumption path and must not clog the durable inbox.
        if (envelope.moduleId == "espmesh" && envelope.capabilityId == As100EspMeshMessageMethod.ID) {
            store.updateInbox(wire.messageId, EspMeshInboxState.DISPATCHED, "Transport test message received")
            refreshSnapshot()
            return
        }

        scope.launch {
            val receiver = inbound
            if (receiver == null) {
                store.updateInbox(wire.messageId, EspMeshInboxState.DISPATCH_PENDING, "Transport runtime is not currently available for dispatch")
            } else {
                runCatching { receiver.invoke(envelope) }
                    .onSuccess { store.updateInbox(wire.messageId, EspMeshInboxState.DISPATCHED, "Dispatched in memory") }
                    .onFailure { store.updateInbox(wire.messageId, EspMeshInboxState.DISPATCH_PENDING, it.message.orEmpty()) }
            }
            refreshSnapshot()
        }
    }

    private fun handleBridgeFrame(frame: EspMeshBridgeFrame) {
        mutableLastInvalidFrame.value = null
        when (frame.kind) {
            "HELLO_ACK", "CONFIG_ACK", "SYNC_ACK" -> {
                val body = frame.body
                val old = mutableGatewayInfo.value
                val info = EspMeshGatewayInfo(
                    nodeId = body.optString("node_id", old.nodeId),
                    firmware = body.optString("firmware", old.firmware),
                    provisioned = body.optBoolean("provisioned", old.provisioned),
                    networkId = body.optString("network_id", old.networkId),
                    pendingForRadio = body.optInt("pending_for_radio", old.pendingForRadio),
                    pendingForPhone = body.optInt("pending_for_phone", old.pendingForPhone),
                    spoolError = body.optString("spool_error", old.spoolError),
                    lastRadioAtMs = body.optLong("last_radio_at_ms", old.lastRadioAtMs)
                )
                mutableGatewayInfo.value = info
                if (info.lastRadioAtMs > 0) lastRadioAtMs = maxOf(lastRadioAtMs, info.lastRadioAtMs)
                mutableStatus.value = status.value.copy(detail = when (frame.kind) {
                    "CONFIG_ACK" -> "Mesh network provisioned"
                    "SYNC_ACK" -> "Gateway synchronized"
                    else -> "Gateway handshake complete"
                })
                if (frame.kind == "HELLO_ACK") {
                    sendVoiceListenState()
                    sendSyncRequest()
                }
                flushEncryptedOutbox()
            }
            "DATA" -> frame.wire?.let(::handleInboundWire)
                ?: setProtocolError("DATA frame did not contain encrypted wire data")
            "LIVE_VOICE_RX" -> {
                lastRadioAtMs = System.currentTimeMillis()
                val encoded = frame.body.optString("packet")
                if (encoded.isBlank()) setProtocolError("LIVE_VOICE_RX frame did not contain a voice packet")
                else runCatching { Base64.decode(encoded, Base64.DEFAULT) }
                    .onSuccess { packet ->
                        if (packet.size <= EspMeshLiveVoicePacket.MAX_PACKET_BYTES) mutableLiveVoicePackets.tryEmit(packet)
                        else setProtocolError("LIVE_VOICE_RX packet exceeded the bounded live-voice size")
                    }
                    .onFailure { setProtocolError("LIVE_VOICE_RX packet was not valid base64") }
            }
            "VOICE_LISTEN_ACK" -> {
                mutableStatus.value = status.value.copy(detail = if (frame.body.optBoolean("enabled", false)) "Gateway voice listening enabled" else "Gateway voice listening muted")
            }
            "LOCAL_STORED" -> {
                frame.body.optString("message_id").takeIf(String::isNotBlank)?.let { store.updateOutbox(it, EspMeshOutboxState.LOCAL_STORED, "Durably stored by local ESP") }
                refreshSnapshot()
            }
            "REMOTE_STORED" -> {
                lastRadioAtMs = System.currentTimeMillis()
                frame.body.optString("message_id").takeIf(String::isNotBlank)?.let { store.markRemoteStored(it) }
                refreshSnapshot()
            }
            "RADIO_ERROR", "ERROR" -> setProtocolError(frame.body.optString("error", "Gateway reported an error"))
        }
        refreshSnapshot()
    }

    private fun setProtocolError(detail: String) { mutableStatus.value = status.value.copy(detail = detail) }

    @SuppressLint("MissingPermission")
    private fun connectConfigured() = connect(gatewayAddress())

    @SuppressLint("MissingPermission")
    private fun connect(address: String) {
        if (!isPersistentEnabled() || address.isBlank()) return
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            mutableStatus.value = TransportStatus(true, false, "Bluetooth connect permission is required; encrypted queues retained")
            scheduleReconnect(); return
        }
        val adapter = bluetoothAdapter()
        if (adapter?.isEnabled != true) { mutableStatus.value = TransportStatus(true, false, "Bluetooth is off; encrypted queues retained"); scheduleReconnect(); return }
        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) { mutableStatus.value = TransportStatus(true, false, "Configured gateway is not currently addressable"); scheduleReconnect(); return }
        closeGatt()
        mutableStatus.value = TransportStatus(true, false, "Connecting to ${gatewayName().ifBlank { address }}")
        val created = runCatching {
            if (Build.VERSION.SDK_INT >= 26) device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            else device.connectGatt(context, false, callback)
        }.getOrElse { error ->
            mutableStatus.value = TransportStatus(true, false, "Gateway connect failed: ${error.message.orEmpty()}")
            scheduleReconnect(); null
        }
        gatt = created
        if (created != null) {
            mainHandler.postDelayed({
                if (this.gatt === created && !status.value.connected) {
                    runCatching { created.disconnect() }; runCatching { created.close() }
                    if (this.gatt === created) this.gatt = null
                    mutableStatus.value = TransportStatus(true, false, "Gateway connect timed out; encrypted queues retained")
                    scheduleReconnect(); refreshSnapshot()
                }
            }, CONNECT_ATTEMPT_TIMEOUT_MS)
        }
        refreshSnapshot()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, statusCode: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED && statusCode == BluetoothGatt.GATT_SUCCESS) {
                reconnectAttempt = 0; nextReconnectAtMs = 0L; lastBleAtMs = System.currentTimeMillis()
                mutableStatus.value = TransportStatus(true, true, "Gateway connected")
                refreshSnapshot()
                if (!gatt.requestMtu(PREFERRED_MTU)) { negotiatedMtu = DEFAULT_MTU; gatt.discoverServices() }
            } else {
                mutableStatus.value = TransportStatus(isPersistentEnabled(), false, "Gateway unavailable ($statusCode); queues retained")
                gatt.close(); if (this@EspMeshTransportProvider.gatt === gatt) this@EspMeshTransportProvider.gatt = null
                uplink = null; downlink = null; lastSyncSentAtMs = 0L; resetWriteQueue(); scheduleReconnect(); refreshSnapshot()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, statusCode: Int) {
            negotiatedMtu = if (statusCode == BluetoothGatt.GATT_SUCCESS) mtu else DEFAULT_MTU
            mutableStatus.value = mutableStatus.value.copy(detail = "Gateway connected · MTU $negotiatedMtu")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, statusCode: Int) {
            if (statusCode != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID)
            uplink = service?.getCharacteristic(UPLINK_UUID)
            downlink = service?.getCharacteristic(DOWNLINK_UUID)
            val down = downlink
            if (uplink == null || down == null) {
                mutableStatus.value = TransportStatus(true, false, "Gateway protocol v2 service not found")
                return
            }
            gatt.setCharacteristicNotification(down, true)
            val descriptor = down.getDescriptor(CLIENT_CONFIG_UUID)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                else writeLegacyDescriptor(gatt, descriptor)
            } else sendHello()
            refreshSnapshot()
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, statusCode: Int) {
            if (descriptor.uuid == CLIENT_CONFIG_UUID && statusCode == BluetoothGatt.GATT_SUCCESS) sendHello()
            else if (descriptor.uuid == CLIENT_CONFIG_UUID) mutableStatus.value = status.value.copy(detail = "Could not enable gateway notifications ($statusCode)")
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != DOWNLINK_UUID) return
            lastBleAtMs = System.currentTimeMillis()
            val complete = runCatching { bleReassembler.accept(value) }.getOrElse { error ->
                captureInvalid(value, characteristic.uuid, error); null
            } ?: return
            handleRawBridgeFrame(complete, characteristic.uuid)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, statusCode: Int) {
            if (characteristic.uuid != UPLINK_UUID) return
            synchronized(writeLock) { writeInFlight = false }
            if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                // Never continue a partially failed fragmented frame. Durable
                // DATA remains authoritative in the encrypted phone store;
                // live voice is intentionally best-effort and may be dropped.
                failBleLink(gatt, "BLE gateway write failed ($statusCode); durable queues retained and the link will reconnect")
                return
            }
            pumpWriteQueue()
        }
    }

    private fun handleRawBridgeFrame(bytes: ByteArray, characteristicUuid: UUID) {
        val text = bytes.toString(Charsets.UTF_8)
        try { handleBridgeFrame(EspMeshBridgeFrame.fromJson(JSONObject(text))) }
        catch (error: Exception) { captureInvalid(bytes, characteristicUuid, error) }
    }

    private fun captureInvalid(bytes: ByteArray, uuid: UUID, error: Throwable) {
        val d = EspMeshGatewayFrameDiagnostic(
            characteristicUuid = uuid.toString(), byteLength = bytes.size, utf8 = bytes.toString(Charsets.UTF_8),
            hex = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) },
            parserError = "${error::class.java.simpleName}: ${error.message.orEmpty()}", receivedAtMs = System.currentTimeMillis()
        )
        mutableLastInvalidFrame.value = d
        mutableStatus.value = status.value.copy(detail = "Invalid gateway frame · ${d.parserError}")
        Log.e(TAG, "Invalid gateway frame characteristic=${d.characteristicUuid} bytes=${d.byteLength} utf8=${d.utf8} hex=${d.hex}", error)
    }

    private fun sendHello() {
        enqueueBridgeFrame(EspMeshBridgeFrame("HELLO", body = JSONObject()
            .put("client", "MethodMesh Android")
            .put("phone_id", phoneId())
            .put("transport", TRANSPORT_ID)
            .put("protocol_version", EspMeshBridgeFrame.VERSION)
            .put("e2e_key_id", crypto.keyId())))
        mainHandler.postDelayed({
            if (status.value.connected && mutableGatewayInfo.value.firmware.isBlank()) {
                mutableStatus.value = status.value.copy(detail = "Gateway did not answer protocol v2; install ESP-NOW firmware 0.4.0 or newer")
            }
        }, HANDSHAKE_TIMEOUT_MS)
    }

    private fun sendSyncRequest() {
        if (!status.value.connected) return
        if (enqueueBridgeFrame(EspMeshBridgeFrame("SYNC_REQUEST", body = JSONObject()
                .put("phone_id", phoneId()).put("e2e_key_id", crypto.keyId())))) {
            lastSyncSentAtMs = System.currentTimeMillis()
        }
    }

    private fun sendVoiceListenState() {
        enqueueBridgeFrame(
            EspMeshBridgeFrame(
                "VOICE_LISTEN",
                body = JSONObject().put("enabled", mutableVoiceListening.value)
            ),
            highPriority = true
        )
    }

    private fun enqueueBridgeFrame(
        frame: EspMeshBridgeFrame,
        highPriority: Boolean = true,
        requireSinglePacket: Boolean = false
    ): Boolean {
        val connection = gatt ?: return false
        val characteristic = uplink ?: return false
        if (!status.value.connected) return false
        val maxPacket = (negotiatedMtu - 3).coerceAtMost(MAX_BLE_PACKET_BYTES)
        if (maxPacket < MIN_SUPPORTED_PACKET_BYTES) {
            mutableStatus.value = status.value.copy(detail = "Gateway MTU $negotiatedMtu is too small for mesh framing")
            return false
        }
        val encoded = frame.encode()
        if (requireSinglePacket && encoded.size > maxPacket) {
            mutableStatus.value = status.value.copy(
                detail = "BLE MTU $negotiatedMtu is too small for low-latency live voice; reconnect the gateway"
            )
            return false
        }
        val packets = runCatching { EspMeshBlePacketCodec.fragment(encoded, maxPacket) }.getOrElse {
            mutableStatus.value = status.value.copy(detail = it.message.orEmpty()); return false
        }
        synchronized(writeLock) {
            val target = when {
                frame.kind == "LIVE_VOICE" -> liveVoiceWriteQueue
                highPriority -> highPriorityWriteQueue
                else -> normalWriteQueue
            }
            if (frame.kind == "LIVE_VOICE") {
                // Live speech must not turn a temporarily slow BLE link into a
                // delayed audio replay. Keep only a short real-time horizon.
                while (target.size >= MAX_LIVE_BLE_QUEUE_PACKETS) target.removeFirst()
            }
            packets.forEach(target::addLast)
        }
        pumpWriteQueue(connection, characteristic)
        return true
    }

    @SuppressLint("MissingPermission")
    private fun pumpWriteQueue(connection: BluetoothGatt? = gatt, characteristic: BluetoothGattCharacteristic? = uplink) {
        val g = connection ?: return; val c = characteristic ?: return
        val next = synchronized(writeLock) {
            if (writeInFlight) return
            val queue = when {
                highPriorityWriteQueue.isNotEmpty() -> highPriorityWriteQueue
                liveVoiceWriteQueue.isNotEmpty() -> liveVoiceWriteQueue
                normalWriteQueue.isNotEmpty() -> normalWriteQueue
                else -> return
            }
            writeInFlight = true
            queue.removeFirst()
        }
        val accepted = if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, next, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        else writeLegacyCharacteristic(g, c, next)
        if (!accepted) {
            synchronized(writeLock) { writeInFlight = false }
            failBleLink(g, "BLE gateway rejected a write; durable queues retained and the link will reconnect")
        }
    }

    private fun resetWriteQueue() = synchronized(writeLock) {
        highPriorityWriteQueue.clear()
        liveVoiceWriteQueue.clear()
        normalWriteQueue.clear()
        writeInFlight = false
    }

    @SuppressLint("MissingPermission")
    private fun failBleLink(connection: BluetoothGatt, detail: String) {
        mutableStatus.value = TransportStatus(isPersistentEnabled(), false, detail)
        resetWriteQueue()
        runCatching { connection.disconnect() }
        runCatching { connection.close() }
        if (gatt === connection) gatt = null
        uplink = null
        downlink = null
        negotiatedMtu = DEFAULT_MTU
        lastSyncSentAtMs = 0L
        scheduleReconnect()
        refreshSnapshot()
    }

    @Suppress("DEPRECATION")
    private fun writeLegacyDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; gatt.writeDescriptor(descriptor)
    }
    @Suppress("DEPRECATION")
    private fun writeLegacyCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        characteristic.value = bytes; return gatt.writeCharacteristic(characteristic)
    }

    private fun closeGatt() {
        runCatching { gatt?.disconnect() }; runCatching { gatt?.close() }
        gatt = null; uplink = null; downlink = null; negotiatedMtu = DEFAULT_MTU; lastSyncSentAtMs = 0L; resetWriteQueue()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private fun scheduleReconnect() {
        if (!isPersistentEnabled() || gatewayAddress().isBlank() || inbound == null) return
        val delayMs = ((1L shl reconnectAttempt.coerceAtMost(6)) * 1000L).coerceAtMost(MAX_RECONNECT_MS)
        reconnectAttempt += 1
        nextReconnectAtMs = System.currentTimeMillis() + delayMs
    }

    private fun refreshSnapshot() {
        val q = queueSnapshot()
        mutableSnapshot.value = EspMeshTransportSnapshot(
            enabled = isPersistentEnabled(), phoneId = phoneId(), e2eKeyId = crypto.keyId(), gatewayAddress = gatewayAddress(),
            gatewayName = gatewayName(), connected = status.value.connected, outboxPending = q.outboundPending,
            inboxTotal = q.inboundTotal, delivered = q.delivered, voiceListening = mutableVoiceListening.value,
            bleMtu = negotiatedMtu,
            liveVoiceReady = status.value.connected && (negotiatedMtu - 3).coerceAtMost(MAX_BLE_PACKET_BYTES) >= LIVE_VOICE_MIN_ATT_BYTES,
            lastBleAtMs = lastBleAtMs, lastRadioAtMs = lastRadioAtMs
        )
    }

    companion object {
        const val TRANSPORT_ID = "espmesh.gateway"
        const val E2E_ACK_TYPE = "MESH_DELIVERY_ACK"
        private const val TAG = "MethodMeshEspMesh"
        private const val PREFS = "methodmesh_espmesh_gateway"
        private const val KEY_ADDRESS = "gateway_address"
        private const val KEY_NAME = "gateway_name"
        private const val KEY_ENABLED = "transport_enabled"
        private const val KEY_PHONE_ID = "phone_id"
        private const val KEY_VOICE_LISTENING = "voice_listening"
        private const val PREFERRED_MTU = 517
        private const val DEFAULT_MTU = 23
        private const val MAX_BLE_PACKET_BYTES = 480
        private const val MIN_SUPPORTED_PACKET_BYTES = 180
        private const val MAX_SECURE_WIRE_BYTES = 32 * 1024
        private const val DEFAULT_LIVE_VOICE_TTL = 2
        private const val MAX_LIVE_VOICE_TTL = 4
        private const val LIVE_VOICE_MIN_ATT_BYTES = 430
        private const val MAX_LIVE_BLE_QUEUE_PACKETS = 12
        private const val MAX_RECENT_INBOUND = 50
        private const val MAX_FLUSH_PER_TICK = 25
        private const val MAX_INBOX_REPLAY_PER_TICK = 25
        private const val SCAN_DURATION_MS = 12_000L
        private const val MAINTENANCE_INTERVAL_MS = 5_000L
        private const val STEADY_SYNC_INTERVAL_MS = 60_000L
        private const val HANDSHAKE_TIMEOUT_MS = 5_000L
        private const val MAX_RECONNECT_MS = 60_000L
        private const val CONNECT_ATTEMPT_TIMEOUT_MS = 25_000L
        val SERVICE_UUID: UUID = UUID.fromString("b6f2a910-9b8f-4f4e-9a1f-4f37a0010000")
        val UPLINK_UUID: UUID = UUID.fromString("b6f2a911-9b8f-4f4e-9a1f-4f37a0010000")
        val DOWNLINK_UUID: UUID = UUID.fromString("b6f2a912-9b8f-4f4e-9a1f-4f37a0010000")
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        @Volatile private var instance: EspMeshTransportProvider? = null
        fun create(context: Context): EspMeshTransportProvider = instance ?: synchronized(this) {
            instance ?: EspMeshTransportProvider(context.applicationContext).also { instance = it }
        }
        fun get(context: Context): EspMeshTransportProvider = create(context)
        fun persistentEnabled(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)
        fun ensureServiceRunning(context: Context): Boolean {
            if (!persistentEnabled(context)) return false
            return runCatching {
                ContextCompat.startForegroundService(context, android.content.Intent(context, EspMeshGatewayService::class.java))
                true
            }.getOrDefault(false)
        }
    }
}
