package com.example.researchos.platform.devices

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Transport-neutral signal emitted by a device service. */
data class DeviceSignal(
    val sourceId: String,
    val signalType: String,
    val value: String,
    val unit: String? = null,
    val capturedAtEpochMillis: Long = System.currentTimeMillis(),
    val metadata: Map<String, String> = emptyMap()
)

enum class DeviceTransport { CAMERA, NFC, BLE, BLUETOOTH_CLASSIC, USB, WIFI, LORA, ANDROID_SENSOR, LOCATION, AUDIO }

data class DeviceDescriptor(
    val id: String,
    val name: String,
    val transport: DeviceTransport,
    val capabilities: Set<String> = emptySet(),
    val metadata: Map<String, String> = emptyMap()
)

sealed interface DeviceServiceState {
    data object Idle : DeviceServiceState
    data object Discovering : DeviceServiceState
    data class Ready(val device: DeviceDescriptor) : DeviceServiceState
    data class Unavailable(val reason: String) : DeviceServiceState
    data class Failed(val reason: String) : DeviceServiceState
}

/**
 * Base contract for all hardware backends. Capabilities consume DeviceSignal;
 * they do not depend on BLE, USB or another transport implementation directly.
 */
interface DeviceSignalService {
    val transport: DeviceTransport
    val state: Flow<DeviceServiceState>
    val signals: Flow<DeviceSignal>
    suspend fun start(context: Context)
    suspend fun stop()
}

interface DiscoverableDeviceService : DeviceSignalService {
    val devices: Flow<List<DeviceDescriptor>>
    suspend fun discover(context: Context)
    suspend fun connect(context: Context, deviceId: String)
    suspend fun disconnect()
}

/** Safe placeholder used where a transport is supported architecturally but no protocol adapter is installed. */
class UnconfiguredDeviceService(
    override val transport: DeviceTransport
) : DeviceSignalService {
    override val state: Flow<DeviceServiceState> = emptyFlow()
    override val signals: Flow<DeviceSignal> = emptyFlow()
    override suspend fun start(context: Context) = Unit
    override suspend fun stop() = Unit
}
