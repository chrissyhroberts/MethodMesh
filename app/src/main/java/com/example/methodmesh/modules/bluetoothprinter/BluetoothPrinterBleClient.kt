package com.example.methodmesh.modules.bluetoothprinter

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Module-local Android BLE boundary for Qutie-family printers.
 *
 * Keeping GATT state here prevents the Compose capability screen from becoming
 * the protocol/transport implementation. This class depends only on Android BLE
 * APIs and coroutines already used by MethodMesh.
 */
internal class BluetoothPrinterBleClient(
    context: Context,
    private val onStatus: (String) -> Unit,
    private val onConnectedChanged: (Boolean) -> Unit
) {
    data class SendResult(
        val succeeded: Boolean,
        val bytesSent: Int,
        val writeMode: String
    )

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val pendingWrite = AtomicReference<CompletableDeferred<Boolean>?>(null)

    private var gatt: BluetoothGatt? = null
    private var writer: BluetoothGattCharacteristic? = null
    private var notifier: BluetoothGattCharacteristic? = null
    private var negotiatedMtu: Int = 23
    private var lastNotification: String = ""
    private var connected: Boolean = false

    val isConnected: Boolean get() = connected && gatt != null && writer != null

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        closeGattOnly()
        connected = false
        writer = null
        notifier = null
        negotiatedMtu = 23
        lastNotification = ""
        onConnectedChanged(false)
        onStatus("Connecting to ${device.name ?: device.address}…")

        gatt = device.connectGatt(appContext, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(g: BluetoothGatt, statusCode: Int, newState: Int) {
                handler.post {
                    connected = newState == BluetoothProfile.STATE_CONNECTED
                    onConnectedChanged(connected)
                    if (connected) {
                        onStatus("Connected; discovering printer services…")
                        g.requestMtu(247)
                        g.discoverServices()
                    } else {
                        writer = null
                        notifier = null
                        onStatus("Disconnected (GATT $statusCode).")
                    }
                }
            }

            override fun onMtuChanged(g: BluetoothGatt, mtu: Int, statusCode: Int) {
                if (statusCode == BluetoothGatt.GATT_SUCCESS) {
                    handler.post { negotiatedMtu = mtu }
                }
            }

            override fun onServicesDiscovered(g: BluetoothGatt, statusCode: Int) {
                handler.post {
                    if (statusCode != BluetoothGatt.GATT_SUCCESS) {
                        onStatus("Printer service discovery failed (GATT $statusCode).")
                        return@post
                    }
                    val service = g.services.firstOrNull {
                        it.uuid.toString().equals(BluetoothPrinterProtocol.DEFAULT_SERVICE_UUID, ignoreCase = true)
                    }
                    if (service == null) {
                        onStatus("Configured printer service was not found.")
                        return@post
                    }

                    writer = service.characteristics.firstOrNull {
                        it.uuid.toString().equals(BluetoothPrinterProtocol.DEFAULT_WRITE_UUID, ignoreCase = true) &&
                            it.properties and (
                                BluetoothGattCharacteristic.PROPERTY_WRITE or
                                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
                                ) != 0
                    }
                    notifier = service.characteristics.firstOrNull {
                        it.uuid.toString().equals(BluetoothPrinterProtocol.DEFAULT_NOTIFY_UUID, ignoreCase = true) &&
                            it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                    }

                    notifier?.let { characteristic ->
                        g.setCharacteristicNotification(characteristic, true)
                        characteristic.getDescriptor(UUID.fromString(BluetoothPrinterProtocol.CLIENT_CONFIG_UUID))?.let { descriptor ->
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            g.writeDescriptor(descriptor)
                        }
                    }

                    val properties = writer?.properties ?: 0
                    val mode = when {
                        properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> "write-no-response"
                        properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> "acknowledged-write"
                        else -> "none"
                    }
                    onStatus(
                        if (writer != null) {
                            "Printer endpoint ready ($mode, MTU $negotiatedMtu)."
                        } else {
                            "Printer service found, but the configured write endpoint is unavailable."
                        }
                    )
                }
            }

            override fun onCharacteristicChanged(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                val hex = value.joinToString(" ") { "%02X".format(it) }
                handler.post {
                    lastNotification = hex
                    onStatus("Printer response: $hex")
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(g: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                onCharacteristicChanged(g, characteristic, characteristic.value ?: byteArrayOf())
            }

            override fun onCharacteristicWrite(
                g: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                statusCode: Int
            ) {
                pendingWrite.getAndSet(null)?.complete(statusCode == BluetoothGatt.GATT_SUCCESS)
            }
        })
    }

    @SuppressLint("MissingPermission")
    suspend fun send(bytes: ByteArray): SendResult {
        val currentGatt = gatt
        val characteristic = writer
        if (!connected || currentGatt == null || characteristic == null) {
            return SendResult(false, 0, "none")
        }
        if (bytes.isEmpty()) {
            return SendResult(false, 0, "none")
        }

        val properties = characteristic.properties
        val supportsNoResponse = properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        val supportsAck = properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
        if (!supportsNoResponse && !supportsAck) {
            return SendResult(false, 0, "none")
        }

        val writeMode = if (supportsNoResponse) "write-no-response" else "acknowledged-write"
        val chunkSize = minOf(100, (negotiatedMtu - 3).coerceAtLeast(20))
        val chunks = bytes.toList().chunked(chunkSize).map { it.toByteArray() }
        onStatus("Sending ${bytes.size} bytes in ${chunks.size} packet(s)…")

        var ok = true
        var sent = 0
        for (chunk in chunks) {
            if (!ok) break
            if (supportsNoResponse) {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                characteristic.value = chunk
                ok = currentGatt.writeCharacteristic(characteristic)
                if (ok) {
                    sent += chunk.size
                    delay(14)
                }
            } else {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                characteristic.value = chunk
                val confirmation = CompletableDeferred<Boolean>().also { pendingWrite.set(it) }
                val queued = currentGatt.writeCharacteristic(characteristic)
                ok = queued && (withTimeoutOrNull(2500) { confirmation.await() } == true)
                if (ok) {
                    sent += chunk.size
                    delay(8)
                } else {
                    pendingWrite.compareAndSet(confirmation, null)
                }
            }
        }

        val message = if (ok) {
            if (lastNotification.isBlank()) {
                "Print payload sent (${bytes.size} bytes)."
            } else {
                "Print payload sent; last response $lastNotification"
            }
        } else {
            "Printer rejected or stalled during the payload."
        }
        onStatus(message)
        return SendResult(ok, sent, writeMode)
    }

    fun close() {
        pendingWrite.getAndSet(null)?.cancel()
        closeGattOnly()
        handler.removeCallbacksAndMessages(null)
        connected = false
    }

    @SuppressLint("MissingPermission")
    private fun closeGattOnly() {
        runCatching { gatt?.close() }
        gatt = null
        writer = null
        notifier = null
    }
}
