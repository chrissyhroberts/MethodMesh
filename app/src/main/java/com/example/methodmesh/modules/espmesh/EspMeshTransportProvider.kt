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
import com.example.methodmesh.core.transport.MethodMeshTransportEnvelope
import com.example.methodmesh.core.transport.MethodMeshTransportProvider
import com.example.methodmesh.core.transport.TransportOutboxState
import com.example.methodmesh.core.transport.TransportSendResult
import com.example.methodmesh.core.transport.TransportStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

data class EspMeshGatewayCandidate(val address: String, val name: String, val rssi: Int)

/** Android BLE gateway adapter. ESP-NOW framing remains firmware-owned. */
class EspMeshTransportProvider private constructor(private val context: Context) : MethodMeshTransportProvider {
    override val transportId: String = TRANSPORT_ID
    private val mutableStatus = MutableStateFlow(TransportStatus(false, false, "No ESP mesh gateway provisioned"))
    override val status: StateFlow<TransportStatus> = mutableStatus
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var inbound: (suspend (MethodMeshTransportEnvelope) -> Unit)? = null
    private var gatt: BluetoothGatt? = null
    private var uplink: BluetoothGattCharacteristic? = null
    private var downlink: BluetoothGattCharacteristic? = null
    private val pending = ArrayDeque<MethodMeshTransportEnvelope>()
    private val pendingLock = Any()
    private val callbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var scanCallback: ScanCallback? = null

    override suspend fun start(onEnvelope: suspend (MethodMeshTransportEnvelope) -> Unit) {
        inbound = onEnvelope
        if (gatewayAddress().isBlank()) {
            mutableStatus.value = TransportStatus(false, false, "No ESP mesh gateway provisioned")
        } else connectConfigured()
    }

    override suspend fun stop() {
        inbound = null
        scanCallback?.let { bluetoothAdapter()?.bluetoothLeScanner?.stopScan(it) }
        scanCallback = null
        gatt?.close()
        gatt = null
        uplink = null
        downlink = null
        mutableStatus.value = TransportStatus(false, false, "Stopped")
    }

