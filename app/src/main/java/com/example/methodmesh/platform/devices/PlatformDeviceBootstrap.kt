package com.example.methodmesh.platform.devices

/** Registers transport extension points without binding capabilities to hardware APIs. */
object PlatformDeviceBootstrap {
    fun initialise() {
        DeviceTransport.entries.forEach { transport ->
            if (DeviceServiceRegistry.service(transport) == null) {
                DeviceServiceRegistry.register(UnconfiguredDeviceService(transport))
            }
        }
    }
}
