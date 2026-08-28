package com.example.methodmesh.platform.devices

import java.util.concurrent.ConcurrentHashMap

/** Runtime registry for interchangeable transport adapters. */
object DeviceServiceRegistry {
    private val services = ConcurrentHashMap<DeviceTransport, DeviceSignalService>()

    fun register(service: DeviceSignalService) {
        services[service.transport] = service
    }

    fun service(transport: DeviceTransport): DeviceSignalService? = services[transport]

    fun all(): List<DeviceSignalService> = services.values.sortedBy { it.transport.name }

    fun unregister(transport: DeviceTransport) {
        services.remove(transport)
    }
}