    @SuppressLint("MissingPermission")
    override suspend fun send(envelope: MethodMeshTransportEnvelope): TransportSendResult {
        val frame = EspMeshBridgeFrame("OUTBOUND", envelope = envelope).toJson().toString()
        if (frame.toByteArray(Charsets.UTF_8).size > MAX_BLE_FRAME_BYTES) {
            return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, "Gateway frame needs fragmentation")
        }
        val characteristic = uplink
        val connection = gatt
        if (characteristic == null || connection == null || !status.value.connected) {
            synchronized(pendingLock) {
                if (pending.size >= MAX_PENDING) pending.removeFirst()
                pending.addLast(envelope)
            }
            return TransportSendResult(TransportOutboxState.QUEUED, "Waiting for gateway connection")
        }
        return if (write(connection, characteristic, frame.toByteArray(Charsets.UTF_8))) {
            TransportSendResult(TransportOutboxState.SENT)
        } else {
            synchronized(pendingLock) {
                if (pending.size >= MAX_PENDING) pending.removeLast()
                pending.addFirst(envelope)
            }
            TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "BLE write failed")
        }
    }

    @SuppressLint("MissingPermission")
    fun scan(onCandidate: (EspMeshGatewayCandidate) -> Unit, onFinished: () -> Unit = {}) {
        val scanner = bluetoothAdapter()?.bluetoothLeScanner ?: run { onFinished(); return }
        scanCallback?.let { scanner.stopScan(it) }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = result.scanRecord?.deviceName.orEmpty().ifBlank { device.name.orEmpty() }
                if (name.contains("MethodMesh", ignoreCase = true) || result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true) {
                    onCandidate(EspMeshGatewayCandidate(device.address, name.ifBlank { "ESP mesh gateway" }, result.rssi))
                }
            }
            override fun onScanFailed(errorCode: Int) { mutableStatus.value = TransportStatus(false, false, "BLE scan failed ($errorCode)"); onFinished() }
        }
        scanCallback = callback
        scanner.startScan(callback)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ scanner.stopScan(callback); scanCallback = null; onFinished() }, SCAN_DURATION_MS)
    }

    @SuppressLint("MissingPermission")
    fun provision(candidate: EspMeshGatewayCandidate) {
        prefs.edit().putString(KEY_ADDRESS, candidate.address).putString(KEY_NAME, candidate.name).apply()
        connect(candidate.address)
    }

    @SuppressLint("MissingPermission")
    fun configureNetwork(networkId: String, networkKey: String, provisioningToken: String, peers: List<String> = emptyList()): TransportSendResult {
        val connection = gatt
        val characteristic = uplink
        if (connection == null || characteristic == null || !status.value.connected) {
            return TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "Gateway is not connected")
        }
        val frame = EspMeshBridgeFrame("CONFIG", body = org.json.JSONObject()
            .put("network_id", networkId.trim())
            .put("network_key", networkKey.trim())
            .put("provisioning_token", provisioningToken.trim())
            .put("peers", org.json.JSONArray(peers))).toJson().toString().toByteArray(Charsets.UTF_8)
        if (frame.size > MAX_BLE_FRAME_BYTES) return TransportSendResult(TransportOutboxState.FAILED_PERMANENT, "Configuration is too large")
        return if (write(connection, characteristic, frame)) {
            TransportSendResult(TransportOutboxState.SENT, "Network settings sent")
        } else TransportSendResult(TransportOutboxState.FAILED_RETRYABLE, "BLE write failed")
    }

    fun gatewayAddress(): String = prefs.getString(KEY_ADDRESS, "").orEmpty()
    fun gatewayName(): String = prefs.getString(KEY_NAME, "").orEmpty()

    @SuppressLint("MissingPermission")
    private fun connectConfigured() = connect(gatewayAddress())

    @SuppressLint("MissingPermission")
    private fun connect(address: String) {
        val adapter = bluetoothAdapter()
        val device = runCatching { adapter?.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            mutableStatus.value = TransportStatus(false, false, "Gateway address is not available")
            return
        }
        gatt?.close()
        mutableStatus.value = TransportStatus(true, false, "Connecting to ${gatewayName().ifBlank { address }}")
        gatt = if (Build.VERSION.SDK_INT >= 26) device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE) else device.connectGatt(context, false, callback)
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                mutableStatus.value = TransportStatus(true, true, "Gateway connected")
                gatt.discoverServices()
            } else {
                mutableStatus.value = TransportStatus(true, false, "Gateway disconnected ($status)")
                gatt.close()
                this@EspMeshTransportProvider.gatt = null
                uplink = null
                downlink = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val service = gatt.getService(SERVICE_UUID)
            uplink = service?.getCharacteristic(UPLINK_UUID)
            downlink = service?.getCharacteristic(DOWNLINK_UUID)
            downlink?.let { characteristic ->
                gatt.setCharacteristicNotification(characteristic, true)
                characteristic.getDescriptor(CLIENT_CONFIG_UUID)?.let { descriptor ->
                    if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                    else writeLegacyDescriptor(gatt, descriptor)
                }
            }
            if (uplink == null || downlink == null) mutableStatus.value = TransportStatus(true, false, "Gateway protocol service not found")
            else {
                sendHello(gatt)
                drainPending()
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != DOWNLINK_UUID) return
            handleFrame(value)
        }
    }

    private fun handleFrame(bytes: ByteArray) {
        try {
            val frame = EspMeshBridgeFrame.fromJson(JSONObject(bytes.toString(Charsets.UTF_8)))
            frame.envelope?.let { envelope ->
                callbackScope.launch { inbound?.invoke(envelope) }
            }
        } catch (_: Exception) {
            mutableStatus.value = status.value.copy(detail = "Invalid gateway frame")
        }
    }

    @SuppressLint("MissingPermission")
    private fun drainPending() {
        val waiting = synchronized(pendingLock) { pending.toList().also { pending.clear() } }
        waiting.forEach { envelope -> callbackScope.launch { send(envelope) } }
    }

    @SuppressLint("MissingPermission")
    private fun sendHello(gatt: BluetoothGatt) {
        val characteristic = uplink ?: return
        val frame = EspMeshBridgeFrame("HELLO", body = JSONObject()
            .put("client", "MethodMesh Android")
            .put("transport", TRANSPORT_ID)
            .put("protocol_version", EspMeshBridgeFrame.VERSION))
            .toJson().toString().toByteArray(Charsets.UTF_8)
        if (frame.size <= MAX_BLE_FRAME_BYTES) write(gatt, characteristic, frame)
    }

    @SuppressLint("MissingPermission")
    private fun write(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothGatt.GATT_SUCCESS
        else writeLegacyCharacteristic(gatt, characteristic, bytes)
    }

    @Suppress("DEPRECATION")
    private fun writeLegacyDescriptor(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor) {
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        gatt.writeDescriptor(descriptor)
    }

    @Suppress("DEPRECATION")
    private fun writeLegacyCharacteristic(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, bytes: ByteArray): Boolean {
        characteristic.value = bytes
        return gatt.writeCharacteristic(characteristic)
    }

    private fun bluetoothAdapter(): BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    companion object {
        const val TRANSPORT_ID = "espmesh.gateway"
        private const val PREFS = "methodmesh_espmesh_gateway"
        private const val KEY_ADDRESS = "gateway_address"
        private const val KEY_NAME = "gateway_name"
        private const val MAX_BLE_FRAME_BYTES = 4096
        private const val MAX_PENDING = 100
        private const val SCAN_DURATION_MS = 12_000L
        val SERVICE_UUID: UUID = UUID.fromString("b6f2a910-9b8f-4f4e-9a1f-4f37a0010000")
        val UPLINK_UUID: UUID = UUID.fromString("b6f2a911-9b8f-4f4e-9a1f-4f37a0010000")
        val DOWNLINK_UUID: UUID = UUID.fromString("b6f2a912-9b8f-4f4e-9a1f-4f37a0010000")
        val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        @Volatile private var instance: EspMeshTransportProvider? = null
        fun create(context: Context): EspMeshTransportProvider = instance ?: synchronized(this) { instance ?: EspMeshTransportProvider(context.applicationContext).also { instance = it } }
        fun get(context: Context): EspMeshTransportProvider = create(context)
    }
}
